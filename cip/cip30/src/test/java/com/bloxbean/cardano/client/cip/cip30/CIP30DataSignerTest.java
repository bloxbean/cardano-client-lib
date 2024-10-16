package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CIP30DataSignerTest {

    String mnemonic = "nice orient enjoy teach jump office alert inquiry apart unaware seat tumble unveil device have bullet morning eyebrow time image embody divide version uniform";

    Account account = new Account(Networks.testnet(), mnemonic);

    @Test
    void signData() throws DataSignError {
        byte[] payload = "Hello".getBytes();

        Address address = new Address(account.baseAddress());
        DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(address.getBytes(), payload, account);

        System.out.println(dataSignature);
        assertThat(dataSignature).isNotNull();
    }

    @Test
    void signDataAndVerify() throws DataSignError {
        byte[] payload = "Hello".getBytes();

        //Sign
        Address address = new Address(account.baseAddress());
        DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(address.getBytes(), payload, account);

        //Verify
        boolean verify = CIP30DataSigner.INSTANCE.verify(dataSignature);

        assertThat(verify).isTrue();
    }

    @Test
    void verifyNamiSignature() {
        DataSignature dataSignature = new DataSignature()
                .signature("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05")
                .key("a4010103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");

        boolean verified = CIP30DataSigner.INSTANCE.verify(dataSignature);
        assertThat(verified).isTrue();
    }

    @Test
    void verifyNamiSignature_invalidKey() {
        Account someAccount = new Account(Networks.testnet());
        COSEKey coseKey = new COSEKey()
                .keyType(1) //OKP
                .keyId(new Address(someAccount.baseAddress()).getBytes())
                .algorithmId(-8) //EdDSA
                .addOtherHeader(-1, new UnsignedInteger(6)) //crv Ed25519
                .addOtherHeader(-2, new ByteString(someAccount.publicKeyBytes()));

        DataSignature dataSignature = new DataSignature()
                .signature("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05")
                .key(HexUtil.encodeHexString(coseKey.serializeAsBytes())); //set some invalid cosekey

        boolean verified = CIP30DataSigner.INSTANCE.verify(dataSignature);
        assertThat(verified).isFalse();
    }

    @Test
    void verifyHashedLedgerHardwareWallet() {
        DataSignature dataSignature = new DataSignature()
                .signature("84582aa201276761646472657373581de103d205532089ad2f7816892e2ef42849b7b52788e41b3fd43a6e01cfa166686173686564f5581c1c1afc33a1ed48205eadcbbda2fc8e61442af2e04673616f21b7d0385840954858f672e9ca51975655452d79a8f106011e9535a2ebfb909f7bbcce5d10d246ae62df2da3a7790edd8f93723cbdfdffc5341d08135b1a40e7a998e8b2ed06")
                .key("a4010103272006215820c13745be35c2dfc3fa9523140030dda5b5346634e405662b1aae5c61389c55b3");

        boolean verified = CIP30DataSigner.INSTANCE.verify(dataSignature);

        assertThat(verified).isTrue();
    }

    @Test
    void verifySignDataHashedPayload() {
        DataSignature dataSignature = new DataSignature()
                .signature("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05")
                .key("a4010103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");

        boolean verified = CIP30DataSigner.INSTANCE.verify(dataSignature);
        assertThat(verified).isTrue();
    }

    @Test
    void signDataHashedPayload() throws DataSignError {
        byte[] payload = "Hello World".getBytes();

        Address address = new Address(account.baseAddress());
        DataSignature dataSignature = CIP30DataSigner.INSTANCE.signData(address.getBytes(), payload, account, true);

        assertThat(dataSignature).isNotNull();
        assertThat(dataSignature.signature()).isEqualTo("845882a3012704583900327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0e6761646472657373583900327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0ea166686173686564f5581c19790463ef4ad09bdb724e3a6550c640593d4870f6e192ac8147f35d5840d6348538f8c69f5ac30615700b78597dc29795d5fef2aa6165f17ac208b3163b2d2d55405beb6cd8fc66e3beaac1d08b91fae7b9679cc0ae212c65cfe277d608");
        assertThat(dataSignature.key()).isEqualTo("a5010102583900327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0e03272006215820097c8507b71063f99e38147f09eacf76f25576a2ddfac2f40da8feee8dab2d5d");
        assertThat(HexUtil.encodeHexString(dataSignature.address())).isEqualTo("00327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0e");
    }

    @Test
    public void verifySignedHashedPayload() {
        String sig = "845882a3012704583900327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0e6761646472657373583900327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0ea166686173686564f5581c19790463ef4ad09bdb724e3a6550c640593d4870f6e192ac8147f35d5840d6348538f8c69f5ac30615700b78597dc29795d5fef2aa6165f17ac208b3163b2d2d55405beb6cd8fc66e3beaac1d08b91fae7b9679cc0ae212c65cfe277d608";
        String key = "a5010102583900327d065c4c135860b9ac6a758c9ef032100a724865998a6b1b8219f3d11c3061dfc0c16e14f5b6779fef214eab7aaa3dffdc5e30c1272f0e03272006215820097c8507b71063f99e38147f09eacf76f25576a2ddfac2f40da8feee8dab2d5d";

        DataSignature dataSig = new DataSignature().signature(sig).key(key);

        boolean isVerified = CIP30DataSigner.INSTANCE.verify(dataSig);

        assertThat(isVerified).isTrue();
    }

}
