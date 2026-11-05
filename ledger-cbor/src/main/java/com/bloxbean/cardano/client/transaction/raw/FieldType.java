package com.bloxbean.cardano.client.transaction.raw;

/**
 * The CDDL type of a field in a ledger record table, for a {@link FormatPolicy} to check a value without knowing where
 * it is. A record that has a table of its own is walked as well ({@link FieldSpec#record()}); every other type is a
 * leaf of the walk.
 */
public enum FieldType {
    UINT,
    /** {@code uint .size 2}. */
    UINT16,
    /** {@code uint .size 4}. */
    UINT32,
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
    OPTIONAL_HASH32(Shape.NULLABLE),
    /** A member of a tagged union, as encoded. */
    MEMBER,
    CREDENTIAL,
    DREP,
    MIR,
    /** The second item of a move_instantaneous_reward: a map of stake credentials to delta coins, or a coin. */
    MIR_TARGET,
    VKEY,
    SIGNATURE,
    VRF_VKEY,
    VRF_CERT,
    /** {@code bytes .size 80}. */
    VRF_PROOF,
    KES_VKEY,
    KES_SIGNATURE,
    SEQUENCE_NUMBER,
    KES_PERIOD,
    MAJOR_PROTOCOL_VERSION,
    MINOR_PROTOCOL_VERSION,
    /** The items of a header body, whose table depends on the header's layout. */
    HEADER_BODY,
    OPERATIONAL_CERT,
    PROTOCOL_VERSION,
    TRANSACTION_OUTPUT,
    ADDRESS,
    VALUE,
    DATUM_OPTION,
    /** Plutus data in tag 24, as encoded. */
    DATA,
    SCRIPT_REF,
    ASSET_NAME,
    INT64,
    NONZERO_INT64,
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
    /** A set of key hashes, for a policy to tell what its items are. */
    KEY_HASH_SET(Shape.COLLECTION),
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
    public RecordSpec record() {
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
            case VRF_CERT:
                return RawVrfCert.RECORD;
            case OPERATIONAL_CERT:
                return RawHeader.OPERATIONAL_CERT;
            case PROTOCOL_VERSION:
                return RawHeader.PROTOCOL_VERSION;
            case CERTIFICATE_LIST:
            case NONEMPTY_CERTIFICATE_SET:
                return CertKind.RECORD;
            case TRANSACTION_OUTPUT:
                return RawOutput.RECORD;
            case VALUE:
                return RawValue.RECORD;
            case DATUM_OPTION:
                return RawDatum.OPTION;
            default:
                return null;
        }
    }
}
