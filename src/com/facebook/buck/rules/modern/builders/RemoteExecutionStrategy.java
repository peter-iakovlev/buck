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

package com.facebook.buck.rules.modern.builders;

import build.bazel.remote.execution.v2.ExecuteOperationMetadata;
import com.facebook.buck.core.build.engine.BuildResult;
import com.facebook.buck.core.build.engine.BuildRuleStatus;
import com.facebook.buck.core.build.engine.BuildRuleSuccessType;
import com.facebook.buck.core.build.engine.BuildStrategyContext;
import com.facebook.buck.core.build.execution.context.ExecutionContext;
import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.build.strategy.BuildRuleStrategy;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.io.file.MostFiles;
import com.facebook.buck.remoteexecution.MetadataProviderFactory;
import com.facebook.buck.remoteexecution.RemoteExecutionClients;
import com.facebook.buck.remoteexecution.RemoteExecutionServiceClient.ExecutionHandle;
import com.facebook.buck.remoteexecution.RemoteExecutionServiceClient.ExecutionResult;
import com.facebook.buck.remoteexecution.WorkerRequirementsProvider;
import com.facebook.buck.remoteexecution.config.RemoteExecutionConfig;
import com.facebook.buck.remoteexecution.config.RemoteExecutionStrategyConfig;
import com.facebook.buck.remoteexecution.event.RemoteBuildRuleExecutionEvent;
import com.facebook.buck.remoteexecution.event.RemoteExecutionActionEvent;
import com.facebook.buck.remoteexecution.event.RemoteExecutionActionEvent.State;
import com.facebook.buck.remoteexecution.event.RemoteExecutionSessionEvent;
import com.facebook.buck.remoteexecution.interfaces.MetadataProvider;
import com.facebook.buck.remoteexecution.interfaces.Protocol;
import com.facebook.buck.remoteexecution.interfaces.Protocol.Digest;
import com.facebook.buck.remoteexecution.util.OutputsMaterializer.FilesystemFileMaterializer;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ImmutableStepExecutionResult;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.StepFailedException;
import com.facebook.buck.util.Scope;
import com.facebook.buck.util.concurrent.JobLimiter;
import com.facebook.buck.util.concurrent.MostExecutors;
import com.facebook.buck.util.function.ThrowingFunction;
import com.facebook.buck.util.types.Either;
import com.facebook.buck.util.types.Unit;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * A {@link BuildRuleStrategy} that uses a Remote Execution service for executing BuildRules. It
 * currently only supports ModernBuildRule and creates a remote Action With {@link
 * ModernBuildRuleRemoteExecutionHelper}.
 *
 * <p>See https://docs.google.com/document/d/1AaGk7fOPByEvpAbqeXIyE8HX_A3_axxNnvroblTZ_6s/preview
 * for a high-level description of the approach to remote execution.
 */
public class RemoteExecutionStrategy extends AbstractModernBuildRuleStrategy {
  private static final Logger LOG = Logger.get(RemoteExecutionStrategy.class);

  private final BuckEventBus eventBus;
  private final RemoteExecutionClients executionClients;
  private final RemoteExecutionHelper mbrHelper;

  private final ListeningExecutorService service;

  private final JobLimiter computeActionLimiter;
  private final JobLimiter pendingUploadsLimiter;
  private final JobLimiter executionLimiter;
  private final JobLimiter handleResultLimiter;
  private final OptionalLong maxInputSizeBytes;
  private final WorkerRequirementsProvider requirementsProvider;
  private final MetadataProvider metadataProvider;
  private final String auxiliaryBuildTag;
  private final RemoteExecutionSessionEvent.Started remoteExecutionSessionStartedEvent;

