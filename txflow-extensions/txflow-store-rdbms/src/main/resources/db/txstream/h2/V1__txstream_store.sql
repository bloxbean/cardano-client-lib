CREATE TABLE IF NOT EXISTS txstream_schema_history (
    version_no INTEGER PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    installed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS txstream_item (
    item_id VARCHAR(512) PRIMARY KEY,
    stream_id VARCHAR(255),
    idempotency_key VARCHAR(512),
    lane_name VARCHAR(512),
    fingerprint VARCHAR(512),
    accepted_at TIMESTAMP(6) WITH TIME ZONE,
    status VARCHAR(64),
    execution_id VARCHAR(512),
    step_id VARCHAR(512),
    projection_lane_name VARCHAR(512),
    transaction_hash VARCHAR(512),
    error_code VARCHAR(255),
    error_message VARCHAR(4096),
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    projection_sequence BIGINT,
    terminal BOOLEAN
);

CREATE TABLE IF NOT EXISTS txstream_binding (
    item_id VARCHAR(512) PRIMARY KEY,
    execution_id VARCHAR(512) NOT NULL,
    flow_id VARCHAR(512) NOT NULL,
    step_id VARCHAR(512) NOT NULL,
    lane_name VARCHAR(512) NOT NULL,
    outcome VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS txstream_planned (
    execution_id VARCHAR(512) PRIMARY KEY,
    stream_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL,
    lane_name VARCHAR(512) NOT NULL,
    canonical_spending_identity VARCHAR(1024) NOT NULL,
    portable_flow CLOB NOT NULL,
    metadata_payload CLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS txstream_batch (
    stream_id VARCHAR(255) NOT NULL,
    batch_id VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    item_ids CLOB NOT NULL,
    execution_ids CLOB NOT NULL,
    failure_code VARCHAR(255),
    failure_message VARCHAR(4096),
    PRIMARY KEY (stream_id, batch_id)
);

CREATE TABLE IF NOT EXISTS txstream_bootstrap (
    stream_id VARCHAR(255) PRIMARY KEY,
    fingerprint VARCHAR(1024) NOT NULL
);

CREATE TABLE IF NOT EXISTS txstream_ownership (
    stream_id VARCHAR(255) PRIMARY KEY,
    owner_token VARCHAR(512),
    epoch BIGINT NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_txstream_item_nonterminal
    ON txstream_item (stream_id, terminal);
CREATE INDEX IF NOT EXISTS idx_txstream_planned_stream
    ON txstream_planned (stream_id);
