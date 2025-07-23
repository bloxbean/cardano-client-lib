package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.registry;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.exception.ProcessingException;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry that manages DataTypeProcessor instances using the Strategy pattern.
 * This replaces the large switch statement in the original DataTypeProcessUtil.
 * 
 * Processors are automatically discovered and ordered by priority.
 * Supports caching for performance optimization.
 */
public class DataTypeProcessorRegistry {
    
    private final List<DataTypeProcessor> processors;
    private final Map<BlueprintDatatype, DataTypeProcessor> processorCache;
    private final boolean enableCaching;
    
    /**
     * Creates a registry with the given processors
     * 
     * @param processors list of data type processors
     */
    public DataTypeProcessorRegistry(List<DataTypeProcessor> processors) {
        this(processors, true);
    }
    
    /**
     * Creates a registry with optional caching
     * 
     * @param processors list of data type processors
     * @param enableCaching whether to enable processor caching
     */
    public DataTypeProcessorRegistry(List<DataTypeProcessor> processors, boolean enableCaching) {
        this.processors = processors.stream()
                .sorted(Comparator.comparing(DataTypeProcessor::getPriority))
                .collect(Collectors.toList());
        this.enableCaching = enableCaching;
        this.processorCache = enableCaching ? new ConcurrentHashMap<>() : null;
    }
    
    /**
     * Processes the data type using the appropriate processor
     * 
     * @param context the processing context
     * @return the generated FieldSpec
     * @throws ProcessingException if no processor can handle the data type
     */
    public FieldSpec processDataType(ProcessingContext context) {
        BlueprintDatatype dataType = context.getSchema().getDataType();
        
        if (dataType == null) {
            throw new ProcessingException(
                "Data type is null",
                context.getSchemaTitle(),
                "null"
            );
        }
        
        DataTypeProcessor processor = findProcessor(dataType);
        
        if (processor == null) {
            throw new ProcessingException(
                "No processor found for data type: " + dataType,
                context.getSchemaTitle(),
                dataType.toString()
            );
        }
        
        try {
            return processor.processDataType(context);
        } catch (Exception e) {
            throw new ProcessingException(
                "Failed to process data type: " + e.getMessage(),
                context.getSchemaTitle(),
                dataType.toString(),
                e
            );
        }
    }
    
    /**
     * Finds the appropriate processor for the given data type
     * 
     * @param dataType the data type to process
     * @return the processor or null if none found
     */
    public DataTypeProcessor findProcessor(BlueprintDatatype dataType) {
        if (enableCaching && processorCache.containsKey(dataType)) {
            return processorCache.get(dataType);
        }
        
        DataTypeProcessor processor = processors.stream()
                .filter(p -> p.canProcess(dataType))
                .findFirst()
                .orElse(null);
        
        if (enableCaching && processor != null) {
            processorCache.put(dataType, processor);
        }
        
        return processor;
    }
    
    /**
     * Checks if a processor exists for the given data type
     * 
     * @param dataType the data type to check
     * @return true if a processor exists
     */
    public boolean hasProcessor(BlueprintDatatype dataType) {
        return findProcessor(dataType) != null;
    }
    
    /**
     * Returns all registered processors
     * 
     * @return list of processors
     */
    public List<DataTypeProcessor> getProcessors() {
        return List.copyOf(processors);
    }
    
    /**
     * Returns processors that can handle the given data type
     * 
     * @param dataType the data type
     * @return list of compatible processors
     */
    public List<DataTypeProcessor> getProcessorsForType(BlueprintDatatype dataType) {
        return processors.stream()
                .filter(p -> p.canProcess(dataType))
                .collect(Collectors.toList());
    }
    
    /**
     * Returns information about the registry
     * 
     * @return registry info
     */
    public RegistryInfo getInfo() {
        Map<BlueprintDatatype, Long> supportedTypes = processors.stream()
                .flatMap(p -> java.util.Arrays.stream(BlueprintDatatype.values())
                        .filter(p::canProcess))
                .collect(Collectors.groupingBy(
                        type -> type,
                        Collectors.counting()
                ));
        
        return new RegistryInfo(
                processors.size(),
                supportedTypes.size(),
                enableCaching ? processorCache.size() : 0,
                supportedTypes
        );
    }
    
    /**
     * Clears the processor cache
     */
    public void clearCache() {
        if (enableCaching && processorCache != null) {
            processorCache.clear();
        }
    }
    
    /**
     * Information about the registry state
     */
    public static class RegistryInfo {
        private final int processorCount;
        private final int supportedTypeCount;
        private final int cacheSize;
        private final Map<BlueprintDatatype, Long> typeProcessorCounts;
        
        public RegistryInfo(int processorCount, int supportedTypeCount, int cacheSize,
                           Map<BlueprintDatatype, Long> typeProcessorCounts) {
            this.processorCount = processorCount;
            this.supportedTypeCount = supportedTypeCount;
            this.cacheSize = cacheSize;
            this.typeProcessorCounts = Map.copyOf(typeProcessorCounts);
        }
        
        public int getProcessorCount() { return processorCount; }
        public int getSupportedTypeCount() { return supportedTypeCount; }
        public int getCacheSize() { return cacheSize; }
        public Map<BlueprintDatatype, Long> getTypeProcessorCounts() { return typeProcessorCounts; }
        
        @Override
        public String toString() {
            return "RegistryInfo{" +
                   "processors=" + processorCount +
                   ", supportedTypes=" + supportedTypeCount +
                   ", cacheSize=" + cacheSize +
                   '}';
        }
    }
}