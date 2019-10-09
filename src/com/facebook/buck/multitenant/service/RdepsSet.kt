/*
 * Copyright 2019-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.multitenant.service

import com.facebook.buck.multitenant.collect.Generation
import io.vavr.collection.HashSet
import io.vavr.collection.Set
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntCollection

/**
 * A set of rdeps. Note that the type of storage we use depends on a combination of (1) the size of
 * the set and (2) how frequently it is updated.
 */
internal sealed class RdepsSet : Iterable<BuildTargetId> {
    abstract val size: Int

    /**
     * Custom method for adding the values in this set to an [IntCollection]. This gives the
     * implementation the opportunity to avoid boxing and unboxing integers.
     */
    abstract fun addAllTo(destination: IntCollection)

    /**
     * Unique representation of a set of rdeps that does not share any memory with another RdepsSet.
     */
    class Unique(val rdeps: BuildTargetSet) : RdepsSet() {
        override val size: Int get() = rdeps.size
        override fun iterator(): Iterator<BuildTargetId> = IntArrayList.wrap(rdeps).iterator()
        override fun addAllTo(destination: IntCollection) {
            addBuildTargetSetToCollection(rdeps, destination)
        }
    }

    /**
     * Note this is backed by a persistent collection, which for a single instance, we expect to
     * take up more memory than if it were represented as a BuildTargetSet.
     */
    class Persistent(val rdeps: Set<BuildTargetId>) : RdepsSet() {
        override val size: Int get() = rdeps.size()
        override fun iterator(): Iterator<BuildTargetId> = rdeps.iterator()
        override fun addAllTo(destination: IntCollection) {
            for (buildTargetId in rdeps) {
                destination.add(buildTargetId)
            }
        }
    }
}

internal fun addBuildTargetSetToCollection(source: BuildTargetSet, destination: IntCollection) {
    // Although it might be slightly more efficient to wrap the BuildTargetSet as an IntCollection
    // so that we can use IntCollection.addAll(IntCollection), as it does some some capacity checks
    // before calling add() in a loop, but IntCollection is a little annoying to implement, so we
    // should only bother if profiling proves it is worth it.
    //
    // Incidentally, it.unimi.dsi.fastutil.ints.IntIterators.wrap(int[]) almost does what we need
    // except addAll() requires an IntCollection rather than an IntListIterator.
    for (buildTargetId in source) {
        destination.add(buildTargetId)
    }
}

/**
 * Enum type that represents either an "add" or a "remove" to a [BuildTargetSet]. These can be
 * computed independently and later "applied" to a persistent collection to derive a new version.
 */
internal sealed class BuildTargetSetDelta : Comparable<BuildTargetSetDelta> {
    abstract val buildTargetId: BuildTargetId
    override fun compareTo(other: BuildTargetSetDelta): Int = buildTargetId.compareTo(other.buildTargetId)
    data class Add(override val buildTargetId: BuildTargetId) : BuildTargetSetDelta()
    data class Remove(override val buildTargetId: BuildTargetId) : BuildTargetSetDelta()
}

/**
 * Takes a list of individual rdeps updates applied to a repo at a point in time and computes the
 * aggregate changes that need to be made to an rdepsMap.
 */
internal fun deriveRdepsDeltas(
    rdepsUpdates: List<Pair<BuildTargetId, BuildTargetSetDelta>>,
    generation: Generation,
    indexGenerationData: IndexGenerationData
): Map<BuildTargetId, RdepsSet?> {
    val deltasByTarget = collectDeltasByTarget(rdepsUpdates)
    val deltaDeriveInfos = indexGenerationData.withRdepsMap { rdepsMap ->
        deltasByTarget.map { (buildTargetId, buildTargetSetDeltas) ->
            val oldRdeps = rdepsMap.getVersion(buildTargetId, generation)
            DeltaDeriveInfo(buildTargetId, oldRdeps, buildTargetSetDeltas)
        }
    }

    return aggregateDeltaDeriveInfos(deltaDeriveInfos)
}

/**
 * Iterates the `deltas` and for each [BuildTargetId], collects its corresponding deltas into its
 * own [List]. In the returned [List], every [Pair] will have a distinct [BuildTargetId].
 */
