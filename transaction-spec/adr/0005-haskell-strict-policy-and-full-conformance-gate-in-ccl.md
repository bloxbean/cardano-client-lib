# ADR 0005: The Haskell-Strict Decoding Policy and the Full Conformance Gate in CCL

**Status**: Accepted (r5; r6 records the implementation). Reviewer1 had no open findings on r5; the maintainer confirmed
Q1 (a) and Q3 (a) on 2026-11-16. Q4 stays open. M1 and M2 are implemented in this PR (section 7.1); M4 in Yano #253, a
draft stacked on Yano #244; M3 is the release
**Date**: 2026-11-09; r5 2026-11-16; r6 2026-11-17
**Risk**: High. The policy decides which transactions and block headers Yano accepts. A mistake made while moving it
changes what a node admits.
**Related**: ADR 0002 (D2, D9, D10); ADR 0003 (D1, D2, D6, D9, I1, I7, I9, I13, M7b, sections 7.1 and 9); Yano ADR-060
(node-diversity reports); PR #748; PR #753 (#749); Yano #244
**Modules**: CCL `common`, `ledger-cbor`, `transaction-spec`, a new conformance module; Yano `ledger-rules`,
`ledger-conformance`, `runtime`, `script-evaluators`

This ADR amends ADR 0003. ADR 0003 D6 made the reader report what it reads and left "what to reject" to the caller:
CCL shipped only `FormatPolicy.ACCEPT_ALL`, and the policy matching Haskell's decoders stayed in Yano. This ADR moves
that policy into `ledger-cbor` as an opt-in, and makes CCL run the full cardano-cbor-dataset gate, valid and invalid
samples, on the release commit before anything is published. Yano keeps publishing the node-diversity report, from the
same code.

## 1. Context

### 1.1 Goal

A change to the reader or the policy should be proven against the whole dataset in CCL, before any downstream project
takes it. The result Yano publishes for the node-diversity page should be the same result, produced by the same code.

### 1.2 What each project checks today

**r1 measurement, kept as history.** Measured at CCL `d14956d1` and Yano `0b4eb61cd` (branch
`feat/ledger-cbor-reader`), dataset `4fd977b2`. The migration baseline, measured since, follows below.

| Check | Where | Samples | Decides acceptance with | In CI |
|---|---|---|---|---|
| `CborCorpusConformanceTest` | CCL `transaction-spec` | Valid Conway samples of the rules CCL has a model for, and Dijkstra's | CCL's models (lenient). Invalid samples are out of scope by its Javadoc, "as rejecting them is the job of a strict reader" (ADR 0002 D10) | Yes: the only class the CBOR job selects, guarded on `tests="4"` (`build.yml:108-115`) |
| `LedgerReaderConformanceTest` | CCL `transaction-spec` | Valid Conway and Dijkstra samples | A walk with a structural policy, and every certificate accessor; Dijkstra with `StrictLedgerPolicy` | No: it needs `cbor.dataset`, and the job selects only the class above |
| `StrictLedgerPolicy` | CCL `ledger-cbor` test fixtures | Reader unit tests, Dijkstra vectors and the Dijkstra corpus walk | Shapes, hash sizes and integer widths, not other lengths and ranges. Not published | Through the unit tests |
| `CborDecodingDiversityReportTest` | Yano `ledger-conformance` | All 43,434 Conway samples, valid and invalid | CCL's reader + Yano's `HaskellPolicy` (`RawTransaction.parse` for transactions), then CCL's writers for re-encoding. Result: 43,365 of 43,434 at r1; 43,369 at the r5 baseline (below) | Yano's report workflow |

So CCL proves only that valid samples are accepted. Whether invalid samples are rejected, 31,842 of the 43,434 Conway
samples, is first measured in Yano, after a CCL release.

