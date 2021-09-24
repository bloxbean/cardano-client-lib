package com.bloxbean.cardano.client.common;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class Bech32Test {

    private static Stream<Arguments> bech32TestArguments() {
        return Stream.of(
                Arguments.of("addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x", "019493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8e337b62cfff6403a06a3acbc34f8c46003c69fe79a3628cefa9c47251", 0, "addr"),
                Arguments.of("addr1z8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gten0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs9yc0hh", "11c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f337b62cfff6403a06a3acbc34f8c46003c69fe79a3628cefa9c47251", 2, "addr"),
                Arguments.of("addr1yx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs2z78ve", "219493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8ec37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 4, "addr"),
                Arguments.of("addr1x8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gt7r0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shskhj42g", "31c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542fc37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 6, "addr"),
                Arguments.of("addr1gx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5pnz75xxcrzqf96k", "419493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8e8198bd431b03", 8, "addr"),
                Arguments.of("addr128phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcrtw79hu", "51c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f8198bd431b03", 10, "addr"),
                Arguments.of("addr1vx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzers66hrl8", "619493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8e", 12, "addr"),
                Arguments.of("addr1w8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcyjy7wx", "71c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 14, "addr"),
                Arguments.of("stake1uyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gh6ffgw", "e1337b62cfff6403a06a3acbc34f8c46003c69fe79a3628cefa9c47251", 28, "stake"),
                Arguments.of("stake178phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcccycj5", "f1c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 30, "stake"),
                Arguments.of("addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs68faae", "009493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8e337b62cfff6403a06a3acbc34f8c46003c69fe79a3628cefa9c47251", 0, "addr_test"),
                Arguments.of("addr_test1zrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gten0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgsxj90mg", "10c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f337b62cfff6403a06a3acbc34f8c46003c69fe79a3628cefa9c47251", 2, "addr_test"),
                Arguments.of("addr_test1yz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shsf5r8qx", "209493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8ec37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 4, "addr_test"),
                Arguments.of("addr_test1xrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gt7r0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs4p04xh", "30c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542fc37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 6, "addr_test"),
                Arguments.of("addr_test1gz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5pnz75xxcrdw5vky", "409493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8e8198bd431b03", 8, "addr_test"),
                Arguments.of("addr_test12rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcryqrvmw", "50c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f8198bd431b03", 10, "addr_test"),
                Arguments.of("addr_test1vz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerspjrlsz", "609493315cd92eb5d8c4304e67b7e16ae36d61d34502694657811a2c8e", 12, "addr_test"),
                Arguments.of("addr_test1wrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcl6szpr", "70c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 14, "addr_test"),
                Arguments.of("stake_test1uqehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gssrtvn", "e0337b62cfff6403a06a3acbc34f8c46003c69fe79a3628cefa9c47251", 28, "stake_test"),
                Arguments.of("stake_test17rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcljw6kf", "f0c37b1b5dc0669f1d3c61a6fddb2e8fde96be87b881c60bce8e8d542f", 30, "stake_test"),
                Arguments.of("pool1yr0cv3dtmhcfgqa6yetvmf769ngk89e6tepecmjrmjl2jzcw2lm", "20df8645abddf09403ba2656cda7da2cd163973a5e439c6e43dcbea9", 4, "pool")
        );
    }

    @ParameterizedTest
    @MethodSource("bech32TestArguments")
    void bech32DecodeTests(String addr, String expectedHex, int expectedVer, String expectedHrp) {

        var decoded = Bech32.decode(addr);
        var actualHrp = decoded.hrp;
        var actualHex = Hex.toHexString(decoded.data);

        Assertions.assertTrue(Bech32.isValid(addr));

        Assertions.assertEquals(expectedHrp, actualHrp);
        Assertions.assertEquals(expectedHex, actualHex);
        Assertions.assertEquals(expectedVer, decoded.ver);
    }

    @ParameterizedTest
    @MethodSource("bech32TestArguments")
    void bech32EncodeTests(String expectedAddr, String expectedHex, int expectedVer, String expectedHrp) {
        var actualAddr = Bech32.encode(Hex.decode(expectedHex), expectedHrp);
        Assertions.assertEquals(expectedAddr, actualAddr);
    }

}
