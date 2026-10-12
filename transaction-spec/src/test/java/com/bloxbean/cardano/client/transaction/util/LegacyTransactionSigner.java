package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;

/**
 * {@code TransactionSigner.addWitnessToTransaction} as it was before the splice: it decodes the whole witness set with
 * cbor-java and re-encodes it. Kept as a test oracle and benchmark baseline.
 */
final class LegacyTransactionSigner {
    private LegacyTransactionSigner() {
    }

    static byte[] addWitnessToTransaction(TransactionBytes transactionBytes, byte[] vkey, byte[] signature) {
        try {
            DataItem witnessSetDI = CborSerializationUtil.deserialize(transactionBytes.getTxWitnessBytes());
            Map witnessSetMap = (Map) witnessSetDI;

            DataItem vkWitnessArrayDI = witnessSetMap.get(new UnsignedInteger(0));
            Array vkWitnessArray;
            if (vkWitnessArrayDI != null) {
                vkWitnessArray = (Array) vkWitnessArrayDI;
            } else {
                vkWitnessArray = new Array();
                witnessSetMap.put(new UnsignedInteger(0), vkWitnessArray);
            }

            Array vkeyWitness = new Array();
            vkeyWitness.add(new ByteString(vkey));
            vkeyWitness.add(new ByteString(signature));
            vkWitnessArray.add(vkeyWitness);

            byte[] txWitnessBytes = CborSerializationUtil.serialize(witnessSetMap, false);
            return transactionBytes.withNewWitnessSetBytes(txWitnessBytes).getTxBytes();
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
    }
}
