package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.dsl.context.TxHandlerRegistry;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TDD tests for TxDslBuilder V3 design - Context Wrapper Pattern
 */
class TxDslBuilderTest {

    private BackendService backendService;
    private TxDslBuilder txDslBuilder;

    @BeforeEach
    void setup() {
        backendService = mock(BackendService.class);
        txDslBuilder = new TxDslBuilder(backendService);
        TxHandlerRegistry.clear(); // Clean registry for each test
    }

    @Test
    void testTxDslBuilderCreation() {
        // Given - BackendService
        // When - Create TxDslBuilder
        TxDslBuilder builder = new TxDslBuilder(backendService);
        
        // Then - Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    void testContextConfiguration() {
        // Given - TxDslBuilder
        // When - Configure context
        TxDslBuilder result = txDslBuilder
            .feePayer("addr1_treasury...")
            .collateralPayer("addr1_treasury...")
            .utxoSelectionStrategy("LARGEST_FIRST")
            .signer("treasurySigner");
        
        // Then - Should return same builder for chaining
        assertThat(result).isSameAs(txDslBuilder);
    }

    @Test
    void testSingleTxDslComposition() {
        // Given - TxDsl transaction
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));
        
        // When - Compose with context
        QuickTxBuilder.TxContext txContext = txDslBuilder
            .feePayer("addr1_treasury...")
            .compose(txDsl);
        
        // Then - Should return QuickTxBuilder.TxContext
        assertThat(txContext).isNotNull();
        assertThat(txContext).isInstanceOf(QuickTxBuilder.TxContext.class);
    }

    @Test
    void testMultipleTxDslComposition() {
        // Given - Multiple TxDsl transactions
        TxDsl txDsl1 = new TxDsl()
            .from("addr1_sender1...")
            .payToAddress("addr1_receiver1...", Amount.ada(5));
        
        TxDsl txDsl2 = new TxDsl()
            .from("addr1_sender2...")
            .payToAddress("addr1_receiver2...", Amount.ada(3));
        
        // When - Compose multiple TxDsl with shared context
        QuickTxBuilder.TxContext txContext = txDslBuilder
            .feePayer("addr1_treasury...")
            .signer("treasurySigner")
            .compose(txDsl1, txDsl2);
        
        // Then - Should return QuickTxBuilder.TxContext
        assertThat(txContext).isNotNull();
        assertThat(txContext).isInstanceOf(QuickTxBuilder.TxContext.class);
    }

    @Test
    void testContextWithHandlerRegistry() {
        // Given - Register handlers
        TxSigner mockSigner = mock(TxSigner.class);
        UtxoSelectionStrategy mockStrategy = mock(UtxoSelectionStrategy.class);
        
        TxHandlerRegistry.register("treasurySigner", mockSigner);
        TxHandlerRegistry.register("LARGEST_FIRST", mockStrategy);
        
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));
        
        // When - Use registered handlers in context
        QuickTxBuilder.TxContext txContext = txDslBuilder
            .utxoSelectionStrategy("LARGEST_FIRST")
            .signer("treasurySigner")
            .compose(txDsl);
        
        // Then - Should apply context successfully
        assertThat(txContext).isNotNull();
    }

    @Test
    void testVariableExtraction() {
        // Given - TxDsl with variables
        TxDsl txDsl = new TxDsl()
            .withVariable("treasury", "addr1_treasury...")
            .from("${treasury}")
            .payToAddress("addr1_receiver...", Amount.ada(5));
        
        // When - Compose with variable resolution
        QuickTxBuilder.TxContext txContext = txDslBuilder
            .feePayer("${treasury}")
            .compose(txDsl);
        
        // Then - Variables should be extracted and resolved
        assertThat(txContext).isNotNull();
    }

    @Test
    void testBackwardCompatibilityWithAbstractTx() {
        // Given - TxDsl for unwrapping
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));
        
        // When - Use traditional AbstractTx compose method
        QuickTxBuilder.TxContext txContext = txDslBuilder
            .feePayer("addr1_treasury...")
            .compose(txDsl.unwrap()); // Use AbstractTx compose method
        
        // Then - Should work with existing QuickTxBuilder API
        assertThat(txContext).isNotNull();
        assertThat(txContext).isInstanceOf(QuickTxBuilder.TxContext.class);
    }

    @Test
    void testEmptyContextStillWorks() {
        // Given - TxDsl without any context
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));
        
        // When - Compose without setting any context
        QuickTxBuilder.TxContext txContext = txDslBuilder.compose(txDsl);
        
        // Then - Should still work (context is optional)
        assertThat(txContext).isNotNull();
    }

    @Test
    void testBuilderReusability() {
        // Given - TxDslBuilder with context
        txDslBuilder
            .feePayer("addr1_treasury...")
            .signer("treasurySigner");
        
        TxDsl txDsl1 = new TxDsl()
            .from("addr1_sender1...")
            .payToAddress("addr1_receiver1...", Amount.ada(5));
        
        TxDsl txDsl2 = new TxDsl()
            .from("addr1_sender2...")
            .payToAddress("addr1_receiver2...", Amount.ada(3));
        
        // When - Reuse same builder for multiple compositions
        QuickTxBuilder.TxContext txContext1 = txDslBuilder.compose(txDsl1);
        QuickTxBuilder.TxContext txContext2 = txDslBuilder.compose(txDsl2);
        
        // Then - Both should work with same context
        assertThat(txContext1).isNotNull();
        assertThat(txContext2).isNotNull();
    }
}