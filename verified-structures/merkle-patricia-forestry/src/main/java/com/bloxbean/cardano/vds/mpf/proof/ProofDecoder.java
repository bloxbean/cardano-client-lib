package com.bloxbean.cardano.vds.mpf.proof;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Special;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the MPF proof wire format (the CBOR of Aiken's {@code Proof}, i.e. a list of
 * {@code Branch} (tag 121), {@code Fork} (tag 122) and {@code Leaf} (tag 123) constructors).
 *
 * <p>Definite- and indefinite-length arrays and byte strings are accepted (CCL writes definite lengths, the
 * off-chain JS library indefinite ones). Every field is checked against the digest length, so malformed input
 * fails here with an {@link IllegalArgumentException} instead of later in verification.</p>
 */
final class ProofDecoder {

    private static final int TAG_BRANCH = 121;
    private static final int TAG_FORK = 122;
    private static final int TAG_LEAF = 123;
    private static final int MAX_STEPS = 1024;

    private ProofDecoder() {
    }

    /** Decodes a proof whose digest length is inferred from its fields (they must agree). */
    static WireProof decode(byte[] cborBytes) {
        return decode(cborBytes, -1);
    }

    /**
     * Decodes a proof.
     *
     * @param digestLength the expected digest length in bytes, or {@code -1} to infer it from the proof
     * @throws IllegalArgumentException if the bytes are not a well-formed MPF proof
     */
    static WireProof decode(byte[] cborBytes, int digestLength) {
        if (cborBytes == null) {
            throw new IllegalArgumentException("Proof bytes are null");
        }
        List<DataItem> items;
        try {
            items = new CborDecoder(new ByteArrayInputStream(cborBytes)).decode();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode MPF proof", e);
        }
        if (items.size() != 1 || !(items.get(0) instanceof Array)) {
            throw new IllegalArgumentException("MPF proof must be a single CBOR array");
        }
        List<DataItem> stepItems = elements((Array) items.get(0));
        if (stepItems.size() > MAX_STEPS) {
            throw new IllegalArgumentException("MPF proof has too many steps");
        }
        int[] digest = {digestLength};
        List<WireProof.Step> steps = new ArrayList<>(stepItems.size());
        for (DataItem item : stepItems) {
            if (!(item instanceof Array)) {
                throw new IllegalArgumentException("Invalid MPF proof step encoding");
            }
            steps.add(decodeStep((Array) item, digest));
        }
        return new WireProof(steps);
    }

    private static WireProof.Step decodeStep(Array array, int[] digest) {
        Tag tag = array.getTag();
        if (tag == null) {
            throw new IllegalArgumentException("MPF proof step without constructor tag");
        }
        List<DataItem> fields = elements(array);
        long tagValue = tag.getValue();
        if (tagValue == TAG_BRANCH) {
            // Branch { skip, neighbors } (+ optional branch value hash for non-MPF commitment schemes)
            if (fields.size() != 2 && fields.size() != 3) {
                throw new IllegalArgumentException("Branch step must have 2 fields");
            }
            int skip = readSkip(fields.get(0));
            byte[] neighborBytes = readByteString(fields.get(1));
            int d = digestFrom(digest, neighborBytes.length, 4);
            byte[][] neighbors = new byte[4][d];
            for (int i = 0; i < 4; i++) System.arraycopy(neighborBytes, i * d, neighbors[i], 0, d);
            byte[] branchValueHash = null;
            if (fields.size() == 3) {
                branchValueHash = readDigest(fields.get(2), digest);
            }
            return new WireProof.BranchStep(skip, neighbors, 0, branchValueHash);
        }
        if (tagValue == TAG_FORK) {
            // Fork { skip, neighbor: Neighbor { nibble, prefix, root } }
            if (fields.size() != 2) {
                throw new IllegalArgumentException("Fork step must have 2 fields");
            }
            int skip = readSkip(fields.get(0));
            if (!(fields.get(1) instanceof Array)) {
                throw new IllegalArgumentException("Fork neighbor must be a constructor");
            }
            Array neighbor = (Array) fields.get(1);
            if (neighbor.getTag() == null || neighbor.getTag().getValue() != TAG_BRANCH) {
                throw new IllegalArgumentException("Fork neighbor must use constructor tag 121");
            }
            List<DataItem> nf = elements(neighbor);
            if (nf.size() != 3) {
                throw new IllegalArgumentException("Fork neighbor must have 3 fields");
            }
            int nibble = readNibble(nf.get(0));
            byte[] prefix = readByteString(nf.get(1));
            for (byte b : prefix) {
                if ((b & 0xFF) > 15) {
                    throw new IllegalArgumentException("Fork neighbor prefix must hold one nibble per byte");
                }
            }
            byte[] root = readDigest(nf.get(2), digest);
            return new WireProof.ForkStep(skip, nibble, prefix, root);
        }
        if (tagValue == TAG_LEAF) {
            // Leaf { skip, key, value }
            if (fields.size() != 3) {
                throw new IllegalArgumentException("Leaf step must have 3 fields");
            }
            int skip = readSkip(fields.get(0));
            byte[] keyHash = readDigest(fields.get(1), digest);
            byte[] valueHash = readDigest(fields.get(2), digest);
            return new WireProof.LeafStep(skip, keyHash, valueHash);
        }
        throw new IllegalArgumentException("Unknown MPF proof step tag: " + tagValue);
    }

    /** Array elements without the trailing BREAK marker of an indefinite-length array. */
    private static List<DataItem> elements(Array array) {
        List<DataItem> out = new ArrayList<>(array.getDataItems().size());
        for (DataItem di : array.getDataItems()) {
            if (Special.BREAK.equals(di)) continue;
            out.add(di);
        }
        return out;
    }

    private static int readSkip(DataItem di) {
        BigInteger v = readUnsigned(di, "skip");
        if (v.compareTo(BigInteger.valueOf(4096)) > 0) {
            throw new IllegalArgumentException("Proof step skip out of range");
        }
        return v.intValue();
    }

    private static int readNibble(DataItem di) {
        BigInteger v = readUnsigned(di, "nibble");
        if (v.compareTo(BigInteger.valueOf(15)) > 0) {
            throw new IllegalArgumentException("Fork neighbor nibble out of range");
        }
        return v.intValue();
    }

    private static BigInteger readUnsigned(DataItem di, String field) {
        if (!(di instanceof UnsignedInteger) || di.getTag() != null) {
            throw new IllegalArgumentException("Expected an unsigned integer for " + field);
        }
        return ((UnsignedInteger) di).getValue();
    }

    private static byte[] readDigest(DataItem di, int[] digest) {
        byte[] bytes = readByteString(di);
        digestFrom(digest, bytes.length, 1);
        return bytes;
    }

    /** Checks (or infers) the digest length given a field of {@code multiple} digests. */
    private static int digestFrom(int[] digest, int length, int multiple) {
        if (length == 0 || length % multiple != 0) {
            throw new IllegalArgumentException("Proof field has an invalid length " + length);
        }
        int d = length / multiple;
        if (digest[0] < 0) {
            digest[0] = d;
        } else if (digest[0] != d) {
            throw new IllegalArgumentException("Proof field length " + length + " does not match digest length " + digest[0]);
        }
        return d;
    }

    private static byte[] readByteString(DataItem di) {
        if (!(di instanceof ByteString) || di.getTag() != null) {
            throw new IllegalArgumentException("Expected a byte string but got: " + (di == null ? "null" : di.getMajorType()));
        }
        // Chunked (indefinite-length) byte strings are concatenated by the decoder.
        return ((ByteString) di).getBytes();
    }
}
