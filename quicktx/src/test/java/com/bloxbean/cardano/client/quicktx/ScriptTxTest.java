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
import com.bloxbean.cardano.client.quicktx.helper.ListPredicates;
import com.bloxbean.cardano.client.quicktx.helper.ScriptPredicates;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    @Test
    void collectFrom_withScriptPredicates_filtersUtxosCorrectly() {
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
                        .amount(List.of(Amount.ada(50)))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build(),
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(1)
                        .amount(List.of(Amount.ada(100)))
                        .inlineDatum(BigIntPlutusData.of(24).serializeToHex())
                        .build(),
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(2)
                        .amount(List.of(Amount.ada(30)))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build()
        );

        // Use ScriptPredicates to filter for UTXOs with specific datum and minimum ADA
        PlutusData expectedDatum = BigIntPlutusData.of(42);
        Predicate<Utxo> predicates = ScriptPredicates.and(
                ScriptPredicates.atAddress(scriptAddr),
                ScriptPredicates.withInlineDatum(expectedDatum),
                ScriptPredicates.withMinLovelace(BigInteger.valueOf(25_000_000)) // 25 ADA minimum
        );

        // Filter UTXOs using our predicates
        List<Utxo> filteredUtxos = scriptUtxos.stream()
                .filter(predicates)
                .collect(Collectors.toList());

        assertThat(filteredUtxos).hasSize(2);
        assertThat(filteredUtxos.get(0).getOutputIndex()).isEqualTo(0); // 50 ADA UTXO
        assertThat(filteredUtxos.get(1).getOutputIndex()).isEqualTo(2); // 30 ADA UTXO

        PlutusData redeemer = BigIntPlutusData.of(2);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(filteredUtxos.get(0), redeemer)
                .payToAddress(receiver1, Amount.ada(40))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddr, expectedDatum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .collateralPayer(sender1)
                .feePayer(receiver1)
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
    }

    @Test
    void collectFromList_withListPredicates_selectsTopUtxos() {
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
                        .amount(List.of(Amount.ada(30)))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build(),
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(1)
                        .amount(List.of(Amount.ada(100)))
                        .inlineDatum(BigIntPlutusData.of(24).serializeToHex())
                        .build(),
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(2)
                        .amount(List.of(Amount.ada(50)))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build()
        );

        // Use ListPredicates to select top 2 UTXOs by value
        ListPredicates.SelectingPredicate<List<Utxo>> topByValue = ListPredicates.selectTop(2,
                Comparator.comparing((Utxo utxo) -> {
                    return utxo.getAmount().stream()
                            .filter(amount -> "lovelace".equals(amount.getUnit()))
                            .map(Amount::getQuantity)
                            .findFirst()
                            .orElse(BigInteger.ZERO);
                }).reversed()
        );

        List<Utxo> selectedUtxos = topByValue.select(scriptUtxos);
        assertThat(selectedUtxos).hasSize(2);

        // Verify correct UTXOs selected (100 ADA and 50 ADA)
        List<BigInteger> amounts = selectedUtxos.stream()
                .map(utxo -> utxo.getAmount().stream()
                        .filter(amount -> "lovelace".equals(amount.getUnit()))
                        .map(Amount::getQuantity)
                        .findFirst()
                        .orElse(BigInteger.ZERO))
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.toList());

        assertThat(amounts.get(0)).isEqualTo(adaToLovelace(100));
        assertThat(amounts.get(1)).isEqualTo(adaToLovelace(50));

        PlutusData redeemer = BigIntPlutusData.of(2);
        PlutusData datum = BigIntPlutusData.of(42);

        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(selectedUtxos.get(0), redeemer)
                .payToAddress(receiver1, Amount.ada(80))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddr, datum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .collateralPayer(sender1)
                .feePayer(receiver1)
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
    }

    @Test
    void collectFrom_withComposedPredicates_demonstratesDeFiScenario() throws CborSerializationException {
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
        String policyId = plutusScript.getPolicyId();

        List<Utxo> scriptUtxos = List.of(
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(0)
                        .amount(List.of(
                                Amount.ada(10),
                                Amount.asset(policyId, "TokenA", BigInteger.valueOf(1000))
                        ))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build(),
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(1)
                        .amount(List.of(
                                Amount.ada(100),
                                Amount.asset(policyId, "TokenB", BigInteger.valueOf(500))
                        ))
                        .inlineDatum(BigIntPlutusData.of(24).serializeToHex())
                        .build(),
                Utxo.builder()
                        .address(scriptAddr)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(2)
                        .amount(List.of(
                                Amount.ada(50),
                                Amount.asset(policyId, "TokenA", BigInteger.valueOf(2000))
                        ))
                        .inlineDatum(BigIntPlutusData.of(42).serializeToHex())
                        .build()
        );

        // DeFi scenario: Find UTXOs with specific token and sufficient collateral
        PlutusData expectedDatum = BigIntPlutusData.of(42);
        Predicate<Utxo> defiPredicates = ScriptPredicates.and(
                ScriptPredicates.atAddress(scriptAddr),
                ScriptPredicates.withInlineDatum(expectedDatum),
                ScriptPredicates.withAsset(policyId, "TokenA"),
                ScriptPredicates.withMinAssetQuantity(policyId, "TokenA", BigInteger.valueOf(1500))
        );

        // Filter using DeFi predicates
        List<Utxo> defiUtxos = scriptUtxos.stream()
                .filter(defiPredicates)
                .collect(Collectors.toList());

        assertThat(defiUtxos).hasSize(1);
        assertThat(defiUtxos.get(0).getOutputIndex()).isEqualTo(2);

        PlutusData redeemer = BigIntPlutusData.of(2);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(defiUtxos.get(0), redeemer)
                .payToAddress(receiver1, Amount.ada(30))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddr, expectedDatum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .collateralPayer(sender1)
                .feePayer(receiver1)
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(2);
        assertThat(transaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
    }

}
