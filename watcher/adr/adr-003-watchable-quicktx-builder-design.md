# ADR-003: WatchableQuickTxBuilder Design for Transaction Watching and Chaining

**Status**: Draft  
**Date**: August 2025

## Context

After implementing the initial Watcher API with TxContext support (ADR-002), we identified the need for a more comprehensive design that:

1. **Supports Transaction Chaining**: The original vision includes sophisticated transaction chains with different execution policies
2. **Preserves QuickTxBuilder Patterns**: Maintains the familiar and powerful composition patterns from QuickTxBuilder
3. **Handles Complex Compositions**: Multiple AbstractTx objects can be composed into a single blockchain transaction
4. **Provides Flexible Context**: Different steps in a chain may have different signers, fee payers, and configurations
5. **Enables UTXO Flow Management**: Steps in a chain need to reference and use outputs from previous steps
6. **Supports Input Dependencies**: Steps should be able to explicitly declare which previous step outputs they require

The current implementation focuses on single transaction watching. We need a design that naturally extends to transaction chains with sophisticated UTXO flow management while keeping simple cases simple.

## Decision

We will create a `WatchableQuickTxBuilder` that extends the QuickTxBuilder pattern, making transaction contexts "watchable" and chainable with sophisticated UTXO flow management. This approach:

1. **Extends Rather Than Replaces**: Builds on top of existing QuickTxBuilder functionality
2. **Composition-First**: Leverages the powerful composition model already familiar to users
3. **Chain as First-Class Concept**: Treats chains as the primary abstraction (single transactions are just chains with one step)
4. **UTXO-Aware Chaining**: Provides explicit methods for steps to reference outputs from previous steps
5. **Flexible Input Dependencies**: Supports automatic, filtered, and explicit UTXO selection from previous steps
6. **Deferred Serialization**: Designs with serialization in mind but defers implementation to avoid MVP complexity

## Design Overview

### Core Components

#### 1. WatchableQuickTxBuilder

Extends QuickTxBuilder to produce watchable contexts:

```java
public class WatchableQuickTxBuilder extends QuickTxBuilder {
    
    public WatchableQuickTxBuilder(UtxoSupplier utxoSupplier,
                                  ProtocolParamsSupplier protocolParamsSupplier,
                                  TransactionProcessor transactionProcessor) {
        super(utxoSupplier, protocolParamsSupplier, transactionProcessor);
    }
    
    @Override
    public WatchableTxContext compose(AbstractTx... txs) {
        return new WatchableTxContext(txs);
    }
    
    public class WatchableTxContext extends TxContext {
        private String stepId;
        private String description;
        
        WatchableTxContext(AbstractTx... txs) {
            super(txs);
        }
        
        // Inherit all TxContext methods
        // Add watchable-specific methods
        
        public WatchableTxContext withStepId(String stepId) {
            this.stepId = stepId;
            return this;
        }
        
        public WatchableTxContext withDescription(String description) {
            this.description = description;
            return this;
        }
        
        // === UTXO Dependency Methods ===
        
        /**
         * Use all outputs from a previous step as inputs for this step.
         * Equivalent to calling collectFrom() with all UTXOs from the specified step.
         */
        public WatchableTxContext fromStep(String stepId) {
            this.addUtxoDependency(new StepOutputDependency(stepId, UtxoSelectionStrategy.ALL));
            return this;
        }
        
        /**
         * Use filtered outputs from a previous step as inputs.
         * Only UTXOs matching the condition will be used as inputs.
         */
        public WatchableTxContext fromStepWhere(String stepId, Predicate<Utxo> condition) {
            this.addUtxoDependency(new StepOutputDependency(stepId, 
                new FilteredUtxoSelectionStrategy(condition)));
            return this;
        }
        
        /**
         * Use a specific UTXO from a previous step by index.
         * Index 0 is the first output of the transaction.
         */
        public WatchableTxContext fromStepUtxo(String stepId, int utxoIndex) {
            this.addUtxoDependency(new StepOutputDependency(stepId, 
                new IndexedUtxoSelectionStrategy(utxoIndex)));
            return this;
        }
        
        /**
         * Use UTXOs from a previous step based on asset requirements.
         * Will select enough UTXOs to cover the specified amounts.
         */
        public WatchableTxContext fromStepForAmounts(String stepId, List<Amount> requiredAmounts) {
            this.addUtxoDependency(new StepOutputDependency(stepId, 
                new AmountRequiredUtxoSelectionStrategy(requiredAmounts)));
            return this;
        }
        
        /**
         * Use UTXOs from multiple previous steps.
         * Useful for consolidating outputs from parallel steps.
         */
        public WatchableTxContext fromSteps(String... stepIds) {
            for (String stepId : stepIds) {
                this.addUtxoDependency(new StepOutputDependency(stepId, UtxoSelectionStrategy.ALL));
            }
            return this;
        }
        
        /**
         * Add a custom UTXO dependency with a specific selection strategy.
         */
        public WatchableTxContext withUtxoDependency(String stepId, UtxoSelectionStrategy strategy) {
            this.addUtxoDependency(new StepOutputDependency(stepId, strategy));
            return this;
        }
        
        /**
         * Convert this context into a watchable step
         */
        public WatchableStep watchable() {
            return new WatchableStep(this, stepId, description);
        }
        
        /**
         * Direct watch for simple single-transaction cases
         */
        public WatchHandle watch() {
            return Watcher.build("single-tx-" + System.currentTimeMillis())
                .step(this.watchable())
                .watch();
        }
    }
}
```

