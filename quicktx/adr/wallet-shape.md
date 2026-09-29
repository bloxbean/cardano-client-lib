# Wallet Shape

**Status**: Proposed
**Date**: 2026-09-24
**Issue**: https://github.com/bloxbean/cardano-client-lib/issues/678 (related: https://github.com/bloxbean/cardano-client-lib/issues/42, https://github.com/bloxbean/cardano-client-lib/issues/279)
**Modules**: `quicktx`, `function`

## 1. Context

QuickTx always returns change as a **single output** at the sender's change address
(`InputBuilders.buildInputs` → one `ChangeOutput`; `FeeCalculators` deducts the fee from it;
`ChangeOutputAdjustments` tops it up to min-ada). Every token the wallet keeps ends up in that one output, while
tokens and small amounts received from others stay in their own small UTxOs.

**The wallet shape is not cosmetic.** Depending on how its UTxOs are laid out, a wallet that holds enough funds can
become unable to make its next transaction, or pay far more in fees than necessary. This ADR has two goals:

1. **The user can always create their next transaction**, without a separate clean-up tool (such as the UnFrack.It
   website) and without someone sending them ADA.
2. **The user doesn't overpay fees** because of how their UTxOs are laid out.

### 1.1 When a wallet can no longer transact

The ledger limits (mainnet) that matter: a transaction can't exceed `maxTxSize` (16,384 bytes), an output's value
can't exceed `maxValSize` (5,000 bytes), and every output needs min-ada (`coinsPerUTxOByte` × (160 + output size)).
Measured CBOR sizes: a transaction input takes about **36 bytes**, so one transaction can spend at most about
**440 UTxOs**; a single-token policy in an output takes about **39 bytes**, so one output holds at most about
**128 single-token policies**.

