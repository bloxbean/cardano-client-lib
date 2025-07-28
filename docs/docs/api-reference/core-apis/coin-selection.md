---
description: Comprehensive guide to UTXO selection strategies including UtxoSelectionStrategy interface, built-in implementations, custom strategy development, and transaction building integration
sidebar_label: Coin Selection
sidebar_position: 3
---

# Coin Selection

The coin selection module provides flexible and efficient UTXO selection strategies for transaction building. It includes multiple built-in algorithms optimized for different use cases and supports custom strategy implementation.

:::tip Prerequisites
Understanding of [Transaction Building](../../quicktx/index.md) and UTXO concepts is recommended.
:::

## Overview

UTXO selection is a critical component in transaction building that determines which unspent transaction outputs to use as inputs. The library provides several strategies:

- **DefaultUtxoSelectionStrategyImpl** - Asset-matching prioritized selection with pagination
- **LargestFirstUtxoSelectionStrategy** - Largest UTXOs first for minimal inputs
- **RandomImproveUtxoSelectionStrategy** - Cardano spec-compliant random-improve algorithm
- **ExcludeUtxoSelectionStrategy** - Wrapper for UTXO exclusion
- **Custom Strategies** - Implement your own selection logic

## UtxoSelectionStrategy Interface

The `UtxoSelectionStrategy` interface defines the contract for UTXO selection algorithms.

### Core Interface

```java
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.Amount;
import java.util.Set;
import java.util.List;

public interface UtxoSelectionStrategy {
    
    // Main selection method
    Set<Utxo> select(AddressIterator addressIterator, 
                     List<Amount> outputAmounts, 
                     String datumHash, 
                     PlutusData inlineDatum, 
                     Set<Utxo> utxosToExclude, 
                     int maxUtxoSelectionLimit);
    
    // Fallback strategy for failed selections
    UtxoSelectionStrategy fallback();
    
    // Convenience methods with fewer parameters
    default Set<Utxo> select(AddressIterator addressIterator, 
                            List<Amount> outputAmounts) {
        return select(addressIterator, outputAmounts, null, null, 
                     Collections.emptySet(), Integer.MAX_VALUE);
    }
}
```

### Selection Parameters

```java
public class SelectionParametersExample {
    
    public void demonstrateParameters() {
        UtxoSelectionStrategy strategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        
        // Address iterator - provides addresses to search for UTXOs
        AddressIterator addressIterator = AddressIterator.create(
            Arrays.asList(
                "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x",
                "addr1qy99ljm0x8zmuahd6uj0cr4cp0w2x6y0rt5jjf7d7y8w7jzm6a7n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2syzz4u3"
            )
        );
        
        // Output amounts - what needs to be covered
        List<Amount> outputAmounts = Arrays.asList(
            Amount.ada(10.5),                    // 10.5 ADA
            Amount.asset("policy123", "token", 1000), // 1000 custom tokens
            Amount.ada(2.0)                      // Additional 2 ADA
        );
        
        // Datum hash - for script UTXOs (optional)
        String datumHash = "d5e6bf0500378d4f0da4e8dde6becec7621cd8cbf5cbb9b87013d4cc8e39e4b0";
        
        // Inline datum - for script UTXOs (optional)
        PlutusData inlineDatum = PlutusData.unit();
        
        // UTXOs to exclude - prevent double spending
        Set<Utxo> utxosToExclude = Set.of(
            // UTXOs from pending transactions
        );
        
        // Maximum UTXOs to select
        int maxUtxoSelectionLimit = 20;
        
        // Perform selection
        Set<Utxo> selectedUtxos = strategy.select(
            addressIterator,
            outputAmounts,
            datumHash,
            inlineDatum,
            utxosToExclude,
            maxUtxoSelectionLimit
        );
        
        System.out.println("Selected " + selectedUtxos.size() + " UTXOs");
    }
}
```

## DefaultUtxoSelectionStrategyImpl

The default strategy prioritizes UTXOs that contain the most matching assets required by the transaction outputs.

### Algorithm Overview

