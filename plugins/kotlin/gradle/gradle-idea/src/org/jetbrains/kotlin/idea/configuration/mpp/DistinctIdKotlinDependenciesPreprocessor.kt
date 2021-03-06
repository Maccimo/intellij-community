// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.configuration.mpp

import org.jetbrains.kotlin.gradle.KotlinDependency

internal object DistinctIdKotlinDependenciesPreprocessor : KotlinDependenciesPreprocessor {
    override fun invoke(dependencies: Iterable<KotlinDependency>): List<KotlinDependency> {
        return dependencies
            .groupBy { dependency -> dependency.id }
            .mapValues { (_, dependenciesWithSameId) ->
                dependenciesWithSameId.firstOrNull { it.scope == "COMPILE" } ?: dependenciesWithSameId.lastOrNull()
            }
            .values
            .filterNotNull()
    }

}