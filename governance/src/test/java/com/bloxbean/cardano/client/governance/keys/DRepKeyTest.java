package com.bloxbean.cardano.client.governance.keys;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

//Test Vector : https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-1.md
public class DRepKeyTest {
    private String mnemonic1 = "test walk nut penalty hip pave soap entry language right filter choice";

    @Test
   void testDRepSigningAndVerificationKeys() {
        HdKeyPair drepKeyPair = getDRepKeyPair();
        DRepKey dRepKey = DRepKey.from(drepKeyPair);

        String expectedDRepSigningKey = "a8e57a8e0a68b7ab50c6cd13e8e0811718f506d34fca674e12740fdf73e1a45e612fa30b7e4bbe9883958dcf365de1e6c1607c33172c5d3d7754f3294e450925";
        String expectedDRepSigningKeyBech32 = "drep_sk14rjh4rs2dzm6k5xxe5f73cypzuv02pknfl9xwnsjws8a7ulp530xztarpdlyh05csw2cmnekths7dstq0se3wtza84m4fueffezsjfglsqmad";

        String expectedDRepVerificationKey = "f74d7ac30513ac1825715fd0196769761fca6e7f69de33d04ef09a0c417a752b";
        String expectedDRepVerificationKeyBech32 = "drep_vk17axh4sc9zwkpsft3tlgpjemfwc0u5mnld80r85zw7zdqcst6w54sdv4a4e";

        byte[] singingKey = dRepKey.signingKey();
        String bech32SigningKey = dRepKey.bech32SigningKey();

        byte[] verificationKey = dRepKey.verificationKey();
        String bech32VerificationKey = dRepKey.bech32VerificationKey();

        assertThat(HexUtil.encodeHexString(singingKey)).isEqualTo(expectedDRepSigningKey);
        assertThat(bech32SigningKey).isEqualTo(expectedDRepSigningKeyBech32);

        assertThat(HexUtil.encodeHexString(verificationKey)).isEqualTo(expectedDRepVerificationKey);
        assertThat(bech32VerificationKey).isEqualTo(expectedDRepVerificationKeyBech32);
    }


    @Test
    void testDRepExtendedSigningAndVerificationKeys() {
        HdKeyPair drepKeyPair = getDRepKeyPair();
        DRepKey dRepKey = DRepKey.from(drepKeyPair);

        String expectedDRepExtendedSigningKey = "a8e57a8e0a68b7ab50c6cd13e8e0811718f506d34fca674e12740fdf73e1a45e612fa30b7e4bbe9883958dcf365de1e6c1607c33172c5d3d7754f3294e4509251d8411029969123371cde99fb075730f1da4fd41ee7acefba7e211f0e20c91ca";
        String expectedDRepExtendedSigningKeyBech32 = "drep_xsk14rjh4rs2dzm6k5xxe5f73cypzuv02pknfl9xwnsjws8a7ulp530xztarpdlyh05csw2cmnekths7dstq0se3wtza84m4fueffezsjfgassgs9xtfzgehrn0fn7c82uc0rkj06s0w0t80hflzz8cwyry3eg9066uj";

        String expectedDRepExtendedVerificationKey = "f74d7ac30513ac1825715fd0196769761fca6e7f69de33d04ef09a0c417a752b1d8411029969123371cde99fb075730f1da4fd41ee7acefba7e211f0e20c91ca";
        String expectedDRepExtendedVerificationKeyBech32 = "drep_xvk17axh4sc9zwkpsft3tlgpjemfwc0u5mnld80r85zw7zdqcst6w543mpq3q2vkjy3nw8x7n8asw4es78dyl4q7u7kwlwn7yy0sugxfrjs6z25qe";

        byte[] extendedSingingKey = dRepKey.extendedSigningKey();
        String extendedBech32SigningKey = dRepKey.bech32ExtendedSigningKey();

        byte[] extVerificationKey = dRepKey.extendedVerificationKey();
        String extBech32VerificationKey = dRepKey.bech32ExtendedVerificationKey();

        assertThat(HexUtil.encodeHexString(extendedSingingKey)).isEqualTo(expectedDRepExtendedSigningKey);
        assertThat(extendedBech32SigningKey).isEqualTo(expectedDRepExtendedSigningKeyBech32);

        assertThat(HexUtil.encodeHexString(extVerificationKey)).isEqualTo(expectedDRepExtendedVerificationKey);
        assertThat(extBech32VerificationKey).isEqualTo(expectedDRepExtendedVerificationKeyBech32);
    }

