package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import org.junit.jupiter.api.Test;

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
}
