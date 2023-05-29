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

    @BeforeEach
    public void setup() throws ApiException {
        protocolParams = getBackendService().getEpochService().getProtocolParameters().getValue();
    }

    @Test
    @Order(1)
    void testStakeRegistration_addressKeyAsStakeKey() throws ApiException, CborSerializationException {
        String senderMnemonic = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount.stakeHdKeyPair();
        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();

        //-- Stake key registration
        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromKey(stakePublicKey));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeRegistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().subtract(deposit)));
                })
                .andThen(feeCalculator(senderAddr, 2))
                .andThen(adjustChangeOutput(senderAddr, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount);

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
        String senderMnemonic = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount.stakeHdKeyPair();
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
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeDelegation);
                })
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(feeCalculator(senderAddr, 2))
                .andThen(adjustChangeOutput(senderAddr, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount)
                .andThen(transaction -> senderAccount.signWithStakeKey(transaction));

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
        String senderMnemonic = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount.stakeHdKeyPair();
        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();

        //-- Stake key deregistration
        StakeDeregistration stakeRegistration = new StakeDeregistration(StakeCredential.fromKey(stakePublicKey));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeRegistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().add(deposit)));
                })
                .andThen(feeCalculator(senderAddr, 2))
                .andThen(adjustChangeOutput(senderAddr, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount)
                .andThen(transaction -> TransactionSigner.INSTANCE.sign(transaction, stakeHdKeyPair));

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
        String senderMnemonic = "farm hunt wasp believe happy palm skull apple execute paddle asthma absorb misery unlock broom turkey few dry focus vacuum novel crumble dish token";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();
        System.out.println(senderAddr);

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        HdKeyPair stakeHdKeyPair = senderAccount.stakeHdKeyPair();
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
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeDelegation);
                })
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(feeCalculator(senderAddr, 2))
                .andThen(adjustChangeOutput(senderAddr, 2));

        TxSigner signer = SignerProviders.signerFrom(senderAccount)
                .andThen(transaction -> senderAccount.signWithStakeKey(transaction));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }

    @Test
    void testWithdrawal() throws ApiException, CborSerializationException {
        String senderMnemonic = "farm hunt wasp believe happy palm skull apple execute paddle asthma absorb misery unlock broom turkey few dry focus vacuum novel crumble dish token";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        String senderMnemonic2 = "limb myself better pyramid home measure quality smile also reveal used sleep kind trend destroy output guide test memory clever spoil polar salon artist";
        Account senderAccount2 = new Account(Networks.testnet(), senderMnemonic2);

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        //-- Stake addresses
        String stakeAddress = senderAccount.stakeAddress();
        String stakeAddress2 = senderAccount2.stakeAddress();

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    //Currently zero for testing as there is no reward available. But update this value later once reward is available.
                    BigInteger rewardAmt = BigInteger.ZERO;
                    txn.getBody().getWithdrawals().add(new Withdrawal(stakeAddress, rewardAmt));
                    txn.getBody().getWithdrawals().add(new Withdrawal(stakeAddress2, rewardAmt)); //another dummy withdrawal
                    //Add reward amount to output
                    txn.getBody().getOutputs().get(0).getValue().getCoin().add(rewardAmt);
                })
                .andThen(feeCalculator(senderAddr, 3))
                .andThen(adjustChangeOutput(senderAddr, 3));

        TxSigner signer = SignerProviders.signerFrom(senderAccount)
                .andThen(transaction -> senderAccount.signWithStakeKey(transaction))
                .andThen(transaction -> senderAccount2.signWithStakeKey(transaction));

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
        String senderMnemonic = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        Policy policy = loadJsonPolicyScript(STAKEREGISTRATION_POLICY_JSON);

        System.out.println(JsonUtil.getPrettyJson(policy));

        //-- Stake key registration with script hash
        StakeRegistration stakeRegistration = new StakeRegistration(StakeCredential.fromScript(policy.getPolicyScript()));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeRegistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().subtract(deposit)));
                })
                .andThen(feeCalculator(senderAddr, 1))
                .andThen(adjustChangeOutput(senderAddr, 1));

        TxSigner signer = SignerProviders.signerFrom(senderAccount);

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
        String stakingPaymentAccountMnemonic = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";
        Account stakingPaymentAccount = new Account(Networks.testnet(), stakingPaymentAccountMnemonic);

        Policy policy = loadJsonPolicyScript(STAKEREGISTRATION_POLICY_JSON);

        //Get a address for payment key (Account at address 0) and Script as delegation key
        Address address = AddressProvider.getBaseAddress(stakingPaymentAccount.hdKeyPair().getPublicKey(), policy.getPolicyScript(), Networks.testnet());
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
                .andThen(transaction -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(0)))
                .andThen(transaction -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(1)));

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
        String senderMnemonic = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        //protocol params
        String depositStr = getBackendService().getEpochService().getProtocolParameters().getValue().getKeyDeposit();
        BigInteger deposit = new BigInteger(depositStr);

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        List<Utxo> utxos = selectionStrategy.selectUtxos(senderAddr, LOVELACE, deposit.add(adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        Policy policy = loadJsonPolicyScript(STAKEREGISTRATION_POLICY_JSON);

        System.out.println(JsonUtil.getPrettyJson(policy));

        //-- Stake key deregistration with script hash
        StakeDeregistration stakeDeregistration = new StakeDeregistration(StakeCredential.fromScript(policy.getPolicyScript()));

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(utxos, senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(stakeDeregistration);
                })
                .andThen((context, txn) -> {
                    txn.getBody().getOutputs().stream().filter(transactionOutput -> senderAddr.equals(transactionOutput.getAddress()))
                            .findFirst()
                            .ifPresent(transactionOutput -> transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().add(deposit)));
                })
                .andThen((context, txn) -> {
                    txn.getWitnessSet().getNativeScripts().add(policy.getPolicyScript());
                })
                .andThen(feeCalculator(senderAddr, 3))
                .andThen(adjustChangeOutput(senderAddr, 3));

        TxSigner signer = SignerProviders.signerFrom(senderAccount)
                .andThen(transaction -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(0)))
                .andThen(transaction -> TransactionSigner.INSTANCE.sign(transaction, policy.getPolicyKeys().get(1)));

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
