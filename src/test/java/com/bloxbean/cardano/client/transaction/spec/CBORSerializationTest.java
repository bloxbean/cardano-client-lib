package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionDeserializationException;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CBORSerializationTest {

    @Test
    public void testSerializeTransactionWithMint() throws CborException, AddressExcepion {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        long ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset("736174636f696e", BigInteger.valueOf(4000))));

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
    public void testSerializeTransactionWithMetadata() throws CborException, AddressExcepion {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset("736174636f696e", BigInteger.valueOf(4000))));

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
                .put(new BigInteger("197819"),new BigInteger("200001"))
                .put("203", new byte[] { 11,11,10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[] { 11,11,10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0,11})
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
    public void testDeserialization() throws CborException, TransactionDeserializationException, AddressExcepion {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(
                new Asset("736174636f696e", BigInteger.valueOf(4000)),
                new Asset("446174636f696e", BigInteger.valueOf(1100))
                ));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset("", BigInteger.valueOf(9000))));

        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId("449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset2.setAssets(Arrays.asList(new Asset("666174636f696e", BigInteger.valueOf(5000))));
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

        assertThat(deSeTransaction.getBody().getInputs(), equalTo(transaction.getBody().getInputs()));
        assertThat(deSeTransaction.getBody().getOutputs(), equalTo(transaction.getBody().getOutputs()));
        assertThat(deSeTransaction.getBody().getFee(), is(transaction.getBody().getFee()));
        assertThat(deSeTransaction.getBody().getTtl(), is(transaction.getBody().getTtl()));
        assertThat(deSeTransaction.getBody().getValidityStartInterval(), is(transaction.getBody().getValidityStartInterval()));
        assertThat(deSeTransaction.getBody().getMetadataHash(), equalTo(transaction.getBody().getMetadataHash()));
        assertThat(deSeTransaction.getBody().getMint(), equalTo(transaction.getBody().getMint()));
        assertThat(deSeTransaction, equalTo(transaction));

        assertThat(deSeTransaction.serializeToHex(), is(hex));
    }

    @Test
    public void testDeserializationWithWitness() throws CborException, TransactionDeserializationException, AddressExcepion, TransactionSerializationException {
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
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(
                new Asset("736174636f696e", BigInteger.valueOf(4000)),
                new Asset("446174636f696e", BigInteger.valueOf(1100))
        ));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset("", BigInteger.valueOf(9000))));

        MultiAsset multiAsset2 = new MultiAsset();
        multiAsset2.setPolicyId("449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset2.setAssets(Arrays.asList(new Asset("666174636f696e", BigInteger.valueOf(5000))));
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
                .put(new BigInteger("197819"),new BigInteger("200001"))
                .put("203", new byte[] { 11,11,10});

        CBORMetadataList list = new CBORMetadataList()
                .add("301value")
                .add(new BigInteger("300001"))
                .add(new byte[] { 11,11,10})
                .add(new CBORMetadataMap()
                        .put(new BigInteger("401"), "401str")
                        .put("hello", "hellovalue"));
        Metadata metadata = new CBORMetadata()
                .put(new BigInteger("197819781978"), "John")
                .put(new BigInteger("197819781979"), "CA")
                .put(new BigInteger("1978197819710"), new byte[]{0,11})
                .put(new BigInteger("1978197819711"), mm)
                .put(new BigInteger("1978197819712"), list);


        Transaction transaction = new Transaction();
        transaction.setMetadata(metadata);
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

        assertThat(deSeTransaction.getBody().getInputs(), equalTo(transaction.getBody().getInputs()));
        assertThat(deSeTransaction.getBody().getOutputs(), equalTo(transaction.getBody().getOutputs()));
        assertThat(deSeTransaction.getBody().getFee(), is(transaction.getBody().getFee()));
        assertThat(deSeTransaction.getBody().getTtl(), is(transaction.getBody().getTtl()));
        assertThat(deSeTransaction.getBody().getValidityStartInterval(), is(transaction.getBody().getValidityStartInterval()));
        assertThat(deSeTransaction.getBody().getMetadataHash(), equalTo(transaction.getBody().getMetadataHash()));
        assertThat(deSeTransaction.getBody().getMint(), hasSize(3));
        assertThat(deSeTransaction.getBody().getMint().get(0).getAssets(), equalTo(transaction.getBody().getMint().get(0).getAssets()));
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses(), hasSize(2) );
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey(), notNullValue());
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature(), notNullValue());

        //metadata
        assertThat(deSeTransaction.getMetadata(), samePropertyValuesAs(transaction.getMetadata()));
        assertThat(deSeTransaction.getMetadata().serialize(), equalTo(transaction.getMetadata().serialize()));

        assertThat(deSeTransaction.serializeToHex(), is(txnHex));
    }


    @Test
    public void testDeserializationWithScriptWitness() throws CborException, TransactionDeserializationException, AddressExcepion, TransactionSerializationException {
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
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), Collections.emptyList()));

        TransactionOutput changeOutput =  new TransactionOutput();
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


        assertThat(deSeTransaction.getBody().getInputs(), equalTo(transaction.getBody().getInputs()));
        assertThat(deSeTransaction.getBody().getOutputs(), equalTo(transaction.getBody().getOutputs()));
        assertThat(deSeTransaction.getBody().getFee(), is(transaction.getBody().getFee()));
        assertThat(deSeTransaction.getBody().getTtl(), is(transaction.getBody().getTtl()));
        assertThat(deSeTransaction.getBody().getValidityStartInterval(), is(transaction.getBody().getValidityStartInterval()));
        assertThat(deSeTransaction.getBody().getMetadataHash(), equalTo(transaction.getBody().getMetadataHash()));

        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses(), hasSize(3) );
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey(), notNullValue());
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature(), notNullValue());

        assertThat(deSeTransaction.getWitnessSet().getNativeScripts(), hasSize(1));

        assertThat(deSeTransaction.serializeToHex(), is(txnHex));
    }
}
