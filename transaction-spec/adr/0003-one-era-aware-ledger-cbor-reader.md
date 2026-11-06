# ADR 0003: One Era-Aware Ledger CBOR Reader

**Status**: Accepted. M0 to M6 are implemented (section 7.1); M7 and M8 are not started
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

**Implementation.** M0 to M6 are implemented, and section 7.1 records how each exit was met and what differs from
this design. Code snippets in the decisions are illustrative.

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
    FEE(2, COIN, REQUIRED, SHELLEY, null),
    UPDATE(6, UPDATE_PROPOSAL, OPTIONAL, SHELLEY, BABBAGE),
    REQUIRED_SIGNERS(14, KEY_HASH_SET, OPTIONAL, ALONZO, CONWAY),
    GUARDS(14, GUARD_SET, OPTIONAL, DIJKSTRA, null);        // same key, new meaning
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

Three entry points, so that deciding what a block is never requires being able to parse it:

1. **`BlockEnvelope.of(bytes)`**, or `of(span)` for a caller holding a span, reads `[era, block]`, optionally in tag 24
   as `RawBlock` allows today, and returns the era index and the block's span. It accepts any index, including Byron and eras CCL does not know. It rejects only
   bytes that are not such an envelope.
2. **`RawBlock.of(envelope)`** reads a known Shelley-family era through its layout. An unknown era raises a specific
   `UnsupportedEraException`.
3. **`RawBlock.readAs(envelope, era)`** reads the block with a named era's layouts and field tables, whatever index the
   envelope carries. Its `era()` returns the named era, and `envelope.eraIndex()` stays the fact. This is always the
   caller's explicit choice; `RawBlock.of` never falls back to it. `EraSpec.newest()` names the last row of the table.

This resolves the conflict ADR 0002 D8 noted. `RawBlock` rejects unknown eras, while Yaci has no list of layouts:
`BlockSerializer` parses any non-Byron index, leaves `Block.era` null (`EraUtil.getEra`), and delivers the block.
Listeners get that null and `TxCborUtil.assembleTxCbor` handles it, returning null. So today a new era with an unchanged
layout keeps syncing, and that must not be lost. Yaci's wrapper dispatches on the envelope alone:

| Envelope index | Yaci | Result |
|---|---|---|
| 0, 1 | Its Byron serializers | As today |
| 2 to 7 | `RawBlock.of(envelope)` | `Block.era` set |
| Anything else | `RawBlock.readAs(envelope, EraSpec.newest())` | Populated `Block` with a null era. A block that does not fit that layout, such as Dijkstra's two items, is a parse failure and raises `onParsingError` (I12) |

Reading an unknown era with the newest tables can misread a key whose meaning changed, as key 14 does in Dijkstra. That
is no worse than today, since Yaci's serializers do not look at the era at all, and the null era tells the listener the
era was not recognised. Adding an era to `EraSpec` moves `newest()` to it. CCL's own users keep the rejection, and
choose `readAs` only if they want this behaviour.

New reading in this decision: `RawHeader` with both layouts, giving block number, slot, previous hash, keys, VRF
results, body size and hash, operational certificate, protocol version and the header hash. The header hash is
Blake2b-256 of the header's original bytes (I8), which corrects Yaci on some encodings (D8, corrections).

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
public interface FormatPolicy {                  // every method throws to reject; the defaults do nothing
    default void enter(RecordSpec record, CborSpan container, Era era) {}
    default void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {}
    default void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {}
    default void check(FieldType type, CborSpan value, Era era) {}
    FormatPolicy ACCEPT_ALL = new FormatPolicy() {};
}

