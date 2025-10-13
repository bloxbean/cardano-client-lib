-- JMT Base Schema for SQLite
-- Compatible with SQLite 3.35+

-- JMT Nodes Table
-- Stores JMT internal and leaf nodes
-- Primary key: (namespace, node_path, version)
CREATE TABLE IF NOT EXISTS jmt_nodes (
    namespace       INTEGER NOT NULL,
    node_path       BLOB NOT NULL,
    version         INTEGER NOT NULL,
    node_data       BLOB NOT NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, node_path, version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_nodes_ns_path ON jmt_nodes(namespace, node_path);
CREATE INDEX IF NOT EXISTS idx_jmt_nodes_ns_ver ON jmt_nodes(namespace, version);


-- JMT Values Table
-- Stores key-value pairs with versioning
-- Primary key: (namespace, key_hash, version)
CREATE TABLE IF NOT EXISTS jmt_values (
    namespace       INTEGER NOT NULL,
    key_hash        BLOB NOT NULL,
    version         INTEGER NOT NULL,
    value_data      BLOB,
    is_tombstone    INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, key_hash, version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_values_ns_key ON jmt_values(namespace, key_hash);
CREATE INDEX IF NOT EXISTS idx_jmt_values_ns_ver ON jmt_values(namespace, version);


-- JMT Roots Table
-- Stores root hashes for each version
CREATE TABLE IF NOT EXISTS jmt_roots (
    namespace       INTEGER NOT NULL,
    version         INTEGER NOT NULL,
    root_hash       BLOB NOT NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_roots_ns ON jmt_roots(namespace);


-- JMT Latest Root Metadata
-- Stores latest version/root for each namespace
CREATE TABLE IF NOT EXISTS jmt_latest (
    namespace       INTEGER NOT NULL PRIMARY KEY,
    latest_version  INTEGER NOT NULL,
    latest_root     BLOB NOT NULL,
    updated_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- JMT Stale Nodes Table
-- Tracks nodes marked for deletion (for pruning)
CREATE TABLE IF NOT EXISTS jmt_stale (
    namespace       INTEGER NOT NULL,
    stale_since     INTEGER NOT NULL,
    node_path       BLOB NOT NULL,
    node_version    INTEGER NOT NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, stale_since, node_path, node_version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_stale_ns_since ON jmt_stale(namespace, stale_since);
