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

package com.facebook.buck.features.project.intellij;

import com.facebook.buck.android.AndroidPrebuiltAarDescription;
import com.facebook.buck.android.AndroidPrebuiltAarDescriptionArg;
import com.facebook.buck.android.UnzipAar;
import com.facebook.buck.core.description.arg.CommonDescriptionArg;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.features.project.intellij.model.IjLibrary;
import com.facebook.buck.features.project.intellij.model.IjLibraryFactory;
import com.facebook.buck.features.project.intellij.model.IjLibraryFactoryResolver;
import com.facebook.buck.jvm.java.PrebuiltJarDescription;
import com.facebook.buck.jvm.java.PrebuiltJarDescriptionArg;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Filters out all of the targets which can be represented as IntelliJ prebuilts from the set of
 * TargetNodes and allows resolving those as dependencies of modules.
 */
class DefaultIjLibraryFactory extends IjLibraryFactory {

  /** Rule describing how to create a {@link IjLibrary} from a {@link TargetNode}. */
  private interface IjLibraryRule {
    void applyRule(TargetNode<?> targetNode, IjLibrary.Builder library);
  }

  /**
   * Rule describing how to create a {@link IjLibrary} from a {@link TargetNode}.
   *
   * @param <T> the type of the TargetNode.
   */
  abstract class TypedIjLibraryRule<T extends CommonDescriptionArg> implements IjLibraryRule {
    abstract Class<? extends DescriptionWithTargetGraph<?>> getDescriptionClass();

    abstract void apply(TargetNode<T> targetNode, IjLibrary.Builder library);

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void applyRule(TargetNode<?> targetNode, IjLibrary.Builder library) {
      apply((TargetNode) targetNode, library);
    }
  }

  private Map<Class<? extends DescriptionWithTargetGraph<?>>, IjLibraryRule> libraryRuleIndex =
      new HashMap<>();
  private Set<String> uniqueLibraryNamesSet = new HashSet<>();
  private IjLibraryFactoryResolver libraryFactoryResolver;
  private Map<TargetNode<?>, Optional<IjLibrary>> libraryCache;

  public DefaultIjLibraryFactory(IjLibraryFactoryResolver libraryFactoryResolver) {
    this.libraryFactoryResolver = libraryFactoryResolver;

    addToIndex(new AndroidPrebuiltAarLibraryRule());
    addToIndex(new PrebuiltJarLibraryRule());

    libraryCache = new HashMap<>();
  }

  private void addToIndex(TypedIjLibraryRule<?> rule) {
    Preconditions.checkArgument(!libraryRuleIndex.containsKey(rule.getDescriptionClass()));
    libraryRuleIndex.put(rule.getDescriptionClass(), rule);
  }

  @Override
  public Optional<IjLibrary> getLibrary(TargetNode<?> target) {
    Optional<IjLibrary> library = libraryCache.get(target);
    if (library == null) {
      library = createLibrary(target);
      libraryCache.put(target, library);
    }
    return library;
  }

  private Optional<IjLibraryRule> getRule(TargetNode<?> targetNode) {
    IjLibraryRule rule = libraryRuleIndex.get(targetNode.getDescription().getClass());
    if (rule == null) {
      rule =
          libraryFactoryResolver
              .getPathIfJavaLibrary(targetNode)
              .map(libraryFactoryResolver::getPath)
              .map(JavaLibraryRule::new)
              .orElse(null);
    }
    return Optional.ofNullable(rule);
  }

  private Optional<IjLibrary> createLibrary(TargetNode<?> targetNode) {
    return getRule(targetNode)
        .map(
            rule -> {
              String libraryName = Util.intelliJLibraryName(targetNode.getBuildTarget());
              Preconditions.checkState(
                  !uniqueLibraryNamesSet.contains(libraryName),
                  "Trying to use the same library name for different targets.");

              IjLibrary.Builder libraryBuilder = IjLibrary.builder();
              rule.applyRule(targetNode, libraryBuilder);
              libraryBuilder.setName(libraryName);
              libraryBuilder.setTargets(ImmutableSet.of(targetNode.getBuildTarget()));
              return libraryBuilder.build();
            });
  }

  private static class JavaLibraryRule implements IjLibraryRule {
    private final Path binaryJarPath;

    public JavaLibraryRule(Path binaryJarPath) {
      this.binaryJarPath = binaryJarPath;
    }

    @Override
    public void applyRule(TargetNode<?> targetNode, IjLibrary.Builder library) {
      library.addBinaryJars(binaryJarPath);
    }
  }

  private class AndroidPrebuiltAarLibraryRule
      extends TypedIjLibraryRule<AndroidPrebuiltAarDescriptionArg> {

    @Override
    public Class<? extends DescriptionWithTargetGraph<?>> getDescriptionClass() {
      return AndroidPrebuiltAarDescription.class;
    }

    @Override
    public void apply(
        TargetNode<AndroidPrebuiltAarDescriptionArg> targetNode, IjLibrary.Builder library) {
      AndroidPrebuiltAarDescriptionArg arg = targetNode.getConstructorArg();
      arg.getSourceJar()
          .ifPresent(
              sourcePath -> library.addSourceJars(libraryFactoryResolver.getPath(sourcePath)));
      arg.getJavadocUrl().ifPresent(library::addJavadocUrls);

      Path aarUnpackPath =
          BuildTargetPaths.getScratchPath(
              targetNode.getFilesystem(),
              targetNode
                  .getBuildTarget()
                  .withFlavors(AndroidPrebuiltAarDescription.AAR_UNZIP_FLAVOR),
              UnzipAar.getAarUnzipPathFormat());

      // Based on https://developer.android.com/studio/projects/android-library.html#aar-contents,
      // the AAR library is required to have a resources folder.
      library.addClassPaths(Paths.get(aarUnpackPath.toString(), "res"));
      library.addBinaryJars(Paths.get(aarUnpackPath.toString(), "classes.jar"));
    }
  }

  private class PrebuiltJarLibraryRule extends TypedIjLibraryRule<PrebuiltJarDescriptionArg> {

    @Override
    public Class<? extends DescriptionWithTargetGraph<?>> getDescriptionClass() {
      return PrebuiltJarDescription.class;
    }

    @Override
    public void apply(TargetNode<PrebuiltJarDescriptionArg> targetNode, IjLibrary.Builder library) {
      PrebuiltJarDescriptionArg arg = targetNode.getConstructorArg();
      library.addBinaryJars(libraryFactoryResolver.getPath(arg.getBinaryJar()));
      if (!(arg.getBinaryJar() instanceof PathSourcePath)) {
        // If the input jar isn't a direct path, then it must be generated by another rule,
        // In this case we need the output path of this library so that a cache hit on the
        // PrebuiltJar directly will also resolve.
        library.addBinaryJars(
            libraryFactoryResolver.getPath(
                DefaultBuildTargetSourcePath.of(targetNode.getBuildTarget())));
      }
      arg.getSourceJar()
          .ifPresent(sourceJar -> library.addSourceJars(libraryFactoryResolver.getPath(sourceJar)));
      arg.getJavadocUrl().ifPresent(library::addJavadocUrls);
    }
  }
}
