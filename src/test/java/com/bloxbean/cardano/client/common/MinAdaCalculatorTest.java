package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MinAdaCalculatorTest {

    private final BigInteger MIN_UTXO_VALUE = BigInteger.valueOf(1000000);
    private MinAdaCalculator minAdaCalculator;

    @BeforeEach
    public void setup() {
        minAdaCalculator = new MinAdaCalculator(MIN_UTXO_VALUE);
    }

    @Test
    public void testCalculateMinAdaWithAdaOnlyUtxo() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        output.setValue(new Value(new BigInteger(String.valueOf(20000)), null));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(MIN_UTXO_VALUE));
    }

    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAndNoAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset("", BigInteger.valueOf(4000))));
        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1407406)));
    }

    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAndSingleCharacterAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset(HexUtil.encodeHexString(new byte[1]), BigInteger.valueOf(4000))));

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1444443)));
    }

    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAnd1x32CharsAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        List<Asset> assets = new ArrayList<>();
        for(int i=0; i < 1; i++) {
            Asset asset = new Asset(HexUtil.encodeHexString(new byte[32]), BigInteger.valueOf(4000));
            assets.add(asset);
        }
        multiAsset.setAssets(assets);

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1555554)));
    }

    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAnd110x32CharsAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        List<Asset> assets = new ArrayList<>();
        for(int i=0; i < 110; i++) {
            Asset asset = new Asset(HexUtil.encodeHexString(new byte[32]), BigInteger.valueOf(4000));
            assets.add(asset);
        }
        multiAsset.setAssets(assets);

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(23777754)));
    }

    @Test
    public void testCalculateMinAdaWhen60xPolicyIdAnd1x32CharsAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        List<MultiAsset> multiAssets = new ArrayList<>();
        for(int i=0; i< 60;i++) {
            MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
            multiAsset.setAssets(Arrays.asList(new Asset(HexUtil.encodeHexString(new byte[32]), BigInteger.valueOf(4000))));
            multiAssets.add(multiAsset);
        }

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), multiAssets));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(21222201)));
    }

}
