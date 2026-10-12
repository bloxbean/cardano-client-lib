# chain-parse-check

A manual, full-chain check of CCL's CBOR parsing. It streams every block from the first Shelley block to the tip
from a Cardano node, parses each block and transaction with CCL and compares the results with values computed
independently from the raw bytes. It is meant to be run by hand from time to time — before a release, or after a
change to the CBOR, raw-view or model code — not in CI.

It is a **standalone Gradle build**, not part of the root build: it reads blocks with
[Yaci](https://github.com/bloxbean/yaci), and Yaci depends on CCL, so including it in the root build would be a
circular dependency. The root `settings.gradle` does not include it, and `./gradlew build` / `./gradlew test` at the
root never compile or run it.

## What it checks

Per block (Shelley to Conway):

- `RawBlock`: era detection against the block envelope, the header shape (TPraos or Praos), the block body hash
  against the header, and that transaction bodies, witness sets, auxiliary data keys and invalid transaction
  indexes line up.

Per transaction:

- `RawTx`: the transaction id against a hash of the body slice, `TransactionUtil.getTxHash` and a re-parse of the
  transaction bytes; the auxiliary data hash against body key 7.
- Native scripts (witness, auxiliary data and reference scripts): the script hash from the raw bytes against the
  model's hash, and `NativeScriptEvaluator` with the transaction's vkey witnesses and validity interval — every
  witness native script must hold.
- Plutus scripts: the raw script hash against the model's hash.
- Datums (witness and inline): the raw datum hash against `PlutusData.getDatumHashAsBytes()`; redeemer data
  decodes.
- Script data hash (Alonzo and later): recomputed with the epoch's cost models from Koios and compared with body
  key 11.
- `Transaction.deserialize`: succeeds, and the model agrees with the raw view on inputs, outputs, fee, TTL,
  validity start, auxiliary data hash, script data hash, `isValid`, witness counts and redeemers.

### What it does not check

- **Byron** blocks are out of scope: they are counted and skipped.
- **Script data hash**: the languages of reference scripts in spent or referenced outputs are not known without a
  UTxO set, so the check accepts any superset of the witness-script languages for which the epoch has a cost model
  (`sdh_matched_with_ref_script_languages` counts those matches).
- **Model hashes on non-canonical encodings**: a model re-encodes what it parsed, so a native script or datum hash
  from the model can differ from the on-chain hash when the on-chain encoding is not canonical (indefinite
  lengths, non-minimal heads, unsorted map keys, ...). An independent byte-level scan decides whether an encoding
  is canonical; mismatches are reported only for canonical encodings and are counted otherwise
  (`native_script_noncanonical`, `datum_noncanonical_*`).
- Ledger rules: nothing is validated beyond the parsing and hashing above.

## Build

Java 25 is required (`yaci-core` 0.5.x is compiled for Java 25). The build uses its own Gradle wrapper; Gradle finds
a JDK 25 toolchain on the machine.

The CCL version under test defaults to `version` in the root `gradle.properties` and is resolved from Maven Local,
so publish it first:

```bash
# at the repository root (CCL's build runs on JDK 17 or 21)
./gradlew publishToMavenLocal

# then
cd tools/chain-parse-check
./gradlew installDist
```

Options: `-PcclVersion=<version>` tests another published CCL version, `-PyaciVersion=<version>` another
`yaci-core`. Every `cardano-client-*` module Yaci brings in is resolved to the CCL version under test.

## Run

```bash
cd tools/chain-parse-check
build/install/chain-parse-check/bin/chain-parse-check --network=preprod
```

| Option | Default |
|---|---|
| `--network=<mainnet\|preprod\|preview>` | required |
| `--host=<host> --port=<port>` | the network's public relay (`backbone.cardano.iog.io`, `preprod-node.play.dev.cardano.org`, `preview-node.play.dev.cardano.org`, port 3001) |
| `--run-dir=<dir>` | `runs/<network>` (ignored by git) |
| `--fresh` | resume when the run directory has state |
| `--workers=<n>` | 1 parser thread |
| `--max-blocks=<n>` | no limit: stop after n blocks in this session |
| `--stop-slot=<slot>` | the tip: stop after the last block at or before this slot, to compare two CCL builds over the same range |
| `--koios=<url>` | the network's public Koios, for cost models |
| `--no-script-data-hash` | check the script data hash (needs Koios) |

A local node is much faster than a public relay. The tool opens one node-to-node connection for blocks (plus a
short chain-sync connection at start to find the range and the tip):

```bash
# a local preprod node on port 32000
build/install/chain-parse-check/bin/chain-parse-check --network=preprod --host=localhost --port=32000

# mainnet, in the background
nohup build/install/chain-parse-check/bin/chain-parse-check --network=mainnet --host=localhost --port=3001 \
    > mainnet.log 2>&1 &
```

Expected run times, one worker: with a local node preprod takes about 7 minutes and mainnet about 40 minutes;
preview takes about 35 minutes from its public relay.

### Cost models

The script data hash check needs the Plutus cost models of each epoch. They are fetched once from Koios
`epoch_params` (read-only, paged, at most one request per second) and cached in `<run-dir>/cost_models.json`; an
epoch missing from the cache causes a refetch at most once a minute. If Koios is unreachable the check is skipped
and counted (`sdh_skipped_no_costmodels`); `--no-script-data-hash` skips it without any request.

### Resume

State is saved every 15 seconds and at the end in `<run-dir>/state.json`. Running again with the same network and
run directory resumes after the last saved block — after a stop, a crash, or a completed run, to check the blocks
added since. `--fresh` starts over.

The state records the run's identity: the network, the CCL version, a SHA-256 of the `cardano-client-*` jars on the
class path (a SNAPSHOT can be republished with other code) and whether the script data hash is checked. A run
resumes only with the same identity; after rebuilding against another CCL, or with other options, it refuses to
start and asks for `--fresh` or another `--run-dir`, so the counters of a run always come from one implementation.
The summary shows the identity. After a crash, `issues.jsonl` can repeat entries for the blocks processed after
the last save; the counts in `state.json` and the summary do not.

## Results

In the run directory:

| File | Contents |
|---|---|
| `status.json` | progress while running: slot, percent of the tip, blocks per second, issue counts |
| `summary.txt` | at the end: counters per era, issue counts, the first three examples of each issue kind |
| `summary.json` | the same counters as JSON |
| `issues.jsonl` | one line per finding (up to 5,000 per kind): era, slot, block, transaction, check and detail; the transaction CBOR for the first 200 of each kind |
| `failed-blocks/` | block CBOR (hex) for the first 50 block-level findings of each kind |

A clean run has `outcome complete` and no issue counts. Each issue kind names the check that failed, for example
`body_hash`, `txid_transaction_util`, `tx_deserialize`, `native_script_hash_canonical`, `script_data_hash`,
`datum_hash_canonical_inline` (a `_bignum_` infix means the datum contains a bignum) or `model_fee`; `*_error` kinds
are exceptions, with a stack trace in the detail. The era table and "other counters" show how much was covered:
transactions, scripts, datums, redeemers and script data hashes checked.

To check a finding, decode the `txCbor` of its `issues.jsonl` line, or the block in `failed-blocks/`, and parse it
with CCL in a unit test.
