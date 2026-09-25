# UTxO Unfracking via Pre-Balance Transformer

**Status**: Proposed
**Date**: 2026-09-24
**Issue**: https://github.com/bloxbean/cardano-client-lib/issues/678 (related: https://github.com/bloxbean/cardano-client-lib/issues/42, https://github.com/bloxbean/cardano-client-lib/issues/279)
**Modules**: `quicktx`, `function`

## 1. Context

QuickTx always returns change as a **single output** at the sender's change address
(`InputBuilders.buildInputs` → one `ChangeOutput`; `FeeCalculators` deducts the fee from it;
`ChangeOutputAdjustments` tops it up to min-ada). Over time a wallet converges to one of two unhealthy shapes:

- **One "hot" UTxO** holding almost all ADA and every token. Every new transaction must spend it, and it stays
  unavailable until the previous transaction settles. A wallet can therefore only have **one transaction in flight**.
  Unfracking alone does not remove this limit; see the non-goal below.
- **Many dusty fragments**. Transactions need many inputs, which makes them larger and more expensive.

Additional costs of mixing everything into one UTxO:

- A plain ADA payment must move every token the wallet owns, which makes the transaction bigger and the fee higher.
- The change output grows with every received token, towards `maxValSize`, until it can become unspendable
  (issue #42).

In the Cardano ecosystem the fix is called **unfracking** (after [UnFrack.It](https://unfrack.it/)). The
[Evolution SDK](https://github.com/IntersectMBO/evolution-sdk) builds it into transaction building
(`BuildOptions.unfrack`, `packages/evolution/src/sdk/builders/Unfrack.ts`):

- tokens are bundled per policy;
- large ADA change is subdivided;
- this happens **during change creation of a normal transaction**, not in a separate consolidation transaction.

For CCL the motivation is **wallet hygiene** (issue #678):

- keep change outputs below `maxValSize`, so they stay spendable (issue #42);
- ADA payments and single-token transfers move only the UTxOs they need, not every token in the wallet;
- keep ADA-only UTxOs available, e.g. for collateral.

Unfracking **does not solve UTxO contention**. Several UTxOs are a prerequisite for concurrent transactions, but
choosing uncontended inputs still needs coordination (e.g. txflow) or a dedicated selection strategy (§9). Evolution
SDK does not attempt this either.

## 2. Decision

1. Reuse the existing **`preBalanceTx(TxBuilder)`** hook. Make it **chain** transformers (`andThen`) instead of
   overwriting the previous one, so unfracking can be combined with other pre-balance transformers. When `Unfrack`
   is not added, transaction building is unchanged, byte for byte.
2. Provide **`Unfrack`**, a `TxBuilder` that delegates the split to a pluggable **`ChangeSplitStrategy`**
   (strategy pattern, like `UtxoSelectionStrategy` for coin selection). The default, **`EvolutionStrategy`**, is a
   faithful port of Evolution SDK's `createUnfrackedChangeOutputs`, for parity and as a basis for a possible CIP.
   Alternative strategies are included so that algorithms can be compared instead of copied blindly (§4.4).
3. Unfrack **in the same transaction** by reshaping change outputs. There is no separate consolidation transaction
   (unlike UnFrack.It).
4. Run `Unfrack` **before** fee/min-ada balancing. All existing balancing logic stays as it is and is reused
   (see §5).

```java
quickTxBuilder.compose(tx)
    .feePayer(sender)
    .preBalanceTx(new Unfrack())                          // EvolutionStrategy
    // .preBalanceTx(new Unfrack(new EqualLanesStrategy())) // or any other ChangeSplitStrategy
    .withSigner(signer)
    .completeAndWait();
```

## 3. API Changes

### `function` module — `com.bloxbean.cardano.client.function.balance.unfrack`

| Class | Role |
|---|---|
| `Unfrack` | `TxBuilder`. Finds change outputs, calls the strategy, verifies its result, applies the fee reserve and output order (§4.2) |
| `ChangeSplitStrategy` | Strategy interface: `List<Value> split(ChangeSplitRequest)` |
| `ChangeSplitRequest` | Change address, change value (minus fee reserve), protocol params, the transaction (read-only) and the `UtxoSupplier`; min-ada helpers |
| `EvolutionStrategy` | Default. Port of Evolution SDK (§4.1) |
| `AbstractChangeSplitStrategy` | Base for strategies that keep ADA apart from tokens and only differ in how ADA is split (§4.4) |
| `PercentageSplitStrategy`, `EqualLanesStrategy`, `PaymentSizedStrategy`, `TargetShapeStrategy` | Alternative strategies (§4.4) |
| `TokenBundlingStrategy` | How tokens are grouped into outputs: `PolicyBundling` (per policy, by count) or `ByteBudgetBundling` (by CBOR size) |

A strategy only returns values. `Unfrack` checks that they sum to the change and that each meets min-ada, and
throws `IllegalStateException` otherwise, so a faulty third-party strategy cannot create an invalid transaction.

### `quicktx` module

`QuickTxBuilder.TxContext#preBalanceTx(TxBuilder)` appends the function to the existing pre-balance transformer
(`andThen`) instead of replacing it. Functions run in the order they were added. No new QuickTx method is added.

`Unfrack` lives in `function`, not `quicktx`, so it can also be composed manually with the low-level `TxBuilder`
API.

## 4. Algorithm

### 4.1 Default strategy: `EvolutionStrategy` (port of Evolution SDK)

The input is the change value `V` at address `A`. The output is a list of values that sums exactly to `V`, where
every value meets min-ada at `A`. A single-element result means "do not split".

1. **Leftover is ADA only**
   - If `ada < subdivideThreshold`, return one output.
   - Otherwise, if the smallest percentage slice is at least the ADA-only min-ada, split ADA by
     `subdividePercentages`. The last slice takes the rounding remainder. If not, return one output.
2. **Leftover has tokens**
   - Group the tokens by policy and chunk each policy into bundles of at most `bundleSize` assets.
   - Give each bundle its min-ada (CBOR-based, `MinAdaCalculator`).
   - Compute `remaining = ada − Σ bundle min-ada`. If it is negative, return one output.
   - If `remaining ≥ subdivideThreshold` and `remaining ≥` the ADA-only min-ada, return the bundles plus the ADA,
     subdivided as in step 1 when that is affordable, or as a single ADA output when it isn't.
   - Otherwise **spread** `remaining` evenly across the bundles; the last bundle takes the remainder.

### 4.2 CCL-specific adaptation: fee reserve and output ordering

Evolution recomputes the fee in its own phase loop. CCL's fee calculator instead deducts the fee from the
**max-coin output at the fee payer address**, and `ChangeOutputAdjustments` fails if **more than one** change
output ends up below min-ada. To fit both without changing them:

- `Unfrack` plans on `change − feeReserve` and adds the reserve back to the **largest** piece. That piece carries
  the fee, and all other pieces still meet min-ada after balancing.
- The fee-bearing piece replaces the original change output **at the same index**, and the remaining pieces are
  **appended**. The indexes of all other outputs are therefore unchanged.
- The strategy result is verified: Σ pieces must equal the change and each piece must meet min-ada, otherwise it
  throws.

**Open question:** the default reserve size. A fixed 2 ADA prevents splitting small change that Evolution does
split. See the comparison and options in §11.6 (F1).

### 4.3 Configuration

`Unfrack(strategy, feeReserve)`: `feeReserve` defaults to 2 ADA (CCL-specific, §4.2). `EvolutionStrategy.builder()`:

| Parameter | Default | Source |
|---|---|---|
| `subdivideThreshold` | 100 ADA | Evolution |
| `subdividePercentages` | 50, 15, 10, 10, 5, 5, 5 | Evolution / UnFrack.It |
| `bundleSize` | 10 | Evolution (UnFrack.It uses 30) |

Evolution also declares `isolateFungibles` and `groupNftsByPolicy`, but its change-creation path
(`createUnfrackedChangeOutputs`) never reads them, so they are intentionally omitted.

### 4.4 Alternative strategies

The Evolution algorithm has known weaknesses:

- below `subdivideThreshold` it **spreads** the ADA over the token bundles, so a later ADA payment has to spend a
  token UTxO;
- it creates **one output per policy**, however small (150 airdropped policies = 150 outputs);
- `bundleSize` counts tokens, not bytes;
- it ignores the wallet and **re-splits on every transaction**, so the UTxO count keeps growing;
- the percentage split is relative to whatever the change is, and has a cliff at the threshold (99 ADA → 1 output,
  100 ADA → 7).

The alternatives extend `AbstractChangeSplitStrategy`. It bundles tokens with a `TokenBundlingStrategy` (default
`ByteBudgetBundling`, 1,000 bytes: first-fit decreasing, small policies share an output, large policies are chunked),
gives each bundle exactly its min-ada, and **always keeps the remaining ADA in ADA-only outputs** when it covers
min-ada. Only the ADA split differs:

| Strategy | ADA split | Intended for | Defaults |
|---|---|---|---|
| `PercentageSplitStrategy` | Evolution percentages above the threshold, otherwise one output | Evolution behaviour without the spread and per-policy issues | 100 ADA, 50/15/10/10/5/5/5 |
| `EqualLanesStrategy` | Up to N equal lanes, each at least `minLaneAmount` | Bots and services paying similar amounts | 5 lanes, 10 ADA |
| `PaymentSizedStrategy` | One piece per payment (payment + fee allowance + min-ada), largest first, plus the remainder ([CIP-2](https://cips.cardano.org/cip/CIP-0002) "self-organisation") | Wallets whose UTxOs should drift towards their typical payment sizes | max 5 pieces, 0.5 ADA fee allowance |
| `TargetShapeStrategy` | Only the lanes still missing to reach `targetLanes` ADA-only UTxOs of `laneAmount` (reads the wallet via `UtxoSupplier`, ignores UTxOs spent by this transaction) | Keeping a stable wallet shape; stops the UTxO count from growing | 5 lanes, 10 ADA |

These strategies are **experimental**. §12 analyses each one with worked examples and a first simulation, and
gives a recommendation.

## 5. Build Pipeline Placement

```
per-tx complete()        inputs selected, one ChangeOutput per sender, deposits resolved
preBalanceTx(...)        existing, now chained (← CHANGED): user/extender transformers, then Unfrack splits ChangeOutputs
collateral               existing
script cost evaluation   existing; sees the final output set
balanceTx(feePayer)      existing: FeeCalculators → ChangeOutputAdjustments → collateral balance
postBalanceTx(...)       existing
```

`Unfrack` runs before balancing because:

- at that point each `ChangeOutput` still holds the full surplus, so no fee has to be restored;
- script cost evaluation runs afterwards, so validators that inspect outputs are evaluated against the final
  outputs;
- fee calculation naturally includes the size of the extra outputs.

## 6. Rules and Edge Cases

- Only outputs that are `instanceof ChangeOutput`, have a positive coin, and carry **no** datum, datum hash or
  script ref are split. User payment outputs are never touched.
- Change at or below `feeReserve`, and change the strategy cannot afford to split, is left unchanged.
- **Fee payer ≠ sender**: the sender's change is split, and the fee is still taken from the fee payer's output.
- **Balancing adds inputs later** (min-ada top-up): the extra value merges into the largest piece. This is
  acceptable.
- **`mergeOutputs(true)`**: change may be merged into a user output, which is not a `ChangeOutput`, so `Unfrack`
  does nothing.
- **Transactions without inputs** (withdrawal/deregistration funded by a refund) have no `ChangeOutput` yet, so
  `Unfrack` does nothing.
- **Other pre-balance transformers**: they run in the order they were added. `Unfrack` only touches
  `ChangeOutput`s, so the order only matters if another transformer also changes change outputs.
- NFT vs fungible detection is not needed for the ported path; tokens are bundled by policy only.

## 7. Alternatives Considered

### Separate consolidation transaction (UnFrack.It style)
This requires an extra transaction, an extra fee and a wait for confirmation before the benefit appears, and it
doesn't fit the QuickTx one-shot model. It was rejected in favour of splitting change inside every opted-in
transaction.

### Replace `ScriptBalanceTxProviders.balanceTx` with a pluggable balancer
This would duplicate fee calculation, min-ada adjustment, script re-evaluation and collateral balancing, and every
balancer would have to reimplement them. It was rejected; the pre-balance approach is purely additive.

### Split change after balancing (`postBalanceTx`)
No fee recalculation happens after this point. `Unfrack` would have to restore the fee, re-run
`FeeCalculators`/`ChangeOutputAdjustments` and re-evaluate scripts itself. It was rejected as fragile.

### Implement unfracking as a `UtxoSelectionStrategy`
Selection strategies only choose inputs and have no say in the shape of the change. They are the wrong layer.

### A dedicated `TxBalancer` hook (`TxContext.balancer(...)`)
The first spike added a new interface and QuickTx method that ran right after `preBalanceTx`. That is the same
position in the pipeline, so it only added API surface. It also had a misleading name, because it reshapes change
and does not balance anything. It was rejected in favour of chaining `preBalanceTx`.

### Hard-code one algorithm (the Evolution port)
Simple, but Evolution's algorithm has known weaknesses (§4.4) and there is no single right shape for every wallet.
A strategy interface lets us compare and change algorithms without API changes, the same way coin selection uses
`UtxoSelectionStrategy`. It was rejected in favour of `ChangeSplitStrategy`.

### Keep `preBalanceTx` as a single, overwriting slot
`Unfrack` would then collide with other pre-balance transformers. `MintValidatorExtender` already sets one
internally (to remove an inline script when a reference script is used), and a user's own `preBalanceTx(...)`
silently replaces it today. Chaining fixes that bug too.

## 8. Consequences

**Positive**
- Change outputs stay below `maxValSize` and remain spendable (issue #42).
- ADA payments no longer drag every token along.
- The wallet has several independent UTxOs, a prerequisite (not a solution) for concurrent transactions.
- The change is opt-in and adds no new QuickTx API; the default path must stay unchanged (existing
  `function`/`quicktx` tests act as the guard).
- `preBalanceTx` chaining fixes the lost `MintValidatorExtender` transformer described in §7.
- The algorithm matches Evolution SDK, so behaviour is predictable across TypeScript and Java stacks.

**Negative / trade-offs**
- More outputs per transaction make that transaction slightly larger and its fee slightly higher. This one-time
  cost is intended.
- The Evolution percentage split targets **spending flexibility**, not concurrency (§9).
- More UTxOs increase wallet scan and selection work.
- **Behaviour change**: calling `preBalanceTx` twice now runs both functions instead of only the last one. Code that
  relied on replacing an earlier transformer must be adjusted. No usage in this repository does this.
- Unfracking is less discoverable without a dedicated method; Javadoc and docs examples have to cover it.

## 9. Future Work

- **Simulator extensions**: the committed simulator (§12.8) uses largest-first selection, a fixed fee and serial
  transactions. Next: other coin selection strategies (random-improve), a size-based fee, NFT sends, and concurrent
  submission with in-flight UTxOs.
- **Consolidation**: merge dust UTxOs (Evolution's `maxUtxosToConsolidate` is not implemented either).
  `TargetShapeStrategy` only stops the growth.
- **Fallback instead of failure**: if validation after balancing fails, rebuild with a single change output.
- **Size limits**: check `maxValSize` per bundle and `maxTxSize` for the whole transaction (issue #42).
- **TxPlan / YAML**: an `unfrack` field so that YAML plans can opt in.
- **txflow / TxStream integration**: make unfracked change usable as intra-address lanes, complementing today's
  address-based `LanePolicy`.

## 10. Test Plan

- **One unit test class per strategy and token bundling** (no transaction context): thresholds and boundaries,
  rounding remainders, unaffordable splits, token handling, strategy-specific behaviour (e.g. payments for
  `PaymentSizedStrategy`, existing and spent UTxOs for `TargetShapeStrategy`), config validation.
- **Contract test** for every strategy on a few hundred generated change values: Σ pieces equals the input, every
  piece meets min-ada, no policy repeated in one output, input not modified, deterministic; and the generated
  inputs must actually cause splits.
- **Wallet simulation** (`UnfrackSimulationTest`): replays the §12.8 workloads for every strategy, prints the report
  tables and asserts the properties this ADR relies on (conservation of ADA and tokens, `TargetShapeStrategy` stays
  at its target, per-transaction strategies grow, no token churn after unfracking a hot UTxO, byte-budget bundling
  locks less ADA, Evolution's spread in small wallets, `PaymentSizedStrategy` pieces fund a payment alone).
- **`Unfrack` unit tests** (on a `Transaction`):
  - in-place split and index preservation, several change outputs;
  - small change left untouched;
  - non-change outputs and change with a datum, datum hash or script ref left untouched;
  - the strategy receives change minus the reserve, the transaction and the `UtxoSupplier`;
  - custom and zero fee reserve;
  - invalid strategy results (value mismatch, dropped token, piece below min-ada, empty) are rejected.
- **QuickTx tests** (mocked `UtxoSupplier`/`ProtocolParamsSupplier`): the same transaction built with and without
  `.preBalanceTx(new Unfrack())`, checking output count, min-ada, inputs = outputs + fee, and token conservation. Without the
  `Unfrack`, the output must be identical to today's. A non-default strategy end to end. Two `preBalanceTx(...)` calls
  must both be applied.
- **Parity:** port selected Evolution SDK scenarios (`Unfrack.test.ts`, `TxBuilder.UnfrackChangeHandling.test.ts`)
  so that CCL produces the same split for the same input.
- **Integration** (Yaci DevKit): submit an unfracking transaction and check that the resulting UTxOs are on-chain
  and spendable.

## 11. Examples: Evolution SDK and CCL

The TypeScript snippets below are taken from Evolution SDK
([`0167cf9`](https://github.com/IntersectMBO/evolution-sdk/tree/0167cf91381eea2c2f33db5ab1396a66b4dbe2da)): its docs,
and its builder tests, which are the only runnable unfrack examples it ships (its `examples/` folder has none). Each
is followed by the proposed CCL equivalent.

The "CCL result" rows come from running the same wallets through the prototype `Unfrack` (`EvolutionStrategy`) with QuickTx and mocked
suppliers. Both runs use the same protocol parameters: `minFeeA` 44, `minFeeB` 155,381, `coinsPerUtxoByte` 4,310.
Evolution results are the values asserted in its tests.

### 11.1 Minimal usage

Evolution SDK ([`docs/.../advanced/performance.mdx`](https://github.com/IntersectMBO/evolution-sdk/blob/0167cf91381eea2c2f33db5ab1396a66b4dbe2da/docs/content/docs/advanced/performance.mdx)):

```typescript
const tx = await client
  .newTx()
  .payToAddress({
    address: Address.fromBech32("addr_test1..."),
    assets: Assets.fromLovelace(2_000_000n)
  })
  .build({
    unfrack: {
      tokens: { /* token bundling options */ },
      ada: { /* ADA consolidation/subdivision options */ }
    }
  })
```

CCL:

```java
Result<String> result = quickTxBuilder
        .compose(new Tx()
                .payToAddress(receiver, Amount.ada(2))
                .from(sender))
        .preBalanceTx(new Unfrack())                   // EvolutionStrategy with Evolution defaults
        .withSigner(SignerProviders.signerFrom(account))
        .complete();
```

### 11.2 ADA-only change, custom subdivision

Evolution SDK ([`TxBuilder.UnfrackDrain.test.ts`](https://github.com/IntersectMBO/evolution-sdk/blob/0167cf91381eea2c2f33db5ab1396a66b4dbe2da/packages/evolution/test/TxBuilder.UnfrackDrain.test.ts),
"should drain and consolidate multiple ADA-only UTxOs with subdivision"). The wallet has UTxOs of 200 ADA and
150 ADA, and pays 1 ADA:

```typescript
const signBuilder = await makeTxBuilder({ chain: mainnet })
  .payToAddress({
    address: CoreAddress.fromBech32(DESTINATION_ADDRESS),
    assets: CoreAssets.fromLovelace(1_000_000n)
  })
  .build({
    changeAddress: CoreAddress.fromBech32(SOURCE_ADDRESS),
    availableUtxos: utxos,
    drainTo: 0,                                  // fallback only; not triggered here
    protocolParameters: PROTOCOL_PARAMS,
    unfrack: {
      ada: {
        subdivideThreshold: 100_000_000n,        // 100 ADA
        subdividePercentages: [50, 30, 20]
      }
    }
  })
```

CCL:

```java
EvolutionStrategy strategy = EvolutionStrategy.builder()
        .subdivideThreshold(adaToLovelace(100))
        .subdividePercentages(List.of(50, 30, 20))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx().payToAddress(destination, Amount.ada(1)).from(source))
        .preBalanceTx(new Unfrack(strategy))
        .build();
```

| | Inputs | Outputs | Fee | Change outputs (lovelace) |
|---|---|---|---|---|
| Evolution | 1 | 4 | 173,861 | 99,413,069 · 59,647,841 · 39,765,229 |
| CCL (`feeReserve` 2 ADA) | 1 | 4 | 173,861 | 100,326,139 · 59,100,000 · 39,400,000 |

The output count and fee are identical. The amounts differ because Evolution splits the change **after** the fee,
while CCL splits `change − feeReserve` **before** balancing and the fee is then deducted from the largest piece
(§4.2).

### 11.3 Tokens: bundles plus a separate ADA output

Evolution SDK ([`TxBuilder.UnfrackChangeHandling.test.ts`](https://github.com/IntersectMBO/evolution-sdk/blob/0167cf91381eea2c2f33db5ab1396a66b4dbe2da/packages/evolution/test/TxBuilder.UnfrackChangeHandling.test.ts),
"should create separate ADA output when remaining above subdivideThreshold"). A single UTxO holds 7 ADA and three
tokens from three policies, and pays 2 ADA:

```typescript
const signBuilder = await makeTxBuilder({ chain: mainnet })
  .collectFrom({ inputs: [initialUtxo] })        // 7 ADA + TOKEN1(A) + TOKEN2(B) + TOKEN3(C)
  .payToAddress({
    address: CoreAddress.fromBech32(DESTINATION_ADDRESS),
    assets: CoreAssets.fromLovelace(2_000_000n)
  })
  .build({
    changeAddress: CoreAddress.fromBech32(CHANGE_ADDRESS),
    availableUtxos: [],
    protocolParameters: PROTOCOL_PARAMS,
    unfrack: { ada: { subdivideThreshold: 500_000n, subdividePercentages: [50, 30, 20] } }
  })
// expect: 1 payment + 4 change (3 token bundles + 1 ADA output)
```

CCL:

```java
EvolutionStrategy strategy = EvolutionStrategy.builder()
        .subdivideThreshold(BigInteger.valueOf(500_000))
        .subdividePercentages(List.of(50, 30, 20))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx().payToAddress(destination, Amount.lovelace(BigInteger.valueOf(2_000_000))).from(source))
        .preBalanceTx(new Unfrack(strategy, BigInteger.valueOf(300_000)))   // fee reserve, see finding F1
        .build();
```

| | Outputs | Change |
|---|---|---|
| Evolution | 5 | 3 token bundles + 1 ADA output |
| CCL, `feeReserve` 2 ADA (default) | 2 | **not split**: 1 change output with all tokens |
| CCL, `feeReserve` 0.3 ADA | 5 | 1 ADA (1,361,071) + 3 token bundles (~1.15 ADA each) |

### 11.4 Tokens: remaining ADA spread across bundles

Evolution SDK (same file, "should spread remaining lovelace across token bundles when below subdivideThreshold").
The UTxO holds 5 ADA and the same three tokens, and pays 1.2 ADA. The expected result is 1 payment + 3 change,
with no separate ADA output.

The CCL call is the same as §11.3 with a payment of `1_200_000` lovelace.

| | Outputs | Change |
|---|---|---|
| Evolution | 4 | 3 token bundles with ADA spread |
| CCL, `feeReserve` 2 ADA (default) | 2 | **not split** |
| CCL, `feeReserve` 0.3 ADA | 4 | 3 token bundles: 1,290,091 (fee-bearing) · 1,165,230 · 1,165,230 |

### 11.5 Wallet clean-up: consolidate a fragmented wallet

Evolution SDK ([`TxBuilder.UnfrackDrain.test.ts`](https://github.com/IntersectMBO/evolution-sdk/blob/0167cf91381eea2c2f33db5ab1396a66b4dbe2da/packages/evolution/test/TxBuilder.UnfrackDrain.test.ts),
"should optimize wallet before major transaction (cleanup)"). The fragmented wallet has 6 UTxOs:

- 150 ADA + HOSKY + NFT001
- 50 ADA
- 10 ADA + SNEK + SUNDAE + NFT002 + NFT003
- 300 ADA
- 5 ADA + HOSKY + CNFT001 + CNFT002
- 25 ADA

It spends all of them with a minimal payment:

```typescript
const signBuilder = await makeTxBuilder({ chain: mainnet })
  .collectFrom({ inputs: utxos })                // every fragment
  .payToAddress({
    address: CoreAddress.fromBech32(DESTINATION_ADDRESS),
    assets: CoreAssets.fromLovelace(1_000_000n)
  })
  .build({
    changeAddress: CoreAddress.fromBech32(SOURCE_ADDRESS),
    availableUtxos: utxos,
    drainTo: 0,
    protocolParameters: PROTOCOL_PARAMS,
    unfrack: {
      tokens: { bundleSize: 10, isolateFungibles: true, groupNftsByPolicy: true },
      ada: { subdivideThreshold: 50_000_000n, subdividePercentages: [50, 25, 15, 10] }
    }
  })
```

CCL (there is no `drainTo`; spending every UTxO is expressed with `collectFrom`):

```java
List<Utxo> fragments = utxoSupplier.getAll(source);

EvolutionStrategy strategy = EvolutionStrategy.builder()
        .bundleSize(10)
        .subdivideThreshold(adaToLovelace(50))
        .subdividePercentages(List.of(50, 25, 15, 10))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx()
                .collectFrom(fragments)
                .payToAddress(destination, Amount.ada(1))
                .from(source))
        .preBalanceTx(new Unfrack(strategy))
        .build();
```

| | Inputs | Outputs | Fee | Change |
|---|---|---|---|---|
| Evolution | 6 | 9 | 205,189 | 4 token bundles + 4 ADA outputs |
| CCL (`feeReserve` 2 ADA) | 6 | 9 | 205,189 | 4 token bundles (A: HOSKY+SNEK, B: SUNDAE, C: 3 NFTs, D: 2 NFTs) + 4 ADA outputs (267.9 · 133.1 · 79.8 · 53.2 ADA) |

This is full parity: the same inputs, outputs, fee and bundle layout. Note that bundles are **by policy**:
`isolateFungibles`/`groupNftsByPolicy` have no effect in Evolution (§4.3). HOSKY and SNEK share policy A, so they
share a UTxO.

### 11.6 Findings from the comparison

- **F1: A fixed 2 ADA `feeReserve` blocks unfracking for small change.** In §11.3 and §11.4 the reserve eats the ADA
  the bundles need, so CCL falls back to a single output where Evolution splits. With a 0.3 ADA reserve the output
  counts match Evolution. Options for review:
  - (a) lower the default, e.g. 0.5 ADA;
  - (b) derive the reserve from protocol parameters, e.g. an estimated fee for the expected output count;
  - (c) apply the reserve only when the change address is the fee payer;
  - (d) after balancing, recompute the split on the post-fee amount (closest to Evolution, more complex).
- **F2: Different amounts, same structure.** Output count, fee and bundle layout match. ADA slice amounts differ
  slightly because of where the fee is taken (§11.2). If exact numeric parity matters, option (d) above is required.
- **F3: Output order differs.** Evolution emits bundles first, then ADA slices. CCL keeps the fee-bearing (largest)
  piece at the original change index and appends the rest. Both are valid; CCL's order keeps the indexes of existing
  outputs stable.
- **F4: `drainTo` / `onInsufficientChange: "burn"`.** These Evolution fallbacks have no CCL counterpart. Clean-up
  (§11.5) works without them via `collectFrom`, so they are out of scope for this ADR.

## 12. Strategy Analysis

This section explains every strategy in detail, so that the Javadoc can stay short.

**How to read the examples.** They use mainnet `coinsPerUtxoByte` 4,310 and a Shelley base address. With these,
an ADA-only output needs 0.969750 ADA, a bundle with one token needs 1.146460 ADA, and a bundle with three
single-token policies needs 1.482640 ADA. "Change" is the value a strategy receives, i.e. already without the fee
reserve (§4.2); `Unfrack` later adds the reserve to the largest piece. All numbers were produced by running the
strategies, not calculated by hand.

### 12.1 `EvolutionStrategy` (default, port of Evolution SDK)

**Idea.** Separate tokens from ADA, and cut large ADA into a fixed "logarithmic" set of sizes: one big piece for
large payments, some medium ones and several small ones.

**Algorithm.** See §4.1. In short:

1. No tokens: below 100 ADA one output; otherwise 50/15/10/10/5/5/5 %, if the 5 % slice covers min-ada.
2. Tokens: one bundle per policy (chunks of 10 assets), each with its min-ada. The remaining ADA becomes separate
   ADA outputs only if it is at least 100 ADA; below that it is **spread** evenly over the token bundles.

**Examples** (the transaction pays 10 ADA).

| Change | Result |
|---|---|
| 1,000 ADA | 7 outputs: 500 · 150 · 100 · 100 · 50 · 50 · 50 ADA |
| 60 ADA | 1 output (below the threshold) |
| 60 ADA + 3 tokens of 3 policies | 3 outputs of **20 ADA + 1 token** each, no ADA-only output |
| 1,000 ADA + 3 tokens of 3 policies | 10 outputs: 3 bundles of 1.146460 ADA + 498.28 · 149.48 · 99.66 · 99.66 · 49.83 · 49.83 · 49.83 ADA |
| 300 ADA + 150 single-token policies | 157 outputs, **171.97 ADA locked** as min-ada of token bundles |

**Strengths.**

- Parity with Evolution SDK; the same wallet shape across TypeScript and Java.
- Simple and well understood; a reasonable basis for a CIP discussion.
- Creates many UTxOs that can fund a payment alone (high concurrency *potential*, §12.8).

**Weaknesses.**

- **Spread**: with less than 100 ADA left, spendable ADA is stored next to tokens (60 ADA + 3 tokens → 3 × 20 ADA with
  a token each). A later ADA payment has to spend a token UTxO and carry the token along.
- **One output per policy**: 150 airdropped policies give 157 outputs and lock 172 ADA, and such a transaction can
  exceed `maxTxSize`.
- **Unbounded growth**: every transaction with 100+ ADA change adds up to 6 UTxOs; largest-first selection then
  spends the 50 % slice and splits its change again. In §12.8 a single 10,000 ADA UTxO became 361 UTxOs after
  300 payments.
- **Cliff** at the threshold (99 ADA → 1 output, 100 ADA → 7), and the slices are relative to whatever the change
  is, not to how the wallet spends.

**Use it for** parity with Evolution SDK, and as the reference in comparisons.

### 12.2 `PercentageSplitStrategy`

**Idea.** Evolution's ADA split, without its token problems.

**Algorithm.** Tokens are bundled by `ByteBudgetBundling` (§12.6), each bundle gets exactly its min-ada, and the
remaining ADA always goes to ADA-only outputs (only an amount below the ADA-only min-ada is added to the last bundle).
That ADA is split by Evolution's percentages above the threshold and kept as one output below it.

**Examples.**

| Change | Result |
|---|---|
| 1,000 ADA | same as Evolution: 500 · 150 · 100 · 100 · 50 · 50 · 50 ADA |
| 60 ADA + 3 tokens of 3 policies | 2 outputs: **1 bundle** (3 policies, 1.482640 ADA) + **58.517360 ADA** ADA-only |
| 1,000 ADA + 3 tokens of 3 policies | 8 outputs: 1 bundle (1.482640 ADA) + 499.26 · 149.78 · 99.85 · 99.85 · 49.93 · 49.93 · 49.93 ADA |
| 300 ADA + 150 single-token policies | 14 outputs: 7 bundles of 12–23 policies + 7 ADA slices, **32.06 ADA locked** |

**Strengths.**

- Fixes the spread and per-policy problems: ADA payments never need a token UTxO, and 150 policies cost
  7 bundles and 32 ADA instead of 150 bundles and 172 ADA.
- Behaves exactly like Evolution for ADA-only change.

**Weaknesses.**

- The same unbounded growth and cliff as Evolution for ADA (identical results in §12.8).
- Bundles now mix policies. That is fine for payments, but a DEX or marketplace that wants one policy per UTxO would
  use `PolicyBundling` instead.

**Use it for** a drop-in improvement over Evolution when wallets hold many tokens.

### 12.3 `EqualLanesStrategy`

**Idea.** Split ADA into N equal "lanes", so that several independent UTxOs of a useful size exist.

**Algorithm.** `lanes = min(N, ADA / max(minLaneAmount, min-ada))`. If that is at most 1, one output; otherwise
equal lanes, with the rounding remainder on the last one. Tokens are handled as in §12.2.

**Examples** (defaults: 5 lanes, at least 10 ADA each).

| Change | Result |
|---|---|
| 1,000 ADA | 5 × 200 ADA |
| 60 ADA | 5 × 12 ADA |
| 35 ADA | 3 lanes: 11.666666 · 11.666666 · 11.666668 ADA |
| 9 ADA | 1 output (a lane would be below 10 ADA) |
| 60 ADA + 3 tokens of 3 policies | 1 bundle (1.482640 ADA) + 5 × 11.703472 ADA |

**Strengths.**

- Predictable, equal-sized UTxOs; easy to reason about for bots and services with similar payments.
- The most UTxOs able to fund a payment alone (§12.8).

**Weaknesses.**

- The **worst growth**: every transaction re-splits its change into 5 lanes. With 10 ADA lanes a single
  10,000 ADA UTxO became 625 UTxOs after 300 payments. A larger `minLaneAmount` (60 ADA) reduces this to 125.
- Needs tuning: lanes smaller than the typical payment can't fund it alone, and average inputs per transaction
  go up (1.73 for random 1–50 ADA payments with 10 ADA lanes).

**Use it for** short-lived bursts where many equal UTxOs are wanted right away, with a lane size above the typical
payment. Not as a permanent setting.

### 12.4 `PaymentSizedStrategy` (CIP-2 self-organisation)

**Idea.** From [CIP-2](https://cips.cardano.org/cip/CIP-0002): if every payment of size *v* leaves a change piece of
about *v*, the wallet gradually fills up with UTxOs matching its typical payments.

**Algorithm.** Take the ADA of the non-change outputs, largest first. For each payment create a piece of
**payment + `feeAllowance` + ADA-only min-ada** (default 0.5 + 0.969750 = 1.469750 ADA on top), so that the piece
can pay a similar payment alone, including its fee and a change output. Payments below min-ada, and payments whose
piece would leave a remainder below min-ada, are skipped. The remainder is the last piece; at most `maxPieces` (5)
pieces in total.

The margin was added after the first simulation: with pieces of exactly the payment size, a 10 ADA piece could not
pay a 10 ADA payment (fee and change missing), and the number of UTxOs able to fund a payment alone stayed at 1.0.
With the margin it is 150.5 (§12.8).

**Examples.**

| Change | Payments | Result |
|---|---|---|
| 1,000 ADA | 10 ADA | 11.469750 · 988.530250 ADA |
| 1,000 ADA | 50, 20, 5 ADA | 51.469750 · 21.469750 · 6.469750 · 920.590750 ADA |
| 35 ADA | 10 ADA | 11.469750 · 23.530250 ADA |
| 11 ADA | 10 ADA | 1 output (the piece would not leave a valid remainder) |
| 60 ADA + 3 tokens of 3 policies | 10 ADA | 1 bundle (1.482640 ADA) + 11.469750 · 47.047610 ADA |

**Strengths.**

- Grounded in an existing Cardano standard (CIP-2) instead of ad-hoc percentages.
- Adapts to the wallet's real payment sizes; at most one extra UTxO per payment.

**Weaknesses.**

- CIP-2 assumes Random-Improve coin selection, which spends small UTxOs over time. With CCL's largest-first default
  the payment-sized pieces are never picked while a bigger UTxO exists, so they pile up: 301 UTxOs after 300 fixed
  payments (§12.8).
- The ADA of token payments (e.g. 1.2 ADA sent with an NFT) also creates pieces, which are small.

**Use it for** experiments with a random coin selection strategy; not with largest-first.

### 12.5 `TargetShapeStrategy` (wallet-aware)

**Idea.** Decide on a wanted wallet shape, e.g. "5 ADA-only UTxOs of at least 10 ADA", and only create what is
missing, instead of reshaping every change blindly.

**Algorithm.**

1. Load the change address' UTxOs from the `UtxoSupplier`. Count the **existing lanes**: ADA-only UTxOs of at least
   `laneAmount`, without datum or script ref, not spent by this transaction.
2. `missing = targetLanes − existing`, limited by how many lanes the ADA can fund.
3. If at most one lane is missing, keep one ADA output (it can be that lane). Otherwise create `missing − 1` lanes of
   exactly `laneAmount` and put the rest in the last lane.
4. Tokens are handled as in §12.2.

**Examples** (defaults: 5 lanes of 10 ADA).

| Wallet already has | Change | Result |
|---|---|---|
| no lanes | 1,000 ADA | 10 · 10 · 10 · 10 · 960 ADA |
| 2 lanes of 20 ADA | 1,000 ADA | 10 · 10 · 980 ADA |
| 5 lanes | 1,000 ADA | 1 output |
| no lanes | 60 ADA + 3 tokens of 3 policies | 1 bundle (1.482640 ADA) + 10 · 10 · 10 · 10 · 18.517360 ADA |

**Strengths.**

- The **only strategy with bounded growth**: the wallet stays at `targetLanes` ADA-only UTxOs (5 or 10 in §12.8)
  instead of growing with every transaction, and usually only 1 change output is created (1.01 on average).
- Lowest input count (≈1.0) and, with `laneAmount` above the typical payment, every lane can fund a payment alone.
- The parameters mean something to users: "how many parallel payments" and "how big".

**Weaknesses.**

- One `UtxoSupplier.getAll` call per transaction; slow for large wallets or rate-limited backends.
- It can't see UTxOs spent by transactions still in flight, so it may count a lane that is about to disappear.
- Needs `laneAmount` above the typical payment; with 10 ADA lanes and 10 ADA payments the lanes can't fund a payment
  alone.
- Does not consolidate an already fragmented wallet; it only stops further growth.

**Use it for** general-purpose wallets and services: it gives predictable, bounded concurrency groundwork.

### 12.6 Token bundling: `PolicyBundling` vs `ByteBudgetBundling`

| | `PolicyBundling` (Evolution) | `ByteBudgetBundling` (default of the alternatives) |
|---|---|---|
| Rule | One bundle per policy, chunks of `bundleSize` (10) assets | Bundles up to `maxBundleBytes` (1,000) of CBOR, first-fit decreasing; a policy that fits is never split |
| 3 single-token policies | 3 outputs | 1 output |
| 150 single-token policies | 150 outputs, 171.97 ADA min-ada | 7 outputs, 32.06 ADA min-ada |
| Output size | Depends on asset name lengths | Bounded by the budget, far below `maxValSize` (5,000) |
| Mixes policies | Never | Yes |

`ByteBudgetBundling` is the better default for wallets. `PolicyBundling` is still useful when one policy per UTxO
matters (DEX, marketplace, staking of a specific token).

### 12.7 Summary

| Strategy | Growth under largest-first | Tokens | Parameters | Main risk |
|---|---|---|---|---|
| `EvolutionStrategy` | Unbounded (~1.2 UTxOs per tx) | Per policy, spread | Evolution defaults | Fragmentation, 1 output per policy |
| `PercentageSplitStrategy` | Unbounded (as Evolution) | Byte budget, ADA separate | Evolution defaults | Fragmentation |
| `EqualLanesStrategy` | Unbounded (up to ~2 UTxOs per tx) | Byte budget, ADA separate | Lanes, lane size | Worst fragmentation |
| `PaymentSizedStrategy` | Unbounded (~1 UTxO per tx) | Byte budget, ADA separate | Max pieces, fee allowance | Pieces pile up under largest-first |
| `TargetShapeStrategy` | **Bounded** at `targetLanes` | Byte budget, ADA separate | Lanes, lane size | `getAll` per transaction |

### 12.8 Simulation

`UnfrackSimulationTest` (with `UnfrackSimulator`) replays wallet workloads through the real `Unfrack`, so the strategy
result checks run on every step. It prints the tables below and asserts the properties this section relies on.

Model:

- one address; serial transactions; inputs chosen **largest-first** (CCL's default); fixed fee 0.2 ADA; fee reserve
  2 ADA; payments in ADA to another address;
- "fundable" is the average number of ADA-only UTxOs that could pay the next payment alone (a proxy for concurrency
  potential);
- "token churn" is the share of payments whose inputs included a UTxO with tokens;
- "ADA in token UTxOs" is ADA locked next to tokens at the end.

**ADA-only workloads** (wallet: one 10,000 ADA UTxO, 300 payments).

| Strategy | Fixed 10 ADA: max UTxOs | fundable | Random 1–50 ADA: max UTxOs | fundable | Mixed 2–10 / 100–300 ADA: max UTxOs | fundable |
|---|---|---|---|---|---|---|
| None (today) | 1 | 1.0 | 1 | 1.0 | 1 | 1.0 |
| Evolution | 361 | 228.0 | 301 | 95.4 | 331 | 214.8 |
| EqualLanes (5 × 10 ADA) | 625 | 427.4 | 470 | 90.6 | 482 | 299.7 |
| EqualLanes (5 × 60 ADA) | 125 | 118.4 | 125 | 89.6 | 116 | 84.4 |
| PaymentSized (5) | 301 | 150.5 | 205 | 52.6 | 288 | 77.5 |
| TargetShape (5 × 10 ADA) | 5 | 1.0 | 5 | 1.7 | 5 | 4.0 |
| TargetShape (5 × 60 ADA) | 5 | 5.0 | 5 | 5.0 | 5 | 4.6 |
| TargetShape (10 × 60 ADA) | 10 | 10.0 | 10 | 10.0 | 10 | 9.2 |

`PercentageSplitStrategy` is identical to Evolution for ADA-only change. Average inputs per transaction stayed between
1.0 and 1.3 for all strategies except EqualLanes with 10 ADA lanes (up to 1.73).

**Token workloads.**

| Strategy | Hot UTxO¹: max UTxOs | token churn | ADA in token UTxOs | Small wallet²: final UTxOs | ADA in token UTxOs |
|---|---|---|---|---|---|
| None (today) | 1 | **100 %** (150 tokens moved per payment) | 2,183.60 | 1 | 69.56 |
| Evolution | 445 | 0 % | **171.97** | 27 | **22.93** |
| PercentageSplit | 308 | 0 % | 32.06 | 8 | 4.34 |
| EqualLanes (5 × 60 ADA) | 132 | 0 % | 32.06 | 3 | 4.34 |
| PaymentSized (5) | 212 | 0 % | 32.06 | 27 | 3.09 |
| TargetShape (5 × 60 ADA) | **12** | 0 % | 32.06 | **3** | 4.34 |

¹ One UTxO with 10,000 ADA and 150 single-token policies, 300 random 1–50 ADA payments.
² One UTxO with 150 ADA and 20 single-token policies, 40 random 1–3 ADA payments.

Token airdrops that arrive as separate UTxOs (a fourth workload) make no difference: largest-first never selects
them, so they never reach the change and no strategy touches them.

**Reading.**

- Every strategy fixes the hot-UTxO problem: after the first transaction, payments no longer move tokens.
- One bundle per policy (Evolution) locks 5× more ADA than byte-budget bundling and adds 150 UTxOs at once.
- Evolution's spread stores ADA next to tokens in small wallets (22.93 vs 4.34 ADA).
- The per-transaction strategies create many fundable UTxOs, but only by fragmenting the wallet without limit, which
  is the "fracked" state unfracking is meant to prevent. `TargetShapeStrategy` gives exactly the concurrency it is
  configured for.

**Caveats.** One address, serial transactions, largest-first selection only, fixed fee, synthetic payments. See §9.

### 12.9 Recommendation

1. **Tokens: `ByteBudgetBundling`, with ADA always kept separate** (as in all alternatives). It is better than the
   Evolution behaviour in every case simulated.
2. **ADA: `TargetShapeStrategy`** for general use, with `laneAmount` above the typical payment and `targetLanes` set to
   the wanted number of parallel payments. It is the only strategy whose wallet shape stays bounded and predictable.
3. **Keep `EvolutionStrategy` as the default for now**, for parity with Evolution SDK, until reviewers decide on the
   default (PR #676). The data favours `TargetShapeStrategy` with `ByteBudgetBundling`. A CIP would be stronger if it
   standardised a *target wallet shape* as an extension of CIP-2, rather than Evolution's percentages.
4. **`PaymentSizedStrategy`**: the margin fix makes its pieces useful, but under largest-first it still piles up
   UTxOs. Evaluate it again with a random coin selection strategy.
5. **`EqualLanesStrategy` only for short bursts**, with large lanes.
