---
title: "TxPlan Advanced Usage"
description: "Signer Registry, script transactions, multi-tx plans, and dynamic configuration"
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

## Signer Registry

The `SignerRegistry` keeps private keys and signing logic out of YAML files. Senders, fee payers, collateral payers,
and additional signers are referenced by URI-style identifiers that the registry resolves at runtime.

### DefaultSignerRegistry

```java
SignerRegistry registry = new DefaultSignerRegistry()
        .addAccount("account://ops", opsAccount)
        .addPolicy("policy://nft", nftPolicy);
```

### Using Registry References in Java

```java
Tx tx = new Tx()
        .fromRef("account://ops")
        .payToAddress("addr_test1_receiver", Amount.ada(5));

Result<String> result = builder
        .compose(tx)
        .withSignerRegistry(registry)
        .feePayerRef("account://ops")
        .withSignerRef("account://ops", "payment")  // fromRef does NOT auto-add a signer
        .withSignerRef("policy://nft", "policy")
        .completeAndWait();
```

> **Note:** `fromRef()` resolves the sender address but does **not** automatically add a signer.
> Use `withSignerRef()` or the YAML `signers` context to provide signing.

- `fromRef` defers sender selection to the registry at compose time
- `feePayerRef` / `collateralPayerRef` resolve to a wallet or address at compose time
- `withSignerRef(ref, scope)` adds additional witnesses (scopes: `payment`, `stake`, `drep`, `committeeCold`,
  `committeeHot`, `policy`)
- Mixing address-based APIs with references is allowed; conflicts raise a `TxBuildException`. In YAML, if a `tx` sets
  both `from` and `from_ref`, `from_ref` wins silently

### Using Registry References in YAML

```yaml
version: 1.0
variables:
  receiver: addr_test1_receiver_address
  amount: 5000000
context:
  fee_payer_ref: account://ops
  collateral_payer_ref: account://ops
  signers:
    - ref: policy://nft
      scope: policy
transaction:
  - tx:
      from_ref: account://ops
      intents:
        - type: payment
          address: ${receiver}
          amounts:
            - unit: lovelace
              quantity: ${amount}
```

When any `*_ref` field or `signers` entries are present, supply a compatible `SignerRegistry` at runtime:

```java
TxPlan plan = TxPlan.from(yamlString);

Result<String> result = builder
        .compose(plan, registry)
        .completeAndWait();
```

If the registry cannot resolve a reference, `QuickTxBuilder` fails fast with a `TxBuildException`.

A reference resolves an address only. It doesn't add a signature, so list every key that must sign under `signers`,
including the sender's with `scope: payment`. With no signers at all, the build throws `No signers found`.

A plan with `from_ref` can be built only once, because the first build sets the plan's sender. Parse the YAML again for
each execution, and don't build one plan concurrently.

## Multiple `tx` entries

> **Important:** Each `tx` entry in a plan must have a **unique `from` address**. To send multiple payments from the
> same address, use multiple intents within a single `tx` entry rather than multiple `tx` entries.

A single YAML document can define several `tx` entries. They're merged into one on-chain transaction with one hash.
`fee_payer` is mandatory when there's more than one entry, and every sender must sign:

```yaml
version: 1.0
variables:
  treasury: addr_test1_treasury
  ops: addr_test1_ops
  pool_operator: addr_test1_pool_op

context:
  fee_payer: ${treasury}
  valid_to_slot: 5000

transaction:
  - tx:
      from: ${treasury}
      intents:
        - type: payment
          address: ${pool_operator}
          amounts:
            - unit: lovelace
              quantity: '500000000'

  - tx:
      from: ${ops}
      intents:
        - type: payment
          address: addr_test1_alice
          amounts:
            - unit: lovelace
              quantity: '100000000'
```

## Script Transactions in YAML

With the [Unified Tx API](/quicktx/unified-tx/), all script operations are available in the `tx` block. Script
inputs go in the `inputs` section, validator attachments in the `scripts` section:

