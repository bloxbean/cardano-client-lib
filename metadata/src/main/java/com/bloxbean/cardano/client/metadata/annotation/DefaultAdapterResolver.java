package com.bloxbean.cardano.client.metadata.annotation;

/**
 * Default {@link MetadataAdapterResolver} that instantiates adapters via their
 * public no-arg constructor using reflection.
 * <p>
 * This is the resolver used by the no-arg constructor of generated converters,
 * preserving backward compatibility with existing {@link MetadataTypeAdapter} classes.
 *
 * @see MetadataAdapterResolver
 */
public class DefaultAdapterResolver implements MetadataAdapterResolver {

    /** Shared singleton instance. */
    public static final DefaultAdapterResolver INSTANCE = new DefaultAdapterResolver();

    @Override
    public <T> T resolve(Class<T> adapterClass) {
        try {
            return adapterClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot instantiate adapter '" + adapterClass.getName()
                            + "'. Ensure it has a public no-arg constructor, or provide a custom "
                            + "MetadataAdapterResolver to the converter constructor.", e);
        }
    }
}
