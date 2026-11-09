# ADR 0005: The Haskell-Strict Decoding Policy and the Full Conformance Gate in CCL

**Status**: Proposed
**Date**: 2026-11-09
**Risk**: High. The policy decides which transactions and block headers Yano accepts. A mistake made while moving it
changes what a node admits.
**Related**: ADR 0002 (D9, D10); ADR 0003 (D1, D6, D9, I1, I7, I9); Yano ADR-060 (node-diversity reports); PR #748
**Modules**: CCL `ledger-cbor`, `transaction-spec`, a new conformance module; Yano `ledger-rules`, `ledger-conformance`

This ADR amends ADR 0003. ADR 0003 D6 made the reader report what it reads and left "what to reject" to the caller:
CCL shipped only `FormatPolicy.ACCEPT_ALL`, and the policy matching Haskell's decoders stayed in Yano. This ADR moves
that policy into `ledger-cbor` as an opt-in, and makes CCL run the full cardano-cbor-dataset gate, valid and invalid
samples, before a release. Yano keeps publishing the node-diversity report, from the same code.

## 1. Context

### 1.1 Goal

A change to the reader or the policy should be proven against the whole dataset in CCL, before any downstream project
takes it. The result Yano publishes for the node-diversity page should be the same result, produced by the same code.

### 1.2 What each project checks today

Measured at CCL `d14956d1` and Yano `0b4eb61cd` (branch `feat/ledger-cbor-reader`), dataset `4fd977b2`.

| Check | Where | Samples | Decides acceptance with |
|---|---|---|---|
| `CborCorpusConformanceTest` | CCL `transaction-spec` | Valid Conway samples of the rules CCL has a model for, and Dijkstra's | CCL's models (lenient). Invalid samples are out of scope by its Javadoc, "as rejecting them is the job of a strict reader" (ADR 0002 D10) |
| `LedgerReaderConformanceTest` | CCL `transaction-spec` | Valid Conway and Dijkstra samples | A walk with a structural policy: no unknown, repeated, out-of-era or missing field |
| `StrictLedgerPolicy` | CCL `ledger-cbor` test fixtures | Reader unit tests | Shapes, hash sizes and integer widths, not other lengths and ranges. Not published |
| `CborDecodingDiversityReportTest` | Yano `ledger-conformance` | All 43,434 Conway samples, valid and invalid | CCL's reader + Yano's `HaskellPolicy`, then CCL's writers for re-encoding. Result: 43,365 of 43,434 |

So CCL proves only that valid samples are accepted. Whether invalid samples are rejected, 31,842 of the 43,434 Conway
samples, is first measured in Yano, after a CCL release.

The 69 samples Yano misses at `4fd977b2`:

- 64 indefinite-length Allegra auxiliary data arrays the dataset calls valid. Yano rejects them as deployed nodes do:
  `decodeAllegra` is a `RecD` record below decoder version 12 (ADR 0003 section 7.1).
- 4 rationals with bignum or negative parts: bloxbean/cardano-client-lib#749.
- 1 Byron address re-encoding in the harness.

### 1.3 Where the strict decoding lives in Yano

After ADR 0003 M6, Yano's reading is CCL's views, and its rejection is a policy plus a few validators. In package
`org.yanoproject.ledger.rules.conway.tx` at `0b4eb61cd`:

| Class | Lines | What it is |
|---|---|---|
| `HaskellPolicy` | 550 | The `FormatPolicy`: hash and key sizes, integer widths, definite-length rules below decoder version 12, sets without duplicates, required fields, multi-asset size, era of a field |
| `StrictCbor` | 191 | Strict item reads used by the policy |
| `CborCanonical` | 327 | Canonical encoding, for "equal as decoded" in sets and maps |
| `BoundedFields` | 108 | URL, DNS name and rational bounds |
| `CborCursor` | 206 | A flat head walker, used by the three validators below |
| `PlutusData` | 141 | Iterative Plutus data validator |
| `Metadata` | 100 | Iterative metadata validator |
| `AddressBytes` | 193 | `validate` is the decoder's check (uses CCL's `ByronAddress`); the rest are rule helpers |
| `RawScript` | 205 | `readScript` and `of` are decoding checks; `accepted` builds a native script through CCL's `RawModels`; the record is the rules' |
| `RawCredential`, `RawVoter`, `RawTransaction` | 66, 94, 537 | 1, 2 and 2 remaining rejections; otherwise records and mapping for the rules |

