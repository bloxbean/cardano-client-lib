package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.StateTrees;
import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcOptions;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcReport;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;

import java.util.Collections;

/**
 * RDBMS implementation of {@link StateTrees} for Merkle Patricia Forestry.
 *
 * <p>Provides a unified interface composing {@link RdbmsNodeStore} and {@link RdbmsRootsIndex},
 * supporting both {@link StorageMode#SINGLE_VERSION} and {@link StorageMode#MULTI_VERSION} modes.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * DbConfig config = DbConfig.builder().simpleJdbcUrl("jdbc:h2:mem:test").build();
 * try (RdbmsStateTrees trees = new RdbmsStateTrees(config, StorageMode.SINGLE_VERSION)) {
 *     MpfTrie trie = new MpfTrie(trees.nodeStore());
 *     trie.put(key, value);
 *     trees.putRootSnapshot(trie.getRootHash());
 * }
 * }</pre>
 *
 * @since 0.8.0
 */
public class RdbmsStateTrees implements StateTrees {

    private final RdbmsNodeStore nodeStore;
    private final RdbmsRootsIndex rootsIndex;
    private final StorageMode storageMode;

    /**
     * Creates RDBMS state trees with default namespace and SINGLE_VERSION mode.
     *
     * @param config the database configuration
     */
    public RdbmsStateTrees(DbConfig config) {
        this(config, StorageMode.SINGLE_VERSION);
    }

    /**
     * Creates RDBMS state trees with the specified storage mode and default namespace.
     *
     * @param config      the database configuration
     * @param storageMode the storage mode
     */
    public RdbmsStateTrees(DbConfig config, StorageMode storageMode) {
        this(config, storageMode, (byte) 0x00);
    }

    /**
     * Creates RDBMS state trees with the specified storage mode and namespace.
     *
     * @param config      the database configuration
     * @param storageMode the storage mode
     * @param keyPrefix   the namespace ID (0-255)
     */
    public RdbmsStateTrees(DbConfig config, StorageMode storageMode, byte keyPrefix) {
        this.nodeStore = new RdbmsNodeStore(config, keyPrefix);
        this.rootsIndex = new RdbmsRootsIndex(config, keyPrefix);
        this.storageMode = storageMode;
    }

    @Override
    public RdbmsNodeStore nodeStore() {
        return nodeStore;
    }

    @Override
    public RdbmsRootsIndex rootsIndex() {
        return rootsIndex;
    }

    @Override
    public StorageMode storageMode() {
        return storageMode;
    }

    @Override
    public long putRootWithRefcount(byte[] root) {
        if (storageMode != StorageMode.MULTI_VERSION) {
            throw new IllegalStateException(
                "putRootWithRefcount() requires MULTI_VERSION mode. " +
                "Use putRootSnapshot() for SINGLE_VERSION mode.");
        }
        if (root == null || root.length == 0) {
            throw new IllegalArgumentException("root cannot be null/empty");
        }

        // For RDBMS MULTI_VERSION, we store the root with an auto-assigned version.
        // Note: refcount-based GC is not implemented for RDBMS; mark-sweep is used instead.
        return nodeStore.withTransaction(() -> {
            long version = rootsIndex.nextVersion();
            rootsIndex.put(version, root);
            return version;
        });
    }

    @Override
    public void putRootSnapshot(byte[] root) {
        if (storageMode != StorageMode.SINGLE_VERSION) {
            throw new IllegalStateException(
                "putRootSnapshot() requires SINGLE_VERSION mode. " +
                "Use putRootWithRefcount() for MULTI_VERSION mode.");
        }
        if (root == null || root.length == 0) {
            throw new IllegalArgumentException("root cannot be null/empty");
        }

        nodeStore.withTransaction(() -> {
            rootsIndex.put(0L, root);
            return null;
        });
    }

    @Override
    public byte[] getCurrentRoot() {
        if (storageMode != StorageMode.SINGLE_VERSION) {
            throw new IllegalStateException(
                "getCurrentRoot() requires SINGLE_VERSION mode. " +
                "Use rootsIndex().get(version) for MULTI_VERSION mode.");
        }
        return rootsIndex.get(0L);
    }

    /**
     * {@inheritDoc}
     *
     * <p>For the RDBMS backend, the options parameter should be a {@link RdbmsGcOptions} instance.
     * If null is passed, default options are used.</p>
     *
     * @param options {@link RdbmsGcOptions} instance, or null for defaults
     * @return a {@link RdbmsGcReport} wrapped as Object
     */
    @Override
    public Object cleanupOrphanedNodes(Object options) throws Exception {
        if (storageMode != StorageMode.SINGLE_VERSION) {
            throw new IllegalStateException(
                "cleanupOrphanedNodes() requires SINGLE_VERSION mode.");
        }

        RdbmsGcOptions gcOptions;
        if (options == null) {
            gcOptions = new RdbmsGcOptions();
        } else if (options instanceof RdbmsGcOptions) {
            gcOptions = (RdbmsGcOptions) options;
        } else {
            throw new IllegalArgumentException(
                "Options must be a RdbmsGcOptions instance for RDBMS backend");
        }

        byte[] currentRoot = getCurrentRoot();
        RdbmsMarkSweepGc gc = new RdbmsMarkSweepGc(nodeStore, rootsIndex);

        if (currentRoot == null) {
            // No root stored, delete everything
            return gc.run(Collections.emptyList(), gcOptions);
        }
        return gc.run(Collections.singletonList(currentRoot), gcOptions);
    }

    @Override
    public void close() {
        nodeStore.close();
        rootsIndex.close();
    }
}