#### 2. WatchableStep

Represents a single executable step in a chain:

```java
public class WatchableStep {
    private final String stepId;
    private final String description;
    private final WatchableTxContext txContext;
    private final StepConfig config;
    
    // Step execution state
    private StepStatus status = StepStatus.PENDING;
    private String transactionHash;
    private List<Utxo> outputUtxos;
    private Throwable lastError;
    private int retryCount = 0;
    
    public WatchableStep(WatchableTxContext txContext, String stepId, String description) {
        this.txContext = txContext;
        this.stepId = stepId != null ? stepId : UUID.randomUUID().toString();
        this.description = description;
        this.config = StepConfig.defaultConfig();
    }
    
    /**
     * Execute this step with the given chain context
     */
    public StepResult execute(ChainContext chainContext) {
        try {
            // Use txContext to build and submit transaction
            Transaction tx = txContext.buildAndSign();
            Result<String> submitResult = transactionProcessor.submitTransaction(tx.serialize());
            
            if (submitResult.isSuccessful()) {
                this.transactionHash = submitResult.getValue();
                this.status = StepStatus.SUBMITTED;
                
                // Watch for confirmation
                return watchForConfirmation(transactionHash, chainContext);
            } else {
                throw new StepExecutionException("Transaction submission failed: " + submitResult.getResponse());
            }
        } catch (Exception e) {
            this.lastError = e;
            this.status = StepStatus.FAILED;
            return StepResult.failure(stepId, e);
        }
    }
    
    /**
     * Rebuild this step with fresh context (for rollback scenarios)
     */
    public WatchableStep rebuild(WatchableQuickTxBuilder builder) {
        // Recreate the step with fresh UTXOs and context
        WatchableTxContext rebuiltContext = builder.compose(txContext.getTxList());
        
        // Reapply configuration
        rebuiltContext.withSigner(txContext.getSigners())
                     .feePayer(txContext.getFeePayer())
                     // ... other configuration
                     
        return new WatchableStep(rebuiltContext, stepId, description);
    }
}
```

#### 3. Watcher (Chain Builder and Executor)

Builds and executes transaction chains:

