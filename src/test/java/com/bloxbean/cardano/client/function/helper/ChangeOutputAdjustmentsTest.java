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
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
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
}
