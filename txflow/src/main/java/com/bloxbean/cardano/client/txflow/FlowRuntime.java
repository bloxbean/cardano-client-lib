package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.resource.ResourceRef;
import com.bloxbean.cardano.client.txflow.stream.TxFlowStream;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optional managed composition root for scripts, tutorials, CLI tools, and
 * small applications.
 * <p>
 * A runtime owns one ordinary {@link FlowEngine}, the executors it creates,
 * and the ordinary {@link TxFlowStream} instances opened through it. It adds no
 * planning or transaction behavior: {@link #engine()} returns the exact engine
 * used by every stream. Advanced/server applications remain free to construct
 * engines and streams directly with caller-owned resources.
 * <p>
 * Closing a runtime first rejects new opens, then gracefully closes tracked
 * streams in reverse open order, followed by its maintenance and task
 * executors. A stream close remains unbounded so funds-critical accepted work
 * is not silently interrupted.
 */
public final class FlowRuntime implements AutoCloseable {
    private final Object lifecycleLock = new Object();
    private final FlowEngine engine;
    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService maintenanceExecutor;
    private final StreamOpener streamOpener;
    private final List<TxFlowStream> streams = new ArrayList<>();
    private final CompletableFuture<Throwable> closeCompletion = new CompletableFuture<>();
    private boolean closing;
    private boolean closed;
    private Thread closingThread;

    private FlowRuntime(FlowEngine engine, ExecutorService taskExecutor,
                        ScheduledExecutorService maintenanceExecutor,
                        StreamOpener streamOpener) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor");
        this.maintenanceExecutor = Objects.requireNonNull(
                maintenanceExecutor, "maintenanceExecutor");
        this.streamOpener = Objects.requireNonNull(streamOpener, "streamOpener");
    }

    /**
     * Creates a narrow managed-runtime builder around a backend service.
     *
     * @param backend backend used by the runtime-owned engine
     * @return runtime builder
     */
    public static Builder builder(BackendService backend) {
        return new Builder(Objects.requireNonNull(backend, "backend"));
    }

    /**
     * Returns the exact ordinary engine owned by this runtime.
     *
     * @return runtime engine
     */
    public FlowEngine engine() {
        return engine;
    }

    /**
     * Builds, starts, and tracks an ordinary stream using safe defaults and the
     * runtime-owned executors. Opens are serialized with close so a race either
     * returns a tracked stream that close will visit or rejects before startup;
     * it cannot leak an untracked live stream.
     *
     * @param streamId stable stream id
     * @return started stream
     * @throws IllegalStateException when runtime close has begun
     */
    public TxFlowStream open(String streamId) {
        Objects.requireNonNull(streamId, "streamId");
        synchronized (lifecycleLock) {
            if (closing || closed) {
                throw new IllegalStateException("FlowRuntime is closing or closed; no new streams"
                        + " may be opened");
            }
            TxFlowStream stream = Objects.requireNonNull(streamOpener.open(
                    streamId, engine, maintenanceExecutor), "streamOpener result");
            streams.add(stream);
            return stream;
        }
    }

    /**
     * Closes tracked streams in reverse open order, then shuts down the
     * maintenance executor followed by the task executor. Cleanup continues
     * after failures; later failures are suppressed on the first.
     */
    @Override
    public void close() {
        List<TxFlowStream> toClose;
        boolean awaitConcurrentClose = false;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            if (closing) {
                if (closingThread == Thread.currentThread()) {
                    return;
                }
                awaitConcurrentClose = true;
                toClose = List.of();
            } else {
                toClose = new ArrayList<>(streams);
                streams.clear();
                closing = true;
                closingThread = Thread.currentThread();
            }
        }
        if (awaitConcurrentClose) {
            rethrow(closeCompletion.join());
            return;
        }

        Throwable failure = null;
        for (int index = toClose.size() - 1; index >= 0; index--) {
            try {
                toClose.get(index).close();
            } catch (RuntimeException | Error closeFailure) {
                failure = collect(failure, closeFailure);
            }
        }
        try {
            maintenanceExecutor.shutdown();
        } catch (RuntimeException | Error shutdownFailure) {
            failure = collect(failure, shutdownFailure);
        }
        try {
            taskExecutor.shutdown();
        } catch (RuntimeException | Error shutdownFailure) {
            failure = collect(failure, shutdownFailure);
        }
        synchronized (lifecycleLock) {
            closed = true;
            closingThread = null;
        }
        closeCompletion.complete(failure);
        rethrow(failure);
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
    }

    private static Throwable collect(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != first) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** Opens an ordinary stream; replaceable only by package-level lifecycle tests. */
    @FunctionalInterface
    interface StreamOpener {
        TxFlowStream open(String streamId, FlowEngine engine,
                          ScheduledExecutorService maintenanceExecutor);
    }

    /** Package-level lifecycle-test constructor; production uses {@link Builder}. */
    static FlowRuntime forTesting(FlowEngine engine, ExecutorService taskExecutor,
                                  ScheduledExecutorService maintenanceExecutor,
                                  StreamOpener streamOpener) {
        return new FlowRuntime(engine, taskExecutor, maintenanceExecutor, streamOpener);
    }

    /** Narrow builder for runtime-owned resources and signer registrations. */
    public static final class Builder {
        private static final int DEFAULT_MAINTENANCE_THREADS = 2;
        private final BackendService backend;
        private final Set<String> signerRefs = new HashSet<>();
        private final Map<String, Account> accounts = new LinkedHashMap<>();
        private final Map<String, Wallet> wallets = new LinkedHashMap<>();
        private String name = "default";
        private int taskParallelism = defaultTaskParallelism();
        private int maintenanceThreads = DEFAULT_MAINTENANCE_THREADS;

        private Builder(BackendService backend) {
            this.backend = backend;
        }

        /**
         * Sets the identifier embedded in runtime-owned platform thread names.
         *
         * @param value non-blank identifier containing letters, digits,
         *        {@code .}, {@code _}, or {@code -}, at most 64 characters
         * @return this builder
         */
        public Builder name(String value) {
            if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException("runtime name must contain 1-64 letters,"
                        + " digits, '.', '_', or '-' characters");
            }
            this.name = value;
            return this;
        }

        /**
         * Sets the fixed task-pool size used on Java 17-20. Java 21 and newer
         * use one virtual thread per task instead.
         *
         * @param value positive platform task-thread count
         * @return this builder
         */
        public Builder taskParallelism(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("taskParallelism must be positive");
            }
            this.taskParallelism = value;
            return this;
        }

        /**
         * Sets the number of platform scheduled-maintenance threads.
         *
         * @param value positive maintenance-thread count
         * @return this builder
         */
        public Builder maintenanceThreads(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maintenanceThreads must be positive");
            }
            this.maintenanceThreads = value;
            return this;
        }

        /**
         * Registers an account signer on the runtime-owned engine.
         *
         * @param ref unique canonical {@code account://} reference
         * @param account signing account
         * @return this builder
         */
        public Builder account(String ref, Account account) {
            String canonical = requireSignerRef(ref, "account");
            Objects.requireNonNull(account, "account");
            addSignerRef(canonical);
            accounts.put(canonical, account);
            return this;
        }

        /**
         * Registers a wallet signer on the runtime-owned engine.
         *
         * @param ref unique canonical {@code wallet://} reference
         * @param wallet signing wallet
         * @return this builder
         */
        public Builder wallet(String ref, Wallet wallet) {
            String canonical = requireSignerRef(ref, "wallet");
            Objects.requireNonNull(wallet, "wallet");
            addSignerRef(canonical);
            wallets.put(canonical, wallet);
            return this;
        }

        /**
         * Creates the engine and runtime-owned task and maintenance executors.
         * If construction fails, every executor already created is shut down.
         *
         * @return managed runtime
         */
        public FlowRuntime build() {
            ExecutorService taskExecutor = null;
            ScheduledExecutorService maintenanceExecutor = null;
            try {
                taskExecutor = RuntimeExecutors.taskExecutor(name, taskParallelism);
                maintenanceExecutor = RuntimeExecutors.maintenanceExecutor(
                        name, maintenanceThreads);
                FlowEngine.Builder engineBuilder = FlowEngine.builder(backend)
                        .executor(taskExecutor)
                        .maintenanceExecutor(maintenanceExecutor);
                accounts.forEach(engineBuilder::account);
                wallets.forEach(engineBuilder::wallet);
                FlowEngine engine = engineBuilder.build();
                return new FlowRuntime(engine, taskExecutor, maintenanceExecutor,
                        (streamId, flowEngine, scheduler) ->
                                TxFlowStream.builder(streamId, flowEngine)
                                        .maintenanceExecutor(scheduler)
                                        .open());
            } catch (RuntimeException | Error failure) {
                if (maintenanceExecutor != null) {
                    try {
                        maintenanceExecutor.shutdownNow();
                    } catch (RuntimeException | Error cleanupFailure) {
                        if (cleanupFailure != failure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                }
                if (taskExecutor != null) {
                    try {
                        taskExecutor.shutdownNow();
                    } catch (RuntimeException | Error cleanupFailure) {
                        if (cleanupFailure != failure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                }
                throw failure;
            }
        }

        private void addSignerRef(String ref) {
            if (!signerRefs.add(ref)) {
                throw new IllegalArgumentException(
                        "A signer is already registered for resource reference '" + ref + "'");
            }
        }

        private static String requireSignerRef(String ref, String scheme) {
            if (ref == null || ref.isBlank()) {
                throw new IllegalArgumentException(
                        scheme + " reference cannot be null, empty, or whitespace");
            }
            ResourceRef parsed;
            try {
                parsed = ResourceRef.of(ref);
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Expected an absolute " + scheme
                        + ":// resource reference, but got '" + ref + "'", invalid);
            }
            String canonical = parsed.value();
            if (!canonical.regionMatches(true, 0, scheme + "://", 0, scheme.length() + 3)) {
                throw new IllegalArgumentException("Expected a " + scheme
                        + ":// resource reference, but got '" + ref + "'");
            }
            if (!canonical.equals(ref)) {
                throw new IllegalArgumentException("Resource reference '" + ref
                        + "' is not canonical; use '" + canonical + "'");
            }
            return canonical;
        }

        private static int defaultTaskParallelism() {
            return Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors()));
        }
    }

    /** Java-version-aware executor creation kept behind a Java 17 binary seam. */
    static final class RuntimeExecutors {
        private RuntimeExecutors() {
        }

        static ExecutorService taskExecutor(String runtimeName, int parallelism) {
            if (Runtime.version().feature() >= 21) {
                ExecutorService virtual = virtualThreadExecutor(runtimeName);
                if (virtual != null) {
                    return virtual;
                }
            }
            return Executors.newFixedThreadPool(parallelism,
                    new NamedThreadFactory(threadPrefix(runtimeName, "task")));
        }

        static ScheduledExecutorService maintenanceExecutor(String runtimeName, int threads) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(threads,
                    new NamedThreadFactory(threadPrefix(runtimeName, "maintenance")));
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            return executor;
        }

        static String threadPrefix(String runtimeName, String role) {
            return "txflow-runtime-" + runtimeName + "-" + role + "-";
        }

        private static ExecutorService virtualThreadExecutor(String runtimeName) {
            try {
                Class<?> virtualBuilderType =
                        Class.forName("java.lang.Thread$Builder$OfVirtual");
                Object builder = Thread.class.getMethod("ofVirtual").invoke(null);
                Method name = virtualBuilderType.getMethod("name", String.class, long.class);
                Object namedBuilder = name.invoke(builder, threadPrefix(runtimeName, "task"), 1L);
                ThreadFactory factory = (ThreadFactory) virtualBuilderType
                        .getMethod("factory").invoke(namedBuilder);
                Method newPerTask = Executors.class.getMethod(
                        "newThreadPerTaskExecutor", ThreadFactory.class);
                return (ExecutorService) newPerTask.invoke(null, factory);
            } catch (InvocationTargetException invocationFailure) {
                Throwable cause = invocationFailure.getCause();
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                return null;
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                     | SecurityException | LinkageError unavailable) {
                return null;
            }
        }
    }

    /** Non-daemon deterministic platform-thread factory. */
    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
