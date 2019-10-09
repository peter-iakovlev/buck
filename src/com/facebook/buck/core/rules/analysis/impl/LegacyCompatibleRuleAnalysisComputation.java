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
package com.facebook.buck.core.rules.analysis.impl;

import com.facebook.buck.core.description.BaseDescription;
import com.facebook.buck.core.description.RuleDescription;
import com.facebook.buck.core.description.arg.ConstructorArg;
import com.facebook.buck.core.graph.transformation.ComputationEnvironment;
import com.facebook.buck.core.graph.transformation.GraphComputation;
import com.facebook.buck.core.graph.transformation.model.ComputationIdentifier;
import com.facebook.buck.core.graph.transformation.model.ComputeKey;
import com.facebook.buck.core.graph.transformation.model.ComputeResult;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.ImmutableProviderCreationContext;
import com.facebook.buck.core.rules.ProviderCreationContext;
import com.facebook.buck.core.rules.actions.ActionCreationException;
import com.facebook.buck.core.rules.analysis.RuleAnalysisException;
import com.facebook.buck.core.rules.analysis.RuleAnalysisKey;
import com.facebook.buck.core.rules.analysis.RuleAnalysisResult;
import com.facebook.buck.core.rules.providers.collect.ProviderInfoCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * The {@link GraphComputation} for performing the target graph to provider and action graph
 * transformation, with legacy compatible behaviour where we delegate to the {@link
 * com.facebook.buck.core.rules.DescriptionWithTargetGraph#createProviders(ProviderCreationContext,
 * BuildTarget, ConstructorArg)}.
 */
public class LegacyCompatibleRuleAnalysisComputation
    implements GraphComputation<RuleAnalysisKey, RuleAnalysisResult> {

  private final RuleAnalysisComputation delegate;
  private final TargetGraph targetGraph;

  public LegacyCompatibleRuleAnalysisComputation(
      RuleAnalysisComputation delegate, TargetGraph targetGraph) {
    this.delegate = delegate;
    this.targetGraph = targetGraph;
  }

  @Override
  public ComputationIdentifier<RuleAnalysisResult> getIdentifier() {
    return RuleAnalysisKey.IDENTIFIER;
  }

  @Override
  public RuleAnalysisResult transform(RuleAnalysisKey key, ComputationEnvironment env)
      throws ActionCreationException, RuleAnalysisException {
    TargetNode<?> targetNode = targetGraph.get(key.getBuildTarget());
    BaseDescription<?> description = targetNode.getDescription();
    if (description instanceof RuleDescription) {
      return delegate.transform(key, env);
    } else if (description instanceof DescriptionWithTargetGraph) {
      return computeLegacyProviders(key, env, targetNode);
    }

    throw new IllegalStateException(
        String.format("Unknown Description type %s", description.getClass()));
  }

  @Override
  public ImmutableSet<? extends ComputeKey<? extends ComputeResult>> discoverDeps(
      RuleAnalysisKey key, ComputationEnvironment env) {
    return delegate.discoverDeps(key, env);
  }

  @Override
  public ImmutableSet<? extends ComputeKey<? extends ComputeResult>> discoverPreliminaryDeps(
      RuleAnalysisKey key) {
    return delegate.discoverPreliminaryDeps(key);
  }

  private <T extends ConstructorArg> RuleAnalysisResult computeLegacyProviders(
      RuleAnalysisKey key, ComputationEnvironment env, TargetNode<T> targetNode) {
    DescriptionWithTargetGraph<T> description =
        (DescriptionWithTargetGraph<T>) targetNode.getDescription();
    ProviderInfoCollection providerInfoCollection =
        description.createProviders(
            new ImmutableProviderCreationContext(
                env.getDeps(RuleAnalysisKey.IDENTIFIER).values().stream()
                    .collect(
                        ImmutableMap.toImmutableMap(
                            RuleAnalysisResult::getBuildTarget,
                            RuleAnalysisResult::getProviderInfos)),
                targetNode.getFilesystem()),
            key.getBuildTarget(),
            targetNode.getConstructorArg());

    return ImmutableLegacyProviderRuleAnalysisResult.of(
        key.getBuildTarget(), providerInfoCollection);
  }
}
