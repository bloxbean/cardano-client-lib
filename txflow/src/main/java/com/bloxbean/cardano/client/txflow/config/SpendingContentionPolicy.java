package com.bloxbean.cardano.client.txflow.config;

/**
 * Controls concurrent execution requests whose canonical spending-resource
 * identities overlap.
 *
 * <p>The identity comes from {@code ResourceDescriptor.spendingIdentity}, not
 * from the resource alias written in a flow, so different aliases for the same
 * account can be made to contend.</p>
 */
public enum SpendingContentionPolicy {
    /** Wait up to the configured queue limit and run overlapping executions one at a time. */
    SERIALIZE,
    /** Reject an execution immediately when an overlapping resource is already held. */
    REJECT,
    /** Do not coordinate overlapping spending resources. */
    ALLOW
}
