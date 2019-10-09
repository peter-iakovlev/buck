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
package com.facebook.buck.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;
import java.util.Objects;

/** Configuration that doesn't have any options */
public class EmptyTargetConfiguration implements TargetConfiguration {
  public static final EmptyTargetConfiguration INSTANCE = new EmptyTargetConfiguration();

  private final int hashCode = Objects.hash(EmptyTargetConfiguration.class.getName());

  private EmptyTargetConfiguration() {}

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof EmptyTargetConfiguration;
  }

  @JsonIgnore
  @Override
  public ImmutableSet<BuildTarget> getConfigurationTargets() {
    return ImmutableSet.of();
  }
}
