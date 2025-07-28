---
description: Comprehensive guide to the supplier pattern including UtxoSupplier, ProtocolParamsSupplier, custom supplier development, and caching strategies
sidebar_label: Supplier Pattern
sidebar_position: 4
---

# Supplier Pattern

The supplier pattern provides a flexible abstraction layer for accessing blockchain data in the Cardano Client Library. It enables backend independence, simplifies testing, and allows for custom implementations including caching strategies.

:::tip Prerequisites
Understanding of [Backend Services](../../fundamentals/backend-services/backend-providers.md) and [Transaction Building](../../quicktx/index.md) is recommended.
:::

## Overview

The supplier pattern serves several key purposes:

- **Backend Abstraction** - Decouple transaction building from specific backend implementations
- **Data Access Standardization** - Provide consistent interfaces for common data needs
- **Testability** - Enable easy mocking and testing
- **Flexibility** - Allow custom implementations for specialized requirements
- **Performance Optimization** - Support caching and other optimizations

Key supplier interfaces include:
- **UtxoSupplier** - UTXO retrieval and management
- **ProtocolParamsSupplier** - Protocol parameter access
- **ScriptSupplier** - Script retrieval by hash

## UtxoSupplier Interface

The `UtxoSupplier` interface provides methods for retrieving UTXOs from various sources.

### Core Interface Methods

```java
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.OrderEnum;
import java.util.List;
import java.util.Optional;

public interface UtxoSupplier {
    
    // Get paginated UTXOs for an address
    List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order);
    
    // Get a specific transaction output
    Optional<Utxo> getTxOutput(String txHash, int outputIndex);
    
    // Get all UTXOs for an address (default implementation available)
    default List<Utxo> getAll(String address) {
        List<Utxo> allUtxos = new ArrayList<>();
        int page = 0;
        List<Utxo> pageUtxos;
        
        do {
            pageUtxos = getPage(address, 100, page++, OrderEnum.asc);
            allUtxos.addAll(pageUtxos);
        } while (pageUtxos.size() == 100);
        
        return allUtxos;
    }
    
    // Check if an address has been used
    default boolean isUsedAddress(Address address) {
        List<Utxo> utxos = getPage(address.toBech32(), 1, 0, OrderEnum.asc);
        return !utxos.isEmpty();
    }
    
    // Configure search by address verification key hash
    default void setSearchByAddressVkh(boolean searchByAddressVkh) {
        // Implementation specific
    }
}
```

### Basic Usage

```java
public class UtxoSupplierExample {
    
    public void demonstrateBasicUsage(UtxoSupplier utxoSupplier) {
        // Get UTXOs for an address
        String address = "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x";
        
        // Paginated access
        List<Utxo> firstPage = utxoSupplier.getPage(address, 50, 0, OrderEnum.desc);
        System.out.println("First page UTXOs: " + firstPage.size());
        
        // Get all UTXOs
        List<Utxo> allUtxos = utxoSupplier.getAll(address);
        System.out.println("Total UTXOs: " + allUtxos.size());
        
        // Check specific output
        String txHash = "a1b2c3d4e5f6...";
        Optional<Utxo> specificUtxo = utxoSupplier.getTxOutput(txHash, 0);
        
        if (specificUtxo.isPresent()) {
            Utxo utxo = specificUtxo.get();
            System.out.println("Found UTXO: " + utxo.getAmount());
        }
        
        // Check if address is used
        boolean isUsed = utxoSupplier.isUsedAddress(Address.fromBech32(address));
        System.out.println("Address used: " + isUsed);
    }
}
```

## DefaultUtxoSupplier

The `DefaultUtxoSupplier` wraps a backend `UtxoService` to implement the `UtxoSupplier` interface.

### Implementation Details

```java
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.UtxoService;

public class DefaultUtxoSupplierExample {
    
    public void demonstrateDefaultSupplier(UtxoService utxoService) {
        // Create default supplier
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
        
        // The DefaultUtxoSupplier:
        // - Wraps backend service calls
        // - Handles API exceptions
        // - Converts backend responses to standard Utxo objects
        // - Supports address verification key hash searching
        
        // Enable VKH searching for better performance with some backends
        utxoSupplier.setSearchByAddressVkh(true);
        
        // Use in transaction building
        TxBuilderContext context = TxBuilderContext.init(utxoSupplier, protocolParams);
    }
    
    // Error handling example
    public List<Utxo> getUtxosWithErrorHandling(UtxoSupplier supplier, String address) {
        try {
            return supplier.getAll(address);
        } catch (RuntimeException e) {
            // DefaultUtxoSupplier wraps backend exceptions
            System.err.println("Failed to fetch UTXOs: " + e.getMessage());
            
            // Could implement retry logic or fallback
            return Collections.emptyList();
        }
    }
}
```