  RemoteExecutionStrategy(
      BuckEventBus eventBus,
      RemoteExecutionStrategyConfig strategyConfig,
      RemoteExecutionClients executionClients,
      MetadataProvider metadataProvider,
      RemoteExecutionHelper mbrHelper,
      WorkerRequirementsProvider requirementsProvider,
      ListeningExecutorService service,
      String auxiliaryBuildTag) {
    this.executionClients = executionClients;
    this.service = service;
    this.computeActionLimiter = new JobLimiter(strategyConfig.getMaxConcurrentActionComputations());
    this.pendingUploadsLimiter = new JobLimiter(strategyConfig.getMaxConcurrentPendingUploads());
    this.executionLimiter = new JobLimiter(strategyConfig.getMaxConcurrentExecutions());
    this.handleResultLimiter = new JobLimiter(strategyConfig.getMaxConcurrentResultHandling());
    this.maxInputSizeBytes = strategyConfig.maxInputSizeBytes();
    this.eventBus = eventBus;
    this.metadataProvider = metadataProvider;
    this.mbrHelper = mbrHelper;
    this.requirementsProvider = requirementsProvider;
    this.auxiliaryBuildTag = auxiliaryBuildTag;
    this.remoteExecutionSessionStartedEvent = RemoteExecutionSessionEvent.started();
    this.eventBus.post(remoteExecutionSessionStartedEvent);
  }

  /**
   * Creates a BuildRuleStrategy for a particular config
   *
   * @return
   */
  static LocalFallbackStrategy createRemoteExecutionStrategy(
      BuckEventBus eventBus,
      RemoteExecutionConfig remoteExecutionConfig,
      RemoteExecutionClients clients,
      SourcePathRuleFinder ruleFinder,
      Cell rootCell,
      ThrowingFunction<Path, HashCode, IOException> fileHasher,
      MetadataProvider metadataProvider,
      WorkerRequirementsProvider workerRequirementsProvider) {
    RemoteExecutionStrategyConfig strategyConfig = remoteExecutionConfig.getStrategyConfig();
    return new LocalFallbackStrategy(
        new RemoteExecutionStrategy(
            eventBus,
            strategyConfig,
            clients,
            metadataProvider,
            new ModernBuildRuleRemoteExecutionHelper(
                eventBus, clients.getProtocol(), ruleFinder, rootCell, fileHasher),
            workerRequirementsProvider,
            MoreExecutors.listeningDecorator(
                MostExecutors.newMultiThreadExecutor("remote-exec", strategyConfig.getThreads())),
            remoteExecutionConfig.getAuxiliaryBuildTag()),
        eventBus,
        strategyConfig.isLocalFallbackEnabled());
  }

  @Override
  public boolean canBuild(BuildRule instance) {
    return super.canBuild(instance)
        && mbrHelper.supportsRemoteExecution((ModernBuildRule<?>) instance);
  }

  @Override
  public void close() throws IOException {
    executionClients.close();
    eventBus.post(RemoteExecutionSessionEvent.finished(remoteExecutionSessionStartedEvent));
  }