private fun collectDeltasByTarget(
    deltas: List<Pair<BuildTargetId, BuildTargetSetDelta>>
): List<Pair<BuildTargetId, MutableList<BuildTargetSetDelta>>> {
    // targetToRdepsUpdates is effectively a multimap, but none of the Guava ListMultimap
    // implementations work for us here because we want to be able to sort the List for each
    // entry when we are done populating the map and Guava's ListMultimap returns the List for
    // each entry as an unmodifiable view.
    val targetToRdepsUpdates = mutableMapOf<BuildTargetId, MutableList<BuildTargetSetDelta>>()
    deltas.forEach { (buildTargetId, buildTargetSetDelta) ->
        val rdepsUpdates = targetToRdepsUpdates[buildTargetId] ?: mutableListOf()
        if (rdepsUpdates.isEmpty()) {
            targetToRdepsUpdates[buildTargetId] = rdepsUpdates
        }
        rdepsUpdates.add(buildTargetSetDelta)
    }
    return targetToRdepsUpdates.toList()
}

/**
 * Information needed to derive a new RdepsSet from an existing one.
 * @property buildTargetId target whose rdeps this represents
 * @property oldRdeps previous version of rdeps for the target
 * @property deltas on top of old rdeps. This is not guaranteed to be sorted! Though it is a
 *     MutableList so a client is free to sort it.
 */
internal data class DeltaDeriveInfo(
    val buildTargetId: BuildTargetId,
    val oldRdeps: RdepsSet?,
    val deltas: MutableList<BuildTargetSetDelta>
)

/**
 * Takes a list of build targets whose rdeps have changed and produces the new version of the rdeps
 * for each build target. Where it makes sense, persistent collections are used to make more
 * efficient use of memory, as they make it possible to share information between old and new
 * versions of a set of rdeps.
 */
internal fun aggregateDeltaDeriveInfos(
    deltaDeriveInfos: List<DeltaDeriveInfo>
): Map<BuildTargetId, RdepsSet?> {
    // We use java.util.HashMap so we can specify the initialCapacity. We use the fully qualified
    // name here to clarify that this is NOT a io.vavr.collection.HashMap.
    val out = java.util.HashMap<BuildTargetId, RdepsSet?>(deltaDeriveInfos.size)
    deltaDeriveInfos.forEach { (buildTargetId, oldRdeps, deltas) ->
        out[buildTargetId] = if (oldRdeps == null) {
            // No one was depending on this rule at the previous generation. All of the
            // deltas must be of type Add.
            check(deltas.all { it is BuildTargetSetDelta.Add }) {
                "There was a 'Remove' delta for a non-existent set."
            }
            deltas.sort()
            val buildTargetIds = IntArray(deltas.size) { index ->
                deltas[index].buildTargetId
            }
            // Even though buildTargetIds might be large, we create a unique copy of the data
            // because we would prefer to use a more compact storage format if it turns out
            // that it is not going to be updated very frequently.
            RdepsSet.Unique(buildTargetIds)
        } else {
            applyDeltas(oldRdeps, deltas)
        }
    }
    return out
}

/**
 * If the size of the rdeps set is below this size, we always choose Unique over Persistent.
 * NOTE: we should use telemetry to determine the right value for this constant. Currently, it is
 * completely pulled out of thin air.
 */
internal const val THRESHOLD_FOR_UNIQUE_VS_PERSISTENT = 10

/**
 * Derives a new RdepsSet from an existing one by applying some deltas. If applying all of the
 * deltas results in an empty set, returns null.
 * @param oldRdeps original set
 * @param deltas is not required to be sorted, but it may be sorted as a result of invoking this
 *     method. By construction, it should also be non-empty.
 */
