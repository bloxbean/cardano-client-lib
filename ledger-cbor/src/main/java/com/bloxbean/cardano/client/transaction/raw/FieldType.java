package com.bloxbean.cardano.client.transaction.raw;

/**
 * The CDDL type of a field in a ledger record table, for a {@link FormatPolicy} to check a value without knowing where
 * it is. A record that has a table of its own is walked as well ({@link FieldSpec#record()}); every other type is a
 * leaf of the walk.
 */
public enum FieldType {
    UINT,
    COIN,
    POSITIVE_COIN,
    NETWORK_ID,
    HASH28,
    HASH32,
    BYTES,
    REWARD_ACCOUNT,
    URL,
    DNS_NAME,
    UNIT_INTERVAL,
    /** The types that may be null. */
    OPTIONAL_PORT(Shape.NULLABLE),
    OPTIONAL_IPV4(Shape.NULLABLE),
    OPTIONAL_IPV6(Shape.NULLABLE),
    OPTIONAL_ANCHOR(Shape.NULLABLE),
    OPTIONAL_POOL_METADATA(Shape.NULLABLE),
    /** A member of a tagged union, as encoded. */
    MEMBER,
    CREDENTIAL,
    DREP,
    MIR,
    /** The second item of a move_instantaneous_reward: a map of stake credentials to delta coins, or a coin. */
    MIR_TARGET,
    TRANSACTION_OUTPUT,
    UPDATE,
    REDEEMERS,
    MAP,
    NONEMPTY_MAP,
    /** An array, of any length. */
    LIST(Shape.COLLECTION),
    NONEMPTY_LIST(Shape.COLLECTION),
    /** An array, optionally in tag 258. */
    SET(Shape.COLLECTION),
    NONEMPTY_SET(Shape.COLLECTION),
    RELAY_LIST(Shape.COLLECTION),
    CERTIFICATE_LIST(Shape.COLLECTION),
    NONEMPTY_CERTIFICATE_SET(Shape.COLLECTION);

    private enum Shape {
        PLAIN, NULLABLE, COLLECTION
    }

    private final Shape shape;

    FieldType() {
        this(Shape.PLAIN);
    }

    FieldType(Shape shape) {
        this.shape = shape;
    }

    boolean nullable() {
        return shape == Shape.NULLABLE;
    }

    boolean collection() {
        return shape == Shape.COLLECTION;
    }

    /**
     * @return the table of a value of this type, or of each item of a collection; null for a leaf
     */
    RecordSpec record() {
        switch (this) {
            case OPTIONAL_ANCHOR:
                return RawAnchor.RECORD;
            case OPTIONAL_POOL_METADATA:
                return RawPoolMetadata.RECORD;
            case CREDENTIAL:
                return RawCredential.RECORD;
            case DREP:
                return RawDRep.RECORD;
            case MIR:
                return RawMir.RECORD;
            case RELAY_LIST:
                return RawRelay.RECORD;
            case CERTIFICATE_LIST:
            case NONEMPTY_CERTIFICATE_SET:
                return CertKind.RECORD;
            default:
                return null;
        }
    }
}
