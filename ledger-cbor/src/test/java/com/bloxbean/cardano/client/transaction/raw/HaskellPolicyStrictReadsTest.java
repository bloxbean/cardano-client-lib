package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * {@link HaskellPolicy}'s strict reads (ADR 0005 D4): every rejection is a {@link LedgerDecodingException} with a
 * stable reason (D10), the block read checks its header (D4), and every {@link FieldType} has a rule or is listed as
 * having none (J9).
 */
class HaskellPolicyStrictReadsTest {

    private static final HaskellPolicy P = HaskellPolicy.CONWAY;
    private static final String HASH28 = "581c" + "11".repeat(28);
    private static final String HASH32 = "5820" + "22".repeat(32);
    private static final String BODY = "a3" + "0081" + "82" + HASH32 + "00" + "0180" + "0200";
    private static final String TX_BODY = "a3008001800200";
    private static final String VKEY = "5820" + "33".repeat(32);

    /** A Conway header: a nested body of 10 items and a 448-byte KES signature. */
    private static final String HEADER_BODY = "8a" + "00" + "00" + "f6" + VKEY + VKEY
            + "82" + "5840" + "44".repeat(64) + "5850" + "55".repeat(80)
            + "00" + HASH32
            + "84" + VKEY + "00" + "00" + "5840" + "66".repeat(64)
            + "82" + "0a" + "00";
    private static final String HEADER = "82" + HEADER_BODY + "5901c0" + "77".repeat(448);

    private static byte[] hex(String hex) {
        return HexUtil.decodeHexString(hex);
    }

    private static void rejects(Reason reason, Runnable read) {
        assertThatThrownBy(read::run).isInstanceOf(LedgerDecodingException.class)
                .extracting(e -> ((LedgerDecodingException) e).reason()).isEqualTo(reason);
    }

    private static void rejectsTx(Reason reason, String tx) {
        rejects(reason, () -> P.transaction(hex(tx)));
    }

    private static void rejectsValue(Reason reason, FieldType type, String value) {
        rejects(reason, () -> P.walk(type, hex(value)));
    }

    @Test
    void aTransactionThatDecodesIsReturnedAsCclsView() {
        RawTx tx = P.transaction(hex("84" + BODY + "a0" + "f5" + "f6"));
        assertThat(tx.isValid()).isTrue();
        assertThat(tx.txId()).hasSize(32);
    }

    @Test
    void bytesThatAreNotOneWellFormedItem() {
        rejectsTx(Reason.NOT_WELL_FORMED, "");
        rejectsTx(Reason.NOT_WELL_FORMED, "84");
        rejectsTx(Reason.NOT_WELL_FORMED, "84" + BODY + "a0f5f6" + "00");
        rejectsTx(Reason.NOT_WELL_FORMED, "84" + BODY + "a0f5" + "ff");
        rejects(Reason.NOT_WELL_FORMED, () -> P.walk(FieldType.UINT, new byte[0]));
        rejects(Reason.NOT_WELL_FORMED, () -> HaskellPolicy.header(new byte[0]));
        rejects(Reason.NOT_WELL_FORMED, () -> P.block(hex("85")));
        rejects(Reason.NOT_WELL_FORMED, () -> P.script(hex("82")));
    }

    @Test
    void theReadersOwnChecksAreStructure() {
        rejectsTx(Reason.STRUCTURE, "80");
        rejectsTx(Reason.STRUCTURE, "a0");
        rejectsTx(Reason.STRUCTURE, "82" + BODY + "a0");
        rejectsTx(Reason.STRUCTURE, "85" + BODY + "a0f5f6f6");
    }

