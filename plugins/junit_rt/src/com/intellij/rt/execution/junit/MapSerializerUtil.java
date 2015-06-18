/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.rt.execution.junit;


import java.util.Iterator;
import java.util.Map;

public class MapSerializerUtil {
  public static final String TEST_FAILED = "testFailed";
  public static final String TEST_IGNORED = "testIgnored";


  /**
   * String escaping info provider.
   */
  public interface EscapeInfoProvider {
    /**
     * Converts character to its representation in the final string
     * @param c character to convert
     * @return character representation or 0 if conversion is not applicable to that character
     */
    char escape(char c);

    /**
     * Escape character to use before escaped characters (before character representations generated by {@link #escape(char)}  method)
     * @return see above
     */
    char escapeCharacter();
  }

  public static final EscapeInfoProvider STD_ESCAPER = new EscapeInfoProvider() {
    public char escape(final char c) {
      switch (c) {
        case '\n': return 'n';
        case '\r': return 'r';
        case '\u0085': return 'x'; // next-line character
        case '\u2028': return 'l'; // line-separator character
        case '\u2029': return 'p'; // paragraph-separator character
        case '|': return '|';
        case '\'': return '\'';
        case '[': return '[';
        case ']': return ']';
        default:return 0;
      }
    }

    public char escapeCharacter() {
      return '|';
    }
  };

  /**
   * Escapes characters specified by provider with '\' and specified character.
   * @param str initial string
   * @param p escape info provider.
   * @return escaped string.
   */
  public static String escapeStr(final String str, EscapeInfoProvider p) {
    if (str == null) return null;
    int finalCount = calcFinalEscapedStringCount(str, p);

    if (str.length() == finalCount) return str;

    char[] resultChars = new char[finalCount];
    int resultPos = 0;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      final char escaped = p.escape(c);
      if (escaped != 0) {
        resultChars[resultPos++] = p.escapeCharacter();
        resultChars[resultPos++] = escaped;
      }
      else {
        resultChars[resultPos++] = c;
      }
    }

    if (resultPos != finalCount) {
      throw new RuntimeException("Incorrect escaping for '" + str + "'");
    }
    return new String(resultChars);
  }

  private static int calcFinalEscapedStringCount(final String name, final EscapeInfoProvider p) {
    int result = 0;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (p.escape(c) != 0) {
        result += 2;
      }
      else {
        result += 1;
      }
    }

    return result;
  }

  public static String asString(final String messageName, final Map attributes) {
    String text = "##teamcity[" + messageName;
    for (Iterator iterator = attributes.keySet().iterator(); iterator.hasNext(); ) {
      final Object attrName = iterator.next();
      text += " " + attrName + "='" + escapeStr((String)attributes.get(attrName), STD_ESCAPER) + "'";
    }
    text += "]";
    return text;
  }

}