  @Override
  public StrategyBuildResult build(BuildRule rule, BuildStrategyContext strategyContext) {
    Preconditions.checkState(rule instanceof ModernBuildRule);
    BuildTarget buildTarget = rule.getBuildTarget();

    RemoteExecutionActionEvent.sendScheduledEvent(eventBus, rule.getBuildTarget());
    RuleContext ruleContext = new RuleContext(eventBus, rule.getBuildTarget());

    ListenableFuture<RemoteExecutionActionInfo> actionInfoFuture =
        pendingUploadsLimiter.schedule(
            service, () -> computeActionAndUpload(rule, strategyContext, ruleContext));

    AtomicReference<RemoteExecutionActionInfo> actionInfo = new AtomicReference<>();
    ListenableFuture<Optional<ExecutionResult>> executionResult =
        Futures.transformAsync(
            actionInfoFuture,
            actionInfoResult -> {
              actionInfo.set(actionInfoResult);
              return handleActionInfo(rule, strategyContext, actionInfoResult, ruleContext);
            },
            service);

    AtomicReference<ExecutionResult> executionInfo = new AtomicReference<>();
    ListenableFuture<Optional<BuildResult>> buildResult =
        Futures.transformAsync(
            executionResult,
            result -> {
              if (!result.isPresent()) {
                return Futures.immediateFuture(
                    Optional.of(
                        strategyContext.createCancelledResult(ruleContext.getCancelReason())));
              }
              executionInfo.set(result.get());
              // actionInfoFuture must be set at this point
              Preconditions.checkState(actionInfo.get() != null);
              Streams.stream(actionInfo.get().getOutputs())
                  .filter(
                      output ->
                          !output
                              .toString()
                              .contains(
                                  ModernBuildRuleRemoteExecutionHelper.METADATA_PATH.toString()))
                  .forEach(output -> recordArtifact(rule, strategyContext, output));
              return Futures.immediateFuture(
                  Optional.of(
                      strategyContext.createBuildResult(
                          BuildRuleSuccessType.BUILT_LOCALLY, Optional.of("built remotely"))));
            },
            MoreExecutors.directExecutor());

    Futures.addCallback(
        buildResult,
        new FutureCallback<Optional<BuildResult>>() {
          @Override
          public void onSuccess(@Nullable Optional<BuildResult> result) {
            if (result == null
                || !result.isPresent()
                || result.get().getStatus() == BuildRuleStatus.CANCELED) {
              RemoteExecutionActionEvent.sendTerminalEvent(
                  eventBus,
                  State.ACTION_CANCELLED,
                  buildTarget,
                  Optional.ofNullable(actionInfo.get())
                      .map(RemoteExecutionActionInfo::getActionDigest),
                  Optional.empty(),
                  Optional.of(ruleContext.timeMsInState),
                  Optional.of(ruleContext.timeMsAfterState));
            } else {
              // actionInfo and executionInfo must be set at this point
              Preconditions.checkState(actionInfo.get() != null);
              Preconditions.checkState(executionInfo.get() != null);

              RemoteExecutionActionEvent.sendTerminalEvent(
                  eventBus,
                  State.ACTION_SUCCEEDED,
                  buildTarget,
                  Optional.ofNullable(actionInfo.get())
                      .map(RemoteExecutionActionInfo::getActionDigest),
                  Optional.ofNullable(executionInfo.get()).map(ExecutionResult::getActionMetadata),
                  Optional.of(ruleContext.timeMsInState),
                  Optional.of(ruleContext.timeMsAfterState));
            }
          }

          @Override
          public void onFailure(Throwable t) {
            RemoteExecutionActionEvent.sendTerminalEvent(
                eventBus,
                t instanceof InterruptedException ? State.ACTION_CANCELLED : State.ACTION_FAILED,
                buildTarget,
                Optional.ofNullable(actionInfo.get())
                    .map(RemoteExecutionActionInfo::getActionDigest),
                Optional.empty(),
                Optional.of(ruleContext.timeMsInState),
                Optional.of(ruleContext.timeMsAfterState));
          }
        },
        MoreExecutors.directExecutor());

    return new StrategyBuildResult() {
      @Override
      public void cancel(Throwable cause) {
        ruleContext.cancel(cause);
      }

      @Override
      public boolean cancelIfNotComplete(Throwable reason) {
        cancel(reason);
        return ruleContext.isCancelled();
      }

      @Override
      public ListenableFuture<Optional<BuildResult>> getBuildResult() {
        return buildResult;
      }
    };
  }

  private void recordArtifact(BuildRule rule, BuildStrategyContext strategyContext, Path output) {
    strategyContext
        .getBuildableContext()
        .recordArtifact(
            rule.getProjectFilesystem().relativize(mbrHelper.getCellPathPrefix().resolve(output)));
  }

  private ListenableFuture<RemoteExecutionActionInfo> computeActionAndUpload(
      BuildRule rule, BuildStrategyContext strategyContext, RuleContext guardContext) {
    ListenableFuture<RemoteExecutionActionInfo> actionInfoFuture =
        computeActionLimiter.schedule(
            service,
            () ->
                Futures.immediateFuture(
                    getRemoteExecutionActionInfo(rule, strategyContext, guardContext)));
    return Futures.transformAsync(
        actionInfoFuture,
        actionInfo -> uploadInputs(rule, actionInfo, guardContext),
        MoreExecutors.directExecutor());
  }

