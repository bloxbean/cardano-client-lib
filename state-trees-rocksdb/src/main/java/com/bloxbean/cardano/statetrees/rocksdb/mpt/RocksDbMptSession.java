package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/**
 * Convenience helper to execute MPT mutations in a single RocksDB WriteBatch
 * with a consistent WriteOptions configuration (e.g., WAL on/off).
 *
 * <p>This mirrors the ergonomics of the JMT store by keeping backend-specific
 * toggles (like WAL) in the RocksDB adapter layer, without changing the
 * platform-agnostic MPT API.</p>
 */
public final class RocksDbMptSession implements AutoCloseable {

    /** Simple options; extend as needed with more RocksDB write knobs. */
    public static final class Options {
        private final boolean disableWal;

        private Options(Builder b) { this.disableWal = b.disableWal; }

        public static Builder builder() { return new Builder(); }

        public static Options defaults() { return builder().build(); }

        public boolean disableWal() { return disableWal; }

        public static final class Builder {
            private boolean disableWal;
            public Builder disableWal(boolean disableWal) { this.disableWal = disableWal; return this; }
            public Options build() { return new Options(this); }
        }
    }

    private final RocksDbNodeStore nodeStore;
    private final Options options;

    public static RocksDbMptSession of(RocksDbNodeStore nodeStore) {
        return new RocksDbMptSession(nodeStore, Options.defaults());
    }

    public static RocksDbMptSession of(RocksDbNodeStore nodeStore, Options options) {
        return new RocksDbMptSession(nodeStore, options == null ? Options.defaults() : options);
    }

    public static RocksDbMptSession of(RocksDbStateTrees stateTrees) {
        return of(stateTrees.nodeStore());
    }

    public static RocksDbMptSession of(RocksDbStateTrees stateTrees, Options options) {
        return of(stateTrees.nodeStore(), options);
    }

    private RocksDbMptSession(RocksDbNodeStore nodeStore, Options options) {
        this.nodeStore = nodeStore;
        this.options = options;
    }

    /**
     * Executes the given work inside a WriteBatch, then writes it once via db.write.
     *
     * <p>The work can perform any number of MPT operations that rely on the
     * NodeStore (e.g., trie.put/delete) and will see read-your-writes consistency
     * through the nodeStore's batch staging.</p>
     */
    public <T> T write(java.util.concurrent.Callable<T> work) {
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            if (options.disableWal()) wo.setDisableWAL(true);
            T result = nodeStore.withBatch(wb, work);
            nodeStore.db().write(wo, wb);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute MPT batch", e);
        }
    }

    public void write(Runnable r) { write(() -> { r.run(); return null; }); }

    @Override
    public void close() { /* nothing to close */ }
}

