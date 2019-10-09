/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.step;

import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.util.ProcessExecutor;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.immutables.value.Value;

/** Exit code, command and stderr info from the executed step */
@Value.Immutable(builder = true, copy = false, prehash = false)
@BuckStyleValue
public interface StepExecutionResult {

  int getExitCode();

  ImmutableList<String> getExecutedCommand();

  Optional<String> getStderr();

  default boolean isSuccess() {
    return getExitCode() == StepExecutionResults.SUCCESS_EXIT_CODE;
  }

  /** Creates {@code StepExecutionResult} from {@code exitCode} */
  static StepExecutionResult of(int exitCode) {
    return ImmutableStepExecutionResult.builder().setExitCode(exitCode).build();
  }

  /** Creates {@code StepExecutionResult} from {@code ProcessExecutor.Result} */
  static StepExecutionResult of(ProcessExecutor.Result result) {
    return ImmutableStepExecutionResult.builder()
        .setExitCode(result.getExitCode())
        .setExecutedCommand(result.getCommand())
        .setStderr(result.getStderr())
        .build();
  }
}
