# ADR 0003: One Era-Aware Ledger CBOR Reader

**Status**: Accepted. M0 to M6 are implemented (section 7.1). r6 splits M7: M7a, experimental Dijkstra and Leios
reading (D4a), is implemented in CCL (section 7.2). r9 decides Q7: Yaci's models gain Dijkstra's fields in M7c
(D4b), and M7c is implemented (section 7.3). M7b and M8 are not started
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
this design. r6 adds D4a and M7a, which replace D4's Dijkstra example. Code snippets in the decisions are illustrative.

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
- Dijkstra's ledger rules, and writing Dijkstra. Reading Dijkstra is in scope from r6, marked experimental until the
  era is final (D4a, M7a). CCL's Dijkstra models and writers are ADR 0004's subject.
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
| cardano-ledger, Dijkstra (r6) | `95b369053fd32422695a81fb7d5289d6dd35eee2`, cardano-ledger-dijkstra 0.5.0.0. Its Dijkstra CDDL and codecs equal `54e24ba4` (0.4.0.0), which cardano-node 11.2.0 ships; in the libraries both use, the only codec difference is the IPv4 relay byte order. `eb_references` from master `9d20aed5ebeca80c89d8882bd9e58c620a0cff92`, its only CDDL addition |
| ouroboros-consensus (r6) | 5.2.0.0, `952b7020172dd7ab135b66e736da070309b47909`, the version cardano-node 11.2.0 uses. Envelopes and the Dijkstra header shape |
| cardano-cbor-dataset (r6) | `d4709b12`, as pinned for the report; CCL's gates move to `4fd977b2` in r7, whose Dijkstra files are the same (section 7.2). Its Dijkstra samples were generated with cardano-ledger `6176e413` (2026-09-01), before the block, header and several rules changed; D4a lists which rules are still valid at the pin |
| Dijkstra test vectors (r6) | cardano-ledger `95b36905` and master `7827506b` goldens; ouroboros-consensus `69d53918` goldens (ledger `b21c518e`); pallas `4b76c2e8` `test_data/dijkstra-fixtures.md` (Musashi, `prototype-2026w36`, ledger `1587f21a`; rc-2026w42 transactions); gouroboros v0.217.1 (`3032c7c9`) test data (DijkstraNet, cardano-node 11.2.0; `rc-2026w41` devnet; Musashi); KleioScan API, fetched 2026-11-06. The fixture file records each item's source |

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
| Dijkstra (r6, D4a) | `UNIFIED` | `NESTED_10` or `NESTED_12`, by shape |

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
| 2 to 7 (r6: 2 to 8, D4a) | `RawBlock.of(envelope)` | `Block.era` set |
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
check when the era is frozen (M7). Until then, Dijkstra stays rejected, as ADR 0001 decided. (r6: this example was
checked against `f649f975`. At the current pin the header and block body differ, and D4a replaces it.)

### D4a. Dijkstra and Leios, read as experimental (r6)

Leios work is starting in Yaci, and it builds on Dijkstra's block, header and transaction formats. Reading them in
`ledger-cbor` now means that work has one reader to build on, and it tests D3 and D4 on a real era while this ADR is
open. Dijkstra is not final: cardano-node 11.2.0, which carries it, is a pre-release that enables it only behind
`ExperimentalHardForksEnabled`. So everything this decision adds is **experimental**: each public type and method added
for Dijkstra or Leios says so in its Javadoc ("Experimental: Dijkstra is not final; this may change in any release"),
and M7b removes that marking once the era is final.

**Pin.** cardano-ledger `95b36905` (cardano-ledger-dijkstra 0.5.0.0). Its Dijkstra CDDL and codecs equal those of
`54e24ba4` (0.4.0.0), the version cardano-node 11.2.0 builds with. ouroboros-consensus 5.2.0.0 (`952b7020`), the version
11.2.0 uses, decides the envelope and the header shape. Leios endorser-block references come from cardano-ledger master
`9d20aed5`, where they are the only CDDL addition. Section 4 has the full pins. The facts below were read from the code
at these pins, not from the CDDL alone; where the two differ, the code decides, and the table says so.

