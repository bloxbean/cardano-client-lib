package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
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

    @Test
    void testAccountFromRootKey() {
        String rootKey = "xprv1frqqvtmax6a5lqv5h6e8vt2wxglasnweglnap8dclz69fd62zp2kqccn08nmjah5rct9zvuh3mx4dln9z984hf42474q6jp2frn3ahkxxaau9y2yfvrr7ex4nw24g37flvarqfhy87g99kp20yknqn7kgs04h87k";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;
        Account account = Account.createFromRootKey(Networks.testnet(), rootKeyBytes);

        //expected
        //tragic movie pulp rely quick damage spoil case bubble forget banana bomb pilot fresh trumpet learn basic melt curtain defy erode soccer race oil

        assertThat(account.baseAddress()).isEqualTo("addr_test1qzm0439fe55aynh58qcn4jnh4mwuqwr5n5fez7j0hck9ds8j3nmg5pkqfur4gyupppuu82r83s5eheewzmf6fwlzfz7qzsp6rc");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qqkqwker9785sna30vmjggynjxzce6sdg2th7w3w0sgfvr8j3nmg5pkqfur4gyupppuu82r83s5eheewzmf6fwlzfz7q5r7hzu");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1uregea52qmqy7p65zwqss7wr4pncc2vmuuhpd5ayh03y30q7ag6w7");
        assertThat(account.drepId()).isEqualTo("drep1y2vwwy93jq0gttdsrux3eum43cl2c4umzxd04pcmqmdvgwg8rnc4h");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1zg6ed528z5xc8x9wnnjnyw5gu26a69j8es2cff6nq6799vszr7ukm");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qth000uamffyddnlqlsjw55f8gthy4nr96rtdrvkvuhd5gsjxuyjw");

    }

    @Test
    void testGetRootKeyPair() {
        var seedPhrase =
                "tragic movie pulp rely quick damage spoil case bubble forget banana bomb pilot fresh trumpet learn basic melt curtain defy erode soccer race oil";
        HdKeyPair rootKeyPair = new Account(Networks.testnet(), seedPhrase).getRootKeyPair().get();
        String rootPvtKeyBech32 = rootKeyPair.getPrivateKey().toBech32();

        System.out.println(HexUtil.encodeHexString(rootKeyPair.getPrivateKey().getBytes()));

        assertThat(rootPvtKeyBech32).isEqualTo("xprv1frqqvtmax6a5lqv5h6e8vt2wxglasnweglnap8dclz69fd62zp2kqccn08nmjah5rct9zvuh3mx4dln9z984hf42474q6jp2frn3ahkxxaau9y2yfvrr7ex4nw24g37flvarqfhy87g99kp20yknqn7kgs04h87k");
    }

    @Test
    void testAccountFromRootKey_128Bytes_throwsException() {
        //Last 32 bytes in 128 bytes array are arbitary bytes
        String rootKey128Bytes = "48c0062f7d36bb4f8194beb2762d4e323fd84dd947e7d09db8f8b454b74a105560631379e7b976f41e165133978ecd56fe65114f5ba6aaafaa0d482a48e71edec6377bc291444b063f64d59b955447c9fb3a3026e43f9052d82a792d304fd644c333ef7429361bdb1414e15e054f6654bce419d26057d0e38d76993f9c3ab71f";
        byte[] rootKey = HexUtil.decodeHexString(rootKey128Bytes);

        assertThrows(Exception.class, () -> {
            Account.createFromRootKey(Networks.testnet(), rootKey);
        });
    }

    @Test
    void testAccountFromAccountKey_0() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        //at account=0
        String accountPrvKey = "acct_xsk14zau3uj79pxh2wplfnezeetj3ms5wfgvyltg3jap0ch5cuswq3qe39l4aty2wjgtyzagzc8squ0hz6hrej6ypqdrj4yhxynapsf462ypgv3clpf74q56k6r32847a4cp9dlx6n8ew8hyqdv6ydv5q8yt9vhn8ktv";
        byte[] accountPrvKeyBytes = Bech32.decode(accountPrvKey).data;

        System.out.println(accountPrvKeyBytes.length);

        Account account = Account.createFromAccountKey(Networks.testnet(), accountPrvKeyBytes);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qq7x3pklemucwtw6qcym6trkcfmenslhnsq7e8cag7h82507mkje2hyjgc8zr00z835as0kw5hwa48h3vnrctjpu4a8qy8lf5r");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qr430yp4r7mgsj5t34pgp2zcv37w8arj62gyqtdayheejz87mkje2hyjgc8zr00z835as0kw5hwa48h3vnrctjpu4a8qy27g5x");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1urldmfv4tjfyvr3phh3rc6wc8m82thw6nmckf3u9eq727ns3kwf9u");
        assertThat(account.drepId()).isEqualTo("drep1y2zjw9adazlmychc6wlz4k8qa8g5jcjqwujg0jws9r6v9egvasg8r");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1ztslrgxse5awd9yx9csqrcmystzw6rd88tva4tw7lqkjrtc6d4ezh");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qgmfvk4g7vfquys52cdrx59ez948q337jhp5ycde3umlprqulcyff");
    }

    @Test
    void testAccountFromRootKey_0() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        String rootKey = "root_xsk1xrvg8kfpdlaluwstt0twcajgavqcgkczmav6lffvgfmrxeqwq3qer3rasrjdj9f663xa98xcu4a28zuv5cks5lytdvfezn49ycrndz2mptat9v0t5eafsdj9rpe4lcxndvys0v6qahq8v0flv9ycpav8ks46k8xh";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;

        Account account = Account.createFromRootKey(Networks.testnet(), rootKeyBytes);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qq7x3pklemucwtw6qcym6trkcfmenslhnsq7e8cag7h82507mkje2hyjgc8zr00z835as0kw5hwa48h3vnrctjpu4a8qy8lf5r");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qr430yp4r7mgsj5t34pgp2zcv37w8arj62gyqtdayheejz87mkje2hyjgc8zr00z835as0kw5hwa48h3vnrctjpu4a8qy27g5x");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1urldmfv4tjfyvr3phh3rc6wc8m82thw6nmckf3u9eq727ns3kwf9u");
        assertThat(account.drepId()).isEqualTo("drep1y2zjw9adazlmychc6wlz4k8qa8g5jcjqwujg0jws9r6v9egvasg8r");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1ztslrgxse5awd9yx9csqrcmystzw6rd88tva4tw7lqkjrtc6d4ezh");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qgmfvk4g7vfquys52cdrx59ez948q337jhp5ycde3umlprqulcyff");
    }

    @Test
    void testAccountFromAccountKey_2() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        //at account=2 1852H/1815H/2H
        String accountPrvKey = "acct_xsk1tz659rq7zjytmgycce72k2xsswzv5uy6rzaydam2au6ksagwq3qh4pykgcaw6u7swpqxgyf2sugc2vrjjqzrpnjldwxk3gn7h4q8h38s83nlhc5y33ptlqwqhvg3k2hxyte9avdav9jvu9qz6j6tgsnuugkv3cea";
        byte[] accountPrvKeyBytes = Bech32.decode(accountPrvKey).data;

        System.out.println(accountPrvKeyBytes.length);

        Account account = Account.createFromAccountKey(Networks.testnet(), accountPrvKeyBytes);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qpyf7633hxe5t5lwr20dre9xy54nuhl4qf53rvpcs3geq5mgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sfpmuak");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qqphrf9wtwwhghevuy0gd95cldmdzn3gyrganlh7g8d8hvrgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7s6xvl48");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1up5zq38z7v9wy2xcxu4c59ktxlyc4y6sdw6rg75ht2daz0guncpsl");
        assertThat(account.drepId()).isEqualTo("drep1ygmmj4nqjzvaa39qxj6w7pxpx4u62f8lvnu4xzjwjk8segg56qful");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1zftrxuz0u938d8mu5ndawz8yv6rkurfq8flrus6mcr32fhspk4ju3");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qtx827hr45paddz5h6w7k0rg40vpc79262dff8r42rlwxgq4jj6dg");
    }

    @Test
    void testAccountFromRootKey_2() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        String rootKey = "root_xsk1xrvg8kfpdlaluwstt0twcajgavqcgkczmav6lffvgfmrxeqwq3qer3rasrjdj9f663xa98xcu4a28zuv5cks5lytdvfezn49ycrndz2mptat9v0t5eafsdj9rpe4lcxndvys0v6qahq8v0flv9ycpav8ks46k8xh";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;

        Account account2 = Account.createFromRootKey(Networks.testnet(), rootKeyBytes, 2, 0);
        Account account28 = Account.createFromRootKey(Networks.testnet(), rootKeyBytes, 2, 8);

        assertThat(account2.baseAddress()).isEqualTo("addr_test1qpyf7633hxe5t5lwr20dre9xy54nuhl4qf53rvpcs3geq5mgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sfpmuak");
        assertThat(account2.changeAddress()).isEqualTo("addr_test1qqphrf9wtwwhghevuy0gd95cldmdzn3gyrganlh7g8d8hvrgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7s6xvl48");
        assertThat(account2.stakeAddress()).isEqualTo("stake_test1up5zq38z7v9wy2xcxu4c59ktxlyc4y6sdw6rg75ht2daz0guncpsl");
        assertThat(account2.drepId()).isEqualTo("drep1ygmmj4nqjzvaa39qxj6w7pxpx4u62f8lvnu4xzjwjk8segg56qful");
        assertThat(account2.committeeColdKey().id()).isEqualTo("cc_cold1zftrxuz0u938d8mu5ndawz8yv6rkurfq8flrus6mcr32fhspk4ju3");
        assertThat(account2.committeeHotKey().id()).isEqualTo("cc_hot1qtx827hr45paddz5h6w7k0rg40vpc79262dff8r42rlwxgq4jj6dg");

        assertThat(account28.baseAddress()).isEqualTo("addr_test1qqacfd33e3qa20vqmv00qx6xftxhcqwhlr905l4rchf5j0rgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sa0sjfq");
    }

    @Test
    void testAccountFromRootKey_derivationPath_2() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        String rootKey = "root_xsk1xrvg8kfpdlaluwstt0twcajgavqcgkczmav6lffvgfmrxeqwq3qer3rasrjdj9f663xa98xcu4a28zuv5cks5lytdvfezn49ycrndz2mptat9v0t5eafsdj9rpe4lcxndvys0v6qahq8v0flv9ycpav8ks46k8xh";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;

        var path0 = DerivationPath.createExternalAddressDerivationPathForAccount(2);
        var path8 = DerivationPath.createExternalAddressDerivationPathForAccount(2);
        path8.getIndex().setValue(8);

        Account account2 = Account.createFromRootKey(Networks.testnet(), rootKeyBytes, path0);
        Account account28 = Account.createFromRootKey(Networks.testnet(), rootKeyBytes, path8);

        assertThat(account2.baseAddress()).isEqualTo("addr_test1qpyf7633hxe5t5lwr20dre9xy54nuhl4qf53rvpcs3geq5mgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sfpmuak");
        assertThat(account2.changeAddress()).isEqualTo("addr_test1qqphrf9wtwwhghevuy0gd95cldmdzn3gyrganlh7g8d8hvrgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7s6xvl48");
        assertThat(account2.stakeAddress()).isEqualTo("stake_test1up5zq38z7v9wy2xcxu4c59ktxlyc4y6sdw6rg75ht2daz0guncpsl");
        assertThat(account2.drepId()).isEqualTo("drep1ygmmj4nqjzvaa39qxj6w7pxpx4u62f8lvnu4xzjwjk8segg56qful");
        assertThat(account2.committeeColdKey().id()).isEqualTo("cc_cold1zftrxuz0u938d8mu5ndawz8yv6rkurfq8flrus6mcr32fhspk4ju3");
        assertThat(account2.committeeHotKey().id()).isEqualTo("cc_hot1qtx827hr45paddz5h6w7k0rg40vpc79262dff8r42rlwxgq4jj6dg");

        assertThat(account28.baseAddress()).isEqualTo("addr_test1qqacfd33e3qa20vqmv00qx6xftxhcqwhlr905l4rchf5j0rgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sa0sjfq");
    }

    @Test
    void testAccountFromMnemonic_15words_0() {
        String mnemonic = "top exact spice seed cloud birth orient bracket happy cat section girl such outside elder";

        Account account = Account.createFromMnemonic(Networks.testnet(), mnemonic);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qqy4c938alydc5sa4w46dnprn8f0hrjcd6jrhfte8rfvgewc6r9xxc78l7x6h8tjvm8emnlewwxk2a6tae88h50etkcqkx92yf");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qredvj90u0arndxjeggntqrynn7l5waszuy7g47qkd0yhtwc6r9xxc78l7x6h8tjvm8emnlewwxk2a6tae88h50etkcqxhe48d");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1urvdpjnrv0rllrdtn4exdnuaeluh8rt9wa97unnm68u4mvq00d2tz");
        assertThat(account.drepId()).isEqualTo("drep1y2uys0eg8cvvszyxua6g8wz04jpgscgjelym9pvwthzg25q0ssk4v");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1zgz6epprs27zmptt5ermfd89s8uvp7tzzm744sw7hkpedtgywh6r9");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qtlsh6swxn9dc30n2lx0u4qpu0cq0czrlccrueduk590dycmegtnc");
    }

    @Test
    void testAccountFromMnemonic_15words_acc4_index3() {
        String mnemonic = "top exact spice seed cloud birth orient bracket happy cat section girl such outside elder";

        Account account = Account.createFromMnemonic(Networks.testnet(), mnemonic, 4, 3);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qz3cw2uuwjwhdjwyf32pre79kca5mf722nm09a6welje4edx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fshq2mdz");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qq8x3y83vfsx6zrcnuyseact0z85fyev6vjhrk5nfxzm57dx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fsf894c3");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1uzn8nv3yj7yruk7vrskg5q4wfylmdfvw43za7y6evtwmg5c47c0hf");
        assertThat(account.drepId()).isEqualTo("drep1yt07pz022vfqzwr40jrch8pwcd09g2s2nqqsffdjag9d33g2czftn");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1ztw27g74fpg4n5wl556c4spzx8n5gz6njtf8lqcrehgvqaswaasts");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qgjg5c946hgkjh5mvpw8x4q7v9m0zykjvw0wrq5pfm04tvqqh35qa");
    }

    @Test
    void testAccountFromRootKey_15words_acc4_index3() {

        //Original mnemonic
        //top exact spice seed cloud birth orient bracket happy cat section girl such outside elder

        String rootKey = "root_xsk1zza6z52v8gelnaqdhuny3ywlccud5dtm8rvvyem4utnfwzcaa9pspsmdm99qfpy2qz7sw9sts59mrkegmdqyjen5ykm4z3ccyrkn8g5mm0qw35arvwxclfh6tj3s4x7t2q85wenvppjpxckcxgnf8vd80ug0l6rw";

        Account account = Account.createFromRootKey(Networks.testnet(), Bech32.decode(rootKey).data, 4, 3);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qz3cw2uuwjwhdjwyf32pre79kca5mf722nm09a6welje4edx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fshq2mdz");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qq8x3y83vfsx6zrcnuyseact0z85fyev6vjhrk5nfxzm57dx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fsf894c3");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1uzn8nv3yj7yruk7vrskg5q4wfylmdfvw43za7y6evtwmg5c47c0hf");
        assertThat(account.drepId()).isEqualTo("drep1yt07pz022vfqzwr40jrch8pwcd09g2s2nqqsffdjag9d33g2czftn");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1ztw27g74fpg4n5wl556c4spzx8n5gz6njtf8lqcrehgvqaswaasts");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qgjg5c946hgkjh5mvpw8x4q7v9m0zykjvw0wrq5pfm04tvqqh35qa");
    }

    @Test
    void testAccountFromAccountKey_15words_acc4_index3() {
        //Original mnemonic
        //top exact spice seed cloud birth orient bracket happy cat section girl such outside elder

        String accountKey = "acct_xsk1azc6gn5zkdprp4gkapmhdckykphjl62rm9224699ut5z6xcaa9p4hv5hmjfgcrzk72tnsqh6dw0njekdjpsv8nv5h5hk6lpd4ag62zenwhzqs205kfurd7kgs8fm5gx4l4j8htutwj060kyp5y5kgw55qc8lsltd";

        Account account = Account.createFromAccountKey(Networks.testnet(), Bech32.decode(accountKey).data, 4, 3);

        assertThat(account.baseAddress()).isEqualTo("addr_test1qz3cw2uuwjwhdjwyf32pre79kca5mf722nm09a6welje4edx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fshq2mdz");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qq8x3y83vfsx6zrcnuyseact0z85fyev6vjhrk5nfxzm57dx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fsf894c3");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1uzn8nv3yj7yruk7vrskg5q4wfylmdfvw43za7y6evtwmg5c47c0hf");
        assertThat(account.drepId()).isEqualTo("drep1yt07pz022vfqzwr40jrch8pwcd09g2s2nqqsffdjag9d33g2czftn");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1ztw27g74fpg4n5wl556c4spzx8n5gz6njtf8lqcrehgvqaswaasts");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qgjg5c946hgkjh5mvpw8x4q7v9m0zykjvw0wrq5pfm04tvqqh35qa");
    }

    @Test
    void testAccountFromAccountKey_128bytes_throwsException() {
        //Added random 32 bytes at the end to test with a 128 bytes key
        String accountKey = "e8b1a44e82b34230d516e87776e2c4b06f2fe943d954aae8a5e2e82d1b1de9435bb297dc928c0c56f2973802fa6b9f3966cd9060c3cd94bd2f6d7c2daf51a50b3375c40829f4b27836fac881d3ba20d5fd647baf8b749fa7d881a129643a9406c333ef7429361bdb1414e15e054f6654bce419d26057d0e38d76993f9c3ab71f";
        System.out.println(HexUtil.decodeHexString(accountKey));

        assertThrows(Exception.class, () -> {
            Account.createFromAccountKey(Networks.testnet(), HexUtil.decodeHexString(accountKey), 4, 3);
        });
    }
}
