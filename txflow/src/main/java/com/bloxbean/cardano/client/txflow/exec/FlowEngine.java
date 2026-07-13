package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.quicktx.script.ScriptRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.compile.CompiledTxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionPolicy;
import com.bloxbean.cardano.client.txflow.config.SpendingContentionPolicy;
import com.bloxbean.cardano.client.txflow.resource.FlowResourceCatalog;
import com.bloxbean.cardano.client.txflow.recovery.FlowRecoveryCoordinator;
import com.bloxbean.cardano.client.txflow.recovery.FlowRecoveryRequest;
import com.bloxbean.cardano.client.txflow.recovery.FlowRecoveryResult;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.bloxbean.cardano.client.txflow.model.ParameterSpec;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

/**
 * Thread-safe entry point with immutable configuration for compiling and
 * executing portable TxFlow requests.
 *
 * <p>The engine dispatches each accepted execution to a caller-supplied
 * {@link Executor}. It never creates, owns, or shuts down that executor, so
 * applications control concurrency and may supply a virtual-thread executor
 * when running on Java 21 or later. Cancellation is cooperative and is exposed
 * through the returned {@link FlowExecutionHandle}.</p>
 *
 * <p>When a {@link FlowExecutionStore} is configured, signed attempts and
 * lifecycle events are durably ordered under fenced execution and resource
 * leases. Durable mode also requires a caller-owned maintenance executor for
 * lease renewal; it must remain schedulable while flow tasks or spending waits
 * are blocked.</p>
 */
public final class FlowEngine {
    private final UtxoSupplier utxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final TransactionProcessor transactionProcessor;
    private final ChainDataSupplier chainDataSupplier;
    private final Executor executor;
    private final Executor maintenanceExecutor;
    private final Clock clock;
    private final TxFlowCompiler compiler;
    private final FlowResourceCatalog resources;
    private final FlowExecutionPolicy policy;
    private final SignerRegistry signerRegistry;
    private final ScriptRegistry scriptRegistry;
    private final FlowExecutionStore store;
    private final Duration leaseDuration;
    private final String ownerToken;
    private final int maxInMemoryIdempotencyClaims;
    private final Map<String, FlowExecutionHandle> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyClaim> idempotencyClaims = new ConcurrentHashMap<>();
    private final SpendingResourceCoordinator spendingCoordinator;

    private FlowEngine(Builder builder) {
        this.utxoSupplier = Objects.requireNonNull(builder.utxoSupplier, "utxoSupplier");
        this.protocolParamsSupplier = Objects.requireNonNull(builder.protocolParamsSupplier, "protocolParamsSupplier");
        this.transactionProcessor = Objects.requireNonNull(builder.transactionProcessor, "transactionProcessor");
        this.chainDataSupplier = Objects.requireNonNull(builder.chainDataSupplier, "chainDataSupplier");
        this.executor = Objects.requireNonNull(builder.executor,
                "executor must be supplied (use a virtual-thread executor on Java 21 when desired)");
        if (builder.store != null && builder.maintenanceExecutor == null) {
            throw new NullPointerException(
                    "maintenanceExecutor must be supplied for durable execution so lease renewal "
                            + "cannot be starved by blocking flow tasks");
        }
        this.maintenanceExecutor = builder.maintenanceExecutor != null
                ? builder.maintenanceExecutor : this.executor;
        this.clock = builder.clock != null ? builder.clock : Clock.systemUTC();
        this.spendingCoordinator = new SpendingResourceCoordinator(new SystemFlowScheduler(this.clock));
        this.compiler = builder.compiler != null ? builder.compiler : new TxFlowCompiler();
        this.resources = builder.resources;
        this.policy = builder.policy != null ? builder.policy : FlowExecutionPolicy.permissive();
        this.signerRegistry = builder.signerRegistry;
        this.scriptRegistry = builder.scriptRegistry;
        this.store = builder.store;
        this.leaseDuration = builder.leaseDuration != null ? builder.leaseDuration : Duration.ofSeconds(30);
        this.ownerToken = builder.ownerToken != null ? builder.ownerToken : UUID.randomUUID().toString();
        this.maxInMemoryIdempotencyClaims = builder.maxInMemoryIdempotencyClaims;
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (ownerToken.isBlank()) throw new IllegalArgumentException("ownerToken cannot be blank");
        if (maxInMemoryIdempotencyClaims < 1) {
            throw new IllegalArgumentException("maxInMemoryIdempotencyClaims must be positive");
        }
    }

