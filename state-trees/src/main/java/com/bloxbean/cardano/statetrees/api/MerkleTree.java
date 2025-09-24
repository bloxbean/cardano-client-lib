package com.bloxbean.cardano.statetrees.api;

import java.util.List;

public interface MerkleTree {
    byte[] build(List<byte[]> leaves);

    List<byte[]> prove(int leafIndex);

    boolean verify(byte[] leaf, List<byte[]> path, byte[] root);
}

