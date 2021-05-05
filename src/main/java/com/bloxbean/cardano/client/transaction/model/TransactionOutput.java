package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;

import java.math.BigInteger;

public class TransactionOutput {
    private byte[] address;
    private BigInteger value;

    public byte[] getAddress() {
        return address;
    }

    public void setAddress(byte[] address) {
        this.address = address;
    }

    public BigInteger getValue() {
        return value;
    }

    public void setValue(BigInteger value) {
        this.value = value;
    }

    public void serialize(ArrayBuilder builder) throws CborException {
        builder.add(address)
                .add(value.longValue()) //TODO BigInteger to long value
                .end();
    }
}
