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

package com.facebook.buck.remoteexecution.grpc;

import build.bazel.remote.execution.v2.ContentAddressableStorageGrpc.ContentAddressableStorageFutureStub;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.remoteexecution.ContentAddressedStorageClient;
import com.facebook.buck.remoteexecution.UploadDataSupplier;
import com.facebook.buck.remoteexecution.config.RemoteExecutionStrategyConfig;
import com.facebook.buck.remoteexecution.interfaces.Protocol;
import com.facebook.buck.remoteexecution.interfaces.Protocol.Digest;
import com.facebook.buck.remoteexecution.interfaces.Protocol.OutputDirectory;
import com.facebook.buck.remoteexecution.interfaces.Protocol.OutputFile;
import com.facebook.buck.remoteexecution.proto.RemoteExecutionMetadata;
import com.facebook.buck.remoteexecution.util.MultiThreadedBlobUploader;
import com.facebook.buck.remoteexecution.util.OutputsMaterializer;
import com.facebook.buck.util.concurrent.MostExecutors;
import com.facebook.buck.util.types.Unit;
import com.google.bytestream.ByteStreamGrpc.ByteStreamStub;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

/** Implementation of a CAS client using GRPC. */
public class GrpcContentAddressableStorageClient implements ContentAddressedStorageClient {

  private static final int SIZE_LIMIT = 10 * 1024 * 1024; // 10MB
  private static final int FIND_MISSING_CHECK_LIMIT = 1000;
  private static final int EXECUTOR_THREADS = 4;

  private final MultiThreadedBlobUploader uploader;
  private final OutputsMaterializer outputsMaterializer;
  private final GrpcAsyncBlobFetcher fetcher;

  public GrpcContentAddressableStorageClient(
      ContentAddressableStorageFutureStub storageStub,
      ByteStreamStub byteStreamStub,
      int casDeadline,
      String instanceName,
      Protocol protocol,
      BuckEventBus buckEventBus,
      RemoteExecutionMetadata metadata,
      RemoteExecutionStrategyConfig strategyConfig) {
    this.uploader =
        new MultiThreadedBlobUploader(
            FIND_MISSING_CHECK_LIMIT,
            SIZE_LIMIT,
            MostExecutors.newMultiThreadExecutor("blob-uploader", EXECUTOR_THREADS),
            new GrpcCasBlobUploader(
                instanceName, storageStub, byteStreamStub, buckEventBus, metadata));

    this.fetcher =
        new GrpcAsyncBlobFetcher(
            instanceName,
            storageStub,
            byteStreamStub,
            buckEventBus,
            metadata,
            protocol,
            casDeadline);
    this.outputsMaterializer =
        new OutputsMaterializer(
            SIZE_LIMIT,
            MostExecutors.newMultiThreadExecutor(
                "output-materializer", strategyConfig.getOutputMaterializationThreads()),
            fetcher,
            protocol,
            buckEventBus);
  }

  @Override
  public ListenableFuture<Unit> addMissing(Collection<UploadDataSupplier> data) throws IOException {
    return uploader.addMissing(data.stream());
  }

  @Override
  public ListenableFuture<Unit> materializeOutputs(
      List<OutputDirectory> outputDirectories,
      List<OutputFile> outputFiles,
      FileMaterializer materializer)
      throws IOException {
    return outputsMaterializer.materialize(outputDirectories, outputFiles, materializer);
  }

  @Override
  public boolean containsDigest(Digest digest) {
    return uploader.containsDigest(digest);
  }

  @Override
  public ListenableFuture<ByteBuffer> fetch(Digest digest) {
    return fetcher.fetch(digest);
  }
}
