package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MinAdaCalculatorTest {

    private MinAdaCalculator minAdaCalculator;

    @BeforeEach
    public void setup() {
        ProtocolParams protocolParams = ProtocolParams.builder()
                .coinsPerUtxoWord("34482")
                .coinsPerUtxoSize("4310")
                .build();
        minAdaCalculator = new MinAdaCalculator(protocolParams);
    }

    @Test
    public void testCalculateMinAdaWithAdaOnlyUtxo() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        output.setValue(new Value(new BigInteger(String.valueOf(20000)), null));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(969750)));
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
        assertThat(minAda, is(BigInteger.valueOf(1129220)));
    }

    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAndSingleCharacterAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset(HexUtil.encodeHexString(getRandomBytes(1), true), BigInteger.valueOf(4000))));

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1133530)));
    }

    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAnd3x1CharsAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        List<Asset> assets = new ArrayList<>();
        for(int i=0; i < 3; i++) {
            Asset asset = new Asset(HexUtil.encodeHexString(getRandomBytes(1), true), BigInteger.valueOf(4000));
            assets.add(asset);
        }
        multiAsset.setAssets(assets);

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1176630)));
    }

    @Test
    public void testCalculateMinAdaWhen2xPolicyIdAnd1x0CharsAssetName() throws CborSerializationException {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        List<MultiAsset> multiAssets = new ArrayList<>();
        for(int i=0; i< 2;i++) {
            MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId(PolicyUtil.createMultiSigScriptAllPolicy("test", 1).getPolicyId());
            multiAsset.setAssets(Arrays.asList(new Asset(HexUtil.encodeHexString(new byte[0], true), BigInteger.valueOf(4000))));
            multiAssets.add(multiAsset);
        }

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), multiAssets));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1280070)));
    }

    @Test
    public void testCalculateMinAdaWhen2xPolicyIdAnd1x1CharsAssetName() throws CborSerializationException {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        List<MultiAsset> multiAssets = new ArrayList<>();
        for(int i=0; i< 2;i++) {
           MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId(PolicyUtil.createMultiSigScriptAllPolicy("test", 1).getPolicyId());
            multiAsset.setAssets(Arrays.asList(new Asset(HexUtil.encodeHexString(getRandomBytes(1), true), BigInteger.valueOf(4000))));
            multiAssets.add(multiAsset);
        }

        output.setValue(new Value(new BigInteger(String.valueOf(4000000)), multiAssets));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(1288690)));
    }

    //TODO - reverify with other impl. Other tests are already verified
    @Test
    public void testCalculateMinAdaWhen3xPolicyIdAnd96x1CharsAssetName() throws CborSerializationException {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        char[] chars = new char[105];
        for(int i=0;i<101;i++) {
            chars[i] = (char)i;
        }

        List<MultiAsset> multiAssets = new ArrayList<>();
        int index = 0;
        for(int i=0; i< 3;i++) {
            MultiAsset multiAsset = new MultiAsset();

            ScriptPubkey scriptPubkey = ScriptPubkey.createWithNewKey()._1;
            String policyId = scriptPubkey.getPolicyId();
            multiAsset.setPolicyId(policyId);

            for(int k=0; k<32; k++) {
                multiAsset.getAssets().add(new Asset(HexUtil.encodeHexString(new byte[]{(byte)chars[index++]}, true), BigInteger.valueOf(4000)));
            }
            multiAssets.add(multiAsset);
        }

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), multiAssets));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(3460930)));
    }

    //TODO - Test cases with datum hash

    //Additional test cases
    @Test
    public void testCalculateMinAdaWhenOnePolicyIdAnd110x32CharsAssetName() {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        List<Asset> assets = new ArrayList<>();
        for(int i=0; i < 110; i++) {
            Asset asset = new Asset(HexUtil.encodeHexString(getRandomBytes(32), true), BigInteger.valueOf(4000));
            assets.add(asset);
        }
        multiAsset.setAssets(assets);

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), Arrays.asList(multiAsset)));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(18657990)));
    }

    @Test
    public void testCalculateMinAdaWhen60xPolicyIdAnd1x32CharsAssetName() throws CborSerializationException {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        List<MultiAsset> multiAssets = new ArrayList<>();
        for(int i=0; i<60; i++) {
            MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId(PolicyUtil.createMultiSigScriptAllPolicy("test", 1).getPolicyId());
            multiAsset.setAssets(Arrays.asList(new Asset(HexUtil.encodeHexString(getRandomBytes(32), true), BigInteger.valueOf(4000))));
            multiAssets.add(multiAsset);
        }

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), multiAssets));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(18567480)));
    }

    //Tests with few same asset names
    //TODO - reverify with other impl. Other tests are already verified
    @Test
    public void testCalculateMinAdaWhen3xPolicyIdAnd96x1CharsAssetNameWithFewSameAssetNames() throws CborSerializationException {
        TransactionOutput output =  new TransactionOutput();
        output.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        char[] chars = new char[105];
        for(int i=0;i<101;i++) {
            chars[i] = (char)i;
        }

        chars[4] = chars[32];
        chars[5] = chars[32];
        chars[7] = chars[32];
        chars[8] = chars[32];
        chars[9] = chars[32];
        chars[2] = chars[32];

        List<MultiAsset> multiAssets = new ArrayList<>();
        int index = 0;
        for(int i=0; i< 3;i++) {
            MultiAsset multiAsset = new MultiAsset();

            ScriptPubkey scriptPubkey = ScriptPubkey.createWithNewKey()._1;
            String policyId = scriptPubkey.getPolicyId();
            multiAsset.setPolicyId(policyId);

            for(int k=0; k<32; k++) {
                multiAsset.getAssets().add(new Asset(HexUtil.encodeHexString(new byte[]{(byte)chars[index++]}, true), BigInteger.valueOf(4000)));
            }
            multiAssets.add(multiAsset);
        }

        output.setValue(new Value(new BigInteger(String.valueOf(40000)), multiAssets));

        BigInteger minAda = minAdaCalculator.calculateMinAda(output);
        System.out.println(minAda);
        assertThat(minAda, is(BigInteger.valueOf(3353180)));
    }

    private byte[] getRandomBytes(int size) {
        byte[] b = new byte[size];
        new Random().nextBytes(b);
        return b;
    }
}
