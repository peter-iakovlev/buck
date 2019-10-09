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

package com.facebook.buck.features.apple.projectV2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.apple.AppleBinaryBuilder;
import com.facebook.buck.apple.AppleBundleBuilder;
import com.facebook.buck.apple.AppleBundleExtension;
import com.facebook.buck.apple.AppleLibraryBuilder;
import com.facebook.buck.apple.AppleTestBuilder;
import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.TestCellBuilder;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraphFactory;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.model.targetgraph.TestTargetGraphCreationResultFactory;
import com.facebook.buck.core.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableGroup.Linkage;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.features.apple.common.NullPathOutputPresenter;
import com.facebook.buck.features.apple.common.XcodeWorkspaceConfigDescription;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.keys.config.TestRuleKeyConfigurationFactory;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.timing.SettableFakeClock;
import com.facebook.buck.util.types.Either;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class XCodeProjectCommandHelperTest {

  private TargetNode<?> barLibNode;
  private TargetNode<?> fooLibNode;
  private TargetNode<?> fooBinBinaryNode;
  private TargetNode<?> fooBinNode;
  private TargetNode<?> bazLibNode;
  private TargetNode<?> bazTestNode;
  private TargetNode<?> fooTestNode;
  private TargetNode<?> fooBinTestNode;
  private TargetNode<?> quxBinNode;
  private TargetNode<?> workspaceNode;
  private TargetNode<?> workspaceExtraTestNode;
  private TargetNode<?> smallWorkspaceNode;

  private TargetGraph originalTargetGraph;

  @Before
  public void buildGraph() {
    assumeTrue(Platform.detect() == Platform.MACOS || Platform.detect() == Platform.LINUX);

    // Create the following dep tree:
    //
    // FooBin -has-test-> FooBinTest
    // |
    // V
    // FooLib -has-test-> FooLibTest
    // |                  |
    // V                  V
    // BarLib             BazLib -has-test-> BazLibTest
    // ^
    // |
    // QuxBin
    //
    // FooBin and BazLib and FooLibTest use "tests" to specify their tests.

    BuildTarget bazTestTarget = BuildTargetFactory.newInstance("//baz:xctest");
    BuildTarget fooBinTestTarget = BuildTargetFactory.newInstance("//foo:bin-xctest");

    BuildTarget barLibTarget = BuildTargetFactory.newInstance("//bar:lib");
    barLibNode = AppleLibraryBuilder.createBuilder(barLibTarget).build();

    BuildTarget bazLibTarget = BuildTargetFactory.newInstance("//baz:lib");
    bazLibNode =
        AppleLibraryBuilder.createBuilder(bazLibTarget)
            .setTests(ImmutableSortedSet.of(bazTestTarget))
            .build();

    BuildTarget fooTestTarget = BuildTargetFactory.newInstance("//foo:lib-xctest");
    fooTestNode =
        AppleTestBuilder.createBuilder(fooTestTarget)
            .setDeps(ImmutableSortedSet.of(bazLibTarget))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    BuildTarget fooLibTarget = BuildTargetFactory.newInstance("//foo:lib");
    fooLibNode =
        AppleLibraryBuilder.createBuilder(fooLibTarget)
            .setDeps(ImmutableSortedSet.of(barLibTarget))
            .setTests(ImmutableSortedSet.of(fooTestTarget))
            .build();

    BuildTarget fooBinBinaryTarget = BuildTargetFactory.newInstance("//foo:binbinary");
    fooBinBinaryNode =
        AppleBinaryBuilder.createBuilder(fooBinBinaryTarget)
            .setDeps(ImmutableSortedSet.of(fooLibTarget))
            .build();

    BuildTarget fooBinTarget = BuildTargetFactory.newInstance("//foo:bin");
    fooBinNode =
        AppleBundleBuilder.createBuilder(fooBinTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setBinary(fooBinBinaryTarget)
            .setTests(ImmutableSortedSet.of(fooBinTestTarget))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    bazTestNode =
        AppleTestBuilder.createBuilder(bazTestTarget)
            .setDeps(ImmutableSortedSet.of(bazLibTarget))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    fooBinTestNode =
        AppleTestBuilder.createBuilder(fooBinTestTarget)
            .setDeps(ImmutableSortedSet.of(fooBinTarget))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    BuildTarget quxBinTarget = BuildTargetFactory.newInstance("//qux:bin");
    quxBinNode =
        AppleBinaryBuilder.createBuilder(quxBinTarget)
            .setDeps(ImmutableSortedSet.of(barLibTarget))
            .build();

    BuildTarget workspaceExtraTestTarget = BuildTargetFactory.newInstance("//foo:extra-xctest");
    workspaceExtraTestNode =
        AppleTestBuilder.createBuilder(workspaceExtraTestTarget)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    BuildTarget workspaceTarget = BuildTargetFactory.newInstance("//foo:workspace");
    workspaceNode =
        XcodeWorkspaceConfigBuilder.createBuilder(workspaceTarget)
            .setWorkspaceName(Optional.of("workspace"))
            .setSrcTarget(Optional.of(fooBinTarget))
            .setExtraTests(ImmutableSortedSet.of(workspaceExtraTestTarget))
            .build();

    BuildTarget smallWorkspaceTarget = BuildTargetFactory.newInstance("//baz:small-workspace");
    smallWorkspaceNode =
        XcodeWorkspaceConfigBuilder.createBuilder(smallWorkspaceTarget)
            .setWorkspaceName(Optional.of("small-workspace"))
            .setSrcTarget(Optional.of(bazLibTarget))
            .build();

    originalTargetGraph =
        TargetGraphFactory.newInstance(
            barLibNode,
            fooLibNode,
            fooBinBinaryNode,
            fooBinNode,
            bazLibNode,
            bazTestNode,
            fooTestNode,
            fooBinTestNode,
            quxBinNode,
            workspaceExtraTestNode,
            workspaceNode,
            smallWorkspaceNode);
  }

  @Test
  public void testCreateTargetGraphWithoutTests() {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(),
            /* withTests = */ false,
            /* withDependenciesTests = */ false);

    assertEquals(
        ImmutableSortedSet.of(
            workspaceNode,
            fooBinNode,
            fooBinBinaryNode,
            fooLibNode,
            barLibNode,
            smallWorkspaceNode,
            bazLibNode,
            workspaceExtraTestNode),
        ImmutableSortedSet.copyOf(targetGraph.getNodes()));
  }

  @Test
  public void testCreateTargetGraphWithTests() {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(),
            /* withTests = */ true,
            /* withDependenciesTests */ true);

    assertEquals(
        ImmutableSortedSet.of(
            workspaceNode,
            fooBinNode,
            fooBinBinaryNode,
            fooLibNode,
            fooBinTestNode,
            fooTestNode,
            barLibNode,
            smallWorkspaceNode,
            bazLibNode,
            bazTestNode,
            workspaceExtraTestNode),
        ImmutableSortedSet.copyOf(targetGraph.getNodes()));
  }

  @Test
  public void testCreateTargetGraphForSliceWithoutTests() {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(workspaceNode.getBuildTarget()),
            /* withTests = */ false,
            /* withDependenciesTests */ false);

    assertEquals(
        ImmutableSortedSet.of(
            workspaceNode,
            fooBinNode,
            fooBinBinaryNode,
            fooLibNode,
            barLibNode,
            workspaceExtraTestNode),
        ImmutableSortedSet.copyOf(targetGraph.getNodes()));
  }

  @Test
  public void testCreateTargetGraphForSliceWithTests() {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(workspaceNode.getBuildTarget()),
            /* withTests = */ true,
            /* withDependenciesTests */ true);

    assertEquals(
        ImmutableSortedSet.of(
            workspaceNode,
            fooBinNode,
            fooBinBinaryNode,
            fooLibNode,
            fooBinTestNode,
            fooTestNode,
            barLibNode,
            bazLibNode,
            workspaceExtraTestNode),
        ImmutableSortedSet.copyOf(targetGraph.getNodes()));
  }

  @Test
  public void testSharedLibrariesToBundles() {
    BuildTarget sharedLibTarget = BuildTargetFactory.newInstance("//foo:shared");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo:bundle");
    BuildTarget workspaceTarget = BuildTargetFactory.newInstance("//foo:workspace");
    BuildTarget fooBinTarget = BuildTargetFactory.newInstance("//foo:bin");
    BuildTarget fooBinBinaryTarget = BuildTargetFactory.newInstance("//foo:binbinary");

    TargetNode<?> sharedLibNode =
        AppleLibraryBuilder.createBuilder(sharedLibTarget)
            .setPreferredLinkage(Linkage.SHARED)
            .build();
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(sharedLibTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info2.plist"))
            .build();
    TargetNode<?> fooBinBinaryNode =
        AppleBinaryBuilder.createBuilder(fooBinBinaryTarget)
            .setDeps(ImmutableSortedSet.of(sharedLibTarget))
            .build();
    TargetNode<?> fooBinNode =
        AppleBundleBuilder.createBuilder(fooBinTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setBinary(fooBinBinaryTarget)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();
    TargetNode<?> workspaceNode =
        XcodeWorkspaceConfigBuilder.createBuilder(workspaceTarget)
            .setWorkspaceName(Optional.of("workspace"))
            .setSrcTarget(Optional.of(fooBinTarget))
            .build();
    XcodeWorkspaceConfigBuilder.createBuilder(workspaceTarget)
        .setWorkspaceName(Optional.of("workspace"))
        .setSrcTarget(Optional.of(fooBinTarget))
        .build();

    TargetGraph originalTargetGraph =
        TargetGraphFactory.newInstance(
            sharedLibNode, bundleNode, fooBinBinaryNode, fooBinNode, workspaceNode);
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(workspaceNode.getBuildTarget()),
            /* withTests = */ false,
            /* withDependenciesTests */ false);

    ImmutableMap<BuildTarget, TargetNode<?>> sharedLibraryToBundle =
        ProjectGenerator.computeSharedLibrariesToBundles(
            ImmutableSet.of(sharedLibNode, bundleNode), targetGraph);
    assertTrue(sharedLibraryToBundle.containsKey(sharedLibTarget));
    assertTrue(sharedLibraryToBundle.containsValue(bundleNode));
    assertEquals(sharedLibraryToBundle.size(), 1);
  }

  @Test
  public void testCreateTargetGraphForSmallSliceWithoutTests() {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(smallWorkspaceNode.getBuildTarget()),
            /* withTests = */ false,
            /* withDependenciesTests */ false);

    assertEquals(
        ImmutableSortedSet.of(smallWorkspaceNode, bazLibNode),
        ImmutableSortedSet.copyOf(targetGraph.getNodes()));
  }

  @Test
  public void testCreateTargetGraphForSmallSliceWithTests() {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph,
            ImmutableSet.of(smallWorkspaceNode.getBuildTarget()),
            /* withTests = */ true,
            /* withDependenciesTests */ true);

    assertEquals(
        ImmutableSortedSet.of(smallWorkspaceNode, bazLibNode, bazTestNode),
        ImmutableSortedSet.copyOf(targetGraph.getNodes()));
  }

  @Test
  public void testTargetWithTests() throws IOException, InterruptedException {
    ImmutableList<XCodeProjectCommandHelper.Result> results =
        generateProjectsForTests(
            ImmutableSet.of(fooBinNode.getBuildTarget()),
            /* withTests = */ true,
            /* withDependenciesTests */ true);

    XCodeProjectCommandHelper.Result result = results.get(0);
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "bin-xctest");
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "lib-xctest");
  }

  private ImmutableList<XCodeProjectCommandHelper.Result> generateProjectsForTests(
      ImmutableSet<BuildTarget> passedInTargetsSet,
      boolean isWithTests,
      boolean isWithDependenciesTests)
      throws IOException, InterruptedException {
    return generateWorkspacesForTargets(
        originalTargetGraph, passedInTargetsSet, isWithTests, isWithDependenciesTests);
  }

  @Test
  public void testTargetWithoutDependenciesTests() throws IOException, InterruptedException {
    ImmutableList<XCodeProjectCommandHelper.Result> results =
        generateProjectsForTests(
            ImmutableSet.of(fooBinNode.getBuildTarget()),
            /* withTests = */ true,
            /* withDependenciesTests */ false);

    XCodeProjectCommandHelper.Result result = results.get(0);
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "bin-xctest");
    ProjectGeneratorTestUtils.assertTargetDoesNotExist(result.getProject(), "lib-xctest");
  }

  @Test
  public void testTargetWithoutTests() throws IOException, InterruptedException {
    ImmutableList<XCodeProjectCommandHelper.Result> results =
        generateProjectsForTests(
            ImmutableSet.of(fooBinNode.getBuildTarget()),
            /* withTests = */ false,
            /* withDependenciesTests */ false);

    XCodeProjectCommandHelper.Result result = results.get(0);
    ProjectGeneratorTestUtils.assertTargetDoesNotExist(result.getProject(), "bin-xctest");
    ProjectGeneratorTestUtils.assertTargetDoesNotExist(result.getProject(), "lib-xctest");
  }

  @Test
  public void testWorkspaceWithoutDependenciesTests() throws IOException, InterruptedException {
    ImmutableList<XCodeProjectCommandHelper.Result> results =
        generateProjectsForTests(
            ImmutableSet.of(workspaceNode.getBuildTarget()),
            /* withTests = */ true,
            /* withDependenciesTests */ false);

    XCodeProjectCommandHelper.Result result = results.get(0);
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "bin-xctest");
    ProjectGeneratorTestUtils.assertTargetDoesNotExist(result.getProject(), "lib-xctest");
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "extra-xctest");
  }

  @Test
  public void testWorkspaceWithoutExtraTestsWithoutDependenciesTests()
      throws IOException, InterruptedException {
    ImmutableList<XCodeProjectCommandHelper.Result> results =
        generateProjectsForTests(
            ImmutableSet.of(smallWorkspaceNode.getBuildTarget()),
            /* withTests = */ true,
            /* withDependenciesTests */ false);
    XCodeProjectCommandHelper.Result result = results.get(0);
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "lib");
    ProjectGeneratorTestUtils.assertTargetExists(result.getProject(), "xctest");
  }

  private static TargetGraph createTargetGraph(
      TargetGraph projectGraph,
      ImmutableSet<BuildTarget> passedInTargetsSet,
      boolean withTests,
      boolean withDependenciesTests) {
    ImmutableSet<BuildTarget> graphRoots;
    if (!passedInTargetsSet.isEmpty()) {
      graphRoots = passedInTargetsSet;
    } else {
      graphRoots =
          XCodeProjectCommandHelper.getRootsFromPredicate(
              projectGraph,
              node -> node.getDescription() instanceof XcodeWorkspaceConfigDescription);
    }

    ImmutableSet<BuildTarget> graphRootsOrSourceTargets =
        XCodeProjectCommandHelper.replaceWorkspacesWithSourceTargetsIfPossible(
            TestTargetGraphCreationResultFactory.create(projectGraph, graphRoots));

    Iterable<TargetNode<?>> associatedTests = ImmutableSet.of();
    if (withTests) {
      ImmutableSet<BuildTarget> explicitTests =
          XCodeProjectCommandHelper.getExplicitTestTargets(
              graphRootsOrSourceTargets,
              projectGraph,
              withDependenciesTests,
              FocusedTargetMatcher.noFocus());
      associatedTests = projectGraph.getAll(explicitTests);
    }

    Iterable<TargetNode<?>> projectRoots = projectGraph.getAll(graphRoots);

    return projectGraph.getSubgraph(Iterables.concat(projectRoots, associatedTests));
  }

  private static ImmutableList<XCodeProjectCommandHelper.Result> generateWorkspacesForTargets(
      TargetGraph originalTargetGraph,
      ImmutableSet<BuildTarget> passedInTargetsSet,
      boolean isWithTests,
      boolean isWithDependenciesTests)
      throws IOException, InterruptedException {
    TargetGraph targetGraph =
        createTargetGraph(
            originalTargetGraph, passedInTargetsSet, isWithTests, isWithDependenciesTests);

    Cell cell =
        new TestCellBuilder()
            .setFilesystem(new FakeProjectFilesystem(SettableFakeClock.DO_NOT_CARE))
            .build();
    return XCodeProjectCommandHelper.generateWorkspacesForTargets(
        BuckEventBusForTests.newInstance(),
        BuckPluginManagerFactory.createPluginManager(),
        cell,
        AppleProjectHelper.createDefaultBuckConfig(cell.getFilesystem()),
        TestRuleKeyConfigurationFactory.create(),
        MoreExecutors.newDirectExecutorService(),
        TestTargetGraphCreationResultFactory.create(targetGraph, passedInTargetsSet),
        ProjectGeneratorOptions.builder()
            .setShouldGenerateReadOnlyFiles(false)
            .setShouldIncludeTests(isWithTests)
            .setShouldIncludeDependenciesTests(isWithDependenciesTests)
            .setShouldForceLoadLinkWholeLibraries(false)
            .setShouldGenerateMissingUmbrellaHeader(false)
            .setShouldUseShortNamesForTargets(true)
            .build(),
        ImmutableSet.of(),
        FocusedTargetMatcher.noFocus(),
        new NullPathOutputPresenter(),
        Optional.empty());
  }
}