**Nor does the corpus job hold back a release** (CCL master `d90602ea`). A release is a version bump merged to master,
then `tag-release.yml` (after a release owner's approval) pushes the `v*` tag, and `release.yml` builds, signs and
uploads on the tag. `release.yml`'s only build is `./gradlew clean build` without `-PcborDataset`, so every corpus test
skips. `build.yml`'s corpus job does run on the push of the version bump, but nothing waits for it: master's ruleset
requires a code owner's review and no status check, and neither release workflow reads its result. A release can
publish while that job is pending or has failed.

The 69 samples Yano missed at r1 (Yano's `decoding-baseline.txt`):

- 64 indefinite-length Allegra auxiliary data arrays (14 `auxiliary_data`, 50 `auxiliary_data_array`) the dataset calls
  valid. Yano rejects them as deployed nodes do: `decodeAllegra` is a `RecD` record below decoder version 12 (ADR 0003
  section 7.1).
- 4 rationals with bignum or negative parts (2 `unit_interval`, 2 `nonnegative_interval`), all accepted by the strict
  side: 3 with bignum parts fail CCL's model decoder, and the negative one decodes but re-encodes unreduced
  (bloxbean/cardano-client-lib#749).
- 1 Byron address re-encoding in the harness.

The strict side accepts no invalid sample.

**Migration baseline (r5).** Since r1, #748 merged (`6aa36c63`) and #753 (`f80c7547`) fixed #749's rationals: CCL reads
bignum parts, keeps parts as read, and both normalizers reduce a rational as Haskell's `n % d` does, with the sign on
the numerator (CCL `92327070`, Yano `93e60c3d5`). Both PRs are in CCL 0.8.0-pre8. Yano #244 also has P1 (section 1.4).
Re-measured on 2026-11-16:

- Pins: Yano `de892d95` (Yano #244, on CCL 0.8.0-pre8); Yaci #244 at `32726dc5` built from source, as Yano #244's
  head needs its ledger-cbor API, which no Yaci release has yet; dataset `4fd977b2` (clean checkout).
- Command: `./gradlew :ledger-conformance:nodeDiversityReports --tests '*CborDecodingDiversityReportTest'
  -PnodeDiversityCorpora=<dir>`, with an init script that includes Yaci's build and keeps every CCL module at
  0.8.0-pre8, and `CBOR_DECODER_OUTCOMES` set for the per-decoder outcomes.
- Result: **43,369 of 43,434**. The 65 misses are the 64 Allegra auxiliary data arrays and the Byron address above;
  the 4 rationals pass. All 31,842 invalid samples are rejected.
- Model stage: of the 11,528 samples the strict side accepts, CCL's model decodes every one outside the seven
  Yaci-backed header rules (D5). No decoder crashes on any sample. So no sample has a model gap here (J10).

This is the baseline M2's exit is measured against, and the outcomes file is generated from such a run. It is not
copied from Yano's `decoding-baseline.txt`: at `de892d95` that file still lists r1's 69 misses, because ADR 0003 I7
let a monotonic list keep misses that now pass. Section 1.3 is the r1 inventory, with line numbers at `0b4eb61cd`;
of its classes only `HaskellPolicy` and `RawScript` changed since (P1), and M1's record re-takes it at its entry head.

### 1.3 How Yano decides today

**Admission.** `JavaLedgerValidationEngine.run` (`:148-154`) decodes in two steps:

1. `Transaction.deserialize(txCbor)`, CCL's lenient model. Any exception is `DECODING_FAILURE`.
2. `RawTransaction.parse(txCbor, tx)`: `RawTx.of`, the walk with `HaskellPolicy`, then the views mapped into Yano's
   rule records. `TxDecodingException` is `DECODING_FAILURE`; any other runtime exception is `ENGINE_FAILURE`.

Yano ADR-060 section 6 describes both steps. The report's harness, though, calls `RawTransaction.parse(cbor, null)` and
treats the model as a diagnostic. So the report already differs from admission on any input step 1 alone rejects.

**After the walk**, `RawTransaction` can still reject:

| Site | What |
|---|---|
| `RawTransaction:438` | Empty input, before `RawTx.of` |
| `RawTransaction:155` | Translation: any `CborRuntimeException` from the views read after the walk becomes `TxDecodingException` |
| `RawScript.accepted` (`:73-83`) | A native script is built as CCL's `NativeScript` model (`RawModels.toScript`); a model failure is `TxDecodingException`. Reached for witness, auxiliary data and reference scripts |
| `RawOutput.accepted` (`:67-72`) | A reference script read with `RawScript.readScript` |
| `RawCredential:25`, `RawVoter:23,27` | A credential or voter hash that is not 28 bytes; an unknown voter tag |
| `RawCertificate:69,102` | `IllegalArgumentException`, which the engine reports as `ENGINE_FAILURE` |

**The strict classes**, in package `org.yanoproject.ledger.rules.conway.tx` at `0b4eb61cd`:

| Class | Lines | What it is |
|---|---|---|
| `HaskellPolicy` | 550 | The `FormatPolicy`: hash and key sizes, integer widths, definite-length rules below decoder version 12, sets without duplicates, required fields, multi-asset size, era of a field |
| `StrictCbor` | 191 | Strict item reads used by the policy |
| `CborCanonical` | 327 | Canonical encoding, for "equal as decoded" in sets and maps |
| `BoundedFields` | 108 | URL, DNS name and rational bounds |
| `CborCursor` | 206 | A flat head walker, used by `PlutusData`, `Metadata` and `RawScript` |
| `PlutusData` | 141 | Iterative Plutus data validator |
| `Metadata` | 100 | Iterative metadata validator |
| `AddressBytes` | 193 | `validate` is the decoder's check (uses CCL's `ByronAddress`); the rest are rule helpers |
| `RawScript` | 205 | `readScript` and `of` are decoding checks; `accepted` builds the model; the record is the rules' |

None of the first seven imports a Yano type: apart from each other, they import only CCL's `common` and the reader.
The rule-side types (`GovActionId`, `ProposalState`, `Outpoint`) appear only in the mapping classes, which stay.

**Other users in Yano** of what moves:

| Class used | By |
|---|---|
| `HaskellPolicy.header` | `runtime` `ShelleyHeaderView:194-201` |
| `StrictCbor` | `runtime` `ShelleyHeaderView`; `script-evaluators` `ConwayTxInfoTranslator:788-821`; `ledger-rules` `RawParamUpdate:58`, and in the classes that stay, `RawScript.fromScriptRef` (`:93-100`) and `RawOutput.accepted` (`:72`) |
| `PlutusData.validateFirst` (trailing bytes allowed) | `ledger-rules` `PlutusScriptDecoder:534-537` |
| `AddressBytes` helpers | `UtxoChecks`, `WitnessNeeds`, `ConwayTxInfoTranslator` (they stay) |
| `RawScript` record | `ScriptIntegrity`, `MinFee`, `UtxowSubject`, the script evaluators, `runtime` (it stays) |
| `RawScript.fromScriptRef` | `MinFee:108`, `UtxowSubject:135`, `DefaultUtxoStore:1917`, `TransactionOutputProjector:98`, `script-evaluators` `ConwayTxInfoTranslator:499` (found in r6) |
| `TxDecodingException` caught | `JavaLedgerValidationEngine:127`, `ScalusLedgerValidationEngine`, `TransactionOutputProjector:55`, `UtxowSubject:135`, `ShelleyHeaderView:205`, `PlutusScriptDecoder:537`; `AmaruTransactionValidator:359` until Yano #241 removes it |

### 1.4 A gap found while writing this ADR

On the ledger-cbor branches, Yano's native-script check is CCL's model decoder (`RawScript.accepted`). ADR 0004 taught
that decoder type 6, `RequireGuard`, in every era (`NativeScriptCodec.type`, `:126-135`, accepts 0 to 6). Haskell's
Conway decoder accepts 0 to 5 (`TimelockRaw`, `Allegra/Scripts.hs:235-245` at `f649f975`). So Yano on those branches
admits a Conway native script `[6, credential]` that Haskell rejects. Yano `main`, on CCL 0.8.0-pre7, rejects it. This
was found by reading the code and has not been reproduced. It is a prerequisite fix in Yano's ledger-cbor branch,
before that branch merges (P1, section 7). P1 is done: Yano `8811854a6` (Yano #244) makes `HaskellPolicy` refuse a
guard at any depth in Conway, with tests in `WitnessDecodingTest`.

The dataset cannot see it. Its only type-6 sample at `4fd977b2`,
`native_script/manual-invalid/auto-unused-discriminant` (`81 06`, that is `[6]`), is rejected for its arity, which is
why Yano's baseline shows no invalid sample accepted. P1's own test is the only check until the dataset gains a
`[6, credential]` sample.

### 1.5 Why ADR 0003 kept the policy in Yano, and what changed

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
- strict entry points for a transaction, a block, a header, a record and a value;
- Yano's admission taking the strict read as its first decoding verdict, with the model built after it;
- one conformance harness with typed outcomes, used by CCL's gate and by Yano's report;
- CCL's CI gate over all Conway samples, valid and invalid;
- what Yano keeps, deletes and runs.

Out of scope:

- Ledger rules. Fees, balance, witnesses, scripts and every rule that crosses fields stay in Yano.
- Dijkstra's decoder version (12). `HaskellPolicy.forEra` stays Conway-only; Dijkstra's rules arrive with ADR 0003
  M7b, now in CCL.
- Changing CCL's models. Their lenient decoding stays (ADR 0003 D7); this ADR only constrains it from one side (J10).
- Yaci. It keeps its tolerant reading for sync; a later decision may use the policy.
- Checking mutated transactions against the Haskell ledger directly (bloxbean/yano#242).
- Changing a rejection, apart from the corrections J1 lists.

## 3. Threat model and trust assumptions

- **Untrusted input**, as ADR 0003 section 3: blocks and transactions from peers and APIs, built to exhaust stack or
  memory.
- **Strict is not validity.** A transaction that passes the policy decodes as Haskell decodes it at the era's decoder
  version. It can still fail every ledger rule. The Javadoc says so, and so does the release note.
- **A crash is not a rejection.** An input that makes the code throw by accident must not count as correctly rejected,
  in the gate or in admission accounting (D10).
- **Correlated failure.** ADR 0003 accepted that one reader bug reaches CCL, Yaci and Yano together. The policy now
  joins it: a policy bug passes CCL's gate and Yano's report alike. The independent check stays the dataset, which is
  generated from Haskell, and the exact per-sample outcomes file (J4). A direct Haskell oracle is bloxbean/yano#242.
- **Default path unchanged.** CCL users who do not pass the policy get exactly today's behaviour (J2).

## 4. Pinned references

| Reference | Version |
|---|---|
| CCL | `v0.8.0-pre8` (`561e1a2d`), which contains #748 (`6aa36c63`) and #753 (`f80c7547`). r1 to r4 measured at `feat/ledger-cbor` `d14956d1` |
| Yano | Yano #244, `feat/ledger-cbor-reader` `de892d95` (the r5 baseline). r1 to r4 measured at `0b4eb61cd` |
| Yaci | Yaci #244, `feat/ledger-cbor-reader` `32726dc5`, built from source for the r5 baseline |
| cardano-cbor-dataset | `4fd977b201f7426a2983161a702bb127c87973f7`, CCL CI's `CBOR_DATASET_REVISION` and Yano's `corpora.env` |
| cardano-ledger | `f649f9751074d2ab3de033fc3912f29c9862c1f5`, the decoders `HaskellPolicy`'s Javadoc cites; ADR 0003 section 4 |
| cardano-ledger, headers and languages | `6176e413`, the dataset's: headers of either length (`HaskellPolicy.header`), the cost-model language bound. The header question stays open (Q4) |

## 5. Decisions

### D1. The Haskell decoding policy becomes CCL's, opt-in

- `HaskellPolicy` moves to `ledger-cbor`, public, in package `com.bloxbean.cardano.client.transaction.raw`. Its name and
  its `forEra(Era)` stay: Conway decodes at version 9, and any other era is refused until it has rules.
- `FormatPolicy.ACCEPT_ALL`, `lenient` and `tolerant` stay, and stay the defaults. No view, model or Yaci path calls the
  policy unless its caller passes it.
- ADR 0002 D9's remaining parts ("the strict reader stays in Yano", "the `PlutusData` validator stays") are superseded.
  `julc-core` stays CCL-free, as ADR 0002 D2 says: JuLC is not a consumer here.
- The decoder version stays inside the policy, as ADR 0003 D6 put it. No view gains a version parameter, which keeps
  ADR 0001's "no options" for the models.

**Amendments.** The decision text below is amended; the implementation records in ADR 0003 sections 7.1 to 7.3 stay as
history.

| Where | Was | Becomes |
|---|---|---|
| ADR 0002 D2, row L2 | Haskell-exact rejection in Yano `ledger-rules`, "Stays (D9)" | In CCL `ledger-cbor`, opt-in (this ADR) |
| ADR 0002 D9 | The strict reader and the Plutus data validator stay in Yano | Superseded, apart from JuLC (above) |
| ADR 0003 D1, Policy row | Yano for validation | CCL `ledger-cbor` for Haskell's decoders (opt-in); Yano for ledger rules; Yaci for fail-or-continue |
| ADR 0003 D2 | Depends on `common` and `common-spec` only | Also `address` (D3) |
| ADR 0003 D4a, "What is not the reader's" | Yano adds the version-12 policy when it adds Dijkstra (M7b) | CCL's `HaskellPolicy` gains it, at M7b. The rules stay a policy's, not the reader's |
| ADR 0003 D6 | CCL ships only `ACCEPT_ALL`; Yano's `HaskellPolicy(decoderVersion)` holds `StrictCbor`, `CborCanonical`, `BoundedFields`; Yano's iterative validators read recursive types | CCL ships `ACCEPT_ALL` as the default and `HaskellPolicy` as an opt-in, with the validators |
| ADR 0003 D9 | Yano enforces `HaskellPolicy` | Yano enforces CCL's `HaskellPolicy` |
| ADR 0003 I1 | `ledger-cbor` depends only on `common` and `common-spec` | J6 |
| ADR 0003 I7 | A monotonic baseline: misses may shrink and strict rejections may grow | Strengthened to an exact outcomes file (J3, J4); an improvement now changes the file, after review |
| ADR 0003 I13 | Yano's checks through the walk, by M1's keyed-record proof | Met at M5 (section 7.1); the proof's test is deleted with D7, and J1 now holds the walk to the same outcomes |
| ADR 0003 M7b | Yano gains its version-12 policy | CCL's `HaskellPolicy` gains it |
| ADR 0003 section 9, "strict or version parameter" row | "`FormatPolicy` keeps that in Yano" | The policy is in CCL, as a separate object; the rejection of a parameter on the views stands |

### D2. What moves, and what stays in Yano

| Yano class | Decision |
|---|---|
| `HaskellPolicy`, `StrictCbor`, `CborCanonical`, `BoundedFields` | Move whole. `StrictCbor` and `CborCanonical` stay package-private (D4) |
| `PlutusData`, `Metadata` | Move, renamed `PlutusDataValidator` and `MetadataValidator`, so they do not share a simple name with `plutus.spec.PlutusData` and `metadata.Metadata` in modules that see both. `validateFirst` stays public, with its trailing-bytes meaning |
| `CborCursor` | Does not move. Its head parsing is replaced by `CborHead.at` in `common`, now public with the head's parts (r6), so the RFC 8949 head rules have one home. Its frame stack stays, as the package-private `HeadCursor` in `ledger-cbor` on top of `CborHead.at`: `CborSpan` cannot serve the validators, as enumerating a span's children walks each child again, which is quadratic in the depth |
| `AddressBytes` | `validate` moves (D3). The rule helpers (`isVKeyLocked`, `network`, `bootstrapAttrsSize` and the rest) stay in Yano |
| `RawScript` | The decoding checks (`readScript`, the language and definite-length checks) move, with the native-script check (D3). The record the rules use stays, and still builds the model for `nativeScriptHolds` |
| `RawTransaction`, `RawCredential`, `RawVoter`, `RawCertificate`, `RawOutput` | Each site in section 1.3's table either moves into the strict reads (D4), or is shown unreachable once the strict read accepts, in M1's record. The mapping into rule records stays |
| `TxDecodingException` | Stays in Yano as the engine's `DecodingFailure` (D7) |

**Tests are split by what they need.** `ledger-cbor`'s test classpath has `test-support` only, and ADR 0003 I1's test
checks that no model class loads.

| Test | Goes to |
|---|---|
| Byte-level cases of `HaskellPolicyTest`, `DefiniteRecordDecodingTest`, `WitnessDecodingTest`, `CertificateDecodingTest`, `BoundedFieldsTest`, `PlutusDataTest` | `ledger-cbor` |
| Cases that build transactions with CCL's models or Yano's fixtures (`DefiniteLengthDecodingTest` uses `ConwayTxBuilder`, `MutationWorld`, `TxSpec`) | Stay in Yano, or go to the conformance module where they need only CCL |
| `DeepNativeScriptTest`, which drives the whole engine | Stays in Yano. In CCL, `RecordWalkDepthTest` today walks with `ACCEPT_ALL` and a counting policy, and a walk treats a native script as one leaf, so its 200,000-level `DEEP_NATIVE_SCRIPT` does not reach a checker. It gains runs through `HaskellPolicy.CONWAY`, which do (J7) |
| Anything that tests Yano's mapping, and `RawTransactionTest`, `RawProposalTest`, `LedgerValueTest` | Stay in Yano |

**`StrictLedgerPolicy` stays.** It serves the Dijkstra reader tests, the Dijkstra vectors and the Dijkstra corpus walk,
which `HaskellPolicy` cannot replace until it has Dijkstra rules (ADR 0003 M7b).

### D3. What the move needs in `ledger-cbor`

- **Byron addresses.** `AddressBytes.validate` decodes a Byron address with CCL's `ByronAddress`, in module `address`.
  `ledger-cbor` gains `api project(':address')`. `address` depends on `common`, `crypto` and `common-spec` and holds no
  model and no ledger rule, so ADR 0003 D1's boundary holds. Users of `transaction-spec` already get `address` through
  it.
- **Native scripts.** Yano checks a native script by building CCL's model, which `ledger-cbor` cannot call (ADR 0003
  D2). So `ledger-cbor` gains an iterative native-script checker, written from Haskell's decoder and era-aware: types
  0 to 5 from Allegra, type 6 from Dijkstra, as `TimelockRaw` and Dijkstra's script decoder read them. It checks; it
  builds nothing.
  - It is the one era-aware statement of native-script structure. CCL's `NativeScriptCodec` stays the lenient model
    decoder (ADR 0003 D7), accepting type 6 in any era as ADR 0004 decided.
  - The two must agree one way: what the checker accepts, the model decodes (J10), over standalone, witness,
    auxiliary data and reference scripts.
  - Correction to J1: a Conway `[6, …]` is rejected (section 1.4).
- **Java 17.** CCL builds for Java 17 (`build.gradle:28-29`). Moved code is ported: `List.getFirst` and guarded
  pattern switches go.

Alternatives: putting the policy in `transaction-spec`, where both are reachable, mixes the policy with the models,
which ADR 0003 D2 made a module to prevent. Copying `ByronAddress`'s decoding into `ledger-cbor` repeats format code
that already has one home. Rewriting `NativeScriptCodec` on the checker would change a lenient model decoder CCL users
rely on, which this ADR does not do.

### D4. Strict entry points

The policy keeps its walk methods, and adds one strict read per kind of input. Illustrative:

```java
// Illustrative. Every entry point takes the bytes, so that building the span is inside the strict read.
HaskellPolicy p = HaskellPolicy.forEra(Era.Conway);
RawTx tx = p.transaction(bytes);    // RawTx.of, the walk, and every check of section 1.3's table that moves
RawBlock b = p.block(bytes);        // a bare block of the policy's era: header, containers, each transaction
RawHeader h = HaskellPolicy.header(bytes);  // either header layout, era-less, as today
p.walk(recordSpec, bytes);          // one record of the tables
p.walk(fieldType, bytes);           // one value: its rule, then its table
p.script(bytes);                    // script = [language, script]
```

- **A deliberate rejection** is `LedgerDecodingException`, a new subclass of `CborRuntimeException`, carrying a stable
  reason code (D10) and a message. A caller that catches the reader's exception today catches it too. The reader's
  own rejections, and `CborSpan.of`'s for bytes that are not well formed or have trailing bytes, stay plain
  `CborRuntimeException`s; inside a strict read they are rejections too, with reason `NOT_WELL_FORMED` or
  `STRUCTURE` (D10).
- **The block read checks the header.** Today's harness reads a block with `RawBlock.of`, which keeps the header as a
  span without looking into it (`RawBlock.java:73`, `:233`), and then each transaction. So bare Conway bytes
  `85 00 80 80 a0 80`, whose header is `0`, pass. Haskell's block decoder decodes the header. `p.block` reads the header
  with `HaskellPolicy.header` and checks it is the era's layout. Any sample this changes is a J1 correction. None is
  expected: at `4fd977b2` no invalid `block` sample differs from a valid block only in its header, so the dataset
  cannot see this gap, as it cannot see section 1.4's. The header check gets its own unit tests: a malformed header and
  a header of another era's layout.
- **The strict transaction read is the first decoding verdict.** Once it accepts, Yano's mapping rejects nothing (J5).
  Yano's admission also builds CCL's model, which can still fail on a gap in the model; J10 lists each such gap.
- **Package-private helpers.** `StrictCbor` and `CborCanonical` stay internal. Yano's five direct `StrictCbor` callers
  (section 1.3: `ShelleyHeaderView`, `ConwayTxInfoTranslator`, `RawParamUpdate`, `RawScript.fromScriptRef` and
  `RawOutput.accepted`) move to
  `CborSpan`, `RawItems` and the strict reads; a read they need that has no public form is added to `RawItems`, the
  reader's existing class for reading one item.
- Strict reads are stack-safe as the walk is (J7).

### D5. One conformance harness, in CCL, published

A new published CCL module holds the harness, `ledger-cbor-conformance` (artifact
`cardano-client-ledger-cbor-conformance`), depending on `transaction-spec` (which brings `plutus`) for the writers.
Yano depends on it in its tests. This follows from section 1.1's goal: only one harness lets the report Yano publishes
be produced by the code CCL gated (Q1). It holds what Yano's `ledger-conformance` holds today for this report:

- the rule table: each dataset rule name to its strict read (D4), CCL's model decoding and its writer, including the
  `block` rule (each transaction read strictly);
- the minimal valid parents that rules checked by their parent are placed in (Yano's `CborPlacedRules`);
- the dataset's normalization, one copy, extracted from CCL's test `CborNormalizer` as of `92327070` (#753). That is
  the first version that reduces a rational as Haskell's `n % d` does, with the sign on the numerator (`-1/-2` is
  `1/2`). Yano's copy at the r1 pin `0b4eb61cd` lacks that fix, so it is not interchangeable with current CCL's; Yano's
  copy at `93e60c3d5` (Yano #244) has the same code as CCL's, Javadoc aside;
- a runner giving each sample a typed outcome per stage (D10);
- the outcomes file for its pinned dataset revision (D6), with a check that the dataset directory it is given is
  exactly that revision (D8).

It has no test framework dependency and reads the dataset from a path it is given, so it is a library, not a test. It
runs on Java 17.

**What stays in Yano:** every Yaci decoder. That is the Yaci column, and also the `library` decoder of seven rules,
which today is Yaci's, since CCL has no header model: `header`, `header_body`, and the placed rules `block_number`,
`kes_period`, `kes_vkey`, `operational_cert` and `vrf_vkey` (`CborPlacedRules:222`, `inHeaderBody` at `:332`). In CCL's
table these seven have no model stage. Yano attaches its Yaci decoders to them as diagnostics, as today.

### D6. CCL's gate covers the whole Conway dataset

- The CBOR conformance job runs three classes, each with its own guard that it ran and skipped nothing:
  - the new strict gate, over every Conway sample at `CBOR_DATASET_REVISION`, valid and invalid;
  - `CborCorpusConformanceTest`, which gates the lenient model path CCL users call;
  - `LedgerReaderConformanceTest`, whole: its certificate accessor checks and its Dijkstra corpus walk are coverage
    the strict gate does not replace. It joins CI here for the first time.
- The outcomes are exact, per sample (J3, J4). This strengthens ADR 0003 I7, which only let misses shrink and strict
  rejections grow.
- **The same job gates every release (J12).** The job moves to a reusable workflow, `cbor-conformance.yml`
  (`on: workflow_call`): it fetches the dataset at `CBOR_DATASET_REVISION`, runs the three classes and their guards. Two
  callers:
  - `build.yml`, on pushes and pull requests, as today;
  - `release.yml`, as a job its `publish` job `needs`. It checks out the tag's commit, the same `GITHUB_SHA` that
    `publish` builds, so the gate and the artifacts come from one commit in one run. `publish` signs and uploads
    nothing unless the gate job succeeded: no `if: always()`, no `continue-on-error`, and a re-run of `publish` alone
    reuses that run's gate result. The gate job has `contents: read` and no environment, so it never sees the release
    secrets of `release-staging`.

  Alternatives: a check in `tag-release.yml` that `build.yml` passed on the commit. It is not enough on its own: a `v*`
  tag can also be pushed by an org admin or the release App outside that workflow, and `release.yml` is what uploads,
  so the gate belongs in the run that uploads. A required status check on master stops a failing merge, but not a tag
  on a commit whose run is still pending. Either may be added later as an earlier warning; neither replaces the job in
  `release.yml`.

### D7. Yano's part

- **The strict read is the first decoding verdict; model gaps are listed.** `JavaLedgerValidationEngine` calls
  `HaskellPolicy.transaction` first; a rejection is `DECODING_FAILURE`. CCL's model is built after it, and can still
  fail on a gap in the model (at r1, the 3 bignum rationals of #749, which #753 fixed; none at the r5 baseline,
  section 1.2). That still rejects the transaction, as
  `DECODING_FAILURE`, so no admission outcome changes. Admission is the strict read and the model together, as today;
  what changes is the order, and that every case where they differ is listed (J10) and shown in the report's model
  stage (D10). Closing the gaps is #749's, not a prerequisite here. ADR-060 section 6 is corrected to say this.
- **Exceptions.** A strict read rejects with any `CborRuntimeException` (D4, D10): `LedgerDecodingException`, the
  reader's own, and `CborSpan.of`'s and `CborHead`'s for truncated or malformed bytes. So at each boundary where Yano
  calls CCL's strict code, it translates that whole family into `TxDecodingException`, and lets any other runtime
  exception through unchanged, as an engine failure. The boundaries: the engine's first call,
  `HaskellPolicy.transaction` (through `RawTransaction.parse`); `ShelleyHeaderView`; `PlutusScriptDecoder` (which keeps
  returning `Malformed`); and `RawScript.fromScriptRef`, which covers its four callers. The other catch sites of
  section 1.3 then need no change. Regression tests: a truncated transaction, a truncated reference script and a
  truncated Plutus data constant each give the same result as today.
  `RawScript.fromScriptRef` and `RawOutput.accepted` move off `StrictCbor` as D4 says.
- Deletes what D2 moves.
- **The report** runs CCL's harness and writes the same report format, version 2, with the Yaci decoders added. The
  release workflow and the files the node-diversity page reads do not change.
- `CborKeyedWalkDiversityReportTest`, ADR 0003 M1's keyed-record proof, is deleted: the walk is now the only strict
  reader, so there is nothing beside it to compare.
- The report fails on any difference from the harness's outcomes file (J3).
- Yano ADR-060 and ADR-056 get notes pointing here.

### D8. Dataset pins

- Yano's report runs at the dataset revision its CCL version's outcomes file was made for. A dataset bump goes
  through CCL first: CCL moves `CBOR_DATASET_REVISION`, its gate passes with the outcomes file updated after review,
  and a CCL pre-release carries it. Then Yano moves `corpora.env`.
- The harness checks that the directory it is given is exactly that revision: a git checkout whose `HEAD` is the pin,
  with no file the revision lacks (untracked or ignored) and every file of the revision hashed equal to its blob (r6;
  `git status` alone trusts index flags and file times). Both CI (`cbor-conformance.yml`) and Yano's `fetch-corpora.sh`
  already fetch the dataset with git. Git compares contents, so an edited input, expected output or normalization file,
  or a substituted sample, fails the run. A directory that is not such a checkout fails too.
- The harness is given the dataset's `dataset` subdirectory, as both callers pass it today, and finds the checkout's
  root with `git rev-parse --show-toplevel`. It runs the `git` executable for these checks, which both CI and Yano's
  build already need to fetch the dataset; it adds no Git library to the published artifact.
- So the outcomes file never meets a revision it was not made for, and a sample's identity is its path at that
  revision.

Q3 records the alternative.

### D9. Release

M1 and M2 ship in one CCL pre-release, then Yano follows (M4). Which pre-release is Q2.

From M2 on, every CCL release, that pre-release first, publishes only after the three corpus gates passed on its tag's
commit in the same `release.yml` run (D6, J12). This holds for tags on commits that include M2: a maintenance tag on a
branch cut before it, such as `release/0.7.x`, runs that branch's `release.yml`, which has no gate (and no
`ledger-cbor`). Snapshots are published by their own workflows and are not gated. A release with a failing gate is fixed
and re-tagged under a new version, never published around the gate. Changes to `.github/workflows/` need a release
owner's review (CODEOWNERS).

### D10. Typed outcomes

Today the report counts any exception as a rejection, including `NullPointerException`
(`CborDecodingDiversityReportTest:174`). On an invalid sample that passes, and never enters the baseline.

Each sample gets one outcome per stage:

| Stage | Outcomes | What counts |
|---|---|---|
| Strict | accepted; rejected with a reason code; error | A `CborRuntimeException` from a strict read, which includes `LedgerDecodingException`, the reader's own rejections and `CborSpan.of`'s, is a rejection: a `LedgerDecodingException` gives its reason code, the others `NOT_WELL_FORMED` or `STRUCTURE`. So is a deliberate restriction of a dataset rule in the harness. Anything else thrown is an error |
| Model | decoded; failed; not applicable | Run only on strict-accepted samples of a rule with a CCL model. A `CborRuntimeException` or `CborDeserializationException` is a failure; anything else is an error |
| Encoding | matches; differs; not applicable | The re-encoding against the expected bytes, normalized as the dataset says |

- A sample passes when it is valid and accepted, decoded or not applicable, and matches or not applicable; or when it
  is invalid and rejected. Every other sample is a miss.
- Any error fails the gate (J11). That includes a `ClassCastException` or `IllegalArgumentException` from a model
  decoder that declares no checked exception, or from the harness's own casts and placement in the model stage. Such
  a failure is a model or harness defect to fix in M2, never a reason to widen what counts as "failed".
- Reason codes are a fixed set, named in M1; `LedgerDecodingException.Reason` is the normative list (r6):
  `NOT_WELL_FORMED`, `STRUCTURE`, `UNEXPECTED_TYPE`, `TAG`, `UNKNOWN_FIELD`, `FIELD_NOT_IN_ERA`, `REPEATED_KEY`,
  `DUPLICATE_ITEM`, `MISSING_FIELD`, `INDEFINITE_RECORD`, `INDEFINITE_STRING`, `BYTES_SIZE`, `INTEGER_RANGE`, `ZERO`,
  `EMPTY_COLLECTION`, `MULTI_ASSET_TOO_BIG`, `MAP_HEAD_WIDTH`, `ADDRESS`, `TEXT_BOUNDS`, `RATIONAL`, `PLUTUS_DATA`,
  `METADATA`, `SCRIPT`, `NESTING_TOO_DEEP`. Messages are free text and are never compared. A strict read rejects
  with a `LedgerDecodingException` only: it classifies the reader's and `CborSpan.of`'s own `CborRuntimeException`s
  as `STRUCTURE` and `NOT_WELL_FORMED` itself (r6). So the runner counts a plain `CborRuntimeException` from a strict
  read as an error, a broken contract, and a deliberate restriction of a dataset rule in the harness (a narrower form
  than the type the strict read takes) as `R:RULE`, apart from the policy's reasons (r6).
- **The outcomes file** has a header with the dataset revision, and one line for **every** sample: rule, path, and the
  three stage outcomes, with the strict reason code. J3 and J4 compare whole files, not only the misses. The misses
  are a view of it.

## 6. Invariants

| ID | Invariant | Constrains | Checked by |
|---|---|---|---|
| J1 | Moving changes no outcome. Over every Conway sample at the pin, Yano's decision after M4 equals its decision before, per sample: accepted or rejected, and the re-encoding. The listed corrections: a Conway native script of type 6 is rejected (section 1.4), already so in the r5 baseline through P1, and also in a script read alone (`p.script`, r6); any `block` sample whose header the strict block read now rejects (D4); and a block in an `[era, block]` envelope or a tag, which `p.block`, reading bare blocks only, now rejects (r6). None changes a sample at `4fd977b2`. Yano's `ledger-rules`, `ledger-conformance`, `tx-services`, `runtime` and `script-evaluators` suites pass, less tests that moved, and admission is checked through the engine itself (`JavaLedgerValidationEngine`) on every transaction sample | D1-D4, D7 | A per-sample comparison of Yano before and after; the suites |
| J2 | CCL's default paths do not change: `CborCorpusConformanceTest`'s list, the golden bytes, and ADR 0003's G3 and G5 give the same results as before | D1 | The existing gates, run before and after |
| J3 | At the same dataset revision and CCL version, CCL's gate and Yano's report produce the same outcomes file: every sample's three stage outcomes and strict reason code | D5, D7, D8, D10 | Yano's report test compares its outcomes file with the harness's, whole |
| J4 | The outcomes file is exact: any change to any sample's line, a new miss, a miss that now passes, another outcome or reason code, fails the gate until the file is updated after review. One rejection lost and another gained, with equal totals, fails | D6, D10 | A gate test |
| J5 | Once `HaskellPolicy.transaction` accepts, Yano's mapping rejects nothing. Checked over every accepted sample of a rule that lives in a transaction, placed in its minimal transaction (the dataset has only 103 valid top-level `transaction` samples); header rules are checked through `HaskellPolicy.header` instead | D4, D7 | Yano's harness run and suites. M1's record names each site of section 1.3's table: moved, or unreachable and why |
| J6 | `ledger-cbor` depends only on `common`, `common-spec` and `address`, and holds no model and no ledger rule (amends ADR 0003 I1) | D3 | ADR 0003 I1's dependency test, updated |
| J7 | Strict reads and the validators are stack-safe at maximum nesting | D2, D4 | ADR 0003 I9's depth fixtures run through `HaskellPolicy.CONWAY`, including `RecordWalkDepthTest`'s `DEEP_NATIVE_SCRIPT`, and the preprod deep script, on the default stack, `-Xint` and a virtual thread |
| J8 | Yano's four node-diversity reports (CBOR, ledger rules, UPLC, turbo) are unchanged at the same pins, comparing outcomes and counts, not messages, and apart from the version label and J1's corrections | D7 | Before and after, as the Amaru removal was checked (Yano #241) |
| J9 | Every `FieldType` has a rule in `HaskellPolicy` or is listed as having none, so a new type cannot arrive without a decision | D2 | A test over `FieldType.values()` |
| J10 | What the strict checks accept, CCL's model decodes, except samples whose model stage is "failed" in the outcomes file (none at the r5 baseline of section 1.2; at r1, the 3 bignum rationals of #749, fixed by #753): for every strict-accepted sample of a rule with a CCL model, and for the native-script checker against `NativeScriptCodec` over standalone, witness, auxiliary data and reference scripts and mutated scripts | D3, D7 | CCL's gate; a differential test in `transaction-spec` |
| J11 | No error outcome, in any stage, on any sample | D10 | CCL's gate and Yano's report |
| J12 | No CCL release artifact is signed or uploaded unless the three corpus gates (D6) passed on the exact commit the release tag names, in the same `release.yml` run; for every tag whose commit includes M2 (a tag on an older branch runs that branch's `release.yml`; snapshots are not releases and are not gated) | D6, D9 | Review of `release.yml` in M2 (`publish` needs the gate job; no `always()` or `continue-on-error` on the path); M3's release run |

## 7. Milestones

| ID | Project | What | Entry | Exit |
|---|---|---|---|---|
| P1 | Yano | Prerequisite: Yano's ledger-cbor branch rejects a Conway native script of type 6, with a test | None | The test, before that branch merges. Done in `8811854a6` (Yano #244) |
| M1 | CCL | D1 to D4, D10: the policy, validators, native-script checker, strict reads, the exception and reason codes, the `address` dependency, `CborHead`'s accessors, with the tests that move | ADR accepted; #748 merged | J1 (Yano's current harness run against the CCL snapshot), J2, J5, J6, J7, J9, J10; M1's record of section 1.3's sites |
| M2 | CCL | D5, D6, D8: the harness, the outcomes file, the full gate in CI, and the gate in `release.yml` (`cbor-conformance.yml`) | M1 | J4, J11; J12's workflow structure; the gate's per-sample outcomes equal Yano's report at the r5 baseline (section 1.2: 43,369 of 43,434 at `4fd977b2`, no model gap), changed only by J1's corrections; the harness's normalizer is CCL's from `92327070` on (D5) |
| M3 | CCL | Release (D9) | M2; ADR 0003's G3 and G5 | Released by a `release.yml` run whose corpus gate job succeeded on the tag's commit before `publish` started (J12); the run's URL in the release record |
| M4 | Yano | D7 | M3 | J1, J3, J5, J8, J11; the moved classes and the keyed-walk test deleted; ADR-060 corrected |

### 7.1 Implementation record (r6)

**M1** (CCL PR #750: `48c02cf4`, `13201790`, then review fixes). `CborHead.at` and its accessors in `common`;
in `ledger-cbor`: `HaskellPolicy` (public, with the strict reads `transaction`, `block`, `header`, `walk` of a record
or a value, `script`), `LedgerDecodingException` and its `Reason`, `PlutusDataValidator` and `MetadataValidator`
(public), and package-private `StrictCbor`, `CborCanonical`, `BoundedFields`, `AddressValidator`, `NativeScriptChecker`,
`HeadCursor`; `ledger-cbor` depends on `address`. Decisions taken while implementing:

- `p.block` reads a bare block only: an `[era, block]` envelope or a tag is `UNEXPECTED_TYPE`. `p.script` refuses a
  Conway guard, which the old harness's `script` rule did not (it read without the guard). Both are J1 corrections, as
  the header check is; no dataset sample changes (below).
- `p.transaction` walks before it builds CCL's view, so a rejection carries the policy's reason; accept and reject are
  unchanged.
- `HaskellPolicy` refuses a walk that names another era than Conway (`IllegalArgumentException`): its rules are
  Conway's, and Dijkstra's arrive with ADR 0003 M7b.
- Every `FieldType` has a rule or is listed in `HaskellPolicy.NO_RULE` (38 types: records walked by their tables,
  types of later eras, values Conway's walk reads unchecked); an unlisted type is an `IllegalStateException`, which
  the gate counts as an error (J9).
- Three denial-of-service paths inherited from Yano were found in review and fixed, each with the same verdicts:
  set-item equality re-read a rational that is not a pair of integers, doubling the work per nested tag 30 (a
  161-byte transaction took 5 s); the Byron address check built the address's Base58 text, quadratic in its length
  (`address` gains `ByronAddress.validate(byte[])`); a rational's reduction ran an unbounded gcd (now continued-fraction
  convergents, stopping beyond `Word64`). The set-item fix counts a tag-30 level once in the equality key's depth
  budget, where the re-read counted it twice: an already invalid item nested 32 to 63 deep through non-pair rationals
  now gets the walk's reason instead of `NESTING_TOO_DEEP`; no verdict changes.

Section 1.3's sites (J5), each now moved into the strict read or unreachable once it accepts:

| Site | Now |
|---|---|
| `RawTransaction:438`, empty input | `NOT_WELL_FORMED` in the strict read |
| `RawTransaction:155`, translation of the views' `CborRuntimeException` | Yano translates at the boundary (D7); after acceptance the views read only fields the walk checked |
| `RawScript.accepted`, the native-script model | `NativeScriptChecker` in the walk, with the same verdict as Yano's model-based check on 40,000 generated and mutated scripts alone, and one in eight of them placed in each of the four places |
| `RawOutput.accepted`, a reference script | The walk's `SCRIPT_REF` rule (stricter: a definite byte string in tag 24) |
| `RawCredential:25`, `RawVoter:27`, a hash that is not 28 bytes | The walk's `HASH28` rule |
| `RawVoter:23`, an unknown voter tag | `UNKNOWN_FIELD` in the voter union |
| `RawCertificate:69,102`, `IllegalArgumentException` | Unreachable by construction: a DRep of kind 0 or 1 has a credential and 2 or 3 none; a delegatee is built only with a pool or a DRep. Section 13's question is closed |

J1, measured: the M2 gate's per-sample outcomes against Yano `de892d95`'s (section 1.2): all 43,434 strict outcomes
and every model outcome of a strict-accepted sample are the same. Yano's strict reads against CCL's harness on every
Conway sample and four seeded byte-level mutations of each, 217,170 inputs: no difference (Yano `e276a3886`, on CCL
#750 and Yaci #244). J7: the depth fixtures run through the strict reads in `ledger-cbor` (platform and virtual
threads; the JDK 21 CI job now runs `ledger-cbor`) and in `transaction-spec`'s `RawViewDepthFixtures` (platform,
`-Xint`, virtual thread), where the preprod trigger transaction (a 5,383-level native script) and its block are
accepted.

**M2** (CCL PR #750: `4e9e29a7`). The module `ledger-cbor-conformance` (`CborRule`, the placed rules, `CborNormalizer`,
moved from `transaction-spec`'s tests, `CborConformance`, `SampleOutcome`, `DatasetCheckout`) and the outcomes file
`outcomes-4fd977b2.txt`, one line per sample: 43,369 of 43,434 pass, all 31,842 invalid samples rejected, no model
gap, no error. The seven header rules have no model stage, and `CborRule.headerFor` gives a caller's own header
decoders their header (Yano's Yaci column). CI runs `cbor-conformance.yml` from `build.yml` and from `release.yml`,
where `publish` needs it; its guards are `tests="4"` (`ConwayOutcomesGateTest`), `tests="4"`
(`CborCorpusConformanceTest`) and `tests="6"` (`LedgerReaderConformanceTest`). `CertificateOutcomesTest` stays as
ADR 0003 G7 left it, outside CI. Reviews of M2 changed the outcomes file twice, each time after review: a script
reference's embedded-script defects are `SCRIPT` (4 lines), and the harness's own restrictions `R:RULE` (399 lines),
with no sample changing between accepted and rejected.

**M4** (Yano #253: `e276a3886`, `2a0616d40`, `b6bc5afc9`, built on CCL #750 and Yaci #244 until their releases).
Admission runs `HaskellPolicy.transaction` first, then CCL's model, then `RawTransaction.of`, the mapping only; the
`CborRuntimeException` family is a `TxDecodingException` at the engine, `RawScript.fromScriptRef` (five callers),
`ShelleyHeaderView` and `PlutusScriptDecoder`. Yano's copies of the policy, its validators and its harness are deleted.
Yano keeps an era-neutral reading of a script (`RawScript.readScript`, `fromScriptRef`, on `CborSpan` and `CborHead`),
as sync hashes reference scripts of any era, a Dijkstra guard included, which `p.script` refuses in Conway; and
`ConwayTxInfoTranslator` keeps private untagged reads of an accepted parameter update. D2's `RawScript` row and D4's
callers bullet are met that way, not through `RawItems`. Measured:

- J1: before the deletion, Yano's strict reads and CCL's agreed on 217,170 inputs (every Conway sample and four
  mutations of each).
- J3: the cbor-decoding report equals CCL's pinned outcomes file. J5: admission's decoding (strict read, model,
  mapping) over every strict-accepted sample that lives in a transaction and every transaction of an accepted block,
  134,368 transactions, no model gap and no rejection after the strict read (`CborMappingDiversityReportTest`, with
  `CborRule.transactionFor`).
- J8: the four node-diversity reports at the same pins, before (`de892d95`, CCL 0.8.0-pre8) and after: cbor-decoding
  43,369 of 43,434 sample by sample, ledger-rules 368, uplc-conformance 1,064 and uplc-turbo 3,997 entries, no
  difference.
- J11: no error stage. Yano's `ledger-rules`, `ledger-conformance`, `tx-services` and `script-evaluators` suites
  pass; `runtime`'s three failures are the same on unmodified `de892d95` with CCL 0.8.0-pre8 and Yaci #244.

## 8. Verification

- **Independent evidence.** The dataset is generated from cardano-ledger, so its labels are Haskell's, not this code's.
  The 64 auxiliary data samples stay the documented exception, as ADR 0003 section 7.1 records. The native-script
  correction is checked against Haskell's decoder at the pin.
- **Differential.** J1 compares old and new per sample, so a move that loses one rejection and gains another fails.
  J10 compares the strict checks with CCL's model.
- **Negative cases.** The moved tests are mostly rejections, each citing the Haskell decoder it follows.
- **Not evidence.** A passing gate does not prove ledger validity (section 3).

## 9. Compatibility

| Item | Impact |
|---|---|
| CCL models, `lenient`, `tolerant`, `ACCEPT_ALL` | None (J2) |
| CCL public API | New: `HaskellPolicy`, `LedgerDecodingException`, `PlutusDataValidator`, `MetadataValidator`, the strict reads, `CborHead`'s accessors and the harness module. Pre-release API, like the rest of `ledger-cbor` |
| `ledger-cbor`'s dependencies | Gains `address`. No change for `transaction-spec` users, who have it already |
| Native image | The policy and validators use no reflection |
| Yano admission | Same outcomes (J1), apart from the native-script correction; the strict read runs before the model, and a model gap is still `DECODING_FAILURE` |
| Yano code | About 1,500 lines of main code leave Yano, and the byte-level tests with them |
| Yano's report files | Same format and content (J8) |
| Release order | CCL, then Yano, as ADR 0003 |
| CCL release workflow | `release.yml` fetches the dataset and runs the corpus gate before `publish`; a release cannot publish while the dataset host is unreachable or the gate fails (J12) |

## 10. Alternatives considered

| Alternative | Why not |
|---|---|
| Keep the policy in Yano (ADR 0003 D6 as written) | CCL cannot see invalid samples, so a reader change's effect on rejection is found after a release |
| Copy Yano's policy into CCL's tests only | Two copies of the decoding rules, kept equal by hand. The report Yano publishes would not be what CCL gated |
| A strict mode or version parameter on the views | Rejected by ADR 0003 section 9 for the default path, and still rejected: the policy stays a separate object |
| Policy in `transaction-spec` | Mixes rejection with the models; ADR 0003 D2 made `ledger-cbor` a module to stop that |
| Keep the model decode first in Yano's admission | Admission would reject first on the lenient model's terms, so a transaction failing both decoders would be reported with the model's reason, not Haskell's. Strict first gives every rejection the strict read can make its Haskell reason code, and leaves the model only J10's listed gaps |
| Move model restrictions into the policy, so the two decoders agree | The policy would reject what Haskell accepts. J10 lists the gaps instead, and #749 fixes them in the model |
| Run Yano's report in CCL's CI | CCL would depend on a Yano build, and the dependency runs the wrong way |

## 11. Risks

| Risk | Mitigation |
|---|---|
| A rejection is lost in the move | J1, per sample, and Yano's suites |
| An accidental exception passes as a rejection | D10, J11 |
| CCL users take strict as validity | Javadoc and release note (section 3) |
| The native-script checker and CCL's model disagree | J10, over every place a native script occurs |
| A policy bug reaches CCL and Yano together | Accepted, as ADR 0003 accepted it for the reader; the dataset and the exact outcomes file stay the independent check |
| A release publishes without the gate (version bump merged while CI is pending or red; a tag pushed outside `tag-release.yml`) | The gate is a job `publish` needs in `release.yml` itself (D6, J12) |
| A release is blocked because GitHub or the dataset repository is unreachable | Accepted: re-run the release workflow. The pin is a commit, so the fetch is reproducible; no path publishes around the gate |
| CCL releases more often, for policy and dataset changes | Accepted by the maintainer on 2026-11-09: a reader or policy change needs a CCL pre-release |

## 12. Open questions

**Q1. Where the harness lives. Decided: (a)** (proposed in r3, confirmed by the maintainer on 2026-11-16). Options were
(a) a published CCL module (D5), which Yano depends on in its tests; (b) Yano keeps its harness and CCL gets its own,
compared through their lists; (c) the harness stays in CCL's tests and Yano compares with a list CCL publishes. (b) and
(c) keep two rule tables, which section 1.1's goal rules out, so D5 takes (a). The cost is one more artifact in CCL's
published set. The maintainer confirms it on acceptance.

**Q2. Which pre-release. Settled by events in r5: (b).** Options were (a) 0.8.0-pre8, waiting for M1 and M2; (b) pre8
ships ADR 0003 as planned, and this ships in a later one. 0.8.0-pre8 was tagged on 2026-11-15 (`v0.8.0-pre8`,
`561e1a2d`) with #748 and #753 and without this ADR, so M3 is 0.8.0-pre9 or later. P1 did not wait for either.

**Q3. Dataset revisions.** Options: (a) D8: Yano runs only the revision its CCL version's outcomes file was made for, so
every dataset bump is a CCL pre-release first; (b) Yano may run a newer revision, with sample identity defined by input,
expectation and outcome, and rules for changed, removed and repaired samples. Decided: (a), confirmed by the maintainer
on 2026-11-16. It is simple and is the rule this ADR exists for. Its cost is a CCL pre-release per dataset bump; (b)
avoids that at the price of a second set of comparison rules.

**Q4. Indefinite-length headers** (from ADR 0003 section 7.1, still open). `HaskellPolicy.header` accepts either
length, as cardano-ledger `6176e413` reads them, while deployed nodes read headers definite-only. The move keeps that
behaviour; the question moves to CCL with the policy.

## 13. Related findings (out of scope)

- Yano's report runs at protocol 11.0 and covers Conway only. Dijkstra's dataset samples are gated in CCL by the model
  and walk tests, not by a policy, until ADR 0003 M7b.
- `RawTransaction`'s mapping is Conway-only, as `HaskellPolicy.forEra` is.
- `RawCertificate`'s two checks throw `IllegalArgumentException`, which the engine reports as `ENGINE_FAILURE`. M1
  decides whether they are reachable after the policy (J5): they are not (section 7.1).
- `p.block` checks the header and each transaction, but leaves the block's containers to `RawBlock`, which is lenient
  where Haskell's block decoder may not be: Fable's review of M1 found an auxiliary data map entry of `null`
  (`85 <header> 81 a3008001800200 81 a0 a1 00 f6 80`) accepted by Yano and CCL alike. Haskell's decoder of the map's
  values is expected to refuse it; not checked against the source. Parity holds (J1), so it is a follow-up, as the
  header gap was before D4.

## 14. Revision history

- **r1** (2026-11-09): initial proposal.
- **r2** (2026-11-09, responds to the Fable and Codex reviews of `b3395268`):
  - Admission (Fable F1, Codex F1): section 1.3 records the two-step decoding; D7 makes the strict read the first and
    only decoding verdict; new J10 for strict-accepts-implies-model-decodes; J5 covers component samples; ADR-060 is
    corrected in M4; new alternatives rows.
  - Native scripts (Fable F2, Codex F2): section 1.4 records the type-6 gap; new P1; D3's checker is era-aware, with
    J10 and J1's correction.
  - `StrictLedgerPolicy` kept (Fable F3, Codex F4); J9 kept on its own reason.
  - Tests split by classpath (Fable F4); D2's test table.
  - Other users and exception boundaries (Fable F5, Codex F5): section 1.3's tables; D4's `StrictCbor` decision; D7's
    wrapping; `runtime` and `script-evaluators` suites in J1.
  - Pins (Fable F6, F13, Codex F6): `6176e413` pinned; new Q4; D8 requires the same verified revision, and Q3 is
    restated.
  - CI (Fable F7, Codex F8): section 1.2's CI column; D6 runs three classes with guards, and keeps
    `LedgerReaderConformanceTest` whole.
  - `CborCursor` (Fable F8): does not move; `CborHead` gains ADR 0002 D9's accessors.
  - Typed outcomes and exact lists (Fable F9, Codex F3): new D10, J11; J3, J4 and J8 compare outcomes and reason
    codes, not messages.
  - Names (Fable F10): `PlutusDataValidator`, `MetadataValidator`.
  - Harness (Fable F11, Codex F7): the `block` rule and read; Yaci's header decoders stay in Yano; Java 17.
  - Amendments (Fable F12, Codex F9): D1's amendment table; `header` is static; J8 cites Yano #241.
  - Rejection inventory (Codex F10): section 1.3's table of sites in place of a count.
- **r3** (2026-11-09, responds to the Fable and Codex reviews of `08a9e526`):
  - Admission wording (Codex F1, Fable F18): the strict read is the first decoding verdict, not the only one; admission
    stays strict read and model together; model gaps are listed (J10) and shown in the report's model stage. D4, D7,
    J1 (through the engine), J10.
  - Typed outcomes (Fable F16, Codex F3): three stages (strict, model, encoding); every `CborRuntimeException` from a
    strict read, including `CborSpan.of`'s, is a rejection; entry points take bytes; model failures are an outcome,
    not an error; a whole-sample outcomes file, compared whole (J3, J4). Section 1.2 splits the 4 rationals into 3
    model failures and 1 re-encoding.
  - Block header (Codex F11): `p.block` reads and checks the header; J1 lists any block sample this changes. Bare
    blocks, era from the policy (Fable F20).
  - Dataset identity (Codex F6, Fable F19): a clean git checkout at the pin, checked with `git status`, in place of
    revision and counts.
  - Yaci-backed rules (Codex F7): seven rules listed; J5 and J10 scoped to rules with a transaction or a model; Q1
    taken as (a) in D5, for the maintainer to confirm.
  - Amendments (Codex F9): ADR 0003 D4a and I7 rows; J4 strengthens I7.
  - Depth fixtures (Fable F14): `RecordWalkDepthTest` gains runs through `HaskellPolicy.CONWAY`.
  - Callers (Fable F15): `RawScript.fromScriptRef` and `RawOutput.accepted` use `StrictCbor`; `UtxowSubject` and
    `AmaruTransactionValidator` catch sites; `fromScriptRef` is the wrap point.
  - Section 1.4 says the dataset cannot see the type-6 gap (Fable F17).
- **r4** (2026-11-09, responds to Fable's approval of `7b233b78` with F21-F24 and Codex's review of it):
  - Exception boundaries (Codex F12): Yano translates the whole `CborRuntimeException` family a strict read rejects
    with, at each boundary including the engine's first call; other runtime exceptions stay engine failures;
    regression tests for truncated inputs. D7.
  - Alternatives row on strict-first admission gives the real reason (Codex F1).
  - The dataset cannot see the block-header gap; unit tests named (Fable F21). D4.
  - Five `StrictCbor` callers (Fable F22). D4.
  - The harness finds the checkout root and uses the `git` executable, no Git library (Fable F23). D8.
  - Model-stage exceptions without a checked type are errors and defects to fix (Fable F24). D10.
  - The five `StrictCbor` callers named, not counted by package (Codex F13, after its approval of `d943dc37`).
- **r5** (2026-11-16, responds to Reviewer1's review of `ef5fde1f`):
  - Release gate (Reviewer1 P1; also the first item of the initial automated review of `b3395268`): section 1.2
    records that `release.yml` builds without the dataset and that nothing waits for `build.yml`'s corpus job. D6
    moves the job to a reusable workflow that `release.yml`'s `publish` job needs, on the tag's commit; D9 makes it
    apply to every release from M2 on; new J12; M2 adds the workflow and M3's exit requires the gate on the release
    commit; compatibility and risk rows.
  - Migration baseline (Reviewer1 P2): r1's measurement kept as history; a fresh baseline measured at Yano `de892d95`
    on CCL 0.8.0-pre8 (with #748 and #753): 43,369 of 43,434, no model gap among strict-accepted samples. Pins updated
    (section 4); D7 and J10 list no model gap; M2's exit uses the new baseline; D5 takes the normalizer from CCL
    `92327070` on, not from Yano's r1 copy.
  - Facts that changed since r4: P1 is done (Yano `8811854a6`), so J1's type-6 correction is already in the baseline;
    0.8.0-pre8 shipped without this ADR, which settles Q2 as (b).
- **Accepted** (2026-11-16): Reviewer1 had no open findings on r5. The maintainer confirmed Q1 (a) and Q3 (a). Text
  otherwise unchanged.
- **r6** (2026-11-16, implementation of M1 and M2 in this PR, and their reviews: Codex C1-C2, a multi-agent review
  W1-W18, Fable F1-F8, all of `13201790`):
  - Section 7.1, the implementation record: what landed, decisions taken while implementing, section 1.3's sites
    (J5), J1 and J7 measured (Codex C2, W1, W2, W8, W9; Fable F4, F5).
  - Three denial-of-service paths inherited from Yano, fixed with the same verdicts: nested rationals in set-item
    equality (Codex C1), the Byron address's Base58 encoding (W17, Fable F1), an unbounded gcd (W18, Fable F2).
  - J1's corrections also list a bare-only block and the guard in a script read alone (Fable F3).
  - D2's `CborCursor` row: `HeadCursor` on `CborHead.at` (W5). D10: the normative reason codes, and a strict read
    rejecting with `LedgerDecodingException` only (W6); `TRAILING_BYTES` removed (W15, Fable F6b); a script
    reference's embedded script that is not one item is `SCRIPT` (Fable F6a): four `babbage_transaction_output`
    samples change from `R:NOT_WELL_FORMED` to `R:SCRIPT` in the outcomes file, reviewed and pinned (J4); no sample
    changes from accepted to rejected or back.
  - Section 1.3: the fifth `fromScriptRef` caller. Section 13: the `RawCertificate` question closed; the block
    containers' leniency recorded (Fable F8).
  - Codex's review of `b05e7cfc` (D1-D3): the rational reduction bounds each step's quotient before dividing (a
    numerator twice the denominator's length took a second for 3 MB); the dataset check (D8) hashes every file of the
    revision against its blob and refuses any file the revision lacks, ignored ones included, so index flags and
    size-and-time-preserving edits cannot hide a change from it, as they can from `git status`; git's output goes to
    a file, so the check's deadline holds.
  - Fable's review of `b05e7cfc` and `2a0616d40` (G1-G8): the outcomes file was stale at `b05e7cfc` (G1, fixed in
    `ef8030f7`); `CborRule.transactionFor` and Yano's mapping test over the dataset for J5 (G2); `R:RULE` for the
    harness's restrictions and an error for a plain `CborRuntimeException` (G3, D10); the dataset check runs git with
    `--no-replace-objects` and without `GIT_*` variables (G4); both release jobs check out `github.sha`, and J12 and
    D9 say which tags and artifacts they cover (G5); the M4 record (G7); Yano's `RawTransaction` keeps its own copy
    (G8).
  - The multi-agent review of `b05e7cfc` and `2a0616d40` (X1-X15; X2, X4, X5, X9, X11 and X13 were already fixed):
    the gate runs on JDK 17, the release's (X1); a normalization check needs vectors (X3); the header placements are
    one immutable map (X6); any `Error` is that sample's error (X7); a crash a model decoder wraps is an error, not a
    gap (X12); section 7.1's depth note and differential count (X14, X15). Yano: a bignum part of a rational in a
    parameter update reaches the script context as the policy accepts it (X8); a decoding failure keeps the strict
    reason (X10).
