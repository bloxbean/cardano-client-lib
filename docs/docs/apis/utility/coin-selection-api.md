# Coin Selection API

The Coin Selection API provides strategies for selecting UTXOs when building transactions. It offers various algorithms to optimize UTXO selection based on different criteria such as minimizing transaction size, maximizing efficiency, or following specific business logic.

## Overview

Coin selection is crucial for efficient transaction building in Cardano. The API provides:

- **Multiple Selection Strategies**: Different algorithms for various use cases
- **Configurable Selection**: Customize selection behavior and limits
- **Smart Filtering**: Filter UTXOs based on datum hash, inline datum, and other criteria
- **Multi-Asset Support**: Handle native tokens alongside ADA
- **Fallback Mechanisms**: Chain strategies for robust selection

## Core Interfaces

### UtxoSelectionStrategy Interface

The main interface for implementing custom UTXO selection strategies:

```java
public interface UtxoSelectionStrategy {
    Set`<Utxo>` select(AddressIterator addressIterator, List`<Amount>` outputAmounts, 
                    String datumHash, PlutusData inlineDatum, 
                    Set`<Utxo>` utxosToExclude, int maxUtxoSelectionLimit);
    
    UtxoSelectionStrategy fallback();
    void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash);
}
```

### UtxoSelector Interface

Higher-level interface for UTXO selection with built-in strategies:

```java
public interface UtxoSelector {
    List`<Utxo>` selectUtxos(String address, String unit, BigInteger amount, Set`<Utxo>` utxosToExclude);
    List`<Utxo>` selectUtxos(AddressIterator addrIter, List`<Amount>` amounts, Set`<Utxo>` utxosToExclude);
}
```

## Built-in Selection Strategies

### Default Strategy

The default strategy provides balanced UTXO selection:

```java
// Create with UTXO supplier
UtxoSupplier utxoSupplier = backendService.getUtxoService();
UtxoSelectionStrategy strategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);

// Configure to ignore UTXOs with datum hash
strategy.setIgnoreUtxosWithDatumHash(true);

// Select UTXOs for payment
Set`<Utxo>` selectedUtxos = strategy.select(
    AddressIterators.of(senderAddress),
    Arrays.asList(Amount.ada(10)),
    null, // No specific datum hash
    null, // No inline datum
    Collections.emptySet(), // No UTXOs to exclude
    20 // Max selection limit
);
```

### Largest First Strategy

Selects the largest UTXOs first to minimize the number of inputs:

```java
UtxoSelectionStrategy largestFirstStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);

// Select UTXOs using largest-first approach
Set`<Utxo>` selectedUtxos = largestFirstStrategy.select(
    AddressIterators.of(senderAddress),
    Arrays.asList(Amount.ada(5)),
    null, null,
    Collections.emptySet(),
    10
);
```

### Random Improve Strategy

Uses a randomized approach with improvement heuristics:

```java
UtxoSelectionStrategy randomImproveStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier);

// Configure random seed for reproducible results (optional)
randomImproveStrategy.setIgnoreUtxosWithDatumHash(false);

Set`<Utxo>` selectedUtxos = randomImproveStrategy.select(
    AddressIterators.of(senderAddress),
    Arrays.asList(Amount.ada(15)),
    null, null,
    Collections.emptySet(),
    15
);
```

## Using with UtxoSelector

### Default UtxoSelector

Use the default implementation for common scenarios:

```java
UtxoSupplier utxoSupplier = backendService.getUtxoService();
UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

// Select UTXOs for ADA payment
List`<Utxo>` selectedUtxos = utxoSelector.selectUtxos(
    senderAddress.getAddress(),
    "lovelace", // ADA unit
    Amount.ada(10).getQuantity(), // 10 ADA
    Collections.emptySet() // No exclusions
);

// Select UTXOs for multiple amounts
List`<Amount>` amounts = Arrays.asList(
    Amount.ada(5),
    new Amount("asset1abc123", BigInteger.valueOf(1000))
);

List`<Utxo>` multiAssetUtxos = utxoSelector.selectUtxos(
    AddressIterators.of(senderAddress),
    amounts,
    Collections.emptySet()
);
```

## Advanced Selection Scenarios

### Multi-Address Selection

Select UTXOs from multiple addresses:

```java
// Create address iterator for multiple addresses
List`<Address>` addresses = Arrays.asList(address1, address2, address3);
AddressIterator addressIterator = AddressIterators.of(addresses);

UtxoSelectionStrategy strategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);

Set`<Utxo>` selectedUtxos = strategy.select(
    addressIterator,
    Arrays.asList(Amount.ada(20)),
    null, null,
    Collections.emptySet(),
    30
);
```

