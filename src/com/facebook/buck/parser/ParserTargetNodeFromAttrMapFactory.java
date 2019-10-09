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

package com.facebook.buck.parser;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.event.PerfEventId;
import com.facebook.buck.event.SimplePerfEvent;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * Creates {@link TargetNode} instances from raw attribute map returned by the parser. This is a
 * legacy interface which is used only for configuration targets, and should be removed.
 */
public interface ParserTargetNodeFromAttrMapFactory {
  TargetNode<?> createTargetNode(
      Cell cell,
      Path buildFile,
      BuildTarget target,
      Map<String, Object> rawNode,
      Function<PerfEventId, SimplePerfEvent.Scope> perfEventScope);
}
