/**
 * Streaming transaction workflows on the {@code FlowEngine} durable runtime.
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
 * own step and projecting from its own step's outcome. An item may instead be
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
 * {@code engine.recover(...)} without anyone polling.
 */
package com.bloxbean.cardano.client.txflow.stream;
