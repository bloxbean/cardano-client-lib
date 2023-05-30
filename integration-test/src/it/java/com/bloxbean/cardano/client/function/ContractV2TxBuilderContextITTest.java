package com.bloxbean.cardano.client.function;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.model.*;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.util.ScriptDataHashGenerator;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.CostModelUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.CollateralBuilders.collateralFrom;
import static com.bloxbean.cardano.client.function.helper.CollateralBuilders.collateralOutputs;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.*;
import static com.bloxbean.cardano.client.function.helper.OutputBuilders.createFromOutput;
import static com.bloxbean.cardano.client.function.helper.ScriptCallContextProviders.scriptCallContext;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static com.bloxbean.cardano.client.transaction.util.CostModelUtil.PlutusV2CostModel;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractV2TxBuilderContextITTest extends BaseITTest {
    BackendService backendService;
    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    EpochService epochService;
    MetadataService metadataService;
    ProtocolParams protocolParams;
    UtxoSupplier utxoSupplier;

    Account sender;
    String senderAddress;

    @BeforeEach
    public void setup() throws ApiException {
        backendService = getBackendService();
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        transactionHelperService = backendService.getTransactionHelperService();
        blockService = backendService.getBlockService();
        feeCalculationService = backendService.getFeeCalculationService(transactionHelperService);
        epochService = backendService.getEpochService();
        metadataService = backendService.getMetadataService();

        protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        utxoSupplier = new DefaultUtxoSupplier(utxoService);

//        String senderMnemonic = "company coast prison denial unknown design paper engage sadness employ phone cherry thunder chimney vapor cake lock afraid frequent myself engage lumber between tip";
        //addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender = new Account(Networks.testnet(), senderMnemonic);
        senderAddress = sender.baseAddress();
    }

    @Test
    void alwaysSuccessContractCall_inlineDatum_functions() throws CborSerializationException, AddressExcepion, ApiException, CborDeserializationException, CborException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).getAddress();

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData,  null, null);
        assertTrue(paymentSuccessful);

        String collateral = "265d8a13536c20af5529ceabaf1a6123cb9c31d0bd6540cf4e59e85aa1fb09d4";
        int collateralIndex = 0;
        Utxo collateralUtxo = checkCollateral(sender, collateral, collateralIndex);
        if (collateralUtxo == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        System.out.println("script address: " + scriptAddress);
        System.out.println("randInt >> " + plutusData.getValue());

        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);

        Set<Utxo> utxos = utxoSelectionStrategy.selectByInlineDatum(scriptAddress, new Amount(LOVELACE, BigInteger.valueOf(1)), plutusData, Set.of(collateralUtxo));

        assertTrue(utxos.size() != 0, "No script utxo found for inlineDatum(hex) : " + plutusData.serializeToHex());
        Utxo scriptUtxo = utxos.iterator().next();

        Output output = Output.builder()
                .address(sender.baseAddress())
                .assetName(LOVELACE)
                .qty(scriptAmt)
                .build();

        //Mem and steps are set to 0, as we are going to evaluate those in this test
        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(0))
                .steps(BigInteger.valueOf(0)).build();

        TxBuilder txBuilder = output.outputBuilder()
                .buildInputs(createFromUtxos(Arrays.asList(scriptUtxo), senderAddress))
                .andThen(collateralFrom(collateralUtxo.getTxHash(), collateralUtxo.getOutputIndex()))
                .andThen(scriptCallContext(plutusScript, scriptUtxo, null, redeemerData, RedeemerTag.Spend, exUnits))
                .andThen((context, txn) -> { //Evaluate ExUnits
                    //update estimate ExUnits
                    ExUnits estimatedExUnits;
                    try {
                        estimatedExUnits = evaluateExUnits(txn);
                        txn.getWitnessSet().getRedeemers().get(0).setExUnits(estimatedExUnits);
                    } catch (Exception e) {
                        throw new ApiRuntimeException("Script cost evaluation failed", e);
                    }
                })
                .andThen(balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signer);

        Result<String> result = transactionService.submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void alwaysSuccessContractCall_inlineDatum_lowlevel() throws CborSerializationException, AddressExcepion, ApiException, CborDeserializationException, CborException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).getAddress();

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData, null, null);
        assertTrue(paymentSuccessful);

        String collateral = "265d8a13536c20af5529ceabaf1a6123cb9c31d0bd6540cf4e59e85aa1fb09d4";
        int collateralIndex = 0;
        Utxo collateralUtxo = checkCollateral(sender, collateral, collateralIndex);
        if (collateralUtxo == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        System.out.println("script address: " + scriptAddress);

        System.out.println("randInt >> " + plutusData.getValue());
        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);

        Set<Utxo> utxos = utxoSelectionStrategy.selectByInlineDatum(scriptAddress, new Amount(LOVELACE, BigInteger.valueOf(1)), plutusData, Set.of(collateralUtxo));

        assertTrue(utxos.size() != 0, "No script utxo found for inlineDatum(hex) : " + plutusData.serializeToHex());
        Utxo inputUtxo = utxos.iterator().next();

        //Find utxos first and then create inputs
        List<TransactionInput> inputs = Arrays.asList(
                TransactionInput.builder()
                        .transactionId(inputUtxo.getTxHash())
                        .index(inputUtxo.getOutputIndex()).build()
        );

        TransactionInput collateralInput = TransactionInput.builder()
                .transactionId(collateralUtxo.getTxHash())
                .index(collateralUtxo.getOutputIndex()).build();

        TransactionOutput change = TransactionOutput
                .builder()
                .address(sender.baseAddress())
                .value(new Value(scriptAmt, null)) //Actual amount will be set after fee estimation
                .build();

        List<TransactionOutput> outputs = Arrays.asList(change);

        //Create the transaction body with dummy fee
        TransactionBody body = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .collateral(Arrays.asList(collateralInput))
                .fee(BigInteger.valueOf(170000)) //Dummy fee
                .ttl(getTtl())
                .networkId(NetworkId.TESTNET)
                .build();

        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData)
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1700))
                        .steps(BigInteger.valueOf(476468)).build()
                ).build();

        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.setPlutusV2Scripts(Arrays.asList(plutusScript));
        transactionWitnessSet.setRedeemers(Arrays.asList(redeemer));

        byte[] scriptDataHash = ScriptDataHashGenerator.generate(Arrays.asList(redeemer),
                Collections.emptyList(), CostModelUtil.getLanguageViewsEncoding(PlutusV2CostModel));
        body.setScriptDataHash(scriptDataHash);

        MessageMetadata metadata = MessageMetadata.create()
                .add("PlutusV2 Test - Cardano Client Lib");

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        Transaction transaction = Transaction.builder()
                .body(body)
                .witnessSet(transactionWitnessSet)
                .auxiliaryData(auxiliaryData)
                .build();

        System.out.println(transaction);
        Transaction signTxnForFeeCalculation = sender.sign(transaction);

        BigInteger baseFee = feeCalculationService.calculateFee(signTxnForFeeCalculation);
        BigInteger scriptFee = feeCalculationService.calculateScriptFee(Arrays.asList(redeemer.getExUnits()));
        BigInteger totalFee = baseFee.add(scriptFee);

        System.out.println("Total Fee ----- " + totalFee);

        //Update change amount based on fee
        BigInteger changeAmt = scriptAmt.subtract(totalFee);
        change.getValue().setCoin(changeAmt);
        body.setFee(totalFee);

        System.out.println("-- fee : " + totalFee);

        Transaction signTxn = sender.sign(transaction); //cbor encoded bytes in Hex format
        System.out.println(signTxn);

        System.out.printf(signTxn.serializeToHex());

        Result<String> result = transactionService.submitTransaction(signTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void alwaysSuccessContractCall_scriptReference_functions() throws CborSerializationException, AddressExcepion, ApiException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).getAddress();

        //Uncomment to create a script reference
