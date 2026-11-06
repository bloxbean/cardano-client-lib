# ADR 0004: Dijkstra in CCL's Transaction Model, as an Opt-In Era

**Status**: Proposed
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
and stays so. Code that never names Dijkstra writes exactly the bytes it wrote before (W1).

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
| `ProtocolParamUpdate` | Fields for keys 34-56. Key 53 has three states, as Haskell's `StrictMaybe` inside an update: absent, set to nil, or set to a number. Its field is an `Optional<Long>` that is null when absent and empty for nil, the one exception to the model's plain nullable fields | Only when set |

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
- **Everything else** keeps ADR 0002's rules: the body map in ascending key order (CIP-21), sets with tag 258 from
  Conway, redeemers as a map. Haskell writes body keys in its constructor order (0, 13, 18, 1, ...), and the ledger
  accepts either; ascending order is what a hardware wallet needs and what CCL writes today.

### D5. Reading Dijkstra into the models

`Transaction.deserialize(byte[] bytes, Era era)` is added. It reads through ADR 0003's views with the given era, maps
the new fields, and sets the transaction's `era`, so serializing it again writes the same era. `deserialize(byte[])`
keeps reading as Conway (`RawModels.READ_IN`), as it must (Q3).

**Ids come from the reader, not the model.** A model keeps no original bytes, and writing it again follows D4, not the
sender's encoding: ascending body keys, for one. So a model read and written again can have another body hash than
the bytes it was read from, in Dijkstra as in Conway today. The id of a received transaction is `RawTx`'s, from its
original bytes (ADR 0003 I15). `TransactionUtil.getTxHash` keeps hashing the model's own encoding, which is the id of
the transaction the model writes.

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

## 7. Milestones

| ID | What | Entry | Exit |
|---|---|---|---|
| W-M1 | D1-D6 | ADR 0003 M7a's reader | W1-W5 |
| W-M2 | QuickTx and builders for Dijkstra | Dijkstra final (ADR 0003 M7b); a separate decision | Separate ADR |

## 8. Compatibility

| Item | Impact |
|---|---|
| Conway serialization | None (W1) |
| Public constructors and methods | None (W2); new constructors are private |
| `Language`, `RedeemerTag`, `Era` | New constants. A caller's `switch` expression over them without a default stops compiling; CCL has none |
| `CostMdls` | A language-3 model read era-less stays unknown, as today; read in Dijkstra, or added by the caller, it is `PLUTUS_V4` (D2, W1) |
| Builder utilities that list languages (`CostModelUtil`, `tools/chain-parse-check`) | Stop at V3, as before. Plutus V4 in the builders is W-M2's |
| `Transaction.serialize(Era)`, `ProposalProcedure.serialize(Era)`, `ProtocolParamUpdate.serialize(Era)`, `Transaction.deserialize(byte[], Era)` | New public methods. Additive |
| `Transaction.deserialize(byte[])` | Unchanged; reads Conway |
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

**Q3. Reading without an era.** Should `deserialize(byte[])` detect Dijkstra? Some bytes could only be Dijkstra, such
as a 3-item transaction with body keys of Alonzo or later. But the legacy 4-item form with `true` is also Conway's
form, and key 14 reads differently in the two eras. Lean: no; the caller names the era, as ADR 0003 D3 requires.

## 12. Revision history

- **r1** (2026-11-06): initial proposal. Reviewed before commit by a Fable pass and a Codex pass; their findings are
  folded in: era propagation through nested writers and no `EraSerializationConfig` route (D1); errors only for fields
  an earlier era has no place for, so W1 stays exact (D1, W4); ids from the reader, not the model (D5, W5); parameter
  53's three states and intervals with no bound (D2); the hash-code claim (D3); `CostMdls` and the new public methods
  (W1, section 8); Q3's reason. Round 2: Fable approved; Codex's F10, a language-3 cost model's language view, is
  answered in D2's `CostMdls` row and W1.
