package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.impl.StaticTransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;
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
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits
                        .builder()
                        .mem(BigInteger.valueOf(1000_000))
                        .steps(BigInteger.valueOf(1_000_000)).build())))
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
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build()
                )))
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
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build()
                        )
                ))
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
    void multiMint_redeemerIndicesMatchSortedPolicyIdOrder() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        // Two scripts with different cborHex → different policyIds
        PlutusV2Script scriptA = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        PlutusV2Script scriptB = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        String policyA = scriptA.getPolicyId();
        String policyB = scriptB.getPolicyId();
        // Determine which is "lower" and "higher" in sorted order
        PlutusV2Script lowerScript = policyA.compareTo(policyB) < 0 ? scriptA : scriptB;
        PlutusV2Script higherScript = policyA.compareTo(policyB) < 0 ? scriptB : scriptA;
        String lowerPolicy = lowerScript.getPolicyId();
        String higherPolicy = higherScript.getPolicyId();

        // Add mints in REVERSE sorted order: higher policyId first, lower second
        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(higherScript, new Asset("TokenH", BigInteger.valueOf(500)), BigIntPlutusData.of(1), sender1)
                .mintAsset(lowerScript, new Asset("TokenL", BigInteger.valueOf(300)), BigIntPlutusData.of(2), sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build()
                )))
                .build();

        // Assert mint list is sorted by policyId
        List<MultiAsset> mintList = transaction.getBody().getMint();
        assertThat(mintList).hasSize(2);
        assertThat(mintList.get(0).getPolicyId()).isEqualTo(lowerPolicy);
        assertThat(mintList.get(1).getPolicyId()).isEqualTo(higherPolicy);

        // Assert Mint redeemer indices match sorted positions
        List<Redeemer> mintRedeemers = transaction.getWitnessSet().getRedeemers().stream()
                .filter(r -> r.getTag() == RedeemerTag.Mint)
                .collect(Collectors.toList());
        assertThat(mintRedeemers).hasSize(2);

        // The redeemer for lowerPolicy should have index 0 (first in sorted mint list)
        Redeemer lowerRedeemer = mintRedeemers.stream()
                .filter(r -> r.getIndex().intValue() == 0)
                .findFirst().orElse(null);
        assertThat(lowerRedeemer).isNotNull();
        // lowerScript used redeemer BigIntPlutusData.of(2)
        assertThat(lowerRedeemer.getData().serializeToHex())
                .isEqualTo(BigIntPlutusData.of(2).serializeToHex());

        // The redeemer for higherPolicy should have index 1 (second in sorted mint list)
        Redeemer higherRedeemer = mintRedeemers.stream()
                .filter(r -> r.getIndex().intValue() == 1)
                .findFirst().orElse(null);
        assertThat(higherRedeemer).isNotNull();
        // higherScript used redeemer BigIntPlutusData.of(1)
        assertThat(higherRedeemer.getData().serializeToHex())
                .isEqualTo(BigIntPlutusData.of(1).serializeToHex());
    }

    @Test
    void multiWithdrawal_redeemerIndicesMatchSortedCredentialOrder() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        // Two scripts → two reward addresses with different credential hashes
        PlutusV2Script scriptA = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        PlutusV2Script scriptB = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        Address rewardAddrA = AddressProvider.getRewardAddress(scriptA, Networks.testnet());
        Address rewardAddrB = AddressProvider.getRewardAddress(scriptB, Networks.testnet());

        // Get credential hashes for sorting comparison
        String credHashA = HexUtil.encodeHexString(
                AddressProvider.getDelegationCredentialHash(rewardAddrA).orElseThrow());
        String credHashB = HexUtil.encodeHexString(
                AddressProvider.getDelegationCredentialHash(rewardAddrB).orElseThrow());

        // Determine sorted order
        Address lowerRewardAddr = credHashA.compareTo(credHashB) < 0 ? rewardAddrA : rewardAddrB;
        Address higherRewardAddr = credHashA.compareTo(credHashB) < 0 ? rewardAddrB : rewardAddrA;
        PlutusV2Script lowerScript = credHashA.compareTo(credHashB) < 0 ? scriptA : scriptB;
        PlutusV2Script higherScript = credHashA.compareTo(credHashB) < 0 ? scriptB : scriptA;

        // Add withdrawals in REVERSE credential hash order
        ScriptTx scriptTx = new ScriptTx()
                .withdraw(higherRewardAddr, BigInteger.ZERO, BigIntPlutusData.of(10))
                .attachRewardValidator(higherScript)
                .withdraw(lowerRewardAddr, BigInteger.ZERO, BigIntPlutusData.of(20))
                .attachRewardValidator(lowerScript);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build()
                )))
                .build();

        // Assert withdrawal list is sorted by credential hash
        List<Withdrawal> withdrawals = transaction.getBody().getWithdrawals();
        assertThat(withdrawals).hasSize(2);
        assertThat(withdrawals.get(0).getRewardAddress()).isEqualTo(lowerRewardAddr.toBech32());
        assertThat(withdrawals.get(1).getRewardAddress()).isEqualTo(higherRewardAddr.toBech32());

        // Assert Reward redeemer indices match sorted positions
        List<Redeemer> rewardRedeemers = transaction.getWitnessSet().getRedeemers().stream()
                .filter(r -> r.getTag() == RedeemerTag.Reward)
                .collect(Collectors.toList());
        assertThat(rewardRedeemers).hasSize(2);

        // The redeemer for lowerRewardAddr should have index 0
        Redeemer lowerRedeemer = rewardRedeemers.stream()
                .filter(r -> r.getIndex().intValue() == 0)
                .findFirst().orElse(null);
        assertThat(lowerRedeemer).isNotNull();
        assertThat(lowerRedeemer.getData().serializeToHex())
                .isEqualTo(BigIntPlutusData.of(20).serializeToHex());

        // The redeemer for higherRewardAddr should have index 1
        Redeemer higherRedeemer = rewardRedeemers.stream()
                .filter(r -> r.getIndex().intValue() == 1)
                .findFirst().orElse(null);
        assertThat(higherRedeemer).isNotNull();
        assertThat(higherRedeemer.getData().serializeToHex())
                .isEqualTo(BigIntPlutusData.of(10).serializeToHex());
    }

    @Test
    void combinedMintAndWithdrawal_redeemerIndicesCorrect() throws CborSerializationException {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        // Two scripts for minting
        PlutusV2Script mintScriptA = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        PlutusV2Script mintScriptB = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        String mintPolicyA = mintScriptA.getPolicyId();
        String mintPolicyB = mintScriptB.getPolicyId();
        PlutusV2Script lowerMintScript = mintPolicyA.compareTo(mintPolicyB) < 0 ? mintScriptA : mintScriptB;
        PlutusV2Script higherMintScript = mintPolicyA.compareTo(mintPolicyB) < 0 ? mintScriptB : mintScriptA;

        // Reuse same scripts for withdrawal (script hash == credential hash for script reward addresses)
        Address wdRewardAddr1 = AddressProvider.getRewardAddress(mintScriptA, Networks.testnet());
        Address wdRewardAddr2 = AddressProvider.getRewardAddress(mintScriptB, Networks.testnet());

        String wdCredHash1 = HexUtil.encodeHexString(
                AddressProvider.getDelegationCredentialHash(wdRewardAddr1).orElseThrow());
        String wdCredHash2 = HexUtil.encodeHexString(
                AddressProvider.getDelegationCredentialHash(wdRewardAddr2).orElseThrow());

        Address lowerWdRewardAddr = wdCredHash1.compareTo(wdCredHash2) < 0 ? wdRewardAddr1 : wdRewardAddr2;
        Address higherWdRewardAddr = wdCredHash1.compareTo(wdCredHash2) < 0 ? wdRewardAddr2 : wdRewardAddr1;
        PlutusV2Script lowerWdScript = wdCredHash1.compareTo(wdCredHash2) < 0 ? mintScriptA : mintScriptB;
        PlutusV2Script higherWdScript = wdCredHash1.compareTo(wdCredHash2) < 0 ? mintScriptB : mintScriptA;

        // Add 2 mints (reverse order) + 2 withdrawals (reverse order) in one ScriptTx
        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(higherMintScript, new Asset("TokenH", BigInteger.valueOf(500)), BigIntPlutusData.of(1), sender1)
                .mintAsset(lowerMintScript, new Asset("TokenL", BigInteger.valueOf(300)), BigIntPlutusData.of(2), sender1)
                .withdraw(higherWdRewardAddr, BigInteger.ZERO, BigIntPlutusData.of(30))
                .attachRewardValidator(higherWdScript)
                .withdraw(lowerWdRewardAddr, BigInteger.ZERO, BigIntPlutusData.of(40))
                .attachRewardValidator(lowerWdScript);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        Transaction transaction = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build(),
                        ExUnits.builder().mem(BigInteger.valueOf(1000_000)).steps(BigInteger.valueOf(1_000_000)).build()
                )))
                .build();

        // Assert mint list is sorted by policyId
        List<MultiAsset> mintList = transaction.getBody().getMint();
        assertThat(mintList).hasSize(2);
        assertThat(mintList.get(0).getPolicyId()).isEqualTo(lowerMintScript.getPolicyId());
        assertThat(mintList.get(1).getPolicyId()).isEqualTo(higherMintScript.getPolicyId());

        // Assert Mint redeemer indices
        List<Redeemer> mintRedeemers = transaction.getWitnessSet().getRedeemers().stream()
                .filter(r -> r.getTag() == RedeemerTag.Mint)
                .collect(Collectors.toList());
        assertThat(mintRedeemers).hasSize(2);

        Redeemer mintIdx0 = mintRedeemers.stream().filter(r -> r.getIndex().intValue() == 0).findFirst().orElse(null);
        assertThat(mintIdx0).isNotNull();
        assertThat(mintIdx0.getData().serializeToHex()).isEqualTo(BigIntPlutusData.of(2).serializeToHex());

        Redeemer mintIdx1 = mintRedeemers.stream().filter(r -> r.getIndex().intValue() == 1).findFirst().orElse(null);
        assertThat(mintIdx1).isNotNull();
        assertThat(mintIdx1.getData().serializeToHex()).isEqualTo(BigIntPlutusData.of(1).serializeToHex());

        // Assert withdrawal list is sorted by credential hash
        List<Withdrawal> withdrawals = transaction.getBody().getWithdrawals();
        assertThat(withdrawals).hasSize(2);
        assertThat(withdrawals.get(0).getRewardAddress()).isEqualTo(lowerWdRewardAddr.toBech32());
        assertThat(withdrawals.get(1).getRewardAddress()).isEqualTo(higherWdRewardAddr.toBech32());

        // Assert Reward redeemer indices
        List<Redeemer> rewardRedeemers = transaction.getWitnessSet().getRedeemers().stream()
                .filter(r -> r.getTag() == RedeemerTag.Reward)
                .collect(Collectors.toList());
        assertThat(rewardRedeemers).hasSize(2);

        Redeemer rewardIdx0 = rewardRedeemers.stream().filter(r -> r.getIndex().intValue() == 0).findFirst().orElse(null);
        assertThat(rewardIdx0).isNotNull();
        assertThat(rewardIdx0.getData().serializeToHex()).isEqualTo(BigIntPlutusData.of(40).serializeToHex());

        Redeemer rewardIdx1 = rewardRedeemers.stream().filter(r -> r.getIndex().intValue() == 1).findFirst().orElse(null);
        assertThat(rewardIdx1).isNotNull();
        assertThat(rewardIdx1.getData().serializeToHex()).isEqualTo(BigIntPlutusData.of(30).serializeToHex());
    }

}
