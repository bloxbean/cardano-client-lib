-- MPT Base Schema for PostgreSQL
-- Compatible with PostgreSQL 12+

-- MPT Nodes Table
-- Stores MPT nodes (leaf, extension, branch)
-- Primary key: (namespace, node_hash)
CREATE TABLE IF NOT EXISTS mpt_nodes (
    namespace       SMALLINT NOT NULL,
    node_hash       BYTEA NOT NULL,
    node_data       BYTEA NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, node_hash)
);

CREATE INDEX IF NOT EXISTS idx_mpt_nodes_ns ON mpt_nodes(namespace);


-- MPT Roots Table (Optional - for versioning/snapshots)
-- Stores root hashes for versioning/snapshots
CREATE TABLE IF NOT EXISTS mpt_roots (
    namespace       SMALLINT NOT NULL,
    version         BIGINT NOT NULL,
    root_hash       BYTEA NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, version)
);

CREATE INDEX IF NOT EXISTS idx_mpt_roots_ns ON mpt_roots(namespace);


-- MPT Latest Root Metadata (Optional)
CREATE TABLE IF NOT EXISTS mpt_latest (
    namespace       SMALLINT NOT NULL PRIMARY KEY,
    latest_version  BIGINT NOT NULL,
    latest_root     BYTEA NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
