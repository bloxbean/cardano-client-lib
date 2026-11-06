package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * A certificate as received, read from its original bytes by the {@link CertKind} table, in any era: a certificate
 * outside its era is the policy's to reject (ADR 0003 D5). Accessors read an item when they are called, and throw
 * {@link CborRuntimeException} if it is not what the table says or the kind has no such item. Tags on an item are
 * looked through, and any array may be indefinite. Hash lengths, ranges and the like are not checked.
 * <p>
 * {@link #of(CborSpan)} reads the format as ADR 0003 D5 sets it. {@link #lenient(CertKind, CborSpan)} reads what
 * this module's models have always accepted, which ADR 0003 D7 keeps until a separate decision changes it.
 */
public final class RawCertificate {
    private final CertKind kind;
    private final CborSpan span;
    private final PositionalRecord record;
    private final boolean lenient;

    private RawCertificate(CertKind kind, CborSpan span, List<CborSpan> items, boolean lenient, Era era) {
        this.kind = kind;
        this.span = span;
        this.record = PositionalRecord.of(kind.record(items, era), span, items, !lenient);
        this.lenient = lenient;
    }

    /**
     * @param span a certificate as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array, its tag is unknown, or
     *                              it does not have the items of its tag
     */
    public static RawCertificate of(CborSpan span) {
        return of(span, null);
    }

    /**
     * Reads a certificate as {@link #of(CborSpan)} does, in an era: from Dijkstra, a pool registration whose item after
     * the VRF key hash is an array or null has a BLS key slot there ({@link #blsKey()}), as the ledger reads it.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param span a certificate as encoded
     * @param era  the era to read it in, or null to read it as {@link #of(CborSpan)} does
     * @return the view
     * @throws CborRuntimeException if it is not an array, its tag is unknown, or
     *                              it does not have the items of its tag
     */
    public static RawCertificate of(CborSpan span, Era era) {
        List<CborSpan> items = RawItems.array(span, CertKind.RECORD.name());
        CertKind kind = CertKind.of(items, span).orElseThrow(() -> error("Unknown certificate tag", span));
        return new RawCertificate(kind, span, items, false, era);
    }

    /**
     * Reads a certificate by position, as the models always have: the tag is not compared with the kind, items after
     * the last position are ignored here and in a relay, a DRep, pool metadata and a move_instantaneous_reward, and a
     * position of a number holding something else reads as null.
     *
     * @param kind the kind to read it as
     * @param span a certificate as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array or has too few items
     */
    public static RawCertificate lenient(CertKind kind, CborSpan span) {
        return new RawCertificate(kind, span, RawItems.array(span, CertKind.RECORD.name()), true, null);
    }

    /**
     * Reads a certificate by position as {@link #lenient(CertKind, CborSpan)} does, as the kind its tag names.
     *
     * @param span a certificate as encoded
     * @return the view, or empty for an unknown tag
     * @throws CborRuntimeException if it is not an array starting with an unsigned integer that fits an int, or has too
     *                              few items
     */
    public static Optional<RawCertificate> lenient(CborSpan span) {
        return lenient(span, null);
    }

    // As lenient(span), in an era: see of(span, era).
    static Optional<RawCertificate> lenient(CborSpan span, Era era) {
        List<CborSpan> items = RawItems.array(span, CertKind.RECORD.name());
        return CertKind.of(items, span).map(kind -> new RawCertificate(kind, span, items, true, era));
    }

    /**
     * Walks this certificate with a policy, as {@code CertKind.RECORD.walk} does, without listing its items again.
     *
     * @param era    the era to read it in
     * @param policy the policy, which throws to reject
     * @throws CborRuntimeException if the policy accepts a value the walk cannot enter
     */
    public void walk(Era era, FormatPolicy policy) {
        policy.enter(CertKind.RECORD, span, era);
        RecordWalk.tagged(CertKind.RECORD, span, record.items(), era, policy);
    }

    /**
     * For a view from {@link #lenient(CertKind, CborSpan)}: checks what the models check on the certificates that
     * existed before Conway.
     *
     * @return this view
     * @throws CborRuntimeException if the certificate's tag is not this kind's,
     *                              or it has more items than the table
     */
    public RawCertificate exact() {
        if (record.tag() != kind.tag())
            throw error("Expected a certificate of tag " + kind.tag(), span);
        record.checkArity(true);
        return this;
    }

    public CertKind kind() {
        return kind;
    }

    /**
     * @return the certificate as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the stake, DRep or cold committee credential
     */
    public RawCredential credential() {
        return RawCredential.of(record.value(FieldType.CREDENTIAL));
    }

    /**
     * @return the hot committee credential
     */
    public RawCredential hotCredential() {
        return RawCredential.of(record.value(FieldType.CREDENTIAL, 1));
    }

    /**
     * @return the pool key hash: of a delegation or a retirement, or the operator of a registration
     */
    public byte[] poolKeyHash() {
        return RawItems.bytes(record.value(FieldType.HASH28));
    }

    /**
     * @return the deposit, or the refund of an unregistration
     */
    public BigInteger coin() {
        return RawItems.coin(record.value(FieldType.COIN), lenient);
    }

    public RawDRep drep() {
        CborSpan drep = record.value(FieldType.DREP);
        return lenient ? RawDRep.lenient(drep) : RawDRep.of(drep);
    }

    /**
     * @return the anchor, or empty if it is null
     */
    public Optional<RawAnchor> anchor() {
        return Optional.ofNullable(RawItems.nullable(record.value(FieldType.OPTIONAL_ANCHOR), RawAnchor::of));
    }

    /**
     * @return the epoch of a pool retirement, its 64 bits: a value of 2^63 or more reads as negative
     */
    public long epoch() {
        return RawItems.unsignedLong(record.value(FieldType.UINT));
    }

    /**
     * @return the VRF key hash of a pool registration or a genesis key delegation
     */
    public byte[] vrfKeyHash() {
        return RawItems.bytes(record.value(FieldType.HASH32));
    }

    public BigInteger pledge() {
        return RawItems.coin(record.value(FieldType.COIN), lenient);
    }

    public BigInteger cost() {
        return RawItems.coin(record.value(FieldType.COIN, 1), lenient);
    }

    public UnitInterval margin() {
        return RawItems.rational(record.value(FieldType.UNIT_INTERVAL), lenient);
    }

    public byte[] rewardAccount() {
        return RawItems.bytes(record.value(FieldType.REWARD_ACCOUNT));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the BLS key of a pool registration read in Dijkstra, or empty if it is null or the registration has none
     * @throws CborRuntimeException if it is not an array of two byte strings
     */
    public Optional<RawBlsKey> blsKey() {
        return record.optional(FieldType.OPTIONAL_BLS_KEY).map(item -> RawItems.nullable(item, RawBlsKey::of));
    }

    /**
     * @return the owners' key hashes, in encoded order with any repeated hash kept
     */
    public List<byte[]> poolOwners() {
        List<byte[]> owners = new ArrayList<>();
        for (CborSpan owner : RawItems.array(record.value(FieldType.KEY_HASH_SET), FieldType.KEY_HASH_SET.name()))
            owners.add(RawItems.bytes(owner));
        return owners;
    }

    /**
     * @return the relays; each is read when it is asked for, so one that is not a relay fails only there
     */
    public List<RawRelay> relays() {
        List<CborSpan> relays = RawItems.array(record.value(FieldType.RELAY_LIST), FieldType.RELAY_LIST.name());
        boolean exact = !lenient;
        return new AbstractList<>() {
            @Override
            public RawRelay get(int index) {
                return RawRelay.read(relays.get(index), exact);
            }

            @Override
            public int size() {
                return relays.size();
            }
        };
    }

    /**
     * @return the pool metadata, or empty if it is null
     */
    public Optional<RawPoolMetadata> poolMetadata() {
        CborSpan metadata = record.value(FieldType.OPTIONAL_POOL_METADATA);
        return Optional.ofNullable(RawItems.nullable(metadata, item -> RawPoolMetadata.read(item, !lenient)));
    }

    public byte[] genesisHash() {
        return RawItems.bytes(record.value(FieldType.HASH28));
    }

    public byte[] genesisDelegateHash() {
        return RawItems.bytes(record.value(FieldType.HASH28, 1));
    }

    public RawMir moveInstantaneousReward() {
        return RawMir.read(record.value(FieldType.MIR), lenient);
    }
}