private fun applyDeltas(oldRdeps: RdepsSet, deltas: MutableList<BuildTargetSetDelta>): RdepsSet? {
    val size = deltas.fold(oldRdeps.size) { acc, delta ->
        when (delta) {
            is BuildTargetSetDelta.Add -> acc + 1
            is BuildTargetSetDelta.Remove -> acc - 1
        }
    }
    return when {
        size == 0 -> null
        size < THRESHOLD_FOR_UNIQUE_VS_PERSISTENT -> createSimpleSet(oldRdeps, deltas, size)
        else -> {
            val existingRdeps = when (oldRdeps) {
                is RdepsSet.Unique -> {
                    // The old version was Unique, but now we have exceeded
                    // THRESHOLD_FOR_UNIQUE_VS_PERSISTENT, so now we want to use Persistent for the new
                    // version.
                    HashSet.ofAll(oldRdeps)
                }
                is RdepsSet.Persistent -> {
                    oldRdeps.rdeps
                }
            }
            deriveNewPersistentSet(existingRdeps, deltas)
        }
    }
}

private fun createSimpleSet(
    oldRdeps: RdepsSet,
    deltas: MutableList<BuildTargetSetDelta>,
    expectedSize: Int
): RdepsSet.Unique {
    val oldRdepsSorted: IntArray = when (oldRdeps) {
        is RdepsSet.Unique -> oldRdeps.rdeps
        is RdepsSet.Persistent -> {
            val array = oldRdeps.rdeps.toMutableList().toIntArray()
            array.sort()
            array
        }
    }
    deltas.sort()
    val newBuildTargetSet = IntArray(expectedSize)

    // Now that both oldRdeps and deltas are sorted, we walk forward and populate newBuildTargetSet,
    // as appropriate.
    var oldIndex = 0
    var deltaIndex = 0
    var index = 0
    while (oldIndex < oldRdepsSorted.size && deltaIndex < deltas.size) {
        val oldBuildTargetId = oldRdepsSorted[oldIndex]
        val delta = deltas[deltaIndex]
        val deltaBuildTargetId = delta.buildTargetId
        when {
            oldBuildTargetId < deltaBuildTargetId -> {
                // Next delta does not affect oldBuildTargetId, so add oldBuildTargetId to the
                // output.
                newBuildTargetSet[index++] = oldBuildTargetId
                ++oldIndex
            }
            oldBuildTargetId > deltaBuildTargetId -> {
                when (delta) {
                    is BuildTargetSetDelta.Add -> {
                        newBuildTargetSet[index++] = deltaBuildTargetId
                        ++deltaIndex
                    }
                    is BuildTargetSetDelta.Remove -> {
                        error(
                            "Should not Remove when $deltaBuildTargetId does not exist in oldRdeps.")
                    }
                }
            }
            else /* oldBuildTargetId == deltaBuildTargetId */ -> {
                when (delta) {
                    is BuildTargetSetDelta.Add -> {
                        error("Should not Add when $deltaBuildTargetId already exists in oldRdeps.")
                    }
                    is BuildTargetSetDelta.Remove -> {
                        // We should omit this buildTargetId from the output, so
                        ++oldIndex
                        ++deltaIndex
                    }
                }
            }
        }
    }

    while (oldIndex < oldRdepsSorted.size) {
        newBuildTargetSet[index++] = oldRdepsSorted[oldIndex++]
    }
    while (deltaIndex < deltas.size) {
        when (val delta = deltas[deltaIndex++]) {
            is BuildTargetSetDelta.Add -> {
                newBuildTargetSet[index++] = delta.buildTargetId
            }
            is BuildTargetSetDelta.Remove -> {
                error("Should not Remove when ${delta.buildTargetId} does not exist in oldReps.")
            }
        }
    }

    if (index != expectedSize) {
        error("Only assigned $index out of $expectedSize slots in output.")
    }

    return RdepsSet.Unique(newBuildTargetSet)
}

private fun deriveNewPersistentSet(
    oldRdeps: Set<BuildTargetId>,
    deltas: List<BuildTargetSetDelta>
): RdepsSet.Persistent {
    var out = oldRdeps
    deltas.forEach { delta ->
        out = when (delta) {
            is BuildTargetSetDelta.Add -> out.add(delta.buildTargetId)
            is BuildTargetSetDelta.Remove -> out.remove(delta.buildTargetId)
        }
    }
    return RdepsSet.Persistent(out)
}