rawTx.walk(era, policy);
```

**The walk's contract.** Today's rules need to see what accessors hide: at pinned Yano, `readBody()` rejects unknown keys,
duplicate keys and missing keys 0, 1 and 2, before any value is read. So the walk reports structure as well as values:

- **`enter`** is called with the original container before anything is normalised, so its head, tag and length form are
  visible.
- **`entry`** is called for every raw entry of a keyed record, in source order. Duplicates and unknown keys are not
  dropped.
  - `field` is null if the key is in no era's table, or is not an integer.
  - `field.validIn(era)` says whether a known field belongs to this era.
  - `occurrence` counts earlier entries with the same decoded key, so `02` and `1802` are the same key. The raw `key`
    span still shows the encoding.
- **`missing`** is called after the entries, for each field the table marks required in this era and the record lacks.
- **Other records.** A tagged union is the same with its tag as the key. A positional record is the same with its
  position as the key, so a short record reports `missing` and a long one reports entries with a null `field`.
- **`check`** is called for each known value with its type, and a recursive type is one value.

`RecordSpec` and `FieldSpec` come from the tables and carry the key, type, era range and required flag. The policy therefore asks
"is this field unknown, repeated, out of era or missing" without knowing a key number, which is what D1 requires. If the
policy accepts a value that the walk cannot enter, the reader rejects it as D5 says.

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
| `BlockHeaderSerializer`, `TransactionBodySerializer`, `TransactionOutputSerializer`, `WitnessesSerializer`, `AuxDataSerializer`, `CertificateSerializer`, `UpdateSerializer`, `WithdrawalsSerializer` | Yaci core only | Become mappers. Deprecate, then remove once Q5's period has passed (Yaci is public, so unseen users may exist) |
| Network message serializers (`StartBatch`, `BatchDone` and others) | Yaci core | Keep. They are protocol messages, not ledger data |

Yaci behaviour that has been earned the hard way and must be kept. Each gets a regression test before its code moves:

- `BlockSerializer.deserialize` tolerates trailing top-level values. `CborSpan.of` rejects them, so the wrapper reads
  the first value with `CborSpan.at` and passes that span to `BlockEnvelope.of`.
- An unknown era index in the newest layout gives a populated `Block` with a null era (D4, I12).
- Witnesses or auxiliary data that fail to parse do not fail the block (`DataItemIsolation`, `metadataParseError`).
- A count mismatch between parsed and raw items logs and continues.
- Datum and redeemer CBOR and hashes come from source bytes, and the datum duplicate rule is kept.
- Auxiliary data CBOR is exposed only if it matches the transaction's hash.
- `YaciConfig` still controls `returnBlockCbor`, `returnTxBodyCbor` and `returnFullTxCbor`.
- A parse failure raises `onParsingError` with the block number when the header can be read.

Two differences from today are known already, and go in I5's list for approval at M2. Neither should occur in a block
from a valid chain, and G3 checks that on real chains. In both, the listener gets `onParsingError` in place of a
populated `Block`:

- `BlockSerializer` chooses its header reader by shape, not by era, and accepts four or five block items under any
  index. Under D4, a known era whose shape contradicts its `EraSpec` row fails, and so does an unknown era in an older
  shape than the newest.
- `RawBlock`'s checks from ADR 0001 D6 (D5's third row) reject mismatched counts and bad indexes in a block that Yaci's
  serializer does not check for.

Four inputs pin all this from M2 on. They use Yaci's fixture `core/src/test/resources/block/preprod292683.txt`, which has
three transactions, and were reproduced by the review of this ADR against Yaci `7c1638a`:

| Change to the fixture | Yaci today | Under this ADR | Status |
|---|---|---|---|
| Envelope era 6 changed to 9 | Populated, `era` null, 3 bodies and 3 witnesses | Same | I12 |
| A top-level `00` appended | Populated | Same | Kept, as listed above |
| Witness array replaced with `80` | Populated, 3 bodies and 0 witnesses | `onParsingError` | Q6 |
| Invalid-index list replaced with `8103` | Populated, `invalidTransactions` is `[3]` | `onParsingError` | Q6 |

**Corrections.** Some of Yaci's results are wrong on some inputs, and the migration should change them. Each one goes in
an approved list, with a release note, and I6 checks it against an expected value computed outside both parsers. The
comparator never normalises a listed field away.

| Field | Today | After | Milestone |
|---|---|---|---|
| `HeaderBody.blockHash` | Blake2b-256 of a re-encoding of the header (`BlockHeaderSerializer`, both branches) | Blake2b-256 of the original header bytes | M2 |
| `costModelsHash` | Blake2b-256 of a re-encoding (`UpdateSerializer`) | To decide at M5 from G3's results. Moving to original bytes is the default under I8. This field may differ on real chain data, so it needs its own look | M5 |

Take that fixture and change only the block number's encoding, from `1a0004774b` to `1b000000000004774b`, which D5
accepts. Yaci still reports `1cf993e9...d248`, while the hash of the bytes received is `73ee9b5e...03ae`. Haskell keeps a
header's original bytes and hashes those: at the pinned ledger commit, `Praos/BlockHeader.hs` and `TPraos/BlockHeader.hs`
in `libs/cardano-protocol` define headers as `MemoBytes` and use `getMemoSafeHash`. So the second value is the right one
for such a header. This is not a claim that such a header is valid on chain. On the unchanged fixture the two agree. G3
reports how many real headers change; this has not been measured.

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
| I6 | Old and new produce equal models over the valid dataset samples and full chain replays, for CCL and Yaci. The only exceptions are fields on D8's approved correction list, each compared to an independently computed expected value | D7, D8 | Differential test; G3 |
| I7 | Two separate checks per sample, against M0's baseline. Yano's report misses a subset of the baseline's samples. And the strict side alone rejects a superset of what it rejected in the baseline (31,679 of 31,761 invalid samples today), apart from corrections listed and approved. A count alone does not pass | D6, D9 | G6, including after M6 removes the model decoder from the harness. A gate test: one rejection lost and another gained, with equal totals, must fail |
| I8 | Every hash and every byte-exact field comes from original bytes | D5 | ADR 0001 fixtures; golden tests from #741 |
| I9 | Reading and walking stay stack-safe at maximum nesting | D5, D6 | ADR 0001 depth fixtures, on the walk as well |
| I10 | Parse time and allocation on the same blocks stay within 10% of the M0 baseline (proposed figure) | D8 | Benchmark, recorded per milestone |
| I11 | A bad witness or auxiliary data item still does not fail its block, and listeners see the same errors | D5, D8 | Regression tests from D8's list |
| I12 | In Yaci, an unknown era index in the newest layout reaches listeners as a populated `Block` with a null era, and one that does not fit the layout raises `onParsingError`. `RawBlock.of` still rejects it | D4, D8 | Regression tests: a valid Conway block with its index changed, written before the code moves, and a two-item block |
| I13 | Yano's body and witness checks for unknown keys, duplicate keys, including differently encoded equal keys, and missing required fields are expressed through the walk with no key number in the policy | D1, D6 | M1's keyed-record proof |

Gates reused from ADR 0002: **G3** `tools/chain-parse-check` full replay on mainnet, preprod and preview, before and
after; **G5** downstream suites against a CCL snapshot: Yaci `core`, JuLC, Yano `ledger-rules`, `ledger-conformance` and
`tx-services`; **G6** Yano's report at each milestone. New: **G7**, the differential tests of I5 and I6 as a CI job in
Yaci and CCL, deleted with the old code once the milestone has shipped.

## 7. Milestones

Each milestone releases in the usual order: CCL, then Yaci, then Yano. It can stop there and leave a working system.

| ID | What | Entry | Exit |
|---|---|---|---|
| M0 | Stack merged and released (D10). ADR 0002's standing D8 items in Yaci. New module; raw views moved with no behaviour change. Baselines recorded: speed (I10), report, accept/reject sets, and the per-sample outcome of Yano's strict side alone (I7) | ADR accepted | Report at 42,215; G3, G5 pass; baselines committed; I10's limit confirmed or changed from the baseline (Q3) |
| M1 | **Pilot: certificates**, through all three projects. `CertKind` table, `RawCertificate` reader, `FormatPolicy`, Yaci and CCL mappers, Yano policy. Also the keyed-record proof below | M0 | The decision below |
| M2 | Block, envelope and header. `EraSpec` complete for Shelley to Conway. Yaci's `BlockHeaderSerializer` and extractors move | M1 passes | I5, I6, I10, I11, I12; G3; D8's four inputs and hash correction tested; Q6 decided |
| M3 | Transaction body, inputs, outputs, values, withdrawals, mint | M2 | I5, I6, I8; G3, G6 |
| M4 | Witnesses, scripts, datums, redeemers, auxiliary data | M3 | I5, I6, I8, I9, I11; G3, G6 |
| M5 | Governance, `ProtocolParamUpdate`, pre-Conway `update`. Includes deciding `costModelsHash` (D8), and checking whether parameter key 17 changes meaning at Babbage (Alonzo's field is `CoinsPerUTxOWord`, Babbage's `CoinsPerUTxOByte`) | M4 | I3, I5, I6; G3, G6 |
| M6 | Cleanup. Yaci's inner serializers deprecated. Yano's old reading code and the G7 differential jobs deleted. Yano's harness reads each rule directly | M5 | Report no lower; I7's two checks still pass; `StrictElements` gone |
| M7 | Dijkstra, as D4 sets out | Era frozen for a hard fork | Dijkstra dataset samples pass; report extended |
| M8 | Listener ADR in Yaci (D11) | M6; maintainer's decision | Separate ADR |

**M1's decision.** Certificates are the pilot because they exist in all three projects, have 19 tags across eras, and
involve no hashing, so a mistake is cheap. Today's code is 903 lines in Yaci, 350 in Yano's `RawCertificate`, and
certificate classes of 1,801 lines in CCL, of which the writers stay.

- **Continue** if all of these hold: certificate format knowledge exists only in `ledger-cbor`; I5, I6, I7, I10, I13 pass;
  Yaci's and Yano's certificate reading code each get smaller; and Yano's `HaskellPolicy` is straightforward to read.
- **Fall back** if the Yano part fails, including a current rejection that cannot be written without layout knowledge
  in the policy: keep D1-D5 and D7-D8 for CCL and Yaci, and Yano keeps its reader (ADR 0002 D9). Any check left out on
  purpose is recorded here.
- **Stop** if the mappers end up no simpler than the old serializers: keep ADR 0002's boundary and record why here.

**The keyed-record proof.** Certificates are mostly tagged and positional, so they do not prove the boundary for keyed
records. M1 therefore also builds the transaction body and witness set tables and walks them with Yano's policy, with no
mapper or accessor work. These inputs must get the same outcome as Yano's reader:

- a body with no fee
- an unknown body key, and an unknown witness key
- a repeated key, and equal keys encoded differently (`02` and `1802`)

Yano's strict reader is deleted only after this and the rest of the plan pass (M6).

The maintainer makes this call from the measured results.

### 7.1 Implementation record (r4)

M0 to M6 are implemented on CCL branch `feat/ledger-cbor` (one PR), Yaci branch `feat/ledger-cbor-reader` and Yano
branch `feat/ledger-cbor-reader`. Each step was built and tested against local snapshots of the others, with no release
in between. M7 and M8 are not started. What follows records how each exit was met, what differs from the plan, and what
needs the maintainer's approval before release. On 2026-11-06 the maintainer accepted the decisions and differences
listed here, except two that stay open: I10 for Yano's validation path, and indefinite-length headers. The final numbers
below are for CCL `762b18ec`, Yaci `489712e` and Yano `5f5d71f00`. Yaci `3b09410`, which only turns a stack overflow in
a block into a parse error, gives 0 differences against `489712e` on the corpus, the governance blocks and the dataset.

**How the work ran, against the plan.**

- **One CCL PR, no release between milestones.** The maintainer chose this on 2026-11-05 so that CCL is not released
  once per milestone. It changes Q4's order of releases (CCL, then Yaci, then Yano per milestone) into one release of
  each at the end. Every milestone was still gated in all three projects before the next one started.
- **G7 is a local tool, not a CI job.** With old and new code in one PR, a CI job comparing them has nothing to compare
  after merge. `tools/reader-diff` loads two sides (a CCL and a Yaci build each) in separate class loaders and compares
  their outcomes and models as canonical JSON, over a block corpus, the dataset and a chain stream. It also benches.
- **The M1 decision** was taken under the maintainer's instruction to continue autonomously: **continue**. No fall-back
  condition held. Certificate format knowledge exists only in `ledger-cbor`; I5, I6, I7 and I13 passed; Yaci's
  certificate code went from 903 to 562 lines and Yano's certificate reading from 146 to 54. I10 is discussed below.

**Gates, per milestone.** Numbers are from the final state unless a milestone is named.

| Gate | Result |
|---|---|
| I1, I2, I3 | Tests pass. `tools/cddl-table-check` matches every table at the pin and at `6176e413` |
| I5, I6 (CCL and Yaci) | 0 differences on real data at every milestone: 22,319 mainnet blocks (288,763 transactions), 118 mainnet blocks holding every update and proposal, cost-model blocks of preprod and preview, and the valid dataset samples. Differences on invalid or synthetic inputs are listed below. At the end, a full-chain stream of all three networks (preprod 5,376,728 blocks, preview 4,827,659, mainnet 9,595,957 with 124,065,266 transactions): CCL equal on every transaction, Yaci different only in D8's header hash correction, yaci#191's native script hash and one listed metadata message |
| I7 (Yano) | Both checks pass at every milestone with 0 lost. Report 42,215 of 42,675 at M0, 42,606 at the end. The strict side rejects 31,761 of 31,761 invalid samples (31,679 at M0) and accepts 10,850 of 10,914 valid ones (10,462 at M0); of the 64 it rejects, the reason for each is in the gate's baseline |
| I8 | Every hash and byte-exact field reads original bytes, including `costModelsHash` (below) |
| I9 | Depth fixtures pass on the walk and on every view, on the default stack, `-Xint` and virtual threads |
| I10 | See below |
| I11, I12 | Regression tests from D8's list, written before the code moved |
| I13 | M1's keyed-record proof passed; by M5 the walk rejects exactly what Yano's reader rejected on transactions, bodies and witness sets |
| G3 | 0 issues on preprod, preview and mainnet at every milestone from M2. At the end every per-era counter equals the pre-ADR run's: blocks, transactions, scripts, datums, redeemers and script data hashes checked and matched |
| G5 | 0 failures. CCL whole repository: 6,204 tests in 43 modules, with the dataset tests on. Yaci `core` 1,133 and `helper` 28. Yano `ledger-rules` 432, `ledger-conformance` 181, `tx-services` 49 |

**I10.** Measured on the same blocks, alternating old and new under the same load.

| Path | Result |
|---|---|
| CCL `Transaction.deserialize` per block | 124 µs against 207 µs (−40%), allocation −63%. Met |
| Yaci `BlockSerializer` per block | 380 µs against 434 µs (−12.5%), allocation −24%. Met |
| Yano strict reading of a Conway block | +30% against the pre-ADR reader at M4, unchanged since. Not met |

Yano's cost is structural: the walk checks every leaf against the policy, and the views then read the leaves again,
where the old reader did both in one pass. Removing it would need the walk to skip leaves the views read anyway, which
D6 rules out. The cost is about 40 µs per Conway block. The maintainer decides whether I10 applies to Yano's
validation path or only to the readers CCL and Yaci users call.

**Decisions taken during implementation.**

- **Q6: (a).** G3 and the full-chain streams found no real block affected, so a block whose shape contradicts its era,
  or which has mismatched counts or bad indexes, now raises `onParsingError` in Yaci. The list of shapes is in the PR.
- **`costModelsHash`: original bytes.** Blake2b-256 of the field as received (`RawCostModels.span()`), and each
  language's value in `costModels` is its original bytes as well. On every cost-model field on mainnet (23 fields, 44
  models), preprod (26, 49) and preview (42, 77) the old re-encoding equals the original, so no real hash changes. Only
  non-canonical encodings change, for example the dataset's `cost_models/manual-valid/auto-map-3-occur-key-boundary-255`.
- **Parameter key 17 changes meaning at Babbage.** cardano-ledger reads it as `CoinPerWord` in Alonzo
  (`Alonzo/PParams.hs`, `ppuCoinsPerUTxOWordL`) and `CoinPerByte` from Babbage (`Babbage/PParams.hs`,
  `ppuCoinsPerUTxOByteL`). The CDDL types both as `coin`, so I3 cannot see it. `ProtocolParamField` has a row for
  each. CCL's and Yaci's models keep putting the value in `adaPerUtxoByte` in every era, as before.
- **Indefinite-length headers: open for the maintainer.** Yano's header rules follow `6176e413`, where cardano-ledger
  `3b0a49bc90` (2026-07-27, in `cardano-protocol-0.2.0.0`) reads the header and its body with `decodeRecordNamed`,
  which accepts either length. The dataset marks 57 indefinite headers valid on that basis. Released nodes (10.6.x,
  10.7.0) still decode headers in ouroboros-consensus `Praos/Header.hs` with `RecD`, which is definite only, as is the
  pin `f649f975`. So Yano accepts a header that deployed nodes reject. The pre-ADR reader did too, as it could not tell
  the two lengths apart. Going definite-only costs those 57 samples in the report until a node release carries the
  change.
- **Yaci deprecates entry points, not classes.** The serializer classes are now the mappers, so only their cbor-java
  tree entry points (`deserializeDI(DataItem)` and similar) are deprecated for removal under Q5. The `byte[]` readers
  read source bytes and stay. `BlockHeaderSerializer.deserialize*` stays, as chain sync and block fetch use it.
- **CCL models read in Conway.** `RawModels.READ_IN` pins the era the models read with. M7 must decide what it becomes.

**Corrections to this ADR.**

- D2: `plutus` also depends on `ledger-cbor`, since `Redeemer`, `ExUnits`, `ExUnitPrices` and `CostMdls` live there.
- D3: `RecordSpec.replacedIn` only hides an older row once a newer row of the same key is valid. So a key that gains a
  meaning later can still be read under the new meaning in an earlier era (`coinsPerUtxoByte()` returns an Alonzo word
  value). "A key cannot keep its meaning" is enforced in one direction only.
- D5: "no narrowing" and "wrong arity rejects" conflict for certificates. Views have `of` (D5's format) and `lenient()`
  (the models' existing leniency), and Yaci adds `tolerant` readers for what a chain follower must keep.
- D5: the reader also checks the tags of rationals and tagged language strings (`checkTags`), as the trees did.
- D6: `RawTx.walk` is static, because `RawTx.of` already rejects repeated keys the walk must report. A record whose
  shape depends on the container's major type (`BY_MAJOR_TYPE`: value, output, auxiliary data, redeemers) reports the
  row it picked through `entry`, with a null key, so a policy can reject an out-of-era shape.
- D7: re-encoding a tree is not lossless. A tree has already lost some encoding facts, and a `PlutusData` map that lost
  pairs keeps its mark only on the bytes path. Some overloads now throw `CborRuntimeException` where they threw
  `IllegalArgumentException` or `ClassCastException`; `ProtocolParamUpdate`, `Update` and `Nonce` keep throwing a
  runtime exception, as their tests pin.
- D8: "a witness or auxiliary data failure does not fail the block" held only for stack exhaustion and JSON failures;
  structural failures failed the block. Per-item isolation now replaces the whole-block retry, and the count-mismatch
  log is gone. "The datum duplicate rule" is really the Conway redeemer map rule: first position, last value.
  Auxiliary data CBOR is exposed when the body has no hash, and hidden only for an index past the bodies. I12's
  `onParsingError` applies to block fetch; local chain sync logs and returns null. `LocalRollForwardSerializer` also
  calls `BlockSerializer`. Trailing bytes after the block need `CborSpan.skip`, and a damaged block needs prefix
  navigation (`BlockEnvelope.headerOf`) so that its number reaches `onParsingError`.
- D8's corrections list also needs `costModels`' per-language values, which were hex of a re-encoding.

**Differences for the maintainer's approval** (I5's list). None occurs on real chain data; each is pinned by a test.

- **Writers in `common`.** An empty indefinite map is written `bfff` (it was `bf`, missing its break). Tag 2^64-1 is
  written in full. Tags are no longer written on cbor-java's shared `null`, `true`, `false`, `undefined` and break
  values, which another decoder can tag for every user of the JVM.
- **Yaci, reading.**
  - Not-well-formed input is rejected; canonical map order is by unsigned bytes; indefinite arrays in blocks are
    accepted (yaci#113). The frame decoder keeps a not-well-formed message buffered rather than dropping it.
  - Wraparound is now rejected where the old code wrapped through `int`: relay, credential, certificate, MIR pot and
    DRep tags, port, retirement epoch, input index, network id, redeemer tag, gov action index, voter and vote tags,
    Word16 fields.
  - Rejected for wrong arity or type: certificates, protocol version, ex units, thresholds, updates, parameter change or
    treasury withdrawal without a policy hash, nonce with another tag, null proposal anchor, negative or bignum
    naturals, bignum or byte string cost-model language keys, a tagged null.
  - Indefinite-length update, proposal procedure, gov action id, voting procedure, constitution and ex unit prices are
    rejected, as the ledger does below protocol version 12 (343 invalid dataset samples). From version 12 the ledger
    accepts indefinite records, so a tolerant reading without the definite-length check will then be needed.
  - Widened: `minFeeA`, `minFeeB` or a minor version that is not a number reads as null where it threw; a nonce with
    extra items is read; chunked text split inside a character reads as that character.
  - Kept as before, correction proposed: the update epoch, a hard fork's minor version, 32-bit parameter fields and
    committee terms keep their low 32 bits. 22 valid dataset samples carry a minor version of 2^31 or more.
  - Witness Plutus script content is the source encoding (differs only for non-canonical input). A native script hash
    is present where the old hash walk failed.
  - The metadata of preview slot 127055999 still gives no JSON, with another error message.
- **CCL models.** A tree overload (`TransactionWitnessSet.deserialize(Map)`, `Redeemer` and others) loses a
  `PlutusData` map's lost-pairs mark, which the bytes path keeps. Exception classes change as in D7 above.
- **CCL views (pre-release API).** The raw package moves to `cardano-client-ledger-cbor`; `RawDatum.toPlutusData` and
  `RawScript.toScript` move to `RawModels` (Q2); `RawBlock.of(bytes, era)` is replaced by `readAs`; `RawRelay`'s
  optional items are `Optional`; `CborSpan.items()` and `entries()` return the same read-only list on each call; strict
  coin reads reject negative numbers and bignums, as the ledger's `Coin` does.
- **Yano.** Gains three rejections (`block_number` and `kes_period` negative, repeated pool owners) and loses none. The
  64 indefinite Allegra auxiliary data arrays the dataset calls valid stay rejected: `decodeAllegra` is a RecD below
  version 12. An empty parameter update decodes, as in the ledger, where `ppuWellFormed` is a GOV rule.

**Left open.**

- Yano does not enforce cardano-ledger's `isMultiAssetSmallEnough` on output values or mint (bloxbean/yano#238). This
  predates the ADR and was found in review.
- Yaci: `BlockSerializer.deserialize` wraps only CCL's `CborRuntimeException` (and stack exhaustion); another runtime
  exception escapes as it is, and the agents catch it. A certificate array of fewer than two items still reads as a
  stake registration, as before.

- CCL's `crypto`, `crypto-ext`, `cip8`, `cip30` and `merkle` modules still decode with cbor-java's decoder, which can
  tag its shared simple values for the whole JVM. The reader paths no longer do.
- Yaci's `UtxoByAddressQuery` caller decoder is unchecked, and `deserializeDI(di, txBytes)` ignored `di` (now
  deprecated).

## 8. Compatibility

| Item | Impact |
|---|---|
| CCL model classes and signatures | None |
| `Transaction.deserialize` results | Same, by I5 and I6 |
| Yaci models and listeners | None |
| `BlockSerializer` | Same class. Same result, including the null era for an unknown era (I12), except the differences listed in D8, pending Q6 |
| `HeaderBody.blockHash` | Same on minimally encoded headers. Corrected to the hash of the original bytes otherwise, with a release note (D8) |
| Yaci's inner serializers | Deprecated at M6, removed after one major release or two minor releases (Q5) |
| `transaction.raw` | Pre-release API. Same package in a new artifact, which arrives transitively. `RawDatum.toPlutusData` and `RawScript.toScript` become helpers (Q2) |
| `EraRules` | Package-private, so no impact |
| `RawBlock.of(bytes)` | Same. New `of(BlockEnvelope)` and `readAs(BlockEnvelope, Era)`. A new exception type for unknown eras extends the current one |
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
| Keep the reader in `transaction-spec` | Works, but only review stops a view importing a model. Rejected in Q1 |
| Runtime flag choosing old or new parsers | Keeps both parsers alive and doubles test paths (D8) |
| Move Byron into CCL | Yaci is the only reader and no one else needs it. No duplication to remove |

## 10. Risks

| Risk | Mitigation |
|---|---|
| A reader bug affects CCL, Yaci and Yano together, as they no longer differ | Dataset, I5 and I6 before each release; G3 on real chains. I7 checks the policy alone, sample by sample. This risk does not go away, and we accept it in exchange for bugs being fixed once |
| Sync breaks on an old block | Lazy reads, the G3 full replay per milestone, and Yaci's `onParsingError` stays. One milestone at a time, so a failure has a small search space |
| Mappers change results subtly, such as a null against an empty list | I6 compares models for equality, and only D8's approved corrections may differ. `equals` must cover the fields, and Yaci's models are Lombok `@EqualsAndHashCode`. Models without it get compared by JSON |
| Tables grow beyond what is readable | Pilot judges this first. Only records and unions use tables; recursive and one-off shapes stay as code |
| Slower sync, with lazy views over spans in place of a tree | I10. It may equally turn out faster, but this has not been measured |
| The work stalls half way, leaving four readers | Each milestone is complete on its own, and old code for a migrated area is deleted in that milestone or M6 |
| Yaci reads a future era with the newest tables and misreads a key that changed meaning | Same exposure as today (D4). `Block.era` stays null, so a listener can tell. M7 adds the era properly |
| Dijkstra changes before it freezes | Only used as a design check here. M7 waits for the freeze |
| Yano's existing strictness is lost while moving to a policy | I7 at each step, per sample: the missed samples can only shrink and the strict side's rejections can only grow, so a loss cannot hide behind a gain or behind CCL's decoder. I13 covers structural rules |

## 11. Open questions

The maintainer decided Q1 to Q5 on 2026-11-05, and they stay here for the record. Q6 was implemented as (a), which
the maintainer accepted on 2026-11-06 (section 7.1).

**Q1. Module name and whether to have a module.** Options were (a) `ledger-cbor`; (b) `cbor`, which is shorter but
suggests general CBOR, which stays in `common`; (c) no module, a package in `transaction-spec` guarded by a test.
**Decided: (a).**

**Q2. Pre-release API change.** `RawDatum.toPlutusData()` and `RawScript.toScript()` cannot move with their classes.
Options were (a) static helpers in `transaction-spec`, with a release note, since these shipped only in pre-releases;
(b) keep the classes in `transaction-spec` and have new views in a new package, leaving two sets of views.
**Decided: (a).**

**Q3. The 10% speed limit in I10.** It is a proposed figure with no measurement behind it. **Decided: record the M0
baseline first, then confirm or change it.** This is part of M0's exit.

**Q4. When to run M1 relative to ADR 0002's other Yaci work.** Options were (a) M0 first, as written; (b) start the
pilot on a branch now against the stack. **Decided: (a),** as a baseline measured against unreleased code would have to
be redone.

**Q5. Yaci serializer removal timing.** The plan deprecates at M6 and removes later, and Yaci's outside users cannot be
seen. **Decided: deprecated classes stay until one major release or two minor releases have shipped with the
deprecation in place.**

**Q6. Yaci's known differences (D8), to decide at M2.** A block whose shape contradicts its era's row, or which has
mismatched counts or bad indexes, is delivered today and would raise `onParsingError`. Options: (a) accept this as a
listed compatibility change, with a release note; (b) add a structural `RawBlock` entry point without these checks, for
Yaci, so nothing narrows. Lean: (a), if G3 finds no real block affected, because a listener then never gets a block that a
conforming node would not send. (b) is the answer if G3 finds any. **Implemented: (a).** G3 and the full-chain streams
found no real block affected.

## 12. Related findings (out of scope)

- ADR 0001's status line and section 7 are stale, as ADR 0002 section 1.5 notes.
- Yaci's `Era` has no constant for later eras, and `EraUtil.getEra` returns null for them, so `Block.era` can be null.
- The dataset carries Dijkstra samples that no Java reader in these projects covers yet.
- In `CertificateSerializer`, a genesis delegation that fails to parse prints a stack trace and returns an empty
  certificate. M1's regression tests have to pin this, and a separate decision should remove it.

## 13. Revision history

- **r1** (2026-11-05): initial proposal.
- **r2** (2026-11-05; responds to the automated Aster review of `38316c73` and the maintainer's decisions): unknown era
  in Yaci → new `RawBlock.readAs` and `EraSpec.newest()` in D4, Yaci's dispatch table, new I12, D8's list and known
  differences, M2 exit, Compatibility, Risks; Q1-Q5 decided, with M0's exit, D8 and Compatibility following them.
- **r3** (2026-11-05; responds to reviewer1's rounds on `38316c73` and `dc6cefe5`): F1 → D8's four inputs as M2
  regressions, Q6 opened for the differences; F2 → D8 corrections list, I6 exceptions with expected values, M2, M5,
  Compatibility, Risks; F3 → D6's walk contract, required flag in D3, new I13, M1's keyed-record proof and fall back
  condition; F4 → I7 per sample on the strict side, M0 baseline, M6, Risks.
- **Status change** (2026-11-05): Accepted by the maintainer, after reviewer1's design approval of r3 at `97a35678`. No
  other text changed. Q6 and the `costModelsHash` decision stay open for M2 and M5.
- **r4** (2026-11-06; implementation of M0 to M6): new section 7.1 records the gates, I10, the decisions taken during
  implementation, corrections to D2, D3, D5, D6, D7 and D8, the differences for approval, and what is left open; status
  line and the note under it; Q6 implemented as (a). The design text above section 7.1 is unchanged.
