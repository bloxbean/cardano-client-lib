package com.bloxbean.cardano.vds.mpf.proof;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

final class ProofDecoder {

    private ProofDecoder() {
    }

    static WireProof decode(byte[] cborBytes) {
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(cborBytes)).decode();
            if (items.isEmpty() || !(items.get(0) instanceof Array)) {
                throw new IllegalArgumentException("Invalid MPF proof CBOR encoding");
            }
            Array root = (Array) items.get(0);
            List<WireProof.Step> steps = new ArrayList<>();
            for (DataItem item : root.getDataItems()) {
                if (!(item instanceof Array)) {
                    throw new IllegalArgumentException("Invalid MPF proof step encoding");
                }
                steps.add(decodeStep((Array) item));
            }
            return new WireProof(steps);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode MPF proof", e);
        }
    }

    private static WireProof.Step decodeStep(Array array) {
        Tag tag = array.getTag();
        if (tag == null || tag.getValue() == 121) {
            int skip = ((UnsignedInteger) array.getDataItems().get(0)).getValue().intValue();
            byte[] neighborBytes = readByteString(array.getDataItems().get(1));
            byte[][] neighbors = splitNeighbors(neighborBytes);
            byte[] branchValueHash = null;
            if (array.getDataItems().size() > 2) {
                branchValueHash = readByteString(array.getDataItems().get(2));
            }
            return new WireProof.BranchStep(skip, neighbors, 0, branchValueHash);
        } else if (tag.getValue() == 122) {
            int skip = ((UnsignedInteger) array.getDataItems().get(0)).getValue().intValue();
            Array neighbor = (Array) array.getDataItems().get(1);
            int nibble = ((UnsignedInteger) neighbor.getDataItems().get(0)).getValue().intValue();
            byte[] prefix = readByteString(neighbor.getDataItems().get(1));
            byte[] root = readByteString(neighbor.getDataItems().get(2));
            return new WireProof.ForkStep(skip, nibble, prefix, root);
        } else if (tag.getValue() == 123) {
            int skip = ((UnsignedInteger) array.getDataItems().get(0)).getValue().intValue();
            byte[] keyHash = readByteString(array.getDataItems().get(1));
            byte[] valueHash = readByteString(array.getDataItems().get(2));
            return new WireProof.LeafStep(skip, keyHash, valueHash);
        }
        throw new IllegalArgumentException("Unknown MPF proof step tag: " + tag);
    }

    /**
     * Reads a ByteString data item, handling both definite and chunked encodings.
     */
    private static byte[] readByteString(DataItem di) {
        if (!(di instanceof ByteString)) {
            throw new IllegalArgumentException("Expected ByteString but got: " + di.getClass());
        }
        ByteString bs = (ByteString) di;
        // The CBOR library returns concatenated bytes for chunked byte strings via getBytes().
        return bs.getBytes();
    }

    private static byte[][] splitNeighbors(byte[] bytes) {
        int digestLength = bytes.length / 4;
        byte[][] neighbors = new byte[4][];
        for (int i = 0; i < 4; i++) {
            neighbors[i] = new byte[digestLength];
            System.arraycopy(bytes, i * digestLength, neighbors[i], 0, digestLength);
        }
        return neighbors;
    }
}
