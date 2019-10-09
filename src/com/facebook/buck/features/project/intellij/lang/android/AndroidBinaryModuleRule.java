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

import com.facebook.buck.android.AndroidBinaryDescription;
import com.facebook.buck.android.AndroidBinaryDescriptionArg;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.features.project.intellij.ModuleBuildContext;
import com.facebook.buck.features.project.intellij.model.DependencyType;
import com.facebook.buck.features.project.intellij.model.IjModuleAndroidFacet;
import com.facebook.buck.features.project.intellij.model.IjModuleFactoryResolver;
import com.facebook.buck.features.project.intellij.model.IjModuleType;
import com.facebook.buck.features.project.intellij.model.IjProjectConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import java.nio.file.Path;
import java.util.Optional;

public class AndroidBinaryModuleRule extends AndroidModuleRule<AndroidBinaryDescriptionArg> {

  private final AndroidManifestParser androidManifestParser;

  public AndroidBinaryModuleRule(
      ProjectFilesystem projectFilesystem,
      IjModuleFactoryResolver moduleFactoryResolver,
      IjProjectConfig projectConfig) {
    super(projectFilesystem, moduleFactoryResolver, projectConfig, AndroidProjectType.APP);
    androidManifestParser = new AndroidManifestParser(projectFilesystem);
  }

  @Override
  public Class<? extends DescriptionWithTargetGraph<?>> getDescriptionClass() {
    return AndroidBinaryDescription.class;
  }

  @Override
  public void apply(TargetNode<AndroidBinaryDescriptionArg> target, ModuleBuildContext context) {
    super.apply(target, context);
    context.addDeps(target.getBuildDeps(), DependencyType.PROD);

    IjModuleAndroidFacet.Builder androidFacetBuilder = context.getOrCreateAndroidFacetBuilder();
    androidFacetBuilder.setProguardConfigPath(moduleFactoryResolver.getProguardConfigPath(target));

    Path manifestPath = moduleFactoryResolver.getAndroidManifestPath(target);
    androidFacetBuilder.addManifestPaths(manifestPath);

    Optional<Integer> targetMinSdkVersion =
        target.getConstructorArg().getManifestEntries().getMinSdkVersion();
    if (targetMinSdkVersion.isPresent()) {
      androidFacetBuilder.addMinSdkVersions(targetMinSdkVersion.get().toString());
    } else {
      Path projectManifestPath = projectFilesystem.getPathForRelativePath(manifestPath);
      androidManifestParser
          .parseMinSdkVersion(projectManifestPath)
          .ifPresent(androidFacetBuilder::addMinSdkVersions);
    }
  }

  @Override
  public IjModuleType detectModuleType(TargetNode<AndroidBinaryDescriptionArg> targetNode) {
    return IjModuleType.ANDROID_MODULE;
  }
}
