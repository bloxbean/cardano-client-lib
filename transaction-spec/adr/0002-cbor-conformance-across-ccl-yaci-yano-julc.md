# ADR 0002: CBOR Conformance Across CCL, Yaci, Yano and JuLC

**Status**: Proposed
**Date**: 2026-11-01
**Risk**: High. Decoders decide what a node accepts, and CCL's decoders have many consumers.
**Issues**: bloxbean/cardano-client-lib#717, #505, #397; PR #734; bloxbean/yaci#113, #214, #191, #152;
bloxbean/yano#202 (closed)
**Related**: ADR 0001 and ADR 0003 in this directory; Yano ADR-060 (node-diversity reports)
**Modules**: CCL `common`, `plutus`, `transaction-spec`, `function`; Yaci `core`; Yano `ledger-rules`,
`ledger-conformance`; JuLC `julc-cardano-client-lib`

This ADR continues ADR 0001, so it lives in the same directory. ADR 0001 built the foundations: the span walker, the
raw views, the iterative codecs. This ADR uses them to work towards 100% on the node-diversity `cbor-decoding` scenario,
and sets which project owns which part of CBOR parsing.

**D8's serializer boundary and D9 are revisited by ADR 0003.** D1-D7 and D10 stand unchanged.

## 1. Context

**Two dataset revisions appear in this ADR.** The active one is `95e9e7c2`, and D1a holds its release criteria. Sections
1, 1.6, 2 and 6's score tables were measured on `6cd8c928` (`conway-123-100`, 9200 samples). Those numbers are historical
and are not release criteria.

### 1.1 Goal

The [node-diversity site](https://pragma-org.github.io/node-diversity-conformance/) scores Yano on the
`cbor-decoding` scenario with the `conway-123-100` corpus of
[cardano-cbor-dataset](https://github.com/r2rationality/cardano-cbor-dataset) (revision `6cd8c928`): 866 valid and
8,334 invalid samples. The target is 100%.

Yano v0.1.0-pre19 scores 8912/9200 (96.87%). **All 288 misses are valid samples.** Every invalid sample is already
rejected. So the work loosens decoders, and the risk is that it also lets invalid input through.

### 1.2 Measurements

Everything below ran Yano's report (`:ledger-conformance:nodeDiversityReports`, Yano `main` `dc1a2ac4e`, yaci
0.5.0-pre15, julc 0.1.0-pre20) with CCL substituted through an init script. "Valid" counts samples that decode through
both of Yano's decoders and re-encode to the reference.

| CCL / harness | Valid | Generated invalid rejected | Zapped rejected | Total |
|---|---:|---:|---:|---:|
| Released v0.1.0-pre19 report (CCL 0.8.0-pre7) | 578/866 | 1634/1634 | 6700/6700 | 8912 |
| PR #734 head `0a16037d` | 815/866 | 1634/1634 | 6700/6700 | 9149 |
| PR #734 and the harness fix in D7 | 829/866 | 1634/1634 | 6700/6700 | 9163 |

With PR #734, each decoder measured alone:

| Decoder | Valid accepted | Invalid rejected |
|---|---:|---:|
| Yano strict reader (`RawTransaction` family) | 864/866 | 8334/8334 |
| CCL model decoders (yaci for headers) | 864/866 | 5093/8334 |

The strict reader alone rejects every invalid sample, so PR #734's newly accepted encodings do not reach the node.
This also retires ADR-060's "8039/8334", which predates yano#202 (fixed by yano#216).

### 1.3 The remaining 37 misses

With PR #734 and D7, 37 valid samples still miss. A probe dumped input, expected and actual bytes for each, and each
proposed fix was then simulated against the expected bytes. The "Confirmed" column is that simulation. The code fixes
themselves are not built yet.

CCL's own gate (G1) sees 22 more: `datum_option` samples whose inline datum CCL's encoder rewrites. Yano's report does
not count them, because it re-encodes that rule from its strict reader, which keeps the original bytes. They need the
same fix as the 3 inline datum samples below (D6.3), so they add work to G1's list but not to Yano's score.

| Count | Rule | Cause | Confirmed by | Fix |
|---:|---|---|---|---|
| 14 | `protocol_param_update` | All 14 use keys 25-33, which CCL drops. 12 also have keys 2, 3 or 7 at or above 2^31, which wrap in `Integer` fields and then fail on re-encoding | Input, normalized (which reduces tag-30 rationals), equals expected on 14/14 | D4, D5 |
| 13 | `gov_action` (12), `proposal_procedure` (1) | `Map AccountAddress Coin` keeps Haskell's `Ord` order, script credentials first; CCL sorts by bytes | Sorting by `(network, script first, hash)` gives expected on 13/13 | D6.1 |
| 7 | `transaction_output` | 6 have a script reference `[_ type, script]` that Haskell re-encodes with a definite outer array and the inner bytes kept. 3 have an inline datum Haskell keeps byte for byte and CCL re-encodes. 2 samples have both | Byte comparison on 7/7 | D6.2, D6.3 |
| 2 | `auxiliary_data` | The dataset and the production decoder disagree | Source reading, section 1.4 | Q1 |
| 1 | `certificate` | Pool retirement epoch at or above 2^63 fails on re-encoding (`PoolRetirement.java:52` reads it with `toLong`) | Stack trace | D4 |

The rationals need no CCL change: the dataset's normalization reduces them (vector
`0012-tag-30-rational`, `CborNormalizer`).

### 1.4 The two disputed samples

`auxiliary_data/valid/00003-a457defa17a4ecca` and `00025-756519256dadb3b0` are indefinite Allegra aux data,
`[_ metadata, scripts]`. The dataset calls them valid. Production Haskell rejects them.

At the revision the dataset pins (cardano-ledger `6176e413b2`) and at `c8cbb661d3` (2026-04-10):

```haskell
-- eras/alonzo/impl/src/Cardano/Ledger/Alonzo/TxAuxData.hs: what a node runs below decoder version 12
decodeAllegra =
  decode
    ( Ann (RecD AlonzoTxAuxDataRaw)
        <*! Ann From
        <*! D
          (sequence <$> decodeStrictSeq decCBOR)
        <*! Ann (Emit Map.empty)
    )

-- libs/cardano-ledger-binary/.../Decoding/Coders.hs
decodeCount (RecD cn) n = decodeRecordNamed "RecD" (const n) (pure (n, cn))
```

