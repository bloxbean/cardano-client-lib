package com.bloxbean.cardano.vds.rdbms.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H2DialectTest {

    @Test
    void usesExplicitKeysRatherThanInferringFromPrefixedTableName() {
        String sql = new H2Dialect().insertOrIgnoreSql(
                "values_a_jmt_stale",
                "namespace, stale_since, node_path, node_version",
                "?, ?, ?, ?",
                "namespace, stale_since, node_path, node_version");

        assertEquals("MERGE INTO values_a_jmt_stale "
                + "(namespace, stale_since, node_path, node_version) "
                + "KEY(namespace, stale_since, node_path, node_version) VALUES (?, ?, ?, ?)", sql);
    }
}
