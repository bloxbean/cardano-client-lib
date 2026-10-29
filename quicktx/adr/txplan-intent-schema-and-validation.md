# Generated TxPlan Intent Schema and Strict Intent Validation

**Status**: Proposed
**Date**: 2026-10-25
**Issue**: TBD
**Modules**: `quicktx` (schema, generator, tests, runtime strictness); `txflow` (shared strict reader configuration and schema conformance tests; consumes the schema by `$ref`)
**Related ADRs**: [ADR 0002: Portable TxFlow Contract](../../txflow/adr/0002-portable-txflow-contract-and-runtime.md) (§ Transaction Schema Ownership), [ADR 0008: Generic QuickTx Extensions in TxFlow](../../txflow/adr/0008-generic-quicktx-extensions-in-txflow.md)
**Risk**: Medium. The schema change is additive tooling, but D8 changes runtime parsing of standalone TxPlan YAML (documents that parse today can fail).

## Revision History

- **r1** (2026-10-25): Initial proposal. OQ1–OQ4 were answered by the maintainer before review (§12).
- **r2** (2026-10-25; responds to the Aster review of `dbdcf628`, plus evidence from implementation probes):
  - Aster 1 → D2: hand-written `context` integer fields accept placeholders (and numeric strings for the slots), keeping literal bounds.
  - Aster 2 → D4: the extension branch is limited to `tx.intents`, matching ADR 0008 and `FlowExtensions.checkTemplateIntents`.
  - Probe → D4 rewritten: array placement follows each array's declared element type, so `intents` accepts all 23 types; the r1 rule would have rejected `collect_from` under `intents`, which both readers accept. A placeholder-discriminator branch is added.
  - Probe → D1: property names come from Jackson's built bean deserializer (introspection misses `UnitInterval`); polymorphic nested types (`Relay`) are handled generically.
  - Probe → D3: enum names come from the mapper (`spend`, case-sensitive); custom deserializers go through an explicit table; every non-string schema is wrapped with the placeholder branch.
  - New I10 (schema never stricter than the runtime), I11 (reader totality) and I12 (fixed point); §9 adds Jazzer fuzz targets; M1 and M2 exit criteria updated.
- **r3** (2026-10-25; from the first I10 test runs, before review of r2):
  - D3: `String` fields accept every scalar; `integerLike` allows a sign.
  - D5: root `transaction` entries do not require `tx`.
  - D8: "strict" also disables float-to-int truncation and numeric enum indexes, and makes UTxO references exact.
  - §8: M2 lands before M1, so I10 uses the real strict readers.
  - §13: two repository tests with a non-existent `to:` field, a broken docs example, and the `.inf` writer case.
- **r4** (2026-10-25; from implementing M2 and M1 and fuzzing them with Jazzer):
  - D2: hand-written `String` fields accept every scalar; `signers` entries do not require `ref`.
  - D4: array items use `anyOf`.
  - D8: strict readers reject explicit nulls and empty values.
  - I11 wording: "never a programming error". I12 scoped to strict readers.
  - §13: the `signers` finding, and four pre-existing robustness bugs that fuzzing found and M2 fixes.
- **r5** (2026-10-26; from a multi-agent review of both implementation PRs — Codex and two Claude reviewers — and further fuzzing):
  - §1.6 corrected: `TxPlan.from` substitutes text across the whole document.
  - D2–D4 (schema): placeholders at every structural position; numbers for hex/IP and reference text; trimmed enum names; `booleanLike`; the generator owns the whole `tx.inputs`/`intents`/`scripts` properties; generation fails fast on constructs it cannot describe.
  - D7: the corpus described as built.
  - D8: further strict rules, TxFlow uses the shared configuration, extension statement narrowed, fixes that apply in both modes.
  - I9: stated exceptions.
  - §13: follow-ups.
- **r6** (2026-10-26; from the confirmation reviews of the fix commits — Codex and Claude — and further fuzzing):
  - D8: template-valued variables are a `TxPlan.from` rule; `TxPlanCodec` keeps its literal-replacement contract.
  - §7: TxFlow behaviour changes and the canonical-writer change recorded.
  - M2 exit criteria: per-type tests in quicktx, programmable-token suite included.
  - §13: two more small follow-ups.
