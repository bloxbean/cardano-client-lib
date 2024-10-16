package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.*;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static com.bloxbean.cardano.client.function.helper.AuxDataProviders.metadataProvider;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.MintCreators.mintCreator;
import static com.bloxbean.cardano.client.function.helper.OutputBuilders.createFromMintOutput;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TxBuilderContextTest extends OgmiosBaseTest {
    UtxoSupplier utxoSupplier;
    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws ApiException {
        utxoSupplier = new DefaultUtxoSupplier(kupoUtxoService);
        protocolParams = ogmiosBackendService.getEpochService().getProtocolParameters().getValue();
    }

    @Test
    void testTransactionBuilding() throws CborSerializationException, ApiException {
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
                                .buildInputs(InputBuilders.createFromSender(secondSender.baseAddress(), secondSender.baseAddress()))
                )
                .andThen(MintCreators.mintCreator(nftPolicy.getPolicyScript(), nftMultiAsset))
                .andThen(MintCreators.mintCreator(policy.getPolicyScript(), multiAsset))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(AuxDataProviders.metadataProvider(nftMetadata))
                .andThen(balanceTx(changeAddress, 2 + policy.getPolicyKeys().size() + policy.getPolicyKeys().size()));

        TxSigner signer = signerFrom(senderAccount, secondSender)
                .andThen(signerFrom(policy, nftPolicy));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(builder, signer);

        System.out.println(signedTxn);

        Result<String> result = ogmiosBackendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);

        assertThat(result.isSuccessful());
        waitForTransaction(result, senderAddress);
    }

    @Test
    void testMintingTwoNFTs_withSamePolicy() throws Exception {
        String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String senderAddress = sender.baseAddress();

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
                        .buildInputs(createFromSender(senderAddress, senderAddress))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));

        Result<String> result = ogmiosBackendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful());
        waitForTransaction(result, senderAddress);
    }

    @Test
    void testMintingTwoNFTs_withSamePolicyAndAssetName_differentReceivers() throws Exception {
        String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String senderAddress = sender.baseAddress();

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
                        .buildInputs(createFromSender(senderAddress, senderAddress))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));

        Result<String> result = ogmiosBackendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful());
        waitForTransaction(result, senderAddress);
    }

    @Test
    void testMintingTwoNFTs_withSamePolicyAndAssetName_differentReceivers_withOutputClass() throws Exception {
        String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String senderAddress = sender.baseAddress();

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
                        .buildInputs(createFromSender(senderAddress, senderAddress))
                        .andThen(mintCreator(policy.getPolicyScript(), mergeMultiAsset))
                        .andThen(metadataProvider(nftMetadata))
                        .andThen(balanceTx(senderAddress, 2));

        //Build and sign transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));

        Result<String> result = ogmiosBackendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful());
        waitForTransaction(result, senderAddress);
    }

    @Test
    public void mintToken() throws CborSerializationException, ApiException, AddressExcepion {
        String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String senderAddress = sender.baseAddress();
        System.out.println("sender: " + senderAddress);

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
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(MintCreators.mintCreator(policy.getPolicyScript(), multiAsset))
                .andThen(AuxDataProviders.metadataProvider(metadata))
                .andThen(BalanceTxBuilders.balanceTx(senderAddress, 2));

        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));

        Result<String> result = ogmiosBackendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);
        assertTrue(result.isSuccessful());

        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        assertThat(result.isSuccessful());
        waitForTransaction(result, senderAddress);
    }

    private void waitForTransaction(Result<String> result, String address) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    int size = utxoSupplier.getAll(address)
                            .stream()
                            .filter(utxo -> utxo.getTxHash().equals(result.getValue()))
                            .collect(Collectors.toList()).size();
                    if (size > 0) {
                        System.out.printf("Txn id : " + result.getValue());
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