    /**
     * Creates an engine builder around the four backend services needed to
     * build, submit, and observe transactions.
     *
     * @param utxoSupplier UTxO lookup service
     * @param protocolParamsSupplier protocol-parameter service
     * @param transactionProcessor transaction submission service
     * @param chainDataSupplier chain and transaction observation service
     * @return engine builder; an execution {@link Executor} must also be set
     */
    public static Builder builder(UtxoSupplier utxoSupplier,
                                  ProtocolParamsSupplier protocolParamsSupplier,
                                  TransactionProcessor transactionProcessor,
                                  ChainDataSupplier chainDataSupplier) {
        return new Builder(utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier);
    }

    /**
     * Compiles and starts an execution.
     *
     * <p>Compilation and request validation happen before dispatch. Validation,
     * policy, or idempotency failures are represented by an already-completed
     * handle. Accepted work runs on the configured caller-owned executor.
     * Repeating an active execution ID, or a matching idempotency claim, returns
     * the existing logical execution rather than submitting it again.</p>
     *
     * @param request immutable execution request
     * @return handle for cancellation, events, and terminal completion
     */
    public FlowExecutionHandle start(FlowExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        FlowCompilationResult compilation = compiler.compile(FlowCompilationRequest.builder(request.getDefinition())
                .bindings(request.getBindings()).resources(resources).policy(policy).build());
        if (compilation.hasErrors()) {
            String message = compilation.getDiagnostics().toString();
            return completedFailure(request.getExecutionId(), null, "TXFLOW_COMPILATION_FAILED",
                    FlowErrorCategory.VALIDATION, message);
        }
        CompiledTxFlow compiled = compilation.requireCompiledFlow();
        if (store != null) {
            for (ParameterSpec parameter : request.getDefinition().getParameters().values()) {
                if (parameter.isSensitive()
                        && (request.getBindings().get(parameter.getName()).isPresent()
                        || parameter.getDefaultValue() != null)
                        && !request.getSecureBindingReferences().containsKey(parameter.getName())) {
                    return completedFailure(request.getExecutionId(), compiled.getFingerprint(),
                            "TXFLOW_SECURE_BINDING_REFERENCE_REQUIRED", FlowErrorCategory.VALIDATION,
                            "Sensitive binding '" + parameter.getName()
                                    + "' requires an external secure reference for durable execution");
                }
            }
            for (String parameterName : request.getSecureBindingReferences().keySet()) {
                ParameterSpec parameter = request.getDefinition().getParameters().get(parameterName);
                if (parameter == null || !parameter.isSensitive()) {
                    return completedFailure(request.getExecutionId(), compiled.getFingerprint(),
                            "TXFLOW_SECURE_BINDING_REFERENCE_INVALID", FlowErrorCategory.VALIDATION,
                            "Secure binding references may name only sensitive parameters");
                }
            }
        }
        if (request.isConcurrentSpendingAllowed() && !policy.isConcurrentSpendingOptOutAllowed()) {
            return completedFailure(request.getExecutionId(), compiled.getFingerprint(),
                    "TXFLOW_CONCURRENT_SPENDING_FORBIDDEN", FlowErrorCategory.POLICY,
                    "Server policy does not allow the concurrent-spending opt-out");
        }
        String requestFingerprint = requestFingerprint(request, compiled);

        if (store == null && request.getIdempotencyKey() != null) {
            String claimKey = request.getIdempotencyNamespace() + "\u0000" + request.getIdempotencyKey();
            synchronized (idempotencyClaims) {
                IdempotencyClaim existing = idempotencyClaims.get(claimKey);
                if (existing != null) {
                    if (existing.fingerprint.equals(requestFingerprint)) return existing.handle;
                    return completedFailure(request.getExecutionId(), compiled.getFingerprint(),
                            "TXFLOW_IDEMPOTENCY_CONFLICT", FlowErrorCategory.VALIDATION,
                            "Idempotency key is already associated with a different execution request");
                }
                if (idempotencyClaims.size() >= maxInMemoryIdempotencyClaims) {
                    return completedFailure(request.getExecutionId(), compiled.getFingerprint(),
                            "TXFLOW_IDEMPOTENCY_CAPACITY_EXCEEDED", FlowErrorCategory.RESOURCE,
                            "In-memory idempotency claim capacity is exhausted; configure a durable store "
                                    + "or increase maxInMemoryIdempotencyClaims");
                }
                FlowExecutionHandle created = createHandle(request, compiled);
                idempotencyClaims.put(claimKey, new IdempotencyClaim(requestFingerprint, created));
                return created;
            }
        }
        return createHandle(request, compiled);
    }

    /**
     * Starts an execution and blocks the calling thread for its terminal result.
     *
     * <p>Execution still occurs on the configured executor; this method does not
     * create a private worker.</p>
     *
     * @param request immutable execution request
     * @return terminal execution result
     */
    public FlowExecutionResult executeSync(FlowExecutionRequest request) {
        return start(request).await();
    }

