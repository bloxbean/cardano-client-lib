# Self-Healing Wallets: Execution Plan

**Type**: Execution plan
**Status**: Draft
**Date**: 2026-09-30
**Design document**: [Self-Healing Wallets](self-healing-wallets.md)
**Related**: issue [#678](https://github.com/bloxbean/cardano-client-lib/issues/678), ADR
[`quicktx/adr/wallet-shape.md`](../quicktx/adr/wallet-shape.md), PRs
[#676](https://github.com/bloxbean/cardano-client-lib/pull/676) (ADR) and
[#677](https://github.com/bloxbean/cardano-client-lib/pull/677) (prototype)

This plan turns the [Self-Healing Wallets](self-healing-wallets.md) design into work. It runs on three parallel tracks,
with a few gates between them. Every milestone ships something useful on its own; nothing waits for the whole design.

| Track | What | Outcome |
|---|---|---|
| **1. CCL implementation** | Milestones M1–M6 | cardano-client-lib implements self-healing wallets |
| **2. Writing** | W1–W3 | Blog post, off-chain patterns series, CIP |
| **3. Coordination** | Maintainers, SDKs, wallets, dApps, CIP editors | Agreement and adoption beyond CCL |

## 1. Starting Point

What exists today (see the design document, §11, and the ADR):

- **Prototype (PR #677)**: `DefaultWalletShaper` (tokens apart from ADA, one ADA output), `withConsolidation()`,
  chained `preBalanceTx`, `TxBuilderContext.mergeChange`, and a wallet shape simulator.
- **TxFlow**: chained execution (`PIPELINED`, `BATCH`), pending outputs, confirmation tracking, retries, rollback
  handling.
- **Coin selection limit**: `CoinselectionConfig.coinSelectionLimit` defaults to **20** inputs (a global setting). A
  payment needing more fails with `InputsLimitExceededException`, although the ledger allows about 440 inputs. Today a
  CCL wallet is stuck as soon as a payment needs 21 small UTxOs.
- **No local limit checks**: CCL reads `maxTxSize` and `maxValSize` but doesn't enforce them, so oversized
  transactions are only discovered when the node rejects them.

## 2. Track 1: CCL Implementation

| # | Milestone | Size | Depends on | Done when |
|---|---|---|---|---|
| **M1** | Stop wallets getting stuck | S–M | — | CCL no longer builds transactions the node rejects for size; a payment needing 21+ inputs works |
| **M2** | Prevention | M (mostly done) | Maintainer decision D1 | Opt-in wallet shaper released |
| **M3** | Evidence | M | M1 | Stuck rates measured for today vs M1 vs M2 |
| **M4** | Recovery | L | M1, M3 | The simulator shows no stuck wallet for affordable intents |
| **M5** | dApps and end-to-end | M | M4 | A dApp back end can return a recovery chain for one CIP-103 approval |
| **M6** | Server-side shapes | M | Demand | Built only if users ask |

### Milestone contents

| Milestone | Changes (§2.1) |
|---|---|
| M1 | C1–C6 |
| M2 | C7–C12 |
| M3 | C13–C17 |
| M4 | C18–C25 |
| M5 | C26–C28 |
| M6 | C29 |

### 2.1 CCL change list

Status: ⬜ to do · 🟡 prototype in PR #677 · ✅ done.

#### M1: Stop wallets getting stuck (separate branch and PRs)

| # | Change | Module / classes | Kind | Tests | Status |
|---|---|---|---|---|---|
| C1 | Add a per-builder input limit `maxInputs`; when not set, derive it from `ProtocolParams.maxTxSize` (≈ `(maxTxSize − base size − outputs − witnesses) / 36`, about 440 on mainnet) | `function`: `TxBuilderContext` | Change | Derivation from protocol params; explicit value wins | ⬜ |
| C2 | Pass that limit to coin selection instead of the global default: call the existing `select(..., maxUtxoSelectionLimit)` overload | `function`: `InputBuilders.getUtxosForValue`, `ChangeOutputAdjustments` (additional inputs) | Change | A payment needing 21 and 400 inputs builds; 500 inputs fails as expected | ⬜ |
| C3 | Keep `CoinselectionConfig.coinSelectionLimit` (20) for callers of the strategy API outside a builder; deprecate the global setting in favour of C1 | `coinselection`: `CoinselectionConfig` | Change | Existing coin selection tests unchanged | ⬜ |
| C4 | `TxLimitExceededException extends TxBuildException` with `reason` (`TOO_MANY_INPUTS`, `MAX_TX_SIZE`, `MAX_VAL_SIZE`, `MIN_ADA`, `FEE_NOT_PAYABLE`), measured value and limit, and an optional repair plan (filled in M4) | `function`: new, `function.exception` | New | Fields and message | ⬜ |
| C5 | `TxLimitValidator`: checks the built transaction's size including witnesses against `maxTxSize` (reusing the size estimate of the fee calculation), every output's value against `maxValSize`, and min-ada; throws C4 | `function`: new, `function.helper` | New | Oversized transaction, oversized output, output below min-ada, valid transaction passes | ⬜ |
| C6 | Run C5 at the end of `_build()` (before signing), and map `InputsLimitExceededException` from coin selection to C4 (`TOO_MANY_INPUTS`). Opt-out `TxContext.validateLimits(false)` | `quicktx`: `QuickTxBuilder.TxContext` | Change | QuickTx fails early with C4 instead of building a transaction the node rejects | ⬜ |

#### M2: Prevention (wallet shape, Part A)

| # | Change | Module / classes | Kind | Tests | Status |
|---|---|---|---|---|---|
| C7 | `preBalanceTx(...)` chains transformers instead of overwriting | `quicktx`: `QuickTxBuilder.TxContext` | Change | Two chained transformers both run | 🟡 |
| C8 | `WalletShaper` marker interface and `DefaultWalletShaper` (tokens in size-capped bundles apart from ADA, one ADA output; `withConsolidation()`) | `function`: `function.walletshape` | New | `DefaultWalletShaperTest`, contract test, simulation test | 🟡 |
| C9 | `TxBuilderContext.mergeChange`; `InputBuilders` keeps the change separate when it is `false`; QuickTx sets it for a `WalletShaper` | `function`: `TxBuilderContext`, `InputBuilders`; `quicktx`: `QuickTxBuilder` | Change | `mergeOutputs(true)` with and without a shaper | 🟡 |
| C10 | `OutputMerger` returned by `OutputMergers.mergeOutputsForAddress`; QuickTx warns when combined with a `WalletShaper` | `function`: `OutputMerger`, `OutputMergers`; `quicktx`: `QuickTxBuilder` | New / change | Merger after a shaper builds and warns | 🟡 |
| C11 | Decide the simplest default: sequential token chunking ("prevention only") or the current first-fit decreasing bundling; adjust `DefaultWalletShaper` | `function`: `DefaultWalletShaper`, `ByteBudgetBundling` | Change | Contract test | ⬜ |
| C12 | Review, merge and release C7–C11 as opt-in | — | — | Full `function` and `quicktx` suites | ⬜ |

#### M3: Evidence (liveness simulator)

| # | Change | Module / classes | Kind | Tests | Status |
|---|---|---|---|---|---|
| C13 | In-memory ledger: UTxO set per address, applies a signed transaction (removes inputs, adds outputs under the real transaction hash), acts as `UtxoSupplier` | Test sources (e.g. `quicktx` tests or `test-support`) | New | Applying and chaining transactions | ⬜ |
| C14 | Use C5 as the local Phase-1 validator of the simulator (plus balance and fee checks) | Test sources | New | Agrees with the node on the Yaci samples (C27) | ⬜ |
| C15 | Seeded generators for hostile wallets and action sequences | Test sources | New | Reproducible with a seed | ⬜ |
| C16 | Classification of every failure: builder failure, degraded (recoverable in *k* consolidation transactions), destroyed | Test sources | New | Known cases classified correctly | ⬜ |
| C17 | Report comparing today, M1 and M2; replaces the shape metrics of `WalletShapeSimulator` | Test sources | New | Printed tables, asserted properties | ⬜ |

#### M4: Recovery

| # | Change | Module / classes | Kind | Tests | Status |
|---|---|---|---|---|---|
| C18 | ADR 2 (QuickTx recovery): C19–C22 | `quicktx/adr` | Doc | — | ⬜ |
| C19 | `ConsolidationPlanner`: a pure function from the wallet's UTxOs and protocol parameters to consolidation `Tx`s that each fit the limits; per address; respects a configurable device limit (hardware wallets) | `quicktx` (produces `Tx`) | New | Plans fit `maxTxSize`; value conserved; stays within one address | ⬜ |
| C20 | `RecoveryPlan` model: steps, purpose of each step, fee per step, maximum total cost | `quicktx` | New | Fees and total cost | ⬜ |
| C21 | `TxContext.withRecovery()`: on C4, try single-transaction repairs (fewer and larger inputs; split an oversized change; merge token fragments to free min-ada for the fee); otherwise throw C4 carrying a C20 plan | `quicktx`: `QuickTxBuilder.TxContext` | New | Each repair, and the plan when no single transaction works | ⬜ |
| C22 | Adaptive consolidation: merge more when the wallet has many UTxOs, bounded by a transaction size budget | `function`: `DefaultWalletShaper` | Change | Convergence in the simulator under a dust storm | ⬜ |
| C23 | ADR 3 (TxFlow recovery): C24–C25 | `txflow/adr` | Doc | — | ⬜ |
| C24 | `RecoveryPolicy` next to `RetryPolicy`: on C4 with a plan, insert the repair steps before the failing step, run them chained (`PIPELINED`/`BATCH`), then retry the step | `txflow`: `FlowExecutionSettings`, `FlowExecutor`, `StepRunner` | New | Flow with a stuck wallet completes; `FlowResult` lists all steps | ⬜ |
| C25 | Re-plan after a partial failure of the repair chain | `txflow`: `FlowExecutor` | New | Failure of a repair step, retry from there | ⬜ |

#### M5: dApps and end-to-end

| # | Change | Module / classes | Kind | Tests | Status |
|---|---|---|---|---|---|
| C26 | Build an **unsigned**, chained plan (CBOR list, transaction hashes computed with `TransactionUtil.getTxHash`) that a dApp back end returns for CIP-103 `signTxs` | `quicktx` or `txflow` | New | Later steps spend outputs of earlier ones by hash | ⬜ |
| C27 | Yaci DevKit tests: recovery chains submitted to a node; C5 agrees with the node | `quicktx`/`txflow` integration tests | New | Node accepts the chain; rejects what C5 rejects | ⬜ |
| C28 | Documentation page: wallet shape, recovery, TxFlow recovery | `docs` site | New | — | ⬜ |

#### M6: Server-side shapes (driven by demand)

| # | Change | Module / classes | Kind | Tests | Status |
|---|---|---|---|---|---|
| C29 | ADR Part B: `CustomWalletShaper`, `WalletShape` with `AdaShape` (lanes, percentages), `TokenShape`, `Consolidation`, profiles; restore from commit `d934907a` where useful | `function`: `function.walletshape` | New | As in the earlier prototype | ⬜ |

## 3. Track 2: Writing

| # | Deliverable | Needs first | Contents |
|---|---|---|---|
| **W1** | Blog post: "Self-Healing Wallets" | M1 and M3 numbers | User story, measured limits, the five phases, CIP-103, first results in CCL |
| **W2** | Off-chain patterns series (possibly a book) | W1 | Self-Healing Wallets as the first chapter |
| **W3** | Informational CIP (category *Wallets*, extending CIP-2) | M4 evidence and ideally a second SDK agreeing | Shape rules and the liveness requirement in MUST/SHOULD wording; CIP-103 for approval |

Chapter candidates for W2, all topics this work has already touched:

1. Self-Healing Wallets
2. Transaction chaining (pipelined and batched execution, partial failure)
3. UTxO contention and parallel builders (lanes, coordination)
4. Idempotent submission and retries
5. Rollback-aware confirmation
6. Collateral management
7. Pre-flight validation (checking ledger limits before submission)
8. Multi-address (HD) wallets: privacy when merging UTxOs

What the design document needs for each:

- **Blog**: less specification, more narrative: a concrete user story, pictures (wallet before and after, the phase
  diagram), and §11 shortened to a "how cardano-client-lib implements it" section.
- **CIP**: remove §11 and keep the text SDK-neutral; turn §6 and the liveness requirement into MUST/SHOULD
  statements; add Rationale, Path to Active and Copyright (CIP-1); settle the open questions (privacy, hardware-wallet
  limits) first.

## 4. Track 3: Coordination

| When | Who | Why |
|---|---|---|
| **Now** | CCL maintainers | Decisions D1–D3 (§6) |
| After W1 | Evolution SDK (IntersectMBO) | Closest peer, already doing unfracking; share the design document; propose a joint CIP |
| After W1 | UnFrack.It author | Knows the real-world failure cases best; invite a review |
| After W1 | Mesh, Lucid Evolution, PyCardano | Wider SDK buy-in before the CIP |
| During M5 | Wallets implementing CIP-103 (Eternl, Typhon) | Check the recovery-chain experience; ask Lace and Vespr about CIP-103 support |
| During M5 | dApps (JPG Store, which uses CIP-103; a DEX) | Try recovery with real user wallets |
| Before W3 | CIP editors and community (CIP forum, Discord) | Discuss before opening the pull request |

## 5. Gates

| Gate | Condition |
|---|---|
| Publish W1 (blog) | Real numbers from M1 and M3 |
| Submit W3 (CIP) | M4 proven by the simulator, and interest from at least one other SDK |
| Start M6 (Part B) | A concrete user need |

## 6. Decisions Needed Now

| # | Decision | Options |
|---|---|---|
| **D1** | Merge the wallet shape Part A (PRs #676/#677)? | Opt-in as proposed / changes requested |
| **D2** | Replace the fixed 20-input coin selection limit with a limit derived from `maxTxSize` (C1–C3), and fail early on exceeded limits (C6)? | Change the default / keep 20 and add an opt-in / configurable per builder only |
| **D3** | Where do the design documents live? | In cardano-client-lib (temporary) / a separate repository / the CIP repository later |

## 7. Next Two Weeks

- [ ] Maintainers decide D1–D3.
- [ ] M1 PR 1 (C1–C3): input limit from `maxTxSize`, with tests (21 inputs no longer fails).
- [ ] M1 PR 2 (C4–C6): local limit checks with `TxLimitExceededException`.
- [ ] Address review comments on #676 and #677.
- [ ] Start M3 (C13–C17): the liveness simulator.
