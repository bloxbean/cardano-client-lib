package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

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
        String txnHex = transaction.serializeToHex();
        System.out.println(txnHex);

        Transaction desTxn = Transaction.deserialize(HexUtil.decodeHexString(txnHex));

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
        assertThat(desTxn.getBody().getMint()).contains(multiAsset1);

        assertThat(desTxn.getBody().getMint().get(2))
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*name")
                .isEqualTo(multiAsset2);

        assertThat(desTxn.getBody().getMint()).contains(burnAsset);

        assertThat(desTxn.serializeToHex()).isEqualTo(txnHex);

        //Verify if the signed serialized value (without witness) is same after txn sign
        String signedTxn = fakeSignAndSerializedRemoveWitness(transaction);
        assertThat(signedTxn).isEqualTo(transaction.serializeToHex());
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
    public void testSerializeTransactionWithMetadata() throws CborSerializationException, CborDeserializationException {
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

        Transaction deserTxn = Transaction.deserialize(HexUtil.decodeHexString(hexStr));
        String deSerTxnHex = deserTxn.serializeToHex();

        assertThat(deSerTxnHex).isEqualTo(hexStr);
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
        assertThat(deSeTransaction.getBody().getOutputs().size()).isEqualTo(transaction.getBody().getOutputs().size());
        assertThat(deSeTransaction.getBody().getFee()).isEqualTo((transaction.getBody().getFee()));
        assertThat(deSeTransaction.getBody().getTtl()).isEqualTo(transaction.getBody().getTtl());
        assertThat(deSeTransaction.getBody().getValidityStartInterval()).isEqualTo(transaction.getBody().getValidityStartInterval());
        assertThat(deSeTransaction.getBody().getAuxiliaryDataHash()).isEqualTo(transaction.getBody().getAuxiliaryDataHash());

        //TODO -- Check mint details later
//        assertThat(deSeTransaction.getBody().getMint()).isEqualTo(transaction.getBody().getMint());
        assertThat(deSeTransaction.serializeToHex()).isEqualTo(hex);

        String signedTxnHex = fakeSignAndSerializedRemoveWitness(transaction);
        assertThat(signedTxnHex).isEqualTo(transaction.serializeToHex());
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

        Transaction signTxn = account1.sign(transaction);
        signTxn = account2.sign(signTxn);

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(signTxn.serializeToHex()));

        //Asserts
        assertThat(deSeTransaction.serializeToHex()).isEqualTo((signTxn.serializeToHex()));

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

        byte[] metadataByte = deSeTransaction.getAuxiliaryData().getMetadata().serialize();
        byte[] originalMetadataByte = transaction.getAuxiliaryData().getMetadata().serialize();
        assertThat(metadataByte).isEqualTo(originalMetadataByte);

        //Cross verify after signing the transaction with serialization-lib
        String signedTxn = fakeSignAndSerializedRemoveWitness(transaction);
        Transaction signedTxnObj = Transaction.deserialize(HexUtil.decodeHexString(signedTxn));
        Metadata signedMetadata = signedTxnObj.getAuxiliaryData().getMetadata();
        assertThat(signedMetadata)
                .usingDefaultComparator()
                .usingRecursiveComparison()
                .isEqualTo(deSeTransaction.getAuxiliaryData().getMetadata());


        //TODO -- Check if individual elements in the metadata can be compared
        //metadata
