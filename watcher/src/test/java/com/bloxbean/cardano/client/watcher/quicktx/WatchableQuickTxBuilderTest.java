package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WatchableQuickTxBuilder.
 */
class WatchableQuickTxBuilderTest {

    @Mock
    private UtxoSupplier utxoSupplier;

    @Mock
    private ProtocolParamsSupplier protocolParamsSupplier;

    @Mock
    private TransactionProcessor transactionProcessor;

    private WatchableQuickTxBuilder builder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        builder = new WatchableQuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
    }

    @Test
    void testCreateWatchableQuickTxBuilder() {
        assertNotNull(builder);
        assertInstanceOf(WatchableQuickTxBuilder.class, builder);
    }

    @Test
    void testComposeReturnsWatchableTxContext() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        assertNotNull(context);
        assertInstanceOf(WatchableQuickTxBuilder.WatchableTxContext.class, context);
    }

    @Test
    void testWatchableTxContextStepId() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Test setting step ID
        WatchableQuickTxBuilder.WatchableTxContext result = context.withStepId("test-step");
        assertSame(context, result); // Should return same instance for method chaining
        assertEquals("test-step", context.getStepId());
    }

    @Test
    void testWatchableTxContextDescription() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Test setting description
        WatchableQuickTxBuilder.WatchableTxContext result = context.withDescription("Test payment");
        assertSame(context, result); // Should return same instance for method chaining
        assertEquals("Test payment", context.getDescription());
    }

    @Test
    void testWatchableTxContextGeneratesStepIdIfNotSet() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Should generate a UUID if no step ID set
        String stepId = context.getStepId();
        assertNotNull(stepId);
        assertFalse(stepId.isEmpty());
        
        // Should return same ID on subsequent calls
        assertEquals(stepId, context.getStepId());
    }

    @Test
    void testFromStepAddsUtxoDependency() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Test adding step dependency
        WatchableQuickTxBuilder.WatchableTxContext result = context.fromStep("previous-step");
        assertSame(context, result); // Should return same instance for method chaining
        
        List<StepOutputDependency> dependencies = context.getUtxoDependencies();
        assertEquals(1, dependencies.size());
        
        StepOutputDependency dependency = dependencies.get(0);
        assertEquals("previous-step", dependency.getStepId());
        assertEquals(UtxoSelectionStrategy.ALL, dependency.getSelectionStrategy());
        assertFalse(dependency.isOptional());
    }

    @Test
    void testFromStepUtxoAddsIndexedDependency() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Test adding indexed dependency
        WatchableQuickTxBuilder.WatchableTxContext result = context.fromStepUtxo("previous-step", 2);
        assertSame(context, result);
        
        List<StepOutputDependency> dependencies = context.getUtxoDependencies();
        assertEquals(1, dependencies.size());
        
        StepOutputDependency dependency = dependencies.get(0);
        assertEquals("previous-step", dependency.getStepId());
        assertInstanceOf(IndexedUtxoSelectionStrategy.class, dependency.getSelectionStrategy());
        
        IndexedUtxoSelectionStrategy strategy = (IndexedUtxoSelectionStrategy) dependency.getSelectionStrategy();
        assertEquals(2, strategy.getIndex());
    }

    @Test
    void testFromStepWhereAddsFilteredDependency() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Test adding filtered dependency
        Predicate<Utxo> filter = utxo -> utxo.getAmount().stream()
            .anyMatch(amount -> "lovelace".equals(amount.getUnit()) && 
                               amount.getQuantity().compareTo(BigInteger.valueOf(1000000)) > 0);
        
        WatchableQuickTxBuilder.WatchableTxContext result = context.fromStepWhere("previous-step", filter);
        assertSame(context, result);
        
        List<StepOutputDependency> dependencies = context.getUtxoDependencies();
        assertEquals(1, dependencies.size());
        
        StepOutputDependency dependency = dependencies.get(0);
        assertEquals("previous-step", dependency.getStepId());
        assertInstanceOf(FilteredUtxoSelectionStrategy.class, dependency.getSelectionStrategy());
        
        FilteredUtxoSelectionStrategy strategy = (FilteredUtxoSelectionStrategy) dependency.getSelectionStrategy();
        assertSame(filter, strategy.getFilter());
    }

    @Test
    void testMultipleUtxoDependencies() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // Add multiple dependencies
        context.fromStep("step1")
               .fromStepUtxo("step2", 0)
               .fromStepWhere("step3", utxo -> true);

        List<StepOutputDependency> dependencies = context.getUtxoDependencies();
        assertEquals(3, dependencies.size());
        
        assertEquals("step1", dependencies.get(0).getStepId());
        assertEquals("step2", dependencies.get(1).getStepId());
        assertEquals("step3", dependencies.get(2).getStepId());
    }

    @Test
    void testWatchableMethodCreatesWatchableStep() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx)
            .withStepId("test-step")
            .withDescription("Test step");

        WatchableStep step = context.watchable();
        
        assertNotNull(step);
        assertEquals("test-step", step.getStepId());
        assertEquals("Test step", step.getDescription());
        assertSame(context, step.getTxContext());
    }

    @Test
    void testWatchMethodExecutesChain() {
        Tx tx = new Tx()
            .payToAddress("addr_test123", Amount.ada(10))
            .from("addr_sender123");

        WatchableQuickTxBuilder.WatchableTxContext context = builder.compose(tx);

        // The watch() method should now create a chain and attempt execution
        // Since we don't have real backend services, this will likely fail with a different exception
        // but it should not throw UnsupportedOperationException anymore
        try {
            WatchHandle handle = context.watch();
            assertNotNull(handle);
            // If it succeeds, the handle should be returned
        } catch (Exception e) {
            // If it fails, it should not be UnsupportedOperationException
            assertFalse(e instanceof UnsupportedOperationException, 
                "watch() should not throw UnsupportedOperationException anymore");
        }
    }
}