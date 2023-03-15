package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.function.BaseTest;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceTxBuildersTest extends BaseTest {
    @Mock
    UtxoSupplier utxoSupplier;

    @Mock
    UtxoSelector utxoSelector;

    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        protocolParamJsonFile = "protocol-params.json";
        protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
    }

    @Test
    void balanceTx_withAdditionalInputs_whenAdjustChangeOutputs_withExpectedTotalCollateral() throws Exception {
        //Prepare data
        String receiver1 = "addr_test1qpstze8klh30rt5vz6cw6pz4ztjs04y22a2x77yhenarxvwga4dulkd2070d93xwhnaj4d5mhkxn3fkpzzzj3qespmlsskd4ev";
        String receiver2 = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        String changeAddress = "addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka";

        //Additional utxos used during change output adjustment
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

        //Utxos used as collateral
        List<Utxo> collateralUtxos = List.of(
                Utxo.builder()
                        .txHash("d5975c341088ca1c0ed2384a3139d34a1de4b31ef6c9cd3ac0c4eb55108fdf85")
                        .outputIndex(1)
                        .amount(List.of(
                                new Amount(LOVELACE, adaToLovelace(20)),
                                new Amount("606bba5da14fcffd08a8e58217ce6bdc38a6250669db5c285c8d2f8f56657279426967436f696e", BigInteger.valueOf(200000)),
                                new Amount("34250edd1e9836f5378702fbf9416b709bc140e04f668cc3552085184154414441636f696e", BigInteger.valueOf(400))
                        ))
                        .build(),
                Utxo.builder()
                        .txHash("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                        .outputIndex(1)
                        .amount(List.of(
                                new Amount(LOVELACE, adaToLovelace(5)),
                                new Amount("606bba5da14fcffd08a8e58217ce6bdc38a6250669db5c285c8d2f8f56657279426967436f696e", BigInteger.valueOf(100000)),
                                new Amount("66250edd1e9836f5378702fbf9416b709bc140e04f668cc3552085184154414441636f696e", BigInteger.valueOf(500))
                        ))
                        .build()
        );

        //Mocks
        //Mock utxoSelector service to return first utxo from our list. This will be called during changeoutput adjustment
        given(utxoSelector.findFirst(eq(changeAddress), any(), any())).willReturn(Optional.of(additionalUtxos.get(0)));
        given(utxoSelector.findFirst(eq(changeAddress), any())).willReturn(Optional.of(additionalUtxos.get(0)));

        //Initialize transaction object
        //Add random inputs/outputs. But the outputs are not enough according to min ada requirement after fee deduction
        //So, change adjustment will be triggered with a new input and then collateral output calculation
        Transaction transaction = new Transaction();
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
                .value(Value.builder().coin(BigInteger.valueOf(948000)).build()) //Insufficient ada
                .build();
        transaction.getBody().getOutputs().addAll(List.of(to1, to2, to3));

        //Crate collateral Tx builder and balance Tx builder
        TxBuilder collateralTxBuilder = CollateralBuilders.collateralOutputs(changeAddress, collateralUtxos);
        //Balance Tx : Fee Calculation + Change Ouput Adjustment + Total collateral calculation
        TxBuilder balanceTxBuilder = BalanceTxBuilders.balanceTx(changeAddress, 1);

        //Initialize TxBuilderContext with mock utxoSelector & protocol params
        TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParams);
        txBuilderContext.setUtxoSelector(utxoSelector);

        //Build txn
        txBuilderContext.build(transaction, collateralTxBuilder); //apply collateral txbuilder
        txBuilderContext.build(transaction, balanceTxBuilder);    //apply balance txbuilder

        //Asserts
        //asserts size-- A third input should be added to the transaction to cover required ada
        assertThat(transaction.getBody().getInputs()).hasSize(3);
        assertThat(transaction.getBody().getOutputs()).hasSize(3);

        //assert inputs
        assertThat(transaction.getBody().getInputs()).contains(ti1, ti2);
        assertThat(transaction.getBody().getInputs().get(2)).isEqualTo(new TransactionInput(additionalUtxos.get(0).getTxHash(), additionalUtxos.get(0).getOutputIndex()));

        //assert outputs
        assertThat(transaction.getBody().getOutputs().get(0)).isEqualTo(to1);
        assertThat(transaction.getBody().getOutputs().get(1)).isEqualTo(to2);
        assertThat(transaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(changeAddress);

        //assert calculated txn fee
        assertThat(transaction.getBody().getFee()).isGreaterThan(new BigInteger("18000"));

        //check total collaterals
        BigInteger expectedTotalCollateral = new BigDecimal(transaction.getBody().getFee())
                .multiply(protocolParams.getCollateralPercent().divide(BigDecimal.valueOf(100)))
                .setScale(0, RoundingMode.CEILING).toBigInteger();
        assertThat(transaction.getBody().getTotalCollateral()).isEqualTo(expectedTotalCollateral);
    }
}
