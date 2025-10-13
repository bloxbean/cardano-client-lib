package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.api.ClassicProof;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Verifier for Classic (node-encoding) MPT proofs.
 */
public final class ClassicProofVerifier {
    private ClassicProofVerifier() {
    }

    public static boolean verifyInclusion(byte[] expectedRoot,
                                          HashFunction hashFn,
                                          byte[] key,
                                          byte[] value,
                                          ClassicProof proof,
                                          CommitmentScheme commitments) {
        Objects.requireNonNull(value, "value");
        return verify(expectedRoot, hashFn, key, value, true, proof, commitments);
    }

    public static boolean verifyNonInclusion(byte[] expectedRoot,
                                             HashFunction hashFn,
                                             byte[] key,
                                             ClassicProof proof,
                                             CommitmentScheme commitments) {
        return verify(expectedRoot, hashFn, key, null, false, proof, commitments);
    }

    public static boolean verify(byte[] expectedRoot,
                                 HashFunction hashFn,
                                 byte[] key,
                                 byte[] valueOrNull,
                                 boolean including,
                                 ClassicProof proof,
                                 CommitmentScheme commitments) {
        Objects.requireNonNull(hashFn, "hashFn");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(commitments, "commitments");

        if (expectedRoot == null || expectedRoot.length == 0) {
            return !including; // empty trie cannot include any key
        }

        List<byte[]> enc = proof.nodes();
        if (enc.isEmpty()) return false;

        // Decode nodes
        List<Node> nodes = new ArrayList<>(enc.size());
        for (byte[] n : enc) nodes.add(TrieEncoding.decode(n));

        // Root chain must match expectedRoot
        Flatten frRoot = flatten(nodes, 0, hashFn, commitments);
        if (!Arrays.equals(expectedRoot, frRoot.commit)) return false;
        if (frRoot.consumed > nodes.size()) return false;

        // Traverse
        int[] nibbles = Nibbles.toNibbles(key);
        int pos = 0;
        int idx = 0;
        while (idx < nodes.size()) {
            Node node = nodes.get(idx);
            if (node instanceof BranchNode) {
                BranchNode br = (BranchNode) node;
                byte[][] children = br.getChildren();
                byte[] branchValue = br.getValue();
                if (pos >= nibbles.length) {
                    if (including) {
                        if (branchValue == null) return false;
                        return Arrays.equals(branchValue, valueOrNull);
                    } else {
                        return branchValue == null;
                    }
                }
                int childIndex = nibbles[pos];
                byte[] childHash = (childIndex >= 0 && childIndex < 16) ? children[childIndex] : null;
                if (childHash == null || childHash.length == 0) {
                    return !including;
                }
                if (idx + 1 >= nodes.size()) return false;
                Flatten fr = flatten(nodes, idx + 1, hashFn, commitments);
                if (!Arrays.equals(childHash, fr.commit)) return false;
                idx = idx + fr.consumed;
                pos++;
                continue;
            }
            if (node instanceof ExtensionNode) {
                ExtensionNode en = (ExtensionNode) node;
                Nibbles.HP hp = Nibbles.unpackHP(en.getHp());
                int[] ext = hp.nibbles;
                if (pos + ext.length > nibbles.length) return !including;
                for (int i = 0; i < ext.length; i++) if (ext[i] != nibbles[pos + i]) return !including;
                pos += ext.length;
                idx += 1;
                continue;
            }
            if (node instanceof LeafNode) {
                LeafNode lf = (LeafNode) node;
                Nibbles.HP hp = Nibbles.unpackHP(lf.getHp());
                int[] suf = hp.nibbles;
                if (pos + suf.length == nibbles.length) {
                    boolean eq = true;
                    for (int i = 0; i < suf.length; i++)
                        if (suf[i] != nibbles[pos + i]) {
                            eq = false;
                            break;
                        }
                    if (eq) {
                        if (!including) return false;
                        return Arrays.equals(lf.getValue(), valueOrNull);
                    }
                }
                return !including;
            }
            return false;
        }
        return false;
    }

    private static final class Flatten {
        final byte[] commit;
        final int consumed;

        Flatten(byte[] c, int k) {
            commit = c;
            consumed = k;
        }
    }

    private static Flatten flatten(List<Node> nodes, int start, HashFunction hashFn, CommitmentScheme commitments) {
        if (start >= nodes.size()) return new Flatten(commitments.nullHash(), 0);
        Node node = nodes.get(start);
        if (node instanceof LeafNode) {
            LeafNode lf = (LeafNode) node;
            Nibbles.HP hp = Nibbles.unpackHP(lf.getHp());
            NibblePath suf = NibblePath.of(hp.nibbles);
            byte[] vh = hashFn.digest(lf.getValue());
            return new Flatten(commitments.commitLeaf(suf, vh), 1);
        }
        if (node instanceof BranchNode) {
            BranchNode br = (BranchNode) node;
            byte[][] children = new byte[16][];
            byte[][] from = br.getChildren();
            for (int i = 0; i < 16; i++)
                children[i] = (i < from.length && from[i] != null && from[i].length > 0) ? from[i] : null;
            byte[] v = br.getValue();
            byte[] vh = v == null ? null : hashFn.digest(v);
            return new Flatten(commitments.commitBranch(NibblePath.EMPTY, children, vh), 1);
        }
        if (node instanceof ExtensionNode) {
            int idx = start;
            NibblePath acc = NibblePath.EMPTY;
            while (idx < nodes.size() && nodes.get(idx) instanceof ExtensionNode) {
                ExtensionNode en = (ExtensionNode) nodes.get(idx);
                Nibbles.HP hp = Nibbles.unpackHP(en.getHp());
                acc = acc.concat(NibblePath.of(hp.nibbles));
                idx++;
            }
            if (idx >= nodes.size())
                return new Flatten(commitments.commitExtension(acc, commitments.nullHash()), idx - start);
            Node child = nodes.get(idx);
            if (child instanceof BranchNode) {
                BranchNode br = (BranchNode) child;
                byte[][] children = new byte[16][];
                byte[][] from = br.getChildren();
                for (int i = 0; i < 16; i++)
                    children[i] = (i < from.length && from[i] != null && from[i].length > 0) ? from[i] : null;
                byte[] v = br.getValue();
                byte[] vh = v == null ? null : hashFn.digest(v);
                byte[] c = commitments.commitBranch(acc, children, vh);
                return new Flatten(c, (idx - start) + 1);
            } else if (child instanceof LeafNode) {
                LeafNode lf = (LeafNode) child;
                Nibbles.HP hp = Nibbles.unpackHP(lf.getHp());
                NibblePath suf = acc.concat(NibblePath.of(hp.nibbles));
                byte[] c = commitments.commitLeaf(suf, hashFn.digest(lf.getValue()));
                return new Flatten(c, (idx - start) + 1);
            } else {
                byte[] c = commitments.commitExtension(acc, commitments.nullHash());
                return new Flatten(c, (idx - start));
            }
        }
        throw new IllegalStateException("Unsupported node type " + node.getClass().getSimpleName());
    }
}

