# ADR 0001: Original-Byte CBOR Slices and Stack-Safe Decoders

**Status**: Proposed
**Date**: 2026-10-01
**Issue**: https://github.com/bloxbean/cardano-client-lib/issues/681
**Modules**: `common`, `common-spec`, `plutus`, `metadata`, `transaction-spec`

This ADR lives in `transaction-spec/adr/` (numbering starts at 0001) because `transaction-spec` depends on every
affected module and owns the entry points: `Transaction`, `TransactionBytes`, `TransactionUtil`, `TransactionSigner`
and the new raw views.

## 1. Context

### 1.1 Trigger

On preprod, block 5183974 contains tx `f90dce5765108da976abdbb9fc618f9a6ffd9fa4d93b2f288eed1808545424c9`. It is
16,181 bytes and carries a witness native script nested 5,383 levels deep (`[1, [[1, [ ... ]]]]`, 3 bytes per level).
The Haskell node accepts it. cardano-ledger has no nesting limit: decoding runs on the heap (cborg's CPS decoder plus
`MemoBytes`/Annotator), and the maximum tx size (16,384 bytes) is the only bound.

### 1.2 Where CCL recurses

Every path from bytes to model, and from model to hash, recurses once per nesting level.

| Step | Code | Recursion |
|---|---|---|
| Bytes to `DataItem` | cbor-java 0.9 `CborDecoder` (`gradle/libs.versions.toml:11`, `common/build.gradle:2`); `ArrayDecoder`/`MapDecoder`/`TagDecoder` call back `CborDecoder.decodeNext()` | one or more frames per level, with no depth option |
| Tx entry point | `Transaction.deserialize` decodes the whole tx with `CborDecoder.decode(bytes)` (`Transaction.java:128`) | whole tx |
| Plutus Data | `PlutusData.deserialize(DataItem)` (`PlutusData.java:45-71`) → `ListPlutusData.deserialize` (`ListPlutusData.java:40-61`, recurses at `:52`), `MapPlutusData.deserialize` (`MapPlutusData.java:27-40`), `ConstrPlutusData.deserialize` (`ConstrPlutusData.java:42-66`) | per level |
| Native script | `NativeScript.deserialize(Array)` (`NativeScript.java:23-45`) → `ScriptAll.deserialize` (`ScriptAll.java:47-58`, recurses at `:53`), and likewise `ScriptAny` and `ScriptAtLeast` | per level |
| Inline datum | `TransactionOutput.deserializePostAlonzo` decodes the tag-24 payload with `PlutusData.deserialize(bytes)` (`TransactionOutput.java:175`) | per level |
| Datum hash | `PlutusData.getDatumHashAsBytes` re-serializes the model, then canonical-encodes it (`PlutusData.java:98-104`) | model → `DataItem` → bytes |
| Script hash | `Script.getScriptHash` = `blake2b224(type ‖ serializeScriptBody())` (`common-spec/.../Script.java:19-33`); `NativeScript.serializeScriptBody` re-encodes (`NativeScript.java:47-55`) | per level |
| Aux data hash | `AuxiliaryData.getAuxiliaryDataHash` re-encodes (`AuxiliaryData.java:123-132`) | per level |

Two more paths recurse:

- **The encoder.** `CustomCborEncoder.encode` (`CustomCborEncoder.java:44-67`) recurses. So does
  `CustomMapEncoder.encodeCanonical` (`CustomMapEncoder.java:60-98`), which sorts keys by encoding every entry with a
  new encoder.
- **The signer.** `TransactionSigner.addWitnessToTransaction` decodes the whole witness set
  (`TransactionSigner.java:134`) and re-encodes it (`:153`).

`TransactionUtil.extractTransactionBodyFromTx` (`TransactionUtil.java:67-89`) and `TransactionBytes`
(`TransactionBytes.java:65-109`) use `CborDecoder.decodeNext()` too. The body has a fixed, shallow depth, so the body
path survives. `TransactionBytes` also decodes the witness set (`:78`), however, so it overflows on deep witnesses.

### 1.3 Measurements

The 2026-10-01 nesting probe used synthetic txs shaped like the trigger. It ran on JDK 25.0.2, macOS arm64, with the
default `ThreadStackSize` of 2048 KB. The table gives the largest depth that decoded. Linux x86_64 defaults to a 1 MB
stack, so expect roughly half these values there.

| Path | Payload | Warm (JIT) | `-Xint` | Max depth in a 16,384-byte tx |
|---|---|---:|---:|---:|
| `Transaction.deserialize` | witness native script | 11,226 | 2,283 | 5,410 |
| `Transaction.deserialize` | witness list datum | 22,453 | 4,569 | 16,262 |
| `Transaction.deserialize` | witness constr datum | 11,227 | 3,426 | 5,420 |
| `Transaction.deserialize` | redeemer data / metadata | 22,451 / 22,453 | 4,568 / 4,568 | 16,247 / 16,256 |
| `NativeScript.deserialize` + `getScriptHash` | native script | **6,860** | 2,285 | 5,410 |
| `PlutusData.deserialize(bytes)` + `getDatumHash` | list / constr datum | 13,724 / 8,823 | 4,572 / 3,429 | 16,256 / 5,420 |
| `AuxiliaryData.deserialize` + `getAuxiliaryDataHash` | metadata list | 22,453 | 4,570 | 16,256 |
| cbor-java `CborDecoder.decodeNext` | native script | 12,349 | 2,284 | 5,410 |
| `TransactionUtil.extractTransactionBodyFromTx` | any | all depths tested (up to 262,144) | all depths tested | n/a |
| Yaci `BlockSerializer` (iterative `CborSlice`) | any | all depths tested (up to 262,144) | all depths tested | n/a |

On a cold JVM (first call), `Transaction.deserialize` fails on the 5,383-level tx and on synthetic native scripts from
about 2,000 levels. After warm-up it passes. **The same tx therefore passes or fails depending on JIT state.** A valid
tx at maximum size overflows CCL on every interpreted or cold path.

### 1.4 Who hit it

- **Yaci / yaci-store.** Sync stopped on this tx. Yaci fixed it with iterative `CborSlice` fallbacks and
  `ArrayCborDecoder`: bloxbean/yaci #198 "Fix block parsing with deeply nested native scripts", #199 "Preserve blocks
  when nested datum or metadata parsing fails" and #200 "Fix raw datum and redeemer extraction without blocking sync".