```java
public class Watcher {
    private final WatcherService watcherService;
    
    public static WatcherBuilder build(String chainId) {
        return new WatcherBuilder(chainId);
    }
    
    public static class WatcherBuilder {
        private final String chainId;
        private final List<ExecutionNode> executionPlan = new ArrayList<>();
        private String description;
        private WatcherConfig config = WatcherConfig.defaultConfig();
        
        public WatcherBuilder(String chainId) {
            this.chainId = chainId;
        }
        
        /**
         * Add a sequential step
         */
        public WatcherBuilder step(WatchableStep step) {
            executionPlan.add(new SequentialNode(step));
            return this;
        }
        
        /**
         * Add parallel steps
         */
        public WatcherBuilder parallel(WatchableStep... steps) {
            executionPlan.add(new ParallelNode(Arrays.asList(steps)));
            return this;
        }
        
        /**
         * Add a conditional step
         */
        public WatcherBuilder conditional(Predicate<ChainContext> condition, WatchableStep step) {
            executionPlan.add(new ConditionalNode(condition, step));
            return this;
        }
        
        /**
         * Add a step that depends on previous step outputs
         */
        public WatcherBuilder stepWithDependency(String dependsOn, 
                                                Function<ChainContext, WatchableStep> stepFactory) {
            executionPlan.add(new DependentNode(dependsOn, stepFactory));
            return this;
        }
        
        public WatcherBuilder withDescription(String description) {
            this.description = description;
            return this;
        }
        
        public WatcherBuilder withConfig(WatcherConfig config) {
            this.config = config;
            return this;
        }
        
        /**
         * Build and execute the chain
         */
        public WatchHandle watch() {
            WatchChain chain = new WatchChain(chainId, description, executionPlan, config);
            return watcherService.watchChain(chain);
        }
    }
}
```

#### 4. Chain Execution Model

Different execution node types for chain orchestration:

```java
// Base execution node
public interface ExecutionNode {
    NodeResult execute(ChainContext context, ChainExecutor executor);
}

// Sequential execution
public class SequentialNode implements ExecutionNode {
    private final WatchableStep step;
    
    public NodeResult execute(ChainContext context, ChainExecutor executor) {
        return executor.executeStep(step, context);
    }
}

// Parallel execution
public class ParallelNode implements ExecutionNode {
    private final List<WatchableStep> steps;
    
    public NodeResult execute(ChainContext context, ChainExecutor executor) {
        List<CompletableFuture<StepResult>> futures = steps.stream()
            .map(step -> executor.executeStepAsync(step, context))
            .collect(Collectors.toList());
            
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Collect results
        List<StepResult> results = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
            
        return NodeResult.fromStepResults(results);
    }
}

// Conditional execution
public class ConditionalNode implements ExecutionNode {
    private final Predicate<ChainContext> condition;
    private final WatchableStep step;
    
    public NodeResult execute(ChainContext context, ChainExecutor executor) {
        if (condition.test(context)) {
            return executor.executeStep(step, context);
        }
        return NodeResult.skipped(step.getStepId());
    }
}
```

#### 5. Chain Context

Manages state and data flow between chain steps:

```java
public class ChainContext {
    private final String chainId;
    private final Map<String, StepResult> stepResults = new ConcurrentHashMap<>();
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
    private final ChainAwareUtxoSupplier chainAwareUtxoSupplier;
    
    public ChainContext(String chainId, UtxoSupplier baseSupplier) {
        this.chainId = chainId;
        this.chainAwareUtxoSupplier = new ChainAwareUtxoSupplier(baseSupplier);
    }
    
    /**
     * Record step result
     */
    public void recordStepResult(String stepId, StepResult result) {
        stepResults.put(stepId, result);
        
        // Update chain-aware UTXO supplier with new outputs
        if (result.isSuccessful() && result.getOutputUtxos() != null) {
            chainAwareUtxoSupplier.addPendingUtxos(result.getOutputUtxos());
        }
    }
    
    /**
     * Get outputs from a previous step
     */
    public List<Utxo> getStepOutputs(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
            .map(StepResult::getOutputUtxos)
            .orElse(Collections.emptyList());
    }
    
    /**
     * Check if a step completed successfully
     */
    public boolean hasSuccessfulStep(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
            .map(StepResult::isSuccessful)
            .orElse(false);
    }
    
    /**
     * Store shared data between steps
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }
    
    public <T> Optional<T> getSharedData(String key, Class<T> type) {
        Object value = sharedData.get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
}
```

## UTXO Dependency Management

### UTXO Selection Strategies