### Selection with Datum Requirements

Select UTXOs with specific datum hash or inline datum:

```java
String requiredDatumHash = "abc123..."; // Specific datum hash
PlutusData requiredInlineDatum = PlutusData.of("hello"); // Inline datum

// Select UTXOs with specific datum hash
Set`<Utxo>` utxosWithDatum = strategy.select(
    AddressIterators.of(contractAddress),
    Arrays.asList(Amount.ada(5)),
    requiredDatumHash, // Must match this datum hash
    null,
    Collections.emptySet(),
    10
);

// Select UTXOs with inline datum
Set`<Utxo>` utxosWithInlineDatum = strategy.select(
    AddressIterators.of(contractAddress),
    Arrays.asList(Amount.ada(5)),
    null,
    requiredInlineDatum, // Must match this inline datum
    Collections.emptySet(),
    10
);
```

### Excluding Specific UTXOs

Exclude certain UTXOs from selection:

```java
// UTXOs to exclude (e.g., already used in pending transactions)
Set`<Utxo>` utxosToExclude = new HashSet<>();
utxosToExclude.add(pendingUtxo1);
utxosToExclude.add(pendingUtxo2);

Set`<Utxo>` selectedUtxos = strategy.select(
    AddressIterators.of(senderAddress),
    Arrays.asList(Amount.ada(10)),
    null, null,
    utxosToExclude, // Exclude these UTXOs
    20
);
```

## Configuration

### Global Coin Selection Configuration

Configure global coin selection settings:

```java
// Set global selection limit
CoinselectionConfig.INSTANCE.setCoinSelectionLimit(50);

// Get current selection limit
int currentLimit = CoinselectionConfig.INSTANCE.getCoinSelectionLimit();
```

### Strategy-Specific Configuration

Configure individual strategies:

```java
UtxoSelectionStrategy strategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);

// Ignore UTXOs with datum hash (default: true)
strategy.setIgnoreUtxosWithDatumHash(false);

// Allow selection of UTXOs with datum for smart contract interactions
strategy.setIgnoreUtxosWithDatumHash(false);
```

## Custom Selection Strategies

### Implement Custom Strategy

Create custom selection logic:

```java
public class CustomUtxoSelectionStrategy implements UtxoSelectionStrategy {
    
    private final UtxoSupplier utxoSupplier;
    private boolean ignoreUtxosWithDatumHash = true;
    
    public CustomUtxoSelectionStrategy(UtxoSupplier utxoSupplier) {
        this.utxoSupplier = utxoSupplier;
    }
    
    @Override
    public Set`<Utxo>` select(AddressIterator addressIterator, List`<Amount>` outputAmounts,
                           String datumHash, PlutusData inlineDatum,
                           Set`<Utxo>` utxosToExclude, int maxUtxoSelectionLimit) {
        
        Set`<Utxo>` selectedUtxos = new HashSet<>();
        Map<String, BigInteger> remainingAmounts = new HashMap<>();
        
        // Initialize remaining amounts
        for (Amount amount : outputAmounts) {
            remainingAmounts.put(amount.getUnit(), amount.getQuantity());
        }
        
        // Iterate through addresses
        while (addressIterator.hasNext()) {
            Address address = addressIterator.next();
            List`<Utxo>` utxos = utxoSupplier.getAll(address.getAddress());
            
            // Sort UTXOs by custom criteria (e.g., minimize fragmentation)
            utxos.sort(this::compareUtxos);
            
            for (Utxo utxo : utxos) {
                if (shouldSkipUtxo(utxo, datumHash, inlineDatum, utxosToExclude)) {
                    continue;
                }
                
                if (selectUtxoIfNeeded(utxo, remainingAmounts, selectedUtxos)) {
                    if (remainingAmounts.isEmpty()) {
                        return selectedUtxos;
                    }
                    
                    if (selectedUtxos.size() >= maxUtxoSelectionLimit) {
                        break;
                    }
                }
            }
        }
        
        return selectedUtxos;
    }
    
    private int compareUtxos(Utxo u1, Utxo u2) {
        // Custom comparison logic
        BigInteger value1 = getUtxoValue(u1);
        BigInteger value2 = getUtxoValue(u2);
        return value2.compareTo(value1); // Descending order
    }
    
    @Override
    public UtxoSelectionStrategy fallback() {
        return new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
    }
    
    @Override
    public void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash) {
        this.ignoreUtxosWithDatumHash = ignoreUtxosWithDatumHash;
    }
}
```

