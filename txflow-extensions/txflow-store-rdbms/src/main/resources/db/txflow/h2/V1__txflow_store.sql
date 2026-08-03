CREATE TABLE IF NOT EXISTS txflow_schema_history (
    version_no INTEGER PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    installed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS txflow_execution (
    execution_id VARCHAR(512) PRIMARY KEY,
    definition_fingerprint VARCHAR(512) NOT NULL,
    request_fingerprint VARCHAR(512) NOT NULL,
    execution_state VARCHAR(64) NOT NULL,
    revision_no BIGINT NOT NULL,
    last_sequence BIGINT NOT NULL,
    compacted_through BIGINT NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    data_format VARCHAR(64) NOT NULL,
    data_version INTEGER NOT NULL,
    data_payload CLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS txflow_idempotency (
    namespace_id VARCHAR(255) NOT NULL,
    claim_key VARCHAR(512) NOT NULL,
    execution_id VARCHAR(512) NOT NULL,
    PRIMARY KEY (namespace_id, claim_key),
    CONSTRAINT fk_txflow_claim_execution FOREIGN KEY (execution_id)
        REFERENCES txflow_execution (execution_id)
);

CREATE TABLE IF NOT EXISTS txflow_event (
    execution_id VARCHAR(512) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    step_id VARCHAR(512),
    transaction_hash VARCHAR(256),
    details_format VARCHAR(64) NOT NULL,
    details_version INTEGER NOT NULL,
    details_payload CLOB NOT NULL,
    PRIMARY KEY (execution_id, sequence_no),
    CONSTRAINT fk_txflow_event_execution FOREIGN KEY (execution_id)
        REFERENCES txflow_execution (execution_id)
);

CREATE TABLE IF NOT EXISTS txflow_execution_lease (
    execution_id VARCHAR(512) PRIMARY KEY,
    owner_token VARCHAR(512) NOT NULL,
    fence_epoch BIGINT NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_txflow_lease_execution FOREIGN KEY (execution_id)
        REFERENCES txflow_execution (execution_id)
);

CREATE TABLE IF NOT EXISTS txflow_resource_lease (
    resource_id VARCHAR(1024) PRIMARY KEY,
    execution_id VARCHAR(512) NOT NULL,
    owner_token VARCHAR(512) NOT NULL,
    fence_epoch BIGINT NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_txflow_resource_execution FOREIGN KEY (execution_id)
        REFERENCES txflow_execution (execution_id)
);

CREATE TABLE IF NOT EXISTS txflow_lease_epoch (
    singleton_id INTEGER PRIMARY KEY,
    last_epoch BIGINT NOT NULL,
    CONSTRAINT ck_txflow_lease_epoch_singleton CHECK (singleton_id = 1)
);

CREATE INDEX IF NOT EXISTS idx_txflow_execution_recovery
    ON txflow_execution (execution_state, updated_at);
CREATE INDEX IF NOT EXISTS idx_txflow_idempotency_execution
    ON txflow_idempotency (execution_id);
CREATE INDEX IF NOT EXISTS idx_txflow_resource_execution
    ON txflow_resource_lease (execution_id);
