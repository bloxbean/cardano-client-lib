package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TxPlanRefYamlTest {

    @Test
    void tx_from_ref_serializes_and_deserializes() {
        Tx tx = new Tx()
                .payToAddress("addr_test1qxyz...receiver", Amount.ada(1))
                .fromRef("account://alice")
                .withChangeAddress("addr_test1qchange...111");

        TxPlan plan = TxPlan.from(tx)
                .feePayerRef("wallet://ops")
                .collateralPayerRef("wallet://ops")
                .withSigner("policy://nft", "policy");

        String yaml = plan.toYaml();

        assertThat(yaml).contains("from_ref: account://alice");
        assertThat(yaml).contains("fee_payer_ref: wallet://ops");
        assertThat(yaml).contains("collateral_payer_ref: wallet://ops");
        assertThat(yaml).contains("- type: policy");
        assertThat(yaml).contains("ref: policy://nft");
        assertThat(yaml).contains("scope: policy");

        TxPlan restored = TxPlan.from(yaml);
        assertThat(restored.getTxs()).hasSize(1);
        Tx restoredTx = (Tx) restored.getTxs().get(0);
        assertThat(restoredTx.getFromRef()).isEqualTo("account://alice");
        assertThat(restored.getFeePayerRef()).isEqualTo("wallet://ops");
        assertThat(restored.getCollateralPayerRef()).isEqualTo("wallet://ops");
        assertThat(restored.getSignerRefs()).isNotNull();
        assertThat(restored.getSignerRefs()).hasSize(1);
        assertThat(restored.getSignerRefs().get(0).getRef()).isEqualTo("policy://nft");
        assertThat(restored.getSignerRefs().get(0).getScope()).isEqualTo("policy");
    }
}
