package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Base58;
import com.bloxbean.cardano.client.crypto.exception.AddressFormatException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ByronAddressTest {
    private static final String ROOT = "9fb5418b25eb98db1f8e80bc697bbe0e2a9a2ffa87120d6fdcaa8ce2";
    private static final String ROOT_ITEM = "581c" + ROOT;
    private static final String PAYLOAD = "83" + ROOT_ITEM + "a0" + "00";

    @Test
    void mainnetDaedalusAddress() {
        ByronAddress address = new ByronAddress("DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK");

        assertThat(HexUtil.encodeHexString(address.getRoot())).isEqualTo(ROOT);
        assertThat(address.getByronType()).isEqualTo(ByronAddressType.VerKey);
        assertThat(address.getProtocolMagic()).isEmpty();
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getDerivationPath()).hasValueSatisfying(path ->
                assertThat(HexUtil.encodeHexString(path)).isEqualTo("04d160b3bcb17ef13564efebbc15364c59b7b17c87c86d19b3d0881a"));
    }

    @Test
    void mainnetRedeemAddress() {
        ByronAddress address = new ByronAddress("Ae2tdPwUPEZ3MHKkpT5Bpj549vrRH7nBqYjNXnCV8G2Bc2YxNcGHEa8ykDp");

        assertThat(HexUtil.encodeHexString(address.getRoot())).isEqualTo("41941d3c8d3a90fe493505094e388fa802cd5d043aadd972066b6444");
        assertThat(address.getByronType()).isEqualTo(ByronAddressType.Redeem);
        assertThat(address.getProtocolMagic()).isEmpty();
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getDerivationPath()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testnetAddresses")
    void testnetAddress(String name, String base58, String root, long protocolMagic, Network network) {
        ByronAddress address = new ByronAddress(base58);

        assertThat(HexUtil.encodeHexString(address.getRoot())).isEqualTo(root);
        assertThat(address.getByronType()).isEqualTo(ByronAddressType.VerKey);
        assertThat(address.getProtocolMagic()).hasValue(protocolMagic);
        assertThat(address.getNetwork()).isEqualTo(network);
        assertThat(address.getDerivationPath()).isEmpty();
    }

    static Stream<Arguments> testnetAddresses() {
        return Stream.of(
                arguments("legacy testnet", "2cWKMJemoBaipzQe9BArYdo2iPUfJQdZAjm4iCzDA1AfNxJSTgm9FZQTmFCYhKkeYrede",
                        "65d6bdf13c6bf6da3b7d3df5b6caf6bb35f488fcd093b81de482df87", 1097911063L, Networks.testnet()),
                //From preprod Byron genesis
                arguments("preprod", "FHnt4NL7yPXhCzCHVywZLqVsvwuG3HvwmjKXQJBrXh3h2aigv6uxkePbpzRNV8q",
                        "056d8907b4530dabec0ab77456a2b5c7e695150d7534380a8093091e", 1L, Networks.preprod()),
                //From preview Byron genesis
                arguments("preview", "FHnt4NL7yPXjpZtYj1YUiX9QYYUZGXDT9gA2PJXQFkTSMx3EgawXK5BUrCHdhe2",
                        "171850d32f1635626a08a10b975cc5fd91956e543a0010750f9f3c09", 2L, Networks.preview())
        );
    }

    @Test
    void unknownProtocolMagic() {
        ByronAddress address = new ByronAddress(envelope("83" + ROOT_ITEM + "a1" + "02" + bstr("182a") + "00"));

        assertThat(address.getProtocolMagic()).hasValue(42);
        assertThat(address.getNetwork()).isEqualTo(new Network(0, 42));
    }

    @Test
    void unknownAttributesAreAccepted() {
        ByronAddress address = new ByronAddress(envelope("83" + ROOT_ITEM + "a3" + "02" + bstr("01") + "03" + bstr("ffff") + "18ff" + bstr("") + "00"));

        assertThat(address.getProtocolMagic()).hasValue(1);
        assertThat(address.getDerivationPath()).isEmpty();
    }

    @Test
    void nonCanonicalEncodingsAcceptedByLedgerAreAccepted() {
        //Non-shortest array, tag, byte string, attribute key and crc encodings outside the canonical-only fields
        String payload = "9803" + "59001c" + ROOT + "b801" + "1802" + "5801" + "01" + "00";
        String address = "82" + "d90018" + "59" + String.format("%04x", payload.length() / 2) + payload
                + "1b00000000" + String.format("%08x", crc(payload));

        ByronAddress byronAddress = new ByronAddress(HexUtil.decodeHexString(address));

        assertThat(HexUtil.encodeHexString(byronAddress.getRoot())).isEqualTo(ROOT);
        assertThat(byronAddress.getProtocolMagic()).hasValue(1);
    }

    @Test
    void bytesRoundTrip() {
        String base58 = "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK";
        byte[] bytes = new ByronAddress(base58).getBytes();

        ByronAddress address = new ByronAddress(bytes);

        assertThat(address.toBase58()).isEqualTo(base58);
        assertThat(address.getAddress()).isEqualTo(base58);
        assertThat(address.getBytes()).isEqualTo(bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ah7V1nBomind8SQegZr48LK2PmMYsBgHiKZwEYkWuDod", //32 bytes of invalid CBOR starting with 0x8f
            "FtnfEJw", //84 01 02 03 04
            "27uung4Ye", //82 5a 7fffffff: byte string length beyond the input must be rejected without allocating
            "",
            "0OIl", //Not base58
            "ExxxFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREk",
            "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREL" //Last char changed
    })
    void invalidBase58Address(String address) {
        assertThatThrownBy(() -> new ByronAddress(address)).isInstanceOf(AddressFormatException.class);
        assertThat(AddressUtil.isValidAddress(address)).isFalse();
    }

    @Test
    void nullAddress() {
        assertThatThrownBy(() -> new ByronAddress((String) null)).isInstanceOf(AddressFormatException.class);
        assertThatThrownBy(() -> new ByronAddress((byte[]) null)).isInstanceOf(AddressFormatException.class);
        assertThat(AddressUtil.isValidAddress(null)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEnvelopes")
    void invalidEnvelope(String name, String hex) {
        assertInvalid(HexUtil.decodeHexString(hex));
    }

    static Stream<Arguments> invalidEnvelopes() {
        String validAddress = envelopeHex(PAYLOAD);
        String crc = uint(crc(PAYLOAD));
        String tamperedPayload = PAYLOAD.replace(ROOT, "00" + ROOT.substring(2));

        return Stream.of(
                arguments("empty", ""),
                arguments("truncated", validAddress.substring(0, validAddress.length() - 2)),
                arguments("trailing byte", validAddress + "00"),
                arguments("crc mismatch", "82d818" + bstr(PAYLOAD) + uint(crc(PAYLOAD) ^ 1)),
                arguments("payload changed after crc", "82d818" + bstr(tamperedPayload) + crc),
                arguments("crc above uint32", "82d818" + bstr(PAYLOAD) + "1b00000001" + String.format("%08x", crc(PAYLOAD))),
                arguments("crc negative", "82d818" + bstr(PAYLOAD) + "20"),
                arguments("crc missing", "81d818" + bstr(PAYLOAD)),
                arguments("array of 3", "83d818" + bstr(PAYLOAD) + crc + "00"),
                arguments("non-shortest array header", "9802d818" + bstr(PAYLOAD) + crc),
                arguments("indefinite array", "9fd818" + bstr(PAYLOAD) + crc + "ff"),
                arguments("not an array", "a2d818" + bstr(PAYLOAD) + crc),
                arguments("tag 25", "82d819" + bstr(PAYLOAD) + crc),
                arguments("no tag", "82" + bstr(PAYLOAD) + crc),
                arguments("payload not bytes", "82d818" + PAYLOAD + crc),
                arguments("indefinite payload bytes", "82d8185f" + bstr(PAYLOAD) + "ff" + crc),
                arguments("payload length beyond data", "82d8185a7fffffff" + PAYLOAD)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    void invalidPayload(String name, String payloadHex) {
        assertInvalid(envelope(payloadHex));
    }

    static Stream<Arguments> invalidPayloads() {
        String root27 = "581b" + ROOT.substring(2);
        String root29 = "581d" + ROOT + "00";
        return Stream.of(
                arguments("empty payload", ""),
                arguments("payload array of 2", "82" + ROOT_ITEM + "a0"),
                arguments("payload array of 4", "84" + ROOT_ITEM + "a0" + "00" + "00"),
                arguments("indefinite payload array", "9f" + ROOT_ITEM + "a0" + "00" + "ff"),
                arguments("trailing bytes in payload", PAYLOAD + "00"),
                arguments("root of 27 bytes", "83" + root27 + "a0" + "00"),
                arguments("root of 29 bytes", "83" + root29 + "a0" + "00"),
                arguments("root not bytes", "83" + "00" + "a0" + "00"),
                arguments("indefinite root bytes", "83" + "5f" + ROOT_ITEM + "ff" + "a0" + "00"),
                arguments("script type", "83" + ROOT_ITEM + "a0" + "01"),
                arguments("unknown type", "83" + ROOT_ITEM + "a0" + "03"),
                arguments("type out of uint8 range", "83" + ROOT_ITEM + "a0" + "190100"),
                arguments("type not canonical", "83" + ROOT_ITEM + "a0" + "1800"),
                arguments("type negative", "83" + ROOT_ITEM + "a0" + "20"),
                arguments("type missing", "83" + ROOT_ITEM + "a0"),
                arguments("attributes not a map", "83" + ROOT_ITEM + "80" + "00"),
                arguments("indefinite attributes map", "83" + ROOT_ITEM + "bf" + "02" + bstr("01") + "ff" + "00"),
                arguments("attributes count beyond data", "83" + ROOT_ITEM + "a5" + "02" + bstr("01") + "00"),
                arguments("duplicate attribute key", "83" + ROOT_ITEM + "a2" + "02" + bstr("01") + "02" + bstr("01") + "00"),
                arguments("decreasing attribute keys", "83" + ROOT_ITEM + "a2" + "03" + bstr("01") + "02" + bstr("01") + "00"),
                arguments("attribute key above uint8", "83" + ROOT_ITEM + "a1" + "190100" + bstr("01") + "00"),
                arguments("attribute key negative", "83" + ROOT_ITEM + "a1" + "20" + bstr("01") + "00"),
                arguments("attribute value not bytes", "83" + ROOT_ITEM + "a1" + "02" + "01" + "00"),
                arguments("protocol magic empty", "83" + ROOT_ITEM + "a1" + "02" + bstr("") + "00"),
                arguments("protocol magic not uint", "83" + ROOT_ITEM + "a1" + "02" + bstr("40") + "00"),
                arguments("protocol magic not canonical", "83" + ROOT_ITEM + "a1" + "02" + bstr("1801") + "00"),
                arguments("protocol magic above uint32", "83" + ROOT_ITEM + "a1" + "02" + bstr("1b0000000100000000") + "00"),
                arguments("protocol magic with trailing bytes", "83" + ROOT_ITEM + "a1" + "02" + bstr("0101") + "00"),
                arguments("derivation path not bytes", "83" + ROOT_ITEM + "a1" + "01" + bstr("01") + "00"),
                arguments("derivation path not canonical", "83" + ROOT_ITEM + "a1" + "01" + bstr("5801aa") + "00"),
                arguments("indefinite derivation path", "83" + ROOT_ITEM + "a1" + "01" + bstr("5f41aaff") + "00"),
                arguments("derivation path with trailing bytes", "83" + ROOT_ITEM + "a1" + "01" + bstr("4000") + "00")
        );
    }

    private static void assertInvalid(byte[] bytes) {
        assertThatThrownBy(() -> new ByronAddress(bytes)).isInstanceOf(AddressFormatException.class);

        String base58 = Base58.encode(bytes);
        assertThatThrownBy(() -> new ByronAddress(base58)).isInstanceOf(AddressFormatException.class);
        assertThat(AddressUtil.isValidAddress(base58)).isFalse();
    }

    private static byte[] envelope(String payloadHex) {
        return HexUtil.decodeHexString(envelopeHex(payloadHex));
    }

    private static String envelopeHex(String payloadHex) {
        return "82" + "d818" + bstr(payloadHex) + uint(crc(payloadHex));
    }

    private static long crc(String hex) {
        CRC32 crc32 = new CRC32();
        crc32.update(HexUtil.decodeHexString(hex));
        return crc32.getValue();
    }

    private static String bstr(String hex) {
        return header(2, hex.length() / 2) + hex;
    }

    private static String uint(long value) {
        return header(0, value);
    }

    private static String header(int majorType, long value) {
        int initialByte = majorType << 5;
        if (value < 24)
            return String.format("%02x", initialByte | value);
        else if (value <= 0xFF)
            return String.format("%02x%02x", initialByte | 24, value);
        else if (value <= 0xFFFF)
            return String.format("%02x%04x", initialByte | 25, value);
        else
            return String.format("%02x%08x", initialByte | 26, value);
    }
}