| Wallet state | What fails | Severity | How the default shape (Part A) prevents it |
|---|---|---|---|
| Tokens keep accumulating in the one change output, and a payment has to combine token-heavy UTxOs | The change would exceed `maxValSize`, so every such payment fails (issue #42) | **Stuck for a builder with one change output**, which is what QuickTx has today; a builder that splits the change could still transact | Token bundles are capped at 1,000 bytes |
| Many small UTxOs, e.g. 1,000 × 1 ADA from years of small payments | Sending 500 ADA needs about 500 inputs, more than fits into 16 KB, although 1,000 ADA is there | **Degraded**: that payment is impossible in one transaction; the wallet needs several consolidation transactions first | Consolidation merges small UTxOs a few at a time in normal transactions; one ADA output never creates new dust |
| One token spread over hundreds of UTxOs | Sending all of it at once needs too many inputs | **Degraded** | Consolidation merges token fragments; bundling keeps a token together |
| All ADA sits as min-ada in a single token UTxO | No fee can be paid: moving the token needs the same min-ada again | **Destroyed** until someone sends ADA in | ADA is always kept in its own output instead of being spread into token UTxOs |
| `coinsPerUTxOByte` increases | Existing token UTxOs are below the new min-ada; spending them needs extra ADA | **Degraded or destroyed**, as above | Free ADA in its own output; merging bundles saves the per-output overhead (about 1 ADA per merged pair) |
| No ADA-only UTxO | Smart-contract transactions need collateral handling (collateral return) | **Degraded** for script transactions | An ADA-only output always exists |

CCL doesn't check `maxTxSize` or `maxValSize` when it builds a transaction; it only reads them from the protocol
parameters. So today these failures only show up when the node rejects the transaction.

### 1.2 When a user overpays fees

The fee is 44 lovelace per byte plus 155,381 lovelace per transaction.

| Cause | Extra cost | How the default shape prevents it |
|---|---|---|
| **Every payment drags all tokens along** in the change | About 39 bytes per single-token policy: a wallet with 100 tokens pays about 0.17 ADA more **per payment** | Payments only spend ADA-only UTxOs; tokens stay in their bundles |
| **Many small inputs** | About 36 bytes per input: 100 dust inputs cost about 0.16 ADA more per payment | Dust is merged over time, in normal transactions |
| **Separate clean-up transactions** (the UnFrack.It approach) | Each costs at least the 0.155 ADA base fee plus its size | Merging inside a normal transaction costs only the extra input, about 0.0016 ADA per UTxO |

### 1.3 Other problems

- **ADA locked next to scattered tokens**: each token UTxO holds its own min-ada (about 1.1–1.5 ADA). Coin selection
  never picks these small UTxOs, so they stay: 60 airdrops leave about 99 ADA idle (Appendix B.8).
- **One transaction in flight**: every transaction spends the same UTxO, so the next one waits. This only matters for
  server-side services; see §8.1.

A wallet that holds only ADA can still collect dust (the second row of §1.1), but none of the token problems.

**Terminology.** In the Cardano ecosystem, repairing such a wallet is known as "unfracking", after
[UnFrack.It](https://unfrack.it/), a website that repairs wallets with separate clean-up transactions;
[Evolution SDK](https://github.com/IntersectMBO/evolution-sdk) uses the same term for its `unfrack` build option. This
ADR avoids it: the term is specific jargon and describes the problem rather than the goal. It speaks of the **wallet
shape**, i.e. how a wallet's UTxOs are laid out, and of keeping it healthy so that no repair is needed.

**Two audiences.** The same problems look very different depending on who owns the wallet:

- **Retail users and dApps.** A person uses a wallet for everything: a DEX swap today, an NFT purchase tomorrow, a
  payment to a friend the day after, often through transactions built by dApps. They must never have to think about
  UTxO layout, choose profiles, or "defragment" their wallet by hand, the way old Windows users had to defragment
  their hard drives. Keeping the wallet healthy has to happen automatically, in every transaction, with one behaviour.
- **Server-side applications.** Payout services, bots and market makers own their wallets, build all their
  transactions themselves, and have specific needs (parallel payments, one token policy per UTxO, signing without
  access to the UTxO set). Their developers are technical and can configure the shape.

This ADR is therefore split: **Part A** defines the default wallet shape for retail users and dApps, **Part B** the
configurable shapes for server-side applications.

## 2. Decision

1. **Part A: one default wallet shape, no configuration.** `new DefaultWalletShaper()` keeps the change healthy with a
   single, simple behaviour that fits every retail transaction, whatever the dApp or activity (§4):
   - the user's tokens are always kept apart from ADA and grouped into compact bundles;
   - ADA stays in one output;
   - nothing else: no wallet reads, no extra inputs.

   `DefaultWalletShaper.withConsolidation()` adds one thing for wallets building their own transactions: it merges a
   few small UTxOs (dust, scattered tokens) into each transaction, so the wallet heals itself over time (§4.2).
2. **Part B: configurable shapes for server-side applications.** `new CustomWalletShaper(WalletShape)` with a
   `WalletShape` made of an `AdaShape`, a `TokenShape` and a `Consolidation`, plus a few named profiles for common
   server-side needs (§9), and a `WalletShapeStrategy` interface for custom algorithms (§10).
3. **Same transaction, before balancing.** A shaper reshapes the change of the transaction being built; there is no
   separate clean-up transaction. It runs as a pre-balance transformer, so all existing fee and min-ada balancing is
   reused (§6.4). Without a shaper, transaction building is unchanged, byte for byte.
4. **Reuse the existing `preBalanceTx(TxBuilder)` hook** and make it **chain** transformers (`andThen`) instead of
   overwriting the previous one. Every shaper implements the marker interface `WalletShaper`. With a `WalletShaper`,
   the change is always kept as its own output, even with `mergeOutputs(true)`; merging then applies to payment
   outputs only (§6.5).
5. **Aim for a CIP on the default.** The rules of Part A are written so they can be proposed as an informational CIP
   that every transaction builder (SDKs, wallets, dApps) follows by default, as an extension of
   [CIP-2](https://cips.cardano.org/cip/CIP-0002) (§13). No CIP-30 extension is needed (§13.1).

```java
// Part A: retail users and dApps
quickTxBuilder.compose(tx)
    .preBalanceTx(new DefaultWalletShaper())
    .withSigner(signer)
    .completeAndWait();

// Part B: server side
quickTxBuilder.compose(tx)
    .preBalanceTx(new CustomWalletShaper(WalletShape.throughput(10, Amount.ada(60))))
    .withSigner(signer)
    .completeAndWait();
```

## 3. A Healthy Wallet

**What the user gets.** A healthy wallet:

- can always build its next transaction: no output near `maxValSize`, no payment that needs hundreds of inputs, and
  spendable ADA for the fee;
- pays no fees for tokens or dust it doesn't use;
- has ADA that can be spent without touching any token;
- keeps its tokens in a few compact bundles, each far below `maxValSize`, holding only their min-ada;
- has no idle dust and no ADA locked in scattered token UTxOs (with consolidation);
- has a small number of UTxOs that stays stable instead of growing with every transaction.

Two typical wallets, before and after, with `DefaultWalletShaper.withConsolidation()` (simulator, Appendix B.8; 300
random 1–50 ADA payments):

| Starting point | Without a shaper | With the default shape and consolidation |
|---|---|---|
| One UTxO with 10,000 ADA and 120 single-token policies | Still 1 UTxO. Every payment re-sends all 120 tokens (4,684 bytes, 316 bytes below `maxValSize`). | 7 UTxOs: 1 ADA-only and 6 token bundles holding 26.04 ADA of min-ada. Payments move no tokens. |
| 10,000 ADA, plus a single-token airdrop every 5th transaction (60 in total) | 61 UTxOs: 1 ADA UTxO and 60 token UTxOs locking 99.05 ADA. | 6 UTxOs: 1 ADA-only and 5 token bundles locking 15.24 ADA; about 84 ADA is spendable again. |

**Rules.** Every shape in this ADR follows these rules; Part A's are the core of a possible CIP:

1. **Tokens are never mixed with spendable ADA.** Token bundles hold exactly their min-ada; ADA goes to ADA-only
   outputs. Only an amount below the ADA-only min-ada is added to the last bundle.
2. **Outputs stay small.** Token bundles are bounded by a byte budget (or a per-policy asset count), far below
   `maxValSize`.
3. **Value is conserved and every output is valid.** The pieces sum exactly to the change; each piece meets min-ada.
4. **The wallet shape is bounded.** A shaper moves the wallet towards its shape and stops there, instead of creating
   more UTxOs with every transaction.
5. **The wallet stays able to transact.** No rule may create a state from §1.1: outputs stay far below `maxValSize`,
   ADA is never dissolved into token UTxOs, and repairs (consolidation) happen a few UTxOs at a time so that every
   transaction stays small.

---

# Part A — Default Wallet Shape (retail users and dApps)

## 4. The Default Shape

### 4.1 Behaviour

`new DefaultWalletShaper()`:

1. **Tokens**: all tokens in the change are grouped into bundles of at most 1,000 bytes of CBOR (first-fit decreasing:
   small policies share a bundle, a policy that fits is never split, a larger one is chunked). Each bundle holds
   exactly its min-ada.
2. **ADA**: the rest of the change stays in **one** ADA-only output. If it is below the ADA-only min-ada, it is added
   to the last bundle instead.
3. Nothing else. It doesn't read the wallet's UTxO set and doesn't add inputs, so the user signs no input they didn't
   expect, and it works for every transaction builder, including dApps building for a CIP-30 wallet.

### 4.2 With consolidation

`DefaultWalletShaper.withConsolidation()` does the same and, before shaping, merges up to **3** small UTxOs (at most
5 ADA each) of the sender into the transaction: ADA dust, and token *fragments* when there are at least two of them
(§6.2). Over a few transactions this reclaims the ADA locked next to scattered tokens, like an automatic background
defragmentation. It reads the wallet's UTxOs and adds inputs, so it is meant for **wallets building their own
transactions**; dApps use the plain default.

### 4.3 One default for every retail user

A single default is possible because a retail user's needs don't depend on what they do:

- The shape only concerns the user's **change**. The transaction's purpose (swap, purchase, listing) is expressed by
  the dApp's own outputs, which a shaper never touches. So a user who swaps today and buys an NFT tomorrow needs the
  same treatment of their change both times.
- Retail users don't need parallel transactions, so there is no need for several ADA outputs ("lanes"); one ADA output
  is simplest and never grows the wallet.
- The default keeps the wallet **always able to transact and to recover** (§1.1):
  - no output can approach `maxValSize`, because bundles are capped at 1,000 bytes;
  - an ADA payment never carries tokens, and a token transfer moves only that token's bundle, so transactions stay
    small;
  - a wallet in a bad state recovers by itself: a hot UTxO is split into bundles by the next transaction, and with
    consolidation, a fragmented wallet is merged a few UTxOs per transaction, so each transaction stays small.
- Two transactions built by different parties apply the same rules, so the wallet converges to the same shape no
  matter who built them. The wallet's own transactions (with consolidation) repair anything a builder without a shaper
  left behind.

So the retail user never chooses anything: dApps apply `new DefaultWalletShaper()`, wallets apply
`DefaultWalletShaper.withConsolidation()` to their own transactions.

### 4.4 Evidence

Simulator (Appendix B.8), 300 payments:

| Workload | Without a shaper | `DefaultWalletShaper()` | `withConsolidation()` | Evolution SDK algorithm |
|---|---|---|---|---|
| ADA only (fixed, random, mixed payments): max UTxOs | 1 | 1 | 1 | 301–361 |
| Hot UTxO (10,000 ADA + 120 tokens): tokens moved per payment | 120 | 0 after the first | 0 after the first | 0 after the first |
| Hot UTxO: ADA locked in token UTxOs | all of it | 26.04 | 26.04 | 137.58 (one output per policy) |
| Airdrop every 5th transaction: token UTxOs / ADA locked | 60 / 99.05 | 60 / 99.05 | **5 / 15.24** | 60 / 99.05 |
| Small wallet (150 ADA + 20 tokens): final UTxOs | 1 (all tokens moved every time) | 2 | 2 | 27 |

### 4.5 Why not something else as the default

- **The Evolution SDK algorithm** (Part B: `TieredSplitStrategy`) is stateless like the default, but it grows the
  wallet without limit (361 UTxOs after 300 fixed payments), stores ADA next to tokens when less than 100 ADA is left,
  and creates one output per token policy, so a change with 150 policies alone can push a transaction towards
  `maxTxSize`. It fails "always able to recover".
- **ADA lanes** (several ADA-only outputs) help only parallel server-side payments and need the wallet's UTxO set;
  retail users gain nothing from them (Part B).
- **No shaper** keeps all four problems of §1.

## 5. API

### `function` module — `com.bloxbean.cardano.client.function.walletshape`

| Type | Role |
|---|---|
| `WalletShaper` | Marker interface (`extends TxBuilder`) for pre-balance transformers that shape the wallet; QuickTx keeps the change separate when one is used (§6.5) |
| `DefaultWalletShaper` | Part A. `new DefaultWalletShaper()`, `DefaultWalletShaper.withConsolidation()`; optional `feeReserve` |
| `CustomWalletShaper` | Part B. `new CustomWalletShaper(WalletShape)` or `new CustomWalletShaper(WalletShapeStrategy)`; optional `feeReserve` |
| `WalletShape`, `AdaShape`, `TokenShape`, `Consolidation` | Part B configuration (§9); `TokenShape` implementations `ByteBudgetBundling` and `PolicyBundling` are also used by Part A |
| `WalletShapeStrategy`, `WalletShapeRequest`, `AbstractWalletShapeStrategy` | Part B custom algorithms (§10) |
| `ConfigurableWalletShapeStrategy` | The algorithm behind every `WalletShape`; `DefaultWalletShaper` uses it with the default shape |
| `TieredSplitStrategy` | Part B: the Evolution SDK algorithm, for parity (§10.2) |

**Naming.** All types use the *wallet shape* vocabulary: a `WalletShape` is made of an `AdaShape`, a `TokenShape` and a
`Consolidation`; a `WalletShapeStrategy` implements it; a `WalletShaper` applies it to a transaction.

### `quicktx` module

`QuickTxBuilder.TxContext#preBalanceTx(TxBuilder)` appends the function to the existing pre-balance transformer
(`andThen`) instead of replacing it. Functions run in the order they were added. No new QuickTx method is added.

When a `WalletShaper` is passed to `preBalanceTx`, `TxContext` keeps the change as its own `ChangeOutput`, also with
`mergeOutputs(true)` (§6.5). Other pre-balance transformers don't change how outputs are merged. In
`TxBuilderContext` (`function` module), a new `mergeChange` flag controls whether the change may be merged into an
existing output at the change address; it defaults to the value of `mergeOutputs`, so existing behaviour is unchanged.

The shapers live in `function`, not `quicktx`, so they can also be composed manually with the low-level `TxBuilder`
API.

## 6. How a Shaper Works

This section applies to Part A and Part B.

### 6.1 Fee reserve and output ordering

CCL's fee calculator deducts the fee from the **max-coin output at the fee payer address**, and
`ChangeOutputAdjustments` fails if **more than one** change output ends up below min-ada. To fit both without changing
them:

- the shaper splits `change − feeReserve` (default 2 ADA) and adds the reserve back to the **largest** piece. That piece
  carries the fee, and all other pieces still meet min-ada after balancing;
- the fee-bearing piece replaces the original change output **at the same index**, and the other pieces are
  **appended**, so the indexes of all other outputs are unchanged;
- the result is verified: Σ pieces must equal the change and each piece must meet min-ada, otherwise
  `IllegalStateException` is thrown, so a faulty custom strategy cannot create an invalid transaction.

**Open question:** the default reserve size (2 ADA). Appendix A.6 (F1) compares the options.

### 6.2 Consolidation

With consolidation (`DefaultWalletShaper.withConsolidation()` or a `WalletShape` with `Consolidation`), the shaper adds
small UTxOs of the change address as extra inputs before the change is shaped:

1. Only an address that **already has an input** in the transaction is consolidated, so no new signer is needed.
2. Candidates are UTxOs at that address that are not inputs yet, have no datum or script ref, and hold at most
   `maxUtxoLovelace`:
   - **ADA-only dust** is always a candidate;
   - **token UTxOs** are candidates only if they are **fragments** (`TokenShape.isFragment`: less than half the byte
     budget, or fewer than half of `bundleSize` assets), and only when there are **at least two** of them. A single
     fragment would just be re-bundled, and a full bundle would be moved again and again.
3. Token fragments go first (they hold locked min-ada), then the smallest UTxOs; at most `maxExtraInputs`.
4. Their value is added to the first change output of that address, then the change is shaped as usual. The extra
   inputs' size is part of the normal fee calculation.

Consolidation is **skipped** when the transaction has redeemers (extra inputs change input order and therefore
redeemer indexes) and when there is no `UtxoSupplier`.

### 6.3 Token bundling

| | `ByteBudgetBundling(maxBundleBytes)` (default) | `PolicyBundling(bundleSize)` |
|---|---|---|
| Rule | Bundles up to `maxBundleBytes` (1,000) of CBOR, first-fit decreasing; a policy that fits is never split; a larger policy is chunked | One bundle per policy, chunks of `bundleSize` (10) assets |
| Mixes policies | Yes | Never |
| Change with 150 single-token policies (more than fits into one output) | 7 outputs, 32.06 ADA min-ada | 150 outputs, 171.97 ADA min-ada |
| Fragment (for consolidation) | Uses less than half the budget | Holds fewer than half of `bundleSize` assets |

Part A always uses `ByteBudgetBundling(1000)`; `PolicyBundling` is a Part B option.

### 6.4 Build pipeline placement

```
per-tx complete()        outputs built, inputs selected, one ChangeOutput per sender, deposits resolved;
                         with mergeOutputs(true), outputs to the same address are merged here
                         (the change too, unless a WalletShaper is used, §6.5)
preBalanceTx(...)        existing, now chained (← CHANGED): user/extender transformers, then the shaper:
                           1. consolidation adds small UTxOs as inputs, if enabled (← NEW)
                           2. the change is shaped (← NEW)
collateral               existing
script cost evaluation   existing; sees the final output set
balanceTx(feePayer)      existing: FeeCalculators → ChangeOutputAdjustments → collateral balance
postBalanceTx(...)       existing
```

The shaper runs before balancing because:

- at that point each `ChangeOutput` still holds the full surplus, so no fee has to be restored;
- script cost evaluation runs afterwards, so validators that inspect outputs are evaluated against the final
  outputs;
- fee calculation naturally includes the size of the extra inputs and outputs.

### 6.5 Interaction with `mergeOutputs`

`mergeOutputs(true)` (QuickTx default `false`; `TxBuilderContext` default `true` in the low-level API) merges outputs
with the same address into one. It is applied **while the outputs are built**, inside `tx.complete()`, not as a
separate step:

- payment outputs to the same address are merged (`OutputBuilders`);
- the change is added to the first existing output at the change address instead of a new `ChangeOutput`
  (`InputBuilders`);
- deposit refunds are merged into an existing output (`DepositResolvers`).

So merging always happens **before** the shaper. Without special handling, the result of combining them depends on
the transaction:

| Configuration | Result without special handling (QuickTx, one UTxO with 1,000 ADA and 2 tokens, 10 ADA payment) |
|---|---|
| `mergeOutputs(true)` + a shaper, and the transaction also pays to the sender's own address | 2 outputs: the change is merged into the plain payment output to the sender, which is not a `ChangeOutput`, so the shaper does nothing. ADA and tokens stay mixed. |
| `mergeOutputs(true)` + a shaper, no other output to the sender's address | Several outputs at the sender's address: the shaper splits the change, so the outputs are not merged as requested. |
| A shaper, then `OutputMergers.mergeOutputsForAddress(sender)` in `postBalanceTx` | 2 outputs: the shaping is undone, and the fee was calculated for more outputs, so it is slightly too high. |

Of the first two, only the first is a real conflict, because `mergeOutputs(true)` does two different things and only
one of them clashes with a shaper:

- **merging payment outputs** to the same address (e.g. three payments to one recipient become one output) doesn't
  touch the change and works fine together with a shaper;
- **merging the change into another output** hides it: the shaper then sees one output with payment, change and tokens
  mixed, can't tell which part is change, and does nothing.

Wanting merged payments *and* a shaped change is legitimate, so the combination is supported. The rules:

1. **With a `WalletShaper`, the change is never merged.** When `preBalanceTx(...)` receives a `WalletShaper`, `TxContext`
   sets `mergeChange(false)` on the `TxBuilderContext`, and `InputBuilders` then always creates a separate
   `ChangeOutput`. `mergeOutputs(true)` still merges payment outputs and deposit refunds (refunds are merged into the
   `ChangeOutput`, which is harmless). The rule is: *`mergeOutputs` applies to payments; the change is shaped by the
   `WalletShaper`.*
2. **Only transformers that declare it.** Other pre-balance transformers (`MintValidatorExtender`, user code that sets
   metadata or redeemer execution units) don't touch outputs, so they don't change how outputs are merged. The check is
   on the `WalletShaper` interface, so a custom wallet-shaping transformer gets the same treatment by implementing it.
3. **A shaper warns when the change is gone.** If it finds no `ChangeOutput` in a context where `mergeChange` is `true`
   (the low-level API, or a `WalletShaper` wrapped in a lambda that QuickTx can't recognise), it logs a warning that the
   change was merged into another output and can't be shaped. It doesn't throw.
4. **Merging after a shaper produces a warning.** `OutputMergers.mergeOutputsForAddress(...)` returns a recognisable
   `TxBuilder`. If one is added through `preBalanceTx` or `postBalanceTx` in a `TxContext` that also has a
   `WalletShaper`, QuickTx logs a warning that merging outputs after shaping will probably undo it. It is not rejected,
   because the merger may target another address.
5. **Documentation**: the Javadoc of `mergeOutputs(...)`, `WalletShaper`, the shapers and `OutputMergers` states rule 1.

## 7. Rules and Edge Cases

- Only outputs that are `instanceof ChangeOutput`, have a positive coin, and carry **no** datum, datum hash or
  script ref are changed. User payment outputs are never touched.
- Change at or below `feeReserve`, and change the shape cannot afford to split, is left unchanged.
- **Fee payer ≠ sender**: the sender's change is shaped, and the fee is still taken from the fee payer's output.
- **Balancing adds inputs later** (min-ada top-up): the extra value merges into the largest piece. This is
  acceptable.
- **`mergeOutputs(true)`**: with a `WalletShaper`, only payment outputs are merged; the change stays separate (§6.5).
  In the low-level API, where `mergeChange` defaults to `mergeOutputs` (`true`), the change may be merged into a user
  output, so the shaper does nothing and logs a warning.
- **Transactions without inputs** (withdrawal/deregistration funded by a refund) have no `ChangeOutput` yet, so the
  shaper does nothing.
- **Other pre-balance transformers** run in the order they were added. A shaper only touches `ChangeOutput`s and, with
  consolidation, adds inputs; the order only matters if another transformer changes the same.
- **Consolidation**: only at addresses the transaction already spends from; skipped with redeemers or without a
  `UtxoSupplier` (§6.2).
- **In-flight transactions**: consolidation and `Lanes` (Part B) read the UTxOs from the `UtxoSupplier` and can't see
  UTxOs already spent by transactions still in flight.

---

# Part B — Advanced Wallet Shapes (server-side applications)

## 8. When the Default Is Not Enough

Server-side applications own their wallets and build all their transactions themselves. Some need a different shape:

| Need | Shape |
|---|---|
| Several independent workers pay from one wallet at the same time | Several ADA-only lanes: `WalletShape.throughput(n, laneSize)` |
| A protocol or bot expects one token policy per UTxO | `PolicyBundling`: `WalletShape.policyPerUtxo()` |
| Transactions are signed without access to the UTxO set | A stateless ADA split: `WalletShape.stateless()` |
| Anything else | The `WalletShape` builder or a custom `WalletShapeStrategy` |

### 8.1 Concurrency

A wallet shape **does not solve UTxO contention**: several UTxOs are a prerequisite for concurrent transactions, but
choosing uncontended inputs still needs coordination (e.g. txflow) or a dedicated selection strategy. Evolution SDK
does not attempt this either. Concurrency is not a goal of this ADR; one Part B shape can help:

| Need | Primary tool | Where a wallet shape helps |
|---|---|---|
| One process sends many transactions quickly | **Transaction chaining** (txflow): each transaction spends an output of the previous one before it confirms; works even with a single UTxO | Not needed |
| Several independent builders (workers, machines) share one wallet without shared state | Coordination between builders | `throughput(n, laneSize)` keeps `n` independent lanes, one per builder |

## 9. WalletShape Configuration

`new CustomWalletShaper(WalletShape shape)` shapes the wallet towards a `WalletShape` with three dimensions.

### 9.1 `AdaShape`

**`Lanes(count, laneSize)`** keeps `count` ADA-only UTxOs ("lanes") of at least `laneSize` at the change address and
creates only the lanes that are missing:

1. Load the change address' UTxOs from the `UtxoSupplier`. Count the **existing lanes**: ADA-only UTxOs of at least
   `laneSize`, without datum or script ref, not spent by this transaction.
2. `missing = count − existing`, limited by how many lanes the ADA can fund.
3. If at most one lane is missing, keep one ADA output (it can be that lane). Otherwise create `missing − 1` lanes of
   exactly `laneSize` and put the rest in the last lane.

`laneSize` below the ADA-only min-ada is raised to min-ada. Without a `UtxoSupplier` the ADA stays in one output, so a
wallet never grows without bound. Because lanes are counted from the wallet's current state, lanes spent by other
transactions are recreated by the next one.

**`Percentages(threshold, percentages)`** splits ADA by the percentages (last slice gets the rounding remainder) when it
is at least `threshold` and the smallest slice meets min-ada; otherwise one output. It never reads the wallet, but it
re-splits every large change and so keeps growing the wallet (Appendix B.8).

**`Single()`** keeps ADA in one output (the Part A default).

### 9.2 `TokenShape` and `Consolidation`

`TokenShape` is `ByteBudgetBundling(maxBundleBytes)` or `PolicyBundling(bundleSize)` (§6.3).
`Consolidation(maxExtraInputs, maxUtxoLovelace)` is `none()`, `opportunistic(n)` (UTxOs up to 5 ADA) or
`opportunistic(n, maxUtxo)` (§6.2).

### 9.3 Profiles

| Profile | ADA | Tokens | Consolidation | For |
|---|---|---|---|---|
| `throughput(n, laneSize)` | `Lanes(n, laneSize)` | `ByteBudgetBundling(1000)` | none | Parallel payments by independent workers; pick `laneSize` above the typical payment plus fee |
| `policyPerUtxo()` | `Single()` | `PolicyBundling(10)` | none | Protocols and bots that spend a specific token and expect it alone in a UTxO; locks more min-ada (120 policies: 137.58 ADA vs 26.04) |
| `stateless()` | `Percentages(100 ADA, 50/15/10/10/5/5/5)` | `ByteBudgetBundling(1000)` | none | Signing without access to the UTxO set; grows the wallet, so only when nothing else works |

Evidence (Appendix B.8): `throughput(10, 60 ADA)` keeps 10 ADA-only UTxOs, and all 10 can pay a 1–50 ADA payment alone
(average 10.0), with 1.03 change outputs per transaction.

Everything else is a builder call, e.g. a token treasury that reclaims locked ADA aggressively:

```java
WalletShape shape = WalletShape.builder()
        .ada(AdaShape.single())
        .tokens(new ByteBudgetBundling(2000))
        .consolidation(Consolidation.opportunistic(20, Amount.ada(10)))
        .build();
```

### 9.4 Configuration reference

| Type | Parameter | Default | Meaning |
|---|---|---|---|
| `DefaultWalletShaper`, `CustomWalletShaper` | `feeReserve` | 2 ADA | Kept on the largest piece to pay the fee (§6.1) |
| `WalletShape` | `ada` | `Single()` | ADA shape (§9.1) |
| | `tokens` | `ByteBudgetBundling(1000)` | Token bundling (§6.3) |
| | `consolidation` | `none()` | Consolidation (§6.2) |
| `AdaShape.Lanes` | `count`, `laneSize` | — | Wanted ADA-only UTxOs and their minimum size |
| `AdaShape.Percentages` | `threshold`, `percentages` | — | Split above `threshold`; positive, sum to 100 |
| `ByteBudgetBundling` | `maxBundleBytes` | 1,000 | Max CBOR size of a bundle |
| `PolicyBundling` | `bundleSize` | 10 | Max assets of one policy per bundle |
| `Consolidation` | `maxExtraInputs` | 0 (`none()`) | Max UTxOs merged per transaction |
| | `maxUtxoLovelace` | 5 ADA in `opportunistic(n)` | Only UTxOs with at most this much ADA |
| `TieredSplitStrategy` | `subdivideThreshold`, `subdividePercentages`, `bundleSize` | 100 ADA, 50/15/10/10/5/5/5, 10 | §10.2 |

A default `WalletShape` (`WalletShape.builder().build()`) is the Part A default shape.

## 10. Custom Strategies

### 10.1 `WalletShapeStrategy`

For needs no `WalletShape` covers, a custom algorithm implements `WalletShapeStrategy`:

```java
public interface WalletShapeStrategy {
    List<Value> split(WalletShapeRequest request);
}
```

The request carries the change address, the change (minus the fee reserve), the protocol parameters, the read-only
transaction and the `UtxoSupplier`. `AbstractWalletShapeStrategy` is a base for strategies that keep ADA apart from
tokens and only split ADA differently. The shaper verifies every result (§6.1). A custom strategy runs without
consolidation: `new CustomWalletShaper(strategy)`.

### 10.2 `TieredSplitStrategy` (Evolution SDK algorithm)

A faithful port of Evolution SDK's `createUnfrackedChangeOutputs`, for behaviour identical to Evolution SDK. Named after
what it does: it splits large ADA change into fixed **tiers** of sizes.

1. **Change is ADA only**
   - If `ada < subdivideThreshold`, return one output.
   - Otherwise, if the smallest percentage slice is at least the ADA-only min-ada, split ADA by
     `subdividePercentages` (the last slice takes the rounding remainder). If not, return one output.
2. **Change has tokens**
   - Group the tokens by policy and chunk each policy into bundles of at most `bundleSize` assets.
   - Give each bundle its min-ada.
   - `remaining = ada − Σ bundle min-ada`. If it is negative, return one output.
   - If `remaining ≥ subdivideThreshold` and it covers the ADA-only min-ada, return the bundles plus the ADA,
     subdivided as in step 1 when that is affordable, or as a single ADA output when it isn't.
   - Otherwise **spread** `remaining` evenly across the bundles; the last bundle takes the remainder.

Defaults: `subdivideThreshold` 100 ADA, `subdividePercentages` 50/15/10/10/5/5/5, `bundleSize` 10. Evolution also
declares `isolateFungibles` and `groupNftsByPolicy`, but its change-creation path never reads them, so they are
omitted. Its weaknesses (ADA spread into token bundles, one output per policy, unbounded growth) are analysed in
Appendix B.1; Appendix A compares it with Evolution SDK on Evolution's own examples.

---

## 11. Alternatives Considered

### Make the Evolution SDK algorithm the default
Stateless and known from another SDK, but it grows the wallet without limit and can itself create transactions close
to the size limits (§4.5). Kept as a Part B option (`TieredSplitStrategy`).

### Profiles for retail users
Retail users would have to pick a profile ("everyday", "collector", "DEX user", …), although their activity changes
from day to day and the treatment of their change doesn't depend on it (§4.3). Rejected in favour of one automatic
default; profiles are a Part B feature for server-side applications.

### Separate clean-up transaction (UnFrack.It style)
Requires an extra transaction, an extra fee, a wait for confirmation, and for retail users a manual action. Rejected in
favour of shaping and consolidating inside every transaction.

### Offer several algorithms as public options
Five candidate algorithms were evaluated (Appendix B): the Evolution SDK algorithm, a percentage split, equal lanes,
payment-sized pieces per CIP-2, and wallet-aware lanes. Every algorithm that reshapes each change without looking at
the wallet keeps growing it (hundreds of UTxOs after 300 payments). Offering all of them would give users
configurations known to fragment their wallets, and would be hard to take back in a library. Rejected in favour of one
default (Part A) and one configurable algorithm (Part B).

### One algorithm without an interface
A closed implementation would force users with special needs to fork CCL. Rejected in favour of `WalletShapeStrategy`
as an escape hatch.

### Reject `mergeOutputs(true)` together with a shaper
Simple and safe, but it forbids a legitimate combination (merged payments, shaped change) and would turn working
configurations into build errors. Rejected in favour of keeping the change separate (§6.5).

### Keep the change separate for any pre-balance transformer
Most pre-balance transformers (`MintValidatorExtender`, metadata, redeemer execution units) don't touch outputs.
Changing how outputs are merged for all of them would change transactions of code that never asked for a shaper.
Rejected in favour of the `WalletShaper` marker.

### Replace `ScriptBalanceTxProviders.balanceTx` with a pluggable balancer
This would duplicate fee calculation, min-ada adjustment, script re-evaluation and collateral balancing, and every
balancer would have to reimplement them. Rejected; the pre-balance approach is purely additive.

### Shape the change after balancing (`postBalanceTx`)
No fee recalculation happens after this point. The shaper would have to restore the fee, re-run
`FeeCalculators`/`ChangeOutputAdjustments` and re-evaluate scripts itself. Rejected as fragile.

### Implement the shape as a `UtxoSelectionStrategy`
Selection strategies only choose inputs and have no say in the shape of the change. Consolidation does touch inputs,
but it needs the change shape too, so it lives in the shaper.

### A dedicated `TxBalancer` hook (`TxContext.balancer(...)`)
A new interface and QuickTx method running right after `preBalanceTx` would sit at the same position in the pipeline,
so it would only add API surface. The name would also mislead, because the hook reshapes change and doesn't balance
anything. Rejected in favour of chaining `preBalanceTx`.

### Keep `preBalanceTx` as a single, overwriting slot
A shaper would then collide with other pre-balance transformers. `MintValidatorExtender` already sets one internally
(to remove an inline script when a reference script is used), and a user's own `preBalanceTx(...)` silently replaces it
today. Chaining fixes that bug too.

## 12. Consequences

**Positive**
- Payments no longer fail because the change would exceed `maxValSize` or the transaction `maxTxSize` (issue #42).
- ADA payments no longer drag every token along.
- With consolidation, locked min-ada is reclaimed and dust is merged, automatically.
- Retail users and dApps get one behaviour with no configuration; server-side applications get a configurable shape.
- The wallet shape is bounded; the default never creates more than one ADA output.
- The change is opt-in and adds no new QuickTx method; the default path stays unchanged (existing `function`/`quicktx`
  tests act as the guard).
- `preBalanceTx` chaining fixes the lost `MintValidatorExtender` transformer described in §11.
- The Part A rules are a good basis for a CIP.

**Negative / trade-offs**
- More outputs (and, with consolidation, more inputs) make a transaction slightly larger: about 0.003 ADA per extra
  ADA-only output and 0.0016 ADA per extra input, far less than the savings of §1.2.
- Consolidation and `Lanes` read the wallet's UTxOs on every build: one extra backend call, slow for large wallets.
- Behaviour differs from Evolution SDK by default; `TieredSplitStrategy` keeps parity available.
- With a `WalletShaper`, `mergeOutputs(true)` no longer merges the change into other outputs; it only merges payments
  (§6.5). This is a deliberate, documented exception to "one output per address".
- **Behaviour change**: calling `preBalanceTx` twice now runs both functions instead of only the last one. Code that
  relied on replacing an earlier transformer must be adjusted. No usage in this repository does this.
- The shapers are less discoverable without a dedicated QuickTx method; Javadoc and docs examples have to cover them.

## 13. Future Work

- **CIP for the default**: the Part A rules (§3, §4) as an informational CIP that every transaction builder follows by
  default, extending CIP-2. Its motivation is the two goals of §1 (always able to transact, no overpaid fees) with the
  cases of §1.1 and §1.2, the simulation as rationale, and Evolution SDK as prior art. Involve the Evolution SDK
  maintainers early.
- **Liveness simulator**: property-based tests without a node. An in-memory ledger applies transactions built by the
  real `QuickTxBuilder`; a local Phase-1 validator checks what a node would reject (`maxTxSize`, `maxValSize`, min-ada,
  balance, fee); seeded generators create wallets (dust, token fragments, hot UTxOs near the limits, min-ada-only token
  UTxOs, a `coinsPerUTxOByte` increase) and action sequences (payments up to "send almost everything", token
  transfers, airdrops, dApp transactions without a shaper). After every step it checks whether the wallet can still
  pay, and classifies failures as builder failure, degraded (recoverable in *k* consolidation transactions) or
  destroyed. Yaci DevKit replays a sample to confirm the local validator agrees with the node.
- **Local limit checks**: check `maxTxSize` and `maxValSize` when CCL builds a transaction, so these failures surface
  before submission.
- **Default in QuickTx**: once proven, make `DefaultWalletShaper` the behaviour of QuickTx without any configuration
  (a breaking change for a major version), so retail users get it without anyone opting in.
- **Tune defaults** (bundle budget, consolidation limits, fee reserve) with the simulator.
- **Simulator extensions**: other coin selection strategies (random-improve), a size-based fee, NFT sends, and
  concurrent submission with in-flight UTxOs.
- **Fallback instead of failure**: if validation after balancing fails, rebuild with a single change output.
- **Size limits**: check `maxValSize` per bundle and `maxTxSize` for the whole transaction (issue #42).
- **TxPlan / YAML**: e.g. `walletShape: default` so that YAML plans can opt in.
- **txflow / TxStream integration**: use `throughput` lanes as intra-address lanes, complementing today's
  address-based `LanePolicy`.

### 13.1 Why no CIP-30 extension

An earlier idea was a CIP-30 extension through which a wallet tells a dApp how to shape its change. With a single
default it isn't needed:

- the shape only concerns the user's change, and for a retail user the right treatment of the change is the same for
  every dApp and every activity (§4.3), so there is nothing wallet-specific to communicate;
- if dApp SDKs follow the Part A rules by default (the CIP above), every dApp transaction already leaves a healthy
  change;
- whatever a dApp without a shaper leaves behind, the wallet's own transactions repair (§4.3).

## 14. Test Plan

- **One test class per public type**: `WalletShape` (profiles, builder, validation), `AdaShape` (lanes counting,
  percentages, single), the configurable strategy (tokens with every ADA shape and profile; base-class guards),
  `PolicyBundling` and `ByteBudgetBundling` (incl. fragments), `TieredSplitStrategy`.
- **Default shaper**: tokens bundled and separated from ADA, one ADA output, no wallet reads and no extra inputs;
  `withConsolidation()` merges fragments and dust.
- **Contract test** for the default, every profile, `TieredSplitStrategy` and the evaluation baselines on a few hundred
  generated change values: Σ pieces equals the input, every piece meets min-ada, no policy repeated in one output,
  input not modified, deterministic; and the generated inputs must actually cause splits.
- **Shaper unit tests** (on a `Transaction`):
  - in-place split and index preservation, several change outputs;
  - small change left untouched;
  - non-change outputs and change with a datum, datum hash or script ref left untouched;
  - what the strategy receives; custom and zero fee reserve;
  - invalid strategy results rejected;
  - consolidation: fragments first, smallest ADA next, limit, single fragment and full bundles skipped, only spent
    addresses, skipped with redeemers or without a supplier.
- **`mergeOutputs` interaction** (§6.5):
  - `mergeOutputs(true)` + a shaper with a payment to the sender's own address: the change stays a separate
    `ChangeOutput` and is shaped, whichever of the two is configured first;
  - `mergeOutputs(true)` + a shaper: payments to the same recipient are still merged;
  - other pre-balance transformers with `mergeOutputs(true)`: change merged exactly as today;
  - a custom `WalletShaper` gets the same treatment;
  - a shaper in a low-level context where the change was merged away logs a warning and doesn't throw;
  - an `OutputMergers` merger in `preBalanceTx` or `postBalanceTx` next to a `WalletShaper` logs a warning.
- **Wallet simulation** (`WalletShapeSimulationTest`): replays the Appendix B.8 workloads for the default, every
  profile, `TieredSplitStrategy` and the baselines, prints the report tables and asserts the properties this ADR relies
  on.
- **QuickTx tests** (mocked suppliers): `DefaultWalletShaper`, `withConsolidation()`, a profile, `TieredSplitStrategy`,
  without a shaper (unchanged output), and two chained `preBalanceTx(...)` calls.
- **Parity:** port selected Evolution SDK scenarios so that `TieredSplitStrategy` produces the same split.
- **Integration** (Yaci DevKit): submit transactions with `DefaultWalletShaper.withConsolidation()` and check that the
  resulting UTxOs are on-chain and spendable.

---

## Appendix A. Comparison with Evolution SDK

The TypeScript snippets below are taken from Evolution SDK
([`0167cf9`](https://github.com/IntersectMBO/evolution-sdk/tree/0167cf91381eea2c2f33db5ab1396a66b4dbe2da)): its docs,
and its builder tests, which are the only runnable examples of this feature it ships (its `examples/` folder has
none). Each is followed by the CCL equivalent with `TieredSplitStrategy`, the port of Evolution's algorithm (§10.2).

The "CCL result" rows come from running the same wallets through `CustomWalletShaper` (`TieredSplitStrategy`) with QuickTx and mocked
suppliers. Both runs use the same protocol parameters: `minFeeA` 44, `minFeeB` 155,381, `coinsPerUtxoByte` 4,310.
Evolution results are the values asserted in its tests.

### A.1 Minimal usage

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
        .preBalanceTx(new CustomWalletShaper(new TieredSplitStrategy()))   // Evolution defaults
        .withSigner(SignerProviders.signerFrom(account))
        .complete();
```

### A.2 ADA-only change, custom subdivision

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
TieredSplitStrategy strategy = TieredSplitStrategy.builder()
        .subdivideThreshold(adaToLovelace(100))
        .subdividePercentages(List.of(50, 30, 20))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx().payToAddress(destination, Amount.ada(1)).from(source))
        .preBalanceTx(new CustomWalletShaper(strategy))
        .build();
```

| | Inputs | Outputs | Fee | Change outputs (lovelace) |
|---|---|---|---|---|
| Evolution | 1 | 4 | 173,861 | 99,413,069 · 59,647,841 · 39,765,229 |
| CCL (`feeReserve` 2 ADA) | 1 | 4 | 173,861 | 100,326,139 · 59,100,000 · 39,400,000 |

The output count and fee are identical. The amounts differ because Evolution splits the change **after** the fee,
while CCL splits `change − feeReserve` **before** balancing and the fee is then deducted from the largest piece
(§6.1).

### A.3 Tokens: bundles plus a separate ADA output

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
TieredSplitStrategy strategy = TieredSplitStrategy.builder()
        .subdivideThreshold(BigInteger.valueOf(500_000))
        .subdividePercentages(List.of(50, 30, 20))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx().payToAddress(destination, Amount.lovelace(BigInteger.valueOf(2_000_000))).from(source))
        .preBalanceTx(new CustomWalletShaper(strategy, BigInteger.valueOf(300_000)))   // fee reserve, see finding F1
        .build();
```

| | Outputs | Change |
|---|---|---|
| Evolution | 5 | 3 token bundles + 1 ADA output |
| CCL, `feeReserve` 2 ADA (default) | 2 | **not split**: 1 change output with all tokens |
| CCL, `feeReserve` 0.3 ADA | 5 | 1 ADA (1,361,071) + 3 token bundles (~1.15 ADA each) |

### A.4 Tokens: remaining ADA spread across bundles

Evolution SDK (same file, "should spread remaining lovelace across token bundles when below subdivideThreshold").
The UTxO holds 5 ADA and the same three tokens, and pays 1.2 ADA. The expected result is 1 payment + 3 change,
with no separate ADA output.

The CCL call is the same as A.3 with a payment of `1_200_000` lovelace.

| | Outputs | Change |
|---|---|---|
| Evolution | 4 | 3 token bundles with ADA spread |
| CCL, `feeReserve` 2 ADA (default) | 2 | **not split** |
| CCL, `feeReserve` 0.3 ADA | 4 | 3 token bundles: 1,290,091 (fee-bearing) · 1,165,230 · 1,165,230 |

### A.5 Wallet clean-up: consolidate a fragmented wallet

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

TieredSplitStrategy strategy = TieredSplitStrategy.builder()
        .bundleSize(10)
        .subdivideThreshold(adaToLovelace(50))
        .subdividePercentages(List.of(50, 25, 15, 10))
        .build();

Transaction tx = quickTxBuilder
        .compose(new Tx()
                .collectFrom(fragments)
                .payToAddress(destination, Amount.ada(1))
                .from(source))
        .preBalanceTx(new CustomWalletShaper(strategy))
        .build();
```

| | Inputs | Outputs | Fee | Change |
|---|---|---|---|---|
| Evolution | 6 | 9 | 205,189 | 4 token bundles + 4 ADA outputs |
| CCL (`feeReserve` 2 ADA) | 6 | 9 | 205,189 | 4 token bundles (A: HOSKY+SNEK, B: SUNDAE, C: 3 NFTs, D: 2 NFTs) + 4 ADA outputs (267.9 · 133.1 · 79.8 · 53.2 ADA) |

This is full parity: the same inputs, outputs, fee and bundle layout. Note that bundles are **by policy**:
`isolateFungibles`/`groupNftsByPolicy` have no effect in Evolution (§9.1). HOSKY and SNEK share policy A, so they
share a UTxO.

### A.6 Findings from the comparison

- **F1: A fixed 2 ADA `feeReserve` blocks splitting small change.** In A.3 and A.4 the reserve eats the ADA
  the bundles need, so CCL falls back to a single output where Evolution splits. With a 0.3 ADA reserve the output
  counts match Evolution. Options for review:
  - (a) lower the default, e.g. 0.5 ADA;
  - (b) derive the reserve from protocol parameters, e.g. an estimated fee for the expected output count;
  - (c) apply the reserve only when the change address is the fee payer;
  - (d) after balancing, recompute the split on the post-fee amount (closest to Evolution, more complex).
- **F2: Different amounts, same structure.** Output count, fee and bundle layout match. ADA slice amounts differ
  slightly because of where the fee is taken (A.2). If exact numeric parity matters, option (d) above is required.
- **F3: Output order differs.** Evolution emits bundles first, then ADA slices. CCL keeps the fee-bearing (largest)
  piece at the original change index and appends the rest. Both are valid; CCL's order keeps the indexes of existing
  outputs stable.
- **F4: `drainTo` / `onInsufficientChange: "burn"`.** These Evolution fallbacks have no CCL counterpart. Clean-up
  (A.5) works without them via `collectFrom`, so they are out of scope for this ADR.

## Appendix B. Evaluation of Candidate Algorithms

The design was chosen by evaluating five candidate algorithms for splitting change, implemented as
`WalletShapeStrategy`s and compared in a simulator. This appendix explains each one and the evidence. Their role in the
design:

| Candidate | Role in the design |
|---|---|
| `TieredSplitStrategy` (Evolution SDK algorithm) | Part B option, for parity (§10.2) |
| `PercentageSplitStrategy` | Basis of `AdaShape.Percentages`, used by `stateless()` |
| `TargetShapeStrategy` | Basis of `AdaShape.Lanes`, used by `throughput()` |
| `EqualLanesStrategy` | Not adopted; kept as a simulator baseline in test sources |
| `PaymentSizedStrategy` | Not adopted; kept as a simulator baseline in test sources |

The Part A default takes the token handling that all alternatives share (byte-budget bundles, ADA kept separate) and
keeps ADA in a single output.

The prototype implements Part A only. The code that produced this appendix (all candidates, Part B shapes and the full
simulator) is kept in the history of the prototype branch, at commit `d934907a` of `feat/utxo-unfracking-impl`.

**How to read the examples.** They use mainnet `coinsPerUtxoByte` 4,310 and a Shelley base address. With these,
an ADA-only output needs 0.969750 ADA, a bundle with one token needs 1.146460 ADA, and a bundle with three
single-token policies needs 1.482640 ADA. "Change" is the value a strategy receives, i.e. already without the fee
reserve (§6.1); the shaper later adds the reserve to the largest piece. All numbers were produced by running the
strategies, not calculated by hand.

### B.1 `TieredSplitStrategy` (Evolution SDK algorithm)

**Idea.** Separate tokens from ADA, and cut large ADA into a fixed "logarithmic" set of sizes: one big piece for
large payments, some medium ones and several small ones.

**Algorithm.** See §10.2. In short:

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
- Creates many UTxOs that can fund a payment alone (high concurrency *potential*, B.8).

**Weaknesses.**

- **Spread**: with less than 100 ADA left, spendable ADA is stored next to tokens (60 ADA + 3 tokens → 3 × 20 ADA with
  a token each). A later ADA payment has to spend a token UTxO and carry the token along.
- **One output per policy**: 150 airdropped policies give 157 outputs and lock 172 ADA, and such a transaction can
  exceed `maxTxSize`.
- **Unbounded growth**: every transaction with 100+ ADA change adds up to 6 UTxOs; largest-first selection then
  spends the 50 % slice and splits its change again. In B.8 a single 10,000 ADA UTxO became 361 UTxOs after
  300 payments.
- **Cliff** at the threshold (99 ADA → 1 output, 100 ADA → 7), and the slices are relative to whatever the change
  is, not to how the wallet spends.

**Use it for** parity with Evolution SDK, and as the reference in comparisons.

### B.2 `PercentageSplitStrategy` (basis of `AdaShape.Percentages`)

**Idea.** Evolution's ADA split, without its token problems.

**Algorithm.** Tokens are bundled by `ByteBudgetBundling` (B.6), each bundle gets exactly its min-ada, and the
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

- The same unbounded growth and cliff as Evolution for ADA (identical results in B.8).
- Bundles now mix policies. That is fine for payments, but a DEX or marketplace that wants one policy per UTxO would
  use `PolicyBundling` instead.

**Use it for** a drop-in improvement over Evolution when wallets hold many tokens.

### B.3 `EqualLanesStrategy` (not adopted)

**Idea.** Split ADA into N equal "lanes", so that several independent UTxOs of a useful size exist.

**Algorithm.** `lanes = min(N, ADA / max(minLaneAmount, min-ada))`. If that is at most 1, one output; otherwise
equal lanes, with the rounding remainder on the last one. Tokens are handled as in B.2.

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
- The most UTxOs able to fund a payment alone (B.8).

**Weaknesses.**

- The **worst growth**: every transaction re-splits its change into 5 lanes. With 10 ADA lanes a single
  10,000 ADA UTxO became 625 UTxOs after 300 payments. A larger `minLaneAmount` (60 ADA) reduces this to 125.
- Needs tuning: lanes smaller than the typical payment can't fund it alone, and average inputs per transaction
  go up (1.73 for random 1–50 ADA payments with 10 ADA lanes).

**Use it for** short-lived bursts where many equal UTxOs are wanted right away, with a lane size above the typical
payment. Not as a permanent setting.

### B.4 `PaymentSizedStrategy` (CIP-2 self-organisation; not adopted)

**Idea.** From [CIP-2](https://cips.cardano.org/cip/CIP-0002): if every payment of size *v* leaves a change piece of
about *v*, the wallet gradually fills up with UTxOs matching its typical payments.

**Algorithm.** Take the ADA of the non-change outputs, largest first. For each payment create a piece of
**payment + `feeAllowance` + ADA-only min-ada** (default 0.5 + 0.969750 = 1.469750 ADA on top), so that the piece
can pay a similar payment alone, including its fee and a change output. Payments below min-ada, and payments whose
piece would leave a remainder below min-ada, are skipped. The remainder is the last piece; at most `maxPieces` (5)
pieces in total.

The margin matters: with pieces of exactly the payment size, a 10 ADA piece could not pay a 10 ADA payment (fee and
change missing), and the number of UTxOs able to fund a payment alone would stay at 1.0. With the margin it is 150.5
(B.8).

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
  payments (B.8).
- The ADA of token payments (e.g. 1.2 ADA sent with an NFT) also creates pieces, which are small.

**Use it for** experiments with a random coin selection strategy; not with largest-first.

### B.5 `TargetShapeStrategy` (wallet-aware; basis of `AdaShape.Lanes`)

**Idea.** Decide on a wanted wallet shape, e.g. "5 ADA-only UTxOs of at least 10 ADA", and only create what is
missing, instead of reshaping every change blindly.

**Algorithm.**

1. Load the change address' UTxOs from the `UtxoSupplier`. Count the **existing lanes**: ADA-only UTxOs of at least
   `laneAmount`, without datum or script ref, not spent by this transaction.
2. `missing = targetLanes − existing`, limited by how many lanes the ADA can fund.
3. If at most one lane is missing, keep one ADA output (it can be that lane). Otherwise create `missing − 1` lanes of
   exactly `laneAmount` and put the rest in the last lane.
4. Tokens are handled as in B.2.

**Examples** (defaults: 5 lanes of 10 ADA).

| Wallet already has | Change | Result |
|---|---|---|
| no lanes | 1,000 ADA | 10 · 10 · 10 · 10 · 960 ADA |
| 2 lanes of 20 ADA | 1,000 ADA | 10 · 10 · 980 ADA |
| 5 lanes | 1,000 ADA | 1 output |
| no lanes | 60 ADA + 3 tokens of 3 policies | 1 bundle (1.482640 ADA) + 10 · 10 · 10 · 10 · 18.517360 ADA |

**Strengths.**

- The **only strategy with bounded growth**: the wallet stays at `targetLanes` ADA-only UTxOs (5 or 10 in B.8)
  instead of growing with every transaction, and usually only 1 change output is created (1.01 on average).
- Lowest input count (≈1.0) and, with `laneAmount` above the typical payment, every lane can fund a payment alone.
- The parameters mean something to users: "how many parallel payments" and "how big".

**Weaknesses.**

- One `UtxoSupplier.getAll` call per transaction; slow for large wallets or rate-limited backends.
- It can't see UTxOs spent by transactions still in flight, so it may count a lane that is about to disappear.
- Needs `laneAmount` above the typical payment; with 10 ADA lanes and 10 ADA payments the lanes can't fund a payment
  alone.
- Does not consolidate an already fragmented wallet; it only stops further growth. `WalletShape` adds
  consolidation for that (§6.2).

**Use it for** server-side services that need several independent UTxOs: it gives predictable, bounded concurrency
groundwork. This is the basis of `throughput()`. Retail users don't need lanes, so the Part A default keeps one ADA
output instead. As `AdaShape.Lanes` it keeps ADA in one output when there is no `UtxoSupplier`, so a wallet without a
UTxO view never grows.

### B.6 Token bundling: `PolicyBundling` vs `ByteBudgetBundling`

| | `PolicyBundling` (Evolution) | `ByteBudgetBundling` (default of the alternatives) |
|---|---|---|
| Rule | One bundle per policy, chunks of `bundleSize` (10) assets | Bundles up to `maxBundleBytes` (1,000) of CBOR, first-fit decreasing; a policy that fits is never split |
| 3 single-token policies | 3 outputs | 1 output |
| Change with 150 single-token policies (more than fits into one output) | 150 outputs, 171.97 ADA min-ada | 7 outputs, 32.06 ADA min-ada |
| Output size | Depends on asset name lengths | Bounded by the budget, far below `maxValSize` (5,000) |
| Mixes policies | Never | Yes |

`ByteBudgetBundling` is the better default for wallets. `PolicyBundling` is still useful when one policy per UTxO
matters (DEX, marketplace, staking of a specific token).

### B.7 Summary

| Strategy | Growth under largest-first | Tokens | Parameters | Main risk |
|---|---|---|---|---|
| `TieredSplitStrategy` (Evolution SDK) | Unbounded (~1.2 UTxOs per tx) | Per policy, spread | Evolution defaults | Fragmentation, 1 output per policy |
| `PercentageSplitStrategy` → `stateless()` | Unbounded (as Evolution) | Byte budget, ADA separate | Evolution defaults | Fragmentation |
| Part A default (`Single` ADA) | **Bounded**: one ADA output | Byte budget, ADA separate | None | Creates no lanes (not needed for retail) |
| `EqualLanesStrategy` (not adopted) | Unbounded (up to ~2 UTxOs per tx) | Byte budget, ADA separate | Lanes, lane size | Worst fragmentation |
| `PaymentSizedStrategy` (not adopted) | Unbounded (~1 UTxO per tx) | Byte budget, ADA separate | Max pieces, fee allowance | Pieces pile up under largest-first |
| `TargetShapeStrategy` → `throughput()` | **Bounded** at the lane count | Byte budget, ADA separate | Lanes, lane size | `getAll` per transaction |

### B.8 Simulation

`WalletShapeSimulationTest` (with `WalletShapeSimulator`) replays wallet workloads through the real shaper, so
consolidation and the strategy result checks run on every step. It prints the tables below and asserts the properties
this ADR relies on.

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
| **Part A default** | 1 | 1.0 | 1 | 1.0 | 1 | 1.0 |
| **Part A default + consolidation** | 1 | 1.0 | 1 | 1.0 | 1 | 1.0 |
| `throughput(5, 60 ADA)` | 5 | 5.0 | 5 | 5.0 | 5 | 4.6 |
| `throughput(10, 60 ADA)` | 10 | 10.0 | 10 | 10.0 | 10 | 9.2 |
| `stateless()` | 361 | 228.0 | 301 | 95.4 | 331 | 214.8 |
| `TieredSplitStrategy` (Evolution SDK) | 361 | 228.0 | 301 | 95.4 | 331 | 214.8 |
| baseline EqualLanes (5 × 10 ADA) | 625 | 427.4 | 470 | 90.6 | 482 | 299.7 |
| baseline EqualLanes (5 × 60 ADA) | 125 | 118.4 | 125 | 89.6 | 116 | 84.4 |
| baseline PaymentSized | 301 | 150.5 | 205 | 52.6 | 288 | 77.5 |

`stateless()` equals the Evolution SDK algorithm for ADA-only change. Average inputs per transaction stayed between 1.0
and 1.3, except EqualLanes with 10 ADA lanes (up to 1.73).

**Token workloads.**

| Shape / strategy | Airdrop¹: token UTxOs | ADA in token UTxOs | Hot UTxO²: max UTxOs | token churn | ADA in token UTxOs | Small wallet³: final UTxOs | ADA in token UTxOs |
|---|---|---|---|---|---|---|---|
| None (today) | 60 | 99.05 | 1 | **100 %** | 2,183.60 | 1 | 69.56 |
| **Part A default** | 60 | 99.05 | 7 | 0 % | 26.04 | 2 | 4.34 |
| **Part A default + consolidation** | **5** | **15.24** | 7 | 0 % | 26.04 | 2 | 4.34 |
| `throughput(5, 60 ADA)` | 60 | 99.05 | 11 | 0 % | 26.04 | 3 | 4.34 |
| `stateless()` | 60 | 99.05 | 307 | 0 % | 26.04 | 8 | 4.34 |
| `TieredSplitStrategy` (Evolution SDK) | 60 | 99.05 | 415 | 0 % | 137.58 | 27 | 22.93 |

¹ Random 1–50 ADA payments from 10,000 ADA; every 5th transaction a new single-token UTxO arrives (60 in total).
² One UTxO with 10,000 ADA and 120 single-token policies (4,684 bytes, just below `maxValSize`), 300 random 1–50 ADA
payments.
³ One UTxO with 150 ADA and 20 single-token policies, 40 random 1–3 ADA payments.

**Reading.**

- Every shape fixes the hot-UTxO problem: after the first transaction, payments no longer move tokens.
- Only consolidation touches airdropped tokens: largest-first never selects those small UTxOs, so without consolidation
  they stay scattered with their min-ada locked.
- One bundle per policy (the Evolution SDK algorithm, `PolicyBundling`) locks 5× more ADA than byte-budget bundling.
- The Part A default keeps one ADA output and never grows the wallet; the `Lanes` profiles stay at their configured
  size; the stateless strategies create many fundable UTxOs only by fragmenting the wallet without limit.

**Caveats.** One address, serial transactions, largest-first selection only, fixed fee, synthetic payments. See §13.

### B.9 Conclusions

The evaluation supports the decision in §2:

1. **Tokens: byte-budget bundling with ADA always kept separate** is better than the Evolution SDK behaviour in every
   case simulated. It is used by the Part A default and by every Part B profile except `policyPerUtxo()`.
2. **ADA for retail: one output.** Retail users don't need lanes, and one ADA output is the simplest shape that never
   grows. **ADA for parallel server-side payments: wallet-aware lanes**, the only approach whose shape stays bounded.
3. **Consolidation is needed** to reclaim scattered token UTxOs, because coin selection never picks them; it is the
   difference between the Part A default and its consolidating variant.
4. **One default and one configurable algorithm** instead of five strategies. `TieredSplitStrategy` is available for
   parity; `PaymentSizedStrategy` and `EqualLanesStrategy` are not adopted.
