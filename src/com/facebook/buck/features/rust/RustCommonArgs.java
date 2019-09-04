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
package com.facebook.buck.features.rust;

import com.facebook.buck.core.description.arg.CommonDescriptionArg;
import com.facebook.buck.core.description.arg.HasDefaultPlatform;
import com.facebook.buck.core.description.arg.HasSrcs;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import org.immutables.value.Value;

public interface RustCommonArgs
    extends CommonDescriptionArg, HasNamedDeclaredDeps, HasSrcs, HasDefaultPlatform {
  @Value.NaturalOrder
  ImmutableSortedMap<SourcePath, String> getMappedSrcs();

  @Value.NaturalOrder
  ImmutableSortedMap<String, StringWithMacros> getEnv();

  Optional<String> getEdition();

  @Value.NaturalOrder
  ImmutableSortedSet<String> getFeatures();

  ImmutableList<StringWithMacros> getRustcFlags();

  Optional<String> getCrate();

  Optional<SourcePath> getCrateRoot();

  @Value.Default
  default PatternMatchedCollection<ImmutableSortedSet<BuildTarget>> getPlatformDeps() {
    return PatternMatchedCollection.of();
  }
}
