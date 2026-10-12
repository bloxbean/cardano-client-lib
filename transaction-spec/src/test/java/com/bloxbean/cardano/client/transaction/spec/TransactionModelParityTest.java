package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Transaction#deserialize(byte[])} on the iterative decoder gives the same models as on cbor-java's decoder, for
 * every real transaction and every transaction of every real block: the same body, witness set and validity, by
 * serialization and JSON. The old path could not read Shelley transactions with metadata and dropped Allegra/Mary aux
 * data; those now decode, and their aux data is checked against the original bytes.
 */
class TransactionModelParityTest {

    @Test
    void modelsDecodeAsWithCborJava() throws Exception {
        int compared = 0;
        int fixed = 0;
        for (RealCborFixturesAccess.Tx tx : RealCborFixturesAccess.allTxsAndBlockTxs()) {
            if (RealCborFixturesAccess.tooDeepForRecursiveCode(tx.cbor()))
                continue; // the deeply nested trigger: covered by the depth tests
            Transaction legacy;
            try {
                legacy = LegacyTransactionDeserializer.deserialize(tx.cbor());
            } catch (Exception e) {
                legacy = null; // a Shelley tx with metadata: [body, witnesses, metadata]
            }
            Transaction decoded = Transaction.deserialize(tx.cbor());
            RawTx raw = RawTx.of(tx.cbor());
            if (legacy != null) {
                // some model classes (certificates) have no value equality, so compare what the models hold
                assertThat(decoded.getBody().serialize()).as(tx.name()).isEqualTo(legacy.getBody().serialize());
                assertThat(decoded.getWitnessSet().serialize()).as(tx.name()).isEqualTo(legacy.getWitnessSet().serialize());
                assertThat(decoded.isValid()).as(tx.name()).isEqualTo(legacy.isValid());
                assertThat(JsonUtil.getPrettyJson(decoded.getBody())).as(tx.name()).isEqualTo(JsonUtil.getPrettyJson(legacy.getBody()));
                compared++;
            }
            if (legacy == null || (legacy.getAuxiliaryData() == null) != raw.auxData().isEmpty()) {
                assertSameAuxData(tx.name(), decoded.getAuxiliaryData(), raw);
                fixed++;
            } else if (legacy.getAuxiliaryData() != null) {
                assertThat(CborSerializationUtil.serialize(decoded.getAuxiliaryData().serialize()))
                        .as(tx.name()).isEqualTo(CborSerializationUtil.serialize(legacy.getAuxiliaryData().serialize()));
            }
        }
        assertThat(compared).isGreaterThan(300);
        assertThat(fixed).isGreaterThanOrEqualTo(10);
        System.out.println("model parity: " + compared + " compared, " + fixed + " decoded only by the new path");
    }

    // The aux data the old path could not read: its metadata and native scripts are the ones in the original bytes.
    private static void assertSameAuxData(String name, AuxiliaryData auxData, RawTx raw) throws Exception {
        CborSpan aux = raw.auxData().orElseThrow();
        CborSpan metadata = aux.majorType() == 4 ? aux.get(0) : aux;
        assertThat(((CBORMetadata) auxData.getMetadata()).serialize()).as(name)
                .isEqualTo(CBORMetadata.deserialize(metadata.bytes()).serialize());
        List<String> expected = new ArrayList<>();
        raw.auxScripts().forEach(script -> expected.add(HexUtil.encodeHexString(script.hash())));
        List<String> actual = new ArrayList<>();
        for (NativeScript script : auxData.getNativeScripts())
            actual.add(HexUtil.encodeHexString(script.getScriptHash()));
        assertThat(actual).as(name).isEqualTo(expected);
    }
}
