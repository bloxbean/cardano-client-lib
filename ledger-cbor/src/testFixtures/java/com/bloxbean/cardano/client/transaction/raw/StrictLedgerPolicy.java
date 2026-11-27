package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;

/**
 * For tests of the ledger reader (ADR 0003 D6): rejects, by throwing {@link IllegalStateException}, an item that is
 * unknown, outside its era or beyond a record's last position, a repeated key of a record, a missing field, and a
 * value whose CBOR
 * shape is not its {@link FieldType}'s. The shapes are those Yano's {@code HaskellPolicy.check} reads, with hash sizes
 * and integer widths but without its other lengths and ranges, so a table that names a wrong type fails on real input.
 * A type with no shape here is rejected, so that a new type is added here when it is added to the tables.
 */
public final class StrictLedgerPolicy implements FormatPolicy {
    /** The policy; it has no state. */
    public static final StrictLedgerPolicy INSTANCE = new StrictLedgerPolicy();

    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_NEGATIVE = 1;
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_TEXT = 3;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final long EMBEDDED_CBOR = 24;
    private static final long RATIONAL = 30;
    private static final long SET = 258;

    private StrictLedgerPolicy() {
    }

    @Override
    public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
        if (field == null || !field.validIn(era))
            throw new IllegalStateException("unknown or out-of-era item in " + record + " at " + value.offset());
        // a repeated key of a record; a map of like entries, such as redeemers, has the ledger's rule per type
        if (occurrence > 0 && record.keyType() == null)
            throw new IllegalStateException("repeated key in " + record + " at offset " + value.offset());
    }

    @Override
    public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
        throw new IllegalStateException("missing " + field.type() + " in " + record);
    }

    @Override
    public void check(FieldType type, CborSpan value, Era era) {
        if (!fits(type, value))
            throw new IllegalStateException("not a " + type + " at offset " + value.offset());
    }

    private static boolean fits(FieldType type, CborSpan value) {
        CborSpan item = content(value);
        int major = item.majorType();
        switch (type) {
            case UINT, COIN, SEQUENCE_NUMBER, KES_PERIOD, MAJOR_PROTOCOL_VERSION, EX_UNIT, NETWORK_ID, LANGUAGE,
                 REDEEMER_TAG, VOTE:
                return major == MAJOR_UNSIGNED;
            case UINT16:
                return unsigned(item, 16);
            case UINT32, MINOR_PROTOCOL_VERSION:
                return unsigned(item, 32);
            case POSITIVE_COIN:
                return unsigned(item, 64) && item.asBigInteger().signum() > 0;
            case POSITIVE_UINT32:
                return unsigned(item, 32) && item.asBigInteger().signum() > 0;
            case INT64:
                return major <= MAJOR_NEGATIVE;
            case NONZERO_INT64:
                return major <= MAJOR_NEGATIVE && item.asBigInteger().signum() != 0;
            case HASH28:
                return sized(item, 28);
            case HASH32:
                return sized(item, 32);
            case OPTIONAL_HASH28:
                return value.isNull() || sized(item, 28);
            case OPTIONAL_HASH32:
                return value.isNull() || sized(item, 32);
            case BYTES, VKEY, SIGNATURE, VRF_VKEY, VRF_PROOF, KES_VKEY, KES_SIGNATURE, ADDRESS, REWARD_ACCOUNT,
                 ASSET_NAME, PLUTUS_SCRIPT:
                return major == MAJOR_BYTES;
            case OPTIONAL_IPV4, OPTIONAL_IPV6:
                return value.isNull() || major == MAJOR_BYTES;
            case URL, DNS_NAME:
                return major == MAJOR_TEXT;
            case UNIT_INTERVAL, NONNEGATIVE_INTERVAL, POSITIVE_INTERVAL:
                return rational(value);
            case OPTIONAL_NONNEGATIVE_INTERVAL:
                return value.isNull() || rational(value);
            case BOOL:
                return value.length() == 1 && ((value.buffer()[value.offset()] & 0xfe) == 0xf4);
            case OPTIONAL_PORT, OPTIONAL_UINT32:
                return value.isNull() || unsigned(item, 32);
            case OPTIONAL_COIN:
                return value.isNull() || major == MAJOR_UNSIGNED;
            case OPTIONAL_ANCHOR, OPTIONAL_POOL_METADATA, OPTIONAL_GOV_ACTION_ID, OPTIONAL_EB_REFERENCES_ANNOUNCEMENT,
                 OPTIONAL_BLS_KEY:
                return value.isNull() || major == MAJOR_ARRAY;
            case OPTIONAL_AUXILIARY_DATA:
                return value.isNull() || major == MAJOR_ARRAY || major == MAJOR_MAP;
            case AUXILIARY_DATA, TRANSACTION_OUTPUT, REDEEMERS:
                return major == MAJOR_ARRAY || major == MAJOR_MAP;
            case MEMBER, CREDENTIAL, DREP, MIR, VRF_CERT, OPERATIONAL_CERT, PROTOCOL_VERSION, HEADER_BODY, EX_UNITS,
                 EX_UNIT_PRICES, ANCHOR, GOV_ACTION_ID, VOTING_PROCEDURE, CONSTITUTION, VOTER, GOV_ACTION,
                 REDEEMER_KEY, REDEEMER_VALUE, NATIVE_SCRIPT, UPDATE, NONCE, DATUM_OPTION, POOL_VOTING_THRESHOLDS,
                 DREP_VOTING_THRESHOLDS, COST_MODEL, LIST, RELAY_LIST, CERTIFICATE_LIST:
                return major == MAJOR_ARRAY;
            case NONEMPTY_ARRAY:
                return major == MAJOR_ARRAY && item.size() > 0;
            case SET, KEY_HASH_SET, CREDENTIAL_SET:
                return set(value);
            case NONEMPTY_SET, NONEMPTY_LIST, NONEMPTY_CERTIFICATE_SET:
                return set(value) && item.size() > 0;
            case MAP, WITHDRAWALS, COMMITTEE_TERMS, COST_MODELS, PROTOCOL_PARAM_UPDATE, PROPOSED_PARAMETER_UPDATES,
                 METADATA, TRANSACTION_BODY, SUB_TRANSACTION_BODY, TRANSACTION_WITNESS_SET:
                return major == MAJOR_MAP;
            case NONEMPTY_MAP:
                return major == MAJOR_MAP && item.size() > 0;
            case MIR_TARGET:
                return major == MAJOR_MAP || major == MAJOR_UNSIGNED;
            case VALUE:
                return major == MAJOR_UNSIGNED || major == MAJOR_ARRAY;
            case ACCOUNT_BALANCE_INTERVAL:
                return major == MAJOR_UNSIGNED || major == MAJOR_ARRAY && item.size() == 2;
            case DATA, SCRIPT_REF:
                return value.tag() == EMBEDDED_CBOR && value.untag().majorType() == MAJOR_BYTES;
            case PLUTUS_DATA, OPTIONAL_PLUTUS_DATA:
                return true;
            default:
                throw new IllegalStateException("no shape for " + type);
        }
    }

    private static CborSpan content(CborSpan value) {
        CborSpan content = value;
        while (content.isTagged())
            content = content.untag();
        return content;
    }

    private static boolean unsigned(CborSpan item, int bits) {
        return item.majorType() == MAJOR_UNSIGNED && item.asBigInteger().bitLength() <= bits;
    }

    private static boolean sized(CborSpan item, int length) {
        return item.majorType() == MAJOR_BYTES && item.byteString().length == length;
    }

    private static boolean rational(CborSpan value) {
        return value.tag() == RATIONAL && value.untag().majorType() == MAJOR_ARRAY && value.untag().size() == 2;
    }

    private static boolean set(CborSpan value) {
        CborSpan item = value.untagIf(SET);
        return !item.isTagged() && item.majorType() == MAJOR_ARRAY;
    }
}
