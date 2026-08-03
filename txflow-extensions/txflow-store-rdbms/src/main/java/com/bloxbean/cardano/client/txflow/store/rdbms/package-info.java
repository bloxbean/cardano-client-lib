/**
 * JDBC implementation of the TxFlow durable execution-store contract.
 *
 * <p>H2 2.x and PostgreSQL 17.x are the current automated certification profiles. The adapter is
 * synchronous and schedules no work; all calls execute on the caller's thread.</p>
 */
package com.bloxbean.cardano.client.txflow.store.rdbms;
