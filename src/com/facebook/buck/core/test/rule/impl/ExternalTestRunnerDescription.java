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

import com.facebook.buck.core.description.arg.CommonDescriptionArg;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.tool.BinaryBuildRule;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * A rule for specifying external test runners. This rule does nothing except propagate a binary
 * that acts as the actual test runner.
 */
public class ExternalTestRunnerDescription
    implements DescriptionWithTargetGraph<ExternalTestRunnerDescriptionArg> {
  @Override
  public Class<ExternalTestRunnerDescriptionArg> getConstructorArgType() {
    return ExternalTestRunnerDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      ExternalTestRunnerDescriptionArg args) {
    BuildRule rule = context.getActionGraphBuilder().requireRule(args.getBinary());
    Preconditions.checkState(
        rule instanceof BinaryBuildRule,
        "external_test_runner should have one dependency that points to a binary");
    return new ExternalTestRunner(
        buildTarget, context.getProjectFilesystem(), (BinaryBuildRule) rule);
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractExternalTestRunnerDescriptionArg extends CommonDescriptionArg {
    BuildTarget getBinary();
  }
}