    @Test
    void eachRuleRejectsWithItsReason() {
        rejectsTx(Reason.UNKNOWN_FIELD, "84" + "a4" + "0081" + "82" + HASH32 + "00" + "0180" + "0200" + "0c00" + "a0f5f6");
        rejectsTx(Reason.FIELD_NOT_IN_ERA, "84" + "a4" + "0081" + "82" + HASH32 + "00" + "0180" + "0200" + "06a0"
                + "a0f5f6");
        rejectsTx(Reason.REPEATED_KEY, "84" + "a4" + "0081" + "82" + HASH32 + "00" + "0180" + "0200" + "0200"
                + "a0f5f6");
        rejectsTx(Reason.MISSING_FIELD, "84" + "a2" + "0081" + "82" + HASH32 + "00" + "0180" + "a0f5f6");
        rejectsTx(Reason.MAP_HEAD_WIDTH, "84" + "a3" + "0081" + "82" + HASH32 + "00"
                + "0181" + "bb0000000000000002" + "00" + "581d60" + "11".repeat(28) + "0100" + "0200" + "a0f5f6");

        rejectsValue(Reason.UNEXPECTED_TYPE, FieldType.UINT, "20");
        rejectsValue(Reason.UNEXPECTED_TYPE, FieldType.BOOL, "00");
        rejectsValue(Reason.INTEGER_RANGE, FieldType.UINT16, "1a00010000");
        rejectsValue(Reason.INTEGER_RANGE, FieldType.INT64, "1b8000000000000000");
        rejectsValue(Reason.BYTES_SIZE, FieldType.HASH28, "581b" + "11".repeat(27));
        rejectsValue(Reason.INDEFINITE_STRING, FieldType.HASH28, "5f" + "4111" + "581b" + "11".repeat(27) + "ff");
        rejectsValue(Reason.INDEFINITE_RECORD, FieldType.ANCHOR, "9f" + "60" + HASH32 + "ff");
        rejectsValue(Reason.ZERO, FieldType.POSITIVE_COIN, "00");
        rejectsValue(Reason.ZERO, FieldType.NONZERO_INT64, "00");
        rejectsValue(Reason.EMPTY_COLLECTION, FieldType.NONEMPTY_LIST, "80");
        rejectsValue(Reason.DUPLICATE_ITEM, FieldType.KEY_HASH_SET, "82" + HASH28 + HASH28);
        rejectsValue(Reason.TAG, FieldType.DATA, "4100");
        rejectsValue(Reason.ADDRESS, FieldType.ADDRESS, "40");
        rejectsValue(Reason.ADDRESS, FieldType.REWARD_ACCOUNT, "581d" + "e2" + "11".repeat(28));
        rejectsValue(Reason.TEXT_BOUNDS, FieldType.URL, "7881" + "61".repeat(129));
        rejectsValue(Reason.RATIONAL, FieldType.UNIT_INTERVAL, "d81e820302");
        rejectsValue(Reason.RATIONAL, FieldType.UNIT_INTERVAL, "d81e820100");
        rejectsValue(Reason.PLUTUS_DATA, FieldType.PLUTUS_DATA, "60");
        rejectsValue(Reason.METADATA, FieldType.METADATA, "a101f5");
        rejectsValue(Reason.REPEATED_KEY, FieldType.METADATA, "a201000100");
        rejectsValue(Reason.SCRIPT, FieldType.NATIVE_SCRIPT, "8207" + HASH28);
    }

    @Test
    void aMultiAssetTooBigToCompact() {
        // 1489 assets under one policy: 44 * 1489 + 28 = 65544 > 65535; 1488 fit (65500)
        StringBuilder assets = new StringBuilder("b905d1");
        for (int i = 0; i < 1489; i++) {
            assets.append("42").append(String.format("%04x", i)).append("01");
        }
        rejectsValue(Reason.MULTI_ASSET_TOO_BIG, FieldType.VALUE, "8200" + "a1" + HASH28 + assets);
        StringBuilder fewer = new StringBuilder("b905d0");
        for (int i = 0; i < 1488; i++) {
            fewer.append("42").append(String.format("%04x", i)).append("01");
        }
        assertThatCode(() -> P.walk(FieldType.VALUE, hex("8200" + "a1" + HASH28 + fewer))).doesNotThrowAnyException();
    }

