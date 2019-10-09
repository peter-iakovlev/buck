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
package com.facebook.buck.shell;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.rules.coercer.SourceSet;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.sandbox.NoSandboxExecutionStrategy;
import com.facebook.buck.sandbox.SandboxExecutionStrategy;
import com.facebook.buck.sandbox.SandboxProperties;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Utility builder class for building instances of GenruleBuildable. Not particularly useful outside
 * of tests, so it's here instead of in src.
 */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractGenruleBuildableBuilder {
  public abstract BuildTarget getBuildTarget();

  public abstract ProjectFilesystem getFilesystem();

  @Value.Default
  public SandboxExecutionStrategy getSandboxExecutionStrategy() {
    return new NoSandboxExecutionStrategy();
  }

  public abstract ImmutableList<SourcePath> getSrcs();

  public abstract Optional<String> getCmd();

  public abstract Optional<StringWithMacros> getCmdMacro();

  public abstract Optional<String> getBash();

  public abstract Optional<String> getCmdExe();

  @Value.Default
  public String getOut() {
    return "example-file";
  }

  @Value.Default
  public boolean getEnableSandboxingInGenrule() {
    return false;
  }

  @Value.Default
  public String getEnvironmentExpansionSeparator() {
    return " ";
  }

  @Value.Default
  public SandboxProperties getSandboxProperties() {
    return SandboxProperties.builder().build();
  }

  public abstract Optional<GenruleAndroidTools> getAndroidTools();

  public GenruleBuildable toBuildable() {
    return new GenruleBuildable(
        getBuildTarget(),
        getFilesystem(),
        getSandboxExecutionStrategy(),
        SourceSet.ofUnnamedSources(ImmutableSortedSet.copyOf(getSrcs())),
        getCmd().map(StringArg::of),
        getBash().map(StringArg::of),
        getCmdExe().map(StringArg::of),
        getOut(),
        getEnableSandboxingInGenrule(),
        getEnvironmentExpansionSeparator(),
        getSandboxProperties(),
        getAndroidTools());
  }
}
