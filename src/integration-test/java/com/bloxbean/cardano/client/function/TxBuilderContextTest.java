package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.PolicyUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;

public class TxBuilderContextTest {
    BackendService backendService =
            BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, "SmzYuTWziWPs0fTRON2Qrz9PnGkkhJ2y");

    @Test
    void testTransactionBuilding() throws CborSerializationException, ApiException {
        String senderMnemonic = "stone decade great marine meadow merge boss ahead again rapid detect cover vital estate web silly copper estate wisdom empty speed salute oak car";

        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);

        Output output1 = Output.builder()
                .address("addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c")
                .assetName(LOVELACE)
                .qty(BigInteger.valueOf(12000))
                .build();

        Output output2 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .assetName(LOVELACE)
                .qty(ONE_ADA.multiply(BigInteger.valueOf(20)))
                .build();

        Output output3 = Output.builder()
                .address("addr_test1qq46hhhpppek6e33hqakqyu2z5xeqwlc4pc0xynwamn34l6vps306wwr475xeh2lnt4hqjm4mfyjqnvla9j5wtc3fxespv67ka")
                .policyId("8bb9f400ee6ec7c81c5afa2c656945c1ab06785b9751993653441e32")
                .assetName("TestAss1")
                .qty(BigInteger.valueOf(8))
                .build();

        Value value2 = Value.builder()
                .multiAssets(Arrays.asList(
                        MultiAsset.builder()
                                .policyId("8bb9f400ee6ec7c81c5afa2c656945c1ab06785b9751993653441e32")
                                .assets(Arrays.asList(new Asset("TestAss1", BigInteger.valueOf(5))))
                                .build()

//                        MultiAsset.builder()
//                                .policyId("d11b0562dcac7042636c9dbb44897b38675da0d613d30f98a541a290")
//                                .assets(Arrays.asList(new Asset("TestCoin", BigInteger.valueOf(1))))
//                                .build()
                )).build();
        TxOutputBuilder multiAssetOutputBuilder =
                OutputBuilders.createFromOutput(new TransactionOutput(
                        "addr_test1qz7g6c8w6lzhr5weyus79rl4alepskc6u2pfuzkr7s5qad30ry2sf3u3vq0hzkyhht4uwqy8p40xfy5qkgc79xswq5msnaucg2",
                        value2));

        String senderAddress = senderAccount.baseAddress();
        String changeAddress = senderAccount.baseAddress();

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("mypolicy", 1);
        MultiAsset multiAsset = MultiAsset.builder()
                .policyId(policy.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("xyz_func")
                                .value(BigInteger.valueOf(100))
                                .build()
                )).build();

        Policy nftPolicy = PolicyUtil.createMultiSigScriptAllPolicy("nftPolicy", 1);
        MultiAsset nftMultiAsset = MultiAsset.builder()
                .policyId(nftPolicy.getPolicyId())
                .assets(List.of(
                        Asset.builder()
                                .name("nft-1")
                                .value(BigInteger.valueOf(1))
                                .build()
                )).build();

        Value mintValue = Value.builder()
                .coin(BigInteger.ZERO)
                .multiAssets(List.of(multiAsset, nftMultiAsset))
                .build();

        String mintReceiver = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
        TxOutputBuilder mintAssetOuputBuilder =
                OutputBuilders.createFromMintOutput(new TransactionOutput(mintReceiver, mintValue));

        NFT nft = NFT.create()
                .name("nft-1")
                .description("Test nft 1")
                .image("http://imageurl")
                .assetName("nft-1");

        NFTMetadata nftMetadata = NFTMetadata.create()
                .addNFT(nftPolicy.getPolicyId(), nft)
                .version("1.0");

        MessageMetadata metadata = MessageMetadata.create()
                .add("This is test message !! ---");

        Metadata finalMetadata = metadata.merge(nftMetadata);

        //addr_test1qpg4faaydel7n6cq8e4p5kscg6zahmrhlgeke8c6hn6utespky66rz9quy288xqfwc4k2z3v5h4g7gqxpkr8hn9rngvq00hz02
        String secondSenderMnemonic = "reflect robust shy pond spirit hour suffer can million truck final arrow turn lecture worth quarter choose tourist weird lady flee before congress group";
        Account secondSender = new Account(Networks.testnet(), secondSenderMnemonic);


        TxBuilder builder = output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .and(multiAssetOutputBuilder)
                .buildInputs(InputBuilders.defaultUtxoSelector(senderAddress, changeAddress))
                .andThen(
                        mintAssetOuputBuilder
                                .buildInputs(InputBuilders.defaultUtxoSelector(secondSender.baseAddress(), changeAddress))
                )
                .andThen(MintCreators.mintCreator(nftPolicy, nftMultiAsset))
                .andThen(MintCreators.mintCreator(policy, multiAsset))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(AuxDataProviders.metadataProvider(nftMetadata))
                .andThen(FeeCalculators.feeCalculator(changeAddress, 2 + policy.getPolicyKeys().size() + policy.getPolicyKeys().size()));


        Transaction transaction = new Transaction();
        transaction.setBody(TransactionBody.builder()
                .outputs(new ArrayList<>())
                .inputs(new ArrayList<>())
                .build()
        );

        TxBuilderContext txBuilderContext
                = new TxBuilderContext(backendService);

        builder.accept(txBuilderContext, transaction);

        Transaction signedTxn = senderAccount.sign(transaction);
        signedTxn = secondSender.sign(signedTxn);
        for (SecretKey sk : policy.getPolicyKeys()) {
            signedTxn = TransactionSigner.INSTANCE.sign(signedTxn, sk);
        }

        for (SecretKey sk : nftPolicy.getPolicyKeys()) {
            signedTxn = TransactionSigner.INSTANCE.sign(signedTxn, sk);
        }

        System.out.println(signedTxn);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        waitForTransaction(result);
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

}
