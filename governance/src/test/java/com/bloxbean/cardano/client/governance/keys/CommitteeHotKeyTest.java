package com.bloxbean.cardano.client.governance.keys;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

//Test Vector : https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-1.md
import static org.assertj.core.api.Assertions.assertThat;

public class CommitteeHotKeyTest {
    private String mnemonic1 = "test walk nut penalty hip pave soap entry language right filter choice";

    public CommitteeHotKeyTest() {

    }

    @Test
    void signingKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotSigningKey = "d85717921e6289606e15c1e2ee65b3bd6ec247e357889ba16178eedb74e1a45ef955aa17bd002971b05e750048b766eb6df4d855c54dd2ec7ad8850e2fe35ebe";

        var signingKey = committeeHotKey.signingKey();
        assertThat(HexUtil.encodeHexString(signingKey)).isEqualTo(expectedCommitteeHotSigningKey);
    }

    @Test
    void bech32SigningKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotSigningKeyBech32 = "cc_hot_sk1mpt30ys7v2ykqms4c83wuednh4hvy3lr27yfhgtp0rhdka8p5300j4d2z77sq2t3kp082qzgkanwkm05mp2u2nwja3ad3pgw9l34a0sdh7u7e";

        var bech32SigningKey = committeeHotKey.bech32SigningKey();
        assertThat(bech32SigningKey).isEqualTo(expectedCommitteeHotSigningKeyBech32);
    }

    @Test
    void bech32SigningKey_hdPrivateKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair.getPrivateKey());

        var expectedCommitteeHotSigningKeyBech32 = "cc_hot_sk1mpt30ys7v2ykqms4c83wuednh4hvy3lr27yfhgtp0rhdka8p5300j4d2z77sq2t3kp082qzgkanwkm05mp2u2nwja3ad3pgw9l34a0sdh7u7e";

        var bech32SigningKey = committeeHotKey.bech32SigningKey();
        assertThat(bech32SigningKey).isEqualTo(expectedCommitteeHotSigningKeyBech32);
    }

    @Test
    void verificationKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotVerificationKey = "792a7f83cab90261f72ef57ee94a65ca9b0c71c1be2c8fdd5318c3643b20b52f";

        var verificationKey = committeeHotKey.verificationKey();
        assertThat(HexUtil.encodeHexString(verificationKey)).isEqualTo(expectedCommitteeHotVerificationKey);
    }

    @Test
    void bech32VerificationKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotVerificationKeyBech32 = "cc_hot_vk10y48lq72hypxraew74lwjjn9e2dscuwphckglh2nrrpkgweqk5hschnzv5";

        var bech32VerificationKey = committeeHotKey.bech32VerificationKey();
        assertThat(bech32VerificationKey).isEqualTo(expectedCommitteeHotVerificationKeyBech32);
    }

    @Test
    void bech32VerificationKey_hdPublicKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair.getPublicKey());

        var expectedCommitteeHotVerificationKeyBech32 = "cc_hot_vk10y48lq72hypxraew74lwjjn9e2dscuwphckglh2nrrpkgweqk5hschnzv5";

        var bech32VerificationKey = committeeHotKey.bech32VerificationKey();
        assertThat(bech32VerificationKey).isEqualTo(expectedCommitteeHotVerificationKeyBech32);
    }

    @Test
    void extendedSigningKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotExtendedSigningKey = "d85717921e6289606e15c1e2ee65b3bd6ec247e357889ba16178eedb74e1a45ef955aa17bd002971b05e750048b766eb6df4d855c54dd2ec7ad8850e2fe35ebe5487e846e9a708b27681d6835fa2dac968108b3c845e379597491e6b476aa0b2";

        var extendedSigningKey = committeeHotKey.extendedSigningKey();
        assertThat(HexUtil.encodeHexString(extendedSigningKey)).isEqualTo(expectedCommitteeHotExtendedSigningKey);
    }

    @Test
    void bech32ExtendedSigningKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotExtendedSigningKeyBech32 = "cc_hot_xsk1mpt30ys7v2ykqms4c83wuednh4hvy3lr27yfhgtp0rhdka8p5300j4d2z77sq2t3kp082qzgkanwkm05mp2u2nwja3ad3pgw9l34a0j5sl5yd6d8pze8dqwksd069kkfdqggk0yytcmet96fre45w64qkgyxl0dt";

        var bech32ExtendedSigningKey = committeeHotKey.bech32ExtendedSigningKey();
        assertThat(bech32ExtendedSigningKey).isEqualTo(expectedCommitteeHotExtendedSigningKeyBech32);
    }

    @Test
    void bech32ExtendedSigningKey_hdPrivateKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair.getPrivateKey());

        var expectedCommitteeHotExtendedSigningKeyBech32 = "cc_hot_xsk1mpt30ys7v2ykqms4c83wuednh4hvy3lr27yfhgtp0rhdka8p5300j4d2z77sq2t3kp082qzgkanwkm05mp2u2nwja3ad3pgw9l34a0j5sl5yd6d8pze8dqwksd069kkfdqggk0yytcmet96fre45w64qkgyxl0dt";

        var bech32ExtendedSigningKey = committeeHotKey.bech32ExtendedSigningKey();
        assertThat(bech32ExtendedSigningKey).isEqualTo(expectedCommitteeHotExtendedSigningKeyBech32);
    }

    @Test
    void extendedVerificationKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotExtendedVerificationKey = "792a7f83cab90261f72ef57ee94a65ca9b0c71c1be2c8fdd5318c3643b20b52f5487e846e9a708b27681d6835fa2dac968108b3c845e379597491e6b476aa0b2";

        var extendedVerificationKey = committeeHotKey.extendedVerificationKey();
        assertThat(HexUtil.encodeHexString(extendedVerificationKey)).isEqualTo(expectedCommitteeHotExtendedVerificationKey);
    }

    @Test
    void bech32ExtendedVerificationKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotExtendedVerificationKeyBech32 = "cc_hot_xvk10y48lq72hypxraew74lwjjn9e2dscuwphckglh2nrrpkgweqk5h4fplggm56wz9jw6qadq6l5tdvj6qs3v7ggh3hjkt5j8ntga42pvs5rvh0a";

        var bech32ExtendedVerificationKey = committeeHotKey.bech32ExtendedVerificationKey();
        assertThat(bech32ExtendedVerificationKey).isEqualTo(expectedCommitteeHotExtendedVerificationKeyBech32);
    }

    @Test
    void bech32ExtendedVerificationKey_hdPublicKey() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair.getPublicKey());

        var expectedCommitteeHotExtendedVerificationKeyBech32 = "cc_hot_xvk10y48lq72hypxraew74lwjjn9e2dscuwphckglh2nrrpkgweqk5h4fplggm56wz9jw6qadq6l5tdvj6qs3v7ggh3hjkt5j8ntga42pvs5rvh0a";

        var bech32ExtendedVerificationKey = committeeHotKey.bech32ExtendedVerificationKey();
        assertThat(bech32ExtendedVerificationKey).isEqualTo(expectedCommitteeHotExtendedVerificationKeyBech32);
    }

    @Test
    void verificationKeyHash() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotVerificationKeyHash = "f6d29c0f7164d37610cbf67b126a993beb24a076d0653f1fa069588f";

        var verificationKeyHash = committeeHotKey.verificationKeyHash();
        assertThat(HexUtil.encodeHexString(verificationKeyHash)).isEqualTo(expectedCommitteeHotVerificationKeyHash);

        //Adding test for bech32VerificationKeyHash
        var expectedCommitteeHotVerificationKeyHashBech32 = committeeHotKey.bech32VerificationKeyHash();
        var committeeHotVKeyHashBech32 = CommitteeHotKey.bech32VerificationKeyHash(expectedCommitteeHotVerificationKeyHash);
        assertThat(committeeHotVKeyHashBech32).isEqualTo(expectedCommitteeHotVerificationKeyHashBech32);
    }

    @Test
    void bech32erificationKeyHash() {
        var hdKeyPair = getCommitteeHotKeyPair();
        var committeeHotKey = CommitteeHotKey.from(hdKeyPair);

        var expectedCommitteeHotVerificationKeyHash = "cc_hot_vkh17mffcrm3vnfhvyxt7ea3y65e804jfgrk6pjn78aqd9vg7vk5akz";

        var bech32erificationKeyHash = committeeHotKey.bech32VerificationKeyHash();
        assertThat(bech32erificationKeyHash).isEqualTo(expectedCommitteeHotVerificationKeyHash);
    }

    @Test
    void bech32ScriptHash1() {
        byte[] scriptHash = HexUtil.decodeHexString("d27a4229c92ec8961b6bfd32a87380dcee4a08c77b0d6c8b33f180e8");

        var expectedCommitteeHotScriptHashBech32 = "cc_hot_script16fayy2wf9myfvxmtl5e2suuqmnhy5zx80vxkezen7xqwskncf40";

        var bech32ScriptHash = CommitteeHotKey.bech32ScriptHash(scriptHash);
        assertThat(bech32ScriptHash).isEqualTo(expectedCommitteeHotScriptHashBech32);
    }

    @Test
    void bech32ScriptHash2() {
        byte[] scriptHash = HexUtil.decodeHexString("62e0798c7036ff35862cf42f4e7ada06f7fb5b6465390082a691be14");

        var expectedCommitteeHotScriptHashBech32 = "cc_hot_script1vts8nrrsxmlntp3v7sh5u7k6qmmlkkmyv5uspq4xjxlpg6u229p";

        var bech32ScriptHash = CommitteeHotKey.bech32ScriptHash(scriptHash);
        assertThat(bech32ScriptHash).isEqualTo(expectedCommitteeHotScriptHashBech32);
    }


    @Test
    void testCcHot_fromAccPubKeyXvk() {
        String accountXvk = "acct_xvk1kxenc045r0l2u5ethalm89pej406fu3ltk3csy37x9jrx56f8yqquzpltg7ydf7qvxl9kw53q3qzp30799u69yvlvgl0s4pdtpux4yc8mgmff";
        var accountVKBytes = Bech32.decode(accountXvk).data;

        var ccHotDerivationPath = DerivationPath.createCommitteeHotKeyDerivationPathForAccount(0);

        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(accountVKBytes, ccHotDerivationPath.getRole().getValue(), 0); //role = 5

        CommitteeHotKey committeeHotKey = CommitteeHotKey.from(hdPublicKey);
        assertThat(committeeHotKey.id()).isEqualTo("cc_hot1qgxf280zt5yznyq6u9t57rqy3c6v5qj03cvy200cm66cgnsc9z9ht");
    }

    private HdKeyPair getCommitteeHotKeyPair() {
        DerivationPath committeeHotKeyDerivationPath = DerivationPath.createCommitteeHotKeyDerivationPathForAccount(0);

        return new CIP1852().getKeyPairFromMnemonic(mnemonic1, committeeHotKeyDerivationPath);
    }

}
