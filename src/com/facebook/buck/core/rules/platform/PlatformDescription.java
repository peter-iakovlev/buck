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

package com.facebook.buck.core.rules.platform;

import com.facebook.buck.core.description.arg.ConstructorArg;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.ConfigurationBuildTargets;
import com.facebook.buck.core.model.UnconfiguredBuildTargetView;
import com.facebook.buck.core.rules.config.ConfigurationRule;
import com.facebook.buck.core.rules.config.ConfigurationRuleDescription;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.immutables.value.Value;

/**
 * A description for {@code platform}.
 *
 * <p>A platform is a set of constraints.
 *
 * <p>For example:
 *
 * <pre>
 *   platform(
 *      name = "platform",
 *      constraint_values = [
 *          ":constraint1",
 *          ":constraint2",
 *      ]
 *   )
 * </pre>
 */
public class PlatformDescription implements ConfigurationRuleDescription<PlatformArg> {

  @Override
  public Class<PlatformArg> getConstructorArgType() {
    return PlatformArg.class;
  }

  @Override
  public ConfigurationRule createConfigurationRule(
      ConfigurationRuleResolver configurationRuleResolver,
      BuildTarget buildTarget,
      PlatformArg arg) {
    return PlatformRule.of(
        buildTarget,
        arg.getName(),
        ConfigurationBuildTargets.convert(arg.getConstraintValues()),
        ConfigurationBuildTargets.convert(arg.getDeps()));
  }

  @Override
  public ImmutableSet<BuildTarget> getConfigurationDeps(PlatformArg arg) {
    return ImmutableSet.<BuildTarget>builder()
        .addAll(ConfigurationBuildTargets.convert(arg.getConstraintValues()))
        .addAll(ConfigurationBuildTargets.convert(arg.getDeps()))
        .build();
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractPlatformArg extends ConstructorArg {
    String getName();

    @Value.NaturalOrder
    ImmutableSortedSet<UnconfiguredBuildTargetView> getConstraintValues();

    @Value.NaturalOrder
    ImmutableSortedSet<UnconfiguredBuildTargetView> getDeps();
  }

  /** {@code platform} rule. */
  @BuckStyleImmutable
  @Value.Immutable(builder = false, copy = false)
  interface AbstractPlatformRule extends ConfigurationRule {
    @Value.Parameter
    @Override
    BuildTarget getBuildTarget();

    @Value.Parameter
    String getName();

    @Value.NaturalOrder
    @Value.Parameter
    ImmutableSortedSet<BuildTarget> getConstrainValues();

    @Value.Parameter
    @Value.NaturalOrder
    ImmutableSortedSet<BuildTarget> getDeps();
  }
}