- **r7** (2026-10-26; from the automated reviews of #725 at `b070dc2d` and #726 at `ceaef1c5`):
  - D8: the YAML-text checks now cover `variables` (an alias there was substituted as the anchor name, `n: 010` as 8); a decimal variable keeps its written scale, so a kept `BigDecimal` `1.50` still rereads.
  - D8: in `TxPlan.from`, a placeholder used as a mapping key (`'${field}': 5`) is rejected. Its textual substitution renamed the key; the codec and the schema substitute values only.
- **r8** (2026-10-26; review consistency fixes):
  - D2: slot integers remain unbounded below, matching the current reader and §13; reference text fields also accept numbers.
  - D8 and I12: clarify the full strictness opt-out and the codec's `readLiteral` snapshot round trip.
  - Module scope, D1 and OQ3 aligned with the revised TxFlow work, polymorphic schema and milestone order.
- **r9** (2026-10-27; reviewer1 review of `cb10d41b`, after #725 and #726 were merged into this PR): strict reading stays the default; the false positives and the one bypass are fixed.
  - D8: an intent written as a list (`[pool_update, {...}]`) is rejected; Jackson bound it and the alias and pool-flag checks skipped it.
  - D8: a whole number written as a float (`20000000.0`) reads as that integer; only a fractional part is rejected. The number-form check compares a float with its `BigDecimal` text, so `20000000.0` is plain.
  - D8: an empty trailing YAML document is allowed. The extra rules the code already enforced are listed, and unquoted YAML 1.1 booleans are recorded as a limitation.
  - D8 and §7: TxFlow copies in-memory plans with `TxPlan.fromSnapshot`, which reads the library's own output as before; legacy TxFlow documents are re-written with the canonical writer.
  - I9: the shared mapper's `BigDecimal` reading is a stated exception. §13: legacy TxFlow and list-form relays added; the `.inf` writer item marked fixed.
- **r10** (2026-10-27; maintainer decisions after r9):
  - D5 and §7: the schema file is renamed `txplan-v1alpha1.schema.json`, with `$id` `https://cardano-client.dev/schemas/txplan/v1alpha1` and title "TxPlan v1alpha1", to match `txflow-v1alpha1.schema.json`. The file has shipped only in 0.8.0 pre-release builds; TxFlow's `$ref` moves with it.
  - D8: the opt-out is renamed `TxPlanCodec.builder().strictReading(false)` (it was `strictIntentFields`), since it controls every strict rule, not only intent fields. It has not been released under the old name.
  - §10.1: the test-scope validator no longer changes the Jackson version on test classpaths.
- **r11** (2026-10-27; review of `10c51e30` by Codex and two Claude reviewers, plus DevKit integration runs):
  - D8: a whole-number float is converted only from its exact decimal value, and only up to Jackson's number length (1000 digits). r10 also converted a `double`, which TxFlow's parser produces and which may already be rounded (`9007199254740993.0` became `…992`, `1000000.00000000001` became `1000000`); and `1E+100000000` took about 40 s to build. TxFlow therefore keeps rejecting every float for an integer field (§7).
  - D8: why TxFlow's snapshot copies use `TxPlan.fromSnapshot` rather than the codec's literal copy. `StrictReading`'s text and variable checks are package-private.
  - D5, §1.4, I7, §7, §9 and OQ1: stale or unscoped statements corrected (TxFlow's `$ref` moved with r10; "TxFlow already rejects" means portable v1alpha1; TxFlow compile tests cover representative types).
  - §13: the open review items, and metadata text starting with `0x`, which the metadata convention reads as bytes.
- **r12** (2026-10-28; review of `5d76df09`):
  - D8: reject fractional decimals before converting them to integers. The positive-exponent length guard did not cover `1E-100000000`: Java 17's `toBigIntegerExact` expanded its scale before rejecting it, consuming tens of seconds. Regression tests cover both strict readers and `BigInteger`, `Integer`, and `Long` fields.
  - D8: count the minus sign in the integer length guard, so an accepted `-1E+999` cannot become an unreadable 1001-character number. Preserve Jackson's decimal-to-integer scale constraint, including for scaled zero.

## 1. Context

All facts below were verified at `origin/master` `ea79ceea` (after PR #723).

### 1.1 The schema does not describe intents

`quicktx/src/main/resources/schema/quicktx-transaction-v1alpha1.schema.json` (90 lines) describes intents only as:

```json
"inputs":  { "type": "array", "items": { "type": "object" } },
"intents": { "type": "array", "items": { "type": "object", "required": ["type"] } },
"scripts": { "type": "array", "items": { "type": "object", "required": ["type"] } }
```

`TxIntent.java` registers 23 type names in `@JsonSubTypes`, for 22 concrete classes (`pool_update` reuses `PoolRegistrationIntent`):

| Group | Type names |
|---|---|
| Payment and metadata | `payment`, `donation`, `minting`, `metadata` |
| Stake and pool | `stake_registration`, `stake_deregistration`, `stake_delegation`, `stake_withdrawal`, `pool_registration`, `pool_update`, `pool_retirement` |
| Governance | `drep_registration`, `drep_deregistration`, `drep_update`, `governance_proposal`, `voting`, `voting_delegation` |
| Script | `script_collect_from`, `script_minting`, `validator`, `native_script`, `reference_input` |
| Inputs | `collect_from` |

None of these names, and none of their fields, appear in the schema. A user cannot learn from the schema which intents exist or what they accept. The docs do not fill the gap: only 4 of the 23 names (`payment`, `collect_from`, `script_collect_from`, `validator`) appear as a YAML `type:` in `docs/content`.

### 1.2 There is no schema for a standalone TxPlan document

The schema has only `$defs`, and its `transaction` definition is TxFlow's embedded single transaction (`{tx, context}`), referenced from `txflow-v1alpha1.schema.json:156`. The standalone TxPlan envelope (`version`, `variables`, `extensions`, a top-level `context`, `transaction: [ - tx: … ]`) is not described anywhere, so pointing an editor at the schema validates nothing for a TxPlan file. ADR 0002 rule 2 keeps that envelope out of TxFlow, which is correct, but nothing describes it for TxPlan either. `scriptTx` entries are rejected at runtime (`TxPlan.java:712`), so they are not part of the contract.

### 1.3 The schema is hand-maintained, and nothing checks it

No production code loads the schema. TxFlow tests read a few paths from the TxFlow schema structurally. When PR #723 added `additional_signers_count`, the schema had to be edited by hand. Nothing would detect a new intent, a renamed field, or a removed one.

### 1.4 Standalone TxPlan silently drops unknown intent fields

All 22 concrete intent classes carry `@JsonIgnoreProperties(ignoreUnknown = true)`. In a standalone TxPlan a misspelled field is silently ignored:

```yaml
# probe: TxPlan.from(yaml)
transaction:
  - tx:
      from: addr_test1sender
      intents:
        - type: donation
          current_treasury_value: 100
          donation_amout: 5          # typo
# result: DonationIntent(currentTreasuryValue=100, donationAmount=null), no error
```

Intents are the only lenient place. A probe added an unknown key at each level and read the document with both `TxPlan.from` and `TxPlanCodec`: an unknown key at the root, in `context` or in `tx` is rejected by both; an unknown key in an intent is accepted by both.

Portable (v1alpha1) TxFlow already rejects this: `QuickTxTemplateMaterializer`'s strict mapper strips `ignoreUnknown` (`withoutIgnoreUnknown()`, line 461). This diverges from ADR 0002 rule 3, which says a TxFlow step's `tx` and `context` use "the same Java model, serialized names, intent discriminators, validation rules, and execution semantics" as TxPlan.

### 1.5 The model already contains what a generator needs

A probe ran Jackson deserialization introspection (`getDeserializationConfig().introspect(type).findProperties()`) on the real mapper (`YamlSerializer.getYamlMapper()`). It listed the accepted property names and Java types for all 23 type names, with no new dependency. For example:

```
stake_withdrawal: reward_address=String amount=BigInteger receiver=String redeemer_hex=String
voting: voter_hex gov_action_tx_hash gov_action_index=Integer vote anchor_url anchor_hash redeemer_hex
validator: role=RedeemerTag cbor_hex script_ref script_hash version=PlutusVersion
```

- Aliases are visible through `findAliases()`; `collect_from` accepts `refs` for `utxo_refs`.
- Which arrays accept a type is visible from its Java interface and each array's declared element type: `inputs` is `List<TxInputIntent>` (`collect_from`, `script_collect_from`, `reference_input`), `scripts` is `List<TxScriptAttachmentIntent>` (`validator`, `native_script`), and `intents` is `List<TxIntent>`, so it accepts every type (corrected in r2, see D4).
- A second probe compared serializable and deserializable properties per class. The only property written but not read back is `type`, the discriminator Jackson consumes itself. So rejecting unknown fields cannot break a `toYaml()` → `from()` round trip.

### 1.6 Placeholders appear in typed fields

A TxPlan file is often a template. Numeric and enum fields can hold `${var}` (TxPlan variables) or `${{ inputs.x }}` (TxFlow bindings). There are two read paths:
- `TxPlanCodec` resolves variables across the whole tree before binding.
- `TxPlan.from(yaml)` substitutes `${var}` as **text** across the whole document (including the `variables` block and keys) when a `variables` section is present, then binds; intents later resolve their own string fields (corrected in r5).

A schema meant for files *as written* must therefore accept a placeholder string wherever a typed value is expected.

## 2. Goals

1. The schema states every core intent type, its fields, and which array it belongs in, so a user can learn the format from the schema alone.
2. Adding or changing an intent cannot leave the schema stale: the build fails until it is regenerated.
3. Editors and CI can validate a standalone TxPlan file and a TxFlow step with any JSON Schema tool.
4. Standalone TxPlan rejects unknown intent fields, matching TxFlow (ADR 0002 rule 3).

## 3. Non-Goals

- Field descriptions, `required` lists beyond `type`, or cross-field rules (mutually exclusive fields, conditional requirements). Each intent's own validation stays the authority.
- Schemas for extension intents (`<namespace>:<type>`). They stay open; an extension-contributed schema fragment is future work.
- Fixing model quirks the generator exposes (§13); the schema publishes the current format as-is.
- A new schema version. This stays `v1alpha1`. (r10: the file and `$id` are renamed from `quicktx-transaction` to `txplan`; see §7.)
- Runtime validation against the JSON Schema itself.

## 4. Trust and Validation Boundary

The schema is an authoring and CI aid, not a security boundary. Portable input is untrusted. Its enforcement stays in the Java readers: strict parsing (D8), per-field validation such as the `additional_signers_count` bound from PR #723, and each intent's own checks. A document that passes the schema can still be rejected at runtime, and the runtime must never assume a document was schema-validated.

## 5. Decisions

### D1. Generate intent definitions from the Jackson model

The single source of truth is what Jackson actually binds. A generator in quicktx **test** sources (no main-jar change, no runtime dependency):

- walks `@JsonSubTypes` on `TxIntent`, so a type registered there is picked up automatically
- asks Jackson for the bean deserializer it actually builds for each class, using the readers' own mapper (`YamlSerializer.getYamlMapper()`), and takes the property names from that deserializer (r2). Bean-description introspection alone is not faithful: it reports no properties for `UnitInterval`, which Jackson binds through its private fields. Aliases come from introspection (`findAliases()`).
- emits one definition per type name:
  `$defs/intent.<type>` = `{type: object, additionalProperties: false, required: [type], properties: {type: {const: "<type>"}, …}}`. The `type` discriminator is always the `const`, even where a class also exposes a `type` setter (`pool_registration`).
- emits each accepted alias as an additional property with the same schema
- recurses into nested beans (for example `Amount`, `Asset`, `PoolRegistration`, `UnitInterval`), as `$defs/model.<SimpleName>` with `additionalProperties: false`. Two nested classes with the same simple name fail generation instead of colliding.
- handles polymorphic nested types generically (r2): a type with `@JsonTypeInfo(use = NAME, property = p)` and `@JsonSubTypes` (for example `Relay`, discriminated by `relay_type`) becomes an `anyOf` over one definition per subtype, each with `p` as a `const`, plus the placeholder-discriminator branch in D3.

### D2. The generator owns a marked region; everything else stays hand-written

Generated keys are exactly `$defs/intent.*`, `$defs/model.*`, `$defs/placeholder`, `$defs/integerLike`, `$defs/booleanLike` and the `tx.inputs`, `tx.intents` and `tx.scripts` properties (r5: the whole property, so the array itself may be a placeholder). The generator reads the committed file, replaces only those keys, and writes it back with sorted, stable key order. Everything else stays hand-written and untouched, including `context` with its bounds, `utxoRef`, `flowOutputRef`, `inputRef`, `transaction`, `tx`, and the root (D5).

**Hand-written typed fields are placeholder-tolerant too (r2).** `context` integer fields accept the same placeholders as generated ones, so `additional_signers_count: '${count}'` (accepted by `TxPlanCodec`, see `TxPlanContextPropertiesTest`) is valid:
- `valid_from_slot` and `valid_to_slot` become `anyOf [integer, integerLike, placeholder]`; Jackson coerces a numeric string to `Long`. Negative slots are currently accepted, so a schema minimum would violate I10 (see §13).
- `additional_signers_count` becomes `anyOf [integer 0–1000, placeholder]`. It takes no numeric string, because its `StrictIntegerDeserializer` rejects strings.
- (r4) Hand-written `String` fields of `context` and `tx` (`fee_payer`, `from`, `change_address`, …) accept every scalar, like generated ones (D3). Reference text fields (`tx_hash`, `step`, `output`) accept strings or numbers, matching `UtxoRefDeserializer`'s numeric-to-text conversion (r5).
- (r4) A `signers` entry does not require `ref`. Today the runtime accepts an entry without `ref`, and `compose(TxPlan)` skips it; see §13.

### D3. Type mapping is placeholder-tolerant

`$defs/placeholder` is a string containing `${`. `$defs/integerLike` is a string of decimal digits with an optional leading sign (`new BigInteger("+5")` is valid). Every non-string schema below is wrapped as `anyOf [<schema>, placeholder]`, including arrays and nested objects, because `TxPlanCodec` can replace a whole value with a bound variable.

| Java type (as Jackson binds it) | Schema |
|---|---|
| `String` | `string`, `number` or `boolean` (r3). Jackson coerces any scalar to text, and the canonical writer relies on it: it writes digit-only hex such as `cbor_hex: 49480100002221200101` and the `String`-typed donation amounts unquoted. A string-only schema rejected the writer's own output (found by the I10 tests). |
| `int`, `Integer`, `long`, `Long`, `BigInteger` | `integer` or `integerLike` (quantities are written quoted today) |
| `boolean`, `Boolean` | `boolean` or `booleanLike` (r5: `true`/`True`/`TRUE`/`false`/`False`/`FALSE` as text, with surrounding whitespace, because `TxPlan.from` substitutes a quoted `'${flag}'` as text) |
| enum (`RedeemerTag`, `PlutusVersion`, …) | `enum` of the names the mapper writes for each constant, e.g. `spend`, `v2` (r2: `RedeemerTag` uses `@JsonProperty` names, and the mapper is case-sensitive, so `Spend` and `V2` are rejected), or the same name with surrounding whitespace, which Jackson trims (r5) |
| `List<X>`, `Set<X>` | `array` of X |
| nested bean | `$ref` to `$defs/model.<SimpleName>` |
| polymorphic nested type | `anyOf` over its subtype definitions (D1) and an object whose discriminator is a placeholder (r5) |
| `JsonNode` | `{}` (open: `datum`, `redeemer`, `utxo_filter` have their own grammars) |

**Custom deserializers (r2)** are mapped through one explicit table in the generator:

| Deserializer | Schema |
|---|---|
| `MetadataDeserializer` | `{}` (YAML, JSON or CBOR hex) |
| `HexToByteArrayDeserializer` | `string` or `number` (r5: it reads the token's text, so unquoted digit-only hex is accepted) |
| `InetAddressDeserializer` | `string` or `number` (r5: same reason) |
| `UtxoRefDeserializer` | `$ref` to the hand-written `$defs/inputRef` (`utxoRef` or `flowOutputRef`) |

A Java type the generator cannot map, or a custom deserializer missing from the table, fails generation and names the class and property, rather than silently emitting `{}`. Type-level custom deserializers are checked before built-in mappings, including enums. (r5) Generation also fails on constructs whose accepted input the schema would not describe: `@JsonUnwrapped`, a property Jackson binds that the bean deserializer does not list, scalar or delegating creators on non-polymorphic beans, enum constant aliases, content or key deserializers, `ACCEPT_SINGLE_VALUE_AS_ARRAY`, and `@JsonTypeInfo(defaultImpl)`. Subtypes are taken from Jackson's `SubtypeResolver`, so a subtype named only by `@JsonTypeName` is included. Reusing a nested model definition with incompatible type parameters or discriminator names/values also fails generation; each shape needs an explicit mapping instead of silently sharing the first definition.

### D4. Array placement and extension intents (revised in r2)

Each array accepts what its declared element type accepts at runtime. `TransactionDocument.TxContent` declares `inputs` as `List<TxInputIntent>`, `scripts` as `List<TxScriptAttachmentIntent>` and `intents` as `List<TxIntent>`, and Jackson resolves any registered subtype assignable to the element type:
- `tx.inputs.items` = `anyOf` over the `TxInputIntent` types (`collect_from`, `script_collect_from`, `reference_input`).
- `tx.scripts.items` = `anyOf` over the `TxScriptAttachmentIntent` types (`validator`, `native_script`).
- `tx.intents.items` = `anyOf` over **all 23** types.

(r4: `anyOf`, not `oneOf`. The core branches exclude each other by their `const` discriminator, but the extension branch and the placeholder-discriminator branch can both match a type such as `${ns}:pay`.) A probe confirmed that both readers accept `collect_from` under `intents`. The canonical writer (`toYaml()`) still places input and script intents in their own arrays.

A core type in an array whose element type it does not implement (e.g. `payment` in `inputs`, `collect_from` in `scripts`) fails validation; the runtime rejects it too ("Could not resolve type id … as a subtype of TxInputIntent").

**Extension intents** (r2): only `tx.intents` gets the open branch for an object whose `type` matches `^[^:]+:.+$` (e.g. `fk:pay`). This matches ADR 0008 and `FlowExtensions.checkTemplateIntents`, which rejects extension intents in `inputs` and `scripts` ("Extension intents are allowed only in tx.intents").

**Placeholder discriminators** (r2): each array also accepts an object whose `type` is a placeholder (contains `${`), since `TxPlanCodec` resolves variables before binding. Its fields are not checked by the schema. (r5) An element may itself be a placeholder, as may the whole array; the hand-written `context`, `transaction`, entries, `tx`, `required_signers`, `signers` and `flow_output` accept a placeholder too.

### D5. Root schema for a standalone TxPlan document (hand-written)

Add root properties describing the standalone envelope: `version`, `variables` (open map), `extensions` (open map), `context` (`$ref` to the existing definition) and `transaction` (array of `{tx: $ref tx}`, `additionalProperties: false`, so `scriptTx` is not valid). `tx` is not required: the runtime skips an empty entry (r3, found by the I10 fuzz target). `$defs/transaction` keeps its current shape, so TxFlow's `$ref …#/$defs/transaction` resolves to the same definition; r10 renamed the schema, so the `$ref` URI changed with it.

### D6. Drift guard and one regenerate command

- `TxPlanSchemaTest.schemaIsUpToDate()` regenerates in memory and fails on any difference with: "TxPlan schema is out of date: run `./gradlew :quicktx:generateTxPlanSchema`".
- `quicktx/build.gradle` adds `generateTxPlanSchema` (`JavaExec` on the test runtime classpath), which writes the file.
- The result: adding, renaming or removing an intent field, or adding a `@JsonSubTypes` entry, fails `./gradlew :quicktx:test` until regenerated, and the regenerated diff appears in the PR for review.

### D7. Prove the schema accepts real documents

A test validates documents against the schema using `com.networknt:json-schema-validator` **2.x** in quicktx `testImplementation` only (OQ2, resolved; choice in §10.1):
- **Accepted** (r5: stated as built): `toYaml()` output for one sample of every intent type plus variants (structured Plutus data, flow-output references, other relays, governance actions and DReps); every quicktx test text block that parses as a TxPlan document (`%s` templates filled); every YAML block of the TxPlan docs; and, in txflow, the embedded transaction of every hand-written portable flow that compiles with one value per parameter. YAML built by string concatenation is not scanned. Per-source floors fail the test if a scan finds fewer documents than today.
- **Rejected:** a misspelled intent field, an unknown core type, a type in an array whose element type it does not implement (`payment` in `inputs`), an extension intent in `inputs` or `scripts`, a wrong enum value (`Spend`), and an out-of-bounds literal `additional_signers_count`.

The accepted documents were not produced by the generator, so they are independent evidence.

### D8. Standalone TxPlan rejects unknown intent fields

By default, both standalone readers (`TxPlan.from(yaml)` and `TxPlanCodec`) read core intents with `ignoreUnknown` disabled, as TxFlow's strict mapper does, so `donation_amout` fails and names the field (OQ1, resolved). The `@JsonIgnoreProperties(ignoreUnknown = true)` annotations stay in place for other mappers; the readers override them, so nothing else changes. Extension intents keep their own `ignoreUnknown` setting (see below).

**Opt-out.** `TxPlanCodec.builder().strictReading(false)` restores today's behaviour: unknown intent fields are ignored. The default is `true`.
- The switch exists only on `TxPlanCodec`. `TxPlan.from(yaml)` is the convenience reader and is always strict; a caller who needs lenient reading uses the codec. `TxPlan.fromSnapshot` (r9) is not a general lenient reader: it is for this library's own `toYaml()` output (see TxFlow snapshots below).
- The switch controls all strict reading rules below, including scalar coercions, nulls and reference shapes. Unknown keys outside intents (`context`, `tx`, the root) are already rejected today, because those classes carry no `ignoreUnknown`, and they stay rejected in either mode.
- TxFlow's portable compilation stays strict regardless: ADR 0002 requires portable documents to "reject unknown fields by default".

**What "strict" covers (r3).** The I10 tests showed that rejecting unknown names alone still lets the runtime accept documents no schema should describe. So the strict readers also refuse silent scalar and reference coercions:
- **Unknown and ignored names:** `FAIL_ON_UNKNOWN_PROPERTIES` and `FAIL_ON_IGNORED_PROPERTIES` with `ignoreUnknown` stripped, exactly as TxFlow's strict mapper does. Names of `@JsonIgnore` Java-only fields are rejected rather than skipped.
- **No fractional truncation:** `ACCEPT_FLOAT_AS_INT` is disabled. Today `quantity: 2.5` silently becomes `2`, the same bug class as PR #723's F2. (r9) A whole number written as a float (`quantity: 20000000.0`, as JSON tools often write it) still reads as that integer: only a fractional part is rejected, and a value outside the field's range is rejected rather than wrapped. (r11) The conversion uses the exact decimal value, which the TxPlan readers have, and rejects a value longer than Jackson's number limit (1000 digits) before building it. A `double`, which TxFlow's parser produces, may already be rounded, so TxFlow rejects every float for an integer field. Fields with their own deserializer (`additional_signers_count`) take integers only.
- **No numeric enum indexes:** `FAIL_ON_NUMBERS_FOR_ENUMS` is enabled. Today `role: 0` silently becomes `spend`.
- **Bounded decimal conversion (r12):** fractional decimals are rejected before integer conversion expands their scale; `1E-100000000` fails immediately. The number length includes a minus sign, and Jackson's decimal-to-integer scale constraint also applies. Whole-number decimals and scaled zero within those limits remain valid.
- **Exact UTxO references:** `UtxoRefDeserializer` is hand-written and today ignores every key it does not use. A probe showed it accepts `{flow_output: d}` (a string) and `{tx_hash: …, output_index: 0, index: 1}`, producing an empty or partial reference. In strict mode a reference is exactly `{tx_hash, output_index}` or `{flow_output: {step, output}}`, with no other keys.

- **No explicit nulls (r4):** `key: null` and an empty value (`address:` with nothing after it) are rejected, as are `null` list elements (`Nulls.FAIL`). An empty value is usually a forgotten value, and treating it as absent would be another silent drop. The canonical writer never writes `null`. Found by fuzzing: `refs: null` overwrote a field default, so `toYaml()` had no fixed point.

- **Further rules (r5, from the multi-agent review and fuzzing).** Each was a real silent drop or coercion, reproduced first:
  - one property under two names (`utxo_refs` and its alias `refs`: Jackson kept the last list), and a pool `type` that contradicts `is_update`/`update`
  - the string `"null"` for a primitive (`is_update: 'null'` turned a `pool_update` into a `pool_registration`)
  - `output_index` beyond `int` range (`4294967296` became 0), negative, or text that is neither digits nor a placeholder
  - YAML aliases (`*r` was read as the string `"r"`), and unquoted numbers not in plain decimal form (`010` → 8, `0x10`, `1_000`, `1e3`; an unquoted `datum_hex: 0102` became `"66"` in the codec). The check reads the raw scalar text. (r7) It covers `variables` too, since a variable value is substituted as it was read; there a decimal only has to be decimal text, so `1.50` keeps its scale.
  - (r7) in `TxPlan.from`, a placeholder used as a mapping key (`'${field}': 5`). Its textual substitution renamed the key; `TxPlanCodec` and the schema substitute values only, and reject the key as unknown.
  - (r9) an entry of `inputs`, `intents` or `scripts` written as a list (`[pool_update, {is_update: false}]`). Jackson binds this form, and the alias and pool-flag checks above did not see it, so the example became a `pool_registration`.
  - (r9, already enforced, now listed) a second YAML document with content after `---` (an empty trailing document is allowed); an empty string for any non-text value; a number or numeric text for a boolean.
  - in `TxPlan.from` only, a declared variable whose value is itself a `${...}` template, or whose name contains `$`, braces or a line break (its textual substitution resolved one more level per read, so there was no fixed point). Checked on the `variables` block as written. `TxPlanCodec` keeps its existing contract: a replacement value is literal and never re-read as a template, and snapshots are read with `readLiteral` (r6).
- **Number form (r9):** the plain-decimal check compares a float with its `BigDecimal` text, which is how the readers bind it: `20000000.0` and `1.50` are plain, `1e3`, `.5` and `0.0000001` (which reads as `1E-7`) are not. The canonical writer quotes a string that either comparison would read back differently.
- **Limitation (r9):** the readers parse YAML 1.1, so unquoted `yes`, `no`, `on` and `off` are booleans; a text field given `datum_hex: yes` receives `"true"`. Quote such values. A YAML 1.2 tool reading the same file sees a string, so the schema and the runtime can disagree on these words.
- **TxFlow (r5):** its portable compiler now builds its mapper with the shared `StrictReading.apply`, so a value is no longer coerced (`quantity: 2000000.5` → `2000000`) before the strict check sees it.
- **TxFlow snapshots (r9):** TxFlow copies an in-memory plan (execution snapshots, compiled steps, stream copies) with `TxPlan.fromSnapshot`, which reads this library's own `toYaml()` output without the strict rules, as `TxPlan.from` did before this ADR. A plan built in Java, for example with `addVariable("b", "${a}")`, therefore runs as before. Legacy TxFlow documents re-write each step with the canonical writer before `TxPlan.from` reads it, so a quoted `"0xabc"` stays a string. (r11) A literal copy (`TxPlanCodec.readLiteral`, as used for plans with extensions) was considered: `compose(TxPlan)` does not resolve variables, so a Java-built core plan whose payment address is its own `${beneficiary}` would no longer resolve. `fromSnapshot` keeps today's behaviour.
- **Extension intents (r5, narrowed):** they keep their own `ignoreUnknown`; the scalar, null and YAML rules apply to their fields too.

Fixed in both modes, because they were crashes or silent corruption rather than lenient behaviour worth keeping: malformed structured Plutus data (`fields: 5`, map entries without `k`/`v`, `constructor: abc` became 0); null intents and `scriptTx` entries (now `IllegalArgumentException`); and the canonical writer, which now quotes a string YAML would read back differently (`"0xabc"` was written unquoted and read back as 2748; `".inf"` as a float).

`strictReading(false)` restores the strict rules to today's behaviour, except for the fixes explicitly applied in both modes above. It does not change `TxPlan.from` or TxFlow.

### D9. Documentation

The TxPlan docs gain a "Validating your YAML" section: the schema URL, how to point VS Code (YAML extension) or `check-jsonschema` at it, and what the runtime still checks beyond the schema (§4).

## 6. Invariants

- **I1** (D1, D6): every `@JsonSubTypes` name on `TxIntent` has exactly one `$defs/intent.<name>`, whose `type` is `const` that name.
- **I2** (D1, D3): each intent definition's property set equals the class's Jackson-deserializable property names plus aliases, with `additionalProperties: false`.
- **I3** (D2): generation never changes a hand-written key; running the generator on its own output is a no-op.
- **I4** (D6): generation is deterministic, and the guard fails on any difference between the committed and regenerated schema.
- **I5** (D7): every accepted document in D7 validates, and every rejected document in D7 fails.
- **I6** (D5): `$defs/transaction` stays referenceable from `txflow-v1alpha1.schema.json`; TxFlow golden hashes and fingerprints are unchanged.
- **I7** (D8): an unknown field in any core intent fails in `TxPlan.from`, `TxPlanCodec` and portable TxFlow compilation alike, with the field name in the error.
- **I8** (D8): `toYaml()` → `from()` round-trips every intent type with strictness on.
- **I9** (D8): with `strictReading(false)`, `TxPlanCodec` reads every document exactly as it does today (unknown intent fields ignored), and the option never relaxes TxFlow compilation. Stated exceptions (r5): inputs that used to crash or corrupt silently (D8, "fixed in both modes") are rejected or written correctly in both modes. (r9) The shared YAML mapper reads floats as `BigDecimal` in both modes, so a decimal variable keeps its precision and `getVariables()` returns `BigDecimal` where it returned `Double`. The lenient codec reads like `TxPlanCodec` did; it does not reproduce `TxPlan.from`'s old textual substitution.
- **I10** (D1–D4, r2): **the schema is never stricter than the runtime.** Every document a strict reader accepts validates against the schema. Checked by the D7 corpus and by fuzzing (§9).
- **I11** (D8, r2; wording r4): **readers are total over untrusted text.** For any input, `TxPlan.from` and `TxPlanCodec.fromYaml` (strict and lenient) either return a plan or reject the input. They never fail with a programming error: no `NullPointerException`, `ClassCastException`, `IndexOutOfBoundsException`, `ArithmeticException`, `UnsupportedOperationException`, `StackOverflowError` or other `Error` anywhere in the cause chain.
- **I12** (D8, r2; scope r4, clarified r8): **strictly accepted plans have a fixed point.** For any input a strict reader accepts, its serialized snapshot reads back and serializes identically. `TxPlan.from` snapshots are read with `TxPlan.from`; codec snapshots use `codec.readLiteral(codec.toYaml(plan))`, so retained variables and literal `${...}` replacement values are not rebound. The lenient codec keeps today's reading, quirks included (I9), so I12 does not cover it.

## 7. Compatibility

- **Schema:** stays `v1alpha1`. (r10) The file is renamed from `quicktx-transaction-v1alpha1.schema.json` to `txplan-v1alpha1.schema.json`, and its `$id` from `…/schemas/quicktx/transaction/v1alpha1` to `…/schemas/txplan/v1alpha1`; only 0.8.0 pre-release builds shipped the old names, and TxFlow's `$ref` is updated with it. It becomes stricter: a document with unknown intent fields that passed the loose schema now fails it. Such fields were already ignored at runtime, so they had no effect.
- **Runtime (D8):** a standalone TxPlan with an unknown or misspelled intent field that parses today will fail. 0.8.0 is pre-release (`0.8.0-pre7-SNAPSHOT`), and portable TxFlow already behaves this way. The release notes must call it out, together with the `TxPlanCodec.builder().strictReading(false)` opt-out.
- **Format quirks:** the generated schema publishes current names as-is (§13), so no document changes meaning.
- **TxFlow (r6):** portable compilation now binds embedded transactions with the shared strict mapper. Templates that compiled before and now fail with `TXFLOW_TRANSACTION_INVALID` include `context.fee_payer: null` (and other explicit nulls), `role: 0`, `quantity: ''`, `quantity: 2000000.5`, any float for an integer field including `quantity: 2000000.0` (TxFlow reads floats as `double`, so only TxPlan files get the whole-number reading; r11), and a pool `type` that contradicts its update flag. The YAML-text checks (aliases, number forms) do not yet apply to TxFlow YAML (§13). Plans built in Java are copied with `TxPlan.fromSnapshot` and are not affected (r9).
- **Canonical writer (r6):** only strings YAML would read back as a different number are now quoted (`0x1F`, `1_000`, `+5`, `-0`, `.5`, `1e3`, `.inf`, …); a before/after comparison of the writer over a set of tricky strings shows every other value byte-identical. Hashes of `toYaml()` text change only for plans that held such a value, and those plans did not read back correctly before.
- **Build:** one test-scope dependency (`com.networknt:json-schema-validator` 2.x); published artifacts' dependencies do not change.

## 8. Implementation Milestones

**Order (r3): M2 lands first, then M1.** I10's oracle is "a strict reader accepts the document". Implemented first, M1 would have to emulate M2's strictness in test code (unknown fields, scalar coercion, reference shapes) and delete it again in M2. With M2 first, M1's fuzz target uses the real strict readers. The IDs are unchanged; only the gates moved.

### M1. Generated schema, root schema, guard, conformance tests, docs (one PR)
- **Entry gates:** M2 merged (r3).
- **Scope:** D1–D7 and D9.
- **Exit criteria:** I1–I6 and I10 hold; `./gradlew :quicktx:test :txflow:test` passes (Java 17); a mutation check (remove one intent field locally) fails the guard; the I10 fuzz target runs with `JAZZER_FUZZ=1` and finds no counterexample.

### M2. Runtime strictness (separate PR, OQ3)
- **Entry gates:** this ADR accepted (r3: M2 now lands before M1).
- **Scope:** D8, including the `strictReading` opt-out, plus release notes.
- **Exit criteria:** I7, I8 and I9 hold for every intent type, with a test per type in quicktx (TxFlow uses the same mapper, so its tests cover representative types); the I11 and I12 fuzz targets run with `JAZZER_FUZZ=1` and find no counterexample; the quicktx, txflow and programmable-token suites pass.

## 9. Verification Strategy

- **Independent acceptance evidence:** existing fixtures, test documents and doc examples written before the generator existed (D7).
- **Negative cases:** typo fields, unknown types, a type in an array whose element type it does not implement, extension intents outside `tx.intents`, wrong enum values, `scriptTx` entries at the root.
- **Placeholder cases:** `${var}` and `${{ inputs.x }}` in integer, boolean and enum fields are accepted by the schema.
- **Drift:** the guard plus a mutation check per milestone.
- **Runtime (M2):** per-type unknown-field tests through `TxPlan.from` and `TxPlanCodec`, and a portable TxFlow compile for representative types; a round trip for every type; the same documents with `strictReading(false)` read as today.
- **Fuzzing (r2):** Jazzer `@FuzzTest` targets in quicktx, following the existing MPF setup (`libs.jazzer.junit`; a normal build replays committed seeds; `JAZZER_FUZZ=1` explores). jqwik is not used, because quicktx deliberately excludes it from its test classpath.
  - **M1, schema soundness (I10):** structured mutations of corpus documents (drop, duplicate, rename or retype fields, swap intent types and arrays, insert placeholders). Whenever a reader accepts the mutant, the schema must validate it.
  - **M2, reader totality (I11):** arbitrary YAML text and structured mutations into both readers, strict and lenient.
  - **M2, fixed point (I12):** for every accepted mutant, `toYaml()` validates, reads back, and is idempotent.
  - Any crash input found is committed as a seed once fixed.

## 10. Alternatives Considered

- **Hand-write per-intent definitions.** Rejected: this is the current failure mode. It drifted silently (PR #723 needed a manual edit, and nothing detects a missing intent).
- **Use a schema-generator library (e.g. victools `jsonschema-generator` with its Jackson module).** Rejected for now: it adds a dependency and would still need customization for placeholders, interface-based array placement and aliases. Reconsider if descriptions or `required` lists become goals.
- **Generate at build time without committing the file.** Rejected: the schema is a published contract referenced by URL from TxFlow and users. Committing it gives a reviewable diff for every format change.
- **Annotate intent classes with schema annotations.** Rejected: it duplicates what Jackson already knows, and the annotations can drift the same way.
- **Validate against the JSON Schema at runtime.** Rejected: it needs a runtime dependency and duplicates Jackson strictness (D8), which also covers in-memory documents.

### 10.1 Test-scope JSON Schema validator (D7)

Checked on 2026-10-25. GitHub figures are from the GitHub API; compliance is the [Bowtie](https://bowtie.report/) badge for the official JSON Schema test suite; versions are from Maven Central `maven-metadata.xml`.

| Library | Stars | Last push | Licence | Draft 2020-12 | JSON model | Notes |
|---|---|---|---|---|---|---|
| **networknt/json-schema-validator** | 1,085 | 2026-10-24 | Apache-2.0 | 100% (Bowtie tests 3.0.6) | Jackson | Two maintained lines: **2.x** (Java 8+, Jackson 2; latest 2.0.8) and 3.x (Java 17+, Jackson 3; latest 3.0.8). Also reads YAML through Jackson. |
| everit-org/json-schema | 900 | 2025-08-01 | Apache-2.0 | Not supported | org.json | README: "maintenance mode and superseded by erosb/json-sKema"; drafts 4, 6 and 7 only. |
| erosb/json-sKema | 84 | 2026-10-02 | MIT | Supported (README); not in Bowtie | Own parser | Successor to everit; pre-1.0 (v0.32.0). |
| harrel56/json-schema (`dev.harrel`) | 35 | 2026-10-20 | MIT | 99% | Pluggable (Jackson 2/3, Gson, …) | Java 8+. |
| OptimumCode/json-schema-validator (KMP) | 66 | 2026-10-19 | MIT | 99% | kotlinx | Kotlin Multiplatform; pre-1.0. |
| leadpony/justify | 103 | 2023-06-13 | Apache-2.0 | Not in Bowtie | JSON-P | Last release v3.1.0 (2020-11-03); inactive. |

**Choice:** networknt 2.x. (r10) It is declared with its Jackson dependencies excluded, so test classpaths keep the Jackson version the library ships. It is the most widely used and actively maintained of these, passes the full Draft 2020-12 suite, and uses Jackson, which CCL already depends on (2.21). The 2.x line matches CCL's Jackson 2; move to 3.x only if CCL moves to Jackson 3. Its runtime dependencies (`jackson-databind`, `itu`, `slf4j-api`) stay in test scope. The runner-up is harrel56, which is equally current and Jackson-compatible but far less used.

## 11. Consequences and Risks

- **Positive:** the schema becomes a usable reference and validation target; drift is a build failure; TxPlan and TxFlow converge on one validation rule (ADR 0002 rule 3).
- **R1:** the generated schema may reject a valid document, e.g. a field type the mapping handles wrongly. Mitigation: D7's acceptance corpus, and a generator that fails on unmapped types instead of guessing.
- **R2:** placeholder tolerance weakens type checking in template files (any `${…}` string passes). Accepted: the runtime validates resolved values.
- **R3:** D8 breaks documents that relied on ignored fields. Mitigation: pre-release timing, release notes, and an error message that names the field.
- **R4:** contributors must remember to run the regenerate task. Mitigation: the guard's failure message names the command.

## 12. Questions

All four were answered by the maintainer on 2026-10-25, before review; none remain open.

1. **OQ1 (D8), resolved:** reject unknown intent fields **by default**, with a `TxPlanCodec.builder().strictReading(false)` opt-out. Reasons: 0.8.0 is pre-release, portable TxFlow already rejects them, and ADR 0002 rule 3 asks for the same validation rules.
2. **OQ2 (D7), resolved:** add `com.networknt:json-schema-validator` 2.x to quicktx `testImplementation` (§10.1).
3. **OQ3, resolved:** two PRs, so the behaviour change is reviewed on its own. The revised order is M2, then M1 (§8), with strict readers supplying the schema acceptance oracle.
4. **OQ4 (§13), resolved:** publish the current quirks as-is. The generator stays a faithful mirror, and the quirks get a follow-up.

## 13. Related Findings (Out of Scope)

- (r6) Also not addressed: a `variables` entry with an explicit null value is accepted; after `TxPlan.from`'s textual substitution, an error can name the substituted text rather than the variable (e.g. "Number 0x10 … quote it" for a variable the user did quote).
- (r5) Follow-ups from the multi-agent review, not addressed here:
  - `TxPlan.from` substitutes variables as text: a value containing ` #` is cut at the comment, and a map value is written as Java `toString()`. Recommend structural resolution, as `TxPlanCodec` does.
  - `InetAddressDeserializer` calls `InetAddress.getByName`, so a non-literal `ipv4`/`ipv6` triggers a DNS lookup while reading untrusted YAML.
  - `pool_registration.pool_registration.type` overwrites the `final` `PoolRegistration.type` field by reflection.
  - Negative `valid_from_slot`/`valid_to_slot` are accepted; the schema mirrors this.
  - TxFlow's own YAML parsing (`TxFlowCodec`) does not apply the YAML alias and number-form checks.
  - (r9) Legacy (non-portable) TxFlow documents bind steps with `FlowDocument`'s mapper, which keeps `ignoreUnknown`, so a misspelled intent field is still dropped there. I7's "TxFlow compilation" means portable v1alpha1.
  - (r11) Open review items, not addressed here: a leading empty YAML document (`---\n---\n<plan>`) fails with "root must be a mapping"; `Integer`/`Long` fields accept non-ASCII digits (`"٥"`), which the schema's `integerLike` rejects; legacy `FlowDocument` still throws `UnsupportedOperationException` for `scriptTx`; no test ties the hand-written closed schema sections (root, `tx`, `context`, `signers`, `utxoRef`) to their Java models; the schema `$id` is not served, so a decision on publishing it or documenting a reference mapping is open; the issue link and the location of the release notes.
  - (r11) Metadata text that starts with `0x` (`"0x1F"`) is read back from TxPlan YAML as bytes, because the metadata module's text convention treats a `0x` prefix as hex. Found by the DevKit run; it predates this ADR.
  - (r9) A pool relay written as a list (`[single_host_addr, {...}]`) is bound by the runtime and rejected by the schema. It changes no value, so it is left for a follow-up.

- Two existing tests (`TxPlanRealQuickTxBuilderTest`, `TxPlanQuickTxBuilderIntegrationTest`) give a `payment` intent a `to:` field, which does not exist; the lenient readers drop it silently. M2 corrects them to `address:`. This is the D8 bug class in the repository's own tests.
- `docs/content/preview/txplan/advanced-usage.mdx` uses `type: script_collect`, which is not a registered type (`script_collect_from` is); M1 corrects it. Another example there carries truncated, illustrative hex.
- (r4) A `context.signers` entry without `ref` is accepted and then silently skipped by `compose(TxPlan)`. It sits outside intents, so D8 does not cover it; the schema mirrors the runtime. Follow-up: reject it in strict mode.
- (r4) Found by fuzzing and fixed in M2 because they break I11 or I12 in both modes: `transaction: null`, a null transaction entry and an empty YAML document threw `NullPointerException`; an unresolved `metadata: ${v}` was accepted but could not be written back.
- The canonical writer emitted the string `.inf` unquoted, so it read back as a float and failed to bind. Fixed in M2 (D8, "fixed in both modes").
- `pool_registration` accepts the update flag three ways: the `type` discriminator (`pool_registration` / `pool_update`), `is_update`, and a further `update` property that Jackson derives from Lombok accessors.
- `script_minting` uses camelCase `policyId`; every other field is snake_case.
- Docs cover 4 of 23 intent types as YAML examples (§1.1). D9 adds validation guidance only; an intent reference page (possibly generated from the same model) is a follow-up.
