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
package com.facebook.buck.core.starlark.rule.attr.impl;

import com.facebook.buck.core.artifact.Artifact;
import com.facebook.buck.core.artifact.ImmutableSourceArtifactImpl;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.providers.collect.ProviderInfoCollection;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;

/** Utility class to resolve specified sources into {@link Artifact}s. */
class SourceArtifactResolver {

  private SourceArtifactResolver() {}

  /**
   * @param src the object representing the sources of a rule attribute
   * @param deps the {@link ProviderInfoCollection} from the dependencies of a rule
   * @return the {@link Artifact}s representing the sources.
   */
  static Collection<Artifact> getArtifactsFromSrcs(
      Object src, ImmutableMap<BuildTarget, ProviderInfoCollection> deps) {
    if (src instanceof BuildTargetSourcePath) {
      BuildTarget target = ((BuildTargetSourcePath) src).getTarget();
      ProviderInfoCollection providerInfos = deps.get(target);
      if (providerInfos == null) {
        throw new IllegalStateException(String.format("Deps %s did not contain %s", deps, src));
      }
      return providerInfos.getDefaultInfo().defaultOutputs();
    } else if (src instanceof PathSourcePath) {
      return ImmutableSet.of(ImmutableSourceArtifactImpl.of((PathSourcePath) src));
    } else {
      throw new IllegalStateException(
          String.format("%s must either be a source file, or a BuildTarget", src));
    }
  }
}
