---
title: "TxPlan Overview"
description: "YAML-based transaction definitions with variable substitution"
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

TxPlan provides a configuration-driven approach to defining Cardano transactions. Instead of writing Java code for every
transaction, you can define transactions in YAML with variable substitution, context properties, and signer registry
references — then execute them through the standard `QuickTxBuilder` pipeline.

## Benefits

- **Configuration-driven** — define transactions in YAML files, separate from application code
- **Variable resolution** — use `${variable}` placeholders for dynamic values
- **Context properties** — centralize fee payer, collateral, validity, and signer configuration
- **Version control friendly** — human-readable transaction definitions
- **No extra dependency** — TxPlan is part of the `quicktx` module

## Dependency

TxPlan is included in the `quicktx` module. If you already use `cardano-client-lib` or `cardano-client-quicktx`,
no additional dependency is needed.

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-lib:<version>'
}
```

## Creating a TxPlan

### From a Tx Object

```java
Tx tx = new Tx()
        .from("addr_test1...")
        .payToAddress("addr_test1_receiver", Amount.ada(5));

TxPlan plan = TxPlan.from(tx)
        .feePayer("addr_test1_fee_payer")
        .validTo(2000L);

// Serialize to YAML
String yaml = plan.toYaml();
```

### From YAML

```java
TxPlan plan = TxPlan.from(yamlString);

// Access transactions
List<AbstractTx<?>> transactions = plan.getTxs();
```

## YAML Format

### Basic Structure

```yaml
version: 1.0
variables:
  sender: addr_test1_sender_address
  receiver: addr_test1_receiver_address
  amount: 5000000
context:
  fee_payer: ${sender}
  valid_to_slot: 5000
transaction:
  - tx:
      from: ${sender}
      intents:
        - type: payment
          address: ${receiver}
          amounts:
            - unit: lovelace
              quantity: ${amount}
```

### Variables

The `variables` section defines key-value pairs that can be referenced anywhere in the YAML using `${variable_name}`
syntax. Variables are resolved before deserialization, on the raw text. An undefined `${x}`, even in a comment, fails with
`Variable not found: x`, and a value containing YAML syntax such as `: ` or `#` can change the document's structure.

```yaml
variables:
  treasury: addr_test1_treasury
  alice: addr_test1_alice
  payment_amount: 10000000
  pool_id: pool1abc123
```

### Context Properties

The `context` section configures transaction-level settings that apply to the `QuickTxBuilder.TxContext`.

| Property | Description |
|----------|-------------|
| `fee_payer` | Address that pays transaction fees |
| `fee_payer_ref` | Registry reference for fee payer (e.g., `account://ops`) |
| `collateral_payer` | Address that provides collateral for script transactions |
| `collateral_payer_ref` | Registry reference for collateral payer |
| `signers` | Array of `{ref, scope}` entries for additional signers |
| `required_signers` | List of required signer credential hashes (hex) |
| `valid_from_slot` | Transaction validity start slot |
| `valid_to_slot` | Transaction validity end slot |
| `additional_signers_count` | Extra signers to count in fee estimation when they sign later, such as a multisig. Integer from 0 to 1000. Available from 0.8.0-pre7 |
| `deposit_payer` | Address that pays deposit (e.g., for stake registration) |
| `deposit_mode` | Deposit handling mode (`AUTO`, `CHANGE_OUTPUT`, `ANY_OUTPUT`, `NEW_UTXO_SELECTION`) |

### Transaction Entries

Each entry in the `transaction` list contains a `tx` block with:

- `from` / `from_ref` — sender address or registry reference
- `change_address` — optional explicit change address
- `change_datum` / `change_datum_hash` — datum for change output (script transactions)
- `inputs` — inputs to consume or reference: `script_collect_from`, `collect_from`, `reference_input`
- `intents` — what the transaction does (see below)
- `scripts` — script attachments: `validator`, `native_script`

Intent types:

| Area | Types |
|------|-------|
| Core | `payment`, `donation`, `minting`, `script_minting`, `metadata` |
| Staking | `stake_registration`, `stake_deregistration`, `stake_delegation`, `stake_withdrawal` |
| Pools | `pool_registration`, `pool_update`, `pool_retirement` |
| Governance | `drep_registration`, `drep_deregistration`, `drep_update`, `governance_proposal`, `voting`, `voting_delegation` |

Unknown keys under the document root, `tx` or `context` fail, but unknown fields inside an intent are dropped silently, so
check your spelling there.

### Plans with extensions

`TxPlan.from` and `toYaml` handle core intents only. For extension intents, such as [programmable tokens](/learn/cip113-programmable-tokens/),
use `TxPlanCodec`:

```java
TxPlanCodec codec = TxPlanCodec.builder().withExtension(extension).build();
String yaml = codec.toYaml(plan);
TxPlan restored = codec.fromYaml(yaml, runtimeVariables);

new QuickTxBuilder(backend).withExtension(extension).compose(restored);
```

## QuickTxBuilder Integration

### Direct Composition

```java
QuickTxBuilder builder = new QuickTxBuilder(backendService);

TxPlan plan = TxPlan.from(yamlString);

TxResult result = builder.compose(plan)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait();
```

### With Variables

> **Important:** Variables are resolved during `TxPlan.from()` — calling `addVariable()` after parsing only updates
> the variable map for `toYaml()` serialization. It does **not** re-resolve already-parsed transaction fields.

To use dynamic variables, set them in the YAML string before calling `from()`:

```java
String yaml = baseYaml
    .replace("${amount}", "10000000")
    .replace("${receiver}", dynamicReceiverAddress);

TxPlan plan = TxPlan.from(yaml);
```

Alternatively, define variables in the YAML `variables` section with their final values before parsing.

## Complete Example

```java
String yamlPlan = """
    version: 1.0
    variables:
      sender: addr_test1_sender
      receiver: addr_test1_receiver
    context:
      fee_payer: ${sender}
      valid_to_slot: 5000
    transaction:
      - tx:
          from: ${sender}
          intents:
            - type: payment
              address: ${receiver}
              amounts:
                - unit: lovelace
                  quantity: '5000000'
    """;

QuickTxBuilder builder = new QuickTxBuilder(backendService);
TxPlan plan = TxPlan.from(yamlPlan);

TxResult result = builder.compose(plan)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait();
```
