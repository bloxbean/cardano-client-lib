package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BaseITTest;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static com.bloxbean.cardano.client.function.helper.AuxDataProviders.metadataProvider;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromUtxos;
import static com.bloxbean.cardano.client.function.helper.MintCreators.mintCreator;
import static com.bloxbean.cardano.client.function.helper.OutputBuilders.createFromMintOutput;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TxBuilderContextIT extends BaseITTest {
    BackendService backendService;
    UtxoSupplier utxoSupplier;
    ProtocolParams protocolParams;

    static Account senderAccount1;
    static String senderAddress1;

    static Account senderAccount2;
    static String senderAddress2;

    @BeforeAll
    public static void setupAll() {
        //addr_test1qrynkm9vzsl7vrufzn6y4zvl2v55x0xwc02nwg00x59qlkxtsu6q93e6mrernam0k4vmkn3melezkvgtq84d608zqhnsn48axp
        String senderMnemonic = "stone decade great marine meadow merge boss ahead again rapid detect cover vital estate web silly copper estate wisdom empty speed salute oak car";
        senderAccount1 = new Account(Networks.testnet(), senderMnemonic);
        senderAddress1 = senderAccount1.baseAddress();

        String senderMnemonic2 = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
        senderAccount2 = new Account(Networks.testnet(), senderMnemonic2);
        senderAddress2 = senderAccount2.baseAddress();

        if (backendType.equals(DEVKIT)) {
            topUpFund(senderAddress1, 50000);
            topUpFund(senderAddress2, 50000);
        }
    }

    @BeforeEach
    public void setup() throws ApiException {
        backendService = getBackendService();
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        protocolParams = getBackendService().getEpochService().getProtocolParameters().getValue();
    }

    @Test
    void testTransactionBuilding() throws CborSerializationException, ApiException {
        if (backendType == DEVKIT) return; //skip
        //addr_test1qrynkm9vzsl7vrufzn6y4zvl2v55x0xwc02nwg00x59qlkxtsu6q93e6mrernam0k4vmkn3melezkvgtq84d608zqhnsn48axp
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
                .policyId("c48f707fea6f08af67a8c06c9bea5b3ec847f5901dc08420cd7f8ade")
                .assetName("OgmiosIT")
                .qty(BigInteger.valueOf(8))
                .build();

        Value value2 = Value.builder()
                .multiAssets(Arrays.asList(
                        MultiAsset.builder()
                                .policyId("c48f707fea6f08af67a8c06c9bea5b3ec847f5901dc08420cd7f8ade")
                                .assets(Arrays.asList(new Asset("OgmiosIT", BigInteger.valueOf(5))))
                                .build()
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
                .version(1);

        MessageMetadata metadata = MessageMetadata.create()
                .add("This is test message !! ---");

        //addr_test1qpg4faaydel7n6cq8e4p5kscg6zahmrhlgeke8c6hn6utespky66rz9quy288xqfwc4k2z3v5h4g7gqxpkr8hn9rngvq00hz02
        String secondSenderMnemonic = "reflect robust shy pond spirit hour suffer can million truck final arrow turn lecture worth quarter choose tourist weird lady flee before congress group";
        Account secondSender = new Account(Networks.testnet(), secondSenderMnemonic);


        TxBuilder builder = output1.outputBuilder()
                .and(output2.outputBuilder())
                .and(output3.outputBuilder())
                .and(multiAssetOutputBuilder)
                .buildInputs(InputBuilders.createFromSender(senderAddress, changeAddress))
                .andThen(
                        mintAssetOuputBuilder
                                .buildInputs(InputBuilders.createFromSender(secondSender.baseAddress(), changeAddress))
                )
                .andThen(MintCreators.mintCreator(nftPolicy.getPolicyScript(), nftMultiAsset))
                .andThen(MintCreators.mintCreator(policy.getPolicyScript(), multiAsset))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(AuxDataProviders.metadataProvider(nftMetadata))
                .andThen(FeeCalculators.feeCalculator(changeAddress, 2 + policy.getPolicyKeys().size() + policy.getPolicyKeys().size())
                .andThen(ChangeOutputAdjustments.adjustChangeOutput(changeAddress,
                        2 + policy.getPolicyKeys().size() + policy.getPolicyKeys().size())) //Incase changeout goes below min ada after fee deduction
                );

        TxSigner signer = signerFrom(senderAccount, secondSender)
                .andThen(signerFrom(policy, nftPolicy));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(builder, signer);

        System.out.println(signedTxn);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        waitForTransaction(result);
    }

    @Test
    void testMintingTwoNFTs_withSamePolicy() throws Exception {

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qq9f6hzuwmqpe3p9h90z2rtgs0v0lsq9ln5f79fjyec7eclg7v88q9als70uzkdh5k6hw20uuwqfz477znfp5v4rga2s3ysgxu";
        String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset1.getAssets().add(asset);

        NFT nft1 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        //NFT-2
        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId(policy.getPolicyId());
        Asset asset2 = new Asset("TestNFT2", BigInteger.valueOf(1));
        multiAsset2.getAssets().add(asset2);

        NFT nft2 = NFT.create()
                .assetName(asset2.getName())
                .name(asset2.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        NFTMetadata nftMetadata = NFTMetadata.create()
                .version(1)
                .addNFT(policy.getPolicyId(), nft1)
                .addNFT(policy.getPolicyId(), nft2);

        //Define outputs
        //Output using TransactionOutput
        TransactionOutput mintOutput1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(adaToLovelace(2.3))
                        .multiAssets(List.of(multiAsset1)).build()).build();

        TransactionOutput mintOutput2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(adaToLovelace(1.8))
                        .multiAssets(List.of(multiAsset2)).build()).build();

        //Output using new Output class
        Output output3 = Output.builder()
                .address(receiver3)
                .assetName(LOVELACE)
                .qty(adaToLovelace(4)).build();

        MultiAsset mergeMultiAsset = multiAsset1.add(multiAsset2);

        //Create TxBuilder function
        TxBuilder txBuilder =
                createFromMintOutput(mintOutput1)
                        .and(createFromMintOutput(mintOutput2))
                        .and(createFromMintOutput(output3))
                        .buildInputs(createFromSender(senderAddress1, senderAddress1))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress1, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(senderAccount1).andThen(signerFrom(policy)));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);
    }

    @Test
    void testMintingTwoNFTs_withSamePolicyAndAssetName_differentReceivers() throws Exception {

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qq9f6hzuwmqpe3p9h90z2rtgs0v0lsq9ln5f79fjyec7eclg7v88q9als70uzkdh5k6hw20uuwqfz477znfp5v4rga2s3ysgxu";
        String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset1.getAssets().add(asset);

        NFT nft1 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        //NFT-2
        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId(policy.getPolicyId());
        Asset asset2 = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset2.getAssets().add(asset2);

        NFT nft2 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        NFTMetadata nftMetadata = NFTMetadata.create()
                .version(1)
                .addNFT(policy.getPolicyId(), nft1)
                .addNFT(policy.getPolicyId(), nft2);

        //Define outputs
        //Output using TransactionOutput
        TransactionOutput mintOutput1 = TransactionOutput.builder()
                .address(receiver1)
                .value(Value.builder().coin(adaToLovelace(2.3))
                        .multiAssets(List.of(multiAsset1)).build()).build();

        TransactionOutput mintOutput2 = TransactionOutput.builder()
                .address(receiver2)
                .value(Value.builder().coin(adaToLovelace(1.8))
                        .multiAssets(List.of(multiAsset2)).build()).build();

        //Output using new Output class
        Output output3 = Output.builder()
                .address(receiver3)
                .assetName(LOVELACE)
                .qty(adaToLovelace(4)).build();

        MultiAsset mergeMultiAsset = multiAsset1.add(multiAsset2);

        //Create TxBuilder function
        TxBuilder txBuilder =
                createFromMintOutput(mintOutput1)
                        .and(createFromMintOutput(mintOutput2))
                        .and(createFromMintOutput(output3))
                        .buildInputs(createFromSender(senderAddress1, senderAddress1))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress1, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(senderAccount1).andThen(signerFrom(policy)));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);
    }

    @Test
    void testMintingTwoNFTs_withSamePolicyAndAssetName_differentReceivers_withOutputClass() throws Exception {

        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qq9f6hzuwmqpe3p9h90z2rtgs0v0lsq9ln5f79fjyec7eclg7v88q9als70uzkdh5k6hw20uuwqfz477znfp5v4rga2s3ysgxu";
        String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset1.getAssets().add(asset);

        NFT nft1 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        //NFT-2
        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId(policy.getPolicyId());
        Asset asset2 = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset2.getAssets().add(asset2);

        NFT nft2 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        NFTMetadata nftMetadata = NFTMetadata.create()
                .version(1)
                .addNFT(policy.getPolicyId(), nft1)
                .addNFT(policy.getPolicyId(), nft2);

        //Define outputs
        //Output using Output class

        Output output1 = Output.builder()
                .address(receiver1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(2.3))
                .build();
        Output output11 = Output.builder()
                .address(receiver1)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(1))
                .build();

        Output output2 = Output.builder()
                .address(receiver2)
                .assetName(LOVELACE)
                .qty(adaToLovelace(1.8))
                .build();
        Output output21 = Output.builder()
                .address(receiver2)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(1))
                .build();

        //Output using new Output class
        Output output3 = Output.builder()
                .address(receiver3)
                .assetName(LOVELACE)
                .qty(adaToLovelace(4)).build();

        MultiAsset mergeMultiAsset = multiAsset1.add(multiAsset2);

        //Create TxBuilder function
        TxBuilder txBuilder =
                output1.outputBuilder()
                        .and(output11.mintOutputBuilder())
                        .and(output2.outputBuilder())
                        .and(output21.mintOutputBuilder())
                        .and(createFromMintOutput(output3)) //An alternate way
                        .buildInputs(createFromSender(senderAddress1, senderAddress1))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress1, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(senderAccount1).andThen(signerFrom(policy)));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);
    }

    @Test
    public void mintToken() throws CborSerializationException, ApiException, AddressExcepion {

        String receiverAddress = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestCoin", BigInteger.valueOf(50000));
        multiAsset.getAssets().add(asset);

        //Metadata
        CBORMetadataMap tokenInfoMap
                = new CBORMetadataMap()
                .put("token", "Test Token")
                .put("symbol", "TTOK");

        CBORMetadataList tagList
                = new CBORMetadataList()
                .add("tag1")
                .add("tag2");

        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("670001"), tokenInfoMap)
                .put(new BigInteger("670002"), tagList);

        Output output = Output.builder()
                .address(receiverAddress)
                .policyId(policy.getPolicyId())
                .assetName(asset.getName())
                .qty(BigInteger.valueOf(50000))
                .build();

        TxBuilder txBuilder = output.mintOutputBuilder()
                .buildInputs(InputBuilders.createFromSender(senderAddress1, senderAddress1))
                .andThen(MintCreators.mintCreator(policy.getPolicyScript(), multiAsset))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(BalanceTxBuilders.balanceTx(senderAddress1, 2));

        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(senderAccount1).andThen(signerFrom(policy)));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);
    }

    @Test
    void testPayments_whenMergeOutputTrue() throws Exception {
        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qq9f6hzuwmqpe3p9h90z2rtgs0v0lsq9ln5f79fjyec7eclg7v88q9als70uzkdh5k6hw20uuwqfz477znfp5v4rga2s3ysgxu";
        String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset1.getAssets().add(asset);

        NFT nft1 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        //NFT-2
        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId(policy.getPolicyId());
        Asset asset2 = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset2.getAssets().add(asset2);

        NFT nft2 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        NFTMetadata nftMetadata = NFTMetadata.create()
                .version(1)
                .addNFT(policy.getPolicyId(), nft1)
                .addNFT(policy.getPolicyId(), nft2);

        //Define outputs
        //Output using Output class

        Output output1 = Output.builder()
                .address(receiver1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(2.3))
                .build();
        Output output11 = Output.builder()
                .address(receiver1)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(1))
                .build();

        Output output2 = Output.builder()
                .address(receiver2)
                .assetName(LOVELACE)
                .qty(adaToLovelace(1.8))
                .build();
        Output output21 = Output.builder()
                .address(receiver2)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(1))
                .build();

        //Output using new Output class
        Output output3 = Output.builder()
                .address(receiver3)
                .assetName(LOVELACE)
                .qty(adaToLovelace(4)).build();

        MultiAsset mergeMultiAsset = multiAsset1.add(multiAsset2);

        //Create TxBuilder function
        TxBuilder txBuilder =
                output1.outputBuilder()
                        .and(output11.mintOutputBuilder())
                        .and(output2.outputBuilder())
                        .and(output21.mintOutputBuilder())
                        .and(createFromMintOutput(output3)) //An alternate way
                        .buildInputs(createFromSender(senderAddress1, senderAddress1))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress1, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(senderAccount1).andThen(signerFrom(policy)));

        assertThat(signedTransaction.getBody().getOutputs()).hasSize(4); //Total 4 outputs including change output
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(2.3));
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getMultiAssets()).hasSize(1);
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(adaToLovelace(1.8));
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getMultiAssets()).hasSize(1);
        assertThat(signedTransaction.getBody().getOutputs().get(2).getValue().getCoin()).isEqualTo(adaToLovelace(4));
        assertThat(signedTransaction.getBody().getOutputs().get(2).getValue().getMultiAssets()).hasSize(0);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);
    }

    @Test
    void testPayments_whenMergeOutputFalse() throws Exception {
        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String receiver2 = "addr_test1qq9f6hzuwmqpe3p9h90z2rtgs0v0lsq9ln5f79fjyec7eclg7v88q9als70uzkdh5k6hw20uuwqfz477znfp5v4rga2s3ysgxu";
        String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset1.getAssets().add(asset);

        NFT nft1 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        //NFT-2
        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId(policy.getPolicyId());
        Asset asset2 = new Asset("TestNFT", BigInteger.valueOf(1));
        multiAsset2.getAssets().add(asset2);

        NFT nft2 = NFT.create()
                .assetName(asset.getName())
                .name(asset.getName())
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT");

        NFTMetadata nftMetadata = NFTMetadata.create()
                .version(1)
                .addNFT(policy.getPolicyId(), nft1)
                .addNFT(policy.getPolicyId(), nft2);

        //Define outputs
        //Output using Output class

        Output output1 = Output.builder()
                .address(receiver1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(2.3))
                .build();
        Output output11 = Output.builder()
                .address(receiver1)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(1))
                .build();

        Output output2 = Output.builder()
                .address(receiver2)
                .assetName(LOVELACE)
                .qty(adaToLovelace(1.8))
                .build();
        Output output21 = Output.builder()
                .address(receiver2)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(1))
                .build();

        //Output using new Output class
        Output output3 = Output.builder()
                .address(receiver3)
                .assetName(LOVELACE)
                .qty(adaToLovelace(4)).build();

        MultiAsset mergeMultiAsset = multiAsset1.add(multiAsset2);

        //Create TxBuilder function
        TxBuilder txBuilder =
                output1.outputBuilder()
                        .and(output11.mintOutputBuilder())
                        .and(output2.outputBuilder())
                        .and(output21.mintOutputBuilder())
                        .and(createFromMintOutput(output3)) //An alternate way
                        .buildInputs(createFromSender(senderAddress2, senderAddress2))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress2, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .mergeOutputs(false)
                .buildAndSign(txBuilder, signerFrom(senderAccount2).andThen(signerFrom(policy)));

        assertThat(signedTransaction.getBody().getOutputs()).hasSize(6); //Total 4 outputs including change output
        assertThat(signedTransaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(2.3));
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getMultiAssets()).isEmpty();

        assertThat(signedTransaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(receiver1);
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getCoin()).isLessThan(adaToLovelace(2)); //min ada
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getMultiAssets()).hasSize(1);

        assertThat(signedTransaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(receiver2);
        assertThat(signedTransaction.getBody().getOutputs().get(2).getValue().getCoin()).isEqualTo(adaToLovelace(1.8));
        assertThat(signedTransaction.getBody().getOutputs().get(2).getValue().getMultiAssets()).hasSize(0);

        assertThat(signedTransaction.getBody().getOutputs().get(3).getAddress()).isEqualTo(receiver2);
        assertThat(signedTransaction.getBody().getOutputs().get(3).getValue().getCoin()).isLessThan(adaToLovelace(2)); //min ada
        assertThat(signedTransaction.getBody().getOutputs().get(3).getValue().getMultiAssets()).hasSize(1);

        assertThat(signedTransaction.getBody().getOutputs().get(4).getAddress()).isEqualTo(receiver3);
        assertThat(signedTransaction.getBody().getOutputs().get(4).getValue().getCoin()).isEqualTo(adaToLovelace(4));
        assertThat(signedTransaction.getBody().getOutputs().get(4).getValue().getMultiAssets()).isEmpty();

        assertThat(signedTransaction.getBody().getOutputs().get(5).getAddress()).isEqualTo(senderAddress2);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);
    }

    @Test
    void testPayment_withMinUtxoValue_mergeOutputsIsTrue() throws Exception {
        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(100));
        multiAsset1.getAssets().add(asset);

        //Define outputs
        //Output using Output class
        Output mintOutput = Output.builder()
                .address(senderAddress2)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(100))
                .build();

        Output feeOutput = Output.builder()
                .address(receiver1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(1))
                .build();

        //Create TxBuilder function
        TxBuilder txBuilder =
                mintOutput.mintOutputBuilder()
                        .and(feeOutput.mintOutputBuilder())
                        .buildInputs(createFromSender(senderAddress2, senderAddress2))
                        .andThen(mintCreator(policy.getPolicyScript(), multiAsset1))
                        .andThen(balanceTx(senderAddress2, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(senderAccount2).andThen(signerFrom(policy)));

        //asserts
        assertThat(signedTransaction.getBody().getOutputs()).hasSize(2);
        assertThat(signedTransaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(receiver1);
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(adaToLovelace(1));
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getMultiAssets()).isEmpty();

        assertThat(signedTransaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(senderAddress2);
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getMultiAssets().size()).isGreaterThanOrEqualTo(1);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void testPayment_withCreateFromUtxos_withMinUtxoValue_mergeOutputsIsFalse() throws Exception {
        String receiver1 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);

        //Multi asset and NFT metadata
        //NFT-1
        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset("TestNFT", BigInteger.valueOf(100));
        multiAsset1.getAssets().add(asset);

        //Define outputs
        //Output using Output class
        Output mintOutput = Output.builder()
                .address(senderAddress1)
                .policyId(policy.getPolicyId())
                .assetName("TestNFT")
                .qty(BigInteger.valueOf(100))
                .build();

        Output feeOutput = Output.builder()
                .address(receiver1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(1))
                .build();

        List<Utxo> utxos = backendService.getUtxoService().getUtxos(senderAddress1, 10, 0).getValue();
        if (utxos == null)
            utxos = Collections.emptyList();

        //Create TxBuilder function
        TxBuilder txBuilder =
                mintOutput.mintOutputBuilder()
                        .and(feeOutput.mintOutputBuilder())
                        .buildInputs(createFromUtxos(utxos, senderAddress1))
                        .andThen(mintCreator(policy.getPolicyScript(), multiAsset1))
                        .andThen(balanceTx(senderAddress1, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .mergeOutputs(false)
                .buildAndSign(txBuilder, signerFrom(senderAccount1).andThen(signerFrom(policy)));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        //asserts
        assertThat(signedTransaction.getBody().getOutputs()).hasSize(3);
        assertThat(signedTransaction.getBody().getOutputs().get(0).getAddress()).isEqualTo(senderAddress1);
        assertThat(signedTransaction.getBody().getOutputs().get(0).getValue().getMultiAssets()).hasSize(1);

        assertThat(signedTransaction.getBody().getOutputs().get(1).getAddress()).isEqualTo(receiver1);
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(adaToLovelace(1));
        assertThat(signedTransaction.getBody().getOutputs().get(1).getValue().getMultiAssets()).isEmpty();

        assertThat(signedTransaction.getBody().getOutputs().get(2).getAddress()).isEqualTo(senderAddress1);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertTrue(result.isSuccessful());
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
