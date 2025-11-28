# Coin Selection API

The Coin Selection API provides strategies for selecting UTXOs when building transactions. It includes built-in algorithms and extension points for custom selection logic.

## Overview

- Multiple strategies: default, largest-first, random-improve
- Configurable limits and datum handling
- Inline datum and datum hash aware
- Fallback chaining for robustness
- UTXO discovery helpers via `UtxoSelector`

## Core Interfaces

### UtxoSelectionStrategy

```java
public interface UtxoSelectionStrategy {
    Set<Utxo> select(AddressIterator addressIterator,
                     List<Amount> outputAmounts,
                     String datumHash,
                     PlutusData inlineDatum,
                     Set<Utxo> utxosToExclude,
                     int maxUtxoSelectionLimit);

    UtxoSelectionStrategy fallback();
    void setIgnoreUtxosWithDatumHash(boolean ignore);
}
```

### UtxoSelector

Utility interface to query UTXOs by predicate:

```java
Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate);
List<Utxo> findAll(String address, Predicate<Utxo> predicate);
default void setSearchByAddressVkh(boolean flag) { }
```

## Built-in Selection Strategies

### Default

```java
UtxoSelectionStrategy strategy =
        new DefaultUtxoSelectionStrategyImpl(utxoSupplier);

strategy.setIgnoreUtxosWithDatumHash(true); // default true

Set<Utxo> selected = strategy.select(
        AddressIterators.of(senderAddress),
        List.of(Amount.ada(10)),
        null, // datumHash
        null, // inline datum
        Collections.emptySet(),
        20);
```

### Largest First

```java
UtxoSelectionStrategy strategy =
        new LargestFirstUtxoSelectionStrategy(utxoSupplier);

Set<Utxo> selected = strategy.select(
        AddressIterators.of(senderAddress),
        List.of(Amount.ada(5)),
        null, null,
        Collections.emptySet(),
        10);
```

### Random Improve

```java
UtxoSelectionStrategy strategy =
        new RandomImproveUtxoSelectionStrategy(utxoSupplier);
strategy.setIgnoreUtxosWithDatumHash(false);

Set<Utxo> selected = strategy.select(
        AddressIterators.of(senderAddress),
        List.of(Amount.ada(15)),
        null, null,
        Collections.emptySet(),
        15);
```

## Using UtxoSelector

```java
UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

List<Utxo> adaUtxos = utxoSelector.findAll(
        senderAddress,
        utxo -> utxo.containsUnit("lovelace"));

Optional<Utxo> firstScriptUtxo = utxoSelector.findFirst(
        scriptAddress,
        utxo -> utxo.getReferenceScriptHash() != null);
```

If your backend supports lookup by address verification hash (`addr_vkh`), enable it:

```java
utxoSelector.setSearchByAddressVkh(true);
utxoSupplier.setSearchByAddressVkh(true); // only if supported by your supplier/backend
```

## Advanced Scenarios

### Multi-Address Selection

```java
AddressIterator iterator = AddressIterators.of(List.of(addr1, addr2, addr3));

Set<Utxo> selected = strategy.select(
        iterator,
        List.of(Amount.ada(20)),
        null, null,
        Collections.emptySet(),
        30);
```

### Datum Requirements

```java
String requiredDatumHash = "...";
PlutusData requiredInlineDatum = PlutusData.of("hello");

Set<Utxo> withHash = strategy.select(
        AddressIterators.of(contractAddress),
        List.of(Amount.ada(5)),
        requiredDatumHash,
        null,
        Collections.emptySet(),
        10);

Set<Utxo> withInline = strategy.select(
        AddressIterators.of(contractAddress),
        List.of(Amount.ada(5)),
        null,
        requiredInlineDatum,
        Collections.emptySet(),
        10);
```

### Excluding Specific UTXOs

```java
Set<Utxo> exclusions = Set.of(pendingUtxo1, pendingUtxo2);

Set<Utxo> selected = strategy.select(
        AddressIterators.of(senderAddress),
        List.of(Amount.ada(10)),
        null, null,
        exclusions,
        20);
```

### Global Configuration

```java
CoinselectionConfig.INSTANCE.setCoinSelectionLimit(50);
int current = CoinselectionConfig.INSTANCE.getCoinSelectionLimit();
```

## Custom Strategy

```java
public class CustomUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private final UtxoSupplier utxoSupplier;
    private boolean ignoreUtxosWithDatumHash = true;

    // implement select(...) using your own ordering/filtering

    @Override
    public UtxoSelectionStrategy fallback() {
        return new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
    }

    @Override
    public void setIgnoreUtxosWithDatumHash(boolean ignore) {
        this.ignoreUtxosWithDatumHash = ignore;
    }
}
```

## QuickTx Integration

Provide a custom strategy per composition:

```java
UtxoSelectionStrategy custom = new LargestFirstUtxoSelectionStrategy(utxoSupplier);

Tx tx = new Tx()
        .payToAddress(receiverAddress, Amount.ada(10))
        .from(senderAddress);

TxResult result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withUtxoSelectionStrategy(custom)
        .withSigner(SignerProviders.signerFrom(sender))
        .complete();
```
