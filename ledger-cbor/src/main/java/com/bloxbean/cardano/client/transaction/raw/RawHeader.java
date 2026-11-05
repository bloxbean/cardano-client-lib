package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;
import java.util.Optional;

/**
 * A block header as received, {@code [header_body, body_signature]}, in either {@link HeaderLayout} (ADR 0003 D4). The
 * arrays of the header and its body are checked when the view is created. Accessors read an item when they are called,
 * and throw {@link CborRuntimeException} if it is not what the table says. Tags on an item are looked through, arrays
 * may be indefinite, strings chunked and integers non-minimal. Hash and key lengths and ranges are not checked (D5).
 */
public final class RawHeader {
    /** The {@code header} rule; its body's table depends on the layout, see {@link #walk(CborSpan, FormatPolicy)}. */
    public static final RecordSpec HEADER = RecordSpec.positional("header", FieldType.HEADER_BODY,
            FieldType.KES_SIGNATURE);
    static final RecordSpec OPERATIONAL_CERT = RecordSpec.positional("operational_cert", FieldType.KES_VKEY,
            FieldType.SEQUENCE_NUMBER, FieldType.KES_PERIOD, FieldType.SIGNATURE);
    static final RecordSpec PROTOCOL_VERSION = RecordSpec.positional("protocol_version",
            FieldType.MAJOR_PROTOCOL_VERSION, FieldType.MINOR_PROTOCOL_VERSION);

    private final CborSpan span;
    private final PositionalRecord header;
    private final HeaderLayout layout;
    private final PositionalRecord body;

    private RawHeader(CborSpan span, PositionalRecord header, HeaderLayout layout) {
        this.span = span;
        this.header = header;
        this.layout = layout;
        this.body = PositionalRecord.of(layout.body, header.value(FieldType.HEADER_BODY), true);
    }

