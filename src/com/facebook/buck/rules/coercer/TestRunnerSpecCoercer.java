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
package com.facebook.buck.rules.coercer;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.test.rule.ImmutableTestRunnerSpec;
import com.facebook.buck.core.test.rule.TestRunnerSpec;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Map;

/**
 * Coerces a freeform JSON as a {@link TestRunnerSpec}, which is basically a JSON containing {@link
 * StringWithMacros}
 */
public class TestRunnerSpecCoercer implements TypeCoercer<TestRunnerSpec> {

  private final TypeCoercer<StringWithMacros> macrosTypeCoercer;
  private final TypeCoercer<ImmutableMap<StringWithMacros, TestRunnerSpec>> mapTypeCoercer;
  private final ListTypeCoercer<TestRunnerSpec> listTypeCoercer;

  public TestRunnerSpecCoercer(TypeCoercer<StringWithMacros> macrosTypeCoercer) {
    this.macrosTypeCoercer = macrosTypeCoercer;
    this.mapTypeCoercer = new MapTypeCoercer<>(macrosTypeCoercer, this);
    this.listTypeCoercer = new ListTypeCoercer<>(this);
  }

  @Override
  public Class<TestRunnerSpec> getOutputClass() {
    return TestRunnerSpec.class;
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return false;
  }

  @Override
  public void traverse(CellPathResolver cellRoots, TestRunnerSpec object, Traversal traversal) {}

  @Override
  public TestRunnerSpec coerce(
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      Path pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      Object object)
      throws CoerceFailedException {

    return ImmutableTestRunnerSpec.of(
        coerceRecursively(
            cellRoots, filesystem, pathRelativeToProjectRoot, targetConfiguration, object));
  }

  private Object coerceRecursively(
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      Path pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      Object object)
      throws CoerceFailedException {
    if (object instanceof Map) {
      return mapTypeCoercer.coerce(
          cellRoots, filesystem, pathRelativeToProjectRoot, targetConfiguration, object);
    }
    if (object instanceof Iterable) {
      return listTypeCoercer.coerce(
          cellRoots, filesystem, pathRelativeToProjectRoot, targetConfiguration, object);
    }
    if (object instanceof Number || object instanceof Boolean) {
      return object;
    }
    return macrosTypeCoercer.coerce(
        cellRoots, filesystem, pathRelativeToProjectRoot, targetConfiguration, object);
  }
}
