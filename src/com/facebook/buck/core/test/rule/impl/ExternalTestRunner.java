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

package com.facebook.buck.core.test.rule.impl;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.impl.NoopBuildRule;
import com.facebook.buck.core.rules.tool.BinaryBuildRule;
import com.facebook.buck.io.filesystem.ProjectFilesystem;

/**
 * A generic test runner that wraps a binary which will run the test. Does not need to be compiled
 * with an associated test.
 */
public final class ExternalTestRunner extends NoopBuildRule {

  private final BinaryBuildRule binaryBuildRule;

  public ExternalTestRunner(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BinaryBuildRule binaryBuildRule) {
    super(buildTarget, projectFilesystem);
    this.binaryBuildRule = binaryBuildRule;
  }

  public BinaryBuildRule getBinary() {
    return binaryBuildRule;
  }
}