    @Test
    void testDRepSigningKeys_hdPrivateKey() {
        HdKeyPair drepKeyPair = getDRepKeyPair();
        DRepKey dRepKey = DRepKey.from(drepKeyPair.getPrivateKey());

        String expectedDRepSigningKey = "a8e57a8e0a68b7ab50c6cd13e8e0811718f506d34fca674e12740fdf73e1a45e612fa30b7e4bbe9883958dcf365de1e6c1607c33172c5d3d7754f3294e450925";
        String expectedDRepSigningKeyBech32 = "drep_sk14rjh4rs2dzm6k5xxe5f73cypzuv02pknfl9xwnsjws8a7ulp530xztarpdlyh05csw2cmnekths7dstq0se3wtza84m4fueffezsjfglsqmad";

        String expectedDRepExtendedSigningKey = "a8e57a8e0a68b7ab50c6cd13e8e0811718f506d34fca674e12740fdf73e1a45e612fa30b7e4bbe9883958dcf365de1e6c1607c33172c5d3d7754f3294e4509251d8411029969123371cde99fb075730f1da4fd41ee7acefba7e211f0e20c91ca";
        String expectedDRepExtendedSigningKeyBech32 = "drep_xsk14rjh4rs2dzm6k5xxe5f73cypzuv02pknfl9xwnsjws8a7ulp530xztarpdlyh05csw2cmnekths7dstq0se3wtza84m4fueffezsjfgassgs9xtfzgehrn0fn7c82uc0rkj06s0w0t80hflzz8cwyry3eg9066uj";

        byte[] singingKey = dRepKey.signingKey();
        String bech32SigningKey = dRepKey.bech32SigningKey();

        byte[] extendedSingingKey = dRepKey.extendedSigningKey();
        String extendedBech32SigningKey = dRepKey.bech32ExtendedSigningKey();

        assertThat(HexUtil.encodeHexString(singingKey)).isEqualTo(expectedDRepSigningKey);
        assertThat(bech32SigningKey).isEqualTo(expectedDRepSigningKeyBech32);

        assertThat(HexUtil.encodeHexString(extendedSingingKey)).isEqualTo(expectedDRepExtendedSigningKey);
        assertThat(extendedBech32SigningKey).isEqualTo(expectedDRepExtendedSigningKeyBech32);
    }

    @Test
    void testDRepVerificationKeys_hdPublicKey() {
        HdKeyPair drepKeyPair = getDRepKeyPair();
        DRepKey dRepKey = DRepKey.from(drepKeyPair.getPublicKey());

        String expectedDRepVerificationKey = "f74d7ac30513ac1825715fd0196769761fca6e7f69de33d04ef09a0c417a752b";
        String expectedDRepVerificationKeyBech32 = "drep_vk17axh4sc9zwkpsft3tlgpjemfwc0u5mnld80r85zw7zdqcst6w54sdv4a4e";

        String expectedDRepExtendedVerificationKey = "f74d7ac30513ac1825715fd0196769761fca6e7f69de33d04ef09a0c417a752b1d8411029969123371cde99fb075730f1da4fd41ee7acefba7e211f0e20c91ca";
        String expectedDRepExtendedVerificationKeyBech32 = "drep_xvk17axh4sc9zwkpsft3tlgpjemfwc0u5mnld80r85zw7zdqcst6w543mpq3q2vkjy3nw8x7n8asw4es78dyl4q7u7kwlwn7yy0sugxfrjs6z25qe";

        byte[] verificationKey = dRepKey.verificationKey();
        String bech32VerificationKey = dRepKey.bech32VerificationKey();

        byte[] extVerificationKey = dRepKey.extendedVerificationKey();
        String extBech32VerificationKey = dRepKey.bech32ExtendedVerificationKey();

        assertThat(HexUtil.encodeHexString(verificationKey)).isEqualTo(expectedDRepVerificationKey);
        assertThat(bech32VerificationKey).isEqualTo(expectedDRepVerificationKeyBech32);

        assertThat(HexUtil.encodeHexString(extVerificationKey)).isEqualTo(expectedDRepExtendedVerificationKey);
        assertThat(extBech32VerificationKey).isEqualTo(expectedDRepExtendedVerificationKeyBech32);
    }

    @Test
    void testBech32VkhAndVKeyHash() {
        HdKeyPair drepKeyPair = getDRepKeyPair();
        DRepKey dRepKey = DRepKey.from(drepKeyPair);

        String expectedVKeyHash = "a5b45515a3ff8cb7c02ce351834da324eb6dfc41b5779cb5e6b832aa";
        String expectedDRepId = "drep_vkh15k6929drl7xt0spvudgcxndryn4kmlzpk4meed0xhqe254czjh2";

        byte[] vKeyHash = dRepKey.verificationKeyHash();
        String bech32Vkh = dRepKey.bech32VerificationKeyHash();

        assertThat(HexUtil.encodeHexString(vKeyHash)).isEqualTo(expectedVKeyHash);
        assertThat(bech32Vkh).isEqualTo(expectedDRepId);
    }

