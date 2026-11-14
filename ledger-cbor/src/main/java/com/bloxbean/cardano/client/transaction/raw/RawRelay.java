package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A pool relay as received: {@code [0, port / nil, ipv4 / nil, ipv6 / nil]}, {@code [1, port / nil, dns_name]} or
 * {@code [2, dns_name]}. Item lengths and ranges are not checked: they are policy (ADR 0003 D5).
 */
public final class RawRelay {
    /** The members of the {@code relay} rule. */
    public enum Kind {
        SINGLE_HOST_ADDR(0, FieldType.OPTIONAL_PORT, FieldType.OPTIONAL_IPV4, FieldType.OPTIONAL_IPV6),
        SINGLE_HOST_NAME(1, FieldType.OPTIONAL_PORT, FieldType.DNS_NAME),
        MULTI_HOST_NAME(2, FieldType.DNS_NAME);

        private final FieldSpec member;

        Kind(int tag, FieldType... items) {
            this.member = FieldSpec.member(tag, Era.Shelley, null, items);
        }

        private static Kind of(int tag) {
            for (Kind kind : values()) {
                if (kind.member.key() == tag)
                    return kind;
            }
            return null;
        }
    }

    static final RecordSpec RECORD;

    static {
        List<FieldSpec> members = new ArrayList<>();
        for (Kind kind : Kind.values())
            members.add(kind.member);
        RECORD = RecordSpec.tagged("relay", members);
    }

    private final Kind kind;
    private final PositionalRecord record;

    private RawRelay(Kind kind, PositionalRecord record) {
        this.kind = kind;
        this.record = record;
    }

    /**
     * @param span a relay as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a known tag and the items of that tag
     */
    public static RawRelay of(CborSpan span) {
        return read(span, true);
    }

    static RawRelay read(CborSpan span, boolean exact) {
        PositionalRecord record = PositionalRecord.member(RECORD, span, exact);
        return new RawRelay(Kind.of(record.tag()), record);
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return the port, or empty if it is null
     */
    public Optional<Integer> port() {
        return optional(record.value(FieldType.OPTIONAL_PORT), RawItems::smallUnsigned);
    }

    /**
     * @return the IPv4 address bytes, or empty if they are null
     */
    public Optional<byte[]> ipv4() {
        return optional(record.value(FieldType.OPTIONAL_IPV4), RawItems::bytes);
    }

    /**
     * @return the IPv6 address bytes, or empty if they are null
     */
    public Optional<byte[]> ipv6() {
        return optional(record.value(FieldType.OPTIONAL_IPV6), RawItems::bytes);
    }

    /**
     * @return the DNS name, or empty if it is encoded as null, which the rule does not allow and the reader passes on
     */
    public Optional<String> dnsName() {
        return optional(record.value(FieldType.DNS_NAME), RawItems::text);
    }

    private static <T> Optional<T> optional(CborSpan item, Function<CborSpan, T> read) {
        return Optional.ofNullable(RawItems.nullable(item, read));
    }
}