```java
/**
 * Strategy interface for selecting UTXOs from a previous step's outputs
 */
public interface UtxoSelectionStrategy {
    List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context);
    
    // Pre-defined strategies
    UtxoSelectionStrategy ALL = (utxos, ctx) -> new ArrayList<>(utxos);
    UtxoSelectionStrategy LARGEST_FIRST = (utxos, ctx) -> utxos.stream()
        .sorted((u1, u2) -> u2.getAmount().getValue().compareTo(u1.getAmount().getValue()))
        .collect(Collectors.toList());
    UtxoSelectionStrategy SMALLEST_FIRST = (utxos, ctx) -> utxos.stream()
        .sorted((u1, u2) -> u1.getAmount().getValue().compareTo(u2.getAmount().getValue()))
        .collect(Collectors.toList());
}

/**
 * Strategy that filters UTXOs based on a predicate
 */
public class FilteredUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private final Predicate<Utxo> filter;
    
    public FilteredUtxoSelectionStrategy(Predicate<Utxo> filter) {
        this.filter = filter;
    }
    
    @Override
    public List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context) {
        return availableUtxos.stream()
            .filter(filter)
            .collect(Collectors.toList());
    }
}

/**
 * Strategy that selects a specific UTXO by index
 */
public class IndexedUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private final int index;
    
    public IndexedUtxoSelectionStrategy(int index) {
        this.index = index;
    }
    
    @Override
    public List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context) {
        if (index >= 0 && index < availableUtxos.size()) {
            return List.of(availableUtxos.get(index));
        }
        return Collections.emptyList();
    }
}

/**
 * Strategy that selects UTXOs to cover required amounts
 */
public class AmountRequiredUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private final List<Amount> requiredAmounts;
    
    public AmountRequiredUtxoSelectionStrategy(List<Amount> requiredAmounts) {
        this.requiredAmounts = requiredAmounts;
    }
    
    @Override
    public List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context) {
        // Implement coin selection logic to cover required amounts
        // This is a simplified version - production would use sophisticated coin selection
        List<Utxo> selected = new ArrayList<>();
        Map<String, BigInteger> remainingAmounts = new HashMap<>();
        
        // Initialize remaining amounts
        for (Amount amount : requiredAmounts) {
            remainingAmounts.merge(amount.getUnit(), amount.getQuantity(), BigInteger::add);
        }
        
        // Select UTXOs until requirements are met
        for (Utxo utxo : availableUtxos) {
            if (remainingAmounts.isEmpty()) break;
            
            boolean utxoNeeded = false;
            for (Amount utxoAmount : utxo.getAmount()) {
                String unit = utxoAmount.getUnit();
                BigInteger remaining = remainingAmounts.get(unit);
                if (remaining != null && remaining.compareTo(BigInteger.ZERO) > 0) {
                    utxoNeeded = true;
                    BigInteger newRemaining = remaining.subtract(utxoAmount.getQuantity());
                    if (newRemaining.compareTo(BigInteger.ZERO) <= 0) {
                        remainingAmounts.remove(unit);
                    } else {
                        remainingAmounts.put(unit, newRemaining);
                    }
                }
            }
            
            if (utxoNeeded) {
                selected.add(utxo);
            }
        }
        
        return selected;
    }
}
```

### UTXO Dependency Management

