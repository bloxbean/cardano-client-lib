package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CIP68FTTest {

    @Test
    void testAssetName() {
        CIP68FT cip68FT = CIP68FT.create()
                .name("GiveYouUp");

        byte[] prefixBytes = CIP67AssetNameUtil.labelToPrefix(333);
        String prefix = HexUtil.encodeHexString(prefixBytes);

        assertEquals(333, cip68FT.getAssetNameLabel());
        assertThat(cip68FT.getAssetNameAsHex()).isEqualTo("0x0014df1047697665596f755570");
        assertThat(cip68FT.getAssetNameAsHex()).startsWith("0x" + prefix);
    }

    @Test
    public void testCIP68NFT_properties() throws Exception {
        CIP68FT ft = CIP68FT.create()
                .name("GiveYouUp")
                .ticker("GYU")
                .url("https://xyz.com")
                .logo("https://xyz.com/logo.png")
                .description("This is my first CIP-68 FT")
                .property("key1", "key1Value")
                .property("key2", List.of("key2Value", "key2Value2"))
                .property("long_desc", "This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.")
                .property("key5", "key5Value")
                .property(BytesPlutusData.of("key6"), BigIntPlutusData.of(1001))
                .property("key7", 200)
                .property("key8", 500L)
                .property(ListPlutusData.of(BytesPlutusData.of("key9"), BytesPlutusData.of("key10")), BytesPlutusData.of("key11"));


        assertThat(ft.getAssetNameAsHex()).isEqualTo("0x0014df1047697665596f755570");
        assertThat(ft.getName()).isEqualTo("GiveYouUp");
        assertThat(ft.getAssetNameAsBytes()).isEqualTo(HexUtil.decodeHexString("0x0014df1047697665596f755570"));
        assertThat(ft.getTicker()).isEqualTo("GYU");
        assertThat(ft.getURL()).isEqualTo("https://xyz.com");
        assertThat(ft.getLogo()).isEqualTo("https://xyz.com/logo.png");
        assertThat(ft.getDescription()).isEqualTo("This is my first CIP-68 FT");
        assertThat(ft.getStringProperty("key1")).isEqualTo("key1Value");
        assertThat(ft.getListProperty("key2")).isEqualTo(List.of("key2Value", "key2Value2"));
        assertThat(ft.getStringProperty("long_desc")).isEqualTo("This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.");
        assertThat(ft.getBigIntegerProperty("key6")).isEqualTo(BigInteger.valueOf(1001));
        assertThat(ft.getIntProperty("key7")).isEqualTo(200);
        assertThat(ft.getLongProperty("key8")).isEqualTo(500L);
        assertThat(ft.getProperty(ListPlutusData.of(BytesPlutusData.of("key9"), BytesPlutusData.of("key10")))).isEqualTo(BytesPlutusData.of("key11"));
    }

    @Test
    public void testCIP68FT_verifyMetadataJson() throws Exception {
        CIP68FT ft = CIP68FT.create()
                .name("GiveYouUp")
                .ticker("GYU")
                .url("https://xyz.com")
                .logo("https://xyz.com/logo.png")
                .description("This is my first CIP-68 FT")
                .property("key1", "key1Value");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode metadataJson = objectMapper.readTree(ft.getMetadataJson());

        assertThat(metadataJson.get("name").asText()).isEqualTo("GiveYouUp");
        assertThat(metadataJson.get("ticker").asText()).isEqualTo("GYU");
        assertThat(metadataJson.get("url").asText()).isEqualTo("https://xyz.com");
        assertThat(metadataJson.get("logo").asText()).isEqualTo("https://xyz.com/logo.png");
        assertThat(metadataJson.get("description").asText()).isEqualTo("This is my first CIP-68 FT");
    }

    @Test
    public void testCIP68FT_verifyDatumPlutusData() throws Exception {
        CIP68FT ft = CIP68FT.create()
                .name("GiveYouUp")
                .ticker("GYU")
                .url("https://xyz.com")
                .logo("https://xyz.com/logo.png")
                .description("This is my first CIP-68 FT")
                .property("key1", "key1Value");

        ft.getDatum().extra(BytesPlutusData.of("This is extra data"));

        System.out.println(PlutusDataJsonConverter.toJson(ft.getDatumAsPlutusData()));

        var datumPlutusData = ft.getDatumAsPlutusData();

        var dataList = datumPlutusData.getData().getPlutusDataList();
        var metadataMap = (MapPlutusData) dataList.get(0);
        var version = ((BigIntPlutusData) dataList.get(1)).getValue().intValue();
        var extra = dataList.get(2);

        System.out.println(ft.getDatum().getMetadataJson());

        assertThat(datumPlutusData.getAlternative()).isEqualTo(0);
        assertThat(version).isEqualTo(1);
        assertThat(extra).isEqualTo(BytesPlutusData.of("This is extra data"));
        assertThat(metadataMap.getMap().get(BytesPlutusData.of("name"))).isEqualTo(BytesPlutusData.of("GiveYouUp"));
    }

    @Test
    @SneakyThrows
    public void deserialize_fromDatumBytes() {
        String datum = "d8799fa74375726c4f68747470733a2f2f78797a2e636f6d446b657931496b65793156616c7565446c6f676f581868747470733a2f2f78797a2e636f6d2f6c6f676f2e706e67446e616d654947697665596f755570467469636b65724347595548646563696d616c73064b6465736372697074696f6e581a54686973206973206d79206669727374204349502d3638204654014a45787472612074657874ff";

        CIP68FT cip68FT = CIP68FT.fromDatum(HexUtil.decodeHexString(datum));

        assertThat(cip68FT.getAssetNameAsHex()).isEqualTo("0x0014df1047697665596f755570");
        assertThat(cip68FT.getAssetNameLabel()).isEqualTo(333);
        assertThat(cip68FT.getName()).isEqualTo("GiveYouUp");

        assertThat(cip68FT.getReferenceToken().getAssetNameAsHex()).isEqualTo("0x000643b047697665596f755570");
        assertThat(cip68FT.getReferenceToken().getAssetNameLabel()).isEqualTo(100);
        assertThat(cip68FT.getReferenceToken().getName()).isEqualTo("GiveYouUp");

        assertThat(cip68FT.getTicker()).isEqualTo("GYU");
        assertThat(cip68FT.getURL()).isEqualTo("https://xyz.com");
        assertThat(cip68FT.getLogo()).isEqualTo("https://xyz.com/logo.png");
        assertThat(cip68FT.getDescription()).isEqualTo("This is my first CIP-68 FT");
        assertThat(cip68FT.getStringProperty("key1")).isEqualTo("key1Value");
        assertThat(cip68FT.getDatum().getMetadataJson()).isNotNull();
        assertThat(cip68FT.getDatumAsPlutusData()).isNotNull();
    }

}