### Backend Service Integration

```java
public class BackendIntegrationExample {
    
    public UtxoSupplier createSupplierFromBackend(BackendService backendService) {
        // Each backend service provides access to UTXO service
        UtxoService utxoService = backendService.getUtxoService();
        
        // Create supplier from service
        return new DefaultUtxoSupplier(utxoService);
    }
    
    // Different backend examples
    public void demonstrateBackendSuppliers() {
        // Blockfrost
        BackendService blockfrost = new BFBackendService(
            Constants.BLOCKFROST_TESTNET_URL, 
            apiKey
        );
        UtxoSupplier blockfrostSupplier = new DefaultUtxoSupplier(
            blockfrost.getUtxoService()
        );
        
        // Koios
        BackendService koios = new KoiosBackendService(
            Constants.KOIOS_TESTNET_URL
        );
        UtxoSupplier koiosSupplier = new DefaultUtxoSupplier(
            koios.getUtxoService()
        );
        
        // Suppliers work identically regardless of backend
        List<Utxo> utxos1 = blockfrostSupplier.getAll(address);
        List<Utxo> utxos2 = koiosSupplier.getAll(address);
    }
}
```

## ProtocolParamsSupplier

The `ProtocolParamsSupplier` provides access to current protocol parameters needed for transaction building.

### Interface and Usage

```java
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;

public class ProtocolParamsSupplierExample {
    
    public void demonstrateProtocolParams() {
        // Create supplier (functional interface)
        ProtocolParamsSupplier supplier = () -> {
            // Fetch current protocol parameters
            return backendService.getEpochService()
                .getProtocolParameters()
                .getValue();
        };
        
        // Get parameters
        ProtocolParams params = supplier.getProtocolParams();
        
        // Access specific parameters
        System.out.println("Min fee A: " + params.getMinFeeA());
        System.out.println("Min fee B: " + params.getMinFeeB());
        System.out.println("Max tx size: " + params.getMaxTxSize());
        System.out.println("Min UTXO: " + params.getMinUtxo());
        System.out.println("Key deposit: " + params.getKeyDeposit());
        System.out.println("Pool deposit: " + params.getPoolDeposit());
        
        // Protocol parameters are essential for:
        // - Fee calculation
        // - Transaction size validation
        // - Min UTXO calculation
        // - Deposit amounts
    }
}
```

### DefaultProtocolParamsSupplier

```java
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;

public class DefaultProtocolParamsExample {
    
    public void demonstrateDefaultSupplier() {
        // Create from epoch service
        EpochService epochService = backendService.getEpochService();
        ProtocolParamsSupplier supplier = new DefaultProtocolParamsSupplier(epochService);
        
        // Use in transaction context
        TxBuilderContext context = TxBuilderContext.init(
            utxoSupplier, 
            supplier.getProtocolParams()
        );
        
        // The default supplier handles:
        // - API calls to epoch service
        // - Error handling
        // - Response transformation
    }
    
    // Caching protocol parameters
    public ProtocolParamsSupplier createCachedSupplier(EpochService epochService) {
        return new ProtocolParamsSupplier() {
            private ProtocolParams cachedParams;
            private long lastFetch = 0;
            private static final long CACHE_DURATION = 3600000; // 1 hour
            
            @Override
            public ProtocolParams getProtocolParams() {
                long now = System.currentTimeMillis();
                
                if (cachedParams == null || (now - lastFetch) > CACHE_DURATION) {
                    cachedParams = new DefaultProtocolParamsSupplier(epochService)
                        .getProtocolParams();
                    lastFetch = now;
                }
                
                return cachedParams;
            }
        };
    }
}
```

## ScriptSupplier

The `ScriptSupplier` interface provides script retrieval functionality for reference scripts.

### Interface and Implementation

