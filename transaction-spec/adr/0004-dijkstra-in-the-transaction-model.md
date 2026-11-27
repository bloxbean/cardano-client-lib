# ADR 0004: Dijkstra in CCL's Transaction Model, as an Opt-In Era

**Status**: Proposed. W-M1 is implemented on CCL branch `feat/ledger-cbor` (section 7.1), with r3's fixes (section 7.2)
**Date**: 2026-11-06
**Risk**: Medium. The models are CCL's most used public API, and a change to their Conway bytes would change
transaction ids for every user. Dijkstra itself is experimental, so its own risk is low.
**Related**: ADR 0002 (writers, map order), ADR 0003 (reader; D4a reads Dijkstra, M7a)
**Modules**: CCL `common-spec`, `plutus`, `transaction-spec`

ADR 0003 D4a makes CCL read Dijkstra. This ADR lets a CCL user build the same structures with the model classes and
write them in Dijkstra's format, for example to submit a transaction to a Dijkstra test network such as Musashi. It goes
no higher than the model: QuickTx and the transaction builders are a later decision.

## 1. Context

**What a user can do today.** CCL's models (`Transaction`, `TransactionBody`, `TransactionWitnessSet`,
`AuxiliaryData`, the certificates) cover Conway. `Transaction.serialize()` writes in the transaction's `era`, or in
`EraSerializationConfig`'s era (Conway, with no setter) when that is null; `Transaction.serialize(Era)` is private. The
body, witness set, auxiliary data and certificates have a public `serialize(Era)`, but the body writes its proposals
without the era, and `ParameterChangeAction.serialize(Era)` writes its parameter update without it. Nothing models Dijkstra: no guards, no sub-transactions, no
Plutus V4, no BLS key on a pool, and `Era` stops at Conway.

**What Dijkstra changes for a writer**, read from cardano-ledger `95b36905` (ADR 0003 section 4 and D4a):

| Area | Change |
|---|---|
| Transaction | A submitted transaction is `[body, wits, aux / nil]`. The legacy `[body, wits, true, aux / nil]` is still read, and `false` fails (`Tx.hs:147-169`). Haskell writes the 3-item form (`Tx.hs:500-520`) |
| Body key 14 | `guards = nonempty_set<addr_keyhash> / nonempty_oset<credential>`. Haskell writes the credential form with tag 258, in insertion order (`TxBody.hs:505`) |
| Body keys 23-27 | `sub_transactions` (top level only), `required_top_level_guards` (credential to datum or nil), `direct_deposits` (reward account to coin), `account_balance_intervals` and `starting_account_balance_intervals` (top level only; reward account to a coin or `[lower / nil, upper / nil]`) |
| Sub-transaction | `[sub_body, wits, aux / nil]`. A sub body has no keys 2, 13, 16, 17, 23, 27. Haskell writes the list without tag 258 (`OMap/Strict.hs:423`) |
| Witness set | Key 8, Plutus V4 scripts. Redeemers are a map only. Redeemer tag 6, guarding |
| Scripts | Plutus V4 (script type 4, language 3); native script type 6 `script_require_guard`; auxiliary data `#6.259` key 5 |
| Certificates | Tags 0, 1, 5, 6 are gone. A pool registration may carry `bls_key = [bytes(96), bytes(48)]` after the VRF key hash; Haskell writes it only when present (`StakePool.hs:707-725`) |
| Parameter update | Keys 34-56 |

**The constraint.** Adding a field to a Lombok `@AllArgsConstructor` class changes its public constructor, and
`TransactionBody`, `TransactionWitnessSet`, `AuxiliaryData`, `PoolRegistration` and `ProtocolParamUpdate` all have one.
ADR 0003 D7 says model fields are only ever added and no public signature changes; both have to hold.

## 2. Scope

In scope: Dijkstra fields in the models, writing them with an explicit Dijkstra era, reading Dijkstra bytes into the
models through ADR 0003's reader, and the Plutus V4 language where the model needs it (script hashing, the script data
hash's language view).

Out of scope: QuickTx and the builders, fee and balance calculation, Plutus V4 evaluation, hardware wallet support,
Yaci's models (ADR 0003 Q7), and everything Leios-specific.

## 3. Threat model and trust assumptions

- **Conway bytes are the asset.** A change to what a Conway model writes changes transaction ids and signatures for
  users who never asked for Dijkstra. W1 and W2 guard it.