//        createScriptReferenceOutput(sender, scriptAddress, plutusScript);

        TransactionInput refScriptInput = TransactionInput.builder()
                .transactionId("a6a46e2776b74a1356541ab587ffc10cb7a1ad55e845f2b11190174db3727123")
                .index(0).build();

        Random rand = new Random();
        int randInt = rand.nextInt(); //566992858; //
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData, null, null);
        assertTrue(paymentSuccessful);

        String collateral = "265d8a13536c20af5529ceabaf1a6123cb9c31d0bd6540cf4e59e85aa1fb09d4";
        int collateralIndex = 0;
        Utxo collateralUtxo = checkCollateral(sender, collateral, collateralIndex);
        if (collateralUtxo == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        System.out.println("script address: " + scriptAddress);

        System.out.println("randInt >> " + plutusData.getValue());
        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);

        Set<Utxo> utxos = utxoSelectionStrategy.selectByInlineDatum(scriptAddress, new Amount(LOVELACE, BigInteger.valueOf(1)), plutusData, Set.of(collateralUtxo));

        assertTrue(utxos.size() != 0, "No script utxo found for inlineDatum(hex) : " + plutusData.serializeToHex());
        Utxo inputUtxo = utxos.iterator().next();

        TransactionOutput output = TransactionOutput
                .builder()
                .address(sender.baseAddress())
                .value(new Value(scriptAmt, null)) //Actual amount will be set after fee estimation
                .build();

        //Mem and steps are set to 0, as we are going to evaluate those in this test
        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(0))
                .steps(BigInteger.valueOf(0)).build();

        TxBuilder txBuilder = createFromOutput(output)
                .buildInputs(createFromUtxos(Arrays.asList(inputUtxo), senderAddress))
                .andThen(collateralFrom(collateralUtxo.getTxHash(), collateralUtxo.getOutputIndex()))
                .andThen(referenceInputsFrom(List.of(refScriptInput)))
                .andThen(scriptCallContext(plutusScript, inputUtxo, null, redeemerData, RedeemerTag.Spend, exUnits))
                .andThen((context, txn) -> { //Evaluate ExUnits
                    //update estimate ExUnits
                    ExUnits estimatedExUnits;
                    try {
                        estimatedExUnits = evaluateExUnits(txn);
                        txn.getWitnessSet().getRedeemers().get(0).setExUnits(estimatedExUnits);
                    } catch (Exception e) {
                        throw new ApiRuntimeException("Script cost evaluation failed", e);
                    }

                    //Remove script from witnessset.
                    //TODO - handle this in composable function
                    txn.getWitnessSet().getPlutusV2Scripts().clear();
                })
                .andThen(balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signer);

        System.out.printf("data hash: " + HexUtil.encodeHexString(signedTxn.getBody().getScriptDataHash()));
        System.out.printf("Txn: " + signedTxn);

        Result<String> result = transactionService.submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void alwaysSuccessContractCall_scriptReference_lowlevel() throws CborSerializationException, AddressExcepion, ApiException, CborException {
        PlutusV2Script plutusScript1 = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = AddressProvider.getEntAddress(plutusScript1, Networks.testnet()).getAddress();

        //Uncomment to create a script reference
        //createScriptReferenceOutput(sender, scriptAddress, plutusScript1);

        TransactionInput refScriptInput = TransactionInput.builder()
                .transactionId("a6a46e2776b74a1356541ab587ffc10cb7a1ad55e845f2b11190174db3727123")
                .index(0).build();

        Random rand = new Random();
        int randInt = rand.nextInt(); //566992858; //
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData, null, null);
        assertTrue(paymentSuccessful);

        String collateral = "265d8a13536c20af5529ceabaf1a6123cb9c31d0bd6540cf4e59e85aa1fb09d4";
        int collateralIndex = 0;
        Utxo collateralUtxo = checkCollateral(sender, collateral, collateralIndex);
        if (collateralUtxo == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        System.out.println("script address: " + scriptAddress);

        System.out.println("randInt >> " + plutusData.getValue());
        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);
        Set<Utxo> utxos = utxoSelectionStrategy.selectByInlineDatum(scriptAddress, new Amount(LOVELACE, BigInteger.valueOf(1)), plutusData, Set.of(collateralUtxo));
        assertTrue(utxos.size() != 0, "No script utxo found for inlineDatum(hex) : " + plutusData.serializeToHex());
        Utxo inputUtxo = utxos.iterator().next();

        //Find utxos first and then create inputs
        List<TransactionInput> inputs = Arrays.asList(
                TransactionInput.builder()
                        .transactionId(inputUtxo.getTxHash())
                        .index(inputUtxo.getOutputIndex()).build()
        );

        TransactionInput collateralInput = TransactionInput.builder()
                .transactionId(collateralUtxo.getTxHash())
                .index(collateralUtxo.getOutputIndex()).build();

        TransactionOutput change = TransactionOutput
                .builder()
                .address(sender.baseAddress())
                .value(new Value(scriptAmt, null)) //Actual amount will be set after fee estimation
                .build();

        List<TransactionOutput> outputs = Arrays.asList(change);

        //Create the transaction body with dummy fee
        TransactionBody body = TransactionBody.builder()
                .referenceInputs(Arrays.asList(refScriptInput))
                .inputs(inputs)
                .outputs(outputs)
                .collateral(Arrays.asList(collateralInput))
                .fee(BigInteger.valueOf(170000)) //Dummy fee
                .ttl(getTtl())
                .networkId(NetworkId.TESTNET)
                .build();

        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData)
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1700))
                        .steps(BigInteger.valueOf(476468)).build()
                ).build();


        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.setRedeemers(Arrays.asList(redeemer));

        byte[] scriptDataHash = ScriptDataHashGenerator.generate(Arrays.asList(redeemer),
                Collections.emptyList(), CostModelUtil.getLanguageViewsEncoding(PlutusV2CostModel));
        body.setScriptDataHash(scriptDataHash);

        MessageMetadata metadata = MessageMetadata.create()
                .add("PlutusV2 Test - Cardano Client Lib");

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        Transaction transaction = Transaction.builder()
                .body(body)
                .witnessSet(transactionWitnessSet)
                .auxiliaryData(auxiliaryData)
                .build();

        System.out.println(transaction);
        Transaction signTxnForFeeCalculation = sender.sign(transaction);

        BigInteger baseFee = feeCalculationService.calculateFee(signTxnForFeeCalculation);
        BigInteger scriptFee = feeCalculationService.calculateScriptFee(Arrays.asList(redeemer.getExUnits()));
        BigInteger totalFee = baseFee.add(scriptFee);

        System.out.println("Total Fee ----- " + totalFee);

        //Update change amount based on fee
        BigInteger changeAmt = scriptAmt.subtract(totalFee);
        change.getValue().setCoin(changeAmt);
        body.setFee(totalFee);

        System.out.println("-- fee : " + totalFee);

        Transaction signTxn = sender.sign(transaction); //cbor encoded bytes in Hex format
        System.out.println(signTxn);

        System.out.printf(signTxn.serializeToHex());

        Result<String> result = transactionService.submitTransaction(signTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void alwaysSuccessContractCall_collateralOutput_functions() throws CborSerializationException, AddressExcepion, ApiException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).getAddress();

        //Uncomment to create a script reference