| Item | At the pin | Reader |
|---|---|---|
| Era | Envelope index 8 for blocks; 7 in the HFC sum for headers and transactions (consensus `Cardano/Node.hs:180`, `Cardano/Block.hs:284-293`) | `Era.Dijkstra(8)` in `common-spec`, after Conway. One `EraSpec` row |
| Block | `[header, block_body]`. `block_body = [transactions, leios_certificate / nil, peras_certificate / nil]`, each transaction `[body, wits, aux / nil, is_valid]`, with no `invalid_transactions` list and no ordering rule (`Dijkstra/Block.hs:27-42`, `BlockBody/Internal.hs:184-208`, `Tx.hs:175-182`) | New block layout `UNIFIED`. `txCount()`, `tx(i)`, `txBytes(i)`, `bodyHash()` work as for the parallel layouts. `invalidTxIndexes()` lists the positions whose flag is false. `invalidTransactions()` is empty, and `transactionBodies()`, `transactionWitnessSets()` and `auxiliaryDataSet()` throw, as D4 already says |
| Body hash | Blake2b-256 of the whole `block_body`'s original bytes (`BlockBody/Internal.hs:120`). The CDDL's "merkle triple root" comment is a leftover | `bodyHash()` hashes the body span (I8) |
| Header | Consensus 5.2.0.0 uses the Praos header for Dijkstra, the 10-item `header_body` of Babbage and Conway, and reads its `protocol_version` pair as `header_version_info`, `[highest supported major, software tag]` (`HFEras.hs:69, 97-120`). The CDDL's 12-item `header_body` is the ledger's Leios header (`cardano-protocol`, `Leios/BlockHeader.hs:217-246`), which no released node sends; Leios prototype networks do. Item 9 has the same shape in both | The Dijkstra row allows `NESTED_10` and a new `NESTED_12`, whose items 10 and 11 are `block_body_contains_leios_cert` and `eb_references_announcement / nil`. A header is read in the layout of its era with as many items as it has, so a 12-item header is still refused in Conway. `protocolMajor()` and `protocolMinor()` read item 9 in every layout, and their Javadoc says that in Dijkstra the pair is the highest supported major version and the software tag. New `leiosCertInBody()` and `ebReferencesAnnouncement()` (hash, size) are present only in `NESTED_12`. The header hash stays the hash of the original bytes |
| Transaction | Submitted as `[body, wits, aux / nil]`, or the legacy `[body, wits, true, aux / nil]`; `false` fails. Relayed and sent in the 3-item form. In a block, `[body, wits, aux / nil, is_valid]` (`Tx.hs:147-182, 500-533`) | `RawTx.of` and `RawTx.lenient`, which the models read through, read the three forms. The 4-item forms are told apart by which item is a bool, as auxiliary data never is. `isValid()` gives the flag in both. `false` in a submitted transaction is a rule, so it is policy (D6). The walk picks a table by arity and flag position: the 3-item form has rows to Mary and a second table from Dijkstra on, since `FieldSpec` holds one era range; the flag-third form from Alonzo on; the flag-last form from Dijkstra on. `tx(i)` and `txBytes(i)` of a Dijkstra block give the 3-item form from the original spans |
| Body key 14 | `guards = nonempty_set<addr_keyhash> / nonempty_oset<credential>`. The decoder picks the form from the first element; mixing fails; empty is accepted (`TxBody.hs:1659-1679`) | Row `GUARDS` from Dijkstra; `RawTxBody.guards()` returns credentials, a key hash read as a key credential. `requiredSigners()` is empty in Dijkstra, as `replacedIn` already does |
| Body keys 23-27 | `sub_transactions`, `required_top_level_guards`, `direct_deposits`, `account_balance_intervals`, `starting_account_balance_intervals`. An interval is a coin or `[lower / nil, upper / nil]`, and both bounds nil fails (`Dijkstra/Scripts.hs:684-696`) | Five rows and their accessors. Both bounds nil is read, and is policy |
| Sub-transactions | `[sub_body, wits, aux / nil]`, with no flag. A sub body is the body without keys 2, 13, 16, 17, 23 and 27, which Haskell rejects as unknown keys; it requires keys 0 and 1 only (`TxBody.hs:362-480`). A sub-transaction's id is the hash of its body | The body table gains a top-level-only column, and the sub body is a second `RecordSpec` over the other rows, with the fee not required. So a walk reports a top-level-only key in a sub body as unknown, and a policy rejects it as Haskell does without naming a key. Each sub-transaction is a `RawTx` that reads and walks its body with that table |
| Witness set | Key 8 is Plutus V4. Redeemers are a map only: the array form fails at decode from version 12. Redeemer tag 6 is guarding (`Alonzo/TxWits.hs:566-666`, `Scripts.hs:173-182`) | Row `PLUTUS_V4_SCRIPTS` from Dijkstra. The array redeemer row ends at Conway, so a policy can reject it |
| Scripts | `script_ref` type 4 is Plutus V4; native script type 6 is `script_require_guard`; auxiliary data `#6.259` key 5 is Plutus V4 | `RawScript` accepts type 4; `AuxDataField` row for key 5. Native scripts stay one leaf (D3) |
| Certificates | Tags 2, 3, 4 and 7-18. Tags 0, 1, 5 and 6 fail (`Dijkstra/TxCert.hs:239-250`). Pool parameters take an optional BLS key after the VRF key hash: 9 fields, or 10 when the slot holds a BLS key or null (`StakePool.hs:686-757`) | `CertKind` already bounds 0, 1, 5 and 6. Pool registration picks one of two records by arity, as the transaction forms do |
| Parameter update | Keys 34-56 are new; 53 is `nil / uint` (`Dijkstra/PParams.hs:741-796, 806-876, 880-1123`) | `ProtocolParamField` rows from Dijkstra |
| Leios certificate | `[signers : bytes .size (0..8192), signature : bytes .size 48]`. Not checked by BBODY at the pin | `RawBlock.leiosCertificate()`, an optional view of the two byte strings |
| Peras certificate | Plain bytes. Validation always fails at the pin, so a valid block has nil (`BlockBody/Internal.hs:243-246`) | `RawBlock.perasCertificate()`, an optional span |
| Endorser-block references (master) | `nonempty_omap<hash32, uint .size 4>`: wire order kept, duplicate keys fail, empty fails; hashed over its original bytes (`Block/Endorser.hs:57-79`). The keys are a whole-transaction hash whose definition changed between the pin and master (the four component hashes at `Tx.hs:444-470`; the block form's bytes in master's golden `hashtx.hex`) | `RawEbReferences.of(bytes)`: entries in wire order and `hash()`. The transaction hash is left to the Leios work until it settles |

**What is not the reader's.** Decoding at version 12 changes rules as well as shapes: chunked byte strings and
indefinite records are accepted; duplicate key witnesses, bootstrap witnesses, native scripts and datums fail; a
bootstrap chain code must have 32 bytes; the outer multi-asset map must be non-empty; tag 30 becomes mandatory on unit
intervals; array redeemers and both-nil intervals fail. These are decoder-version rules, so they belong to a policy at
version 12 (D6). Yano adds that policy when it adds Dijkstra (M7b). The reader already accepts every shape involved (D5).

**`EraSpec.newest()` moves to Dijkstra.** Every caller that used it to mean "the current era" means Conway today, and
names Conway instead, so its result does not change (I14):

- CCL: `RawHeader.walk(span, policy)`, which Yano's header policy calls, and `RawTx`'s era-less `redeemers()`,
  `scripts()`, `witnessDatums()` and `auxScripts()`. `RawHeader.walk` gains an overload with an era, which Dijkstra
  headers use. A Dijkstra caller of the others reads the witness set or auxiliary data with its era.
- Yaci: the six deprecated era-less serializer entry points.

Only Yaci's fallback for an unknown index, 9 or more, keeps `newest()`, and that is what D4 intends: it reads with
Dijkstra's tables. A Conway-shaped block at index 9 or more then fails to parse and raises `onParsingError`, where it
was a populated `Block` with a null era; a Dijkstra-shaped one is the populated `Block`. I12 is re-pinned to that
(section 8).

**No switch.** A Dijkstra block is read only when its envelope says Dijkstra, and that index appears only after a hard
fork to version 12. A caller that reads a transaction names the era, as D3 already requires. Writing Dijkstra needs an
explicit era as well, which is ADR 0004's subject.