```java
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

public class ScriptSupplierExample {
    
    public void demonstrateScriptSupplier() {
        // Create script supplier
        ScriptSupplier scriptSupplier = scriptHash -> {
            try {
                // Fetch script from backend
                ScriptService scriptService = backendService.getScriptService();
                Result<ScriptContent> result = scriptService.getScript(scriptHash);
                
                if (result.isSuccessful()) {
                    ScriptContent content = result.getValue();
                    // Convert to PlutusScript
                    return Optional.of(deserializeScript(content));
                }
                
                return Optional.empty();
            } catch (Exception e) {
                logger.error("Failed to fetch script: " + scriptHash, e);
                return Optional.empty();
            }
        };
        
        // Use script supplier
        String scriptHash = "abc123...";
        Optional<PlutusScript> script = scriptSupplier.getScript(scriptHash);
        
        if (script.isPresent()) {
            PlutusScript plutusScript = script.get();
            System.out.println("Script type: " + plutusScript.getType());
            System.out.println("Script size: " + plutusScript.scriptBytes().length);
        }
    }
    
    // Default implementation
    public ScriptSupplier createDefaultScriptSupplier(ScriptService scriptService) {
        return new DefaultScriptSupplier(scriptService);
    }
}
```

## Custom Supplier Development

### Basic Custom Supplier

```java
public class CustomUtxoSupplier implements UtxoSupplier {
    
    private final CustomBackend backend;
    
    public CustomUtxoSupplier(CustomBackend backend) {
        this.backend = backend;
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        try {
            // Call custom backend
            CustomUtxoResponse response = backend.queryUtxos(address, nrOfItems, page, order);
            
            // Transform to standard Utxo objects
            return response.getItems().stream()
                .map(this::convertToUtxo)
                .collect(Collectors.toList());
                
        } catch (CustomBackendException e) {
            throw new RuntimeException("Failed to fetch UTXOs", e);
        }
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        try {
            CustomTxOutput output = backend.getOutput(txHash, outputIndex);
            return Optional.ofNullable(output).map(this::convertToUtxo);
            
        } catch (CustomBackendException e) {
            logger.warn("Failed to fetch output: {}#{}", txHash, outputIndex, e);
            return Optional.empty();
        }
    }
    
    private Utxo convertToUtxo(CustomUtxoData data) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(data.getTxId());
        utxo.setOutputIndex(data.getIndex());
        utxo.setAddress(data.getAddress());
        
        // Convert amounts
        List<Amount> amounts = data.getAssets().stream()
            .map(asset -> Amount.asset(asset.getPolicy(), asset.getName(), asset.getQuantity()))
            .collect(Collectors.toList());
        
        // Add ADA amount
        amounts.add(0, Amount.lovelace(data.getLovelaceAmount()));
        utxo.setAmount(amounts);
        
        // Handle datum if present
        if (data.hasDatum()) {
            utxo.setDataHash(data.getDatumHash());
            utxo.setInlineDatum(data.getInlineDatum());
        }
        
        return utxo;
    }
}
```

### Database-Backed Supplier

```java
public class DatabaseUtxoSupplier implements UtxoSupplier {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    public DatabaseUtxoSupplier(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        String sql = """
            SELECT tx_hash, output_index, address, amounts, datum_hash, inline_datum
            FROM utxos
            WHERE address = ? AND spent = false
            ORDER BY created_at %s
            LIMIT ? OFFSET ?
            """.formatted(order == OrderEnum.desc ? "DESC" : "ASC");
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, address);
            ps.setInt(2, nrOfItems);
            ps.setInt(3, page * nrOfItems);
            
            List<Utxo> utxos = new ArrayList<>();
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    utxos.add(mapResultSetToUtxo(rs));
                }
            }
            
            return utxos;
            
        } catch (SQLException e) {
            throw new RuntimeException("Database query failed", e);
        }
    }
    
    private Utxo mapResultSetToUtxo(ResultSet rs) throws SQLException {
        Utxo utxo = new Utxo();
        utxo.setTxHash(rs.getString("tx_hash"));
        utxo.setOutputIndex(rs.getInt("output_index"));
        utxo.setAddress(rs.getString("address"));
        
        // Parse amounts JSON
        String amountsJson = rs.getString("amounts");
        try {
            List<Amount> amounts = objectMapper.readValue(amountsJson, 
                new TypeReference<List<Amount>>() {});
            utxo.setAmount(amounts);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse amounts", e);
        }
        
        // Optional fields
        utxo.setDataHash(rs.getString("datum_hash"));
        
        String inlineDatumJson = rs.getString("inline_datum");
        if (inlineDatumJson != null) {
            try {
                PlutusData datum = objectMapper.readValue(inlineDatumJson, PlutusData.class);
                utxo.setInlineDatum(datum);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to parse inline datum", e);
            }
        }
        
        return utxo;
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        String sql = """
            SELECT * FROM utxos 
            WHERE tx_hash = ? AND output_index = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, txHash);
            ps.setInt(2, outputIndex);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUtxo(rs));
                }
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            logger.error("Failed to fetch output: {}#{}", txHash, outputIndex, e);
            return Optional.empty();
        }
    }
}
```

