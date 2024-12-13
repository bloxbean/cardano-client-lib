package com.bloxbean.cardano.client.address;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressServiceTest {

    @Test
    void getBaseAddress_whenIndex0_testnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath paymentDerivationPath = DerivationPath.createExternalAddressDerivationPath(0);
        HdKeyPair paymentHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, paymentDerivationPath);

        //stake
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address baseAddress = AddressService.getInstance().getBaseAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.testnet());

        System.out.println(baseAddress.toBech32());

        String expected = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        assertThat(baseAddress.toBech32()).isEqualTo(expected);
    }

    @Test
    void getBaseAddress_whenIndex0_mainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        Account account = new Account(mnemonic);
        System.out.println(account.baseAddress());

        DerivationPath paymentDerivationPath = DerivationPath.createExternalAddressDerivationPath(0);
        HdKeyPair paymentHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, paymentDerivationPath);

        //stake
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address baseAddress = AddressService.getInstance().getBaseAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.mainnet());

        System.out.println(baseAddress.toBech32());

        String expected = "addr1qxx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqrzmetm";
        assertThat(baseAddress.toBech32()).isEqualTo(expected);
    }

    @Test
    void getBaseAddress_whenIndex1_mainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath paymentDerivationPath = DerivationPath.createExternalAddressDerivationPath(1);
        HdKeyPair paymentHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, paymentDerivationPath);

        //stake
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address baseAddress = AddressService.getInstance().getBaseAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.mainnet());

        System.out.println(baseAddress.toBech32());

        String expected = "addr1qyvhg6pxu3dgl3jxehkmcnz2kcr4th3rc4huat7pxmr0ttzdjs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqxvm6yt";
        assertThat(baseAddress.toBech32()).isEqualTo(expected);
    }

    @Test
    void getBaseAddress_whenIndex1_mainnet_internalAddress() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath changeAddressDerivationPath = DerivationPath.createInternalAddressDerivationPath(0);
        HdKeyPair paymentHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, changeAddressDerivationPath);

        //stake - Role 2
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address baseAddress = AddressService.getInstance().getBaseAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.mainnet());

        System.out.println(baseAddress.toBech32());

        String expected = "addr1q8vayr52ketz2rtsmkswk6tf4llylwt6rjjtm74wvqlwe56djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqv5ay42";
        assertThat(baseAddress.toBech32()).isEqualTo(expected);
    }

    @Test
    void getStakeAddress_whenMainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        //stake - Role 2
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address stakeAddress = AddressService.getInstance().getRewardAddress(stakeHdKeyPair.getPublicKey(), Networks.mainnet());

        System.out.println(stakeAddress.toBech32());

        String expected = "stake1u9xeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697quawequ";
        assertThat(stakeAddress.toBech32()).isEqualTo(expected);
    }

    @Test
    void getPaymentAddress_fromPaymentVerificationKey() {
        String paymentVkey = "addr_xvk1r30n0pv6d40kzzl4e6xje2y7c446gw2x9sgnms3vv62tx264tf5n9lxnuxqc5xpqlg30dtlq0tf0fav4kafsge6u24x296vg85l399cx2uv4k";
        byte[] paymentVKeyBytes = Bech32.decode(paymentVkey).data;

        HdPublicKey hdPublicKey = HdPublicKey.fromBytes(paymentVKeyBytes);
        Address address = AddressService.getInstance().getEntAddress(hdPublicKey, Networks.testnet());

        assertThat(address.getAddress()).isEqualTo("addr_test1vp8w93j8pappvvu8tcajysvr65ph8wt5yg5u4s5u2j4e80ggxcu4e");
    }

    @Nested
    class ScriptAddresses {

        @Test
        void getScriptEntAddress_whenNativeScript() throws CborSerializationException {
            ScriptAtLeast scriptAtLeast = getMultisigScript();

            Address address = AddressService.getInstance().getEntAddress(scriptAtLeast, Networks.testnet());
            System.out.println(address.toBech32());

            assertThat(address.toBech32()).isEqualTo("addr_test1wzchaw4vxmmpws44ffh99eqzmlg6wr3swg36pqug8xn20ygxgqher");
        }

        @Test
        void getScriptEntAddress_whenPlutusScript() throws CborSerializationException {
            PlutusV1Script plutusScript = PlutusV1Script.builder()
                    .type("PlutusScriptV1")
                    .cborHex("4e4d01000033222220051200120011")
                    .build();

            Address address = AddressService.getInstance().getEntAddress(plutusScript, Networks.testnet());
            System.out.println(address.toBech32());

            assertThat(address.toBech32()).isEqualTo("addr_test1wpnlxv2xv9a9ucvnvzqakwepzl9ltx7jzgm53av2e9ncv4sysemm8");
        }

        @Test
        void getScriptStakeAddress_whenNativeScript() throws CborSerializationException {
            ScriptAtLeast scriptAtLeast = getMultisigScript();

            Address address = AddressService.getInstance().getRewardAddress(scriptAtLeast, Networks.testnet());
            System.out.println(address.toBech32());

            assertThat(address.toBech32()).isEqualTo("stake_test17zchaw4vxmmpws44ffh99eqzmlg6wr3swg36pqug8xn20ygxq70wf");
        }
    }

    @Nested
    class TestVectors {
        String vkey = "addr_vk1w0l2sr2zgfm26ztc6nl9xy8ghsk5sh6ldwemlpmp9xylzy4dtf7st80zhd";
        String stakeKey = "stake_vk1px4j0r2fk7ux5p23shz8f3y5y2qam7s954rgf3lg5merqcj6aetsft99wu";
        String scriptKey = "script1cda3khwqv60360rp5m7akt50m6ttapacs8rqhn5w342z7r35m37";

        HdPublicKey publicKey;
        HdPublicKey stakePubKey;
        Pointer pointer = new Pointer(2498243, 27, 3);

        Script script;

        @BeforeEach
        void setup() {
            publicKey = new HdPublicKey();
            publicKey.setKeyData(Bech32.decode(vkey).data);

            stakePubKey = new HdPublicKey();
            stakePubKey.setKeyData(Bech32.decode(stakeKey).data);

            script = new Script() {

                @Override
                public DataItem serializeAsDataItem() throws CborSerializationException {
                    return null;
                }

                @Override
                public byte[] serializeScriptBody() {
                    return new byte[0];
                }

                @Override
                public byte[] getScriptHash() { //Provide a dummy impl for script hash
                    byte[] serializedBytes = Bech32.decode(scriptKey).data;
                    return serializedBytes;
                }

                @Override
                public String getPolicyId() throws CborSerializationException {
                    return null;
                }

                @Override
                public byte[] scriptRefBytes() throws CborSerializationException {
                    return new byte[0];
                }

                @Override
                public byte[] getScriptTypeBytes() {
                    return new byte[0];
                }

                @Override
                public int getScriptType() {
                    return 0;
                }
            };
        }

        @Test
        void getBaseAddress_type00_mainnet() {
            Address address = AddressService.getInstance().getBaseAddress(publicKey, stakePubKey, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x");
        }

        @Test
        void getBaseAddress_type00_testnet() {
            Address address = AddressService.getInstance().getBaseAddress(publicKey, stakePubKey, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs68faae");
        }

        @Test
        void getBaseAddress_type01_mainnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getBaseAddress(script, stakePubKey, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1z8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gten0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs9yc0hh");
        }

        @Test
        void getBaseAddress_type01_testnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getBaseAddress(script, stakePubKey, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1zrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gten0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgsxj90mg");
        }

        @Test
        void getBaseAddress_type02_mainnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getBaseAddress(publicKey, script, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1yx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs2z78ve");
        }

        @Test
        void getBaseAddress_type02_testnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getBaseAddress(publicKey, script, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1yz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shsf5r8qx");
        }

        @Test
        void getBaseAddress_type03_mainnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getBaseAddress(script, script, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1x8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gt7r0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shskhj42g");
        }

        @Test
        void getBaseAddress_type03_testnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getBaseAddress(script, script, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1xrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gt7r0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs4p04xh");
        }

        @Test
        void getPointerAddress_type04_mainnet() {
            Address address = AddressService.getInstance().getPointerAddress(publicKey, pointer, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1gx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5pnz75xxcrzqf96k");
        }

        @Test
        void getPointerAddress_type04_testnet() throws CborSerializationException {
            Address address = AddressService.getInstance().getPointerAddress(publicKey, pointer, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1gz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5pnz75xxcrdw5vky");
        }

        @Test
        void getPointerAddress_type05_mainnet_script() throws CborSerializationException {
            Address address = AddressService.getInstance().getPointerAddress(script, pointer, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr128phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcrtw79hu");
        }

        @Test
        void getPointerAddress_type05_testnet_script() throws CborSerializationException {
            Address address = AddressService.getInstance().getPointerAddress(script, pointer, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test12rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcryqrvmw");
        }

        @Test
        void getPointerAddress_fromPublicKey() {
            byte[] entropy = new byte[] {(byte) 0xdf, (byte)0x9e, (byte)0xd2, (byte)0x5e, (byte)0xd1, (byte)0x46, (byte)0xbf, 0x43, 0x33, 0x6a, 0x5d, 0x7c, (byte)0xf7, 0x39, 0x59, (byte)0x94};
            HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromEntropy(entropy, DerivationPath.createExternalAddressDerivationPath());

            Pointer pointer = Pointer.builder()
                    .slot(1).txIndex(2).certIndex(3)
                    .build();
            Address address = AddressService.getInstance().getPointerAddress(hdKeyPair.getPublicKey(), pointer, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1gz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerspqgpsqe70et");

            Pointer  pointer1 = Pointer.builder()
                    .slot(24157).txIndex(177).certIndex(42)
                    .build();
            Address mainnetAddress = AddressService.getInstance().getPointerAddress(hdKeyPair.getPublicKey(), pointer1, Networks.mainnet());
            assertThat(mainnetAddress.toBech32()).isEqualTo("addr1gx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5ph3wczvf2w8lunk");
        }


        @Test
        void getEntAddress_type06_mainnet() {
            Address address = AddressService.getInstance().getEntAddress(publicKey, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1vx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzers66hrl8");
        }

        @Test
        void getEntAddress_type06_testnet() {
            Address address = AddressService.getInstance().getEntAddress(publicKey, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1vz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerspjrlsz");
        }

        @Test
        void getEntAddress_type06_mainnet_script() throws CborSerializationException {
            Address address = AddressService.getInstance().getEntAddress(script, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("addr1w8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcyjy7wx");
        }

        @Test
        void getEntAddress_type06_testnet_script() throws CborSerializationException {
            Address address = AddressService.getInstance().getEntAddress(script, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("addr_test1wrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcl6szpr");
        }

        @Test
        void getEntAddress_type14_mainnet() {
            Address address = AddressService.getInstance().getRewardAddress(stakePubKey, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("stake1uyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gh6ffgw");
        }

        @Test
        void getEntAddress_type14_testnet() {
            Address address = AddressService.getInstance().getRewardAddress(stakePubKey, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("stake_test1uqehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gssrtvn");
        }

        @Test
        void getEntAddress_type15_mainnet_script() throws CborSerializationException {
            Address address = AddressService.getInstance().getRewardAddress(script, Networks.mainnet());
            assertThat(address.toBech32()).isEqualTo("stake178phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcccycj5");
        }

        @Test
        void getEntAddress_type15_testnet_script() throws CborSerializationException {
            Address address = AddressService.getInstance().getRewardAddress(script, Networks.testnet());
            assertThat(address.toBech32()).isEqualTo("stake_test17rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcljw6kf");
        }
    }

    public ScriptAtLeast getMultisigScript() {
        ScriptPubkey key1 = new ScriptPubkey();
        key1.setKeyHash("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");

        ScriptPubkey key2 = new ScriptPubkey();
        key2.setKeyHash("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");

        ScriptPubkey key3 = new ScriptPubkey();
        key3.setKeyHash("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2);
        scriptAtLeast.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        return scriptAtLeast;
    }

    @Nested
    class VerificationTests {

        @Test
        void verifyBaseAddress_testnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            String baseAddress = account.baseAddress();
            Address address = new Address(baseAddress);

            //pub key
            byte[] publicKey = account.publicKeyBytes();
            boolean verified = AddressService.getInstance().verifyAddress(address, publicKey);

            assertThat(verified).isTrue();
        }

        @Test
        void verifyBaseAddress_mainnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(mnemonic);
            String baseAddress = account.baseAddress();
            Address address = new Address(baseAddress);

            //pub key
            byte[] publicKey = account.publicKeyBytes();
            boolean verified = AddressService.getInstance().verifyAddress(address, publicKey);

            assertThat(verified).isTrue();
        }

        @Test
        void verifyBaseAddress_invalidPubKey_testnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            String baseAddress = account.baseAddress();
            Address address = new Address(baseAddress);

            //pub key
            byte[] publicKey = new Account(Networks.testnet()).publicKeyBytes(); //Different pub key
            boolean verified = AddressService.getInstance().verifyAddress(address, publicKey);

            assertThat(verified).isFalse();
        }

        @Test
        void verifyBaseAddress_invalidPubKey_mainnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(mnemonic);
            String baseAddress = account.baseAddress();
            Address address = new Address(baseAddress);

            //pub key
            byte[] publicKey = new Account().publicKeyBytes(); //Different pub key
            boolean verified = AddressService.getInstance().verifyAddress(address, publicKey);

            assertThat(verified).isFalse();
        }

        @Test
        void verifyRewardAddress_testnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            String stakeAddress = account.stakeAddress();
            Address address = new Address(stakeAddress);

            //pub key
            byte[] stakeCredential = account.stakeHdKeyPair().getPublicKey().getKeyData();
            boolean verified = AddressService.getInstance().verifyAddress(address, stakeCredential);

            assertThat(verified).isTrue();
        }

        @Test
        void verifyRewardAddress_invalidPubKey_testnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            String stakeAddress = account.stakeAddress();
            Address address = new Address(stakeAddress);

            //pub key
            byte[] stakeCredential = new Account(Networks.testnet()).stakeHdKeyPair().getPublicKey().getKeyData();
            boolean verified = AddressService.getInstance().verifyAddress(address, stakeCredential);

            assertThat(verified).isFalse();
        }

        @Test
        void verifyEntAddress_testnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            String entAddress = account.enterpriseAddress();
            Address address = new Address(entAddress);

            //pub key
            byte[] publicKey = account.publicKeyBytes();
            boolean verified = AddressService.getInstance().verifyAddress(address, publicKey);

            assertThat(verified).isTrue();
        }

        @Test
        void verifyEntAddress_invalidPubKey_testnet() {
            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            String entAddress = account.enterpriseAddress();
            Address address = new Address(entAddress);

            //pub key
            byte[] publicKey = new Account(Networks.testnet()).publicKeyBytes();
            boolean verified = AddressService.getInstance().verifyAddress(address, publicKey);

            assertThat(verified).isFalse();
        }

        @Test
        void verifyPtrAddress_testnet() {
            Pointer pointer = new Pointer(2498243, 27, 3);

            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            Address pointerAddress = AddressService.getInstance().getPointerAddress(account.hdKeyPair().getPublicKey(), pointer, Networks.testnet());

            //pub key
            byte[] publicKey = account.publicKeyBytes();
            boolean verified = AddressService.getInstance().verifyAddress(pointerAddress, publicKey);

            assertThat(verified).isTrue();
        }

        @Test
        void verifyPtrAddress_invalidPubKey_testnet() {
            Pointer pointer = new Pointer(2498243, 27, 3);

            String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
            Account account = new Account(Networks.testnet(), mnemonic);
            Address pointerAddress = AddressService.getInstance().getPointerAddress(account.hdKeyPair().getPublicKey(), pointer, Networks.testnet());

            //pub key
            byte[] publicKey = new Account(Networks.testnet()).publicKeyBytes();
            boolean verified = AddressService.getInstance().verifyAddress(pointerAddress, publicKey);

            assertThat(verified).isFalse();
        }
    }

    @Nested
    class StakeAddressTests {

        @Test
        void getStakeAddress_fromBaseAddress() {
            Address baseAddress = new Address("addr1qxaghr9uhuk73gfuxs5vvdwnanmezqkmc7m265fhjrqczny4l34qz5xuyjzm4nxaju7eduazqtdnay7vzagwc37zyayqrzmr04");

            Address stakeAddress = AddressService.getInstance().getStakeAddress(baseAddress);
            System.out.println(stakeAddress.toBech32());

            assertThat(stakeAddress.toBech32()).isEqualTo("stake1ux2lc6sp2rwzfpd6enwew0vk7w3q9ke7j0xpw58vglpzwjq7sy7gn");
        }

        @Test
        void getStakeAddress_fromBaseAddress_testnet() {
            Address baseAddress = new Address("addr_test1qr2juy0nujzrdh2zm0kxmzxz89eju5pm3dar9t6cuhu8vffndw08djstd55f2k8pxdt2nzha98nh3q3ulr8s4ruj3jlqvltt8m");

            Address stakeAddress = AddressService.getInstance().getStakeAddress(baseAddress);
            System.out.println(stakeAddress.toBech32());

            assertThat(stakeAddress.toBech32()).isEqualTo("stake_test1uqekh8nkeg9k62y4trsnx44f3t7jnemcsg703nc237fge0surp9uz");
        }

        @Test
        void getStakeAddress_fromBaseAddress_scriptHashStake() {
            Address baseAddress = new Address("addr1yx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs2z78ve");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake178phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcccycj5");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type00_mainnet() {
            Address baseAddress = new Address("addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake1uyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gh6ffgw");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type00_testnet() {
            Address baseAddress = new Address("addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs68faae");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake_test1uqehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gssrtvn");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type01_mainnet() {
            Address baseAddress = new Address("addr1z8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gten0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs9yc0hh");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake1uyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gh6ffgw");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type01_testnet() {
            Address baseAddress = new Address("addr_test1zrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gten0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgsxj90mg");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake_test1uqehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gssrtvn");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type02_mainnet() { //script stake
            Address baseAddress = new Address("addr1yx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs2z78ve");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake178phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcccycj5");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type02_testnet() { //script stake
            Address baseAddress = new Address("addr_test1yz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerkr0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shsf5r8qx");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake_test17rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcljw6kf");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type03_mainnet() { //script stake
            Address baseAddress = new Address("addr1x8phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gt7r0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shskhj42g");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake178phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcccycj5");
        }

        @Test
        void getStakeAddress_fromBaseAddress_type03_testnet() { //script stake
            Address baseAddress = new Address("addr_test1xrphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gt7r0vd4msrxnuwnccdxlhdjar77j6lg0wypcc9uar5d2shs4p04xh");
            Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);

            assertThat(stakeAddres.toBech32()).isEqualTo("stake_test17rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtcljw6kf");
        }

        @Test
        void getStakeAddress_fromPointerAddress_type04_mainnet() {
            assertThrows(AddressRuntimeException.class, () -> {
                Address baseAddress = new Address("addr1gx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5pnz75xxcrzqf96k");
                Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);
            });
        }

        @Test
        void getStakeAddress_fromPointerAddress_type04_testnet() {
            assertThrows(AddressRuntimeException.class, () -> {
                Address baseAddress = new Address("addr_test1gz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5pnz75xxcrdw5vky");
                Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);
            });
        }

        @Test
        void getStakeAddress_fromPointerAddress_type05_mainnet_script() {
            assertThrows(AddressRuntimeException.class, () -> {
                Address baseAddress = new Address("addr128phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcrtw79hu");
                Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);
            });
        }

        @Test
        void getStakeAddress_fromPointerAddress_type05_testnet_script() {
            assertThrows(AddressRuntimeException.class, () -> {
                Address baseAddress = new Address("addr_test12rphkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcryqrvmw");
                Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);
            });
        }

        @Test
        void getStakeAddress_fromEntAddress_type06_mainnet() {
            assertThrows(AddressRuntimeException.class, () -> {
                Address baseAddress = new Address("addr1vx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzers66hrl8");
                Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);
            });
        }

        @Test
        void getStakeAddress_fromEntAddress_type06_testnet() {
            assertThrows(AddressRuntimeException.class, () -> {
                Address baseAddress = new Address("addr_test1vz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerspjrlsz");
                Address stakeAddres = AddressService.getInstance().getStakeAddress(baseAddress);
            });
        }
    }

    @Nested
    class GetDelegationHashAndPaymentKeyHashTest {
        @Test
        void getStakeKeyHash_fromTestnetEntAddress_returnsNull() {
            String entAddress = "addr_test1vzxazkxxxapq9k76aae6reskfjuzfljy4lf209kduuxjfec7msk6n";
            Address address = new Address(entAddress);

            Optional<byte[]> delegationHash = AddressService.getInstance().getDelegationHash(address);
            assertThat(delegationHash).isEmpty();
        }

        @Test
        void getStakeKeyHash_fromMainetEntAddress_returnsNull() {
            String entAddress = "addr1vxm9rssxy335nxtph8x4jndrnxj7eyg0e66uv0u7k4dzyjsg6fr38";
            Address address = new Address(entAddress);

            Optional<byte[]> delegationHash = AddressService.getInstance().getDelegationHash(address);
            assertThat(delegationHash).isEmpty();
        }

        @Test
        void getStakeKeyHash_fromBaseAddress_testnet() {
            String baseAddress = "addr_test1qra2kf2lzdt05j7793wz9nxgf5ywf79puqu50djvp0q804g4ee7lj7wgt0urvw6n57eklscwju3p02wu2vmdqd84hgdsc8lqcp";
            Address address = new Address(baseAddress);

            byte[] delegationHash = AddressService.getInstance().getDelegationHash(address).get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("15ce7df979c85bf8363b53a7b36fc30e972217a9dc5336d034f5ba1b");
        }

        @Test
        void getStakeKeyHash_fromBaseAddress_mainnet() {
            String baseAddress = "addr1q8hxcjnvwd2hf397kee6ptfu889r248cn0k5qzvyz78ywa5tdzj0j8al6970zz8urxtdsvwejxn89aqkp4fk6y2jt2wswpm6fz";
            Address address = new Address(baseAddress);

            byte[] delegationHash = AddressService.getInstance().getDelegationHash(address).get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("8b68a4f91fbfd17cf108fc1996d831d991a672f4160d536d11525a9d");
        }

        @Test
        void getStakeKeyHash_fromRewardAddress_testnet() {
            String rewardAddress = "stake_test1up2gmk3f9s6l50ehm26s9kufd9y2gkektu2xy3uawvzk5ug0ze6xv";
            Address address = new Address(rewardAddress);

            byte[] delegationHash = AddressService.getInstance().getDelegationHash(address).get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("548dda292c35fa3f37dab502db896948a45b365f1462479d73056a71");
        }

        @Test
        void getStakeKeyHash_fromRewardAddress_mainnet() {
            String rewardAddress = "stake1u857f9s2s556nfedulykja499mvredrnj6qupp2f2mpumsgkw23zc";
            Address address = new Address(rewardAddress);

            byte[] delegationHash = AddressService.getInstance().getDelegationHash(address).get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("e9e4960a8529a9a72de7c96976a52ed83cb4739681c0854956c3cdc1");
        }

        @Test
        void getStakeKeyHash_fromPointerAddress_testnet() {
            String pointerAddress = "addr_test1grl9uzketqym52kqyjrxplslh3t5zlm65vmlvgzmnycg7m48kw8hvqqqt5mz03";
            Address address = new Address(pointerAddress);

            byte[] delegationHash = AddressService.getInstance().getDelegationHash(address).get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("a7b38f760000");
        }

        @Test
        void getStakeKeyHash_fromPointerAddress_mainnet() {
            String pointerAddress = "addr1g9ekml92qyvzrjmawxkh64r2w5xr6mg9ngfmxh2khsmdrcudevsft64mf887333adamant";
            Address address = new Address(pointerAddress);

            byte[] delegationHash = AddressService.getInstance().getDelegationHash(address).get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("8dcb2095eabb49cfe8c63d");
        }


        @Test
        void getPaymentKeyHash_fromBaseAddress_mainnet() {
            String baseAddress = "addr1q9wf2pasguad0uy8rzxly6hxc4yk4vhlj4ufv7xg973fvu9l7f23amv6dqy99nrlezg38y3797tgel0udlxfsjp26ensg63c8u";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address).get();
            System.out.println(HexUtil.encodeHexString(paymentKeyHash));
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("5c9507b0473ad7f087188df26ae6c5496ab2ff95789678c82fa29670");
        }

        @Test
        void getPaymentKeyHash_fromBaseAddress_testnet() {
            String baseAddress = "addr_test1qz96la9s5xa0ad67vvprdrl8dw79gs7nqf58czr409xynscttpxxc9w2gmdewjwzlv8lx255q7z3mqymv0umkygycskqyf227e";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address).get();
            System.out.println(HexUtil.encodeHexString(paymentKeyHash));
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("8baff4b0a1bafeb75e6302368fe76bbc5443d302687c0875794c49c3");
        }

        @Test
        void getPaymentKeyHash_fromEntAddress_testnet() {
            String baseAddress = "addr_test1wzdtu0djc76qyqak9cj239udezj2544nyk3ksmfqvaksv7c9xanpg";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address).get();
            System.out.println(HexUtil.encodeHexString(paymentKeyHash));
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("9abe3db2c7b40203b62e24a8978dc8a4aa56b325a3686d20676d067b");
        }

        @Test
        void getPaymentKeyHash_fromEntAddress_mainnet() {
            String baseAddress = "addr1vypj5jf999edw02khvy8e63ec9w0wk39y2pels36ntn3gxsqlvq6t";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address).get();
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("032a49252972d73d56bb087cea39c15cf75a2522839fc23a9ae7141a");
        }

        @Test
        void getPaymentKeyHash_fromStakeAddress_mainnet() {
            String baseAddress = "stake1uytvm7mh3xvpqgef9fh6f5llwktk0urg7q23cs5h20x9qesrphuw7";
            Address address = new Address(baseAddress);

            Optional<byte[]> paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address);
            assertThat(paymentKeyHash).isEmpty();
        }

        @Test
        void getPaymentKeyHash_fromPointerAddress_mainnet() {
            String pointerAddress = "addr1g9ekml92qyvzrjmawxkh64r2w5xr6mg9ngfmxh2khsmdrcudevsft64mf887333adamant";
            Address address = new Address(pointerAddress);

            byte[] paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address).get();
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("736dfcaa011821cb7d71ad7d546a750c3d6d059a13b35d56bc36d1e3");
        }

        @Test
        void getPaymentKeyHash_fromPointerAddress_testnet() {
            String pointerAddress = "addr_test1grl9uzketqym52kqyjrxplslh3t5zlm65vmlvgzmnycg7m48kw8hvqqqt5mz03";
            Address address = new Address(pointerAddress);

            byte[] paymentKeyHash = AddressService.getInstance().getPaymentKeyHash(address).get();
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("fe5e0ad95809ba2ac0248660fe1fbc57417f7aa337f6205b99308f6e");
        }
    }

    @Nested
    class IsPubKeyHashOrScriptHashInPaymentPartTest {
        @Test
        void isPubKeyHashInPaymentPart_whenScriptAddress() {
            String addr = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
            Address address = new Address(addr);

            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_whenScriptAddress() {
            String addr = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
            Address address = new Address(addr);

            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenPubKeyAddress() {
            String addr = "addr_test1qpkcp26l47j2fp4crdl9n83zmnw84qrp64sd5w6fwesqt6g8sd9mcktl67rn2t0cth25ryflz59yfxlx636csng7hawstfp400";
            Address address = new Address(addr);

            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_whenPubKeyAddress() {
            String addr = "addr_test1qpkcp26l47j2fp4crdl9n83zmnw84qrp64sd5w6fwesqt6g8sd9mcktl67rn2t0cth25ryflz59yfxlx636csng7hawstfp400";
            Address address = new Address(addr);

            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isPubKeyHashInPaymentPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenDelegationPartIsScriptHash() throws Exception {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_whenDelegationPartIsScriptHash() throws Exception {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isPubKeyHashInPaynmentPart_whenPaymentPartIsScriptHash() throws Exception {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(plutusScript, hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaynmentPart_whenPaymentPartIsScriptHash() throws Exception {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(plutusScript, hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenBothPaymentPartAndDelegationPartAreScriptHash() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(plutusScript, plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_whenBothPaymentPartAndDelegationPartAreScriptHash() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(plutusScript, plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenPaymentPartIsPubKeyHash_entAddress() {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();

            Address address = AddressService.getInstance().getEntAddress(hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_whenPaymentPartIsPubKeyHash_entAddress() {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();

            Address address = AddressService.getInstance().getEntAddress(hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenPaymentPartIsScriptHash_entAddress() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getEntAddress(plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_whenPaymentPartIsScriptHash_entAddress() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getEntAddress(plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_returnsFalse_whenRewardAddress() {
            Address address = new Address("stake_test1uqrcxjaut9la0pe49hu9m42pjyl32zjyn0ndgavgf50t7hghyh702");
            boolean flag = AddressService.getInstance().isPubKeyHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_returnsFalse_whenRewardAddress() {
            Address address = new Address("stake_test1uqrcxjaut9la0pe49hu9m42pjyl32zjyn0ndgavgf50t7hghyh702");
            boolean flag = AddressService.getInstance().isScriptHashInPaymentPart(address);
            assertThat(flag).isFalse();
        }
    }

    @Nested
    class IsStakeKeyHashOrScriptHashInDelegationPart {
        @Test
        void isStakeKeyHashInDelegationPart_whenScriptHash() throws Exception {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isStakeKeyHashInDelegationPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInDelegationPart_whenScriptHash() throws Exception {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressService.getInstance().getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = AddressService.getInstance().isScriptHashInDelegationPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isStakeKeyHashInDelegationPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = AddressService.getInstance().isStakeKeyHashInDelegationPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInDelegationPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = AddressService.getInstance().isScriptHashInDelegationPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isStakeKeyHashInDelegationPart_returnsFalse_whenEnterpriseAddress() {
            Address address = new Address(new Account().enterpriseAddress());
            boolean flag = AddressService.getInstance().isStakeKeyHashInDelegationPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInDelegationPart_returnsFalse_whenEnterpriseAddress() {
            Address address = new Address(new Account().enterpriseAddress());
            boolean flag = AddressService.getInstance().isScriptHashInDelegationPart(address);
            assertThat(flag).isFalse();
        }

        @Test
        void isStakeKeyHashInDelegationPart_whenStakeAddress() {
            Address address = new Address(new Account().stakeAddress());

            boolean flag = AddressService.getInstance().isStakeKeyHashInDelegationPart(address);
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInDelegationPart_whenStakeAddress() {
            Address address = new Address(new Account().stakeAddress());

            boolean flag = AddressService.getInstance().isScriptHashInDelegationPart(address);
            assertThat(flag).isFalse();
        }
    }

}
