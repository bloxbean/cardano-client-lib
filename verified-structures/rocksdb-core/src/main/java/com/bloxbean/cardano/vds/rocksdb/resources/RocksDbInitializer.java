package com.bloxbean.cardano.vds.rocksdb.resources;

import org.rocksdb.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Safe initialization utility for RocksDB databases with proper resource management.
 *
 * <p>This utility class provides a safe way to initialize RocksDB databases with
 * complex column family configurations while ensuring proper resource cleanup
 * in case of initialization failures. It handles the common initialization
 * patterns used throughout the RocksDB storage system.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic column family discovery and creation</li>
 *   <li>Safe resource management during initialization</li>
 *   <li>Comprehensive error handling with cleanup</li>
 *   <li>Support for both new and existing databases</li>
 *   <li>Flexible column family configuration</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * RocksDbInitializer.Builder builder = RocksDbInitializer.builder("/path/to/db")
 *     .withRequiredColumnFamily("nodes")
 *     .withRequiredColumnFamily("roots")
 *     .withDbOptionsModifier(opts -> opts.setMaxBackgroundJobs(4));
 *
 * try (RocksDbInitializer.Result result = builder.initialize()) {
 *     RocksDB db = result.getDatabase();
 *     ColumnFamilyHandle nodesHandle = result.getColumnFamily("nodes");
 *     ColumnFamilyHandle rootsHandle = result.getColumnFamily("roots");
 *
 *     // Use the database...
 * } // All resources automatically cleaned up
 * }</pre>
 *
 * @since 0.8.0
 */
public final class RocksDbInitializer {

    /**
     * Private constructor - use {@link #builder(String)} to create instances.
     */
    private RocksDbInitializer() {
        // Utility class
    }

    /**
     * Creates a new builder for RocksDB initialization.
     *
     * @param databasePath the file system path for the database
     * @return a new Builder instance
     * @throws IllegalArgumentException if databasePath is null or empty
     */
    public static Builder builder(String databasePath) {
        return new Builder(databasePath);
    }

    /**
     * Builder for configuring RocksDB initialization parameters.
     */
    public static final class Builder {
        private final String databasePath;
        private final List<String> requiredColumnFamilies = new ArrayList<>();
        private DBOptionsModifier dbOptionsModifier = opts -> opts;
        private ColumnFamilyOptionsModifier cfOptionsModifier = opts -> opts;

        /**
         * Creates a new builder with the specified database path.
         *
         * @param databasePath the database path
         */
        private Builder(String databasePath) {
            this.databasePath = Objects.requireNonNull(databasePath, "Database path cannot be null");
            if (databasePath.trim().isEmpty()) {
                throw new IllegalArgumentException("Database path cannot be empty");
            }
        }

        /**
         * Adds a required column family that must exist in the database.
         *
         * <p>If the column family doesn't exist, it will be created during initialization.</p>
         *
         * @param columnFamilyName the name of the required column family
         * @return this builder for method chaining
         * @throws IllegalArgumentException if columnFamilyName is null or empty
         */
        public Builder withRequiredColumnFamily(String columnFamilyName) {
            Objects.requireNonNull(columnFamilyName, "Column family name cannot be null");
            if (columnFamilyName.trim().isEmpty()) {
                throw new IllegalArgumentException("Column family name cannot be empty");
            }
            requiredColumnFamilies.add(columnFamilyName.trim());
            return this;
        }

        /**
         * Sets a modifier function for customizing database options.
         *
         * @param modifier the function to modify DBOptions
         * @return this builder for method chaining
         * @throws IllegalArgumentException if modifier is null
         */
        public Builder withDbOptionsModifier(DBOptionsModifier modifier) {
            this.dbOptionsModifier = Objects.requireNonNull(modifier, "DB options modifier cannot be null");
            return this;
        }

        /**
         * Sets a modifier function for customizing column family options.
         *
         * @param modifier the function to modify ColumnFamilyOptions
         * @return this builder for method chaining
         * @throws IllegalArgumentException if modifier is null
         */
        public Builder withColumnFamilyOptionsModifier(ColumnFamilyOptionsModifier modifier) {
            this.cfOptionsModifier = Objects.requireNonNull(modifier, "Column family options modifier cannot be null");
            return this;
        }

