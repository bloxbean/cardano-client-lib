---
description: QuickTx declarative intent-based APIs, builder patterns, transaction composition strategies, and production optimization techniques
sidebar_label: Builder Patterns Guide
sidebar_position: 4
---

# QuickTx Builder Patterns Guide

QuickTx provides a unique **declarative, intent-based API** that separates transaction intent from implementation. Unlike imperative approaches where you manually construct transaction components, QuickTx allows you to declare what you want to accomplish through intent objects (`Tx`, `ScriptTx`), which the `QuickTxBuilder` then composes into executable transactions.

## Architecture: Intent-Based vs Imperative

### Traditional Imperative Approach

```java
// Imperative: Manual transaction construction
TransactionBody.Builder txBodyBuilder = TransactionBody.builder();
txBodyBuilder.inputs(selectedInputs);
txBodyBuilder.outputs(constructedOutputs);
txBodyBuilder.fee(calculatedFee);

// Manual fee calculation, balancing, witness creation...
Transaction transaction = Transaction.builder()
    .body(txBodyBuilder.build())
    .witnessSet(manuallyCreatedWitnesses)
    .build();
```

### QuickTx Declarative Approach

```java
// Declarative: Express intent, let QuickTx handle implementation
Tx paymentIntent = new Tx()
    .payToAddress(recipient, Amount.ada(10))
    .from(sender);

// QuickTxBuilder composes intent into executable transaction
Result<String> result = new QuickTxBuilder(backendService)
    .compose(paymentIntent)  // Takes intent and builds transaction
    .withSigner(signer)
    .complete();
```

## Core Concepts: Intents and Composition

### Intent Objects as Declarations

Intent objects (`Tx`, `ScriptTx`, `StakeTx`, `GovTx`) are **declarative specifications** of what you want to achieve:

```java
// Payment intent - "I want to send 10 ADA to this address"
Tx paymentIntent = new Tx()
    .payToAddress("addr1...", Amount.ada(10))
    .from(senderAddress);

// Minting intent - "I want to mint these tokens with this policy"
Tx mintingIntent = new Tx()
    .mintAssets(mintingPolicy, assets)
    .from(minterAddress);

// Contract interaction intent - "I want to spend from this script with this redeemer"
ScriptTx contractIntent = new ScriptTx()
    .collectFrom(scriptUtxo, redeemer)
    .payToAddress(recipient, Amount.ada(5))
    .attachSpendingValidator(validator);

// Staking intent - "I want to register and delegate to this pool"
Tx stakingIntent = new Tx()
    .registerStakeAddress(stakeAddress)
    .delegateTo(stakeAddress, poolId)
    .from(accountAddress);
```

### QuickTxBuilder as Intent Composer

The `QuickTxBuilder` takes these intents and handles all the complex implementation details:

```java
QuickTxBuilder builder = new QuickTxBuilder(backendService);

// Composer takes multiple intents and creates unified transaction
Result<String> result = builder
    .compose(paymentIntent, mintingIntent, stakingIntent)  // Combine intents
    .withSigner(paymentSigner)
    .withSigner(mintingSigner)
    .withSigner(stakingSigner)
    .complete();  // Builder handles: UTXO selection, fee calculation, balancing, witness creation
```

**What QuickTxBuilder handles automatically:**
- ✅ UTXO selection and optimization
- ✅ Fee calculation and adjustment
- ✅ Transaction balancing
- ✅ Change output creation
- ✅ Script execution cost calculation
- ✅ Witness requirement determination
- ✅ Transaction size optimization
- ✅ Protocol parameter compliance

## Core Builder Pattern Concepts

### Fluent Interface Design

QuickTx implements the fluent interface pattern, allowing method chaining for readable transaction construction:

```java
// Fluent transaction building
Tx payment = new Tx()
    .payToAddress(recipient1, Amount.ada(10))
    .payToAddress(recipient2, Amount.ada(5))
    .attachMetadata(MessageMetadata.create().add("Batch payment"))
    .from(sender);

Result<String> result = quickTxBuilder
    .compose(payment)
    .withSigner(SignerProviders.signerFrom(account))
    .withTxInspector(tx -> log.info("Fee: " + tx.getBody().getFee()))
    .completeAndWait();
```

### Type-Safe Composition

The builder pattern ensures type safety at compile time:

