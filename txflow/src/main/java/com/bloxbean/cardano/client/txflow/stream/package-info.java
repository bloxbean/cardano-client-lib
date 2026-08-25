/**
 * Streaming transaction workflows on the {@code FlowEngine} durable runtime.
 * <p>
 * The managed beginner path uses
 * {@link com.bloxbean.cardano.client.txflow.FlowRuntime} to own one ordinary
 * engine, its executors, and opened streams. The default lane is derived from
 * each plan's funding source, and the common submission overload uses the item
 * id as its idempotency key:
 * <pre>{@code
 * try (FlowRuntime runtime = FlowRuntime.builder(backend)
 *         .account("account://sender", sender)
 *         .build();
 *      TxFlowStream stream = runtime.open("payouts")) {
 *     TxPlan plan = TxPlan.from(new Tx()
 *                     .payToAddress(receiver, Amount.ada(2))
 *                     .fromRef("account://sender"))
 *             .withSigner("account://sender");
 *     TxStreamItemResult result = stream.submit("order-0042", plan)
 *             .awaitConfirmed(Duration.ofMinutes(5));
 * }
 * }</pre>
 * Direct engine and stream builders remain the advanced/server path and never
 * take ownership of caller-supplied executors. See the module's
 * {@code TXSTREAM_GETTING_STARTED.md} for typed uncertainty recovery and the
 * effective defaults.
 * <p>
 * A {@link com.bloxbean.cardano.client.txflow.stream.TxFlowStream} accepts
 * portable {@link com.bloxbean.cardano.client.txflow.stream.TxWorkItem}s and
 * plans them into idempotent engine executions on lanes — a lane is a funding
 * scope ({@link com.bloxbean.cardano.client.txflow.stream.ResolvedLane})
 * with at most one in-flight execution at a time. The default
 * {@link com.bloxbean.cardano.client.txflow.stream.TxStreamPlanner#perItem()}
 * planner executes each item as its own single-step flow; a
 * {@link com.bloxbean.cardano.client.txflow.stream.WindowPolicy} combined
 * with a multi-item planner (for example
 * {@link com.bloxbean.cardano.client.txflow.stream.TxStreamPlanner#perWindow()})
 * groups a window of items into shared multi-step flows, each item riding its
 * own step and projecting from its own step's outcome. Generated per-window
 * flows can opt into planner-local pipelining through
 * {@link com.bloxbean.cardano.client.txflow.stream.TxStreamPlanner#perWindow(com.bloxbean.cardano.client.txflow.ChainingMode)};
 * this never overrides per-item, batching, custom-planner, or template flow
 * settings. An item may instead be
 * a reference to a pre-registered parameterized template
 * ({@link com.bloxbean.cardano.client.txflow.stream.TxWorkItem.Builder#withTemplate}
 * against a definition registered with
 * {@link com.bloxbean.cardano.client.txflow.stream.TxFlowStream.Builder#template}):
 * the definition is compiled, validated, and fingerprinted once and each item
 * is a parameterized invocation of it with its own bindings, dispatched as a
 * whole-flow single-member execution. Lanes are
 * statically
 * configured ({@link com.bloxbean.cardano.client.txflow.stream.LanePolicy#single}),
 * named per item and resolved dynamically through a
 * {@link com.bloxbean.cardano.client.txflow.stream.LaneIdentityResolver}
 * ({@link com.bloxbean.cardano.client.txflow.stream.LanePolicy#explicit}),
 * derived from each item's transaction funding source
 * ({@link com.bloxbean.cardano.client.txflow.stream.LanePolicy#byFundingSource}),
 * or hash-partitioned across N application-provided lane addresses with an
 * optional one-time fan-out bootstrap
 * ({@link com.bloxbean.cardano.client.txflow.stream.LanePolicy#partitioned}).
 * Scheduling keys on the lane's <em>canonical spending identity</em>, never
 * its label: alias names share one FIFO, different identities run
 * concurrently under a global {@code maxInFlight} cap. Execution identities
 * are derived deterministically from the idempotency claims of the member
 * items (a single item's claim under the per-item planner, the sorted member
 * keys for a shared flow), so redelivery attaches to the existing execution
 * instead of double-spending, and the stream's write-ahead binding always
 * names the execution the engine runs.
 * <p>
 * Item status is an honest projection of engine truth: submission is only
 * reported after the backend observed it, a known transaction hash is never
 * dropped, and a submitted-but-unconfirmed transaction inside a terminal flow
 * is reported as
 * {@link com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus#RECOVERY_REQUIRED}
 * and repaired through reconciliation against the engine snapshot: read-through
 * by default ({@link com.bloxbean.cardano.client.txflow.stream.TxFlowStream#reconcile(String)}
 * / {@link com.bloxbean.cardano.client.txflow.stream.TxFlowStream#getItemStatus(String)}),
 * and — when
 * {@link com.bloxbean.cardano.client.txflow.stream.TxFlowStream.Builder#reconciliationInterval(java.time.Duration)}
 * is opted in — push-repaired by a periodic stream-owned observer that runs on
 * the caller-owned maintenance scheduler, so a durable
 * {@code RECOVERY_REQUIRED} item is repaired after an operator runs
 * {@code engine.recover(...)} without anyone polling. Eager validation and
 * authoritative registration failures are rejected before any receipt or item
 * state is created; blocking submission throws the typed cause and
 * non-blocking submission returns
 * {@link com.bloxbean.cardano.client.txflow.stream.EmitResult.Status#REJECTED}.
 */
package com.bloxbean.cardano.client.txflow.stream;