- **Yano.** Shadow sync overflowed in CCL `Transaction.deserialize` on the JVM, and in its own recursive
  `Timelock.evaluate` in native image.
  - Yano's own slicers (`CborItems`, `CborReader`, `CborSlice`) are on the unmerged branch `feat/conway-ledger-rules`
    (yano PR #155).
  - Yano `main` has a recursive `NativeScriptEvaluator` in `ccl-ledger-rules`, which the CCL evaluator (D4.3)
    replaces.
- **Julc.** `OriginalTxBytes` (julc PR #221) is a fourth hand-written slicer, and it is recursive (overflow at 2,284
  native-script levels with `-Xint`). Commit 7958c4da on that PR, not yet merged, makes its set-tag skip accept
  tag 258 at any argument width.

That makes four slicers in four repositories, three of them recursive. All of them should be one CCL
implementation.

### 1.5 Prior art

All issue numbers below were checked with `gh`.

| Implementation | Approach |
|---|---|
| Haskell cardano-ledger | `MemoBytes`/Annotator keep original bytes; cborg decodes in CPS on the heap; no depth limit |
| Pallas (Dolos) | `KeepRaw<T>` keeps original bytes. After this block stopped Dolos, txpipe/pallas #802 (native scripts), #807 (PlutusData), #808 and #810 (Metadatum) and #814 (equality) made decode, encode, clone and compare heap-iterative. |
| Amaru | `WithOriginalBytes` plus stack-safe recursive types (pragma-org/amaru #1380) |
| gouroboros | A 256-level CBOR nesting cap rejected values the reference decoder accepts (#2244, blinklabs-io/dingo #4291). #2252 raised it to 16,384. |
| Scalus 1.1.1 | `Transaction.fromCbor` enforces a 1,000-level nesting cap (borer), so it rejects this tx (measured by the probe) |

### 1.6 Key insight

Only three Cardano types nest without bound: **Plutus Data**, **native scripts (Timelock)** and **transaction
metadatums**. Every other structure (envelope, body, outputs, values, certificates, governance, witnesses) has a
small fixed depth set by the CDDL. The following are enough to make every path stack-safe, with no depth cap and no
`-Xss`:

- one iterative span walker;
- iterative codecs for those three types;
- an iterative `DataItem` codec underneath them.

### 1.7 Fidelity gaps found while reading

A scratch program checked the first nine rows against cardano-client-lib 0.8.0-pre5-dev1. The same code is on
`master` 83d4cdbf.

| Gap | Code | Observed |
|---|---|---|
| Shelley 3-element tx with metadata fails to decode | `Transaction.java:169-171` reads `txnItems.get(3)` | `IndexOutOfBoundsException: Index 3 out of bounds for length 3` |
| Allegra/Mary aux data `[metadata, scripts]` is silently dropped | `Transaction.java:172` accepts only `MAP`; `AuxiliaryData.deserialize(Map)` | `auxiliaryData == null` |
| Indefinite outer tx array does not round-trip | `TransactionBytes.java:49-54, 73` | trailing `ff` lost |
| Non-minimal outer header (`98 04`) | `TransactionUtil.java:75` skips exactly one byte | returns `04` as the "body" |
| Datum hash from a non-canonical map | `PlutusData.java:100` (canonical re-encode) | hash differs from the original bytes |
| Native script hash from indefinite arrays | `NativeScript.java:47-55` | hash differs from the original bytes |
| Aux data hash from a non-canonical metadata map | `AuxiliaryData.java:123-132` | hash differs from the original bytes |
| Duplicate keys in a Data map | `MapPlutusData.java:25` (`LinkedHashMap`); cbor-java `Map.put` keeps the first position and the last value | `a201000101` → `a10101` |
| Non-minimal ints and chunked bytes in Data | model re-encode | `9f1800ff` → `9f00ff`; `5f41aa41bbff` → `42aabb` |
| Unknown native script type silently dropped | `NativeScript.deserialize` returns `null` (`NativeScript.java:42-43`); `ScriptAll.deserialize` skips nulls (`ScriptAll.java:53-55`) | Haskell fails with "Unknown Timelock kind" (allegra `Scripts.hs:313`) |

Re-encoding is correct when *building* a tx. For a *received* tx it is wrong whenever the encoding is not canonical.
Haskell hashes original bytes in every case.

## 2. Scope

In scope:

- one iterative span walker in `common`;
- raw views `RawTx` and `RawBlock` for the released Shelley-based eras, Shelley (2) through Conway (7), in
  `transaction-spec`, plus the missing decoding eras in CCL's `Era` (D7);
- every ledger hash computed from original slices, including the block body hash;
- stack-safe `DataItem` decode and encode, and stack-safe Plutus Data, native script and metadata codecs that the
  existing model API delegates to;
- a native script evaluator;
- `TransactionBytes`, `TransactionUtil` and `TransactionSigner` moved onto the walker;
- the gaps in section 1.7.

Out of scope:

- Byron: regular and epoch-boundary blocks, and Byron txs. They are rejected with a clear error. Yano does not
  validate Byron; it stores Byron block bytes and UTxOs as they are. CCL is almost entirely Shelley-family, and Yaci
  parses Byron with its own `ByronBlock` parser. This ADR therefore covers Shelley and the eras after it; D8 keeps
  room for a Byron view.
- Dijkstra (era 8), still in development at `f649f975`. Its block is `[header, block_body]` with unsegregated
  `transactions`, Leios/Peras certificates and a different body hash, and it adds sub-transactions and Plutus V4
  (`dijkstra.cddl:3-102, 785-798`). `RawBlock` rejects era 8 and the 2-element body. Support is a follow-up once the
  era is frozen for a hard fork; nothing here claims forward-era compatibility (see D6 on unknown keys).
- Ledger validation rules (bounded bytes ≤ 64, non-empty sets, set duplicates, unknown keys and so on). The walker
  checks CBOR well-formedness and the structural decisions in D6 only.
- Recursive `toString()`. Jackson JSON (de)serializers for Data and metadata are also out of scope: Jackson 2.21 caps
  nesting at 1,000 by default and fails with a clean `StreamConstraintsException`, not an overflow.
- Memoizing original bytes inside model objects (deferred; see section 11).

Related builder bugs, out of scope here and filed separately:

- `AuxiliaryData.getAuxiliaryData` chooses the Shelley shape when metadata is combined with V3-only scripts, because
  the condition omits `plutusV3Scripts` (`AuxiliaryData.java:138-142`), so the V3 scripts are dropped.
- `ScriptDataHashGenerator` chooses the redeemer form by era (`ScriptDataHashGenerator.java:38-40, 58-71`). That is
  correct for building; verifying a received tx uses D3.

## 3. Decision

### D1. One iterative span walker: `CborSpan` (`common`)

A `CborSpan` is `(buffer, offset, length)`: one complete CBOR item in an original buffer. It never copies until
`bytes()` is called.

- **One walker.** `skip(buf, offset, limit)` returns the end of one item. It uses an explicit frame stack (an
  `int`/`long` array that grows on demand), not recursion. Arrays count children, maps count keys plus values, tags
  require one payload, and indefinite containers and strings end at `BREAK`. Every loop iteration consumes at least
  one byte, so the walker cannot hang. The frame stack is at most `length` entries.
- **Navigation.** By array index, by map key (unsigned-int keys for CDDL records; any span for generic maps), and by
  tag payload. `items()` and `entries()` list the direct children in one pass, in encoded order, with duplicates kept.
  Views cache child lists, so index access stays O(n).
- **Tags.** A tag's value is read from its numeric argument, so `d9 0102`, `da 00000102` and `db 0000000000000102`
  are all 258. `untagIf(258)` strips an optional set tag at any width.
- **Embedded CBOR.** `embedded()` returns the tag-24 payload as a span. For a definite byte string this is a window
  into the same buffer, so offsets stay in the original buffer. For a chunked byte string the chunks are concatenated
  into a new buffer.
- **Scalars.** `asLong()`, `asBigInteger()` (uint, nint, tags 2/3), `asBoolean()`, `isNull()`, `byteString()`
  (payload; chunks concatenated) and `text()`.
- **Splice.** `replacing(targets, replacements)` rewrites disjoint child spans and keeps every other byte, as in
  Yaci's `CborSlice.replacing`.
- **Well-formedness (RFC 8949 §5.3).** The walker rejects:
  - truncated headers, arguments or strings;
  - reserved additional info 28–30;
  - indefinite length on major types 0, 1, 6 and 7;
  - a `BREAK` outside an indefinite container, or after a map key;
  - an indefinite string whose chunks are of a different type or are themselves indefinite;
  - a length that does not fit `int`;
  - trailing bytes after the item in `of()`.

  The walker accepts non-minimal integers and lengths and keeps them as encoded, as Haskell does.
- **Bounded memory.** A declared count larger than the remaining bytes (every item takes at least one byte) is
  rejected before anything is allocated (the idea from `BoundedCbor.java:87-90` and Yaci's `CborSlice`). Nothing is
  preallocated from declared sizes.
- **Errors.** `CborRuntimeException` (in `common`) with the byte offset and the reason.

The walker is **generic**. It knows nothing about transactions: `RawBlock`, `RawTx`, Yaci's block parser and Julc's
script-context builder all navigate with the same calls.

### D2. Typed views over spans (`transaction-spec`)

Views are thin, immutable and lazy, and every accessor returns spans of the caller's buffer. They decode only what a
CDDL position needs: a few ints, bools and tags. They never build the unbounded types unless asked to
(`toPlutusData()`, `toScript()`).

- **`RawBlock`** accepts `[era, block]` (HFC envelope; era 2–7), the bare block, or either one wrapped in tag 24. The
  era decides the D6 rules, and bare CBOR cannot tell Alonzo from Babbage or Conway, so `of(bytes)` requires the
  envelope and `of(bytes, era)` takes it explicitly (an envelope, if present, must agree). It exposes:
  - `era()` and the header span;
  - the tx-bodies, witness-sets, aux-data-map and invalid-txs spans;
  - `txCount()`, `tx(i)` (a `RawTx` built from the parallel arrays) and `invalidTxIndexes()` (as encoded).
    `tx(i).isValid()` is `i ∉ invalidTxIndexes()`, and always true before Alonzo (D6);
  - `txBytes(i)`, which assembles a full tx for callers that need it: Yaci's full tx CBOR, and submission;
  - `bodyHash()`, defined below.

  It rejects Byron (era 0/1 or a Byron shape), Dijkstra (era 8 or its 2-element body), mismatched tx counts and the
  cases in D6.
- **`RawBlock.bodyHash()`** is `blake2b256(h(bodies) ‖ h(witnesses) ‖ h(auxMap) [‖ h(invalidTxs)])`, where `h` is
  `blake2b256` of the original segment bytes. Shelley–Mary use three parts; Alonzo+ add the fourth (shelley
  `BlockBody/Internal.hs:211-229`; alonzo `BlockBody/Internal.hs:188-211`). Yano already builds blocks with this
  formula (`DevnetBlockBuilder`) and must verify it during sync.
- **`RawTx`** accepts the submission form `[body, witnesses, aux / null]` (any era; `isValid` is true) or
  `[body, witnesses, isValid, aux / null]` (Alonzo+; element 2 must be a bool). The Alonzo–Conway decoder accepts
  both: it peeks at the type of element 2 and defaults to `IsValid True` (alonzo `Tx.hs:541-553`). `RawTx` is
  era-agnostic; the era-dependent `scriptDataHash` takes an `Era`, which a block supplies via `RawBlock.era()`. It
  exposes:
  - `body()`, `witnessSet()`, `auxData()` and `isValid()`;
  - `bodyField(key)` and `witnessField(key)`;
  - `outputs()` and `collateralReturn()` as `RawOutput`. Legacy array outputs `[addr, value, ?datum_hash]` and map
    outputs are handled alike. Each output gives its address, value, datum hash, inline datum (tag-24 payload) and
    script ref (`#6.24([type, script])`).
  - `witnessDatums()`;
  - `redeemers()` as `RawRedeemer(tag, index, data, exUnits)`, for both the array form and the Conway map form, in
    encoded order;
  - `scripts()`: witness native and V1/V2/V3 scripts as `RawScript(type, span)`;
  - `vkeyWitnesses()` and `bootstrapWitnesses()`.

### D3. Every hash from original slices

| Hash | Preimage |
|---|---|
| TxId | `blake2b256(body span)` |
| Datum hash (witness) | `blake2b256(datum span)` |
| Datum hash (inline) | `blake2b256(tag-24 payload)` |
| Native script hash (witness, aux, reference) | `blake2b224(0x00 ‖ native script span)` |
| Plutus V1/V2/V3 script hash (witness, aux, reference) | `blake2b224(0x01/0x02/0x03 ‖ byte-string payload)` |
| Aux data hash | `blake2b256(aux span)`, whatever the shape |
| Block body hash | see `RawBlock.bodyHash()` in D2 |
| Script integrity | `blake2b256(redeemers ‖ datums ‖ languageViews)` (see below) |

Script integrity terms (alonzo `Tx.hs:391-423`). "Empty" means zero entries, whatever the encoding (`80`, `9fff`,
`a0`, `d9 0102 80`):

- `redeemers` is the original bytes of witness field 5, even when empty. When the field is absent, the era's empty
  encoding is used: `80` before Conway, `a0` in Conway (alonzo `TxWits.hs:157-162`, the same rule as
  `ScriptDataHashGenerator.java:75-84`).
- `datums` is the original bytes of witness field 4, including any tag 258, when it has at least one entry. An absent
  **or empty** field contributes zero bytes (`Tx.hs:393`).
- `languageViews` is supplied by the caller for exactly the Plutus languages the tx runs: the languages of the needed
  scripts, whether provided as witnesses or as reference scripts in the UTxO (`asatPlutusLanguagesUsed`, alonzo
  `Alonzo.hs:156-157`, used at `Rules/Utxow.hs:370`). It is not every cost model in the protocol params. `RawTx`
  cannot compute it, because reference scripts need the UTxO; callers build it with
  `CostModelUtil.getLanguageViewsEncoding(CostModel...)` for those languages, or pass `a0` for none.
- The result is empty only when the redeemers, the datums **and** the language views are all empty (`Tx.hs:413-417`).
- The `Era` argument selects only the absent-redeemers encoding. It must be Alonzo or later; an earlier era raises
  `IllegalArgumentException`, because there is no script integrity before Alonzo.

These are the bytes Haskell hashes. The block body hash and the script integrity hash are reconstructed preimages:
fixed framing around original slices (and, for integrity, the caller's language views). The existing model hash
methods stay as they are. They remain correct for txs that CCL builds, and the Javadoc points to the raw views for
received bytes.

### D4. Stack-safe codecs behind the existing model API

The fix goes in the codecs, so the public model API keeps working on inputs of any depth.

1. **`DataItem` codec (`common`).** `CborSerializationUtil.deserialize` becomes iterative. It produces the same
   `DataItem` trees as cbor-java with CCL's settings:
   - tags attached to items;
   - `Special.BREAK` as the last item of an indefinite array;
   - chunked flags;
   - tag-30 rationals;
   - duplicate keys: the first position is kept with the last value.

   It records each item's offset and length in the same single pass, so `Transaction.deserialize` needs no separate
   walker pass (section 9).

   `CustomCborEncoder` (with `CustomMapEncoder` and `CustomByteStringEncoder`) encodes containers with an explicit
   stack. Maps that need canonical sorting buffer their encoded entries; everything else streams. Maps are built
   through one small `co.nstant.in.cbor.model.Map` subclass that keys entries by the key's minimal re-encoding. The
   re-encoding is iterative, which keeps cbor-java's equality semantics without calling the recursive
   `DataItem.hashCode()` on deep keys (risk R2). All `CborDecoder.decode` calls in `common`, `plutus`, `metadata` and
   `transaction-spec` go through `CborSerializationUtil`.
2. **Plutus Data (`plutus`).** One package-private codec converts bytes ↔ model and `DataItem` ↔ model with explicit
   stacks. It covers:
   - all constr forms: tags 121–127, 1280–1400, and 102 `[alt, fields]`;
   - bignums (tags 2/3);
   - chunked bytes;
   - indefinite lists.

   `PlutusData.deserialize(...)`, the subclasses' `deserialize`/`serialize()`, `serializeToBytes()` and
   `getDatumHash()` delegate to it. `equals`/`hashCode` of `List`/`Map`/`Constr` become iterative (they are
   Lombok-generated today), because `MapPlutusData` hashes keys during decode.
3. **Native scripts (`transaction-spec`).** `NativeScript.deserialize(Array)`, `deserializeScriptRef`,
   `serializeAsDataItem` and `serializeScriptBody` delegate to one iterative codec.
   - The codec **rejects** an unknown script type, as Haskell does ("Unknown Timelock kind", allegra
     `Scripts.hs:313`). Today it returns `null`, which `ScriptAll` silently drops (section 1.7).
   - `m` in `[3, m, scripts]` is decoded as a signed integer: Haskell's `TimelockMOf !Int` may be negative (allegra
     `Scripts.hs:184`).

   A new `NativeScriptEvaluator` evaluates iteratively (post-order with an explicit stack) using Haskell
   `evalTimelock` semantics:
   - `RequireTimeStart s`: lower bound present and `s ≤ lower`;
   - `RequireTimeExpire s`: upper bound present and `upper ≤ s`;
   - `MOfN`: count of satisfied sub-scripts `≥ m`.
4. **Metadata (`metadata`).** The model already wraps `DataItem` (`CBORMetadataMap`/`CBORMetadataList`), so
   item 1 makes it stack-safe. `CBORMetadata.deserialize(byte[])` (`CBORMetadata.java:142-162`) goes through
   `CborSerializationUtil`.
5. **`Transaction.deserialize`** uses the single-pass decoder from item 1 and decodes each field through the existing
   model code, now stack-safe. It accepts the Shelley 3-element form and the Allegra/Mary `[metadata, scripts]` aux
   shape.

**Spans are authoritative; the models are lossy.** Haskell's `Metadatum.Map` and Plutus `Data.Map` are lists of pairs
(core `Metadata.hs:60-61`, `decodeMapN`), so every era keeps duplicate keys and their order. The CCL models
(`MapPlutusData`, cbor-java `Map`) keep the first position with the last value. This stays as it is, and the Javadoc
says so. Hashes, preimages and anything a validator inspects come from spans.

### D5. `TransactionBytes`, `TransactionUtil`, `TransactionSigner`

`TransactionBytes` keeps its public surface: the constructor, the getters, `getTxBytes()` and
`withNewWitnessSetBytes()`. Internally it parses through `RawTx.of(txBytes)`.

| Field / method | Today | With the walker |
|---|---|---|
| `initialBytes` | first byte, assumed to be the whole header (`TransactionBytes.java:73-74`) | the tx array header bytes at any width, or `9f` |
| `txBodyBytes` | `decodeNext()` builds a `DataItem` tree; the size comes from stream position (`:76, :95-109`) | `rawTx.body().bytes()`, with no tree built |
| `txWitnessBytes` | `decodeNext()`, recursive: overflows on deep witnesses (`:78`) | `rawTx.witnessSet().bytes()` |
| `validBytes` | element 2 if it decodes to `TRUE`/`FALSE` (`:80-86`) | element 2 when the array has 4 elements; it must be a bool |
| `auxiliaryDataBytes` | the next element (`:87, :89`) | the last element (map, array, tag-259 map or `null`) |
| `getTxBytes()` | merge (`:49-54`); the trailing `ff` is lost for `9f` | merge, plus `ff` when the header was indefinite |

The other two classes change as follows:

- **`TransactionUtil.extractTransactionBodyFromTx`** returns `RawTx.of(tx).body().bytes()`, and `getTxHash(byte[])`
  returns `RawTx.txId()`. Any header width now works. The whole tx is walked once, iteratively, so malformed later
  elements now raise an error (section 8).
- **`TransactionSigner.addWitnessToTransaction`** (`TransactionSigner.java:132-158`) splices instead of decoding and
  re-encoding. This lands as its own PR.
  - It appends the new `[vkey, sig]` to the field 0 array. The array keeps its tag 258; for an indefinite array the
    witness is inserted before `ff`.
  - If field 0 is absent, it appends the entry `00 → [witness]` and increments the map count. This is the key
    position used today.
  - Definite counts are rewritten minimally, so the header grows by one width step when a count crosses 23→24
    (1→2 bytes), 255→256 (2→3 bytes) or 65,535→65,536 (3→5 bytes). This applies to the field 0 array and to the
    witness map count.
  - Every other witness field stays byte-identical, so the redeemer and datum bytes covered by `script_data_hash`
    cannot change.
  - A test checks that the output is byte-identical to today's on canonical inputs, including inputs at each
    count boundary.

`BoundedCbor` (`verified-structures/jellyfish-merkle/.../BoundedCbor.java`) is a JMT-specific preflight. It requires
canonical encoding, rejects indefinite length and is recursive, but its depth is bounded by `maxDepth`. It is not
reused or changed here; only its rule of checking declared counts against the remaining input is copied into the
walker.

### D6. Structural decisions (checked against Haskell)

The views keep every entry in encoded order. Validation stays the ledger's job, with these exceptions.

- **Duplicate keys in records are rejected, in all eras.** This covers `field()` on the tx body, the witness set,
  map outputs and the Alonzo tag-259 aux record. Haskell decodes these records `SparseKeyed`; `applyField` fails with
  `duplicateKey` (cardano-binary `Decoding/Coders.hs:566-571, 634-639`). The call sites are:
  - shelley `TxBody.hs:138`;
  - conway `TxBody.hs:183`;
  - shelley `TxWits.hs:226`;
  - alonzo `TxWits.hs:610-611`;
  - babbage `TxOut.hs:614-615`;
  - alonzo `TxAuxData.hs:260`.
- **Unknown record keys are passed through, deliberately.** Haskell rejects them (`invalidField`). The views do not,
  so that an additive key (for example aux tag-259 key 5, PlutusV4, alonzo `TxAuxData.hs:299`) does not break span
  access or hashing. This is not a claim of support for a later era (section 2).
- **Aux-map index out of range: rejected.** This fails in every Haskell era (`auxDataSeqDecoder`, shelley
  `BlockBody/Internal.hs:231-239`).
- **Duplicate aux-map keys follow the era.** Shelley to Babbage accept them and the last value wins
  (`IntMap.fromList`); Conway rejects them (`decodeIntMap`, `Decoder.hs:858-875`). The aux-map span and `bodyHash()`
  keep every byte either way; `tx(i).auxData()` applies the era's lookup. Producer behaviour does not define
  acceptance: any block the reference decoder accepts must be readable.
- **`invalid_transactions` index out of range: rejected.** Haskell fails the same way (alonzo
  `BlockBody/Internal.hs:240-243`).
- **Duplicate or non-ascending `invalid_transactions`: rejected, as Haskell's decoder actually behaves.** There is no
  explicit check, but `alignedValidFlags` (`:283-290`) calls `Seq.replicate (x - prev - 1)`, which is negative for
  any duplicate or descending pair, and containers' `Data.Sequence.replicate` calls `error` for a negative count.
  The flags are forced when the txs are built (`zipWith4`, `:250`), so no such block decodes. Strictly ascending
  in-range indexes are the only accepted form; tx `i` is then valid iff `i ∉ indexes`.
- **Aux-data keys are era-dependent, in three layers.** The decoder version is the era's `ProtVerLow` (`Era.hs:132`,
  Conway = 9), not the live protocol version.
  1. The Alonzo tag-259 record rejects duplicate keys (above).
  2. The metadata map (`Map Word64 Metadatum`) keeps the last value from Shelley to Babbage and rejects duplicates
     from Conway (`decodeMapByKey`, `Decoder.hs:815-821`).
  3. Metadatum inner maps are pair lists: duplicates and order are kept.

  The views keep every entry; `field()` rejects duplicates only on records. The model's deduplication is lossy and
  documented (D4).

### D7. Decoding eras in `Era` (`common-spec`)

CCL's `Era` has only `Babbage(6)` and `Conway(7)`, so a caller cannot name Alonzo without calling it Babbage. The
enum gains `Shelley(2)`, `Allegra(3)`, `Mary(4)` and `Alonzo(5)`, with `value` equal to the HFC era index, as for the
existing two, and `Era.fromValue(int)`. One enum is reused rather than adding a second decoding-era type.

- Every existing use compares `era.value >= Era.Conway.value` (`SerializationUtil.java:13`,
  `TransactionWitnessSet.java:111`, `ScriptDataHashGenerator.java:38, 58, 75`), so the new constants serialize as
  Babbage does today, which is also correct for Alonzo. The Javadoc says the builders do not produce pre-Alonzo
  shapes, so the earlier constants are for decoding and verification.
- `RawBlock` takes its era from the envelope or from `of(bytes, era)`. `RawTx` never guesses an era.
- Byron and Dijkstra get no constant (section 2).

### D8. Adding an era or era family

Existing APIs do not change when an era is added.

- **New Shelley-family era (Dijkstra and later).** Add an `Era` constant. New or changed CDDL fields are reached by
  key through `field()`, never by position. Era-dependent rules live in one per-era table in `transaction.raw`: record
  keys, aux shapes, set tags, redeemer form, block body hash parts and the D6 duplicate rules. A new era is one row
  there plus its scenario-matrix fixtures. A changed block or tx layout, such as Dijkstra's `[header, block_body]`,
  gets its own branch, selected by the D2 envelope dispatch.
- **Another era family (Byron).** `CborSpan` is era-agnostic. A future `RawByronBlock`, or a sealed era-family
  type, sits on the same walker and the same envelope dispatch (era 0/1); `RawBlock`/`RawTx` stay Shelley-family.
  Starting points, checked at `f649f975`: header hash `blake2b256(82 01 | 82 00 ‖ header)` (byron
  `Block/Header.hs:482-490`), witnesses hashed with `9f … ff` framing (`UTxO/TxPayload.hs:82-92`), and a Merkle
  split at the largest power of two below the count (`Common/Merkle.hs:150-175`).
- **Model and signer APIs** (`Transaction`, `TransactionBytes`, `TransactionUtil`, `TransactionSigner`) stay
  Shelley-family only, and Byron bytes are never routed into them.

## 4. API sketch

```java
// common: com.bloxbean.cardano.client.common.cbor
public final class CborSpan {
    public static CborSpan of(byte[] buf);                        // exactly one item; walks it once
    public static int skip(byte[] buf, int offset, int limit);    // the iterative walker

    public byte[] buffer(); public int offset(); public int length(); public int headerLength();
    public byte[] bytes();                                        // copy of the original encoding
    public int majorType();                                       // after tags
    public long tag();                                            // outermost tag, -1 if none
    public CborSpan untag();                                      // payload of the outermost tag
    public CborSpan untagIf(long tag);                            // strips it when equal, at any width
    public boolean isIndefinite();
    public int size();                                            // array items / map pairs
    public CborSpan get(int index);
    public List<CborSpan> items();
    public List<Map.Entry<CborSpan, CborSpan>> entries();         // encoded order, duplicates kept
    public Optional<CborSpan> field(long uintKey);                // CDDL records; rejects a duplicated key
    public long asLong(); public BigInteger asBigInteger(); public boolean asBoolean(); public boolean isNull();
    public byte[] byteString(); public String text();
    public CborSpan embedded();                                   // tag-24 payload as CBOR
    public byte[] replacing(List<CborSpan> targets, List<byte[]> replacements);
}

// transaction-spec: com.bloxbean.cardano.client.transaction.raw
public final class RawTx {
    public static RawTx of(byte[] txCbor);
    public CborSpan span(); public CborSpan body(); public CborSpan witnessSet();
    public Optional<CborSpan> auxData(); public boolean isValid();
    public byte[] txId(); public Optional<byte[]> auxDataHash();
    public Optional<byte[]> scriptDataHash(Era era, byte[] languageViews);  // era >= Alonzo; D3
    public Optional<CborSpan> bodyField(int key); public Optional<CborSpan> witnessField(int key);
    public List<RawOutput> outputs(); public Optional<RawOutput> collateralReturn();
    public List<RawDatum> witnessDatums(); public List<RawRedeemer> redeemers(); public List<RawScript> scripts();
    public List<CborSpan> vkeyWitnesses(); public List<CborSpan> bootstrapWitnesses();
}
public final class RawBlock {
    public static RawBlock of(byte[] blockCbor);                 // [era, block], optionally in #6.24
    public static RawBlock of(byte[] blockCbor, Era era);        // also a bare block; envelope must agree
    public Era era(); public CborSpan header();
    public int txCount(); public RawTx tx(int i); public byte[] txBytes(int i);
    public int[] invalidTxIndexes(); public byte[] bodyHash();
}
public record RawOutput(CborSpan span, CborSpan address, CborSpan value, Optional<byte[]> datumHash,
                        Optional<RawDatum> inlineDatum, Optional<RawScript> scriptRef) {}
public record RawDatum(CborSpan span) { public byte[] hash(); public PlutusData toPlutusData(); }
public record RawScript(int type, CborSpan span) { public byte[] hash(); public Script toScript(); }
public record RawRedeemer(int tag, long index, CborSpan data, CborSpan exUnits) {}

// common-spec: com.bloxbean.cardano.client.spec (existing enum, D7)
public enum Era { Shelley(2), Allegra(3), Mary(4), Alonzo(5), Babbage(6), Conway(7);
    public static Era fromValue(int hfcEraIndex); }

// transaction-spec: com.bloxbean.cardano.client.transaction.spec.script
public final class NativeScriptEvaluator {
    public static boolean evaluate(NativeScript script, Set<String> vkeyHashesHex,
                                   Long invalidBefore, Long invalidHereafter);
}
```

The names (`CborSpan`, `RawTx`, `RawBlock`) and the `transaction.raw` package are decided. There are no new options,
no depth settings and no SPI. The iterative codecs are package-private and are reached through the existing static
entry points. Shortcuts such as `first()` or an `arrayItem(path…)` that skips the full walk are added only if the
benchmark shows a need.

## 5. Module placement and dependency direction

| Component | Module | Uses (existing dependencies only) |
|---|---|---|
| `CborSpan`, iterative `DataItem` codec, `Map` subclass | `common` | cbor-java (already `api`) |
| `Era` constants (D7) | `common-spec` | none new |
| Plutus Data codec, iterative `equals`/`hashCode` | `plutus` | `common`, `common-spec` |
| Metadata routing | `metadata` | `common` |
| Native script codec and evaluator, `RawTx`/`RawBlock`/records, hashes, `TransactionBytes`/`TransactionUtil`/`TransactionSigner` | `transaction-spec` | `common`, `crypto`, `common-spec`, `plutus`, `metadata` |

Downstream, CCL ← Yaci ← Yano and CCL ← Julc. No new module or dependency is added anywhere, and nothing in CCL
depends on its consumers.

## 6. Scenario matrix: "supports all types of transactions"

A **gap** marker means CCL fails on that scenario today.

### 6.1 Era shapes (block and submission)

Every era row from Shelley to Conway has a real block and real txs, plus a synthetic non-canonical variant (indefinite
containers, non-minimal heads, unsorted maps). Real fixtures assert TxId, aux data hash, block body hash and, from
Alonzo, the script integrity hash against the on-chain values. A synthetic variant changes the hashed bytes, so its
hashes are asserted against an independent reference computation over the mutated bytes (blake2b of the expected
slice), never against the unmodified fixture's hashes; embedded hashes (body keys 7 and 11, the header body hash) are
updated to match.

| Scenario | Handling | Fixture |
|---|---|---|
| Byron | Out of scope. `RawBlock` rejects era 0/1 and Byron shapes; `RawTx` rejects 2-element `[tx, witnesses]`. The error message says Byron is out of scope. | mainnet Byron block, Byron tx → expected error |
| Dijkstra (8) | Out of scope (section 2). `RawBlock` rejects era 8 and the 2-element `[header, block_body]` shape. | synthetic from the pinned CDDL → expected error |
| Shelley (2) | 3 elements: `[body, wits, metadata / null]`. Body keys 0–7; witness keys 0, 1, 2; aux is the metadata map. **Gap:** `Transaction.deserialize` throws (`Transaction.java:169-171`). | mainnet Shelley tx with metadata; synthetic `83 body a0 a1…` |
| Allegra (3) | Body key 8 (validity start); timelock script types 4/5; aux may be `[metadata, [scripts]]`. **Gap:** array-form aux dropped (`Transaction.java:172`). | Allegra tx with aux scripts |
| Mary (4) | Mint (key 9); value `[coin, multiasset]`. | Mary mint tx |
| Alonzo (5) | 4 elements with `isValid`, or 3 elements with implicit `isValid = true` (also Babbage and Conway; alonzo `Tx.hs:541-553`). Body keys 11, 13, 14, 15; witness keys 3, 4 and 5 (array form); aux `#6.259{…}`; legacy array outputs with a datum hash. | Alonzo Plutus V1 spend; 3-element Alonzo, Babbage and Conway submissions |
| Babbage (6) | Map outputs (datum option, script ref); body keys 16, 17, 18; witness key 6. | Babbage inline-datum and reference-script txs |
| Conway (7) | Body keys 19–22; witness key 7; map-form redeemers; optional tag 258 on sets. | Conway vote, proposal, V3 txs |
| Block vs submission | Same `RawTx` view. In a block, validity comes from `invalid_transactions`; `txBytes(i)` assembles `[body, wits, isValid, aux / null]`, or 3 elements before Alonzo. | per-era block; `txBytes(i)` hash equals the block-derived TxId |
| Era context | `RawBlock.of(bytes)` without an envelope is an error; `of(bytes, era)` with a mismatching envelope is an error | bare and enveloped blocks |

### 6.2 Outputs, datums, script refs

| Scenario | Handling | Fixture |
|---|---|---|
| Legacy array output `[addr, value]` / `[addr, value, hash]` | `RawOutput.datumHash` from element 2 | Shelley and Alonzo outputs |
| Map output `{0, 1, ?2, ?3}` | fields by uint key, in any order; a duplicate key is rejected (D6) | Babbage output with keys out of order; duplicate key → error |
| `datum_option [0, hash]` | `datumHash` | Babbage hash datum |
| `datum_option [1, #6.24(bytes)]` | `inlineDatum.span = embedded()`; hash = payload hash | inline datum with non-canonical map |
| Script ref `#6.24([type, script])`, types 0–3 | `RawScript(type, span)`; hash per D3 | reference script of each type; reference native script with indefinite arrays |
| Chunked tag-24 byte string | `embedded()` concatenates the chunks | synthetic chunked inline datum |
| Coin-only vs multi-asset value | value span kept as encoded | both forms |

### 6.3 Sets (tag 258)

The views accept tag 258 in any era, on purpose. Haskell's rules are ledger rules, not framing rules: it rejects the
tag for decoder version < 9 (`Decoder.hs:941-951`), Conway sets reject duplicates, and many Conway sets must be
non-empty (conway `TxBody.hs:199-258`).

| Scenario | Handling | Fixture |
|---|---|---|
| Tag absent | the plain array is accepted | pre-Conway and Conway txs |
| `d9 0102` | `untagIf(258)` | Conway tx |
| `da 00000102`, `db 0000000000000102` | numeric argument equals 258, so accepted (the julc #221 review finding) | synthetic, at every set position: inputs, collateral, reference inputs, required signers, certs, proposals, witness arrays |
| A tag other than 258 at a set position | error | synthetic |
| Tagged datums in the script integrity preimage | non-empty witness field 4 bytes taken as encoded, including the tag; `d9 0102 80` is empty and contributes nothing (D3) | Conway Plutus tx; `scriptDataHash` equals body key 11 |

### 6.4 Non-canonical CBOR

| Scenario | Handling | Fixture |
|---|---|---|
| Indefinite arrays and maps (body, witnesses, Data, metadata, native scripts) | frames end at `BREAK`; bytes preserved; hashes from spans | synthetic indefinite variant of every container position |
| Indefinite bytes/text (chunked) in Data and metadata | `byteString()` concatenates; the span keeps the chunks. **Gap:** the model re-chunks (`5f41aa41bbff` → `42aabb`) | Data with 32-byte chunks; metadata with chunked text |
| Non-minimal ints, lengths and tags (`18 00`, `98 04`, `da …`) | accepted and preserved. **Gap:** `TransactionUtil` returns `04` for header `98 04` | synthetic per position |
| Duplicate keys in records (body, witness set, outputs, tag-259 aux) | rejected by `field()` (D6) | duplicate body key → error |
| Duplicate keys in Data and metadatum maps | kept in spans in every era (Haskell pair lists). The CCL models keep the first position and last value (lossy, documented); hashes come from spans. | `a201000101` datum; metadatum map with a duplicate key |
| Map key order | preserved in spans; never sorted. **Gap:** model hashes re-sort | non-canonical datum and metadata maps |

### 6.5 Aux data, redeemers, validity

| Scenario | Handling | Fixture |
|---|---|---|
| Shelley aux: metadata map | aux span; hash = span hash; duplicate labels per D6 layer 2 | Shelley metadata tx |
| Allegra/Mary aux: `[metadata, scripts]` | aux span; scripts via `RawScript` | Mary tx with aux scripts |
| Alonzo+ aux: `#6.259{0..4}` | aux span; unknown keys (e.g. key 5, PlutusV4 on Haskell master) passed through | Babbage/Conway aux with scripts; synthetic key 5 |
| Legacy aux shapes in later eras | the Shelley map and the Allegra `[metadata, scripts]` shapes stay valid from Alonzo on (alonzo `TxAuxData.hs:304-312`); hash = span hash | Alonzo, Babbage and Conway txs with each legacy shape |
| `null` aux | `auxData()` empty | any tx without aux |
| Redeemers, array form `[[tag, idx, data, ex]*]` | `RawRedeemer` list. Conway still accepts this form; Haskell rejects it only from protocol version 12 (alonzo `TxWits.hs:551-560`). | Alonzo/Babbage tx; Conway tx in array form |
| Redeemers, Conway map form `{[tag, idx] => [data, ex]}` | `RawRedeemer` list in encoded order, duplicates kept. Haskell requires the map to be non-empty and keeps the last value for a duplicate key; consumers that evaluate apply last-wins. | Conway tx; synthetic duplicate key |
| Witness datums (Conway) | every entry kept. Haskell requires the set to be non-empty and allows duplicates until protocol version 12 (alonzo `TxWits.hs:336-347`). | Conway tx with a duplicated datum |
| `isValid = false` and `invalid_transactions` | `RawTx.isValid()`; `tx(i).isValid()` is `i ∉ invalidTxIndexes()`; out-of-range, duplicate and non-ascending indexes are rejected, as in Haskell (D6) | block mixing valid and invalid txs (assert each tx's flag); block with an empty index list (all valid); pre-Alonzo block (all valid); synthetic `[1, 1]` and `[2, 0]` → error |
| Script integrity: datums | non-empty redeemers with datums absent, `80`, `9fff` and `d9 0102 80`: all four give the same hash, with zero datum bytes (D3) | synthetic Alonzo, Babbage, Conway |
| Script integrity: language views | the caller passes views for the languages used only (D3) | single-language tx under protocol params with V1–V3 cost models; language supplied only by a reference script; an unneeded reference script of another language |
| Collateral return (body key 16) | `collateralReturn()`; output index = `outputs().size()` | invalid tx with collateral return |

### 6.6 Big integers and constr encodings

| Scenario | Handling | Fixture |
|---|---|---|
| Tags 2/3 bignums, including chunked payloads | `asBigInteger()`; the Data codec handles them | `c2 5f …ff` |
| Constr tags 121–127, 1280–1400, 102 | the Data codec handles all three; the walker treats them as generic tags | one of each, nested |
| Native script: unknown type; negative `m` in `[3, m, …]` | unknown type rejected; `m` decoded as signed (D4.3). **Gap:** the unknown type is silently dropped today | synthetic |

### 6.7 Deep nesting at maximum tx size

All cases decode, hash, re-encode and evaluate with the default stack and with `-Xint`, on a platform thread under
Java 17 and on a virtual thread under JDK 21 (section 10).

| Location | Depth in 16,384 bytes | Fixture |
|---|---:|---|
| Witness native script | 5,410 | the real preprod tx, plus a synthetic one at maximum size |
| Reference native script (`[0, script]` in tag 24) | 5,408 | synthetic |
| Aux-data native scripts | ≈5.4K | synthetic |
| Witness list datum | 16,262 | the 16,250-level list datum fixture |
| Witness constr datum | 5,420 | synthetic |
| Inline datum | 16,256 | synthetic |
| Redeemer data | 16,247 | synthetic, array and map form |
| Metadata (nested lists, nested maps, and a deep map key) | 16,256 | synthetic |

### 6.8 Malformed input

| Scenario | Handling | Fixture |
|---|---|---|
| Truncated header, argument, string or container | `CborRuntimeException` with the offset | every tx fixture cut at each byte |
| Reserved additional info 28–30; indefinite major type 0/1/6/7 | error | synthetic |
| `BREAK` outside an indefinite container, or after a map key | error | synthetic |
| Mismatched chunk type in an indefinite string | error | synthetic |
| Declared length larger than the remaining input (e.g. `9b ffff…`) | error before any allocation | synthetic; assert allocation stays flat |
| Trailing bytes in `of()` | error | synthetic |
| Random input | terminates with a result or a `CborRuntimeException`, never a hang or overflow | fuzz with a time and heap budget |

### 6.9 Block level

| Scenario | Handling | Fixture |
|---|---|---|
| Envelope `[era, block]`, bare block, tag-24 wrapper | all accepted; a block without an envelope needs `of(bytes, era)` (D2) | the same block in all three forms |
| Tx count alignment (bodies vs witness sets) | mismatch is an error | synthetic |
| Aux map keys | uint `< txCount`, else an error. Duplicates: the last value wins Shelley–Babbage, an error in Conway (D6) | synthetic duplicate-index Babbage block (last value used, `bodyHash()` unchanged) and Conway block (error) |
| Invalid-tx indexes | Alonzo+ only; absent before Alonzo; rules per D6 | Shelley and Conway blocks |
| Header vs body spans | `header()` and the four body spans are disjoint and cover the block | assert concatenation equals the original |
| Block body hash | `bodyHash()` equals the header's body hash, with 3 parts before Alonzo and 4 from Alonzo | one block per era |

## 7. Migration plan

**Phase 1: CCL.** Each step is one PR and is green on its own.

1. `common`: `CborSpan`, the single-pass iterative `DataItem` decoder and encoder, the `Map` subclass, and
   `CborSerializationUtil` routed through them. Differential tests against cbor-java.
2. `plutus`: the iterative Data codec and iterative `equals`/`hashCode`; the `PlutusData` entry points delegate to it.
3. `transaction-spec`, in this order:
   - the native script codec and `NativeScriptEvaluator`;
   - the `Era` constants (D7), then `RawTx`, `RawBlock` and the hashes;
   - `TransactionBytes` and `TransactionUtil`;
   - `Transaction.deserialize`, including the Shelley/Allegra shapes;
   - the remaining `CborDecoder.decode` call sites routed through `CborSerializationUtil`.
4. `transaction-spec`: the `TransactionSigner` splice, as its own PR.
5. Release as the next `0.8.0-preN`.

**Phase 2: Yaci.** Yaci `main` pins CCL 0.7.2 (`gradle/libs.versions.toml:2`); `next` already uses 0.8.0-pre4. Phase 2
lands on `next` and bumps CCL to the release that carries Phase 1. It then:

- replaces `CborSlice` with `CborSpan`, and `ArrayCborDecoder` with `CborSerializationUtil`;
- builds raw tx, datum, redeemer and witness extraction on `RawBlock`/`RawTx`, attaching spans and original-byte
  hashes (TxId, datum hash, script hash).

**Phase 3: Yano.** Delete `CborItems`, `CborReader`, `CborSlice` and the re-decoding of the CCL `Transaction`.
`RawTransaction` becomes a thin wrapper over `RawTx` that keeps only rule-specific accessors. `RawScript`/`RawAuxData`
move onto CCL views. `ccl-ledger-rules` `NativeScriptEvaluator` and `Timelock.evaluate` are replaced by the CCL
evaluator. `PlutusData.validate` iterates with `CborSpan`. Sync verifies `RawBlock.bodyHash()`.

**Phase 4: Julc.** Delete `OriginalTxBytes` and use `RawTx` for the body, witness datums, redeemer data and inline
datums. `PlutusDataCborDecoder` becomes iterative.

Each phase bumps its dependency only after the previous release is on Central.

**Follow-ups for the Yaci and Julc issues** (decided there, not in this ADR):

- **Yaci:** keep `DataItemIsolation` for semantic, non-depth failures, or remove it once the depth fallbacks are
  gone?
- **Julc:** can the module that holds `PlutusDataCborDecoder` depend on CCL (then use `CborSpan`), or must it stay
  CCL-free (then copy the frame pattern)?

## 8. Compatibility and release notes

**Public signatures:** no changes. All additions are new types, methods or `Era` constants. An exhaustive `switch`
over `Era` without `default` in consumer code must add the new cases.

**Behaviour changes** (all of them fixes):

- Deep inputs decode instead of throwing `StackOverflowError`.
- `TransactionBytes` round-trips indefinite or non-minimal outer arrays, and detects validity by element count.
- `TransactionUtil.extractTransactionBodyFromTx` and `getTxHash(byte[])` handle any header width. They also reject a
  tx whose later elements are malformed; today those elements are not read.
- `TransactionSigner.addWitnessToTransaction` leaves every witness field except field 0 byte-identical. The output
  differs from today only where today's re-encoding was lossy, and in those cases today's output could invalidate
  `script_data_hash`.
- `Transaction.deserialize` accepts Shelley 3-element txs with metadata and Allegra/Mary array aux data. It rejects
  unknown native script types and duplicate record keys.
- `CborSerializationUtil.deserialize` returns equal `DataItem` trees; maps are a `Map` subclass (`instanceof Map` is
  still true).

**Unchanged:** model hash methods (`getDatumHash`, `getScriptHash`, `getAuxiliaryDataHash`) still hash the
re-encoded model, and the models still deduplicate map keys. They are right for built txs. Received bytes should be
hashed through `RawTx`, `RawDatum` and `RawScript`; the release notes and Javadoc say so.

## 9. Performance

- **Walker paths.** One linear pass with no `DataItem` allocation, so `TransactionBytes`, `getTxHash` and
  TxId/datum hashing from views must be faster than today.
- **`Transaction.deserialize`.** It does not walk twice: the iterative `DataItem` decoder records offsets in its
  single pass (D4.1). The extra cost is the offset bookkeeping and heap frames in place of call frames.
- **Canonical encoding** of nested multi-entry maps copies each level's buffer once. The worst case is
  O(depth × size), bounded by the 16 KB tx size.
- **Harness.** A test-scope benchmark, excluded from CI, runs on the section 10 corpus. It compares, old path against
  new: `Transaction.deserialize`, `TransactionBytes`, `getTxHash`, and model `getDatumHash` against `RawDatum.hash`.
  It reports median and p99 time plus allocated bytes per tx.
- **Budgets** (median):
  - walker-only paths: no slower than today;
  - `Transaction.deserialize`: at most 20% slower;
  - every other path: at most 10% slower.

  Results go in the Phase 1 PRs.

## 10. Testing

- **Real trigger.** Preprod tx `f90dce57…24c9` and block 5183974, fetched once and committed under
  `transaction-spec/src/test/resources`. The tests check decode, TxId, native script hash, evaluation, block body hash
  and the `TransactionBytes` round trip. Today Yaci has this tx only in a live integration test (`BlockFetcherIT`).
- **Depth fixtures at maximum tx size.** Every row of section 6.7, including the 16,250-level list datum. Each one goes
  through decode, re-encode, original-byte hash and model hash (equal for canonical input), and evaluation for native
  scripts.
- **Original bytes in, original bytes out.** For every span a view exposes, `span.bytes()` equals the matching slice
  of the input. Concatenating the envelope parts reproduces the tx and the block exactly. `TransactionBytes` round-trips
  every fixture.
- **Signer splice.** The output is byte-identical to today's on canonical inputs, including at the 23/24, 255/256 and
  65,535/65,536 count boundaries. On non-canonical inputs, the untouched fields are byte-identical.
- **Differential tests on a corpus.** A few hundred committed real txs and blocks, covering every era and every
  scenario in section 6, from mainnet, preprod and preview:
  - the new `DataItem` decoder gives `equals` trees to cbor-java;
  - model objects equal those from the old path;
  - view hashes equal the on-chain values: TxId, datum hashes, script hashes, aux data hash (body key 7),
    script integrity hash (body key 11) and block body hash (header).

  An optional integration test runs the same checks over a block range from a local node.
- **Malformed input and fuzzing.** Every section 6.8 row, every rejection in D6, a truncation sweep over all fixtures,
  and a seeded fuzz loop with time and heap assertions.
- **Threads and modes.** The normal build targets Java 17, so the depth fixtures run there on a platform thread with
  the default stack, and in a forked JVM with `-Xint`. A separate JDK 21 CI job, shaped like `txflow-java21`
  (`build.yml:46-63`), runs the same fixtures on a virtual thread; that test is `@EnabledForJreRange(min = JAVA_21)`
  and creates the thread reflectively, so it compiles on 17. No test sets `-Xss`.

## 11. Alternatives considered

| Alternative | Verdict |
|---|---|
| `-Xss` or a dedicated big-stack thread | Rejected as the fix. `-Xss` is a JVM-wide deployment setting that every consumer, including native images, must remember, and the safe value still depends on JIT state. A big-stack thread needs a hand-off at every entry point. Acceptable as a stopgap until Phase 1 ships. |
| Depth cap | Rejected: Haskell has none, so any cap either rejects valid txs (gouroboros at 256 in #2244, Scalus at 1,000) or is too high to prevent overflow on cold or `-Xint` paths (CCL fails at about 2.3K). |
| Interim implementation in Julc | Rejected: Yaci cannot depend on Julc, so the work would be done twice, and Julc's API would break when CCL lands. |
| New separate library | Rejected: every consumer already depends on CCL. The fix belongs where the recursive code lives. |
| Patch or fork cbor-java | Rejected: CCL's model and encoders sit on top of it anyway, and cbor-java 0.9 has no maintained fork. The `DataItem` codec in CCL is small and fully tested against it. |
| Memoize original bytes in model objects (`MemoBytes` style) | Deferred: it changes the model classes and their equality. The views already give original bytes without touching the models. |

## 12. Risks

- **R1: `DataItem` parity.** The new decoder must match cbor-java's quirks (`BREAK` items, chunked flags, tag-30
  rationals, duplicate-key semantics). Mitigation: differential tests on the corpus plus fuzzing.
- **R2: Deep keys.** cbor-java `Array.hashCode` and Lombok `equals`/`hashCode` recurse. Mitigation: the CCL `Map`
  subclass keys by re-encoded bytes, and Data `equals`/`hashCode` are iterative. Model `toString()` stays recursive.
- **R3: `Transaction.deserialize` budget.** The single-pass offset recording must keep it within 20% (section 9).
  Fallback: record offsets only for the envelope and the witness fields.
- **R4: Canonical encoding cost** for nested multi-entry maps (section 9). It is bounded by tx size; the benchmark
  watches it.
- **R5: Linux 1 MB default stack.** Today's failure thresholds are about half the macOS values, so the current
  exposure is larger than the measurements show.

## 13. References

- Probe from the 2026-10-01 audit: `results-warm.txt`, `results-xint.txt`, `results-cold.txt` (synthetic txs shaped
  like the preprod trigger; not committed).
- Yaci `next`: `core/.../serializers/util/CborSlice.java`, `core/.../util/ArrayCborDecoder.java` (#198, #199, #200).
- Yano: `feat/conway-ledger-rules` (PR #155): `ledger-rules/.../util/CborItems.java`, `.../conway/tx/CborReader.java`,
  `CborSlice.java`, `RawTransaction.java`, `Timelock.java`. `main`: `ccl-ledger-rules/.../NativeScriptEvaluator.java`.
- Julc PR #221: `julc-cardano-client-lib/.../eval/OriginalTxBytes.java`; commit 7958c4da (any-width tag 258, not yet
  merged).
- Haskell cardano-ledger: `MemoBytes`; the decoder citations in D2, D3, D4 and D6.
- Pallas #802, #807, #808, #810, #814; Amaru #1380; gouroboros #2244, #2252; dingo #4291.
