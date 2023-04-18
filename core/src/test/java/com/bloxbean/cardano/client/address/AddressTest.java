package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.transaction.spec.PlutusV2Script;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class AddressTest {

    @Test
    public void testBaseAddress_whenMainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(mnemonic);

        Address address = new Address(account.baseAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Base);
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getPrefix()).isEqualTo("addr");
    }

    @Test
    public void testBaseAddress_whenPreview() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.preview(), mnemonic);

        Address address = new Address(account.baseAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Base);
        assertThat(address.getPrefix()).isEqualTo("addr_test");

    }
    @Test
    public void testBaseAddress_whenPreprod() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.preprod(), mnemonic);

        Address address = new Address(account.baseAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Base);
        assertThat(address.getPrefix()).isEqualTo("addr_test");

    }



    @Test
    public void testBaseAddress_whenTestnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.testnet(), mnemonic);

        Address address = new Address(account.baseAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Base);
        assertThat(address.getNetwork()).isEqualTo(Networks.testnet());
        assertThat(address.getPrefix()).isEqualTo("addr_test");

    }

    @Test
    public void testEntAddress_whenMainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.mainnet(), mnemonic);

        Address address = new Address(account.enterpriseAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Enterprise);
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getPrefix()).isEqualTo("addr");
    }

    @Test
    public void testEntAddress_whenTestnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.testnet(), mnemonic);

        Address address = new Address(account.enterpriseAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Enterprise);
        assertThat(address.getNetwork()).isEqualTo(Networks.testnet());
        assertThat(address.getPrefix()).isEqualTo("addr_test");
    }

    @Test
    public void testRewardAddress_whenMainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.mainnet(), mnemonic);

        Address address = new Address(account.stakeAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Reward);
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getPrefix()).isEqualTo("stake");
        assertThat(address.getAddress().toString()).isEqualTo("stake1u9xeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697quawequ");
    }

    @Test
    public void testRewardAddress_whenMainnet_Account1() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPath();
        derivationPath.getAccount().setValue(1);

        Account account = new Account(Networks.mainnet(), mnemonic, derivationPath);

        Address address = new Address(account.stakeAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Reward);
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getPrefix()).isEqualTo("stake");
        assertThat(address.getAddress().toString()).isEqualTo("stake1u9xcv6e9z75qg8pkkzwfyd6aq2t50hgkymv9jq5q5kpj9lcthljzu");
    }

    @Test
    public void testRewardAddress_whenMainnet_Account2() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPath();
        derivationPath.getAccount().setValue(2);

        Account account = new Account(Networks.mainnet(), mnemonic, derivationPath);

        Address address = new Address(account.stakeAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Reward);
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getPrefix()).isEqualTo("stake");
        assertThat(address.getAddress().toString()).isEqualTo("stake1uyzprh5g4anfumuslz52r98g8vx4lrnu6grt9m329y8hwxq9w8v34");
    }

    @Test
    public void testRewardAddress_whenTestnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account account = new Account(Networks.testnet(), mnemonic);

        Address address = new Address(account.stakeAddress());

        assertThat(address.getAddressType()).isEqualTo(AddressType.Reward);
        assertThat(address.getNetwork()).isEqualTo(Networks.testnet());
        assertThat(address.getPrefix()).isEqualTo("stake_test");
    }

    @Test
    public void testPointerAddress_whenMainnet() {
        String pointerAddress = "addr1gx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5ph3wczvf2w8lunk";

        Address address = new Address(pointerAddress);

        assertThat(address.getAddressType()).isEqualTo(AddressType.Ptr);
        assertThat(address.getNetwork()).isEqualTo(Networks.mainnet());
        assertThat(address.getPrefix()).isEqualTo("addr");
    }

    @Test
    public void testPointerAddress_whenTestnet() {
        String pointerAddress = "addr_test1gz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerspqgpsqe70et";

        Address address = new Address(pointerAddress);

        assertThat(address.getAddressType()).isEqualTo(AddressType.Ptr);
        assertThat(address.getNetwork()).isEqualTo(Networks.testnet());
        assertThat(address.getPrefix()).isEqualTo("addr_test");
    }

    @Nested
    class GetDelegationHashAndPaymentKeyHashTest {
        @Test
        void getStakeKeyHash_fromTestnetEntAddress_returnsNull() {
            String entAddress = "addr_test1vzxazkxxxapq9k76aae6reskfjuzfljy4lf209kduuxjfec7msk6n";
            Address address = new Address(entAddress);

            Optional<byte[]> delegationHash = address.getDelegationCredential();
            assertThat(delegationHash).isEmpty();
        }

        @Test
        void getStakeKeyHash_fromMainetEntAddress_returnsNull() {
            String entAddress = "addr1vxm9rssxy335nxtph8x4jndrnxj7eyg0e66uv0u7k4dzyjsg6fr38";
            Address address = new Address(entAddress);

            Optional<byte[]> delegationHash = address.getDelegationCredential();
            assertThat(delegationHash).isEmpty();
        }

        @Test
        void getStakeKeyHash_fromBaseAddress_testnet() {
            String baseAddress = "addr_test1qra2kf2lzdt05j7793wz9nxgf5ywf79puqu50djvp0q804g4ee7lj7wgt0urvw6n57eklscwju3p02wu2vmdqd84hgdsc8lqcp";
            Address address = new Address(baseAddress);

            byte[] delegationHash = address.getDelegationCredential().get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("15ce7df979c85bf8363b53a7b36fc30e972217a9dc5336d034f5ba1b");
        }

        @Test
        void getStakeKeyHash_fromBaseAddress_mainnet() {
            String baseAddress = "addr1q8hxcjnvwd2hf397kee6ptfu889r248cn0k5qzvyz78ywa5tdzj0j8al6970zz8urxtdsvwejxn89aqkp4fk6y2jt2wswpm6fz";
            Address address = new Address(baseAddress);

            byte[] delegationHash = address.getDelegationCredential().get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("8b68a4f91fbfd17cf108fc1996d831d991a672f4160d536d11525a9d");
        }

        @Test
        void getStakeKeyHash_fromRewardAddress_testnet() {
            String rewardAddress = "stake_test1up2gmk3f9s6l50ehm26s9kufd9y2gkektu2xy3uawvzk5ug0ze6xv";
            Address address = new Address(rewardAddress);

            byte[] delegationHash = address.getDelegationCredential().get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("548dda292c35fa3f37dab502db896948a45b365f1462479d73056a71");
        }

        @Test
        void getStakeKeyHash_fromRewardAddress_mainnet() {
            String rewardAddress = "stake1u857f9s2s556nfedulykja499mvredrnj6qupp2f2mpumsgkw23zc";
            Address address = new Address(rewardAddress);

            byte[] delegationHash = address.getDelegationCredential().get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("e9e4960a8529a9a72de7c96976a52ed83cb4739681c0854956c3cdc1");
        }

        @Test
        void getStakeKeyHash_fromPointerAddress_testnet() {
            String pointerAddress = "addr_test1grl9uzketqym52kqyjrxplslh3t5zlm65vmlvgzmnycg7m48kw8hvqqqt5mz03";
            Address address = new Address(pointerAddress);

            byte[] delegationHash = address.getDelegationCredential().get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("a7b38f760000");
        }

        @Test
        void getStakeKeyHash_fromPointerAddress_mainnet() {
            String pointerAddress = "addr1g9ekml92qyvzrjmawxkh64r2w5xr6mg9ngfmxh2khsmdrcudevsft64mf887333adamant";
            Address address = new Address(pointerAddress);

            byte[] delegationHash = address.getDelegationCredential().get();
            assertThat(HexUtil.encodeHexString(delegationHash)).isEqualTo("8dcb2095eabb49cfe8c63d");
        }


        @Test
        void getPaymentKeyHash_fromBaseAddress_mainnet() {
            String baseAddress = "addr1q9wf2pasguad0uy8rzxly6hxc4yk4vhlj4ufv7xg973fvu9l7f23amv6dqy99nrlezg38y3797tgel0udlxfsjp26ensg63c8u";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = address.getPaymentCredential().get();
            System.out.println(HexUtil.encodeHexString(paymentKeyHash));
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("5c9507b0473ad7f087188df26ae6c5496ab2ff95789678c82fa29670");
        }

        @Test
        void getPaymentKeyHash_fromBaseAddress_testnet() {
            String baseAddress = "addr_test1qz96la9s5xa0ad67vvprdrl8dw79gs7nqf58czr409xynscttpxxc9w2gmdewjwzlv8lx255q7z3mqymv0umkygycskqyf227e";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = address.getPaymentCredential().get();
            System.out.println(HexUtil.encodeHexString(paymentKeyHash));
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("8baff4b0a1bafeb75e6302368fe76bbc5443d302687c0875794c49c3");
        }

        @Test
        void getPaymentKeyHash_fromEntAddress_testnet() {
            String baseAddress = "addr_test1wzdtu0djc76qyqak9cj239udezj2544nyk3ksmfqvaksv7c9xanpg";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = address.getPaymentCredential().get();
            System.out.println(HexUtil.encodeHexString(paymentKeyHash));
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("9abe3db2c7b40203b62e24a8978dc8a4aa56b325a3686d20676d067b");
        }

        @Test
        void getPaymentKeyHash_fromEntAddress_mainnet() {
            String baseAddress = "addr1vypj5jf999edw02khvy8e63ec9w0wk39y2pels36ntn3gxsqlvq6t";
            Address address = new Address(baseAddress);

            byte[] paymentKeyHash = address.getPaymentCredential().get();
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("032a49252972d73d56bb087cea39c15cf75a2522839fc23a9ae7141a");
        }

        @Test
        void getPaymentKeyHash_fromStakeAddress_mainnet() {
            String baseAddress = "stake1uytvm7mh3xvpqgef9fh6f5llwktk0urg7q23cs5h20x9qesrphuw7";
            Address address = new Address(baseAddress);

            Optional<byte[]> paymentKeyHash = address.getPaymentCredential();
            assertThat(paymentKeyHash).isEmpty();
        }

        @Test
        void getPaymentKeyHash_fromPointerAddress_mainnet() {
            String pointerAddress = "addr1g9ekml92qyvzrjmawxkh64r2w5xr6mg9ngfmxh2khsmdrcudevsft64mf887333adamant";
            Address address = new Address(pointerAddress);

            byte[] paymentKeyHash = address.getPaymentCredential().get();
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("736dfcaa011821cb7d71ad7d546a750c3d6d059a13b35d56bc36d1e3");
        }

        @Test
        void getPaymentKeyHash_fromPointerAddress_testnet() {
            String pointerAddress = "addr_test1grl9uzketqym52kqyjrxplslh3t5zlm65vmlvgzmnycg7m48kw8hvqqqt5mz03";
            Address address = new Address(pointerAddress);

            byte[] paymentKeyHash = address.getPaymentCredential().get();
            assertThat(HexUtil.encodeHexString(paymentKeyHash)).isEqualTo("fe5e0ad95809ba2ac0248660fe1fbc57417f7aa337f6205b99308f6e");
        }
    }

    @Nested
    class IsPubKeyHashOrScriptHashInPaymentPartTest {
        @Test
        void isPubKeyHashInPaymentPart_whenScriptAddress() {
            String addr = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
            Address address = new Address(addr);

            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_whenScriptAddress() {
            String addr = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
            Address address = new Address(addr);

            boolean flag = address.isScriptHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenPubKeyAddress() {
            String addr = "addr_test1qpkcp26l47j2fp4crdl9n83zmnw84qrp64sd5w6fwesqt6g8sd9mcktl67rn2t0cth25ryflz59yfxlx636csng7hawstfp400";
            Address address = new Address(addr);

            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_whenPubKeyAddress() {
            String addr = "addr_test1qpkcp26l47j2fp4crdl9n83zmnw84qrp64sd5w6fwesqt6g8sd9mcktl67rn2t0cth25ryflz59yfxlx636csng7hawstfp400";
            Address address = new Address(addr);

            boolean flag = address.isScriptHashInPaymentPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isPubKeyHashInPaymentPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = address.isScriptHashInPaymentPart();
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

            Address address = AddressProvider.getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = address.isPubKeyHashInPaymentPart();
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

            Address address = AddressProvider.getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = address.isScriptHashInPaymentPart();
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

            Address address = AddressProvider.getBaseAddress(plutusScript, hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = address.isPubKeyHashInPaymentPart();
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

            Address address = AddressProvider.getBaseAddress(plutusScript, hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = address.isScriptHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenBothPaymentPartAndDelegationPartAreScriptHash() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressProvider.getBaseAddress(plutusScript, plutusScript, Networks.mainnet());
            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_whenBothPaymentPartAndDelegationPartAreScriptHash() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressProvider.getBaseAddress(plutusScript, plutusScript, Networks.mainnet());
            boolean flag = address.isScriptHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenPaymentPartIsPubKeyHash_entAddress() {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();

            Address address = AddressProvider.getEntAddress(hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInPaymentPart_whenPaymentPartIsPubKeyHash_entAddress() {
            Account account = new Account();
            HdKeyPair hdKeyPair = account.hdKeyPair();

            Address address = AddressProvider.getEntAddress(hdKeyPair.getPublicKey(), Networks.mainnet());
            boolean flag = address.isScriptHashInPaymentPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isPubKeyHashInPaymentPart_whenPaymentPartIsScriptHash_entAddress() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressProvider.getEntAddress(plutusScript, Networks.mainnet());
            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_whenPaymentPartIsScriptHash_entAddress() throws Exception {
            PlutusV2Script plutusScript = PlutusV2Script.builder()
                    .type("PlutusScriptV2")
                    .cborHex("49480100002221200101")
                    .build();

            Address address = AddressProvider.getEntAddress(plutusScript, Networks.mainnet());
            boolean flag = address.isScriptHashInPaymentPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isPubKeyHashInPaymentPart_returnsFalse_whenRewardAddress() {
            Address address = new Address("stake_test1uqrcxjaut9la0pe49hu9m42pjyl32zjyn0ndgavgf50t7hghyh702");
            boolean flag = address.isPubKeyHashInPaymentPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInPaymentPart_returnsFalse_whenRewardAddress() {
            Address address = new Address("stake_test1uqrcxjaut9la0pe49hu9m42pjyl32zjyn0ndgavgf50t7hghyh702");
            boolean flag = address.isScriptHashInPaymentPart();
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

            Address address = AddressProvider.getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = address.isStakeKeyHashInDelegationPart();
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

            Address address = AddressProvider.getBaseAddress(hdKeyPair.getPublicKey(), plutusScript, Networks.mainnet());
            boolean flag = address.isScriptHashInDelegationPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isStakeKeyHashInDelegationPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = address.isStakeKeyHashInDelegationPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInDelegationPart_randomNewAccount() {
            Address address = new Address(new Account().baseAddress());

            boolean flag = address.isScriptHashInDelegationPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isStakeKeyHashInDelegationPart_returnsFalse_whenEnterpriseAddress() {
            Address address = new Address(new Account().enterpriseAddress());
            boolean flag = address.isStakeKeyHashInDelegationPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isScriptHashInDelegationPart_returnsFalse_whenEnterpriseAddress() {
            Address address = new Address(new Account().enterpriseAddress());
            boolean flag = address.isScriptHashInDelegationPart();
            assertThat(flag).isFalse();
        }

        @Test
        void isStakeKeyHashInDelegationPart_whenStakeAddress() {
            Address address = new Address(new Account().stakeAddress());

            boolean flag = address.isStakeKeyHashInDelegationPart();
            assertThat(flag).isTrue();
        }

        @Test
        void isScriptHashInDelegationPart_whenStakeAddress() {
            Address address = new Address(new Account().stakeAddress());

            boolean flag = address.isScriptHashInDelegationPart();
            assertThat(flag).isFalse();
        }
    }
}