```java
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.supplier.api.UtxoSupplier;

public class DefaultStrategyExample {
    
    public void demonstrateDefaultStrategy() {
        // Create strategy with UTXO supplier
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        UtxoSelectionStrategy strategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        
        // The strategy works in phases:
        // 1. Groups output amounts by asset unit
        // 2. Iterates through addresses
        // 3. Fetches UTXOs in pages (100 per page)
        // 4. Prioritizes UTXOs with most matching assets
        // 5. Continues until all requirements are met
        
        demonstrateSelection(strategy);
    }
    
    private void demonstrateSelection(UtxoSelectionStrategy strategy) {
        // Multi-asset transaction requirements
        List<Amount> outputs = Arrays.asList(
            Amount.ada(5.0),                           // ADA requirement
            Amount.asset("policy1", "tokenA", 100),    // Token A requirement
            Amount.asset("policy1", "tokenB", 50),     // Token B requirement
            Amount.asset("policy2", "tokenC", 25)      // Token C requirement
        );
        
        AddressIterator addresses = createAddressIterator();
        Set<Utxo> selected = strategy.select(addresses, outputs);
        
        // Analyze selection efficiency
        analyzeSelection(selected, outputs);
    }
    
    private void analyzeSelection(Set<Utxo> selectedUtxos, List<Amount> requiredOutputs) {
        System.out.println("Selection Analysis:");
        System.out.println("UTXOs selected: " + selectedUtxos.size());
        
        // Calculate total values
        Map<String, BigInteger> totalValues = new HashMap<>();
        for (Utxo utxo : selectedUtxos) {
            for (Amount amount : utxo.getAmount()) {
                String unit = amount.getUnit();
                BigInteger current = totalValues.getOrDefault(unit, BigInteger.ZERO);
                totalValues.put(unit, current.add(amount.getQuantity()));
            }
        }
        
        // Compare with requirements
        Map<String, BigInteger> required = groupRequiredAmounts(requiredOutputs);
        
        for (Map.Entry<String, BigInteger> entry : required.entrySet()) {
            String unit = entry.getKey();
            BigInteger requiredAmount = entry.getValue();
            BigInteger selectedAmount = totalValues.getOrDefault(unit, BigInteger.ZERO);
            
            System.out.println(String.format("Unit: %s, Required: %s, Selected: %s", 
                unit, requiredAmount, selectedAmount));
        }
    }
}
```

### Configuration and Pagination

```java
public class DefaultStrategyConfiguration {
    
    public void demonstrateConfiguration() {
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        DefaultUtxoSelectionStrategyImpl strategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        
        // The strategy uses pagination to manage memory usage
        // Default: 100 UTXOs per page
        // This prevents loading all UTXOs into memory at once
        
        // Configure datum hash handling
        // ignoreUtxosWithDatumHash = true by default
        // This means script UTXOs are ignored unless specifically requested
        
        // Fallback behavior
        // On failure or input limit exceeded, falls back to LargestFirstUtxoSelectionStrategy
        UtxoSelectionStrategy fallback = strategy.fallback();
        System.out.println("Fallback strategy: " + fallback.getClass().getSimpleName());
    }
    
    // Custom configuration example
    public UtxoSelectionStrategy createConfiguredStrategy() {
        UtxoSupplier customSupplier = new DefaultUtxoSupplier(backendService.getUtxoService()) {
            @Override
            public Page<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
                // Custom pagination logic
                // Could implement caching, filtering, etc.
                return super.getPage(address, nrOfItems, page, order);
            }
        };
        
        return new DefaultUtxoSelectionStrategyImpl(customSupplier);
    }
}
```

## LargestFirstUtxoSelectionStrategy

This strategy selects the largest UTXOs first to minimize the number of transaction inputs.

### Algorithm Implementation

```java
import com.bloxbean.cardano.client.coinselection.impl.LargestFirstUtxoSelectionStrategy;

public class LargestFirstExample {
    
    public void demonstrateLargestFirst() {
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        UtxoSelectionStrategy strategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
        
        // Algorithm steps:
        // 1. Fetches ALL UTXOs from all addresses
        // 2. Filters UTXOs containing required assets
        // 3. Sorts by total quantity (descending)
        // 4. Selects UTXOs until requirements are met
        
        List<Amount> outputs = Arrays.asList(
            Amount.ada(50.0)  // Large ADA requirement
        );
        
        AddressIterator addresses = createAddressIterator();
        Set<Utxo> selected = strategy.select(addresses, outputs);
        
        // This strategy typically selects fewer UTXOs
        System.out.println("UTXOs selected: " + selected.size());
        
        // But may create larger change outputs
        analyzeChangeOutputs(selected, outputs);
    }
    
    private void analyzeChangeOutputs(Set<Utxo> selectedUtxos, List<Amount> outputs) {
        // Calculate total selected value
        BigInteger totalSelected = selectedUtxos.stream()
            .flatMap(utxo -> utxo.getAmount().stream())
            .filter(amount -> amount.getUnit().equals("lovelace"))
            .map(Amount::getQuantity)
            .reduce(BigInteger.ZERO, BigInteger::add);
        
        // Calculate required value
        BigInteger totalRequired = outputs.stream()
            .filter(amount -> amount.getUnit().equals("lovelace"))
            .map(Amount::getQuantity)
            .reduce(BigInteger.ZERO, BigInteger::add);
        
        // Calculate change
        BigInteger change = totalSelected.subtract(totalRequired);
        
        System.out.println("Total selected: " + totalSelected + " lovelace");
        System.out.println("Total required: " + totalRequired + " lovelace");
        System.out.println("Change amount: " + change + " lovelace");
        
        // Large change may require splitting into multiple outputs
        if (change.compareTo(BigInteger.valueOf(5000000)) > 0) { // > 5 ADA
            System.out.println("Large change output - consider UTXO set optimization");
        }
    }
}
```

