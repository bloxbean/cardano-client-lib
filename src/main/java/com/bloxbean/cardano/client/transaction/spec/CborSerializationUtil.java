package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Number;

import java.math.BigInteger;

class CborSerializationUtil {

    public static BigInteger getBigInteger(DataItem valueItem) {
        BigInteger value = null;
        if(MajorType.UNSIGNED_INTEGER.equals(valueItem.getMajorType())
                || MajorType.NEGATIVE_INTEGER.equals(valueItem.getMajorType())) {
            value = ((Number) valueItem).getValue();
        } else if(MajorType.BYTE_STRING.equals(valueItem.getMajorType())) { //For BigNum. >  2 pow 64 Tag 2
            if(valueItem.getTag().getValue() == 2) { //positive
                value = new BigInteger(((ByteString) valueItem).getBytes());
            } else if(valueItem.getTag().getValue() == 3) { //Negative
                value = new BigInteger(((ByteString)valueItem).getBytes()).multiply(BigInteger.valueOf(-1));
            }
        }

        return value;
    }
}
