-- MPT Base Schema for SQLite
-- Compatible with SQLite 3.35+

-- MPT Nodes Table
-- Stores MPT nodes (leaf, extension, branch)
-- Primary key: (namespace, node_hash)
CREATE TABLE IF NOT EXISTS mpt_nodes (
    namespace       INTEGER NOT NULL,
    node_hash       BLOB NOT NULL,
    node_data       BLOB NOT NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, node_hash)
);

CREATE INDEX IF NOT EXISTS idx_mpt_nodes_ns ON mpt_nodes(namespace);


-- MPT Roots Table (Optional - for versioning/snapshots)
-- Stores root hashes for versioning/snapshots
CREATE TABLE IF NOT EXISTS mpt_roots (
    namespace       INTEGER NOT NULL,
    version         INTEGER NOT NULL,
    root_hash       BLOB NOT NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (namespace, version)
);

CREATE INDEX IF NOT EXISTS idx_mpt_roots_ns ON mpt_roots(namespace);


-- MPT Latest Root Metadata (Optional)
CREATE TABLE IF NOT EXISTS mpt_latest (
    namespace       INTEGER NOT NULL PRIMARY KEY,
    latest_version  INTEGER NOT NULL,
    latest_root     BLOB NOT NULL,
    updated_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