//        assertThat(deSeTransaction.getAuxiliaryData().getMetadata())
//                .usingDefaultComparator()
//                .usingRecursiveComparison()
//                .isEqualTo(transaction.getAuxiliaryData().getMetadata());
        assertThat(deSeTransaction.getAuxiliaryData().getMetadata().serialize()).isEqualTo(transaction.getAuxiliaryData().getMetadata().serialize());
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

        PlutusV1Script plutusScript = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();
        PlutusV1Script plutusScript1 = PlutusV1Script.builder()
                .type("PlutusScriptV1")
                .cborHex("4d01000033222220051200120011")
                .build();

        ScriptPubkey scriptPubkey = ScriptPubkey.createWithNewKey()._1;

        transaction.setAuxiliaryData(AuxiliaryData.builder()
                .metadata(metadata)
                .plutusV1Scripts(Arrays.asList(plutusScript, plutusScript1))
                .nativeScripts(Arrays.asList(scriptPubkey))
                .build());

        transaction.setBody(txnBody);

        Transaction signTxn = account1.sign(transaction);
        signTxn = account2.sign(signTxn);

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(signTxn.serializeToHex()));

        //Asserts
        assertThat(deSeTransaction.serializeToHex()).isEqualTo(signTxn.serializeToHex());
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
        assertThat(deSeTransaction.serializeToHex()).isEqualTo((signTxn.serializeToHex()));
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
        assertThat(deSeTransaction.getAuxiliaryData().getPlutusV1Scripts()).hasSize(2);
        assertThat(deSeTransaction.getAuxiliaryData().getPlutusV1Scripts()).hasSameElementsAs(transaction.getAuxiliaryData().getPlutusV1Scripts());
        assertThat(deSeTransaction.getAuxiliaryData().getNativeScripts()).hasSameElementsAs(transaction.getAuxiliaryData().getNativeScripts());
        assertThat(deSeTransaction.getBody().getMint()).hasSize(3);
        assertThat(deSeTransaction.getBody().getMint().get(0).getAssets()).isEqualTo(transaction.getBody().getMint().get(0).getAssets());
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses()).hasSize(2);
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getVkey()).isNotNull();
        assertThat(deSeTransaction.getWitnessSet().getVkeyWitnesses().get(0).getSignature()).isNotNull();

        byte[] metadataByte = deSeTransaction.getAuxiliaryData().getMetadata().serialize();
        byte[] originalMetadataByte = transaction.getAuxiliaryData().getMetadata().serialize();
        assertThat(metadataByte).isEqualTo(originalMetadataByte);

        //Cross verify after signing the transaction with serialization-lib
        String signedTxn = fakeSignAndSerializedRemoveWitness(transaction);
        Transaction signedTxnObj = Transaction.deserialize(HexUtil.decodeHexString(signedTxn));
        Metadata signedMetadata = signedTxnObj.getAuxiliaryData().getMetadata();
        assertThat(signedMetadata)
                .usingDefaultComparator()
                .usingRecursiveComparison()
                .isEqualTo(deSeTransaction.getAuxiliaryData().getMetadata());

        //TODO - Check if individual element in the metadata can be compared
