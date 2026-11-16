package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bounded field types of certificates, proposals and votes decode as Haskell's decoders at version 9 and later:
 * {@code Url}/{@code DnsName} ≤ 128 bytes of UTF-8, 32-byte anchor hashes, {@code UnitInterval} margins, relay ports
 * and addresses (BaseTypes.hs:678-697, 996-1004; StakePool.hs:406-421; Plain.hs:159-167).
 */
class BoundedFieldsTest {

    private static final String INPUT = "825820" + "00".repeat(32) + "00";
    private static final String BODY = "a3" + "00" + "81" + INPUT + "01" + "81" + "82581d60" + "11".repeat(28)
            + "1a000f4240" + "02" + "1a00030d40";
    private static final String CRED = "8200581c" + "11".repeat(28);

    /** @return the transaction with {@code key → value} added to the body */
    private static byte[] tx(String key, String value) {
        return HexUtil.decodeHexString("84" + "a4" + BODY.substring(2) + key + value + "a0f5f6");
    }

    private static String text(int bytes) {
        return textOf("a".repeat(bytes));
    }

    private static String textOf(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        return header(3, utf8.length) + HexUtil.encodeHexString(utf8);
    }

    private static String header(int major, int length) {
        int base = major << 5;
        if (length < 24) {
            return String.format("%02x", base | length);
        }
        if (length < 256) {
            return String.format("%02x%02x", base | 24, length);
        }
        return String.format("%02x%04x", base | 25, length);
    }

    private static String anchor(String url, int hashBytes) {
        return "82" + url + header(2, hashBytes) + "ab".repeat(hashBytes);
    }

    /** A committee resignation (tag 15) carrying {@code anchor}. */
    private static byte[] resignation(String anchor) {
        return tx("04", "81" + "830f" + CRED + anchor);
    }

    private static byte[] pool(String margin, String relays, String metadata) {
        return tx("04", "81" + "8a03" + "581c" + "22".repeat(28) + "5820" + "33".repeat(32) + "00" + "00" + margin
                + "581de0" + "44".repeat(28) + "81581c" + "55".repeat(28) + relays + metadata);
    }

    private static final String MARGIN = "d81e820105";

    private static void decodes(byte[] tx) {
        HaskellPolicy.CONWAY.transaction(tx);
    }

    private static void fails(byte[] tx, String message) {
        assertThatThrownBy(() -> HaskellPolicy.CONWAY.transaction(tx)).isInstanceOf(LedgerDecodingException.class)
                .hasMessageContaining(message);
    }

    @Test
    void anchorUrlsAreAtMost128BytesOfUtf8AndHashesAre32Bytes() {
        decodes(resignation(anchor(text(128), 32)));
        decodes(resignation("f6"));
        fails(resignation(anchor(text(129), 32)), "exceeds 128 bytes");
        fails(resignation(anchor(text(10), 31)), "hash of 31 bytes");
        fails(resignation(anchor("62c328", 32)), "not valid UTF-8");
        fails(resignation("83" + text(4) + "5820" + "ab".repeat(32) + "00"), "unknown anchor item");
        // DRep registration and update carry anchors too.
        fails(tx("04", "81" + "8410" + CRED + "1a1dcd6500" + anchor(text(129), 32)), "exceeds 128 bytes");
        fails(tx("04", "81" + "8312" + CRED + anchor(text(10), 33)), "hash of 33 bytes");
    }

    @Test
    void poolMetadataUrlsRelaysAndMarginsHaveHaskellsBounds() {
        decodes(pool(MARGIN, "80", "f6"));
        decodes(pool(MARGIN, "80", "82" + text(128) + "5820" + "00".repeat(32)));
        fails(pool(MARGIN, "80", "82" + text(129) + "5820" + "00".repeat(32)), "url exceeds 128");

        // relays: [0, port, ipv4, ipv6], [1, port, dns], [2, dns]
        decodes(pool(MARGIN, "83" + "8400f6f6f6" + "8301190bb8" + text(128) + "8202" + text(5), "f6"));
        decodes(pool(MARGIN, "81" + "840019ffff44" + "7f000001" + "50" + "00".repeat(16), "f6"));
        fails(pool(MARGIN, "81" + "84001a00010000f6f6", "f6"), "port exceeds Word16");
        fails(pool(MARGIN, "81" + "8400f643" + "7f0000" + "f6", "f6"), "IPv4 address of 3 bytes");
        fails(pool(MARGIN, "81" + "8400f6f64f" + "00".repeat(15), "f6"), "IPv6 address of 15 bytes");
        fails(pool(MARGIN, "81" + "8202" + text(129), "f6"), "DNS name exceeds 128");
        fails(pool(MARGIN, "81" + "8103", "f6"), "unknown relay key 03");
        fails(pool(MARGIN, "81" + "8302" + text(3) + "00", "f6"), "unknown member 2 item");

        // margin: a tag-30 rational in [0, 1] with a non-zero denominator (n % d reduces and normalises the sign)
        decodes(pool("d81e820101", "80", "f6"));
        decodes(pool("d81e822021", "80", "f6"));
        fails(pool("d81e820605", "80", "f6"), "not in [0, 1]");
        fails(pool("d81e820100", "80", "f6"), "denominator cannot be zero");
        fails(pool("820105", "80", "f6"), "tag-30");
        fails(pool("d81e83010203", "80", "f6"), "rational has 3 elements");
    }

    @Test
    void proposalAndVoteAnchorsHaveTheSameBounds() {
        String account = "581de0" + "44".repeat(28);
        String info = "841a000f4240" + account + "8106";
        decodes(tx("14", "81" + info + anchor(text(128), 32)));
        fails(tx("14", "81" + info + anchor(text(129), 32)), "url exceeds 128");
        fails(tx("14", "81" + info + anchor(text(8), 31)), "hash of 31 bytes");
        // NewConstitution [5, prev, [anchor, script_hash / null]]
        String constitution = "841a000f4240" + account + "8305f6" + "82" + anchor(text(8), 31) + "f6";
        fails(tx("14", "81" + constitution + anchor(text(8), 32)), "hash of 31 bytes");

        String voter = "8200581c" + "77".repeat(28);
        String actionId = "825820" + "00".repeat(32) + "00";
        decodes(tx("13", "a1" + voter + "a1" + actionId + "8201f6"));
        decodes(tx("13", "a1" + voter + "a1" + actionId + "8201" + anchor(text(128), 32)));
        fails(tx("13", "a1" + voter + "a1" + actionId + "8201" + anchor(text(129), 32)), "url exceeds 128");
        fails(tx("13", "a1" + voter + "a1" + actionId + "8301f600"), "unknown voting_procedure item");
        decodes(tx("13", "a1" + voter + "a1" + actionId + "8200f6"));
    }
}
