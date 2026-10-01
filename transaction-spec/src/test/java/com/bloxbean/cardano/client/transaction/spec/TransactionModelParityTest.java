package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Transaction#deserialize(byte[])} on the iterative decoder gives the same models as on cbor-java's decoder, for
 * every real transaction and every transaction of every real block: the same serialization and the same JSON.
 */
class TransactionModelParityTest {

    @Test
    void modelsDecodeAsWithCborJava() throws Exception {
        int compared = 0;
        int failedOnBoth = 0;
        for (RealCborFixturesAccess.Tx tx : RealCborFixturesAccess.allTxsAndBlockTxs()) {
            Transaction legacy;
            try {
                legacy = LegacyTransactionDeserializer.deserialize(tx.cbor());
            } catch (StackOverflowError e) {
                continue; // the deeply nested trigger: the model codecs become iterative in later changes
            } catch (Exception e) {
                // e.g. a Shelley tx with metadata: the new path must fail the same way
                assertThat(tryDeserialize(tx.cbor())).as(tx.name()).isNull();
                failedOnBoth++;
                continue;
            }
            // some model classes (certificates) have no value equality, so compare what the models hold
            Transaction decoded = Transaction.deserialize(tx.cbor());
            assertThat(decoded.serialize()).as(tx.name()).isEqualTo(legacy.serialize());
            assertThat(decoded.toJson()).as(tx.name()).isEqualTo(legacy.toJson());
            compared++;
        }
        assertThat(compared).isGreaterThan(80);
        System.out.println("model parity: " + compared + " compared, " + failedOnBoth + " failing on both paths");
    }

    private static Transaction tryDeserialize(byte[] cbor) {
        try {
            return Transaction.deserialize(cbor);
        } catch (Exception e) {
            return null;
        }
    }
}