- **A Dijkstra field must not leak into an earlier era.** A guard silently dropped, or a sub-transaction written into a
  Conway body, would produce a transaction the user did not build. W4 makes it an error.
- **Lenient reading stays lenient.** Reading into a model accepts what ADR 0003's reader accepts; validity stays a
  ledger question (ADR 0003 section 3).

## 4. Pinned references

As ADR 0003 D4a: cardano-ledger `95b369053fd32422695a81fb7d5289d6dd35eee2` (cardano-ledger-dijkstra 0.5.0.0, equal
codecs to 0.4.0.0 in cardano-node 11.2.0), with file and line citations in the tables. Test vectors as ADR 0003 D4a.

## 5. Decisions

### D1. Dijkstra is chosen by era, and only by era

The model always has the Dijkstra fields. Whether they are written depends only on the era the caller names: a
`Transaction` whose `era` is Dijkstra, `Transaction.serialize(Era)`, which becomes public, or the public
`serialize(Era)` of a body, witness set or auxiliary data. `EraSerializationConfig` is not a way in: it has no setter,
and stays so. Code that never names Dijkstra writes exactly the bytes it wrote before (W1). The one other way in is a
read: `Transaction.deserialize(byte[])` sets Dijkstra on bytes that only Dijkstra reads (D5a, r3), which then write
back in Dijkstra; bytes Conway reads keep no era, as before.

**The era reaches every nested writer.** Where a writer drops it today, an overload taking the era is added and the
era-less method keeps its behaviour: `ProposalProcedure.serialize(Era)`, `GovAction` serialization through it, and
`ProtocolParamUpdate.serialize(Era)`, which `ParameterChangeAction.serialize(Era)` then calls. Nothing else changes
signature.

**A field an earlier era has no place for is an error, not dropped.** Writing for an era before Dijkstra with any of
these set fails with an exception that names the field: guards, keys 23-27, Plutus V4 scripts in a witness set or in
auxiliary data, a pool's BLS key, parameter keys 34-56. Values the model writes as given are the caller's, with no era
check, as today: reference script bytes on an output, a native script's tree (a `RequireGuard` inside it), a
redeemer's tag, a cost model's language. They are written byte for byte, so nothing is lost, and the ledger judges
them. That keeps W1 exact for every object a caller can build today.

**A value Dijkstra removed is an error when writing Dijkstra:** a certificate with tag 0, 1, 5 or 6, and a top-level
transaction marked invalid (Q2).

`Era.Dijkstra` is ADR 0003 D4a's constant.

### D2. What the model gains

| Class | Gains | Written as |
|---|---|---|
| `Era` | `Dijkstra(8)` | — |
| `Language` | `PLUTUS_V4(3)` | — |
| `PlutusV4Script` | New, as `PlutusV3Script` with type 4 | Script hash prefix 4 |
| `RedeemerTag` | `Guarding(6)` | — |
| `RequireGuard` | New native script, `[6, credential]` | — |
| `TransactionBody` | `guards` (`List<Credential>`), `subTransactions` (`List<SubTransaction>`), `requiredTopLevelGuards` (`List<RequiredGuard>`: credential and optional `PlutusData`), `directDeposits` (`List<Withdrawal>`, the same reward-account-to-coin shape), `accountBalanceIntervals` and `startingAccountBalanceIntervals` (`List<AccountBalanceInterval>`). All default to null | Keys 14, 23, 24, 25, 26, 27 |
| `SubTransaction` | New: `body` (`TransactionBody`), `witnessSet`, `auxiliaryData` | `[body, wits, aux / nil]` |
| `AccountBalanceInterval` | New: reward account, and either `exact`, or `inclusiveLower` and `exclusiveUpper` with at least one of them set, as Haskell's four forms (`Dijkstra/Scripts.hs:670-696`). Writing one with neither is an error | A coin, or `[lower / nil, upper / nil]` |
| `TransactionWitnessSet` | `plutusV4Scripts` | Key 8 |
| `AuxiliaryData` | `plutusV4Scripts` | Key 5 of `#6.259` |
| `PoolRegistration` | `blsKey` (`BlsKey`: 96-byte key, 48-byte proof) | After the VRF key hash, only when present |
| `CostMdls` | A Plutus V4 cost model. One the caller adds as `PLUTUS_V4` is a known language, in the language view as V2 and V3 are (`Alonzo/PParams.hs:559-573`). A language-3 model read from bytes stays in `getUnknownCostMdls()` unless it is read in Dijkstra, so era-less reads, their writes and their language views keep today's bytes | As today |
| `ProtocolParamUpdate` | Fields for keys 34-56. Keys 38 and 53 have three states, as Haskell writes both with `encodeNullStrictMaybe` inside an update (`Core/PParams.hs:369-379`, `Dijkstra/PParams.hs:1064-1081`): absent, set to nil, or set to a value. Their fields are `Optional`s that are null when absent and empty for nil (r2: key 38 was missed in r1), the only exceptions to the model's plain nullable fields | Only when set |

