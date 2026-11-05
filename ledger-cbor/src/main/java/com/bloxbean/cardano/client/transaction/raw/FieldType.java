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
    NONNEGATIVE_INTERVAL,
    /** The types that may be null. */
    OPTIONAL_PORT(Shape.NULLABLE),
    OPTIONAL_IPV4(Shape.NULLABLE),
    OPTIONAL_IPV6(Shape.NULLABLE),
    OPTIONAL_ANCHOR(Shape.NULLABLE),
    OPTIONAL_POOL_METADATA(Shape.NULLABLE),
    OPTIONAL_HASH32(Shape.NULLABLE),
    OPTIONAL_AUXILIARY_DATA(Shape.NULLABLE),
    OPTIONAL_GOV_ACTION_ID(Shape.NULLABLE),
    /** A script hash or null, such as a guardrails script hash. */
    OPTIONAL_HASH28(Shape.NULLABLE),
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
    /** The {@code genesis_hash => protocol_param_update} map of an update. */
    PROPOSED_PARAMETER_UPDATES,
    PROTOCOL_PARAM_UPDATE,
    NONCE,
    COST_MODELS,
    /** A key of {@code cost_models}. */
    LANGUAGE,
    /** A value of {@code cost_models}: an array of int64, as one value. */
    COST_MODEL,
    EX_UNIT_PRICES,
    POOL_VOTING_THRESHOLDS,
    DREP_VOTING_THRESHOLDS,
    VOTER,
    GOV_ACTION_ID,
    VOTING_PROCEDURE,
    /** {@code 0 .. 2}. */
    VOTE,
    ANCHOR,
    GOV_ACTION,
    CONSTITUTION,
    /** A map of reward accounts to coins, such as a treasury withdrawal's. */
    WITHDRAWALS,
    /** The {@code committee_cold_credential => epoch} map of a committee update. */
    COMMITTEE_TERMS,
    BOOL,
    TRANSACTION_BODY,
    TRANSACTION_WITNESS_SET,
    REDEEMERS,
    REDEEMER_TAG,
    /** The key of the map form of redeemers. */
    REDEEMER_KEY,
    /** The value of the map form of redeemers. */
    REDEEMER_VALUE,
    EX_UNITS,
    /** An item of {@code ex_units}, {@code 0 .. max_int64}. */
    EX_UNIT,
    /** Recursive, so one value. */
    NATIVE_SCRIPT,
    PLUTUS_SCRIPT,
    /** Recursive, so one value. */
    PLUTUS_DATA,
    /** Recursive, so one value. */
    METADATA,
    /** The array or the keyed form of auxiliary data, as encoded. */
    AUXILIARY_DATA,
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
    /** A set of committee cold credentials. */
    CREDENTIAL_SET(Shape.COLLECTION),
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

    // Says only what container a value is: a row of this type has a table for what is inside.
    boolean container() {
        return collection() || this == MAP || this == NONEMPTY_MAP;
    }

    /**
     * @return the table of a value of this type, or of each item of a collection; null for a leaf
     */
    public RecordSpec record() {
        switch (this) {
            case OPTIONAL_ANCHOR:
            case ANCHOR:
                return RawAnchor.RECORD;
            case OPTIONAL_POOL_METADATA:
                return RawPoolMetadata.RECORD;
            case CREDENTIAL:
            case CREDENTIAL_SET:
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
            case OPTIONAL_AUXILIARY_DATA:
                return RawAuxData.RECORD;
            case REDEEMER_KEY:
                return RawRedeemer.KEY;
            case REDEEMER_VALUE:
                return RawRedeemer.VALUE;
            case EX_UNITS:
                return RawExUnits.RECORD;
            case UPDATE:
                return RawUpdate.RECORD;
            case PROPOSED_PARAMETER_UPDATES:
                return RawUpdate.PROPOSALS;
            case PROTOCOL_PARAM_UPDATE:
                return ProtocolParamField.RECORD;
            case NONCE:
                return RawNonce.RECORD;
            case COST_MODELS:
                return RawCostModels.RECORD;
            case EX_UNIT_PRICES:
                return RawExUnitPrices.RECORD;
            case POOL_VOTING_THRESHOLDS:
                return RawVotingThresholds.POOL;
            case DREP_VOTING_THRESHOLDS:
                return RawVotingThresholds.DREP;
            case VOTER:
                return RawVoter.RECORD;
            case GOV_ACTION_ID:
            case OPTIONAL_GOV_ACTION_ID:
                return RawGovActionId.RECORD;
            case VOTING_PROCEDURE:
                return RawVotingProcedure.RECORD;
            case GOV_ACTION:
                return GovActionKind.RECORD;
            case CONSTITUTION:
                return RawConstitution.RECORD;
            case WITHDRAWALS:
                return RawWithdrawal.RECORD;
            case COMMITTEE_TERMS:
                return RawGovAction.COMMITTEE_TERMS;
            default:
                return null;
        }
    }
}