### Use Cases and Trade-offs

```java
public class LargestFirstUseCases {
    
    // Best for high-value transactions
    public void highValueTransaction() {
        UtxoSelectionStrategy strategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
        
        // Large payment - minimize transaction inputs
        List<Amount> largePayment = Arrays.asList(
            Amount.ada(1000.0)  // 1000 ADA payment
        );
        
        Set<Utxo> selected = strategy.select(addressIterator, largePayment);
        
        // Benefits:
        // - Lower transaction fees (fewer inputs)
        // - Simpler transaction structure
        // - Faster validation
        
        System.out.println("Large payment uses " + selected.size() + " inputs");
    }
    
    // Consider alternatives for frequent small transactions
    public void smallFrequentTransactions() {
        // For small, frequent transactions, LargestFirst may:
        // - Create fragmented UTXO sets
        // - Generate large change outputs
        // - Lead to inefficient long-term UTXO management
        
        System.out.println("Consider RandomImproveUtxoSelectionStrategy for frequent small transactions");
    }
    
    // Memory considerations
    public void memoryUsageConsiderations() {
        // LargestFirst loads ALL UTXOs into memory
        // This can be problematic for:
        // - Addresses with many UTXOs (>1000)
        // - Mobile applications with memory constraints
        // - High-frequency applications
        
        // Monitor memory usage
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        UtxoSelectionStrategy strategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
        Set<Utxo> selected = strategy.select(addressIterator, outputs);
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("Memory used: " + (memoryAfter - memoryBefore) + " bytes");
    }
}
```

## RandomImproveUtxoSelectionStrategy

This strategy implements the Cardano specification's Random-Improve algorithm for optimal change output management.

### Algorithm Details

```java
import com.bloxbean.cardano.client.coinselection.impl.RandomImproveUtxoSelectionStrategy;
import java.security.SecureRandom;

public class RandomImproveExample {
    
    public void demonstrateRandomImprove() {
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        UtxoSelectionStrategy strategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier);
        
        // Algorithm has two phases:
        // Phase 1: Random Selection
        // Phase 2: Improvement
        
        demonstratePhases();
    }
    
    private void demonstratePhases() {
        // Phase 1: Random Selection
        // - Process outputs in descending order of coin value
        // - Randomly select UTXOs until each output requirement is met
        // - Build base selection
        
        System.out.println("Phase 1: Random Selection");
        System.out.println("- Outputs processed in descending value order");
        System.out.println("- Random UTXO selection per output");
        System.out.println("- Ensures all requirements are met");
        
        // Phase 2: Improvement
        // - Process outputs in ascending order of coin value
        // - Calculate target ranges for each output
        // - Attempt to improve selection by adding random UTXOs
        
        System.out.println("\nPhase 2: Improvement");
        System.out.println("- Outputs processed in ascending value order");
        System.out.println("- Target ranges: minimum=v, ideal=2v, maximum=3v");
        System.out.println("- Random improvement attempts within limits");
    }
    
    public void demonstrateTargetRanges() {
        // For an output of value V:
        BigInteger outputValue = Amount.ada(10).getQuantity(); // 10 ADA
        
        BigInteger minimum = outputValue;                      // 10 ADA
        BigInteger ideal = outputValue.multiply(BigInteger.valueOf(2)); // 20 ADA
        BigInteger maximum = outputValue.multiply(BigInteger.valueOf(3)); // 30 ADA
        
        System.out.println("Output value: " + outputValue + " lovelace");
        System.out.println("Target minimum: " + minimum + " lovelace");
        System.out.println("Target ideal: " + ideal + " lovelace");
        System.out.println("Target maximum: " + maximum + " lovelace");
        
        // The improvement phase attempts to get closer to ideal
        // while staying under maximum and within input limits
    }
}
```

### Randomness and Security

