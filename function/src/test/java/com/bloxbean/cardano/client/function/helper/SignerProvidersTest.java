package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;

class SignerProvidersTest {

    @Test
    void signerFromAccounts() throws Exception {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        Transaction signedTxn = SignerProviders.signerFrom(account1, account2)
                .sign(null, buildTransaction());

        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(2);
    }

    @Test
    void signerFromSecretKey() throws Exception {
        SecretKey sk1 = KeyGenUtil.generateKey().getSkey();
        SecretKey sk2 = KeyGenUtil.generateKey().getSkey();
        SecretKey sk3 = KeyGenUtil.generateKey().getSkey();

        Transaction signedTxn = SignerProviders.signerFrom(sk1, sk2, sk3)
                .sign(null, buildTransaction());

        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(3);
    }

    @Test
    void signerFromPolicies() throws Exception {
        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("1", 3);
        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("2", 4);

        Transaction signedTxn = SignerProviders.signerFrom(policy1, policy2)
                .sign(null, buildTransaction());

        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(7);
    }

    @Test
    void signerFromHdKeyPairs() throws Exception {
       Account account1 = new Account(Networks.testnet());
       Account account2 = new Account(Networks.testnet());

        Transaction signedTxn = SignerProviders.signerFrom(account1.stakeHdKeyPair(), account2.stakeHdKeyPair())
                .andThen(SignerProviders.signerFrom(account1))
                .sign(null, buildTransaction());

        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(3);
    }

    @Test
    void signerFromAccountStakeKeys() throws Exception {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        Transaction signedTxn = SignerProviders.stakeKeySignerFrom(account1, account2)
                .andThen(SignerProviders.signerFrom(account1))
                .sign(null, buildTransaction());

        assertThat(signedTxn.getWitnessSet().getVkeyWitnesses()).hasSize(3);
    }

    private Transaction buildTransaction() throws Exception {
        List<TransactionInput> inputs = List.of(
                new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0),
                new TransactionInput("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e", 1)
        );

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qpcsaey8mw7dkkd4ssdqp5gznft2gp78y2ess8qzgu94zmg7680k7djdkftdzfl79shcf3dmdmfngsltd27mvj5v79hq6m7sf2";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("test", 1);
        String unit = policy.getPolicyId() + HexUtil.encodeHexString("token1".getBytes(StandardCharsets.UTF_8));
        MultiAsset ma = AssetUtil.getMultiAssetFromUnitAndAmount(unit, BigInteger.valueOf(800));
        MultiAsset changeMa = AssetUtil.getMultiAssetFromUnitAndAmount(unit, BigInteger.valueOf(200));

        List<TransactionOutput> outputs = List.of(
                TransactionOutput.builder()
                        .address(receiver1)
                        .value(Value.builder()
                                .coin(ONE_ADA.multiply(BigInteger.valueOf(5)))
                                .multiAssets(List.of(ma))
                                .build()
                        )
                        .build(),
                TransactionOutput.builder()
                        .address(receiver2)
                        .value(Value.builder()
                                .coin(ONE_ADA.multiply(BigInteger.valueOf(3)))
                                .multiAssets(List.of(changeMa))
                                .build()
                        )
                        .build()
        );

        Transaction transaction = new Transaction();
        TransactionBody body = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .ttl(6500000)
                .fee(BigInteger.valueOf(17_000)).build();
        transaction.setBody(body);
        transaction.setAuxiliaryData(AuxiliaryData.builder()
                .plutusV1Scripts(List.of())
                .build());

        return transaction;
    }
}
