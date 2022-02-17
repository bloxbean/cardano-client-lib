package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.*;
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
import java.util.Optional;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeOutputAdjustmentsTest extends BaseTest {

    @Mock
    BackendService backendService;

    @Mock
    EpochService epochService;

    @Mock
    UtxoSelectionStrategy utxoSelectionStrategy;

    @Mock
    UtxoSelector utxoSelector;

    @Mock
    FeeCalculationService feeCalculationService;

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
    void adjustFor_onlyLovelaceOutput() throws ApiException, CborSerializationException {
        BigInteger expectedFee = BigInteger.valueOf(19000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);

        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(5))).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(10))).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.of(additionalUtxos.get(0)));
        given(utxoSelector.findFirst(eq(changeAddress), any())).willReturn(Optional.of(additionalUtxos.get(0)));

        Transaction transaction = new Transaction();
        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        transaction.getBody().setFee(BigInteger.valueOf(17000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);

        //Build txn
        txBuilderContext.build(transaction, txBuilder);

        //asserts
        assertThat(transaction.getBody().getInputs()).hasSize(3);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);

        //assert inputs
        assertThat(transaction.getBody().getInputs()).contains(ti1, ti2);
        assertThat(transaction.getBody().getInputs().get(2)).isEqualTo(new TransactionInput(additionalUtxos.get(0).getTxHash(), additionalUtxos.get(0).getOutputIndex()));

        //assert outputs
        assertThat(transaction.getBody().getOutputs().get(0)).isEqualTo(to1);
        assertThat(transaction.getBody().getOutputs().get(1)).isEqualTo(to2);
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(changeAddress);
        assertThat(transaction.getBody().getOutputs().get(2).getValue().getCoin()).isEqualTo(BigInteger.valueOf(5946000));

        //assert new txn fee
        assertThat(transaction.getBody().getFee()).isEqualTo(expectedFee);
    }

    @Test
    void adjustFor_onlyLovelaceOutput_withMultipleAdditionalUtxos() throws ApiException, CborSerializationException {
        BigInteger expectedFee = BigInteger.valueOf(18000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);

        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(BigInteger.valueOf(1_000_000)).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(BigInteger.valueOf(1_500_000)).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.empty());
        given(utxoSelectionStrategy.selectUtxos(eq(changeAddress), eq(LOVELACE), any(), anySet())).willReturn(additionalUtxos);

        Transaction transaction = new Transaction();
        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948_000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        transaction.getBody().setFee(BigInteger.valueOf(17_000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);
        txBuilderContext.setUtxoSelectionStrategy(utxoSelectionStrategy);

        //Build txn
        txBuilderContext.build(transaction, txBuilder);

        //asserts
        assertThat(transaction.getBody().getInputs()).hasSize(4);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);

        //assert inputs
        assertThat(transaction.getBody().getInputs()).contains(ti1, ti2);
        assertThat(transaction.getBody().getInputs().get(2)).isEqualTo(new TransactionInput(additionalUtxos.get(0).getTxHash(), additionalUtxos.get(0).getOutputIndex()));
        assertThat(transaction.getBody().getInputs().get(3)).isEqualTo(new TransactionInput(additionalUtxos.get(1).getTxHash(), additionalUtxos.get(1).getOutputIndex()));

        //assert outputs
        assertThat(transaction.getBody().getOutputs().get(0)).isEqualTo(to1);
        assertThat(transaction.getBody().getOutputs().get(1)).isEqualTo(to2);
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(changeAddress);
        assertThat(transaction.getBody().getOutputs().get(2).getValue().getCoin()).isEqualTo(BigInteger.valueOf(3_447_000));

        //assert new txn fee
        assertThat(transaction.getBody().getFee()).isEqualTo(expectedFee);
    }

    @Test
    void adjustFor_whenMultipleChangeOutputLessThanMinAdaFound_throwsException() throws ApiException, CborSerializationException {
        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(5))).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(10))).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.of(additionalUtxos.get(0)));
        given(utxoSelector.findFirst(eq(changeAddress), any())).willReturn(Optional.of(additionalUtxos.get(0)));

        Transaction transaction = new Transaction();
        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(450000)).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        transaction.getBody().setFee(BigInteger.valueOf(17000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);

        assertThrows(TxBuildException.class, () -> {
            //Build txn
            txBuilderContext.build(transaction, txBuilder);
        });
    }

    @Test
    void adjustFor_onlyLovelaceOutput_multipleFeeCalculationRetry() throws ApiException, CborSerializationException {
        BigInteger expectedFee = BigInteger.valueOf(5000000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);

        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(BigInteger.valueOf(1_000_000)).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(BigInteger.valueOf(1_500_000)).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.empty());
        given(utxoSelectionStrategy.selectUtxos(eq(changeAddress), eq(LOVELACE), any(), anySet())).willReturn(additionalUtxos);

        Transaction transaction = new Transaction();
        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948_000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        transaction.getBody().setFee(BigInteger.valueOf(17_000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);
        txBuilderContext.setUtxoSelectionStrategy(utxoSelectionStrategy);

        //Build txn
        txBuilderContext.build(transaction, txBuilder);

        //asserts
        assertThat(transaction.getBody().getInputs()).hasSize(8);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);

        //assert outputs
        assertThat(transaction.getBody().getOutputs().get(0)).isEqualTo(to1);
        assertThat(transaction.getBody().getOutputs().get(1)).isEqualTo(to2);
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(changeAddress);
        assertThat(transaction.getBody().getOutputs().get(2).getValue().getCoin()).isEqualTo(BigInteger.valueOf(3_465_000));

        //assert new txn fee
        assertThat(transaction.getBody().getFee()).isEqualTo(expectedFee);
    }

    @Test
    void adjustFor_multipleFeeCalculationRetry_failedWithMaxRetry() throws ApiException, CborSerializationException {
        BigInteger expectedFee = BigInteger.valueOf(9000000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);

        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(BigInteger.valueOf(1_000_000)).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(BigInteger.valueOf(1_500_000)).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.empty());
        given(utxoSelectionStrategy.selectUtxos(eq(changeAddress), eq(LOVELACE), any(), anySet())).willReturn(additionalUtxos);

        Transaction transaction = new Transaction();
        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948_000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        transaction.getBody().setFee(BigInteger.valueOf(17_000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);
        txBuilderContext.setUtxoSelectionStrategy(utxoSelectionStrategy);

        assertThrows(TxBuildException.class, () -> {
            //Build txn
            txBuilderContext.build(transaction, txBuilder);
        });
    }

    @Test
    void adjustFor_withContractOutputWithRedeemers_shouldUpdateRedeemerIndex() throws ApiException, CborSerializationException {
        BigInteger expectedFee = BigInteger.valueOf(19000);
        BigInteger scriptFee = BigInteger.valueOf(12000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);
        given(feeCalculationService.calculateScriptFee(any())).willReturn(scriptFee);

        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(5))).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(10))).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.of(additionalUtxos.get(0)));
        given(utxoSelector.findFirst(eq(changeAddress), any())).willReturn(Optional.of(additionalUtxos.get(0)));

        Transaction transaction = new Transaction();

        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("635262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        TransactionInput sti1 = new TransactionInput("335262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput sti2 = new TransactionInput("782262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2, sti1, sti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        //Script parameters
        PlutusScript script = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("590a15590a120100003323322332232323332223233322232333333332222222232333222323333222232323322323332223233322232323322332232323333322222332233223322332233223322223223223232533530333330083333573466e1cd55cea8032400046eb4d5d09aab9e500723504935304a335738921035054310004b499263333573466e1cd55cea8022400046eb4d5d09aab9e500523504935304a3357389201035054310004b499263333573466e1cd55cea8012400046601664646464646464646464646666ae68cdc39aab9d500a480008cccccccccc064cd409c8c8c8cccd5cd19b8735573aa004900011980f981d1aba15002302c357426ae8940088d4164d4c168cd5ce249035054310005b49926135573ca00226ea8004d5d0a80519a8138141aba150093335502e75ca05a6ae854020ccd540b9d728169aba1500733502704335742a00c66a04e66aa0a8098eb4d5d0a8029919191999ab9a3370e6aae754009200023350213232323333573466e1cd55cea80124000466a05266a084eb4d5d0a80118239aba135744a00446a0ba6a60bc66ae712401035054310005f49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502733504275a6ae854008c11cd5d09aba2500223505d35305e3357389201035054310005f49926135573ca00226ea8004d5d09aba2500223505935305a3357389201035054310005b49926135573ca00226ea8004d5d0a80219a813bae35742a00666a04e66aa0a8eb88004d5d0a801181c9aba135744a00446a0aa6a60ac66ae71241035054310005749926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180f181d9aba135573ca00646666ae68cdc3a801240084603a608a6ae84d55cf280211999ab9a3370ea00690011180e98181aba135573ca00a46666ae68cdc3a80224000460406eb8d5d09aab9e50062350503530513357389201035054310005249926499264984d55cea80089baa001357426ae8940088d4124d4c128cd5ce249035054310004b49926104a1350483530493357389201035054350004a4984d55cf280089baa0011375400226ea80048848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004848888c010014848888c00c014848888c008014848888c004014800448c88c008dd6000990009aa81a111999aab9f0012500e233500d30043574200460066ae880080cc8c8c8c8cccd5cd19b8735573aa006900011998039919191999ab9a3370e6aae754009200023300d303135742a00466a02605a6ae84d5d1280111a81b1a981b99ab9c491035054310003849926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235032353033335738921035054310003449926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540b888c8cccd55cf80112804919a80419aa81898031aab9d5002300535573ca00460086ae8800c0b84d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0526a605466ae712401035054310002b499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0466a604866ae71241035054310002549926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d407cd4c080cd5ce24810350543100021499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8111a981199ab9c490103505431000244992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d4068d4c06ccd5ce249035054310001c499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d407cd4c080cd5ce2481035054310002149926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4040d4c044cd5ce2490350543100012499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500a35300b3357389201035054310000c499264984d55cea80089baa001212230020032122300100320011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc008008004ccc888c8c8ccc888ccc888cccccccc88888888cc88ccccc88888cccc8888cc88cc88cc88ccc888cc88cc88ccc888cc88cc88cc88cc88888cc894cd4c0e4008400440e8ccd40d540d800d205433350355036002481508848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800488848ccc00401000c00880044488008488488cc00401000c48004448848cc00400c0084480048848cc00400c008800448488c00800c44880044800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004484888c00c010448880084488800448004848888c010014848888c00c014848888c008014848888c00401480048848cc00400c0088004848888888c01c0208848888888cc018024020848888888c014020488888880104888888800c8848888888cc0080240208848888888cc00402402080048488c00800c888488ccc00401401000c80048488c00800c8488c00400c800448004488ccd5cd19b87002001005004122002122001200101")
                .build();

        PlutusData redeemerData1 = BigIntPlutusData.of(1);
        PlutusData datumData1 = BigIntPlutusData.of(1);

        PlutusData redeemerData2 = BigIntPlutusData.of(2);
        PlutusData datumData2 = BigIntPlutusData.of(2);

        Redeemer redeemer1 = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData1)
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(3434343))
                        .steps(BigInteger.valueOf(353535344)).build()).build();

        Redeemer redeemer2 = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData2)
                .index(BigInteger.valueOf(2))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(3434343))
                        .steps(BigInteger.valueOf(353535344)).build()).build();

        transaction.getWitnessSet().setRedeemers(List.of(redeemer1, redeemer2));
        transaction.getWitnessSet().setPlutusDataList(List.of(datumData1, datumData2));
        transaction.getWitnessSet().setPlutusScripts(List.of(script, script));

        transaction.getBody().setFee(BigInteger.valueOf(17000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);

        //Build txn
        txBuilderContext.build(transaction, txBuilder);

        //Check Redeemer Index.. after sorting during  adjustment
        assertThat(transaction.getWitnessSet().getRedeemers().get(0).getIndex()).isEqualTo(0);
        assertThat(transaction.getWitnessSet().getRedeemers().get(1).getIndex()).isEqualTo(3);

        //asserts
        assertThat(transaction.getBody().getInputs()).hasSize(5);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);

        //assert inputs
        assertThat(transaction.getBody().getInputs()).contains(ti1, ti2, sti1, sti2,
                new TransactionInput(additionalUtxos.get(0).getTxHash(), additionalUtxos.get(0).getOutputIndex()));

        //assert outputs
        assertThat(transaction.getBody().getOutputs().get(0)).isEqualTo(to1);
        assertThat(transaction.getBody().getOutputs().get(1)).isEqualTo(to2);
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(changeAddress);
    }

    @Test
    void adjustFor_withContractOutputWithRedeemers_AndMultipleRetryDurinAdustment_shouldUpdateRedeemerIndex() throws ApiException, CborSerializationException {
        BigInteger expectedFee = BigInteger.valueOf(5000000);
        BigInteger scriptFee = BigInteger.valueOf(12000);
        given(feeCalculationService.calculateFee(any(Transaction.class))).willReturn(expectedFee);
        given(feeCalculationService.calculateScriptFee(any())).willReturn(scriptFee);

        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        List<Utxo> additionalUtxos = List.of(
                Utxo.builder()
                        .txHash("555262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(0)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(5))).build()))
                        .build(),
                Utxo.builder()
                        .txHash("444262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9").outputIndex(1)
                        .amount(List.of(Amount.builder().unit(LOVELACE).quantity(ONE_ADA.multiply(BigInteger.valueOf(10))).build()))
                        .build()
        );

        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.of(additionalUtxos.get(0)));
        given(utxoSelector.findFirst(eq(changeAddress), any())).willReturn(Optional.of(additionalUtxos.get(0)));

        Transaction transaction = new Transaction();

        //Just adding a random input/output
        TransactionInput ti1 = new TransactionInput("635262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput ti2 = new TransactionInput("982262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        TransactionInput sti1 = new TransactionInput("615262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 0);
        TransactionInput sti2 = new TransactionInput("782262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9", 1);
        transaction.getBody().getInputs().addAll(List.of(ti1, ti2, sti1, sti2));

        //Outputs
        TransactionOutput to1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(3))).build())
                .build();
        TransactionOutput to2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(ONE_ADA.multiply(BigInteger.valueOf(4))).build())
                .build();
        TransactionOutput to3 = TransactionOutput.builder()
                .address(changeAddress)
                .value(Value.builder().coin(BigInteger.valueOf(948000)).build())
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        //Script parameters
        PlutusScript script = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("590a15590a120100003323322332232323332223233322232333333332222222232333222323333222232323322323332223233322232323322332232323333322222332233223322332233223322223223223232533530333330083333573466e1cd55cea8032400046eb4d5d09aab9e500723504935304a335738921035054310004b499263333573466e1cd55cea8022400046eb4d5d09aab9e500523504935304a3357389201035054310004b499263333573466e1cd55cea8012400046601664646464646464646464646666ae68cdc39aab9d500a480008cccccccccc064cd409c8c8c8cccd5cd19b8735573aa004900011980f981d1aba15002302c357426ae8940088d4164d4c168cd5ce249035054310005b49926135573ca00226ea8004d5d0a80519a8138141aba150093335502e75ca05a6ae854020ccd540b9d728169aba1500733502704335742a00c66a04e66aa0a8098eb4d5d0a8029919191999ab9a3370e6aae754009200023350213232323333573466e1cd55cea80124000466a05266a084eb4d5d0a80118239aba135744a00446a0ba6a60bc66ae712401035054310005f49926135573ca00226ea8004d5d0a8011919191999ab9a3370e6aae7540092000233502733504275a6ae854008c11cd5d09aba2500223505d35305e3357389201035054310005f49926135573ca00226ea8004d5d09aba2500223505935305a3357389201035054310005b49926135573ca00226ea8004d5d0a80219a813bae35742a00666a04e66aa0a8eb88004d5d0a801181c9aba135744a00446a0aa6a60ac66ae71241035054310005749926135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135573ca00226ea8004d5d0a8011919191999ab9a3370ea00290031180f181d9aba135573ca00646666ae68cdc3a801240084603a608a6ae84d55cf280211999ab9a3370ea00690011180e98181aba135573ca00a46666ae68cdc3a80224000460406eb8d5d09aab9e50062350503530513357389201035054310005249926499264984d55cea80089baa001357426ae8940088d4124d4c128cd5ce249035054310004b49926104a1350483530493357389201035054350004a4984d55cf280089baa0011375400226ea80048848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004848888c010014848888c00c014848888c008014848888c004014800448c88c008dd6000990009aa81a111999aab9f0012500e233500d30043574200460066ae880080cc8c8c8c8cccd5cd19b8735573aa006900011998039919191999ab9a3370e6aae754009200023300d303135742a00466a02605a6ae84d5d1280111a81b1a981b99ab9c491035054310003849926135573ca00226ea8004d5d0a801999aa805bae500a35742a00466a01eeb8d5d09aba25002235032353033335738921035054310003449926135744a00226aae7940044dd50009110919980080200180110009109198008018011000899aa800bae75a224464460046eac004c8004d540b888c8cccd55cf80112804919a80419aa81898031aab9d5002300535573ca00460086ae8800c0b84d5d08008891001091091198008020018900089119191999ab9a3370ea002900011a80418029aba135573ca00646666ae68cdc3a801240044a01046a0526a605466ae712401035054310002b499264984d55cea80089baa001121223002003112200112001232323333573466e1cd55cea8012400046600c600e6ae854008dd69aba135744a00446a0466a604866ae71241035054310002549926135573ca00226ea80048848cc00400c00880048c8cccd5cd19b8735573aa002900011bae357426aae7940088d407cd4c080cd5ce24810350543100021499261375400224464646666ae68cdc3a800a40084a00e46666ae68cdc3a8012400446a014600c6ae84d55cf280211999ab9a3370ea00690001280511a8111a981199ab9c490103505431000244992649926135573aa00226ea8004484888c00c0104488800844888004480048c8cccd5cd19b8750014800880188cccd5cd19b8750024800080188d4068d4c06ccd5ce249035054310001c499264984d55ce9baa0011220021220012001232323232323333573466e1d4005200c200b23333573466e1d4009200a200d23333573466e1d400d200823300b375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c46601a6eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc048c050d5d0a8049bae357426ae8940248cccd5cd19b875006480088c050c054d5d09aab9e500b23333573466e1d401d2000230133016357426aae7940308d407cd4c080cd5ce2481035054310002149926499264992649926135573aa00826aae79400c4d55cf280109aab9e500113754002424444444600e01044244444446600c012010424444444600a010244444440082444444400644244444446600401201044244444446600201201040024646464646666ae68cdc3a800a400446660106eb4d5d0a8021bad35742a0066eb4d5d09aba2500323333573466e1d400920002300a300b357426aae7940188d4040d4c044cd5ce2490350543100012499264984d55cea80189aba25001135573ca00226ea80048488c00800c888488ccc00401401000c80048c8c8cccd5cd19b875001480088c018dd71aba135573ca00646666ae68cdc3a80124000460106eb8d5d09aab9e500423500a35300b3357389201035054310000c499264984d55cea80089baa001212230020032122300100320011122232323333573466e1cd55cea80124000466aa016600c6ae854008c014d5d09aba25002235007353008335738921035054310000949926135573ca00226ea8004498480048004448848cc00400c008448004448c8c00400488cc00cc008008004ccc888c8c8ccc888ccc888cccccccc88888888cc88ccccc88888cccc8888cc88cc88cc88ccc888cc88cc88ccc888cc88cc88cc88cc88888cc894cd4c0e4008400440e8ccd40d540d800d205433350355036002481508848cc00400c0088004888888888848cccccccccc00402c02802402001c01801401000c00880048848cc00400c008800488848ccc00401000c00880044488008488488cc00401000c48004448848cc00400c0084480048848cc00400c008800448488c00800c44880044800448848cc00400c0084800448848cc00400c0084800448848cc00400c00848004484888c00c010448880084488800448004848888c010014848888c00c014848888c008014848888c00401480048848cc00400c0088004848888888c01c0208848888888cc018024020848888888c014020488888880104888888800c8848888888cc0080240208848888888cc00402402080048488c00800c888488ccc00401401000c80048488c00800c8488c00400c800448004488ccd5cd19b87002001005004122002122001200101")
                .build();

        PlutusData redeemerData1 = BigIntPlutusData.of(1);
        PlutusData datumData1 = BigIntPlutusData.of(1);

        PlutusData redeemerData2 = BigIntPlutusData.of(2);
        PlutusData datumData2 = BigIntPlutusData.of(2);

        Redeemer redeemer1 = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData1)
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(3434343))
                        .steps(BigInteger.valueOf(353535344)).build()).build();

        Redeemer redeemer2 = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData2)
                .index(BigInteger.valueOf(2))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(3434343))
                        .steps(BigInteger.valueOf(353535344)).build()).build();

        transaction.getWitnessSet().setRedeemers(List.of(redeemer1, redeemer2));
        transaction.getWitnessSet().setPlutusDataList(List.of(datumData1, datumData2));
        transaction.getWitnessSet().setPlutusScripts(List.of(script, script));

        transaction.getBody().setFee(BigInteger.valueOf(17000));

        TxBuilder txBuilder = ChangeOutputAdjustments.adjustChangeOutput(changeAddress, changeAddress, 1);

        //Initialize TxBuilderContext and set mock backendService and utxoSeletor
        TxBuilderContext txBuilderContext = TxBuilderContext.init(backendService);
        txBuilderContext.setUtxoSelector(utxoSelector);

        //Build txn
        txBuilderContext.build(transaction, txBuilder);

        //Check Redeemer Index.. after sorting during  adjustment
        assertThat(transaction.getWitnessSet().getRedeemers().get(0).getIndex()).isEqualTo(2);
        assertThat(transaction.getWitnessSet().getRedeemers().get(1).getIndex()).isEqualTo(4);

        //asserts
        assertThat(transaction.getBody().getInputs()).hasSize(6);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);
    }

}
