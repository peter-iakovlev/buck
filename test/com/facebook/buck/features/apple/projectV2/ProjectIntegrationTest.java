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

package com.facebook.buck.features.apple.projectV2;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.apple.AppleConfig;
import com.facebook.buck.apple.AppleNativeIntegrationTestUtils;
import com.facebook.buck.apple.toolchain.ApplePlatform;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.BuckBuildLog;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProjectIntegrationTest {
  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Before
  public void setUp() {
    assumeTrue(Platform.detect() == Platform.MACOS || Platform.detect() == Platform.LINUX);
  }

  @Test
  public void testBuckProjectGeneratedSchemeOnlyIncludesDependenciesWithoutTests()
      throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "project_generated_scheme_only_includes_dependencies");

    ProcessResult result =
        workspace.runBuckCommand(
            "project", "--without-tests", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectGeneratedSchemeIncludesTestsAndDependencies() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "project_generated_scheme_includes_tests_and_dependencies");

    ProcessResult result =
        workspace.runBuckCommand("project", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectGeneratedSchemeIncludesTestsAndDependenciesInADifferentBuckFile()
      throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(
            this,
            "project_generated_scheme_includes_tests_and_dependencies_in_a_different_buck_file");
    ProcessResult result =
        workspace.runBuckCommand("project", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectGeneratedSchemesDoNotIncludeOtherTests() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "project_generated_schemes_do_not_include_other_tests");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void generatingAllWorkspacesWillNotIncludeAllProjectsInEachOfThem() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(
            this, "generating_all_workspaces_will_not_include_all_projects_in_each_of_them");
    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void schemeWithActionConfigNames() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "scheme_with_action_config_names");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void schemeWithExtraTests() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "scheme_with_extra_tests");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void schemeWithExtraTestsWithoutSrcTarget() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "scheme_with_extra_tests_without_src_target");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void generatingCombinedProject() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "generating_combined_project");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--combined-project",
            "--without-tests",
            "//Apps:workspace",
            "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void generatingRootDirectoryProject() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "generating_root_directory_project");

    ProcessResult result = workspace.runBuckCommand("project", "//:bundle");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void generatingCombinedProjectWithTests() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "generating_combined_project_with_tests");

    ProcessResult result =
        workspace.runBuckCommand(
            "project", "--combined-project", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testGeneratesWorkspaceFromBundle() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//bin:app");
    result.assertSuccess();
    Files.exists(workspace.resolve("bin/app.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("bin/bin.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratesWorkspaceFromLibrary() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//lib:lib");
    result.assertSuccess();
    Files.exists(workspace.resolve("lib/lib.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("lib/lib.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratesWorkspaceFromBinary() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//bin:bin");
    result.assertSuccess();
    Files.exists(workspace.resolve("bin/bin.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("bin/bin.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testAttemptingToGenerateWorkspaceFromResourceTargetIsABuildError()
      throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult processResult = workspace.runBuckCommand("project", "//res:res");
    processResult.assertFailure();
    assertThat(
        processResult.getStderr(),
        containsString(
            "//res:res must be a xcode_workspace_config, apple_binary, apple_bundle, or apple_library"));
  }

  @Test
  public void testGeneratingProjectWithTargetUsingGenruleSourceBuildsGenrule() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    workspace.runBuckCommand("project", "//lib:lib");

    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//lib:gen");
    buildLog.assertTargetBuiltLocally("other_cell//:gen");
  }

  @Test
  public void testGeneratingProjectWithGenruleResourceBuildsGenrule() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_resource");

    workspace.runBuckCommand("project", "//app:TestApp");

    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//app:GenResource");
  }

  @Test
  public void testBuckProjectBuckConfigWithoutTestsGenerate() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_buckconfig_without_tests_generate");

    ProcessResult result =
        workspace.runBuckCommand("project", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectBuckConfigWithoutTestsGenerateWithTests() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "project_buckconfig_without_tests_generate_with_tests");

    ProcessResult result =
        workspace.runBuckCommand("project", "--with-tests", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectFocus() throws IOException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(
        AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.IPHONESIMULATOR));
    ProjectWorkspace workspace = createWorkspace(this, "project_focus");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--focus",
            "//Libraries/Dep1:Dep1_1#iphonesimulator-x86_64 //Libraries/Dep2:Dep2",
            "//Apps:TestApp#iphonesimulator-x86_64");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectFocusPattern() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_focus_pattern");

    ProcessResult result =
        workspace.runBuckCommand(
            "project", "--focus", "//Libraries/Dep1:", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectFocusWithTests() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_focus_with_tests");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "project.ide=xcode",
            "--with-tests",
            "--focus",
            "//Tests:",
            "//Apps:TestApp");
    result.assertSuccess();
  }

  @Test
  public void testGeneratingProjectMetadataWithGenrule() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    workspace.runBuckCommand("project", "//lib:lib");
    workspace.verify();
  }

  @Test
  public void testBuckProjectWithUniqueLibraryNames() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_with_unique_library_names");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "-c",
            "cxx.unique_library_name_enabled=true",
            "//Apps:workspace",
            "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectShowsFullOutput() throws Exception {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    ProcessResult result = workspace.runBuckCommand("project", "--show-full-output", "//lib:lib");
    workspace.verify();

    assertEquals(
        "//lib:lib#default,static "
            + workspace.getDestPath().resolve("lib").resolve("lib.xcworkspace")
            + System.lineSeparator(),
        result.getStdout());
  }

  @Test
  public void testBuckProjectShowsOutput() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    ProcessResult result = workspace.runBuckCommand("project", "--show-output", "//lib:lib");
    workspace.verify();

    assertEquals(
        "//lib:lib#default,static " + Paths.get("lib", "lib.xcworkspace") + System.lineSeparator(),
        result.getStdout());
  }

  @Test
  public void testBuckProjectWithCell() throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand("project", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test
  public void testBuckProjectWithEmbeddedCellBuckout() throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "project.embedded_cell_buck_out_enabled=true",
            "//Apps:workspace",
            "--experimental");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test
  public void testBuckProjectWithCellAndMergedHeaderMap() throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "apple.merge_header_maps_in_xcode=true",
            "//Apps:workspace",
            "--experimental");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test(timeout = 180000)
  public void testBuckProjectWithAppleBundleTests() throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_apple_bundle_test");

    ProcessResult result = workspace.runBuckCommand("project", "//app:bundle");
    result.assertSuccess();

    ProcessExecutor.Result xcodeTestResult =
        workspace.runCommand(
            "xcodebuild",
            "-workspace",
            "app/bundle.xcworkspace",
            "-scheme",
            "bundle",
            "-destination 'platform=OS X,arch=x86_64'",
            "clean",
            "test");
    xcodeTestResult.getStderr().ifPresent(System.err::print);
    assertEquals("xcodebuild should succeed", 0, xcodeTestResult.getExitCode());
  }

  @Test
  public void testBuckProjectWithEmbeddedCellBuckoutAndMergedHeaderMap()
      throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "project.embedded_cell_buck_out_enabled=true",
            "--config",
            "apple.merge_header_maps_in_xcode=true",
            "//Apps:workspace",
            "--experimental");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test
  public void testBuckProjectOtherCell() throws IOException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "project.embedded_cell_buck_out_enabled=true",
            "--config",
            "apple.merge_header_maps_in_xcode=true",
            "--show-output",
            "bar//Dep2:Dep2");
    result.assertSuccess();

    assertEquals(
        "bar//Dep2:Dep2#default,static "
            + Paths.get("bar", "Dep2", "Dep2.xcworkspace")
            + System.lineSeparator(),
        result.getStdout());
  }

  @Test
  public void testBuckProjectWithSwiftDependencyOnModularObjectiveCLibrary()
      throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        createWorkspace(this, "project_with_swift_dependency_on_modular_objective_c_library");

    ProcessResult result = workspace.runBuckCommand("project", "//Apps:App");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/App.xcworkspace", "App");
  }

  @Test
  public void
      testBuckProjectWithSwiftDependencyOnModularObjectiveCLibraryAndUmbrellaDirectoryModuleMap()
          throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "umbrella_directory_modulemap", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("project", "//:Test");
    result.assertSuccess();

    runXcodebuild(workspace, "Test.xcworkspace", "Test");
  }

  @Test
  public void
      testBuckProjectWithSwiftDependencyOnModularObjectiveCLibraryAndPerLibraryUmbrellaDirectoryModuleMap()
          throws IOException, InterruptedException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "umbrella_directory_modulemap_per_library", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("project", "//:Test");
    result.assertSuccess();

    runXcodebuild(workspace, "Test.xcworkspace", "Test");
  }

  @Test
  public void testHalide() throws IOException {
    assumeTrue(Platform.detect() == Platform.MACOS);
    assumeTrue(
        AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.IPHONESIMULATOR));
    ProjectWorkspace workspace = createWorkspace(this, "project_halide");

    ProcessResult result =
        workspace.runBuckCommand("project", "//Apps:workspace", "--experimental");
    result.assertSuccess();

    workspace.verify();
  }

  private void runXcodebuild(ProjectWorkspace workspace, String workspacePath, String schemeName)
      throws IOException, InterruptedException {
    ProcessExecutor.Result processResult =
        workspace.runCommand(
            "xcodebuild",

            // "json" output.
            "-json",

            // Make sure the output stays in the temp folder.
            "-derivedDataPath",
            "xcode-out/",

            // Build the project that we just generated
            "-workspace",
            workspacePath,
            "-scheme",
            schemeName,

            // Build for iphonesimulator
            "-arch",
            "x86_64",
            "-sdk",
            "iphonesimulator");
    processResult.getStderr().ifPresent(System.err::print);
    assertEquals("xcodebuild should succeed", 0, processResult.getExitCode());
  }

  private ProjectWorkspace createWorkspace(Object testCase, String scenario) throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(testCase, scenario, temporaryFolder);
    workspace.setUp();

    ProjectFilesystem projectFilesystem = workspace.getProjectFileSystem();
    Path buildScriptPath = AppleProjectHelper.getBuildScriptPath(projectFilesystem);

    TestDataHelper.overrideBuckconfig(
        workspace,
        ImmutableMap.of(
            AppleConfig.APPLE_SECTION,
            ImmutableMap.of(AppleConfig.BUILD_SCRIPT, buildScriptPath.toString())));

    return workspace;
  }
}
