package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CBORSerializationTest {

    @Test
    public void testSerializeTransactionWithMint() throws AddressExcepion, CborSerializationException, CborDeserializationException {
        TransactionBody txnBody = new TransactionBody();

        String txId = "73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002";
        String recAddress = "addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v";
        String outputAddress = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        long fee = 367965;
        long ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId(txId); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress(recAddress);
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), null));

        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress(outputAddress);

        MultiAsset multiAsset1 = new MultiAsset();
        String policyId1 = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96";
        multiAsset1.setPolicyId(policyId1);
        String asset1 = "0x736174636f696e";
        multiAsset1.setAssets(Arrays.asList(new Asset(asset1, BigInteger.valueOf(4000))));

        MultiAsset multiAsset2 = new MultiAsset();
        String policyId2 = "6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";
        multiAsset2.setPolicyId(policyId2);
        String asset2 = "Test";
        multiAsset2.setAssets(Arrays.asList(new Asset(asset2, BigInteger.valueOf(9000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset1, multiAsset2)));

        MultiAsset burnAsset = new MultiAsset();
        String burnPolicyId = "229728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a26";
        burnAsset.setPolicyId(burnPolicyId);
        burnAsset.setAssets(Arrays.asList(new Asset(asset1, BigInteger.valueOf(-5000))));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset1);
        txnBody.getMint().add(multiAsset2);
        txnBody.getMint().add(burnAsset);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        String hexStr = transaction.serializeToHex();
        System.out.println(hexStr);

        Transaction desTxn = Transaction.deserialize(HexUtil.decodeHexString(hexStr));

        assertThat(desTxn.getBody().getInputs().get(0)).isEqualTo(txnBody.getInputs().get(0));

        assertThat(desTxn.getBody().getOutputs().get(0).getAddress()).isEqualTo(recAddress);
        assertThat(desTxn.getBody().getOutputs().get(0).getValue().getCoin()).isEqualTo(txnBody.getOutputs().get(0).getValue().getCoin());
        assertThat(desTxn.getBody().getOutputs().get(0).getValue().getMultiAssets()).isEmpty();

        //change output
        assertThat(desTxn.getBody().getOutputs().get(1).getAddress()).isEqualTo(outputAddress);
        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(txnBody.getOutputs().get(1).getValue().getCoin());

        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getPolicyId()).isEqualTo(policyId1);
        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets().get(0).getName()).isEqualTo(asset1);
        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getMultiAssets().get(0).getAssets().get(0).getValue()).isEqualTo(BigInteger.valueOf(4000));

        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getPolicyId()).isEqualTo(policyId2);
        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getAssets().get(0).getName()).isEqualTo("0x54657374");
        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getMultiAssets().get(1).getAssets().get(0).getValue()).isEqualTo(BigInteger.valueOf(9000));

        assertThat(desTxn.getBody().getFee()).isEqualTo(BigInteger.valueOf(fee));
        assertThat(desTxn.getBody().getTtl()).isEqualTo(ttl);
        assertThat(desTxn.getBody().getMint().get(0)).isEqualTo(multiAsset1);
        assertThat(desTxn.getBody().getMint().get(1))
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*name")
                .isEqualTo(multiAsset2);
        assertThat(desTxn.getBody().getMint().get(2)).isEqualTo(burnAsset);

        boolean res = CardanoJNAUtil.validateTransactionCBOR(hexStr);
        System.out.println(res);
        assertTrue(res);
    }

    @Test
    //Below test is only for serialization test. Some of the values are invalid in real scenario.
    public void testSerializeTransaction_whenValueGreaterThan64Bytes() throws CborSerializationException, CborDeserializationException {
        TransactionBody txnBody = new TransactionBody();

        String txId = "73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002";
        String recAddress = "addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v";
        String outputAddress = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        long fee = 367965;
        long ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId(txId); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress(recAddress);
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), null));

        //output 2
        TransactionOutput txnOutput2 = new TransactionOutput();
        txnOutput2.setAddress(recAddress);
        txnOutput2.setValue(new Value(null, null));

        //change output
        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress(outputAddress);

        MultiAsset multiAsset1 = new MultiAsset();
        String policyId1 = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96";
        multiAsset1.setPolicyId(policyId1);
        String asset1 = "0x736174636f696e";
        multiAsset1.setAssets(Arrays.asList(new Asset(asset1, new BigInteger("888000000000000000000000000"))));

        MultiAsset multiAsset2 = new MultiAsset();
        String policyId2 = "429728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96";
        multiAsset2.setPolicyId(policyId2);
        String asset2 = "0x466174636f696e";
        multiAsset2.setAssets(Arrays.asList(new Asset(asset2, new BigInteger("-4555666000000000000"))));
        //Negative value is not supported in value.coin field, but this is just for serialization test. Should not happen
        changeOutput.setValue(new Value(new BigInteger("-333000000000000000000"), Arrays.asList(multiAsset1, multiAsset2)));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(txnOutput2);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset1);
        txnBody.getMint().add(multiAsset2);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        String hexStr = transaction.serializeToHex();

        Transaction desTxn = Transaction.deserialize(HexUtil.decodeHexString(hexStr));

        //assert
        assertThat(desTxn.getBody().getOutputs().get(1).getAddress()).isEqualTo(recAddress);
        assertThat(desTxn.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(BigInteger.ZERO);

        assertThat(desTxn.getBody().getOutputs().get(2).getAddress()).isEqualTo(outputAddress);
        assertThat(desTxn.getBody().getOutputs().get(2).getValue().getCoin())
                .isLessThan(BigInteger.ZERO)
                .isGreaterThanOrEqualTo(new BigInteger("-333000000000000000000")); //For some rounding error
        assertThat(desTxn.getBody().getOutputs().get(2).getValue().getMultiAssets().get(0).getAssets().get(0).getValue())
                .isEqualTo(new BigInteger("888000000000000000000000000"));
        assertThat(desTxn.getBody().getOutputs().get(2).getValue().getMultiAssets().get(1).getAssets().get(0).getValue())
                .isEqualTo(new BigInteger("-4555666000000000000"));

    }

    @Test
    public void testSerializeTransactionWithMetadata() throws CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), null));

        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset("0x736174636f696e", BigInteger.valueOf(4000))));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset(null, BigInteger.valueOf(9000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset, multiAsset1)));


        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset);
        txnBody.getMint().add(multiAsset1);

        CBORMetadataMap mm = new CBORMetadataMap()
                .put(new BigInteger("1978"), "201value")
                .put(new BigInteger("197819"), new BigInteger("200001"))
                .put("203", new byte[]{11, 11, 10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[]{11, 11, 10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0, 11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        String hexStr = transaction.serializeToHex();
        System.out.println("********************");
        System.out.println(hexStr);

        boolean res = CardanoJNAUtil.validateTransactionCBOR(hexStr);
        System.out.println(res);

        assertTrue(res);
    }

    @Test
    public void testDeserialization() throws CborDeserializationException, AddressExcepion, CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(
                new Asset("0x736174636f696e", BigInteger.valueOf(4000)),
                new Asset("0x446174636f696e", BigInteger.valueOf(1100))
        ));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset("0x", BigInteger.valueOf(9000))));

        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId("449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset2.setAssets(Arrays.asList(new Asset("0x666174636f696e", BigInteger.valueOf(5000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset, multiAsset1, multiAsset2)));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset);
        txnBody.getMint().add(multiAsset1);
        txnBody.getMint().add(multiAsset2);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);

        String hex = transaction.serializeToHex();

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(hex));

        assertThat(deSeTransaction.getBody().getInputs()).isEqualTo(transaction.getBody().getInputs());
        assertThat(deSeTransaction.getBody().getOutputs()).isEqualTo(transaction.getBody().getOutputs());
        assertThat(deSeTransaction.getBody().getFee()).isEqualTo((transaction.getBody().getFee()));
        assertThat(deSeTransaction.getBody().getTtl()).isEqualTo(transaction.getBody().getTtl());
        assertThat(deSeTransaction.getBody().getValidityStartInterval()).isEqualTo(transaction.getBody().getValidityStartInterval());
        assertThat(deSeTransaction.getBody().getAuxiliaryDataHash()).isEqualTo(transaction.getBody().getAuxiliaryDataHash());
        assertThat(deSeTransaction.getBody().getMint()).isEqualTo(transaction.getBody().getMint());
        assertThat(deSeTransaction).isEqualTo(transaction);

        assertThat(deSeTransaction.serializeToHex()).isEqualTo(hex);
    }

    @Test
    public void testDeserializationWithWitness()
            throws CborDeserializationException, AddressExcepion, CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(
                new Asset("0x736174636f696e", BigInteger.valueOf(4000)),
                new Asset("0x446174636f696e", BigInteger.valueOf(1100))
        ));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset("0x", BigInteger.valueOf(9000))));

        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId("449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset2.setAssets(Arrays.asList(new Asset("0x666174636f696e", BigInteger.valueOf(5000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset, multiAsset1, multiAsset2)));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset);
        txnBody.getMint().add(multiAsset1);
        txnBody.getMint().add(multiAsset2);

        CBORMetadataMap mm = new CBORMetadataMap()
                .put(new BigInteger("1978"), "201value")
                .put(new BigInteger("197819"), new BigInteger("200001"))
                .put("203", new byte[]{11, 11, 10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[]{11, 11, 10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0, 11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);

        Transaction transaction = new Transaction();
        transaction.setAuxiliaryData(AuxiliaryData.builder()
                .metadata(metadata)
                .build());
        transaction.setBody(txnBody);

        String txnHex = account1.sign(transaction);
        txnHex = account2.sign(txnHex);

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));

        //Sort certain fields and then assert
        Comparator<Asset> assetComparator = new Comparator<Asset>() {
            @Override
            public int compare(Asset o1, Asset o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };

        Comparator<MultiAsset> multiAssetComparator = new Comparator<MultiAsset>() {
            @Override
            public int compare(MultiAsset o1, MultiAsset o2) {
                return o1.getPolicyId().compareTo(o2.getPolicyId());
            }
        };

        deSeTransaction.getBody().getOutputs().forEach(
                out -> Collections.sort(out.getValue().getMultiAssets(), multiAssetComparator));
        deSeTransaction.getBody().getOutputs().forEach(
                out -> out.getValue().getMultiAssets()
                        .forEach(ma -> Collections.sort(ma.getAssets(), assetComparator))
        );

        transaction.getBody().getOutputs().forEach(
                out -> Collections.sort(out.getValue().getMultiAssets(), multiAssetComparator));
        transaction.getBody().getOutputs().forEach(
                out -> out.getValue().getMultiAssets()
                        .forEach(ma -> Collections.sort(ma.getAssets(), assetComparator))
        );

        deSeTransaction.getBody().getMint().forEach(ma -> Collections.sort(ma.getAssets(), assetComparator));
        transaction.getBody().getMint().forEach(ma -> Collections.sort(ma.getAssets(), assetComparator));

        assertThat(deSeTransaction.getBody().getInputs()).isEqualTo(transaction.getBody().getInputs());
        assertThat(deSeTransaction.getBody().getOutputs()).isEqualTo(transaction.getBody().getOutputs());
        assertThat(deSeTransaction.getBody().getFee()).isEqualTo(transaction.getBody().getFee());
        assertThat(deSeTransaction.getBody().getTtl()).isEqualTo(transaction.getBody().getTtl());
        assertThat(deSeTransaction.getBody().getValidityStartInterval()).isEqualTo(transaction.getBody().getValidityStartInterval());
        assertThat(deSeTransaction.getBody().getAuxiliaryDataHash()).isEqualTo(transaction.getBody().getAuxiliaryDataHash());
        assertThat(deSeTransaction.getBody().getMint()).hasSize(3);
        assertThat(deSeTransaction.getBody().getMint().get(0).getAssets()).isEqualTo(transaction.getBody().getMint().get(0).getAssets());
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses()).hasSize(2);
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey()).isNotNull();
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature()).isNotNull();

        //metadata
        assertThat(deSeTransaction.getAuxiliaryData().getMetadata())
                .usingDefaultComparator()
                .usingRecursiveComparison()
                .isEqualTo(transaction.getAuxiliaryData().getMetadata());
        assertThat(deSeTransaction.getAuxiliaryData().getMetadata().serialize()).isEqualTo(transaction.getAuxiliaryData().getMetadata().serialize());

        assertThat(deSeTransaction.serializeToHex()).isEqualTo((txnHex));
    }

    @Test
    public void testDeserializationWithWitness_whenMetadataPlutusScriptAndNativeScripts()
            throws CborDeserializationException, AddressExcepion, CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(
                new Asset("0x736174636f696e", BigInteger.valueOf(4000)),
                new Asset("0x446174636f696e", BigInteger.valueOf(1100))
        ));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset("0x", BigInteger.valueOf(9000))));

        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId("449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset2.setAssets(Arrays.asList(new Asset("0x666174636f696e", BigInteger.valueOf(5000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset, multiAsset1, multiAsset2)));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset);
        txnBody.getMint().add(multiAsset1);
        txnBody.getMint().add(multiAsset2);

        CBORMetadataMap mm = new CBORMetadataMap()
                .put(new BigInteger("1978"), "201value")
                .put(new BigInteger("197819"), new BigInteger("200001"))
                .put("203", new byte[]{11, 11, 10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[]{11, 11, 10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0, 11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);

        Transaction transaction = new Transaction();

        PlutusScript plutusScript = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();
        PlutusScript plutusScript1 = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        ScriptPubkey scriptPubkey = ScriptPubkey.createWithNewKey()._1;

        transaction.setAuxiliaryData(AuxiliaryData.builder()
                .metadata(metadata)
                .plutusScripts(Arrays.asList(plutusScript, plutusScript1))
                .nativeScripts(Arrays.asList(scriptPubkey))
                .build());

        transaction.setBody(txnBody);

        String txnHex = account1.sign(transaction);
        txnHex = account2.sign(txnHex);

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));

        //Sort certain fields and then assert
        Comparator<Asset> assetComparator = new Comparator<Asset>() {
            @Override
            public int compare(Asset o1, Asset o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };

        Comparator<MultiAsset> multiAssetComparator = new Comparator<MultiAsset>() {
            @Override
            public int compare(MultiAsset o1, MultiAsset o2) {
                return o1.getPolicyId().compareTo(o2.getPolicyId());
            }
        };

        deSeTransaction.getBody().getOutputs().forEach(
                out -> Collections.sort(out.getValue().getMultiAssets(), multiAssetComparator));
        deSeTransaction.getBody().getOutputs().forEach(
                out -> out.getValue().getMultiAssets()
                        .forEach(ma -> Collections.sort(ma.getAssets(), assetComparator))
        );

        transaction.getBody().getOutputs().forEach(
                out -> Collections.sort(out.getValue().getMultiAssets(), multiAssetComparator));
        transaction.getBody().getOutputs().forEach(
                out -> out.getValue().getMultiAssets()
                        .forEach(ma -> Collections.sort(ma.getAssets(), assetComparator))
        );

        deSeTransaction.getBody().getMint().forEach(ma -> Collections.sort(ma.getAssets(), assetComparator));
        transaction.getBody().getMint().forEach(ma -> Collections.sort(ma.getAssets(), assetComparator));

        assertThat(deSeTransaction.getBody().getInputs()).isEqualTo(transaction.getBody().getInputs());
        assertThat(deSeTransaction.getBody().getOutputs()).isEqualTo(transaction.getBody().getOutputs());
        assertThat(deSeTransaction.getBody().getFee()).isEqualTo(transaction.getBody().getFee());
        assertThat(deSeTransaction.getBody().getTtl()).isEqualTo(transaction.getBody().getTtl());
        assertThat(deSeTransaction.getBody().getValidityStartInterval()).isEqualTo(transaction.getBody().getValidityStartInterval());
        assertThat(deSeTransaction.getBody().getAuxiliaryDataHash()).isEqualTo(transaction.getBody().getAuxiliaryDataHash());
        assertThat(deSeTransaction.getAuxiliaryData().getPlutusScripts()).hasSize(2);
        assertThat(deSeTransaction.getAuxiliaryData().getPlutusScripts()).hasSameElementsAs(transaction.getAuxiliaryData().getPlutusScripts());
        assertThat(deSeTransaction.getAuxiliaryData().getNativeScripts()).hasSameElementsAs(transaction.getAuxiliaryData().getNativeScripts());
        assertThat(deSeTransaction.getBody().getMint()).hasSize(3);
        assertThat(deSeTransaction.getBody().getMint().get(0).getAssets()).isEqualTo(transaction.getBody().getMint().get(0).getAssets());
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses()).hasSize(2);
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey()).isNotNull();
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature()).isNotNull();

        //metadata
        assertThat(deSeTransaction.getAuxiliaryData().getMetadata())
                .usingDefaultComparator()
                .usingRecursiveComparison()
                .isEqualTo(transaction.getAuxiliaryData().getMetadata());
        assertThat(deSeTransaction.getAuxiliaryData().getMetadata().serialize()).isEqualTo(transaction.getAuxiliaryData().getMetadata().serialize());

        assertThat(deSeTransaction.serializeToHex()).isEqualTo((txnHex));
    }

    @Test
    public void testDeserializationWithScriptWitness() throws CborDeserializationException, CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 = new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput = new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        changeOutput.setValue(new Value(new BigInteger("30"), Collections.emptyList()));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);

        Tuple<ScriptPubkey, Keys> tuple1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey1 = tuple1._1;
        SecretKey sk1 = tuple1._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey2 = tuple2._1;
        SecretKey sk2 = tuple2._2.getSkey();

        Tuple<ScriptPubkey, Keys> tuple3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey scriptPubkey3 = tuple3._1;
        SecretKey sk3 = tuple3._2.getSkey();

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(1)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        transaction.setWitnessSet(new TransactionWitnessSet());
        transaction.getWitnessSet().setNativeScripts(Arrays.asList(scriptAtLeast));


        String txnHex = account1.sign(transaction);
        txnHex = account2.sign(txnHex);

        txnHex = CardanoJNAUtil.signWithSecretKey(txnHex, HexUtil.encodeHexString(sk1.getBytes()));

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));


        assertThat(deSeTransaction.getBody().getInputs()).isEqualTo(transaction.getBody().getInputs());
        assertThat(deSeTransaction.getBody().getOutputs()).isEqualTo(transaction.getBody().getOutputs());
        assertThat(deSeTransaction.getBody().getFee()).isEqualTo(transaction.getBody().getFee());
        assertThat(deSeTransaction.getBody().getTtl()).isEqualTo(transaction.getBody().getTtl());
        assertThat(deSeTransaction.getBody().getValidityStartInterval()).isEqualTo(transaction.getBody().getValidityStartInterval());
        assertThat(deSeTransaction.getBody().getAuxiliaryDataHash()).isEqualTo(transaction.getBody().getAuxiliaryDataHash());

        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses()).hasSize(3);
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey()).isNotNull();
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature()).isNotNull();

        assertThat(deSeTransaction.getWitnessSet().getNativeScripts()).hasSize(1);

        assertThat(deSeTransaction.serializeToHex()).isEqualTo(txnHex);
    }
}
