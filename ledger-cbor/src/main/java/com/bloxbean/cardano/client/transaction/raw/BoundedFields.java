package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decoders for the bounded Conway field types that {@link HaskellPolicy} checks, with the bounds
 * Haskell's decoders enforce at decoder version 9 and later (cardano-ledger {@code f649f975}); anything else is a
 * {@link LedgerDecodingException}.
 *
 * <ul>
 *   <li>{@code Url} and {@code DnsName}: UTF-8 text (each chunk valid, {@code decodeString}) of at most 128 bytes
 *       ({@code textDecCBOR 128}, BaseTypes.hs:678-697).</li>
 *   <li>{@code UnitInterval}: tag 30, two integers (bignums tagged 2/3 too), a non-zero denominator, the reduced
 *       ratio in [0, 1] with numerator and denominator in {@code Word64} ({@code decodeRationalWithTag}, Plain.hs:159-167;
 *       {@code boundRational}, BaseTypes.hs:386-402).</li>
 * </ul>
 */
final class BoundedFields {

    /** {@code textDecCBOR 128} at decoder version 9 and later. */
    static final int MAX_TEXT_BYTES = 128;
    private static final BigInteger WORD64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private BoundedFields() {
    }

    /** Reads a {@code Url}. */
    static void url(CborSpan item, String what) {
        text(item, what + " url");
    }

    /** Reads a {@code DnsName}. */
    static void dnsName(CborSpan item) {
        text(item, "relay DNS name");
    }

    private static void text(CborSpan item, String what) {
        byte[] text = StrictCbor.definiteText(item);
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(text));
        } catch (CharacterCodingException e) {
            throw new LedgerDecodingException(Reason.TEXT_BOUNDS, what + " is not valid UTF-8");
        }
        int length = text.length;
        if (length > MAX_TEXT_BYTES) {
            throw new LedgerDecodingException(Reason.TEXT_BOUNDS,
                    what + " exceeds " + MAX_TEXT_BYTES + " bytes: " + length);
        }
    }

    /**
     * Reads a {@code UnitInterval} ({@code unit}) or a {@code NonNegativeInterval}: tag 30, two integers, a non-zero
     * denominator, the reduced ratio in [0, 1] or [0, 2^64 - 1] with numerator and denominator in {@code Word64}
     * ({@code decodeRationalWithTag}, Plain.hs:159-167; {@code fromRationalBoundedRatio}, BaseTypes.hs:346-376).
     *
     * @return the reduced numerator and denominator
     */
    static BigInteger[] boundedRational(CborSpan item, String what, boolean unit) {
        if (item.tag() != 30) {
            throw new LedgerDecodingException(Reason.RATIONAL, what + " is not a tag-30 rational");
        }
        List<CborSpan> parts = StrictCbor.array(item.untag());
        if (parts.size() != 2) {
            throw new LedgerDecodingException(Reason.RATIONAL, what + " rational has " + parts.size() + " elements");
        }
        BigInteger n = integer(parts.get(0));
        BigInteger d = integer(parts.get(1));
        if (d.signum() == 0) {
            throw new LedgerDecodingException(Reason.RATIONAL, what + ": denominator cannot be zero");
        }
        // n % d: reduced, denominator positive, then in range
        BigInteger[] reduced = reducedWithinWord64(n, d);
        boolean outOfRange = reduced == null || (unit && reduced[0].compareTo(reduced[1]) > 0);
        if (outOfRange) {
            throw new LedgerDecodingException(Reason.RATIONAL, what + " " + shown(n) + "/" + shown(d)
                    + (unit ? " is not in [0, 1]" : " is not a non-negative Word64 ratio"));
        }
        BigInteger numerator = reduced[0];
        BigInteger denominator = reduced[1];
        return new BigInteger[]{numerator, denominator};
    }

    /**
     * {@code n % d} when both its parts are non-negative and fit {@code Word64}, as Haskell's
     * {@code boundRational} keeps it; null otherwise. The same verdict as reducing by the gcd and comparing, without
     * a gcd of unbounded numbers, whose cost is quadratic in their length: the reduced fraction is the last convergent
     * of the continued fraction of {@code |n| / |d|}, and convergents' numerators and denominators never decrease, so
     * the reduction stops, out of range, at the first convergent beyond {@code 2^64 - 1}. That is at most about 93
     * steps (convergents grow at least as Fibonacci numbers), each one division whose quotient is bounded first, so
     * that no step divides numbers whose quotient is itself huge (Codex review D1).
     *
     * @param d not zero
     * @return {numerator, denominator}, reduced, or null when the ratio is negative or a part exceeds {@code Word64}
     */
    static BigInteger[] reducedWithinWord64(BigInteger n, BigInteger d) {
        if (n.signum() == 0) {
            return new BigInteger[]{BigInteger.ZERO, BigInteger.ONE};
        }
        if (n.signum() != d.signum()) {
            return null;
        }
        BigInteger a = n.abs();
        BigInteger b = d.abs();
        BigInteger h1 = BigInteger.ONE;
        BigInteger h2 = BigInteger.ZERO;
        BigInteger k1 = BigInteger.ZERO;
        BigInteger k2 = BigInteger.ONE;
        while (true) {
            // the largest quotient that keeps the next convergent within Word64; a larger one rejects before any
            // division of numbers whose quotient is itself unbounded
            BigInteger maxQuotient = WORD64_MAX;
            if (h1.signum() > 0) {
                maxQuotient = maxQuotient.min(WORD64_MAX.subtract(h2).divide(h1));
            }
            if (k1.signum() > 0) {
                maxQuotient = maxQuotient.min(WORD64_MAX.subtract(k2).divide(k1));
            }
            if (a.compareTo(b.multiply(maxQuotient.add(BigInteger.ONE))) >= 0) {
                return null;
            }
            BigInteger[] qr = a.divideAndRemainder(b);
            BigInteger h = qr[0].multiply(h1).add(h2);
            BigInteger k = qr[0].multiply(k1).add(k2);
            if (h.compareTo(WORD64_MAX) > 0 || k.compareTo(WORD64_MAX) > 0) {
                return null;
            }
            if (qr[1].signum() == 0) {
                return new BigInteger[]{h, k};
            }
            h2 = h1;
            h1 = h;
            k2 = k1;
            k1 = k;
            a = b;
            b = qr[1];
        }
    }

    /** A number for a message: in full up to 128 bits, else by its size, as a bignum may be megabytes long. */
    private static String shown(BigInteger value) {
        return value.bitLength() <= 128 ? value.toString() : "(a " + value.bitLength() + "-bit integer)";
    }

    /**
     * cborg's {@code decodeInteger}: a major-0/1 integer or a bignum, tag 2 (non-negative) or 3 (negative) with the
     * one-byte tag head {@code c2}/{@code c3} and a definite byte string (a longer tag head is a plain tag, which
     * {@code decodeInteger} refuses).
     */
    static BigInteger integer(CborSpan item) {
        int initial = item.buffer()[item.offset()] & 0xff;
        if (initial == 0xc2 || initial == 0xc3) {
            BigInteger magnitude = new BigInteger(1, StrictCbor.definiteBytes(item.untag()));
            return initial == 0xc2 ? magnitude : magnitude.negate().subtract(BigInteger.ONE);
        }
        return StrictCbor.integer(item);
    }
}
