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
import static org.junit.jupiter.api.Assertions.*;

class OutputAmountVerifierTest {

    @Test
    void verify_loveLaceAndAssetAmounts() {
        //Transaction to verify
        Transaction transaction = new Transaction();
        String policyId1 = "a9a750f73f678495ccc869ba300c983814d3079367f0cf909afea8d6";
        String policyId2 = "5ac3d4bdca238105a040a565e5d7e734b7c9e1630aec7650e809e34a";
        String policyId3 = "5ac3d4bdca238105a040a565e5d7e734b7c9e1630aec7650e809e34a";

        String address1 = "addr1q8vqpw8tedwx649q3xlt4g5ezk5wdyegwwwmgx3jzx5nkqkekhfqfxcm4uxzaw7fdc6sq77qlgm280d6mpgrtqazcx4sam04xx";
        String address2 = "addr1wx3937ykmlcaqxkf4z7stxpsfwfn4re7ncy48yu8vutcpxgg67me2";
        String address3 = "addr1wx2527ykmlcaqxkf4z7stxpsfwfn4re7ncy48yu8vutcpxgg67me4";

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

        TransactionOutput transactionOutput4 = TransactionOutput.builder()
                .address(address3)
                .value(Value.builder()
                        .coin(adaToLovelace(7))
                        .build()).build();

        TransactionBody transactionBody = TransactionBody.builder()
                .outputs(List.of(transactionOutput1, transactionOutput2, transactionOutput3, transactionOutput4))
                .build();
        transaction.setBody(transactionBody);

        //Verify Lovelace amount
        Verifier outputAmountVerifier11 = new OutputAmountVerifier(address1, Amount.ada(700.0), "Lovelace amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier11.verify(transaction));

        Verifier outputAmountVerifier12 = new OutputAmountVerifier(address2, Amount.ada(2100), "Lovelace amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier12.verify(transaction));

        Verifier outputAmountVerifier13 = new OutputAmountVerifier(address3, Amount.ada(7), "Lovelace amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier13.verify(transaction));

        //wrong coin value for address1, should throw exception
        Verifier outputAmountVerifier21 = new OutputAmountVerifier(address1, Amount.ada(900.0), "Lovelace amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier21.verify(transaction));

        //wrong value for address2, should throw exception
        Verifier outputAmountVerifier22 = new OutputAmountVerifier(address2, Amount.ada(2000), "Lovelace amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier22.verify(transaction));

        //wrong value for address3, should throw exception
        Verifier outputAmountVerifier23 = new OutputAmountVerifier(address3, Amount.ada(2000), "Lovelace amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier23.verify(transaction));

        //Verify MultiAsset amount
        Verifier outputAmountVerifier31 = new OutputAmountVerifier(address1, Amount.asset(policyId1, "asset1", 45), null);
        assertDoesNotThrow(() -> outputAmountVerifier31.verify(transaction));

        Verifier outputAmountVerifier32 = new OutputAmountVerifier(address2, Amount.asset(policyId1, "asset1", 10), "Asset amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier32.verify(transaction));

        Verifier outputAmountVerifier33 = new OutputAmountVerifier(address2, Amount.asset(policyId2, "asset3", 50), "Asset amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier33.verify(transaction));

        Verifier outputAmountVerifier34 = new OutputAmountVerifier(address1, Amount.asset(policyId1, "asset4", 90), "Asset amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier34.verify(transaction));

        Verifier outputAmountVerifier35 = new OutputAmountVerifier(address1, Amount.asset(policyId1, "asset2", 8), "Asset amount mismatch");
        assertDoesNotThrow(() -> outputAmountVerifier35.verify(transaction));

        //wrong value for address1, should throw exception
        Verifier outputAmountVerifier41 = new OutputAmountVerifier(address1, Amount.asset(policyId1, "asset1", 555), "Asset amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier41.verify(transaction));

        Verifier outputAmountVerifier42 = new OutputAmountVerifier(address2, Amount.asset(policyId2, "asset3", 33), "Asset amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier42.verify(transaction));

        Verifier outputAmountVerifier43 = new OutputAmountVerifier(address1, Amount.asset(policyId1, "asset4", 100), "Asset amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier43.verify(transaction));

        Verifier outputAmountVerifier44 = new OutputAmountVerifier(address1, Amount.asset(policyId1, "asset2", 9), "Asset amount mismatch");
        assertThrows(VerifierException.class, () -> outputAmountVerifier44.verify(transaction));

        Verifier outputAmountVerifier45 = new OutputAmountVerifier(address2, Amount.asset(policyId1, "asset1", 11), "Asset amount mismatch for address2");
        assertThatThrownBy(() -> outputAmountVerifier45.verify(transaction))
                .isInstanceOf(VerifierException.class)
                .hasMessageStartingWith("Asset amount mismatch for address2");
    }
}
