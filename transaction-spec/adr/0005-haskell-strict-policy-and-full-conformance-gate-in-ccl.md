# ADR 0005: The Haskell-Strict Decoding Policy and the Full Conformance Gate in CCL

**Status**: Proposed (r4)
**Date**: 2026-11-09
**Risk**: High. The policy decides which transactions and block headers Yano accepts. A mistake made while moving it
changes what a node admits.
**Related**: ADR 0002 (D2, D9, D10); ADR 0003 (D1, D2, D6, D9, I1, I7, I9, I13, M7b, sections 7.1 and 9); Yano ADR-060
(node-diversity reports); PR #748
**Modules**: CCL `common`, `ledger-cbor`, `transaction-spec`, a new conformance module; Yano `ledger-rules`,
`ledger-conformance`, `runtime`, `script-evaluators`

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

| Check | Where | Samples | Decides acceptance with | In CI |
|---|---|---|---|---|
| `CborCorpusConformanceTest` | CCL `transaction-spec` | Valid Conway samples of the rules CCL has a model for, and Dijkstra's | CCL's models (lenient). Invalid samples are out of scope by its Javadoc, "as rejecting them is the job of a strict reader" (ADR 0002 D10) | Yes: the only class the CBOR job selects, guarded on `tests="4"` (`build.yml:108-115`) |
| `LedgerReaderConformanceTest` | CCL `transaction-spec` | Valid Conway and Dijkstra samples | A walk with a structural policy, and every certificate accessor; Dijkstra with `StrictLedgerPolicy` | No: it needs `cbor.dataset`, and the job selects only the class above |
| `StrictLedgerPolicy` | CCL `ledger-cbor` test fixtures | Reader unit tests, Dijkstra vectors and the Dijkstra corpus walk | Shapes, hash sizes and integer widths, not other lengths and ranges. Not published | Through the unit tests |
| `CborDecodingDiversityReportTest` | Yano `ledger-conformance` | All 43,434 Conway samples, valid and invalid | CCL's reader + Yano's `HaskellPolicy` (`RawTransaction.parse` for transactions), then CCL's writers for re-encoding. Result: 43,365 of 43,434 | Yano's report workflow |

So CCL proves only that valid samples are accepted. Whether invalid samples are rejected, 31,842 of the 43,434 Conway
samples, is first measured in Yano, after a CCL release.

