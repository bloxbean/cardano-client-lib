package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionBuilder;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.transaction.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.coinselection.impl.LargestFirstUtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.*;
import com.bloxbean.cardano.client.function.helper.ChangeOutputAdjustments;
import com.bloxbean.cardano.client.function.helper.FeeCalculators;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.util.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.AuxDataProviders.metadataProvider;
import static com.bloxbean.cardano.client.function.helper.ChangeOutputAdjustments.adjustChangeOutput;
import static com.bloxbean.cardano.client.function.helper.FeeCalculators.feeCalculator;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.MintCreators.mintCreator;
import static com.bloxbean.cardano.client.function.helper.OutputBuilders.createFromMintOutput;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static org.junit.jupiter.api.Assertions.assertTrue;

//Additional tests for TransactionHelperService
public class BatchTransactionsIT extends BaseITTest {

    BackendService backendService;
    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    EpochService epochService;

    UtxoSupplier utxoSupplier;
    ProtocolParamsSupplier protocolParamsSupplier;

    Account sender;
    String senderAddress;

    String receiverMnemonic;

    @BeforeEach
    public void setup() {
        backendService = getBackendService();
        utxoService = backendService.getUtxoService();
        epochService = backendService.getEpochService();
        blockService = backendService.getBlockService();
        transactionService = backendService.getTransactionService();

        utxoSupplier = new DefaultUtxoSupplier(utxoService);
        protocolParamsSupplier = new DefaultProtocolParamsSupplier(epochService);

        //Create TransactionHelperService with LargestFirst
        TransactionBuilder transactionBuilder = new TransactionBuilder(new LargestFirstUtxoSelectionStrategy(utxoSupplier), protocolParamsSupplier);
        transactionHelperService = new TransactionHelperService(transactionBuilder, new DefaultTransactionProcessor(transactionService));
        feeCalculationService = backendService.getFeeCalculationService(transactionHelperService);

        String senderMnemonic = "capable venture stove poet great turtle hurdle photo improve tongue light bean orchard negative clog forest page coil never report hammer grid waste cigar";
        sender = new Account(Networks.testnet(), senderMnemonic);
        senderAddress = sender.baseAddress();

        receiverMnemonic = "tired cannon pig ski jar plastic shiver moon ordinary want token dutch excuse hat club laugh differ spice random mean endless creek despair country";
    }

    @Test
    public void transferMultiAssetMultiPayments_whenSingleSender_multiReceivers_highLevelApi() throws Exception {
        //Mint 10 tokens
        List<String> units = mintMultipleTokens(sender, 10);

        ArrayList<PaymentTransaction> paymentTransactionArrayList = new ArrayList<>();

        String receiver1 = sender.baseAddress();

        PaymentTransaction paymentTransaction1 =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver1)
                        .unit(LOVELACE)
                        .amount(BigInteger.valueOf(1000000))
                        .fee(BigInteger.valueOf(4000)) //some low fee (invalid). Just for testing
                        .build();
        paymentTransactionArrayList.add(paymentTransaction1);

        for (int i = 0; i < units.size(); i++) {
            Account receiverAcc = new Account(Networks.testnet(), receiverMnemonic, i);
            paymentTransactionArrayList.add(PaymentTransaction.builder()
                    .sender(sender)
                    .receiver(receiverAcc.baseAddress())
                    .unit(units.get(i))
                    .amount(BigInteger.valueOf(1))
                    .build());
        }

        //Calculate total fee for all payment transactions and set in one of the payment transaction
        BigInteger fee = feeCalculationService.calculateFee(paymentTransactionArrayList,
                TransactionDetailsParams
                        .builder()
                        .ttl(getTtl())
                        .build(), null);
        paymentTransaction1.setFee(fee);

        Result<TransactionResult> result = transactionHelperService.transfer(paymentTransactionArrayList,
                TransactionDetailsParams.builder().ttl(getTtl()).build());

        System.out.println(result);

        assertTrue(result.isSuccessful());
        waitForTransaction(Result.success(result.toString()).withValue(result.getValue().getTransactionId()));
    }

    @Test
    public void transferMultiAssetMultiPayments_whenSingleSender_multiReceivers_functions() throws Exception {
        //Mint 10 tokens
        List<String> units = mintMultipleTokens(sender, 10);

        TxOutputBuilder txOutputBuilder = (txBuilderContext, list) -> {
        };

        for (int i = 0; i < units.size(); i++) {
            Account account = new Account(Networks.testnet(), receiverMnemonic, i);
            Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(units.get(i));
            Output output = Output.builder()
                    .address(account.baseAddress())
                    .qty(BigInteger.ONE)
                    .policyId(policyAssetName._1)
                    .assetName(policyAssetName._2).build();

            txOutputBuilder = txOutputBuilder.and(output.outputBuilder());
        }

        TxBuilder builder = txOutputBuilder
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(FeeCalculators.feeCalculator(senderAddress, 1))
                .andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress, 1));

        TxSigner signer = signerFrom(sender);
        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                .buildAndSign(builder, signer);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    private List<String> mintMultipleTokens(Account sender, int count) throws Exception {
        String senderAddress = sender.baseAddress();
        String receiver = sender.baseAddress();

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy.getPolicyId());

        NFTMetadata nftMetadata = NFTMetadata.create().version(1);

        for (int i = 0; i < 10; i++) {
            Asset asset = new Asset("TestNFT-" + i, BigInteger.valueOf(1));
            multiAsset.getAssets().add(asset);

            NFT nft = NFT.create()
                    .assetName(asset.getName())
                    .name(asset.getName())
                    .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                    .mediaType("image/png")
                    .addFile(NFTFile.create()
                            .name("file-1")
                            .mediaType("image/png")
                            .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                    .description("This is a test NFT - " + i);

            nftMetadata.addNFT(policy.getPolicyId(), nft);
        }

        List multiAssets = new ArrayList();
        multiAssets.add(multiAsset);
        TransactionOutput mintOutput = TransactionOutput.builder()
                .address(receiver)
                .value(Value.builder().coin(adaToLovelace(40))
                        .multiAssets(multiAssets).build()).build();

        //Create TxBuilder function
        TxBuilder txBuilder =
                createFromMintOutput(mintOutput)
                        .buildInputs(createFromSender(senderAddress, senderAddress))
                        .andThen(mintCreator(policy.getPolicyScript(), multiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(feeCalculator(senderAddress, 2))
                        .andThen(adjustChangeOutput(senderAddress, 2)); //any adjustment in change output

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        List<String> units = new ArrayList<>();
        multiAsset.getAssets().forEach(asset -> {
            String unit = AssetUtil.getUnit(multiAsset.getPolicyId(), asset);
            units.add(unit);
        });

        return units;
    }

    public long getTtl() throws ApiException {
        Block block = blockService.getLatestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    public void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue());
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

}
