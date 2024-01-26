package com.bloxbean.cardano.client.transaction;

import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.VkeyWitness;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSignerTest {

    @Test
    void sign_withSecretKey_transaction() throws CborSerializationException, CborDeserializationException, AddressExcepion {
        String txnHex = "83a4008282582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002018258208e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68000184825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a3aa51029a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a14019232882583900c93b6cac143fe60f8914f44a899f5329433ccec3d53721ef350a0fd8cb873402c73ad8f239f76fb559bb4e3bcff22b310b01eadd3ce205e71a007a1200825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a3b1b1aa3021a000b3aba031a018fb29aa0f6";
        String sk = "ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807";

        SecretKey secretKey = SecretKey.create(HexUtil.decodeHexString(sk));
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));

        transaction = TransactionSigner.INSTANCE.sign(transaction, secretKey);

        VkeyWitness vkeyWitness = transaction.getWitnessSet().getVkeyWitnesses().get(0);
        String vkeyHex = HexUtil.encodeHexString(vkeyWitness.getVkey());
        String signatureHex = HexUtil.encodeHexString(vkeyWitness.getSignature());

        System.out.println("VKey : " + vkeyHex);
        System.out.println("Key Hash : " + KeyGenUtil.getKeyHash(VerificationKey.create(vkeyWitness.getVkey())));
        System.out.println("Signature: " + signatureHex);

        assertThat(vkeyHex).isEqualTo("60209269377f220cdecdc6d5ad42d9b04e58ce74b349efb396ee46adaeb956f3");
        assertThat(signatureHex).isEqualTo("cd9f8e70a09f24328ee6c14053a38a6a654d31e9e58a9c6c44848e4592265237ce3604eda0cb1812028c3e6b04c66ccc64a1d2685d98e0567477cbc33a4c2f0f");
    }

    @Test
    void sign_withSecretKey_transactionBytes() throws CborSerializationException, CborDeserializationException, AddressExcepion {
        String txnHex = "83a4008282582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002018258208e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68000184825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a3aa51029a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a14019232882583900c93b6cac143fe60f8914f44a899f5329433ccec3d53721ef350a0fd8cb873402c73ad8f239f76fb559bb4e3bcff22b310b01eadd3ce205e71a007a1200825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a3b1b1aa3021a000b3aba031a018fb29aa0f6";
        String sk = "ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807";

        SecretKey secretKey = SecretKey.create(HexUtil.decodeHexString(sk));
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));

        var transactionBytes = TransactionSigner.INSTANCE.sign(transaction.serialize(), secretKey);
        transaction = Transaction.deserialize(transactionBytes);

        VkeyWitness vkeyWitness = transaction.getWitnessSet().getVkeyWitnesses().get(0);
        String vkeyHex = HexUtil.encodeHexString(vkeyWitness.getVkey());
        String signatureHex = HexUtil.encodeHexString(vkeyWitness.getSignature());

        System.out.println("VKey : " + vkeyHex);
        System.out.println("Key Hash : " + KeyGenUtil.getKeyHash(VerificationKey.create(vkeyWitness.getVkey())));
        System.out.println("Signature: " + signatureHex);

        assertThat(vkeyHex).isEqualTo("60209269377f220cdecdc6d5ad42d9b04e58ce74b349efb396ee46adaeb956f3");
        assertThat(signatureHex).isEqualTo("cd9f8e70a09f24328ee6c14053a38a6a654d31e9e58a9c6c44848e4592265237ce3604eda0cb1812028c3e6b04c66ccc64a1d2685d98e0567477cbc33a4c2f0f");
    }

    @Test
    void sign_withAdditionalKey_transactionBytes() throws Exception {
        String txHex = "84a50082825820bf23db27c0f908de9cab0087867cc3457396cc30fe04d11219e2e9cf010747db0182582097cec126d14f763197d8b6411d1f2f6045e30919b822a4cf56ea3e734b2b6e88010184825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c42344821a0016e360a1581c2474352285a03c836fd6e54836f6e8281494803410ab7c02bd95c427a1474d7941737365741907d0825839007d1fcb835da61dd128c9283510bd26c2d8c6d0439e938f14b5ef941e248d073c7065dc990d6a98455f4514f4190a310b60ccda5801cfc36d1a01143fdd8258390000c906a94e1a04453b33ec71bbbb9cdfa704f9a7a82c30ed7ee2b94d5bde4d09d8c1f745ef568a56335bc410aae4128cd296eba026312b4b1a0020805082583900a89c059647839b018c2fdaf8ae90329cac293dc6bc917fa6750f5ac0f1fd43a58e272fee8c0735f17d65f8d6c180cc62bdd0250b8a1b5f4f821b0000000fc7509273a1581c3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712a24d000de14043495036382d4e4654014f506c757475734d696e74546f6b656e190fa0021a00031d0d0758203a99497a4e5cdeb32dabe52e34ce5aa64297dbe1be481951d048109c5a0875eb09a1581c2474352285a03c836fd6e54836f6e8281494803410ab7c02bd95c427a1474d7941737365741907d0a20083825820f8c12a466dfc1e8665ad1b50f434578cb6f89f23ac4bae8f152f5524955ff829584094c72c56f4ee376eba182129fee894789fad36da9870d103a7a115f22e7a0fa548b3ac8f6aa39cd9fec4fa11942426e61606c20be168e5da37d1b8b00d1a2e0e825820212cc53581b27abeee3f564588ded10f46161320285d57426c9c85ea071e97d458400624999ef4f13497bab552bd1d814441da55bafef3065be43646f57f3fdc04afbba848363fed9edc650333c2fa64d6cd94ce1bcd1d47ffabe03aae43971c290982582006e717f015593822e7d0fccd38c3e2cc88ae8794d5ab3c0968bc1beb20330f295840308788613e31d536f480544798e38ad86e9e2bd3e8e7bd055a16493f6700b64b501f28aadc25071048c945b273339e21f5399b10060586e91cf4f2183a3dee090181830301818200581c6e0374dbd346ab7f40ad5f86bab8cd41e62787bace376bacab2c696cf5a11902a2a1636d7367816a4d696e74696e67207478";
        byte[] txBytes = HexUtil.decodeHexString(txHex);
        var originalTransaction = Transaction.deserialize(txBytes);

        //Generate key pair from mnemonic
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";
        byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair keyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);

        //Sign transaction
        var signedTransactionBytes = TransactionSigner.INSTANCE.sign(txBytes, keyPair);
        var signedTransaction = Transaction.deserialize(signedTransactionBytes);

        assertThat(signedTransaction).isNotNull();
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().size()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().size() + 1);
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(1).getVkey()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(1).getVkey());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(1).getSignature()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(1).getSignature());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(2).getVkey()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(2).getVkey());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(2).getSignature()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(2).getSignature());

        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(3).getVkey()).isEqualTo(keyPair.getPublicKey().getKeyData());
        assertThat(signedTransaction.getAuxiliaryData().serialize()).isEqualTo(originalTransaction.getAuxiliaryData().serialize());
    }

    @Test
    void sign_withAdditionalKey_transaction() throws Exception {
        String txHex = "84a50082825820bf23db27c0f908de9cab0087867cc3457396cc30fe04d11219e2e9cf010747db0182582097cec126d14f763197d8b6411d1f2f6045e30919b822a4cf56ea3e734b2b6e88010184825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c42344821a0016e360a1581c2474352285a03c836fd6e54836f6e8281494803410ab7c02bd95c427a1474d7941737365741907d0825839007d1fcb835da61dd128c9283510bd26c2d8c6d0439e938f14b5ef941e248d073c7065dc990d6a98455f4514f4190a310b60ccda5801cfc36d1a01143fdd8258390000c906a94e1a04453b33ec71bbbb9cdfa704f9a7a82c30ed7ee2b94d5bde4d09d8c1f745ef568a56335bc410aae4128cd296eba026312b4b1a0020805082583900a89c059647839b018c2fdaf8ae90329cac293dc6bc917fa6750f5ac0f1fd43a58e272fee8c0735f17d65f8d6c180cc62bdd0250b8a1b5f4f821b0000000fc7509273a1581c3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712a24d000de14043495036382d4e4654014f506c757475734d696e74546f6b656e190fa0021a00031d0d0758203a99497a4e5cdeb32dabe52e34ce5aa64297dbe1be481951d048109c5a0875eb09a1581c2474352285a03c836fd6e54836f6e8281494803410ab7c02bd95c427a1474d7941737365741907d0a20083825820f8c12a466dfc1e8665ad1b50f434578cb6f89f23ac4bae8f152f5524955ff829584094c72c56f4ee376eba182129fee894789fad36da9870d103a7a115f22e7a0fa548b3ac8f6aa39cd9fec4fa11942426e61606c20be168e5da37d1b8b00d1a2e0e825820212cc53581b27abeee3f564588ded10f46161320285d57426c9c85ea071e97d458400624999ef4f13497bab552bd1d814441da55bafef3065be43646f57f3fdc04afbba848363fed9edc650333c2fa64d6cd94ce1bcd1d47ffabe03aae43971c290982582006e717f015593822e7d0fccd38c3e2cc88ae8794d5ab3c0968bc1beb20330f295840308788613e31d536f480544798e38ad86e9e2bd3e8e7bd055a16493f6700b64b501f28aadc25071048c945b273339e21f5399b10060586e91cf4f2183a3dee090181830301818200581c6e0374dbd346ab7f40ad5f86bab8cd41e62787bace376bacab2c696cf5a11902a2a1636d7367816a4d696e74696e67207478";
        byte[] txBytes = HexUtil.decodeHexString(txHex);
        var originalTransaction = Transaction.deserialize(txBytes);

        //Generate key pair from mnemonic
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";
        byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair keyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);

        //Sign transaction
        var signedTransaction = TransactionSigner.INSTANCE.sign(originalTransaction, keyPair);

        assertThat(signedTransaction).isNotNull();
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().size()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().size() + 1);
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(1).getVkey()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(1).getVkey());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(1).getSignature()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(1).getSignature());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(2).getVkey()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(2).getVkey());
        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(2).getSignature()).isEqualTo(originalTransaction.getWitnessSet().getVkeyWitnesses().get(2).getSignature());

        assertThat(signedTransaction.getWitnessSet().getVkeyWitnesses().get(3).getVkey()).isEqualTo(keyPair.getPublicKey().getKeyData());
        assertThat(signedTransaction.getAuxiliaryData().serialize()).isEqualTo(originalTransaction.getAuxiliaryData().serialize());
    }

}