`decodeRecordNamed` checks the end of an indefinite list before any field is read, so `[_ a, b]` fails.

The production source defines only `DecCBOR (Annotator (AlonzoTxAuxDataRaw era))`. The dataset's rule decodes plain
`TxAuxData` (`mkRuleCheck @(TxAuxData ConwayEra)`, `app/LedgerRules.hs:175`), and the only instance for that is the test
library's orphan (imported at `app/LedgerRules.hs:77-80`), whose decoder differs:

```haskell
-- eras/alonzo/impl/testlib/Test/Cardano/Ledger/Alonzo/Binary/Annotator.hs:132-134
decodeAllegra =
  decodeRecordNamed "AlonzoTxAuxDataRaw" (const 2) $
    AlonzoTxAuxDataRaw <$> decCBOR <*> decCBOR <*> pure Map.empty
```

So cardano-node's 9200/9200 comes from a decoder the node does not run. Passing these two samples honestly needs the
corpus to change. See Q1.

### 1.5 Who parses CBOR today

| Project | Code | State |
|---|---|---|
| CCL | `CborSpan`, `DataItemDecoder`, `RawTx`, `RawBlock` | ADR 0001's D1-D8 are all in code, including the "deferred" D4. Its status line and section 7 are stale |
| CCL | Model `deserialize` methods | Lenient, sometimes lossy. No era, version or strict parameter. Second CDDL reader beside the views |
| Yaci | `CborSlice`, `ArrayCborDecoder` | Duplicate `CborSpan` and `DataItemDecoder`. 0 uses of `CborSpan`, though `next` pins CCL 0.8.0-pre7, which has it |
| Yaci | `model/serializers/*` | Third CDDL reader. Returns Yaci's public models; Yano imports them in 97 main files, and Yaci Store in 222 (at `524d7deca`). Unlike CCL's, it also covers Byron and node-to-client results |
| Yano | `org.yanoproject.ledger.rules.conway.tx` (4,273 lines) | Strict reader. Already built on `CborSpan` and `RawTx`. Adds `CborCursor` because `CborSpan` exposes neither heads nor chunks |
| JuLC | `PlutusDataCborDecoder` / `Encoder` in `julc-core` | CCL-free by choice. Agrees with Yano's Data rules on every case compared |
| JuLC | `OriginalTxBytes` in `julc-cardano-client-lib` | Fourth slicer, recursive. Duplicates `RawTx` |

