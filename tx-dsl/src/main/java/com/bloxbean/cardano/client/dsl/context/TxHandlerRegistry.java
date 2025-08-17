package com.bloxbean.cardano.client.dsl.context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Registry for runtime handler objects used in DSL context.
 * Allows registration of complex objects like TxSigner, UtxoSelectionStrategy, etc.
 * that can be referenced by key in DSL configuration.
 */
public class TxHandlerRegistry {
    private static final Map<String, Object> handlers = new ConcurrentHashMap<>();
    
    /**
     * Register a handler with the given key
     * @param key the key to register the handler with
     * @param handler the handler object
     */
    public static void register(String key, Object handler) {
        handlers.put(key, handler);
    }
    
    /**
     * Get a handler by key and type
     * @param key the key of the handler
     * @param type the expected type of the handler
     * @param <T> the type parameter
     * @return the handler object cast to the expected type
     * @throws IllegalArgumentException if handler not found or wrong type
     */
    public static <T> T get(String key, Class<T> type) {
        Object handler = handlers.get(key);
        if (handler == null) {
            throw new IllegalArgumentException("Handler not found: " + key);
        }
        if (!type.isInstance(handler)) {
            throw new IllegalArgumentException("Handler '" + key + "' is not of type " + type.getName() + 
                ", found: " + handler.getClass().getName());
        }
        return type.cast(handler);
    }
    
    /**
     * Check if a handler exists for the given key
     * @param key the key to check
     * @return true if handler exists, false otherwise
     */
    public static boolean exists(String key) {
        return handlers.containsKey(key);
    }
    
    /**
     * Remove a handler by key
     * @param key the key of the handler to remove
     * @return the removed handler, or null if not found
     */
    public static Object remove(String key) {
        return handlers.remove(key);
    }
    
    /**
     * Clear all registered handlers
     */
    public static void clear() {
        handlers.clear();
    }
    
    /**
     * Get all registered handler keys
     * @return a set of all registered keys
     */
    public static java.util.Set<String> getRegisteredKeys() {
        return handlers.keySet();
    }
}