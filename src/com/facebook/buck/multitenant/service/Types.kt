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

import com.facebook.buck.core.model.UnconfiguredBuildTarget
import com.facebook.buck.multitenant.fs.FsAgnosticPath
import java.util.Objects

typealias Commit = String

internal typealias BuildTargetId = Int

/**
 * The values in the array must be sorted in ascending order or else [equalsBuildTargetSet] and
 * [hashCodeBuildTargetSet] will not work properly.
 */
internal typealias BuildTargetSet = IntArray

/**
 * This is a RawTargetNode paired with its deps as determined by configuring the RawTargetNode with
 * the empty configuration.
 */
data class RawBuildRule(
    val targetNode: ServiceRawTargetNode,
    val deps: Set<UnconfiguredBuildTarget>
)

/**
 * Represents an error happened during parsing a package
 */
data class BuildPackageParsingError(val message: String, val stacktrace: List<String>)

/**
 * @param[deps] must be sorted in ascending order!!!
 */
internal data class InternalRawBuildRule(
    val targetNode: ServiceRawTargetNode,
    val deps: BuildTargetSet
) {
    /*
     * Because RawTargetNodeAndDeps contains an IntArray field, which does not play well with
     * `.equals()` (or `hashCode()`, for that matter), we have to do a bit of work to implement
     * these methods properly when the default implementations for a data class are not appropriate.
     */

    override fun equals(other: Any?): Boolean {
        if (other !is InternalRawBuildRule) {
            return false
        }
        return targetNode == other.targetNode && equalsBuildTargetSet(deps, other.deps)
    }

    override fun hashCode(): Int {
        return 31 * Objects.hash(targetNode) + hashCodeBuildTargetSet(deps)
    }
}

private fun equalsBuildTargetSet(set1: BuildTargetSet, set2: BuildTargetSet): Boolean {
    return set1.contentEquals(set2)
}

private fun hashCodeBuildTargetSet(set: BuildTargetSet): Int {
    return set.contentHashCode()
}

/**
 * By construction, the name for each rule in rules should be distinct across all of the rules in
 * the set.
 */
data class BuildPackage(
    val buildFileDirectory: FsAgnosticPath,
    val rules: Set<RawBuildRule>,
    val errors: List<BuildPackageParsingError> = emptyList()
)

internal data class InternalBuildPackage(
    val buildFileDirectory: FsAgnosticPath,
    val rules: Set<InternalRawBuildRule>
)

/**
 * By construction, the Path for each BuildPackage should be distinct across all of the
 * collections of build packages.
 */
data class BuildPackageChanges(
    val addedBuildPackages: List<BuildPackage> = emptyList(),
    val modifiedBuildPackages: List<BuildPackage> = emptyList(),
    val removedBuildPackages: List<FsAgnosticPath> = emptyList()
) {
    fun isEmpty(): Boolean = addedBuildPackages.isEmpty() && modifiedBuildPackages.isEmpty() && removedBuildPackages.isEmpty()
}

internal data class InternalChanges(
    val addedBuildPackages: List<InternalBuildPackage>,
    val modifiedBuildPackages: List<InternalBuildPackage>,
    val removedBuildPackages: List<FsAgnosticPath>
)

/**
 * Metadata for the commit loaded into index
 */
data class CommitData(
    /**
     * Hash of the commit
     */
    val commit: Commit,

    /**
     * Timestamp, in milliseconds since Unix epoch, when commit was loaded into an index
     */
    val timestampLoadedMillies: Long
)