**Yaci.** `Era.Dijkstra(8)` and `EraUtil`; D4's dispatch table routes 2 to 8 to `RawBlock.of`. `BlockSerializer`
reads a `UNIFIED` block through `tx(i)`, and takes invalid transactions from `invalidTxIndexes()` in every era, with
null only before Alonzo as today, so a Dijkstra transaction whose flag is false is reported. The header mapper is
unchanged: `ProtocolVersion` carries the version pair, which in Dijkstra is the highest supported major version and
the software tag. `TxBodyType` gains Dijkstra (7) for submission, and `TxCborUtil` takes Dijkstra transactions from
`txBytes(i)`. Dijkstra-only fields are not added to Yaci's models here (Q7): listeners reach them through the
transaction's CBOR and CCL's views. r9: they are added in M7c (D4b).

**Yano.** Unchanged. `HaskellPolicy.forEra` keeps refusing Dijkstra, and Yano's header policy keeps refusing a 12-item
header (I14).

**Test vectors (I15).** Each source is pinned in section 4, and none was produced by running a node or a tool here.
Expected values are checked against values this code did not compute, each named by what it hashes:

- **cardano-cbor-dataset** `d4709b12`: the samples of the 87 rules whose definition, with every rule it refers to, is
  unchanged between `6176e413` and the pin (10,759 valid, 20,551 invalid), and the samples of 7 changed rules that
  provably reach no changed part (965 valid, 2,747 invalid). Block, block body, header, header body and account
  balance intervals have none. The dataset has no licence, so it is fetched, never vendored, as for Conway.
- **cardano-ledger** at the pin: the golden Dijkstra transaction (a sub-transaction, Plutus V4 witnesses, a script
  credential guard, reward-account-keyed intervals, pool registrations with and without a BLS key) and the Leios
  certificate goldens; master's 1,000-signer certificate.
- **ouroboros-consensus** `69d53918` goldens, which pin ledger `b21c518e`: a 12-item Dijkstra header and a transaction
  with its id.
- **Real networks, all Haskell nodes:** the first DijkstraNet block and header from a cardano-node 11.2.0 relay (10-item
  header; its block hash and previous hash as gouroboros v0.217.1 records them); 15 Musashi blocks (ouroboros-leios
  `prototype-2026w36`, ledger `1587f21a`, 12-item headers, three with Leios certificates), each with the header hash
  the node's own index records; two Leios devnet blocks (`rc-2026w41`), one certifying the other; three endorser-block
  references, each with the hash and size its announcing header carries; transactions with sub-transactions and Plutus
  V4 witnesses, each with its id (Blake2b-256 of the body). From pallas `4b76c2e8` and gouroboros v0.217.1 test data
  (Apache-2.0) and the KleioScan explorer. `1587f21a`'s CDDL differs from the pin only by additions.
- **Not used:** fixtures that a third-party library built by hand, such as pallas's proposal and script transactions,
  and the ledger's whole-transaction `hashtx.hex`, which is not a transaction id.

The Apache-2.0 files and the chain data go into the test resources with a file recording each one's source; the
dataset stays fetched.

Not covered by any vector: a non-nil Peras certificate, real parameter updates with keys 40-56, guards with a script
credential on a real network, and rejection cases for the block, header and interval rules. Those rules are tested
only for acceptance.

### D4b. Dijkstra fields in Yaci's models (r9)

Q7 is decided as (a): Yaci's models gain Dijkstra's fields now, in M7c, so that the Leios work and a later Dijkstra
network test start from them. Everything added is experimental, as in D4a, and carries the same Javadoc.

**Source of the values.** Every new field is mapped from a CCL view that M7a already reads (D8: Yaci adds no format
knowledge). Read in an era before Dijkstra, those views return empty, so a Conway block maps as before.

| Yaci model | New field | Type | From |
|---|---|---|---|
| `TransactionBody` | `guards` (key 14 in Dijkstra) | `List<Credential>`, in encoded order | `RawTxBody.guards()` |
| | `subTransactions` (23) | `List<SubTransaction>` | `RawTxBody.subTransactions()` |
| | `requiredTopLevelGuards` (24) | `List<TopLevelGuard>` | `RawTxBody.requiredTopLevelGuards()` |
| | `directDeposits` (25) | `Map<String, BigInteger>`, keyed as `withdrawals` is | `RawTxBody.directDeposits()` |
| | `accountBalanceIntervals` (26), `startingAccountBalanceIntervals` (27) | `Map<String, AccountBalanceInterval>`, keyed as `withdrawals` is | `RawTxBody.accountBalanceIntervals()`, `startingAccountBalanceIntervals()` |
| `Witnesses` | `plutusV4Scripts` (8) | `List<PlutusScript>` | `RawWitnessSet.plutusV4Scripts()` |
| `AuxData` | `plutusV4Scripts` (5) | `List<PlutusScript>` | `RawAuxData.plutusV4Scripts()` |
| `PoolParams` | `blsKey` | `BlsKey` | `RawCertificate.blsKey()` |
| `ProtocolParamUpdate` | keys 34-56, one field each, named after CCL's `ProtocolParamField` rows | Yaci's conventions (below) | `RawProtocolParamUpdate` |
| `Block` | `leiosCertificate`, `perasCertificate` | `LeiosCertificate`; the Peras certificate's bytes as hex | `RawBlock.leiosCertificate()`, `perasCertificate()` |
| `HeaderBody` | `leiosCertInBody`, `ebAnnouncement` (12-item header only) | `Boolean`; `EbAnnouncement` | `RawHeader.leiosCertInBody()`, `ebReferencesAnnouncement()` |

**New small models.** Each is a Lombok value class, like its neighbours.
- `SubTransaction`: `body` (a `TransactionBody`), `witnesses` (a `Witnesses`) and `auxData` (an `AuxData`, or null).
  - Its id is `body.txHash`, the hash of the body's original bytes (I8), so there is no second copy.
  - A sub-transaction body has none of keys 2, 13, 16, 17, 23 and 27 (ADR 0004 D2). So `fee`, `collateralInputs`,
    `collateralReturn`, `totalCollateral`, `subTransactions` and `startingAccountBalanceIntervals` are null in it.