```java
public class RandomImproveRandomness {
    
    public void demonstrateRandomnessHandling() {
        // RandomImproveUtxoSelectionStrategy uses SecureRandom
        // with SHA1PRNG algorithm for cryptographically secure randomness
        
        SecureRandom secureRandom = new SecureRandom();
        System.out.println("Random algorithm: " + secureRandom.getAlgorithm());
        
        // This ensures:
        // - Unpredictable UTXO selection
        // - Resistance to timing attacks
        // - Proper entropy for selection decisions
        
        // Custom seed can be provided for deterministic testing
        SecureRandom deterministicRandom = new SecureRandom();
        deterministicRandom.setSeed(12345L); // Fixed seed for testing
        
        // Note: In production, never use fixed seeds
        // Always rely on system entropy
    }
    
    public void demonstrateRandomSelectionBehavior() {
        UtxoSelectionStrategy strategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier);
        
        List<Amount> outputs = Arrays.asList(Amount.ada(5.0));
        
        // Multiple runs will produce different selections
        for (int i = 0; i < 3; i++) {
            Set<Utxo> selected = strategy.select(addressIterator, outputs);
            System.out.println("Run " + (i + 1) + ": " + selected.size() + " UTXOs selected");
            
            // Each run may select different UTXOs
            // This helps with UTXO set health over time
        }
    }
}
```

### Change Optimization Benefits

```java
public class ChangeOptimizationExample {
    
    public void demonstrateChangeOptimization() {
        // Compare RandomImprove with LargestFirst for change management
        UtxoSelectionStrategy randomImprove = new RandomImproveUtxoSelectionStrategy(utxoSupplier);
        UtxoSelectionStrategy largestFirst = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
        
        List<Amount> outputs = Arrays.asList(Amount.ada(3.5));
        
        // RandomImprove selection
        Set<Utxo> randomSelected = randomImprove.select(addressIterator, outputs);
        BigInteger randomChange = calculateChange(randomSelected, outputs);
        
        // LargestFirst selection
        Set<Utxo> largestSelected = largestFirst.select(addressIterator, outputs);
        BigInteger largestChange = calculateChange(largestSelected, outputs);
        
        System.out.println("RandomImprove change: " + randomChange + " lovelace");
        System.out.println("LargestFirst change: " + largestChange + " lovelace");
        
        // RandomImprove typically produces more balanced change outputs
        // This is better for long-term UTXO set health
        
        analyzeUTXOSetImpact(randomSelected, largestSelected);
    }
    
    private BigInteger calculateChange(Set<Utxo> selected, List<Amount> outputs) {
        BigInteger totalInput = selected.stream()
            .flatMap(utxo -> utxo.getAmount().stream())
            .filter(amount -> amount.getUnit().equals("lovelace"))
            .map(Amount::getQuantity)
            .reduce(BigInteger.ZERO, BigInteger::add);
        
        BigInteger totalOutput = outputs.stream()
            .filter(amount -> amount.getUnit().equals("lovelace"))
            .map(Amount::getQuantity)
            .reduce(BigInteger.ZERO, BigInteger::add);
        
        return totalInput.subtract(totalOutput);
    }
    
    private void analyzeUTXOSetImpact(Set<Utxo> randomSelected, Set<Utxo> largestSelected) {
        System.out.println("\nUTXO Set Impact Analysis:");
        System.out.println("RandomImprove inputs: " + randomSelected.size());
        System.out.println("LargestFirst inputs: " + largestSelected.size());
        
        // RandomImprove often uses more inputs but creates better change distribution
        // This helps prevent UTXO set fragmentation over time
        
        if (randomSelected.size() > largestSelected.size()) {
            System.out.println("RandomImprove uses more inputs but improves long-term UTXO distribution");
        }
    }
}
```

## ExcludeUtxoSelectionStrategy

This wrapper strategy allows excluding specific UTXOs from selection, useful for preventing double-spending in concurrent operations.

### Basic Usage

```java
import com.bloxbean.cardano.client.coinselection.impl.ExcludeUtxoSelectionStrategy;

public class ExcludeStrategyExample {
    
    public void demonstrateExclusion() {
        // Base strategy
        UtxoSelectionStrategy baseStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        
        // UTXOs to exclude (e.g., from pending transactions)
        Set<Utxo> utxosToExclude = getPendingTransactionUtxos();
        
        // Create excluding wrapper
        UtxoSelectionStrategy excludingStrategy = new ExcludeUtxoSelectionStrategy(
            baseStrategy, utxosToExclude);
        
        // Selection will avoid excluded UTXOs
        List<Amount> outputs = Arrays.asList(Amount.ada(10.0));
        Set<Utxo> selected = excludingStrategy.select(addressIterator, outputs);
        
        // Verify no excluded UTXOs were selected
        for (Utxo utxo : selected) {
            if (utxosToExclude.contains(utxo)) {
                System.err.println("ERROR: Excluded UTXO was selected!");
            }
        }
        
        System.out.println("Selection successful, " + utxosToExclude.size() + 
                          " UTXOs excluded");
    }
    
    private Set<Utxo> getPendingTransactionUtxos() {
        // In practice, this would come from your transaction management system
        return Set.of(
            // UTXOs from unconfirmed transactions
        );
    }
}
```

