package com.bloxbean.cardano.client.function;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressService;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.CostModelUtil;
import com.bloxbean.cardano.client.transaction.util.ScriptDataHashGenerator;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.ChangeOutputAdjustments.adjustChangeOutput;
import static com.bloxbean.cardano.client.function.helper.CollateralBuilders.collateralFrom;
import static com.bloxbean.cardano.client.function.helper.FeeCalculators.feeCalculator;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromUtxos;
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
        String scriptAddress = AddressService.getInstance().getEntAddress(plutusScript, Networks.testnet()).getAddress();

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData, null, null);
        assertTrue(paymentSuccessful);

        String collateral = "265d8a13536c20af5529ceabaf1a6123cb9c31d0bd6540cf4e59e85aa1fb09d4";
        int collateralIndex = 0;

        Tuple<String, Integer> collateralTuple = checkCollateral(sender, collateral, collateralIndex);
        if (collateralTuple == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        collateral = collateralTuple._1;
        collateralIndex = collateralTuple._2;

        System.out.println("script address: " + scriptAddress);
        System.out.println("randInt >> " + plutusData.getValue());

        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);
        Utxo collateralUtxo = Utxo.builder()
                .txHash(collateral)
                .outputIndex(collateralIndex)
                .build();
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
                .buildInputs(createFromUtxos(Arrays.asList(scriptUtxo)))
                .andThen(collateralFrom(collateral, collateralIndex))
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
                .andThen(feeCalculator(senderAddress, 1))
                .andThen(adjustChangeOutput(senderAddress)); //Incase change output goes below min ada after fee deduction

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
        String scriptAddress = AddressService.getInstance().getEntAddress(plutusScript, Networks.testnet()).getAddress();

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number
        PlutusData redeemerData = new BigIntPlutusData(BigInteger.valueOf(4544)); //any number doesn't matter

        //Transfer fund to script address
        boolean paymentSuccessful = transferFund(sender, scriptAddress, scriptAmt, plutusData, null, null);
        assertTrue(paymentSuccessful);

        String collateral = "265d8a13536c20af5529ceabaf1a6123cb9c31d0bd6540cf4e59e85aa1fb09d4";
        int collateralIndex = 0;

        Tuple<String, Integer> collateralTuple = checkCollateral(sender, collateral, collateralIndex);
        if (collateralTuple == null) {
            System.out.println("Collateral cannot be found or created. " + collateral);
            return;
        }

        collateral = collateralTuple._1;
        collateralIndex = collateralTuple._2;

        System.out.println("script address: " + scriptAddress);

        System.out.println("randInt >> " + plutusData.getValue());
        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);
        Utxo collateralUtxo = Utxo.builder()
                .txHash(collateral)
                .outputIndex(collateralIndex)
                .build();
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
                .transactionId(collateral)
                .index(collateralIndex).build();

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

    private Tuple<String, Integer> checkCollateral(Account sender, final String collateralUtxoHash, final int collateralIndex) throws ApiException, AddressExcepion, CborSerializationException {
        List<Utxo> utxos = utxoService.getUtxos(sender.baseAddress(), 100, 1).getValue(); //Check 1st page 100 utxos
        Optional<Utxo> collateralUtxoOption = utxos.stream().filter(utxo -> utxo.getTxHash().equals(collateralUtxoHash))
                .findAny();

        if (collateralUtxoOption.isPresent()) {//Collateral present
            System.out.println("--- Collateral utxo still there");
            return new Tuple(collateralUtxoHash, collateralIndex);
        } else {

            Utxo randomCollateral = getRandomUtxoForCollateral(sender.baseAddress());
            if (randomCollateral != null) {
                System.out.println("Found random collateral ---");
                return new Tuple<>(randomCollateral.getTxHash(), randomCollateral.getOutputIndex());
            } else {
                System.out.println("*** Collateral utxo not found");

                //Transfer to self to create collateral utxo
                BigInteger collateralAmt = BigInteger.valueOf(8000000L);
                transferFund(sender, sender.baseAddress(), collateralAmt, null, null, null);

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
                String colUtxoHash = collateral.getTxHash();
                int colIndex = collateral.getOutputIndex();

                return new Tuple(colUtxoHash, colIndex);
            }
        }
    }

    private boolean transferFund(Account sender, String recevingAddress, BigInteger amount, PlutusData plutusData,
                                 String collateralUtxoHash, Integer collateralIndex) throws CborSerializationException, AddressExcepion, ApiException {

        //Ignore collateral utxos
        Set ignoreUtxos = new HashSet();
        if (collateralUtxoHash != null) {
            Utxo collateralUtxo = Utxo.builder()
                    .txHash(collateralUtxoHash)
                    .outputIndex(collateralIndex)
                    .build();
            ignoreUtxos.add(collateralUtxo);
        }

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(sender.baseAddress(), LOVELACE, amount, ignoreUtxos);

        Output output = Output.builder()
                .address(recevingAddress)
                .assetName(LOVELACE)
                .qty(amount)
                .datum(plutusData)
                .inlineDatum(true)
                //TODO inline datum
                .build();

        TxBuilder txBuilder = output.outputBuilder()
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(feeCalculator(senderAddress, 1))
                .andThen(adjustChangeOutput(senderAddress, 1));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender));

        System.out.println(signedTxn);
        System.out.printf(signedTxn.serializeToHex());

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        waitForTransaction(result);

//        PaymentTransaction paymentTransaction =
//                PaymentTransaction.builder()
//                        .sender(sender)
//                        .receiver(recevingAddress)
//                        .amount(amount)
//                        .unit("lovelace")
//                        .datumHash(datumHash)
//                        .utxosToInclude(utxos)
//                        .build();
//
//        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
//        paymentTransaction.setFee(fee);
//
//        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());
//        if (result.isSuccessful())
//            System.out.println("Transaction Id: " + result.getValue());
//        else
//            System.out.println("Transaction failed: " + result);
//
//        if (result.isSuccessful()) {
//            Result<String> resultWithTxId = Result.success(result.getResponse()).code(result.code())
//                    .withValue(result.getValue().getTransactionId());
//
//            waitForTransaction(resultWithTxId);
//        } else {
//            System.out.println(result);
//        }

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
