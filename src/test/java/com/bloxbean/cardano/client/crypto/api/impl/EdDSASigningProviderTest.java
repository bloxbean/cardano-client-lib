package com.bloxbean.cardano.client.crypto.api.impl;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.Utils;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EdDSASigningProviderTest {

    @Test
    void signExtended() {
        String msg = "hello";
        String pvtKey = "78bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";
        String pubKey = "9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8";

        SigningProvider signingProvider = new EdDSASigningProvider();
        byte[] signature = signingProvider.signExtended(msg.getBytes(StandardCharsets.UTF_8), HexUtil.decodeHexString(pvtKey), HexUtil.decodeHexString(pubKey));

        String signatureHex = HexUtil.encodeHexString(signature);

        assertThat(signatureHex).isEqualTo("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
    }

    @Test
    public void verifyExtended() throws Exception {
        byte[] signature = Utils.hexToBytes("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        byte[] publicKey = Utils.hexToBytes("9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(message);

        assertThat(sgr.verify(signature)).isEqualTo(true);
    }

    @Test
    public void verifyExtendedInvalidKey() throws Exception {
        byte[] signature = Utils.hexToBytes("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        byte[] publicKey = Utils.hexToBytes("INVALID103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(message);

        assertThat(sgr.verify(signature)).isEqualTo(false);
    }

    @Test
    public void verifyExtendedInvalidMessage() throws Exception {
        byte[] signature = Utils.hexToBytes("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        byte[] publicKey = Utils.hexToBytes("9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] message = "ola".getBytes(StandardCharsets.UTF_8);

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(message);

        assertThat(sgr.verify(signature)).isEqualTo(false);
    }

    @Test
    public void sign() {
        byte[] seed = Utils.hexToBytes("0000000000000000000000000000000000000000000000000000000000000000");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);
        SigningProvider signingProvider = new EdDSASigningProvider();
        byte[] signature = signingProvider.sign(message, seed);
        byte[] expectedSignature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        assertThat(signature).isEqualTo(expectedSignature);
    }

    @Test
    public void verify() throws Exception {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(message);

        assertThat(sgr.verify(signature)).isEqualTo(true);
    }

    @Test
    public void verifyInvalidKey() throws Exception {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("INVALIDcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a secret message".getBytes(StandardCharsets.UTF_8);

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(message);

        assertThat(sgr.verify(signature)).isEqualTo(false);
    }

    @Test
    public void verifyInvalidMessage() throws Exception {
        byte[] signature = Utils.hexToBytes("94825896c7075c31bcb81f06dba2bdcd9dcf16e79288d4b9f87c248215c8468d475f429f3de3b4a2cf67fe17077ae19686020364d6d4fa7a0174bab4a123ba0f");
        byte[] publicKey = Utils.hexToBytes("3b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29");
        byte[] message = "This is a modified message".getBytes(StandardCharsets.UTF_8);

        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(message);

        assertThat(sgr.verify(signature)).isEqualTo(false);
    }

    @Test
    void signAndVerify() throws Exception{
        byte[] msg = "eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc".getBytes(StandardCharsets.UTF_8);
        byte[] privateKey = Base64.getUrlDecoder().decode("nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A");
        byte[] publicKey = Base64.getUrlDecoder().decode("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo");

        // sign
        SigningProvider signingProvider = new EdDSASigningProvider();
        byte[] signature = signingProvider.sign(msg, privateKey);
        String actualSignature = Base64.getUrlEncoder().encodeToString(signature).replace("=", "");
        String expectedSignature = "hgyY0il_MGCjP0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg";
        assertThat(actualSignature).isEqualTo(expectedSignature);

        // verify
        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
        EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        PublicKey vKey = new EdDSAPublicKey(pubKey);
        sgr.initVerify(vKey);
        sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        sgr.update(msg);
        assertThat(sgr.verify(signature)).isEqualTo(true);
    }

    @Test
    void sign_throwErrorWhenInvalidPrivateKey() {
        String msg = "hello";
        String pvtKey = "bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";

        Assertions.assertThrows(CryptoException.class, () -> {
            SigningProvider signingProvider = new EdDSASigningProvider();
            signingProvider.sign(msg.getBytes(StandardCharsets.UTF_8), HexUtil.decodeHexString(pvtKey));
        });
    }

    @Test
    public void sign_checkSerialized() throws CborDeserializationException, CborSerializationException, CborException {
        TransactionBody txnBody = new TransactionBody();

        TransactionInput txnInput = new TransactionInput();

        txnInput.setTransactionId("dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d");
        txnInput.setIndex(1);

        List<TransactionInput> inputList = new ArrayList<>();
        inputList.add(txnInput);
        txnBody.setInputs(inputList);

        //Total : 994632035
        TransactionOutput txnOutput =  new TransactionOutput();
        txnOutput.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput.setValue(new Value(new BigInteger("5000000"), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        changeOutput.setValue(new Value(new BigInteger("989264070"), null));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput);
        outputs.add(changeOutput);

        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger("367965"));
        txnBody.setTtl(26194586);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);

        CBORMetadata metadata = new CBORMetadata();

        metadata.putNegative(BigInteger.valueOf(1022222222), BigInteger.valueOf(-1481433243434L));

        AuxiliaryData data = AuxiliaryData.builder()
                .metadata(metadata)
                .build();
        transaction.setAuxiliaryData(data);

        Account account = new Account();

        Transaction signedTxn1 = account.sign(transaction);
        //Sign again
        Transaction signTxn2 = account.sign(transaction);

        assertThat(signedTxn1.serializeToHex()).isEqualTo(signTxn2.serializeToHex());

        //Clear witness and check with original txn
        signTxn2.setWitnessSet(new TransactionWitnessSet());
        assertThat(signTxn2.serializeToHex()).isEqualTo(transaction.serializeToHex());
    }
}
