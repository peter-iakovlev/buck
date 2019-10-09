/*
 * Copyright 2015-present Facebook, Inc.
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
package com.facebook.buck.features.project.intellij.lang.android;

import com.facebook.buck.android.AndroidLibraryDescription;
import com.facebook.buck.android.AndroidLibraryGraphEnhancer;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.features.project.intellij.JavaLanguageLevelHelper;
import com.facebook.buck.features.project.intellij.ModuleBuildContext;
import com.facebook.buck.features.project.intellij.aggregation.AggregationContext;
import com.facebook.buck.features.project.intellij.lang.java.JavaLibraryRuleHelper;
import com.facebook.buck.features.project.intellij.model.IjLibrary;
import com.facebook.buck.features.project.intellij.model.IjModuleAndroidFacet;
import com.facebook.buck.features.project.intellij.model.IjModuleFactoryResolver;
import com.facebook.buck.features.project.intellij.model.IjModuleType;
import com.facebook.buck.features.project.intellij.model.IjProjectConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

public class AndroidLibraryModuleRule extends AndroidModuleRule<AndroidLibraryDescription.CoreArg> {

  private final AndroidManifestParser androidManifestParser;

  public AndroidLibraryModuleRule(
      ProjectFilesystem projectFilesystem,
      IjModuleFactoryResolver moduleFactoryResolver,
      IjProjectConfig projectConfig) {
    super(projectFilesystem, moduleFactoryResolver, projectConfig, AndroidProjectType.LIBRARY);
    androidManifestParser = new AndroidManifestParser(projectFilesystem);
  }

  @Override
  public Class<? extends DescriptionWithTargetGraph<?>> getDescriptionClass() {
    return AndroidLibraryDescription.class;
  }

  @Override
  public void apply(
      TargetNode<AndroidLibraryDescription.CoreArg> target, ModuleBuildContext context) {
    super.apply(target, context);
    addDepsAndSources(target, true /* wantsPackagePrefix */, context);
    JavaLibraryRuleHelper.addCompiledShadowIfNeeded(projectConfig, target, context);
    JavaLibraryRuleHelper.addNonSourceBuildTargets(target, context);
    Optional<Path> dummyRDotJavaClassPath = moduleFactoryResolver.getDummyRDotJavaPath(target);
    if (dummyRDotJavaClassPath.isPresent()) {
      BuildTarget dummyRDotJavaTarget =
          target.getBuildTarget().withFlavors(AndroidLibraryGraphEnhancer.DUMMY_R_DOT_JAVA_FLAVOR);
      IjLibrary extraLibraryDependency =
          IjLibrary.builder()
              .setBinaryJars(ImmutableSet.of(dummyRDotJavaClassPath.get()))
              .setTargets(ImmutableSet.of(dummyRDotJavaTarget))
              .setName(dummyRDotJavaTarget.getFullyQualifiedName())
              .build();
      context.addExtraLibraryDependency(extraLibraryDependency);
      context.addExtraModuleDependency(dummyRDotJavaClassPath.get());
    }

    context.setJavaLanguageLevel(JavaLanguageLevelHelper.getLanguageLevel(projectConfig, target));

    IjModuleAndroidFacet.Builder builder = context.getOrCreateAndroidFacetBuilder();
    Optional<Path> manifestPath = moduleFactoryResolver.getLibraryAndroidManifestPath(target);
    manifestPath.ifPresent(builder::addManifestPaths);

    if (manifestPath.isPresent()) {
      Path projectManifestPath = projectFilesystem.getPathForRelativePath(manifestPath.get());
      androidManifestParser
          .parseMinSdkVersion(projectManifestPath)
          .ifPresent(builder::addMinSdkVersions);
    }

    context.setCompilerOutputPath(moduleFactoryResolver.getCompilerOutputPath(target));
  }

  @Override
  public void applyDuringAggregation(
      AggregationContext context, TargetNode<AndroidLibraryDescription.CoreArg> targetNode) {
    super.applyDuringAggregation(context, targetNode);
    JavaLibraryRuleHelper.addLanguageAggregationKeyIfNeeded(projectConfig, targetNode, context);
  }

  @Override
  public IjModuleType detectModuleType(TargetNode<AndroidLibraryDescription.CoreArg> targetNode) {
    return IjModuleType.ANDROID_MODULE;
  }
}