- `TopLevelGuard`: `credential`, and `datum` (Yaci's `Datum`, or null). The datum is built from the entry's source bytes,
  as witness datums are (`DataItemIsolation`), so its hash follows I8.
- `AccountBalanceInterval`: either `exact`, or `inclusiveLower` and `exclusiveUpper`, where either bound may be null.
  The names follow Haskell's `Inclusive Coin` and `Exclusive Coin` (`Dijkstra/Scripts.hs:670-674`) and CCL's model.
- `BlsKey`: `publicKey` and `proofOfPossession`, as hex.
- `LeiosCertificate`: `signers` and `signature`, as hex.
- `EbAnnouncement`: `hash` (hex) and `size`.

**Parameter types follow Yaci's model, not CCL's.**

| Keys | Haskell type | Yaci type |
|---|---|---|
| 39, 44, 54 | unit interval | Yaci `UnitInterval` |
| 37, 50, and 38's value | positive or non-negative interval | `NonNegativeInterval` (the type `priceMem` uses) |
| Word32 keys: 34-36, 40-42, 45, 46, 48, 49 and 53's value | `Word32`; 36 is `NonZero Word32`, and 40-42 are `Milliseconds32`, a Word32 (`PParams.hs:706`) | `Long`, with no narrowing: `UpdateSerializer`'s 32-bit cast (`low32`) is kept for the old keys only, as an `Integer` would turn 4294967295 into -1 |
| Word16 keys: 43, 51, 52 | `Word16` | `Integer` |
| 55, 56 | coin | `BigInteger` |
| 47 | ex-units | split into `maxEndorserBlockExMem` and `maxEndorserBlockExSteps`, as 20 and 21 are |

**Enumerations and native scripts.**
- New constants: `PlutusScriptType.PlutusScriptV4`, `RedeemerTag.Guarding(6)` (JSON `guarding`), and
  `NativeScriptType.REQUIRE_GUARD = 6`. The redeemer mapper maps tag 6, which it leaves null today.
- A native script of type 6 gets JSON: `{"type": "guard", "credential": {...}}`, with the credential written as Yaci's
  `Credential` is.
- Where the name comes from: Yaci's native-script JSON uses cardano-cli's names (`sig`, `all`, `any`, `atLeast`, `after`,
  `before`), not the ledger's (`TimeStart`, `TimeExpire`: `Allegra/Scripts.hs:311-318`). cardano-cli has no name for the
  guard. So Yaci takes the ledger's kind `Guard` (`Dijkstra/Scripts.hs:385-390`) in its own lower-case style. CCL's
  `RequireGuard` has no JSON form to reuse.
- These mappings take no era, as today:
  - A type-6 script in an earlier era's block was kept with a parse error (section 7.2); it now gets the guard JSON.
  - A redeemer tag 6 in an earlier era was null; it is now `Guarding`.

  Both inputs are invalid before Dijkstra. Both are listed differences, each with a regression test.

**Absent and empty.**
- Each new field is null when the field is absent, and carries `@JsonInclude(NON_NULL)`. This keeps Conway JSON
  unchanged.
- Only `guards` can be present and empty in Haskell: `decodeGuards` has no `failOnNull`. Key 23 is a non-empty set, and
  24 to 27 use `failOnNull` (`Dijkstra/TxBody.hs:438-470`).
- CCL reads 23 to 27 tolerantly and returns an empty list for both absent and empty. Yaci maps both to null. Since a
  present empty value is invalid, nothing a valid chain carries is lost.
- `Witnesses` is the exception. Every list there is empty, never null, when absent, and Haskell rejects an empty key 8
  (`scriptDecoderV9`). So `plutusV4Scripts` follows its neighbours: an empty list, with `@JsonInclude(NON_EMPTY)` to keep
  Conway JSON unchanged.
- `AuxData`'s script lists are null when absent, and `plutusV4Scripts` follows them.

**`requiredSigners` in Dijkstra.** Haskell keeps required signers as a derived view of the guards:
`reqSignerHashesTxBodyG` is the key hashes of the key-credential guards (`Dijkstra/TxBody.hs:1417-1424`), and UTXOW
needs their VKey witnesses through it (`Dijkstra/UTxO.hs:160`, `getConwayWitsVKeyNeeded`).
- The mapper does the same. For a Dijkstra body, `requiredSigners` is the set of hex key hashes of the key guards. It is
  null when key 14 is absent, and an empty set when the guards hold no key credential, as a Conway body's present but
  empty key 14 gives today.
- `guards` keeps the full ordered list, scripts included.
- `RawTxBody.requiredSigners()` stays empty in Dijkstra, as the view reads key 14's own meaning.
- Yaci Store stores and serves this field (`TransactionProcessor`, the Blockfrost `required_signers` endpoint), so
  leaving it null would silently empty it for every Dijkstra transaction.

**Parameter keys 38 and 53.** These have three states (ADR 0004 D2): absent, nil, or a value.
- Each is an `Optional`: null when absent, empty when nil, and present with a value.
- The model must round-trip through any `ObjectMapper`, with or without Jackson's JDK 8 module. Yaci Store reads Yaci
  models with a bare `new ObjectMapper()` (`RemoteEventConsumer`).
- CCL's `NilOrValueJsonSerializer` therefore becomes public. It gains a matching `NilOrValueJsonDeserializer`: a JSON
  null gives an empty `Optional`, a value gives `Optional.of` it, and a missing field stays null.
- Both are field annotations, used by CCL's model and by Yaci's two fields. Tests round-trip all three states through a
  bare mapper and through one with the JDK 8 module, with key 38's interval and key 53's number populated in both
  models.
- CCL's `Rational`, key 38's value type, has no constructor Jackson can use. Its existing constructor gains
  `@JsonCreator`, with `@JsonProperty("numerator")` and `@JsonProperty("denominator")` on its arguments, which is
  additive.
- Two details in the deserializer matter:
  - It overrides `getNullValue` to return an empty `Optional`. Jackson routes a JSON null there, not to `deserialize`,
    so without the override nil would become absent.
  - It is a `ContextualDeserializer`, which takes the content type from the property. One class then serves
    `Optional<Rational>`, `Optional<Long>`, `Optional<NonNegativeInterval>` and `Optional<Integer>`.

**Writers.** Yaci's builder of transactions stays out of scope, but a Yaci writer of a changed model must not silently
drop a new field (ADR 0004 D1).
- `UpdateSerializer.serializePPUpdate` writes keys 34 to 56, as the key-by-key inverse of `read`. `GovActionSerializer`
  calls it, and Yano persists proposals through that path for enactment (`GovernanceCborCodec`).
- That is the only Yaci writer that builds a changed model's bytes from its fields. The other `serialize` and
  `serializeDI` overrides in `core` are on classes that gain nothing (`Credential`, `StakeCredential`, `ExUnits`,
  `GovActionId`). `TxCborUtil` writes from the `cbor` fields byte for byte (section 7.2), so it is unaffected.
- Reading back what this writer wrote needs the era. `GovActionSerializer.deserialize(bytes)` reads as Conway, so CCL's
  view drops keys 34 to 56 there (`RawProtocolParamUpdate.since`). Yaci adds `deserialize(bytes, era)`; the era-less
  method keeps reading as Conway. Yano's `GovernanceCborCodec` reloads proposals era-less, so passing the proposal's era
  is a Yano prerequisite before Dijkstra (section 8). A Yaci test writes a Dijkstra `ParameterChange` and reads it back
  with its era.
- Tests write and read keys 34 to 56 at the boundaries of their types: 2^31 and 2^32 - 1 for the Word32 keys.

**API compatibility.** This follows ADR 0004's constructor rule.
- A class that gains fields makes its Lombok all-args constructor private, and keeps **every** existing public
  constructor with exactly today's parameters. That is, for example, three for `AuxData` and two for `Witnesses`.
- The only main-code caller in Yano is `YaciBlockDecoder`, which rebuilds a block whose era is null with the old 7-item
  `new Block(...)`. Tests call `new Block(...)` and the 6-argument `new AuxData(...)`. Yano's `new PoolParams(...)` in
  `ledger-rules` is its own record, and Yaci Store's main code calls none of these constructors.
- The old constructors leave the new fields null, except `Witnesses.plutusV4Scripts`, which they set to an empty list
  as its neighbours are. A copy through them drops the new fields, so:
  - `Block` gains `toBuilder`;
  - `YaciBlockDecoder` copies with it, keeping the Leios and Peras fields of an unknown-index Dijkstra block (I12);
  - a test covers that path.
- Builders gain the new setters.
- `equals`, `hashCode` and `toString` include the new fields in classes that already generate them. `PoolParams`, which
  has no `@EqualsAndHashCode`, does not gain one.

**What listeners must still do.** Yaci maps the block; it does not apply ledger rules. Five things are left to
listeners, and are recorded in section 8 as prerequisites before Yano or Yaci Store ingest Dijkstra:
- **Sub-transactions change the UTxO.** When the top-level transaction is phase-2 valid, each sub-transaction's inputs
  are spent and its outputs are created under its own id (`Dijkstra/Rules/SubUtxo.hs:255-262`, from LEDGER through
  SUBLEDGERS). When the top-level transaction is invalid, they do nothing.
  - `helper`'s `Transaction.utxos` keeps listing only the top-level outputs, and its Javadoc says so.
  - Adding sub-transaction outputs there without their spent inputs and the inherited validity would apply half the
    rule. So the whole rule stays with the listener's ledger, where both halves are.
  - The listener reads them from `body.subTransactions[i].body`.
- **Plutus V4 scripts and the guarding purpose.** These are new values for consumers. Yaci Store's script store rejects
  `PlutusScriptV4` (`ScriptUtil`) and skips `Guarding` (`RedeemerDatumMatcher`), and its own `ScriptType` has no V4.
- **The guards in key 14.** These are the authority for script guards. `requiredSigners` carries only the key hashes.
- **Yano's proposal store** must reload a Dijkstra proposal with its era (`GovernanceCborCodec`, through the new
  `GovActionSerializer.deserialize(bytes, era)`).
