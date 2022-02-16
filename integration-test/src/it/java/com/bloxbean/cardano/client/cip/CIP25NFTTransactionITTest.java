package com.bloxbean.cardano.client.cip;

import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.PolicyUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CIP25NFTTransactionITTest extends CIPBaseTransactionITTest {

    @Test
    public void mintToken() throws CborSerializationException, ApiException, AddressExcepion {
        long currentSlot = queryTipSlot();
        assertNotEquals(0, currentSlot);
        Policy policy = PolicyUtil.createEpochBasedTimeLockedPolicy("CIP25PolicyTimeLockedPolicy", currentSlot, 5L);
        String assetName = "NFTTest-" + new Random().nextInt();
        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(policy.getPolicyId());
        Asset asset = new Asset(HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8), true), BigInteger.valueOf(1));
        multiAsset.getAssets().add(asset);

        Map<String, String> datePropsMap = new HashMap<>();
        datePropsMap.put("year", "2021");
        datePropsMap.put("month", "Dec");

        NFT nft = NFT.create()
                .assetName(assetName)
                .name(assetName)
                .image("ipfs://Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4")
                .mediaType("image/png")
                .addFile(NFTFile.create()
                        .name("file-1")
                        .mediaType("image/png")
                        .src("ipfs/Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4"))
                .description("This is a test NFT")
                .description("This is a test NFT-2")
                .property("Artist", "Simply NFT")
                .property("Brand", "My Brand")
                .property("Series", "2")
                .property("Attributes", Arrays.asList("Accessory", "Chain", "Clothing"))
                .property("date", datePropsMap);

        //Add some extra attributes
        nft.putNegative("longitude", new BigInteger("-1223787"));

        NFTMetadata nftMetadata = NFTMetadata.create().addNFT(policy.getPolicyId(), nft);

        System.out.println("Metadata: " + nftMetadata.toJson());

        MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();

        BigInteger fee = feeCalculationService.calculateFee(mintTransaction, TransactionDetailsParams.builder().ttl(getTtl()).build(), nftMetadata);
        mintTransaction.setFee(fee);
        System.out.println(fee);

        Result<TransactionResult> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build(), nftMetadata);

        System.out.println("Request: \n" + JsonUtil.getPrettyJson(mintTransaction));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        waitForTransaction(result);

        assertThat(result.isSuccessful(), is(true));
        if (result.isSuccessful()) {
            System.out.println("Transaction Id: " + result.getValue().getTransactionId());
            System.out.println("Policy Id: " + policy.getPolicyId());
            System.out.println("Asset Name: " + assetName);
        }
    }

    private long queryTipSlot() throws ApiException {
        Result<Block> blockResult = blockService.getLastestBlock();
        if (blockResult.isSuccessful()) {
            Block block = blockResult.getValue();
            return block.getSlot();
        } else {
            return 0;
        }
    }
}
