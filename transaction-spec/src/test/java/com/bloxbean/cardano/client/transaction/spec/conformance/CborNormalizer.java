package com.bloxbean.cardano.client.transaction.spec.conformance;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/**
 * The cardano-cbor-dataset normalization (README, "Normalization specification"), applied recursively to every item
 * of a re-encoding before it is compared with a sample's {@code .expected.cbor}: indefinite arrays, maps and chunked
 * strings become definite, every head is minimal, a bignum (tags 2 and 3) whose value fits a native head becomes that
 * head and otherwise loses leading zero bytes, and a rational (tag 30) is reduced. Map order, duplicate keys, other
 * tags and float widths are kept.
 */
final class CborNormalizer {

    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    private final byte[] in;
    private int pos;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private CborNormalizer(byte[] in) {
        this.in = in;
    }

    /** @return the normalized form of the single CBOR item {@code cbor} */
    static byte[] normalize(byte[] cbor) {
        CborNormalizer normalizer = new CborNormalizer(cbor);
        normalizer.item();
        if (normalizer.pos != cbor.length) {
            throw new IllegalArgumentException("trailing bytes after the CBOR item");
        }
        return normalizer.out.toByteArray();
    }

    private void item() {
        int initial = next();
        int major = initial >>> 5;
        int info = initial & 0x1f;
        switch (major) {
            case 0, 1 -> head(major, argument(info));
            case 2, 3 -> string(major, info);
            case 4 -> {
                if (info == 31) {
                    ByteArrayOutputStream saved = swapOut();
                    long count = 0;
                    while (!isBreak()) {
                        item();
                        count++;
                    }
                    pos++;
                    flush(saved, 4, count);
                } else {
                    BigInteger count = argument(info);
                    head(4, count);
                    for (long i = 0; i < count.longValueExact(); i++) {
                        item();
                    }
                }
            }
            case 5 -> {
                if (info == 31) {
                    ByteArrayOutputStream saved = swapOut();
                    long count = 0;
                    while (!isBreak()) {
                        item();
                        item();
                        count++;
                    }
                    pos++;
                    flush(saved, 5, count);
                } else {
                    BigInteger count = argument(info);
                    head(5, count);
                    for (long i = 0; i < count.longValueExact(); i++) {
                        item();
                        item();
                    }
                }
            }
            case 6 -> tag(argument(info));
            default -> simple(info);
        }
    }

    private void tag(BigInteger tag) {
        if (tag.equals(BigInteger.TWO) || tag.equals(BigInteger.valueOf(3))) {
            byte[] magnitude = normalizedChild();
            if ((magnitude[0] & 0xff) >>> 5 == 2) {
                BigInteger value = new BigInteger(1, stringContent(magnitude));
                if (value.compareTo(TWO_64) < 0) {
                    head(tag.intValue() == 2 ? 0 : 1, value);
                } else {
                    head(6, tag);
                    byte[] stripped = value.toByteArray();
                    int skip = stripped[0] == 0 ? 1 : 0;
                    head(2, BigInteger.valueOf(stripped.length - skip));
                    out.write(stripped, skip, stripped.length - skip);
                }
                return;
            }
            head(6, tag);
            out.writeBytes(magnitude);
            return;
        }
        if (tag.equals(BigInteger.valueOf(30))) {
            byte[] pair = normalizedChild();
            head(6, tag);
            out.writeBytes(reducedRational(pair));
            return;
        }
        head(6, tag);
        item();
    }

