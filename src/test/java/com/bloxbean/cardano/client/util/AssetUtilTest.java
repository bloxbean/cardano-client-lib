package com.bloxbean.cardano.client.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetUtilTest {

    @Test
    void calculateFingerPrint() {
    }

    @Test
    void testCalculateFingerPrint1() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1rjklcrnsdzqp65wjgrg55sy9723kw09mlgvlc3", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint2() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc37e";
        String assetName = "";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1nl0puwxmhas8fawxp8nx4e2q3wekg969n2auw3", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint3() {
        String policyId = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1uyuxku60yqe57nusqzjx38aan3f2wq6s93f6ea", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint4() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "504154415445";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, assetName);

        assertEquals("asset13n25uv0yaf5kus35fm2k86cqy60z58d9xmde92", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint5() {
        String policyId = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "504154415445";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, assetName);

        assertEquals("asset1hv4p5tv2a837mzqrst04d0dcptdjmluqvdx9k3", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint6() {
        String policyId = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, assetName);

        assertEquals("asset1aqrdypg669jgazruv5ah07nuyqe0wxjhe2el6f", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint7() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, assetName);

        assertEquals("asset17jd78wukhtrnmjh3fngzasxm8rck0l2r4hhyyt", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint8() {
        String policyId = "7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "0000000000000000000000000000000000000000000000000000000000000000";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, assetName);

        assertEquals("asset1pkpwyknlvul7az0xx8czhl60pyel45rpje4z8w", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint_when0xPrefixInParameters() {
        String policyId = "0x7eae28af2208be856f7a119668ae52a49b73725e326dc16579dcc373";
        String assetName = "0x1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, assetName);

        assertEquals("asset17jd78wukhtrnmjh3fngzasxm8rck0l2r4hhyyt", fingerPrint);
    }

    @Test
    void testCalculateFingerPrint__when0xPrefixInParametersAndEmptyAssetName() {
        String policyId = "0x1e349c9bdea19fd6c147626a5260bc44b71635f398b67c59881df209";
        String assetName = "";

        String fingerPrint =
                AssetUtil.calculateFingerPrint(policyId, "0x" + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8)));

        assertEquals("asset1uyuxku60yqe57nusqzjx38aan3f2wq6s93f6ea", fingerPrint);
    }
}
