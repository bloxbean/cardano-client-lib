package com.bloxbean.cardano.client.backend.api.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.api.helper.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBaseTest;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.CostModelConstants;
import com.bloxbean.cardano.client.transaction.util.ScriptDataHashGenerator;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractTransactionIT extends BFBaseTest {
    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    EpochService epochService;
    MetadataService metadataService;

    @BeforeEach
    public void setup() {
        BackendService backendService = BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        transactionHelperService = backendService.getTransactionHelperService();
        blockService = backendService.getBlockService();
        feeCalculationService = backendService.getFeeCalculationService(transactionHelperService);
        epochService = backendService.getEpochService();
        metadataService = backendService.getMetadataService();
    }

    @Test
    void alwaysSuccessContractCall() throws CborSerializationException, AddressExcepion, ApiException, CborDeserializationException, CborException {
        String senderMnemonic = "company coast prison denial unknown design paper engage sadness employ phone cherry thunder chimney vapor cake lock afraid frequent myself engage lumber between tip";
        Account sender = new Account(Networks.testnet(), senderMnemonic);

        String collateral = "06568935be80d4484485ec902676b0001a935a9b8d677fdfa2674dd6c4022479";
        int collateralIndex = 0;

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = "addr_test1wpnlxv2xv9a9ucvnvzqakwepzl9ltx7jzgm53av2e9ncv4sysemm8";

        PlutusData plutusData = new BigIntPlutusData(new BigInteger("2021"));
        String datumHash = plutusData.getDatumHash();

        //Transfer fund to script address
        boolean paymentSuccessful = transferToContractAddress(sender, scriptAddress, scriptAmt, datumHash, collateral, collateralIndex);
        assertTrue(paymentSuccessful);

        //Start contract transaction to claim fund
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoService);
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);
        List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(scriptAddress, LOVELACE, BigInteger.valueOf(1), datumHash, Collections.EMPTY_SET);

        assertTrue(utxos.size() != 0, "No script utxo found for datumhash : " + datumHash);
        Utxo inputUtxo = utxos.get(0);

        //Find utxos first and then create inputs
        List<TransactionInput> inputs = Arrays.asList(
                TransactionInput.builder()
                        .transactionId(inputUtxo.getTxHash())
                        .index(inputUtxo.getOutputIndex()).build()
        );

        TransactionInput collateralInput = TransactionInput.builder()
                .transactionId(collateral)
                .index(collateralIndex).build();


        BigInteger fee = BigInteger.valueOf(323456);
        BigInteger changeAmt = scriptAmt.subtract(fee);

        TransactionOutput change = TransactionOutput
                .builder()
                .address(sender.baseAddress())
                .value(new Value(changeAmt, null))
                .build();

        List<TransactionOutput> outputs = Arrays.asList(change);

        PlutusScript plutusScript = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        //Create the transaction body with dummy fee
        TransactionBody body = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .collateral(Arrays.asList(collateralInput))
                .ttl(getTtl())
                .fee(fee)
                .networkId(NetworkId.TESTNET)
                .build();

        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(plutusData)
                .index(BigInteger.valueOf(0))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1700))
                        .steps(BigInteger.valueOf(476468)).build()
                ).build();

        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.setPlutusScripts(Arrays.asList(plutusScript));
        transactionWitnessSet.setPlutusDataList(Arrays.asList(plutusData));
        transactionWitnessSet.setRedeemers(Arrays.asList(redeemer));

        byte[] scriptDataHash = ScriptDataHashGenerator.generate(Arrays.asList(redeemer),
                Arrays.asList(plutusData), CostModelConstants.LANGUAGE_VIEWS);
        body.setScriptDataHash(scriptDataHash);

        CBORMetadata cborMetadata = new CBORMetadata();
        CBORMetadataMap metadataMap = new CBORMetadataMap();
        CBORMetadataList metadataList = new CBORMetadataList();
        metadataList.add("Contract call");
        metadataMap.put("msg", metadataList);
        cborMetadata.put(new BigInteger("674"), metadataMap);

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(cborMetadata)
                .plutusScripts(Arrays.asList(plutusScript))
                .build();

        Transaction transaction = Transaction.builder()
                .body(body)
                .witnessSet(transactionWitnessSet)
                .auxiliaryData(auxiliaryData)
                .isValid(true)
                .build();

        String hex = transaction.serializeToHex();
        System.out.println(hex);
        //Sign the transaction. so that we get the actual size of the transaction to calculate the fee
        String signTxnHash = sender.sign(transaction); //cbor encoded bytes in Hex format

        //Calculate fees
        byte[] signTxnBytes = HexUtil.decodeHexString(signTxnHash);

        Result<String> result = transactionService.submitTransaction(signTxnBytes);
        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

