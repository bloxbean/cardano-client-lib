package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read without an era, every real transaction, Shelley to Conway, stays in Conway, with no era set (ADR 0004 W6): the
 * pre-Alonzo ones through the rows their eras dropped.
 */
class RealTransactionEraTest {

    @Test
    void realTransactionsAreReadInConway() throws Exception {
        List<RealCborFixtures.Tx> txs = new ArrayList<>(RealCborFixtures.allTxs());
        txs.addAll(RealCborFixtures.blockTxs());
        assertThat(txs).isNotEmpty();
        for (RealCborFixtures.Tx tx : txs) {
            assertThat(RawTx.eraOf(tx.cbor(), Era.Conway)).as(tx.txHash()).isEqualTo(Era.Conway);
            assertThat(Transaction.deserialize(tx.cbor()).getEra()).as(tx.txHash()).isNull();
        }
    }
}