- **Local-state query results** for Dijkstra parameters keep their Conway shape. Their format is the node's query codec,
  not the ledger CDDL, and M7b revisits it.

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
| Envelope era 6 changed to 9 | Populated, `era` null, 3 bodies and 3 witnesses | Same (r6: `onParsingError`, as index 9 reads with Dijkstra's tables; D4a) | I12 |
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
| I12 | In Yaci, an unknown era index in the newest layout reaches listeners as a populated `Block` with a null era, and one that does not fit the layout raises `onParsingError`. `RawBlock.of` still rejects it | D4, D8 | Regression tests: a valid Conway block with its index changed, written before the code moves, and a two-item block. From r6 the newest layout is Dijkstra's: a Dijkstra block with index 9 is the populated `Block`, and the Conway block with index 9 raises `onParsingError` (D4a) |
| I13 | Yano's body and witness checks for unknown keys, duplicate keys, including differently encoded equal keys, and missing required fields are expressed through the walk with no key number in the policy | D1, D6 | M1's keyed-record proof |
| I14 | Adding Dijkstra changes no outcome for an earlier era: CCL's dataset gates, G3, G5 and G6 give the same results as before M7a | D4a | The existing gates, run before and after; tests that a 12-item header is still refused by Yano's Conway header policy, and that a Conway witness set with an unknown key 8 still gives the same `scripts()` |
| I15 | Every valid Dijkstra test vector reads and walks with no rejection, and every hash and id matches the vector's, compared on the reader's original bytes. Vectors come only from Haskell code or from networks of Haskell nodes, never from this code or a third party's hand-built fixtures; each rule the vectors miss is listed | D4a | M7a's vector suite |
| I16 | (r9) Yaci's Dijkstra fields come only from CCL's views. A block of an earlier era maps to equal models with byte-identical JSON before and after M7c, apart from D4b's two listed era-less mappings (a type-6 native script, redeemer tag 6), and every public constructor and method of a changed Yaci model still exists. Keys 38 and 53 round-trip in all three states through any `ObjectMapper`. Each new field read from a Dijkstra vector equals the vector's value, taken from the vector's own source and not from this code | D4b | Yaci `core` and `helper` suites; a JSON pin of fixtures from every era against the M7a commit; a signature listing against it, as ADR 0004 W2; M7a's vectors mapped to Yaci's models |

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
| M7a | Dijkstra and Leios reading, experimental (D4a). Yaci delivers Dijkstra blocks to listeners | M6; the r6 pin and its test vectors | I2, I3 at the r6 pin, I8, I14, I15; G5 |
| M7b | Dijkstra final: the experimental marking goes, pins move to the release that forks, Yano gains its version-12 policy | Era frozen for a hard fork: a cardano-node release that forks into Dijkstra without `ExperimentalHardForksEnabled` | Dijkstra dataset samples pass at the new pin; report extended |
| M7c | (r9) Dijkstra fields in Yaci's models, experimental (D4b): CCL's nil-or-value JSON codec public, Yaci's models, mappers and `serializePPUpdate`, Yano's `YaciBlockDecoder` copy | M7a | I14, I16; G5 |
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
  (r6: it stays Conway, named explicitly (D4a); ADR 0004 adds reading with a named era.)

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
- Yano's decoder for M7. Haskell decodes an era's blocks and transactions at the era's lowest major version
  (`eraDecoder`, `eraProtVerLow`), not at the current protocol version, so Conway always decodes at 9. Yano picks its
  policy by era (`HaskellPolicy.forEra`): Conway at 9, any other era refused. M7b adds Dijkstra at 12, where a RecD
  record such as auxiliary data `[metadata, scripts]` may be indefinite, and checks the two auxiliary data forms whose
  decoders change at 12: a plain metadata map (`decodeShelleyPv12`) and `#6.259` (`decodeSparseKeyed` in place of
  `SparseKeyed`). Dijkstra's ledger rules in Yano are outside this ADR.

- CCL's `crypto`, `crypto-ext`, `cip8`, `cip30` and `merkle` modules still decode with cbor-java's decoder, which can
  tag its shared simple values for the whole JVM. The reader paths no longer do.
- Yaci's `UtxoByAddressQuery` caller decoder is unchecked, and `deserializeDI(di, txBytes)` ignored `di` (now
  deprecated).