The 69 samples Yano misses at `4fd977b2` (Yano's `decoding-baseline.txt`):

- 64 indefinite-length Allegra auxiliary data arrays (14 `auxiliary_data`, 50 `auxiliary_data_array`) the dataset calls
  valid. Yano rejects them as deployed nodes do: `decodeAllegra` is a `RecD` record below decoder version 12 (ADR 0003
  section 7.1).
- 4 rationals with bignum or negative parts (2 `unit_interval`, 2 `nonnegative_interval`), all accepted by the strict
  side: 3 with bignum parts fail CCL's model decoder, and the negative one decodes but re-encodes unreduced
  (bloxbean/cardano-client-lib#749).
- 1 Byron address re-encoding in the harness.

The strict side accepts no invalid sample.

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
| `RawScript.fromScriptRef` | `MinFee:108`, `UtxowSubject:135`, `DefaultUtxoStore:1917`, `TransactionOutputProjector:98` |
| `TxDecodingException` caught | `JavaLedgerValidationEngine:127`, `ScalusLedgerValidationEngine`, `TransactionOutputProjector:55`, `UtxowSubject:135`, `ShelleyHeaderView:205`, `PlutusScriptDecoder:537`; `AmaruTransactionValidator:359` until Yano #241 removes it |

### 1.4 A gap found while writing this ADR

On the ledger-cbor branches, Yano's native-script check is CCL's model decoder (`RawScript.accepted`). ADR 0004 taught
that decoder type 6, `RequireGuard`, in every era (`NativeScriptCodec.type`, `:126-135`, accepts 0 to 6). Haskell's
Conway decoder accepts 0 to 5 (`TimelockRaw`, `Allegra/Scripts.hs:235-245` at `f649f975`). So Yano on those branches
admits a Conway native script `[6, credential]` that Haskell rejects. Yano `main`, on CCL 0.8.0-pre7, rejects it. This
was found by reading the code and has not been reproduced. It is a prerequisite fix in Yano's ledger-cbor branch,
before that branch merges (P1, section 7).

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
| CCL | `feat/ledger-cbor` `d14956d1` (PR #748) |
| Yano | `feat/ledger-cbor-reader` `0b4eb61cd` (local; pushed with ADR 0003's Yano PR) |
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
| `CborCursor` | Does not move. Its three users are rewritten on `CborSpan` and `CborHead` in `common`, which gain the head and chunk accessors ADR 0002 D9 planned ("`CborCursor` goes away"), so CCL keeps one flat walker |
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
- the dataset's normalization, one copy (CCL's test and Yano's `CborNormalizer` hold two that differ only in Javadoc);
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

### D7. Yano's part

- **The strict read is the first decoding verdict; model gaps are listed.** `JavaLedgerValidationEngine` calls
  `HaskellPolicy.transaction` first; a rejection is `DECODING_FAILURE`. CCL's model is built after it, and can still
  fail on a gap in the model (today the 3 bignum rationals of #749). That still rejects the transaction, as
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
- The harness checks that the directory it is given is exactly that revision: a git checkout whose `HEAD` is the pin
  and whose working tree is clean, with no modified, deleted or untracked file (`git status --porcelain
  --untracked-files=all` empty). Both CI (`build.yml:104-107`) and Yano's `fetch-corpora.sh` already fetch the dataset
  with git. Git compares contents, so an edited input, expected output or normalization file, or a substituted
  sample, fails the run. A directory that is not such a checkout fails too.
- The harness is given the dataset's `dataset` subdirectory, as both callers pass it today, and finds the checkout's
  root with `git rev-parse --show-toplevel`. It runs the `git` executable for these checks, which both CI and Yano's
  build already need to fetch the dataset; it adds no Git library to the published artifact.
- So the outcomes file never meets a revision it was not made for, and a sample's identity is its path at that
  revision.

Q3 records the alternative.

### D9. Release

M1 and M2 ship in one CCL pre-release, then Yano follows (M4). Which pre-release is Q2.

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
- Reason codes are a fixed set (for example `UNKNOWN_FIELD`, `REPEATED_KEY`, `MISSING_FIELD`, `INDEFINITE_RECORD`,
  `HASH_SIZE`, `INTEGER_RANGE`, `NOT_WELL_FORMED`, `STRUCTURE`), named in M1. Messages are free text and are never
  compared.
- **The outcomes file** has a header with the dataset revision, and one line for **every** sample: rule, path, and the
  three stage outcomes, with the strict reason code. J3 and J4 compare whole files, not only the misses. The misses
  are a view of it.

## 6. Invariants

| ID | Invariant | Constrains | Checked by |
|---|---|---|---|
| J1 | Moving changes no outcome. Over every Conway sample at the pin, Yano's decision after M4 equals its decision before, per sample: accepted or rejected, and the re-encoding. The listed corrections: a Conway native script of type 6 is rejected (section 1.4), if P1 has not already made it so; and any `block` sample whose header the strict block read now rejects (D4). Yano's `ledger-rules`, `ledger-conformance`, `tx-services`, `runtime` and `script-evaluators` suites pass, less tests that moved, and admission is checked through the engine itself (`JavaLedgerValidationEngine`) on every transaction sample | D1-D4, D7 | A per-sample comparison of Yano before and after; the suites |
| J2 | CCL's default paths do not change: `CborCorpusConformanceTest`'s list, the golden bytes, and ADR 0003's G3 and G5 give the same results as before | D1 | The existing gates, run before and after |
| J3 | At the same dataset revision and CCL version, CCL's gate and Yano's report produce the same outcomes file: every sample's three stage outcomes and strict reason code | D5, D7, D8, D10 | Yano's report test compares its outcomes file with the harness's, whole |
| J4 | The outcomes file is exact: any change to any sample's line, a new miss, a miss that now passes, another outcome or reason code, fails the gate until the file is updated after review. One rejection lost and another gained, with equal totals, fails | D6, D10 | A gate test |
| J5 | Once `HaskellPolicy.transaction` accepts, Yano's mapping rejects nothing. Checked over every accepted sample of a rule that lives in a transaction, placed in its minimal transaction (the dataset has only 103 valid top-level `transaction` samples); header rules are checked through `HaskellPolicy.header` instead | D4, D7 | Yano's harness run and suites. M1's record names each site of section 1.3's table: moved, or unreachable and why |
| J6 | `ledger-cbor` depends only on `common`, `common-spec` and `address`, and holds no model and no ledger rule (amends ADR 0003 I1) | D3 | ADR 0003 I1's dependency test, updated |
| J7 | Strict reads and the validators are stack-safe at maximum nesting | D2, D4 | ADR 0003 I9's depth fixtures run through `HaskellPolicy.CONWAY`, including `RecordWalkDepthTest`'s `DEEP_NATIVE_SCRIPT`, and the preprod deep script, on the default stack, `-Xint` and a virtual thread |
| J8 | Yano's four node-diversity reports (CBOR, ledger rules, UPLC, turbo) are unchanged at the same pins, comparing outcomes and counts, not messages, and apart from the version label and J1's corrections | D7 | Before and after, as the Amaru removal was checked (Yano #241) |
| J9 | Every `FieldType` has a rule in `HaskellPolicy` or is listed as having none, so a new type cannot arrive without a decision | D2 | A test over `FieldType.values()` |
| J10 | What the strict checks accept, CCL's model decodes, except samples whose model stage is "failed" in the outcomes file (today the 3 bignum rationals of #749): for every strict-accepted sample of a rule with a CCL model, and for the native-script checker against `NativeScriptCodec` over standalone, witness, auxiliary data and reference scripts and mutated scripts | D3, D7 | CCL's gate; a differential test in `transaction-spec` |
| J11 | No error outcome, in any stage, on any sample | D10 | CCL's gate and Yano's report |

## 7. Milestones

| ID | Project | What | Entry | Exit |
|---|---|---|---|---|
| P1 | Yano | Prerequisite: Yano's ledger-cbor branch rejects a Conway native script of type 6, with a test | None | The test, before that branch merges |
| M1 | CCL | D1 to D4, D10: the policy, validators, native-script checker, strict reads, the exception and reason codes, the `address` dependency, `CborHead`'s accessors, with the tests that move | ADR accepted; #748 merged | J1 (Yano's current harness run against the CCL snapshot), J2, J5, J6, J7, J9, J10; M1's record of section 1.3's sites |
| M2 | CCL | D5, D6, D8: the harness, the outcomes file and the full gate in CI | M1 | J4, J11; the gate's numbers equal Yano's report at the pin (43,365 of 43,434 at `4fd977b2`, or as J1's corrections change them) |
| M3 | CCL | Release (D9) | M2; ADR 0003's G3 and G5 | Released |
| M4 | Yano | D7 | M3 | J1, J3, J5, J8, J11; the moved classes and the keyed-walk test deleted; ADR-060 corrected |

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
| CCL releases more often, for policy and dataset changes | Accepted by the maintainer on 2026-11-09: a reader or policy change needs a CCL pre-release |

## 12. Open questions

**Q1. Where the harness lives. Proposed as decided in r3: (a).** Options were (a) a published CCL module (D5), which
Yano depends on in its tests; (b) Yano keeps its harness and CCL gets its own, compared through their lists; (c) the
harness stays in CCL's tests and Yano compares with a list CCL publishes. (b) and (c) keep two rule tables, which
section 1.1's goal rules out, so D5 takes (a). The cost is one more artifact in CCL's published set. The maintainer
confirms it on acceptance.

**Q2. Which pre-release.** Options: (a) 0.8.0-pre8, which then waits for M1 and M2, and holds Yaci's and Yano's
ledger-cbor work (ADR 0003) behind them; (b) pre8 ships ADR 0003 as planned, and this ships in the next one.
Lean: (b). The ledger-cbor reader is reviewed and its downstream work is waiting; this is a separate change with its
own review. P1 does not wait for either.

**Q3. Dataset revisions.** Options: (a) D8: Yano runs only the revision its CCL version's outcomes file was made
for, so every dataset bump is a CCL pre-release first; (b) Yano may run a newer revision, with sample identity defined
by input, expectation and outcome, and rules for changed, removed and repaired samples. Lean: (a). It is simple and is
the rule this ADR exists for. Its cost is a CCL pre-release per dataset bump; (b) avoids that at the price of a second
set of comparison rules.

**Q4. Indefinite-length headers** (from ADR 0003 section 7.1, still open). `HaskellPolicy.header` accepts either
length, as cardano-ledger `6176e413` reads them, while deployed nodes read headers definite-only. The move keeps that
behaviour; the question moves to CCL with the policy.

## 13. Related findings (out of scope)

- Yano's report runs at protocol 11.0 and covers Conway only. Dijkstra's dataset samples are gated in CCL by the model
  and walk tests, not by a policy, until ADR 0003 M7b.
- `RawTransaction`'s mapping is Conway-only, as `HaskellPolicy.forEra` is.
- `RawCertificate`'s two checks throw `IllegalArgumentException`, which the engine reports as `ENGINE_FAILURE`. M1
  decides whether they are reachable after the policy (J5).

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
