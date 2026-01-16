package com.bloxbean.cardano.vds.mpt.proof;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.vds.core.util.Bytes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities to export MPF proofs (wire CBOR) to formats consumed by the
 * JavaScript off‑chain library and by Aiken text helpers.
 *
 * <p>These helpers are convenience bridges for interoperability testing and
 * tooling. They do not change the proof semantics — they only change
 * presentation.</p>
 */
public final class ProofFormatter {

    private ProofFormatter() { }

    /**
     * Convert an MPF CBOR proof (as produced by getProofWire) into the JSON
     * step shape expected by the JS off‑chain library's Proof.fromJSON(key, value, steps).
     *
     * <p>The returned string is a JSON array of step objects where each object is one of:</p>
     * <ul>
     *   <li>{"type":"branch","skip":N,"neighbors":"&lt;hex&gt;"}</li>
     *   <li>{"type":"fork","skip":N,"neighbor":{"nibble":X,"prefix":"&lt;hex&gt;","root":"&lt;hex&gt;"}}</li>
     *   <li>{"type":"leaf","skip":N,"neighbor":{"key":"&lt;hex&gt;","value":"&lt;hex&gt;"}}</li>
     * </ul>
     */
    public static String toJson(byte[] proofCbor) {
        WireProof proof = ProofDecoder.decode(proofCbor);
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (WireProof.Step step : proof.steps()) {
            if (!first) sb.append(',');
            first = false;
            if (step instanceof WireProof.BranchStep) {
                WireProof.BranchStep br = (WireProof.BranchStep) step;
                String neighborsHex = concatHex(br.neighbors());
                sb.append('{')
                        .append("\"type\":\"branch\",")
                        .append("\"skip\":").append(br.skip()).append(',')
                        .append("\"neighbors\":\"").append(neighborsHex).append("\"")
                        .append('}');
            } else if (step instanceof WireProof.ForkStep) {
                WireProof.ForkStep fk = (WireProof.ForkStep) step;
                sb.append('{')
                        .append("\"type\":\"fork\",")
                        .append("\"skip\":").append(fk.skip()).append(',')
                        .append("\"neighbor\":{")
                        .append("\"nibble\":").append(fk.nibble()).append(',')
                        .append("\"prefix\":\"").append(Bytes.toHex(fk.prefix())).append("\",")
                        .append("\"root\":\"").append(Bytes.toHex(fk.root())).append("\"")
                        .append('}')
                        .append('}');
            } else if (step instanceof WireProof.LeafStep) {
                WireProof.LeafStep lf = (WireProof.LeafStep) step;
                sb.append('{')
                        .append("\"type\":\"leaf\",")
                        .append("\"skip\":").append(lf.skip()).append(',')
                        .append("\"neighbor\":{")
                        .append("\"key\":\"").append(Bytes.toHex(lf.keyHash())).append("\",")
                        .append("\"value\":\"").append(Bytes.toHex(lf.valueHash())).append("\"")
                        .append('}')
                        .append('}');
            } else {
                throw new IllegalArgumentException("Unknown proof step type: " + step.getClass());
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Convert an MPF CBOR proof to an Aiken‑style textual representation, mirroring
     * the JS off‑chain library's Proof.toAiken(). Intended for debugging and fixtures.
     */
    public static String toAiken(byte[] proofCbor) {
        WireProof proof = ProofDecoder.decode(proofCbor);
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (WireProof.Step step : proof.steps()) {
            if (step instanceof WireProof.BranchStep) {
                WireProof.BranchStep br = (WireProof.BranchStep) step;
                String neighborsHex = concatHex(br.neighbors());
                sb.append("  Branch { skip: ")
                        .append(br.skip())
                        .append(", neighbors: #\"")
                        .append(neighborsHex)
                        .append("\" },\n");
            } else if (step instanceof WireProof.ForkStep) {
                WireProof.ForkStep fk = (WireProof.ForkStep) step;
                sb.append("  Fork { skip: ")
                        .append(fk.skip())
                        .append(", neighbor: Neighbor { nibble: ")
                        .append(fk.nibble())
                        .append(", prefix: #\"")
                        .append(Bytes.toHex(fk.prefix()))
                        .append("\", root: #\"")
                        .append(Bytes.toHex(fk.root()))
                        .append("\" } },\n");
            } else if (step instanceof WireProof.LeafStep) {
                WireProof.LeafStep lf = (WireProof.LeafStep) step;
                sb.append("  Leaf { skip: ")
                        .append(lf.skip())
                        .append(", key: #\"")
                        .append(Bytes.toHex(lf.keyHash()))
                        .append("\", value: #\"")
                        .append(Bytes.toHex(lf.valueHash()))
                        .append("\" },\n");
            } else {
                throw new IllegalArgumentException("Unknown proof step type: " + step.getClass());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert an MPF CBOR proof to PlutusData format that can be directly passed
     * to Aiken MPF validators for on-chain verification.
     *
     * <p>The returned ListPlutusData represents a list of ProofStep structures matching
     * the Aiken type definitions:</p>
     * <pre>
     * pub type ProofStep {
     *   Branch { skip: Int, neighbors: ByteArray }
     *   Fork { skip: Int, neighbor: Neighbor }
     *   Leaf { skip: Int, key: ByteArray, value: ByteArray }
     * }
     *
     * pub type Neighbor {
     *   nibble: Int,
     *   prefix: ByteArray,
     *   root: ByteArray,
     * }
     * </pre>
     *
     * @param proofCbor The CBOR-encoded proof bytes
     * @return ListPlutusData containing the proof steps as ConstrPlutusData
     */
    public static ListPlutusData toPlutusData(byte[] proofCbor) {
        WireProof proof = ProofDecoder.decode(proofCbor);
        List<PlutusData> steps = new ArrayList<>();

        for (WireProof.Step step : proof.steps()) {
            if (step instanceof WireProof.BranchStep) {
                WireProof.BranchStep br = (WireProof.BranchStep) step;
                byte[] neighborsConcat = concatBytes(br.neighbors());

                // Branch { skip: Int, neighbors: ByteArray }
                // Alternative 0
                ConstrPlutusData branchStep = ConstrPlutusData.builder()
                        .alternative(0)
                        .data(ListPlutusData.of(
                                BigIntPlutusData.of(BigInteger.valueOf(br.skip())),
                                BytesPlutusData.of(neighborsConcat)
                        ))
                        .build();
                steps.add(branchStep);

            } else if (step instanceof WireProof.ForkStep) {
                WireProof.ForkStep fk = (WireProof.ForkStep) step;

                // Neighbor { nibble: Int, prefix: ByteArray, root: ByteArray }
                // Alternative 0
                ConstrPlutusData neighbor = ConstrPlutusData.builder()
                        .alternative(0)
                        .data(ListPlutusData.of(
                                BigIntPlutusData.of(BigInteger.valueOf(fk.nibble())),
                                BytesPlutusData.of(fk.prefix()),
                                BytesPlutusData.of(fk.root())
                        ))
                        .build();

                // Fork { skip: Int, neighbor: Neighbor }
                // Alternative 1
                ConstrPlutusData forkStep = ConstrPlutusData.builder()
                        .alternative(1)
                        .data(ListPlutusData.of(
                                BigIntPlutusData.of(BigInteger.valueOf(fk.skip())),
                                neighbor
                        ))
                        .build();
                steps.add(forkStep);

            } else if (step instanceof WireProof.LeafStep) {
                WireProof.LeafStep lf = (WireProof.LeafStep) step;

                // Leaf { skip: Int, key: ByteArray, value: ByteArray }
                // Alternative 2
                ConstrPlutusData leafStep = ConstrPlutusData.builder()
                        .alternative(2)
                        .data(ListPlutusData.of(
                                BigIntPlutusData.of(BigInteger.valueOf(lf.skip())),
                                BytesPlutusData.of(lf.keyHash()),
                                BytesPlutusData.of(lf.valueHash())
                        ))
                        .build();
                steps.add(leafStep);

            } else {
                throw new IllegalArgumentException("Unknown proof step type: " + step.getClass());
            }
        }

        return ListPlutusData.of(steps.toArray(new PlutusData[0]));
    }

    private static String concatHex(byte[][] parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder(parts.length * 64);
        for (byte[] p : parts) sb.append(Bytes.toHex(p));
        return sb.toString();
    }

    private static byte[] concatBytes(byte[][] parts) {
        if (parts == null || parts.length == 0) return new byte[0];
        int totalLength = 0;
        for (byte[] p : parts) {
            totalLength += p.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, result, offset, p.length);
            offset += p.length;
        }
        return result;
    }
}

