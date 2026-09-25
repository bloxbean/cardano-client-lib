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
2. Provide **`Unfrack`**, a `TxBuilder` that is a faithful port of Evolution SDK's
   `createUnfrackedChangeOutputs`. The goals are parity and a well-understood algorithm; tuning comes later.
3. Unfrack **in the same transaction** by reshaping change outputs. There is no separate consolidation transaction
   (unlike UnFrack.It).
4. Run `Unfrack` **before** fee/min-ada balancing. All existing balancing logic stays as it is and is reused
   (see §5).

```java
quickTxBuilder.compose(tx)
    .feePayer(sender)
    .preBalanceTx(new Unfrack())
    .withSigner(signer)
    .completeAndWait();
```

## 3. API Changes

### `function` module — `com.bloxbean.cardano.client.function.balance.unfrack`

| Class | Role |
|---|---|
| `Unfrack` | `TxBuilder` implementation. Finds change outputs and replaces each with its split pieces |
| `UnfrackPlanner` | Pure algorithm: `List<Value> plan(String address, Value change)`. No transaction or context dependency |
| `UnfrackConfig` | Immutable Lombok builder with the tuning parameters (§4.3) |

### `quicktx` module

`QuickTxBuilder.TxContext#preBalanceTx(TxBuilder)` appends the function to the existing pre-balance transformer
(`andThen`) instead of replacing it. Functions run in the order they were added. No new QuickTx method is added.

`Unfrack` lives in `function`, not `quicktx`, so it can also be composed manually with the low-level `TxBuilder`
API.

## 4. Algorithm

### 4.1 Planner (port of Evolution SDK)

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
- Value conservation is asserted: Σ pieces must equal the original change, otherwise it throws.

**Open question:** the default reserve size. A fixed 2 ADA prevents splitting small change that Evolution does
split. See the comparison and options in §11.6 (F1).

### 4.3 Configuration (`UnfrackConfig`)

| Parameter | Default | Source |
|---|---|---|
| `subdivideThreshold` | 100 ADA | Evolution |
| `subdividePercentages` | 50, 15, 10, 10, 5, 5, 5 | Evolution / UnFrack.It |
| `bundleSize` | 10 | Evolution (UnFrack.It uses 30) |
| `feeReserve` | 2 ADA | CCL-specific (§4.2) |

Evolution also declares `isolateFungibles` and `groupNftsByPolicy`, but its change-creation path
(`createUnfrackedChangeOutputs`) never reads them, so they are intentionally omitted.

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
- Change at or below `feeReserve`, and change the planner cannot afford to split, is left unchanged.
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

- **Parallelism-oriented strategy**: split ADA into N roughly equal "lanes" sized to the expected payment, instead
  of 50/15/10/…, and possibly keep a target count of ADA-only UTxOs in the wallet.
- **Fallback instead of failure**: if validation after balancing fails, rebuild with a single change output.
- **Size limits**: check `maxValSize` per bundle and `maxTxSize` for the whole transaction (issue #42).
- **TxPlan / YAML**: an `unfrack` field so that YAML plans can opt in.
- **txflow / TxStream integration**: make unfracked change usable as intra-address lanes, complementing today's
  address-based `LanePolicy`.

## 10. Test Plan

- **Planner unit tests** (`UnfrackPlanner`, no transaction context):
  - ADA below and above the threshold, including rounding remainder;
  - subdivision that can't be afforded;
  - spreading ADA across bundles;
  - chunking by `bundleSize`;
  - bundles plus subdivided ADA;
  - bundles that can't be funded;
  - config validation;
  - invariants on every result: Σ pieces equals the input, and every piece meets min-ada.
- **`Unfrack` unit tests** (on a `Transaction`):
  - in-place split and index preservation;
  - small change left untouched;
  - non-change outputs and change with a datum left untouched.
- **QuickTx tests** (mocked `UtxoSupplier`/`ProtocolParamsSupplier`): the same transaction built with and without
  `.preBalanceTx(new Unfrack())`, checking output count, min-ada, inputs = outputs + fee, and token conservation. Without the
  `Unfrack`, the output must be identical to today's. Two `preBalanceTx(...)` calls must both be applied.
- **Parity:** port selected Evolution SDK scenarios (`Unfrack.test.ts`, `TxBuilder.UnfrackChangeHandling.test.ts`)
  so that CCL produces the same split for the same input.
- **Integration** (Yaci DevKit): submit an unfracking transaction and check that the resulting UTxOs are on-chain
  and spendable.

## 11. Examples: Evolution SDK and CCL

The TypeScript snippets below are taken from Evolution SDK
([`0167cf9`](https://github.com/IntersectMBO/evolution-sdk/tree/0167cf91381eea2c2f33db5ab1396a66b4dbe2da)): its docs,
and its builder tests, which are the only runnable unfrack examples it ships (its `examples/` folder has none). Each
is followed by the proposed CCL equivalent.

The "CCL result" rows come from running the same wallets through the prototype `Unfrack` with QuickTx and mocked
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
        .preBalanceTx(new Unfrack())                   // defaults = Evolution defaults
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
UnfrackConfig config = UnfrackConfig.builder()
        .subdivideThreshold(adaToLovelace(100))
        .subdividePercentages(List.of(50, 30, 20))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx().payToAddress(destination, Amount.ada(1)).from(source))
        .preBalanceTx(new Unfrack(config))
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
UnfrackConfig config = UnfrackConfig.builder()
        .subdivideThreshold(BigInteger.valueOf(500_000))
        .subdividePercentages(List.of(50, 30, 20))
        .feeReserve(BigInteger.valueOf(300_000))     // see finding F1
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx().payToAddress(destination, Amount.lovelace(BigInteger.valueOf(2_000_000))).from(source))
        .preBalanceTx(new Unfrack(config))
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

UnfrackConfig config = UnfrackConfig.builder()
        .bundleSize(10)
        .subdivideThreshold(adaToLovelace(50))
        .subdividePercentages(List.of(50, 25, 15, 10))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx()
                .collectFrom(fragments)
                .payToAddress(destination, Amount.ada(1))
                .from(source))
        .preBalanceTx(new Unfrack(config))
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
