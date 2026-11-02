package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Transaction#deserialize(byte[])} reads every transaction shape and aux data shape of the Shelley-family eras
 * (ADR 0001 section 1.7), and rejects records that repeat a key (D6).
 */
class TransactionDeserializeShapesTest {
    private static final String BODY = "a3008001800200"; // {0: [], 1: [], 2: 0}
    private static final String METADATA = "a1" + "01" + "6161"; // {1: "a"}
    private static final String KEY_HASH = "55555555555555555555555555555555555555555555555555555555";
    private static final String NATIVE = "8200581c" + KEY_HASH;
    private static final String ADDRESS = "581d60" + KEY_HASH;

    private static Transaction deserialize(String hex) throws CborDeserializationException {
        return Transaction.deserialize(decodeHexString(hex));
    }

    @Test
    void shelleyTransactionWithMetadata() throws Exception {
        // [body, witnesses, metadata]: an IndexOutOfBoundsException before
        Transaction tx = deserialize("83" + BODY + "a0" + METADATA);
        assertThat(tx.isValid()).isTrue();
        assertThat(((CBORMetadata) tx.getAuxiliaryData().getMetadata()).get(BigInteger.ONE)).isEqualTo("a");
        assertThat(deserialize("83" + BODY + "a0" + "f6").getAuxiliaryData()).isNull();
    }

    @Test
    void allegraAndMaryAuxData() throws Exception {
        // [metadata, [native scripts]]: silently dropped before
        Transaction tx = deserialize("83" + BODY + "a0" + "82" + METADATA + "81" + NATIVE);
        assertThat(((CBORMetadata) tx.getAuxiliaryData().getMetadata()).get(BigInteger.ONE)).isEqualTo("a");
        assertThat(tx.getAuxiliaryData().getNativeScripts()).containsExactly(new ScriptPubkey(KEY_HASH));
        // the same shape is valid in later eras
        tx = deserialize("84" + BODY + "a0" + "f5" + "82" + "a0" + "9f" + NATIVE + "ff");
        assertThat(tx.getAuxiliaryData().getNativeScripts()).hasSize(1);
    }

    @Test
    void alonzoAuxDataAndValidity() throws Exception {
        Transaction tx = deserialize("84" + BODY + "a0" + "f4" + "d90103a2" + "00" + METADATA + "0181" + NATIVE);
        assertThat(tx.isValid()).isFalse();
        assertThat(tx.getAuxiliaryData().getNativeScripts()).hasSize(1);
        assertThat(deserialize("84" + BODY + "a0" + "f5" + "f6").getAuxiliaryData()).isNull();
    }

    @Test
    void indefiniteTransactionArray() throws Exception {
        // the BREAK of the indefinite array was read as the aux data before, and the metadata dropped
        Transaction tx = deserialize("9f" + BODY + "a0" + METADATA + "ff");
        assertThat(((CBORMetadata) tx.getAuxiliaryData().getMetadata()).get(BigInteger.ONE)).isEqualTo("a");
        assertThat(deserialize("9f" + BODY + "a0" + "f4" + "f6" + "ff").isValid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "84" + BODY + "a0" + "01" + "f6",                                    // isValid is not a bool
            "82" + "83808080" + "80",                                            // Byron [tx, witnesses]
            "83" + BODY + "a0" + "01",                                           // aux data of no known shape
            "83" + BODY + "a0" + "83" + METADATA + "80" + "80",                   // [metadata, scripts, ?]
            "83" + "a4008001800200" + "0080" + "a0" + "f6",                       // body {0, 1, 2, 0}
            "83" + BODY + "a2" + "0080" + "1800" + "80" + "f6",                   // witness set {0, 0}
            "83" + "a3" + "0080" + "0181" + "a3" + "00" + ADDRESS + "0100" + "0100" + "0200" + "a0" + "f6", // output {0, 1, 1}
            "83" + "a4" + "0080" + "0180" + "0200" + "10" + "a3" + "00" + ADDRESS + "00" + ADDRESS + "0100" + "a0" + "f6", // collateral return {0, 0, 1}
            "83" + BODY + "a0" + "d90103a2" + "00a0" + "00a0",                    // aux #6.259({0, 0})
    })
    void malformedTransactionsAreRejected(String hex) {
        assertThatThrownBy(() -> deserialize(hex)).isInstanceOf(CborDeserializationException.class);
    }
}