### Concurrent Transaction Management

```java
public class ConcurrentTransactionExample {
    
    private final Set<Utxo> reservedUtxos = ConcurrentHashMap.newKeySet();
    private final UtxoSelectionStrategy baseStrategy;
    
    public ConcurrentTransactionExample(UtxoSelectionStrategy baseStrategy) {
        this.baseStrategy = baseStrategy;
    }
    
    public synchronized TransactionBuilder createTransaction(List<Amount> outputs) {
        // Create strategy that excludes reserved UTXOs
        UtxoSelectionStrategy strategy = new ExcludeUtxoSelectionStrategy(
            baseStrategy, new HashSet<>(reservedUtxos));
        
        // Select UTXOs
        Set<Utxo> selected = strategy.select(addressIterator, outputs);
        
        if (selected.isEmpty()) {
            throw new IllegalStateException("Insufficient UTXOs available");
        }
        
        // Reserve selected UTXOs
        reservedUtxos.addAll(selected);
        
        // Create transaction builder
        return new TransactionBuilder(selected, outputs) {
            @Override
            public void onTransactionComplete() {
                // Release UTXOs when transaction is complete
                releaseUtxos(selected);
            }
            
            @Override
            public void onTransactionFailed() {
                // Release UTXOs if transaction fails
                releaseUtxos(selected);
            }
        };
    }
    
    private void releaseUtxos(Set<Utxo> utxos) {
        synchronized (this) {
            reservedUtxos.removeAll(utxos);
        }
        System.out.println("Released " + utxos.size() + " reserved UTXOs");
    }
}
```

## Custom Strategy Implementation

### Basic Custom Strategy

```java
public class CustomUtxoSelectionStrategy implements UtxoSelectionStrategy {
    
    private final UtxoSupplier utxoSupplier;
    private final UtxoSelectionStrategy fallbackStrategy;
    
    public CustomUtxoSelectionStrategy(UtxoSupplier utxoSupplier) {
        this.utxoSupplier = utxoSupplier;
        this.fallbackStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
    }
    
    @Override
    public Set<Utxo> select(AddressIterator addressIterator, 
                           List<Amount> outputAmounts, 
                           String datumHash, 
                           PlutusData inlineDatum, 
                           Set<Utxo> utxosToExclude, 
                           int maxUtxoSelectionLimit) {
        
        Set<Utxo> selectedUtxos = new HashSet<>();
        Map<String, BigInteger> remainingAmounts = groupAmountsByUnit(outputAmounts);
        
        // Custom algorithm: Prefer UTXOs with specific characteristics
        for (String address : addressIterator) {
            try {
                List<Utxo> availableUtxos = fetchAvailableUtxos(address, utxosToExclude);
                
                // Custom sorting logic
                List<Utxo> sortedUtxos = customSort(availableUtxos, remainingAmounts);
                
                for (Utxo utxo : sortedUtxos) {
                    if (selectedUtxos.size() >= maxUtxoSelectionLimit) {
                        break; // Respect input limit
                    }
                    
                    if (isUtxoUseful(utxo, remainingAmounts, datumHash, inlineDatum)) {
                        selectedUtxos.add(utxo);
                        updateRemainingAmounts(remainingAmounts, utxo);
                        
                        if (allRequirementsMet(remainingAmounts)) {
                            return selectedUtxos; // Success
                        }
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Error processing address " + address + ": " + e.getMessage());
                continue; // Try next address
            }
        }
        
        // If custom algorithm fails, try fallback
        System.out.println("Custom strategy failed, using fallback");
        return fallbackStrategy.select(addressIterator, outputAmounts, datumHash, 
                                     inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
    }
    
    @Override
    public UtxoSelectionStrategy fallback() {
        return fallbackStrategy;
    }
    
    private List<Utxo> customSort(List<Utxo> utxos, Map<String, BigInteger> requiredAmounts) {
        // Custom sorting logic - example: prefer UTXOs with fewer assets
        return utxos.stream()
            .sorted((u1, u2) -> {
                // Prefer UTXOs with fewer different asset types
                int assets1 = u1.getAmount().size();
                int assets2 = u2.getAmount().size();
                
                if (assets1 != assets2) {
                    return Integer.compare(assets1, assets2);
                }
                
                // If same number of assets, prefer larger values
                BigInteger value1 = getTotalLovelaceValue(u1);
                BigInteger value2 = getTotalLovelaceValue(u2);
                return value2.compareTo(value1);
            })
            .collect(Collectors.toList());
    }
    
    private boolean isUtxoUseful(Utxo utxo, Map<String, BigInteger> remainingAmounts, 
                                String datumHash, PlutusData inlineDatum) {
        // Check if UTXO contains any needed assets
        for (Amount amount : utxo.getAmount()) {
            if (remainingAmounts.containsKey(amount.getUnit()) && 
                remainingAmounts.get(amount.getUnit()).compareTo(BigInteger.ZERO) > 0) {
                
                // Check datum constraints if specified
                if (datumHash != null && !datumHash.equals(utxo.getDataHash())) {
                    continue;
                }
                
                if (inlineDatum != null && !inlineDatum.equals(utxo.getInlineDatum())) {
                    continue;
                }
                
                return true;
            }
        }
        return false;
    }
    
    // Helper methods
    private Map<String, BigInteger> groupAmountsByUnit(List<Amount> amounts) {
        return amounts.stream()
            .collect(Collectors.groupingBy(
                Amount::getUnit,
                Collectors.reducing(BigInteger.ZERO, 
                                  Amount::getQuantity, 
                                  BigInteger::add)
            ));
    }
    
    private List<Utxo> fetchAvailableUtxos(String address, Set<Utxo> excluded) {
        return utxoSupplier.getAll(address).stream()
            .filter(utxo -> !excluded.contains(utxo))
            .collect(Collectors.toList());
    }
    
    private void updateRemainingAmounts(Map<String, BigInteger> remaining, Utxo utxo) {
        for (Amount amount : utxo.getAmount()) {
            String unit = amount.getUnit();
            if (remaining.containsKey(unit)) {
                BigInteger newAmount = remaining.get(unit).subtract(amount.getQuantity());
                remaining.put(unit, newAmount.max(BigInteger.ZERO));
            }
        }
    }
    
    private boolean allRequirementsMet(Map<String, BigInteger> remaining) {
        return remaining.values().stream()
            .allMatch(amount -> amount.compareTo(BigInteger.ZERO) <= 0);
    }
    
    private BigInteger getTotalLovelaceValue(Utxo utxo) {
        return utxo.getAmount().stream()
            .filter(amount -> amount.getUnit().equals("lovelace"))
            .map(Amount::getQuantity)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
```

