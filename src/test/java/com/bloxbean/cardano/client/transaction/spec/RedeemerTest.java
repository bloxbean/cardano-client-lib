package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedeemerTest {

    @Test
    void serializeDeserialize() throws CborSerializationException, CborDeserializationException {
        PlutusData plutusData = new BigIntPlutusData(new BigInteger("2021"));
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(plutusData)
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1700))
                        .steps(BigInteger.valueOf(476468)).build()
                ).build();

        Array array = redeemer.serialize();

        Redeemer deRedeemer = Redeemer.deserialize(array);

        assertThat(deRedeemer).isEqualTo(deRedeemer);
    }

}
