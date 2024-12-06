package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class AccountTest {

    @BeforeAll
    static void setUp() {

    }

    @Test
    void getNewBaseAddress_Mainnet() {
        Account account = new Account(0);
        String address = account.baseAddress();
        String mnemonic = account.mnemonic();

        assertNotNull(address);
        assertNotNull(mnemonic);
        assertTrue(address.startsWith("addr") && !address.startsWith("addr_"));
        assertEquals(24, mnemonic.split(" ").length );
    }

    @Test
    void getNewBaseAddress_Test() {
        Account account = new Account(Networks.testnet(), 0);
        String address = account.baseAddress();
        String mnemonic = account.mnemonic();

        assertNotNull(address);
        assertNotNull(mnemonic);
        assertTrue(address.startsWith("addr_test"));
        assertEquals(24, mnemonic.split(" ").length );
    }

    @Test
    public void testBaseAddressHasSamePrefixForAllTestnets() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account testnetAccount = new Account(Networks.testnet(), mnemonic);
        Account previewAccount = new Account(Networks.preview(), mnemonic);
        Account preprodAccount = new Account(Networks.preprod(), mnemonic);


        String testnetPrefix="addr_test";
        assertTrue(testnetAccount.baseAddress().startsWith(testnetPrefix));
        assertTrue(previewAccount.baseAddress().startsWith(testnetPrefix));
        assertTrue(preprodAccount.baseAddress().startsWith(testnetPrefix));
    }

    @Test
    public void testBaseAddressHasTestnetPrefixForRandomTestnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account testnetAccount = new Account(Networks.testnet(), mnemonic);

        Network customTestnet = new Network(NetworkId.TESTNET.ordinal(), 42);
        Account customTestnetAccount = new Account(customTestnet, mnemonic);

        String testnetPrefix="addr_test";
        assertTrue(customTestnetAccount.baseAddress().startsWith(testnetPrefix));
        assertTrue(testnetAccount.baseAddress().equals(customTestnetAccount.baseAddress()));
    }

    @Test
    void getBaseAddressFromMnemonic() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String address0 = "addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra";

        Account account = new Account(phrase24W, 0);

        assertNotNull(account.baseAddress());
        assertNotNull(account.mnemonic());
        assertEquals(address0, account.baseAddress());
        assertEquals(phrase24W, account.mnemonic());
    }

    @Test
    void getBaseAddressFromMnemonic_byNetwork() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String address0 = "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z";

        Account account = new Account(Networks.testnet(), phrase24W);

        assertNotNull(account.baseAddress());
        assertNotNull(account.mnemonic());
        assertEquals(address0, account.baseAddress());
        assertEquals(phrase24W, account.mnemonic());
    }

    @Test
    void getBaseAddressFromAccountPrivateKey_byNetwork() {
        String accountPrivateKey = "a83aa0356397602d3da7648f139ca06be2465caef14ac4d795b17cdf13bd0f4fe9aac037f7e22335cd99495b963d54f21e8dae540112fe56243b287962da366fd4016f4cfb6d6baba1807621b4216d18581c38404c4768fe820204bef98ba706";
        String address0 = "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z";

        Account account = new Account(Networks.testnet(), HexUtil.decodeHexString(accountPrivateKey));

        assertNotNull(account.baseAddress());
        assertNotNull(account.privateKeyBytes());
        assertEquals(address0, account.baseAddress());
    }

    @Test
    void getChangeAddressFromAccountPrivateKey_byNetwork() {
        String accountPrivateKey = "a83aa0356397602d3da7648f139ca06be2465caef14ac4d795b17cdf13bd0f4fe9aac037f7e22335cd99495b963d54f21e8dae540112fe56243b287962da366fd4016f4cfb6d6baba1807621b4216d18581c38404c4768fe820204bef98ba706";
        String changeAddress0 = "addr_test1qpqwpvc7946mqvl0mwwhqgmh6w4a6335mkuypjyg9fd5elsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psy8w5eh";

        Account account = new Account(Networks.testnet(), HexUtil.decodeHexString(accountPrivateKey));

        assertNotNull(account.changeAddress());
        assertNotNull(account.privateKeyBytes());
        assertEquals(changeAddress0, account.changeAddress());
    }

    @Test
    public void getAddressesFromAccountPrivateKey_byNetwork() {
        String accountPrivateKey = "48db0f8ee847a816af78be975c0423571b8c6081ee804c6eb941ddb9e926fe5348b088c1fa4896fccfc366a3f05dd72064abb17fc913225298c4d3bf36075d362eb9219834f328c566e2d1cc1f2194bb42f896e471d19efa9e08c71dee357fe0";
        String baseAddress0 = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        String changeAddress0 = "addr_test1qrvayr52ketz2rtsmkswk6tf4llylwt6rjjtm74wvqlwe56djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuq0zqye4";
        String entAddress0 = "addr_test1vzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah8c360ccn";

        Account account1 = new Account(Networks.testnet(), HexUtil.decodeHexString(accountPrivateKey));

        assertNotNull(account1.changeAddress());
        assertNotNull(account1.privateKeyBytes());
        assertThat(account1.baseAddress()).isEqualTo(baseAddress0);
        assertThat(account1.changeAddress()).isEqualTo(changeAddress0);
        assertThat(account1.enterpriseAddress()).isEqualTo(entAddress0);
    }

    @Test
    public void getAddressesFromAccountPrivateKey_AccountOne_Index0_byNetwork() {
        //phrase - tell world avoid joy rain wrestle credit hotel silver inmate fetch card key unfold language
        //1852H/1815H/1H
        String accountPrivateKey = "f8e4a0559fb5b9099c91bc111426a1221839bcef45a171f093888fec7f9bc757c9df293e8546b1b3176cc7d2e47201677238fad80cfa1a7d37216032d81e4fdf97f28a4152ab7f20a3722c9a11cd3b1c75a8f69cb7dd9937fc274d28d13faa11";
        String baseAddress0 = "addr1q9el045jcaxdaunqpe43x5tfze4zef9fzemqquw3s649703ffud9yucv5uky2qq266g4rr0c450pqjv7xlgcwfkk86nqrgvq3t";
        String changeAddress0 = "addr1qxsespe74uytzhtgpqzljudae74q34kq59ryyde4jz2nej3ffud9yucv5uky2qq266g4rr0c450pqjv7xlgcwfkk86nqt2ez95";
        String entAddress0 = "addr1v9el045jcaxdaunqpe43x5tfze4zef9fzemqquw3s64970selp223";

        Account account1 = new Account(Networks.mainnet(), HexUtil.decodeHexString(accountPrivateKey), 1, 0);

        assertNotNull(account1.changeAddress());
        assertNotNull(account1.privateKeyBytes());
        assertThat(account1.baseAddress()).isEqualTo(baseAddress0);
        assertThat(account1.changeAddress()).isEqualTo(changeAddress0);
        assertThat(account1.enterpriseAddress()).isEqualTo(entAddress0);
    }

    @Test
    public void getAddressesFromAccountPrivateKey_AccountOne_Index1_byNetwork() {
        //phrase - tell world avoid joy rain wrestle credit hotel silver inmate fetch card key unfold language
        //1852H/1815H/1H
        String accountPrivateKey = "f8e4a0559fb5b9099c91bc111426a1221839bcef45a171f093888fec7f9bc757c9df293e8546b1b3176cc7d2e47201677238fad80cfa1a7d37216032d81e4fdf97f28a4152ab7f20a3722c9a11cd3b1c75a8f69cb7dd9937fc274d28d13faa11";
        String baseAddress1 = "addr1q89ntc6n7pc3nvgv700esraqrhaur70gdmwlr5ft5c2mmr3ffud9yucv5uky2qq266g4rr0c450pqjv7xlgcwfkk86nqrc5pn3";
        String changeAddress0 = "addr1qxsespe74uytzhtgpqzljudae74q34kq59ryyde4jz2nej3ffud9yucv5uky2qq266g4rr0c450pqjv7xlgcwfkk86nqt2ez95";
        String entAddress0 = "addr1v89ntc6n7pc3nvgv700esraqrhaur70gdmwlr5ft5c2mmrsxe77lv";

        Account account1 = new Account(Networks.mainnet(), HexUtil.decodeHexString(accountPrivateKey), 1, 1);

        assertNotNull(account1.changeAddress());
        assertNotNull(account1.privateKeyBytes());
        assertThat(account1.baseAddress()).isEqualTo(baseAddress1);
        assertThat(account1.changeAddress()).isEqualTo(changeAddress0);
        assertThat(account1.enterpriseAddress()).isEqualTo(entAddress0);
    }

    @Test
    void getEntAddressFromMnemonic_byNetwork() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String address0 = "addr_test1vzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvssp226k";
        String address1 = "addr_test1vp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg99fsju";

        Account account = new Account(Networks.testnet(), phrase24W);

        assertEquals(address0, new Account(Networks.testnet(), phrase24W, 0).enterpriseAddress());
        assertEquals(address1, new Account(Networks.testnet(), phrase24W, 1).enterpriseAddress());
        assertNotNull(account.mnemonic());
    }

    @Test
    void getChangeAddressFromMnemonic_whenMainnet() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String expectedChangeAddress = "addr1q9qwpvc7946mqvl0mwwhqgmh6w4a6335mkuypjyg9fd5elsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps83n54g";

        Account account = new Account(Networks.mainnet(), phrase24W);
        String changeAddress0 = account.changeAddress();

        Account account1 = new Account(Networks.mainnet(), phrase24W, 1);
        String changeAddress1 = account1.changeAddress();

        assertEquals(expectedChangeAddress, changeAddress0);
        assertEquals(expectedChangeAddress, changeAddress1); //Change address is same
    }

    @Test
    void getStakeAddressFromMnemonic_whenTestnet() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String expectedRewardAddress = "stake_test1uq06d3cktqn4z9tv8rr9723fvrxdnh44an9tjvjftw6krscamyncv";

        Account account = new Account(Networks.testnet(), phrase24W);
        String rewardAddress = account.stakeAddress();

        assertEquals(expectedRewardAddress, rewardAddress);
    }

    @Test
    void getStakeAddressFromMnemonic_whenMainnet() {
        String phrase24W = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        String expectedRewardAddress = "stake1u9xeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697quawequ";

        Account account = new Account(Networks.mainnet(), phrase24W);
        String rewardAddress = account.stakeAddress();

        assertEquals(expectedRewardAddress, rewardAddress);
    }

    @Test
    void getAddressFromInvalidMnemonic() {
        String phrase = "invalid pass goose lava verb buzz service consider execute goose abstract fresh endless cruise layer belt immense clay glimpse install garage elegant cricket make";

        assertThrows(AddressRuntimeException.class, () -> {
            Account account = new Account(Networks.testnet(), phrase, 0);
        });
    }

    @Test
    void getEntAddressFromInvalidMnemonic() {
        String phrase = "invalid pass goose lava verb buzz service consider execute goose abstract fresh endless cruise layer belt immense clay glimpse install garage elegant cricket make";

        assertThrows(AddressRuntimeException.class, () -> {
            Account account = new Account(Networks.testnet(), phrase);
        });
    }

    @Test
    void testGetPrivateKeyBytes() {
        String phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";

        Account account = new Account(Networks.testnet(), phrase, 0);
        assertEquals(64, account.privateKeyBytes().length);
    }

    @Test
    void testGetPublicKey() {
        String phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";

        Account account = new Account(Networks.testnet(), phrase, 0);
        assertTrue(account.publicKeyBytes().length == 32);
    }

    @Test
    void testSign() throws CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        TransactionInput txnInput = new TransactionInput();

        txnInput.setTransactionId("dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d");
        txnInput.setIndex(1);

        List<TransactionInput> inputList = new ArrayList<>();
        inputList.add(txnInput);
        txnBody.setInputs(inputList);

        //Total : 994632035
        TransactionOutput txnOutput =  new TransactionOutput();
        txnOutput.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput.setValue(new Value(new BigInteger("5000000"), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        changeOutput.setValue(new Value(new BigInteger("989264070"), null));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput);
        outputs.add(changeOutput);

        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger("367965"));
        txnBody.setTtl(26194586);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);

        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        Account account = new Account(Networks.testnet(), phrase24W);

        Transaction signedTxn = account.sign(transaction);

        String signedTxnHex = signedTxn.serializeToHex();

        String expectdSignTxn = "84a400d9010281825820dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d010182825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f81a3af6f8c6021a00059d5d031a018fb29aa100d90102818258204d88ec934e586062c12302e7a5d40fb357035c1142730d8b5b172607d45c2f9f5840528517293953ab4d7239dc290f4dc847d72e52aea3ab7dbd0bd029db9c6fbffa1c98c1a6299e4591258a170857be1838987168469bb15efd69a7a35ae7ad8108f5f6";
        assertEquals(expectdSignTxn, signedTxnHex);
    }

    @Test
    public void testByronAddressToBytes() throws AddressExcepion {
        String byronAddress = "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK";
        byte[] bytes = AddressUtil.addressToBytes(byronAddress);
        assertNotEquals(0, bytes.length);
    }

    @Test
    public void testShelleyAddressToBytes() throws AddressExcepion {
        String shelleyAddr = "addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v";
        byte[] bytes = AddressUtil.addressToBytes(shelleyAddr);
        assertNotEquals(0, bytes.length);
    }

    @Test
    public void testInvalidAddressToBytes() throws AddressExcepion {
        assertThrows(Exception.class, () -> {
            String shelleyAddr = "invalid_address";
            byte[] bytes = AddressUtil.addressToBytes(shelleyAddr);
        });
    }

    @Test
    public void testBytesToByronAddress() throws AddressExcepion {
        String byronAddress = "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK";
        byte[] bytes = AddressUtil.addressToBytes(byronAddress);

        String newAddr = AddressUtil.bytesToBase58Address(bytes);
        assertEquals(byronAddress, newAddr);
    }

    @Test
    public void testBytesToBech32Address() throws AddressExcepion {
        String shelleyAddr = "addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v";
        byte[] bytes = AddressUtil.addressToBytes(shelleyAddr);

        String newAddr = AddressUtil.bytesToAddress(bytes);
        assertEquals(shelleyAddr, newAddr);
    }

    @Test
    public void testBech32PrivateKey() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        Account account = new Account(Networks.testnet(), phrase24W);

        String bech32PrvKey = account.getBech32PrivateKey();
        System.out.println(bech32PrvKey);

        String expected = "xprv1fqxc29k9p4uz5gy4nax8efjaqydr5zz9z33n2lqfkqvz5xaapa8nmr0jgp26np8t65639yjnzf0d690qn2wwacmhyq2fj70sy2n3sr8t94x6l2ecqxllyvzywnjejz5c7cxzvswslzd9g5c95ug2vw7tzgstdx0p";
        assertThat(bech32PrvKey).isEqualTo(expected);
    }

    @Test
    public void testGetBaseAddress() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        Account account = new Account(Networks.testnet(), phrase24W);

        Address address = account.getBaseAddress();

        assertThat(address.toBech32()).isEqualTo("addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z");
        assertThat(HexUtil.encodeHexString(address.getBytes())).isEqualTo("00a1deeb02cc062706b41b689fa0fa9d2f375c5486560491712cfab7321fa6c716582751156c38c65f2a2960ccd9deb5eccab932495bb561c3");
    }

    @Test
    public void testGetEnterpriseAddress() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        Account account = new Account(Networks.testnet(), phrase24W);

        Address address = account.getEnterpriseAddress();

        assertThat(address.toBech32()).isEqualTo("addr_test1vzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvssp226k");
        assertThat(HexUtil.encodeHexString(address.getBytes())).isEqualTo("60a1deeb02cc062706b41b689fa0fa9d2f375c5486560491712cfab732");
    }

    @Test
    public void testBaseAddressAsBase16() {
        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        Account account = new Account(Networks.testnet(), phrase24W);

        String address = account.baseAddressAsBase16();

        assertThat(address).isEqualTo("00a1deeb02cc062706b41b689fa0fa9d2f375c5486560491712cfab7321fa6c716582751156c38c65f2a2960ccd9deb5eccab932495bb561c3");
    }

    @Test
    void testGPublicKey() {
        Account account = new Account(2);
        HdKeyPair hdKeyPair = account.hdKeyPair();

        byte[] derivePubKey = HdKeyGenerator.getPublicKey(hdKeyPair.getPrivateKey().getKeyData());

        assertThat(derivePubKey).isEqualTo(hdKeyPair.getPublicKey().getKeyData());
    }

    @Test
    void testPubKeyFromParentPubKey() {
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";

        Account account = new Account(mnemonicPhrase,2);
        HdKeyPair hdKeyPair = account.hdKeyPair();
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair childHdKeyPair = hdKeyGenerator.getChildKeyPair(hdKeyPair, 1, false);

        HdPublicKey publicKey = hdKeyGenerator.getChildPublicKey(hdKeyPair.getPublicKey(), 1);

        assertThat(publicKey.getKeyData()).isEqualTo(childHdKeyPair.getPublicKey().getKeyData());
    }

    @Test
    void testDRepId() {
        String mnemonicPhrase = "punch smile segment tumble sauce  oak mosquito clay service switch still federal chicken economy saddle galaxy reunion trust dinosaur demise illegal pupil trip lyrics";

        Account account = new Account(Networks.testnet(), mnemonicPhrase);
        String drepId = account.drepId();

        assertThat(drepId).isEqualTo("drep1yg7a8fmpshh5z4tfhjg7mrzjcy58hsghhqu2wu7t0zmva7qhqjauh");

        String legacyDRepId = account.legacyDRepId();
        assertThat(legacyDRepId).isEqualTo("drep18hf6wcv9aaq426duj8kcc5kp9pauz9ac8znh8jmckm80sf7fetw");
    }
}
