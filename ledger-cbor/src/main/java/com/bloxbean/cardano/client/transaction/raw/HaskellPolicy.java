package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The decoding rules of Haskell's ledger decoders, for CCL's reader (ADR 0005): what cardano-ledger rejects in a
 * record the reader walks, plus strict reads of a transaction, a block, a header, a record, a value and a script. The
 * reader knows where each field is, and this policy decides by the record's shape, the field's row and the value's
 * type, never by a key number or a position. It is an opt-in: no view, model or Yaci path calls it unless its caller
 * passes it, and {@link FormatPolicy#ACCEPT_ALL} stays the default.
 *
 * <p>A strict read rejects with a {@link LedgerDecodingException} only, whose {@link LedgerDecodingException#reason()
 * reason} is a stable code: bytes that are not one well-formed item are {@link Reason#NOT_WELL_FORMED}, a rejection by
 * the reader's own checks is {@link Reason#STRUCTURE}, and this policy's rejections carry their own codes. Accepting
 * is not ledger validity: fees, balance, witnesses, scripts and every rule that crosses fields are the ledger's.</p>
 *
 * <p>The decoder version decides three rules (cardano-ledger {@code f649f975}). Below 12 a byte string is definite
 * ({@code decodeBytes}, Decoder.hs:355-361) and a {@code RecD} record or a tuple, such as an anchor, a value's pair,
 * ex units, a redeemer's value, prices, a proposal, a vote, a governance action id or a constitution, is a
 * definite-length array (BaseTypes.hs:996-1004, Value.hs:298-310, ExUnits.hs:208-216, 235-243, DecCBOR.hs:288-300,
 * Procedures.hs:181-190, 487-497, 559-570, 1036-1046); from 12 a rational's numbers are {@code Word64}s and no longer
 * bignums ({@code decodeIntegralRational}, BaseTypes.hs:417-428). Below 9 a text has at most 64 bytes and a set no
 * tag or rule against duplicates, which this class does not hold.</p>
 *
 * <p>The decoder version comes from the era, not the ledger's current protocol version: Haskell decodes an era's blocks,
 * headers and transactions with {@code eraDecoder}, at the era's lowest major version ({@code eraProtVerLow},
 * cardano-ledger Core/Era.hs:259-260; ouroboros-consensus Shelley/Ledger/Block.hs:352, Mempool.hs:294). So a Conway
 * transaction decodes at 9 at protocol versions 9 to 11; {@link #forEra} picks the policy. Only Conway has rules:
 * Dijkstra's (decoder version 12) arrive with ADR 0003 M7b.</p>
 *
 * <p>A type this class does not name has no rule: the reader's walk reads it. Its rules stand for a transaction at
 * Conway's decoder version and for a block header of any era, which no version below 12 changes. Every strict read is
 * stack-safe: the walk nests to the tables' fixed depth, and the recursive types (Plutus data, metadata, native
 * scripts) are checked without recursion.</p>
 */
public final class HaskellPolicy implements FormatPolicy {

    /** Conway's decoder version: {@code eraProtVerLow ConwayEra} (Era.hs:132). */
    public static final HaskellPolicy CONWAY = new HaskellPolicy(9);

    private static final int FIRST_KNOWN_VERSION = 9;
    private static final int VERSION_12 = 12;
    private static final int KEY_HASH_LENGTH = 28;
    private static final int HASH32_LENGTH = 32;
    private static final int IPV4_LENGTH = 4;
    private static final int IPV6_LENGTH = 16;
    private static final int REWARD_ACCOUNT_LENGTH = 29;
    private static final int VKEY_LENGTH = 32;
    private static final int SIGNATURE_LENGTH = 64;
    private static final int VRF_PROOF_LENGTH = 80;
    private static final int KES_SIGNATURE_LENGTH = 448;
    private static final int MAX_ASSET_NAME_LENGTH = 32;
    private static final int TAG_EMBEDDED = 24;
    private static final long MAX_WORD16 = 0xFFFFL;
    private static final long MAX_WORD32 = 0xFFFF_FFFFL;
    /** {@code MaxVersion}, Version.hs:63. */
    private static final long MAX_VERSION = 13;
    private static final BigInteger MIN_INT64 = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_INT64 = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int MAX_PORT = 0xFFFF;
    private static final int MAX_NETWORK_ID = 1;
    private static final int MAX_KEY_SHOWN = 9;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final long NO_TAG = -1;
    /** Conway's {@code PlutusPurpose}, spending to proposing (Conway/Scripts.hs:292-300). */
    private static final long MAX_REDEEMER_TAG = 5;
    /** {@code Vote}'s {@code decodeEnumBounded}: no, yes, abstain (Procedures.hs:418-427). */
    private static final long MAX_VOTE = 2;
    /** A cost model's language is a {@code Word8} ({@code Map Word8 [Int64]}, Plutus/CostModels.hs:302-304). */
    private static final long MAX_LANGUAGE = 0xFF;
    /** {@code succVersion (ProtVerHigh ConwayEra)}, Conway's highest major version being 11 (Era.hs:133). */
    private static final long MAX_HARD_FORK_MAJOR = 12;
    /** A proposal procedure, an item of the body's set of them. */
    private static final RecordSpec PROPOSAL = TxBodyField.PROPOSAL_PROCEDURES.spec().record();
    /** A hard fork initiation, the member of the governance action union. */
    private static final RecordSpec HARD_FORK = GovActionKind.HARD_FORK_INITIATION.member().record();
    /** An output and the redeemers, whose decoders pick their form by cborg's token type. */
    private static final RecordSpec OUTPUT = TxBodyField.OUTPUTS.spec().record();
    private static final RecordSpec REDEEMERS = WitnessSetField.REDEEMERS_FIELD.spec().record();
    /** {@code isMultiAssetSmallEnough} (Mary/Value.hs:721-723): {@code 44 * assets + 28 * policies <= 65535}. */
    private static final long MAX_COMPACT_MULTI_ASSET = 65535;
    private static final long COMPACT_ASSET = 44;
    private static final long COMPACT_POLICY = 28;
    /** A map's head with an 8-byte length, which cborg reports as {@code TypeMapLen64}. */
    private static final int MAP_HEAD_8_BYTES = 0xbb;
    private static final long NATIVE_SCRIPT_LANGUAGE = 0;
    /** Conway's script languages: native, Plutus V1, V2 and V3. */
    private static final long MAX_SCRIPT_LANGUAGE = 3;

    /**
     * The types with no rule of their own (J9): a record's type, whose table the walk reads; a type of an era without
     * rules here (Dijkstra and later); or a value Conway's walk reads with no further check. A type that is neither
     * here nor in {@link #check} fails a strict read with an {@link IllegalStateException}, which the conformance gate
     * counts as an error, so a new type cannot arrive without a decision.
     */
    static final Set<FieldType> NO_RULE = Collections.unmodifiableSet(EnumSet.of(
            FieldType.POSITIVE_INTERVAL, FieldType.POSITIVE_UINT32, FieldType.OPTIONAL_POOL_METADATA,
            FieldType.OPTIONAL_EB_REFERENCES_ANNOUNCEMENT, FieldType.OPTIONAL_BLS_KEY, FieldType.OPTIONAL_PLUTUS_DATA,
            FieldType.OPTIONAL_COIN, FieldType.OPTIONAL_UINT32, FieldType.OPTIONAL_NONNEGATIVE_INTERVAL,
            FieldType.MEMBER, FieldType.CREDENTIAL, FieldType.DREP, FieldType.MIR, FieldType.MIR_TARGET,
            FieldType.HEADER_BODY, FieldType.OPERATIONAL_CERT, FieldType.PROTOCOL_VERSION, FieldType.TRANSACTION_OUTPUT,
            FieldType.DATUM_OPTION, FieldType.UPDATE, FieldType.PROPOSED_PARAMETER_UPDATES,
            FieldType.PROTOCOL_PARAM_UPDATE, FieldType.NONCE, FieldType.COST_MODELS, FieldType.POOL_VOTING_THRESHOLDS,
            FieldType.DREP_VOTING_THRESHOLDS, FieldType.VOTER, FieldType.GOV_ACTION, FieldType.WITHDRAWALS,
            FieldType.COMMITTEE_TERMS, FieldType.TRANSACTION_BODY, FieldType.SUB_TRANSACTION_BODY,
            FieldType.TRANSACTION_WITNESS_SET, FieldType.REDEEMER_KEY, FieldType.AUXILIARY_DATA,
            FieldType.ACCOUNT_BALANCE_INTERVAL, FieldType.MAP, FieldType.CERTIFICATE_LIST));

    private final int decoderVersion;

    HaskellPolicy(int decoderVersion) {
        if (decoderVersion < FIRST_KNOWN_VERSION) {
            throw new IllegalArgumentException("no rules for decoder version " + decoderVersion);
        }
        this.decoderVersion = decoderVersion;
    }

    /**
     * The policy Haskell decodes an era's transactions with: the era's {@code eraDecoder}. Only Conway has rules; an era
     * added later (Dijkstra decodes at 12, where a {@code RecD} record may be indefinite) needs its own entry here.
     *
     * @throws IllegalArgumentException for an era without rules
     */
    public static HaskellPolicy forEra(Era era) {
        if (era == Era.Conway) {
            return CONWAY;
        }
        throw new IllegalArgumentException("no decoding rules for era " + era);
    }

    /**
     * A transaction as Haskell's mempool and block decoders read it: CCL's view of it ({@link RawTx#of}) and the walk
     * of its array, body, witness set, validity flag and auxiliary data with this policy.
     *
     * @param tx a transaction, exactly one CBOR item
     * @return CCL's view of it
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public RawTx transaction(byte[] tx) {
        CborSpan span = StrictCbor.span(tx);
        // the walk first, so that a rejection carries this policy's reason rather than the view's
        return strict(() -> {
            RawTx.walk(span, Era.Conway, this);
            return RawTx.of(tx);
        });
    }

    /**
     * A bare block of this policy's era, as Haskell's block decoder reads one: its header, in the era's layout and with
     * the header's rules ({@link #header(CborSpan)}), its containers, and each of its transactions as
     * {@link #transaction} reads one.
     *
     * @param block a bare block, exactly one CBOR item
     * @return CCL's view of it
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public RawBlock block(byte[] block) {
        CborSpan span = StrictCbor.span(block);
        if (span.isTagged() || StrictCbor.major(span) != MAJOR_ARRAY || BlockEnvelope.isEnvelope(span)) {
            throw new LedgerDecodingException(Reason.UNEXPECTED_TYPE, "a bare block is an untagged array of its parts,"
                    + " not in an [era, block] envelope or a tag");
        }
        RawBlock view = strict(() -> RawBlock.of(block, Era.Conway));
        strict(() -> {
            RawHeader.walk(view.header(), Era.Conway, this);
            return RawHeader.of(view.header(), Era.Conway);
        });
        for (int i = 0; i < view.txCount(); i++) {
            int index = i;
            transaction(strict(() -> view.txBytes(index)));
        }
        return view;
    }

    /**
     * Whether a block header decodes, in either layout. No decoder version below 12 changes a header's rules, so they
     * are read at Conway's. A header and its body may be of either length: {@code decodeRecordNamed} at the dataset's
     * cardano-ledger {@code 6176e413} (Praos/BlockHeader.hs:204-226), later than the {@code RecD} of {@code f649f975}.
     * Which length deployed nodes read is ADR 0005 Q4.
     *
     * @param header a header as encoded, exactly one CBOR item
     * @return CCL's view of it
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public static RawHeader header(byte[] header) {
        return header(StrictCbor.span(header));
    }

    /**
     * {@link #header(byte[])} for a header already read as a span.
     *
     * @param header a header as encoded
     * @return CCL's view of it
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public static RawHeader header(CborSpan header) {
        return strict(() -> {
            RawHeader.walk(header, CONWAY);
            return RawHeader.of(header);
        });
    }

    /**
     * One record of a Conway transaction, walked with this policy: the reader of a CDDL rule that has a table.
     *
     * @param record the record's table
     * @param value  the record, exactly one CBOR item
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public void walk(RecordSpec record, byte[] value) {
        walk(record, StrictCbor.span(value));
    }

    /**
     * {@link #walk(RecordSpec, byte[])} for a record already read as a span.
     *
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public void walk(RecordSpec record, CborSpan value) {
        strict(() -> {
            record.walk(value, Era.Conway, this);
            return null;
        });
    }

    /**
     * One value of a type: its rule, then its table if it has one. The reader of a CDDL rule that is one type, not a
     * collection (a collection's rules, such as being non-empty, are its parent's).
     *
     * @param type  the value's type
     * @param value the value, exactly one CBOR item
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public void walk(FieldType type, byte[] value) {
        walk(type, StrictCbor.span(value));
    }

    /**
     * {@link #walk(FieldType, byte[])} for a value already read as a span.
     *
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public void walk(FieldType type, CborSpan value) {
        strict(() -> {
            check(type, value, Era.Conway);
            if (type.record() != null) {
                type.record().walk(value, Era.Conway, this);
            }
            return null;
        });
    }

    /**
     * A script, {@code script = [language, script]}, as a Conway script reference holds one: a native script
     * ({@code 0}) in Conway's types, with no guard, or a Plutus V1 to V3 script as a definite-length byte string.
     *
     * @param script the script, exactly one CBOR item
     * @return CCL's view of it
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public RawScript script(byte[] script) {
        CborSpan span = StrictCbor.span(script);
        return strict(() -> script(span, Era.Conway));
    }

    /**
     * Runs a strict read: the policy's rejections pass as they are, and any other {@link CborRuntimeException} the
     * reader throws (the arity or form of a record, a repeated key the reader itself refuses) is
     * {@link Reason#STRUCTURE}. Bytes that are not well formed are rejected before, by {@link StrictCbor#span}.
     */
    private static <T> T strict(Supplier<T> read) {
        try {
            return read.get();
        } catch (LedgerDecodingException e) {
            throw e;
        } catch (CborRuntimeException e) {
            throw new LedgerDecodingException(Reason.STRUCTURE, e.getMessage(), e);
        }
    }

    /**
     * A record is in no tag, as Haskell does not look through one, or in its own: the aux data map's 259
     * ({@code TagD 259}, Alonzo/TxAuxData.hs:269-271). A proposal procedure, an item of a set, is a {@code RecD}
     * record (Procedures.hs:559-570). A multi-asset, of a value or of mint, is small enough to compact, at every
     * decoder version ({@code decodeMultiAsset}, Mary/Value.hs:327-337).
     */
    @Override
    public void enter(RecordSpec record, CborSpan container, Era era) {
        if (record == PROPOSAL) {
            recD(container, record.name());
        }
        if (isMultiAsset(record)) {
            smallEnoughToCompact(container);
        }
        CborSpan content = container;
        if (record.tag() != NO_TAG) {
            if (container.tag() != record.tag()) {
                throw new LedgerDecodingException(Reason.TAG,
                        record + " at " + container.offset() + " is not in tag " + record.tag());
            }
            content = container.untag();
        }
        boolean map = record.shape() == RecordSpec.Shape.KEYED;
        if (StrictCbor.major(content) != (map ? MAJOR_MAP : MAJOR_ARRAY)) {
            throw new LedgerDecodingException(Reason.UNEXPECTED_TYPE,
                    record + " at " + container.offset() + " is not an untagged "
                    + (map ? "map" : "array"));
        }
    }

    /**
     * No unknown field, none outside its era, none repeated ({@code invalidField}, {@code decodeRecordSum},
     * {@code decodeMapByKey}), but a redeemer's key: the map of redeemers keeps its last value ({@code Map.fromList},
     * Alonzo/TxWits.hs:566-573). A hard fork's major version is at most {@code succVersion (ProtVerHigh ConwayEra)}
     * ({@code decodeProtVer @ConwayEra}, BaseTypes.hs:253-263, Procedures.hs:938-940); a header's may be one more.
     * The form a value takes, where it may take more than one (a record by major type, such as a value, an output,
     * auxiliary data or redeemers), is checked for its era only: its items are checked where its parent's entry is,
     * and a map of redeemers keeps a repeated key. An output or the redeemers, though, is a map only by a head that
     * cborg reports as {@code TypeMapLen} or {@code TypeMapLenIndef}: the 8-byte head, {@code TypeMapLen64}, takes the
     * list form's decoder, which fails (Babbage/TxOut.hs:554-556, Alonzo/TxWits.hs:556-562).
     */
    @Override
    public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
        if (field == null) {
            throw new LedgerDecodingException(Reason.UNKNOWN_FIELD, "unknown " + at(record, key, value));
        }
        if (!field.validIn(era)) {
            throw new LedgerDecodingException(Reason.FIELD_NOT_IN_ERA, at(record, key, value) + " is not in " + era);
        }
        if (record.shape() == RecordSpec.Shape.BY_MAJOR_TYPE) {
            boolean dispatchedByToken = record == OUTPUT || record == REDEEMERS;
            if (dispatchedByToken && (value.buffer()[value.offset()] & 0xff) == MAP_HEAD_8_BYTES) {
                throw new LedgerDecodingException(Reason.MAP_HEAD_WIDTH,
                        record + " at " + value.offset() + " is a map with an 8-byte head");
            }
            return;
        }
        if (occurrence > 0 && field.type() != FieldType.REDEEMER_VALUE) {
            throw new LedgerDecodingException(Reason.REPEATED_KEY, "repeated " + at(record, key, value));
        }
        if (field.record() != null && isSet(field.type())) {
            distinct(StrictCbor.set(value), field.record(), era);
        } else if (field.record() != null && hasRecordKeys(field.record())) {
            distinct(StrictCbor.map(value).stream().map(Map.Entry::getKey).toList(), field.record().keyType().record(),
                    era);
        }
        if (record == HARD_FORK && field.type() == FieldType.PROTOCOL_VERSION) {
            int major = RawProtocolVersion.of(value).major();
            if (major > MAX_HARD_FORK_MAJOR) {
                throw new LedgerDecodingException(Reason.INTEGER_RANGE,
                        "Protocol version " + major + " exceeds the maximum expected version of "
                        + MAX_HARD_FORK_MAJOR);
            }
        }
    }

    /** A {@code multiasset<a>}: policy ids to maps of asset names to amounts. */
    private static boolean isMultiAsset(RecordSpec record) {
        if (record.keyType() != FieldType.HASH28 || record.fields().size() != 1) {
            return false;
        }
        RecordSpec assets = record.fields().get(0).record();
        return assets != null && assets.keyType() == FieldType.ASSET_NAME;
    }

    private static void smallEnoughToCompact(CborSpan multiAsset) {
        List<Map.Entry<CborSpan, CborSpan>> policies = StrictCbor.map(multiAsset);
        long assets = 0;
        for (Map.Entry<CborSpan, CborSpan> policy : policies) {
            assets += StrictCbor.map(policy.getValue()).size();
        }
        if (COMPACT_ASSET * assets + COMPACT_POLICY * policies.size() > MAX_COMPACT_MULTI_ASSET) {
            throw new LedgerDecodingException(Reason.MULTI_ASSET_TOO_BIG,
                    "MultiAsset is too big to compact: " + assets + " assets under "
                    + policies.size() + " policies");
        }
    }

    // A map whose keys are records, such as voters: CCL counts a repeated key by its items' bytes.
    private static boolean hasRecordKeys(RecordSpec map) {
        return map.keyType() != null && map.keyType().record() != null;
    }

    private static boolean isSet(FieldType type) {
        return switch (type) {
            case SET, NONEMPTY_SET, NONEMPTY_CERTIFICATE_SET, CREDENTIAL_SET -> true;
            default -> false;
        };
    }

    // A key can be any item, of any size.
    private static String at(RecordSpec record, CborSpan key, CborSpan value) {
        if (key == null) {
            return record + " item at " + value.offset();
        }
        return record + " key " + (key.length() <= MAX_KEY_SHOWN ? HexUtil.encodeHexString(key.bytes())
                : "at " + key.offset());
    }

    /** A record has every field required in its era; a positional one, every item. */
    @Override
    public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
        throw new LedgerDecodingException(Reason.MISSING_FIELD,
                record + " at " + container.offset() + " has no " + field.type() + " (no key "
                + field.key() + ")");
    }

    @Override
    public void check(FieldType type, CborSpan value, Era era) {
        switch (type) {
            case UINT, COIN, SEQUENCE_NUMBER, KES_PERIOD -> StrictCbor.word64(value);
            case UINT16 -> atMost(value, MAX_WORD16);
            case UINT32, MINOR_PROTOCOL_VERSION -> atMost(value, MAX_WORD32);
            case MAJOR_PROTOCOL_VERSION -> atMost(value, MAX_VERSION);
            case POSITIVE_COIN -> {
                if (StrictCbor.word64(value) == 0) {
                    throw new LedgerDecodingException(Reason.ZERO, "a positive coin is zero at " + value.offset());
                }
            }
            case NETWORK_ID -> {
                if (StrictCbor.unsignedLong(value) > MAX_NETWORK_ID) {
                    throw new LedgerDecodingException(Reason.INTEGER_RANGE, "network id at " + value.offset());
                }
            }
            case HASH28 -> sized(value, KEY_HASH_LENGTH, "key hash");
            case HASH32 -> sized(value, HASH32_LENGTH, "hash");
            case OPTIONAL_HASH32 -> sizedOrNull(value, HASH32_LENGTH, "hash");
            case VKEY, VRF_VKEY, KES_VKEY -> sized(value, VKEY_LENGTH, "verification key");
            case SIGNATURE -> sized(value, SIGNATURE_LENGTH, "signature");
            case KES_SIGNATURE -> sized(value, KES_SIGNATURE_LENGTH, "KES signature");
            case VRF_PROOF -> sized(value, VRF_PROOF_LENGTH, "VRF proof");
            case VRF_CERT -> {
                if (decoderVersion < VERSION_12) {
                    definite(value, "a VRF result (enforceSize)");
                }
            }
            case ADDRESS -> AddressValidator.validate(bytes(value));
            case VALUE -> {
                if (decoderVersion < VERSION_12 && StrictCbor.major(value) == MAJOR_ARRAY) {
                    definite(value, "a multi-asset value (decodeValuePair)");
                }
            }
            case ASSET_NAME -> {
                int found = length(value);
                if (found > MAX_ASSET_NAME_LENGTH) {
                    throw new LedgerDecodingException(Reason.BYTES_SIZE, "asset name of " + found + " bytes");
                }
            }
            case INT64 -> int64(value);
            case NONZERO_INT64 -> {
                if (int64(value).signum() == 0) {
                    throw new LedgerDecodingException(Reason.ZERO, "MultiAsset cannot contain zeros");
                }
            }
            case DATA -> PlutusDataValidator.validate(embedded(value, "an inline datum"));
            // the script of the era: in Conway, a native script holds no guard (type 6), at any depth
            case SCRIPT_REF -> script(StrictCbor.span(embedded(value, "a script reference")), era);
            case PLUTUS_DATA -> PlutusDataValidator.validate(value);
            case NATIVE_SCRIPT -> NativeScriptChecker.check(value, era, decoderVersion < VERSION_12);
            case PLUTUS_SCRIPT, BYTES -> length(value);
            case METADATA -> MetadataValidator.validate(value);
            case OPTIONAL_AUXILIARY_DATA -> {
                if (decoderVersion < VERSION_12 && !value.isNull() && StrictCbor.major(value) == MAJOR_ARRAY) {
                    definite(value, "auxiliary data [metadata, scripts] (RecD)");
                }
            }
            case BOOL -> bool(value);
            case REDEEMERS -> {
                if (value.isTagged()) {
                    throw new LedgerDecodingException(Reason.TAG, "redeemers at " + value.offset() + " are in a tag");
                }
            }
            case REDEEMER_TAG -> {
                if (StrictCbor.unsignedLong(value) > MAX_REDEEMER_TAG) {
                    throw new LedgerDecodingException(Reason.INTEGER_RANGE,
                            "redeemer tag " + StrictCbor.unsigned(value) + " at "
                            + value.offset());
                }
            }
            case REDEEMER_VALUE, EX_UNITS, EX_UNIT_PRICES, ANCHOR, OPTIONAL_ANCHOR, GOV_ACTION_ID,
                 OPTIONAL_GOV_ACTION_ID, VOTING_PROCEDURE, CONSTITUTION -> {
                if (!value.isNull()) {
                    recD(value, type.name());
                }
            }
            // ExUnits' decNat: at most maxBound :: Int64
            case EX_UNIT -> StrictCbor.unsignedLong(value);
            case REWARD_ACCOUNT -> rewardAccount(value);
            case URL -> BoundedFields.url(value, "ledger record");
            case DNS_NAME -> BoundedFields.dnsName(value);
            case UNIT_INTERVAL -> rational(value, true);
            case NONNEGATIVE_INTERVAL -> rational(value, false);
            case OPTIONAL_HASH28 -> sizedOrNull(value, KEY_HASH_LENGTH, "script hash");
            case VOTE -> atMost(value, MAX_VOTE);
            case LANGUAGE -> atMost(value, MAX_LANGUAGE);
            case COST_MODEL -> StrictCbor.array(value).forEach(HaskellPolicy::int64);
            case OPTIONAL_PORT -> {
                if (!value.isNull() && StrictCbor.unsignedLong(value) > MAX_PORT) {
                    throw new LedgerDecodingException(Reason.INTEGER_RANGE, "relay port exceeds Word16");
                }
            }
            case OPTIONAL_IPV4 -> sizedOrNull(value, IPV4_LENGTH, "IPv4 address");
            case OPTIONAL_IPV6 -> sizedOrNull(value, IPV6_LENGTH, "IPv6 address");
            case LIST, RELAY_LIST -> StrictCbor.array(value);
            case SET, CREDENTIAL_SET -> StrictCbor.set(value);
            case NONEMPTY_LIST, NONEMPTY_SET -> nonEmpty(StrictCbor.set(value).isEmpty(), type.name());
            case NONEMPTY_ARRAY -> nonEmpty(StrictCbor.array(value).isEmpty(), type.name());
            case NONEMPTY_MAP -> nonEmpty(StrictCbor.map(value).isEmpty(), type.name());
            case KEY_HASH_SET -> keyHashes(value);
            case NONEMPTY_CERTIFICATE_SET -> nonEmpty(StrictCbor.set(value).isEmpty(), "Certificates");
            default -> {
                if (!NO_RULE.contains(type)) {
                    throw new IllegalStateException("no decoding rule for " + type + ": add one, or list it in NO_RULE");
                }
            }
        }
    }

    private byte[] bytes(CborSpan value) {
        return decoderVersion < VERSION_12 ? StrictCbor.definiteBytes(value) : StrictCbor.bytes(value);
    }

    private int length(CborSpan value) {
        return decoderVersion < VERSION_12 ? StrictCbor.definiteLength(value) : StrictCbor.bytes(value).length;
    }

    private void sized(CborSpan value, int length, String what) {
        int found = length(value);
        if (found != length) {
            throw new LedgerDecodingException(Reason.BYTES_SIZE, what + " of " + found + " bytes, expected " + length);
        }
    }

    /** A {@code RecD} record or a tuple: a definite-length array below version 12, either from 12. */
    private void recD(CborSpan value, String what) {
        if (decoderVersion < VERSION_12) {
            definite(value, what + " is a RecD record below decoder version 12");
        }
    }

    /** A definite-length array, as {@code decodeListLen} reads one. */
    private static void definite(CborSpan value, String what) {
        if (value.isIndefinite()) {
            throw new LedgerDecodingException(Reason.INDEFINITE_RECORD,
                    "an indefinite-length array at " + value.offset() + " (" + what + ")");
        }
    }

    private static void atMost(CborSpan value, long max) {
        long found = StrictCbor.unsignedLong(value);
        if (found > max) {
            throw new LedgerDecodingException(Reason.INTEGER_RANGE,
                    "unsigned integer " + found + " exceeds " + max + " at " + value.offset());
        }
    }

    /**
     * {@code decodeIntegerBounded64} (Mary/Value.hs:310-335) and a cost model's {@code Int64}: an integer, not a
     * bignum, within {@code Int64}.
     */
    private static BigInteger int64(CborSpan value) {
        BigInteger found = StrictCbor.integer(value);
        if (found.compareTo(MIN_INT64) < 0 || found.compareTo(MAX_INT64) > 0) {
            throw new LedgerDecodingException(Reason.INTEGER_RANGE, "overflow when decoding mint field: " + found);
        }
        return found;
    }

    /** cborg's {@code decodeBool}: the simple value {@code true} or {@code false}. */
    private static void bool(CborSpan value) {
        try {
            value.asBoolean();
        } catch (CborRuntimeException e) {
            throw new LedgerDecodingException(Reason.UNEXPECTED_TYPE, e.getMessage(), e);
        }
    }

    /**
     * {@code script = [language, script]}: a native script ({@code 0}) of the era's types, or a Plutus V1 to V3 script
     * ({@code PlutusBinary}) as a definite-length byte string ({@code decodeScript}, Alonzo/Scripts.hs; Conway's
     * languages are V1 to V3).
     */
    private RawScript script(CborSpan script, Era era) {
        List<CborSpan> fields = StrictCbor.array(script, 2, Reason.SCRIPT, "a script is [language, script]");
        long language = StrictCbor.unsignedLong(fields.get(0));
        if (language > MAX_SCRIPT_LANGUAGE) {
            throw new LedgerDecodingException(Reason.SCRIPT, "script language " + language + " is not supported in "
                    + era);
        }
        CborSpan body = fields.get(1);
        if (language == NATIVE_SCRIPT_LANGUAGE) {
            NativeScriptChecker.check(body, era, decoderVersion < VERSION_12);
        } else {
            length(body);
        }
        return new RawScript((int) language, body);
    }

    /** {@code decodeNestedCborBytes}: tag 24 on a byte string (Decoding.hs:238-239). */
    private byte[] embedded(CborSpan value, String what) {
        if (value.tag() != TAG_EMBEDDED) {
            throw new LedgerDecodingException(Reason.TAG, what + " at " + value.offset() + " is not in tag 24");
        }
        return bytes(value.untag());
    }

    private void sizedOrNull(CborSpan value, int length, String what) {
        if (!value.isNull()) {
            sized(value, length, what);
        }
    }

    /** {@code decodeAccountAddressT} (Address.hs:938-955): header {@code & 0xEE == 0xE0}, then a 28-byte hash. */
    private void rewardAccount(CborSpan value) {
        byte[] account = bytes(value);
        if (account.length != REWARD_ACCOUNT_LENGTH || (account[0] & 0xee) != 0xe0) {
            throw new LedgerDecodingException(Reason.ADDRESS, "reward account is not an account address");
        }
    }

    /** A {@code UnitInterval} or a {@code NonNegativeInterval} ({@link BoundedFields#boundedRational}). */
    private void rational(CborSpan value, boolean unit) {
        BoundedFields.boundedRational(value, unit ? "unit interval" : "non-negative interval", unit);
        if (decoderVersion >= VERSION_12) {
            StrictCbor.array(value.untag()).forEach(StrictCbor::unsigned);
        }
    }

    /** A set of key hashes: {@code decodeSet} rejects a repeated one from version 9 (Decoder.hs:952-962). */
    private void keyHashes(CborSpan value) {
        Set<ByteBuffer> seen = new HashSet<>();
        for (CborSpan hash : StrictCbor.set(value)) {
            sized(hash, KEY_HASH_LENGTH, "key hash");
            if (!seen.add(ByteBuffer.wrap(bytes(hash)))) {
                throw new LedgerDecodingException(Reason.DUPLICATE_ITEM, "duplicate key hash at " + hash.offset());
            }
        }
    }

    /**
     * {@code decodeSet} and {@code decodeOSet} reject two equal items, and {@code decodeMap} two equal keys, equal as
     * decoded values. The walk checks each item afterwards; an item it would reject has a key all the same, or is too
     * deep for one.
     */
    private static void distinct(List<CborSpan> items, RecordSpec record, Era era) {
        if (items.size() < 2) {
            return;
        }
        Set<ByteBuffer> seen = new HashSet<>();
        for (CborSpan item : items) {
            if (!seen.add(EqualityKey.of(item, record, era))) {
                throw new LedgerDecodingException(Reason.DUPLICATE_ITEM,
                        "duplicate " + record + " at " + item.offset());
            }
        }
    }

    private static void nonEmpty(boolean empty, String field) {
        if (empty) {
            throw new LedgerDecodingException(Reason.EMPTY_COLLECTION,
                    "'" + field + "' must be non-empty when supplied");
        }
    }

    /**
     * The value a record that passes this policy decodes to: its canonical encoding ({@link CborCanonical}), with the
     * sets its table names sorted, tagged or not.
     */
    private static final class EqualityKey implements FormatPolicy {
        private final Set<Integer> sets = new HashSet<>();

        /** @return the key of an item; one the walk does not enter is a leaf, its own key */
        static ByteBuffer of(CborSpan item, RecordSpec record, Era era) {
            if (ofLeaves(record)) {
                return ByteBuffer.wrap(CborCanonical.of(item));
            }
            EqualityKey walked = new EqualityKey();
            record.walk(item, era, walked);
            return ByteBuffer.wrap(CborCanonical.of(item, walked.sets));
        }

        // A record of leaves, none a set, decodes to equal values exactly when it has one canonical encoding.
        private static boolean ofLeaves(RecordSpec record) {
            for (FieldSpec field : record.fields()) {
                if ((field.record() != null && !ofLeaves(field.record())) || isSetOf(field.type())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isSetOf(FieldType type) {
            return type == FieldType.KEY_HASH_SET || isSet(type);
        }

        @Override
        public void check(FieldType type, CborSpan value, Era era) {
            if (isSetOf(type)) {
                sets.add(value.offset());
            }
        }
    }
}