### Strategy with Fallback

Implement strategies with fallback mechanisms:

```java
public class StrategyWithFallback implements UtxoSelectionStrategy {
    
    private final UtxoSelectionStrategy primaryStrategy;
    private final UtxoSelectionStrategy fallbackStrategy;
    
    public StrategyWithFallback(UtxoSelectionStrategy primary, UtxoSelectionStrategy fallback) {
        this.primaryStrategy = primary;
        this.fallbackStrategy = fallback;
    }
    
    @Override
    public Set`<Utxo>` select(AddressIterator addressIterator, List`<Amount>` outputAmounts,
                           String datumHash, PlutusData inlineDatum,
                           Set`<Utxo>` utxosToExclude, int maxUtxoSelectionLimit) {
        try {
            Set`<Utxo>` result = primaryStrategy.select(
                addressIterator, outputAmounts, datumHash, inlineDatum,
                utxosToExclude, maxUtxoSelectionLimit);
            
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } catch (Exception e) {
            // Log error and try fallback
            System.err.println("Primary strategy failed: " + e.getMessage());
        }
        
        // Use fallback strategy
        return fallbackStrategy.select(
            addressIterator, outputAmounts, datumHash, inlineDatum,
            utxosToExclude, maxUtxoSelectionLimit);
    }
    
    @Override
    public UtxoSelectionStrategy fallback() {
        return fallbackStrategy;
    }
}
```

## Integration with Transaction Building

### With QuickTx

Use custom selection strategies with QuickTx:

```java
UtxoSelectionStrategy customStrategy = new CustomUtxoSelectionStrategy(utxoSupplier);

QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier)
    .utxoSelectionStrategy(customStrategy);

// Build transaction with custom selection
Tx tx = quickTxBuilder
    .payToAddress(receiverAddress, Amount.ada(10))
    .from(senderAddress)
    .build();
```

### With Transaction Builder

Use with lower-level transaction building:

```java
// Select UTXOs manually
List`<Utxo>` selectedUtxos = utxoSelector.selectUtxos(
    senderAddress.getAddress(),
    "lovelace",
    Amount.ada(15).getQuantity(),
    Collections.emptySet()
);

// Use selected UTXOs in transaction
TransactionBuilder txBuilder = new TransactionBuilder();
for (Utxo utxo : selectedUtxos) {
    txBuilder.addInput(utxo.toTransactionInput());
}
```

## Error Handling

Handle coin selection errors:

```java
try {
    Set`<Utxo>` selectedUtxos = strategy.select(
        AddressIterators.of(senderAddress),
        Arrays.asList(Amount.ada(1000)), // Large amount
        null, null,
        Collections.emptySet(),
        20
    );
    
    if (selectedUtxos.isEmpty()) {
        System.err.println("No suitable UTXOs found");
    }
    
} catch (InputsLimitExceededException e) {
    System.err.println("Selection limit exceeded: " + e.getMessage());
} catch (Exception e) {
    System.err.println("Selection failed: " + e.getMessage());
}
```

## Best Practices

### Optimize for Transaction Size

```java
// Use largest-first to minimize transaction size
UtxoSelectionStrategy strategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
```

### Handle Multi-Asset Scenarios

```java
// Select for multiple assets efficiently
List`<Amount>` multipleAssets = Arrays.asList(
    Amount.ada(5),
    new Amount("token1", BigInteger.valueOf(1000)),
    new Amount("token2", BigInteger.valueOf(500))
);

Set`<Utxo>` selectedUtxos = strategy.select(
    AddressIterators.of(senderAddress),
    multipleAssets,
    null, null,
    Collections.emptySet(),
    25 // Higher limit for multi-asset
);
```

### Implement Caching

```java
public class CachingUtxoSelector implements UtxoSelector {
    
    private final UtxoSelector delegate;
    private final Map`<String, List`<Utxo>>` cache = new ConcurrentHashMap<>();
    
    @Override
    public List`<Utxo>` selectUtxos(String address, String unit, BigInteger amount, Set`<Utxo>` utxosToExclude) {
        String cacheKey = address + ":" + unit + ":" + amount;
        
        return cache.computeIfAbsent(cacheKey, k -> 
            delegate.selectUtxos(address, unit, amount, utxosToExclude));
    }
}
```

The Coin Selection API provides flexible and efficient UTXO selection capabilities, enabling optimized transaction building for various use cases and requirements.
