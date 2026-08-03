# TxFlow / TxStream soak tools

Long-running soak and reconciliation tooling for TxFlow and TxStream, shipped as a standalone
fat jar. Mirrors `verified-structures:load-tools`.

These are **operational tools, not tests**. A soak run lasts hours or days, on a machine next to
a devnet or testnet, ideally against a *published* version of the library. Keeping them out of
the JUnit suites means a soak can never run in CI, and — more importantly — the JVM's heap is
under the operator's control rather than a test worker's.

## Build

```bash
./gradlew :txflow-extensions:txflow-soak:shadowJar
# -> build/libs/cardano-client-txflow-soak-<version>-all.jar
```

## Quick start

Yaci DevKit running (API on 8080, admin on 10000), then:

```bash
./gradlew :txflow-extensions:txflow-soak:shadowJar

java -jar txflow-extensions/txflow-soak/build/libs/\
cardano-client-txflow-soak-0.8.0-pre5-dev1-all.jar \
    txstream --duration=2m --rate=2 --lanes=2 --data=/tmp/soak-smoke
```

Two minutes, and it ends with either `RESULT: CLEAN` (exit 0) or a classified list of
discrepancies (exit 1). Everything below is a variation on that line.

`txstream --help` lists every option.

### The five runs worth knowing

```bash
# 1. smoke — does it work at all
txstream --duration=2m --rate=2 --lanes=2

# 2. concurrency — lanes are where throughput comes from
txstream --duration=10m --rate=6 --lanes=6

# 3. leak hunt — constrain the heap so a leak becomes an OOM, not an argument
java -Xmx512m -jar <jar> txstream --duration=12h --rate=4 --lanes=4 --data=/var/soak/run-17

# 4. reorg survival (DevKit only)
txstream --duration=30m --rate=4 --lanes=4 --chaos-rollback=5m --snapshot-cadence=15

# 5. crash survival — needs the restart loop, see below
while :; do java -jar <jar> txstream \
    --duration=6h --rate=4 --lanes=4 --chaos-crash=20m --data=/var/soak/run-18; done
```

### Reading the result

| Line | Means |
|---|---|
| `expected total` == `actual total` | value conserved — nothing paid twice, nothing dropped |
| `LOST / NOT ON CHAIN` | reported confirmed, absent from the chain |
| `DOUBLE PAID` | a recipient got more than intended — the serious one |
| `never registered` | journalled but the crash beat the store. Expected with crash chaos |
| `backpressure` climbing | you are submitting faster than the chain confirms |

Exit codes: **0** clean · **1** discrepancies · **2** insufficient funding.

## Picking a rate

A lane confirms at most one transaction per block — and the *next* transaction on a lane can
only build once the backend's UTXO view has caught up with the previous one, which costs
roughly another block on a public network. So:

> **`rate ≤ lanes ÷ (2 × blockTimeSeconds)`**

| Network | block time | 4 lanes → sane rate |
|---|---|---|
| Yaci DevKit | ~1s | ~2/sec |
| preprod / mainnet | ~20s | ~0.1/sec |

The first preprod soak used the old `lanes ÷ blockTime` guidance and was ~2× over-driven:
672 of 1439 items were rejected by the node with `All inputs are spent` because each lane's
next transaction was built from a Blockfrost UTXO view that had not yet caught up with the
previous one. The `--utxo-gate` (on by default) now prevents this structurally: each lane
holds its next submission until the previous confirmed transaction's outputs are actually
queryable via `UtxoSupplier.getTxOutput`. With the gate on, an over-driven rate degrades into
waiting rather than into rejected transactions.

Over-driving is not an error — it builds a backlog, then `trySubmit` starts returning `FULL`
and the backpressure counter climbs. That is the stream refusing work correctly. You just want
to be choosing it. `--planner=batching --window=20` raises the ceiling ~20x by merging many
payments into one transaction (see the trade-off below).

## Public networks (preprod)

Defaults target a local DevKit. For preprod, set three environment variables:

```bash
export CARDANO_BF_URL=https://cardano-preprod.blockfrost.io/api/v0/   # note v0, not v1
export BF_PROJECT_ID=preprod...
export SOAK_MNEMONIC="your own funded preprod mnemonic"

java -jar <jar> txstream --duration=2h --rate=0.2 --lanes=4 --data=/var/soak/preprod-1
```

