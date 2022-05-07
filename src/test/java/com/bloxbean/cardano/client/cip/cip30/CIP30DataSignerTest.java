package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.common.model.Networks;
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

}

