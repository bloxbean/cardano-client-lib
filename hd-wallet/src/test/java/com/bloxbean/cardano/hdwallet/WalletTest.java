package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WalletTest {

    String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";

    String baseAddress0 = "addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra";
    String baseAddress1 = "addr1q93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4zthxn";
    String baseAddress2 = "addr1q8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4qp6cs";
    String baseAddress3 = "addr1qxa5pll82u8lqtzqjqhdr828medvfvezv4509nzyuhwt5aql5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psy8jsmy";

    String testnetBaseAddress0 = "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z";
    String testnetBaseAddress1 = "addr_test1qp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psk5kh2v";
    String testnetBaseAddress2 = "addr_test1qrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8pskku650";

    String entAddress0 = "addr1vxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvstf7k4n";
    String entAddress1 = "addr1v93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg7davae";
    String entAddress2 = "addr1v8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgvddj89";

    String testnetEntAddress0 = "addr_test1vzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvssp226k";
    String testnetEntAddress1 = "addr_test1vp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg99fsju";
    String testnetEntAddress2 = "addr_test1vrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgh9ewgq";

    @Test
    void generateMnemonic24w() {
        Wallet hdWallet = new Wallet(Networks.testnet());
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(24, mnemonic.split(" ").length);
    }

    @Test
    void generateMnemonic15w() {
        Wallet hdWallet = new Wallet(Networks.testnet(), Words.FIFTEEN);
        String mnemonic = hdWallet.getMnemonic();
        assertEquals(15, mnemonic.split(" ").length);
    }

    @Test
    void WalletAddressToAccountAddressTest() {
        Wallet hdWallet = new Wallet(Networks.testnet());
        Address address = hdWallet.getBaseAddress(0);
        Account a = new Account(hdWallet.getNetwork(), hdWallet.getMnemonic(), 0);
        assertEquals(address.getAddress(), a.getBaseAddress().getAddress());
    }

    @Test
    void testGetBaseAddressFromMnemonicIndex_0() {
        Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), phrase24W);
        Assertions.assertEquals(baseAddress0, wallet.getBaseAddressString(0));
        Assertions.assertEquals(baseAddress1, wallet.getBaseAddressString(1));
        Assertions.assertEquals(baseAddress2, wallet.getBaseAddressString(2));
        Assertions.assertEquals(baseAddress3, wallet.getBaseAddressString(3));
    }

    @Test
    void testGetBaseAddressFromMnemonicByNetworkInfoTestnet() {
        Wallet wallet = Wallet.createFromMnemonic(Networks.testnet(), phrase24W);
        Assertions.assertEquals(testnetBaseAddress0, wallet.getBaseAddressString(0));
        Assertions.assertEquals(testnetBaseAddress1, wallet.getBaseAddressString(1));
        Assertions.assertEquals(testnetBaseAddress2, wallet.getBaseAddressString(2));
    }

    @Test
    void testGetEnterpriseAddressFromMnemonicIndex() {
        Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), phrase24W);
        Assertions.assertEquals(entAddress0, wallet.getEntAddress(0).getAddress());
        Assertions.assertEquals(entAddress1, wallet.getEntAddress(1).getAddress());
        Assertions.assertEquals(entAddress2, wallet.getEntAddress(2).getAddress());
    }

    @Test
    void testGetEnterpriseAddressFromMnemonicIndexByNetwork() {
        Wallet wallet = Wallet.createFromMnemonic(Networks.testnet(), phrase24W);
        Assertions.assertEquals(testnetEntAddress0, wallet.getEntAddress(0).getAddress());
        Assertions.assertEquals(testnetEntAddress1, wallet.getEntAddress(1).getAddress());
        Assertions.assertEquals(testnetEntAddress2, wallet.getEntAddress(2).getAddress());
    }

    @Test
    void testGetPublicKeyBytesFromMnemonic() {
        byte[] pubKey = Wallet.createFromMnemonic(phrase24W).getRootKeyPair().get().getPublicKey().getKeyData();
        Assertions.assertEquals(32, pubKey.length);
    }

    @Test
    void testGetPrivateKeyBytesFromMnemonic() {
        byte[] pvtKey = Wallet.createFromMnemonic(phrase24W).getRootKeyPair().get().getPrivateKey().getBytes();
        Assertions.assertEquals(96, pvtKey.length);
    }

    @Test
    void testAccountFromAccountKey0() {
        String accountPrivateKey = "a83aa0356397602d3da7648f139ca06be2465caef14ac4d795b17cdf13bd0f4fe9aac037f7e22335cd99495b963d54f21e8dae540112fe56243b287962da366fd4016f4cfb6d6baba1807621b4216d18581c38404c4768fe820204bef98ba706";
        String address0 = "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z";

        Wallet wallet = Wallet.createFromAccountKey(Networks.testnet(), HexUtil.decodeHexString(accountPrivateKey));
        assertThat(wallet.getBaseAddressString(0)).isEqualTo(address0);
    }

    @Test
    void testAccountFromRootKey() {
        String rootKey = "xprv1frqqvtmax6a5lqv5h6e8vt2wxglasnweglnap8dclz69fd62zp2kqccn08nmjah5rct9zvuh3mx4dln9z984hf42474q6jp2frn3ahkxxaau9y2yfvrr7ex4nw24g37flvarqfhy87g99kp20yknqn7kgs04h87k";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;

        Wallet wallet = Wallet.createFromRootKey(Networks.testnet(), rootKeyBytes);
        assertThat(wallet.getBaseAddressString(0)).isEqualTo("addr_test1qzm0439fe55aynh58qcn4jnh4mwuqwr5n5fez7j0hck9ds8j3nmg5pkqfur4gyupppuu82r83s5eheewzmf6fwlzfz7qzsp6rc");
    }

    @Test
    void testAccountFromAccountKey_0() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        //at account=0
        String accountPrvKey = "acct_xsk14zau3uj79pxh2wplfnezeetj3ms5wfgvyltg3jap0ch5cuswq3qe39l4aty2wjgtyzagzc8squ0hz6hrej6ypqdrj4yhxynapsf462ypgv3clpf74q56k6r32847a4cp9dlx6n8ew8hyqdv6ydv5q8yt9vhn8ktv";
        byte[] accountPrvKeyBytes = Bech32.decode(accountPrvKey).data;

        Wallet wallet = Wallet.createFromAccountKey(Networks.testnet(), accountPrvKeyBytes);
        Account account = wallet.getAccountAtIndex(0);

        assertThat(wallet.getBaseAddressString(0)).isEqualTo("addr_test1qq7x3pklemucwtw6qcym6trkcfmenslhnsq7e8cag7h82507mkje2hyjgc8zr00z835as0kw5hwa48h3vnrctjpu4a8qy8lf5r");
        assertThat(account.changeAddress()).isEqualTo("addr_test1qr430yp4r7mgsj5t34pgp2zcv37w8arj62gyqtdayheejz87mkje2hyjgc8zr00z835as0kw5hwa48h3vnrctjpu4a8qy27g5x");
        assertThat(account.stakeAddress()).isEqualTo("stake_test1urldmfv4tjfyvr3phh3rc6wc8m82thw6nmckf3u9eq727ns3kwf9u");
        assertThat(account.drepId()).isEqualTo("drep1y2zjw9adazlmychc6wlz4k8qa8g5jcjqwujg0jws9r6v9egvasg8r");
        assertThat(account.committeeColdKey().id()).isEqualTo("cc_cold1ztslrgxse5awd9yx9csqrcmystzw6rd88tva4tw7lqkjrtc6d4ezh");
        assertThat(account.committeeHotKey().id()).isEqualTo("cc_hot1qgmfvk4g7vfquys52cdrx59ez948q337jhp5ycde3umlprqulcyff");

        assertThat(wallet.getRootKeyPair()).isEmpty();
    }

    @Test
    void testGetRootKeyWhenFromMnemonic() {
        String mnemonic = "fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith";

        String expectedRootKey = "root_xsk1xrvg8kfpdlaluwstt0twcajgavqcgkczmav6lffvgfmrxeqwq3qer3rasrjdj9f663xa98xcu4a28zuv5cks5lytdvfezn49ycrndz2mptat9v0t5eafsdj9rpe4lcxndvys0v6qahq8v0flv9ycpav8ks46k8xh";
        byte[] expectedRootKeyBytes = Bech32.decode(expectedRootKey).data;

        Wallet wallet = Wallet.createFromMnemonic(Networks.testnet(), mnemonic);

        assertThat(HexUtil.encodeHexString(wallet.getRootPvtKey().get())).isEqualTo(HexUtil.encodeHexString(expectedRootKeyBytes));
    }

    @Test
    void testGetRootKeyWhenFromRootKey() {
        String expectedRootKey = "root_xsk1xrvg8kfpdlaluwstt0twcajgavqcgkczmav6lffvgfmrxeqwq3qer3rasrjdj9f663xa98xcu4a28zuv5cks5lytdvfezn49ycrndz2mptat9v0t5eafsdj9rpe4lcxndvys0v6qahq8v0flv9ycpav8ks46k8xh";
        byte[] expectedRootKeyBytes = Bech32.decode(expectedRootKey).data;

        Wallet wallet = Wallet.createFromRootKey(Networks.testnet(), expectedRootKeyBytes);

        assertThat(HexUtil.encodeHexString(wallet.getRootPvtKey().get())).isEqualTo(HexUtil.encodeHexString(expectedRootKeyBytes));
    }

    @Test
    void testAccountWhenFromRootKey_account_2() {
        //original phrase
        //fresh apple bus punch dynamic what arctic elevator logic hole survey hunt better adapt helmet fat refuse season enter category tomato mule capable faith

        String rootKey = "root_xsk1xrvg8kfpdlaluwstt0twcajgavqcgkczmav6lffvgfmrxeqwq3qer3rasrjdj9f663xa98xcu4a28zuv5cks5lytdvfezn49ycrndz2mptat9v0t5eafsdj9rpe4lcxndvys0v6qahq8v0flv9ycpav8ks46k8xh";
        byte[] rootKeyBytes = Bech32.decode(rootKey).data;

        Wallet wallet = Wallet.createFromRootKey(Networks.testnet(), rootKeyBytes, 2);

        assertThat(wallet.getBaseAddressString(0)).isEqualTo("addr_test1qpyf7633hxe5t5lwr20dre9xy54nuhl4qf53rvpcs3geq5mgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sfpmuak");
        assertThat(wallet.getAccountAtIndex(0).changeAddress()).isEqualTo("addr_test1qqphrf9wtwwhghevuy0gd95cldmdzn3gyrganlh7g8d8hvrgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7s6xvl48");
        assertThat(wallet.getAccountAtIndex(0).stakeAddress()).isEqualTo("stake_test1up5zq38z7v9wy2xcxu4c59ktxlyc4y6sdw6rg75ht2daz0guncpsl");
        assertThat(wallet.getAccountAtIndex(0).drepId()).isEqualTo("drep1ygmmj4nqjzvaa39qxj6w7pxpx4u62f8lvnu4xzjwjk8segg56qful");
        assertThat(wallet.getAccountAtIndex(0).committeeColdKey().id()).isEqualTo("cc_cold1zftrxuz0u938d8mu5ndawz8yv6rkurfq8flrus6mcr32fhspk4ju3");
        assertThat(wallet.getAccountAtIndex(0).committeeHotKey().id()).isEqualTo("cc_hot1qtx827hr45paddz5h6w7k0rg40vpc79262dff8r42rlwxgq4jj6dg");

        assertThat(wallet.getAccountAtIndex(8).baseAddress()).isEqualTo("addr_test1qqacfd33e3qa20vqmv00qx6xftxhcqwhlr905l4rchf5j0rgypzw9uc2ug5dsdet3gtvkd7f32f4q6a5x3afwk5m6y7sa0sjfq");

        assertThat(HexUtil.encodeHexString(wallet.getRootPvtKey().get())).isEqualTo(HexUtil.encodeHexString(rootKeyBytes));
    }

    @Test
    void testAccountWhenFromAccountKey_15words_acc4_index3() {
        //Original mnemonic
        //top exact spice seed cloud birth orient bracket happy cat section girl such outside elder

        String accountKey = "acct_xsk1azc6gn5zkdprp4gkapmhdckykphjl62rm9224699ut5z6xcaa9p4hv5hmjfgcrzk72tnsqh6dw0njekdjpsv8nv5h5hk6lpd4ag62zenwhzqs205kfurd7kgs8fm5gx4l4j8htutwj060kyp5y5kgw55qc8lsltd";

        Wallet wallet = Wallet.createFromAccountKey(Networks.testnet(), Bech32.decode(accountKey).data);

        assertThat(wallet.getAccountAtIndex(3).baseAddress()).isEqualTo("addr_test1qz3cw2uuwjwhdjwyf32pre79kca5mf722nm09a6welje4edx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fshq2mdz");
        assertThat(wallet.getAccountAtIndex(3).changeAddress()).isEqualTo("addr_test1qq8x3y83vfsx6zrcnuyseact0z85fyev6vjhrk5nfxzm57dx0xezf9ug8educ8pv3gp2ujflk6jcatz9muf4jckak3fsf894c3");
        assertThat(wallet.getAccountAtIndex(3).stakeAddress()).isEqualTo("stake_test1uzn8nv3yj7yruk7vrskg5q4wfylmdfvw43za7y6evtwmg5c47c0hf");
        assertThat(wallet.getAccountAtIndex(3).drepId()).isEqualTo("drep1yt07pz022vfqzwr40jrch8pwcd09g2s2nqqsffdjag9d33g2czftn");
        assertThat(wallet.getAccountAtIndex(3).committeeColdKey().id()).isEqualTo("cc_cold1ztw27g74fpg4n5wl556c4spzx8n5gz6njtf8lqcrehgvqaswaasts");
        assertThat(wallet.getAccountAtIndex(3).committeeHotKey().id()).isEqualTo("cc_hot1qgjg5c946hgkjh5mvpw8x4q7v9m0zykjvw0wrq5pfm04tvqqh35qa");
    }

    @Test
    void testAccountFromAccountKey_128bytes_throwsException() {
        //Added random 32 bytes at the end to test with a 128 bytes key
        String accountKey = "e8b1a44e82b34230d516e87776e2c4b06f2fe943d954aae8a5e2e82d1b1de9435bb297dc928c0c56f2973802fa6b9f3966cd9060c3cd94bd2f6d7c2daf51a50b3375c40829f4b27836fac881d3ba20d5fd647baf8b749fa7d881a129643a9406c333ef7429361bdb1414e15e054f6654bce419d26057d0e38d76993f9c3ab71f";

        var network = Networks.testnet();
        var acctKeyBytes = HexUtil.decodeHexString(accountKey);
        assertThrows(WalletException.class, () -> {
            Wallet.createFromAccountKey(network, acctKeyBytes);
        });
    }

    @Test
    void testAccountFromRootKey_128Bytes_throwsException() {
        //Last 32 bytes in 128 bytes array are arbitary bytes
        String rootKey128Bytes = "48c0062f7d36bb4f8194beb2762d4e323fd84dd947e7d09db8f8b454b74a105560631379e7b976f41e165133978ecd56fe65114f5ba6aaafaa0d482a48e71edec6377bc291444b063f64d59b955447c9fb3a3026e43f9052d82a792d304fd644c333ef7429361bdb1414e15e054f6654bce419d26057d0e38d76993f9c3ab71f";
        byte[] rootKey = HexUtil.decodeHexString(rootKey128Bytes);

        var network = Networks.testnet();
        assertThrows(WalletException.class, () -> {
            Wallet.createFromRootKey(network, rootKey);
        });
    }
}
