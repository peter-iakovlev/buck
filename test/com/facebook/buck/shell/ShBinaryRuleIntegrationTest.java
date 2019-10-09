/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.shell;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

public class ShBinaryRuleIntegrationTest {

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Test
  public void testTrivialShBinaryRule() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_trivial", temporaryFolder);
    workspace.setUp();

    Path outputFile = workspace.buildAndReturnOutput("//:run_example");

    // Verify contents of example_out.txt
    String output = new String(Files.readAllBytes(outputFile), UTF_8);
    assertEquals("arg1\narg2\n", output);
  }

  @Test
  public void testExecutableOnRebuild() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_caching", temporaryFolder);
    workspace.setUp();
    workspace.enableDirCache();

    // First build only the sh_binary rule itself.
    ProcessResult buildResult = workspace.runBuckCommand("build", "//:example_sh", "-v", "2");
    buildResult.assertSuccess();

    // Make sure the sh_binary output is executable to begin with.
    String outputPath = "buck-out/gen/__example_sh__/example_sh.sh";
    Path output = workspace.getPath(outputPath);
    assertTrue("Output file should be written to '" + outputPath + "'.", Files.exists(output));
    assertTrue("Output file must be executable.", Files.isExecutable(output));

    // Now delete the buck-out directory (but not buck-cache).
    workspace.runBuckCommand("clean", "--keep-cache");

    // Now run the genrule that depends on the sh_binary above. This will force buck to fetch the
    // sh_binary output from cache. If the executable flag is lost somewhere along the way, this
    // will fail.
    buildResult = workspace.runBuckCommand("build", "//:run_example", "-v", "2");
    buildResult.assertSuccess("Build failed when rerunning sh_binary from cache.");

    // Note that previously we used to verify that the //:example_sh rule was
    // fetched from the cache here.  However, caching for sh_binary() rules has
    // since been disabled.

    // In addition to running the build, explicitly check that the output file is still executable.
    assertTrue(
        "Output file must be retrieved from cache at '" + outputPath + ".", Files.exists(output));
    assertTrue("Output file retrieved from cache must be executable.", Files.isExecutable(output));
  }

  @Test
  public void testShBinaryWithMappedResources() throws IOException {
    List<String> lines = testMappedResources("same_cell");
    String expectedPlatform = Platform.detect().getPrintableName();
    List<String> expected = ImmutableList.of(expectedPlatform, "arg1 arg2", "stuff", "fluff");
    assertEquals(expected, lines);
  }

  @Test
  public void testShBinaryWithMappedResourcesDifferentCell() throws IOException {
    List<String> lines = testMappedResources("different_cell");
    String expectedPlatform = Platform.detect().getPrintableName();
    List<String> expected = ImmutableList.of(expectedPlatform, "arg1 arg2", "stuff", "fluff");
    assertEquals(expected, lines);
  }

  @Test
  public void testShBinaryWithMappedResourcesConflicting() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_mapped_resources", temporaryFolder);
    workspace.setUp();

    ProcessResult buildResult = workspace.runBuckBuild("//app:create_output_conflicting_resources");
    assertEquals(ExitCode.BUILD_ERROR, buildResult.getExitCode());

    assertTrue(
        buildResult
            .getStderr()
            .contains(
                "Duplicate resource link path '__default__/node/resource1' "
                    + "(Resolves to both '//node:resource1' and 'node/resource1')"));
  }

  private List<String> testMappedResources(String testCase) throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_mapped_resources", temporaryFolder);
    workspace.setUp();

    Path outputFile = workspace.buildAndReturnOutput("//app:create_output_" + testCase);

    // Verify contents of output.txt
    return Files.readAllLines(outputFile, UTF_8);
  }

  @Test
  public void testShBinaryWithMappedResourcesFromCache() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_mapped_resources", temporaryFolder);
    workspace.setUp();
    workspace.enableDirCache();

    ProcessResult result = workspace.runBuckCommand("run", "//node:node1");
    result.assertSuccess();
    assertThat(result.getStdout(), containsString("stuff\nfluff\n"));

    workspace.runBuckCommand("clean", "--keep-cache");
    ProcessResult result2 = workspace.runBuckCommand("run", "//node:node1");
    result2.assertSuccess();
    assertThat(result2.getStdout(), containsString("stuff\nfluff\n"));
  }

  @Test
  public void testShBinaryWithResources() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_resources", temporaryFolder);
    workspace.setUp();

    Path outputFile = workspace.buildAndReturnOutput("//app:create_output_using_node");

    // Verify contents of output.txt
    List<String> lines = Files.readAllLines(outputFile, UTF_8);
    String expectedPlatform = Platform.detect().getPrintableName();
    assertEquals(expectedPlatform, lines.get(0));
    assertEquals("arg1 arg2", lines.get(1));
  }

  @Test
  public void testShBinaryCannotOverwriteResource() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_overwrite_violation", temporaryFolder);
    workspace.setUp();

    ProcessResult buildResult = workspace.runBuckCommand("build", "//:overwrite");
    buildResult.assertFailure();

    assertThat(buildResult.getStderr(), containsString("/overwrite.sh: Permission denied"));
  }

  @Test
  public void testShBinaryPreservesPwdEnvVar() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sh_binary_pwd", temporaryFolder);
    workspace.setUp();

    String alteredPwd = workspace.getDestPath() + "////////";
    ProcessResult buildResult =
        workspace.runBuckCommandWithEnvironmentOverridesAndContext(
            workspace.getDestPath(),
            Optional.empty(),
            ImmutableMap.of("PWD", alteredPwd),
            "run",
            "//:pwd");
    buildResult.assertSuccess();
    assertThat(buildResult.getStdout(), Matchers.equalTo(alteredPwd));
  }

  @Test
  public void testShBinaryWithCells() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_cells", temporaryFolder);
    workspace.setUp();

    workspace.buildAndReturnOutput("//:create_output_using_node");
  }

  @Test
  public void testShBinaryWithEmbeddedCells() throws IOException {
    // sh_binary is not available on Windows. Ignore this test on Windows.
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_cells", temporaryFolder);
    workspace.setUp();

    workspace.buildAndReturnOutput(
        "--config", "project.embedded_cell_buck_out_enabled=true", "//:create_output_using_node");
  }

  @Test
  public void testShBinaryWithBuckoutOutsideRoot() throws IOException {
    assumeTrue(Platform.detect() != Platform.WINDOWS);

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_trivial", temporaryFolder);
    workspace.setUp();

    // symlink the buck-out to be outside the project root
    Path tempBuckOut = Files.createTempDirectory("buck-out");
    Files.createSymbolicLink(temporaryFolder.getRoot().resolve("buck-out"), tempBuckOut);

    workspace.buildAndReturnOutput("//:run_example");
  }

  @Test
  public void testShBinaryWithTest() throws IOException {
    assumeTrue(Platform.detect() != Platform.WINDOWS);

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "sh_binary_with_test", temporaryFolder);
    workspace.setUp();

    ProcessResult buildResult = workspace.runBuckCommand("test", "//:hello");
    buildResult.assertSuccess();
  }
}