    /**
     * Compiles and validates a request without submitting transactions.
     *
     * @param request request whose definition and bindings should be checked
     * @return compilation result including diagnostics
     */
    public FlowCompilationResult preflight(FlowExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        return compiler.compile(FlowCompilationRequest.builder(request.getDefinition())
                .bindings(request.getBindings()).resources(resources).policy(policy).build());
    }

    /**
     * Compiles and validates a lower-level compilation request without executing
     * the resulting plan.
     *
     * @param request compilation inputs
     * @return compilation result including diagnostics
     */
    public FlowCompilationResult preflight(FlowCompilationRequest request) {
        return compiler.compile(Objects.requireNonNull(request, "request"));
    }

    /**
     * Reconciles one uncertain attempt by transaction hash before any rebuild is
     * considered.
     *
     * <p>If the recovery request identifies a durable execution instead of
     * carrying an attempt, the engine resolves the persisted signed payload and
     * performs recovery under the execution's leases. Recovery may resubmit only
     * the verified, identical signed payload; an inconclusive observation
     * remains recovery-required.</p>
     *
     * @param request attempt or durable execution selection to reconcile
     * @return recovery disposition and resulting attempt state
     */
    public FlowRecoveryResult recover(FlowRecoveryRequest request) {
        Objects.requireNonNull(request, "request");
        FlowRecoveryRequest resolved = request.attempt() != null
                ? request : request.withAttempt(resolveRecoveryAttempt(request));
        if (store == null || request.executionId() == null) {
            return new FlowRecoveryCoordinator(chainDataSupplier, transactionProcessor, clock).recover(resolved);
        }
        return recoverDurably(request.executionId(), resolved);
    }

