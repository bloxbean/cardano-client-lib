package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityChecker;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityMode;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessCoordinator;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessLease;
import com.bloxbean.cardano.vds.jmt.store.JmtConcurrentMutationException;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbJmtExternalHandleConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void wrappersForOneExternalHandleFailFastThroughRequiredSharedCoordinator() throws Exception {
        Path dbPath = tempDir.resolve("external");
        RocksDbJmtStore.Options storeOptions = RocksDbJmtStore.Options.production();
        try (RocksDbJmtStore initializer = RocksDbJmtStore.open(dbPath.toString(), storeOptions)) {
            new JellyfishMerkleTree(initializer).put(0, Map.of(bytes("initial"), bytes("value")));
        }

        try (ExternalDb external = ExternalDb.open(dbPath)) {
            JmtAccessCoordinator coordinator = new JmtAccessCoordinator();
            try (RocksDbJmtStore first = RocksDbJmtStore.attach(
                    external.db, storeOptions, external.handles, coordinator);
                 RocksDbJmtStore second = RocksDbJmtStore.attach(
                         external.db, storeOptions, external.handles, coordinator)) {
                JellyfishMerkleTree secondTree = new JellyfishMerkleTree(second);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                try (JmtAccessLease ignored = coordinator.tryAcquireUpdate("first-wrapper", 1)) {
                    Future<JmtConcurrentMutationException> failure = executor.submit(() ->
                            assertThrows(JmtConcurrentMutationException.class,
                                    () -> secondTree.put(1, Map.of(bytes("second"), bytes("value")))));
                    assertTrue(failure.get(5, TimeUnit.SECONDS).getMessage()
                            .contains("first-wrapper"));
                } finally {
                    executor.shutdownNow();
                }

                secondTree.put(1, Map.of(bytes("second"), bytes("value")));
                assertTrue(new JmtIntegrityChecker(first, JmtProfile.classicBlake2b256V1())
                        .check(JmtIntegrityMode.FULL).healthy());
            }
        }
    }

    @Test
    void rejectsDifferentCoordinatorsForTheSameExternalNamespace() throws Exception {
        Path dbPath = tempDir.resolve("coordinator-mismatch");
        RocksDbJmtStore.Options storeOptions = RocksDbJmtStore.Options.production();
        try (RocksDbJmtStore initializer = RocksDbJmtStore.open(dbPath.toString(), storeOptions)) {
            new JellyfishMerkleTree(initializer).put(0, Map.of(bytes("initial"), bytes("value")));
        }

        try (ExternalDb external = ExternalDb.open(dbPath);
             RocksDbJmtStore first = RocksDbJmtStore.attach(
                     external.db, storeOptions, external.handles, new JmtAccessCoordinator())) {
            assertThrows(IllegalArgumentException.class, () -> RocksDbJmtStore.attach(
                    external.db, storeOptions, external.handles, new JmtAccessCoordinator()));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class ExternalDb implements AutoCloseable {
        private final RocksDB db;
        private final DBOptions dbOptions;
        private final List<ColumnFamilyHandle> columnFamilyHandles;
        private final List<ColumnFamilyOptions> columnFamilyOptions;
        private final Map<String, ColumnFamilyHandle> handles;

        private ExternalDb(RocksDB db,
                           DBOptions dbOptions,
                           List<ColumnFamilyHandle> columnFamilyHandles,
                           List<ColumnFamilyOptions> columnFamilyOptions,
                           Map<String, ColumnFamilyHandle> handles) {
            this.db = db;
            this.dbOptions = dbOptions;
            this.columnFamilyHandles = columnFamilyHandles;
            this.columnFamilyOptions = columnFamilyOptions;
            this.handles = handles;
        }

        private static ExternalDb open(Path path) throws RocksDBException {
            RocksDB.loadLibrary();
            List<byte[]> names;
            try (Options options = new Options()) {
                names = RocksDB.listColumnFamilies(options, path.toString());
            }

            RocksDbJmtStore.ColumnFamilies jmtNames =
                    RocksDbJmtStore.columnFamilies(NamespaceOptions.defaults());
            List<ColumnFamilyOptions> cfOptions = new ArrayList<>();
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            for (byte[] encodedName : names) {
                String name = new String(encodedName, StandardCharsets.UTF_8);
                int prefixLength = name.equals(jmtNames.values()) ? 33
                        : name.equals(jmtNames.nodesByVersion())
                        || name.equals(jmtNames.valuesByVersion()) ? 9 : 1;
                ColumnFamilyOptions options = new ColumnFamilyOptions()
                        .useFixedLengthPrefixExtractor(prefixLength);
                cfOptions.add(options);
                descriptors.add(new ColumnFamilyDescriptor(encodedName, options));
            }

            DBOptions dbOptions = new DBOptions();
            List<ColumnFamilyHandle> openedHandles = new ArrayList<>();
            try {
                RocksDB db = RocksDB.open(dbOptions, path.toString(), descriptors, openedHandles);
                Map<String, ColumnFamilyHandle> handles = new HashMap<>();
                for (int i = 0; i < names.size(); i++) {
                    handles.put(new String(names.get(i), StandardCharsets.UTF_8),
                            openedHandles.get(i));
                }
                return new ExternalDb(db, dbOptions, openedHandles, cfOptions, handles);
            } catch (RocksDBException | RuntimeException e) {
                openedHandles.forEach(ColumnFamilyHandle::close);
                cfOptions.forEach(ColumnFamilyOptions::close);
                dbOptions.close();
                throw e;
            }
        }

        @Override
        public void close() {
            columnFamilyHandles.forEach(ColumnFamilyHandle::close);
            db.close();
            columnFamilyOptions.forEach(ColumnFamilyOptions::close);
            dbOptions.close();
        }
    }
}