```java
// This won't compile - cannot attach spending validator to Tx
// Tx tx = new Tx().attachSpendingValidator(validator); // ❌ Compile error

// Must use ScriptTx for script operations
ScriptTx scriptTx = new ScriptTx()
    .collectFrom(utxo, redeemer)
    .attachSpendingValidator(validator); // ✅ Type safe
```

### Progressive Disclosure

Builders expose relevant methods based on context:

```java
// AbstractTx provides common methods
public abstract class AbstractTx<T> {
    public T payToAddress(String address, Amount amount) { ... }
    public T attachMetadata(Metadata metadata) { ... }
}

// Tx adds simple transaction methods
public class Tx extends AbstractTx<Tx> {
    public Tx from(String sender) { ... }
    public Tx mintAssets(NativeScript script, Asset asset) { ... }
}

// ScriptTx adds script-specific methods
public class ScriptTx extends AbstractTx<ScriptTx> {
    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemer) { ... }
    public ScriptTx attachSpendingValidator(PlutusScript script) { ... }
}
```

## Transaction Composition Patterns

### Single-Purpose Transactions

Simple transactions with a single primary purpose:

```java
public class PaymentService {
    
    public TxResult sendPayment(String sender, String receiver, Amount amount) {
        Tx payment = new Tx()
            .payToAddress(receiver, amount)
            .from(sender);
            
        return quickTxBuilder
            .compose(payment)
            .withSigner(getSigner(sender))
            .completeAndWait();
    }
    
    public TxResult mintToken(NativeScript policy, Asset asset, String receiver) {
        Tx mint = new Tx()
            .mintAssets(policy, asset, receiver)
            .from(getMinterAddress());
            
        return quickTxBuilder
            .compose(mint)
            .withSigner(getMinterSigner())
            .withSigner(getPolicySigner(policy))
            .completeAndWait();
    }
}
```

### Multi-Operation Atomic Transactions

Combine multiple operations in a single atomic transaction:

```java
public class AtomicOperationService {
    
    public TxResult setupWalletAndDelegate(Account account, String poolId) {
        Tx setup = new Tx()
            .registerStakeAddress(account)           // Register for staking
            .delegateTo(account, poolId)             // Delegate to pool
            .payToAddress(account.baseAddress(), Amount.ada(2)) // Fund account
            .from(fundingAddress);
            
        return quickTxBuilder
            .compose(setup)
            .withSigner(SignerProviders.signerFrom(account))
            .withSigner(fundingSigner)
            .completeAndWait();
    }
    
    public TxResult atomicSwap(SwapParams params) {
        Tx swap = new Tx()
            .payToAddress(params.getCounterparty(), params.getOfferAssets())
            .payToAddress(params.getInitiator(), params.getRequestAssets())
            .attachMetadata(createSwapMetadata(params))
            .from(escrowAddress);
            
        return quickTxBuilder
            .compose(swap)
            .withSigner(escrowSigner)
            .validTo(params.getExpirationSlot())
            .completeAndWait();
    }
}
```

### Multi-Transaction Composition

Compose multiple transaction objects for complex operations:

```java
public class DeFiOperationService {
    
    public TxResult liquidityProvision(LiquidityParams params) {
        // Transaction 1: Provide token A
        Tx provideTokenA = new Tx()
            .payToContract(poolAddress, params.getTokenAAmount(), params.getPoolDatum())
            .from(params.getProvider());
            
        // Transaction 2: Provide token B  
        Tx provideTokenB = new Tx()
            .payToContract(poolAddress, params.getTokenBAmount(), params.getPoolDatum())
            .from(params.getProvider());
            
        // Transaction 3: Mint LP tokens
        Tx mintLPTokens = new Tx()
            .mintAssets(params.getLpPolicy(), params.getLpTokens(), params.getProvider())
            .from(protocolAddress);
            
        // Compose all operations atomically
        return quickTxBuilder
            .compose(provideTokenA, provideTokenB, mintLPTokens)
            .withSigner(SignerProviders.signerFrom(params.getProviderAccount()))
            .withSigner(protocolSigner)
            .withSigner(SignerProviders.signerFrom(params.getLpPolicy()))
            .completeAndWait();
    }
}
```

### Script and Native Transaction Mixing

Combine script-based and native operations:

