package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.HardForkInitiationAction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolVersionTest {

    /**
     * The minor version is a uint (cardano-ledger: a Natural until protocol version 12, then a Word32); one from 2^31
     * was read as a negative int. A hard fork action of cardano-cbor-dataset's Conway corpus.
     */
    @Test
    void minorVersionFromTwoToThe31() throws Exception {
        String hex = "8301f682011aa5bb41a4";
        HardForkInitiationAction action = (HardForkInitiationAction) GovAction.deserialize(
                (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex)));
        assertThat(action.getProtocolVersion().getMinor()).isEqualTo(2780512676L);
        assertThat(HexUtil.encodeHexString(CborSerializationUtil.serialize(action.serialize()))).isEqualTo(hex);
    }
}