None of the first seven imports a Yano type: apart from each other, they import only CCL's `common` and the reader.
The rule-side types (`GovActionId`, `ProposalState`, `Outpoint`) appear only in the mapping classes, which stay.

Outside the package, Yano's rules use `AddressBytes` helpers (`UtxoChecks`, `WitnessNeeds`), `RawScript`
(`ScriptIntegrity`, `MinFee`, the script evaluators) and `PlutusData` (`PlutusScriptDecoder`). The runtime's
`ShelleyHeaderView` calls `HaskellPolicy.header`.

### 1.4 Why ADR 0003 kept the policy in Yano, and what changed

ADR 0003 D6 and section 9 rejected "a reader with a strict or version parameter, as Amaru does": CCL serves builders
and chain followers, which should not get ledger rejections, and a decoder version would sit in every public path, for
one consumer. ADR 0002 D9 said the same, and that the Plutus data validator had no second consumer.

Both reasons still hold for the default path, and this ADR keeps it: the policy is a separate object the caller passes,
not a mode of the views. What changed is that there is now a second consumer: CCL's own release gate. Without the
policy, CCL cannot tell whether a reader change makes Yano accept an invalid sample, and that is found only after a
release.

## 2. Scope

In scope:

- moving Yano's Haskell decoding policy and its validators to `ledger-cbor`, as an opt-in;
- strict entry points for a transaction, a header, a record and a value;
- one conformance harness, used by CCL's gate and by Yano's report;
- CCL's CI gate over all Conway samples, valid and invalid;
- what Yano keeps, deletes and runs.

Out of scope:

- Ledger rules. Fees, balance, witnesses, scripts and every rule that crosses fields stay in Yano.
- Dijkstra's decoder version (12). `HaskellPolicy.forEra` stays Conway-only; Dijkstra's rules arrive with ADR 0003
  M7b, now in CCL.