```java
/**
 * Represents a dependency on outputs from a previous step
 */
public class StepOutputDependency {
    private final String stepId;
    private final UtxoSelectionStrategy selectionStrategy;
    private final boolean optional;
    
    public StepOutputDependency(String stepId, UtxoSelectionStrategy selectionStrategy) {
        this(stepId, selectionStrategy, false);
    }
    
    public StepOutputDependency(String stepId, UtxoSelectionStrategy selectionStrategy, boolean optional) {
        this.stepId = stepId;
        this.selectionStrategy = selectionStrategy;
        this.optional = optional;
    }
    
    /**
     * Resolve this dependency using the chain context
     */
    public List<Utxo> resolveUtxos(ChainContext chainContext) {
        List<Utxo> stepOutputs = chainContext.getStepOutputs(stepId);
        if (stepOutputs.isEmpty() && !optional) {
            throw new UtxoDependencyException("Required step '" + stepId + "' has no outputs available");
        }
        return selectionStrategy.selectUtxos(stepOutputs, chainContext);
    }
    
    // Getters
    public String getStepId() { return stepId; }
    public UtxoSelectionStrategy getSelectionStrategy() { return selectionStrategy; }
    public boolean isOptional() { return optional; }
}

/**
 * Enhanced chain context that manages UTXO flow between steps
 */
public class ChainAwareUtxoSupplier implements UtxoSupplier {
    private final UtxoSupplier baseSupplier;
    private final Map<String, List<Utxo>> pendingUtxos = new ConcurrentHashMap<>();
    private final Map<String, List<Utxo>> confirmedUtxos = new ConcurrentHashMap<>();
    
    public ChainAwareUtxoSupplier(UtxoSupplier baseSupplier) {
        this.baseSupplier = baseSupplier;
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer count, String cursor) {
        List<Utxo> baseUtxos = baseSupplier.getPage(address, count, cursor);
        List<Utxo> chainUtxos = getChainUtxosForAddress(address);
        
        // Combine base UTXOs with chain-aware UTXOs
        List<Utxo> combined = new ArrayList<>(baseUtxos);
        combined.addAll(chainUtxos);
        
        return combined;
    }
    
    @Override
    public List<Utxo> getAll(String address) {
        List<Utxo> baseUtxos = baseSupplier.getAll(address);
        List<Utxo> chainUtxos = getChainUtxosForAddress(address);
        
        List<Utxo> combined = new ArrayList<>(baseUtxos);
        combined.addAll(chainUtxos);
        
        return combined;
    }
    
    /**
     * Add pending UTXOs from a step execution
     */
    public void addPendingUtxos(String stepId, List<Utxo> utxos) {
        pendingUtxos.put(stepId, new ArrayList<>(utxos));
    }
    
    /**
     * Confirm UTXOs when step is confirmed on-chain
     */
    public void confirmUtxos(String stepId) {
        List<Utxo> pending = pendingUtxos.remove(stepId);
        if (pending != null) {
            confirmedUtxos.put(stepId, pending);
        }
    }
    
    /**
     * Get all UTXOs for a specific address from the chain context
     */
    private List<Utxo> getChainUtxosForAddress(String address) {
        List<Utxo> result = new ArrayList<>();
        
        // Add confirmed UTXOs
        confirmedUtxos.values().forEach(utxos -> {
            utxos.stream()
                .filter(utxo -> address.equals(utxo.getAddress()))
                .forEach(result::add);
        });
        
        // Add pending UTXOs (optimistically)
        pendingUtxos.values().forEach(utxos -> {
            utxos.stream()
                .filter(utxo -> address.equals(utxo.getAddress()))
                .forEach(result::add);
        });
        
        return result;
    }
}

/**
 * Exception thrown when UTXO dependencies cannot be resolved
 */
public class UtxoDependencyException extends RuntimeException {
    public UtxoDependencyException(String message) {
        super(message);
    }
    
    public UtxoDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## Usage Examples

### Simple Payment Transaction

```java
// Create watchable builder
WatchableQuickTxBuilder watchableBuilder = new WatchableQuickTxBuilder(
    utxoSupplier, protocolParamsSupplier, transactionProcessor
);

// Simple case - single transaction with direct watch
Tx payment = new Tx()
    .payToAddress(receiver, Amount.ada(10))
    .from(sender);

WatchHandle handle = watchableBuilder.compose(payment)
    .withSigner(SignerProviders.signerFrom(senderKey))
    .feePayer(sender)
    .withDescription("Payment to Alice")
    .watch();  // Direct watch for simple case

handle.await(Duration.ofMinutes(5));
```

### Multi-Step Transaction Chain with UTXO Dependencies

```java
// Step 1: Initial deposits to pool
var poolDeposits = watchableBuilder.compose(
        new Tx().payToAddress(poolAddress, Amount.ada(100)).from(sender1),
        new Tx().payToAddress(poolAddress, Amount.ada(50)).from(sender2)
    )
    .withSigner(SignerProviders.signerFrom(wallet1, wallet2))
    .feePayer(sender1)
    .withStepId("pool-deposits")
    .withDescription("Initial deposits to the pool")
    .watchable();

