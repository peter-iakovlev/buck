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

package com.facebook.buck.rules.modern.config;

import com.facebook.buck.command.config.BuildBuckConfig;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.exceptions.HumanReadableException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/** Parses the values of a buckconfig section into a {@link ModernBuildRuleStrategyConfig}. */
public class ModernBuildRuleStrategyConfigFromSection implements ModernBuildRuleStrategyConfig {
  private final BuckConfig delegate;
  private final String section;

  public ModernBuildRuleStrategyConfigFromSection(BuckConfig delegate, String section) {
    this.delegate = delegate;
    this.section = section;
  }

  @Override
  public ModernBuildRuleBuildStrategy getBuildStrategy(
      boolean remoteExecutionAutoEnabled, boolean forceDisableRemoteExecution) {
    if (forceDisableRemoteExecution) {
      return ModernBuildRuleBuildStrategy.NONE;
    }

    return delegate
        .getEnum(section, "strategy", ModernBuildRuleBuildStrategy.class)
        // If Remote Execution was auto enabled, and no explicit strategy was set, then use the
        // experimental_strategy setting as the ModernBuildRuleBuildStrategy for this build.
        .orElse(
            (section.equals(AbstractModernBuildRuleConfig.SECTION) && remoteExecutionAutoEnabled)
                ? delegate
                    .getEnum(section, "experimental_strategy", ModernBuildRuleBuildStrategy.class)
                    .orElse(ModernBuildRuleBuildStrategy.DEFAULT)
                : ModernBuildRuleBuildStrategy.DEFAULT);
  }

  @Override
  public HybridLocalBuildStrategyConfig getHybridLocalConfig() {
    int localDelegateJobs;
    int localJobs = delegate.getView(BuildBuckConfig.class).getNumThreads();
    OptionalInt localJobsConfig = delegate.getInteger(section, "local_jobs");
    Optional<Float> localJobsRatioConfig = delegate.getFloat(section, "local_jobs_ratio");
    if (localJobsRatioConfig.isPresent()) {
      localJobs = (int) Math.ceil(localJobs * localJobsRatioConfig.get());
    } else if (localJobsConfig.isPresent()) {
      localJobs = localJobsConfig.getAsInt();
    }
    Optional<Float> localDelegateJobsRatioConfig =
        delegate.getFloat(section, "local_delegate_jobs_ratio");
    if (localDelegateJobsRatioConfig.isPresent()) {
      localDelegateJobs = (int) Math.ceil(localJobs * localDelegateJobsRatioConfig.get());
    } else {
      localDelegateJobs = localJobs;
    }

    int remoteJobs =
        delegate.getInteger(section, "delegate_jobs").orElseThrow(requires("delegate_jobs"));
    String delegateFlavor =
        delegate.getValue(section, "delegate").orElseThrow(requires("delegate"));
    ModernBuildRuleStrategyConfig delegate = getFlavoredStrategyConfig(delegateFlavor);
    return new HybridLocalBuildStrategyConfig(localJobs, localDelegateJobs, remoteJobs, delegate);
  }

  private Supplier<HumanReadableException> requires(String key) {
    return () ->
        new HumanReadableException(
            "hybrid_local strategy requires %s configuration (in %s section).", key, section);
  }

  public ModernBuildRuleStrategyConfig getFlavoredStrategyConfig(String flavor) {
    return new ModernBuildRuleStrategyConfigFromSection(
        delegate, String.format("%s#%s", ModernBuildRuleConfig.SECTION, flavor));
  }
}