**Do not use the default mnemonic on a public network.** It is Yaci DevKit's well-known test
phrase — anyone can derive those keys and sweep the funds.

There is no faucet, so the run performs a funding preflight and refuses to start with exactly
what to send where:

```
lane-0  addr_test1...5y6sq9  balance 0.00 ADA  needs 58.50 ADA  SHORT

  INSUFFICIENT FUNDS — there is no faucet on this network.
  Fund these addresses before starting:
    send at least 58.50 ADA to addr_test1qrjv...   (lane-0)
```

Fund them, re-run. `--force` starts anyway and stops when it runs dry.

Note that a DevKit running locally does **not** make a preprod run a devnet run — the faucet
and rollback are enabled only when the *backend* is local.

## Lanes and funding

**A lane is a pot of money, and TxStream serialises everything spending from the same pot.** So
`--lanes=1` can never exceed roughly one transaction per block however hard you drive it, and it
exercises none of the interesting machinery. Use several:

```bash
--lanes=4     # 4 funding accounts, 4 lanes, genuinely concurrent
```

Each lane gets its own derived account registered as `account://lane-N`, and every item is built
with that lane's `fromRef`, so lane scope is satisfied by construction. Observed in a short run:
`inflight=1` with one lane, `inflight=3` with three.

## Chaos

All off by default. Each fault has an independent interval so they interleave.

```bash
--chaos-crash=10m       # Runtime.halt() mid-flight
--chaos-rollback=15m    # rewind the chain under the stream (DevKit only)
--chaos-failover=10m    # abort the active instance (needs --instances=2)
```

**Crash chaos needs a restart loop.** `halt()` takes the JVM with it, by design — that is the
point. The run resumes from the journal:

```bash
while :; do java -jar cardano-client-txflow-soak.jar txstream     --duration=6h --rate=5 --lanes=4 --chaos-crash=20m --data=/var/soak/run-3; done
```

The journal (`--data/journal/`) records *intent* only — order id, recipient, amount, lane —
flushed on every write. Outcomes are recovered afterwards from the durable store and the chain,
because recovering them is exactly what is under test; letting the harness record its own
outcomes would be marking its own homework.

**Rollback depth is the snapshot cadence.** A DevKit rollback rewinds to the *last* snapshot, so
`--snapshot-cadence=15` (default) means each reorg is at most ~15s deep. Snapshotting only once
at startup would make every rollback erase the entire run — a chain reset, not a reorg, and it
invalidates the soak. This bit us the first time; the cadence exists because of it.

## What it checks

The stream's own projection is deliberately **not** the oracle — it is one of the things under
test. The chain is the authority. Three checks, which cannot all fail the same way:

| Check | Catches |
|---|---|
| **Item attribution** — every item terminal, every confirmed item's tx present on chain | work silently dropped |
| **Value conservation** — each recipient's on-chain balance delta equals the sum of payments intended for them | **double payments** — which per-item status can never detect, because a duplicate leaves both items looking `CONFIRMED` |
| **Store hygiene** — no non-terminal items, no leftover resource leases, bounded row counts | recovery that did not complete |

Exit code is `0` only when the run is clean.

## Output

Everything lands under `--data`:

- `samples.csv` — one row per sample: post-GC heap, threads, accepted/confirmed/failed,
  in-flight, store rows. This is the file that tells you whether it leaks or degrades.
- `report.txt` — the final reconciliation.
- `txflow-soak.mv.db` — the H2 store the run used, kept for post-mortem inspection.

Heap is sampled as **old-generation collection usage**, not live usage. Live heap sawtooths with
allocation and says nothing about retention.

## Example output

```
  submitted            179
  confirmed            179
  non-terminal         0

  -- value conservation (the double-pay check) --
  expected total       218910000 lovelace
  actual total         218910000 lovelace
  transactions checked 179 on chain

  -- resources --
  heap trend           -0.0 MB/hour (post-GC)
  throughput           29.2/min overall  (first third 29.0 -> last third 29.5)
  orphan leases        0

  RESULT: CLEAN — nothing lost, nothing paid twice, nothing left behind
```

