package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import com.bloxbean.cardano.client.plutus.blueprint.model.RawData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Arrays;
import java.util.Objects;

abstract class ByteArrayWrapper implements RawData {

    private final byte[] value;

    protected ByteArrayWrapper(byte[] value) {
        this.value = Objects.requireNonNull(value, "value cannot be null").clone();
    }

    protected byte[] bytesInternal() {
        return value;
    }

    public byte[] bytes() {
        return value.clone();
    }

    public PlutusData toPlutusData() {
        return BytesPlutusData.of(value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ByteArrayWrapper other = (ByteArrayWrapper) obj;
        return Arrays.equals(this.value, other.value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[size=" + value.length + "]";
    }
}
