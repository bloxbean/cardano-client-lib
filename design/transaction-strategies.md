# Keeping Wallets Transactable: Multi-Leg Transaction Strategies

**Type**: Design document (SDK-independent; basis for a technical blog post and/or a CIP)
**Status**: Draft
**Date**: 2026-09-29
**Related**: cardano-client-lib issue [#678](https://github.com/bloxbean/cardano-client-lib/issues/678),
ADR [`quicktx/adr/wallet-shape.md`](../quicktx/adr/wallet-shape.md)

This document describes, independently of any SDK, how dApps, wallets and transaction builders can make sure a user
can always carry out what they intend to do on Cardano, without paying more than necessary, whatever state their wallet
is in. The last part (§11) shows how cardano-client-lib implements it; the rest applies to any SDK (Mesh, Evolution SDK,
Lucid, PyCardano, …) and any wallet.

## 1. Problem

On Cardano, what a wallet can do next depends not only on **how much** it holds, but on **how its funds are laid out**
in UTxOs. The ledger limits every transaction (mainnet values): a transaction can't exceed `maxTxSize` (16,384 bytes),
an output's value can't exceed `maxValSize` (5,000 bytes), and every output needs a minimum amount of ADA (min-ada)
that grows with its size. In CBOR, a transaction input takes about 36 bytes, so one transaction can spend at most about
**440 UTxOs**; a single-token policy in an output takes about 39 bytes, so one output holds at most about **128
single-token policies**.

As a result, a wallet with enough funds can end up in a state where the user's next action is **impossible** or
**needlessly expensive**:

| Wallet state | Consequence | Severity |
|---|---|---|
| Tokens accumulate in one change output; an action has to combine token-heavy UTxOs | The new change would exceed `maxValSize`; the transaction is invalid | Impossible for a builder that creates one change output |
| Many small UTxOs (e.g. 1,000 × 1 ADA from years of small incoming payments) | Sending 500 ADA needs about 500 inputs, more than one transaction allows | Impossible in one transaction |
| One token spread over hundreds of UTxOs | Sending it all at once needs too many inputs | Impossible in one transaction |
| All ADA sits as min-ada in a single token UTxO | No fee can be paid | Impossible until someone sends ADA |
| Protocol parameters raise the min-ada | Existing token UTxOs need more ADA to be moved | Impossible or more expensive |
| Every payment carries all tokens along | About 0.17 ADA extra fee per payment for 100 tokens | More expensive |
| Many small inputs | About 0.16 ADA extra fee per 100 inputs | More expensive |

Today users typically discover this when a dApp or wallet shows an error, and repair it with a separate tool that
builds clean-up transactions (e.g. the UnFrack.It website). That requires expertise, is manual, and costs extra fees:
like having to defragment a hard drive by hand.

## 2. Goals

1. **Liveness**: every action the user can afford can be carried out, if necessary in several transactions.
   It may cost a bit more; it must never be impossible because of how the wallet is laid out.
2. **Cost-efficiency**: the user doesn't pay fees for tokens, dust or repairs they don't need.
3. **No expertise**: the user never chooses profiles, strategies or "defragments" their wallet; it happens
   automatically.
4. **Predictability**: before signing, the user sees how many transactions are needed and the maximum total cost.
5. **Independence**: the pattern works with any SDK, any wallet and any dApp, using existing standards where possible.

Non-goal: parallel execution of independent transactions (UTxO contention). It needs coordination between builders and
is a separate concern.

## 3. Actors

| Actor | Role in this design |
|---|---|
| **User** | Has an intent ("buy this NFT", "pay 50 ADA"); approves transactions |
| **Wallet** | Holds the keys and a view of the user's UTxOs; builds the user's own transactions; signs transactions built by dApps (CIP-30, CIP-103) |
| **dApp front end** | Captures the intent, talks to the wallet over CIP-30 |
| **dApp back end** | Often builds the transactions with an SDK; may orchestrate several transactions |
| **Transaction builder (SDK)** | Selects inputs, creates outputs and change, computes fees (e.g. Mesh, Evolution SDK, Lucid, PyCardano, cardano-client-lib) |
| **Orchestrator** | Executes several dependent transactions: chains, submits, tracks confirmation, retries (e.g. a dApp back end, cardano-client-lib's TxFlow) |
| **Chain provider / indexer** | Supplies UTxOs and protocol parameters (e.g. Blockfrost, Koios, Ogmios/Kupo, Yaci Store) |
| **Node and mempool** | Validates transactions against ledger rules; accepts a transaction that spends an output of another transaction still in the mempool, which is what makes chaining possible |

## 4. Concepts

- **Intent**: what the user wants to achieve, independent of how many transactions it takes.
- **Wallet shape**: how a wallet's UTxOs are laid out (how many, how big, where the tokens sit).
- **Healthy wallet**: a shape in which every affordable intent fits into one transaction and no fee is wasted:
  tokens in a few compact bundles apart from spendable ADA, no idle dust, and a small, stable number of UTxOs.
- **Leg**: one transaction within a strategy.
- **Strategy**: the ordered set of legs that realises one intent, entered as one unit, with its maximum cost known
  before the user signs.
- **Feasibility**: an intent is *single-leg feasible* (fits into one transaction), *multi-leg feasible* (needs repair
  legs first) or *infeasible* (the wallet really can't afford it).

## 5. The Pattern: Multi-Leg Transaction Strategy

### 5.1 Analogy: the iron condor

In options trading, an **iron condor** is a single strategy made of four legs (options contracts): two options are
sold to earn the premium (the *body*, which pursues the goal), and two further out are bought as *wings* that cap the
loss. The trader enters all four as **one order**, and before entering it knows the maximum loss and the maximum gain.
Each leg on its own makes little sense; together they realise a goal with bounded risk.

A transaction strategy works the same way:

| Iron condor | Transaction strategy |
|---|---|
| The trader's goal (profit while the price stays in a range) | The user's intent (e.g. buy an NFT) |
| Body: the sold options that pursue the goal | **Action leg**: the transaction that carries out the intent |
| Wings: bought options that protect against a large loss | **Repair legs** (consolidation before the action) and the **shape of every leg's change** (protects the next intent) |
| Entered as one order | Signed as one approval and submitted as one chain |
| Maximum loss known before entering | Maximum total cost (fees of all legs) known before signing |
| Legs managed together; a failed fill affects the position | Legs executed in order; if one fails, the later ones fail and the strategy is retried from that leg |

For a healthy wallet, the strategy is a single leg: the action, with a well-shaped change. Repair legs only appear when
the wallet is in a state from §1.

### 5.2 Phases

```
intent ──► 1. Analyse ──► 2. Plan ──► 3. Approve ──► 4. Execute ──► 5. Keep healthy
            feasible in     legs +       one user       chained        shape every
            one tx?         max cost     approval       submission     leg's change
```

1. **Analyse (pre-flight)**. Build the action as one transaction and check it locally against the ledger limits
   (`maxTxSize`, `maxValSize`, min-ada, balance, fee). The limits are deterministic, so this predicts the node's
   decision without submitting. Result: single-leg feasible, multi-leg feasible, or infeasible (with the reason).
2. **Plan**. If single-leg feasible, try single-transaction repairs first (select fewer, larger inputs; split an
   oversized change; merge token fragments to free min-ada for the fee). Otherwise add **repair legs**: consolidation
   transactions, each within the limits, whose outputs the action leg then spends. Compute the maximum total cost.
3. **Approve**. Show the user one summary (number of legs, what each does, total fee) and collect the signatures in one
   step.
4. **Execute**. Submit the legs in order without waiting for confirmations: each leg spends outputs of the previous
   one, which the node accepts while the parent is in the mempool. If a leg is rejected, the later legs fail too;
   rebuild and retry from the failed leg.
5. **Keep healthy**. Every leg shapes its own change (§6), so the next intent is single-leg feasible again. Wallets
   additionally merge a few small UTxOs into their own transactions, so dust never builds up.

### 5.3 Cost

Repair legs are not free: a consolidation transaction spending 400 inputs is about 14.6 KB and costs about 0.80 ADA in
fees (44 lovelace per byte plus the 0.155 ADA base fee). Merging a UTxO inside a normal transaction costs only its input,
about 0.0016 ADA. So the strategy should rarely need repair legs: phase 5 keeps them the exception, and phase 3 makes
their cost visible.

## 6. Wallet Shape Rules

Every transaction builder should shape the change it creates for the user's wallet, by default:

1. **Tokens are never mixed with spendable ADA.** Token bundles hold only their min-ada; ADA goes to ADA-only outputs.
2. **Outputs stay small.** Token bundles are capped well below `maxValSize` (e.g. 1,000 bytes).
3. **ADA is never dissolved into token UTxOs**, so there is always ADA to pay the next fee.
4. **The shape is bounded.** The number of UTxOs doesn't grow with every transaction (for retail wallets: one ADA
   output).
5. **Repairs are gradual.** Merging happens a few UTxOs at a time (dust, and token fragments when there are at least
   two), so every transaction stays small.
6. **Value is conserved and every output is valid.**

Rules 1–4 and 6 need nothing but the transaction being built, so any builder can apply them, including a dApp building
for someone else's wallet. Rule 5 reads the wallet's UTxOs and adds inputs, so it belongs to transactions the wallet
builds for itself.

## 7. Strategies

| Strategy | Legs | Uses | Guarantees |
|---|---|---|---|
| **Simplest default** | 1 | Rules 1–4, 6 | Never creates a bad state; doesn't repair one |
| **Self-healing** (retail) | 1, occasionally more | Rules 1–6, plus repair legs when an intent isn't single-leg feasible | Liveness (goal 1): every affordable intent can be carried out |
| **Server-side** | 1 or more | Configurable shapes (e.g. several ADA-only "lanes" for parallel workers, one token policy per UTxO) | Chosen by technical operators for their needs |

Retail users should get self-healing without choosing anything. Whether it becomes the default in a given SDK is that
SDK's decision; the simplest default is the technically minimal option.

## 8. Responsibilities per Scenario

| Phase | Wallet builds its own transaction | dApp builds a transaction for the user's wallet | Server-side service |
|---|---|---|---|
| Analyse | Wallet (its SDK) | dApp (its SDK) | Service (its SDK) |
| Plan | Wallet | dApp; merges only with the user's approval of the repair legs | Service |
| Approve | Wallet UI, once per strategy | Wallet UI via **CIP-103** `signTxs` (one approval for all legs); otherwise CIP-30 `signTx` per leg | Automatic |
| Execute | Wallet (chained submission) | dApp via CIP-103 `submitTxs`, or its back end | Service's orchestrator |
| Keep healthy | Rules 1–6 in every transaction | Rules 1–4, 6 (no extra inputs without approval) | Its configured shape |

Because the shape rules act on the wallet's current state, anything a dApp without these rules leaves behind is
repaired by the wallet's own next transactions.

## 9. Standards

- **[CIP-2](https://cips.cardano.org/cip/CIP-0002)** (coin selection): the natural place for shape rules and the
  liveness requirement, as an extension.
- **[CIP-30](https://cips.cardano.org/cip/CIP-0030)** (dApp–wallet bridge): `getUtxos()` without an amount returns all
  UTxOs; with an amount, the wallet chooses which. The dApp builds, the wallet only signs, so dApp SDKs must apply the
  rules themselves.
- **[CIP-103](https://cips.cardano.org/cip/CIP-0103)** (bulk transaction signing, a CIP-30 extension, Active,
  implemented by e.g. Eternl, Typhon and JPG Store): `signTxs` signs a list of possibly chained transactions in one user
  approval and in order; `submitTxs` submits them in order. It provides phases 3–4 for dApp-built strategies, although
  it wasn't designed for wallet repair specifically.
- **A possible new CIP** (informational): the shape rules of §6 as the default for every transaction builder, and the
  liveness requirement: *a builder must not fail because of the wallet's layout when a multi-leg strategy exists; it
  should plan repair legs and present their cost.* No new wallet API is needed; CIP-103 covers the multi-leg approval.

## 10. Open Questions

- **Name of the pattern**: "multi-leg transaction strategy" is a working title.
- **Privacy**: consolidation links UTxOs. Within one address this reveals nothing new; across the addresses of a
  multi-address wallet it links them. Repair legs should stay within one address unless the user accepts otherwise.
- **Wallet support for CIP-103**: without it, legs are signed one by one with CIP-30 (worse experience, same result).
- **Hardware wallets**: device limits on transaction size or number of inputs may be lower than the ledger's; repair
  legs should respect them.
- **Partial failure**: CIP-103 `submitTxs` attempts every submission even after an error, so an orchestrator must detect
  a failed leg and retry from there.
- **Incoming dust**: a wallet can't stop others from sending it many small UTxOs; the self-healing strategy must merge
  faster than dust arrives (adaptive consolidation), or fall back to repair legs.

## 11. Reference Implementation: cardano-client-lib

cardano-client-lib covers the phases with three layers, each described in its own ADR:

| Phase | cardano-client-lib | ADR |
|---|---|---|
| Keep healthy (rules 1–6) | **Wallet shaper** in QuickTx's pre-balance hook: `.preBalanceTx(new DefaultWalletShaper())`, `DefaultWalletShaper.withConsolidation()` | Wallet shape ([`quicktx/adr/wallet-shape.md`](../quicktx/adr/wallet-shape.md)) |
| Analyse, single-transaction repair, plan | **QuickTx `withRecovery()`**: local limit checks, single-transaction repairs, and on failure a typed `TxLimitExceededException` carrying the repair plan | QuickTx recovery (to be written) |
| Execute multi-leg strategies | **TxFlow `RecoveryPolicy`**: inserts the planned repair legs before the failing step and runs them chained (`PIPELINED`/`BATCH`), then retries the step | TxFlow recovery (to be written) |
| Approve (dApp-built) | A dApp back end built on cardano-client-lib returns the unsigned legs; the front end signs them with CIP-103 | — |

Proposed API (the recovery parts don't exist yet):

```java
// Wallet's own transaction: shape + single-transaction recovery
quickTxBuilder.compose(tx)
    .preBalanceTx(DefaultWalletShaper.withConsolidation())
    .withRecovery()
    .withSigner(signer)
    .complete();

// Guaranteed liveness: TxFlow executes repair legs when needed
FlowExecutor.create(backendService)
    .withRecoveryPolicy(RecoveryPolicy.consolidate())
    .execute(flow);
```

Validation: a **liveness simulator** (property-based, no node needed) applies generated intents to generated wallets
with an in-memory ledger and a local Phase-1 validator, and checks after every step that every affordable intent is
still possible; Yaci DevKit replays samples to confirm the local validator agrees with the node.

## Glossary

| Term | Meaning |
|---|---|
| min-ada | Minimum ADA an output must hold, proportional to its size (`coinsPerUTxOByte`) |
| `maxTxSize`, `maxValSize` | Ledger limits on a transaction's size and on the size of one output's value |
| Chaining | Submitting a transaction that spends an output of a transaction not yet confirmed |
| Consolidation | Merging several UTxOs into fewer |
| Dust | Small UTxOs that cost more to spend than they are worth in a normal payment |
| Token fragment | A UTxO holding few tokens, far below the bundle size cap |
