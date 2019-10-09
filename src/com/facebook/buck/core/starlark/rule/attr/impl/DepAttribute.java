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
package com.facebook.buck.core.starlark.rule.attr.impl;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.parser.buildtargetparser.ParsingUnconfiguredBuildTargetViewFactory;
import com.facebook.buck.core.rules.providers.Provider;
import com.facebook.buck.core.rules.providers.collect.ProviderInfoCollection;
import com.facebook.buck.core.starlark.rule.attr.Attribute;
import com.facebook.buck.core.starlark.rule.attr.PostCoercionTransform;
import com.facebook.buck.core.starlark.rule.data.SkylarkDependency;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.rules.coercer.BuildTargetTypeCoercer;
import com.facebook.buck.rules.coercer.TypeCoercer;
import com.facebook.buck.rules.coercer.UnconfiguredBuildTargetTypeCoercer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;

/** Represents a single dependency. This is exposed to users as a {@link ProviderInfoCollection} */
@BuckStyleValue
public abstract class DepAttribute extends Attribute<BuildTarget> {

  private static final TypeCoercer<BuildTarget> coercer =
      new BuildTargetTypeCoercer(
          new UnconfiguredBuildTargetTypeCoercer(new ParsingUnconfiguredBuildTargetViewFactory()));

  @Override
  public abstract Object getPreCoercionDefaultValue();

  @Override
  public abstract String getDoc();

  @Override
  public abstract boolean getMandatory();

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("<attr.dep>");
  }

  @Override
  public TypeCoercer<BuildTarget> getTypeCoercer() {
    return coercer;
  }

  public abstract ImmutableList<Provider<?>> getProviders();

  @Override
  public PostCoercionTransform<ImmutableMap<BuildTarget, ProviderInfoCollection>, SkylarkDependency>
      getPostCoercionTransform() {
    return this::postCoercionTransform;
  }

  private SkylarkDependency postCoercionTransform(
      Object dep, ImmutableMap<BuildTarget, ProviderInfoCollection> deps) {
    SkylarkDependency dependency =
        SkylarkDependencyResolver.getDependencyForTargetFromDeps(dep, deps);
    validateProvidersPresent(getProviders(), (BuildTarget) dep, dependency.getProviderInfos());
    return dependency;
  }
}
