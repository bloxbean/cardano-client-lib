package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoolRetirementTest {

    @Test
    void serDeserTest() throws Exception {
        String cborHex = "8304581cfffda60991eafa48674f7137a76e11ddc2464e94c73a4319629072b4190168";

        PoolRetirement poolRetirement = PoolRetirement.deserialize((Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(cborHex)));
        Array serRetirment = poolRetirement.serialize();

        PoolRetirement dePoolRetirement = PoolRetirement.deserialize(serRetirment);
        String deCborHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(dePoolRetirement.serialize()));

        assertThat(deCborHex).isEqualTo(cborHex);
        assertThat(HexUtil.encodeHexString(dePoolRetirement.getPoolKeyHash())).isEqualTo("fffda60991eafa48674f7137a76e11ddc2464e94c73a4319629072b4");
        assertThat(dePoolRetirement.getEpoch()).isEqualTo(360);
    }
}
