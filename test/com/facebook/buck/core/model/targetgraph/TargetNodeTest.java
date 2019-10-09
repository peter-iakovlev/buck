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

package com.facebook.buck.core.model.targetgraph;

import static com.facebook.buck.core.cell.TestCellBuilder.createCellRoots;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.description.arg.CommonDescriptionArg;
import com.facebook.buck.core.description.arg.HasDeclaredDeps;
import com.facebook.buck.core.description.arg.Hint;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.ThrowingTargetConfigurationTransformer;
import com.facebook.buck.core.model.targetgraph.impl.TargetNodeFactory;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.impl.FakeBuildRule;
import com.facebook.buck.core.rules.knowntypes.KnownNativeRuleTypes;
import com.facebook.buck.core.select.impl.ThrowingSelectableConfigurationContext;
import com.facebook.buck.core.select.impl.ThrowingSelectorListResolver;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.SourceWithFlags;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.rules.coercer.ConstructorArgBuilder;
import com.facebook.buck.rules.coercer.ConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;
import org.junit.Test;

public class TargetNodeTest {

  public static final BuildTarget TARGET_THREE =
      BuildTargetFactory.newInstance("//example/path:three");

  @Test
  public void testIgnoreNonBuildTargetOrPathOrSourcePathArgument()
      throws NoSuchBuildTargetException {

    TargetNode<ExampleDescriptionArg> targetNode = createTargetNode(TARGET_THREE);

    assertTrue(targetNode.getExtraDeps().isEmpty());
    assertTrue(targetNode.getDeclaredDeps().isEmpty());
  }

  @Test
  public void testDepsAndPathsAreCollected() throws NoSuchBuildTargetException {
    ImmutableList<String> depsStrings =
        ImmutableList.of("//example/path:one", "//example/path:two");
    ImmutableSet<BuildTarget> depsTargets =
        depsStrings.stream()
            .map(BuildTargetFactory::newInstance)
            .collect(ImmutableSet.toImmutableSet());
    ImmutableMap<String, Object> rawNode =
        ImmutableMap.of(
            "name",
            TARGET_THREE.getShortName(),
            "deps",
            depsStrings,
            "sourcePaths",
            ImmutableList.of("//example/path:two", "//example/path:four", "MyClass.java"),
            "appleSource",
            "//example/path:five",
            "source",
            "AnotherClass.java");

    TargetNode<ExampleDescriptionArg> targetNode =
        createTargetNode(
            TARGET_THREE,
            depsTargets,
            rawNode,
            Sets.newHashSet(
                Paths.get("example/path/AnotherClass.java"),
                Paths.get("example/path/MyClass.java")));

    assertThat(
        targetNode.getInputs(),
        containsInAnyOrder(
            Paths.get("example/path/MyClass.java"), Paths.get("example/path/AnotherClass.java")));

    assertThat(
        targetNode.getExtraDeps(),
        containsInAnyOrder(
            BuildTargetFactory.newInstance("//example/path:two"),
            BuildTargetFactory.newInstance("//example/path:four"),
            BuildTargetFactory.newInstance("//example/path:five")));

    assertThat(
        targetNode.getDeclaredDeps(),
        containsInAnyOrder(
            BuildTargetFactory.newInstance("//example/path:one"),
            BuildTargetFactory.newInstance("//example/path:two")));
  }

  @Test
  public void targetsWithTheSameRelativePathButNotTheSameCellMightNotBeAbleToSeeEachOther() {

    ProjectFilesystem rootOne = FakeProjectFilesystem.createJavaOnlyFilesystem("/one");
    BuildTarget buildTargetOne = BuildTargetFactory.newInstance(rootOne.getRootPath(), "//foo:bar");
    TargetNode<ExampleDescriptionArg> targetNodeOne = createTargetNode(buildTargetOne);

    ProjectFilesystem rootTwo = FakeProjectFilesystem.createJavaOnlyFilesystem("/two");
    BuildTarget buildTargetTwo = BuildTargetFactory.newInstance(rootTwo.getRootPath(), "//foo:bar");
    TargetNode<ExampleDescriptionArg> targetNodeTwo = createTargetNode(buildTargetTwo);

    boolean isVisible = targetNodeOne.isVisibleTo(targetNodeTwo);

    assertThat(isVisible, is(false));
  }