    /** A {@code [numerator, denominator]} pair of integers, reduced; anything else is kept as it is. */
    private static byte[] reducedRational(byte[] pair) {
        CborNormalizer reader = new CborNormalizer(pair);
        if (reader.next() != 0x82) {
            return pair;
        }
        BigInteger numerator = reader.integer();
        BigInteger denominator = reader.integer();
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return pair;
        }
        BigInteger gcd = numerator.gcd(denominator);
        CborNormalizer writer = new CborNormalizer(new byte[0]);
        writer.head(4, BigInteger.TWO);
        writer.integer(numerator.divide(gcd));
        writer.integer(denominator.divide(gcd));
        return writer.out.toByteArray();
    }

    /** Reads a normalized integer (native head or bignum), or null for anything else. */
    private BigInteger integer() {
        int initial = next();
        int major = initial >>> 5;
        if (major == 0) {
            return argument(initial & 0x1f);
        }
        if (major == 1) {
            return argument(initial & 0x1f).add(BigInteger.ONE).negate();
        }
        if (initial == 0xc2 || initial == 0xc3) {
            int lengthByte = next();
            BigInteger magnitude = new BigInteger(1, read(argument(lengthByte & 0x1f).intValueExact()));
            return initial == 0xc2 ? magnitude : magnitude.add(BigInteger.ONE).negate();
        }
        return null;
    }

    private void integer(BigInteger value) {
        if (value.signum() >= 0) {
            if (value.compareTo(TWO_64) < 0) {
                head(0, value);
            } else {
                bignum(2, value);
            }
        } else {
            BigInteger argument = value.negate().subtract(BigInteger.ONE);
            if (argument.compareTo(TWO_64) < 0) {
                head(1, argument);
            } else {
                bignum(3, argument);
            }
        }
    }

    private void bignum(int tag, BigInteger magnitude) {
        head(6, BigInteger.valueOf(tag));
        byte[] bytes = magnitude.toByteArray();
        int skip = bytes[0] == 0 ? 1 : 0;
        head(2, BigInteger.valueOf(bytes.length - skip));
        out.write(bytes, skip, bytes.length - skip);
    }

    private void string(int major, int info) {
        if (info != 31) {
            byte[] content = read(argument(info).intValueExact());
            head(major, BigInteger.valueOf(content.length));
            out.writeBytes(content);
            return;
        }
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        while (!isBreak()) {
            int chunk = next();
            if (chunk >>> 5 != major || (chunk & 0x1f) == 31) {
                throw new IllegalArgumentException("a chunk of an indefinite string must be a definite string");
            }
            joined.writeBytes(read(argument(chunk & 0x1f).intValueExact()));
        }
        pos++;
        head(major, BigInteger.valueOf(joined.size()));
        out.writeBytes(joined.toByteArray());
    }

    private void simple(int info) {
        int width = switch (info) {
            case 24 -> 1;
            case 25 -> 2;
            case 26 -> 4;
            case 27 -> 8;
            default -> {
                if (info > 27) {
                    throw new IllegalArgumentException("reserved additional information " + info);
                }
                yield 0;
            }
        };
        out.write(0xe0 | info);
        out.writeBytes(read(width));
    }

    /** Writes a head with the shortest encoding of {@code argument}. */
    private void head(int major, BigInteger argument) {
        int type = major << 5;
        long value = argument.longValue();
        if (argument.compareTo(BigInteger.valueOf(24)) < 0) {
            out.write(type | (int) value);
        } else if (argument.compareTo(BigInteger.valueOf(0x100)) < 0) {
            out.write(type | 24);
            out.write((int) value);
        } else if (argument.compareTo(BigInteger.valueOf(0x10000)) < 0) {
            out.write(type | 25);
            writeBigEndian(value, 2);
        } else if (argument.compareTo(BigInteger.ONE.shiftLeft(32)) < 0) {
            out.write(type | 26);
            writeBigEndian(value, 4);
        } else {
            out.write(type | 27);
            writeBigEndian(value, 8);
        }
    }

    private void writeBigEndian(long value, int width) {
        for (int shift = (width - 1) * 8; shift >= 0; shift -= 8) {
            out.write((int) (value >>> shift) & 0xff);
        }
    }

    private BigInteger argument(int info) {
        if (info < 24) {
            return BigInteger.valueOf(info);
        }
        int width = switch (info) {
            case 24 -> 1;
            case 25 -> 2;
            case 26 -> 4;
            case 27 -> 8;
            default -> throw new IllegalArgumentException("no argument for additional information " + info);
        };
        return new BigInteger(1, read(width));
    }

    /** @return the normalized form of the next item, without writing it */
    private byte[] normalizedChild() {
        ByteArrayOutputStream saved = swapOut();
        item();
        byte[] child = out.toByteArray();
        restore(saved);
        return child;
    }

    /** @return the content of a normalized definite string item */
    private static byte[] stringContent(byte[] item) {
        CborNormalizer reader = new CborNormalizer(item);
        int initial = reader.next();
        return reader.read(reader.argument(initial & 0x1f).intValueExact());
    }

    private ByteArrayOutputStream swapOut() {
        ByteArrayOutputStream saved = new ByteArrayOutputStream();
        saved.writeBytes(out.toByteArray());
        out.reset();
        return saved;
    }

    private void restore(ByteArrayOutputStream saved) {
        out.reset();
        out.writeBytes(saved.toByteArray());
    }

    /** Writes the definite head and the items collected since {@link #swapOut()}. */
    private void flush(ByteArrayOutputStream saved, int major, long count) {
        byte[] items = out.toByteArray();
        restore(saved);
        head(major, BigInteger.valueOf(count));
        out.writeBytes(items);
    }

    private boolean isBreak() {
        if (pos >= in.length) {
            throw new IllegalArgumentException("unterminated indefinite item");
        }
        return (in[pos] & 0xff) == 0xff;
    }

    private int next() {
        if (pos >= in.length) {
            throw new IllegalArgumentException("unexpected end of CBOR input");
        }
        return in[pos++] & 0xff;
    }

    private byte[] read(int length) {
        if (length < 0 || pos + length > in.length) {
            throw new IllegalArgumentException("unexpected end of CBOR input");
        }
        byte[] bytes = new byte[length];
        System.arraycopy(in, pos, bytes, 0, length);
        pos += length;
        return bytes;
    }
}