`requiredSigners` stays, and keeps its default of an empty list. Both it and `guards` fill key 14, so both non-empty is
an error. `requiredSigners` written for
Dijkstra uses the key-hash alternative, the same bytes as in Conway. A sub-transaction reuses `TransactionBody`, since
a sub body is a top-level body without six keys: writing one with a fee, collateral, a collateral return, total
collateral, sub-transactions or starting intervals is an error. The fee's default of zero is not written in a sub body.

`Credential`, `Withdrawal` and `PlutusData` are reused, not wrapped. `RequiredGuard` exists only because key 24's value
can be nil, which a map entry cannot say on its own.

### D3. Public constructors keep their signatures

A class that gains fields keeps its current public all-arguments constructor, written by hand with exactly today's
parameters, and Lombok's all-arguments constructor becomes private, still used by `@Builder`:

```java
// Illustrative
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)   // used by @Builder; takes every field
@Builder
public class TransactionBody {
    ...
    /** Kept for source and binary compatibility; use the builder for new fields. */
    public TransactionBody(List<TransactionInput> inputs, List<TransactionOutput> outputs, BigInteger fee, ...) { ... }
}
```

Checked with CCL's Lombok 1.18.42: the old signature stays public, and `@Builder` and `@Builder.Default` work
unchanged. New fields default to null, so `equals` between objects built the old way is unchanged; `hashCode` values and
`toString` text change, as they do whenever a field is added. Later fields go only into the private constructor. This
applies to `TransactionBody`, `TransactionWitnessSet`, `AuxiliaryData`, `PoolRegistration` and `ProtocolParamUpdate`,
and every public constructor they have today is kept, such as `ProtocolParamUpdate`'s hand-written one. W2 checks every
pre-existing public constructor by its parameter types.

### D4. How Dijkstra is written

- **Transaction:** the 3-item form `[body, wits, aux / nil]`, as Haskell submits and relays it. A transaction marked
  invalid cannot be submitted in Dijkstra, so writing one is an error (Q2).
- **Key 14 from `guards`:** the credential alternative with tag 258, in list order, as Haskell writes it.
- **Sub-transactions:** a plain array without tag 258, in list order, as Haskell writes it; each element three items.
- **Maps keyed by credential or reward account** (keys 24-27): Haskell's `Ord` order, by ADR 0002 D6.1's rule, since
  no hardware wallet signs a Dijkstra transaction yet (Q1). `Comparators` already holds both orders.
- **Pool registration:** ten fields with a BLS key, nine without; null is never written.
- **Auxiliary data:** always the `#6.259` map, as Haskell writes it (`TxAuxData.hs:145-167`); the shorter forms CCL
  writes in earlier eras are not used (r2).
- **Container lengths** (r2): CCL writes definite lengths, as all its writers do. Haskell switches to indefinite
  lengths above 23 entries (`Encoder.hs:363-443`); the ledger reads both, so above 23 entries the bytes differ from a
  Haskell-built transaction, as they already do in Conway.
- **Everything else** keeps ADR 0002's rules: the body map in ascending key order (CIP-21), sets with tag 258 from
  Conway, redeemers as a map. Haskell writes body keys in its constructor order (0, 13, 18, 1, ...), and the ledger
  accepts either; ascending order is what a hardware wallet needs and what CCL writes today.

### D5. Reading Dijkstra into the models

`Transaction.deserialize(byte[] bytes, Era era)` is added. It reads through ADR 0003's views with the given era, maps
the new fields, and sets the transaction's `era`, so serializing it again writes the same era. `deserialize(byte[])`
reads as Conway (`RawModels.READ_IN`), unless the bytes need a later era (D5a).