//        createScriptReferenceOutput(sender, scriptAddress, plutusScript);

        TransactionInput refScriptInput = TransactionInput.builder()
                .transactionId("a6a46e2776b74a1356541ab587ffc10cb7a1ad55e845f2b11190174db3727123")
                .index(0).build();

        Random rand = new Random();
        int randInt = rand.nextInt(); //566992858; //
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData, null, null);
        assertTrue(paymentSuccessful);

        String collateral = "16b786523c20539b1fc9e79f9ae92b8abe691cd92573099264f192a47ec8e435";
        int collateralIndex = 0;
        Utxo collateralUtxo = checkCollateral(sender, collateral, collateralIndex);
        if (collateralUtxo == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        System.out.println("script address: " + scriptAddress);

        System.out.println("randInt >> " + plutusData.getValue());
        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);
        Set<Utxo> utxos = utxoSelectionStrategy.selectByInlineDatum(scriptAddress, new Amount(LOVELACE, BigInteger.valueOf(1)), plutusData, Set.of(collateralUtxo));

        assertTrue(utxos.size() != 0, "No script utxo found for inlineDatum(hex) : " + plutusData.serializeToHex());
        Utxo inputUtxo = utxos.iterator().next();

        TransactionOutput output = TransactionOutput
                .builder()
                .address(sender.baseAddress())
                .value(new Value(scriptAmt, null)) //Actual amount will be set after fee estimation
                .build();

        //Mem and steps are set to 0, as we are going to evaluate those in this test
        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(0))
                .steps(BigInteger.valueOf(0)).build();

        TxBuilder txBuilder = createFromOutput(output)
                .buildInputs(createFromUtxos(Arrays.asList(inputUtxo), senderAddress))
                .andThen(collateralOutputs(senderAddress, List.of(collateralUtxo)))
                .andThen((context, txn) -> {
                    txn.getBody().getReferenceInputs().add(refScriptInput);
                })
                .andThen(scriptCallContext(plutusScript, inputUtxo, null, redeemerData, RedeemerTag.Spend, exUnits))
                .andThen((context, txn) -> { //Evaluate ExUnits
                    //update estimate ExUnits
                    ExUnits estimatedExUnits;
                    try {
                        estimatedExUnits = evaluateExUnits(txn);
                        txn.getWitnessSet().getRedeemers().get(0).setExUnits(estimatedExUnits);
                    } catch (Exception e) {
                        throw new ApiRuntimeException("Script cost evaluation failed", e);
                    }

                    //Remove script from witnessset.
                    //TODO - handle this in composable function
                    txn.getWitnessSet().getPlutusV2Scripts().clear();
                })
                .andThen(balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);
        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signer);

        System.out.printf("data hash: " + HexUtil.encodeHexString(signedTxn.getBody().getScriptDataHash()));
        System.out.printf("Txn: " + signedTxn);
        System.out.printf("txn hex : " + signedTxn.serializeToHex());

        Result<String> result = transactionService.submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void mint() throws Exception {
        String receiverAddress = "addr_test1qrs2a2hjfs2wt8r3smzwmptezmave3yjgws068hp0qsflmcypglx0rl69tp49396282ns02caz4cx7a2n290h2df0j3qjku4dy";
        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
        PlutusScript plutusScript1 = getPlutusScript(aikenCompiledCode1);

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
        PlutusScript plutusScript2 = getPlutusScript(aikenCompileCode2);

        String collateral = "16b786523c20539b1fc9e79f9ae92b8abe691cd92573099264f192a47ec8e435";
        int collateralIndex = 0;
        Utxo collateralUtxo = checkCollateral(sender, collateral, collateralIndex);
        if (collateralUtxo == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        Asset asset1 = new Asset("PlutusToken-1", BigInteger.valueOf(3000));
        Asset asset2 = new Asset("PlutusToken-2", BigInteger.valueOf(8000));

        MultiAsset multiAsset1 = MultiAsset.builder()
                .policyId(plutusScript1.getPolicyId())
                .assets(Arrays.asList(asset1))
                .build();

        MultiAsset multiAsset2 = MultiAsset.builder()
                .policyId(plutusScript2.getPolicyId())
                .assets(Arrays.asList(asset2))
                .build();

        TransactionOutput mintOutput = TransactionOutput
                .builder()
                .address(receiverAddress)
                .value(new Value(BigInteger.ZERO, Arrays.asList(multiAsset1, multiAsset2)))
                .build();

        MessageMetadata metadata = MessageMetadata.create()
                .add("NFT minted by Plutus script");

        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(989624))
                .steps(BigInteger.valueOf(514842019)).build();

        TxBuilder txBuilder = OutputBuilders.createFromMintOutput(mintOutput)
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(CollateralBuilders.collateralFrom(collateralUtxo.getTxHash(), collateralUtxo.getOutputIndex()))
                .andThen(MintCreators.mintCreator(plutusScript1, multiAsset1))
                .andThen(MintCreators.mintCreator(plutusScript2, multiAsset2))
                .andThen(ScriptCallContextProviders.scriptCallContext(plutusScript1, null, null, BigIntPlutusData.of(1), RedeemerTag.Mint, exUnits))
                .andThen(ScriptCallContextProviders.scriptCallContext(plutusScript2, null, null, BigIntPlutusData.of(2), RedeemerTag.Mint, exUnits))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signer);

        System.out.println(signTxn);
        Result<String> result = transactionService.submitTransaction(signTxn.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    //TODO - test for failed txn with collateral outputs

    private ExUnits evaluateExUnits(Transaction transaction) throws ApiException, CborSerializationException {
        if (backendType.equals(BLOCKFROST)) {
            Result<List<EvaluationResult>> evalResults = transactionService.evaluateTx(transaction.serialize());
            if (evalResults.isSuccessful()) {
                return evalResults.getValue().get(0).getExUnits();
            } else {
                return null;
            }
        } else {
            //Hard coded value for other backend types where evaluateTx is not yet supported
            return ExUnits.builder()
                    .mem(BigInteger.valueOf(4676948))
                    .steps(BigInteger.valueOf(630892334)).build();
        }
    }

    private long getTtl() throws ApiException {
        Block block = blockService.getLatestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    private Utxo checkCollateral(Account sender, final String collateralUtxoHash, final int collateralIndex) throws ApiException, AddressExcepion, CborSerializationException {
        List<Utxo> utxos = utxoService.getUtxos(sender.baseAddress(), 100, 1).getValue(); //Check 1st page 100 utxos
        Optional<Utxo> collateralUtxoOption = utxos.stream().filter(utxo -> utxo.getTxHash().equals(collateralUtxoHash))
                .findAny();

        if (collateralUtxoOption.isPresent()) {//Collateral present
            System.out.println("--- Collateral utxo still there");
            return collateralUtxoOption.get();
        } else {

            Utxo randomCollateral = getRandomUtxoForCollateral(sender.baseAddress());
            if (randomCollateral != null) {
                System.out.println("Found random collateral ---");
                return randomCollateral;
            } else {
                System.out.println("*** Collateral utxo not found");

                //Transfer to self to create collateral utxo
                BigInteger collateralAmt = BigInteger.valueOf(8000000L);
                transferFund(sender, sender.baseAddress(), collateralAmt, null,null, null);

                //Find collateral utxo again
                utxos = utxoService.getUtxos(sender.baseAddress(), 100, 1).getValue();
                collateralUtxoOption = utxos.stream().filter(utxo -> {
                    if (utxo.getAmount().size() == 1 //Assumption: 1 Amount means, only LOVELACE
                            && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                            && collateralAmt.equals(utxo.getAmount().get(0).getQuantity()))
                        return true;
                    else
                        return false;
                }).findFirst();

                if (!collateralUtxoOption.isPresent()) {
                    System.out.println("Collateral cannot be created");
                    return null;
                }

                Utxo collateral = collateralUtxoOption.get();

                return collateral;
            }
        }
    }

    private boolean transferFund(Account sender, String recevingAddress, BigInteger amount, PlutusData plutusData,
                                 String collateralUtxoHash, Integer collateralIndex) throws CborSerializationException, ApiException {

        //Ignore collateral utxos
        Set ignoreUtxos = new HashSet();
        if (collateralUtxoHash != null) {
            Utxo collateralUtxo = Utxo.builder()
                    .txHash(collateralUtxoHash)
                    .outputIndex(collateralIndex)
                    .build();
            ignoreUtxos.add(collateralUtxo);
        }

        Output output = Output.builder()
                .address(recevingAddress)
                .assetName(LOVELACE)
                .qty(amount)
                .datum(plutusData)
                .inlineDatum(true)
                .build();

        TxBuilder txBuilder = output.outputBuilder()
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(balanceTx(senderAddress, 1));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender));

        System.out.println(signedTxn);
        System.out.printf(signedTxn.serializeToHex());

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        waitForTransaction(result);
        return result.isSuccessful();
    }

    private boolean createScriptReferenceOutput(Account sender, String recevingAddress, PlutusScript script) throws CborSerializationException, AddressExcepion, ApiException {
        TransactionOutput output = TransactionOutput.builder()
                .address(recevingAddress)
                .scriptRef(script)
                .value(Value.builder().coin(BigInteger.ZERO).build())
                .build();

        TxBuilder txBuilder = createFromOutput(output)
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(balanceTx(senderAddress, 1));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender));

        System.out.println(signedTxn);
        System.out.printf(signedTxn.serializeToHex());

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        waitForTransaction(result);
        return result.isSuccessful();
    }

    private Utxo getRandomUtxoForCollateral(String address) throws ApiException {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        //Find 5 > utxo > 10 ada
        Optional<Utxo> optional = utxoSelector.findFirst(address, u -> {
            if (u.getAmount().size() == 1
                    && u.getAmount().get(0).getQuantity().compareTo(adaToLovelace(5)) == 1
                    && u.getAmount().get(0).getQuantity().compareTo(adaToLovelace(10)) == -1)
                return true;
            else
                return false;
        });

        return optional.orElse(null);
    }

    private void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = backendService.getTransactionService().getTransaction(result.getValue());
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

    @Constr
    static class Guess {
        @PlutusField
        Integer number;

        public Guess(int number) {
            this.number = number;
        }
    }
}
