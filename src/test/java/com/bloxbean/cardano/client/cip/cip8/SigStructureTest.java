package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SigStructureTest extends COSEBaseTest {

    @Test
    void serDesSigStructureSignature() {
        SigStructure sigStructure = new SigStructure()
                .sigContext(SigContext.Signature)
                .bodyProtected(new ProtectedHeaderMap())
                .signProtected(new ProtectedHeaderMap())
                .externalAad(new byte[]{8, 9, 100})
                .payload(getBytes(73, 23));

        String expectedSerHex = "85695369676e6174757265404043080964574949494949494949494949494949494949494949494949";
        SigStructure deSigStruct = deserializationTest(sigStructure, expectedSerHex);

        assertThat(deSigStruct.sigContext()).isEqualTo(SigContext.Signature);
    }

    @Test
    void serDesSigStructureCounter() {

    }

    private SigStructure deserializationTest(SigStructure hm, String expectedHex) {
        byte[] serializeByte1 = hm.serializeAsBytes();
        byte[] serializeByte2;
        SigStructure sigStructure;
        try {
            sigStructure = SigStructure.deserialize(CborDecoder.decode(serializeByte1).get(0));
            serializeByte2 = sigStructure.serializeAsBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        System.out.println(HexUtil.encodeHexString(serializeByte1));
        assertThat(serializeByte2).isEqualTo(serializeByte1);

        if (expectedHex != null) {
            assertThat(HexUtil.encodeHexString(serializeByte1)).isEqualTo(expectedHex);
        }

        return sigStructure;
    }

}
