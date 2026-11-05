# ADR 0003: One Era-Aware Ledger CBOR Reader

**Status**: Proposed
**Date**: 2026-11-05
**Risk**: High. This changes the code that reads every block and transaction in CCL, Yaci and Yano, and a shared bug
would reach all three.
**Related**: ADR 0001 in this directory; ADR 0002 (PR #736); PRs #734, #741-#745; bloxbean/yaci#113, #214, #191;
Yano ADR-060
**Modules**: CCL new `ledger-cbor`, `common`, `common-spec`, `transaction-spec`; Yaci `core`, `helper`; Yano
`ledger-rules`, `ledger-conformance`

This ADR continues ADR 0001 and ADR 0002, so it lives in the same directory. ADR 0001 built the span walker and the
raw views. ADR 0002 fixes the conformance gaps and shares the primitives. This ADR takes the next step: the Cardano
ledger format is read in one place, in CCL, and each project keeps only its own models and rules on top.

**Design only.** Nothing here is implemented. Code snippets are illustrative.

## 1. Context

### 1.1 Goal

Read the ledger's CBOR format in one place, for every era, so that a format bug is fixed once and a new era is added
once. Do it without breaking the public models people use: Yaci's models in listeners, and CCL's models.

### 1.2 Who reads the format today

Commits are in section 4.

| Project | Code | Size | Notes |
|---|---|---|---|
| CCL | Model `deserialize` methods in `transaction-spec` | At least 46 public methods taking CBOR items | Lenient. No era parameter |
| CCL | `transaction.raw` views: `RawBlock`, `RawTx` and others | 1,011 lines | Era-aware through `EraRules`. Bodies are read by key, and most fields stay raw spans. Header is a raw span |
| Yaci | `core/model/serializers` | 5,545 lines, including Byron | Returns Yaci's public models. Certificates alone take 903 lines |
| Yano | `ledger-rules` `conway.tx` | 4,273 lines in 23 files | Conway only. Reading and rejection are mixed together |

Each project reads the same certificate layout, for example: `CertificateSerializer` in Yaci, `Certificate` in CCL and
`RawCertificate` in Yano. A bignum bug or a missed shape has to be found and fixed in each.

ADR 0002 improves this but stops short. Its D8 keeps Yaci's serializers as they are, because "a mapper per field" would
be "roughly the same code". Its D9 keeps all of Yano's strict reader in Yano. That still leaves three readers of the
same format, which does not meet the goal above.

### 1.3 Why the three readers exist

Each parser mixes three different jobs, and only one of them needs to be shared:

- **Format**: field keys, positions, tags, indefinite forms, set tags, bignums.
- **Models**: turning those fields into the Java classes a project exposes.
- **Policy**: deciding what to reject. A transaction builder, a chain follower and a ledger validator rightly differ here.

ADR 0002 D9 says Yano's reader is tied to its rules. The tie to rule types is small: 4 of its 23 files import a
rule-side type. The larger tie is strictness. 9 files mention a decoder version, in code or comments, and strict checks
are mixed into the reading: `StrictCbor.definiteBytes`, `BoundedFields` and similar calls sit at each position. That is
the part this ADR must separate, and the pilot in M1 tests whether it can.

### 1.4 What actually changes between eras

Read from the ledger's CDDL at `f649f975`, for Shelley to Dijkstra:

| Item | How it changes |
|---|---|
| Block | `[header, bodies, witnesses, aux]` to Mary; `invalid_transactions` added from Alonzo; `[header, block_body]` in Dijkstra, with transactions no longer split into parallel arrays |
| Header body | Flat, 15 items, to Alonzo; nested, 10 items, from Babbage (Yaci's `preBabbageHeader` and `postBabbageHeader` read both) |
| Body keys | Mostly added: 8 Allegra, 9 Mary, 11-15 Alonzo, 16-18 Babbage, 19-22 Conway, 23-27 Dijkstra. Key 6 ends at Babbage |
| Body key 14 | **Changes meaning.** `required_signers` to Conway; `guards` in Dijkstra, marked "replaces required_signers" |
| Certificate tags | 5 and 6 end at Babbage; 7-18 start at Conway; 0 and 1 end at Conway |
| Redeemers | List to Babbage; list or map in Conway; map only in Dijkstra |

Two lessons follow. Most change is additive, so a table of "this field exists from era X to era Y" covers it well. And
key 14 shows that a key cannot be assumed to keep its meaning forever, so a typed read of a transaction body has to know
its era.

### 1.5 What other nodes do

Amaru has one decoder per type, used for both the node and its conformance test, with the protocol version passed in as
context. Dingo decodes through one library, gouroboros. Their setup shows the benefit of one reader. It does not show
how to reach one while keeping two sets of public models that users already depend on, which is the problem here. Both
were read, not run.

## 2. Scope

In scope:

- a new CCL module holding one reader of the Shelley-family ledger format;
- era-awareness that makes a new era a small, local change;
- block and header reading in CCL;
- moving CCL's and Yaci's decoders onto the reader without changing their public APIs;
- separating Yano's strict rules from its reading;
- what happens to the open CBOR PRs;
- the direction for CCL models in Yaci's listeners.

Out of scope:

- Writers. CCL's `serialize` methods and the map order work in #744 stay as they are.
- Byron and node-to-client results. Yaci stays their only reader, so they are not duplicated.
- `julc-core`, which stays free of CCL for consensus-critical Plutus Data (ADR 0002 D2).
- Implementing Dijkstra now. It is used here only to test the design (D4).
- Ledger validation in CCL. It remains out of scope, as in ADR 0001 section 2.

## 3. Threat model and trust assumptions

- **Untrusted input.** Blocks come from peers. Transactions come from peers and submission APIs. Datums and metadata
  can be built to exhaust stack or memory.
- **Lenient is not a validation boundary.** The reader accepts more than the ledger does on purpose (D5). Anything that
  decides ledger validity must go through Yano's policy. CCL users who need validity must not treat a successful read as
  proof of it.
- **Correlated failure.** Today, Yano's strict reader and CCL's decoders are independent, and either can reject a bad
  input. A shared reader removes some of that independence, so a reader bug could affect all consumers at once. Section
  10 covers the mitigation.
- **Hashes.** A transaction id, an auxiliary data hash and a block body hash come from original bytes and never from a
  re-encoding (ADR 0001 D3). Getting one wrong breaks consensus for the caller.

## 4. Pinned references

| Reference | Version |
|---|---|
| CCL | `master` `b2d685a4`; CBOR stack head `161b00f9` (#745) for the raw views and measurements |
| Yaci | `7c1638a` (`chore/ccl-0.8.0-pre7`) |
| Yano | `22852109e`, in `main` |
| Yaci Store | `524d7deca`, which pins Yaci 0.4.7 |
| cardano-ledger | `f649f9751074d2ab3de033fc3912f29c9862c1f5`, as in ADR 0001; `eras/*/impl/cddl/data/*.cddl`. ADR 0002's `6176e413` remains its pin for Conway conformance. Dijkstra is still in development and may change |
| cardano-cbor-dataset | `95e9e7c296ca1b641c8d99af2ab7c286a90caebf`. Conway: 42,675 samples. Dijkstra: 39,668 samples in 99 rules, of which 12,135 valid and 223 manual-valid. Fetched, never vendored, as the repository has no licence file |
| Amaru, Dingo | `798b170`, `efa65af`. Read only |

Measurements from Yano's report on the CBOR stack, with the harness using the new writers: 42,215 of 42,675. Yano's
strict reader alone rejects 31,679 of 31,761 invalid samples; CCL's decoders alone reject 19,132. `main` on CCL pre7
published 39,561.

## 5. Decisions

### D1. Three jobs, one owner each

| Job | Owner | Public impact |
|---|---|---|
| Format | CCL `ledger-cbor` | New API; nothing removed from stable releases |
| Models | CCL `transaction-spec`; Yaci `core`. Thin mappers over the reader | None: classes and signatures stay |
| Policy | Yano `ledger-rules` for validation; Yaci for fail-or-continue during sync | None |

A mapper contains no knowledge of keys, positions, tags or encodings. A policy contains no knowledge of where a field
is. The reader contains no model and no ledger rule.

This changes the balance ADR 0002 D8 drew. Mappers are still per-field code, and total lines may not fall much at
first. The value is that format knowledge, and format bugs, stop being repeated. M1 measures whether that holds.

### D2. A new module, `ledger-cbor`

Artifact `cardano-client-ledger-cbor`. It depends on `common` (`CborSpan`) and `common-spec` (`Era`, and `crypto` for
Blake2b) only. `transaction-spec` gets `api project(':ledger-cbor')`, so everyone who has CCL today gets the module with
no change to their build, Yaci included.

A package would be enough to hold the code. A module is chosen because the build then enforces the rule: the reader
cannot import a model, since none is on its classpath. Cost is one more artifact, and the build already names and
publishes every module the same way.

What moves and what stays:

| Item | Decision |
|---|---|
| `CborSpan`, `CborHead`, `DataItemDecoder` | Stay in `common`. They are general CBOR, and Yano imports them |
| `transaction.raw` views | Move to `ledger-cbor` and keep the package, so imports do not change. Nothing of that package remains in `transaction-spec`, so no package is split |
| `RawDatum.toPlutusData()`, `RawScript.toScript()` | Need model classes, so they cannot move. They become static helpers in `transaction-spec` (Q2) |

The raw views first shipped in `0.8.0-pre6`, and the last stable release, `0.7.3`, does not have them. In these projects,
their users are Yano and `tools/chain-parse-check`; Yaci and JuLC do not use them yet. So this change can be made with a
release note.

ADR 0001 section 5 said no new module. It did not need one then, because the views read little and could use models for
conversions. A full format reader is what changes that.

### D3. Era-awareness is data, in one place

Era knowledge lives in `EraSpec`, which replaces `EraRules` and is the only place that compares eras. It has two parts.

**A row per era, naming a layout for each thing that has layouts.** A layout is implemented once. A new era names the
existing layouts and adds only what truly changed.

| Era | Block layout | Header layout |
|---|---|---|
| Shelley, Allegra, Mary | `PARALLEL_4` | `FLAT_15` |
| Alonzo | `PARALLEL_5` | `FLAT_15` |
| Babbage, Conway | `PARALLEL_5` | `NESTED_10` |
| Dijkstra (when added) | `UNIFIED_2` | `NESTED_10` |

**A table per keyed record or tagged union, with the eras each field is valid for.**

```java
// Illustrative
enum TxBodyField {
    FEE(2, COIN, SHELLEY, null),
    UPDATE(6, UPDATE_PROPOSAL, SHELLEY, BABBAGE),
    REQUIRED_SIGNERS(14, KEY_HASH_SET, ALONZO, CONWAY),
    GUARDS(14, GUARD_SET, DIJKSTRA, null);        // same key, new meaning
}

enum CertKind {
    STAKE_REGISTRATION(0, fields(CREDENTIAL), SHELLEY, CONWAY),
    REG_DREP(16, fields(CREDENTIAL, COIN, OPTIONAL_ANCHOR), CONWAY, null);
}
```

Mappers, policies and accessors all read these tables, so positions and arities are written down once. Writing the
tables by hand is deliberate. Generating them from CDDL was rejected in ADR 0002 and stays rejected (section 9). But
CDDL is good evidence, so a tool in `tools/` checks each table against the pinned files (I3).

Adding a Shelley-family era then means: an `Era` constant, one `EraSpec` row, new rows in the field tables, and a new
layout only if the shape really changes. A test fails if any `Era` constant has no row (I2).

**Era and protocol version are different things.** The reader knows eras, because they decide layout and meaning. It
does not know protocol versions. Rejections that depend on a version belong to the policy (D6).

**Reading a transaction without an era.** A mempool transaction has no envelope. `RawTx.of(bytes)` stays era-free for
what needs no era: spans, the transaction id and raw field access by key. Typed field reads take an era:
`tx.body(era)`. Key 14 is the reason this cannot be avoided.

### D4. Block parsing in CCL

Two steps, so that deciding what a block is never requires being able to parse it:

1. **`BlockEnvelope.of(bytes)`** reads `[era, block]`, optionally in tag 24 as `RawBlock` allows today, and returns the
   era index and the block's span. It accepts any index, including Byron and eras CCL does not know. It rejects only
   bytes that are not such an envelope.
2. **`RawBlock.of(envelope)`** reads a known Shelley-family era through its layout. An unknown era raises a specific
   `UnsupportedEraException`.

This resolves the conflict ADR 0002 D8 noted. `RawBlock` rejects unknown eras, while Yaci attempts a parse and leaves
`Block.era` null (`EraUtil.getEra`). Yaci can now send Byron to its own parsers, and treat an unknown era as its policy
says, using the envelope alone.

New reading in this decision: `RawHeader` with both layouts, giving block number, slot, previous hash, keys, VRF
results, body size and hash, operational certificate, protocol version and the header hash.

`RawBlock`'s public methods that apply to every layout stay the same: `era()`, `header()`, `txCount()`, `tx(i)`,
`txBytes(i)`, `invalidTxIndexes()`, `bodyHash()`. The layout is an internal strategy. Methods that only make sense for
parallel arrays (`transactionBodies()` and others) throw a clear error on a unified layout.

**Worked example: Dijkstra.** The design was checked against `dijkstra.cddl` at the pinned commit. It needs one `Era`
constant, one `EraSpec` row reusing `NESTED_10`, one new layout `UNIFIED_2` with its body hash, field rows for keys 14
and 23-27, removal bounds on certificate tags 0 and 1, and new readers for sub-transactions and the Leios and Peras
certificates. Nothing else in the module branches on era. The dataset's 39,668 Dijkstra samples will be an independent
check when the era is frozen (M7). Until then, Dijkstra stays rejected, as ADR 0001 decided.

### D5. What the reader accepts and rejects

The reader's rejection set is small on purpose, and the rest is reported to the caller.

| Input | Reader |
|---|---|
| Not well-formed CBOR | Reject |
| The wrong container at a position it must enter, or a wrong arity for a known record | Reject when read. Today's decoders mostly fail here too, by cast or size check, and I5 finds any that do not |
| Mismatched counts and bad indexes in a block (ADR 0001 D6) | Reject, as `RawBlock` does today |
| Any shape valid on chain in any supported era | Accept |
| Indefinite lengths, non-minimal integers, optional set tags, unknown keys | Accept, and expose as facts on the span |
| Wrong hash length, empty sets, oversized fields, duplicates in sets, a field outside its era | Accept. These are policy (D6) |

Rules that keep this safe:

- **Lazy.** Views read a field when asked. A bad witness set fails only when it is read, so a consumer can isolate the
  failure. This is also what Yaci's `DataItemIsolation` achieves today, by a harder route.
- **No re-encoding.** Every span is the caller's bytes (I8).
- **No narrowing.** A migrated entry point accepts what it accepted before, unless a difference is listed and approved
  (I5, and ADR 0002 D4). This is what stops strictness arriving by accident.
- **Stack-safe.** Spans are iterative, and Plutus Data, native scripts and metadata stay as spans until asked for
  (ADR 0001).

### D6. Rejection is a policy on top of the reader

The tables make a walk possible that the reader owns and the caller's policy observes:

```java
// Illustrative
public interface FormatPolicy {
    void check(FieldType type, CborSpan value, Era era);   // throws to reject
    FormatPolicy ACCEPT_ALL = (type, value, era) -> {};
}

rawTx.walk(era, policy);
```

- The walk goes over the same tables as the accessors, so it has no second copy of the layout.
- `FieldType` is a small set of CDDL types: `HASH28`, `COIN`, `NONEMPTY_SET`, `UNIT_INTERVAL` and so on.
- CCL ships only `ACCEPT_ALL`. It has no strict mode and no version parameter, which keeps ADR 0001's "no options" for
  the models and avoids what ADR 0002 D9 feared.
- Yano's `HaskellPolicy(decoderVersion)` holds what `StrictCbor`, `CborCanonical` and `BoundedFields` hold today, with
  the protocol version inside it.
- Recursive types are leaves of the walk: the policy gets the span, and Yano's existing iterative validators read it.
- Rules that cross fields, such as "the certificate's deposit matches", stay in Yano's rule code and read the views.

**This supersedes ADR 0002 D9 only if the pilot passes.** If M1 shows that Yano's rules do not separate cleanly, Yano
keeps its reader and shares the primitives only, exactly as D9 says, and the rest of this ADR still stands for CCL and
Yaci.

### D7. CCL's models keep their API

- `Transaction.deserialize` and all model signatures stay. Internally they call the reader and map the result.
- The public overloads taking `Array`, `Map` or `DataItem` stay, at least 46 of them. They re-encode the item and use
  the same path. Encoding facts are already lost in such a tree, and these models do not use them.
- Documented leniency and lossy behaviour stay, and they change only through a separate, visible decision.
- Model fields are only ever added. A model or method is deprecated after a release has offered its replacement.

### D8. Yaci keeps its models and listeners; its serializers shrink

The flow becomes `CCL reader -> Yaci mapper -> Yaci models -> listeners`. `BlockChainDataListener` and its `onBlock`,
`rollforward` and `onParsingError` keep working as they do.

Direct callers of the main serializers, in main code:

| Serializer | Called by | Plan |
|---|---|---|
| `BlockSerializer` | `BlockfetchAgent`; Yano, about 15 uses; Yaci Store, none | Keep the class as a thin wrapper over the reader and the mapper |
| `ByronBlockSerializer`, `ByronEbBlockSerializer` | Yaci; Yano, 6 uses | Keep unchanged: the only Byron readers |
| `BlockHeaderSerializer`, `TransactionBodySerializer`, `TransactionOutputSerializer`, `WitnessesSerializer`, `AuxDataSerializer`, `CertificateSerializer`, `UpdateSerializer`, `WithdrawalsSerializer` | Yaci core only | Become mappers. Deprecate, then remove in a later release (Yaci is public, so unseen users may exist) |
| Network message serializers (`StartBatch`, `BatchDone` and others) | Yaci core | Keep. They are protocol messages, not ledger data |

Yaci behaviour that has been earned the hard way and must be kept. Each gets a regression test before its code moves:

- `BlockSerializer.deserialize` tolerates trailing top-level values, and keeps a null era for unknown eras.
- Witnesses or auxiliary data that fail to parse do not fail the block (`DataItemIsolation`, `metadataParseError`).
- A count mismatch between parsed and raw items logs and continues.
- Datum and redeemer CBOR and hashes come from source bytes, and the datum duplicate rule is kept.
- Auxiliary data CBOR is exposed only if it matches the transaction's hash.
- `YaciConfig` still controls `returnBlockCbor`, `returnTxBodyCbor` and `returnFullTxCbor`.
- A parse failure raises `onParsingError` with the block number when the header can be read.

No runtime switch between old and new parsers is added. Each step is proven before release by comparing old and new
output (I5, I6), and then the old code is deleted. A flag would keep two parsers alive, which is the problem this ADR
exists to solve.

Yaci's own `Era` enum stays, with its Byron constant. The mapper converts. Yaci Store is not affected until it leaves
Yaci 0.4.7.

### D9. Yano

- Reads through the views and enforces `HaskellPolicy`, once D6 passes the pilot.
- `RawCertificate`, `RawProposal` and similar classes keep their records for the rules to consume. They lose their
  reading code.
- **The conformance harness improves as a result.** Readers for each CDDL rule can be called directly, so the harness no
  longer wraps 72 rules in a parent, and the `StrictElements` doorway goes away. Yano's report then measures one reader
  plus one policy, not two decoders together.
- Using the views directly in hot paths, in place of Yaci's models, is possible later and is not part of this ADR.

### D10. The open CBOR PRs

**Merge the stack as planned. This ADR starts after it.** The stack fixes real defects in code that stays public, and
holding it back would delay about 2,650 samples of improvement for no benefit. No PR needs rework.

| PR | Decision | How it relates to this ADR |
|---|---|---|
| #734 indefinite arrays, unknown cost models | Merge first | `DataItem` trees stay for the public overloads (D7) |
| #741 conformance gate, golden bytes | Merge | Becomes the gate for every migration step here |
| #742 number fixes | Merge | Fixes the model decoders in use now. Its tests stay when the code moves onto the reader |
| #743 `ProtocolParamUpdate` Conway keys | Merge | Additive model change. M5's mapper fills the same fields |
| #744 Haskell map order writers | Merge | Writers are out of scope, so it is unaffected |
| #745 inline datum bytes, script references | Merge | Model and writer behaviour, unaffected |
| #736 ADR 0002 | Merge as the record of its decisions and measurements, with a status note added | See below |
| #730 voting redeemer index | Unaffected | Independent of this work |

Once this ADR is accepted, ADR 0002 gets a one-line note: "D8's serializer boundary and D9 are revisited by ADR 0003."
D1-D7 and D10 stand unchanged. Of D8:

| ADR 0002 D8 item | Status |
|---|---|
| `CborSerializationUtil` delegates to CCL, with encoders | Stands. Do it in M0, as yaci#113 needs it |
| `CborSlice` to `CborSpan` | Stands |
| Scalar reads delegate to CCL (shared bignum bug) | Stands |
| Native script hash from spans (yaci#191) | Stands |
| Extractors to `RawBlock` | Stands. Delivered with M2 |
| "Not a wholesale swap", with serializers kept | Superseded by D8 here: models stay, serializers shrink |

On the Yano side, #231 is merged and #230 is closed. After the CCL release, Yano bumps its versions and applies the
harness changes for the new writers, which measure 42,215 of 42,675 locally. The plan's release order is unchanged: CCL,
then Yaci and JuLC, then Yano.

### D11. CCL models in Yaci's listeners: yes as a direction, not yet as a commitment

Users have asked for this, and it would remove a conversion layer in the long run. It is not a drop-in change, and this
is what stands in the way:

| Gap | Evidence |
|---|---|
| CCL has no block or header model | `transaction-spec` has no such class, only `RawBlock` |
| Byron has no CCL model | ADR 0001 keeps Byron out |
| Listener data has no CCL home | Yaci's `helper` `Transaction` carries the hash, CBOR, block number, UTxOs and invalid flag. CCL's `TransactionBody` has no hash or original bytes |
| Users rely on Yaci's models | Yaci Store imports them in 222 files, Yano in 95 main files |
| CCL would become Yaci's public API | Yaci depends on CCL with `implementation`. Exposing CCL types needs `api`, and ties users' CCL version to Yaci's |

Proposed path, as a separate ADR in Yaci after M6:

1. **Add, do not replace.** A new listener callback gets a lazy block view, so a listener that wants only one form pays
   only for that form. The view hands out `RawBlock`, and CCL `Transaction` or Yaci `Block` on request. Today
   `BlockfetchAgent` always builds Yaci's model first, so this needs a change there.
2. **Close the gaps.** CCL gets whatever it needs to represent every Shelley-family era without loss.
3. **Deprecate only when:** the new callback has shipped in at least two minor releases, Yaci Store and Yano have moved,
   and old and new data have been compared on full chains.
4. **Remove only in a major release.**

During that time both callbacks exist, so at first there is more to maintain, not less. The saving comes at the end. If
step 3's conditions are never met, Yaci's models simply stay, and D8 still delivers one reader. Nothing in this ADR
depends on this path.

## 6. Invariants and gates

| ID | Invariant | Constrains | Checked by |
|---|---|---|---|
| I1 | `ledger-cbor` depends only on `common` and `common-spec` | D2 | Build; a test on its declared dependencies |
| I2 | Every `Era` constant has an `EraSpec` row, and `ledger-cbor` compares eras only in `EraSpec` and the field tables | D3 | Unit test; review |
| I3 | Field tables match the pinned CDDL for every era | D3 | Tool in `tools/`, run on each ledger pin bump |
| I4 | No public signature of a stable model or a Yaci serializer changes or disappears in a migration step, except through a deprecation that has shipped | D7, D8 | G5 |
| I5 | For each migrated entry point, old and new accept and reject the same inputs, over the whole dataset and real blocks. Any difference is listed and approved | D5, D7, D8 | Differential test; G3 |
| I6 | Old and new produce equal models over the valid dataset samples and full chain replays, for CCL and Yaci | D7, D8 | Differential test; G3 |
| I7 | Yano's report misses a subset of the baseline's samples, and the strict side alone rejects at least 31,679 of 31,761 invalid samples | D6, D9 | G6 |
| I8 | Every hash and every byte-exact field comes from original bytes | D5 | ADR 0001 fixtures; golden tests from #741 |
| I9 | Reading and walking stay stack-safe at maximum nesting | D5, D6 | ADR 0001 depth fixtures, on the walk as well |
| I10 | Parse time and allocation on the same blocks stay within 10% of the M0 baseline (proposed figure) | D8 | Benchmark, recorded per milestone |
| I11 | A bad witness or auxiliary data item still does not fail its block, and listeners see the same errors | D5, D8 | Regression tests from D8's list |

Gates reused from ADR 0002: **G3** `tools/chain-parse-check` full replay on mainnet, preprod and preview, before and
after; **G5** downstream suites against a CCL snapshot: Yaci `core`, JuLC, Yano `ledger-rules`, `ledger-conformance` and
`tx-services`; **G6** Yano's report at each milestone. New: **G7**, the differential tests of I5 and I6 as a CI job in
Yaci and CCL, deleted with the old code once the milestone has shipped.

## 7. Milestones

Each milestone releases in the usual order: CCL, then Yaci, then Yano. It can stop there and leave a working system.

| ID | What | Entry | Exit |
|---|---|---|---|
| M0 | Stack merged and released (D10). ADR 0002's standing D8 items in Yaci. New module; raw views moved with no behaviour change. Baselines recorded: speed (I10), report, accept/reject sets | ADR accepted | Report at 42,215; G3, G5 pass; baselines committed |
| M1 | **Pilot: certificates**, through all three projects. `CertKind` table, `RawCertificate` reader, `FormatPolicy`, Yaci and CCL mappers, Yano policy | M0 | The decision below |
| M2 | Block, envelope and header. `EraSpec` complete for Shelley to Conway. Yaci's `BlockHeaderSerializer` and extractors move | M1 passes | I5, I6, I10, I11; G3 |
| M3 | Transaction body, inputs, outputs, values, withdrawals, mint | M2 | I5, I6, I8; G3, G6 |
| M4 | Witnesses, scripts, datums, redeemers, auxiliary data | M3 | I5, I6, I8, I9, I11; G3, G6 |
| M5 | Governance, `ProtocolParamUpdate`, pre-Conway `update`. Includes checking whether parameter key 17 changes meaning at Babbage (Alonzo's field is `CoinsPerUTxOWord`, Babbage's `CoinsPerUTxOByte`) | M4 | I3, I5, I6; G3, G6 |
| M6 | Cleanup. Yaci's inner serializers deprecated. Yano's old reading code and the G7 differential jobs deleted. Yano's harness reads each rule directly | M5 | Report no lower; `StrictElements` gone |
| M7 | Dijkstra, as D4 sets out | Era frozen for a hard fork | Dijkstra dataset samples pass; report extended |
| M8 | Listener ADR in Yaci (D11) | M6; maintainer's decision | Separate ADR |

**M1's decision.** Certificates are the pilot because they exist in all three projects, have 19 tags across eras, and
involve no hashing, so a mistake is cheap. Today's code is 903 lines in Yaci, 350 in Yano's `RawCertificate`, and
certificate classes of 1,801 lines in CCL, of which the writers stay.

- **Continue** if all of these hold: certificate format knowledge exists only in `ledger-cbor`; I5, I6, I7, I10 pass;
  Yaci's and Yano's certificate reading code each get smaller; and Yano's `HaskellPolicy` is straightforward to read.
- **Fall back** if the Yano part fails: keep D1-D5 and D7-D8 for CCL and Yaci, and Yano keeps its reader (ADR 0002 D9).
- **Stop** if the mappers end up no simpler than the old serializers: keep ADR 0002's boundary and record why here.

The maintainer makes this call from the measured results.

## 8. Compatibility

| Item | Impact |
|---|---|
| CCL model classes and signatures | None |
| `Transaction.deserialize` results | Same, by I5 and I6 |
| Yaci models and listeners | None |
| `BlockSerializer` | Same class and result |
| Yaci's inner serializers | Deprecated at M6, removed later |
| `transaction.raw` | Pre-release API. Same package in a new artifact, which arrives transitively. `RawDatum.toPlutusData` and `RawScript.toScript` become helpers (Q2) |
| `EraRules` | Package-private, so no impact |
| `RawBlock.of(bytes)` | Same. New `of(BlockEnvelope)`. A new exception type for unknown eras extends the current one |
| Native image | Views use no reflection. `native-image-test` runs on each milestone |
| New artifact | `cardano-client-ledger-cbor`, added to the published set by the existing build logic |
| Wire format | None. The reader only reads |

## 9. Alternatives considered

| Alternative | Why not |
|---|---|
| Stay with ADR 0002's boundary | Leaves three readers (section 1.2). Still the fallback if M1 fails |
| Everyone uses CCL's models | Breaks Yaci's users at once, and the models are lossy and have no block type. D11 is the slow, safe version |
| New universal model replacing both | A third model during migration, and every user has to move anyway |
| Generate readers from CDDL | A rewrite with a code generator to maintain, and CDDL does not state every decoder rule. Used as a check instead (I3) |
| Reader with a strict or version parameter, as Amaru does | CCL also serves transaction builders and chain followers, which should not get ledger rejections. It would have a decoder version in every public path, for one consumer. `FormatPolicy` keeps that in Yano |
| A class per era (`ConwayTxBody`, `BabbageTxBody`) | Most fields are the same across eras (section 1.4), so this repeats them, and every new era copies a class |
| Keep the reader in `transaction-spec` | Works, but only review stops a view importing a model. Open as Q1 |
| Runtime flag choosing old or new parsers | Keeps both parsers alive and doubles test paths (D8) |
| Move Byron into CCL | Yaci is the only reader and no one else needs it. No duplication to remove |

## 10. Risks

| Risk | Mitigation |
|---|---|
| A reader bug affects CCL, Yaci and Yano together, as they no longer differ | Dataset, I5 and I6 before each release; G3 on real chains. The strict figure in I7 is measured for the policy alone. This risk does not go away, and we accept it in exchange for bugs being fixed once |
| Sync breaks on an old block | Lazy reads, the G3 full replay per milestone, and Yaci's `onParsingError` stays. One milestone at a time, so a failure has a small search space |
| Mappers change results subtly, such as a null against an empty list | I6 compares models for equality. `equals` must cover the fields, and Yaci's models are Lombok `@EqualsAndHashCode`. Models without it get compared by JSON |
| Tables grow beyond what is readable | Pilot judges this first. Only records and unions use tables; recursive and one-off shapes stay as code |
| Slower sync, with lazy views over spans in place of a tree | I10. It may equally turn out faster, but this has not been measured |
| The work stalls half way, leaving four readers | Each milestone is complete on its own, and old code for a migrated area is deleted in that milestone or M6 |
| Dijkstra changes before it freezes | Only used as a design check here. M7 waits for the freeze |
| Yano's existing strictness is lost while moving to a policy | I7 at each step: the set of missed samples can only shrink |

## 11. Open questions

**Q1. Module name and whether to have a module.** Options: (a) `ledger-cbor`; (b) `cbor`, which is shorter but suggests
general CBOR, which stays in `common`; (c) no module, a package in `transaction-spec` guarded by a test. Lean: (a).

**Q2. Pre-release API change.** `RawDatum.toPlutusData()` and `RawScript.toScript()` cannot move with their classes.
Options: (a) static helpers in `transaction-spec`, with a release note, since these shipped only in pre-releases; (b)
keep the classes in `transaction-spec` and have new views in a new package, leaving two sets of views. Lean: (a).

**Q3. The 10% speed limit in I10.** It is a proposed figure with no measurement behind it. Lean: record the M0 baseline
first, then confirm or change it.

**Q4. When to run M1 relative to ADR 0002's other Yaci work.** Options: (a) M0 first, as written; (b) start the pilot on
a branch now against the stack. Lean: (a), as a baseline measured against unreleased code would have to be redone.

**Q5. Yaci serializer removal timing.** The plan deprecates at M6 and removes later, and Yaci's outside users cannot be
seen. Lean: keep deprecated classes for at least two minor releases.

## 12. Related findings (out of scope)

- ADR 0001's status line and section 7 are stale, as ADR 0002 section 1.5 notes.
- Yaci's `Era` has no constant for later eras, and `EraUtil.getEra` returns null for them, so `Block.era` can be null.
- The dataset carries Dijkstra samples that no Java reader in these projects covers yet.
- In `CertificateSerializer`, a genesis delegation that fails to parse prints a stack trace and returns an empty
  certificate. M1's regression tests have to pin this, and a separate decision should remove it.

## 13. Revision history

- **r1** (2026-11-05): initial proposal.