## Caching Strategies

### Simple Time-Based Cache

```java
public class CachedUtxoSupplier implements UtxoSupplier {
    
    private final UtxoSupplier delegate;
    private final Map<String, CachedResult<List<Utxo>>> cache;
    private final long cacheDurationMs;
    
    public CachedUtxoSupplier(UtxoSupplier delegate, Duration cacheDuration) {
        this.delegate = delegate;
        this.cache = new ConcurrentHashMap<>();
        this.cacheDurationMs = cacheDuration.toMillis();
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        String cacheKey = String.format("%s:%d:%d:%s", address, nrOfItems, page, order);
        
        CachedResult<List<Utxo>> cached = cache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            return cached.getValue();
        }
        
        // Fetch from delegate
        List<Utxo> result = delegate.getPage(address, nrOfItems, page, order);
        
        // Cache result
        cache.put(cacheKey, new CachedResult<>(result, cacheDurationMs));
        
        return result;
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        // Transaction outputs are immutable, can cache longer
        String cacheKey = txHash + ":" + outputIndex;
        
        // Similar caching logic...
        return delegate.getTxOutput(txHash, outputIndex);
    }
    
    public void clearCache() {
        cache.clear();
    }
    
    public void clearAddressCache(String address) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(address + ":"));
    }
    
    private static class CachedResult<T> {
        private final T value;
        private final long expiryTime;
        
        CachedResult(T value, long durationMs) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + durationMs;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
        
        T getValue() {
            return value;
        }
    }
}
```

### Advanced Caching with Caffeine

```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class CaffeineUtxoSupplier implements UtxoSupplier {
    
    private final UtxoSupplier delegate;
    private final LoadingCache<String, List<Utxo>> pageCache;
    private final Cache<String, Optional<Utxo>> outputCache;
    
    public CaffeineUtxoSupplier(UtxoSupplier delegate) {
        this.delegate = delegate;
        
        // Page cache with automatic loading
        this.pageCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats() // Enable statistics
            .build(this::loadPage);
        
        // Output cache - longer TTL for immutable data
        this.outputCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        String key = createPageKey(address, nrOfItems, page, order);
        return pageCache.get(key);
    }
    
    private List<Utxo> loadPage(String key) {
        // Parse key to extract parameters
        String[] parts = key.split(":");
        return delegate.getPage(parts[0], Integer.parseInt(parts[1]), 
                               Integer.parseInt(parts[2]), OrderEnum.valueOf(parts[3]));
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        String key = txHash + ":" + outputIndex;
        
        return outputCache.get(key, k -> delegate.getTxOutput(txHash, outputIndex));
    }
    
    // Cache management
    public void invalidateAddress(String address) {
        pageCache.asMap().keySet().removeIf(key -> key.startsWith(address + ":"));
    }
    
    public CacheStats getStats() {
        return new CacheStats(
            pageCache.stats().hitRate(),
            pageCache.stats().missCount(),
            pageCache.estimatedSize(),
            outputCache.estimatedSize()
        );
    }
    
    private String createPageKey(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        return String.format("%s:%d:%d:%s", address, nrOfItems, page, order);
    }
}
```

### Redis-Backed Caching

```java
public class RedisBackedUtxoSupplier implements UtxoSupplier {
    
    private final UtxoSupplier delegate;
    private final RedisTemplate<String, List<Utxo>> redisTemplate;
    private final Duration ttl;
    
    public RedisBackedUtxoSupplier(UtxoSupplier delegate, RedisTemplate<String, List<Utxo>> redisTemplate) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(5);
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        String key = createRedisKey(address, nrOfItems, page, order);
        
        // Try cache first
        List<Utxo> cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        
        // Fetch from delegate
        List<Utxo> result = delegate.getPage(address, nrOfItems, page, order);
        
        // Cache with TTL
        redisTemplate.opsForValue().set(key, result, ttl);
        
        return result;
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        String key = "utxo:output:" + txHash + ":" + outputIndex;
        
        // Check cache
        List<Utxo> cached = redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isEmpty()) {
            return Optional.of(cached.get(0));
        }
        
        // Fetch from delegate
        Optional<Utxo> result = delegate.getTxOutput(txHash, outputIndex);
        
        // Cache if present
        result.ifPresent(utxo -> 
            redisTemplate.opsForValue().set(key, List.of(utxo), Duration.ofHours(24))
        );
        
        return result;
    }
    
    private String createRedisKey(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        return String.format("utxo:page:%s:%d:%d:%s", address, nrOfItems, page, order);
    }
    
    // Bulk operations for warming cache
    public void warmCache(List<String> addresses) {
        CompletableFuture<?>[] futures = addresses.stream()
            .map(address -> CompletableFuture.runAsync(() -> {
                // Pre-fetch first page for each address
                getPage(address, 100, 0, OrderEnum.desc);
            }))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
    }
}
```