### Advanced Custom Strategy with Caching

```java
public class CachedUtxoSelectionStrategy implements UtxoSelectionStrategy {
    
    private final UtxoSupplier utxoSupplier;
    private final Cache<String, List<Utxo>> utxoCache;
    private final UtxoSelectionStrategy fallbackStrategy;
    
    public CachedUtxoSelectionStrategy(UtxoSupplier utxoSupplier) {
        this.utxoSupplier = utxoSupplier;
        this.utxoCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
        this.fallbackStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier);
    }
    
    @Override
    public Set<Utxo> select(AddressIterator addressIterator, 
                           List<Amount> outputAmounts, 
                           String datumHash, 
                           PlutusData inlineDatum, 
                           Set<Utxo> utxosToExclude, 
                           int maxUtxoSelectionLimit) {
        
        Set<Utxo> selectedUtxos = new HashSet<>();
        Map<String, BigInteger> remainingAmounts = groupAmountsByUnit(outputAmounts);
        
        for (String address : addressIterator) {
            try {
                // Use cached UTXOs if available
                List<Utxo> availableUtxos = utxoCache.get(address, 
                    k -> utxoSupplier.getAll(address));
                
                // Filter out excluded UTXOs
                List<Utxo> filteredUtxos = availableUtxos.stream()
                    .filter(utxo -> !utxosToExclude.contains(utxo))
                    .collect(Collectors.toList());
                
                // Apply selection algorithm
                selectFromUtxos(filteredUtxos, selectedUtxos, remainingAmounts, 
                              datumHash, inlineDatum, maxUtxoSelectionLimit);
                
                if (allRequirementsMet(remainingAmounts)) {
                    return selectedUtxos;
                }
                
            } catch (Exception e) {
                System.err.println("Cached selection failed for address " + address);
                // Remove from cache and continue
                utxoCache.invalidate(address);
            }
        }
        
        // Fallback if cached strategy fails
        return fallbackStrategy.select(addressIterator, outputAmounts, datumHash, 
                                     inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
    }
    
    private void selectFromUtxos(List<Utxo> utxos, Set<Utxo> selected, 
                                Map<String, BigInteger> remaining, String datumHash, 
                                PlutusData inlineDatum, int maxLimit) {
        
        // Priority-based selection: ADA first, then tokens
        List<Utxo> prioritizedUtxos = prioritizeUtxos(utxos, remaining);
        
        for (Utxo utxo : prioritizedUtxos) {
            if (selected.size() >= maxLimit) break;
            
            if (isUtxoNeeded(utxo, remaining, datumHash, inlineDatum)) {
                selected.add(utxo);
                updateRemainingAmounts(remaining, utxo);
                
                if (allRequirementsMet(remaining)) {
                    break;
                }
            }
        }
    }
    
    private List<Utxo> prioritizeUtxos(List<Utxo> utxos, Map<String, BigInteger> required) {
        return utxos.stream()
            .sorted((u1, u2) -> {
                // Calculate utility score for each UTXO
                double score1 = calculateUtilityScore(u1, required);
                double score2 = calculateUtilityScore(u2, required);
                return Double.compare(score2, score1); // Higher score first
            })
            .collect(Collectors.toList());
    }
    
    private double calculateUtilityScore(Utxo utxo, Map<String, BigInteger> required) {
        double score = 0.0;
        
        for (Amount amount : utxo.getAmount()) {
            String unit = amount.getUnit();
            if (required.containsKey(unit)) {
                BigInteger needed = required.get(unit);
                if (needed.compareTo(BigInteger.ZERO) > 0) {
                    // Score based on how much of the requirement this UTXO satisfies
                    double satisfaction = Math.min(1.0, 
                        amount.getQuantity().doubleValue() / needed.doubleValue());
                    score += satisfaction;
                }
            }
        }
        
        // Bonus for having fewer assets (less complex)
        score += 1.0 / Math.max(1, utxo.getAmount().size());
        
        return score;
    }
    
    @Override
    public UtxoSelectionStrategy fallback() {
        return fallbackStrategy;
    }
    
    // Helper methods (same as previous example)
    // ...
}
```