// Step 2: Withdraw from pool using all outputs from step 1
var poolWithdrawal = watchableBuilder.compose(
        new ScriptTx()
            .fromStep("pool-deposits")  // Use ALL outputs from pool-deposits step
            .collectFrom(poolScript, poolDatum, poolRedeemer)
            .payToAddress(distributor, Amount.ada(140))
    )
    .withSigner(SignerProviders.signerFrom(distributorKey))
    .collateralPayer(distributor)
    .withStepId("pool-withdrawal")
    .withDescription("Withdraw funds from pool")
    .watchable();

// Step 3: Distribute funds using outputs from withdrawal
var distribution = watchableBuilder.compose(
        new Tx()
            .fromStep("pool-withdrawal")  // Use outputs from withdrawal
            .payToAddress(recipient1, Amount.ada(70))
            .payToAddress(recipient2, Amount.ada(70))
    )
    .withSigner(SignerProviders.signerFrom(distributorKey))
    .withStepId("distribution")
    .withDescription("Distribute funds to recipients")
    .watchable();

// Build and execute chain with UTXO flow
WatchHandle chainHandle = Watcher.build("pool-distribution-chain")
    .step(poolDeposits)     // Step 1: Deposit
    .step(poolWithdrawal)   // Step 2: Withdraw (depends on step 1 outputs)
    .step(distribution)     // Step 3: Distribute (depends on step 2 outputs)
    .withDescription("Pool deposit, withdrawal, and distribution workflow")
    .withConfig(WatcherConfig.builder()
        .maxRetries(3)
        .retryDelay(Duration.ofSeconds(10))
        .timeout(Duration.ofMinutes(10))
        .build())
    .watch();

// Monitor chain progress
chainHandle.onStepComplete((stepId, result) -> {
    System.out.println("Step " + stepId + " completed: " + result.getTransactionHash());
    System.out.println("  Output UTXOs: " + result.getOutputUtxos().size());
});

ChainResult result = chainHandle.await();
```

### Advanced UTXO Dependency Examples

```java
// Example 1: Conditional UTXO selection
var conditionalStep = watchableBuilder.compose(
        new Tx()
            .fromStepWhere("pool-deposits", 
                utxo -> utxo.getAmount().getValue().compareTo(BigInteger.valueOf(75_000_000L)) > 0)
            .payToAddress(highValueRecipient, Amount.ada(75))
    )
    .withStepId("high-value-processing")
    .watchable();

// Example 2: Specific UTXO by index
var specificUtxoStep = watchableBuilder.compose(
        new Tx()
            .fromStepUtxo("pool-deposits", 0)  // Use the first output only
            .payToAddress(firstRecipient, Amount.ada(50))
    )
    .withStepId("first-output-processing")
    .watchable();

// Example 3: Amount-based selection
var amountBasedStep = watchableBuilder.compose(
        new Tx()
            .fromStepForAmounts("pool-deposits", 
                List.of(Amount.ada(25), Amount.asset("asset123", 1000)))
            .payToAddress(assetRecipient, Amount.ada(25))
    )
    .withStepId("amount-based-processing")
    .watchable();

// Example 4: Multiple step dependencies
var consolidationStep = watchableBuilder.compose(
        new Tx()
            .fromSteps("step1", "step2", "step3")  // Use outputs from multiple steps
            .payToAddress(consolidationAddress, Amount.ada(200))
    )
    .withStepId("consolidation")
    .watchable();

// Example 5: Custom UTXO selection strategy
var customSelectionStep = watchableBuilder.compose(
        new Tx()
            .withUtxoDependency("pool-deposits", 
                (utxos, context) -> utxos.stream()
                    .filter(utxo -> utxo.getAddress().startsWith("addr1"))
                    .limit(2)
                    .collect(Collectors.toList()))
            .payToAddress(customRecipient, Amount.ada(100))
    )
    .withStepId("custom-selection")
    .watchable();