Yaci fails on valid encodings too (yaci#113): its size checks count the BREAK that `ArrayCborDecoder` keeps, the same
root cause as #717.

Some positions are read with the same bug in more than one place. `new BigInteger(bytes)` reads a bignum as signed,
and tag 3 is taken as `-n`, not `-1-n`: `CborSerializationUtil.java:33,35`, `Value.java:45,48`, and the same code in
Yaci. `CborSpan.asBigInteger` and `BigIntPlutusData` are correct.

### 1.6 What 100% does and does not prove

In Yano's default configuration, the scenario measures transaction admission. Sync decodes with Yaci, shadow sync is
off and header validation is `none`. ADR-060 section 6 says so already. Reaching 100% does not by itself make the sync
path conformant, which is why D8 stands on its own.

## 2. Scope

In scope:

- Every change needed for 100% on `conway-123-100`, the old pin. D1a sets what this stack must meet on the active pin.
- One owner for each layer of CBOR parsing, and what moves there.
- Regression and compatibility gates for CCL's consumers.

Out of scope:

- Moving Yano's strict reader into CCL (Q3).
- Replacing Yaci's public models or serializers wholesale (D8).
- Byron, and node-to-client decoders. CCL has none.
- Dijkstra. The dataset has a corpus (11,900 samples) but the scenario does not use it. `RecD` records accept both
  lengths from decoder version 12, so `definiteItems` will need a version then. It is not added now.
- Byte-exact model round trips in general. Spans stay authoritative (ADR 0001 D4).

## 3. Threat model and trust assumptions

- **Untrusted:** every byte from a peer, a mempool client or a chain sync source.
- **Accepting what Haskell rejects** can split a node from the network. **Rejecting what Haskell accepts** stalls it.
  Both are consensus risks, so loosening a decoder is as sensitive as tightening one.
- **Source of truth:** cardano-ledger's production decoders. The corpus is evidence, not authority. Where they
  disagree, this ADR follows production and takes the disagreement upstream.
- **CCL's other users** (wallets, builders, backends) rely on today's bytes and API. Conformance must not change a
  transaction someone builds.

## 4. Pinned references

| Reference | Version |
|---|---|
| cardano-cbor-dataset | `95e9e7c296ca1b641c8d99af2ab7c286a90caebf` since r9; section 1's measurements used `6cd8c928665bbafe90e02066ebb4620e38f051de`, corpus `conway-123-100`. The repository has no licence file, so samples are fetched, never vendored |
| cardano-ledger | `6176e413b2be9880c23cedbdaa899f9752693b22` (the dataset's pin); `c8cbb661d3` as a second reading |
| Conway CDDL | `eras/conway/impl/cddl/data/conway.cddl` at the same revision |
| Decoder version | The dataset decodes at `eraProtVerHigh` (11). ADR 0001 records production as `ProtVerLow` (9). A search of `eras/` and the binary and core libraries at `c8cbb661d3` found no decoder switch at 10 or 11, so the two agree here |

## 5. Decisions

### D1. What 100% means

100% means every sample of the pinned corpus, with no sample masked in the harness and no behaviour that production
Haskell rejects. On the old revision that was 9200/9200, and this plan reached **9198/9200**. The other two samples
needed Q1. This is still the goal, but it is no longer this stack's release criterion. D1a is.

A new corpus revision is a deliberate pin change, as ADR-060 already says.

### D1a. Release criteria on the active pin (r11)

The active pin is `95e9e7c296ca1b641c8d99af2ab7c286a90caebf`. Its Conway dataset has 42,675 samples in 97 rules: 10,914
valid (10,681 `valid`, 233 `manual-valid`) and 31,761 invalid. This stack does not reach 100% on it, and is not required
to. It is released when all of these hold:

| Check | Scope | Criterion | Measured |
|---|---|---|---|
| CCL gate (G1) | The 2,705 valid and manual-valid samples of the 19 rules with a model | Exactly the 14 listed misses on #745, all `auxiliary_data` (Q1). The list can only shrink | Yes, on #745 at `50a9756c` |
| Yano's report (G6) | All 42,675 samples | At least 42,215: 10,457/10,914 valid and 31,758/31,761 invalid. Its misses are a subset of the 460 below | On a local build of #745 at `161b00f9`, with Yano's harness on the new writers. To be repeated on the release |
| Strict reader alone (G6) | The 31,761 invalid samples | At least 31,679, and no sample it rejects at the baseline is lost. Step 8 records that baseline per sample | The count only, on the same build |

The 460 accepted misses, as classified on that build:

| Samples | Rules | Owner | Reason |
|---:|---|---|---|
| 388 | `header_body` 101, `header` 100, `operational_cert` 87, `kes_period` 53, `block_number` 47 | Yano | `ShelleyHeaderView` |
| 64 | `auxiliary_data_array` 50, `auxiliary_data` 14 | Yano | The strict reader refuses Allegra's indefinite form. The 14 are also on G1's list (Q1) |
| 3 | `%constr<plutus_data>` | CCL | A Plutus constructor's form |
| 2 | `%multiasset<positive_coin>` | Yano's harness | An empty multi-asset |
| 3 | `block_number` and `kes_period` `0001-negative`, `certificate` `pool-owners-repeated` | Yano | Invalid samples accepted |

These are accepted for this stack and left as follow-ups, not silently dropped. The 3 invalid samples mean invalid
rejection is not 100% on this pin, so I2's old wording no longer applies.

CI pins the corpus per step. #741 to #743 gate on `6cd8c928`, with 59, 58 and 44 listed misses. #744 moves CI to
`95e9e7c2`, with 86 listed, and #745 leaves 14.

### D2. One owner per layer

| Layer | Owner | Holds | Others |
|---|---|---|---|
| L0 CBOR primitives | CCL `common` | `CborSpan`, `CborHead`, `DataItemDecoder`, scalar and bignum reads | Yaci and JuLC's CCL module delete theirs. `julc-core` keeps its own |
| L1 Accepted shapes and models | CCL `transaction-spec`, `plutus` | Models and `Raw*` views. Accept everything production Conway accepts | Yaci's serializers stay as adapters to its public models (D8) |
| L2 Haskell-exact rejection | Yano `ledger-rules` | Strict reader. Rejects what Haskell rejects, at a given decoder version | Stays (D9) |
| L3 Sync tolerance | Yaci | Fail-or-continue policy; Byron; node-to-client | Stays. Policy, not format |

CCL holds most of the parsing: every format-level primitive, and the model every consumer can share. Two things do not
move, for the reasons in D8 and D9.

### D3. Merge PR #734 first

PR #734 fixes the BREAK at its source, unknown cost-model languages, tag-30 over indefinite arrays, the uint32 protocol
version and empty DNS names, for +237 samples. It is the first code step.

Before merging:

- Fix `CostMdls.java:156`. Indefinite cost arrays from cbor-java's own decoder fail on their trailing BREAK:
  `a1009f0102ff` gives `Invalid cost : BREAK` (reproduced). Check the other model decoders on cbor-java trees too,
  because callers such as Yaci's `Datum.java:52` pass them to CCL.
- Merge current `master` into it, with no force-push.

PR #734 also changes what a decoded tree holds. Without the BREAK item, a plain cbor-java or Yaci encoder writes
`9f0102` for an array read from `9f0102ff`, which is truncated CBOR. CCL's own encoder writes `9f0102ff`. The direct
`CborEncoder` uses in Yano and JuLC main code do not take CCL-decoded trees, but CCL cannot see its external users, so
this goes in the release notes. The trailing item cannot be kept for those encoders, because it is the root cause of
#717 and yaci#113.

### D4. No silent narrowing

CCL's `toInt` and `toLong` wrap, and a wrapped value that stays non-negative passes unnoticed. Every decoder moves to
one of three treatments:

| Treatment | Meaning | Positions |
|---|---|---|
| Widen | Hold the full range in a wider type | `ProtocolParamUpdate.maxBlockSize` (2), `maxTxSize` (3), `maxEpoch` (7) to `Long`. New keys 28, 29, 32 are `Long`. `Redeemer.index` map form reads the whole `BigInteger` it already holds |
| Unsigned | Keep the `long`, read and write it as unsigned 64-bit | `PoolRetirement.epoch`, `ttl`, `validityStartInterval`, `ConstrPlutusData` alternative. `UpdateCommittee` term, which also widens from `Integer` to `Long` (r8) |
| Exact or throw | Throw rather than wrap, because the type cannot widen without breaking users | `Update.epoch`, certificate and action discriminants |

Unsigned keeps the public type. A value at or above 2^63 reads as negative through the getter, but round-trips exactly.
No ledger-legal epoch or slot gets there; Haskell decodes the value and then fails a ledger rule.

"Exact or throw" narrows acceptance for a value that today wraps silently. A throw is the better failure, but it is a
real gap against the accept rule, so these positions are listed in the release notes. None appears in the corpus's
failures.

`getBigInteger` and `Value` move to one correct bignum read, shared with `CborSpan`.

### D5. Complete `ProtocolParamUpdate`

Add keys 25-33 with their CDDL types, plus `PoolVotingThresholds` and `DRepVotingThresholds` classes, in `deserialize`,
`serialize` and the builder. Update the native-image reachability metadata.

Today a key-30-only update decodes to an empty object and re-encodes as `a0`, and the builder cannot express these keys.

Unknown keys stay ignored, as ADR 0001 D6 decided. After this change, no key Conway defines is lost.

### D6. Three re-encoding fixes, and no general promise

**D6.1 Ledger key order.** A map keyed by a ledger type is written in Haskell's `Ord` order, decided in r2 (Q4), unless
a hardware wallet can sign that map. There CIP-21 decides, as a wallet refuses any other order and the ledger accepts
either (r7).

| Map | Haskell key | `Ord` order | CCL today | Change |
|---|---|---|---|---|
| `TreasuryWithdrawalsAction` | `AccountAddress` | Network, script before key, hash | Encoded bytes, so key accounts first | Yes |
| Body key 5 | `AccountAddress` | As above, but CIP-21 applies: encoded bytes, so key accounts first | Encoded bytes | No |
| `UpdateCommittee` new members | `Credential` | Script before key, hash | Encoded bytes, so key first | Yes |
| `VotingProcedures` outer map | `Voter` | Committee, DRep, pool; script before key within the first two; hash | Encoded bytes, so tag order 0-4 | Yes |
| `VotingProcedures` inner map | `GovActionId` | Transaction id, index | Encoded bytes are length-first, so an index of 24 or more sorts after smaller indexes under any transaction id | Yes |
| Redeemers, cost models, metadata labels | `PlutusPurpose`, `Language`, `Word64` | By tag or number | Encoded bytes give the same order | No |
| Policy ids, in outputs and mint | `PolicyID` | Bytes; CIP-21 agrees, as all have 28 bytes | Hex text in outputs; mint unsorted when non-canonical | Sort by bytes in both |
| Asset names | `AssetName` | Unsigned bytes, then length; CIP-21 applies: length, then unsigned bytes (Q5) | Length, then signed bytes | Unsigned |

The mechanism is the one `Value` and `MultiAsset` already use: the model sorts its entries with a `Comparator` and puts
them in the existing `SortedMap`, which both encoder modes write as inserted (`CustomCborEncoder.java:429`). `SortedMap`
and the encoder do not change. The comparators go in `Comparators`, one per key type. The credential part of
`WithdrawalUtil.getWithdrawalComparator`, in `function`, moves down with network added, and `function` delegates.

Checked with a prototype against PR #734's build: five reward accounts sorted this way came out in Haskell's order with
`canonical` true and false, and when nested in a canonical parent. The corpus samples simulated 13/13 (section 1.3).

The corpus only measures the treasury row: 13 of its 14 treasury samples mix key and script accounts, no Conway sample
is an `UpdateCommittee`, and the Conway set has no valid `transaction_body` sample. The other rows follow the same rule
by decision, not by score.

This changes the bytes of a *built* transaction whose treasury, committee or voter map mixes key and script keys, and
its hash. The transaction stays valid: Haskell's decoder does not require sorted keys here, as the 13 treasury samples
show, since their input is unsorted. D10's I5 records the exception.

The rule for a map a hardware wallet can sign comes from CIP-21: "The keys in every map must be sorted from lowest value
to highest", in RFC 7049's sense, and it names withdrawals and multi-assets. Ledger's app enforces this for withdrawals,
policies and asset names (`LedgerHQ/app-cardano`, `src/transaction/tx_processing.c`,
`SWO_TX_PARSING_FAIL_CANONICAL_ORDER`), as Trezor's does (`signer.py`, `HashBuilderDict`). So:

- Body withdrawals stay in byte order. Byte order and Haskell's differ only when a transaction's withdrawals mix keys
  and scripts, and then it cannot meet both. The ledger accepts this order, confirmed on a Yaci DevKit chain by
  the mixed withdrawals test of CCL PR #727. Mainnet had no such transaction among 18,508 with two or more withdrawals,
  up to 2026-03-26, so the case is narrow in either direction.
- `Withdrawal.serialize` writes this order, and treasury withdrawals use `Withdrawal.serializeInLedgerOrder`.
- `MultiAsset`, `Value` and `TransactionOutput` keep CIP-21's asset order in `serialize` and add
  `serializeInLedgerOrder`, for a caller that must match Haskell's bytes, such as a conformance report. Transaction
  building does not use it.
- Sets are written in key order, as Haskell writes them: pool owners by hash, removed committee members by
  `Comparators.CREDENTIAL`.
- Treasury withdrawals and `UpdateCommittee` do not meet CIP-21 and need not, as CIP-21 forbids proposals in a hardware
  wallet transaction. Voting does not conflict either, as CIP-21 allows "a single voter with a single voting procedure",
  so neither voting map has two keys to order. If CIP-21 ever allows more, the voting maps must be looked at again.

Redeemer indexes are separate from all this. The ledger derives them from its own map order, whatever the wire order,
so `Comparators.WITHDRAWAL` stays script first for `Reward` indexes, as PR #727 made it. Sets encoded as arrays, such as
inputs, are not maps and stay as they are.

**D6.2 Script references.** `TransactionOutput.serialize` writes `82` plus the two original items when the stored
`scriptRef` is an indefinite two-item array. The field keeps the raw bytes, so its meaning does not change. The inner
script bytes stay exact: sample `transaction_output/valid/00019` has an indefinite native script inside, which Haskell
keeps while re-encoding the outer array. Haskell also re-encodes the type integer and a Plutus script's byte string
head. No corpus sample has such forms, and D6.2 leaves them as they are.

**D6.3 Inline datum bytes.** `TransactionOutput` keeps the original tag-24 payload at decode, with the datum object
decoded from it. `serialize` writes the payload only when both of these hold. Otherwise it encodes the model.

1. `inlineDatum` is that same object. This covers replacing the root: model equality ignores map order and decoding
   collapses duplicate keys, so `a201000101` and `a10101` compare equal, and only identity tells a replacement apart.
2. Its datum key equals that of a fresh decode of the payload. The key is the model's values and pairs in order plus,
   for each map, the pairs lost in decoding: a key the CBOR repeats, or CBOR keys that differ but are one Plutus key,
   such as `a0` and `bfff`. This covers everything inside the mutable datum:
   - An in-place reorder changes the order of the pairs from `a201020304` to `a203040102`.
   - A replaced child can leave the encoding unchanged, so the dropped entries are needed as well. Replacing the child
     of `81a201000101` with `a10101` keeps the encoding at `81a10101` and the root object the same, as reproduced on
     CCL's build. Only the dropped entry, which the fresh decode has and the replacement does not, tells them apart.

CCL's decoded CBOR map (`EncodedKeyMap`) records what each repeated key replaced, and `PlutusDataMap` keeps that for
this comparison alone. The key also covers repeats nested inside a repeated key or value. `getMap()` and encoding do not
change, and entries are recorded only when a decoded map has a duplicate key, so other data costs nothing. Encoding a
model map with its duplicates was rejected: `getMap()` is mutable, so stale duplicates could be written after a change,
and it would change the bytes of every re-serialized map with duplicates, which D6.3 does not need.

A map keeps its record for life, so one cleared and refilled with the same entries counts as unchanged. A child replaced
by one of equal value in another encoding also keeps the original bytes, as the model cannot tell them apart.

`setInlineDatum`, `toBuilder` and a deep copy fall back to the model, which is today's behaviour. A new
`TransactionOutput.copy` keeps the bytes, and `MinAdaCalculator` uses it so that it measures what the output writes.

A built output has no retained bytes and encodes as it does today. The fields are excluded from `equals`, `hashCode` and
JSON. The cost is one decode, paid only for decoded outputs at `serialize`.

This also fixes the case behind #397 for inline datums, where re-serializing a decoded output changed its bytes.

**No general promise.** A model round trip stays non-exact everywhere else. `Transaction.serialize` still sorts Plutus
maps, for example. Anything that needs the original bytes reads spans, as ADR 0001 says.

### D7. Harness corrections (Yano)

Both are bugs in the measurement, not masking.

1. **`CborRule.java:154` uses canonical encoding.** `CborSerializationUtil.serialize(item)` defaults to
   `canonical=true`, which sorts Plutus maps. Plutus `Data.Map` is a list of pairs, whose order Haskell keeps. Changing
   to `serialize(item, false)` moved `redeemer` from 33/47 to 47/47, with nothing else changed.
2. **Encoder exceptions count as rejections.** `decodeAndEncode` runs both, and one `catch` treats any exception as
   rejected. An encoder failure on an invalid sample then scores as a correct rejection. It has no effect today,
   because the strict reader rejects all 8,334, but it would hide a regression.

Add a diagnostic column for Yaci's decoders on the same samples, so the sync path has a number as well (section 1.6).
Refresh ADR-060's stale numbers.

### D8. Yaci moves to CCL below its models

| Change | Why |
|---|---|
| Yaci's `CborSerializationUtil.deserialize*` and `serialize*` both delegate to CCL, in the same change | One file serving 93. With PR #734, trees stop holding BREAK, which should remove the size-check failures of yaci#113. Yaci's encoders need that BREAK and would write truncated CBOR, so the decoder cannot move alone |
| `CborSlice` to `CborSpan` (7 files) | Equivalent walkers. CCL gains prefix navigation, the one real gap |
| `TransactionBodyExtractor`, `AuxDataExtractor` to `RawBlock` / `CborSpan` | Supersedes yaci#214 |
| Scalar reads delegate to CCL | Removes the shared bignum bug |
| Native script hash from spans | yaci#191 |

**Not** a wholesale swap to CCL models. Returning CCL models breaks Yano and Yaci Store. Keeping Yaci's models behind
CCL decoding needs a mapper per field, roughly the same code plus a dependency on CCL's semantics. The facade change
above reaches yaci#113 without either.

Yaci's hashing and framing read these re-encodes, so the change needs regression tests with nested indefinite arrays,
and a differential test of the old and new encoders on the same trees. G3 covers real blocks.

Byron, node-to-client decoders and Yaci's fail-or-continue policy stay. A consumer that continues after a parse
failure keeps doing so. This is the one place stricter decoding could hurt: `RawBlock` is eager and rejects unknown
eras, where Yaci today attempts a parse. So Yaci adopts the walker first and takes `RawBlock` only after G3.

Yaci Store pins yaci 0.4.7. A backport to that line is separate, and is not assumed here.

### D9. The strict reader stays in Yano; its primitives move

The `Raw*` readers import rule types, carry rule accessors and hard-code "below decoder version 12". Moving them would
reverse ADR 0001's section 2 and D6, and force a decoder-version parameter into CCL, for a single consumer.

What moves is what makes Yano write its own walker. `CborSpan` gains public head and chunk accessors and linear child
enumeration, and `CborCursor` goes away. The `PlutusData` validator stays: JuLC cannot use a CCL one, so there is no
second consumer.

JuLC (D2):

- `OriginalTxBytes` is replaced by `RawTx` in `julc-cardano-client-lib`.
- `julc-core` stays CCL-free. It needs nothing beyond cbor-java, and that independence is worth keeping for a
  consensus-critical `serialiseData`.
- A shared Data vector set is added, so CCL's documented leniency and the JuLC and Yano rules stay deliberate.

### D10. Compatibility and regression gates

**Invariants**

| ID | Invariant | Constrains | Tested by |
|---|---|---|---|
| I1 | CCL accepts every valid sample in G1's scope, except those in the dispute list (Q1) | D3-D6 | G1 |
| I2 | At every step, the strict reader alone and the node's pair of decoders reject no fewer invalid samples than the baseline for that step's pin. On the active pin that is D1a's 31,679 and 31,758 of 31,761. On the old pin it was 8334/8334 | D1a, D3-D6, D9 | G6 |
| I3 | A decoded value is never silently different from the encoded value | D4 | Unit tests at each boundary, G1 |
| I4 | No harness special-cases a sample | D1, D7 | Review |
| I5 | A transaction built through the API serializes to the same bytes as before, except for order in the treasury, committee and voting maps and the pool owner and removed committee member sets D6.1 changes, and for asset names the signed comparison misordered (Q5). A body a hardware wallet can sign meets CIP-21's map order | D4-D6 | G2, `Cip21BodyOrderTest` |
| I6 | Existing builder calls compile unchanged, including `null` arguments, apart from `UpdateCommittee.newMembersAndTerms` (r8) | D4, D5 | G2, G5, a compile test with `null` |
| I7 | Every block Yaci parses today still parses, with the same model | D8 | G3 |
| I8 | Retained bytes are never written for a datum they no longer represent, at any depth. An unchanged decoded datum keeps them | D6.3 | Mutation tests |

**Gates**

| ID | Gate | Runs |
|---|---|---|
| G1 | CCL CI fetches the pinned corpus and runs valid samples through model decode and re-encode. The expected-miss list shrinks with each milestone (section 6) and must not grow. A listed sample that passes fails the gate, so the list stays exact. It holds the dispute only after M4 | Every CCL PR touching decoders |
| G2 | Golden bytes: a fixed set of built transactions compared with bytes from the last release. `TransactionModelParityTest` and the codec differential tests stay green | CCL CI |
| G3 | `tools/chain-parse-check` full replay on mainnet, preprod and preview, before and after, for CCL and for Yaci | Before each milestone release. Manual, by design |
| G4 | Native-image `ReachabilityMetadataGuard` | CCL CI |
| G5 | Downstream suites against a CCL snapshot: Yaci `core`, JuLC, Yano `ledger-rules`, `ledger-conformance`, `tx-services` | Before each CCL release |
| G6 | Yano's report per milestone: the score and invalid counts meet D1a, in total and for the strict reader alone | Yano CI, dispatched |

## 6. Milestones

The scores in this table are historical, on `6cd8c928`. M5's exit and step 8 follow D1a on the active pin. The expected
scores are from section 1.3. Only 9149 and 9163 are measured; the rest were simulations until built.

| ID | Project | Work | Entry | Exit | Score |
|---|---|---|---|---|---|
| M1 | CCL | D3, PR #734 merged | D3's two items done | G5; the report shows 9149 | 9149 |
| M1a | CCL | G1 and G2 added | M1 | Miss list at 59 | 9149 |
| M2 | Yano | D7 | None. Takes effect with M1 | I4; redeemer 47/47 | 9163 |
| M3 | CCL | D4, D5 | M1a | Miss list at 44; I3, I6; G1, G2, G4, G5 | 9178 |
| M4 | CCL | D6 | M1a | Miss list at 2; I1, I5, I8; G1-G5 | 9198 |
| M5 | All | Release: CCL, then Yaci and JuLC, then Yano | M3, M4; G3 | The report on the release meets D1a | 9198 on the old pin; 42,215 on the active pin |
| M6 | Upstream | The corpus changes (Q1) | Dataset maintainers agree | The corpus pin moves | 9200 |
| M7 | Yaci | D8 | M1 released | yaci#113 closed; I7; G3 | Sync only |
| M8 | Yano, JuLC, CCL | D9, then refresh ADR 0001's status and section 7 | M5 | `CborCursor` and `OriginalTxBytes` deleted | None |

M1 to M6 were the 100% work on the old pin. M7 and M8 are the consolidation, and do not move the score. M2 can land before M1, and
M3 and M4 are independent of each other.

The scores are cumulative, in table order, and each assumes every milestone above it. Without M2 the report shows 14
fewer. If M4 lands before M3, the score after M4 is 9183. The miss lists count CCL's gate, which encodes non-canonically
like M2, so they do not depend on M2. They assume M3 lands first: M4 alone leaves 17.

At 9200 the cbor-decoding score was 100%. With the other pre19 scores unchanged, the overall score would have risen from
99.05% to about 99.83%. On the active pin, D1a's 42,215 is 98.92% for this scenario, and the overall score is measured on
the release.

### 6.1 Implementation order

The maintainer's order is CCL first and Yano last, one PR per step, stacked on the step before it so that work
continues while each waits for review. The steps still merge in order. Yano's score therefore moves mainly at step 8,
and the CCL gate's miss list tracks progress until then. D7 needs no CCL release, so it went up early as
bloxbean/yano#230. Every PR must also keep the strict reader's rejections (I2), by running Yano's report against the CCL
build. Scores through step 6 are now measured against local builds, with D7 applied. They are 9163 (#734), 9164, 9178, 9191 and
9198; the
other values are expected until built.

| Step | PR | Milestone | Samples fixed | Misses left in G1 | Yano report |
|---|---|---|---:|---:|---:|
| 1 | Finish PR #734 | M1 | 237 | n/a | 9149 |
| 2 | Safety net: corpus gate and golden bytes | M1a | 0 | 59 | 9149 |
| 3 | Numerics | M3 (D4) | 1 | 58 | 9150 |
| 4 | `ProtocolParamUpdate` | M3 (D5) | 14 | 44 | 9164 |
| 5 | Haskell map order | M4 (D6.1) | 13 | 31 | 9177 |
| 6 | Script references and inline datum bytes | M4 (D6.2, D6.3) | 7 (and 22 for G1 alone) | 2 | 9184 |
| 7 | Release CCL, then Yaci and JuLC | M5 | 0 | 2 | 9184 |
| 8 | Yano: version bump and harness fixes | M2, M5 | 14 | 2 | 9198 |

That table and steps 1 to 6 below describe the old pin. On the active pin, the measured path is 39,561 for `0.8.0-pre7`,
41,528 for the stack before the Haskell-order writers, and 42,215 with them and Yano's harness on the new writers.

1. **Finish PR #734.** Fix `CostMdls.java:156` for both decoder paths, and check the other model decoders on cbor-java
   trees. Merge `master`. Add the release note for trees without BREAK (D3).
2. **Safety net.** G1 fetches the corpus at its pinned commit and does not vendor it, because the dataset has no
   licence. It starts with 59 listed misses: 14 parameter update, 13 order, 7 output, 22 `datum_option`, 2 disputed,
   1 certificate. G2
   takes golden bytes from the current release, including mixed withdrawals, voters and committee members, so that step
   5 shows up as an intended diff.
3. **Numerics (D4).** One shared bignum read. The unsigned positions, the widened `Redeemer.index`, and the
   exact-or-throw positions, with boundary tests at 2^31, 2^32, 2^63 and 2^64-1.
4. **`ProtocolParamUpdate` (D5, Q2).** Keys 25-33 and the threshold classes. `Long` getters, `...Long` builder methods,
   the old constructor kept as deprecated, and the reachability metadata.
5. **Map order (D6.1).** Comparators in `Comparators`, used with the existing `SortedMap`, and `WithdrawalUtil`
   delegating. Golden bytes change only for the mixed cases outside the body, and for the signed asset name comparison,
   fixed here as Q5 went to (a).
6. **Script references and datums (D6.2, D6.3).** Mutation tests for I8: a replacement with `a10101` over `a201000101`,
   `setInlineDatum`, `toBuilder`, an in-place reorder, and replacements inside a list, a constructor and a map, each
   with a duplicate-key map replaced by one whose encoding matches. An unchanged decoded datum keeps its payload.
7. **Release.** G3 on mainnet, preprod and preview, G4 and G5. Release notes cover the getters, `ProtocolVersion.minor`,
   the exact-or-throw positions, map order and trees without BREAK. CCL is released first, then Yaci and JuLC against
   it.
8. **Yano.** Bump the versions and apply D7: `CborRule.java:154`, encoder exceptions, the Yaci diagnostic column and
   ADR-060's numbers. The report must meet D1a, in total and for the strict reader alone, and step 8 records the strict
   reader's per-sample baseline.

M7 and M8 come after step 8. Raising Q1 with the dataset maintainers is a separate decision.

## 7. Compatibility

| Change | Wire | Source | Binary | Notes |
|---|---|---|---|---|
| PR #734 `ProtocolVersion.minor` to `long` | Same | `getMinor()` type changes | Breaks | Only in-repo caller is an integration test |
| PR #734 trees without BREAK | Same through CCL's encoder | None | None | A plain cbor-java or Yaci encoder writes truncated CBOR for an indefinite array from such a tree (D3, D8). Release note |
| `ProtocolParamUpdate` three `Long` fields | Same below 2^31 | Builder unchanged, with new `...Long` methods; getters change (Q2) | Getters break | No main code in CCL, Yaci, Yano or JuLC uses these getters on CCL's class. Yaci has its own model |
| `ProtocolParamUpdate` new fields | New keys appear | Builder additive | All-args constructor changes | A deprecated constructor with the old signature keeps it |
| Unsigned treatments | Same below 2^63 | None | None | Above that, the getter reads negative |
| Exact-or-throw | Same in range | None | None | A value that wrapped now throws |
| D6.1 order | Changes for treasury, committee and voter maps mixing key and script keys, and so the hash. Body withdrawals keep their bytes | None | None | `gov_action_hex` in stored quicktx plans still decodes. Parties on old and new CCL can compute different hashes for such a transaction |
| D6.2 | Definite outer array | None | None | Only for received indefinite script refs |
| D6.3 | Original bytes | None | None | Only for decoded outputs |

## 8. Alternatives considered

| Alternative | Why not |
|---|---|
| Make CCL's model decoders Haskell-strict | Breaks lenient users, needs a version parameter everywhere, and duplicates the strict reader |
| Move everything to CCL, including the strict reader and Yaci's serializers | Breaks public models and moves rule-coupled code for one consumer. Q3 keeps part of this open |
| Add a strict or lenient mode flag to model decoders | Doubles every decoder's behaviour. ADR 0001 chose no options |
| Generate decoders from CDDL | Attractive, but a rewrite. CCL's hand-written API must survive anyway |
| General memoized bytes in every model | ADR 0001 deferred it, and only inline datums need it here |
| Re-encode inline datums from Yano's `RawOutput` in the harness | Would raise the score and leave CCL's defect in place |
| Accept the two disputed samples | Yano would accept what production Haskell rejects (Q1) |
| `Integer` and `Long` builder overloads | `.maxBlockSize(null)` stops compiling, against I6 (Q2) |
| `int` and `Long` builder overloads | A null `Integer` variable would throw at unboxing |
| A `Comparator<DataItem>` inside `SortedMap` | Keys there are encoded items, so each comparator would re-parse what the model already holds. Caller-side sorting is the existing idiom and needs no encoder change |

## 9. Risks

| Risk | Mitigation |
|---|---|
| Loosened CCL lets invalid input into the node | I2, G6. On the active pin the strict reader alone rejects 31,679 of 31,761, and the node's pair 31,758 |
| Other CCL users depend on an accidental rejection | PR #734 keeps `RecD` rejections. G2, G5 |
| Stale retained datum bytes | D6.3's same-object and datum key checks, I8 |
| External code encodes a CCL-decoded tree with a plain encoder | Release note (D3). Yaci moves its encoder with its decoder (D8) |
| A hardware wallet refuses a CCL-built body (D6.1) | `Cip21BodyOrderTest` walks the encoded body with CIP-21's own rule. Not tested on a device |
| A mixed-withdrawal body built by CCL and one built by a Haskell tool differ in bytes and hash (D6.1) | Each is valid. Parties must sign one set of bytes, as they already must |
| Yaci stops on a block it parses today | Walker before `RawBlock`, G3, I7 |
| Simulated fixes behave differently when built | G6 compares each milestone with section 6 |
| Corpus maintainers disagree on Q1 | Those samples stay missed and documented (D1a), with production behaviour unchanged |

## 10. Open questions

**Q1. The two disputed `auxiliary_data` samples.** Resolved in r2: keep rejecting, as production does. Yano stayed at
9198 on the old pin, and on the active pin the `auxiliary_data` samples stay listed (D1a),
until the corpus changes. Accepting them would make Yano accept a transaction production Haskell rejects, and
special-casing them in the harness is ruled out by I4 and ADR-060. Raising it with the dataset maintainers is a separate
step, not yet taken.

**Q2. `ProtocolParamUpdate` getters.** Resolved in r2, builder revised in r3: `Long` getters. The builder keeps
`maxBlockSize(Integer)` and adds `maxBlockSizeLong(Long)`, and the same for `maxTxSize` and `maxEpoch`, so existing
calls compile and link unchanged, including `null`. A caller assigning a getter to `Integer` fails to compile, which is
loud, and no consumer's main code does; the one caller found is the test at `UpdateTest.java:26`. `Objects.equals`
against an `Integer` still compiles and returns false, so that goes in the release notes.

**Q3. Should the strict reader eventually move to CCL?** Resolved in r2: not now (D9). It is the largest remaining CDDL
reader outside CCL. Revisit if a second consumer appears, such as a CCL user wanting Haskell-exact validation, or at the
Dijkstra work.

**Q4. Other `Ord`-ordered maps** (body withdrawals, `UpdateCommittee`, `VotingProcedures`). Resolved in r2: they follow
Haskell's order (D6.1), although the corpus does not measure them. Changed in r7: body withdrawals follow CIP-21.

**Q5. Asset name order.** Resolved in r7 as (a), by the maintainer. Haskell's `AssetName` uses `ShortByteString`'s
`Ord`: unsigned bytes, then length. So `6161` sorts before `62`. `Comparators.assetComparator` sorts by length first, then signed bytes, so `62` comes first, and of
two names of equal length, one with a byte at or above `0x80` sorts first. The corpus does not measure this: no Conway
`value` sample has two assets under one policy.

- (a) Keep length-first, as RFC 7049 section 3.9 and CIP-21 require, and fix only the signed comparison, which is wrong
  under every rule.
- (b) Use Haskell's order. This changes the bytes and hash of any output or mint with names of different lengths, which
  is common, and a hardware wallet would refuse them under CIP-21.

(b) would reach ordinary wallet transactions, which is why (a) was chosen.

The regenerated dataset does measure this order (r8). So r9 adds Haskell's order as a separate writer,
`serializeInLedgerOrder`, on the maintainer's decision. (a) still holds: `serialize`, and so every transaction built
with CCL, keeps CIP-21's order. A report that uses the new writer shows CCL can write Haskell's order, not that its
transactions use it, and should say so.

## 11. Related findings (out of scope)

Found while researching. Traced by reading only, except where stated. Each needs its own issue.

- **Yaci framing.** `MiniProtoStreamingByteToMessageDecoder.java:93-108` cuts the stream by the length of a
  *re-encoded* message, so a peer's non-minimal bytes misframe. Any decode failure also reads as "wait for more bytes".
  The fix is `CborSpan.skip`, which needs a way to tell truncated from malformed.
- **IPv6 byte order.** Haskell reads four little-endian words (`getHostAddress6`). `PoolRegistration.java:232` and
  Yaci's serializer use the bytes as they are (yaci#152). Bytes round-trip; the displayed address is wrong.
- **Yaci header and Byron hashes** come from canonical re-encodes, not original bytes.
- **Yano `StackOverflowError`** is caught by neither `JavaLedgerValidationEngine.java:127-129` nor
  `EngineAdmission.java:122`.
- **yaci#96** looks fixed in code and may be closable.

## 12. Revision history

- **r1** (2026-11-01): initial proposal.
- **r2** (2026-11-02, responds to the automated review of 7c4d7ba0 and the maintainer's decisions): Q1-Q4 resolved; D6.1
  widened to every map whose `Ord` order differs from its encoded bytes; D6.3 gains a same-object check, because model
  equality ignores map order and duplicate keys; Q5 added for asset names, replacing the related finding.
- **r3** (2026-11-02, responds to reviewer1's review of 7c4d7ba0): 1 -> D8 moves Yaci's encoder with its decoder, and D3
  and section 7 record the wider effect of trees without BREAK; 2 -> D6.3's second check compares non-canonical
  encodings; 3 -> Q2 uses `...Long` builder methods, I6 covers `null`; 4 -> G1's list shrinks per milestone, M1a added,
  scores stated as cumulative. D3's `CostMdls` finding marked as reproduced. Section 6.1 adds the maintainer's
  eight-step order.
- **r4** (2026-11-03, responds to reviewer1's re-review of 943b5360): F2 -> D6.3's second check compares a datum key that
  includes the duplicate entries the decoder dropped, so a replacement inside the datum is detected; I8 and step 6 cover
  nested replacements. G1's starting list corrected from 37 to 59, as measured by the gate in PR #741, with 22
  `datum_option` samples that Yano's report does not count; Yano's scores are unchanged. Steps are stacked, and D7 went
  up early as bloxbean/yano#230.
- **r5** (2026-11-03, from review of the step 5 implementation): D6.1's inner `VotingProcedures` row corrected, as encoded
  bytes do not give `GovActionId`'s order for an index of 24 or more. I5 names the order and asset exceptions.
- **r6** (2026-11-03, from review of the step 6 implementation): D6.3 records repeated entries in `EncodedKeyMap`, covers
  repeats nested inside repeats, and states its limits; `TransactionOutput.copy` added; D6.2 states what it leaves as it
  is. All 6 CCL steps are built, and Yano's report measures 9198 against them locally.
- **r7** (2026-11-03, from the maintainer's review of hardware wallet compatibility): D6.1 keeps body withdrawals in
  CIP-21's order, as a wallet refuses any other and the ledger accepts either, and everything a hardware wallet cannot
  sign follows Haskell's order. Q5 resolved as (a). Policy ids sort by bytes in both encoder modes. I5 and section 9
  updated.
- **r8** (2026-11-05, from measuring on the regenerated dataset): the committee term moves from exact-or-throw to
  unsigned 64-bit, so `newMembersAndTerms` becomes `Map<Credential, Long>`. Haskell reads it as an `EpochNo`, a `Word64`,
  and throwing refused 62 valid `transaction` and `transaction_body` samples that 0.8.0-pre7 accepted by truncating. The
  dataset was regenerated on 2026-10-30, to 42,675 Conway samples in 97 rules at `95e9e7c2`, so this ADR's 9198/9200 and
  section 8's gate describe the old revision. On the new one, measured by Yano's harness (bloxbean/yano#231), this
  stack scores 41528/42675 (97.31%), against 39561 for 0.8.0-pre7. Q5's statement that the corpus does not measure asset
  order no longer holds: 372 samples expect Haskell's asset order, and 89 Haskell's withdrawal order.
- r9 (2026-11-05, maintainer's decision on Q5's new evidence): `serializeInLedgerOrder` added for assets, values and
  outputs, with `serialize` unchanged. Pool owners and removed committee members are written in key order, which
  changes `update_committee_mixed` in the golden bytes, by order only. The gate moves to `95e9e7c2` and measures the
  2705 valid and manual-valid samples of the 19 rules with a model; 14 auxiliary data samples stay listed (Q1). With
  Yano's harness calling the new writers, measured on a local build: 42215/42675 (98.92%). Of the rest, 3 are in CCL (a
  Plutus constructor's form) and 2 in the harness (an empty `%multiasset`); the others are Yano's.
- **r10** (2026-11-05, from ADR 0003's acceptance): the note that ADR 0003 revisits D8's serializer boundary and D9, as
  its D10 asks. No decision here changes.
- **r11** (2026-11-05, responds to reviewer1's review of `4ee2de37`): F5 -> new D1a states the active pin, scope, exact
  misses and release criteria. D1, I1, I2, G6, M5, sections 1, 6 and 6.1, step 8, Risks and Q1 follow it, and the old pin's
  numbers are labelled historical. No decision or implementation scope changes.
- **r12** (2026-11-05, from reviewer1's review of #745): D6.3's key is the model's pairs in order, not its encoding, and
  also covers pairs lost converting to Plutus keys. D6.3's rule and I8 don't change.
