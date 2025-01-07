package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BaseITTest;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.AuxDataProviders;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.ChangeOutputAdjustments.adjustChangeOutput;
import static com.bloxbean.cardano.client.function.helper.FeeCalculators.feeCalculator;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StakeTransactionIT extends BaseITTest {

    public static final String STAKEREGISTRATION_POLICY_JSON = "stakeregistration-policy.json";

    ProtocolParams protocolParams;

    static String senderMnemonic1 = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";
    private static Account senderAccount1;
    private static String senderAddr1;

    static String senderMnemonic2 = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";

    static Account senderAccount2;
    static String senderAddr2;

    static String senderMnemonic3 = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";
    static Account senderAccount3;
    static String senderAddr3;

    static String senderMnemonic4 = "farm hunt wasp believe happy palm skull apple execute paddle asthma absorb misery unlock broom turkey few dry focus vacuum novel crumble dish token";
    static Account senderAccount4;
    static String senderAddr4;

    static String senderMnemonic5 = "farm hunt wasp believe happy palm skull apple execute paddle asthma absorb misery unlock broom turkey few dry focus vacuum novel crumble dish token";
    static Account senderAccount5;
    static String senderAddr5;

    static String senderMnemonic6 = "limb myself better pyramid home measure quality smile also reveal used sleep kind trend destroy output guide test memory clever spoil polar salon artist";
    static Account senderAccount6;
    static String senderAddr6;

    static String senderMnemonic7 = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";
    static Account senderAccount7;
    static String senderAddr7;

    static String senderMnemonic8 = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";
    static Account senderAccount8;
    static String senderAddr8;

    static String senderMnemonic9 = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";
    static Account senderAccount9;
    static String senderAddr9;

    @BeforeAll
    public static void setupAll() {
        senderAccount1 = new Account(Networks.testnet(), senderMnemonic1);
        senderAddr1 = senderAccount1.baseAddress();

        senderAccount2 = new Account(Networks.testnet(), senderMnemonic2);
        senderAddr2 = senderAccount2.baseAddress();

        senderAccount3 = new Account(Networks.testnet(), senderMnemonic3);
        senderAddr3 = senderAccount3.baseAddress();

        senderAccount4 = new Account(Networks.testnet(), senderMnemonic4);
        senderAddr4 = senderAccount4.baseAddress();

        senderAccount5 = new Account(Networks.testnet(), senderMnemonic5);
        senderAddr5 = senderAccount5.baseAddress();

        senderAccount6 = new Account(Networks.testnet(), senderMnemonic6);
        senderAddr6 = senderAccount6.baseAddress();

        senderAccount7 = new Account(Networks.testnet(), senderMnemonic7);
        senderAddr7 = senderAccount7.baseAddress();

        senderAccount8 = new Account(Networks.testnet(), senderMnemonic8);
        senderAddr8 = senderAccount8.baseAddress();

        senderAccount9 = new Account(Networks.testnet(), senderMnemonic9);
        senderAddr9 = senderAccount9.baseAddress();

        if (backendType.equals(DEVKIT)) {
            topUpFund(senderAddr1, 50000);
            topUpFund(senderAddr2, 50000);
            topUpFund(senderAddr3, 50000);
            topUpFund(senderAddr4, 50000);
            topUpFund(senderAddr5, 50000);
            topUpFund(senderAddr6, 50000);
            topUpFund(senderAddr7, 50000);
            topUpFund(senderAddr8, 50000);
            topUpFund(senderAddr9, 50000);
        }
    }

    @BeforeEach
    public void setup() throws ApiException {
        protocolParams = getBackendService().getEpochService().getProtocolParameters().getValue();
    }

    @Test
    @Order(1)
    void testStakeRegistration_addressKeyAsStakeKey() throws ApiException, CborSerializationException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr2, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount2.stakeHdKeyPair();
        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();

        //-- Stake key registration
        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromKey(stakePublicKey));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr2))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeRegistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr2.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().subtract(deposit)));
                })
                .andThen(feeCalculator(senderAddr2, 2))
                .andThen(adjustChangeOutput(senderAddr2, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount2);

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()),
                new DefaultProtocolParamsSupplier(getBackendService().getEpochService()))
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    @Order(2)
    void testStakeDelegation_addressKeyAsStakeKey() throws ApiException, CborSerializationException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr1, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount1.stakeHdKeyPair();
        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();

        //-- Stake key delegation
        StakePoolId stakePoolId = StakePoolId.fromBech32PoolId("pool1z22x50lqsrwent6en0llzzs9e577rx7n3mv9kfw7udwa2rf42fa");
        StakeDelegation stakeDelegation = new StakeDelegation(StakeCredential.fromKey(stakePublicKey), stakePoolId);
        Metadata metadata = MessageMetadata.create()
                .add("Stake Delegation using cardano-client-lib")
                .add("https://github.com/bloxbean/cardano-client-lib");

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr1))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeDelegation);
                })
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(feeCalculator(senderAddr1, 2))
                .andThen(adjustChangeOutput(senderAddr1, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount1)
                .andThen((context, transaction) -> senderAccount1.signWithStakeKey(transaction));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    @Order(3)
    void testStakeDeRegistration_addressKeyAsStakeKey() throws ApiException, CborSerializationException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr3, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount3.stakeHdKeyPair();
        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();

        //-- Stake key deregistration
        StakeDeregistration stakeRegistration = new StakeDeregistration(StakeCredential.fromKey(stakePublicKey));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr3))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeRegistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr3.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().add(deposit)));
                })
                .andThen(feeCalculator(senderAddr3, 2))
                .andThen(adjustChangeOutput(senderAddr3, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount3)
                .andThen((context,transaction) -> TransactionSigner.INSTANCE.sign(transaction, stakeHdKeyPair));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);

        assertThat(result.isSuccessful()).isEqualTo(true);

        waitForTransaction(result);
    }

    @Test
    @Order(4)
    void testStakeDelegationAnotherAccount() throws ApiException, CborSerializationException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr4, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount4.stakeHdKeyPair();
        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();

        //-- Stake key delegation
        StakePoolId stakePoolId = StakePoolId.fromBech32PoolId("pool1z22x50lqsrwent6en0llzzs9e577rx7n3mv9kfw7udwa2rf42fa");
        StakeDelegation stakeDelegation = new StakeDelegation(StakeCredential.fromKey(stakePublicKey), stakePoolId);
        Metadata metadata = MessageMetadata.create()
                .add("Stake Delegation using cardano-client-lib")
                .add("https://github.com/bloxbean/cardano-client-lib");

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr4))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeDelegation);
                })
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(feeCalculator(senderAddr4, 2))
                .andThen(adjustChangeOutput(senderAddr4, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount4)
                .andThen((context,transaction) -> senderAccount4.signWithStakeKey(transaction));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    void testWithdrawal() throws ApiException, CborSerializationException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr5, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        //-- Stake addresses
        String stakeAddress = senderAccount5.stakeAddress();
        String stakeAddress2 = senderAccount6.stakeAddress();

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr5))
                .andThen((context, txn) -> {
                    //Currently zero for testing as there is no reward available. But update this value later once reward is available.
                    BigInteger rewardAmt = BigInteger.ZERO;
                    txn.getBody().getWithdrawals().add(new Withdrawal(stakeAddress, rewardAmt));
                    txn.getBody().getWithdrawals().add(new Withdrawal(stakeAddress2, rewardAmt)); //another dummy withdrawal
                    //Add reward amount to output
                    txn.getBody().getOutputs().get(0).getValue().getCoin().add(rewardAmt);
                })
                .andThen(feeCalculator(senderAddr5, 3))
                .andThen(adjustChangeOutput(senderAddr5, 3));

        TxSigner signer = SignerProviders.signerFrom(senderAccount5)
                .andThen((context, transaction) -> senderAccount5.signWithStakeKey(transaction))
                .andThen((context,transaction) -> senderAccount6.signWithStakeKey(transaction));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    @Order(5)
    void testStakeRegistration_scriptHashAsStakeKey() throws ApiException, CborSerializationException, IOException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr7, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        Policy policy = loadJsonPolicyScript(STAKEREGISTRATION_POLICY_JSON);

        System.out.println(JsonUtil.getPrettyJson(policy));

        //-- Stake key registration with script hash
        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromScript(policy.getPolicyScript()));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr7))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeRegistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr7.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().subtract(deposit)));
                })
                .andThen(feeCalculator(senderAddr7, 1))
                .andThen(adjustChangeOutput(senderAddr7, 1));

        TxSigner signer = SignerProviders.signerFrom(senderAccount7);

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    @Order(6)
    void testStakeDelegation_scriptHashAsStakeKey() throws Exception {
        Policy policy = loadJsonPolicyScript(STAKEREGISTRATION_POLICY_JSON);

        //Get a address for payment key (Account at address 0) and Script as delegation key
        Address address = AddressProvider.getBaseAddress(senderAccount8.hdKeyPair().getPublicKey(), policy.getPolicyScript(), Networks.testnet());
        String baseAddress = address.toBech32();
        System.out.println(baseAddress);

        //Find stake address. Just for info, not required for test
        Address stakeAddress = AddressProvider.getRewardAddress(policy.getPolicyScript(), Networks.testnet());
        System.out.println("Stake address (Bech32) >> " + stakeAddress.toBech32());
        System.out.println("Stake address (Hex) >> " + HexUtil.encodeHexString(stakeAddress.getBytes()));

        //Can use any account to pay fee
        String delegationFeePaymentAccountMnemonic = "farm hunt wasp believe happy palm skull apple execute paddle asthma absorb misery unlock broom turkey few dry focus vacuum novel crumble dish token";
        Account delegationFeePaymentAccount = new Account(Networks.testnet(), delegationFeePaymentAccountMnemonic);
        String delegationFeePaymentAddress = delegationFeePaymentAccount.baseAddress();

        List<Utxo> utxos = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()))
                .selectUtxos(delegationFeePaymentAccount.baseAddress(), LOVELACE, adaToLovelace(2), Collections.EMPTY_SET);
        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        //-- Stake key delegation
        StakePoolId stakePoolId = StakePoolId.fromBech32PoolId("pool1z22x50lqsrwent6en0llzzs9e577rx7n3mv9kfw7udwa2rf42fa");
        StakeDelegation scriptStakeDelegation = new StakeDelegation(StakeCredential.fromScript(policy.getPolicyScript()), stakePoolId);

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, delegationFeePaymentAddress))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(scriptStakeDelegation);
                })
                .andThen((context, txn) -> {
                    txn.getWitnessSet().getNativeScripts().add(policy.getPolicyScript());
                })
                .andThen(feeCalculator(delegationFeePaymentAddress, 3))
                .andThen(adjustChangeOutput(delegationFeePaymentAddress, 3));

        TxSigner signer = SignerProviders.signerFrom(delegationFeePaymentAccount)
                .andThen((context, transaction) -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(0)))
                .andThen((context,transaction) -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(1)));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    @Order(7)
    void testStakeDeRegistration_scriptHashAsStakeKey() throws ApiException, CborSerializationException, IOException {
        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr9, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        Policy policy = loadJsonPolicyScript(STAKEREGISTRATION_POLICY_JSON);

        System.out.println(JsonUtil.getPrettyJson(policy));

        //-- Stake key deregistration with script hash
        StakeDeregistration stakeDeregistration = new StakeDeregistration(StakeCredential.fromScript(policy.getPolicyScript()));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr9))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeDeregistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr9.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().add(deposit)));
                })
                .andThen((context, txn) -> {
                    txn.getWitnessSet().getNativeScripts().add(policy.getPolicyScript());
                })
                .andThen(feeCalculator(senderAddr9, 3))
                .andThen(adjustChangeOutput(senderAddr9, 3));

        TxSigner signer = SignerProviders.signerFrom(senderAccount9)
                .andThen((context, transaction) -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(0)))
                .andThen((context,transaction) -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(1)));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    protected void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = getBackendService().getTransactionService()
                            .getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Policy loadJsonPolicyScript(String policyScriptJsonFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(policyScriptJsonFile), Policy.class);
    }
}
