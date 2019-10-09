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
package com.facebook.buck.core.artifact;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildPaths;
import com.facebook.buck.core.rules.analysis.action.ActionAnalysisData;
import com.facebook.buck.core.rules.analysis.action.ActionAnalysisDataKey;
import com.facebook.buck.core.rules.analysis.action.ImmutableActionAnalysisDataKey;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.testutil.TemporaryPaths;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.events.Location;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;

public class ArtifactFilesystemTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  private final FakeProjectFilesystem filesystem = new FakeProjectFilesystem();

  @Test
  public void inputStreamOfArtifact() throws IOException {
    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);

    filesystem.writeContentsToPath("foo", Paths.get("bar"));
    InputStream inputStream =
        artifactFilesystem.getInputStream(
            ImmutableSourceArtifactImpl.of(PathSourcePath.of(filesystem, Paths.get("bar"))));

    assertEquals("foo", new BufferedReader(new InputStreamReader(inputStream)).readLine());
  }

  @Test
  public void outputStreamOfArtifact() throws IOException {
    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);

    OutputStream outputStream =
        artifactFilesystem.getOutputStream(
            ImmutableSourceArtifactImpl.of(PathSourcePath.of(filesystem, Paths.get("bar"))));

    outputStream.write("foo".getBytes(Charsets.UTF_8));
    outputStream.close();

    assertEquals("foo", Iterables.getOnlyElement(filesystem.readLines(Paths.get("bar"))));
  }

  @Test
  public void makeExecutable() throws IOException {
    ProjectFilesystem filesystem = TestProjectFilesystems.createProjectFilesystem(tmp.getRoot());

    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);
    ImmutableSourceArtifactImpl artifact =
        ImmutableSourceArtifactImpl.of(PathSourcePath.of(filesystem, Paths.get("bar")));

    artifactFilesystem.writeContentsToPath("foobar", artifact);
    artifactFilesystem.makeExecutable(artifact);

    assertEquals("foobar", Iterables.getOnlyElement(filesystem.readLines(Paths.get("bar"))));
    assertTrue(filesystem.isExecutable(artifact.getSourcePath().getRelativePath()));
  }

  @Test
  public void writeContents() throws IOException {
    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);
    ImmutableSourceArtifactImpl artifact =
        ImmutableSourceArtifactImpl.of(PathSourcePath.of(filesystem, Paths.get("bar")));

    artifactFilesystem.writeContentsToPath("foobar", artifact);

    assertEquals("foobar", Iterables.getOnlyElement(filesystem.readLines(Paths.get("bar"))));
  }

  @Test
  public void expandCommandLine() {
    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);
    ImmutableSourceArtifactImpl sourceArtifact =
        ImmutableSourceArtifactImpl.of(PathSourcePath.of(filesystem, Paths.get("bar", "baz")));
    ImmutableSourceArtifactImpl shortArtifact =
        ImmutableSourceArtifactImpl.of(PathSourcePath.of(filesystem, Paths.get("foo")));

    assertEquals(Paths.get("bar", "baz").toString(), artifactFilesystem.stringify(sourceArtifact));
    assertEquals(Paths.get("foo").toString(), artifactFilesystem.stringify(shortArtifact));
    assertEquals(
        filesystem.resolve(Paths.get("bar", "baz")).toAbsolutePath().toString(),
        artifactFilesystem.stringifyAbsolute(sourceArtifact));
    assertEquals(
        filesystem.resolve("foo").toAbsolutePath().toString(),
        artifactFilesystem.stringifyAbsolute(shortArtifact));
  }

  @Test
  public void createsPackagePathsForOutputs() throws IOException {
    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");
    BuildArtifactFactory factory = new BuildArtifactFactory(buildTarget, filesystem);
    ActionAnalysisDataKey key =
        ImmutableActionAnalysisDataKey.of(buildTarget, new ActionAnalysisData.ID() {});

    ImmutableSet<Artifact> artifacts =
        ImmutableSet.of(
            factory
                .createDeclaredArtifact(Paths.get("out.txt"), Location.BUILTIN)
                .materialize(key));

    Path expectedPath = BuildPaths.getGenDir(filesystem, buildTarget);

    assertFalse(filesystem.isDirectory(expectedPath));

    artifactFilesystem.createPackagePaths(artifacts);

    assertTrue(filesystem.isDirectory(expectedPath));
  }

  @Test
  public void deletesBuildArtifactsForOutputs() throws IOException {
    ArtifactFilesystem artifactFilesystem = new ArtifactFilesystem(filesystem);
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");

    BuildArtifactFactory factory = new BuildArtifactFactory(buildTarget, filesystem);
    ActionAnalysisDataKey key =
        ImmutableActionAnalysisDataKey.of(buildTarget, new ActionAnalysisData.ID() {});

    BuildArtifact artifact =
        factory.createDeclaredArtifact(Paths.get("out.txt"), Location.BUILTIN).materialize(key);

    artifactFilesystem.writeContentsToPath("contents", artifact);

    Path expectedBuildPath = BuildPaths.getGenDir(filesystem, buildTarget).resolve("out.txt");

    assertTrue(filesystem.isFile(expectedBuildPath));

    artifactFilesystem.removeBuildArtifacts(ImmutableSet.of(artifact));

    assertFalse(filesystem.isFile(expectedBuildPath));
  }
}
