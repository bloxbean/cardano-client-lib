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

### M1: Stop wallets getting stuck

Independent of the wallet shape work; separate branch and PRs.

- [ ] **PR 1: coin selection limit from `maxTxSize`.** Derive the maximum number of inputs from `maxTxSize` (about 440)
      instead of the fixed 20; make it configurable per builder instead of through the global
      `CoinselectionConfig` singleton. Tests: 21 and 400 inputs work; the limit follows the protocol parameters.
- [ ] **PR 2: local limit checks.** After a transaction is built, check its size (with witnesses) against
      `maxTxSize` and every output's value against `maxValSize`; fail with a typed `TxLimitExceededException`
      (reason, measured numbers). Reuse the size estimate of the fee calculation.
- [ ] Decide with maintainers whether PR 1 is a behaviour change that needs a flag (decision D2).

### M2: Prevention (wallet shape, Part A)

- [ ] Review of ADR PR #676 and prototype PR #677.
- [ ] Decide the simplest default: sequential chunking of tokens ("prevention only") or the current first-fit
      decreasing bundling.
- [ ] Release `DefaultWalletShaper` as opt-in.

### M3: Evidence (liveness simulator)

- [ ] In-memory ledger that applies transactions built by the real `QuickTxBuilder`.
- [ ] Local Phase-1 validator (`maxTxSize`, `maxValSize`, min-ada, balance, fee), shared with M1 PR 2.
- [ ] Generators for hostile wallets (dust storms, fragmented tokens, hot UTxOs near the limits, min-ada-only token
      UTxOs, a `coinsPerUTxOByte` increase) and action sequences (payments up to "send almost everything", token
      transfers, airdrops, dApp transactions without a shaper).
- [ ] Classification of every failure: builder failure, degraded, destroyed.
- [ ] Report comparing today, M1 and M2. Reuse `WalletShapeSimulator` where possible.

### M4: Recovery

- [ ] **ADR 2 (QuickTx recovery)**: local limit checks (from M1), consolidation planner, `withRecovery()`.
- [ ] Consolidation planner: a pure function from the wallet's UTxOs and protocol parameters to consolidation `Tx`s
      that each fit the limits; per address (privacy); respects hardware-wallet limits.
- [ ] `withRecovery()`: single-transaction repairs (fewer, larger inputs; split an oversized change; merge token
      fragments to free min-ada for the fee); otherwise `TxLimitExceededException` carrying the repair plan.
- [ ] **ADR 3 (TxFlow recovery)**: `RecoveryPolicy` next to `RetryPolicy`.
- [ ] `RecoveryPolicy`: on `TxLimitExceededException`, insert the planned repair steps, run them chained, retry the
      step; re-plan after a partial failure.
- [ ] Adaptive consolidation in the shaper: merge more when the wallet has many UTxOs, bounded by a size budget.
- [ ] Plan model: steps, purpose of each step, fee per step, maximum total cost.

### M5: dApps and end-to-end

- [ ] Build an **unsigned**, chained plan (CBOR list) that a dApp back end returns for CIP-103 `signTxs`.
- [ ] Yaci DevKit tests: submit recovery chains; confirm the local validator agrees with the node.
- [ ] Documentation page.

### M6: Server-side shapes (driven by demand)

- [ ] ADR Part B: lanes (`throughput`), one policy per UTxO, stateless split. An earlier prototype exists in the history
      of `feat/utxo-unfracking-impl` at commit `d934907a`.

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
| **D2** | Replace the fixed 20-input coin selection limit with a limit derived from `maxTxSize`? | Change the default / keep 20 and add an opt-in / configurable per builder only |
| **D3** | Where do the design documents live? | In cardano-client-lib (temporary) / a separate repository / the CIP repository later |

## 7. Next Two Weeks

- [ ] Maintainers decide D1–D3.
- [ ] M1 PR 1: coin selection limit from `maxTxSize`, with tests (21 inputs no longer fails).
- [ ] M1 PR 2: local limit checks with `TxLimitExceededException`.
- [ ] Address review comments on #676 and #677.
- [ ] Start M3: the liveness simulator, reusing `WalletShapeSimulator`.
