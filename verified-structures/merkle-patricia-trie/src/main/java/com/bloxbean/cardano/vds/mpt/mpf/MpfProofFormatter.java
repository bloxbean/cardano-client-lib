package com.bloxbean.cardano.vds.mpt.mpf;

import com.bloxbean.cardano.vds.core.util.Bytes;

/**
 * Utilities to export MPF proofs (wire CBOR) to formats consumed by the
 * JavaScript off‑chain library and by Aiken text helpers.
 *
 * <p>These helpers are convenience bridges for interoperability testing and
 * tooling. They do not change the proof semantics — they only change
 * presentation.</p>
 */
public final class MpfProofFormatter {

    private MpfProofFormatter() { }

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
        MpfProof proof = MpfProofDecoder.decode(proofCbor);
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (MpfProof.Step step : proof.steps()) {
            if (!first) sb.append(',');
            first = false;
            if (step instanceof MpfProof.BranchStep) {
                MpfProof.BranchStep br = (MpfProof.BranchStep) step;
                String neighborsHex = concatHex(br.neighbors());
                sb.append('{')
                        .append("\"type\":\"branch\",")
                        .append("\"skip\":").append(br.skip()).append(',')
                        .append("\"neighbors\":\"").append(neighborsHex).append("\"")
                        .append('}');
            } else if (step instanceof MpfProof.ForkStep) {
                MpfProof.ForkStep fk = (MpfProof.ForkStep) step;
                sb.append('{')
                        .append("\"type\":\"fork\",")
                        .append("\"skip\":").append(fk.skip()).append(',')
                        .append("\"neighbor\":{")
                        .append("\"nibble\":").append(fk.nibble()).append(',')
                        .append("\"prefix\":\"").append(Bytes.toHex(fk.prefix())).append("\",")
                        .append("\"root\":\"").append(Bytes.toHex(fk.root())).append("\"")
                        .append('}')
                        .append('}');
            } else if (step instanceof MpfProof.LeafStep) {
                MpfProof.LeafStep lf = (MpfProof.LeafStep) step;
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
        MpfProof proof = MpfProofDecoder.decode(proofCbor);
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (MpfProof.Step step : proof.steps()) {
            if (step instanceof MpfProof.BranchStep) {
                MpfProof.BranchStep br = (MpfProof.BranchStep) step;
                String neighborsHex = concatHex(br.neighbors());
                sb.append("  Branch { skip: ")
                        .append(br.skip())
                        .append(", neighbors: #\"")
                        .append(neighborsHex)
                        .append("\" },\n");
            } else if (step instanceof MpfProof.ForkStep) {
                MpfProof.ForkStep fk = (MpfProof.ForkStep) step;
                sb.append("  Fork { skip: ")
                        .append(fk.skip())
                        .append(", neighbor: Neighbor { nibble: ")
                        .append(fk.nibble())
                        .append(", prefix: #\"")
                        .append(Bytes.toHex(fk.prefix()))
                        .append("\", root: #\"")
                        .append(Bytes.toHex(fk.root()))
                        .append("\" } },\n");
            } else if (step instanceof MpfProof.LeafStep) {
                MpfProof.LeafStep lf = (MpfProof.LeafStep) step;
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

    private static String concatHex(byte[][] parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder(parts.length * 64);
        for (byte[] p : parts) sb.append(Bytes.toHex(p));
        return sb.toString();
    }
}

