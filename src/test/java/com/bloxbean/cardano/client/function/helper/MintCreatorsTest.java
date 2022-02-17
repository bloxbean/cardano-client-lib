package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.PolicyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MintCreatorsTest {

    @Mock
    BackendService backendService;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void mintCreator_whenScriptNotIncludedInAuxData() throws Exception {

        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("policy1", 1);
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId(policy1.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("abc")
                                .value(BigInteger.valueOf(100))
                                .build()
                )).build();

        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("policy2", 1);
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId(policy2.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz")
                                .value(BigInteger.valueOf(200))
                                .build()
                )).build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();

        //Just adding a random input/output
        transaction.getBody().getInputs().add(
                new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0)
        );

        transaction.getBody().getOutputs().add(
                TransactionOutput.builder()
                        .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                        .value(Value.builder().coin(BigInteger.valueOf(1000)).build())
                        .build()
        );


        MintCreators.mintCreator(policy1, multiAsset1)
                .andThen(MintCreators.mintCreator(policy2, multiAsset2))
                .build(context, transaction);


        //No update to existing inputs & outputs
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        assertThat(transaction.getBody().getMint()).contains(multiAsset1, multiAsset2);
        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(policy1.getPolicyScript(), policy2.getPolicyScript());
        assertThat(transaction.getAuxiliaryData()).isNull();
    }

    @Test
    void mintCreator_whenScriptIncludedInAuxData() throws Exception {

        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("policy1", 1);
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId(policy1.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("abc")
                                .value(BigInteger.valueOf(100))
                                .build()
                )).build();

        Policy policy2 = PolicyUtil.createMultiSigScriptAllPolicy("policy2", 1);
        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId(policy2.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz")
                                .value(BigInteger.valueOf(200))
                                .build()
                )).build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();

        //Just adding a random input/output
        transaction.getBody().getInputs().add(
                new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0)
        );

        transaction.getBody().getOutputs().add(
                TransactionOutput.builder()
                        .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                        .value(Value.builder().coin(BigInteger.valueOf(1000)).build())
                        .build()
        );


        MintCreators.mintCreator(policy1, multiAsset1, true)
                .andThen(MintCreators.mintCreator(policy2, multiAsset2, true))
                .build(context, transaction);


        //No update to existing inputs & outputs
        assertThat(transaction.getBody().getInputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs()).hasSize(1);

        assertThat(transaction.getBody().getMint()).contains(multiAsset1, multiAsset2);
        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(policy1.getPolicyScript(), policy2.getPolicyScript());
        assertThat(transaction.getAuxiliaryData().getNativeScripts()).contains(policy1.getPolicyScript(), policy2.getPolicyScript());
    }

    @Test
    void mintCreator_whenSamePolicyIsUsedMultipleTimes_shouldContainsOneCopyOfPolicyScriptInWitness() throws Exception {

        Policy policy1 = PolicyUtil.createMultiSigScriptAllPolicy("policy1", 1);
        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId(policy1.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("abc")
                                .value(BigInteger.valueOf(100))
                                .build()
                )).build();

        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId(policy1.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz")
                                .value(BigInteger.valueOf(200))
                                .build()
                )).build();

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();

        MintCreators.mintCreator(policy1, multiAsset1, true)
                .andThen(MintCreators.mintCreator(policy1, multiAsset2, true))
                .build(context, transaction);

        assertThat(transaction.getBody().getMint()).contains(multiAsset1, multiAsset2);
        assertThat(transaction.getWitnessSet().getNativeScripts()).hasSize(1);
        assertThat(transaction.getAuxiliaryData().getNativeScripts()).hasSize(1);
        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(policy1.getPolicyScript());
        assertThat(transaction.getAuxiliaryData().getNativeScripts()).contains(policy1.getPolicyScript());
    }
}