```java
public class HybridOperationService {
    
    public TxResult claimAndStake(ClaimParams params) {
        // Claim rewards from smart contract
        ScriptTx claimTx = new ScriptTx()
            .collectFrom(params.getRewardUtxo(), params.getClaimRedeemer())
            .attachSpendingValidator(params.getRewardValidator());
            
        // Stake the claimed rewards
        Tx stakeTx = new Tx()
            .registerStakeAddress(params.getStakeAccount())
            .delegateTo(params.getStakeAccount(), params.getPoolId());
            
        return quickTxBuilder
            .compose(claimTx, stakeTx)
            .feePayer(params.getFeePayerAddress())
            .withSigner(SignerProviders.signerFrom(params.getStakeAccount()))
            .withSigner(params.getFeePayer())
            .completeAndWait();
    }
}
```

## Advanced Configuration Patterns

### Fee Management Strategies

#### Centralized Fee Payment

```java
public class CentralizedFeeService {
    private final String centralFeePayerAddress;
    private final TxSigner centralFeeSigner;
    
    public TxResult executeWithCentralFee(AbstractTx... transactions) {
        return quickTxBuilder
            .compose(transactions)
            .feePayer(centralFeePayerAddress)
            .withSigner(centralFeeSigner)
            .completeAndWait();
    }
    
    public TxResult executeBatchPayments(List<Payment> payments) {
        Tx batchTx = new Tx();
        
        for (Payment payment : payments) {
            batchTx.payToAddress(payment.getReceiver(), payment.getAmount());
        }
        
        // All fees paid by central account
        return executeWithCentralFee(batchTx.from(payments.get(0).getSender()));
    }
}
```

#### Proportional Fee Distribution

```java
public class ProportionalFeeService {
    
    public TxResult executeWithProportionalFees(List<WeightedTransaction> weightedTxs) {
        // Calculate fee weights
        BigInteger totalWeight = weightedTxs.stream()
            .map(WeightedTransaction::getWeight)
            .reduce(BigInteger.ZERO, BigInteger::add);
            
        return quickTxBuilder
            .compose(weightedTxs.stream()
                .map(WeightedTransaction::getTransaction)
                .toArray(AbstractTx[]::new))
            .postBalanceTx((context, txn) -> {
                // Distribute fees proportionally after balancing
                distributeFees(txn, weightedTxs, totalWeight);
            })
            .withSigners(getRequiredSigners(weightedTxs))
            .completeAndWait();
    }
}
```

### Conditional Transaction Building

#### Dynamic Transaction Assembly

```java
public class ConditionalTransactionBuilder {
    
    public TxResult buildContextualTransaction(TransactionContext context) {
        Tx baseTx = new Tx().from(context.getSender());
        
        // Add conditional operations
        if (context.shouldSendPayment()) {
            baseTx.payToAddress(context.getPaymentReceiver(), context.getPaymentAmount());
        }
        
        if (context.shouldMintTokens()) {
            baseTx.mintAssets(context.getMintPolicy(), context.getMintAssets());
        }
        
        if (context.shouldRegisterStake()) {
            baseTx.registerStakeAddress(context.getStakeAccount());
        }
        
        // Add conditional metadata
        if (context.getMetadata() != null) {
            baseTx.attachMetadata(context.getMetadata());
        }
        
        return quickTxBuilder
            .compose(baseTx)
            .withSigners(context.getRequiredSigners())
            .completeAndWait();
    }
}
```

#### Strategy Pattern for Transaction Types

```java
public interface TransactionStrategy {
    AbstractTx buildTransaction(TransactionContext context);
    List<TxSigner> getRequiredSigners(TransactionContext context);
}

public class PaymentStrategy implements TransactionStrategy {
    @Override
    public AbstractTx buildTransaction(TransactionContext context) {
        return new Tx()
            .payToAddress(context.getReceiver(), context.getAmount())
            .from(context.getSender());
    }
    
    @Override
    public List<TxSigner> getRequiredSigners(TransactionContext context) {
        return Arrays.asList(SignerProviders.signerFrom(context.getSenderAccount()));
    }
}

public class ContractInteractionStrategy implements TransactionStrategy {
    @Override
    public AbstractTx buildTransaction(TransactionContext context) {
        return new ScriptTx()
            .collectFrom(context.getContractUtxo(), context.getRedeemer())
            .payToContract(context.getContractAddress(), context.getAmount(), context.getDatum())
            .attachSpendingValidator(context.getValidator());
    }
    
    @Override
    public List<TxSigner> getRequiredSigners(TransactionContext context) {
        return Arrays.asList(SignerProviders.signerFrom(context.getUserAccount()));
    }
}

public class ContextualTransactionService {
    private final Map<TransactionType, TransactionStrategy> strategies;
    
    public TxResult executeTransaction(TransactionType type, TransactionContext context) {
        TransactionStrategy strategy = strategies.get(type);
        AbstractTx transaction = strategy.buildTransaction(context);
        List<TxSigner> signers = strategy.getRequiredSigners(context);
        
        TxContext txContext = quickTxBuilder.compose(transaction);
        signers.forEach(txContext::withSigner);
        
        return txContext.completeAndWait();
    }
}
```