  private ListenableFuture<RemoteExecutionActionInfo> uploadInputs(
      BuildRule rule, RemoteExecutionActionInfo actionInfo, RuleContext guardContext)
      throws Exception {
    Objects.requireNonNull(actionInfo);
    if (rule.shouldRespectInputSizeLimitForRemoteExecution()
        && maxInputSizeBytes.isPresent()
        && maxInputSizeBytes.getAsLong() < actionInfo.getTotalInputSize()) {
      throw new RuntimeException(
          "Max file size exceeded for Remote Execution, action contains: "
              + actionInfo.getTotalInputSize()
              + " bytes, max allowed: "
              + maxInputSizeBytes.getAsLong());
    }
    Digest actionDigest = actionInfo.getActionDigest();
    Scope uploadingInputsScope =
        guardContext.enterState(State.UPLOADING_INPUTS, Optional.of(actionDigest));
    ListenableFuture<Unit> inputsUploadedFuture =
        executionClients.getContentAddressedStorage().addMissing(actionInfo.getRequiredData());
    inputsUploadedFuture.addListener(uploadingInputsScope::close, MoreExecutors.directExecutor());
    return Futures.transform(
        inputsUploadedFuture,
        ignored -> {
          // The actionInfo may be very large, so explicitly clear out the unneeded parts.
          // actionInfo.getRequiredData() in particular may be very, very large and is unneeded once
          // uploading has completed.
          return actionInfo.withRequiredData(ImmutableList.of());
        },
        MoreExecutors.directExecutor());
  }

  private ListenableFuture<Optional<ExecutionResult>> handleActionInfo(
      BuildRule rule,
      BuildStrategyContext strategyContext,
      RemoteExecutionActionInfo actionInfo,
      RuleContext guardContext)
      throws IOException {
    Objects.requireNonNull(actionInfo);
    // The actionInfo may be very large, so explicitly capture just the parts that we need and clear
    // it (to hopefully catch future bad uses). actionInfo.getRequiredData() in particular may be
    // very, very large.
    Digest actionDigest = actionInfo.getActionDigest();
    Iterable<? extends Path> actionOutputs = actionInfo.getOutputs();
    Scope uploadingInputsScope =
        guardContext.enterState(State.UPLOADING_ACTION, Optional.of(actionDigest));

    ListenableFuture<Unit> inputsUploadedFuture =
        executionClients.getContentAddressedStorage().addMissing(actionInfo.getRequiredData());
    inputsUploadedFuture.addListener(uploadingInputsScope::close, MoreExecutors.directExecutor());
    return Futures.transformAsync(
        inputsUploadedFuture,
        ignored ->
            executeNowThatInputsAreReady(
                strategyContext,
                rule,
                guardContext,
                actionDigest,
                actionOutputs,
                rule.getFullyQualifiedName()),
        service);
  }

  private RemoteExecutionActionInfo getRemoteExecutionActionInfo(
      BuildRule rule, BuildStrategyContext strategyContext, RuleContext guardContext)
      throws IOException {
    try (Scope ignored = strategyContext.buildRuleScope()) {
      RemoteExecutionActionInfo actionInfo;

      try (Scope ignored1 = guardContext.enterState(State.COMPUTING_ACTION, Optional.empty())) {
        actionInfo =
            mbrHelper.prepareRemoteExecution(
                (ModernBuildRule<?>) rule,
                digest -> !executionClients.getContentAddressedStorage().containsDigest(digest),
                requirementsProvider.resolveRequirements(rule.getBuildTarget(), auxiliaryBuildTag));
      }
      return actionInfo;
    }
  }