        /**
         * Initializes the RocksDB database with the configured parameters.
         *
         * <p>This method performs the complete initialization sequence including:
         * directory creation, column family discovery, option configuration,
         * and database opening. If any step fails, all allocated resources
         * are automatically cleaned up.</p>
         *
         * @return a Result object containing the initialized database and handles
         * @throws RocksDbInitializationException if initialization fails
         */
        public Result initialize() throws RocksDbInitializationException {
            RocksDbResources resources = new RocksDbResources();

            try {
                // Load RocksDB library
                RocksDB.loadLibrary();

                // Ensure database directory exists
                File dbDirectory = new File(databasePath);
                if (!dbDirectory.exists() && !dbDirectory.mkdirs()) {
                    throw new RocksDbInitializationException(
                            "Failed to create database directory: " + databasePath);
                }

                // Create and configure options
                ColumnFamilyOptions cfOptions = resources.register("columnFamilyOptions",
                        cfOptionsModifier.modify(new ColumnFamilyOptions()));
                DBOptions dbOptions = resources.register("dbOptions",
                        dbOptionsModifier.modify(new DBOptions()
                                .setCreateIfMissing(true)
                                .setCreateMissingColumnFamilies(true)));

                // Discover existing column families
                List<byte[]> existingCfNames = RocksDB.listColumnFamilies(
                        new Options().setCreateIfMissing(true), databasePath);

                // Build column family descriptors
                List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
                List<String> cfNames = new ArrayList<>();

                // Always include default column family
                boolean hasDefault = false;
                for (byte[] cfName : existingCfNames) {
                    String name = new String(cfName);
                    cfDescriptors.add(new ColumnFamilyDescriptor(cfName, cfOptions));
                    cfNames.add(name);
                    if (Arrays.equals(cfName, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                        hasDefault = true;
                    }
                }

                if (!hasDefault) {
                    cfDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));
                    cfNames.add(0, "default");
                }

                // Add required column families that don't exist
                for (String requiredCf : requiredColumnFamilies) {
                    boolean exists = cfNames.stream()
                            .anyMatch(name -> name.equals(requiredCf));
                    if (!exists) {
                        cfDescriptors.add(new ColumnFamilyDescriptor(requiredCf.getBytes(), cfOptions));
                        cfNames.add(requiredCf);
                    }
                }

                // Open the database
                List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
                RocksDB db = resources.register("database",
                        RocksDB.open(dbOptions, databasePath, cfDescriptors, cfHandles));

                // Register all column family handles
                for (int i = 0; i < cfHandles.size(); i++) {
                    ColumnFamilyHandle handle = cfHandles.get(i);
                    String cfName = i < cfNames.size() ? cfNames.get(i) : "unknown_" + i;
                    resources.register("cf_" + cfName, handle);
                }

                return new Result(resources, db, cfNames, cfHandles);

            } catch (RocksDBException e) {
                resources.close();
                throw new RocksDbInitializationException("Failed to initialize RocksDB", e);
            } catch (Exception e) {
                resources.close();
                throw new RocksDbInitializationException("Unexpected error during RocksDB initialization", e);
            }
        }
    }

    /**
     * Result of successful RocksDB initialization.
     *
     * <p>This class provides access to the initialized database and column family
     * handles while managing the underlying resource lifecycle. When closed,
     * all RocksDB resources are properly cleaned up.</p>
     */
    public static final class Result implements AutoCloseable {
        private final RocksDbResources resources;
        private final RocksDB database;
        private final List<String> columnFamilyNames;
        private final List<ColumnFamilyHandle> columnFamilyHandles;

        /**
         * Creates a new Result with the initialized components.
         *
         * @param resources           the resource manager
         * @param database            the RocksDB instance
         * @param columnFamilyNames   the names of column families
         * @param columnFamilyHandles the column family handles
         */
        private Result(RocksDbResources resources, RocksDB database,
                       List<String> columnFamilyNames, List<ColumnFamilyHandle> columnFamilyHandles) {
            this.resources = resources;
            this.database = database;
            this.columnFamilyNames = List.copyOf(columnFamilyNames);
            this.columnFamilyHandles = List.copyOf(columnFamilyHandles);
        }

        /**
         * Returns the initialized RocksDB database.
         *
         * @return the RocksDB instance
         */
        public RocksDB getDatabase() {
            return database;
        }

        /**
         * Returns the column family handle for the specified name.
         *
         * @param columnFamilyName the name of the column family
         * @return the column family handle
         * @throws IllegalArgumentException if the column family doesn't exist
         */
        public ColumnFamilyHandle getColumnFamily(String columnFamilyName) {
            int index = columnFamilyNames.indexOf(columnFamilyName);
            if (index < 0) {
                throw new IllegalArgumentException("Column family not found: " + columnFamilyName);
            }
            return columnFamilyHandles.get(index);
        }

        /**
         * Returns all column family names.
         *
         * @return an immutable list of column family names
         */
        public List<String> getColumnFamilyNames() {
            return columnFamilyNames;
        }

        /**
         * Closes all RocksDB resources managed by this result.
         */
        @Override
        public void close() {
            resources.close();
        }
    }

    /**
     * Functional interface for modifying DBOptions during initialization.
     */
    @FunctionalInterface
    public interface DBOptionsModifier {
        /**
         * Modifies the provided DBOptions instance.
         *
         * @param options the options to modify
         * @return the modified options (may be the same instance)
         */
        DBOptions modify(DBOptions options);
    }

    /**
     * Functional interface for modifying ColumnFamilyOptions during initialization.
     */
    @FunctionalInterface
    public interface ColumnFamilyOptionsModifier {
        /**
         * Modifies the provided ColumnFamilyOptions instance.
         *
         * @param options the options to modify
         * @return the modified options (may be the same instance)
         */
        ColumnFamilyOptions modify(ColumnFamilyOptions options);
    }

    /**
     * Exception thrown when RocksDB initialization fails.
     */
    public static final class RocksDbInitializationException extends Exception {

        /**
         * Creates a new RocksDbInitializationException with the specified message.
         *
         * @param message the exception message
         */
        public RocksDbInitializationException(String message) {
            super(message);
        }

        /**
         * Creates a new RocksDbInitializationException with the specified message and cause.
         *
         * @param message the exception message
         * @param cause   the underlying cause
         */
        public RocksDbInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
