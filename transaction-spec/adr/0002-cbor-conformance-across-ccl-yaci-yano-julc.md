# ADR 0002: CBOR Conformance Across CCL, Yaci, Yano and JuLC

**Status**: Proposed
**Date**: 2026-11-01
**Risk**: High. Decoders decide what a node accepts, and CCL's decoders have many consumers.
**Issues**: bloxbean/cardano-client-lib#717, #505, #397; PR #734; bloxbean/yaci#113, #214, #191, #152;
bloxbean/yano#202 (closed)
**Related**: ADR 0001 in this directory; Yano ADR-060 (node-diversity reports)
**Modules**: CCL `common`, `plutus`, `transaction-spec`, `function`; Yaci `core`; Yano `ledger-rules`,
`ledger-conformance`; JuLC `julc-cardano-client-lib`

This ADR continues ADR 0001, so it lives in the same directory. ADR 0001 built the foundations: the span walker, the
raw views, the iterative codecs. This ADR uses them to reach 100% on the node-diversity `cbor-decoding` scenario, and
sets which project owns which part of CBOR parsing.

## 1. Context

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

- Every change needed for 100% on `conway-123-100`.
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
| cardano-cbor-dataset | `6cd8c928665bbafe90e02066ebb4620e38f051de`, corpus `conway-123-100`. The repository has no licence file, so samples are fetched, never vendored |
| cardano-ledger | `6176e413b2be9880c23cedbdaa899f9752693b22` (the dataset's pin); `c8cbb661d3` as a second reading |
| Conway CDDL | `eras/conway/impl/cddl/data/conway.cddl` at the same revision |
| Decoder version | The dataset decodes at `eraProtVerHigh` (11). ADR 0001 records production as `ProtVerLow` (9). A search of `eras/` and the binary and core libraries at `c8cbb661d3` found no decoder switch at 10 or 11, so the two agree here |

## 5. Decisions

### D1. What 100% means

100% means 9200/9200 on the pinned corpus, with no sample masked in the harness and no behaviour that production
Haskell rejects. Under that definition this plan reaches **9198/9200**. The other two samples need Q1.

A new corpus revision is a deliberate pin change, as ADR-060 already says.

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
| Unsigned | Keep the `long`, read and write it as unsigned 64-bit | `PoolRetirement.epoch`, `ttl`, `validityStartInterval`, `ConstrPlutusData` alternative |
| Exact or throw | Throw rather than wrap, because the type cannot widen without breaking users | `UpdateCommittee` term (`Map<Credential, Integer>`), `Update.epoch`, certificate and action discriminants |

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

**D6.1 Ledger key order.** A map keyed by a ledger type is written in Haskell's `Ord` order, decided in r2 (Q4).

| Map | Haskell key | `Ord` order | CCL today | Change |
|---|---|---|---|---|
| `TreasuryWithdrawalsAction`, body key 5 | `AccountAddress` | Network, script before key, hash | Encoded bytes, so key accounts first | Yes |
| `UpdateCommittee` new members | `Credential` | Script before key, hash | Encoded bytes, so key first | Yes |
| `VotingProcedures` outer map | `Voter` | Committee, DRep, pool; script before key within the first two; hash | Encoded bytes, so tag order 0-4 | Yes |
| `VotingProcedures` inner map | `GovActionId` | Transaction id, index | Encoded bytes give the same order | No |
| Redeemers, cost models, metadata labels | `PlutusPurpose`, `Language`, `Word64` | By tag or number | Encoded bytes give the same order | No |

The mechanism is the one `Value` and `MultiAsset` already use: the model sorts its entries with a `Comparator` and puts
them in the existing `SortedMap`, which both encoder modes write as inserted (`CustomCborEncoder.java:429`). `SortedMap`
and the encoder do not change. The comparators go in `Comparators`, one per key type. The credential part of
`WithdrawalUtil.getWithdrawalComparator`, in `function`, moves down with network added, and `function` delegates.

Checked with a prototype against PR #734's build: five reward accounts sorted this way came out in Haskell's order with
`canonical` true and false, and when nested in a canonical parent. The corpus samples simulated 13/13 (section 1.3).

The corpus only measures the treasury row: 13 of its 14 treasury samples mix key and script accounts, no Conway sample
is an `UpdateCommittee`, and the Conway set has no valid `transaction_body` or `voting_procedures` sample. The other
rows follow the same rule by decision, not by score.

This changes the bytes of a *built* transaction whose map mixes key and script keys, and its hash. The transaction stays
valid: Haskell's decoder does not require sorted keys here, as the 13 treasury samples show, since their input is
unsorted. Redeemer indexes do not depend on it, because `WithdrawalUtil` already sorts script-first before computing
them. D10's I5 records the exception.

CIP-21 requires hardware wallets to see map keys sorted lowest to highest, and names withdrawals explicitly. So a body
that mixes key and script withdrawals will no longer meet CIP-21. Proposals do not hit this, because CIP-21 forbids them
in hardware wallet transactions, and voting does not, because it allows a single voter. Section 9 records the risk.

Asset names are the exception, left open as Q5. Sets encoded as arrays, such as inputs, are not maps and stay as they
are.

**D6.2 Script references.** `TransactionOutput.serialize` writes `82` plus the two original items when the stored
`scriptRef` is an indefinite two-item array. The field keeps the raw bytes, so its meaning does not change. The inner
script bytes stay exact: sample `transaction_output/valid/00019` has an indefinite native script inside, which Haskell
keeps while re-encoding the outer array.

**D6.3 Inline datum bytes.** `TransactionOutput` keeps the original tag-24 payload at decode, with the datum object
decoded from it. `serialize` writes the payload only when both of these hold. Otherwise it encodes the model.

1. `inlineDatum` is that same object. This covers replacement: model equality ignores map order and decoding collapses
   duplicate keys, so `a201000101` and `a10101` compare equal, and only identity tells a replacement apart.
2. Its non-canonical encoding equals that of a fresh decode of the payload. This covers in-place mutation, including a
   reorder: after moving a map entry the model still equals a fresh decode, but this encoding changes from `a201020304`
   to `a203040102`. Both were checked against PR #734's build.

`setInlineDatum` and a `toBuilder` with a new datum fall back to the model, as does a deep copy, which is today's
behaviour.

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
| I1 | CCL accepts every valid corpus sample, except those in the dispute list (Q1) | D3-D6 | G1 |
| I2 | The strict reader alone, and the node's pair of decoders, reject 8334/8334 at every step | D3-D6, D9 | G6 |
| I3 | A decoded value is never silently different from the encoded value | D4 | Unit tests at each boundary, G1 |
| I4 | No harness special-cases a sample | D1, D7 | Review |
| I5 | A transaction built through the API serializes to the same bytes as before, except maps mixing key and script keys (D6.1) | D4-D6 | G2 |
| I6 | Existing builder calls compile unchanged, including `null` arguments | D4, D5 | G2, G5, a compile test with `null` |
| I7 | Every block Yaci parses today still parses, with the same model | D8 | G3 |
| I8 | Retained bytes are never written for a datum they no longer represent | D6.3 | Mutation tests |

**Gates**

| ID | Gate | Runs |
|---|---|---|
| G1 | CCL CI fetches the pinned corpus and runs valid samples through model decode and re-encode. The expected-miss list shrinks with each milestone (section 6) and must not grow. A listed sample that passes fails the gate, so the list stays exact. It holds the dispute only after M4 | Every CCL PR touching decoders |
| G2 | Golden bytes: a fixed set of built transactions compared with bytes from the last release. `TransactionModelParityTest` and the codec differential tests stay green | CCL CI |
| G3 | `tools/chain-parse-check` full replay on mainnet, preprod and preview, before and after, for CCL and for Yaci | Before each milestone release. Manual, by design |
| G4 | Native-image `ReachabilityMetadataGuard` | CCL CI |
| G5 | Downstream suites against a CCL snapshot: Yaci `core`, JuLC, Yano `ledger-rules`, `ledger-conformance`, `tx-services` | Before each CCL release |
| G6 | Yano's report per milestone: the score matches the table in section 6 and invalid counts stay at 100%, in total and for the strict reader alone | Yano CI, dispatched |

## 6. Milestones

The expected scores are from section 1.3. Only 9149 and 9163 are measured; the rest are simulations until built.

| ID | Project | Work | Entry | Exit | Score |
|---|---|---|---|---|---|
| M1 | CCL | D3, PR #734 merged | D3's two items done | G5; the report shows 9149 | 9149 |
| M1a | CCL | G1 and G2 added | M1 | Miss list at 37 | 9149 |
| M2 | Yano | D7 | None. Takes effect with M1 | I4; redeemer 47/47 | 9163 |
| M3 | CCL | D4, D5 | M1a | Miss list at 22; I3, I6; G1, G2, G4, G5 | 9178 |
| M4 | CCL | D6 | M1a | Miss list at 2; I1, I5, I8; G1-G5 | 9198 |
| M5 | All | Release: CCL, then Yaci and JuLC, then Yano | M3, M4; G3 | The report on the release shows 9198 | 9198 |
| M6 | Upstream | The corpus changes (Q1) | Dataset maintainers agree | The corpus pin moves | 9200 |
| M7 | Yaci | D8 | M1 released | yaci#113 closed; I7; G3 | Sync only |
| M8 | Yano, JuLC, CCL | D9, then refresh ADR 0001's status and section 7 | M5 | `CborCursor` and `OriginalTxBytes` deleted | None |

M1 to M6 are the 100% work. M7 and M8 are the consolidation, and do not move the score. M2 can land before M1, and
M3 and M4 are independent of each other.

The scores are cumulative, in table order, and each assumes every milestone above it. Without M2 the report shows 14
fewer. If M4 lands before M3, the score after M4 is 9183. The miss lists count CCL's gate, which encodes non-canonically
like M2, so they do not depend on M2. They assume M3 lands first: M4 alone leaves 17.

At 9200 the cbor-decoding score is 100%. With the other pre19 scores unchanged, the overall score rises from 99.05% to
about 99.83%.

### 6.1 Implementation order

The maintainer's order is CCL first and Yano last, one PR per step, each reviewed before the next starts. Yano's
published score therefore moves only at step 8, and the CCL gate's miss list tracks progress until then. Every PR must
also leave the strict reader at 8334/8334, by running Yano's report against the CCL build. Only 9149 is measured; the
other values are expected until built.

| Step | PR | Milestone | Samples fixed | Misses left in G1 | Yano report |
|---|---|---|---:|---:|---:|
| 1 | Finish PR #734 | M1 | 237 | n/a | 9149 |
| 2 | Safety net: corpus gate and golden bytes | M1a | 0 | 37 | 9149 |
| 3 | Numerics | M3 (D4) | 1 | 36 | 9150 |
| 4 | `ProtocolParamUpdate` | M3 (D5) | 14 | 22 | 9164 |
| 5 | Haskell map order | M4 (D6.1) | 13 | 9 | 9177 |
| 6 | Script references and inline datum bytes | M4 (D6.2, D6.3) | 7 | 2 | 9184 |
| 7 | Release CCL, then Yaci and JuLC | M5 | 0 | 2 | 9184 |
| 8 | Yano: version bump and harness fixes | M2, M5 | 14 | 2 | 9198 |

1. **Finish PR #734.** Fix `CostMdls.java:156` for both decoder paths, and check the other model decoders on cbor-java
   trees. Merge `master`. Add the release note for trees without BREAK (D3).
2. **Safety net.** G1 fetches the corpus at its pinned commit and does not vendor it, because the dataset has no
   licence. It starts with 37 listed misses: 14 parameter update, 13 order, 7 output, 2 disputed, 1 certificate. G2
   takes golden bytes from the current release, including mixed withdrawals, voters and committee members, so that step
   5 shows up as an intended diff.
3. **Numerics (D4).** One shared bignum read. The unsigned positions, the widened `Redeemer.index`, and the
   exact-or-throw positions, with boundary tests at 2^31, 2^32, 2^63 and 2^64-1.
4. **`ProtocolParamUpdate` (D5, Q2).** Keys 25-33 and the threshold classes. `Long` getters, `...Long` builder methods,
   the old constructor kept as deprecated, and the reachability metadata.
5. **Map order (D6.1).** Comparators in `Comparators`, used with the existing `SortedMap`, and `WithdrawalUtil`
   delegating. Golden bytes change only for the mixed cases. If Q5 goes to (a), the signed comparison is fixed here too.
6. **Script references and datums (D6.2, D6.3).** Mutation tests for I8: a replacement with `a10101` over `a201000101`,
   `setInlineDatum`, `toBuilder`, and an in-place reorder.
7. **Release.** G3 on mainnet, preprod and preview, G4 and G5. Release notes cover the getters, `ProtocolVersion.minor`,
   the exact-or-throw positions, map order and trees without BREAK. CCL is released first, then Yaci and JuLC against
   it.
8. **Yano.** Bump the versions and apply D7: `CborRule.java:154`, encoder exceptions, the Yaci diagnostic column and
   ADR-060's numbers. The report must show 9198 and 8334/8334, in total and for the strict reader alone.

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
| D6.1 order | Changes for maps mixing key and script keys, and so the hash | None | None | `gov_action_hex` in stored quicktx plans still decodes. Parties on old and new CCL can compute different hashes for such a transaction |
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
| Loosened CCL lets invalid input into the node | I2, G6. The strict reader alone is at 8334/8334 |
| Other CCL users depend on an accidental rejection | PR #734 keeps `RecD` rejections. G2, G5 |
| Stale retained datum bytes | D6.3's same-object and encoding checks, I8 |
| External code encodes a CCL-decoded tree with a plain encoder | Release note (D3). Yaci moves its encoder with its decoder (D8) |
| A hardware wallet refuses a body mixing key and script withdrawals (D6.1) | Release notes. Narrow case; not tested on a device |
| Yaci stops on a block it parses today | Walker before `RawBlock`, G3, I7 |
| Simulated fixes behave differently when built | G6 compares each milestone with section 6 |
| Corpus maintainers disagree on Q1 | Stays at 9198, documented, with production behaviour unchanged |

## 10. Open questions

**Q1. The two disputed `auxiliary_data` samples.** Resolved in r2: keep rejecting, as production does. Yano stays at
9198
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
Haskell's order (D6.1), although the corpus does not measure them.

**Q5. Asset name order.** Haskell's `AssetName` uses `ShortByteString`'s `Ord`: unsigned bytes, then length. So `6161`
sorts before `62`. `Comparators.assetComparator` sorts by length first, then signed bytes, so `62` comes first, and of
two names of equal length, one with a byte at or above `0x80` sorts first. The corpus does not measure this: no Conway
`value` sample has two assets under one policy.

- (a) Keep length-first, as RFC 7049 section 3.9 and CIP-21 require, and fix only the signed comparison, which is wrong
  under every rule.
- (b) Use Haskell's order. This changes the bytes and hash of any output or mint with names of different lengths, which
  is common, and a hardware wallet would refuse them under CIP-21.

Lean: (a). Unlike D6.1's cases, (b) reaches ordinary wallet transactions. No milestone waits on this.

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