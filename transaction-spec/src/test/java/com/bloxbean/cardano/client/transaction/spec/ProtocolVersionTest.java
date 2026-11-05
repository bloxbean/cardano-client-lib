package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.HardForkInitiationAction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolVersionTest {

    /**
     * An indefinite protocol version holds no BREAK in a tree from {@link CborSerializationUtil#deserialize(byte[])},
     * but ends with one in a tree from cbor-java's own decoder, as callers may pass.
     */
    @Test
    void indefiniteVersionFromBothDecoders() throws Exception {
        byte[] update = HexUtil.decodeHexString("a10e9f0b00ff");
        ProtocolVersion expected = new ProtocolVersion(11, 0);

        Map cborJavaTree = (Map) new CborDecoder(new ByteArrayInputStream(update)).decodeNext();
        Map cclTree = (Map) CborSerializationUtil.deserialize(update);

        assertThat(ProtocolParamUpdate.deserialize(cborJavaTree).getProtocolVersion()).isEqualTo(expected);
        assertThat(ProtocolParamUpdate.deserialize(cclTree).getProtocolVersion()).isEqualTo(expected);
    }

    /** A hard fork action with an indefinite version: 8301f69f0b00ff, which cbor-java's tree used to fail on. */
    @Test
    void indefiniteVersionOfAHardForkActionFromCborJava() throws Exception {
        byte[] action = HexUtil.decodeHexString("8301f69f0b00ff");
        Array cborJavaTree = (Array) new CborDecoder(new ByteArrayInputStream(action)).decodeNext();

        HardForkInitiationAction hardFork = (HardForkInitiationAction) GovAction.deserialize(cborJavaTree);

        assertThat(hardFork.getProtocolVersion()).isEqualTo(new ProtocolVersion(11, 0));
    }

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