```yaml
transaction:
  - tx:
      from: addr_test1_sender
      change_address: addr_test1_change
      inputs:
        - type: script_collect_from
          utxo_refs:
            - tx_hash: <64-character transaction hash>
              output_index: 0
          redeemer:
            constructor: 0
            fields: []
      intents:
        - type: payment
          address: addr_test1_receiver
          amounts:
            - unit: lovelace
              quantity: '10000000'
      scripts:
        - type: validator
          role: spend
          cbor_hex: "590a01..."
          version: v2
```

### Change Address with Datum

For script transactions that need a datum on the change output:

```yaml
transaction:
  - tx:
      change_address: addr_test1_script
      change_datum: "590a01a1581c..."
      # or
      change_datum_hash: "abc123def..."
```

## Dynamic Configuration

Load a base plan and customize it programmatically for different environments:

> **Important:** Variables are resolved during `TxPlan.from()`. Calling `addVariable()` after parsing only updates
> the variable map for `toYaml()` serialization — it does **not** re-resolve already-parsed transaction fields.
> Set variable values in the YAML string before calling `from()`.

```java
// Replace variables in YAML before parsing
String customYaml = yamlContent
        .replace("${fee_payer}", environmentConfig.getFeePayer())
        .replace("${amount}", environmentConfig.getAmount());

TxPlan plan = TxPlan.from(customYaml)
        .validTo(getCurrentSlot() + 3600);

// Execute
Result<String> result = builder.compose(plan)
        .withSigner(signerProvider)
        .completeAndWait();
```

## TxPlan with TxFlow

TxPlan can be used as the transaction definition within [TxFlow](/txflow/overview/) steps, enabling
YAML-defined transactions in multi-step orchestrated flows:

Under `FlowEngine`, each plan must hold exactly one `tx`, have no `variables:` block, and declare its signers in
`context.signers`. Use flow parameters and `FlowBindings` for per-run values.

```java
TxPlan depositPlan = TxPlan.from(depositYaml);

FlowStep depositStep = FlowStep.builder("deposit")
        .withTxPlan(depositPlan)
        .build();

TxFlow flow = TxFlow.builder("escrow")
        .addStep(depositStep)
        .addStep(FlowStep.builder("release")
                .fundsFrom("deposit")   // or .needs("deposit") for ordering only
                .withTxPlan(TxPlan.from(releaseYaml))
                .build())
        .build();
```

## TxPlan API Reference

### Construction

| Method | Description |
|--------|-------------|
| `TxPlan.from(String yaml)` | Deserialize from YAML string |
| `TxPlan.from(AbstractTx<?> tx)` | Create from a single transaction |
| `TxPlan.from(List<AbstractTx<?>> txs)` | Create from multiple transactions |
| `TxPlan.toYaml(AbstractTx<?> tx)` | Convenience: serialize a transaction to YAML |

### Configuration (fluent builder)

| Method | Description |
|--------|-------------|
| `addVariable(key, value)` | Add an entry to the `variables:` block written by `toYaml()`. Doesn't resolve or rewrite transaction values |
| `setVariables(map)` | Replace that block |
| `additionalSignersCount(count)` | Set `additional_signers_count`, available from 0.8.0-pre7. Throws `IllegalArgumentException` outside 0-1000 |
| `addTransaction(tx)` | Add a transaction to the plan |
| `feePayer(address)` | Set fee payer address |
| `feePayerRef(ref)` | Set fee payer registry reference |
| `collateralPayer(address)` | Set collateral payer address |
| `collateralPayerRef(ref)` | Set collateral payer registry reference |
| `withRequiredSigners(hex...)` | Add required signer credentials |
| `withSigner(ref)` | Add a payment-scope signer reference |
| `withSigner(ref, scope)` | Add a signer reference with specific scope |
| `validFrom(slot)` | Set validity start slot |
| `validTo(slot)` | Set validity end slot |
| `depositPayer(address)` | Set deposit payer address |
| `depositMode(mode)` | Set deposit mode (`AUTO`, `CHANGE_OUTPUT`, etc.) |
| `toYaml()` | Serialize to YAML string |
