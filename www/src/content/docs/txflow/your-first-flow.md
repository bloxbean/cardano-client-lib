---
title: "Your first flow"
description: "Chain two transactions where the second spends what the first produced — the smallest TxFlow that does something you cannot do with QuickTx alone."
---

:::tip[Preview API]
TxFlow is preview. It works and is documented in full, but `txflow.cardano-client.dev/v1alpha1`
can still change before a stable schema version. Pin an exact version if you build on it now.
:::

This page builds the smallest flow that is genuinely worth using TxFlow for: **two transactions,
where the second spends an output the first created.**

If you only need one transaction, stop here and use [QuickTx](/quicktx/overview/) — it is simpler
and this page will not make it better. Come back when you hit the problem below.

## The problem TxFlow solves

Try chaining two transactions by hand and you meet this immediately:

```java
// Transaction 1 — pays 5 ADA to a staging address. complete() returns once submitted.
TxResult first = quickTxBuilder.compose(fundTx)
        .withSigner(SignerProviders.signerFrom(sender))
        .complete();

// Transaction 2 — wants to spend the output transaction 1 just created.
// But the backend does not know about that UTXO yet, so selection can't find it.
```

`completeAndWait()` narrows this window but doesn't remove it: its default wait is 60 seconds, and a
public indexer's UTxO view can lag behind a confirmed transaction.

The output exists in a submitted transaction, but until the node has indexed it, a normal UTXO
query cannot see it. Working around that by hand means polling, retrying, and reasoning about
rollbacks — and getting it subtly wrong.

TxFlow handles it: you declare that step two consumes step one's output, and the engine
substitutes the concrete transaction hash and output index at run time.

## 1. Declare the flow

TxFlow definitions are YAML. Save this as `funding-flow.yaml`:

```yaml
api_version: txflow.cardano-client.dev/v1alpha1
kind: TxFlow
metadata:
  name: funding
  version: 1.0.0
spec:
  network: preview
  parameters:
    staging:
      type: address
      required: true
    beneficiary:
      type: address
      required: true
  execution:
    confirmation:
      min_confirmations: 1
      check_interval: 2s
      timeout: 2m
  steps:
    # Step 1 — pay into a staging address, and publish that output as "held".
    - id: fund
      outputs:
        held:
          select:
            output_index: 0
          expect: exactly_one
      transaction:
        tx:
          from_ref: account://sender
          intents:
            - type: payment
              address: '${{ inputs.staging }}'
              amounts:
                - unit: lovelace
                  quantity: 5000000
        context:
          signers:
            - ref: account://sender
              scope: payment

    # Step 2 — spend exactly that output.
    - id: forward
      needs: [fund]
      transaction:
        tx:
          from_ref: account://staging
          inputs:
            - type: collect_from
              refs:
                - flow_output:
                    step: fund
                    output: held
          intents:
            - type: payment
              address: '${{ inputs.beneficiary }}'
              amounts:
                - unit: lovelace
                  quantity: 3000000
        context:
          fee_payer_ref: account://staging
          signers:
            - ref: account://staging
              scope: payment
```

Three things carry the whole idea:

- **`outputs.held`** publishes step one's output 0 under a flow-local name. `output_index` is
  zero-based, and `expect: exactly_one` means execution fails loudly if that output is not there.
- **`flow_output`** in step two's `inputs` is the consumption edge. At run time TxFlow replaces it
  with the real transaction hash and index from *this* execution.
- **`needs: [fund]`** declares the dependency. On its own it records and validates the ordering —
  it does **not** spend anything. The `flow_output` reference is what actually moves the UTXO.

The step that spends a `flow_output` must declare a sender with `from_ref` and carry a signer for
that output's payment key, here the staging account. `from_ref` supplies only the address, not a
signature. Preflight doesn't build transactions, so a mistake here surfaces only when the step runs,
after earlier steps have moved funds.

Note what the file does *not* contain: no addresses baked in, no keys, no network endpoint.
`account://sender` is a reference resolved by your application, and `${{ inputs.* }}` are bound at
run time. The definition is reusable across environments.

## 2. Run it

`FlowRuntime` is the managed entry point — it owns the executors, wires the four backend
suppliers, and holds the signer registry, so a first flow does not start with a page of setup:

```java
import com.bloxbean.cardano.client.txflow.FlowRuntime;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowParseResult;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionHandle;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;

String source = Files.readString(Path.of("funding-flow.yaml"), StandardCharsets.UTF_8);

FlowParseResult parsed = TxFlowCodec.standard()
        .parse(source, FlowParseOptions.serverDefaults());
if (parsed.hasErrors()) {
    throw new IllegalArgumentException("Invalid flow: " + parsed.getDiagnostics());
}
TxFlow definition = parsed.requireFlow();

FlowBindings bindings = FlowBindings.builder()
        .put("staging", stagingAccount.baseAddress())
        .put("beneficiary", receiverAddress)
        .build();

try (FlowRuntime runtime = FlowRuntime.builder(backendService)
        .account("account://sender", senderAccount)
        .account("account://staging", stagingAccount)
        .build()) {

    FlowExecutionRequest request = FlowExecutionRequest.builder(definition)
            .executionId("funding-0001")
            .bindings(bindings)
            .build();

    FlowExecutionHandle handle = runtime.engine().start(request);
    FlowExecutionResult result = handle.await();

    // await() doesn't throw on a failed flow, so check the state and the error
    System.out.println(result.state() + " error=" + result.error());
    result.steps().forEach(step ->
            System.out.println(step.getStepId() + " " + step.getTransactionHash()));
}
```

`FlowRuntime` implements `AutoCloseable`, so try-with-resources shuts its executors down for you.
It creates an ordinary `FlowEngine`, and `runtime.engine()` returns it.

`account(...)` registers the signing key against the `account://sender` reference. Note what is
*not* here: no `FlowResourceCatalog` and no `FlowExecutionPolicy`. Both are optional — with no
catalog the compiler skips resource resolution entirely — which is fine for a script you control.
A server accepting flow documents from elsewhere should add both, so the network and the resource
prefixes a document may touch are constrained. [Getting started](/txflow/getting-started/) shows
that setup.

`FlowRuntime` doesn't accept a catalog, policy, store or script registry; use
`FlowEngine.builder(...)` for those. Without a catalog there's no spending identity, so concurrent
flows from one account aren't serialized, and `spec.network` isn't enforced: a `preview` document
runs against whatever backend you pass.

## 3. Check before you run

`preflight` compiles and policy-checks every step without submitting anything:

```java
FlowCompilationResult preflight = runtime.engine().preflight(request);
if (preflight.hasErrors()) {
    throw new IllegalArgumentException("Preflight failed: " + preflight.getDiagnostics());
}
```

It catches an unbound parameter, a `flow_output` naming a step that does not publish it, and a
step ordered after its consumer — all before a transaction reaches the chain. Run it whenever a
flow comes from outside your codebase.

`start(...)` deliberately compiles again rather than trusting an earlier preflight result, so a
definition that changed in between cannot slip through.

## What you get beyond the chaining

The same execution already gives you:

- **Confirmation tracking** — `min_confirmations` is honoured per step; in the default `SEQUENTIAL`
  mode the flow doesn't advance on a merely submitted transaction.
- **Honest rollback handling** — while a step waits for confirmation, a transaction that disappears
  isn't treated as success or failure: the flow ends `RECOVERY_REQUIRED`. To detect and act on
  rollbacks, add `spec.execution.rollback` and a chain adapter that declares authoritative absence;
  see [Policies](/txflow/policies-results-observability/#submission-and-confirmation-are-separate). `FlowRuntime` can't supply
  such an adapter.
- **Idempotency** — add `.idempotency("payments", "order-18473")` to the request and a repeat call
  to this engine attaches to the existing execution instead of paying twice. On `FlowRuntime` that
  claim lives in memory, so it's lost on restart. Surviving a restart needs a
  [durable store](/txflow/durable-runtime/).
- **Honest uncertainty** — an ambiguous submission is reported as uncertain, never as a false
  success or a false failure.

## Where to go next

| You want | Page |
|---|---|
| The full setup with explicit resources and policy | [Getting started](/txflow/getting-started/) |
| Every document field and output-reference form | [Portable authoring](/txflow/portable-authoring/) |
| Engine construction, executors, cancellation | [Execution engine](/txflow/execution-engine/) |
| Retries, events, confirmation and rollback behaviour | [Policies, results & observability](/txflow/policies-results-observability/) |
| Surviving a process restart | [Durable runtime](/txflow/durable-runtime/) |
| Many transactions over time, not one workflow | [TxStream](/txstream/getting-started/) |

:::note[TxFlow or TxStream?]
TxFlow runs **one workflow of related steps** to completion. TxStream submits **a continuous feed
of independent items**, each as its own execution on the same engine. A payroll run of 500
unrelated payments is TxStream; a deposit-then-withdraw pair is TxFlow.
:::