//    @Test
//    public void spaceBudzBid() throws CborSerializationException, AddressExcepion, ApiException, CborDeserializationException, CborException {
//        String scriptAddress = "addr1wyzynye0nksztrfzpsulsq7whr3vgh7uvp0gm4p0x42ckkqqq6kxq";
//
//        //Tuple t = AssetUtil.getPolicyIdAndAssetName("800df05a0cc6b6f0d28aaa1812135bd9eebfbf5e8e80fd47da9989eb53706163654275644269643530");
//        String policyId = "800df05a0cc6b6f0d28aaa1812135bd9eebfbf5e8e80fd47da9989eb";
//        String assetName = HexUtil.encodeHexString("SpaceBudBid2413".getBytes(StandardCharsets.UTF_8));
//        String unit = policyId + assetName;
//
//        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoService);
//        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(false);
//        List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(scriptAddress, unit, BigInteger.valueOf(1), Collections.EMPTY_SET);
//
//        Utxo scriptUtxo = utxos.get(0);
//
//        List<MetadataJSONContent> metadataList = metadataService.getJSONMetadataByTxnHash(scriptUtxo.getTxHash()).getValue();
//
//        String datum = metadataList.stream().filter( metadataJSONContent -> metadataJSONContent.getLabel().equals("405"))
//                .findFirst()
//                .get().getJsonMetadata()
//                .get(scriptUtxo.getOutputIndex() + "").asText();
//
//        ConstrPlutusData START_BID = ConstrPlutusData.builder()
//                .alternative(0)
//                .data(new ListPlutusData())
//                .build();
//        String START_BID_HEX = START_BID.getDatumHash();
//
//        System.out.println(datum);
//        String currentOwner = null;
//        if (!datum.equals(START_BID_HEX)) {
//            String currentOwnerHex = metadataList.stream().filter( metadataJSONContent -> metadataJSONContent.getLabel().equals("406"))
//                    .findFirst()
//                    .get().getJsonMetadata()
//                    .get("address").asText();
//
//            currentOwner = Account.bytesToBech32(HexUtil.decodeHexString(currentOwnerHex));
//        }
//
//        System.out.println(currentOwner);
//
////        const DATUM_TYPE = {
////                StartBid: 0,
////                Bid: 1,
////                Offer: 2,
////        };
//
////        String tradeOwnerAddress =
//
////        ListPlutusData bidListPlutusData = ListPlutusData.builder()
////                .plutusDataList(Arrays.asList(
////
////                )).build();
////
////
////        ConstrPlutusData BID = ConstrPlutusData.builder()
//
////        BigIntPlutusData bytesPlutusData = new BigIntPlutusData(BigInteger.valueOf(0));
////        ListPlutusData listPlutusData = new ListPlutusData();
////
////        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
////                .alternative(0)
////                .data(listPlutusData)
////                .build();
////
////        String datumHash = constrPlutusData.getDatumHash();
////
////        String datumHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(constrPlutusData.serialize()));
////
////        System.out.println(datumHash);
////        System.out.println(datumHex);
//
//    }

    private boolean transferToContractAddress(Account sender, String scriptAddress, BigInteger amount, String datumHash,
                                              String collateralTxHash, int collateralIndex) throws CborSerializationException, AddressExcepion, ApiException {

        Utxo collateralUtxo = Utxo.builder()
                .txHash(collateralTxHash)
                .outputIndex(collateralIndex)
                .build();
        Set ignoreUtxos = new HashSet();
        ignoreUtxos.add(collateralUtxo);

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoService);
        List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(sender.baseAddress(), LOVELACE, amount, ignoreUtxos);

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(scriptAddress)
                        .amount(amount)
                        .unit("lovelace")
                        .datumHash(datumHash)
                        .utxosToInclude(utxos)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), null);
        paymentTransaction.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build());
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        if (result.isSuccessful()) {
            Result<String> resultWithTxId = Result.success(result.getResponse()).code(result.code())
                    .withValue(result.getValue().getTransactionId());

            waitForTransaction(resultWithTxId);
        } else {
            System.out.println(result);
        }

        return result.isSuccessful();
    }

    private long getTtl() throws ApiException {
        Block block = blockService.getLastestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    private void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be added
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be included in a block ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
