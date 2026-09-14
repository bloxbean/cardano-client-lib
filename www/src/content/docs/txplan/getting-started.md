---
title: "TxPlan: Getting Started"
description: "Move a transaction out of Java and into a YAML file you can version, review and reuse — in about ten minutes."
---

:::tip[Preview API]
TxPlan ships inside the `quicktx` module — no extra dependency. It is stable enough to build on,
but its YAML schema may still change before 0.8.0 final.
:::

A `Tx` written in Java hardcodes the decisions. TxPlan moves them into a YAML document your
application loads at run time — so a payout schedule, a treasury operation, or a customer-specific
transfer becomes configuration you can review in a pull request rather than code you have to
redeploy.

**You need**: Java 17+, `cardano-client-lib`, a backend, and a funded test account. If you have not
built a transaction with this library yet, do [Your first transaction](/learn/simple-transfer/)
first — this page assumes you know what `Tx`, `from` and a signer are.

## The fastest way in: build it, then print it

Rather than learning the schema from a reference table, build the transaction you already know how
to build and let TxPlan show you its YAML:

```java
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

Tx tx = new Tx()
        .payToAddress(receiverAddress, Amount.ada(10))
        .from(sender.baseAddress());

String yaml = TxPlan.from(tx)
        .feePayer(sender.baseAddress())
        .addVariable("sender", sender.baseAddress())
        .addVariable("receiver", receiverAddress)
        .toYaml();

System.out.println(yaml);
```

`addVariable` here is an **authoring** call: it populates the `variables:` block that `toYaml()`
emits, so the document you get back is already parameterised rather than full of literal
addresses.

## The plan file

The output has this shape — save it as `payment.yaml`:

```yaml
version: 1.0
variables:
  sender: addr_test1...
  receiver: addr_test1...
  amount: 10000000
context:
  fee_payer: ${sender}
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

Four parts, and it is worth knowing which is which:

- **`variables`** — the values every `${...}` placeholder resolves against.
- **`context`** — settings that apply to the whole plan and map onto `QuickTxBuilder`:
  `fee_payer`, `collateral_payer`, `valid_from_slot`, `valid_to_slot`, `required_signers`,
  `deposit_payer`, `deposit_mode`. Note these keys are `snake_case`.
- **`transaction`** — a list of `- tx:` entries. Singular key, plural contents: a plan can carry
  several transactions submitted together.
- **`intents`** — what each transaction actually does. `type: payment` here; minting, delegation,
  governance and script intents have their own types.

## Load and execute

```java
import java.nio.file.Files;
import java.nio.file.Path;

TxPlan plan = TxPlan.from(Files.readString(Path.of("payment.yaml")));

TxResult result = new QuickTxBuilder(backendService)
        .compose(plan)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();

System.out.println(result.getTxStatus() + " " + result.getTxHash());
```

`compose(TxPlan)` maps the plan's `context` block onto the builder for you — `fee_payer` becomes
the fee payer, `valid_to_slot` becomes the validity bound. You do not repeat them in Java.

## Where runtime values come from

This is the one thing worth getting right early.

**Variable expansion happens when the YAML is parsed**, against the document's own `variables:`
block. `TxPlan.from(yaml)` returns a plan whose transactions are already fully materialised — so
calling `addVariable(...)` on that returned plan does **not** change them. `addVariable` populates
a plan you are serialising *out*; it does not inject into a plan you have read *in*.

To vary values per run, produce the `variables:` block before parsing:

```java
String template = Files.readString(Path.of("payment.yaml"));

// Supply this run's values, then parse.
String yaml = template
        .replace("SENDER_PLACEHOLDER", sender.baseAddress())
        .replace("RECEIVER_PLACEHOLDER", receiverAddress)
        .replace("AMOUNT_PLACEHOLDER", String.valueOf(adaToLovelace(10)));

TxPlan plan = TxPlan.from(yaml);
```

Any templating approach works — a text substitution as above, a YAML library that writes the
`variables:` map, or generating the whole document. What matters is that the values are in the
document by the time `TxPlan.from(String)` sees it.

For addresses and signing keys that are fixed per environment rather than per run, the
**signer registry** is cleaner than variables: reference them as `account://ops` and resolve them
at compose time with `compose(plan, signerRegistry)`. See
[TxPlan overview](/txplan/overview/#context-properties).

## When TxPlan is the right tool

| Situation | TxPlan? |
|---|---|
| One transaction shape, values differ per call | **Yes** — one plan file, different variables |
| Operators need to review transactions before they run | **Yes** — the plan is reviewable text |
| An agent or external system decides what to submit | **Yes** — generating config is safer than generating code |
| Transaction shape is fixed and known at compile time | No — plain [QuickTx](/quicktx/overview/) is simpler |
| Several transactions that depend on each other's outputs | No — that is [TxFlow](/txflow/overview/) |
| A continuous stream of transactions over time | No — that is [TxStream](/txstream/getting-started/) |

TxPlan describes **what** to submit. It does not orchestrate ordering, dependencies or retries —
that is TxFlow's job, and TxFlow can execute plans as its steps.

## Next

- [TxPlan overview](/txplan/overview/) — full YAML schema, context properties, signer registry
- [Advanced usage](/txplan/advanced-usage/) — script transactions, metadata, multi-transaction plans
- [TxFlow](/txflow/overview/) — when the steps depend on each other
