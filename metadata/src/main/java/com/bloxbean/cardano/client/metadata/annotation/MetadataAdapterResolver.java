package com.bloxbean.cardano.client.metadata.annotation;

/**
 * Strategy for resolving adapter instances used by generated metadata converters.
 * <p>
 * When a generated converter has fields annotated with {@link MetadataField#adapter()},
 * {@link MetadataEncoder @MetadataEncoder}, or {@link MetadataDecoder @MetadataDecoder},
 * it uses a resolver to obtain adapter instances. This enables dependency injection
 * and stateful adapters that require constructor arguments.
 *
 * <p><b>Default behavior:</b>
 * The no-arg converter constructor uses {@link DefaultAdapterResolver}, which instantiates
 * adapters via their public no-arg constructor (backward-compatible behavior).
 *
 * <p><b>Spring Boot integration:</b>
 * <pre>{@code
 * @Bean
 * MetadataAdapterResolver adapterResolver(ApplicationContext ctx) {
 *     return new MetadataAdapterResolver() {
 *         public <T> T resolve(Class<T> adapterClass) {
 *             return ctx.getBean(adapterClass);
 *         }
 *     };
 * }
 *
 * // Usage:
 * var converter = new OrderMetadataConverter(adapterResolver);
 * }</pre>
 *
 * @see DefaultAdapterResolver
 * @see MetadataEncoder
 * @see MetadataDecoder
 */
public interface MetadataAdapterResolver {

    /**
     * Resolves an adapter instance for the given class.
     *
     * @param adapterClass the adapter class to resolve
     * @param <T> the adapter type
     * @return a fully initialized adapter instance
     */
    <T> T resolve(Class<T> adapterClass);
}
