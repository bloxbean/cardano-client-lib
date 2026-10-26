package com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal;

import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Resolves the roots a GC run must keep.
 *
 * <p>Every strategy keeps the roots chosen by the {@link RetentionPolicy} plus, unconditionally, the root under
 * the {@code LATEST} key and the root recorded at {@link RocksDbRootsIndex#lastVersion()}. A policy can therefore
 * never make the current trie unreadable, even when it is used directly through
 * {@code GcStrategy.run(...)}.</p>
 *
 * <p>Without a roots index (a caller that records its roots elsewhere), the policy's roots are the only roots kept,
 * so the policy must include the current root. It must return at least one root: an empty result is rejected rather
 * than letting a GC run delete every node. For an empty current trie, pass the all-zero root
 * ({@code MpfTrie.getRootHashOrEmpty()}); a {@code null} root is dropped.</p>
 *
 * <p>Internal API: it may change without notice.</p>
 */
public final class GcRoots {

    private GcRoots() {
    }

    /**
     * Returns the distinct, non-empty roots to keep.
     *
     * @param index  the roots index of the namespace being collected, or {@code null} when the caller records its
     *               roots elsewhere and the policy supplies all of them
     * @param policy the retention policy
     * @return the roots to keep, in policy order followed by the latest roots
     * @throws IllegalArgumentException if there is no index and the policy returns no root
     */
    public static List<byte[]> retainedRoots(RocksDbRootsIndex index, RetentionPolicy policy) {
        List<byte[]> out = new ArrayList<>();
        Collection<byte[]> fromPolicy = policy.resolveRoots(index);
        if (fromPolicy != null) for (byte[] r : fromPolicy) addUnique(out, r);
        if (index == null) {
            if (out.isEmpty()) {
                throw new IllegalArgumentException("Without a roots index the retention policy must return a root");
            }
            return out;
        }
        addUnique(out, index.latest());
        long last = index.lastVersion();
        if (last >= 0) addUnique(out, index.get(last));
        return out;
    }

    /**
     * Returns {@code true} when {@code root} is the all-zero root (Aiken's empty trie), which has no node.
     *
     * @param root a root hash
     * @return whether every byte is zero
     */
    public static boolean isZeroRoot(byte[] root) {
        if (root == null || root.length == 0) return false;
        for (byte b : root) if (b != 0) return false;
        return true;
    }

    /**
     * Returns {@code true} when {@code roots} contains a root equal to {@code root}.
     *
     * @param roots candidate roots
     * @param root  the root to look up
     * @return whether it is present
     */
    public static boolean contains(Collection<byte[]> roots, byte[] root) {
        for (byte[] r : roots) if (Arrays.equals(r, root)) return true;
        return false;
    }

    /**
     * Returns the common digest length of {@code roots}.
     *
     * @param roots         the retained roots
     * @param defaultLength the length to use when there are no roots
     * @return the digest length
     * @throws IllegalStateException if the roots have different lengths
     */
    public static int digestLength(Collection<byte[]> roots, int defaultLength) {
        int len = -1;
        for (byte[] r : roots) {
            if (len < 0) len = r.length;
            else if (len != r.length) {
                throw new IllegalStateException("Retained roots have different lengths (" + len + " and " + r.length
                        + " bytes); a namespace must use a single hash function");
            }
        }
        return len < 0 ? defaultLength : len;
    }

    private static void addUnique(List<byte[]> out, byte[] r) {
        if (r == null || r.length == 0) return;
        if (!contains(out, r)) out.add(r);
    }
}