//        //metadata
//        assertThat(deSeTransaction.getAuxiliaryData().getMetadata())
//                .usingDefaultComparator()
//                .usingRecursiveComparison()
//                .isEqualTo(transaction.getAuxiliaryData().getMetadata());
        assertThat(deSeTransaction.getAuxiliaryData().getMetadata().serialize()).isEqualTo(transaction.getAuxiliaryData().getMetadata().serialize());
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


        Transaction signTxn = account1.sign(transaction);
        signTxn = account2.sign(signTxn);

        signTxn = TransactionSigner.INSTANCE.sign(signTxn, sk1);

        Transaction deSeTransaction = Transaction.deserialize(HexUtil.decodeHexString(signTxn.serializeToHex()));


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

        assertThat(deSeTransaction.serializeToHex()).isEqualTo(signTxn.serializeToHex());
    }

    @Test
    public void testSerialization_whenDeprecatedSetMetadataCalled() {
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

        txnBody.setOutputs(Arrays.asList(txnOutput1, changeOutput));
        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        transaction.setWitnessSet(new TransactionWitnessSet());

        CBORMetadataMap map = new CBORMetadataMap();
        map.put("key1", "hello");
        CBORMetadata metadata = new CBORMetadata();
        metadata.put(BigInteger.valueOf(1002), map);
        transaction.setMetadata(metadata);

        Transaction txnWithBuilder = Transaction.builder()
                .body(txnBody)
                .metadata(metadata)
                .witnessSet(new TransactionWitnessSet())
                .build();

        assertThat(transaction.getMetadata()).isEqualTo(transaction.getAuxiliaryData().getMetadata());
        assertThat(txnWithBuilder.getMetadata()).isEqualTo(txnWithBuilder.getAuxiliaryData().getMetadata());
    }

    private String fakeSignAndSerializedRemoveWitness(Transaction transaction) throws CborSerializationException, CborDeserializationException {
        Transaction signedTxn = TransactionSigner.INSTANCE.sign(transaction, new Account().hdKeyPair());
        //clear witness
        signedTxn.setWitnessSet(new TransactionWitnessSet());

        String signedTxnHex = signedTxn.serializeToHex();
        return signedTxnHex;
    }

    @Nested
    class CanonicalOrdering {

        @Test
        void testCanonicalOrderAssetNames_whenSerialize() throws CborSerializationException, CborDeserializationException {
            TransactionBody txnBody = new TransactionBody();

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

            MultiAsset multiAsset1 = new MultiAsset();
            multiAsset1.setPolicyId("829728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
            multiAsset1.setAssets(Arrays.asList(
                    new Asset("bbb", BigInteger.valueOf(4000)),
                    new Asset("aaa", BigInteger.valueOf(1100)),
                    new Asset("xx", BigInteger.valueOf(4000)),
                    new Asset("yrrrrrr", BigInteger.valueOf(1100))
            ));

            MultiAsset multiAsset2 = new MultiAsset();
            multiAsset2.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
            multiAsset2.setAssets(Arrays.asList(new Asset("0x", BigInteger.valueOf(9000))));

            MultiAsset multiAsset3 = new MultiAsset();
            multiAsset3.setPolicyId("449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
            multiAsset3.setAssets(Arrays.asList(
                    new Asset("2ab", BigInteger.valueOf(2000)),
                    new Asset("3cd", BigInteger.valueOf(3000)),
                    new Asset("1f", BigInteger.valueOf(1000))
            ));
            changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset1, multiAsset2, multiAsset3)));
            txnBody.setOutputs(Arrays.asList(txnOutput1, changeOutput));
            txnBody.setFee(BigInteger.valueOf(170000));
            txnBody.setTtl(498000000);

            Transaction transaction = Transaction.builder()
                    .body(txnBody)
                    .build();

            String txnHex = transaction.serializeToHex();

            System.out.println(txnHex);

            Transaction deSerTxn = Transaction.deserialize(HexUtil.decodeHexString(txnHex));
            System.out.println(deSerTxn.serializeToHex());

            Account account = new Account(Networks.testnet());

            String signedOutputFromRustSerializationLib = "84a4008182582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002010182825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed6199c40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a00053020a3581c449728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a34231661903e8433261621907d043336364190bb8581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a140192328581c829728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a4427878190fa04361616119044c43626262190fa0477972727272727219044c021a00029810031a1daee080a10081825820af4e628f916f670af1dde3c604a5270ec5dadfe28e6dd96cc6e1c4bff6a5d2ef5840d8a5c820487f936f3c9a7974ffe7b243f86d616197074f9b9ab02408dac875a213e3553c3316a0b20da24b26e7a64b8d1c51f8fe3697b569bfcf9153021a7b09f5f6";
            Transaction signedTxnObj = Transaction.deserialize(HexUtil.decodeHexString(signedOutputFromRustSerializationLib));
            //clear witness
            signedTxnObj.setWitnessSet(new TransactionWitnessSet());

            String signedTxnHex = signedTxnObj.serializeToHex();

            assertThat(deSerTxn.serializeToHex()).isEqualTo(txnHex);
            assertThat(signedTxnHex).isEqualTo(deSerTxn.serializeToHex());
        }

    }

    @Nested
    class NamiCompatibility {
        @Test
        public void testCompatibilityWithNamiTxnHex_1() throws Exception {
            String namiTxnHex = "84a30082825820a149b3c9740b8f8b957415442336bec65bb393dfa93e88473c71a8fb7959c6d005825820297b1e742de3e7dd5f013424f5ff62d82fdf65f9d7194de1eff286a8232a196c00018282583900aff761fc1d70474cfeec6d86a2e428231949ebeb8b71464791256205167590ce95c329c8b4ba3e8e254bcc2d1e8db792dc66aa0117f94047821a0014851ea1581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da143434144194e20825839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361d821a045c5f93a2581c0f39b76d79c90289b42af0a4759f04c2cb0adcc42ae19787ff8084eea14541444d494e01581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da1434341441a23c19850021a0002aaeda0f5f6";
            Transaction desTxn = Transaction.deserialize(HexUtil.decodeHexString(namiTxnHex));

            assertThat(desTxn.serializeToHex()).isEqualTo(namiTxnHex);
        }

        @Test
        public void testCompatibilityWithNamiTxnHex_2() throws Exception {
            String namiTxnHex = "84a300838258209f213609a073f632b0f38c8bf0a2b525f6899a0e5ee006117a5734d3bef2585c048258208a1df7d5c860bb63f3b344c03f4ddd8a34a7619ce223fdb8a73f6e8a92d3813b008258201c4859ad2027aa4d8e17f66e266ea83928d575c9dd9e72ca271db9ca1bf11b4f000182825839009cc9fc0f9d97e92ef5c40931bdc9a327be94f6c9d0390d4cbd8b62d16791a7c8e95c53ffb72600ea13051f03854eef6267298760265f5bbb821a1c528901a3581c0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9efa1434f50441a0bebc214581c0f39b76d79c90289b42af0a4759f04c2cb0adcc42ae19787ff8084eea14541444d494e01581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da143434144148258390015cabc8bf1382d2dae1f317c862c3cd3de6400c8db7de092483ce9c91bfb8f3e334b0d42cf6f28e536bcb3151d64484c5c78d47d849d4291821a0014851ea1581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da143434144194e0c021a0002c851a0f5f6";

            Transaction namiTxn = Transaction.deserialize(HexUtil.decodeHexString(namiTxnHex));
            String finalTxnHex = namiTxn.serializeToHex();

            assertThat(finalTxnHex).isEqualTo(namiTxnHex);
        }

        @Test
        public void testMultiAssetsPolicyIdOrdering() throws Exception {
            MultiAsset ma1 = MultiAsset.builder()
                    .policyId("ac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03d")
                    .assets(List.of(Asset.builder()
                            .name("0x434144")
                            .value(BigInteger.valueOf(20)).build()
                    )).build();

            MultiAsset ma2 = MultiAsset.builder()
                    .policyId("0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef")
                    .assets(List.of(Asset.builder()
                            .name("0x4f5044")
                            .value(BigInteger.valueOf(200000020)).build()
                    )).build();

            MultiAsset ma3 = MultiAsset.builder()
                    .policyId("0f39b76d79c90289b42af0a4759f04c2cb0adcc42ae19787ff8084ee")
                    .assets(List.of(Asset.builder()
                            .name("0x41444d494e")
                            .value(BigInteger.valueOf(1)).build()
                    )).build();

            List<MultiAsset> list = List.of(ma1, ma2, ma3);

            Value value = Value.builder()
                    .coin(BigInteger.valueOf(134567))
                    .multiAssets(list).build();

            TransactionOutput txnOut = TransactionOutput.builder()
                    .address("addr_test1qqp6l53xshenlc939a0q74rd09e7dva8lke0fvs3a7ld5f7y7h8vnukjnluapukncvvpxvjgg4nlwu34w3ywvzngw99sy2rpy3")
                    .value(value).build();

            byte[] serBytes = CborSerializationUtil.serialize(txnOut.serialize());
            String serHex = HexUtil.encodeHexString(serBytes);

            //expected
            String expSerLib = "8258390003afd22685f33fe0b12f5e0f546d7973e6b3a7fdb2f4b211efbeda27c4f5cec9f2d29ff9d0f2d3c3181332484567f772357448e60a68714b821a00020da7a3581c0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9efa1434f50441a0bebc214581c0f39b76d79c90289b42af0a4759f04c2cb0adcc42ae19787ff8084eea14541444d494e01581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da14343414414";

            assertThat(serHex).isEqualTo(expSerLib);
        }

        @Test
        void testCompatibilityWithNamiWitness() throws Exception {
            String namiTxnHex = "84a300838258206deb8993fa4b541892b5b698fef4fbb9ea73265395b5837fe45581b37efb3b7a01825820297b1e742de3e7dd5f013424f5ff62d82fdf65f9d7194de1eff286a8232a196c00825820538586beedfbac419ca3a5983c0efa3f486ffddbebf053824abc778a55c06078000182825839005a512f32ebdd33cbb6525d9fe041b0044ecbb24dec402b5c2c8bf98a1bfb8f3e334b0d42cf6f28e536bcb3151d64484c5c78d47d849d4291821a0014851ea1581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da143434144194da8825839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361d821a003040c9a3581c0f39b76d79c90289b42af0a4759f04c2cb0adcc42ae19787ff8084eea14541444d494e01581c869708e97e418f1422e22f8b026559a565aab320184fdd98efff4f4ca14a2142466426544d6d704001581cac6d9e75ca58379c394378a64ae24eddf72b2e78d73f635bac32d03da14343414414021a0002c8d5a0f5f6";
            String namiWitness = "a10081825820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e358402bfb7ba45583f5826e02d1cee544ab37398a72c0db5e01048f88ebd7c97deff7b49492be0f0edabebe67b14cb8b12a3e68c42cbb0b4970ec2e60919c3a5b6b02";
            String namiMnemonic = "round stomach concert dizzy pluck express inject seminar satoshi vote essence artist pink awful bubble frog bullet horror spoil risk false dolphin limit sock";

            Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(namiTxnHex));
            Account namiAcc = new Account(Networks.testnet(), namiMnemonic);
            Transaction signedTxn = namiAcc.sign(transaction);

            String finalWitnessHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(signedTxn.getWitnessSet().serialize()));

            assertThat(finalWitnessHex).isEqualTo(namiWitness);
            assertThat(transaction.serializeToHex()).isEqualTo(namiTxnHex);
        }
    }

    @Nested
    class WithdrwalTransaction {

        @Test
        void testSerializeAndDeserializeWithWithdrawal() throws CborSerializationException, CborDeserializationException {
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

            txnBody.setOutputs(Arrays.asList(txnOutput1));
            txnBody.setWithdrawals(List.of(
                    new Withdrawal("stake_test1uqz9t0qhs4fsjzuauul8l7y5galefd4n6342lg289axcj5qe97f84", BigInteger.valueOf(5000)),
                    new Withdrawal("stake_test1updaungfmrqlw3002699vv6mcsg24eqj3nffd6aqyccjkjc0ltamt", BigInteger.valueOf(9000))));
            txnBody.setFee(new BigInteger(String.valueOf(fee)));
            txnBody.setTtl(ttl);

            Transaction transaction = new Transaction();
            transaction.setBody(txnBody);
            transaction.setWitnessSet(new TransactionWitnessSet());

            Transaction txnWithBuilder = Transaction.builder()
                    .body(txnBody)
                    .witnessSet(new TransactionWitnessSet())
                    .build();

            byte[] serBytes = txnWithBuilder.serialize();

            Transaction deTransaction = Transaction.deserialize(serBytes);

            assertThat(deTransaction.serializeToHex()).isEqualTo(transaction.serializeToHex());
            assertThat(deTransaction.getBody().getWithdrawals()).isEqualTo(transaction.getBody().getWithdrawals());
        }
    }
}
