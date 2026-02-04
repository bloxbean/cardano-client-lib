package com.bloxbean.cardano.client.plutus.aiken.blueprint.std;

import java.util.Arrays;
import java.util.Objects;

abstract class ByteArrayWrapper {

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