### Transaction Hooks and Middleware

#### Pre-Balance Transformations

```java
public class PreBalanceMiddleware {
    
    public TxBuilder addDeveloperFee(BigInteger devFeeAmount, String devAddress) {
        return (context, txn) -> {
            // Add developer fee before balancing
            TransactionOutput devFee = TransactionOutput.builder()
                .address(devAddress)
                .value(Value.builder().coin(devFeeAmount).build())
                .build();
            txn.getBody().getOutputs().add(devFee);
        };
    }
    
    public TxBuilder addProtocolDonation(BigInteger donationAmount) {
        return (context, txn) -> {
            // Add protocol donation
            BigInteger currentTreasury = getCurrentTreasuryValue();
            txn.getBody().setTreasuryValue(currentTreasury.add(donationAmount));
        };
    }
    
    public TxResult executeWithMiddleware(AbstractTx transaction) {
        return quickTxBuilder
            .compose(transaction)
            .preBalanceTx(addDeveloperFee(ADA.ada(0.5), DEV_ADDRESS))
            .preBalanceTx(addProtocolDonation(ADA.ada(1)))
            .withSigner(signer)
            .completeAndWait();
    }
}
```

#### Post-Balance Optimizations

```java
public class PostBalanceOptimizer {
    
    public TxBuilder optimizeOutputs() {
        return (context, txn) -> {
            // Merge outputs to same address
            Map<String, List<TransactionOutput>> outputsByAddress = txn.getBody()
                .getOutputs()
                .stream()
                .collect(Collectors.groupingBy(TransactionOutput::getAddress));
                
            List<TransactionOutput> optimizedOutputs = outputsByAddress.entrySet()
                .stream()
                .map(this::mergeOutputsForAddress)
                .collect(Collectors.toList());
                
            txn.getBody().setOutputs(optimizedOutputs);
        };
    }
    
    public TxBuilder addChangeOptimization() {
        return (context, txn) -> {
            // Optimize change output size
            List<TransactionOutput> outputs = txn.getBody().getOutputs();
            TransactionOutput changeOutput = findChangeOutput(outputs);
            
            if (changeOutput != null && isSmallChange(changeOutput)) {
                // Donate small change to treasury instead of creating dust
                outputs.remove(changeOutput);
                BigInteger changeAmount = changeOutput.getValue().getCoin();
                donateToTreasury(txn, changeAmount);
            }
        };
    }
}
```

### Validation and Inspection Patterns

#### Transaction Validation Pipeline

```java
public class TransactionValidator {
    
    public interface ValidationRule {
        ValidationResult validate(Transaction transaction);
    }
    
    public static class FeeLimitRule implements ValidationRule {
        private final BigInteger maxFee;
        
        public FeeLimitRule(BigInteger maxFee) {
            this.maxFee = maxFee;
        }
        
        @Override
        public ValidationResult validate(Transaction transaction) {
            BigInteger fee = transaction.getBody().getFee();
            if (fee.compareTo(maxFee) > 0) {
                return ValidationResult.failure("Fee " + fee + " exceeds limit " + maxFee);
            }
            return ValidationResult.success();
        }
    }
    
    public static class OutputSizeRule implements ValidationRule {
        private final int maxOutputs;
        
        @Override
        public ValidationResult validate(Transaction transaction) {
            int outputCount = transaction.getBody().getOutputs().size();
            if (outputCount > maxOutputs) {
                return ValidationResult.failure("Too many outputs: " + outputCount);
            }
            return ValidationResult.success();
        }
    }
    
    private final List<ValidationRule> rules = Arrays.asList(
        new FeeLimitRule(ADA.ada(2)),
        new OutputSizeRule(20),
        new MinAdaRule()
    );
    
    public Consumer<Transaction> createValidator() {
        return transaction -> {
            for (ValidationRule rule : rules) {
                ValidationResult result = rule.validate(transaction);
                if (!result.isValid()) {
                    throw new TransactionValidationException(result.getErrorMessage());
                }
            }
        };
    }
}
```

