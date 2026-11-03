package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.Nonce;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0002 D4: no decoder silently changes a number. Unsigned 64-bit positions round-trip exactly, and positions that
 * cannot widen throw rather than wrap.
 */
class NumericBoundaryTest {

    private static final String HASH28 = "581c" + "00".repeat(28);
    private static final String INPUT = "82" + "5820" + "00".repeat(32) + "00";

    @ParameterizedTest
    @ValueSource(strings = {"1a80000000", "1b0000000100000000", "1b8000000000000000", "1bffffffffffffffff"})
    void poolRetirementEpochRoundTrips(String epoch) throws Exception {
        byte[] certificate = hex("8304" + HASH28 + epoch);

        Certificate decoded = Certificate.deserialize((Array) CborSerializationUtil.deserialize(certificate));

        assertThat(decoded).isInstanceOf(PoolRetirement.class);
        assertThat(CborSerializationUtil.serialize(decoded.serialize(), false)).isEqualTo(certificate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1a80000000", "1b0000000100000000", "1b8000000000000000", "1bffffffffffffffff"})
    void ttlAndValidityStartRoundTrip(String slot) throws Exception {
        byte[] body = hex("a50081" + INPUT + "01800200" + "03" + slot + "08" + slot);

        Map encoded = TransactionBody.deserialize((Map) CborSerializationUtil.deserialize(body)).serialize();

        assertThat(CborSerializationUtil.serialize(encoded.get(new UnsignedInteger(3)), false)).isEqualTo(hex(slot));
        assertThat(CborSerializationUtil.serialize(encoded.get(new UnsignedInteger(8)), false)).isEqualTo(hex(slot));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1a80000000", "1b0000000100000000", "1b8000000000000000", "1bffffffffffffffff"})
    void plutusConstructorAlternativeRoundTrips(String alternative) throws Exception {
        byte[] constr = hex("d86682" + alternative + "80");

        PlutusData decoded = PlutusData.deserialize(constr);

        assertThat(CborSerializationUtil.serialize(decoded.serialize(), false)).isEqualTo(constr);
    }

    @Test
    void redeemerMapFormKeepsAWideIndex() throws Exception {
        BigInteger index = BigInteger.ONE.shiftLeft(32);
        Array key = (Array) CborSerializationUtil.deserialize(hex("82001b0000000100000000"));
        Array value = (Array) CborSerializationUtil.deserialize(hex("82008200" + "00"));

        assertThat(Redeemer.deserialize(key, value).getIndex()).isEqualTo(index);
    }

    @Test
    void certificateDiscriminantDoesNotWrap() {
        // 2^32 + 4 wrapped to 4, a pool retirement
        byte[] certificate = hex("831b0000000100000004" + HASH28 + "00");

        assertThatThrownBy(() -> Certificate.deserialize((Array) CborSerializationUtil.deserialize(certificate)))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void govActionDiscriminantDoesNotWrap() {
        // 2^32 + 6 wrapped to 6, an info action
        byte[] action = hex("811b0000000100000006");

        assertThatThrownBy(() -> GovAction.deserialize((Array) CborSerializationUtil.deserialize(action)))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void updateEpochDoesNotWrap() {
        byte[] update = hex("82a0" + "1b0000000100000005");

        assertThatThrownBy(() -> Update.deserialize(CborSerializationUtil.deserialize(update)))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void committeeTermDoesNotWrap() {
        // [4, null, [], {credential: 2^32 + 5}, 1/2]: the term would wrap to 5
        byte[] action = hex("8504f680a1" + "8200" + HASH28 + "1b0000000100000005" + "d81e820102");

        assertThatThrownBy(() -> GovAction.deserialize((Array) CborSerializationUtil.deserialize(action)))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void nonceVariantDoesNotWrap() {
        // 2^32 + 1 wrapped to 1, a hash nonce
        byte[] nonce = hex("821b0000000100000001" + "5820" + "00".repeat(32));

        assertThatThrownBy(() -> Nonce.deserialize(CborSerializationUtil.deserialize(nonce)))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void valueBignumCoinIsUnsigned() {
        // tag 2 over 0x80 00..00 (9 bytes) is 2^71, not a negative number
        Value value = Value.deserialize(CborSerializationUtil.deserialize(hex("c24980" + "00".repeat(8))));

        assertThat(value.getCoin()).isEqualTo(BigInteger.ONE.shiftLeft(71));
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