    @Test
    void aBlockIsReadWithItsHeaderAndEachTransaction() {
        assertThatCode(() -> HaskellPolicy.header(hex(HEADER))).doesNotThrowAnyException();
        RawBlock empty = P.block(hex("85" + HEADER + "80" + "80" + "a0" + "80"));
        assertThat(empty.txCount()).isZero();
        RawBlock one = P.block(hex("85" + HEADER + "81" + TX_BODY + "81" + "a0" + "a0" + "80"));
        assertThat(one.txCount()).isOne();

        // ADR 0005 D4: RawBlock alone keeps the header as a span, so a header of 0 passed the old harness
        assertThatCode(() -> RawBlock.of(hex("850080" + "80a080"), Era.Conway)).doesNotThrowAnyException();
        assertThatThrownBy(() -> P.block(hex("85" + "00" + "80" + "80" + "a0" + "80")))
                .isInstanceOf(LedgerDecodingException.class);
        // a malformed header, and one of another era's layout (Babbage and before: a flat body of 15 items)
        String flat = "82" + "8f" + "00" + "00" + "f6" + VKEY + VKEY + "82" + "5840" + "44".repeat(64)
                + "5850" + "55".repeat(80) + "82" + "5840" + "44".repeat(64) + "5850" + "55".repeat(80)
                + "00" + HASH32 + VKEY + "00" + "00" + "5840" + "66".repeat(64) + "08" + "00"
                + "5901c0" + "77".repeat(448);
        assertThatThrownBy(() -> P.block(hex("85" + flat + "80" + "80" + "a0" + "80")))
                .isInstanceOf(LedgerDecodingException.class);
        String shortKes = "82" + HEADER_BODY + "5901bf" + "77".repeat(447);
        rejects(Reason.BYTES_SIZE, () -> P.block(hex("85" + shortKes + "80" + "80" + "a0" + "80")));
        // a transaction of the block that Haskell would not decode
        rejects(Reason.UNKNOWN_FIELD, () -> P.block(hex("85" + HEADER + "81" + "a4008001800200" + "0c00" + "81"
                + "a0" + "a0" + "80")));
        // a bare block only: no [era, block] envelope, no tag
        rejects(Reason.UNEXPECTED_TYPE, () -> P.block(hex("8207" + "85" + HEADER + "80" + "80" + "a0" + "80")));
        rejects(Reason.UNEXPECTED_TYPE, () -> P.block(hex("d818" + "59" + String.format("%04x",
                hex("85" + HEADER + "80" + "80" + "a0" + "80").length) + "85" + HEADER + "80" + "80" + "a0" + "80")));
    }