#### Comprehensive Transaction Inspection

```java
public class TransactionInspector {
    
    public Consumer<Transaction> createDetailedInspector() {
        return transaction -> {
            logTransactionSummary(transaction);
            logFeeAnalysis(transaction);
            logOutputAnalysis(transaction);
            logScriptAnalysis(transaction);
            logMetadataAnalysis(transaction);
        };
    }
    
    private void logTransactionSummary(Transaction transaction) {
        TransactionBody body = transaction.getBody();
        
        log.info("=== Transaction Summary ===");
        log.info("Inputs: {}", body.getInputs().size());
        log.info("Outputs: {}", body.getOutputs().size());
        log.info("Fee: {} ADA", adaFromLovelace(body.getFee()));
        log.info("Size: {} bytes", transaction.serialize().length);
        
        if (body.getValidityIntervalStart() != null) {
            log.info("Valid from slot: {}", body.getValidityIntervalStart());
        }
        
        if (body.getTtl() != null) {
            log.info("Valid until slot: {}", body.getTtl());
        }
    }
    
    private void logFeeAnalysis(Transaction transaction) {
        BigInteger fee = transaction.getBody().getFee();
        int size = transaction.serialize().length;
        BigInteger feePerByte = fee.divide(BigInteger.valueOf(size));
        
        log.info("=== Fee Analysis ===");
        log.info("Total fee: {} lovelace", fee);
        log.info("Fee per byte: {} lovelace", feePerByte);
        log.info("Fee rate: {} ADA/KB", adaFromLovelace(feePerByte.multiply(BigInteger.valueOf(1024))));
    }
    
    private void logOutputAnalysis(Transaction transaction) {
        List<TransactionOutput> outputs = transaction.getBody().getOutputs();
        
        log.info("=== Output Analysis ===");
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            log.info("Output {}: {} to {}", 
                i, 
                formatValue(output.getValue()), 
                truncateAddress(output.getAddress())
            );
            
            if (output.getDatumHash() != null) {
                log.info("  Datum hash: {}", output.getDatumHash());
            }
            
            if (output.getInlineDatum() != null) {
                log.info("  Inline datum: {} bytes", output.getInlineDatum().getData().length);
            }
            
            if (output.getScriptRef() != null) {
                log.info("  Script reference: {} bytes", output.getScriptRef().length);
            }
        }
    }
}
```

## Async and Reactive Patterns

### Async Transaction Pipeline

```java
public class AsyncTransactionService {
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public CompletableFuture<TxResult> executeAsync(AbstractTx transaction) {
        return quickTxBuilder
            .compose(transaction)
            .withSigner(defaultSigner)
            .completeAndWaitAsync(
                System.out::println,  // Log consumer
                executorService       // Custom executor
            );
    }
    
    public CompletableFuture<List<TxResult>> executeBatch(List<AbstractTx> transactions) {
        List<CompletableFuture<TxResult>> futures = transactions.stream()
            .map(this::executeAsync)
            .collect(Collectors.toList());
            
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
            );
    }
    
    public CompletableFuture<TxResult> executeWithRetry(AbstractTx transaction, int maxRetries) {
        return executeAsync(transaction)
            .thenCompose(result -> {
                if (result.isSuccessful() || maxRetries <= 0) {
                    return CompletableFuture.completedFuture(result);
                } else {
                    return executeWithRetry(transaction, maxRetries - 1);
                }
            });
    }
}
```

### Reactive Transaction Streams

