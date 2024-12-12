package com.bloxbean.cardano.client.governance.keys;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

//Test Vector : https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-1.md
public class CommitteeColdKeyTest {
    private String mnemonic1 = "test walk nut penalty hip pave soap entry language right filter choice";

    public CommitteeColdKeyTest() {

    }

    @Test
    void signingKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdSigningKey = "684f5b480507755f387e7e544cb44b3e55eb3b88b9f6976bd41e5f746ce1a45e28b4aa8bf129088417c0fade65a98a056cbcda96c0a8874cfcbef0bf53932a12";

        var signingKey = committeeColdKey.signingKey();
        assertThat(HexUtil.encodeHexString(signingKey)).isEqualTo(expectedCommitteeColdSigningKey);
    }

    @Test
    void bech32SigningKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdSigningKeyBech32 = "cc_cold_sk1dp84kjq9qa647wr70e2yedzt8e27kwugh8mfw675re0hgm8p530z3d9230cjjzyyzlq04hn94x9q2m9um2tvp2y8fn7tau9l2wfj5yslmdl88";

        var bech32SigningKey = committeeColdKey.bech32SigningKey();
        assertThat(bech32SigningKey).isEqualTo(expectedCommitteeColdSigningKeyBech32);
    }

    @Test
    void bech32SigningKey_hdPrivateKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair.getPrivateKey());

        var expectedCommitteeColdSigningKeyBech32 = "cc_cold_sk1dp84kjq9qa647wr70e2yedzt8e27kwugh8mfw675re0hgm8p530z3d9230cjjzyyzlq04hn94x9q2m9um2tvp2y8fn7tau9l2wfj5yslmdl88";

        var bech32SigningKey = committeeColdKey.bech32SigningKey();
        assertThat(bech32SigningKey).isEqualTo(expectedCommitteeColdSigningKeyBech32);
    }

    @Test
    void verificationKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdVerificationKey = "a9781abfc1604a18ebff6fc35062c000a7a66fdca1323710ed38c1dfc3315bea";

        var verificationKey = committeeColdKey.verificationKey();
        assertThat(HexUtil.encodeHexString(verificationKey)).isEqualTo(expectedCommitteeColdVerificationKey);
    }

    @Test
    void bech32VerificationKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdVerificationKeyBech32 = "cc_cold_vk149up407pvp9p36lldlp4qckqqzn6vm7u5yerwy8d8rqalse3t04q7qsvwl";

        var bech32VerificationKey = committeeColdKey.bech32VerificationKey();
        assertThat(bech32VerificationKey).isEqualTo(expectedCommitteeColdVerificationKeyBech32);
    }

    @Test
    void bech32VerificationKey_hdPublicKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair.getPublicKey());

        var expectedCommitteeColdVerificationKeyBech32 = "cc_cold_vk149up407pvp9p36lldlp4qckqqzn6vm7u5yerwy8d8rqalse3t04q7qsvwl";

        var bech32VerificationKey = committeeColdKey.bech32VerificationKey();
        assertThat(bech32VerificationKey).isEqualTo(expectedCommitteeColdVerificationKeyBech32);
    }

    @Test
    void extendedSigningKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdExtendedSigningKey = "684f5b480507755f387e7e544cb44b3e55eb3b88b9f6976bd41e5f746ce1a45e28b4aa8bf129088417c0fade65a98a056cbcda96c0a8874cfcbef0bf53932a12c601968e75ff3052ffa675aedaaea49ff36cb23036df105e28e1d32b4527e6cf";

        var extendedSigningKey = committeeColdKey.extendedSigningKey();
        assertThat(HexUtil.encodeHexString(extendedSigningKey)).isEqualTo(expectedCommitteeColdExtendedSigningKey);
    }

    @Test
    void bech32ExtendedSigningKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdExtendedSigningKeyBech32 = "cc_cold_xsk1dp84kjq9qa647wr70e2yedzt8e27kwugh8mfw675re0hgm8p530z3d9230cjjzyyzlq04hn94x9q2m9um2tvp2y8fn7tau9l2wfj5ykxqxtgua0lxpf0lfn44md2afyl7dktyvpkmug9u28p6v452flxeuca0v7w";

        var bech32ExtendedSigningKey = committeeColdKey.bech32ExtendedSigningKey();
        assertThat(bech32ExtendedSigningKey).isEqualTo(expectedCommitteeColdExtendedSigningKeyBech32);
    }

    @Test
    void bech32ExtendedSigningKey_hdPrivateKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair.getPrivateKey());

        var expectedCommitteeColdExtendedSigningKeyBech32 = "cc_cold_xsk1dp84kjq9qa647wr70e2yedzt8e27kwugh8mfw675re0hgm8p530z3d9230cjjzyyzlq04hn94x9q2m9um2tvp2y8fn7tau9l2wfj5ykxqxtgua0lxpf0lfn44md2afyl7dktyvpkmug9u28p6v452flxeuca0v7w";

        var bech32ExtendedSigningKey = committeeColdKey.bech32ExtendedSigningKey();
        assertThat(bech32ExtendedSigningKey).isEqualTo(expectedCommitteeColdExtendedSigningKeyBech32);
    }

    @Test
    void extendedVerificationKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdExtendedVerificationKey = "a9781abfc1604a18ebff6fc35062c000a7a66fdca1323710ed38c1dfc3315beac601968e75ff3052ffa675aedaaea49ff36cb23036df105e28e1d32b4527e6cf";

        var extendedVerificationKey = committeeColdKey.extendedVerificationKey();
        assertThat(HexUtil.encodeHexString(extendedVerificationKey)).isEqualTo(expectedCommitteeColdExtendedVerificationKey);
    }

    @Test
    void bech32ExtendedVerificationKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdExtendedVerificationKeyBech32 = "cc_cold_xvk149up407pvp9p36lldlp4qckqqzn6vm7u5yerwy8d8rqalse3t04vvqvk3e6l7vzjl7n8ttk646jflumvkgcrdhcstc5wr5etg5n7dnc8nqv5d";

        var bech32ExtendedVerificationKey = committeeColdKey.bech32ExtendedVerificationKey();
        assertThat(bech32ExtendedVerificationKey).isEqualTo(expectedCommitteeColdExtendedVerificationKeyBech32);
    }

    @Test
    void bech32ExtendedVerificationKey_hdPublicKey() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair.getPublicKey());

        var expectedCommitteeColdExtendedVerificationKeyBech32 = "cc_cold_xvk149up407pvp9p36lldlp4qckqqzn6vm7u5yerwy8d8rqalse3t04vvqvk3e6l7vzjl7n8ttk646jflumvkgcrdhcstc5wr5etg5n7dnc8nqv5d";

        var bech32ExtendedVerificationKey = committeeColdKey.bech32ExtendedVerificationKey();
        assertThat(bech32ExtendedVerificationKey).isEqualTo(expectedCommitteeColdExtendedVerificationKeyBech32);
    }


    @Test
    void verificationKeyHash() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdVerificationKeyHash = "fefb9596ed670ad2c9978d78fe4eb36ba24cbba0a62fa4cdd0c2dcf5";

        var verificationKeyHash = committeeColdKey.verificationKeyHash();
        assertThat(HexUtil.encodeHexString(verificationKeyHash)).isEqualTo(expectedCommitteeColdVerificationKeyHash);

        //Adding test for bech32VerificationKeyHash here
        var expectedCommitteeColdVerificationKeyHashBech32 = committeeColdKey.bech32VerificationKeyHash();
        var committeeColdVKeyHashBech32 = CommitteeColdKey.bech32VerificationKeyHash(expectedCommitteeColdVerificationKeyHash);
        assertThat(committeeColdVKeyHashBech32).isEqualTo(expectedCommitteeColdVerificationKeyHashBech32);
    }

    @Test
    void bech32VerificationKeyHash() {
        var hdKeyPair = getCommitteeColdKeyPair();
        var committeeColdKey = CommitteeColdKey.from(hdKeyPair);

        var expectedCommitteeColdVerificationKeyHash = "cc_cold_vkh1lmaet9hdvu9d9jvh34u0un4ndw3yewaq5ch6fnwsctw0243cw47";

        var bech32VerificationKeyHash = committeeColdKey.bech32VerificationKeyHash();
        assertThat(bech32VerificationKeyHash).isEqualTo(expectedCommitteeColdVerificationKeyHash);
    }

    @Test
    void bech32ScriptHash1() {
        byte[] scriptHash = HexUtil.decodeHexString("ae6f2a27554d5e6971ef3e933e4f0be7ed7aeb60f6f93dfb81cd6e1c");

        var expectedCommitteeColdScriptHashBech32 = "cc_cold_script14ehj5f64f40xju0086fnunctulkh46mq7munm7upe4hpcwpcatv";

        var bech32ScriptHash = CommitteeColdKey.bech32ScriptHash(scriptHash);
        assertThat(bech32ScriptHash).isEqualTo(expectedCommitteeColdScriptHashBech32);
    }

    @Test
    void bech32ScriptHash2() {
        byte[] scriptHash = HexUtil.decodeHexString("119c20cecfedfdba057292f76bb110afa3ab472f9c35a85daf492316");

        var expectedCommitteeColdScriptHashBech32 = "cc_cold_script1zxwzpnk0ah7m5ptjjtmkhvgs4736k3e0ns66shd0fy33vdauq3j";

        var bech32ScriptHash = CommitteeColdKey.bech32ScriptHash(scriptHash);
        assertThat(bech32ScriptHash).isEqualTo(expectedCommitteeColdScriptHashBech32);
    }

    @Test
    void testCcCold_fromAccPubKeyXvk() {
        String accountXvk = "acct_xvk1kxenc045r0l2u5ethalm89pej406fu3ltk3csy37x9jrx56f8yqquzpltg7ydf7qvxl9kw53q3qzp30799u69yvlvgl0s4pdtpux4yc8mgmff";
        var accountVKBytes = Bech32.decode(accountXvk).data;

        var ccColdDerivationPath = DerivationPath.createCommitteeColdKeyDerivationPathForAccount(0);

        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(accountVKBytes, ccColdDerivationPath.getRole().getValue(), 0); //role = 4

        CommitteeColdKey committeeColdKey = CommitteeColdKey.from(hdPublicKey);
        assertThat(committeeColdKey.id()).isEqualTo("cc_cold1zgvh8vc489udnv2jrzez80hkn6ggre38kpng0tk5vw8rd7gu9rgs9");
    }

    @Test
    void testScriptId() {
        String scriptHash = "00000000000000000000000000000000000000000000000000000000";
        String ccColdId = CommitteeColdKey.scriptId(scriptHash);

        assertThat(ccColdId).isEqualTo("cc_cold1zvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6kflvs");
    }

    private HdKeyPair getCommitteeColdKeyPair() {
        DerivationPath committeeColdKeyDerivationPath = DerivationPath.createCommitteeColdKeyDerivationPathForAccount(0);

        return new CIP1852().getKeyPairFromMnemonic(mnemonic1, committeeColdKeyDerivationPath);
    }

}
