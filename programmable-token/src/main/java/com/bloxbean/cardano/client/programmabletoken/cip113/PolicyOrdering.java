package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.util.HexUtil;

import java.util.Comparator;

/**
 * Unsigned bytewise ordering over policy ids, matching the on-chain
 * {@code builtin.less_than_bytearray} used by the registry linked list.
 */
public final class PolicyOrdering {
    public static final Comparator<String> COMPARATOR = PolicyOrdering::compare;

    private PolicyOrdering() {}

    /** Unsigned bytewise comparison of two hex-encoded byte strings. */
    public static int compare(String hexA, String hexB) {
        return compare(HexUtil.decodeHexString(hexA), HexUtil.decodeHexString(hexB));
    }

    public static int compare(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = Integer.compare(a[i] & 0xff, b[i] & 0xff);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * True when {@code node} is the covering node for {@code policyId}, i.e.
     * {@code node.key < policyId < node.next}.
     *
     * <p>The registry is a circular-ish sorted list whose last node points back at a
     * sentinel that is lexicographically smallest, so the wraparound case (next &lt;= key)
     * covers everything greater than key.</p>
     */
    public static boolean covers(String nodeKey, String nodeNext, String policyId) {
        boolean afterKey = compare(nodeKey, policyId) < 0;
        if (!afterKey) return false;
        if (compare(nodeNext, nodeKey) <= 0) return true;   // wraparound / tail node
        return compare(policyId, nodeNext) < 0;
    }
}
