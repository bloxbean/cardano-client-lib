package com.bloxbean.cardano.vds.jmt.store;

/**
 * Thread-owned access lease for a logical JMT namespace.
 */
public interface JmtAccessLease extends AutoCloseable {

    JmtAccessMode mode();

    String operation();

    @Override
    void close();
}
