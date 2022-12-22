package com.bloxbean.cardano.client.api.model;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

class UtxoTest {

    @Test
    void deserialization1Test() throws CborDeserializationException {
        Utxo expected = new Utxo();
        expected.setTxHash("8684f2f176bd59ac41adf9e2dd363f898d3cb54b0af92289a227b287fed205b2");
        expected.setOutputIndex(1);
        expected.setAddress("addr1q8vaadv0h7atv366u6966u4rft2svjlf5uajy8lkpsgdrc24rnskuetxz2u3m5ac22s3njvftxcl2fc8k8kjr088ge0qpn6xhn");
        Amount amount1 = new Amount(LOVELACE, BigInteger.valueOf(4628940));
        Amount amount2 = new Amount("1774343241680e4daef7cbfe3536fc857ce23fb66cd0b66320b2e3dd0x4249534f4e", BigInteger.valueOf(5000000));
        Amount amount3 = new Amount("1a71dc14baa0b4fcfb34464adc6656d0e562571e2ac1bc990c9ce5f60x574f4c46", BigInteger.valueOf(5555555555L));
        Amount amount4 = new Amount("2afb448ef716bfbed1dcb676102194c3009bee5399e93b90def9db6a0x4249534f4e", BigInteger.valueOf(5000000));
        Amount amount5 = new Amount("2d7444cf9e317a12e3eb72bf424fd2a0c8fbafedf10e20bfdb4ad8ab0x434845444441", BigInteger.valueOf(100000));
        Amount amount6 = new Amount("4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bd0x546f6b68756e", BigInteger.valueOf(2));
        Amount amount7 = new Amount("5029eeccd52fef299509d509a8318fd7930c3dffcce1f9f39ff11ef90x464743", BigInteger.valueOf(50));
        Amount amount8 = new Amount("544571c086d0e5c5022aca9717dd0f438e21190abb48f37b3ae129f00x47524f57", BigInteger.valueOf(3));
        Amount amount9 = new Amount("547ceed647f57e64dc40a29b16be4f36b0d38b5aa3cd7afb286fc0940x6262486f736b79", BigInteger.valueOf(500));
        Amount amount10 = new Amount("8d0ae3c5b13b47907b16511a540d47436d12dcc96453c0f59089b4510x42524f4f4d", BigInteger.valueOf(37972049));
        Amount amount11 = new Amount("9668ef339ea4b29a29b7a500b1a1f6769568ddb623cc463f95fe07f20x4d75736963476c6f626554776f", BigInteger.valueOf(2));
        Amount amount12 = new Amount("98dc68b04026544619a251bc01aad2075d28433524ac36cbc75599a10x686f736b", BigInteger.valueOf(100));
        Amount amount13 = new Amount("a0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c2350x484f534b59", BigInteger.valueOf(3000000));
        Amount amount14 = new Amount("af2e27f580f7f08e93190a81f72462f153026d06450924726645891b0x44524950", BigInteger.valueOf(3000000000L));
        Amount amount15 = new Amount("afc910d7a306d20c12903979d4935ae4307241d03245743548e767830x4153484942", BigInteger.valueOf(1000000000));
        Amount amount16 = new Amount("b0446f1c9105f0cc5bb6bd092f5c3e523e13f8a999b31c870298fa400x51554944", BigInteger.valueOf(3));
        Amount amount17 = new Amount("b788fbee71a32d2efc5ee7d151f3917d99160f78fb1e41a1bbf80d8f0x4c454146544f4b454e", BigInteger.valueOf(9232173891L));
        Amount amount18 = new Amount("b84c0133554a0c098ebaded08fa55790873ae6b6b5febad154678bb90x466f7274756e654e616d695765616c7468396f663130", BigInteger.ONE);
        Amount amount19 = new Amount("d030b626219d81673bd32932d2245e0c71ae5193281f971022b23a780x436172646f67656f", BigInteger.valueOf(840));
        Amount amount20 = new Amount("d1333653aa3ac24adfa9c6d09c1a2cc8e2b7b86ad334c17f2acb86470x42696f546f6b656e", BigInteger.valueOf(2));
        Amount amount21 = new Amount("d894897411707efa755a76deb66d26dfd50593f2e70863e1661e98a00x7370616365636f696e73", BigInteger.valueOf(9));
        expected.setAmount(List.of(amount1, amount2, amount3, amount4, amount5, amount6, amount7, amount8, amount9, amount10,
                amount11, amount12, amount13, amount14, amount15, amount16, amount17, amount18, amount19, amount20, amount21));
        String cbor = "828258208684f2f176bd59ac41adf9e2dd363f898d3cb54b0af92289a227b287fed205b20182583901d9deb58fbfbab6475ae68bad72a34ad5064be9a73b221ff60c10d1e1551ce16e656612b91dd3b852a119c98959b1f52707b1ed21bce7465e821a0046a1ccb4581c1774343241680e4daef7cbfe3536fc857ce23fb66cd0b66320b2e3dda1454249534f4e1a004c4b40581c1a71dc14baa0b4fcfb34464adc6656d0e562571e2ac1bc990c9ce5f6a144574f4c461b000000014b230ce3581c2afb448ef716bfbed1dcb676102194c3009bee5399e93b90def9db6aa1454249534f4e1a004c4b40581c2d7444cf9e317a12e3eb72bf424fd2a0c8fbafedf10e20bfdb4ad8aba1464348454444411a000186a0581c4247d5091db82330100904963ab8d0850976c80d3f1b927e052e07bda146546f6b68756e02581c5029eeccd52fef299509d509a8318fd7930c3dffcce1f9f39ff11ef9a1434647431832581c544571c086d0e5c5022aca9717dd0f438e21190abb48f37b3ae129f0a14447524f5703581c547ceed647f57e64dc40a29b16be4f36b0d38b5aa3cd7afb286fc094a1476262486f736b791901f4581c8d0ae3c5b13b47907b16511a540d47436d12dcc96453c0f59089b451a14542524f4f4d1a02436851581c9668ef339ea4b29a29b7a500b1a1f6769568ddb623cc463f95fe07f2a14d4d75736963476c6f626554776f02581c98dc68b04026544619a251bc01aad2075d28433524ac36cbc75599a1a144686f736b1864581ca0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235a145484f534b591a002dc6c0581caf2e27f580f7f08e93190a81f72462f153026d06450924726645891ba144445249501ab2d05e00581cafc910d7a306d20c12903979d4935ae4307241d03245743548e76783a14541534849421a3b9aca00581cb0446f1c9105f0cc5bb6bd092f5c3e523e13f8a999b31c870298fa40a1445155494403581cb788fbee71a32d2efc5ee7d151f3917d99160f78fb1e41a1bbf80d8fa1494c454146544f4b454e1b000000022647cb43581cb84c0133554a0c098ebaded08fa55790873ae6b6b5febad154678bb9a156466f7274756e654e616d695765616c7468396f66313001581cd030b626219d81673bd32932d2245e0c71ae5193281f971022b23a78a148436172646f67656f190348581cd1333653aa3ac24adfa9c6d09c1a2cc8e2b7b86ad334c17f2acb8647a14842696f546f6b656e02581cd894897411707efa755a76deb66d26dfd50593f2e70863e1661e98a0a14a7370616365636f696e7309";
        Assertions.assertEquals(expected, Utxo.deserialize(HexUtil.decodeHexString(cbor)));
    }

    @Test
    void deserialization2Test() throws CborDeserializationException {
        Utxo expected = new Utxo();
        expected.setTxHash("0bd1981eb1404a7daf03ab2bd6c8d318253003473c257b4049968f2597ac5134");
        expected.setOutputIndex(1);
        expected.setAddress("addr1q8vaadv0h7atv366u6966u4rft2svjlf5uajy8lkpsgdrc24rnskuetxz2u3m5ac22s3njvftxcl2fc8k8kjr088ge0qpn6xhn");
        expected.setAmount(List.of(new Amount(LOVELACE, BigInteger.valueOf(90021475))));
        String cbor = "828258200bd1981eb1404a7daf03ab2bd6c8d318253003473c257b4049968f2597ac51340182583901d9deb58fbfbab6475ae68bad72a34ad5064be9a73b221ff60c10d1e1551ce16e656612b91dd3b852a119c98959b1f52707b1ed21bce7465e1a055d9e63";
        Assertions.assertEquals(expected, Utxo.deserialize(HexUtil.decodeHexString(cbor)));
    }
}
