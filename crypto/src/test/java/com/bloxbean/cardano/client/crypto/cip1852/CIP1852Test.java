package com.bloxbean.cardano.client.crypto.cip1852;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.Bip32Type;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CIP1852Test {

    @Test
    void getPaymentVerificationKey() {
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath);
        byte[] pvtKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        byte[] publicKey = hdKeyPair.getPublicKey().getBytes();

        String publicAdd = Bech32.encode(publicKey, "addr_xvk");
        assertThat(publicAdd).isEqualTo("addr_xvk1r30n0pv6d40kzzl4e6xje2y7c446gw2x9sgnms3vv62tx264tf5n9lxnuxqc5xpqlg30dtlq0tf0fav4kafsge6u24x296vg85l399cx2uv4k");
    }

    @Test
    void getStakeVerificationKey() {
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(2, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath);
        byte[] pvtKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        byte[] publicKey = hdKeyPair.getPublicKey().getBytes();

        String publicAdd = Bech32.encode(publicKey, "stake_xvk");
        assertThat(publicAdd).isEqualTo("stake_xvk143rnqx89nnmlt8w5kerl03hvl2reuv02l450wjs2vd74cezqx2mja08euhtd7gejfylpfe8j3vgejh25nu9nwqgfx0qy8d40llf9h6qeg2t4z");

    }

    @Test
    void getPublicKeyFromAccountPubKey_acct_xvk() {
        String accountPubKey = "acct_xvk136qnzfm6c34lddfxll60uqxwe3csymp8sdqvg3zcs79azchhqdn4qhs75qtwvuzjkd5h436fujcrgqgq2mnlmr5yse5zvewdj6flkgg5catgs";
        Bech32.Bech32Data bech32Data = Bech32.decode(accountPubKey);
        String expectedStakePubKey = "03ab2b878c8f8ec759cee22c321f86270bea486796fb5b881241cfa9254a60f32ee31c3e3529c64f8c801fa5f630b52dd11491ded35d180325cd94a1f80f7f2f";
        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(bech32Data.data, DerivationPath.createStakeAddressDerivationPath());

        assertThat(HexUtil.encodeHexString(hdPublicKey.getBytes())).isEqualTo(expectedStakePubKey);
    }

    @Test
    void getPublicKeyFromAccountPubKey_xpub() {
        String accountPubKey = "xpub136qnzfm6c34lddfxll60uqxwe3csymp8sdqvg3zcs79azchhqdn4qhs75qtwvuzjkd5h436fujcrgqgq2mnlmr5yse5zvewdj6flkgg88stt0";
        Bech32.Bech32Data bech32Data = Bech32.decode(accountPubKey);
        String expectedStakePubKey = "03ab2b878c8f8ec759cee22c321f86270bea486796fb5b881241cfa9254a60f32ee31c3e3529c64f8c801fa5f630b52dd11491ded35d180325cd94a1f80f7f2f";
        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(bech32Data.data, DerivationPath.createStakeAddressDerivationPath());

        assertThat(HexUtil.encodeHexString(hdPublicKey.getBytes())).isEqualTo(expectedStakePubKey);
    }

    @Test
    void getPublicKeyFromAccountPubKey_acct_xvk_2() {
        String accountPubKey = "acct_xvk1zxnrf4j4xzvxwwkmsjsrvtgv6g5q4l9yyskp807d62w5y6zvnmhepfxyysq4nydjqsjxj2dcsfc6ns6ljm2gqs6jh5vj58auceyfadsydvkn7";
        Bech32.Bech32Data bech32Data = Bech32.decode(accountPubKey);
        String expectedStakePubKey = "23cebbe2b5707bb3f4255ca44398556925f3f6dc2b5f8c6f2f1b27f252dbbc12de7ea31330158d2b2f163c456416160ef8c8739a2336030f770314277fb1c9f8";
        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(bech32Data.data, DerivationPath.createStakeAddressDerivationPath());

        assertThat(HexUtil.encodeHexString(hdPublicKey.getBytes())).isEqualTo(expectedStakePubKey);
    }

    @Test
    void getPublicKeyFromAccountPubKey_xpub_2() {
        String accountPubKey = "xpub1zxnrf4j4xzvxwwkmsjsrvtgv6g5q4l9yyskp807d62w5y6zvnmhepfxyysq4nydjqsjxj2dcsfc6ns6ljm2gqs6jh5vj58auceyfadshjpksp";
        Bech32.Bech32Data bech32Data = Bech32.decode(accountPubKey);
        String expectedStakePubKey = "23cebbe2b5707bb3f4255ca44398556925f3f6dc2b5f8c6f2f1b27f252dbbc12de7ea31330158d2b2f163c456416160ef8c8739a2336030f770314277fb1c9f8";
        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(bech32Data.data, DerivationPath.createStakeAddressDerivationPath());

        assertThat(HexUtil.encodeHexString(hdPublicKey.getBytes())).isEqualTo(expectedStakePubKey);
    }

    @Test
    void getRootKeyPairFromMnemonic() {
        String mnemonic = "top exact spice seed cloud birth orient bracket happy cat section girl such outside elder";

        var rootKeyPair = new CIP1852().getRootKeyPairFromMnemonic(mnemonic);
        String expectedRootKeyBech32 = "root_xsk1zza6z52v8gelnaqdhuny3ywlccud5dtm8rvvyem4utnfwzcaa9pspsmdm99qfpy2qz7sw9sts59mrkegmdqyjen5ykm4z3ccyrkn8g5mm0qw35arvwxclfh6tj3s4x7t2q85wenvppjpxckcxgnf8vd80ug0l6rw";
        String expectedRootPvtKeyHex = HexUtil.encodeHexString(Bech32.decode(expectedRootKeyBech32).data);

        var rootPvtKey = rootKeyPair.getPrivateKey().getBytes();

        assertThat(HexUtil.encodeHexString(rootPvtKey)).isEqualTo(expectedRootPvtKeyHex);
    }

    @Test
    void getKeyPairFromRootKeyAtDerivationPath() {
        String rootKey = "root_xsk1zza6z52v8gelnaqdhuny3ywlccud5dtm8rvvyem4utnfwzcaa9pspsmdm99qfpy2qz7sw9sts59mrkegmdqyjen5ykm4z3ccyrkn8g5mm0qw35arvwxclfh6tj3s4x7t2q85wenvppjpxckcxgnf8vd80ug0l6rw";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;

        var addr0KeyPair = new CIP1852().getKeyPairFromRootKey(rootKeyBytes, DerivationPath.createExternalAddressDerivationPath(0));

        //1852H/1815H/0H/0/0
        String expectedAddr0PvtKey = "addr_xsk1artlf4j6xz246j4xqtn6d595l7sy4vk0zuvzawlg7lwvq2qaa9pkujarca9j5ju08m0dlgw2qagauw693lvmrghujzvxsdfj99pwdm7xqwpgj5asad6nl5rzact6hune2xsl5x5gv2tds75ksdptavxr6se0fk8e";
        String expectedAddr0PvtKeyHex = HexUtil.encodeHexString(Bech32.decode(expectedAddr0PvtKey).data);

        assertThat(HexUtil.encodeHexString(addr0KeyPair.getPrivateKey().getBytes())).isEqualTo(expectedAddr0PvtKeyHex);
    }

    @Test
    void getRootKeyPairFromRootKey() {
        String rootKeyBech32 = "root_xsk1zza6z52v8gelnaqdhuny3ywlccud5dtm8rvvyem4utnfwzcaa9pspsmdm99qfpy2qz7sw9sts59mrkegmdqyjen5ykm4z3ccyrkn8g5mm0qw35arvwxclfh6tj3s4x7t2q85wenvppjpxckcxgnf8vd80ug0l6rw";
        byte[] expectedRootKeyBytes = Bech32.decode(rootKeyBech32).data;

        var rootKeyPair = new CIP1852().getRootKeyPairFromRootKey(expectedRootKeyBytes);

        assertThat(HexUtil.encodeHexString(rootKeyPair.getPrivateKey().getBytes())).isEqualTo(HexUtil.encodeHexString(expectedRootKeyBytes));
    }

    @Test
    void getRootKeyPairFromMnemonic_Ledger() {
        String mnemonicPhrase = "recall grace sport punch exhibit mad harbor stand obey short width stem awkward used stairs wool ugly trap season stove worth toward congress jaguar";

        HdKeyPair rootKeyPair = new CIP1852().getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.LEDGER);
        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("a08cf85b564ecf3b947d8d4321fb96d70ee7bb760877e371899b14e2ccf88658104b884682b57efd97decbb318a45c05a527b9cc5c2f64f7352935a049ceea60680d52308194ccef2a18e6812b452a5815fbd7f5babc083856919aaf668fe7e4");
    }

    @Test
    void getKeyPairFromMnemonic_Ledger() {
        String mnemonicPhrase = "recall grace sport punch exhibit mad harbor stand obey short width stem awkward used stairs wool ugly trap season stove worth toward congress jaguar";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath, Bip32Type.LEDGER);
        byte[] privateKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKeyBytes);

        assertThat(privateKeyHex).isEqualTo("90c9771c3b6d3daaba283b315036cee82a000ccb4a6e6227e1c7b2f2e4f88658d96d9ecb0e9e605ce723779ad0d3388d9abb504b0fd63a5129593709d1394449563f688471af3a2de595a30813bbe676b0bd5aa06e3615895e915e3459bc70d8");
    }

    @Test
    void getRootKeyPairFromMnemonic_Trezor_15Words() {
        String mnemonicPhrase = "eight country switch draw meat scout mystery blade tip drift useless good keep usage title";

        HdKeyPair rootKeyPair = new CIP1852().getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.TREZOR);
        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        // For < 24 words, Trezor uses same algorithm as ICARUS
        assertThat(masterKeyHex).isEqualTo("c065afd2832cd8b087c4d9ab7011f481ee1e0721e78ea5dd609f3ab3f156d245d176bd8fd4ec60b4731c3918a2a72a0226c0cd119ec35b47e4d55884667f552a23f7fdcd4a10c6cd2c7393ac61d877873e248f417634aa3d812af327ffe9d620");
    }

    @Test
    void getKeyPairFromMnemonic_Icarus() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath, Bip32Type.ICARUS);
        byte[] privateKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKeyBytes);

        assertThat(privateKeyHex).isEqualTo("00a5e1ba771fdb15a63aec002c2a913f5ad48f1a521bc022afb9e4681301645dc7b97a72ab62dae883aa3375f1ae239b697a2218b9aad01f4f50e3915cb80c0d09147f80e9fb742cb26b5c3a65796c7c2dc94e8e3500cc474bd3ae3ef2c424a3");
    }

    @Test
    void getKeyPairFromMnemonic_Trezor() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath, Bip32Type.TREZOR);
        byte[] privateKey = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKey);

        assertThat(privateKeyHex).isEqualTo("d8c899db0375b13bbfd69dcc6051648e7a969b04e533626a3f9917034fd33048a38c7274514494a7f540a8ad7ec1b17ae32ba10adf0a9c8b18686afc9f6555563b921a3235da3d95691d7e6093b92ea12341a1977641e4db71a4d654b1788071");
    }

    @Test
    void getKeyPairFromMnemonic_Icarus_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, passphrase, derivationPath, Bip32Type.ICARUS);
        byte[] privateKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKeyBytes);

        assertThat(privateKeyHex).isEqualTo("1802647084d9b7017c9739b620fb2796f9cb385f2174ccbbcce5f605af581a571c58abaf0495dd9c688c84285713adfae8d8f4223743cc3fb5286068f1dfa01378eb7fc589c62f5a807b7ebd5a85ab4069defb6345a9f87f32ae4f5744ecf19f");
    }

    @Test
    void getKeyPairFromMnemonic_Icarus_WithPassphrase2() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants!!!";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, passphrase, derivationPath, Bip32Type.ICARUS);
        byte[] privateKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKeyBytes);

        // Changed passphrase should give different private key
        assertThat(privateKeyHex).isEqualTo("d8835611efeadfd3cabb4a0132cc4db2fd6bb1f91612a770cae3f367719ef350719e63f41a6062e74a9a6ec1a84ab89009718be762ddf8dba87ed6cdce5e13ceed8202ec218c0f71aaa5dbc9acb4cb565014639ca48199dbefb52c1808fec34d");
    }

    @Test
    void getKeyPairFromMnemonic_Trezor_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, passphrase, derivationPath, Bip32Type.TREZOR);
        byte[] privateKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKeyBytes);

        assertThat(privateKeyHex).isEqualTo("b8f42a29776d1a7d1d6beb87f089f29664d9315c1bb542d0dc75add13ae5ac44d91126eae768fc9cadc9d0eb3ec0c83de64089d2140ec8603272f740f797d78730acd8b23e426da6e2cbd74a3c27679327071a3699c140ab3d4e0a1383da615b");
    }

    @Test
    void getKeyPairFromMnemonic_Ledger_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, passphrase, derivationPath, Bip32Type.LEDGER);
        byte[] privateKeyBytes = hdKeyPair.getPrivateKey().getBytes();
        String privateKeyHex = HexUtil.encodeHexString(privateKeyBytes);

        assertThat(privateKeyHex).isEqualTo("38a1662da3a72b26537d2c6231d403a0241d81f0169c390b5e36dcacbbd28959672e6189f9b060da181984f1bc51fc262715f0d5826124ec8494173802bc1e316e77e743ea943274eabba5b4b7930fe445891206c6443dbb9d04714369fa93e5");
    }

    @Test
    void getRootKeyPairFromMnemonic_Trezor_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";

        HdKeyPair rootKeyPair = new CIP1852().getRootKeyPairFromMnemonic(mnemonicPhrase, passphrase, Bip32Type.TREZOR);
        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("2001d74c3d96956aa6eccbd179cf88b73f39f4ce0b0b4915090776012de5ac44140d27f9a0fb074471feebeacb8d1aaee241feb29e29af60a818f7d2597997c97bf6591b8083509945e780b78ee5c83557c7fa736e3af08998958829f84c3c4d");
    }

    @Test
    void getRootKeyPairFromMnemonic_Ledger_WithPassphrase() {
        String mnemonicPhrase = "journey vessel youth squirrel slim cattle print sugar child master loan scout fine predict immense bargain oven senior broken drive modify argue judge dust";
        String passphrase = "crustypants";

        HdKeyPair rootKeyPair = new CIP1852().getRootKeyPairFromMnemonic(mnemonicPhrase, passphrase, Bip32Type.LEDGER);
        byte[] rootKeyBytes = rootKeyPair.getPrivateKey().getBytes();
        String masterKeyHex = HexUtil.encodeHexString(rootKeyBytes);

        assertThat(masterKeyHex).isEqualTo("e8eaf3ee0b95e31ea5ab4ce6f79d2cebd95dc91a9e91be483f70d535a2d28959d0f4271f660f1d3aa99dbd33577900d65d6b58387b8cdc6b28f40faf5ffb54ea4f672c8b43357c9fbdceaf125225c3a82122de0cae70fce08e8d704760ab05c7");
    }
}