  private ListenableFuture<Optional<ExecutionResult>> executeNowThatInputsAreReady(
      BuildStrategyContext strategyContext,
      BuildRule buildRule,
      RuleContext guardContext,
      Digest actionDigest,
      Iterable<? extends Path> actionOutputs,
      String ruleName) {
    AtomicReference<Throwable> cancelled = new AtomicReference<>(null);
    MetadataProvider metadataProvider =
        MetadataProviderFactory.wrapForRuleWithWorkerRequirements(
            this.metadataProvider,
            () ->
                requirementsProvider.resolveRequirements(
                    buildRule.getBuildTarget(), auxiliaryBuildTag));
    ListenableFuture<ExecutionResult> executionResult =
        executionLimiter.schedule(
            service,
            () -> {
              if (guardContext.isCancelled()) {
                cancelled.set(guardContext.getCancelReason());
                return Futures.immediateFuture(null);
              }
              Scope executingScope =
                  guardContext.enterState(State.EXECUTING, Optional.of(actionDigest));
              ExecutionHandle executionHandle =
                  executionClients
                      .getRemoteExecutionService()
                      .execute(actionDigest, ruleName, metadataProvider);

              guardContext.onCancellation(reason -> executionHandle.cancel());
              Futures.addCallback(
                  executionHandle.getExecutionStarted(),
                  new FutureCallback<ExecuteOperationMetadata>() {
                    @Override
                    public void onSuccess(@Nullable ExecuteOperationMetadata result) {
                      guardContext.tryStart();
                    }

                    @Override
                    public void onFailure(Throwable t) {}
                  },
                  MoreExecutors.directExecutor());
              executionHandle
                  .getResult()
                  .addListener(executingScope::close, MoreExecutors.directExecutor());
              return Futures.transform(
                  executionHandle.getResult(),
                  result -> {
                    // Try Start so that if Executing Started was never sent, we can don't block
                    // cancellation till the result is ready.
                    guardContext.tryStart();
                    if (guardContext.isCancelled()) {
                      cancelled.set(guardContext.getCancelReason());
                      return null;
                    }
                    return result;
                  },
                  MoreExecutors.directExecutor());
            });

    return Futures.transformAsync(
        executionResult,
        result -> {
          if (cancelled.get() != null) {
            return Futures.immediateFuture(Optional.empty());
          }
          return handleResultLimiter.schedule(
              service,
              () ->
                  handleExecutionResult(
                      strategyContext,
                      buildRule,
                      result,
                      actionDigest,
                      actionOutputs,
                      metadataProvider,
                      guardContext));
        },
        service);
  }

  private ListenableFuture<Optional<ExecutionResult>> handleExecutionResult(
      BuildStrategyContext strategyContext,
      BuildRule buildRule,
      ExecutionResult result,
      Digest actionDigest,
      Iterable<? extends Path> actionOutputs,
      MetadataProvider metadataProvider,
      RuleContext guardContext)
      throws IOException, StepFailedException {
    BuildTarget buildTarget = buildRule.getBuildTarget();
    int exitCode = result.getExitCode();
    LOG.debug(
        "[RE] Built target [%s] with exit code [%d]. Action: [%s]. ActionResult: [%s]",
        buildTarget.getFullyQualifiedName(),
        exitCode,
        actionDigest,
        result.getActionResultDigest());
    if (exitCode != StepExecutionResults.SUCCESS_EXIT_CODE) {
      Optional<String> stdout = result.getStdout();
      Optional<String> stderr = result.getStderr();
      LOG.info(
          "[RE] Failed to build target [%s] with exit code [%d]. "
              + "stdout: [%s] stderr: [%s] metadata: [%s] action: [%s]",
          buildTarget.getFullyQualifiedName(),
          exitCode,
          stdout.orElse("<empty>"),
          stderr.orElse("<empty>"),
          metadataProvider.toString(),
          actionDigest);
      throw StepFailedException.createForFailingStepWithExitCode(
          new AbstractExecutionStep("remote_execution") {
            @Override
            public StepExecutionResult execute(ExecutionContext context) {
              throw new RuntimeException();
            }
          },
          strategyContext.getExecutionContext(),
          ImmutableStepExecutionResult.builder().setExitCode(exitCode).setStderr(stderr).build());
    }

    try (Scope ignored1 =
        guardContext.enterState(State.DELETING_STALE_OUTPUTS, Optional.of(actionDigest))) {
      for (Path path : actionOutputs) {
        MostFiles.deleteRecursivelyIfExists(mbrHelper.getCellPathPrefix().resolve(path));
      }
    }

    Scope materializationScope =
        guardContext.enterState(State.MATERIALIZING_OUTPUTS, Optional.of(actionDigest));

    List<Protocol.OutputFile> files = new ArrayList<>();
    ListenableFuture<Unit> metadata = stripMetadata(result.getOutputFiles(), files, buildRule);
    ListenableFuture<Unit> materializationFuture =
        executionClients
            .getContentAddressedStorage()
            .materializeOutputs(
                result.getOutputDirectories(),
                files,
                new FilesystemFileMaterializer(mbrHelper.getCellPathPrefix()));
    materializationFuture.addListener(materializationScope::close, MoreExecutors.directExecutor());
    return Futures.whenAllSucceed(ImmutableList.of(metadata, materializationFuture))
        .call(() -> Optional.of(result), MoreExecutors.directExecutor());
  }