// Build complex chain with various dependency patterns
Watcher.build("complex-dependency-chain")
    .step(poolDeposits)
    .parallel(conditionalStep, specificUtxoStep, amountBasedStep)
    .step(consolidationStep)
    .step(customSelectionStep)
    .watch();
```

### Conditional and Parallel Execution

```java
// Setup steps
var payment = watchableBuilder.compose(paymentTx)
    .withSigner(signer)
    .withStepId("payment")
    .watchable();

var refund = watchableBuilder.compose(refundTx)
    .withSigner(signer)
    .withStepId("refund")
    .watchable();

var notification1 = watchableBuilder.compose(notifyTx1)
    .withSigner(signer)
    .withStepId("notify1")
    .watchable();

var notification2 = watchableBuilder.compose(notifyTx2)
    .withSigner(signer)
    .withStepId("notify2")
    .watchable();

// Chain with conditional and parallel execution
Watcher.build("payment-with-notifications")
    .step(payment)
    .conditional(
        ctx -> ctx.hasSuccessfulStep("payment") && shouldRefund(ctx),
        refund
    )
    .parallel(notification1, notification2)  // Send notifications in parallel
    .withConfig(WatcherConfig.builder()
        .processingType(ProcessingType.SINGLE)  // Process one chain at a time
        .build())
    .watch();
```

### Dynamic Step Creation

```java
// Step that depends on previous outputs
Watcher.build("dynamic-chain")
    .step(initialStep)
    .stepWithDependency("initial", ctx -> {
        // Get outputs from initial step
        List<Utxo> outputs = ctx.getStepOutputs("initial");
        
        // Create new transaction based on outputs
        Tx dynamicTx = new Tx();
        outputs.forEach(utxo -> {
            dynamicTx.collectFrom(utxo);
        });
        dynamicTx.payToAddress(finalRecipient, calculateAmount(outputs));
        
        // Return as watchable step
        return watchableBuilder.compose(dynamicTx)
            .withSigner(signer)
            .watchable();
    })
    .watch();
```

## Benefits

1. **Natural Extension of QuickTxBuilder**: Developers already familiar with QuickTxBuilder can easily adopt watching
2. **Composition Preserved**: The powerful composition model remains intact
3. **Flexible Context Management**: Each step can have its own signers, fee payers, and configuration
4. **Chain as Primary Abstraction**: Single transactions are just chains with one step
5. **Clear Execution Model**: Sequential, parallel, and conditional execution are explicit
6. **Sophisticated UTXO Flow Management**: Native support for UTXO dependencies between steps
7. **Multiple Selection Strategies**: Various ways to select UTXOs from previous steps (all, filtered, indexed, amount-based)
8. **Rollback-Safe**: UTXO dependencies are automatically resolved during rollback recovery
9. **Composable Dependencies**: Steps can depend on multiple previous steps with different selection strategies
10. **Future-Proof**: Designed with serialization in mind but not dependent on it

## Trade-offs

1. **Inheritance vs Composition**: Extending QuickTxBuilder creates coupling but provides familiar API
2. **Complexity for Simple Cases**: Single transactions require chain abstraction (mitigated by `.watch()` shortcut)
3. **Memory Usage**: Keeping full contexts in memory (acceptable for MVP, serialization will address this)
4. **UTXO Dependency Complexity**: Adding UTXO reference methods increases API surface but provides powerful capabilities
5. **Chain-Aware State Management**: Need to track pending/confirmed UTXOs adds execution complexity but enables sophisticated workflows
6. **Dependency Resolution**: Steps must wait for dependent steps to complete, which could impact parallel execution optimization

## Future Enhancements

### Serialization Support

```java
public interface TxContextSerializer {
    byte[] serialize(WatchableTxContext context);
    WatchableTxContext deserialize(byte[] data, WatchableQuickTxBuilder builder);
}

