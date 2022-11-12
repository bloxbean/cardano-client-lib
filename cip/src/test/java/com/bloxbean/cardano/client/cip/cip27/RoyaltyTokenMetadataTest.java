package com.bloxbean.cardano.client.cip.cip27;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoyaltyTokenMetadataTest {

    @Test
    void createRoyaltyTokenMetadataLongAddress() {
        RoyaltyToken royaltyToken = RoyaltyToken.create()
                .address("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y")
                .rate(0.05);

        RoyaltyTokenMetadata royaltyTokenMetadata = RoyaltyTokenMetadata.create()
                .royaltyToken(royaltyToken);

        String json = royaltyTokenMetadata.toString();

        System.out.println(json);

        RoyaltyToken royaltyToken1 = royaltyTokenMetadata.getRoyaltyToken();

        assertThat(royaltyToken1).isNotNull();

        assertThat(royaltyToken1.getAddress()).isEqualTo(royaltyToken.getAddress());
        assertThat(royaltyToken1.getRate()).isEqualTo(royaltyToken.getRate());

        //serialize & deserialize and check serialize bytes
        byte[] serializeBytes = royaltyTokenMetadata.serialize();
        RoyaltyTokenMetadata deSerRoyaltyTokenMetadata = RoyaltyTokenMetadata.create(serializeBytes);

        byte[] deSerializeBytes = deSerRoyaltyTokenMetadata.serialize();
        assertThat(deSerializeBytes).isEqualTo(serializeBytes);
    }

    @Test
    void createRoyaltyTokenMetadataShortAddress() {
        RoyaltyToken royaltyToken = RoyaltyToken.create()
                .address("addr1v9nevxg9wunfck0gt7hpxuy0elnqygglme3u6l3nn5q5gnq5dc9un")
                .rate(0.05);

        RoyaltyTokenMetadata royaltyTokenMetadata = RoyaltyTokenMetadata.create()
                .royaltyToken(royaltyToken);

        String json = royaltyTokenMetadata.toString();

        System.out.println(json);

        RoyaltyToken royaltyToken1 = royaltyTokenMetadata.getRoyaltyToken();

        assertThat(royaltyToken1).isNotNull();

        assertThat(royaltyToken1.getAddress()).isEqualTo(royaltyToken.getAddress());
        assertThat(royaltyToken1.getRate()).isEqualTo(royaltyToken.getRate());

        //serialize & deserialize and check serialize bytes
        byte[] serializeBytes = royaltyTokenMetadata.serialize();
        RoyaltyTokenMetadata deSerRoyaltyTokenMetadata = RoyaltyTokenMetadata.create(serializeBytes);

        byte[] deSerializeBytes = deSerRoyaltyTokenMetadata.serialize();
        assertThat(deSerializeBytes).isEqualTo(serializeBytes);
    }
}
