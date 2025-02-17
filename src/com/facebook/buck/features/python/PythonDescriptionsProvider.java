/*
 * Copyright 2017-present Facebook, Inc.
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

package com.facebook.buck.features.python;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.description.Description;
import com.facebook.buck.core.description.DescriptionCreationContext;
import com.facebook.buck.core.model.targetgraph.DescriptionProvider;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.cxx.config.CxxBuckConfig;
import java.util.Arrays;
import java.util.Collection;
import org.pf4j.Extension;

@Extension
public class PythonDescriptionsProvider implements DescriptionProvider {

  @Override
  public Collection<Description<?>> getDescriptions(DescriptionCreationContext context) {
    BuckConfig buckConfig = context.getBuckConfig();
    CxxBuckConfig cxxBuckConfig = new CxxBuckConfig(buckConfig);
    PythonBuckConfig pyConfig = new PythonBuckConfig(buckConfig);
    ToolchainProvider toolchainProvider = context.getToolchainProvider();

    PythonBinaryDescription pythonBinaryDescription =
        new PythonBinaryDescription(toolchainProvider, pyConfig, cxxBuckConfig);

    return Arrays.asList(
        pythonBinaryDescription,
        new PrebuiltPythonLibraryDescription(),
        new PythonLibraryDescription(toolchainProvider),
        new PythonTestDescription(
            toolchainProvider, pythonBinaryDescription, pyConfig, cxxBuckConfig),
        new PythonTestRunnerDescription(),
        new CxxPythonExtensionDescription(toolchainProvider, cxxBuckConfig));
  }
}