    @SuppressWarnings("unchecked")
    private FlowRecoveryResult recoverDurably(String executionId, FlowRecoveryRequest request) {
        FlowExecutionSnapshot initial = store.get(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown execution: " + executionId));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, leaseDuration, maintenanceExecutor);
        try {
            leases.acquireExecution(executionId, ownerToken);
            leases.startRenewal();
            Object resources = initial.data().get("spending_resources");
            if (resources instanceof Iterable) {
                List<String> identities = new ArrayList<>();
                ((Iterable<?>) resources).forEach(value -> identities.add(String.valueOf(value)));
                identities.stream().sorted().forEach(identity -> {
                    leases.checkHealthy();
                    leases.acquireResource(identity, executionId, ownerToken);
                });
            }
            long sequence = initial.lastSequence();
            FlowEvent started = new FlowEvent(++sequence, executionId, FlowEventType.RECOVERY_STARTED,
                    clock.instant(), request.attempt().stepId(),
                    request.attempt().signedPayload() != null
                            ? request.attempt().signedPayload().transactionHash() : null,
                    Map.of("attempt", request.attempt().attemptNumber()));
            FlowExecutionSnapshot recovering = store.append(executionId, initial.revision(),
                    leases.fence(), List.of(started), current -> current.withState(
                            FlowExecutionState.RUNNING, clock.instant(), current.data()));

            FlowRecoveryResult result = new FlowRecoveryCoordinator(
                    chainDataSupplier, transactionProcessor, clock).recover(request);
            FlowEventType type = result.state() == AttemptState.RECOVERY_REQUIRED
                    ? FlowEventType.RECOVERY_REQUIRED : FlowEventType.RECOVERY_COMPLETED;
            FlowEvent completed = new FlowEvent(++sequence, executionId, type, clock.instant(),
                    request.attempt().stepId(), result.transactionHash(), Map.of(
                    "attempt", request.attempt().attemptNumber(),
                    "state", result.state().name(), "resubmitted",
                    result.identicalPayloadResubmitted()));
            store.append(executionId, recovering.revision(), leases.fence(), List.of(completed), current -> {
                Map<String, Object> data = new java.util.LinkedHashMap<>(current.data());
                Map<String, FlowAttemptSnapshot> attempts = new java.util.LinkedHashMap<>();
                Object existing = data.get(DurableExecutionPersistence.ATTEMPTS_KEY);
                if (existing instanceof Map) {
                    attempts.putAll((Map<String, FlowAttemptSnapshot>) existing);
                }
                String key = request.attempt().stepId() + "#" + request.attempt().attemptNumber();
                FlowAttemptSnapshot old = request.attempt();
                List<com.bloxbean.cardano.client.txflow.store.InclusionRecord> inclusions =
                        new ArrayList<>(old.inclusions());
                if (result.inclusion() != null && inclusions.stream().noneMatch(inclusion ->
                        inclusion.blockHeight() == result.inclusion().blockHeight()
                                && Objects.equals(inclusion.blockHash(), result.inclusion().blockHash()))) {
                    inclusions.add(result.inclusion());
                }
                attempts.put(key, new FlowAttemptSnapshot(old.stepId(), old.attemptNumber(),
                        result.state(), old.signedPayload(), old.validFromSlot(), old.validToSlot(),
                        old.spentInputs(), inclusions, clock.instant(),
                        result.error() != null ? result.error().code() : null));
                data.put(DurableExecutionPersistence.ATTEMPTS_KEY, Map.copyOf(attempts));
                FlowExecutionState state = result.state() == AttemptState.RECOVERY_REQUIRED
                        ? FlowExecutionState.RECOVERY_REQUIRED : FlowExecutionState.RUNNING;
                return current.withState(state, clock.instant(), data);
            });
            return result;
        } finally {
            leases.close();
        }
    }

    private FlowAttemptSnapshot resolveRecoveryAttempt(FlowRecoveryRequest request) {
        if (store == null) {
            throw new IllegalStateException("A FlowExecutionStore is required to recover by execution ID");
        }
        List<FlowAttemptSnapshot> attempts = storedAttempts(request.executionId());
        return attempts.stream()
                .filter(attempt -> request.stepId() == null || request.stepId().equals(attempt.stepId()))
                .filter(attempt -> request.attemptNumber() == null
                        || request.attemptNumber() == attempt.attemptNumber())
                .max(Comparator.comparingInt(FlowAttemptSnapshot::attemptNumber))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No matching persisted attempt for execution " + request.executionId()));
    }

    private FlowExecutionHandle createHandle(FlowExecutionRequest request, CompiledTxFlow compiled) {
        synchronized (activeExecutions) {
            FlowExecutionHandle active = activeExecutions.get(request.getExecutionId());
            if (active != null) return active;
            if (store != null) {
                try {
                    String namespace = request.getIdempotencyNamespace() != null
                            ? request.getIdempotencyNamespace() : "_execution";
                    String key = request.getIdempotencyKey() != null
                            ? request.getIdempotencyKey() : request.getExecutionId();
                    IdempotencyClaimResult claim = store.createOrGet(namespace, key,
                            initialSnapshot(request, compiled));
                    if (!claim.created()) {
                        FlowExecutionHandle claimedActive = activeExecutions.get(claim.snapshot().executionId());
                        return claimedActive != null ? claimedActive : handleForStoredSnapshot(claim.snapshot());
                    }
                } catch (FlowStoreException failure) {
                    FlowErrorCategory category = "TXFLOW_IDEMPOTENCY_CONFLICT".equals(failure.getCode())
                            || "TXFLOW_EXECUTION_ID_CONFLICT".equals(failure.getCode())
                            ? FlowErrorCategory.VALIDATION : FlowErrorCategory.PERSISTENCE;
                    return completedFailure(request.getExecutionId(), compiled.getFingerprint(),
                            failure.getCode(), category, failure.getMessage());
                }
            }

            CompletableFuture<FlowExecutionResult> completion = new CompletableFuture<>();
            AtomicBoolean cancelled = new AtomicBoolean();
            ExecutionJournalSession journal = new ExecutionJournalSession(
                    store, request.getExecutionId(), clock);
            FlowExecutionHandle handle = new FlowExecutionHandle(
                    request.getExecutionId(), completion, cancelled, journal.events());
            activeExecutions.put(request.getExecutionId(), handle);
            journal.record(FlowEventType.EXECUTION_CREATED, null, null, Map.of());
            journal.record(FlowEventType.COMPILATION_COMPLETED, null, null,
                    Map.of("fingerprint", compiled.getFingerprint()));
            executor.execute(() -> run(request, compiled, handle, completion, cancelled, journal));
            return handle;
        }
    }

    private FlowExecutionSnapshot initialSnapshot(FlowExecutionRequest request, CompiledTxFlow compiled) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("spending_resources", effectiveSpendingResources(request, compiled));
        data.put("concurrent_spending", request.isConcurrentSpendingAllowed());
        data.put("bindings", persistedBindings(request));
        return new FlowExecutionSnapshot(
                request.getExecutionId(), compiled.getFingerprint(),
                requestFingerprint(request, compiled),
                FlowExecutionState.CREATED, 0, 0, 0, clock.instant(), data);
    }

    private List<PersistedBinding> persistedBindings(FlowExecutionRequest request) {
        List<PersistedBinding> bindings = new ArrayList<>();
        for (ParameterSpec parameter : request.getDefinition().getParameters().values()) {
            Object value = request.getBindings().get(parameter.getName())
                    .orElse(parameter.getDefaultValue());
            if (value == null) continue;
            if (parameter.isSensitive()) {
                bindings.add(new PersistedBinding(parameter.getName(), parameter.getType().name(),
                        null, request.getSecureBindingReferences().get(parameter.getName()),
                        SignedPayloadVerifier.sha256(String.valueOf(value)), "***"));
            } else {
                bindings.add(new PersistedBinding(parameter.getName(), parameter.getType().name(),
                        value, null, SignedPayloadVerifier.sha256(String.valueOf(value)), String.valueOf(value)));
            }
        }
        return List.copyOf(bindings);
    }