## Integration with Transaction Building

### QuickTx Integration

```java
public class SupplierIntegrationExample {
    
    public void demonstrateQuickTxIntegration() {
        // Create suppliers
        UtxoSupplier utxoSupplier = new CachedUtxoSupplier(
            new DefaultUtxoSupplier(backendService.getUtxoService()),
            Duration.ofMinutes(5)
        );
        
        ProtocolParamsSupplier protocolSupplier = new DefaultProtocolParamsSupplier(
            backendService.getEpochService()
        );
        
        // Create QuickTx builder with suppliers
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        
        // Transaction uses suppliers internally
        Tx tx = new Tx()
            .payTo(receiverAddress, Amount.ada(10))
            .from(senderAddress);
        
        Result<String> result = quickTxBuilder.compose(tx)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .completeAndSubmit();
    }
}
```

### Composable Functions Integration

```java
public class ComposableFunctionsExample {
    
    public void demonstrateComposableFunctions() {
        // Create context with suppliers
        UtxoSupplier utxoSupplier = createCustomUtxoSupplier();
        ProtocolParams protocolParams = getProtocolParams();
        
        TxBuilderContext context = TxBuilderContext.init(utxoSupplier, protocolParams);
        
        // Optional: Add script supplier
        ScriptSupplier scriptSupplier = new DefaultScriptSupplier(
            backendService.getScriptService()
        );
        context.setScriptSupplier(scriptSupplier);
        
        // Build transaction using suppliers
        Transaction transaction = context.buildTransaction(txBuilder -> {
            // Transaction building logic
            // Suppliers are used internally for:
            // - UTXO selection
            // - Fee calculation
            // - Script resolution
        });
    }
}
```

## Testing with Mock Suppliers

### Mock UTXO Supplier

```java
public class MockUtxoSupplier implements UtxoSupplier {
    
    private final Map<String, List<Utxo>> utxosByAddress = new HashMap<>();
    private final Map<String, Utxo> utxosByTxOutput = new HashMap<>();
    
    public void addUtxo(Utxo utxo) {
        utxosByAddress.computeIfAbsent(utxo.getAddress(), k -> new ArrayList<>()).add(utxo);
        utxosByTxOutput.put(utxo.getTxHash() + ":" + utxo.getOutputIndex(), utxo);
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        List<Utxo> allUtxos = utxosByAddress.getOrDefault(address, Collections.emptyList());
        
        // Apply pagination
        int start = page * nrOfItems;
        int end = Math.min(start + nrOfItems, allUtxos.size());
        
        if (start >= allUtxos.size()) {
            return Collections.emptyList();
        }
        
        List<Utxo> pageUtxos = new ArrayList<>(allUtxos.subList(start, end));
        
        // Apply ordering
        if (order == OrderEnum.desc) {
            Collections.reverse(pageUtxos);
        }
        
        return pageUtxos;
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        return Optional.ofNullable(utxosByTxOutput.get(txHash + ":" + outputIndex));
    }
    
    // Test helper methods
    public void createUtxoWithAda(String address, BigInteger lovelace) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(UUID.randomUUID().toString());
        utxo.setOutputIndex(0);
        utxo.setAddress(address);
        utxo.setAmount(List.of(Amount.lovelace(lovelace)));
        
        addUtxo(utxo);
    }
    
    public void createUtxoWithAssets(String address, Amount... amounts) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(UUID.randomUUID().toString());
        utxo.setOutputIndex(0);
        utxo.setAddress(address);
        utxo.setAmount(Arrays.asList(amounts));
        
        addUtxo(utxo);
    }
}
```

### Testing Example

