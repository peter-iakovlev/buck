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

package com.facebook.buck.core.rules.configsetting;

import com.facebook.buck.core.description.arg.Hint;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.ConfigurationBuildTargets;
import com.facebook.buck.core.model.UnconfiguredBuildTargetView;
import com.facebook.buck.core.rules.config.ConfigurationRule;
import com.facebook.buck.core.rules.config.ConfigurationRuleArg;
import com.facebook.buck.core.rules.config.ConfigurationRuleDescription;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.immutables.value.Value;

/**
 * A description for {@code config_setting}.
 *
 * <p>This rule should be used to create conditions for {@code select} statements.
 *
 * <p>The {@code values} parameter is used to list configuration keys (configuration options from
 * {@code .buckconfig} in the form {@code section.option}) with expected values.
 *
 * <p>For example:
 *
 * <pre>
 *   config_setting(
 *      name = "a",
 *      values = {
 *        "section.option": "expected_value",
 *      }
 *   )
 * </pre>
 */
public class ConfigSettingDescription implements ConfigurationRuleDescription<ConfigSettingArg> {

  @Override
  public Class<ConfigSettingArg> getConstructorArgType() {
    return ConfigSettingArg.class;
  }

  @Override
  public ConfigurationRule createConfigurationRule(
      ConfigurationRuleResolver configurationRuleResolver,
      BuildTarget buildTarget,
      ConfigSettingArg arg) {
    return new ConfigSettingRule(
        buildTarget, arg.getValues(), ConfigurationBuildTargets.convert(arg.getConstraintValues()));
  }

  @Override
  public ImmutableSet<BuildTarget> getConfigurationDeps(ConfigSettingArg arg) {
    return ConfigurationBuildTargets.convert(arg.getConstraintValues());
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractConfigSettingArg extends ConfigurationRuleArg {
    @Hint(isConfigurable = false)
    String getName();

    @Value.NaturalOrder
    @Hint(isConfigurable = false)
    ImmutableSortedMap<String, String> getValues();

    @Value.NaturalOrder
    @Hint(isConfigurable = false)
    ImmutableSortedSet<UnconfiguredBuildTargetView> getConstraintValues();
  }
}
