package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
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
        Array seArray = redeemer.serializePreConway();

        Redeemer deRedeemer = Redeemer.deserializePreConway(seArray);
        Array deArray = deRedeemer.serializePreConway();

        //ser, deser test
        assertThat(deArray).isEqualTo(seArray);

        //check each individual value (probably unnecessary)
        assertThat(deRedeemer.getTag()).isEqualTo(RedeemerTag.Spend);
        assertThat(deRedeemer.getIndex()).isEqualTo(BigInteger.ZERO);
        assertThat(deRedeemer.getExUnits().getMem()).isEqualTo(BigInteger.valueOf(1700));
        assertThat(deRedeemer.getExUnits().getSteps()).isEqualTo(BigInteger.valueOf(476468));
        assertThat(((BigIntPlutusData) deRedeemer.getData()).getValue()).isEqualTo(new BigInteger("2021"));
    }
}