    /**
     * Codex C1: two items of a set are compared as decoded (CborCanonical), and a rational that is not a pair of
     * integers was read a second time, so each nested tag 30 doubled the work: 26 of them in a 161-byte transaction
     * took seconds to reject. Inherited from Yano; the outcome is unchanged, the time linear.
     */
    @Test
    void nestedRationalsInASetItemAreComparedInLinearTime() {
        String nested = "d81e81".repeat(26) + "00";
        String tx = "84" + "a3" + "0082" + "82" + HASH32 + nested + "82" + "5820" + "11".repeat(32) + "00"
                + "0180" + "0200" + "a0f5f6";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> P.transaction(hex(tx))).isInstanceOf(LedgerDecodingException.class));
        String deeper = "84" + "a3" + "0082" + "82" + HASH32 + "d81e81".repeat(60) + "00" + "82" + "5820"
                + "11".repeat(32) + "00" + "0180" + "0200" + "a0f5f6";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> P.transaction(hex(deeper))).isInstanceOf(LedgerDecodingException.class));
    }

    /** Workflow review W10: the equality key of a set item refuses an item nested deeper than any Conway value. */
    @Test
    void aSetItemTooDeepToCompare() {
        String deep = "81".repeat(70) + "00";
        rejectsTx(Reason.NESTING_TOO_DEEP, "84" + "a3" + "0082" + "82" + HASH32 + deep + "82" + "5820"
                + "11".repeat(32) + "00" + "0180" + "0200" + "a0f5f6");
    }

    /** Workflow review W17: a Byron address with large attributes is checked in linear time, not Base58-encoded. */
    @Test
    void aLargeByronAddressIsCheckedInLinearTime() {
        byte[] address = byronAddressWithAttribute(65_536);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            P.walk(FieldType.ADDRESS, hex("5a" + String.format("%08x", address.length)
                    + HexUtil.encodeHexString(address)));
        });
        byte[] corrupted = address.clone();
        corrupted[corrupted.length - 1] ^= 1;
        rejects(Reason.ADDRESS, () -> P.walk(FieldType.ADDRESS, hex("5a" + String.format("%08x", corrupted.length)
                + HexUtil.encodeHexString(corrupted))));
    }

    /** {@code [24(bytes [root, {3: bytes}, 0]), crc32]}: a Byron address whose attribute 3 has {@code size} bytes. */
    private static byte[] byronAddressWithAttribute(int size) {
        java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
        payload.writeBytes(hex("83" + "581c" + "00".repeat(28) + "a103"));
        com.bloxbean.cardano.client.common.cbor.CborHead.write(payload, 2, size);
        payload.writeBytes(new byte[size]);
        payload.writeBytes(hex("00"));
        byte[] body = payload.toByteArray();
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(body);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(hex("82d818"));
        com.bloxbean.cardano.client.common.cbor.CborHead.write(out, 2, body.length);
        out.writeBytes(body);
        out.writeBytes(hex("1a" + String.format("%08x", crc.getValue())));
        return out.toByteArray();
    }

    /** Workflow review W18: a rational of huge bignums is judged in linear time, with a short message. */
    @Test
    void aRationalOfHugeBignumsIsJudgedInLinearTime() {
        BigInteger a = BigInteger.ZERO;
        BigInteger b = BigInteger.ONE;
        while (b.bitLength() < 1 << 20) {
            BigInteger next = a.add(b);
            a = b;
            b = next;
        }
        String value = "d81e82" + bignum(a) + bignum(b);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThatThrownBy(() -> P.walk(FieldType.UNIT_INTERVAL, hex(value)))
                    .isInstanceOf(LedgerDecodingException.class)
                    .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(300));
        });
        // 2^65 / 2^66 reduces into range, as the dataset's bignum samples do
        assertThatCode(() -> P.walk(FieldType.UNIT_INTERVAL, hex("d81e82c249020000000000000000c249040000000000000000")))
                .doesNotThrowAnyException();
    }

    private static String bignum(BigInteger value) {
        byte[] bytes = value.toByteArray();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(0xc2);
        com.bloxbean.cardano.client.common.cbor.CborHead.write(out, 2, bytes.length);
        out.writeBytes(bytes);
        return HexUtil.encodeHexString(out.toByteArray());
    }

    /** The bounded reduction gives the gcd reduction's verdict and value, over random and edge parts. */
    @Test
    void theBoundedReductionIsTheGcdReduction() {
        java.util.Random random = new java.util.Random(0x5eed_0005L);
        BigInteger word64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        for (int i = 0; i < 20_000; i++) {
            BigInteger n = randomInteger(random);
            BigInteger d = randomInteger(random);
            if (d.signum() == 0) {
                continue;
            }
            BigInteger g = n.gcd(d);
            BigInteger num = n.divide(g);
            BigInteger den = d.divide(g);
            if (den.signum() < 0) {
                num = num.negate();
                den = den.negate();
            }
            boolean inRange = num.signum() >= 0 && num.compareTo(word64) <= 0 && den.compareTo(word64) <= 0;
            BigInteger[] reduced = BoundedFields.reducedWithinWord64(n, d);
            if (inRange) {
                assertThat(reduced).as(n + "/" + d).containsExactly(num, den);
            } else {
                assertThat(reduced).as(n + "/" + d).isNull();
            }
        }
    }

    private static BigInteger randomInteger(java.util.Random random) {
        int bits = new int[]{3, 8, 63, 64, 65, 130}[random.nextInt(6)];
        BigInteger value = new BigInteger(bits, random).multiply(BigInteger.valueOf(1 + random.nextInt(4)));
        if (random.nextInt(8) == 0) {
            value = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(random.nextInt(3)));
        }
        return random.nextInt(5) == 0 ? value.negate() : value;
    }

    @Test
    void theRulesAreConwaysOnly() {
        assertThatThrownBy(() -> P.check(FieldType.UINT, CborSpan.of(hex("00")), Era.Babbage))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RawTx.walk(hex("84" + BODY + "a0f5f6"), Era.Babbage, P))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyFieldTypeHasARuleOrIsListedAsHavingNone() {
        Set<FieldType> listed = EnumSet.noneOf(FieldType.class);
        for (FieldType type : FieldType.values()) {
            try {
                P.check(type, CborSpan.of(hex("00")), Era.Conway);
            } catch (IllegalStateException e) {
                throw new AssertionError(type + " has no rule and is not listed: " + e.getMessage(), e);
            } catch (CborRuntimeException e) {
                // a rule that rejects 0
            }
            if (HaskellPolicy.NO_RULE.contains(type)) {
                listed.add(type);
            }
        }
        assertThat(listed).isEqualTo(HaskellPolicy.NO_RULE);
        // a listed type has no rule: no input is refused by check alone
        for (FieldType type : HaskellPolicy.NO_RULE) {
            for (String value : new String[]{"00", "20", "40", "60", "80", "a0", "c100", "f5", "f6", "9fff"}) {
                assertThatCode(() -> P.check(type, CborSpan.of(hex(value)), Era.Conway)).as(type + " " + value)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void onlyConwayHasRules() {
        assertThat(HaskellPolicy.forEra(Era.Conway)).isSameAs(P);
        for (Era era : Era.values()) {
            if (era != Era.Conway) {
                assertThatThrownBy(() -> HaskellPolicy.forEra(era)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
