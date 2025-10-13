-- JMT Base Schema for PostgreSQL
-- Compatible with PostgreSQL 12+

-- JMT Nodes Table
-- Stores JMT internal and leaf nodes
-- Primary key: (namespace, node_path, version)
CREATE TABLE IF NOT EXISTS jmt_nodes (
    namespace       SMALLINT NOT NULL,
    node_path       BYTEA NOT NULL,
    version         BIGINT NOT NULL,
    node_data       BYTEA NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, node_path, version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_nodes_ns_path ON jmt_nodes(namespace, node_path);
CREATE INDEX IF NOT EXISTS idx_jmt_nodes_ns_ver ON jmt_nodes(namespace, version);


-- JMT Values Table
-- Stores key-value pairs with versioning
-- Primary key: (namespace, key_hash, version)
CREATE TABLE IF NOT EXISTS jmt_values (
    namespace       SMALLINT NOT NULL,
    key_hash        BYTEA NOT NULL,
    version         BIGINT NOT NULL,
    value_data      BYTEA,
    is_tombstone    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, key_hash, version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_values_ns_key ON jmt_values(namespace, key_hash);
CREATE INDEX IF NOT EXISTS idx_jmt_values_ns_ver ON jmt_values(namespace, version);


-- JMT Roots Table
-- Stores root hashes for each version
CREATE TABLE IF NOT EXISTS jmt_roots (
    namespace       SMALLINT NOT NULL,
    version         BIGINT NOT NULL,
    root_hash       BYTEA NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_roots_ns ON jmt_roots(namespace);


-- JMT Latest Root Metadata
-- Stores latest version/root for each namespace
CREATE TABLE IF NOT EXISTS jmt_latest (
    namespace       SMALLINT NOT NULL PRIMARY KEY,
    latest_version  BIGINT NOT NULL,
    latest_root     BYTEA NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- JMT Stale Nodes Table
-- Tracks nodes marked for deletion (for pruning)
CREATE TABLE IF NOT EXISTS jmt_stale (
    namespace       SMALLINT NOT NULL,
    stale_since     BIGINT NOT NULL,
    node_path       BYTEA NOT NULL,
    node_version    BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, stale_since, node_path, node_version)
);

CREATE INDEX IF NOT EXISTS idx_jmt_stale_ns_since ON jmt_stale(namespace, stale_since);
