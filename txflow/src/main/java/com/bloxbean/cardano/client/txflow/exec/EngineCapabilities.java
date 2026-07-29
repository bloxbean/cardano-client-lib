package com.bloxbean.cardano.client.txflow.exec;

/**
 * Immutable, read-only description of what a {@link FlowEngine} instance guarantees, so callers can
 * gate optional behavior on it at construction time instead of discovering a missing guarantee at
 * runtime.
 *
 * <p>Obtained from {@link FlowEngine#capabilities()}. It reflects the engine's fixed configuration
 * and never changes for a given engine.</p>
 *
 * @param durableExecution whether executions are durably persisted — {@code true} exactly when a
 *        {@link com.bloxbean.cardano.client.txflow.store.FlowExecutionStore} is configured. Callers
 *        whose crash-recovery reasoning depends on "no stored execution ⇒ it never ran" (such as a
 *        durable-mode stream builder invariant) must require this to be {@code true}
 */
public record EngineCapabilities(boolean durableExecution) {
}
