package com.bloxbean.cardano.client.test;

import java.util.Arrays;
import java.util.Objects;

/**
 * Wrapper around byte[] that provides proper equals/hashCode semantics
 * for use as Map keys in property-based tests.
 * <p>
 * Defensively copies the input array to prevent external mutation.
 */
public final class ByteArrayWrapper {
    private final byte[] data;
    private final int hash;

    public ByteArrayWrapper(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        this.data = data.clone();
        this.hash = Arrays.hashCode(this.data);
    }

    public byte[] getData() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteArrayWrapper)) return false;
        return Arrays.equals(data, ((ByteArrayWrapper) o).data);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ByteArrayWrapper[" + Arrays.toString(data) + "]";
    }
}