  @Test
  public void invalidArgumentsThrowAnException() {
    ImmutableMap<String, Object> rawNode =
        ImmutableMap.of("name", TARGET_THREE.getShortName(), "cmd", "$(query_outputs '123')");

    try {
      createTargetNode(TARGET_THREE, ImmutableSet.of(), rawNode, Sets.newHashSet());
    } catch (HumanReadableException e) {
      assertEquals(
          "Cannot traverse attribute cmd of //example/path:three: Error parsing query: 123",
          e.getHumanReadableErrorMessage());
    }
  }

  @Test
  public void configurationDepsAreCopiedToTargetNode() {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    ExampleDescription description = new ExampleDescription();
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");
    BuildTarget configurationBuildTarget = BuildTargetFactory.newInstance("//config:bar");
    TargetNode<?> targetNode =
        new TargetNodeFactory(new DefaultTypeCoercerFactory())
            .createFromObject(
                description,
                createPopulatedConstructorArg(buildTarget, ImmutableMap.of("name", "bar")),
                filesystem,
                buildTarget,
                ImmutableSet.of(),
                ImmutableSortedSet.of(configurationBuildTarget),
                ImmutableSet.of(),
                ImmutableSet.of(),
                createCellRoots(filesystem));

    assertEquals(ImmutableSet.of(configurationBuildTarget), targetNode.getConfigurationDeps());
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractExampleDescriptionArg extends CommonDescriptionArg, HasDeclaredDeps {
    @Value.NaturalOrder
    ImmutableSortedSet<SourcePath> getSourcePaths();

    Optional<SourceWithFlags> getAppleSource();

    Optional<Path> getSource();

    Optional<String> getString();

    @Hint(isDep = false)
    Optional<BuildTarget> getTarget();

    Optional<StringWithMacros> getCmd();
  }

  public static class ExampleDescription
      implements DescriptionWithTargetGraph<ExampleDescriptionArg> {

    @Override
    public Class<ExampleDescriptionArg> getConstructorArgType() {
      return ExampleDescriptionArg.class;
    }

    @Override
    public BuildRule createBuildRule(
        BuildRuleCreationContextWithTargetGraph context,
        BuildTarget buildTarget,
        BuildRuleParams params,
        ExampleDescriptionArg args) {
      return new FakeBuildRule(buildTarget, context.getProjectFilesystem(), params);
    }
  }

  private static TargetNode<ExampleDescriptionArg> createTargetNode(BuildTarget buildTarget)
      throws NoSuchBuildTargetException {
    ImmutableMap<String, Object> rawNode =
        ImmutableMap.of(
            "name",
            buildTarget.getShortName(),
            "deps",
            ImmutableList.of(),
            "string",
            "//example/path:one",
            "target",
            "//example/path:two",
            "sourcePaths",
            ImmutableSortedSet.of());

    return createTargetNode(buildTarget, ImmutableSet.of(), rawNode, Sets.newHashSet());
  }

  private static TargetNode<ExampleDescriptionArg> createTargetNode(
      BuildTarget buildTarget,
      ImmutableSet<BuildTarget> declaredDeps,
      ImmutableMap<String, Object> rawNode,
      Set<Path> files)
      throws NoSuchBuildTargetException {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem(files);

    ExampleDescription description = new ExampleDescription();

    return new TargetNodeFactory(new DefaultTypeCoercerFactory())
        .createFromObject(
            description,
            createPopulatedConstructorArg(buildTarget, rawNode),
            filesystem,
            buildTarget,
            declaredDeps,
            ImmutableSortedSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            createCellRoots(filesystem));
  }

  private static ExampleDescriptionArg createPopulatedConstructorArg(
      BuildTarget buildTarget, Map<String, Object> instance) throws NoSuchBuildTargetException {
    DefaultTypeCoercerFactory coercerFactory = new DefaultTypeCoercerFactory();
    ConstructorArgMarshaller marshaller = new DefaultConstructorArgMarshaller(coercerFactory);
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    KnownNativeRuleTypes knownRuleTypes =
        KnownNativeRuleTypes.of(ImmutableList.of(new ExampleDescription()), ImmutableList.of());
    ConstructorArgBuilder<ExampleDescriptionArg> builder =
        knownRuleTypes.getConstructorArgBuilder(
            coercerFactory,
            knownRuleTypes.getRuleType("example"),
            ExampleDescriptionArg.class,
            buildTarget);
    try {
      return marshaller.populate(
          createCellRoots(projectFilesystem),
          projectFilesystem,
          new ThrowingSelectorListResolver(),
          new ThrowingTargetConfigurationTransformer(),
          new ThrowingSelectableConfigurationContext(),
          buildTarget,
          builder,
          ImmutableSet.builder(),
          ImmutableSet.builder(),
          instance);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