    private FlowExecutionHandle handleForStoredSnapshot(FlowExecutionSnapshot snapshot) {
        Instant now = clock.instant();
        boolean terminal = snapshot.state() == FlowExecutionState.COMPLETED
                || snapshot.state() == FlowExecutionState.FAILED
                || snapshot.state() == FlowExecutionState.CANCELLED
                || snapshot.state() == FlowExecutionState.ROLLED_BACK
                || snapshot.state() == FlowExecutionState.PARTIALLY_COMPLETED;
        FlowExecutionState state = terminal ? snapshot.state() : FlowExecutionState.RECOVERY_REQUIRED;
        FlowError error = terminal && state == FlowExecutionState.COMPLETED ? null
                : new FlowError(terminal ? "TXFLOW_STORED_EXECUTION_TERMINAL" : "TXFLOW_RECOVERY_REQUIRED",
                terminal ? FlowErrorCategory.INTERNAL : FlowErrorCategory.RECOVERY,
                terminal ? "The idempotent execution is already terminal"
                        : "The idempotent execution exists in durable storage and must be recovered",
                null, !terminal);
        FlowExecutionResult result = new FlowExecutionResult(snapshot.executionId(),
                snapshot.definitionFingerprint(), state, List.of(), error,
                snapshot.updatedAt(), now);
        return new FlowExecutionHandle(snapshot.executionId(), CompletableFuture.completedFuture(result),
                new AtomicBoolean(), Collections.synchronizedList(new ArrayList<>()));
    }