**Key 14 keeps its form (r3).** Dijkstra reads key 14 as a set of bare key hashes or of credentials, the first item
deciding, and keeps the bytes it read (`TxBody.hs`, `decodeGuards`). Read in Dijkstra, bare key hashes, the form a
Conway body writes, go to `requiredSigners`, which writes them back in that form; credentials go to `guards`. So key 14
no longer changes a Conway transaction's body when it is read in Dijkstra: written again, the body is as the model
writes any body (ids still come from the reader, below). `RawCredential.isKeyHashOnly()` tells the forms apart. Two
cases stay as before: a set mixing both forms, which Haskell rejects, is read into `guards` and written as credentials;
an empty key 14 is left out on writing.

**A model re-read in its own era (r3).** `TransactionSigner.sign(Transaction, …)` and `TransactionUtil.createCopy`
write a model, then read the bytes back. They read them in the transaction's era (`TransactionUtil.reread`), so its
Dijkstra fields stay; with no era, they read as before.

**Ids come from the reader, not the model.** A model keeps no original bytes, and writing it again follows D4, not the
sender's encoding: ascending body keys, for one. So a model read and written again can have another body hash than
the bytes it was read from, in Dijkstra as in Conway today. The id of a received transaction is `RawTx`'s, from its
original bytes (ADR 0003 I15). `TransactionUtil.getTxHash` keeps hashing the model's own encoding, which is the id of
the transaction the model writes.

### D5a. Reading without an era (r3)

A transaction read without an era is read in the earliest era, from `READ_IN` on, whose tables have a place for every
key and form it holds (`RawTx.eraOf`). That era is set on the transaction when it is not `READ_IN`; a transaction read
as Conway keeps no era, as before.

- **The rule is data, as ADR 0003 D3 requires.** The reader walks the bytes in each era in turn with a policy that
  rejects a key or form the era's tables have no row for. Rows an era dropped still count as read, so an earlier era's
  transaction stays in `READ_IN`. A key no era has, or bytes that are not a transaction, also stay in `READ_IN`, where
  the read fails or tolerates them as before.
- **A form the tables do not show stays in `READ_IN`.** One rule is the reader's, not a table's: from Dijkstra, a record
  may be an indefinite-length array (`RawItems.indefiniteRecordsAllowed`). Bytes whose only Dijkstra form is such a
  record are read in Conway, which rejects them with an error, as before: nothing is dropped, and the caller reads
  them with `deserialize(bytes, Era.Dijkstra)`. Reading them in a later era when Conway's read fails was tried and
  rejected in review: it would also read in Dijkstra the indefinite-length records Conway rejects on purpose, among
  them the disputed auxiliary data of ADR 0002 Q1, and give a Conway transaction CCL cannot read a Dijkstra era.
- **A later era needs only its rows.** When an era after Dijkstra is added to
  `EraSpec` and its rows to the tables, its transactions are found the same way, with no change to this code. This
  holds as long as the new era shows a key whose meaning changes by a form of its own, as Dijkstra's key 14 does with
  credentials: a precondition on the tables of later eras, not something the rule can check. Moving `READ_IN` itself is
  a separate decision, for when an era drops the forms of the one before.
- **Bytes both eras read stay in `READ_IN`.** This is a preference for compatibility, not a claim that the eras read
  such bytes alike: Conway and Dijkstra read them to the same values, with key 14 keeping its form, as far as the
  model's own normalization goes (D5). One value is named differently: a
  language-3 cost model is unknown in Conway and `PLUTUS_V4` in Dijkstra (section 8), with the same bytes either way. A
  Dijkstra node accepts the Conway form `[body, wits, true, aux]` such a transaction then writes, with the same body
  and id.
- **Cost.** One walk in Conway for a Conway transaction; a second, in Dijkstra, only when the first meets a later
  era's key or form. The walk parses the bytes once more: the review measured about 27 ms of walks against 100-120 ms of
  reads for 5,860 transactions, so about a quarter more time for `deserialize(byte[])`.
- **Scope.** The rule is `Transaction.deserialize(byte[])`'s. The other era-less model readers
  (`TransactionBody.deserialize(Map)`, `TransactionWitnessSet.deserialize(Map)`, `AuxiliaryData.deserialize`,
  `ProposalProcedure.deserialize`, the `GovAction` readers) take decoded values, not bytes, and read as `READ_IN`; CCL's
  own code reads Dijkstra through `Transaction`. Bytes `READ_IN` reads only leniently, such as a tagged transaction or a
  body with a key of no era beside a direct deposit, stay in `READ_IN` and drop the Dijkstra fields, as before.

