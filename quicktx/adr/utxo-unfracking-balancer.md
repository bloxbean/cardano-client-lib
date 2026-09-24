# UTxO Unfracking via Pluggable Balancer

**Status**: Proposed
**Date**: 2026-09-24
**Issue**: TBD (related: https://github.com/bloxbean/cardano-client-lib/issues/42, https://github.com/bloxbean/cardano-client-lib/issues/279)
**Modules**: `quicktx`, `function`

## 1. Context

QuickTx always returns change as a **single output** at the sender's change address
(`InputBuilders.buildInputs` → one `ChangeOutput`; `FeeCalculators` deducts the fee from it;
`ChangeOutputAdjustments` tops it up to min-ada). Over time a wallet converges to one of two unhealthy shapes:

- **One "hot" UTxO** holding almost all ADA and every token. Every new transaction must spend it, and it stays
  unavailable until the previous transaction settles. A wallet can therefore only have **one transaction in flight**.
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

For CCL the primary motivation is **concurrency**. When change is partitioned into several independent UTxOs, a
later transaction can select an uncontended UTxO while an earlier one is still in flight.

## 2. Decision

1. Add an opt-in, pluggable **`TxBalancer`** hook to QuickTx, `TxContext.balancer(TxBalancer)`. When it is not set,
   transaction building is unchanged, byte for byte.
2. Provide **`Unfrack`** as the first implementation, a faithful port of Evolution SDK's
   `createUnfrackedChangeOutputs`. The goals are parity and a well-understood algorithm; tuning comes later.
3. Unfrack **in the same transaction** by reshaping change outputs. There is no separate consolidation transaction
   (unlike UnFrack.It).
4. Run the balancer **before** fee/min-ada balancing. All existing balancing logic stays as it is and is reused
   (see §5).

```java
quickTxBuilder.compose(tx)
    .feePayer(sender)
    .balancer(new Unfrack())
    .withSigner(signer)
    .completeAndWait();
```

## 3. API Changes

### `function` module — `com.bloxbean.cardano.client.function.balance`

```java
public interface TxBalancer {
    /** Invoked after inputs and change are built, before collateral, script cost evaluation and fee balancing. */
    TxBuilder preBalance();
}
```

The hook lives in `function`, not `quicktx`, so that it can also be composed manually with the low-level
`TxBuilder` API.

### `function` module — `...function.balance.unfrack`

| Class | Role |
|---|---|
| `Unfrack` | `TxBalancer` implementation. Finds change outputs and replaces each with its split pieces |
| `UnfrackPlanner` | Pure algorithm: `List<Value> plan(String address, Value change)`. No transaction or context dependency |
| `UnfrackConfig` | Immutable Lombok builder with the tuning parameters (§4.3) |

### `quicktx` module

`QuickTxBuilder.TxContext#balancer(TxBalancer)` stores the balancer. `_build()` appends `balancer.preBalance()`
right after the existing `preBalanceTx(...)` transformer.

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
preBalanceTx(...)        user transformer (existing)
balancer.preBalance()    ← NEW: split ChangeOutputs
collateral               existing
script cost evaluation   existing; sees the final output set
balanceTx(feePayer)      existing: FeeCalculators → ChangeOutputAdjustments → collateral balance
postBalanceTx(...)       existing
```

The balancer runs before balancing because:

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
- **`mergeOutputs(true)`**: change may be merged into a user output, which is not a `ChangeOutput`, so the balancer
  does nothing.
- **Transactions without inputs** (withdrawal/deregistration funded by a refund) have no `ChangeOutput` yet, so the
  balancer does nothing.
- NFT vs fungible detection is not needed for the ported path; tokens are bundled by policy only.

## 7. Alternatives Considered

### Separate consolidation transaction (UnFrack.It style)
This requires an extra transaction, an extra fee and a wait for confirmation before the benefit appears, and it
doesn't fit the QuickTx one-shot model. It was rejected in favour of splitting change inside every opted-in
transaction.

### Replace `ScriptBalanceTxProviders.balanceTx` with a pluggable balancer
This would duplicate fee calculation, min-ada adjustment, script re-evaluation and collateral balancing, and every
balancer would have to reimplement them. It was rejected; the pre-balance hook is purely additive.

### Split change after balancing (`postBalanceTx`)
No fee recalculation happens after this point. The balancer would have to restore the fee, re-run
`FeeCalculators`/`ChangeOutputAdjustments` and re-evaluate scripts itself. It was rejected as fragile.

### Implement unfracking as a `UtxoSelectionStrategy`
Selection strategies only choose inputs and have no say in the shape of the change. They are the wrong layer.

### Implement with the existing `preBalanceTx(TxBuilder)`
This would work mechanically, but `preBalanceTx` is a single overwrite-only slot meant for user transformers.
A named, typed `balancer(...)` keeps both usable and makes the intent discoverable.

## 8. Consequences

**Positive**
- A wallet can keep several transactions in flight without extra consolidation transactions.
- ADA payments no longer drag every token along.
- The change is additive and opt-in; the default path must stay unchanged (existing `function`/`quicktx` tests act as the guard).
- The algorithm matches Evolution SDK, so behaviour is predictable across TypeScript and Java stacks.

**Negative / trade-offs**
- More outputs per transaction make that transaction slightly larger and its fee slightly higher. This one-time
  cost is intended.
- The Evolution percentage split targets **spending flexibility**, not concurrency (§9).
- More UTxOs increase wallet scan and selection work.

## 9. Future Work

- **Parallelism-oriented strategy**: split ADA into N roughly equal "lanes" sized to the expected payment, instead
  of 50/15/10/…, and possibly keep a target count of ADA-only UTxOs in the wallet.
- **Fallback instead of failure**: if validation after balancing fails, rebuild with a single change output.
- **Size limits**: check `maxValSize` per bundle and `maxTxSize` for the whole transaction (issue #42).
- **TxPlan / YAML**: a `balancer` field so that YAML plans can opt in.
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
- **Balancer unit tests** (`Unfrack` on a `Transaction`):
  - in-place split and index preservation;
  - small change left untouched;
  - non-change outputs and change with a datum left untouched.
- **QuickTx tests** (mocked `UtxoSupplier`/`ProtocolParamsSupplier`): the same transaction built with and without
  `.balancer(...)`, checking output count, min-ada, inputs = outputs + fee, and token conservation. Without the
  balancer, the output must be identical to today's.
- **Parity:** port selected Evolution SDK scenarios (`Unfrack.test.ts`, `TxBuilder.UnfrackChangeHandling.test.ts`)
  so that CCL produces the same split for the same input.
- **Integration** (Yaci DevKit): submit several transactions concurrently from an unfracked wallet to demonstrate the
  concurrency gain.
