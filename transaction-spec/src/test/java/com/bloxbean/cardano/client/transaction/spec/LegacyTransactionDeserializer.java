package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.Special;

import java.util.List;

/**
 * {@link Transaction#deserialize(byte[])} as it was, on cbor-java's recursive decoder: the oracle for model parity and
 * the baseline for the benchmark.
 */
public final class LegacyTransactionDeserializer {
    private LegacyTransactionDeserializer() {
    }

    public static Transaction deserialize(byte[] bytes) throws Exception {
        List<DataItem> dataItemList = CborDecoder.decode(bytes);
        Transaction transaction = new Transaction();
        Array array = (Array) dataItemList.get(0);
        List<DataItem> txnItems = array.getDataItems();
        DataItem txnBodyDI = txnItems.get(0);
        DataItem witnessDI = txnItems.get(1);
        if (witnessDI != null)
            transaction.setWitnessSet(TransactionWitnessSet.deserialize((Map) witnessDI));
        DataItem isValidDI = txnItems.get(2);
        boolean checkAuxData = true;
        if (isValidDI instanceof Special) {
            if (isValidDI == SimpleValue.TRUE) {
                transaction.setValid(true);
            } else if (isValidDI == SimpleValue.FALSE) {
                transaction.setValid(false);
            } else if (isValidDI == SimpleValue.NULL) {
                checkAuxData = false;
                transaction.setValid(true);
            } else {
                transaction.setValid(true);
            }
        } else {
            transaction.setValid(true);
        }
        if (checkAuxData) {
            DataItem auxiliaryDataDI = txnItems.get(3);
            if (auxiliaryDataDI != null && MajorType.MAP.equals(auxiliaryDataDI.getMajorType()))
                transaction.setAuxiliaryData(AuxiliaryData.deserialize((Map) auxiliaryDataDI));
        }
        transaction.setBody(TransactionBody.deserialize((Map) txnBodyDI));
        return transaction;
    }
}