The reader's views still take an era, as ADR 0003 D3 says: `RawTx.eraOf` is how a caller that has none finds one.

### D6. Experimental

Each public type, field and method added here says in its Javadoc that Dijkstra is not final and the API may change in
any release, as ADR 0003 D4a does. ADR 0003's M7b removes the marking.

## 6. Invariants

| ID | Invariant | Constrains | Checked by |
|---|---|---|---|
| W1 | Without a Dijkstra era, every model writes exactly the bytes it wrote before this ADR | D1, D3 | The whole CCL suite; the Conway dataset gate (2,705 samples, the misses file unchanged); G5 of ADR 0003; a Conway parameter update with a cost model for language 3, read era-less: it stays unknown, and its bytes and its language view are as before |
| W2 | Every public constructor and method that existed before still exists with the same signature | D3 | A reflection test listing them by parameter types |
| W3 | Each valid Dijkstra test vector the models cover reads with `deserialize(bytes, Era.Dijkstra)`, and writes back with `serialize(Era.Dijkstra)` to Haskell's encoding: the dataset's normalised `*.expected.cbor`, as the Conway gate compares, or for other vectors the same value read back. Differences are listed and explained | D2, D4, D5 | The dataset gate run for Dijkstra; the ledger golden transaction; real-network transactions |
| W4 | A field an earlier era has no place for is never dropped from its bytes: writing it fails. A value Dijkstra removed never reaches Dijkstra's bytes | D1, D4 | Unit tests, one per field |
| W5 | Writing a model in Dijkstra and reading it back with the reader in Dijkstra gives the same values, and the reader's id of those bytes is the model's `getTxHash` | D4, D5 | Unit tests over every new field |
| W6 (r3) | Read without an era, a transaction that Conway's tables have a place for is read exactly as before, with no era set; one holding a key or form only a later era's tables have is read in the earliest era with a place for all of it. Signing or copying a model keeps its era's fields, and a key-hash key 14 read in Dijkstra keeps the body's id | D5, D5a | Unit tests per rule; the dataset gate's valid Conway transactions; every committed real transaction, Shelley to Conway; IO's Dijkstra devnet probes (section 7.2) |

## 7. Milestones

| ID | What | Entry | Exit |
|---|---|---|---|
| W-M1 | D1-D6 | ADR 0003 M7a's reader | W1-W5 |
| W-M2 | QuickTx and builders for Dijkstra | Dijkstra final (ADR 0003 M7b); a separate decision | Separate ADR |

### 7.1 Implementation record: W-M1 (r2)

W-M1 is implemented on CCL branch `feat/ledger-cbor`, on top of ADR 0003's M7a reader, and was reviewed by a Fable pass
and a Codex pass over two rounds before commit.

| Invariant | Result |
|---|---|
| W1 | The full CCL suite fails the same tests as the base commit. The Conway dataset gate (2,837 samples at `4fd977b2`) and `certificate-outcomes.txt` are unchanged. `toJson` of objects built the old way is byte-identical to the base commit's output: the new fields are left out while null. A language-3 cost model read without an era stays unknown, with the same bytes and language view. A body written before Dijkstra still writes its proposals in the configured era, pinned with Babbage bytes |
| W2 | 814 public signatures of the 54 classes the change touches, read from the base commit's compiled classes, all still exist |
| W3 | 17 golden and real-network transactions read in Dijkstra and write back in the 3-item form. The new body keys 14, 25, 26 and 27, re-encoded, equal the ledger golden's and a Musashi transaction's original items, in order and with tags; no vector has key 24. Dijkstra model gate: 3,802 dataset samples, 123 listed misses, each a re-encoding difference from D4's choices (tag 258 on sets, CCL's map order, legacy outputs, credential-form guards, the tag-259 auxiliary data form), and the one disputed label (ADR 0003 section 7.2) |
| W4 | One error test per Dijkstra-only field before Dijkstra, including a standalone sub-transaction, and per removed value in Dijkstra |
| W5 | The reader's id of each re-encoding equals `getTxHash` |

**Decisions taken during implementation.**

- **Indefinite-length records in Dijkstra.** CCL's lenient and tolerant views required definite-length records, which is
  Haskell's rule only before version 12. A view read in Dijkstra now accepts both (`RawItems.indefiniteRecordsAllowed`);
  era-less reads and earlier eras are unchanged. Without it, a real Dijkstra transaction with such a record could not be
  read into the models, and Yaci would fail its block.