```java
public class SupplierTestExample {
    
    @Test
    public void testTransactionWithMockSupplier() {
        // Create mock supplier
        MockUtxoSupplier mockUtxoSupplier = new MockUtxoSupplier();
        
        // Add test UTXOs
        String senderAddress = "addr_test1...";
        mockUtxoSupplier.createUtxoWithAda(senderAddress, BigInteger.valueOf(10_000_000)); // 10 ADA
        mockUtxoSupplier.createUtxoWithAssets(senderAddress, 
            Amount.lovelace(2_000_000),
            Amount.asset("policy123", "token", 1000)
        );
        
        // Mock protocol params
        ProtocolParams mockParams = ProtocolParams.builder()
            .minFeeA(44)
            .minFeeB(155381)
            .maxTxSize(16384)
            .minUtxo("1000000")
            .build();
        
        // Create transaction context
        TxBuilderContext context = TxBuilderContext.init(mockUtxoSupplier, mockParams);
        
        // Build and test transaction
        // Transaction building will use mock suppliers
    }
}
```

## Best Practices

### Supplier Design Guidelines

```java
public class SupplierBestPractices {
    
    // 1. Always handle errors gracefully
    public class ResilientUtxoSupplier implements UtxoSupplier {
        private final UtxoSupplier primary;
        private final UtxoSupplier fallback;
        
        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            try {
                return primary.getPage(address, nrOfItems, page, order);
            } catch (Exception e) {
                logger.warn("Primary supplier failed, using fallback", e);
                return fallback.getPage(address, nrOfItems, page, order);
            }
        }
    }
    
    // 2. Implement proper logging
    public class LoggingUtxoSupplier implements UtxoSupplier {
        private final UtxoSupplier delegate;
        private final Logger logger = LoggerFactory.getLogger(LoggingUtxoSupplier.class);
        
        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            long startTime = System.currentTimeMillis();
            
            try {
                List<Utxo> result = delegate.getPage(address, nrOfItems, page, order);
                
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("Fetched {} UTXOs for {} in {}ms", 
                           result.size(), address, duration);
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to fetch UTXOs for {}", address, e);
                throw e;
            }
        }
    }
    
    // 3. Consider thread safety
    public class ThreadSafeSupplier implements UtxoSupplier {
        private final UtxoSupplier delegate;
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        
        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            lock.readLock().lock();
            try {
                return delegate.getPage(address, nrOfItems, page, order);
            } finally {
                lock.readLock().unlock();
            }
        }
    }
    
    // 4. Implement monitoring
    public class MonitoredUtxoSupplier implements UtxoSupplier {
        private final UtxoSupplier delegate;
        private final MeterRegistry meterRegistry;
        
        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            return Timer.Sample.start(meterRegistry)
                .stop(meterRegistry.timer("utxo.supplier.getPage"))
                .recordCallable(() -> {
                    List<Utxo> result = delegate.getPage(address, nrOfItems, page, order);
                    
                    meterRegistry.counter("utxo.supplier.fetched")
                        .increment(result.size());
                    
                    return result;
                });
        }
    }
}
```

### Performance Optimization

```java
public class PerformanceOptimizedSupplier implements UtxoSupplier {
    
    private final UtxoSupplier delegate;
    private final ExecutorService executorService;
    
    public PerformanceOptimizedSupplier(UtxoSupplier delegate) {
        this.delegate = delegate;
        this.executorService = ForkJoinPool.commonPool();
    }
    
    // Parallel fetching for multiple addresses
    public CompletableFuture<Map<String, List<Utxo>>> getUtxosForAddresses(List<String> addresses) {
        List<CompletableFuture<Pair<String, List<Utxo>>>> futures = addresses.stream()
            .map(address -> CompletableFuture.supplyAsync(() -> 
                new Pair<>(address, delegate.getAll(address)), executorService))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)));
    }
    
    // Batch fetching with connection pooling
    public List<Utxo> batchFetchUtxos(List<String> addresses, int batchSize) {
        List<List<String>> batches = Lists.partition(addresses, batchSize);
        
        return batches.parallelStream()
            .flatMap(batch -> batch.stream()
                .flatMap(address -> delegate.getAll(address).stream()))
            .collect(Collectors.toList());
    }
}
```

The supplier pattern provides a powerful abstraction for data access in the Cardano Client Library. By implementing custom suppliers with appropriate caching strategies, you can optimize performance, improve testability, and maintain clean separation between data access and business logic.