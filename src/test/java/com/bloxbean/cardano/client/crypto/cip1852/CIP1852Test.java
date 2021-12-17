package com.bloxbean.cardano.client.crypto.cip1852;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CIP1852Test {

    @Test
    void getPaymentVerificationKey() {
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath);
        byte[] pvtKeyBytes  = hdKeyPair.getPrivateKey().getBytes();
        byte[] publicKey  = hdKeyPair.getPublicKey().getBytes();

        String publicAdd = Bech32.encode(publicKey, "addr_xvk");
        assertThat(publicAdd).isEqualTo("addr_xvk1r30n0pv6d40kzzl4e6xje2y7c446gw2x9sgnms3vv62tx264tf5n9lxnuxqc5xpqlg30dtlq0tf0fav4kafsge6u24x296vg85l399cx2uv4k");
    }

    @Test
    void getStakeVerificationKey() {
        String mnemonicPhrase = "indicate traffic belt syrup chief accident put upset present short drink bus glide warm roof";

        DerivationPath derivationPath = DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(2, false))
                .index(new Segment(0, false))
                .build();

        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonicPhrase, derivationPath);
        byte[] pvtKeyBytes  = hdKeyPair.getPrivateKey().getBytes();
        byte[] publicKey  = hdKeyPair.getPublicKey().getBytes();

        String publicAdd = Bech32.encode(publicKey, "stake_xvk");
        assertThat(publicAdd).isEqualTo("stake_xvk143rnqx89nnmlt8w5kerl03hvl2reuv02l450wjs2vd74cezqx2mja08euhtd7gejfylpfe8j3vgejh25nu9nwqgfx0qy8d40llf9h6qeg2t4z");

    }
}