    /**
     * @param span a header as encoded
     * @param era  the era whose layout it has
     * @return the view
     * @throws CborRuntimeException if it does not have the items of that layout
     */
    public static RawHeader of(CborSpan span, Era era) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        return new RawHeader(span, PositionalRecord.of(HEADER, span, true), EraSpec.of(era).headerLayout);
    }

    /**
     * For a caller with no era, such as one holding only a header: the layout is the one with as many items as the
     * header body has.
     *
     * @param span a header as encoded
     * @return the view
     * @throws CborRuntimeException if it does not have the items of a layout
     */
    public static RawHeader of(CborSpan span) {
        PositionalRecord header = PositionalRecord.of(HEADER, span, true);
        return new RawHeader(span, header, HeaderLayout.ofShape(header.value(FieldType.HEADER_BODY)));
    }

    /**
     * Walks a header with a policy (ADR 0003 D6): {@link #HEADER}, then its body in the layout with as many items as it
     * has, for a caller with no era, as {@link #of(CborSpan)}. The policy is told the first era of that layout, which
     * belongs to every row of its tables.
     *
     * @param span   a header as encoded
     * @param policy the policy, which throws to reject
     * @throws CborRuntimeException if the policy accepts a value the walk cannot enter, or the body has the items of no
     *                              layout
     */
    public static void walk(CborSpan span, FormatPolicy policy) {
        List<CborSpan> items = RawItems.array(span, HEADER.name());
        if (items.isEmpty())
            throw RawTx.error("A header has a body", span);
        HeaderLayout layout = HeaderLayout.ofShape(items.get(0));
        Era era = EraSpec.firstWith(layout);
        HEADER.walk(span, era, policy);
        layout.body.walk(items.get(0), era, policy);
    }

    /**
     * @return the header as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the header body as encoded
     */
    public CborSpan bodySpan() {
        return header.value(FieldType.HEADER_BODY);
    }

    /**
     * @return the block hash: {@code blake2b256} of the header's original bytes, as the ledger keeps and hashes them
     * ({@code MemoBytes} in {@code Praos/BlockHeader.hs} and {@code TPraos/BlockHeader.hs} of {@code cardano-protocol})
     */
    public byte[] hash() {
        return Blake2bUtil.blake2bHash256(span.bytes());
    }

    /**
     * @return the block number, its 64 bits
     */
    public long blockNumber() {
        return number(body, FieldType.UINT, 0);
    }

    /**
     * @return the slot, its 64 bits
     */
    public long slot() {
        return number(body, FieldType.UINT, 1);
    }

    /**
     * @return the hash of the previous block, or empty if it is null: the first block after genesis
     */
    public Optional<byte[]> prevHash() {
        return Optional.ofNullable(RawItems.nullable(body.value(FieldType.OPTIONAL_HASH32), RawItems::bytes));
    }

    public byte[] issuerVkey() {
        return RawItems.bytes(body.value(FieldType.VKEY));
    }

    public byte[] vrfVkey() {
        return RawItems.bytes(body.value(FieldType.VRF_VKEY));
    }

    /**
     * @return the nonce VRF result of a header in the flat layout (to Alonzo), or else empty
     */
    public Optional<RawVrfCert> nonceVrf() {
        return layout == HeaderLayout.FLAT_15 ? Optional.of(vrf(0)) : Optional.empty();
    }

    /**
     * @return the leader VRF result of a header in the flat layout (to Alonzo), or else empty
     */
    public Optional<RawVrfCert> leaderVrf() {
        return layout == HeaderLayout.FLAT_15 ? Optional.of(vrf(1)) : Optional.empty();
    }

    /**
     * @return the one VRF result of a header in the nested layout (from Babbage), or else empty
     */
    public Optional<RawVrfCert> vrfResult() {
        return layout == HeaderLayout.NESTED_10 ? Optional.of(vrf(0)) : Optional.empty();
    }

    private RawVrfCert vrf(int nth) {
        return RawVrfCert.of(body.value(FieldType.VRF_CERT, nth));
    }

    /**
     * @return the block body size the header declares, its 64 bits
     */
    public long bodySize() {
        return number(body, FieldType.UINT32, 0);
    }

    /**
     * @return the block body hash the header declares, which {@link RawBlock#bodyHash()} computes
     */
    public byte[] bodyHash() {
        return RawItems.bytes(body.value(FieldType.HASH32));
    }

    /**
     * @return the operational certificate's hot KES key
     */
    public byte[] hotVkey() {
        return RawItems.bytes(body.group(FieldType.OPERATIONAL_CERT).value(FieldType.KES_VKEY));
    }

    /**
     * @return the operational certificate's sequence number, its 64 bits
     */
    public long sequenceNumber() {
        return number(body.group(FieldType.OPERATIONAL_CERT), FieldType.SEQUENCE_NUMBER, 0);
    }

    /**
     * @return the operational certificate's KES period, its 64 bits
     */
    public long kesPeriod() {
        return number(body.group(FieldType.OPERATIONAL_CERT), FieldType.KES_PERIOD, 0);
    }

    /**
     * @return the operational certificate's signature
     */
    public byte[] sigma() {
        return RawItems.bytes(body.group(FieldType.OPERATIONAL_CERT).value(FieldType.SIGNATURE));
    }

    /**
     * @return the protocol version's major part, its 64 bits
     */
    public long protocolMajor() {
        return number(body.group(FieldType.PROTOCOL_VERSION), FieldType.MAJOR_PROTOCOL_VERSION, 0);
    }

    /**
     * @return the protocol version's minor part, its 64 bits
     */
    public long protocolMinor() {
        return number(body.group(FieldType.PROTOCOL_VERSION), FieldType.MINOR_PROTOCOL_VERSION, 0);
    }

    /**
     * @return the KES signature of the header body
     */
    public byte[] bodySignature() {
        return RawItems.bytes(header.value(FieldType.KES_SIGNATURE));
    }

    private static long number(PositionalRecord record, FieldType type, int nth) {
        return RawItems.integer(record.value(type, nth), false).longValue();
    }
}
