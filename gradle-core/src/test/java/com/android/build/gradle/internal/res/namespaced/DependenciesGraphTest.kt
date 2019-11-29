/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.utils.toImmutableMap
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DependenciesGraphTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emptyGraph() {
        val result = DependenciesGraph.create(ImmutableSet.of())

        assertThat(result.rootNodes).isEmpty()
        assertThat(result.allNodes).isEmpty()
    }

    @Test
    fun noArtifactsTest() {
        //        a    b
        //      /  \ /  \
        //    c    d    e
        //  /  \ /
        // f    g
        val g = createDependency("g")
        val f = createDependency("f")
        val e = createDependency("e")
        val d = createDependency("d", ImmutableSet.of(g))
        val c = createDependency("c", ImmutableSet.of(f, g))
        val b = createDependency("b", ImmutableSet.of(d, e))
        val a = createDependency("a", ImmutableSet.of(c, d))

        val result = DependenciesGraph.create(ImmutableSet.of(a, b))

        assertThat(result.rootNodes).hasSize(2)
        assertThat(result.allNodes).hasSize(7)
        assertThat(visit(result.rootNodes)).containsAllIn(listOf("a", "b", "c", "d", "e", "f", "g"))
    }

    /** As graph nodes are compared by object identity, it is important that duplicates are never created */
    @Test
    fun graphWithSharedRoot() {
        //   b  a
        //  /
        // a*
        val a = createDependency("a")
        val b = createDependency("b", ImmutableSet.of(a))

        // Do this in both orders to avoid spuriously passing if the 'a' is processed first/
        graphWithSharedRoot(listOf(a, b))
        graphWithSharedRoot(listOf(b, a))
    }

    private fun graphWithSharedRoot(list: List<ResolvedDependencyResult>) {
        val result = DependenciesGraph.create(
            ImmutableList.copyOf(list),
            artifacts = ImmutableMap.of()
        )

        // Collect every node from the whole graph.
        val allReachableNodes = HashSet<DependenciesGraph.Node>()
        result.rootNodes.forEach {
            allReachableNodes.add(it)
            allReachableNodes.addAll(it.transitiveDependencies)
        }
        result.allNodes.forEach {
            allReachableNodes.add(it)
            allReachableNodes.addAll(it.transitiveDependencies)
        }

        assertThat(result.rootNodes).hasSize(2)
        assertThat(result.allNodes).hasSize(2)
        assertThat(allReachableNodes).hasSize(2)
    }

    @Test
    fun graphWithArtifacts() {
        //     a
        //    / \
        //   b  c
        //       \
        //        d
        val d = createDependency("d")
        val c = createDependency("c", ImmutableSet.of(d))
        val b = createDependency("b")
        val a = createDependency("a", ImmutableSet.of(b, c))

        val fileA = temporaryFolder.newFile("a.txt")
        val fileB = temporaryFolder.newFile("b.txt")
        val fileC = temporaryFolder.newFile("c.txt")
        val fileD = temporaryFolder.newFile("d.txt")

        val artifacts: ImmutableMap<String, ImmutableCollection<File>> =
                mapOf<String, File>("a" to fileA, "b" to fileB, "c" to fileC, "d" to fileD)
                    .toImmutableMap { ImmutableList.of(it) }

        val result = DependenciesGraph.create(
                ImmutableSet.of(a),
                artifacts = ImmutableMap.of(AndroidArtifacts.ArtifactType.CLASSES, artifacts)
        )

        assertThat(result.rootNodes).hasSize(1)
        assertThat(result.allNodes).hasSize(4)
        val root = result.rootNodes.first()
        assertThat(root.getTransitiveFiles(AndroidArtifacts.ArtifactType.CLASSES))
            .containsExactlyElementsIn(listOf(fileA, fileB, fileC, fileD))
    }

    @Test
    fun clashingNormalizedNames() {
        // a> a< a_
        val a1 = createDependency("a>")
        val a2 = createDependency("a<")
        val a3 = createDependency("a_")

        val graph = DependenciesGraph.create(listOf(a1, a2, a3))

        assertThat(graph.allNodes).hasSize(3)
        assertThat(graph.allNodes.map { it.sanitizedName }.toSet()).hasSize(3)
        graph.allNodes.forEach {
            assertThat(it.sanitizedName).startsWith("a_")
        }
    }

    private fun visit(nodes: ImmutableSet<DependenciesGraph.Node>): List<String> {
        val names = ImmutableList.Builder<String>()
        nodes.forEach { visit(it, names) }
        return names.build()
    }

    private fun visit(node: DependenciesGraph.Node, names: ImmutableList.Builder<String>) {
        for (child in node.dependencies) {
            visit(child, names)
        }
        names.add(node.id.displayName)
    }
}
