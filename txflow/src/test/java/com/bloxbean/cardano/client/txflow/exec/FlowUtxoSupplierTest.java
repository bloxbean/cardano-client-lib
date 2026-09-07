package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowUtxoSupplierTest {

    private static final String ADDR = "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp";
    private static final String TX_HASH_1 = "aaaa000000000000000000000000000000000000000000000000000000000001";
    private static final String TX_HASH_2 = "bbbb000000000000000000000000000000000000000000000000000000000002";
    private static final String TX_HASH_3 = "cccc000000000000000000000000000000000000000000000000000000000003";

    @Mock
    private UtxoSupplier baseSupplier;

    private FlowExecutionContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        context = new FlowExecutionContext("test-flow");
    }

    private Utxo utxo(String txHash, int index, String address) {
        return Utxo.builder()
                .txHash(txHash)
                .outputIndex(index)
                .address(address)
                .amount(List.of(Amount.builder().unit("lovelace").quantity(java.math.BigInteger.valueOf(5_000_000)).build()))
                .build();
    }

    // ==================== getPage: filters spent UTXOs from previous steps ====================

    @Test
    void testGetPage_filtersSpentUtxosFromPreviousSteps() {
        // Step 1 spent TX_HASH_1#0
        FlowStepResult step1Result = FlowStepResult.success(
                "step1", TX_HASH_2,
                List.of(utxo(TX_HASH_2, 0, ADDR)),
                List.of(new TransactionInput(TX_HASH_1, 0))
        );
        context.recordStepResult("step1", step1Result);

        // Base supplier returns both the spent UTXO and an unspent one
        Utxo spentUtxo = utxo(TX_HASH_1, 0, ADDR);
        Utxo unspentUtxo = utxo(TX_HASH_1, 1, ADDR);
        when(baseSupplier.getPage(eq(ADDR), anyInt(), anyInt(), any()))
                .thenReturn(Arrays.asList(spentUtxo, unspentUtxo));

        // No explicit dependencies — but FlowUtxoSupplier should still filter spent inputs
        FlowUtxoSupplier supplier = new FlowUtxoSupplier(baseSupplier, context, Collections.emptyList());

        List<Utxo> result = supplier.getPage(ADDR, 100, 1, OrderEnum.asc);

        assertEquals(1, result.size());
        assertEquals(TX_HASH_1, result.get(0).getTxHash());
        assertEquals(1, result.get(0).getOutputIndex());
    }

    // ==================== getPage: with dependencies, adds pending UTXOs ====================

    @Test
    void testGetPage_withDependencies_addsPendingUtxos() {
        // Step 1 produced output at ADDR
        Utxo pendingUtxo = utxo(TX_HASH_2, 0, ADDR);
        FlowStepResult step1Result = FlowStepResult.success(
                "step1", TX_HASH_2,
                List.of(pendingUtxo),
                List.of(new TransactionInput(TX_HASH_1, 0))
        );
        context.recordStepResult("step1", step1Result);

        // Base supplier returns one UTXO (different from the spent one)
        Utxo baseUtxo = utxo(TX_HASH_3, 0, ADDR);
        when(baseSupplier.getPage(eq(ADDR), anyInt(), anyInt(), any()))
                .thenReturn(List.of(baseUtxo));

        // Step 2 explicitly depends on step1
        StepDependency dep = StepDependency.all("step1");
        FlowUtxoSupplier supplier = new FlowUtxoSupplier(baseSupplier, context, List.of(dep));

        List<Utxo> result = supplier.getPage(ADDR, 100, 1, OrderEnum.asc);

        // Should have both: the unspent base UTXO + pending UTXO from step1
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals(TX_HASH_3)));
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals(TX_HASH_2)));
    }

    // ==================== getPage: with dependencies, filters AND adds ====================

    @Test
    void testGetPage_withDependencies_filtersAndAdds() {
        // Step 1 spent TX_HASH_1#0 and produced TX_HASH_2#0
        Utxo pendingUtxo = utxo(TX_HASH_2, 0, ADDR);
        FlowStepResult step1Result = FlowStepResult.success(
                "step1", TX_HASH_2,
                List.of(pendingUtxo),
                List.of(new TransactionInput(TX_HASH_1, 0))
        );
        context.recordStepResult("step1", step1Result);

        // Base supplier returns the spent UTXO (not yet reflected on-chain)
        Utxo spentUtxo = utxo(TX_HASH_1, 0, ADDR);
        Utxo unspentUtxo = utxo(TX_HASH_1, 1, ADDR);
        when(baseSupplier.getPage(eq(ADDR), anyInt(), anyInt(), any()))
                .thenReturn(Arrays.asList(spentUtxo, unspentUtxo));

        StepDependency dep = StepDependency.all("step1");
        FlowUtxoSupplier supplier = new FlowUtxoSupplier(baseSupplier, context, List.of(dep));

        List<Utxo> result = supplier.getPage(ADDR, 100, 1, OrderEnum.asc);

        // Should filter out TX_HASH_1#0 (spent) and include TX_HASH_1#1 + TX_HASH_2#0 (pending)
        assertEquals(2, result.size());
        assertFalse(result.stream().anyMatch(u -> u.getTxHash().equals(TX_HASH_1) && u.getOutputIndex() == 0));
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals(TX_HASH_1) && u.getOutputIndex() == 1));
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals(TX_HASH_2) && u.getOutputIndex() == 0));
    }

    // ==================== getAll: filters spent UTXOs ====================

    @Test
    void testGetAll_filtersSpentUtxos() {
        // Step 1 spent TX_HASH_1#0
        FlowStepResult step1Result = FlowStepResult.success(
                "step1", TX_HASH_2,
                List.of(utxo(TX_HASH_2, 0, ADDR)),
                List.of(new TransactionInput(TX_HASH_1, 0))
        );
        context.recordStepResult("step1", step1Result);

        Utxo spentUtxo = utxo(TX_HASH_1, 0, ADDR);
        Utxo unspentUtxo = utxo(TX_HASH_1, 1, ADDR);
        when(baseSupplier.getAll(ADDR)).thenReturn(Arrays.asList(spentUtxo, unspentUtxo));

        FlowUtxoSupplier supplier = new FlowUtxoSupplier(baseSupplier, context, Collections.emptyList());

        List<Utxo> result = supplier.getAll(ADDR);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getOutputIndex());
    }

    // ==================== getTxOutput: falls back to pending ====================

    @Test
    void testGetTxOutput_fallbackToPending() {
        // Step 1 produced a UTXO that's not on-chain yet
        Utxo pendingUtxo = utxo(TX_HASH_2, 0, ADDR);
        FlowStepResult step1Result = FlowStepResult.success(
                "step1", TX_HASH_2,
                List.of(pendingUtxo),
                Collections.emptyList()
        );
        context.recordStepResult("step1", step1Result);

        // Base supplier doesn't have it
        when(baseSupplier.getTxOutput(TX_HASH_2, 0)).thenReturn(Optional.empty());

        StepDependency dep = StepDependency.all("step1");
        FlowUtxoSupplier supplier = new FlowUtxoSupplier(baseSupplier, context, List.of(dep));

        Optional<Utxo> result = supplier.getTxOutput(TX_HASH_2, 0);

        assertTrue(result.isPresent());
        assertEquals(TX_HASH_2, result.get().getTxHash());
        assertEquals(0, result.get().getOutputIndex());
    }

    @Test
    void testGetTxOutputHonorsDependencySelection() {
        Utxo first = utxo(TX_HASH_2, 0, ADDR);
        Utxo selected = utxo(TX_HASH_2, 1, ADDR);
        context.recordStepResult("step1", FlowStepResult.success(
                "step1", TX_HASH_2, List.of(first, selected), Collections.emptyList()));
        when(baseSupplier.getTxOutput(anyString(), anyInt())).thenReturn(Optional.empty());

        FlowUtxoSupplier supplier = new FlowUtxoSupplier(
                baseSupplier, context, List.of(StepDependency.atIndex("step1", 1)));

        assertTrue(supplier.getTxOutput(TX_HASH_2, 0).isEmpty());
        assertEquals(selected, supplier.getTxOutput(TX_HASH_2, 1).orElseThrow());
    }

    @Test
    void explicitPortableOutputLookupDoesNotBroadenAddressDependencies() {
        Utxo explicit = utxo(TX_HASH_2, 0, ADDR);
        context.recordStepResult("producer", FlowStepResult.success(
                "producer", TX_HASH_2, List.of(explicit), Collections.emptyList()));
        when(baseSupplier.getTxOutput(anyString(), anyInt())).thenReturn(Optional.empty());
        when(baseSupplier.getAll(ADDR)).thenReturn(List.of());

        FlowUtxoSupplier supplier = new FlowUtxoSupplier(
                baseSupplier, context, List.of(), java.util.Set.of("producer"));

        assertEquals(explicit, supplier.getTxOutput(TX_HASH_2, 0).orElseThrow());
        assertTrue(supplier.getAll(ADDR).isEmpty(),
                "explicit flow_output access must not act like an address dependency");
    }

    @Test
    void portableFundingRelationshipExposesOnlyMatchingAddressOutputs() {
        String otherAddress = "addr_test1vpqother";
        Utxo matching = utxo(TX_HASH_2, 0, ADDR);
        Utxo foreign = utxo(TX_HASH_2, 1, otherAddress);
        context.recordStepResult("producer", FlowStepResult.success(
                "producer", TX_HASH_2, List.of(matching, foreign), Collections.emptyList()));
        when(baseSupplier.getAll(anyString())).thenReturn(List.of());
        when(baseSupplier.getTxOutput(anyString(), anyInt())).thenReturn(Optional.empty());

        FlowUtxoSupplier supplier = new FlowUtxoSupplier(
                baseSupplier, context, List.of(), Set.of(), List.of("producer"));

        assertEquals(List.of(matching), supplier.getAll(ADDR));
        assertEquals(List.of(foreign), supplier.getAll(otherAddress));
        assertTrue(supplier.getTxOutput(TX_HASH_2, 0).isEmpty(),
                "funding availability must not grant exact-output lookup authority");
    }

    @Test
    void portableFundingRelationshipExcludesPendingOutputsSpentByLaterSteps() {
        Utxo olderChange = utxo(TX_HASH_2, 0, ADDR);
        Utxo latestChange = utxo(TX_HASH_3, 0, ADDR);
        context.recordStepResult("step1", FlowStepResult.success(
                "step1", TX_HASH_2, List.of(olderChange),
                List.of(new TransactionInput(TX_HASH_1, 0))));
        context.recordStepResult("step2", FlowStepResult.success(
                "step2", TX_HASH_3, List.of(latestChange),
                List.of(new TransactionInput(TX_HASH_2, 0))));
        when(baseSupplier.getAll(ADDR)).thenReturn(List.of());

        FlowUtxoSupplier supplier = new FlowUtxoSupplier(
                baseSupplier, context, List.of(), Set.of(), List.of("step1", "step2"));

        assertEquals(List.of(latestChange), supplier.getAll(ADDR),
                "transitive funding must not re-offer pending change already spent later");
    }
}
