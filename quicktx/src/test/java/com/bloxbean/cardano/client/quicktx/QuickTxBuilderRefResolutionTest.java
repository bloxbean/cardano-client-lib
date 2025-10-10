package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Minimal tests to verify ref-resolution error paths are wired into composition.
 */
class QuickTxBuilderRefResolutionTest {

    @Test
    void build_throws_when_fromRef_present_but_no_registry_configured() {
        // Given a Tx with from_ref and a basic change address
        Tx tx = new Tx()
                .payToAddress("addr_test1qxyz...receiver", com.bloxbean.cardano.client.api.model.Amount.ada(1))
                .fromRef("account://alice")
                .withChangeAddress("addr_test1qchange...111");

        TxPlan plan = TxPlan.from(tx);

        BackendService backend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(backend);

        QuickTxBuilder.TxContext ctx = builder.compose(plan);

        // When/Then: build should fail early due to missing registry
        assertThrows(TxBuildException.class, ctx::build);
    }

    @Test
    void build_throws_when_feePayerRef_present_but_no_registry_configured() {
        // Given a Tx with explicit from address and context fee_payer_ref
        Tx tx = new Tx()
                .payToAddress("addr_test1qxyz...receiver", com.bloxbean.cardano.client.api.model.Amount.ada(1))
                .from("addr_test1qfrom...111")
                .withChangeAddress("addr_test1qchange...111");

        TxPlan plan = TxPlan.from(tx)
                .feePayerRef("wallet://ops");

        BackendService backend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(backend);

        QuickTxBuilder.TxContext ctx = builder.compose(plan);

        // When/Then: build should fail early due to missing registry
        assertThrows(TxBuildException.class, ctx::build);
    }
}