## Reading a run

- **`in_flight` pinned at 1** with one lane is correct, not a stall — a lane is serial by
  definition. Concurrency comes from more lanes, not more threads.
- **Submitting faster than the chain confirms** builds a backlog that drains after submission
  stops. That is worth knowing about your rate, not a defect.
- **`store_rows` growing linearly** is expected for an append-only journal. Growth that
  *accelerates*, or that never plateaus once retention should be evicting, is not.
- **Backpressure counts rising** means `trySubmit` is returning `FULL` — the stream is
  correctly refusing work rather than queueing without bound.

## Post-mortem reconciliation

A run's verdict can be recomputed at any time from its `--data` directory — the journal, the
H2 store, and the chain are all still there:

```bash
java -jar cardano-client-txflow-soak.jar reconcile --data=/tmp/soak-preprod-1
```

Same network options/env vars as `txstream`. This is how a run gets re-judged after a tool
fix without re-running two hours of load, and how a crashed run gets its final answer.

### PAID BUT NOT REPORTED CONFIRMED

The reconciler chain-checks every non-confirmed item that retained a transaction hash. One
that **is** on chain is counted in the expected totals and listed under
`PAID BUT NOT REPORTED CONFIRMED` — the money conserves; the discrepancy is the reported
status. This is deliberately not called `DOUBLE PAID`: it is evidence of a status mislabel
(e.g. a confirmation timeout settled as `FAILED` — the library-side fix makes those
`RECOVERY_REQUIRED`), not of a duplicate payment. The one thing it must never trigger is a
retry: the payment already landed.

## Restart behaviour

`--on-restart=resubmit` (the default) replays work a crash interrupted, under the **same order
id**. That is safe under every planner, and the reason is worth understanding:

```java
// EngineTxFlowStream.accept()
stateStore.registerItem(...)                     // BEFORE planning or batching
} catch (TxStreamDuplicateItemException d) {
    return Acceptance.conflict(d);
```

The durable store registers an item id before the item is ever buffered, planned or merged. So
an id it already knows is refused outright (`CONFLICT`, counted as *already known to the
store*), and an id it does not know was never planned and cannot be duplicated. The store is
the guard.

The batching hazard is real but lives elsewhere: resubmitting the same logical payment under a
**different** item id. That is a new idempotency claim, and under `batching` everyone in the
new batch pays again. This tool never changes an item id, so it cannot hit it.

`--on-restart=skip` accounts for interrupted work as lost instead of replaying it.

## A finding worth knowing

A 150s run with 10-second reorgs every 50s produced:

```
  confirmed            122
  failed               28
  expected total       149298000 lovelace
  actual total         140856000 lovelace
  LOST / NOT ON CHAIN (7)
```

The gap is 8.442 ADA over exactly 7 transactions — 1.206 ADA each, precisely the payment size.
**The two independent checks agree**, which is the reconciler working: seven transactions were
reported `CONFIRMED`, then a reorg erased them, and nothing re-detected it.

This is expected behaviour, not a defect. The default rollback monitoring horizon is
`UNTIL_STEP_CONFIRMED`: once a transaction reaches its required depth, monitoring stops, so a
*later* reorg is never noticed. What the soak does is **quantify the exposure** — with a shallow
confirmation depth and 10-second reorgs, ~5% of payments were reported confirmed but erased.

The mitigation is the ordinary Cardano one: require more confirmations than your realistic reorg
depth, the same way an exchange does. For stream-planned flows the confirmation depth is not
directly settable on the engine (`FlowExecutionPolicy` is a ceiling, not a default) — it comes
from a flow definition's `spec.execution.confirmation.min_confirmations`, so raising it means
submitting via a registered **template** rather than a bare `TxPlan`. That is a natural next
addition to this tool.

## Notes

- The treasury is topped up from the DevKit admin faucet as the run drains it. On a public
  network there is no faucet: fund the account first, and the run will simply stop when it runs
  out — itself a useful signal.
- `SIGTERM` (Ctrl-C) stops submission, drains what was accepted, and still reconciles. A soak
  interrupted early still produces a valid report.
