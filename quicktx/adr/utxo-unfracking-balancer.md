# UTxO Unfracking: Wallet Shape

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

In the Cardano ecosystem the fix is called **unfracking**, after [UnFrack.It](https://unfrack.it/). The
[Evolution SDK](https://github.com/IntersectMBO/evolution-sdk) uses the same term: its `unfrack` build option
(`packages/evolution/src/sdk/builders/Unfrack.ts`) is "named in respect to the Unfrack.It open source community". It
bundles tokens per policy and subdivides large ADA change **during change creation of a normal transaction**, not in a
separate consolidation transaction.

Unfracking works in **two directions**, depending on the starting point:

- **one UTxO → several**: split a hot UTxO into token bundles and ADA-only UTxOs;
- **many UTxOs → fewer** (consolidation): merge dust and token fragments. Token UTxOs each lock their own min-ada
  (about 1.1–1.5 ADA); merging them reclaims that ADA. ADA-only UTxOs lock nothing, so merging them only reduces the
  number of inputs.

**Why we do this: wallet hygiene** (issue #678). The main motivation is to keep wallets built with CCL in a healthy
shape:

- keep change outputs below `maxValSize`, so they stay spendable (issue #42);
- ADA payments and single-token transfers move only the UTxOs they need, not every token in the wallet, so
  transactions are smaller, cheaper and easier to review on a hardware wallet;
- reclaim ADA locked next to scattered tokens (airdrops, dust), which coin selection never picks up by itself;
- keep ADA-only UTxOs available, e.g. for collateral.

**Concurrency is not the main motivation.** Unfracking **does not solve UTxO contention**: several UTxOs are a
prerequisite for concurrent transactions, but choosing uncontended inputs still needs coordination (e.g. txflow) or a
dedicated selection strategy. Evolution SDK does not attempt this either. Some shapes can still help:

| Need | Primary tool | Where a wallet shape helps |
|---|---|---|
| One process sends many transactions quickly | **Transaction chaining** (txflow): each transaction spends an output of the previous one before it confirms; works even with a single UTxO | Not needed |
| Several independent builders (workers, machines) share one wallet without shared state | Coordination between builders | `throughput(n, laneSize)` keeps `n` independent lanes, one per builder |

So concurrency is a side benefit of one profile, not the reason for this ADR.

## 2. Decision

1. **One algorithm, configured by a `WalletShape`**, instead of a menu of algorithms. A `WalletShape` describes the
   wanted state of the wallet in three dimensions:
   - **ADA** (`AdaShape`): how many ADA-only UTxOs of which size (`Lanes`), a stateless percentage split
     (`Percentages`), or one output (`Single`);
   - **tokens** (`TokenBundlingStrategy`): bundled by byte size (`ByteBudgetBundling`) or one policy per UTxO
     (`PolicyBundling`);
   - **consolidation** (`Consolidation`): whether small UTxOs are merged into the transaction.
2. **Named profiles by intent**: `hygiene()` (default), `throughput(n, laneSize)`, `collector()`, `dex()`,
   `offline()` and `minimal()` (§4.6). Different intents need different parameters, not different algorithms.
3. **The shape belongs to the wallet owner.** A wallet sets it in its settings, a service in its configuration, and a
   dApp building a transaction for someone else's wallet uses `minimal()` (§4.7). A later CIP-30 extension could let
   the wallet share its change preferences with dApps (§13).
4. **Unfrack in both directions, in the same transaction**: split a hot UTxO (tokens into bundles, ADA into lanes) and
   merge small UTxOs (consolidation). There is no separate consolidation transaction (unlike UnFrack.It).
5. **Reuse the existing `preBalanceTx(TxBuilder)` hook**, and make it **chain** transformers (`andThen`) instead of
   overwriting the previous one. `Unfrack` runs before fee/min-ada balancing, so all existing balancing logic is
   reused (§5). Without `Unfrack`, transaction building is unchanged, byte for byte.
6. **`ChangeSplitStrategy` as an escape hatch** for needs no shape covers. `EvolutionStrategy`, a faithful port of
   Evolution SDK, is available through it for parity (§4.8).
7. **Aim for a CIP**: the rules of a healthy wallet (§4.1), the parameterised algorithm and the profiles are written so
   they can be proposed as an informational CIP, as an extension of [CIP-2](https://cips.cardano.org/cip/CIP-0002)
   (§9).

```java
quickTxBuilder.compose(tx)
    .feePayer(sender)
    .preBalanceTx(new Unfrack())                                           // WalletShape.hygiene()
    // .preBalanceTx(new Unfrack(WalletShape.throughput(10, Amount.ada(60))))
    // .preBalanceTx(new Unfrack(new EvolutionStrategy()))                 // Evolution SDK parity
    .withSigner(signer)
    .completeAndWait();
```

§12 evaluates the candidate algorithms behind this design and explains why one parameterised algorithm was chosen.

## 3. API Changes

### `function` module — `com.bloxbean.cardano.client.function.balance.unfrack`

| Type | Role |
|---|---|
| `Unfrack` | `TxBuilder`. Consolidates (§4.5), calls the strategy, verifies its result, applies the fee reserve and output order (§4.2) |
| `WalletShape` | Immutable value (Lombok builder): `ada`, `tokens`, `consolidation`; profiles `hygiene()`, `throughput(n, laneSize)`, `collector()`, `dex()`, `offline()`, `minimal()` |
| `AdaShape` | Sealed interface: `Lanes(count, laneSize)`, `Percentages(threshold, percentages)`, `Single()`; each implements its ADA split |
| `Consolidation` | Record: `maxExtraInputs`, `maxUtxoLovelace`; `none()`, `opportunistic(n)`, `opportunistic(n, maxUtxo)` |
| `TokenBundlingStrategy` | How tokens are grouped: `PolicyBundling` or `ByteBudgetBundling`; `isFragment(...)` tells consolidation which token UTxOs to merge |
| `WalletShapeStrategy` | The algorithm: a `ChangeSplitStrategy` that shapes change towards a `WalletShape` |
| `ChangeSplitStrategy`, `ChangeSplitRequest` | Escape hatch for custom algorithms: `List<Value> split(ChangeSplitRequest)`; the request carries the change address, change (minus fee reserve), protocol params, the read-only transaction and the `UtxoSupplier` |
| `AbstractChangeSplitStrategy` | Base for custom strategies that keep ADA apart from tokens and only split ADA differently |
| `EvolutionStrategy` | Port of Evolution SDK, for parity (§4.8) |

```java
new Unfrack()                                   // hygiene()
new Unfrack(WalletShape shape)
new Unfrack(WalletShape shape, BigInteger feeReserve)
new Unfrack(ChangeSplitStrategy strategy)       // no consolidation
new Unfrack(ChangeSplitStrategy strategy, BigInteger feeReserve)
```

A strategy only returns values. `Unfrack` checks that they sum to the change and that each meets min-ada, and throws
`IllegalStateException` otherwise, so a faulty custom strategy cannot create an invalid transaction.

### `quicktx` module

`QuickTxBuilder.TxContext#preBalanceTx(TxBuilder)` appends the function to the existing pre-balance transformer
(`andThen`) instead of replacing it. Functions run in the order they were added. No new QuickTx method is added.

`Unfrack` lives in `function`, not `quicktx`, so it can also be composed manually with the low-level `TxBuilder`
API.

## 4. Algorithm

### 4.1 Rules of a healthy wallet

Every shape follows these rules; they are also the core of a possible CIP:

1. **Tokens are never mixed with spendable ADA.** Token bundles hold exactly their min-ada; ADA goes to ADA-only
   outputs. Only an amount below the ADA-only min-ada is added to the last bundle.
2. **Outputs stay small.** Token bundles are bounded by a byte budget (or a per-policy asset count), far below
   `maxValSize`.
3. **Value is conserved and every output is valid.** The pieces sum exactly to the change; each piece meets min-ada.
4. **The wallet shape is bounded.** The algorithm moves the wallet towards the declared shape and stops there, instead
   of reshaping every change blindly.

### 4.2 CCL-specific adaptation: fee reserve and output ordering

CCL's fee calculator deducts the fee from the **max-coin output at the fee payer address**, and
`ChangeOutputAdjustments` fails if **more than one** change output ends up below min-ada. To fit both without changing
them:

- `Unfrack` splits `change − feeReserve` and adds the reserve back to the **largest** piece. That piece carries the
  fee, and all other pieces still meet min-ada after balancing.
- The fee-bearing piece replaces the original change output **at the same index**, and the remaining pieces are
  **appended**. The indexes of all other outputs are therefore unchanged.
- The strategy result is verified: Σ pieces must equal the change and each piece must meet min-ada, otherwise it
  throws.

**Open question:** the default reserve size (2 ADA). §11.6 (F1) compares the options.

### 4.3 ADA shapes

**`Lanes(count, laneSize)`** keeps `count` ADA-only UTxOs ("lanes") of at least `laneSize` at the change address and
creates only the lanes that are missing:

1. Load the change address' UTxOs from the `UtxoSupplier`. Count the **existing lanes**: ADA-only UTxOs of at least
   `laneSize`, without datum or script ref, not spent by this transaction.
2. `missing = count − existing`, limited by how many lanes the ADA can fund.
3. If at most one lane is missing, keep one ADA output (it can be that lane). Otherwise create `missing − 1` lanes of
   exactly `laneSize` and put the rest in the last lane.

`laneSize` below the ADA-only min-ada is raised to min-ada. Without a `UtxoSupplier` the ADA stays in one output, so a
wallet never grows without bound.

**`Percentages(threshold, percentages)`** splits ADA by the percentages (last slice gets the rounding remainder) when it
is at least `threshold` and the smallest slice meets min-ada; otherwise one output. It never reads the wallet, so it
also works for hardware or offline signing, but it re-splits every large change (§12.8).

**`Single()`** keeps ADA in one output; useful when only the token or consolidation behaviour is wanted.

### 4.4 Token bundling

| | `ByteBudgetBundling(maxBundleBytes)` (default) | `PolicyBundling(bundleSize)` |
|---|---|---|
| Rule | Bundles up to `maxBundleBytes` (1,000) of CBOR, first-fit decreasing; a policy that fits is never split; a larger policy is chunked | One bundle per policy, chunks of `bundleSize` (10) assets |
| Mixes policies | Yes | Never |
| 150 single-token policies | 7 outputs, 32.06 ADA min-ada | 150 outputs, 171.97 ADA min-ada |
| Fragment (for consolidation) | Uses less than half the budget | Holds fewer than half of `bundleSize` assets |

### 4.5 Consolidation

`Consolidation(maxExtraInputs, maxUtxoLovelace)` makes `Unfrack` add small UTxOs of the change address as extra
inputs before the change is split, so their value is reshaped together with the change:

1. Only an address that **already has an input** in the transaction is consolidated, so no new signer is needed.
2. Candidates are UTxOs at that address that are not inputs yet, have no datum or script ref, and hold at most
   `maxUtxoLovelace`:
   - **ADA-only dust** is always a candidate;
   - **token UTxOs** are candidates only if they are **fragments** (`TokenBundlingStrategy.isFragment`), and only when
     there are **at least two** of them: a single fragment would just be re-bundled, and a full bundle would be moved
     again and again.
3. Token fragments go first (they hold locked min-ada), then the smallest UTxOs; at most `maxExtraInputs`.
4. Their value is added to the first change output of that address, then the change is split as usual. The extra
   inputs' size is part of the normal fee calculation.

Consolidation is **skipped** when the transaction has redeemers (extra inputs change input order and therefore
redeemer indexes) and when there is no `UtxoSupplier`. A custom `ChangeSplitStrategy` runs without consolidation.

Without the fragment rule, consolidation would pull full bundles back into every transaction: in the hot-UTxO
workload (§12.8) `collector()` would create 8 change outputs per transaction instead of 1.03.

### 4.6 Profiles

Wallets are used for very different things: a person paying friends, a payout service sending hundreds of payments,
an NFT collector receiving airdrops, a DEX or marketplace holding tokens per policy, a hardware wallet signing
offline, and a dApp building a transaction for someone else's wallet. A single fixed shape would be wrong for most of
them, and a free combination of parameters is hard to get right. Profiles name the common occasions and give each a
tested set of parameters; the builder remains for everything else.

| Profile | ADA | Tokens | Consolidation |
|---|---|---|---|
| `hygiene()` (default) | `Lanes(3, 50 ADA)` | `ByteBudgetBundling(1000)` | `opportunistic(3)`, UTxOs ≤ 5 ADA |
| `throughput(n, laneSize)` | `Lanes(n, laneSize)` | `ByteBudgetBundling(1000)` | none |
| `collector()` | `Lanes(2, 20 ADA)` | `ByteBudgetBundling(1000)` | `opportunistic(20, 10 ADA)` |
| `dex()` | `Lanes(3, 50 ADA)` | `PolicyBundling(10)` | none |
| `offline()` | `Percentages(100 ADA, 50/15/10/10/5/5/5)` | `ByteBudgetBundling(1000)` | none |
| `minimal()` | `Single()` | `ByteBudgetBundling(1000)` | none |

The numbers below come from the simulator (§12.8): 300 payments from one 10,000 ADA UTxO unless stated otherwise.

#### `hygiene()` — everyday wallet (default)

- **Intent:** a personal wallet that pays, receives tokens now and then, and should stay tidy without configuration.
- **Behaviour:** keeps 3 ADA-only UTxOs of at least 50 ADA, bundles tokens by size, and merges up to 3 small UTxOs (≤ 5
  ADA: dust, airdropped tokens) per transaction.
- **Evidence:** the wallet stays at 3 ADA-only UTxOs in every workload. With a token airdrop every 5 transactions it
  ends with 5 token UTxOs and 15.24 ADA next to tokens, versus 60 token UTxOs and 99.05 ADA without unfracking.
- **Use when:** you don't know better. **Not for:** services that need many parallel payments.

#### `throughput(n, laneSize)` — service or bot

- **Intent:** send up to `n` payments of up to `laneSize` from separate UTxOs, e.g. a payout service.
- **Behaviour:** keeps exactly `n` ADA-only lanes of at least `laneSize`; no consolidation, so no extra inputs.
- **Evidence:** `throughput(10, 60 ADA)` keeps 10 ADA-only UTxOs, and all 10 can pay a 1–50 ADA payment alone
  (average 10.0), with 1.03 change outputs per transaction.
- **Use when:** you need parallelism. Pick `laneSize` above the typical payment plus fee and `n` = wanted parallel
  payments. It only prepares the UTxOs; choosing uncontended inputs still needs coordination (§1).

#### `collector()` — token holder

- **Intent:** a wallet with many tokens that wants to reclaim ADA locked next to them.
- **Behaviour:** keeps 2 ADA-only UTxOs of at least 20 ADA and merges up to 20 UTxOs of at most 10 ADA per transaction.
- **Evidence:** same as `hygiene()` in the simulated workloads (5 token UTxOs, 15.24 ADA in the airdrop workload); its
  larger limits only matter when many fragments arrive between two transactions, which the workloads don't cover yet.
- **Use when:** tokens arrive in bulk (airdrops, NFT mints). Transactions get more inputs, so they are larger.

#### `dex()` — DEX, marketplace, token staking

- **Intent:** keep one policy per UTxO, as protocols that spend a specific token expect.
- **Behaviour:** `PolicyBundling` (never mixes policies), 3 ADA-only lanes of at least 50 ADA, no consolidation (merging
  would mix policies back together).
- **Evidence:** 150 policies become 150 bundles and lock 171.97 ADA, as intended for this use case, versus 32.06 ADA with
  byte-budget bundling.
- **Use when:** policy separation matters more than locked ADA.

#### `offline()` — no wallet view

- **Intent:** hardware or offline signing, where the wallet's UTxOs can't be read while building.
- **Behaviour:** Evolution-style percentage split above 100 ADA, but with ADA always separate from tokens and
  byte-budget bundling; no consolidation.
- **Evidence:** fixes the token problems (32.06 ADA locked vs 171.97; no ADA stored next to tokens), but like every
  stateless split it keeps growing the wallet (361 UTxOs after 300 fixed payments).
- **Use when:** there is no `UtxoSupplier`. Otherwise prefer a `Lanes` profile.

#### `minimal()` — someone else's wallet

- **Intent:** a dApp, DEX or marketplace builds a transaction for a user's wallet and doesn't know that wallet's
  intent (§4.7).
- **Behaviour:** only the rules (§4.1): tokens bundled by size and kept apart from ADA, ADA in one output. It doesn't
  read the wallet, doesn't create lanes and doesn't consolidate, so the user signs no inputs or outputs they didn't
  expect.
- **Evidence:** the wallet keeps one ADA-only UTxO in every workload, and the hot-UTxO token churn still disappears
  after the first transaction (32.06 ADA locked, as with the other byte-budget shapes).
- **Use when:** building for a wallet whose shape is unknown.

### 4.7 Who sets the shape

A shape describes the intent of the wallet's owner, but the transaction is not always built by the wallet:

| Who builds the transaction | Who knows the intent | Shape used |
|---|---|---|
| The wallet itself (send, delegate, withdraw, ...) | The user | A wallet setting: a profile (default `hygiene()`), parameters for advanced users |
| A service or bot with its own keys | The operator | Service configuration, e.g. `throughput(20, Amount.ada(100))` |
| A dApp, DEX or marketplace, for a user's CIP-30 wallet | Only the wallet | The wallet's change preferences, if it can share them (§13); otherwise `minimal()` |
| Hardware or offline signing without UTxO access | The user | `offline()` |

**A wallet built on CCL** exposes the shape as a setting, like the network or the account: "UTxO management:
Everyday / Service (n lanes of x ADA) / Collector / DEX / Offline". It passes `new Unfrack(shape)` to every
transaction it builds.

**dApp-built transactions** are the hard case. With [CIP-30](https://cips.cardano.org/cip/CIP-0030) the dApp reads the
wallet's UTxOs (`getUtxos`) and change address (`getChangeAddress`), builds the transaction itself, and the wallet
only signs it (`signTx`). The wallet can't reshape the change afterwards without changing the transaction the dApp
built, evaluated and possibly partly signed. So:

1. **A dApp must not guess.** Without information it uses `minimal()`: the rules only, no lanes, and no consolidation
   of the user's UTxOs, which would add inputs the user didn't expect (and more to review on a hardware wallet).
2. **The wallet has to tell the dApp.** CIP-30 has no way to do this today; §13 outlines an extension that this
   design prepares for.
3. **The wallet still has some control today**, but only a little: when a dApp calls `getUtxos(amount)`, the wallet
   chooses which UTxOs reach the amount, so it can prefer UTxOs that fit its shape (e.g. not its reserved lanes).
   Without `amount`, CIP-30 requires `getUtxos()` to return all UTxOs, and the dApp's coin selection decides.
4. **The shape recovers by itself.** `Lanes`, token bundling and consolidation act on the wallet's *current* state, not
   on the transaction alone. After dApp transactions that ignored the shape, the wallet's next own transaction
   recreates the missing lanes and merges new fragments. A per-transaction split (like Evolution's) has no such
   property.

**CCL as a dApp backend** therefore uses `new Unfrack(WalletShape.minimal())` for users' wallets, or the wallet's change
preferences once the extension exists (§13).

### 4.8 `EvolutionStrategy` (parity)

A faithful port of Evolution SDK's `createUnfrackedChangeOutputs`, used as `new Unfrack(new EvolutionStrategy())`:

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

Defaults: `subdivideThreshold` 100 ADA, `subdividePercentages` 50/15/10/10/5/5/5, `bundleSize` 10. Evolution also
declares `isolateFungibles` and `groupNftsByPolicy`, but its change-creation path never reads them, so they are
omitted. Its weaknesses (spreading ADA into token bundles, one output per policy, unbounded growth) are analysed in
§12.1; `offline()` is the stateless alternative without them.

### 4.9 Configuration reference

| Type | Parameter | Default | Meaning |
|---|---|---|---|
| `Unfrack` | `feeReserve` | 2 ADA | Kept on the largest piece to pay the fee (§4.2) |
| `WalletShape` | `ada` | `Lanes(3, 50 ADA)` | ADA shape (§4.3) |
| | `tokens` | `ByteBudgetBundling(1000)` | Token bundling (§4.4) |
| | `consolidation` | `none()` | Consolidation (§4.5) |
| `AdaShape.Lanes` | `count`, `laneSize` | — | Wanted ADA-only UTxOs and their minimum size |
| `AdaShape.Percentages` | `threshold`, `percentages` | — | Split above `threshold`; positive, sum to 100 |
| `ByteBudgetBundling` | `maxBundleBytes` | 1,000 | Max CBOR size of a bundle |
| `PolicyBundling` | `bundleSize` | 10 | Max assets of one policy per bundle |
| `Consolidation` | `maxExtraInputs` | 0 (`none()`) | Max UTxOs merged per transaction |
| | `maxUtxoLovelace` | 5 ADA in `opportunistic(n)` | Only UTxOs with at most this much ADA |
| `EvolutionStrategy` | `subdivideThreshold`, `subdividePercentages`, `bundleSize` | 100 ADA, 50/15/10/10/5/5/5, 10 | §4.8 |

Profile defaults are first values chosen with the simulator and should be tuned further before a CIP.

## 5. Build Pipeline Placement

```
per-tx complete()        inputs selected, one ChangeOutput per sender, deposits resolved
preBalanceTx(...)        existing, now chained (← CHANGED): user/extender transformers, then Unfrack:
                           1. consolidation adds small UTxOs as inputs (← NEW)
                           2. change is split towards the WalletShape (← NEW)
collateral               existing
script cost evaluation   existing; sees the final output set
balanceTx(feePayer)      existing: FeeCalculators → ChangeOutputAdjustments → collateral balance
postBalanceTx(...)       existing
```

`Unfrack` runs before balancing because:

- at that point each `ChangeOutput` still holds the full surplus, so no fee has to be restored;
- script cost evaluation runs afterwards, so validators that inspect outputs are evaluated against the final
  outputs;
- fee calculation naturally includes the size of the extra inputs and outputs.

## 6. Rules and Edge Cases

- Only outputs that are `instanceof ChangeOutput`, have a positive coin, and carry **no** datum, datum hash or
  script ref are changed. User payment outputs are never touched.
- Change at or below `feeReserve`, and change the shape cannot afford to split, is left unchanged.
- **Fee payer ≠ sender**: the sender's change is split, and the fee is still taken from the fee payer's output.
- **Balancing adds inputs later** (min-ada top-up): the extra value merges into the largest piece. This is
  acceptable.
- **`mergeOutputs(true)`**: change may be merged into a user output, which is not a `ChangeOutput`, so `Unfrack`
  does nothing.
- **Transactions without inputs** (withdrawal/deregistration funded by a refund) have no `ChangeOutput` yet, so
  `Unfrack` does nothing.
- **Other pre-balance transformers**: they run in the order they were added. `Unfrack` only touches `ChangeOutput`s
  and, with consolidation, adds inputs; the order only matters if another transformer changes the same.
- **Consolidation**: only at addresses the transaction already spends from; skipped with redeemers or without a
  `UtxoSupplier` (§4.5).
- **No wallet view**: `Lanes` keeps ADA in one output without a `UtxoSupplier`; use `offline()` in that case.
- **Transactions built for someone else's wallet**: use `minimal()` (§4.7), or the wallet's change preferences once
  a CIP-30 extension exists (§13).
- **In-flight transactions**: `Lanes` and consolidation read the UTxOs from the `UtxoSupplier` and can't see UTxOs
  already spent by transactions still in flight.

## 7. Alternatives Considered

### Separate consolidation transaction (UnFrack.It style)
This requires an extra transaction, an extra fee and a wait for confirmation before the benefit appears, and it
doesn't fit the QuickTx one-shot model. It was rejected in favour of reshaping and consolidating inside every opted-in
transaction.

### Offer several algorithms as public options
Five candidate algorithms were evaluated (§12): the Evolution port, a percentage split, equal lanes, payment-sized
pieces per CIP-2, and wallet-aware lanes. The simulation (§12.8) shows that every algorithm that reshapes each change
without looking at the wallet keeps growing it (hundreds of UTxOs after 300 payments); only the wallet-aware one stays
bounded. Offering all of them would give users configurations known to fragment their wallets, and would be hard to
take back in a library. Different intents need different *parameters* of one algorithm, not different algorithms.
Rejected in favour of `WalletShape` with profiles.

### One algorithm without an interface
A CIP needs one specification, but a closed implementation would force users with special needs to fork CCL, and the
algorithm will still change while the CIP is reviewed. Rejected in favour of `WalletShape` plus the small
`ChangeSplitStrategy` interface as an escape hatch.

### Replace `ScriptBalanceTxProviders.balanceTx` with a pluggable balancer
This would duplicate fee calculation, min-ada adjustment, script re-evaluation and collateral balancing, and every
balancer would have to reimplement them. It was rejected; the pre-balance approach is purely additive.

### Split change after balancing (`postBalanceTx`)
No fee recalculation happens after this point. `Unfrack` would have to restore the fee, re-run
`FeeCalculators`/`ChangeOutputAdjustments` and re-evaluate scripts itself. It was rejected as fragile.

### Implement unfracking as a `UtxoSelectionStrategy`
Selection strategies only choose inputs and have no say in the shape of the change. Consolidation does touch inputs,
but it needs the change shape too, so it lives in `Unfrack`.

### A dedicated `TxBalancer` hook (`TxContext.balancer(...)`)
A new interface and QuickTx method running right after `preBalanceTx` would sit at the same position in the pipeline,
so it would only add API surface. The name would also mislead, because the hook reshapes change and doesn't balance
anything. Rejected in favour of chaining `preBalanceTx`.

### Keep `preBalanceTx` as a single, overwriting slot
`Unfrack` would then collide with other pre-balance transformers. `MintValidatorExtender` already sets one
internally (to remove an inline script when a reference script is used), and a user's own `preBalanceTx(...)`
silently replaces it today. Chaining fixes that bug too.

## 8. Consequences

**Positive**
- Change outputs stay below `maxValSize` and remain spendable (issue #42).
- ADA payments no longer drag every token along.
- Locked min-ada is reclaimed by merging token fragments, and dust is merged.
- The wallet shape is bounded and described by intent (profiles), not by tuning knobs.
- The wallet has several independent UTxOs, a prerequisite (not a solution) for concurrent transactions.
- The change is opt-in and adds no new QuickTx API; the default path must stay unchanged (existing
  `function`/`quicktx` tests act as the guard).
- `preBalanceTx` chaining fixes the lost `MintValidatorExtender` transformer described in §7.
- One algorithm with rules and profiles is a good basis for a CIP.
- Transactions built by dApps don't break a wallet's shape permanently; the wallet's own transactions restore it
  (§4.7).

**Negative / trade-offs**
- More outputs (and, with consolidation, more inputs) make a transaction slightly larger and its fee slightly higher.
- `Lanes` and consolidation read the wallet's UTxOs on every build: one extra backend call, slow for large wallets.
- Behaviour differs from Evolution SDK by default; `EvolutionStrategy` keeps parity available.
- A wallet's preferences only apply to dApp-built transactions once dApps can read them (CIP-30 extension, §13).
- **Behaviour change**: calling `preBalanceTx` twice now runs both functions instead of only the last one. Code that
  relied on replacing an earlier transformer must be adjusted. No usage in this repository does this.
- Unfracking is less discoverable without a dedicated method; Javadoc and docs examples have to cover it.

## 9. Future Work

- **CIP draft**: rules (§4.1), the parameterised algorithm and the profiles as an informational CIP extending CIP-2,
  with the simulation as rationale and Evolution SDK as prior art. Involve the Evolution SDK maintainers early.
- **CIP-30 extension** for change preferences (§13).
- **Tune profile defaults** with the simulator, including a workload with many fragments per transaction (to tell
  `collector()` from `hygiene()`).
- **Simulator extensions**: other coin selection strategies (random-improve), a size-based fee, NFT sends, and
  concurrent submission with in-flight UTxOs.
- **Fallback instead of failure**: if validation after balancing fails, rebuild with a single change output.
- **Size limits**: check `maxValSize` per bundle and `maxTxSize` for the whole transaction (issue #42).
- **TxPlan / YAML**: e.g. `unfrack: hygiene` so that YAML plans can opt in.
- **txflow / TxStream integration**: use `throughput` lanes as intra-address lanes, complementing today's
  address-based `LanePolicy`.

## 10. Test Plan

- **One test class per public type**: `WalletShapeTest` (profiles, builder, validation), `AdaShapeTest` (lanes counting,
  percentages, single), `WalletShapeStrategyTest` (tokens with every ADA shape and profile; base-class guards),
  `PolicyBundlingTest` and `ByteBudgetBundlingTest` (incl. fragments), `EvolutionStrategyTest`.
- **Contract test** for every profile, Evolution and the baselines on a few hundred generated change values: Σ pieces
  equals the input, every piece meets min-ada, no policy repeated in one output, input not modified, deterministic;
  and the generated inputs must actually cause splits.
- **`Unfrack` unit tests** (on a `Transaction`):
  - in-place split and index preservation, several change outputs;
  - small change left untouched;
  - non-change outputs and change with a datum, datum hash or script ref left untouched;
  - what the strategy receives; custom and zero fee reserve;
  - invalid strategy results rejected;
  - consolidation: fragments first, smallest ADA next, limit, single fragment and full bundles skipped, only spent
    addresses, skipped with redeemers or without a supplier.
- **Wallet simulation** (`UnfrackSimulationTest`): replays the §12.8 workloads for every profile, Evolution and the
  baselines, prints the report tables and asserts the properties this ADR relies on (conservation, lane profiles stay
  at their size, stateless strategies grow, no token churn after unfracking a hot UTxO, byte-budget bundling locks
  less ADA, Evolution's spread, consolidation reclaims airdrops).
- **QuickTx tests** (mocked suppliers): default `hygiene()`, `throughput(...)`, `EvolutionStrategy`, without
  `Unfrack` (unchanged output), and two chained `preBalanceTx(...)` calls.
- **Parity:** port selected Evolution SDK scenarios so that `EvolutionStrategy` produces the same split.
- **Integration** (Yaci DevKit): submit an unfracking transaction with consolidation and check that the resulting
  UTxOs are on-chain and spendable.

## 11. Examples: Evolution SDK and CCL

The TypeScript snippets below are taken from Evolution SDK
([`0167cf9`](https://github.com/IntersectMBO/evolution-sdk/tree/0167cf91381eea2c2f33db5ab1396a66b4dbe2da)): its docs,
and its builder tests, which are the only runnable unfrack examples it ships (its `examples/` folder has none). Each
is followed by the proposed CCL equivalent.

The "CCL result" rows come from running the same wallets through `Unfrack` (`EvolutionStrategy`) with QuickTx and mocked
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
        .preBalanceTx(new Unfrack(new EvolutionStrategy()))   // Evolution defaults
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

## 12. Evaluation of Candidate Algorithms

The design was chosen by evaluating five candidate algorithms for splitting change, implemented as
`ChangeSplitStrategy`s and compared in a simulator. This section explains each one and the evidence. Their role in the
design:

| Candidate | Role in the design |
|---|---|
| `EvolutionStrategy` | Available for parity (§4.8) |
| `PercentageSplitStrategy` | Basis of `AdaShape.Percentages`, used by `offline()` |
| `TargetShapeStrategy` | Basis of `AdaShape.Lanes`, used by `hygiene()`, `throughput()`, `collector()`, `dex()` |
| `EqualLanesStrategy` | Not adopted; kept as a simulator baseline in test sources |
| `PaymentSizedStrategy` | Not adopted; kept as a simulator baseline in test sources |

**How to read the examples.** They use mainnet `coinsPerUtxoByte` 4,310 and a Shelley base address. With these,
an ADA-only output needs 0.969750 ADA, a bundle with one token needs 1.146460 ADA, and a bundle with three
single-token policies needs 1.482640 ADA. "Change" is the value a strategy receives, i.e. already without the fee
reserve (§4.2); `Unfrack` later adds the reserve to the largest piece. All numbers were produced by running the
strategies, not calculated by hand.

### 12.1 `EvolutionStrategy` (port of Evolution SDK, kept for parity)

**Idea.** Separate tokens from ADA, and cut large ADA into a fixed "logarithmic" set of sizes: one big piece for
large payments, some medium ones and several small ones.

**Algorithm.** See §4.8. In short:

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

### 12.2 `PercentageSplitStrategy` (basis of `AdaShape.Percentages`)

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

### 12.3 `EqualLanesStrategy` (not adopted)

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

### 12.4 `PaymentSizedStrategy` (CIP-2 self-organisation; not adopted)

**Idea.** From [CIP-2](https://cips.cardano.org/cip/CIP-0002): if every payment of size *v* leaves a change piece of
about *v*, the wallet gradually fills up with UTxOs matching its typical payments.

**Algorithm.** Take the ADA of the non-change outputs, largest first. For each payment create a piece of
**payment + `feeAllowance` + ADA-only min-ada** (default 0.5 + 0.969750 = 1.469750 ADA on top), so that the piece
can pay a similar payment alone, including its fee and a change output. Payments below min-ada, and payments whose
piece would leave a remainder below min-ada, are skipped. The remainder is the last piece; at most `maxPieces` (5)
pieces in total.

The margin matters: with pieces of exactly the payment size, a 10 ADA piece could not pay a 10 ADA payment (fee and
change missing), and the number of UTxOs able to fund a payment alone would stay at 1.0. With the margin it is 150.5
(§12.8).

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

### 12.5 `TargetShapeStrategy` (wallet-aware; basis of `AdaShape.Lanes`)

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
- Does not consolidate an already fragmented wallet; it only stops further growth. `WalletShape` adds
  consolidation for that (§4.5).

**Use it for** general-purpose wallets and services: it gives predictable, bounded concurrency groundwork. This is the
basis of the `Lanes` profiles. As `AdaShape.Lanes` it keeps ADA in one output when there is no `UtxoSupplier`, so a
wallet without a UTxO view never grows.

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
| `PercentageSplitStrategy` → `offline()` | Unbounded (as Evolution) | Byte budget, ADA separate | Evolution defaults | Fragmentation |
| `EqualLanesStrategy` (not adopted) | Unbounded (up to ~2 UTxOs per tx) | Byte budget, ADA separate | Lanes, lane size | Worst fragmentation |
| `PaymentSizedStrategy` (not adopted) | Unbounded (~1 UTxO per tx) | Byte budget, ADA separate | Max pieces, fee allowance | Pieces pile up under largest-first |
| `TargetShapeStrategy` → `Lanes` profiles | **Bounded** at the lane count | Byte budget, ADA separate | Lanes, lane size | `getAll` per transaction |

### 12.8 Simulation

`UnfrackSimulationTest` (with `UnfrackSimulator`) replays wallet workloads through the real `Unfrack`, so
consolidation and the strategy result checks run on every step. It prints the tables below and asserts the
properties this ADR relies on.

Model:

- one address; serial transactions; inputs chosen **largest-first** (CCL's default); fixed fee 0.2 ADA; fee reserve
  2 ADA; payments in ADA to another address;
- "fundable" is the average number of ADA-only UTxOs that could pay the next payment alone (a proxy for concurrency
  potential);
- "token churn" is the share of payments whose selected inputs included a UTxO with tokens;
- "token UTxOs" and "ADA in token UTxOs" are measured at the end.

**ADA-only workloads** (wallet: one 10,000 ADA UTxO, 300 payments).

| Shape / strategy | Fixed 10 ADA: max UTxOs | fundable | Random 1–50 ADA: max UTxOs | fundable | Mixed 2–10 / 100–300 ADA: max UTxOs | fundable |
|---|---|---|---|---|---|---|
| None (today) | 1 | 1.0 | 1 | 1.0 | 1 | 1.0 |
| `EvolutionStrategy` | 361 | 228.0 | 301 | 95.4 | 331 | 214.8 |
| `hygiene()` | 3 | 3.0 | 3 | 2.9 | 3 | 2.8 |
| `throughput(5, 60 ADA)` | 5 | 5.0 | 5 | 5.0 | 5 | 4.6 |
| `throughput(10, 60 ADA)` | 10 | 10.0 | 10 | 10.0 | 10 | 9.2 |
| `collector()` | 2 | 2.0 | 2 | 1.4 | 2 | 1.9 |
| `dex()` | 3 | 3.0 | 3 | 2.9 | 3 | 2.8 |
| `offline()` | 361 | 228.0 | 301 | 95.4 | 331 | 214.8 |
| `minimal()` | 1 | 1.0 | 1 | 1.0 | 1 | 1.0 |
| baseline EqualLanes (5 × 10 ADA) | 625 | 427.4 | 470 | 90.6 | 482 | 299.7 |
| baseline EqualLanes (5 × 60 ADA) | 125 | 118.4 | 125 | 89.6 | 116 | 84.4 |
| baseline PaymentSized | 301 | 150.5 | 205 | 52.6 | 288 | 77.5 |

`offline()` equals Evolution for ADA-only change. Average inputs per transaction stayed between 1.0 and 1.3, except
EqualLanes with 10 ADA lanes (up to 1.73).

**Token workloads.**

| Shape / strategy | Airdrop¹: token UTxOs | ADA in token UTxOs | Hot UTxO²: max UTxOs | token churn | ADA in token UTxOs | Small wallet³: final UTxOs | ADA in token UTxOs |
|---|---|---|---|---|---|---|---|
| None (today) | 60 | 99.05 | 1 | **100 %** | 2,183.60 | 1 | 69.56 |
| `EvolutionStrategy` | 60 | 99.05 | 445 | 0 % | 171.97 | 27 | 22.93 |
| `hygiene()` | **5** | **15.24** | 10 | 0 % | 32.06 | 3 | 4.34 |
| `throughput(5, 60 ADA)` | 60 | 99.05 | 12 | 0 % | 32.06 | 3 | 4.34 |
| `collector()` | **5** | **15.24** | 9 | 0 % | 32.06 | 3 | 4.34 |
| `dex()` | 60 | 99.05 | 153 | 0 % | 171.97 | 22 | 22.93 |
| `offline()` | 60 | 99.05 | 308 | 0 % | 32.06 | 8 | 4.34 |
| `minimal()` | 60 | 99.05 | 8 | 0 % | 32.06 | 2 | 4.34 |

¹ Random 1–50 ADA payments from 10,000 ADA; every 5th transaction a new single-token UTxO arrives (60 in total).
² One UTxO with 10,000 ADA and 150 single-token policies, 300 random 1–50 ADA payments.
³ One UTxO with 150 ADA and 20 single-token policies, 40 random 1–3 ADA payments.

**Reading.**

- Every shape, including `minimal()`, fixes the hot-UTxO problem: after the first transaction, payments no longer
  move tokens.
- Only consolidation (`hygiene()`, `collector()`) touches airdropped tokens: largest-first never selects those small
  UTxOs, so without consolidation they stay scattered with their min-ada locked.
- One bundle per policy (Evolution, `dex()`) locks 5× more ADA than byte-budget bundling. For `dex()` that is the
  intended price of policy separation.
- The `Lanes` profiles stay at their configured size; the stateless strategies (Evolution, `offline()`, baselines)
  create many fundable UTxOs only by fragmenting the wallet without limit.
- `collector()` and `hygiene()` behave the same in these workloads; a workload with many fragments per transaction is
  needed to tell them apart (§9).

**Caveats.** One address, serial transactions, largest-first selection only, fixed fee, synthetic payments. See §9.

### 12.9 Conclusions

The evaluation supports the decision in §2:

1. **Tokens: byte-budget bundling with ADA always kept separate** is better than Evolution's behaviour in every case
   simulated. It is the default of `WalletShape`.
2. **ADA: wallet-aware lanes** are the only approach whose wallet shape stays bounded. `AdaShape.Lanes` is used by
   every profile except `offline()` and `minimal()`.
3. **Consolidation is needed**: splitting alone never reclaims scattered token UTxOs, because coin selection never
   picks them.
4. **One algorithm with profiles**: the candidates differ in parameters that fit different intents, not in ways that
   need separate algorithms. `EvolutionStrategy` is available for parity; `PaymentSizedStrategy` and
   `EqualLanesStrategy` are not adopted.

## 13. Outlook: CIP-30 Extension

This is not part of this ADR's scope; it shows how the groundwork could be used later.

In dApp-built transactions the wallet only signs (§4.7). A small CIP-30 extension would let the wallet share
**preferences for the user's own change**. It would not say what kind of user this is: someone who swaps on a DEX in
the morning and buys NFTs in the evening returns the same preferences, because they only describe how to treat the
user's change, never the dApp's own outputs.

```js
const api = await window.cardano.someWallet.enable({ extensions: [{ cip: XXXX }] });
const prefs = await api.cipXXXX.getChangePreferences();
// { "version": 1,
//   "tokenBundles":  { "maxBytes": 1000 },
//   "consolidation": { "allowed": true, "maxExtraInputs": 3, "maxUtxoLovelace": "5000000" },
//   "reservedUtxos": [ { "txHash": "8f3a…", "index": 1 } ] }
```

- **Without the extension** a dApp applies the baseline rules (§4.1), i.e. `minimal()`.
- **`tokenBundles`** tunes how the user's tokens are bundled in the change.
- **`consolidation`** is explicit consent to add the user's small UTxOs as extra inputs, which a dApp must never do by
  default.
- **`reservedUtxos`** are UTxOs the dApp should not spend (collateral, the wallet's own lanes).
- **No lanes and no persona**: creating lanes stays the job of the wallet's own transactions, which also restore its
  shape after dApp transactions (§4.7).

In CCL a dApp backend would map the preferences to a `WalletShape`, e.g. `WalletShape.minimal()` with
`ByteBudgetBundling(maxBytes)` and the given `Consolidation`, and exclude `reservedUtxos` from coin selection.
