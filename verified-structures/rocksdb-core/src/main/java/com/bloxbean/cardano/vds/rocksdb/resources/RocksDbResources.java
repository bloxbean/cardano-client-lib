package com.bloxbean.cardano.vds.rocksdb.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modern resource management for RocksDB components using RAII principles.
 *
 * <p>This class provides safe, automatic resource cleanup for RocksDB resources
 * such as databases, column families, options, and iterators. It addresses the
 * common problem of resource leaks by ensuring all registered resources are
 * properly closed, even in the presence of exceptions.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>LIFO (Last-In-First-Out) cleanup order to respect resource dependencies</li>
 *   <li>Exception aggregation to report all cleanup failures</li>
 *   <li>Thread-safe registration and cleanup operations</li>
 *   <li>Detailed error reporting with resource names</li>
 *   <li>Protection against double-close operations</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try (RocksDbResources resources = new RocksDbResources()) {
 *     DBOptions dbOptions = resources.register("dbOptions", new DBOptions());
 *     ColumnFamilyOptions cfOptions = resources.register("cfOptions", new ColumnFamilyOptions());
 *     RocksDB db = resources.register("database", RocksDB.open(dbOptions, path, cfDescriptors, cfHandles));
 *
 *     // Use resources...
 *
 * } // All resources automatically cleaned up in reverse order
 * }</pre>
 *
 * <p><b>Error Handling:</b> If multiple resources fail during cleanup, all exceptions
 * are aggregated into a single RuntimeException with suppressed exceptions containing
 * the individual failure details.</p>
 *
 * @since 0.8.0
 */
public final class RocksDbResources implements AutoCloseable {

    /**
     * Thread-safe queue for storing registered resources.
     * Using ConcurrentLinkedQueue allows safe registration from multiple threads.
     */
    private final ConcurrentLinkedQueue<ResourceEntry> resources = new ConcurrentLinkedQueue<>();

    /**
     * Flag to track whether this resource manager has been closed.
     * Prevents further registrations after cleanup begins.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Registers a resource for automatic cleanup.
     *
     * <p>Resources are cleaned up in LIFO order (reverse of registration order)
     * to respect typical dependency relationships where later-created resources
     * depend on earlier-created ones.</p>
     *
     * @param <T>      the type of the resource being registered
     * @param name     a descriptive name for the resource (used in error messages)
     * @param resource the resource to register for cleanup (must not be null)
     * @return the same resource instance for fluent usage
     * @throws IllegalStateException    if this resource manager has already been closed
     * @throws IllegalArgumentException if name or resource is null
     */
    public <T extends AutoCloseable> T register(String name, T resource) {
        Objects.requireNonNull(name, "Resource name cannot be null");
        Objects.requireNonNull(resource, "Resource cannot be null");

        if (closed.get()) {
            throw new IllegalStateException("Cannot register resources on a closed resource manager");
        }

        resources.offer(new ResourceEntry(name.trim(), resource));
        return resource;
    }

    /**
     * Registers a resource with automatic name generation.
     *
     * <p>The resource name is generated from the resource's class name.
     * This is convenient for cases where the resource type is sufficiently
     * descriptive for debugging purposes.</p>
     *
     * @param <T>      the type of the resource being registered
     * @param resource the resource to register for cleanup (must not be null)
     * @return the same resource instance for fluent usage
     * @throws IllegalStateException    if this resource manager has already been closed
     * @throws IllegalArgumentException if resource is null
     */
    public <T extends AutoCloseable> T register(T resource) {
        Objects.requireNonNull(resource, "Resource cannot be null");
        String name = resource.getClass().getSimpleName();
        return register(name, resource);
    }

    /**
     * Returns the number of currently registered resources.
     *
     * <p>This method is primarily useful for testing and debugging purposes.
     * The count may change if other threads are concurrently registering resources.</p>
     *
     * @return the current number of registered resources
     */
    public int getResourceCount() {
        return resources.size();
    }

    /**
     * Closes all registered resources in reverse order of registration.
     *
     * <p>This method is idempotent - calling it multiple times has no additional
     * effect after the first call. Resources are closed in LIFO order to respect
     * typical dependency relationships.</p>
     *
     * <p>If multiple resources fail during cleanup, all exceptions are aggregated
     * into a single RuntimeException with the individual failures available as
     * suppressed exceptions.</p>
     *
     * @throws RuntimeException if one or more resources fail to close properly
     */
    @Override
    public void close() {
        // Ensure close() is idempotent
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }

        // Collect all resources for LIFO cleanup
        List<ResourceEntry> resourcesToClose = new ArrayList<>();
        ResourceEntry entry;
        while ((entry = resources.poll()) != null) {
            resourcesToClose.add(entry);
        }

        // Close in reverse order (LIFO)
        List<Exception> exceptions = new ArrayList<>();
        for (int i = resourcesToClose.size() - 1; i >= 0; i--) {
            ResourceEntry resource = resourcesToClose.get(i);
            try {
                resource.close();
            } catch (Exception e) {
                exceptions.add(new ResourceCleanupException(
                        "Failed to close resource: " + resource.getName(), e));
            }
        }

        // If there were cleanup failures, aggregate them
        if (!exceptions.isEmpty()) {
            RuntimeException aggregatedException = new RuntimeException(
                    String.format("Failed to close %d out of %d resources",
                            exceptions.size(), resourcesToClose.size()));

            for (Exception e : exceptions) {
                aggregatedException.addSuppressed(e);
            }

            throw aggregatedException;
        }
    }

    /**
     * Internal representation of a registered resource with its name.
     */
    private static final class ResourceEntry {
        private final String name;
        private final AutoCloseable resource;

        ResourceEntry(String name, AutoCloseable resource) {
            this.name = name;
            this.resource = resource;
        }

        String getName() {
            return name;
        }

        void close() throws Exception {
            resource.close();
        }

        @Override
        public String toString() {
            return String.format("ResourceEntry[name=%s, type=%s]",
                    name, resource.getClass().getSimpleName());
        }
    }

    /**
     * Specialized exception for resource cleanup failures.
     *
     * <p>This exception provides better error context when individual
     * resources fail to close, including the resource name and type
     * information for debugging purposes.</p>
     */
    public static final class ResourceCleanupException extends Exception {

        /**
         * Creates a new ResourceCleanupException with the specified message and cause.
         *
         * @param message the exception message
         * @param cause   the underlying exception that caused the cleanup failure
         */
        public ResourceCleanupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