- Yaci. It keeps its tolerant reading for sync; a later decision may use the policy.
- Checking mutated transactions against the Haskell ledger directly (bloxbean/yano#242).
- Changing any rejection. This is a move: every outcome stays as it is (J1).

## 3. Threat model and trust assumptions

- **Untrusted input**, as ADR 0003 section 3: blocks and transactions from peers and APIs, built to exhaust stack or
  memory.
- **Strict is not validity.** A transaction that passes the policy decodes as Haskell decodes it at the era's decoder
  version. It can still fail every ledger rule. The Javadoc says so, and so does the release note.
- **Correlated failure.** ADR 0003 accepted that one reader bug reaches CCL, Yaci and Yano together. The policy now
  joins it: a policy bug passes CCL's gate and Yano's report alike. The independent check stays the dataset, which is
  generated from Haskell, and the exact per-sample lists (J4). A direct Haskell oracle is bloxbean/yano#242.
- **Default path unchanged.** CCL users who do not pass the policy get exactly today's behaviour (J2).

## 4. Pinned references

| Reference | Version |
|---|---|
| CCL | `feat/ledger-cbor` `d14956d1` (PR #748) |
| Yano | `feat/ledger-cbor-reader` `0b4eb61cd` (local; pushed with ADR 0003's Yano PR) |
| cardano-cbor-dataset | `4fd977b201f7426a2983161a702bb127c87973f7`, CCL CI's `CBOR_DATASET_REVISION` and Yano's `corpora.env` |
| cardano-ledger | `f649f9751074d2ab3de033fc3912f29c9862c1f5`, as `HaskellPolicy`'s Javadoc cites; ADR 0003 section 4 |

## 5. Decisions

### D1. The Haskell decoding policy becomes CCL's, opt-in

- `HaskellPolicy` moves to `ledger-cbor`, public, in package `com.bloxbean.cardano.client.transaction.raw`. Its name and
  its `forEra(Era)` stay: Conway decodes at version 9, and any other era is refused until it has rules.
- `FormatPolicy.ACCEPT_ALL`, `lenient` and `tolerant` stay, and stay the defaults. No view, model or Yaci path calls the
  policy unless its caller passes it.
- This amends ADR 0003:
  - D1's table: "Policy" becomes "CCL `ledger-cbor` for Haskell's decoders (opt-in); Yano for ledger rules; Yaci for
    fail-or-continue during sync".
  - D6's "CCL ships only `ACCEPT_ALL`" becomes "CCL ships `ACCEPT_ALL` as the default and `HaskellPolicy` as an
    opt-in".
  - D9's first bullet becomes "Yano enforces CCL's `HaskellPolicy`".
- ADR 0002 D9's remaining parts ("the strict reader stays in Yano", "the `PlutusData` validator stays") are superseded.
  `julc-core` stays CCL-free, as ADR 0002 D2 says: JuLC is not a consumer here.

The decoder version stays inside the policy, as ADR 0003 D6 put it. No view gains a version parameter, which keeps
ADR 0001's "no options" for the models.

### D2. What moves, and what stays in Yano

| Yano class | Decision |
|---|---|
| `HaskellPolicy`, `StrictCbor`, `CborCanonical`, `BoundedFields`, `CborCursor`, `PlutusData`, `Metadata` | Move whole. Package-private where nothing outside `ledger-cbor` calls them |
| `AddressBytes` | `validate` moves (D3). The rule helpers (`isVKeyLocked`, `network`, `bootstrapAttrsSize` and the rest) stay in Yano |
| `RawScript` | The decoding checks (`readScript`, the language and definite-length checks) move, and so does the native script check (D3). The record the rules use stays |
| `RawCredential`, `RawVoter`, `RawTransaction` | Their 5 remaining rejections either move into the strict reads (D4) or are shown unreachable after the policy, each listed in M1's record. The mapping into rule records stays |
| `TxDecodingException` | Stays in Yano as the engine's `DecodingFailure`. CCL throws its own type (D4) and Yano wraps it at its boundary, as it wraps `CborRuntimeException` today |

Tests move with the code they test: `HaskellPolicyTest` (466 lines), `DefiniteLengthDecodingTest` (425),
`DefiniteRecordDecodingTest` (200), `CertificateDecodingTest` (178), `WitnessDecodingTest` (134), `BoundedFieldsTest`
(131), `PlutusDataTest` (81), `DeepNativeScriptTest` (66), less any parts that test Yano's mapping.
`RawTransactionTest`, `RawProposalTest` and `LedgerValueTest` stay.

CCL's `StrictLedgerPolicy` fixture is deleted. Its one property the policy lacks, "a `FieldType` with no rule fails, so
a new type gets a rule when it is added to the tables", becomes a test on `HaskellPolicy` (J9).

### D3. Two dependencies the move needs

- **Byron addresses.** `AddressBytes.validate` decodes a Byron address with CCL's `ByronAddress`, in module `address`.
  `ledger-cbor` gains `api project(':address')`. `address` depends on `common`, `crypto` and `common-spec` and holds no
  model and no ledger rule, so ADR 0003 D1's boundary holds. ADR 0003 I1 becomes J6. Users of `transaction-spec`
  already get `address` through it.
- **Native scripts.** Yano checks a native script by building CCL's model (`RawModels.toScript`, in
  `transaction-spec`). `ledger-cbor` cannot call a model, by ADR 0003 D2. So `ledger-cbor` gains an iterative
  native-script validator, written from the ledger's decoder, next to the Plutus data and metadata validators. It
  checks; it builds nothing.

Alternatives: putting the policy in `transaction-spec`, where both are reachable, mixes the policy with the models,
which ADR 0003 D2 made a module to prevent. Copying `ByronAddress`'s decoding into `ledger-cbor` repeats format code
that already has one home.

### D4. Strict entry points

The policy keeps its walk methods, and `ledger-cbor` adds one strict read per kind of input. Illustrative:

```java
// Illustrative
HaskellPolicy p = HaskellPolicy.forEra(Era.Conway);
RawTx tx = p.transaction(bytes);   // RawTx.of, the walk, and every check Yano's reading makes today
RawHeader h = p.header(span);      // either header layout, as today
p.walk(recordSpec, span);          // one record of the tables
p.walk(fieldType, span);           // one value: its rule, then its table
p.script(span);                    // script = [language, script]
```

- Each throws `LedgerDecodingException`, a new subclass of `CborRuntimeException`, so a caller that catches the
  reader's exception today catches this one too.
- **The transaction read is the complete decoding decision.** After it accepts, Yano's mapping into its rule records
  must not reject (J5). That is what lets the conformance harness measure CCL's read and still describe Yano's
  admission.
- Strict reads are stack-safe as the walk is (J7).

### D5. One conformance harness, in CCL

A new CCL module holds the harness, proposed as `ledger-cbor-conformance` (artifact
`cardano-client-ledger-cbor-conformance`), depending on `transaction-spec` and `plutus` for the writers. It holds what
Yano's `ledger-conformance` holds today for this report:

- the rule table: each dataset rule name to its strict read (D4), CCL's model decoding and its writer;
- the minimal valid parents that rules checked by their parent are placed in (Yano's `CborPlacedRules`);
- the dataset's normalization (one copy; CCL's test and Yano's `CborNormalizer` have one each today);
- a runner that gives a per-sample outcome: accepted or rejected, the re-encoding's result, and the reason;
- the exact list of misses for its pinned dataset revision (D6).

It has no test framework dependency and reads the dataset from a path it is given, so it is a library, not a test.

The Yaci column of Yano's report stays in Yano, because CCL cannot depend on Yaci. It remains a diagnostic.

Whether this is a published module or Yano keeps its own harness is Q1.

### D6. CCL's gate covers the whole Conway dataset

- CI's CBOR conformance job runs the harness over every Conway sample at `CBOR_DATASET_REVISION`, valid and invalid.
- The list of misses is exact, per sample, as ADR 0003 I7 is: a new miss fails, a listed sample that passes fails, and
  a listed sample that misses for another reason fails (J4).
- `CborCorpusConformanceTest` stays. It gates the lenient model path, which CCL users call and the strict gate does not.
- `LedgerReaderConformanceTest`'s Conway part is covered by the new gate and goes. Its Dijkstra part stays until
  ADR 0003 M7b gives Dijkstra a policy.
- The workflow's guard on the number of tests moves with the change.

### D7. Yano's part

- Depends on the CCL release with M1 and M2. Deletes what D2 moves, and wraps `LedgerDecodingException` in
  `TxDecodingException`.
- `RawTransaction.parse` calls `HaskellPolicy.transaction` and maps the result.
- The `cbor-decoding` report runs CCL's harness and writes the same report format, version 2, with the Yaci column
  added. The release workflow and the files the node-diversity page reads do not change.
- `CborKeyedWalkDiversityReportTest`, ADR 0003 M1's keyed-record proof, is deleted: the walk is now the only strict
  reader, so there is nothing beside it to compare.
- Yano's report checks its per-sample outcomes against the harness's list (D8), and fails on any difference.
- Yano ADR-060 and ADR-056 get notes pointing here.

### D8. Dataset pins

The harness's list belongs to one dataset revision, and Yano follows the newest upstream data.

- At the same revision, Yano's outcomes must equal CCL's list exactly (J3).
- Yano may run a newer revision than its CCL version's list. Then it passes only if no new sample misses; a listed
  sample that the newer revision no longer has is ignored.
- A newer revision that adds a miss needs a CCL change first: a fix, or the list updated after review. That is the rule
  this ADR exists for: CCL is checked before Yano publishes.

Q3 records the alternative.

### D9. Release

M1 and M2 ship in one CCL pre-release, then Yano follows (M4). Which pre-release is Q2.

## 6. Invariants

| ID | Invariant | Constrains | Checked by |
|---|---|---|---|
| J1 | Moving changes no outcome. Over every Conway sample at the pin, CCL's strict read after the move accepts and rejects exactly what Yano's strict side did before it, and re-encodes the same; Yano's `ledger-rules`, `ledger-conformance` and `tx-services` suites pass, less only tests that moved | D1, D2, D3, D4 | A per-sample comparison of Yano at `0b4eb61cd` (or the Yano PR's merge base) with a Yano build on the CCL snapshot; the suites |
| J2 | CCL's default paths do not change: `CborCorpusConformanceTest`'s list, the golden bytes, and ADR 0003's G3 and G5 give the same results as before | D1 | The existing gates, run before and after |
| J3 | At the same dataset revision and CCL version, CCL's gate and Yano's report give identical per-sample outcomes | D5, D7, D8 | Yano's report test fails on any difference from the harness's list |
| J4 | The list is exact: a new miss, a listed sample that passes, or one that misses for another reason each fails the gate. One rejection lost and another gained, with equal totals, fails | D6 | A gate test, as ADR 0003 I7's |
| J5 | After `HaskellPolicy.transaction` accepts, Yano's mapping does not reject | D4 | Every accepted dataset transaction mapped by Yano; Yano's suites. Any rejection left in Yano is listed in M1's record with why it cannot move |
| J6 | `ledger-cbor` depends only on `common`, `common-spec` and `address`, and holds no model and no ledger rule (amends ADR 0003 I1) | D3 | The dependency test of ADR 0003 I1, updated |
| J7 | Strict reads and the validators are stack-safe at maximum nesting | D2, D4 | ADR 0003 I9's depth fixtures and Yano's `DeepNativeScriptTest`, on the default stack, `-Xint` and virtual threads |
| J8 | Yano's four node-diversity reports (CBOR, ledger rules, UPLC, turbo) are unchanged at the same pins, apart from the version label | D7 | Before and after, as ADR-062's removal was checked |
| J9 | Every `FieldType` has a rule in `HaskellPolicy` or is listed as having none | D2 | A test over `FieldType.values()` |

## 7. Milestones

| ID | Project | What | Entry | Exit |
|---|---|---|---|---|
| M1 | CCL | D1 to D4: the policy, validators, strict reads, the exception and the `address` dependency, with the tests moved | ADR accepted; #748 merged | J1 (Yano's current report run against the CCL snapshot), J2, J5, J6, J7, J9 |
| M2 | CCL | D5, D6: the harness and the full gate in CI | M1 | J4; the gate's numbers equal Yano's report at the pin (43,365 of 43,434 at `4fd977b2`, or as M1 changes them, with each change listed) |
| M3 | CCL | Release (D9) | M2; ADR 0003's G3 and G5 | Released |
| M4 | Yano | D7 | M3 | J1, J3, J5, J8; the moved classes and the keyed-walk test deleted |

M1's record lists, for each of `RawCredential`'s, `RawVoter`'s and `RawTransaction`'s 5 remaining rejections, where it
went or why it cannot be reached after the policy.

## 8. Verification

- **Independent evidence.** The dataset is generated from cardano-ledger, so its labels are Haskell's, not this code's.
  The 64 auxiliary data samples stay the documented exception, as ADR 0003 section 7.1 records.
- **Differential.** J1 compares old and new per sample, so a move that loses one rejection and gains another fails.
- **Negative cases.** The moved tests are mostly rejections, each citing the Haskell decoder it follows.
- **Not evidence.** A passing gate does not prove ledger validity (section 3).

## 9. Compatibility

| Item | Impact |
|---|---|
| CCL models, `lenient`, `tolerant`, `ACCEPT_ALL` | None (J2) |
| CCL public API | New: `HaskellPolicy`, `LedgerDecodingException`, the strict reads and the harness module. Pre-release API, like the rest of `ledger-cbor` |
| `ledger-cbor`'s dependencies | Gains `address`. No change for `transaction-spec` users, who have it already |
| Native image | The policy and validators use no reflection |
| Yano | Same decisions (J1). About 1,600 lines of main code and 1,700 of tests leave Yano |
| Yano's report files | Same format and content (J8) |
| Release order | CCL, then Yano, as ADR 0003 |

## 10. Alternatives considered

| Alternative | Why not |
|---|---|
| Keep the policy in Yano (ADR 0003 D6 as written) | CCL cannot see invalid samples, so a reader change's effect on rejection is found after a release |
| Copy Yano's policy into CCL's tests only | Two copies of the decoding rules, kept equal by hand. The report Yano publishes would not be what CCL gated |
| A strict mode or version parameter on the views | Rejected by ADR 0003 section 9 for the default path, and still rejected: the policy stays a separate object |
| Policy in `transaction-spec` | Mixes rejection with the models; ADR 0003 D2 made `ledger-cbor` a module to stop that |
| Run Yano's report in CCL's CI | CCL would depend on a Yano build, and the dependency runs the wrong way |

## 11. Risks

| Risk | Mitigation |
|---|---|
| A rejection is lost in the move | J1, per sample, and Yano's suites |
| CCL users take strict as validity | Javadoc and release note (section 3) |
| The native-script validator disagrees with CCL's model decoder | The `native_script` and `script` dataset rules and Yano's native script tests, through J1 |
| A policy bug reaches CCL and Yano together | Accepted, as ADR 0003 accepted it for the reader; the dataset and exact lists stay the independent check |
| CCL releases more often, for policy and dataset changes | Accepted by the maintainer on 2026-11-09: a reader or policy change needs a CCL pre-release |

## 12. Open questions

**Q1. Where the harness lives.** Options:
(a) a published CCL module (D5), which Yano depends on in its tests. One rule table, one normalizer, one runner.
(b) Yano keeps its harness, and CCL gets its own; equality is checked only through the per-sample lists.
(c) the harness stays in CCL's tests, and Yano compares its outcomes with a list CCL publishes.
Lean: (a). It is the only option where the report Yano publishes is produced by the code CCL gated. The cost is one
more artifact in CCL's published set.

**Q2. Which pre-release.** Options: (a) 0.8.0-pre8, which then waits for M1 and M2, and holds Yaci's and Yano's
ledger-cbor work (ADR 0003) behind them; (b) pre8 ships ADR 0003 as planned, and this ships in the next one.
Lean: (b). The ledger-cbor reader is reviewed and its downstream work is waiting; this is a separate change with its
own review.

**Q3. A dataset revision that adds a miss.** Options: (a) D8: Yano cannot publish it until CCL fixes or lists it;
(b) Yano may publish with its own list for the newer revision, and catch up with CCL later.
Lean: (a), which is the reason for this ADR. (b) brings back a second list kept by hand.

## 13. Related findings (out of scope)

- Yano's report runs at protocol 11.0 and covers Conway only. Dijkstra's dataset samples are gated in CCL by the model
  and walk tests, not by a policy, until ADR 0003 M7b.
- `RawTransaction`'s mapping is Conway-only, as `HaskellPolicy.forEra` is.

## 14. Revision history

- **r1** (2026-11-09): initial proposal.
