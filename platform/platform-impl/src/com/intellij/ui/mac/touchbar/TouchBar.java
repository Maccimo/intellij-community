// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;

class TouchBar implements NSTLibrary.ItemCreator {
  static final Logger LOG = Logger.getInstance(TouchBar.class);
  static final boolean ourCollectStats = Boolean.getBoolean("touchbar.collect.stats");

  final @Nullable TouchBarStats myStats;
  final @NotNull String myName;
  final @NotNull ItemsContainer myItems;
  final ItemListener myItemListener;
  final TBItemButton myCustomEsc;

  String[] myVisibleIds;
  private ID myNativePeer;        // java wrapper holds native object

  static final TouchBar EMPTY = new TouchBar();

  private TouchBar() {
    myName = "EMPTY_STUB_TOUCHBAR";
    myItems = new ItemsContainer(myName);
    myCustomEsc = null;
    myNativePeer = ID.NIL;
    myItemListener = null;
    myStats = null;
  }

  TouchBar(@NotNull String touchbarName) {
    this(touchbarName, null, false);
  }

  TouchBar(@NotNull String touchbarName,
           @Nullable CrossEscInfo crossEscInfo,
           boolean closeOnItemEvent) {
    if (closeOnItemEvent) {
      myItemListener = (src, evcode) -> {
        // NOTE: called from AppKit thread
        _closeSelf();
      };
    }
    else {
      myItemListener = null;
    }

    myName = touchbarName;
    myStats = ourCollectStats ? TouchBarStats.getStats(touchbarName) : null;
    myItems = new ItemsContainer(touchbarName);
    if (crossEscInfo != null) {
      final Icon ic = AllIcons.Mac.Touchbar.PopoverClose;
      myCustomEsc = new TBItemButton(myItemListener, null).setIcon(ic).setWidth(64).setTransparentBg().setAction(() -> {
        ActivityTracker.getInstance().inc();
        _closeSelf();
        if (crossEscInfo.emulateEsc) {
          Helpers.emulateKeyPress(KeyEvent.VK_ESCAPE);
        }
      }, false);
      myCustomEsc.setUid(touchbarName + "_custom_esc_button");
    }
    else {
      myCustomEsc = null;
    }

    myNativePeer = NST.createTouchBar(touchbarName, this, myCustomEsc != null ? myCustomEsc.getUid() : null);
  }

  boolean isCrossEsc() {
    return myCustomEsc != null;
  }

  @Override
  public String toString() {
    return myItems + "_" + myNativePeer;
  }

  @Override
  public ID createItem(@NotNull String uid) { // called from AppKit (when NSTouchBarDelegate create items)
    final long startNs = myStats != null ? System.nanoTime() : 0;
    final ID result = createItemImpl(uid);
    if (myStats != null) {
      myStats.incrementCounter(StatsCounters.itemsCreationDurationNs, System.nanoTime() - startNs);
    }
    return result;
  }

  private ID createItemImpl(@NotNull String uid) {
    final TBItem item;
    if (myCustomEsc != null && myCustomEsc.getUid().equals(uid)) {
      item = myCustomEsc;
    } else {
      item = myItems.findItem(uid);
    }

    if (item == null) {
      if (LOG.isDebugEnabled()) {
        // Theoretically possible when:
        // (1) EDT expand ActionGroup and call selectVisibleItemsToShow (that schedules next step in AppKit)
        // (2) AppKit going to create items passed from prev step, but paused by task-manager
        // (3) EDT re-expand ActionGroup and cleared (or refilled with new actions) items (and call selectVisibleItemsToShow with new set)
        // => can't find item from (1) in new set of items.
        // But container will be refilled with new items soon (because of second call selectVisibleItemsToShow from (3)).
        LOG.debug("can't create item %s | %s", uid, myItems.getDescription());
      }
      return ID.NIL;
    }
    return item.createNativePeer();
  }

  ID getNativePeer() { return myNativePeer; }

  void release() {
    final long startNs = myStats != null ? System.nanoTime() : 0;
    myItems.releaseAll();

    synchronized(this) {
      if (!myNativePeer.equals(ID.NIL)) {
        NST.releaseTouchBar(myNativePeer);
        myNativePeer = ID.NIL;
      }
    }

    if (myStats != null) {
      myStats.incrementCounter(StatsCounters.touchbarReleaseDurationNs, System.nanoTime() - startNs);
    }
  }

  //
  // NOTE: must call 'selectVisibleItemsToShow' after touchbar filling
  //
  @NotNull
  TBItemButton addButton() {
    @NotNull TBItemButton butt = new TBItemButton(myItemListener, myStats != null ? myStats.getActionStats("simple_button") : null);
    myItems.addItem(butt);
    return butt;
  }

  @NotNull
  TBItem addItem(@NotNull TBItem item) {
    return addItem(item, null);
  }

  @NotNull
  TBItem addItem(@NotNull TBItem item, @Nullable TBItem positionAnchor) {
    myItems.addItem(item, positionAnchor);
    return item;
  }

  @NotNull
  TBItemScrubber addScrubber() {
    final int defaultScrubberWidth = 500;
    @NotNull TBItemScrubber scrub = new TBItemScrubber(myItemListener, myStats, defaultScrubberWidth);
    myItems.addItem(scrub);
    return scrub;
  }

  void addSpacing(boolean large) { myItems.addSpacing(large); }

  void addFlexibleSpacing() { myItems.addFlexibleSpacing(); }

  void selectVisibleItemsToShow() {
    if (myItems.isEmpty()) {
      if (myVisibleIds != null && myVisibleIds.length > 0) {
        NST.selectItemsToShow(myNativePeer, null, 0);
      }
      myVisibleIds = null;
      return;
    }

    String[] ids = myItems.getVisibleIds();
    if (Arrays.equals(ids, myVisibleIds)) {
      return;
    }

    myVisibleIds = ids;
    NST.selectItemsToShow(myNativePeer, ids, ids.length);
  }

  void setPrincipal(@NotNull TBItem item) {
    // TODO: cache principal items to avoid unnecessary native calls
    NST.setPrincipal(myNativePeer, item.getUid());
  }

  void _closeSelf() {
    TouchBarsManager.hideTouchbar(this);
  }

  @NotNull TBItemAnActionButton createActionButton(@NotNull AnAction action) {
    TouchBarStats.AnActionStats stats;
    if (myStats == null) {
      stats = null;
    }
    else {
      stats = myStats.getActionStats(Helpers.getActionId(action));
    }
    return new TBItemAnActionButton(myItemListener, action, stats);
  }

  //
  // CrossEsc
  //

  static class CrossEscInfo {
    final boolean emulateEsc; // emulate 'esc' button tap when user taps cross-esc
    final boolean persistent; // don't change touchbar when other component gained focus

    CrossEscInfo(boolean emulateEsc, boolean persistent) {
      this.emulateEsc = emulateEsc;
      this.persistent = persistent;
    }
  }
}

final class SpacingItem extends TBItem {
  SpacingItem() { super("space", null); }

  @Override
  protected ID _createNativePeer() { return ID.NIL; } // mustn't be called
}