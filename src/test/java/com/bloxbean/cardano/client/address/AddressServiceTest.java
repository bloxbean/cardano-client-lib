package com.bloxbean.cardano.client.address;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.PlutusScript;
import com.bloxbean.cardano.client.transaction.spec.script.Script;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
            PlutusScript plutusScript = PlutusScript.builder()
                    .type("PlutusScriptV1")
                    .cborHex("4e4d01000033222220051200120011")
                    .build();

            Address address = AddressService.getInstance().getEntAddress(plutusScript, Networks.testnet());
            System.out.println(address.toBech32());

            assertThat(address.toBech32()).isEqualTo("addr_test1wpnlxv2xv9a9ucvnvzqakwepzl9ltx7jzgm53av2e9ncv4sysemm8");
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
                public byte[] getScriptHash() throws CborSerializationException { //Provide a dummy impl for script hash
                    byte[] serializedBytes = Bech32.decode(scriptKey).data;
                    return serializedBytes;
                }

                @Override
                public String getPolicyId() throws CborSerializationException {
                    return null;
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

}
