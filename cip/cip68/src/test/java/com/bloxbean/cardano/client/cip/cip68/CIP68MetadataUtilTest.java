package com.bloxbean.cardano.client.cip.cip68;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CIP68MetadataUtilTest {

    @Test
    void getReferenzTokenNameTest() {
        String userTokenName = "76fa5eb96f727aa1548de7a0e7c46de8bdc3116029e083039ed624dd000de14054657374313233546f6b656e";
        String referenzTokenName = CIP68MetadataUtil.getReferenceTokenName(userTokenName);
        assertEquals("76fa5eb96f727aa1548de7a0e7c46de8bdc3116029e083039ed624dd000643b054657374313233546f6b656e", referenzTokenName);
    }

//    @Test
//    void getReferenzTokenUtxoTest() throws ApiException {
//        BackendService backendService = new
//        String referenceTokenName = "76fa5eb96f727aa1548de7a0e7c46de8bdc3116029e083039ed624dd000643b054657374313233546f6b656e";
//        TxContentUtxoOutputs containingInlineDatum = CIP68MetadataUtil.getReferenceTokenUtxo(referenceTokenName, backendService);
//        assertEquals("745d382a378c5493b5ad1c9debbe6c1296dacc4f9a8158b9691d20d8bb9952c2", containingInlineDatum.getDataHash());
//
//    }
}