### 7.2 Implementation record: M7a (r7)

M7a is implemented in `ledger-cbor` on CCL branch `feat/ledger-cbor`, built and tested against local snapshots, with no
release. Yaci's part follows in its branch. The code was reviewed in two rounds by a Fable pass and a Codex pass before
commit; their findings are folded in below.

**Gates.**

| Gate | Result |
|---|---|
| I2, I3 | `EraSpecTest` covers `Era.Dijkstra`. `tools/cddl-table-check` matches every table in all seven eras at `95b36905`, with one recorded exception: body key 14 `guards` is `SET`, since Haskell accepts empty guards (`decodeGuards`, no `failOnNull`), where the CDDL says `nonempty_set` |
| I8 | Every Dijkstra hash comes from original bytes: block body, header, transaction and sub-transaction ids, endorser-block references |
| I14 | The Conway gates give the same results before and after at the same dataset pin; the full CCL suite fails the same tests as the base commit, with byte-identical messages. A 12-item header is still refused in Conway; Dijkstra keys in a Conway walk are unknown keys, as before (below) |
| I15 | All 43 vectors pass: 18 blocks (header hash, number, slot, body hash, previous hash, transaction count, Leios certificate), 2 headers, 17 transactions (ids from the body's original bytes, sub-transactions, Plutus V4 scripts), 3 Leios certificates and 3 endorser-block references (hash and size as announced). Each block, header and transaction walks in Dijkstra under a strict test policy that checks every field's shape; dataset: 7,576 valid samples of 50 rules read and walk under the same policy; 7,575 pass and 1 is disputed (below) |

**Decisions taken during implementation.**

- **A key from a later era is unknown.** In a keyed record (body, witness set, auxiliary data map, parameter update),
  a key whose every row starts after the era being walked has no row in that era: the walk reports it as an unknown
  key, with a null field, and does not enter its value, and `RecordSpec.field` returns null for it. This is Haskell's
  own view, "Unknown field key", and keeps every Conway walk as it was before Dijkstra's rows existed. It also applies
  to earlier eras: a Conway key in a Babbage body is now unknown rather than out of era. Tagged unions, positional
  records, records chosen by major type, and maps of like entries such as the redeemer map keep D6's out-of-era
  report. Typed accessors added for Dijkstra are empty before it.
- **The BLS key slot follows Haskell's peek.** With an era from Dijkstra, a pool registration's item after the VRF key
  hash is the BLS slot when it is an array or null (`StakePool.hs:749-757`). Era-less reads, and reads before
  Dijkstra, are unchanged: `RawCertificate.of` still rejects 11 items and `lenient` still ignores a trailing item.
  `RawCertificate.of(span, era)` is added.
- **Version-12 set rows.** Witness keys 0, 1, 2 and 4 are non-empty sets from Dijkstra, and the value's multi-asset map
  is non-empty, as the CDDL and the version-12 decoders say; their earlier rows end at Conway. A position of a
  positional record may now have one row per era range, and `RecordSpec.arity()` counts positions.
- **Dataset pin.** CCL's conformance gates move from `95e9e7c2` to `4fd977b2`, upstream's head, so the Conway and
  Dijkstra gates share one pin. Every Conway file of `95e9e7c2` is byte-identical at `4fd977b2`, which adds 1,437; Dijkstra's files equal `d4709b12`'s. Run at both pins, the 59,076 outcomes the two share are identical, so no sample that passed became a miss. The model gate goes from 2,705 to 2,837 valid samples, all 132 new ones passing; `certificate-outcomes.txt` gains the new samples' lines, among them a valid `unit_interval` with a bignum numerator that `PoolRegistration`'s margin does not read, and two invalid negative ratios it reads, as the models always have (D7)
- **A dataset label disputed.** `dijkstra/transaction_witness_set/manual-valid/bootstrap-witnesses-empty` is labelled
  valid, but witness key 2 is decoded with `decodeNonEmptySetLikeEnforceNoDuplicates` at version 12, both at the pin and
  at the dataset's generation commit `6176e413`. The reader accepts it; the gate's strict policy rejects it and lists
  it as disputed.

**Differences for the maintainer's approval** (I5). None occurs in a Conway dataset sample or a real Conway block.

- `RawTx.of` and `lenient` read `[body, wits, aux / nil, bool]` in any era, where they rejected it; the walk reports
  it with the `block_transaction` table, valid from Dijkstra, so a policy rejects it earlier.
- `RawOutput.scriptRef()` reads `[4, bytes]`, a Plutus V4 script, in any era, where it rejected it; type 5 is still
  rejected. `RawModels.toScript` of a V4 script throws, as there is no V4 model until ADR 0004.
- Era-less `RawHeader.of(span)` reads a 12-item header body; `RawHeader.of(span, Era.Conway)` and
  `RawHeader.walk(span, policy)` still refuse it.
- `RawBlock.of` reads index 8 as Dijkstra, where it threw `UnsupportedEraException`; a Conway-shaped block at index 8
  fails to parse. `RawBlock.readAs(envelope, EraSpec.newest())` reads Dijkstra's layout, so a Conway-shaped block at
  an unknown index fails (I12, re-pinned).
- `EraSpec.newest()` is Dijkstra; `Era.values()` and `Era.fromValue(8)` include it.
- A later era's key in a keyed record is unknown, and `RecordSpec.field` returns null for it (above).
- The array redeemer row ends at Conway; witness keys 0, 1, 2, 4 and the multi-asset map have Dijkstra rows (above).

**Yaci (r8).** On Yaci branch `feat/ledger-cbor-reader`, against CCL `9ada74fe`, reviewed by a Fable pass and a Codex
pass over three rounds. Dijkstra blocks reach listeners with `Block.era` Dijkstra, through block fetch and local chain
sync; the tests check them against the source's recorded header hashes, block numbers and slots, and transaction ids
hashed by an independent Blake2b. Byron to Conway delivery is unchanged: the parallel layouts keep their own path.
Implementation choices:

- A Dijkstra transaction's parts come from `RawBlock.transaction(i)`'s spans, not a `RawTx`, and certificates are read
  with `RawCertificate.lenient(kind, span, era)`, so Dijkstra is read as tolerantly as Conway: a repeated body key
  keeps its last value, and a pool registration's trailing items are ignored.
- `TxCborUtil` writes a Dijkstra transaction's 3-item form from the block model, equal byte for byte to
  `RawBlock.txBytes(i)`, since its caller has no `RawBlock`.
- Chain-sync headers are read in their HFC variant's era, so a 12-item header is refused unless it is Dijkstra's.

Differences for approval, beyond D4a's: a native script of a type Yaci's JSON writer does not know, at the top or
nested, is kept with no JSON and a parse error, keeping its type and hash, in every era, where it was dropped or left
out of its parent's tree; a malformed known script still fails its block as before. Only Dijkstra's type 6 or invalid
data reaches this. A chain-sync header must have its era's number of items (15 to Alonzo, 10 for Babbage and Conway,
10 or 12 for Dijkstra), where any shape was read; real headers are not affected, and a header of the wrong shape now
fails as other malformed headers do. `localstate.api.Era.fromInt(7)` and the new `TxBodyType.DIJKSTRA` resolve.

