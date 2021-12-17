package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
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
}