  private ListenableFuture<Unit> stripMetadata(
      List<Protocol.OutputFile> outputFiles, List<Protocol.OutputFile> files, BuildRule buildRule) {
    Digest metadataDigest = null;
    for (Protocol.OutputFile file : outputFiles) {
      if (file.getPath().contains(ModernBuildRuleRemoteExecutionHelper.METADATA_PATH.toString())) {
        metadataDigest = file.getDigest();
      } else {
        files.add(file);
      }
    }
    if (metadataDigest != null) {
      return Futures.transform(
          executionClients.getContentAddressedStorage().fetch(metadataDigest),
          bytes -> {
            long duration = Long.valueOf(StandardCharsets.UTF_8.decode(bytes).toString());
            RemoteBuildRuleExecutionEvent.postEvent(eventBus, buildRule, duration);
            return null;
          },
          MoreExecutors.directExecutor());
    }

    return Futures.immediateFuture(null);
  }

  private static class RuleContext {
    // guard should only be set once. The left value indicates that it has been cancelled and holds
    // the reason, a right value indicates that it has passed the point of no return and can no
    // longer be cancelled.
    AtomicReference<Either<Throwable, Object>> guard = new AtomicReference<>();
    ConcurrentLinkedQueue<Consumer<Throwable>> callbackQueue = new ConcurrentLinkedQueue<>();
    State actionState;
    State prevState;
    final BuildTarget buildTarget;
    final BuckEventBus eventBus;
    Map<State, Long> timeMsInState;
    Map<State, Long> timeMsAfterState;
    long prevStateTime;

    public RuleContext(BuckEventBus eventBus, BuildTarget target) {
      this.buildTarget = target;
      this.eventBus = eventBus;
      this.actionState = State.WAITING;
      this.prevState = State.WAITING;
      this.timeMsInState = new ConcurrentHashMap<>();
      this.timeMsAfterState = new ConcurrentHashMap<>();
      this.prevStateTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    public boolean isCancelled() {
      return guard.get() != null && guard.get().isLeft();
    }

    public Throwable getCancelReason() {
      Verify.verify(isCancelled());
      return guard.get().getLeft();
    }

    public void cancel(Throwable reason) {
      guard.compareAndSet(null, Either.ofLeft(reason));
      if (isCancelled()) {
        processCallbackQueue();
      }
    }

    public boolean tryStart() {
      return guard.compareAndSet(null, Either.ofRight(new Object()));
    }

    public void onCancellation(Consumer<Throwable> cancelCallback) {
      callbackQueue.add(cancelCallback);
      if (isCancelled()) {
        processCallbackQueue();
      }
    }

    private void processCallbackQueue() {
      Throwable cancelReason = getCancelReason();
      while (!callbackQueue.isEmpty()) {
        Consumer<Throwable> callback = callbackQueue.poll();
        if (callback == null) {
          break;
        }
        try {
          callback.accept(cancelReason);
        } catch (Exception e) {
          LOG.warn(e, "Unexpected exception while processing cancellation callbacks.");
        }
      }
    }

    public Scope enterState(State state, Optional<Digest> actionDigest) {
      Preconditions.checkState(
          state.ordinal() > prevState.ordinal(),
          "Cannot Enter State: " + state + " from: " + actionState);
      timeMsAfterState.put(
          prevState, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - prevStateTime);
      actionState = state;
      long startMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
      Scope inner =
          RemoteExecutionActionEvent.sendEvent(eventBus, state, buildTarget, actionDigest);

      return () -> {
        if (actionState != State.WAITING) {
          timeMsInState.put(
              actionState, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - startMs);
          prevState = actionState;
          prevStateTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
          actionState = State.WAITING;
        }
        inner.close();
      };
    }
  }
}