**Left open.** A non-nil Peras certificate, real parameter updates with keys 40-56, a script-credential guard on a
real network, and rejection cases for the block, header and interval rules have no vector. The disputed dataset label
is to be reported upstream.

### 7.3 Implementation record: M7c (r10)

M7c is implemented in CCL on `feat/ledger-cbor`, and in Yaci and Yano on their local branches. It was built and tested
against local snapshots, with no release. The code was reviewed by a Fable pass and a Codex pass before commit, over
three rounds, and their findings are folded in below.

**Gates.**

| Gate | Result |
|---|---|
| I14 | CCL's dataset gates, Yaci `core` (1,176 tests, 0 failures) and `helper` (28), and Yano's G5 suites give the same results as before: `ledger-rules` 436, `ledger-conformance` 182 with 1 skipped, `tx-services` 49 with 5 skipped, test by test |
| I16, JSON | 148 JSON pins, generated at Yaci `51e7870`, cover Byron to Conway blocks, Dijkstra blocks, a witness set and 55 governance bodies, each read with CBOR output off and on. All 138 pre-Dijkstra pins are byte-identical. The 10 Dijkstra pins are identical once D4b's fields are removed. Both reviewers regenerated the pins from `51e7870` and got identical files |
| I16, API | 1,378 public signatures of 97 types at `51e7870` still exist, builders included |
| I16, three states | Keys 38 and 53 round-trip absent, nil and a value through a bare `ObjectMapper` and through one with the JDK 8 module, in CCL and in Yaci. Word32 keys keep 2^31 and 2^32 - 1 |
| I16, vectors | Each new field read from a Dijkstra vector equals the vector's value, taken from its source (the ledger golden in `Examples.hs`, KleioScan, pallas) or decoded by a test-only CBOR reader written from RFC 8949 that shares no code with the reader under test |

**Decisions taken during implementation.**

- **Sub-transaction bodies are read at sub level.** CCL's `RawTx.subTransaction` marks its body view, and a sub-level
  `RawTxBody` hides the six top-level-only keys: 2, 13, 16, 17, 23 and 27, those the ledger guards with `STopTx`
  (`Dijkstra/TxBody.hs`). Yaci maps that view once, so nothing recurses and a forbidden key in a sub-body is never mapped.
  CCL's own models gain the same fix. Top-level and pre-Dijkstra reads are unchanged.
- **A later era's aux data key.** `RawAuxData` hides key 5 (Plutus V4) before Dijkstra in `has()` and in its accessor,
  so Yaci has no era rule. Keys 0 to 4 read as before in every era.
- **`nopt`.** CCL's `ProtocolParamUpdate.nOpt` gains `@JsonProperty("nopt")`, so the whole model round-trips through a
  bare mapper. The JSON it writes is byte-identical.
- **Interval equality.** Yaci's `NonNegativeInterval` compared every two intervals as equal: its `@EqualsAndHashCode` did
  not call the superclass. It now compares numerator and denominator (below).
- **Native image.** Yaci's own `reflect-config.json` registers `Credential`, used by the guard JSON, and the six new
  models. Yano reuses them from Yaci's jar. A test checks the entries; no native image was built.

**Differences for the maintainer's approval** (I5). None occurs in a real block of an earlier era: the JSON pins are
unchanged.

- A malformed type-6 native script, such as `[6]`, fails its block in every era. Type 6 is now a known script type, and a
  malformed known script fails (section 7.2). M7a kept it with a parse error.
- A sub-transaction body with a repeated key fails its block. CCL reads sub-transactions with its lenient view, which
  rejects repeats, while a top-level body keeps the last value (section 7.2). Haskell rejects a repeated key at every
  level (`decodeSparseKeyed`), so no valid block is lost. The tolerant and lenient views differ otherwise only on shapes
  that Haskell's decoders also refuse.
- `NonNegativeInterval.equals` and `hashCode` now use the interval's value. This changes the equality of
  `ProtocolParamUpdate`'s `poolPledgeInfluence`, `priceMem`, `priceStep`, `minFeeRefScriptCostPerByte` and the new
  interval keys. It also changes every type that holds a `ProtocolParamUpdate`: `Update`, `ParameterChangeAction`,
  `ProposalProcedure`, `TransactionBody` and `Block`. Two models that differ only in such an interval compared equal
  before.
- D4b's two era-less mappings: a type-6 native script gets the guard JSON, and redeemer tag 6 is `Guarding`, in any era.

**Left open.**
- No vector has a non-nil Peras certificate, real parameter updates with keys 34 to 56, or a guard native script; these
  are tested with constructed inputs.
- No source records an endorser-block announcement hash or BLS key value, so those are checked against bytes decoded
  independently.
- Native images were not built.
- Yaci's handshake classes (`N2NVersionData`, `N2CVersionData`, `OldN2CVersionData`) share the equality defect and are out
  of scope.