## QuickTx Integration

### Using Strategies with QuickTx

```java
public class QuickTxIntegrationExample {
    
    public void demonstrateQuickTxIntegration() {
        // Create custom strategy
        UtxoSelectionStrategy customStrategy = new RandomImproveUtxoSelectionStrategy(
            new DefaultUtxoSupplier(backendService.getUtxoService())
        );
        
        // Build transaction with custom strategy
        Tx paymentTx = new Tx()
            .payTo(receiverAddress, Amount.ada(10.5))
            .payTo(receiverAddress, Amount.asset("policy123", "token", 100))
            .from(senderAddress);
        
        try {
            Result<String> result = QuickTxBuilder.create(backendService)
                .compose(paymentTx)
                .withSigner(SignerProviders.signerFrom(senderAccount))
                .withUtxoSelectionStrategy(customStrategy)  // Use custom strategy
                .completeAndSubmit();
            
            if (result.isSuccessful()) {
                System.out.println("Transaction submitted: " + result.getValue());
            } else {
                System.err.println("Transaction failed: " + result.getResponse());
            }
            
        } catch (Exception e) {
            System.err.println("Transaction building failed: " + e.getMessage());
        }
    }
    
    public void demonstrateStrategyFallback() {
        // Create strategy with fallback chain
        UtxoSelectionStrategy primaryStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        UtxoSelectionStrategy fallbackStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
        
        // The QuickTx framework automatically handles fallback
        // If primary strategy fails, it will try the fallback
        
        Tx tx = new Tx()
            .payTo(receiverAddress, Amount.ada(5.0))
            .from(senderAddress);
        
        Result<String> result = QuickTxBuilder.create(backendService)
            .compose(tx)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .withUtxoSelectionStrategy(primaryStrategy)
            .completeAndSubmit();
        
        // If primary strategy fails (e.g., due to UTXO limits),
        // the framework automatically tries the fallback strategy
    }
}
```

### Global Strategy Configuration

```java
public class GlobalStrategyConfiguration {
    
    // Configure default strategy for all transactions
    public void configureGlobalStrategy() {
        // Set global coin selection configuration
        CoinselectionConfig.INSTANCE.setMaxUtxoSelectionLimit(50); // Increase limit
        
        // Create application-wide strategy
        UtxoSelectionStrategy appStrategy = new CustomUtxoSelectionStrategy(
            new DefaultUtxoSupplier(backendService.getUtxoService())
        );
        
        // Use in QuickTxBuilder context
        TxBuilderContext globalContext = TxBuilderContext.init(backendService)
            .withUtxoSelectionStrategy(appStrategy);
        
        // All transactions will use this strategy by default
        Tx tx1 = new Tx().payTo(address1, Amount.ada(5.0)).from(senderAddress);
        Tx tx2 = new Tx().payTo(address2, Amount.ada(3.0)).from(senderAddress);
        
        QuickTxBuilder builder = QuickTxBuilder.create(globalContext);
        
        // Both transactions use the global strategy
        Result<String> result1 = builder.compose(tx1)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .completeAndSubmit();
            
        Result<String> result2 = builder.compose(tx2)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .completeAndSubmit();
    }
}
```

