package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScriptTxTest extends QuickTxBaseTest {
    @Mock
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;

    String sender1 = new Account().baseAddress();
    String receiver1 = new Account().baseAddress();

    @BeforeEach
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.openMocks(this);
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;
    }

    @Test
    void collectFrom_withFeePayerAsReciever_differentCollateralPayer() {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        String scriptAddr = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();

        List<Utxo> scriptUtxos = List.of(
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(100)))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build()
        );

        PlutusData plutusData = BigIntPlutusData.of(2);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(scriptUtxos.get(0), plutusData)
                .payToAddress(receiver1, Amount.ada(100))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddr, plutusData);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .collateralPayer(sender1)
                .feePayer(receiver1)
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(1);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(100)
                .subtract(transaction.getBody().getFee()));
    }

    @Test
    void script_unlock_withMinting_successful() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        String scriptAddr = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();

        List<Utxo> scriptUtxos = List.of(
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(100)))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build()
        );

        Asset newAsset = new Asset("newAsset", BigInteger.valueOf(1000));

        PlutusData plutusData = BigIntPlutusData.of(2);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(scriptUtxos.get(0), plutusData)
                .payToAddress(receiver1, Amount.ada(80))
                .attachSpendingValidator(plutusScript)
                .mintAsset(plutusScript, newAsset, BigIntPlutusData.of(1), sender1)
                .withChangeAddress(scriptAddr, plutusData);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .collateralPayer(sender1)
                .feePayer(receiver1)
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(3);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(80)
                .subtract(transaction.getBody().getFee()));

        assertThat(getAssetAmountForAddress(transaction.getBody().getOutputs(), sender1, plutusScript.getPolicyId(), "newAsset").get())
                .isEqualTo(BigInteger.valueOf(1000));
        assertThat(transaction.getBody().getMint()).contains(
                MultiAsset.builder()
                        .policyId(plutusScript.getPolicyId())
                        .assets(List.of(
                                new Asset("newAsset", BigInteger.valueOf(1000))
                        )).build());
    }


    @Test
    void script_unlock_withBurning_successful() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        String scriptAddr = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();

        List<Utxo> scriptUtxos = List.of(
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(0)
                        .amount(List.of(
                                Amount.ada(100),
                                Amount.asset(plutusScript.getPolicyId(), "ExistingAsset", BigInteger.valueOf(2000)),
                                Amount.asset(plutusScript.getPolicyId(), "ExistingAsset1", BigInteger.valueOf(300))
                        )).inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build()
        );

        Asset newAsset = new Asset("newAsset", BigInteger.valueOf(1000));

        PlutusData plutusData = BigIntPlutusData.of(2);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(scriptUtxos.get(0), plutusData)
                .payToAddress(receiver1, Amount.ada(80))
                .attachSpendingValidator(plutusScript)
                .mintAsset(plutusScript, newAsset, BigIntPlutusData.of(1), sender1)
                .mintAsset(plutusScript, new Asset("ExistingAsset", BigInteger.valueOf(1000).negate()), BigIntPlutusData.of(1))
                .mintAsset(plutusScript, List.of(new Asset("ExistingAsset1", BigInteger.valueOf(100).negate())), BigIntPlutusData.of(1))
                .withChangeAddress(scriptAddr, plutusData);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .collateralPayer(sender1)
                .feePayer(receiver1)
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(3);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(80)
                .subtract(transaction.getBody().getFee()));

        assertThat(getAssetAmountForAddress(transaction.getBody().getOutputs(), sender1, plutusScript.getPolicyId(), "newAsset").get())
                .isEqualTo(BigInteger.valueOf(1000));
        assertThat(getAssetAmountForAddress(transaction.getBody().getOutputs(), scriptAddr, plutusScript.getPolicyId(), "ExistingAsset").get())
                .isEqualTo(BigInteger.valueOf(1000));
        assertThat(getAssetAmountForAddress(transaction.getBody().getOutputs(), scriptAddr, plutusScript.getPolicyId(), "ExistingAsset1").get())
                .isEqualTo(BigInteger.valueOf(200));

        assertThat(transaction.getBody().getMint()).contains(
                MultiAsset.builder()
                        .policyId(plutusScript.getPolicyId())
                        .assets(List.of(
                                new Asset("newAsset", BigInteger.valueOf(1000)),
                                new Asset("ExistingAsset", BigInteger.valueOf(1000).negate()),
                                new Asset("ExistingAsset1", BigInteger.valueOf(100).negate())
                        )).build());
    }

}
