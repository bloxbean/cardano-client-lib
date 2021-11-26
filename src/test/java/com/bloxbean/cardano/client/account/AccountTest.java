package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AccountTest {

    static {
        String folderPrefix = Platform.getNativeLibraryResourcePrefix();
        String currentDir = System.getProperty("user.dir");
        String libPath = (currentDir + "/native/" + folderPrefix).replace('/', File.separatorChar);
        System.setProperty("jna.library.path", libPath);

        System.out.println(libPath);
    }

    @BeforeEach
    void setUp() {

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
        assertEquals(96, account.privateKeyBytes().length);
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

        String signedTxn = account.sign(transaction);

        String expectdSignTxn = "84a40081825820dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d010182825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f81a3af6f8c6021a00059d5d031a018fb29aa100818258204d88ec934e586062c12302e7a5d40fb357035c1142730d8b5b172607d45c2f9f5840e627ac36d4699bb52611bfb49ebc772efe85a7315e15dc8aeae83696fd5d27b7d9c9635ba0bf1b091ad5dde1330117cb206427dfaf9adfe4b64ba574a9f30e04f5f6";
        assertEquals(expectdSignTxn, signedTxn);
    }

    @Test
    public void testByronAddressToBytes() throws AddressExcepion {
        String byronAddress = "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK";
        byte[] bytes = Account.toBytes(byronAddress);
        assertNotEquals(0, bytes.length);
    }

    @Test
    public void testShelleyAddressToBytes() throws AddressExcepion {
        String shelleyAddr = "addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v";
        byte[] bytes = Account.toBytes(shelleyAddr);
        assertNotEquals(0, bytes.length);
    }

    @Test
    public void testInvalidAddressToBytes() throws AddressExcepion {
        assertThrows(AddressExcepion.class, () -> {
            String shelleyAddr = "invalid_address";
            byte[] bytes = Account.toBytes(shelleyAddr);
        });
    }

    @Test
    public void testBytesToByronAddress() throws AddressExcepion {
        String byronAddress = "DdzFFzCqrhszg6cqZvDhEwUX7cZyNzdycAVpm4Uo2vjKMgTLrVqiVKi3MBt2tFAtDe7NkptK6TAhVkiYzhavmKV5hE79CWwJnPCJTREK";
        byte[] bytes = Account.toBytes(byronAddress);

        String newAddr = Account.bytesToBase58Address(bytes);
        assertEquals(byronAddress, newAddr);
    }

    @Test
    public void testBytesToBech32Address() throws AddressExcepion {
        String shelleyAddr = "addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v";
        byte[] bytes = Account.toBytes(shelleyAddr);

        String newAddr = Account.bytesToBech32(bytes);
        assertEquals(shelleyAddr, newAddr);
    }
}