```java
public class ReactiveTransactionService {
    
    public Observable<TxResult> processTransactionStream(Observable<AbstractTx> transactionStream) {
        return transactionStream
            .flatMap(tx -> Observable.fromFuture(
                quickTxBuilder
                    .compose(tx)
                    .withSigner(defaultSigner)
                    .completeAndWaitAsync()
            ))
            .retry(3)  // Retry failed transactions
            .filter(TxResult::isSuccessful);  // Only emit successful results
    }
    
    public Observable<TxResult> processWithBackpressure(Observable<AbstractTx> transactionStream) {
        return transactionStream
            .buffer(5)  // Buffer transactions
            .concatMap(batch -> Observable.fromIterable(batch)
                .flatMap(tx -> Observable.fromFuture(executeAsync(tx)), 3)  // Max 3 concurrent
            );
    }
}
```

## Error Handling Patterns

### Graceful Degradation

```java
public class ResilientTransactionService {
    
    public TxResult executeWithFallback(AbstractTx primaryTx, AbstractTx fallbackTx) {
        try {
            TxResult result = quickTxBuilder
                .compose(primaryTx)
                .withSigner(primarySigner)
                .complete();
                
            if (result.isSuccessful()) {
                return result;
            }
            
            log.warn("Primary transaction failed, trying fallback: {}", result.getResponse());
            
        } catch (Exception e) {
            log.error("Primary transaction error: {}", e.getMessage());
        }
        
        // Execute fallback transaction
        return quickTxBuilder
            .compose(fallbackTx)
            .withSigner(fallbackSigner)
            .complete();
    }
}
```

### Circuit Breaker Pattern

```java
public class CircuitBreakerTransactionService {
    private final CircuitBreaker circuitBreaker;
    
    public TxResult executeWithCircuitBreaker(AbstractTx transaction) {
        return circuitBreaker.call(() -> {
            TxResult result = quickTxBuilder
                .compose(transaction)
                .withSigner(signer)
                .complete();
                
            if (!result.isSuccessful()) {
                throw new TransactionFailedException(result.getResponse());
            }
            
            return result;
        });
    }
}
```

## Testing Patterns

### Transaction Builder Testing

```java
@ExtendWith(MockitoExtension.class)
class TransactionBuilderTest {
    
    @Mock
    private QuickTxBuilder mockBuilder;
    
    @Mock
    private TxContext mockContext;
    
    @Test
    void shouldBuildPaymentTransaction() {
        // Given
        when(mockBuilder.compose(any(Tx.class))).thenReturn(mockContext);
        when(mockContext.withSigner(any())).thenReturn(mockContext);
        when(mockContext.complete()).thenReturn(TxResult.success("tx_hash"));
        
        PaymentService service = new PaymentService(mockBuilder);
        
        // When
        TxResult result = service.sendPayment("sender", "receiver", Amount.ada(10));
        
        // Then
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTxHash()).isEqualTo("tx_hash");
        
        verify(mockBuilder).compose(argThat(tx -> 
            tx instanceof Tx && ((Tx) tx).getFromAddress().equals("sender")
        ));
    }
}
```

### Integration Testing

```java
@TestMethodOrder(OrderAnnotation.class)
class QuickTxIntegrationTest {
    
    private static QuickTxBuilder quickTxBuilder;
    private static Account testAccount;
    
    @BeforeAll
    static void setup() {
        // Use testnet for integration tests
        BackendService backend = new MockBackendService(); // Or testnet backend
        quickTxBuilder = new QuickTxBuilder(backend);
        testAccount = Account.createFromMnemonic(Networks.testnet(), TEST_MNEMONIC);
    }
    
    @Test
    @Order(1)
    void shouldSendSimplePayment() {
        Tx payment = new Tx()
            .payToAddress(RECEIVER_ADDRESS, Amount.ada(1))
            .from(testAccount.baseAddress());
            
        TxResult result = quickTxBuilder
            .compose(payment)
            .withSigner(SignerProviders.signerFrom(testAccount))
            .completeAndWait();
            
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTxStatus()).isEqualTo(TxStatus.CONFIRMED);
    }
    
    @Test
    @Order(2)
    void shouldMintAndTransferToken() {
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test", 1, 1);
        Asset token = new Asset("TestToken", BigInteger.valueOf(1000));
        
        Tx mint = new Tx()
            .mintAssets(policy.getPolicyScript(), token, RECEIVER_ADDRESS)
            .from(testAccount.baseAddress());
            
        TxResult result = quickTxBuilder
            .compose(mint)
            .withSigner(SignerProviders.signerFrom(testAccount))
            .withSigner(SignerProviders.signerFrom(policy))
            .completeAndWait();
            
        assertThat(result.isSuccessful()).isTrue();
    }
}
```