## Performance and Optimization

### Benchmarking Strategies

```java
public class StrategyBenchmark {
    
    public void benchmarkStrategies() {
        List<UtxoSelectionStrategy> strategies = Arrays.asList(
            new DefaultUtxoSelectionStrategyImpl(utxoSupplier),
            new LargestFirstUtxoSelectionStrategy(utxoSupplier),
            new RandomImproveUtxoSelectionStrategy(utxoSupplier)
        );
        
        List<Amount> testOutputs = Arrays.asList(
            Amount.ada(5.0),
            Amount.asset("policy1", "token1", 100)
        );
        
        for (UtxoSelectionStrategy strategy : strategies) {
            benchmarkStrategy(strategy, testOutputs);
        }
    }
    
    private void benchmarkStrategy(UtxoSelectionStrategy strategy, List<Amount> outputs) {
        String strategyName = strategy.getClass().getSimpleName();
        
        long startTime = System.nanoTime();
        long memoryBefore = getUsedMemory();
        
        try {
            Set<Utxo> selected = strategy.select(addressIterator, outputs);
            
            long endTime = System.nanoTime();
            long memoryAfter = getUsedMemory();
            
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            long memoryUsed = memoryAfter - memoryBefore;
            
            System.out.println(String.format(
                "Strategy: %s, Time: %d ms, Memory: %d bytes, UTXOs: %d",
                strategyName, duration, memoryUsed, selected.size()
            ));
            
        } catch (Exception e) {
            System.err.println(strategyName + " failed: " + e.getMessage());
        }
    }
    
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
```

### Optimization Best Practices

```java
public class OptimizationBestPractices {
    
    // Choose strategy based on use case
    public UtxoSelectionStrategy chooseOptimalStrategy(TransactionContext context) {
        
        // High-frequency applications
        if (context.isHighFrequency()) {
            return new DefaultUtxoSelectionStrategyImpl(utxoSupplier); // Paginated, memory-efficient
        }
        
        // Large transactions
        if (context.isLargeValue()) {
            return new LargestFirstUtxoSelectionStrategy(utxoSupplier); // Minimal inputs
        }
        
        // Long-term UTXO health
        if (context.optimizeForLongTerm()) {
            return new RandomImproveUtxoSelectionStrategy(utxoSupplier); // Better change distribution
        }
        
        // Concurrent applications
        if (context.isConcurrent()) {
            Set<Utxo> excludeSet = context.getReservedUtxos();
            UtxoSelectionStrategy baseStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
            return new ExcludeUtxoSelectionStrategy(baseStrategy, excludeSet);
        }
        
        // Default fallback
        return new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
    }
    
    // Monitor and adjust limits
    public void monitorAndAdjustLimits() {
        // Monitor transaction sizes
        int currentLimit = CoinselectionConfig.INSTANCE.getMaxUtxoSelectionLimit();
        
        // Adjust based on network conditions and transaction patterns
        if (averageTransactionSize() > 15000) { // 15KB
            CoinselectionConfig.INSTANCE.setMaxUtxoSelectionLimit(Math.max(10, currentLimit - 5));
            System.out.println("Reduced UTXO limit to prevent large transactions");
        } else if (averageTransactionSize() < 5000) { // 5KB
            CoinselectionConfig.INSTANCE.setMaxUtxoSelectionLimit(Math.min(50, currentLimit + 5));
            System.out.println("Increased UTXO limit for better selection");
        }
    }
    
    private int averageTransactionSize() {
        // Implementation would track actual transaction sizes
        return 8000; // Mock value
    }
    
    // Cache frequently accessed UTXOs
    public UtxoSupplier createCachedSupplier(UtxoSupplier baseSupplier) {
        return new UtxoSupplier() {
            private final Cache<String, List<Utxo>> cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
            
            @Override
            public List<Utxo> getAll(String address) {
                return cache.get(address, k -> baseSupplier.getAll(address));
            }
            
            @Override
            public Page<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
                // For paginated access, don't cache individual pages
                return baseSupplier.getPage(address, nrOfItems, page, order);
            }
        };
    }
}
```

The coin selection module provides flexible, efficient, and customizable UTXO selection for all transaction building needs. Choose the appropriate strategy based on your application's requirements for performance, memory usage, and transaction optimization.