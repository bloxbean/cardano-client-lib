package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TxTest extends QuickTxBaseTest {
    String sender1 = new Account().baseAddress();
    String sender2 = new Account().enterpriseAddress();

    String receiver1 = new Account().baseAddress();
    String receiver2 = new Account().baseAddress();
    String receiver3 = new Account().enterpriseAddress();

    @Mock
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;
    }

    @Test
    void payToAddress_ada_withEnoughFund() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        System.out.println(JsonUtil.getPrettyJson(transaction));
        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput changeOutput = transaction.getBody().getOutputs().stream().filter(transactionOutput -> transactionOutput.getAddress().equals(sender1)).findFirst()
                .get();

        assertThat(actualTxOuts).hasSize(3);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(actualTxOuts).contains(TransactionOutput.builder()
                        .address(receiver1)
                        .value(Value.builder().coin(adaToLovelace(10)).build()).build(),
                TransactionOutput.builder()
                        .address(receiver2)
                        .value(Value.builder().coin(adaToLovelace(20)).build()).build());

        assertThat(changeOutput.getValue().getCoin()).isEqualTo(adaToLovelace(100).subtract(adaToLovelace(30)).subtract(transaction.getBody().getFee()));
    }

    @Test
    void payToAddress_ada_asset_withEnoughFund() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, List.of(Amount.asset(policy1, "asset1", 50)))
                .payToAddress(receiver3, List.of(Amount.asset(policy1, "asset2", 100), Amount.ada(5)))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        System.out.println(JsonUtil.getPrettyJson(transaction));
        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput changeOutput = transaction.getBody().getOutputs().stream().filter(transactionOutput -> transactionOutput.getAddress().equals(sender1)).findFirst()
                .get();

        assertThat(actualTxOuts).hasSize(4);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isGreaterThan(adaToLovelace(1));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isLessThan(adaToLovelace(2));
        assertThat(getAssetAmountForAddress(actualTxOuts, receiver2, policy1, "asset1").get()).isEqualTo(50);

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver3).get()).isEqualTo(adaToLovelace(5));
        assertThat(getAssetAmountForAddress(actualTxOuts, receiver3, policy1, "asset2").get()).isEqualTo(100);

        assertThat(getAssetAmountForAddress(actualTxOuts, sender1, policy1, "asset1").get()).isEqualTo(1950);
        assertThat(getAssetAmountForAddress(actualTxOuts, sender1, policy1, "asset2").get()).isEqualTo(2900);
        assertThat(getAssetAmountForAddress(actualTxOuts, sender1, policy2, "asset3").get()).isEqualTo(4000);
        assertThat(changeOutput.getValue().getCoin()).isEqualTo(
                adaToLovelace(120).subtract(
                        adaToLovelace(15).add(getLovelaceAmountForAddress(actualTxOuts, receiver2).get())
                ).subtract(transaction.getBody().getFee())
        );
    }

    @Test
    void payToAddress_ada_asset_NotEnoughAsset() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(List.of());

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, List.of(Amount.asset(policy1, "asset1", 50)))
                .payToAddress(receiver3, List.of(Amount.asset(policy1, "asset2", 5000), Amount.ada(5)))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        assertThatThrownBy(() -> quickTxBuilder.compose(tx).build())
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageStartingWith("Not enough funds for")
                .hasMessageContaining(policy1)
                .hasMessageContaining("2000");
    }

    @Test
    void payToAddress_ada_NotAda() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(List.of());

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, List.of(Amount.asset(policy1, "asset1", 110)))
                .payToAddress(receiver3, List.of(Amount.asset(policy1, "asset2", 3000), Amount.ada(110)))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        assertThatThrownBy(() -> quickTxBuilder.compose(tx).build())
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageStartingWith("Not enough funds for")
                .hasMessageContaining("lovelace");
    }

    @Test
    void payToAddress_multipleTxs_withEnoughFund() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(eq(sender1), anyInt(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );

        given(utxoSupplier.getPage(eq(sender2), anyInt(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(200)))
                                .build()
                )
        );

        Tx tx1 = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        Tx tx2 = new Tx()
                .payToAddress(receiver3, Amount.ada(50))
                .from(sender2);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx1, tx2)
                .feePayer(sender2)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput changeOutput1 = transaction.getBody().getOutputs().stream().filter(transactionOutput -> transactionOutput.getAddress().equals(sender1)).findFirst()
                .get();
        TransactionOutput changeOutput2 = transaction.getBody().getOutputs().stream().filter(transactionOutput -> transactionOutput.getAddress().equals(sender2)).findFirst()
                .get();

        assertThat(actualTxOuts).hasSize(5);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(20));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver3).get()).isEqualTo(adaToLovelace(50));

        assertThat(changeOutput1.getValue().getCoin()).isEqualTo(adaToLovelace(90));
        assertThat(changeOutput2.getValue().getCoin()).isEqualTo(adaToLovelace(150).subtract(transaction.getBody().getFee()));
    }

    @Test
    void payToAddress_multipleTxs_feePayerNotSet() {
        Tx tx1 = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        Tx tx2 = new Tx()
                .payToAddress(receiver3, Amount.ada(50))
                .from(sender2);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        assertThatThrownBy(() -> quickTxBuilder.compose(tx1, tx2).build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Fee Payer address is not set");
    }

    @Test
    void payToAddress_multipleAmountsAsList() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, List.of(Amount.asset(policy1, "asset1", 50), Amount.ada(10), Amount.asset(policy2, "asset3", 90)))
                .payToAddress(receiver2, List.of(Amount.asset(policy1, "asset2", 100), Amount.ada(5)))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();
        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(actualTxOuts).hasSize(3);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(5));

        assertThat(getAssetAmountForAddress(actualTxOuts, receiver1, policy1, "asset1").get()).isEqualTo(50);
        assertThat(getAssetAmountForAddress(actualTxOuts, receiver1, policy2, "asset3").get()).isEqualTo(90);
        assertThat(getAssetAmountForAddress(actualTxOuts, receiver2, policy1, "asset2").get()).isEqualTo(100);
    }

    @Test
    @SneakyThrows
    void testPayToAddress_withMintOutput() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );

        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("new_policy", 1, 1);
        Asset newAsset = new Asset("asset4", BigInteger.valueOf(6000));
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(5))
                .mintAssets(policy.getPolicyScript(), newAsset, sender1)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getAssetAmountForAddress(actualTxOuts, sender1, policy.getPolicyId(), newAsset.getName()).get()).isEqualTo(6000);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(5));
        assertThat(transaction.getBody().getMint()).contains(MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(newAsset))
                .build());
    }

    @Test
    @SneakyThrows
    void testPayToAddress_withMintOutput_differentMintReceivers() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );

        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("new_policy", 1, 1);
        Asset newAsset = new Asset("asset4", BigInteger.valueOf(6000));
        Tx tx = new Tx()
                .payToAddress(receiver1, List.of(Amount.ada(10), Amount.asset(policy.getPolicyId(), newAsset.getName(), BigInteger.valueOf(2000))))
                .payToAddress(receiver2, List.of(Amount.ada(5), Amount.asset(policy.getPolicyId(), newAsset.getName(), BigInteger.valueOf(4000))))
                .mintAssets(policy.getPolicyScript(), newAsset)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getAssetAmountForAddress(actualTxOuts, receiver1, policy.getPolicyId(), newAsset.getName()).get()).isEqualTo(2000);
        assertThat(getAssetAmountForAddress(actualTxOuts, receiver2, policy.getPolicyId(), newAsset.getName()).get()).isEqualTo(4000);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(5));
        assertThat(transaction.getBody().getMint()).contains(MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(newAsset))
                .build());
    }

    @Test
    @SneakyThrows
    void delegateTo() {
        String policy1 = generateRandomHexValue(28);
        String policy2 = generateRandomHexValue(28);
        String poolId = generateRandomHexValue(32);
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build(),
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100),
                                        Amount.asset(policy1, "asset1", 2000),
                                        Amount.asset(policy1, "asset2", 3000),
                                        Amount.asset(policy2, "asset3", 4000)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .delegateTo(receiver1, poolId)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        assertThat(transaction.getBody().getCerts()).hasSize(1);
        StakeDelegation stakeDelegation = (StakeDelegation) transaction.getBody().getCerts().get(0);
        assertThat(HexUtil.encodeHexString(stakeDelegation.getStakePoolId().getPoolKeyHash())).isEqualTo(poolId);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
    }

    @Test
    @SneakyThrows
    void registerStakeAddress() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(5))
                .registerStakeAddress(receiver2)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        assertThat(transaction.getBody().getCerts()).hasSize(1);
        StakeRegistration stakeRegistration = (StakeRegistration) transaction.getBody().getCerts().get(0);
        assertThat(HexUtil.encodeHexString(stakeRegistration.getStakeCredential().getHash()))
                .isEqualTo(new Address(receiver2).getDelegationCredentialHash().map(HexUtil::encodeHexString).get());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(15).subtract(adaToLovelace(2)).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void deRegisterStakeAddress() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(5))
                .deregisterStakeAddress(receiver2, receiver2)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        assertThat(transaction.getBody().getCerts()).hasSize(1);
        StakeDeregistration stakeDeregistration = (StakeDeregistration) transaction.getBody().getCerts().get(0);
        assertThat(HexUtil.encodeHexString(stakeDeregistration.getStakeCredential().getHash()))
                .isEqualTo(new Address(receiver2).getDelegationCredentialHash().map(HexUtil::encodeHexString).get());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(2));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(15).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void deRegisterStakeAddress_refundToSender() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(5))
                .deregisterStakeAddress(receiver2, sender1)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        assertThat(transaction.getBody().getCerts()).hasSize(1);
        StakeDeregistration stakeDeregistration = (StakeDeregistration) transaction.getBody().getCerts().get(0);
        assertThat(HexUtil.encodeHexString(stakeDeregistration.getStakeCredential().getHash()))
                .isEqualTo(new Address(receiver2).getDelegationCredentialHash().map(HexUtil::encodeHexString).get());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(15).add(adaToLovelace(2)).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void withdraw() {
        String rewardAddress = new Account().stakeAddress();
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(5))
                .withdraw(rewardAddress, adaToLovelace(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(15).add(adaToLovelace(6)).subtract(transaction.getBody().getFee()));
    }

    @Test
    void withdraw_separateReceiver() {
        String rewardAddress = new Account().stakeAddress();
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(5))
                .withdraw(rewardAddress, adaToLovelace(6), receiver2)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(15).subtract(transaction.getBody().getFee()));
    }

    @Test
    void withdraw_onlyFromRewardAddress() {
        assertThatThrownBy(() -> new Tx()
                .payToAddress(receiver1, Amount.ada(5))
                .withdraw(receiver2, adaToLovelace(6))
                .from(sender1))
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Only reward address");
    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatum() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToContract(receiver1, Amount.ada(5), BigIntPlutusData.of(42))
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput receiver1Output = actualTxOuts.stream().filter(txOut -> txOut.getAddress().equals(receiver1)).findFirst().get();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(receiver1Output.getInlineDatum()).isEqualTo(BigIntPlutusData.of(42));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void payToContract_datumHash() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToContract(receiver1, List.of(Amount.ada(5)), BigIntPlutusData.of(42).getDatumHash())
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(HexUtil.encodeHexString(transaction.getBody().getOutputs().get(0).getDatumHash())).isEqualTo(BigIntPlutusData.of(42).getDatumHash());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void payToContract_datumHash_noMintOutputFlag() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToContract(receiver1, List.of(Amount.ada(5)), BigIntPlutusData.of(42).getDatumHash())
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(HexUtil.encodeHexString(transaction.getBody().getOutputs().get(0).getDatumHash())).isEqualTo(BigIntPlutusData.of(42).getDatumHash());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void payToContract_datumHash_noAmtList_noMintOutputFlag() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToContract(receiver1, Amount.ada(5), BigIntPlutusData.of(42).getDatumHash())
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(HexUtil.encodeHexString(transaction.getBody().getOutputs().get(0).getDatumHash())).isEqualTo(BigIntPlutusData.of(42).getDatumHash());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatumAndScriptRefBytes_withoutMintOutput() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToContract(receiver1, List.of(Amount.ada(5)), BigIntPlutusData.of(42), new byte[]{1, 2})
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(transaction.getBody().getOutputs().get(0).getInlineDatum()).isEqualTo(BigIntPlutusData.of(42));
        assertThat(transaction.getBody().getOutputs().get(0).getScriptRef()).isEqualTo(new byte[]{1, 2});
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatumAndScriptRef_withoutMintOutput() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        Tx tx = new Tx()
                .payToContract(receiver1, List.of(Amount.ada(5)), BigIntPlutusData.of(42), plutusScript)
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(transaction.getBody().getOutputs().get(0).getInlineDatum()).isEqualTo(BigIntPlutusData.of(42));
        assertThat(transaction.getBody().getOutputs().get(0).getScriptRef()).isEqualTo(plutusScript.scriptRefBytes());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatumAndScriptRef_mintOutputTrue() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("newPolicy", 1, 1);
        Asset newAsset = new Asset("newAsset", BigInteger.valueOf(1000));

        Tx tx = new Tx()
                .payToContract(receiver1, List.of(Amount.ada(5), Amount.asset(policy.getPolicyId(), "newAsset", BigInteger.valueOf(1000))), BigIntPlutusData.of(42), plutusScript)
                .payToAddress(receiver2, Amount.ada(6))
                .mintAssets(policy.getPolicyScript(), List.of(newAsset))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput receiver2Output = actualTxOuts.stream().filter(txOut -> txOut.getAddress().equals(receiver1)).findFirst().get();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(receiver2Output.getInlineDatum()).isEqualTo(BigIntPlutusData.of(42));
        assertThat(receiver2Output.getScriptRef()).isEqualTo(plutusScript.scriptRefBytes());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatumAndScriptRef_mintOutput_throwError_insufficientAsset() {
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20)))
                                .build()
                )
        );
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(List.of());

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("newPolicy", 1, 1);
        Asset newAsset = new Asset("newAsset", BigInteger.valueOf(1000));

        Tx tx = new Tx()
                .payToContract(receiver1, List.of(Amount.ada(5), Amount.asset(policy.getPolicyId(), "newAsset", BigInteger.valueOf(1002))),
                        BigIntPlutusData.of(42), plutusScript)
                .payToAddress(receiver2, Amount.ada(6))
                .mintAssets(policy.getPolicyScript(), List.of(newAsset))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        assertThatThrownBy(() -> quickTxBuilder.compose(tx).build())
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Not enough funds for");
    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatumAndScriptRef_mintOutput_burnOutput_success() {
        Policy existingPolicy = PolicyUtil.createMultiSigScriptAtLeastPolicy("newPolicy", 1, 1);
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20),
                                        Amount.asset(existingPolicy.getPolicyId(), "TestAsset", 1000),
                                        Amount.asset(existingPolicy.getPolicyId(), "TestAsset2", 4000)
                                )).build()
                )
        );

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("newPolicy", 1, 1);
        Asset newAsset = new Asset("newAsset", BigInteger.valueOf(1000));

        Tx tx = new Tx()
                .mintAssets(existingPolicy.getPolicyScript(), List.of(
                        new Asset("TestAsset", BigInteger.valueOf(300).negate()),
                        new Asset("TestAsset2", BigInteger.valueOf(100).negate())))
                .mintAssets(existingPolicy.getPolicyScript(), List.of(new Asset("TestAsset2", BigInteger.valueOf(100).negate())))
                .mintAssets(policy.getPolicyScript(), List.of(newAsset))
                .payToContract(receiver1, List.of(Amount.ada(5),
                        Amount.asset(policy.getPolicyId(), "newAsset", BigInteger.valueOf(1000))), BigIntPlutusData.of(42), plutusScript)
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput receiver2Output = actualTxOuts.stream().filter(txOut -> txOut.getAddress().equals(receiver1)).findFirst().get();

        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(5));
        assertThat(receiver2Output.getInlineDatum()).isEqualTo(BigIntPlutusData.of(42));
        assertThat(receiver2Output.getScriptRef()).isEqualTo(plutusScript.scriptRefBytes());
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver2).get()).isEqualTo(adaToLovelace(6));
        assertThat(getLovelaceAmountForAddress(actualTxOuts, sender1).get()).isEqualTo(adaToLovelace(9).subtract(transaction.getBody().getFee()));
        assertThat(getAssetAmountForAddress(actualTxOuts, sender1, existingPolicy.getPolicyId(), "TestAsset").get())
                .isEqualTo(BigInteger.valueOf(700));
        assertThat(getAssetAmountForAddress(actualTxOuts, sender1, existingPolicy.getPolicyId(), "TestAsset2").get())
                .isEqualTo(BigInteger.valueOf(3800));
        assertThat(transaction.getBody().getMint()).contains(
                MultiAsset.builder()
                .policyId(existingPolicy.getPolicyId())
                .assets(List.of(
                        new Asset("TestAsset", BigInteger.valueOf(300).negate()),
                        new Asset("TestAsset2", BigInteger.valueOf(200).negate())
                )).build(),
                MultiAsset.builder()
                        .policyId(policy.getPolicyId())
                        .assets(List.of(new Asset("newAsset", BigInteger.valueOf(1000)))).build());

    }

    @Test
    @SneakyThrows
    void payToContract_inlineDatumAndScriptRef_mintOutput_burnOutput_insufficient_balance_error() {
        Policy existingPolicy = PolicyUtil.createMultiSigScriptAtLeastPolicy("newPolicy", 1, 1);
        given(utxoSupplier.getPage(anyString(), any(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash(generateRandomHexValue(32))
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(20),
                                        Amount.asset(existingPolicy.getPolicyId(), "TestAsset", 1000),
                                        Amount.asset(existingPolicy.getPolicyId(), "TestAsset2", 4000)
                                )).build()
                )
        );
        given(utxoSupplier.getPage(anyString(), any(), eq(1), any())).willReturn(Collections.emptyList());

        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("newPolicy", 1, 1);
        Asset newAsset = new Asset("newAsset", BigInteger.valueOf(1000));

        Tx tx = new Tx()
                .mintAssets(existingPolicy.getPolicyScript(), List.of(
                        new Asset("TestAsset", BigInteger.valueOf(1200).negate()),
                        new Asset("TestAsset2", BigInteger.valueOf(100).negate())))
                .mintAssets(existingPolicy.getPolicyScript(), List.of(new Asset("TestAsset2", BigInteger.valueOf(100).negate())))
                .mintAssets(policy.getPolicyScript(), List.of(newAsset))
                .payToContract(receiver1, List.of(Amount.ada(5),
                        Amount.asset(policy.getPolicyId(), "newAsset", BigInteger.valueOf(1000))), BigIntPlutusData.of(42), plutusScript)
                .payToAddress(receiver2, Amount.ada(6))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        assertThatThrownBy(() -> quickTxBuilder.compose(tx).build())
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageStartingWith("Not enough funds for")
                .hasMessageContaining(existingPolicy.getPolicyId())
                .hasMessageContaining("200");
    }

    @Test
    void withChangeAddress() {
        given(utxoSupplier.getPage(eq(sender1), anyInt(), eq(0), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        System.out.println("Sender2 > " + sender2);
        System.out.println("Sender1 > " + sender1);
        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .withChangeAddress(sender2)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .feePayer(sender2)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput changeOutput = transaction.getBody().getOutputs().stream().filter(transactionOutput -> transactionOutput.getAddress().equals(sender2)).findFirst()
                .get();

        assertThat(actualTxOuts).hasSize(3);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(actualTxOuts).contains(TransactionOutput.builder()
                        .address(receiver1)
                        .value(Value.builder().coin(adaToLovelace(10)).build()).build(),
                TransactionOutput.builder()
                        .address(receiver2)
                        .value(Value.builder().coin(adaToLovelace(20)).build()).build());

        assertThat(changeOutput.getValue().getCoin()).isEqualTo(adaToLovelace(100).subtract(adaToLovelace(30)).subtract(transaction.getBody().getFee()));
    }

    @Test
    void collectFromUtxos() {
        List<Utxo> utxos = List.of(
                Utxo.builder()
                        .address(sender1)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(100)))
                        .build(),
                Utxo.builder()
                        .address(sender1)
                        .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(120)))
                        .build()
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .collectFrom(utxos)
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .build();

        List<TransactionOutput> actualTxOuts = transaction.getBody().getOutputs();
        TransactionOutput changeOutput = transaction.getBody().getOutputs().stream().filter(transactionOutput -> transactionOutput.getAddress().equals(sender1)).findFirst()
                .get();

        assertThat(actualTxOuts).hasSize(3);
        assertThat(getLovelaceAmountForAddress(actualTxOuts, receiver1).get()).isEqualTo(adaToLovelace(10));
        assertThat(actualTxOuts).contains(TransactionOutput.builder()
                        .address(receiver1)
                        .value(Value.builder().coin(adaToLovelace(10)).build()).build(),
                TransactionOutput.builder()
                        .address(receiver2)
                        .value(Value.builder().coin(adaToLovelace(20)).build()).build());

        assertThat(changeOutput.getValue().getCoin()).isEqualTo(adaToLovelace(220).subtract(adaToLovelace(30)).subtract(transaction.getBody().getFee()));
    }

    @Test
    void postBalance_updateOutputAmount() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .postBalanceTx((context, txn) -> {
                    txn.getBody().getOutputs().get(0).getValue().setCoin(adaToLovelace(200));
                })
                .build();

        assertThat(transaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(200));
    }

    @Test
    void withRequiredSigner_paymentAddress() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        Address address1 = new Account(Networks.mainnet()).getBaseAddress();
        Address address2 = new Account(Networks.mainnet()).getBaseAddress();
        Address address3 = new Account(Networks.mainnet()).getEnterpriseAddress();
        Address address4 = new Address(new Account(Networks.mainnet()).stakeAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .withRequiredSigners(address1)
                .withRequiredSigners(address2)
                .withRequiredSigners(address3)
                .withRequiredSigners(address4)
                .build();

        assertThat(transaction.getBody().getRequiredSigners()).hasSize(4);
        assertThat(transaction.getBody().getRequiredSigners()).contains(address1.getPaymentCredentialHash().get());
        assertThat(transaction.getBody().getRequiredSigners()).contains(address2.getPaymentCredentialHash().get());
        assertThat(transaction.getBody().getRequiredSigners()).contains(address3.getPaymentCredentialHash().get());
        assertThat(transaction.getBody().getRequiredSigners()).contains(address4.getDelegationCredentialHash().get());
    }

    @Test
    void withRequiredSigner_paymentAddress_oneMethod() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        Address address1 = new Account(Networks.mainnet()).getBaseAddress();
        Address address3 = new Account(Networks.mainnet()).getEnterpriseAddress();
        Address address4 = new Address(new Account(Networks.mainnet()).stakeAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        var transaction = quickTxBuilder.compose(tx)
                .withRequiredSigners(address1, address3, address4)
                .build();

        assertThat(transaction.getBody().getRequiredSigners()).hasSize(3);
        assertThat(transaction.getBody().getRequiredSigners()).contains(address1.getPaymentCredentialHash().get());
        assertThat(transaction.getBody().getRequiredSigners()).contains(address3.getPaymentCredentialHash().get());
        assertThat(transaction.getBody().getRequiredSigners()).contains(address4.getDelegationCredentialHash().get());
    }

    @Test
    void withRequiredSigner_bytes() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        byte[] bytes1 = new byte[]{0, 1, 2, 3};
        byte[] bytes2 = new byte[]{4, 5, 6, 7};
        var transaction = quickTxBuilder.compose(tx)
                .withRequiredSigners(bytes1)
                .withRequiredSigners(bytes2)
                .build();

        assertThat(transaction.getBody().getRequiredSigners()).hasSize(2);
        assertThat(transaction.getBody().getRequiredSigners()).contains(bytes1);
        assertThat(transaction.getBody().getRequiredSigners()).contains(bytes2);
    }

    @Test
    void withRequiredSigner_bytes_oneMethod() {
        given(utxoSupplier.getPage(anyString(), anyInt(), any(), any())).willReturn(
                List.of(
                        Utxo.builder()
                                .address(sender1)
                                .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                                .outputIndex(0)
                                .amount(List.of(Amount.ada(100)))
                                .build()
                )
        );

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(10))
                .payToAddress(receiver2, Amount.ada(20))
                .from(sender1);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
        byte[] bytes1 = new byte[]{0, 1, 2, 3};
        byte[] bytes2 = new byte[]{4, 5, 6, 7};
        var transaction = quickTxBuilder.compose(tx)
                .withRequiredSigners(bytes1, bytes2)
                .build();

        assertThat(transaction.getBody().getRequiredSigners()).hasSize(2);
        assertThat(transaction.getBody().getRequiredSigners()).contains(bytes1);
        assertThat(transaction.getBody().getRequiredSigners()).contains(bytes2);
    }
}