    private void run(FlowExecutionRequest request, CompiledTxFlow compiled,
                     FlowExecutionHandle handle, CompletableFuture<FlowExecutionResult> completion,
                     AtomicBoolean cancelled, ExecutionJournalSession journal) {
        SpendingResourceCoordinator.Acquisition spendingAcquisition = null;
        DurableLeaseGuard leases = store != null
                ? new DurableLeaseGuard(store, clock, leaseDuration, maintenanceExecutor) : null;
        Instant started = clock.instant();
        try {
            if (store != null) {
                journal.attach(leases);
                leases.acquireExecution(request.getExecutionId(), ownerToken);
                // The in-process spending queue may wait longer than the durable lease.
                // Renew immediately so the execution fence remains valid while queued.
                leases.startRenewal();
            }
            Set<String> spendingResources = effectiveSpendingResources(request, compiled);
            boolean serializeSpending = policy.getSpendingContention() != SpendingContentionPolicy.ALLOW
                    && !request.isConcurrentSpendingAllowed();
            if (serializeSpending && !spendingResources.isEmpty()) {
                journal.record(FlowEventType.EXECUTION_QUEUED, null, null,
                        Map.of("resources", spendingResources));
            }
            spendingAcquisition = spendingCoordinator.acquire(spendingResources, policy,
                    request.isConcurrentSpendingAllowed(), cancelled);
            for (String identity : spendingAcquisition.identities()) {
                if (store != null) {
                    leases.checkHealthy();
                    leases.acquireResource(identity, request.getExecutionId(), ownerToken);
                }
            }
            if (cancelled.get()) {
                FlowExecutionResult cancelledResult = cancelledResult(
                        request, compiled, handle, journal, started);
                if (store != null) {
                    journal.persist(FlowExecutionState.CANCELLED, data -> { });
                }
                completion.complete(cancelledResult);
                return;
            }

            journal.record(FlowEventType.EXECUTION_STARTED, null, null, Map.of());
            if (store != null) {
                journal.persist(FlowExecutionState.RUNNING, data -> { });
            }
            FlowExecutor facade = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                            transactionProcessor, chainDataSupplier)
                    .withSignerRegistry(signerRegistry)
                    .withScriptRegistry(scriptRegistry)
                    .withListener(eventListener(journal));
            if (store != null) {
                facade.withPersistencePort(new DurableExecutionPersistence(
                        journal, compiled.getExplicitConsumers()));
            }
            FlowResult legacy = facade.executeSync(compiled.getExecutionPlan(),
                    () -> cancelled.get() || (leases != null && leases.hasFailed()));
            if (leases != null) leases.checkHealthy();
            FlowExecutionState state = toState(legacy.getStatus());
            if (state == FlowExecutionState.FAILED
                    && legacy.getStepResults().stream().anyMatch(
                    com.bloxbean.cardano.client.txflow.result.FlowStepResult::isSuccessful)) {
                state = FlowExecutionState.PARTIALLY_COMPLETED;
            }
            FlowStoreException persistenceFailure = findCause(
                    legacy.getError(), FlowStoreException.class);
            if (persistenceFailure != null) state = FlowExecutionState.RECOVERY_REQUIRED;
            ReconciliationUncertainException reconciliationFailure = findCause(
                    legacy.getError(), ReconciliationUncertainException.class);
            if (reconciliationFailure != null) state = FlowExecutionState.RECOVERY_REQUIRED;
            boolean pauseRollback = findCause(legacy.getError(), RollbackException.class) != null
                    && compiled.getExecutionPlan().getExecutionSettings().getRollbackPolicy() != null
                    && compiled.getExecutionPlan().getExecutionSettings().getRollbackPolicy().action()
                    == com.bloxbean.cardano.client.txflow.config.RollbackAction.PAUSE_FOR_RECOVERY;
            if (pauseRollback) {
                state = FlowExecutionState.RECOVERY_REQUIRED;
            }
            FlowError error = legacy.getError() != null
                    ? new FlowError(persistenceFailure != null ? persistenceFailure.getCode()
                            : reconciliationFailure != null || pauseRollback
                                ? "TXFLOW_RECOVERY_REQUIRED" : "TXFLOW_EXECUTION_FAILED",
                            pauseRollback ? FlowErrorCategory.RECOVERY : classify(legacy.getError()),
                            legacy.getError().getMessage(), null,
                            reconciliationFailure != null || pauseRollback)
                    : null;
            FlowExecutionResult result = new FlowExecutionResult(request.getExecutionId(),
                    compiled.getFingerprint(), state, legacy.getStepResults(),
                    storedAttempts(request.getExecutionId()), error,
                    legacy.getStartedAt() != null ? legacy.getStartedAt() : started,
                    legacy.getCompletedAt() != null ? legacy.getCompletedAt() : clock.instant());
            if (store != null) {
                FlowExecutionState persistedState = state;
                journal.persist(persistedState,
                        data -> data.put("step_count", legacy.getStepResults().size()));
            }
            completion.complete(result);
        } catch (Throwable failure) {
            FlowStoreException storeFailure = findCause(failure, FlowStoreException.class);
            ReconciliationUncertainException reconciliationFailure = findCause(
                    failure, ReconciliationUncertainException.class);
            String code = storeFailure != null
                    ? storeFailure.getCode()
                    : reconciliationFailure != null ? "TXFLOW_RECOVERY_REQUIRED"
                    : failure instanceof SpendingResourceBusyException
                    ? "TXFLOW_RESOURCE_BUSY" : "TXFLOW_ENGINE_FAILURE";
            FlowError error = new FlowError(code, classify(failure),
                    failure.getMessage(), null, reconciliationFailure != null);
            journal.record(reconciliationFailure != null
                            ? FlowEventType.RECOVERY_REQUIRED : FlowEventType.EXECUTION_FAILED,
                    null, null, Map.of("message", String.valueOf(failure.getMessage())));
            FlowExecutionState failureState = storeFailure != null || reconciliationFailure != null
                    ? FlowExecutionState.RECOVERY_REQUIRED : FlowExecutionState.FAILED;
            completion.complete(new FlowExecutionResult(request.getExecutionId(), compiled.getFingerprint(),
                    failureState, List.of(), error, started, clock.instant()));
        } finally {
            if (store != null) {
                leases.close();
            }
            if (spendingAcquisition != null) spendingAcquisition.close();
            activeExecutions.remove(request.getExecutionId(), handle);
        }
    }

    private FlowListener eventListener(ExecutionJournalSession journal) {
        return new FlowListener() {
            @Override public void onStepStarted(FlowStep step, int index, int total) {
                journal.record(FlowEventType.STEP_STARTED, step.getId(), null,
                        Map.of("index", index, "total", total));
            }
            @Override public void onTransactionSubmitted(FlowStep step, String hash) {
                if (store != null) return;
                journal.record(FlowEventType.TRANSACTION_SUBMITTED, step.getId(), hash, Map.of());
            }
            @Override public void onTransactionInBlock(FlowStep step, String hash, long height) {
                if (store != null) return;
                journal.record(FlowEventType.TRANSACTION_IN_BLOCK, step.getId(), hash,
                        Map.of("block_height", height));
            }
            @Override public void onConfirmationDepthChanged(FlowStep step, String hash, int depth, ConfirmationStatus status) {
                if (store != null) return;
                journal.record(FlowEventType.CONFIRMATION_DEPTH_CHANGED, step.getId(), hash,
                        Map.of("depth", depth, "status", status.name()));
            }
            @Override public void onTransactionConfirmed(FlowStep step, String hash) {
                if (store != null) return;
                journal.record(FlowEventType.TRANSACTION_CONFIRMED, step.getId(), hash, Map.of());
            }
            @Override public void onTransactionRolledBack(FlowStep step, String hash, long height) {
                if (store != null) return;
                journal.record(FlowEventType.TRANSACTION_ROLLED_BACK, step.getId(), hash,
                        Map.of("previous_block_height", height));
            }
            @Override public void onTransactionRollbackSuspected(FlowStep step, String hash, long height) {
                journal.record(FlowEventType.TRANSACTION_ROLLBACK_SUSPECTED,
                        step.getId(), hash, Map.of("previous_block_height", height));
            }
            @Override public void onStepCompleted(FlowStep step, com.bloxbean.cardano.client.txflow.result.FlowStepResult result) {
                journal.record(FlowEventType.STEP_COMPLETED, step.getId(),
                        result.getTransactionHash(), Map.of());
            }
            @Override public void onStepFailed(FlowStep step, com.bloxbean.cardano.client.txflow.result.FlowStepResult result) {
                journal.record(FlowEventType.STEP_FAILED, step.getId(),
                        result.getTransactionHash(), Map.of("message",
                                result.getError() != null
                                        ? Objects.toString(result.getError().getMessage(), "unknown")
                                        : "unknown"));
            }
            @Override public void onFlowCompleted(TxFlow flow, FlowResult result) {
                journal.record(FlowEventType.EXECUTION_COMPLETED, null, null, Map.of());
            }
            @Override public void onFlowFailed(TxFlow flow, FlowResult result) {
                FlowEventType type = findCause(result.getError(), ReconciliationUncertainException.class) != null
                        ? FlowEventType.RECOVERY_REQUIRED
                        : result.getStatus() == FlowStatus.CANCELLED
                            ? FlowEventType.EXECUTION_CANCELLED : FlowEventType.EXECUTION_FAILED;
                journal.record(type, null, null, Map.of());
            }
        };
    }

    private FlowExecutionResult cancelledResult(FlowExecutionRequest request, CompiledTxFlow compiled,
                                                FlowExecutionHandle handle,
                                                ExecutionJournalSession journal,
                                                Instant started) {
        Map<String, Object> details = handle.getCancellationReason() != null
                ? Map.of("reason", handle.getCancellationReason()) : Map.of();
        journal.record(FlowEventType.EXECUTION_CANCELLED, null, null, details);
        return new FlowExecutionResult(request.getExecutionId(), compiled.getFingerprint(),
                FlowExecutionState.CANCELLED, List.of(),
                new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                        "Execution cancelled", null, false), started, clock.instant());
    }

    private FlowExecutionHandle completedFailure(String executionId, String fingerprint, String code,
                                                 FlowErrorCategory category, String message) {
        Instant now = clock.instant();
        FlowExecutionResult result = new FlowExecutionResult(executionId, fingerprint,
                FlowExecutionState.FAILED, List.of(), new FlowError(code, category, message, null, false),
                now, now);
        return new FlowExecutionHandle(executionId, CompletableFuture.completedFuture(result),
                new AtomicBoolean(), Collections.synchronizedList(new ArrayList<>()));
    }

    private FlowExecutionState toState(FlowStatus status) {
        if (status == FlowStatus.COMPLETED) return FlowExecutionState.COMPLETED;
        if (status == FlowStatus.CANCELLED) return FlowExecutionState.CANCELLED;
        return FlowExecutionState.FAILED;
    }

    private FlowErrorCategory classify(Throwable failure) {
        if (findCause(failure, FlowStoreException.class) != null) return FlowErrorCategory.PERSISTENCE;
        if (findCause(failure, ReconciliationUncertainException.class) != null) return FlowErrorCategory.RECOVERY;
        if (findCause(failure, SpendingResourceBusyException.class) != null) return FlowErrorCategory.RESOURCE;
        if (findCause(failure, RollbackException.class) != null) return FlowErrorCategory.ROLLBACK;
        if (findCause(failure, ConfirmationTimeoutException.class) != null) return FlowErrorCategory.CONFIRMATION;
        if (findCause(failure, FlowExecutionException.class) != null) return FlowErrorCategory.BUILD;
        return FlowErrorCategory.INTERNAL;
    }

    private <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<FlowAttemptSnapshot> storedAttempts(String executionId) {
        if (store == null) return List.of();
        Object attempts = store.get(executionId).map(snapshot -> snapshot.data().get(
                DurableExecutionPersistence.ATTEMPTS_KEY)).orElse(null);
        if (!(attempts instanceof Map)) return List.of();
        return ((Map<String, FlowAttemptSnapshot>) attempts).values().stream()
                .sorted(Comparator.comparing(FlowAttemptSnapshot::stepId)
                        .thenComparingInt(FlowAttemptSnapshot::attemptNumber))
                .toList();
    }

    private Set<String> effectiveSpendingResources(FlowExecutionRequest request,
                                                    CompiledTxFlow compiled) {
        Set<String> identities = new TreeSet<>(compiled.getSpendingResources());
        identities.addAll(request.getSpendingResources());
        return Collections.unmodifiableSet(identities);
    }

    private String requestFingerprint(FlowExecutionRequest request, CompiledTxFlow compiled) {
        return compiled.getFingerprint() + "|" + effectiveSpendingResources(request, compiled)
                + "|concurrent=" + request.isConcurrentSpendingAllowed()
                + "|secure=" + new java.util.TreeMap<>(request.getSecureBindingReferences());
    }

    private record IdempotencyClaim(String fingerprint, FlowExecutionHandle handle) {}

    /**
     * Builder for application-owned engine dependencies and execution policy.
     * Instances are mutable and not thread-safe.
     */
    public static final class Builder {
        private final UtxoSupplier utxoSupplier;
        private final ProtocolParamsSupplier protocolParamsSupplier;
        private final TransactionProcessor transactionProcessor;
        private final ChainDataSupplier chainDataSupplier;
        private Executor executor;
        private Executor maintenanceExecutor;
        private Clock clock;
        private TxFlowCompiler compiler;
        private FlowResourceCatalog resources;
        private FlowExecutionPolicy policy;
        private SignerRegistry signerRegistry;
        private ScriptRegistry scriptRegistry;
        private FlowExecutionStore store;
        private Duration leaseDuration;
        private String ownerToken;
        private int maxInMemoryIdempotencyClaims = 10_000;

        private Builder(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                        TransactionProcessor transactionProcessor, ChainDataSupplier chainDataSupplier) {
            this.utxoSupplier = utxoSupplier;
            this.protocolParamsSupplier = protocolParamsSupplier;
            this.transactionProcessor = transactionProcessor;
            this.chainDataSupplier = chainDataSupplier;
        }
        /**
         * Sets the executor used to run flow tasks.
         *
         * <p>The application retains ownership and must shut it down when
         * appropriate. Any {@link Executor} is accepted, including a Java 21
         * virtual-thread executor.</p>
         *
         * @param value caller-owned execution executor
         * @return this builder
         */
        public Builder executor(Executor value) { this.executor = value; return this; }

        /**
         * Sets the executor used for durable lease-renewal work.
         *
         * <p>Durable mode requires this executor. Provision it independently so
         * blocking execution tasks cannot starve lease renewal. The engine does
         * not shut it down.</p>
         *
         * @param value caller-owned maintenance executor
         * @return this builder
         */
        public Builder maintenanceExecutor(Executor value) { this.maintenanceExecutor = value; return this; }

        /**
         * Sets the wall clock used for execution and persisted-event timestamps.
         *
         * @param value wall clock
         * @return this builder
         */
        public Builder clock(Clock value) { this.clock = value; return this; }

        /**
         * Replaces the default flow compiler.
         *
         * @param value compiler implementation
         * @return this builder
         */
        public Builder compiler(TxFlowCompiler value) { this.compiler = value; return this; }

        /**
         * Supplies the server-owned resources available during compilation.
         *
         * @param value resource catalog
         * @return this builder
         */
        public Builder resources(FlowResourceCatalog value) { this.resources = value; return this; }

        /**
         * Sets the server execution and safety policy.
         *
         * @param value execution policy
         * @return this builder
         */
        public Builder policy(FlowExecutionPolicy value) { this.policy = value; return this; }

        /**
         * Supplies the registry used to resolve named signers.
         *
         * @param value signer registry
         * @return this builder
         */
        public Builder signerRegistry(SignerRegistry value) { this.signerRegistry = value; return this; }

        /**
         * Supplies the registry used to resolve named scripts.
         *
         * @param value script registry
         * @return this builder
         */
        public Builder scriptRegistry(ScriptRegistry value) { this.scriptRegistry = value; return this; }

        /**
         * Enables durable execution, idempotency, event journaling, and fenced
         * leases through the supplied store.
         *
         * @param value durable execution store
         * @return this builder
         */
        public Builder store(FlowExecutionStore value) { this.store = value; return this; }

        /**
         * Sets the lifetime of execution and spending-resource leases.
         *
         * @param value positive lease duration
         * @return this builder
         */
        public Builder leaseDuration(Duration value) { this.leaseDuration = value; return this; }

        /**
         * Sets the durable lease owner identity used by this engine instance.
         *
         * @param value non-blank, process-unique owner token
         * @return this builder
         */
        public Builder ownerToken(String value) { this.ownerToken = value; return this; }

        /**
         * Bounds non-durable in-memory idempotency claims.
         *
         * @param value positive maximum claim count
         * @return this builder
         */
        public Builder maxInMemoryIdempotencyClaims(int value) {
            this.maxInMemoryIdempotencyClaims = value;
            return this;
        }
        /**
         * Builds a reusable engine and validates executor and lease settings.
         *
         * @return thread-safe engine with immutable configuration
         */
        public FlowEngine build() { return new FlowEngine(this); }
    }
}