- **Keys 38 and 53** have three states (D2). JSON writes absent as nothing, nil as `null`, and a value as itself.
- **Auxiliary data** in Dijkstra is always the `#6.259` map, with empty metadata left out as Haskell does (D4).
- **Container lengths** stay definite (D4).
- **Additions for Yaci:** `RawCertificate.lenient(kind, span, era)` and `RawBlock.transaction(int)`, so Yaci reads
  Dijkstra pool registrations and transactions as tolerantly as earlier eras.

**Differences for existing callers.** Reading: a `[6, credential]` native script, redeemer tag 6 and script type 4,
which threw, now read as `RequireGuard`, `Guarding` and `PlutusV4Script`. `CostMdls.add` of a `PLUTUS_V4` model
replaces a language-3 model read as unknown. `toString` and `hashCode` include the new fields. Writing is unchanged
for everything a caller could build before.

**Left open.** Before Dijkstra, `AuxiliaryData` with metadata and only Plutus V3 scripts takes the metadata-only form
and drops the scripts. This predates ADR 0004, and fixing it changes Conway bytes, so it is left for its own issue.

### 7.2 Implementation record: r3 (issue #752)

IO's readiness run against `d3324ec3` on a `cardano-testnet` devnet with cardano-node 11.2.0, forked from Conway to
Dijkstra at protocol version 12 (issue #752), found three model bugs. r3 fixes them (D5, D5a).

| Bug | Fix | Check |
|---|---|---|
| `TransactionSigner.sign(Transaction, …)` read the signed bytes back without an era, dropping body keys 23-27; the node rejected a signed direct deposit (`ValueNotConservedUTxO`, `InvalidWitnessesUTXOW`). `TransactionUtil.createCopy` had the same fault | Both re-read in the transaction's era (`TransactionUtil.reread`) | A unit test with a language-3 cost model, which the bytes leave in Conway, fails with the fault put back; on the devnet the same direct deposit is accepted |
| Dijkstra key 14 of bare key hashes was read as guards and written back as credentials, so the body and id changed | Read into `requiredSigners`, which keeps the form | A unit test, which fails with the fault put back; IO's Conway required-signer transaction, read in Dijkstra, gives the on-chain id |
| A read without an era dropped Dijkstra fields and gave another id | D5a | Unit tests per rule, each of which fails with the fault put back; 25 of IO's 25 transactions give the on-chain id read without an era (21 before), and in Dijkstra (23 before) |

**W1 and W6.** The full CCL suite (`common-spec`, `ledger-cbor`, `transaction-spec`, `plutus`, `core`, `core-api`,
`quicktx`, `backend`) passes on JDK 21, and the dataset gate at `4fd977b2` (misses files, certificate outcomes, golden
bytes) is unchanged. Committed tests pin W6 for Conway: the dataset gate reads each valid Conway transaction in Conway,
and every committed real transaction and block transaction, Shelley to Conway (pre-Alonzo ones through the rows their
eras dropped), stays in Conway with no era set. A scan before commit also gave Conway for all 176 transactions of 16
real Babbage and Conway blocks of Yaci's tests and for the 368 ledger-rules fixtures of the node-diversity corpus, and
Dijkstra for the dataset's 102 valid Dijkstra transactions. The review's own runs gave Conway for 22,630 Conway dataset
samples wrapped as transactions and for 117,200 byte-flip mutations of the real ones. IO's QuickTx and model
probes otherwise give the same results as on `d3324ec3`.

**Left for W-M2 (#752's other items).** QuickTx's stake registration with certificate types 0 and 1, which Dijkstra
removed; Plutus V4 and the new fields in QuickTx; Plutus V4 cost models from the backends; the reference-script fee
parameters 36 and 37 in the fee calculation; and backporting the duplicate-witness fix to 0.7.x.

## 8. Compatibility

| Item | Impact |
|---|---|
| Conway serialization | None (W1) |
| Public constructors and methods | None (W2); new constructors are private |
| `Language`, `RedeemerTag`, `Era` | New constants. A caller's `switch` expression over them without a default stops compiling; CCL has none |
| `CostMdls` | A language-3 model read era-less stays unknown, as today; read in Dijkstra, or added by the caller, it is `PLUTUS_V4` (D2, W1) |
| Builder utilities that list languages (`CostModelUtil`, `tools/chain-parse-check`) | Stop at V3, as before. Plutus V4 in the builders is W-M2's |
| `Transaction.serialize(Era)`, `ProposalProcedure.serialize(Era)`, `ProtocolParamUpdate.serialize(Era)`, `Transaction.deserialize(byte[], Era)` | New public methods. Additive |
| `Transaction.deserialize(byte[])` | Reads Conway as before, unless the bytes hold a key or form only a later era has: then that era, which it sets (D5a, r3) |
| `TransactionSigner.sign(Transaction, …)`, `TransactionUtil.createCopy` | Keep a transaction's era's fields; unchanged without an era (D5, r3) |
| `RawTx.eraOf`, `RawCredential.isKeyHashOnly`, `TransactionUtil.reread` | New public methods. Additive (r3) |
| `toString` of the models | Shows the new fields as `null` |

## 9. Alternatives considered

| Alternative | Why not |
|---|---|
| Subclasses (`DijkstraTransactionBody`) | `@Builder` does not inherit; `@SuperBuilder` changes the builder's type, which breaks callers. Two classes for one body |
| A holder object for the Dijkstra fields | Still adds a constructor parameter, and splits one body across two objects |
| Adding fields and accepting the constructor change | Breaks source and binary compatibility for a constructor many tests and users call |
| A separate Dijkstra model package | Copies every unchanged field and class, as ADR 0003 section 9 found for per-era classes |
| Writing Haskell's body key order | Hardware wallets require ascending order (ADR 0002 D6.1), and the ledger accepts either |

## 10. Risks

| Risk | Mitigation |
|---|---|
| Conway bytes change by accident | W1 over the whole suite and the dataset gate |
| Dijkstra changes before it is final | D6; the change lands in the model's field list and D4's rules |
| Too few vectors for the new writers | The ledger's golden transaction covers every new body field except keys 24 and 27; real transactions cover sub-transactions and Plutus V4. Gaps are listed in W3's results |
| A builder later depends on an experimental field | W-M2 waits for M7b |

## 11. Open questions

**Q1. Order of the new maps (keys 24-27).** (a) Haskell's `Ord` order, as ADR 0002 D6.1 does for maps a hardware
wallet does not sign; (b) encoded-byte order, as body withdrawals. Lean: (a). If CIP-21 or a wallet later covers
Dijkstra, these maps are looked at again, as ADR 0002 says of the voting maps.

**Q2. Writing a transaction marked invalid for Dijkstra.** (a) An error, since such a transaction cannot be submitted;
(b) write the block form `[body, wits, aux / nil, false]`. Lean: (a). A block producer writes blocks through the
reader's spans, not through this model.

**Q3. Reading without an era.** Resolved in r3: yes, by D5a's rule. Some bytes could only be Dijkstra; the legacy
4-item form with `true` is also Conway's; and key 14 reads differently in the two eras. D5a reads bytes with a key or
form only Dijkstra has in Dijkstra, and the rest in Conway, including a 3-item transaction of Conway's keys, which
writes back in the 4-item form with the same body; D5 makes key 14 read alike in both. The r1 lean, that the caller
names the era, left callers that have none (the backends, the evaluators, `Account.sign(String)`) silently dropping
Dijkstra fields (issue #752).

## 12. Revision history

- **r1** (2026-11-06): initial proposal. Reviewed before commit by a Fable pass and a Codex pass; their findings are
  folded in: era propagation through nested writers and no `EraSerializationConfig` route (D1); errors only for fields
  an earlier era has no place for, so W1 stays exact (D1, W4); ids from the reader, not the model (D5, W5); parameter
  53's three states and intervals with no bound (D2); the hash-code claim (D3); `CostMdls` and the new public methods
  (W1, section 8); Q3's reason. Round 2: Fable approved; Codex's F10, a language-3 cost model's language view, is
  answered in D2's `CostMdls` row and W1.
- **r2** (2026-11-07): section 7.1 records W-M1's implementation; key 38's three states (D2); auxiliary data and
  container lengths (D4); status line.
- **r3** (2026-11-14, responds to issue #752 at `d3324ec3`): a model re-read in its own era and key 14 keeping its form
  (D5); reading without an era picks the earliest era with a place for every key and form (D5a), which resolves Q3 and
  is the rule for later eras; W6; section 7.2; compatibility rows. Reviewed before commit by a Fable pass and a Codex
  pass; their finding on indefinite-length records is answered in D5a, a fallback to a later era having been tried
  and dropped.