- The listener prerequisites in D4b remain for Yano and Yaci Store.

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
| `Era` (r6) | Gains `Dijkstra(8)` after Conway. CCL has no `switch` on it. Experimental (D4a) |
| `EraSpec.newest()` (r6) | Becomes Dijkstra. Era-less reads that meant Conway name Conway, so no Conway result changes (I14) |
| `RawBlock` on a Dijkstra block (r6) | The parallel-array accessors throw and `invalidTransactions()` is empty; the layout-neutral accessors work (D4a) |
| Yaci, unknown era index 9 or more (r6) | A Conway-shaped block now raises `onParsingError`, where it reached listeners with a null era; a Dijkstra-shaped one reaches them with a null era (D4a, I12) |
| Yaci native scripts of an unknown type (r8) | Kept with a parse error in every era, where they were dropped (section 7.2) |
| Yaci chain-sync headers (r8) | Read in their variant's era: a header of another era's shape fails (section 7.2) |
| Yaci models (r9) | New nullable fields, every public constructor kept, `Block.toBuilder` (D4b, I16). Enums gain `PlutusScriptV4` and `Guarding`; a native script of type 6 gets a JSON form, in any era. A Dijkstra body's `requiredSigners` are its key guards, as Haskell's `reqSignerHashesTxBodyG` |
| Yaci `NonNegativeInterval` equality (r10) | Compares the interval's value; before, any two intervals were equal. Changes `equals` of `ProtocolParamUpdate` and of every model that holds one (section 7.3) |
| Listeners before Dijkstra (r9) | Must apply sub-transactions to the UTxO (`helper`'s `utxos` are top-level only), and handle Plutus V4 and the guarding purpose; Yaci Store's script store rejects V4 today. Yano reloads stored proposals with their era. Local-state query results stay Conway-shaped (D4b) |
| `NilOrValueJsonSerializer` (r9) | Public, with a new `NilOrValueJsonDeserializer`, so CCL's and Yaci's keys 38 and 53 round-trip without the JDK 8 module. `Rational` gains a `@JsonCreator` |
| `RawHeader.protocolMinor()` on a Dijkstra header (r6) | Returns the software tag, the second number of the pair, as consensus reads it (D4a) |

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
| Dijkstra or Leios changes before they freeze (r6) | Everything added is marked experimental, M7a reads one pinned release, and M7b moves the pin. The change lands in the tables (D3), not across the code |
| A node starts sending the 12-item Leios header (r6) | Both header shapes are read, picked by arity (D4a) |
| Too few independent Dijkstra vectors (r6) | I15 takes only Haskell or real-network vectors and lists what they miss. M7b runs the regenerated dataset |
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

**Q7. Dijkstra fields in Yaci's models (r6).** M7a delivers Dijkstra blocks with Yaci's existing fields; guards, keys
23-27, Plutus V4 scripts, the Leios and Peras certificates and the Leios header fields are reached through CCL's
views. Options: (a) add them to Yaci's models now; (b) let the Leios branch add the ones it uses, as additive fields
under ADR 0004's constructor rule. Lean: (b), so fields arrive with a user and the format is closer to final.
**Decided (r9, maintainer, 2026-11-07): (a)**, so that the Leios work and a Dijkstra network test start from Yaci's
models (D4b, M7c).

## 12. Related findings (out of scope)

- ADR 0001's status line and section 7 are stale, as ADR 0002 section 1.5 notes.
- Yaci's `Era` has no constant for later eras, and `EraUtil.getEra` returns null for them, so `Block.era` can be null.
- The dataset carries Dijkstra samples that no Java reader in these projects covers yet. (r6: M7a reads the ones still
  valid at its pin.)
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
- **r5** (2026-11-06): section 7.1's "Left open" gains Yano's decoder for M7 (decoding by era, the Dijkstra entry
  and the auxiliary data forms to check at version 12). No other text changed.
- **r6** (2026-11-06): Dijkstra reading becomes experimental work now, for the Leios work in Yaci. New D4a, with the
  pin read from the code (cardano-ledger 0.5.0.0, ouroboros-consensus 5.2.0.0): the block body hash is one hash, the
  Dijkstra header on the wire is the 10-item Praos header with the 12-item Leios header also read, the other changes,
  the callers of `EraSpec.newest()`, and the test vectors for I15. M7 splits into M7a and M7b; new I14 and I15; Scope,
  section 4, D3's row, D4's example and dispatch table, D8's index-9 row, I12, section 7.1's two M7 notes,
  Compatibility, Risks, new Q7 and section 12 follow. Writers stay out of scope, in ADR 0004. Reviewed before commit
  by a Fable pass and a Codex pass; their findings are folded in (header version pair read in both layouts, the
  `newest()` callers, I12's re-pin, Yaci's invalid transactions, the block and sub-transaction forms, both-nil
  intervals, vector provenance).
- **r7** (2026-11-07): new section 7.2 records M7a's implementation in CCL: gates, the decisions taken (a later era's
  key is unknown in a keyed record; the BLS slot by Haskell's peek; version-12 set rows; the dataset pin moved to
  `4fd977b2`; a disputed dataset label) and the differences for approval. Status line and section 4's dataset row
  follow. The decisions D1-D11 and D4a are unchanged.
- **r8** (2026-11-07): section 7.2 records Yaci's part of M7a and its differences; two section 8 rows.
- **r9** (2026-11-07): Q7 decided as (a) by the maintainer. New D4b: Dijkstra fields in Yaci's models, from CCL's views,
  null when absent, with ADR 0004's constructor rule. New I16 and M7c; the status line, D4a's Yaci note, section 8 and
  Q7 follow. Reviewed before commit by a Fable pass and a Codex pass; their findings are folded in (required signers
  from the key guards, as Haskell; three-state JSON both ways; sub-transaction UTxO rule stated and left to listeners;
  Yaci's parameter types and writer; the constructor inventory and the archive copy; absent against empty; the era-less
  enum mappings; no duplicate sub-transaction id; `Long` for Word32 keys; `Rational`'s JSON creator; reading stored
  proposals with their era). Both approved: Fable in round 2, Codex in round 3.
- **r10** (2026-11-07): new section 7.3 records M7c's implementation: gates, the decisions taken (sub-transaction
  bodies read at sub level, the aux data key 5 hidden before Dijkstra, `nopt`, interval equality, native image) and the
  differences for approval. Status line and a section 8 row follow. D4b is unchanged.
