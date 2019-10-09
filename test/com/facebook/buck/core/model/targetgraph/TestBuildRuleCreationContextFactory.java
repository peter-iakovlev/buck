/*
 * Copyright 2018-present Facebook, Inc.
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

package com.facebook.buck.core.model.targetgraph;

import com.facebook.buck.core.cell.TestCellBuilder;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.ImmutableBuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.config.registry.impl.ConfigurationRuleRegistryFactory;
import com.facebook.buck.core.rules.providers.collect.impl.LegacyProviderInfoCollectionImpl;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.toolchain.impl.ToolchainProviderBuilder;
import com.facebook.buck.io.filesystem.ProjectFilesystem;

public class TestBuildRuleCreationContextFactory {

  public static BuildRuleCreationContextWithTargetGraph create(
      ActionGraphBuilder graphBuilder, ProjectFilesystem projectFilesystem) {
    return create(TargetGraph.EMPTY, graphBuilder, projectFilesystem);
  }

  public static BuildRuleCreationContextWithTargetGraph create(
      TargetGraph targetGraph,
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem) {
    return create(
        targetGraph, graphBuilder, projectFilesystem, new ToolchainProviderBuilder().build());
  }

  public static BuildRuleCreationContextWithTargetGraph create(
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      ToolchainProvider toolchainProvider) {
    return create(TargetGraph.EMPTY, graphBuilder, projectFilesystem, toolchainProvider);
  }

  public static BuildRuleCreationContextWithTargetGraph create(
      TargetGraph targetGraph,
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      ToolchainProvider toolchainProvider) {
    return ImmutableBuildRuleCreationContextWithTargetGraph.of(
        targetGraph,
        graphBuilder,
        projectFilesystem,
        TestCellBuilder.createCellRoots(projectFilesystem),
        toolchainProvider,
        ConfigurationRuleRegistryFactory.createRegistry(targetGraph),
        LegacyProviderInfoCollectionImpl.of());
  }
}
