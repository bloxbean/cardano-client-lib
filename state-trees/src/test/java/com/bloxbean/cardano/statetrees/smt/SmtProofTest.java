package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.SparseMerkleProof;
import com.bloxbean.cardano.statetrees.api.SparseMerkleTree;
import com.bloxbean.cardano.statetrees.api.SparseMerkleVerifier;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmtProofTest {

    private final HashFunction blake2b = Blake2b256::digest;

    @Test
    void inclusion_proof_verifies() {
        SparseMerkleTree smt = new SparseMerkleTree(new TestNodeStore(), blake2b);
        byte[] k = "alice".getBytes();
        byte[] v = "100".getBytes();
        smt.put(k, v);

        SparseMerkleProof proof = smt.getProof(k);
        assertThat(proof.getType()).isEqualTo(SparseMerkleProof.Type.INCLUSION);
        boolean ok = SparseMerkleVerifier.verifyInclusion(smt.getRootHash(), blake2b, k, v, proof.getSiblings());
        assertThat(ok).isTrue();

        // Wrong value should fail
        boolean bad = SparseMerkleVerifier.verifyInclusion(smt.getRootHash(), blake2b, k, "101".getBytes(), proof.getSiblings());
        assertThat(bad).isFalse();
    }

    @Test
    void non_inclusion_proof_verifies() {
        SparseMerkleTree smt = new SparseMerkleTree(new TestNodeStore(), blake2b);
        smt.put("hello".getBytes(), "world".getBytes());

        byte[] k = "absent".getBytes();
        SparseMerkleProof proof = smt.getProof(k);
        assertThat(proof.getType()).isEqualTo(SparseMerkleProof.Type.NON_INCLUSION_EMPTY);
        boolean ok = SparseMerkleVerifier.verifyNonInclusion(smt.getRootHash(), blake2b, k, proof.getSiblings());
        assertThat(ok).isTrue();
    }
}

