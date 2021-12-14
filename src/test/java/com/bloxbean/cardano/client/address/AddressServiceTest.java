package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
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

        Address address = AddressService.getInstance().getAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.testnet(), AddressType.Base);

        System.out.println(address.toBech32());

        String expected = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
        assertThat(address.toBech32()).isEqualTo(expected);
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

        Address address = AddressService.getInstance().getAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.mainnet(), AddressType.Base);

        System.out.println(address.toBech32());

        String expected = "addr1qxx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqrzmetm";
        assertThat(address.toBech32()).isEqualTo(expected);
    }

    @Test
    void getBaseAddress_whenIndex1_mainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath paymentDerivationPath = DerivationPath.createExternalAddressDerivationPath(1);
        HdKeyPair paymentHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, paymentDerivationPath);

        //stake
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address address = AddressService.getInstance().getAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.mainnet(), AddressType.Base);

        System.out.println(address.toBech32());

        String expected = "addr1qyvhg6pxu3dgl3jxehkmcnz2kcr4th3rc4huat7pxmr0ttzdjs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqxvm6yt";
        assertThat(address.toBech32()).isEqualTo(expected);
    }

    @Test
    void getBaseAddress_whenIndex1_mainnet_internalAddress() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        DerivationPath changeAddressDerivationPath = DerivationPath.createInternalAddressDerivationPath(0);
        HdKeyPair paymentHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, changeAddressDerivationPath);

        //stake - Role 2
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address address = AddressService.getInstance().getAddress(paymentHdKeyPair.getPublicKey(), stakeHdKeyPair.getPublicKey(), Networks.mainnet(), AddressType.Base);

        System.out.println(address.toBech32());

        String expected = "addr1q8vayr52ketz2rtsmkswk6tf4llylwt6rjjtm74wvqlwe56djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqv5ay42";
        assertThat(address.toBech32()).isEqualTo(expected);
    }

    @Test
    void getStakeAddress_whenMainnet() {
        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        //stake - Role 2
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPath();
        HdKeyPair stakeHdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);

        Address address = AddressService.getInstance().getAddress(null, stakeHdKeyPair.getPublicKey(), Networks.mainnet(), AddressType.Reward);

        System.out.println(address.toBech32());

        String expected = "stake1u9xeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697quawequ";
        assertThat(address.toBech32()).isEqualTo(expected);
    }
}