public interface ChainSerializer {
    String serialize(WatchChain chain);
    WatchChain deserialize(String data, WatchableQuickTxBuilder builder);
}
```

### Advanced Chain Features

1. **Loop Support**: Repeat steps based on conditions
2. **Error Handling Strategies**: Per-step error handlers
3. **Chain Templates**: Reusable chain patterns
4. **Dynamic Parallelism**: Determine parallel execution at runtime
5. **Cross-Chain Dependencies**: Steps depending on external chains

### Integration Features

1. **Event Streaming**: Kafka/RabbitMQ integration for chain events
2. **Metrics and Monitoring**: Prometheus/Grafana integration
3. **State Persistence**: Database-backed chain state
4. **REST API**: HTTP endpoints for chain management

## Implementation Priority

### MVP (Phase 1)
1. WatchableQuickTxBuilder with basic WatchableTxContext
2. WatchableStep abstraction
3. Sequential execution only
4. Basic Watcher builder
5. Simple retry logic
6. **Basic UTXO dependency methods**: `.fromStep()`, `.fromStepUtxo()`, `.fromStepWhere()`

### Phase 2
1. ChainContext implementation with UTXO flow tracking
2. Chain-aware UTXO supplier
3. **Advanced UTXO selection strategies**: Amount-based, multiple steps, custom strategies
4. Parallel execution support with dependency management
5. Conditional execution

### Phase 3
1. **UTXO dependency validation**: Detect circular dependencies, validate step references
2. Rollback detection with UTXO dependency resolution
3. Dynamic step creation based on previous step outputs
4. **Optimistic UTXO management**: Use pending UTXOs before confirmation
5. Advanced error handling with dependency-aware recovery

### Phase 4
1. Serialization provider interface for UTXO dependencies
2. **Complex dependency patterns**: Optional dependencies, fallback strategies
3. Performance optimizations for large chains

## Implementation Notes (February 2025)

### ChainAwareUtxoSupplier Implementation
The ChainAwareUtxoSupplier has been implemented with a refined approach:

1. **Step-Specific UtxoSupplier Strategy**: Instead of a global chain-aware supplier, each step determines its own UtxoSupplier based on its dependencies:
   - Steps without dependencies use the base UtxoSupplier
   - Steps with dependencies use a ChainAwareUtxoSupplier that includes pending UTXOs from dependent steps

2. **Per-Step QuickTxBuilder Creation**: Each step creates its own QuickTxBuilder instance with the appropriate UtxoSupplier:
   ```java
   private UtxoSupplier determineUtxoSupplier(ChainContext chainContext) {
       if (dependencies.isEmpty()) {
           return baseSupplier;
       } else {
           return new ChainAwareUtxoSupplier(baseSupplier, chainContext, dependencies);
       }
   }
   ```

3. **Configuration Preservation**: The WatchableTxContext stores signer and fee payer configurations to ensure they are properly applied to the effective TxContext during execution.

4. **Address-Based UTXO Filtering**: The ChainAwareUtxoSupplier filters pending UTXOs by address to ensure only relevant UTXOs are included for each transaction.

This implementation successfully solves the "insufficient funds" issue where step 2 couldn't see pending outputs from step 1, enabling seamless transaction chaining without delays.

## Conclusion

The WatchableQuickTxBuilder design provides a natural extension to the existing QuickTxBuilder pattern while adding powerful watching, chaining, and UTXO flow management capabilities. By treating chains as the primary abstraction and preserving the composition model, we create an API that is both familiar to existing users and capable of handling sophisticated transaction workflows with complex UTXO dependencies.

### Key Innovations

1. **Seamless UTXO Flow**: Steps can naturally reference and use outputs from previous steps using intuitive methods like `.fromStep()` and `.fromStepWhere()`

2. **Flexible Selection Strategies**: Multiple ways to select UTXOs from previous steps, from simple "use all" to complex amount-based selection

3. **Chain-Aware Execution**: The execution engine understands UTXO dependencies and manages the flow of value through the chain

4. **Rollback Recovery**: UTXO dependencies are automatically resolved during rollback scenarios, ensuring chain consistency

The design carefully balances immediate usability with future extensibility, ensuring that simple cases remain simple (a single transaction still works with `.watch()`) while complex cases with sophisticated UTXO flow management are not only possible but elegant to express.

This architecture enables developers to build complex DeFi workflows, multi-step protocols, and sophisticated transaction orchestration while maintaining the familiar and powerful QuickTx composition patterns they already know.
