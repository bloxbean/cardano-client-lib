package com.bloxbean.cardano.client.quicktx.verifiers;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Verifier;
import com.bloxbean.cardano.client.quicktx.VerifierException;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TxVerifiersTest {
    String address1 = "addr1q8vqpw8tedwx649q3xlt4g5ezk5wdyegwwwmgx3jzx5nkqkekhfqfxcm4uxzaw7fdc6sq77qlgm280d6mpgrtqazcx4sam04xx";
    String address2 = "addr1wx3937ykmlcaqxkf4z7stxpsfwfn4re7ncy48yu8vutcpxgg67me2";

    @Test
    void outputAmountVerifier_verifyWithNullMsg() {
        Transaction transaction = buildTransaction();
        assertThrows(VerifierException.class, () -> {
            Verifier verifier = TxVerifiers.outputAmountVerifier(address1, Amount.ada(1000));
            verifier.verify(transaction);
        });
    }

    @Test
    void setOutputAmountVerifier_verifyWithCustomMsg() {
        Transaction transaction = buildTransaction();
        assertThatThrownBy( () -> {
            Verifier verifier = TxVerifiers.outputAmountVerifier(address1, Amount.ada(700), "Lovelace amount is not matching")
                    .andThen(TxVerifiers.outputAmountVerifier(address2, Amount.ada(2200), "Lovelace amount is not matching for address2"));
            verifier.verify(transaction);
        }).isInstanceOf(VerifierException.class)
                .hasMessageStartingWith("Lovelace amount is not matching for address2");
    }

    private Transaction buildTransaction() {
        //Transaction to verify
        Transaction transaction = new Transaction();
        String policyId1 = "a9a750f73f678495ccc869ba300c983814d3079367f0cf909afea8d6";
        String policyId2 = "5ac3d4bdca238105a040a565e5d7e734b7c9e1630aec7650e809e34a";

        String address1 = "addr1q8vqpw8tedwx649q3xlt4g5ezk5wdyegwwwmgx3jzx5nkqkekhfqfxcm4uxzaw7fdc6sq77qlgm280d6mpgrtqazcx4sam04xx";
        String address2 = "addr1wx3937ykmlcaqxkf4z7stxpsfwfn4re7ncy48yu8vutcpxgg67me2";

        TransactionOutput transactionOutput1 = TransactionOutput.builder()
                .address(address1)
                .value(Value.builder()
                        .coin(adaToLovelace(100))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policyId1)
                                        .assets(List.of(
                                                Asset.builder()
                                                        .name("asset1")
                                                        .value(BigInteger.valueOf(5))
                                                        .build(),
                                                Asset.builder()
                                                        .name("asset2")
                                                        .value(BigInteger.valueOf(8))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()).build();

        TransactionOutput transactionOutput2 = TransactionOutput.builder()
                .address(address2)
                .value(Value.builder()
                        .coin(adaToLovelace(2100))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policyId1)
                                        .assets(List.of(
                                                Asset.builder()
                                                        .name("asset1")
                                                        .value(BigInteger.valueOf(10))
                                                        .build()
                                        ))
                                        .build(),
                                MultiAsset.builder()
                                        .policyId(policyId2)
                                        .assets(List.of(
                                                Asset.builder()
                                                        .name("asset3")
                                                        .value(BigInteger.valueOf(50))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()).build();

        TransactionOutput transactionOutput3 = TransactionOutput.builder()
                .address(address1)
                .value(Value.builder()
                        .coin(adaToLovelace(600))
                        .multiAssets(List.of(
                                MultiAsset.builder()
                                        .policyId(policyId1)
                                        .assets(List.of(
                                                Asset.builder()
                                                        .name("asset1")
                                                        .value(BigInteger.valueOf(40))
                                                        .build(),
                                                Asset.builder()
                                                        .name("asset4")
                                                        .value(BigInteger.valueOf(90))
                                                        .build()
                                        ))
                                        .build(),
                                MultiAsset.builder()
                                        .policyId(policyId2)
                                        .assets(List.of(
                                                Asset.builder()
                                                        .name("asset1")
                                                        .value(BigInteger.valueOf(60))
                                                        .build(),
                                                Asset.builder()
                                                        .name("asset4")
                                                        .value(BigInteger.valueOf(9))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()).build();

        TransactionBody transactionBody = TransactionBody.builder()
                .outputs(List.of(transactionOutput1, transactionOutput2, transactionOutput3))
                .build();
        transaction.setBody(transactionBody);

        return transaction;
    }
}