## Performance Optimization Patterns

### Connection Pooling and Reuse

```java
public class OptimizedTransactionService {
    private final QuickTxBuilder quickTxBuilder;
    private final Cache<String, TxSigner> signerCache;
    
    public OptimizedTransactionService(BackendService backendService) {
        this.quickTxBuilder = new QuickTxBuilder(backendService);
        this.signerCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    }
    
    public TxResult executeBatch(List<TransactionRequest> requests) {
        // Group by sender to optimize UTXO selection
        Map<String, List<TransactionRequest>> bySender = requests.stream()
            .collect(Collectors.groupingBy(TransactionRequest::getSender));
            
        List<AbstractTx> transactions = bySender.entrySet().stream()
            .map(this::createBatchTransaction)
            .collect(Collectors.toList());
            
        return quickTxBuilder
            .compose(transactions.toArray(new AbstractTx[0]))
            .withUtxoSelectionStrategy(new LargestFirstUtxoSelectionStrategy())
            .mergeOutputs(true)  // Optimize outputs
            .withSigners(getCachedSigners(requests))
            .completeAndWait();
    }
}
```

### Lazy Evaluation Patterns

```java
public class LazyTransactionBuilder {
    private Supplier<AbstractTx> transactionSupplier;
    private List<TxSigner> signers = new ArrayList<>();
    private Duration timeout = Duration.ofMinutes(2);
    
    public LazyTransactionBuilder transaction(Supplier<AbstractTx> supplier) {
        this.transactionSupplier = supplier;
        return this;
    }
    
    public LazyTransactionBuilder signer(TxSigner signer) {
        this.signers.add(signer);
        return this;
    }
    
    public LazyTransactionBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public TxResult execute() {
        // Only build transaction when needed
        AbstractTx transaction = transactionSupplier.get();
        
        TxContext context = quickTxBuilder.compose(transaction);
        signers.forEach(context::withSigner);
        
        return context.completeAndWait(timeout);
    }
}
```

## Best Practices Summary

### 1. **Composition Over Inheritance**
```java
// ✅ Good - Compose transactions for complex operations
public TxResult complexOperation() {
    Tx payment = new Tx().payToAddress(addr, amount).from(sender);
    ScriptTx scriptOp = new ScriptTx().collectFrom(utxo, redeemer);
    
    return quickTxBuilder.compose(payment, scriptOp)
        .withSigners(getSigners())
        .complete();
}

// ❌ Avoid - Don't extend transaction classes
public class CustomTx extends Tx { ... }
```

### 2. **Explicit Configuration**
```java
// ✅ Good - Explicit configuration
return quickTxBuilder
    .compose(tx)
    .feePayer(feePayerAddress)
    .validTo(expirationSlot)
    .withSigner(signer)
    .complete();

// ❌ Avoid - Implicit behavior reliance
return quickTxBuilder.compose(tx).complete();
```

### 3. **Error Handling**
```java
// ✅ Good - Comprehensive error handling
TxResult result = quickTxBuilder.compose(tx).withSigner(signer).complete();
if (!result.isSuccessful()) {
    handleTransactionError(result);
    return;
}
processSuccess(result);

// ❌ Avoid - Ignoring errors
String txHash = quickTxBuilder.compose(tx).withSigner(signer).complete().getValue();
```

### 4. **Resource Management**
```java
// ✅ Good - Reuse builder instances
private final QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

// ❌ Avoid - Creating new builders for each transaction
QuickTxBuilder builder = new QuickTxBuilder(backendService);
```

## Next Steps

This guide covers advanced builder patterns for QuickTx. For additional guidance, see:

- **[Error Handling Guide](./error-handling.md)** - Comprehensive error management
- **[Performance Guide](./performance.md)** - Production optimization
- **[API Reference](./api-reference.md)** - Complete method documentation

## Resources

- **[Builder Pattern Documentation](https://refactoring.guru/design-patterns/builder)** - General builder pattern concepts
- **[Fluent Interface Design](https://martinfowler.com/bliki/FluentInterface.html)** - Martin Fowler's fluent interface guide
- **[QuickTx Examples](https://github.com/bloxbean/cardano-client-examples)** - Complete working examples

---

**QuickTx builder patterns provide powerful composition capabilities while maintaining clean, readable, and maintainable code for complex Cardano applications.**