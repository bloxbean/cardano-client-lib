package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.PolicyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeCalculatorsTest extends BaseTest {
    @Mock
    BackendService backendService;

    @Mock
    FeeCalculationService feeCalculationService;

    @Mock
    EpochService epochService;

    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);

        given(backendService.getEpochService()).willReturn(epochService);
        given(backendService.getFeeCalculationService()).willReturn(feeCalculationService);
        given(epochService.getProtocolParameters()).willReturn(Result.success(protocolParams.toString()).withValue(protocolParams).code(200));
    }

    @Test
    void feeCalculator_whenAdaTransfer() throws Exception {
        BigInteger expectedFee = BigInteger.valueOf(18000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);

        //prepare transaction
        List<TransactionInput> inputs = List.of(
          new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0),
          new TransactionInput("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e", 1)
        );

        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        Account account = new Account(Networks.testnet());
        String sender = account.baseAddress();

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("test", 1);
        String unit = policy.getPolicyId() + HexUtil.encodeHexString("token1".getBytes(StandardCharsets.UTF_8));
        MultiAsset ma = AssetUtil.getMultiAssetFromUnitAndAmount(unit, BigInteger.valueOf(800));
        MultiAsset changeMa = AssetUtil.getMultiAssetFromUnitAndAmount(unit, BigInteger.valueOf(200));

        List<TransactionOutput> outputs = List.of(
                TransactionOutput.builder()
                        .address(receiver)
                        .value(Value.builder()
                            .coin(ONE_ADA.multiply(BigInteger.valueOf(5)))
                                .multiAssets(List.of(ma))
                                .build()
                        )
                    .build(),
                TransactionOutput.builder()
                        .address(sender)
                        .value(Value.builder()
                                .coin(ONE_ADA.multiply(BigInteger.valueOf(3)))
                                .multiAssets(List.of(changeMa))
                                .build()
                        )
                        .build()
        );

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();
        TransactionBody body = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .ttl(6500000).build();

        transaction.setBody(body);
        transaction.setValid(true);

        //apply
        TxBuilder txBuilder = FeeCalculators.feeCalculator(sender, 1);
        txBuilder.build(context, transaction);

        //assert
        assertThat(transaction.getBody().getFee()).isEqualTo(expectedFee);
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin())
                .isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(3)).subtract(expectedFee));
    }

    @Test
    void feeCalculator_withScript() throws Exception {
        BigInteger expectedFee = BigInteger.valueOf(18000);
        BigInteger scriptFee = BigInteger.valueOf(1000);

        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);
        given(feeCalculationService.calculateScriptFee(ArgumentMatchers.<List<ExUnits>>any())).willReturn(scriptFee);

        //prepare transaction
        List<TransactionInput> inputs = List.of(
                new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0),
                new TransactionInput("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e", 1)
        );

        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        Account account = new Account(Networks.testnet());
        String sender = account.baseAddress();

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("test", 1);
        String unit = policy.getPolicyId() + HexUtil.encodeHexString("token1".getBytes(StandardCharsets.UTF_8));
        MultiAsset ma = AssetUtil.getMultiAssetFromUnitAndAmount(unit, BigInteger.valueOf(800));
        MultiAsset changeMa = AssetUtil.getMultiAssetFromUnitAndAmount(unit, BigInteger.valueOf(200));

        List<TransactionOutput> outputs = List.of(
                TransactionOutput.builder()
                        .address(receiver)
                        .value(Value.builder()
                                .coin(ONE_ADA.multiply(BigInteger.valueOf(5)))
                                .multiAssets(List.of(ma))
                                .build()
                        )
                        .build(),
                TransactionOutput.builder()
                        .address(sender)
                        .value(Value.builder()
                                .coin(ONE_ADA.multiply(BigInteger.valueOf(3)))
                                .multiAssets(List.of(changeMa))
                                .build()
                        )
                        .build()
        );

        TxBuilderContext context = new TxBuilderContext(backendService);
        Transaction transaction = new Transaction();
        TransactionBody body = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .ttl(6500000).build();
        transaction.setBody(body);
        transaction.setAuxiliaryData(AuxiliaryData.builder()
                .plutusScripts(List.of())
                .build());
        transaction.setWitnessSet(new TransactionWitnessSet());
        Redeemer redeemer = Redeemer.builder()
                .index(BigInteger.ZERO)
                .tag(RedeemerTag.Spend)
                .data(BigIntPlutusData.of(7))
                .exUnits(ExUnits.builder()
                    .mem(BigInteger.valueOf(20001))
                        .steps(BigInteger.valueOf(899))
                        .build()
                ).build();
        transaction.getWitnessSet().getRedeemers().add(redeemer);
        transaction.setValid(true);

        //apply
        TxBuilder txBuilder = FeeCalculators.feeCalculator(sender, 1);
        txBuilder.build(context, transaction);

        //assert
        assertThat(transaction.getBody().getFee()).isEqualTo(expectedFee.add(scriptFee));
        assertThat(transaction.getBody().getOutputs().get(1).getValue().getCoin())
                .isEqualTo(ONE_ADA.multiply(BigInteger.valueOf(3)).subtract(expectedFee.add(scriptFee)));
    }

}