    @Test
    void testDRepScriptHashBytes1() {
        String dRepScriptId = DRepKey.bech32ScriptHash(HexUtil.decodeHexString("d0657126dbf0c135a7224d91ca068f5bf769af6d1f1df0bce5170ec5"));

        String expectedDrepScriptId = "drep_script16pjhzfkm7rqntfezfkgu5p50t0mkntmdruwlp089zu8v29l95rg";
        assertThat(dRepScriptId).isEqualTo(expectedDrepScriptId);
    }

    @Test
    void testDRepScriptHash2() {
        String dRepScriptId = DRepKey.bech32ScriptHash(HexUtil.decodeHexString("ae5acf0511255d647c84b3184a2d522bf5f6c5b76b989f49bd383bdd"));

        String expectedDrepScriptId = "drep_script14edv7pg3y4wkglyykvvy5t2j906ld3dhdwvf7jda8qaa63d5kf4";
        assertThat(dRepScriptId).isEqualTo(expectedDrepScriptId);
    }

    @Test
    void testDRepId_fromAccPubKeyXvk() {
        String accountXvk = "acct_xvk1kxenc045r0l2u5ethalm89pej406fu3ltk3csy37x9jrx56f8yqquzpltg7ydf7qvxl9kw53q3qzp30799u69yvlvgl0s4pdtpux4yc8mgmff";
        var accountVKBytes = Bech32.decode(accountXvk).data;

        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(accountVKBytes, 3, 0);

        var drepKey = DRepKey.from(hdPublicKey);
        assertThat(drepKey.dRepId()).isEqualTo("drep1ytf29wgf5llmd22ankjewqrdcjvsx9zvyru2qlpwjyr042gajpq70");
    }

    @Test
    void testDRepId_fromAccPubKeyXpub() {
        String accountXvk = "xpub16nuts3mqurnek523dk35j37hwscsj90a098f7t77v4htwh5ana3ced0tv948hucq537c7l5ypd9d7vvgmtn6wy28xyj9wnp9ftkzqqqxe7kkj";
        var accountVKBytes = Bech32.decode(accountXvk).data;

        var drepDerivationPath = DerivationPath.createDRepKeyDerivationPathForAccount(0);

        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(accountVKBytes, drepDerivationPath.getRole().getValue(), drepDerivationPath.getIndex().getValue());

        var drepKey = DRepKey.from(hdPublicKey);
        assertThat(drepKey.dRepId()).isEqualTo("drep1ytmnsfv8axjemak2jt26p5h9580csq0c3q5mfm26anr2zugd75lws");
    }


    @Test
    void testDRepId() {
        String accountXvk = "acct_xvk1kxenc045r0l2u5ethalm89pej406fu3ltk3csy37x9jrx56f8yqquzpltg7ydf7qvxl9kw53q3qzp30799u69yvlvgl0s4pdtpux4yc8mgmff";
        var accountVKBytes = Bech32.decode(accountXvk).data;

        HdPublicKey hdPublicKey = new CIP1852().getPublicKeyFromAccountPubKey(accountVKBytes, 3, 0);

        var drepKey = DRepKey.from(hdPublicKey);
        assertThat(drepKey.dRepId()).isEqualTo("drep1ytf29wgf5llmd22ankjewqrdcjvsx9zvyru2qlpwjyr042gajpq70");
    }

    @Test
    void fromVerificationKey() throws CborSerializationException {
        //https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/test-vector-2.md
        VerificationKey verificationKey = VerificationKey.create(HexUtil.decodeHexString("70344fe0329bbacbb33921e945daed181bd66889333eb73f3bb10ad8e4669976"));
        String bech32Vkh = DRepKey.bech32VerificationKeyHash(verificationKey);
        assertThat(bech32Vkh).isEqualTo("drep_vkh1rmf3ftma8lu0e5eqculttpfy6a6v5wrn8msqa09gr0tr590rpdl");
    }

    private HdKeyPair getDRepKeyPair() {
        DerivationPath drepDerivationPath = DerivationPath.createDRepKeyDerivationPathForAccount(0);

        return new CIP1852().getKeyPairFromMnemonic(mnemonic1, drepDerivationPath);
    }

}
