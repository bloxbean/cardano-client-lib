package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class PoolRegistrationTest {

    @Test
    void serDeser_whenIpV4() throws CborDeserializationException, CborSerializationException, CborException, UnknownHostException {
        String regCbor = "8a03581ced40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a45820b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b1b00000600aea7d0001a1dcd6500d81e820d1903e8581de1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134d9010281581cf3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134838400190bb94436b12923f68400190bb944037dfcb6f68400190bb944343fe1bef6827468747470733a2f2f6769742e696f2f4a7474546c582051700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41";

        PoolRegistration poolRegistration = PoolRegistration.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(regCbor)));

        byte[] serializeBytes = CborSerializationUtil.serialize(poolRegistration.serialize());
        PoolRegistration dePoolRegistration = PoolRegistration.deserialize(CborSerializationUtil.deserialize(serializeBytes));

        assertThat(dePoolRegistration).isEqualTo(poolRegistration);
        assertThat(HexUtil.encodeHexString(dePoolRegistration.getOperator())).isEqualTo("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");
        assertThat(HexUtil.encodeHexString(dePoolRegistration.getVrfKeyHash())).isEqualTo("b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b");
        assertThat(dePoolRegistration.getPledge()).isEqualTo(BigInteger.valueOf(6600000000000L));
        assertThat(dePoolRegistration.getCost()).isEqualTo(BigInteger.valueOf(500000000));
        assertThat(dePoolRegistration.getMargin()).isEqualTo(new UnitInterval(BigInteger.valueOf(13), BigInteger.valueOf(1000)));
        assertThat(dePoolRegistration.getRewardAccount()).isEqualTo("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134");
        assertThat(dePoolRegistration.getPoolOwners().iterator().next()).isEqualTo("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134");
        assertThat(dePoolRegistration.getPoolMetadataUrl()).isEqualTo("https://git.io/JttTl");
        assertThat(dePoolRegistration.getPoolMetadataHash()).isEqualTo("51700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41");
        assertThat(dePoolRegistration.getRelays()).contains(new SingleHostAddr(3001, (Inet4Address) Inet4Address.getByName("54.177.41.35"),null),
                new SingleHostAddr(3001, (Inet4Address) Inet4Address.getByName("3.125.252.182"),null),
                new SingleHostAddr(3001, (Inet4Address) Inet4Address.getByName("3.125.252.182"),null));

        assertThat(HexUtil.encodeHexString(CborSerializationUtil.serialize(dePoolRegistration.serialize()))).isEqualTo(regCbor);

    }

    @Test
    void serDeSer_whenIpv4Dns() throws CborDeserializationException, CborSerializationException, CborException, UnknownHostException {
        String regCbor = "8a03581c0f8c53d63eecfc1d63d24944db35f9b6dcff7815ec7111c7fd1186fc582007ba891dfb5f78901ec754cce7acb1b9e988fb128832d11b7d416e36e8293a281b0000000271d949001a1443fd00d81e820114581de144c99ecec6dcf2b3c961d0c20f2597a9851234c7224f7d144a898ef5d9010281581c44c99ecec6dcf2b3c961d0c20f2597a9851234c7224f7d144a898ef58283011917707272656c61792e6261636b646174612e6f726784001917724455d13369f682782168747470733a2f2f6765742d6164612e6e65742f706f6f6c6d6574612e6a736f6e582013cc2616f7ffd4ad1f93b3ad23c1a5893688fd79976c0d6c94082f5e2f35a35e";
        PoolRegistration poolRegistration = PoolRegistration.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(regCbor)));

        byte[] serializeBytes = CborSerializationUtil.serialize(poolRegistration.serialize());
        PoolRegistration dePoolRegistration = PoolRegistration.deserialize(CborSerializationUtil.deserialize(serializeBytes));

        assertThat(dePoolRegistration).isEqualTo(poolRegistration);
        assertThat(HexUtil.encodeHexString(CborSerializationUtil.serialize(dePoolRegistration.serialize()))).isEqualTo(regCbor);

        assertThat(dePoolRegistration.getRelays()).contains(new SingleHostName(6000, "relay.backdata.org"),
                new SingleHostAddr(6002, (Inet4Address) Inet4Address.getByName("85.209.51.105"),null));
    }

    @Test
    void getPoolId() {
        String registrationCbor = "8a03581ce0505e5a0b54a0e3dbab40ae9e558bbf99e3ceb758156437d094de325820e3a337111f213197329197c469156f1df986189f29dfaa527653b6028cc8be9f1a009896801a1443fd00d81e82011832581de08552fdacebcc11730c500e35bd7678e39b13f119e45cc569a09278d481581c8552fdacebcc11730c500e35bd7678e39b13f119e45cc569a09278d4818400190bba447f000001f6827820687474703a2f2f79616369646576696b69742e6e6f64652f6d6574616461746158206bf124f217d0e5a0a8adb1dbd8540e1334280d49ab861127868339f43b3948af";

        PoolRegistration poolRegistration = PoolRegistration.deserialize(registrationCbor);
        assertThat(poolRegistration.getBech32PoolId()).isEqualTo("pool1upg9ukst2jsw8katgzhfu4vth7v78n4htq2kgd7sjn0ryhsf2yl");
    }
}
