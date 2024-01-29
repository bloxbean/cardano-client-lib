package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68File;
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

class CIP68RFTTest {

    @Test
    void testAssetName() {
        CIP68RFT cip68RFT = CIP68RFT.create()
                .name("GiveYouUp");

        byte[] prefixBytes = CIP67AssetNameUtil.labelToPrefix(444);
        String prefix = HexUtil.encodeHexString(prefixBytes);

        assertEquals(444, cip68RFT.getAssetNameLabel());
        assertThat(cip68RFT.getAssetNameAsHex()).isEqualTo("0x001bc28047697665596f755570");
        assertThat(cip68RFT.getAssetNameAsHex()).startsWith("0x" + prefix);
    }

    @Test
    public void testCIP68NFT_properties() throws Exception {
        CIP68RFT rft = CIP68RFT.create()
                .name("GiveYouUp")
                .image("https://xyz.com/image1.png")
                .decimals(6)
                .description("This is my first CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image1.png")
                ).addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image2.png")
                        .src("https://xyz.com/image2.png")
                )
                .property("key1", "key1Value")
                .property("key2", List.of("key2Value", "key2Value2"))
                .property("long_desc", "This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.")
                .property("key5", "key5Value")
                .property(BytesPlutusData.of("key6"), BigIntPlutusData.of(1001))
                .property("key7", 200)
                .property("key8", 500L)
                .property(ListPlutusData.of(BytesPlutusData.of("key9"), BytesPlutusData.of("key10")), BytesPlutusData.of("key11"));

        System.out.println(rft.getFriendlyAssetName());

        assertThat(rft.getAssetNameAsHex()).isEqualTo("0x001bc28047697665596f755570");
        assertThat(rft.getFriendlyAssetName()).isEqualTo("(444) GiveYouUp");
        assertThat(rft.getName()).isEqualTo("GiveYouUp");
        assertThat(rft.getAssetNameAsBytes()).isEqualTo(HexUtil.decodeHexString("0x001bc28047697665596f755570"));
        assertThat(rft.getImage()).isEqualTo("https://xyz.com/image1.png");
        assertThat(rft.getDecimals()).isEqualTo(6);
        assertThat(rft.getDescription()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(rft.getDatum().getVersion()).isEqualTo(2);
        assertThat(rft.getFiles()).hasSize(2);
        assertThat(rft.getFiles().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(rft.getFiles().get(0).getName()).isEqualTo("image1.png");
        assertThat(rft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png"));
        assertThat(rft.getFiles().get(1).getMediaType()).isEqualTo("image/png");
        assertThat(rft.getFiles().get(1).getName()).isEqualTo("image2.png");
        assertThat(rft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png"));
        assertThat(rft.getStringProperty("key1")).isEqualTo("key1Value");
        assertThat(rft.getListProperty("key2")).isEqualTo(List.of("key2Value", "key2Value2"));
        assertThat(rft.getStringProperty("long_desc")).isEqualTo("This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.");
        assertThat(rft.getBigIntegerProperty("key6")).isEqualTo(BigInteger.valueOf(1001));
        assertThat(rft.getIntProperty("key7")).isEqualTo(200);
        assertThat(rft.getLongProperty("key8")).isEqualTo(500L);
        assertThat(rft.getProperty(ListPlutusData.of(BytesPlutusData.of("key9"), BytesPlutusData.of("key10")))).isEqualTo(BytesPlutusData.of("key11"));
    }

    @Test
    public void testCIP68RFT_multipleSrcs() {
        CIP68RFT rft = CIP68RFT.create()
                .name("GiveYouUp")
                .image("https://xyz.com/image1.png")
                .description("This is my first CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image1.png")
                        .src("https://xyz.com/image1_1.png")
                        .src("https://xyz.com/image1_2.png")
                ).addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image2.png")
                        .setsrcs(List.of("https://xyz.com/image2.png", "https://xyz.com/image2_1.png", "https://xyz.com/image2_2.png"))
                )
                .property("key1", "key1Value");


        assertThat(rft.getAssetNameAsHex()).isEqualTo("0x001bc28047697665596f755570");
        assertThat(rft.getName()).isEqualTo("GiveYouUp");
        assertThat(rft.getAssetNameAsBytes()).isEqualTo(HexUtil.decodeHexString("0x001bc28047697665596f755570"));
        assertThat(rft.getImage()).isEqualTo("https://xyz.com/image1.png");
        assertThat(rft.getDescription()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(rft.getFiles()).hasSize(2);
        assertThat(rft.getFiles().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(rft.getFiles().get(0).getName()).isEqualTo("image1.png");
        assertThat(rft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png", "https://xyz.com/image1_1.png", "https://xyz.com/image1_2.png"));
        assertThat(rft.getFiles().get(1).getMediaType()).isEqualTo("image/png");
        assertThat(rft.getFiles().get(1).getName()).isEqualTo("image2.png");
        assertThat(rft.getFiles().get(1).getSrcs()).isEqualTo(List.of("https://xyz.com/image2.png", "https://xyz.com/image2_1.png", "https://xyz.com/image2_2.png"));

        assertThat(rft.getStringProperty("key1")).isEqualTo("key1Value");

    }

    @Test
    public void testCIP68RFT_verifyMetadataJson() throws Exception {
        CIP68RFT rft = CIP68RFT.create()
                .name("GiveYouUp")
                .image("https://xyz.com/image1.png")
                .description("This is my first CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image1.png")
                        .src("https://xyz.com/image1_1.png")
                        .src("https://xyz.com/image1_2.png")
                ).addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image2.png")
                        .setsrcs(List.of("https://xyz.com/image2.png", "https://xyz.com/image2_1.png", "https://xyz.com/image2_2.png"))
                ).property("key1", "key1Value");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode metadataJson = objectMapper.readTree(rft.getMetadataJson());

        assertThat(metadataJson.get("name").asText()).isEqualTo("GiveYouUp");
        assertThat(metadataJson.get("image").asText()).isEqualTo("https://xyz.com/image1.png");
        assertThat(metadataJson.get("description").asText()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(metadataJson.get("files").get(0).get("mediaType").asText()).isEqualTo("image/png");
        assertThat(metadataJson.get("files").get(0).get("name").asText()).isEqualTo("image1.png");
        assertThat(metadataJson.get("files").get(0).get("src").get(0).asText()).isEqualTo("https://xyz.com/image1.png");
        assertThat(metadataJson.get("files").get(0).get("src").get(1).asText()).isEqualTo("https://xyz.com/image1_1.png");
        assertThat(metadataJson.get("files").get(0).get("src").get(2).asText()).isEqualTo("https://xyz.com/image1_2.png");
        assertThat(metadataJson.get("files").get(1).get("mediaType").asText()).isEqualTo("image/png");
        assertThat(metadataJson.get("files").get(1).get("name").asText()).isEqualTo("image2.png");
        assertThat(metadataJson.get("files").get(1).get("src").get(0).asText()).isEqualTo("https://xyz.com/image2.png");
        assertThat(metadataJson.get("files").get(1).get("src").get(1).asText()).isEqualTo("https://xyz.com/image2_1.png");
        assertThat(metadataJson.get("files").get(1).get("src").get(2).asText()).isEqualTo("https://xyz.com/image2_2.png");
        assertThat(metadataJson.get("key1").asText()).isEqualTo("key1Value");
    }

    @Test
    public void testCIP68NFT_verifyDatumPlutusData() throws Exception {
        CIP68RFT rft = CIP68RFT.create()
                .name("GiveYouUp")
                .image("https://xyz.com/image1.png")
                .description("This is my first CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image1.png")
                        .src("https://xyz.com/image1_1.png")
                        .src("https://xyz.com/image1_2.png")
                ).addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image2.png")
                        .setsrcs(List.of("https://xyz.com/image2.png", "https://xyz.com/image2_1.png", "https://xyz.com/image2_2.png"))
                ).property("key1", "key1Value");

        rft.getDatum().extra(BytesPlutusData.of("This is extra data"));

        System.out.println(PlutusDataJsonConverter.toJson(rft.getDatumAsPlutusData()));

        var datumPlutusData = rft.getDatumAsPlutusData();

        var dataList = datumPlutusData.getData().getPlutusDataList();
        var metadataMap = (MapPlutusData) dataList.get(0);
        var version = ((BigIntPlutusData) dataList.get(1)).getValue().intValue();
        var extra = dataList.get(2);

        System.out.println(rft.getDatum().getMetadataJson());

        assertThat(datumPlutusData.getAlternative()).isEqualTo(0);
        assertThat(version).isEqualTo(2);
        assertThat(extra).isEqualTo(BytesPlutusData.of("This is extra data"));
        assertThat(metadataMap.getMap().get(BytesPlutusData.of("name"))).isEqualTo(BytesPlutusData.of("GiveYouUp"));
    }

    @Test
    @SneakyThrows
    public void deserialize_fromDatumBytes() {
        String datum = "d8799fa7446b657931496b65793156616c7565446b6579329f496b65793256616c75654a6b65793256616c756532ff446b657935496b65793556616c7565446e616d654947697665596f7555704566696c65739fa343737263581a68747470733a2f2f78797a2e636f6d2f696d616765312e706e67446e616d654a696d616765312e706e67496d656469615479706549696d6167652f706e67ff45696d616765581a68747470733a2f2f78797a2e636f6d2f696d616765312e706e674b6465736372697074696f6e581b54686973206973206d79206669727374204349502d3638204e4654024a45787472612074657874ff";

        CIP68RFT rft = CIP68RFT.fromDatum(HexUtil.decodeHexString(datum));

        assertThat(rft.getAssetNameAsHex()).isEqualTo("0x001bc28047697665596f755570");
        assertThat(rft.getAssetNameLabel()).isEqualTo(444);
        assertThat(rft.getName()).isEqualTo("GiveYouUp");

        assertThat(rft.getReferenceToken().getAssetNameAsHex()).isEqualTo("0x000643b047697665596f755570");
        assertThat(rft.getReferenceToken().getAssetNameLabel()).isEqualTo(100);
        assertThat(rft.getReferenceToken().getName()).isEqualTo("GiveYouUp");

        assertThat(rft.getImage()).isEqualTo("https://xyz.com/image1.png");
        assertThat(rft.getDescription()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(rft.getStringProperty("key1")).isEqualTo("key1Value");
        assertThat(rft.getListProperty("key2")).isEqualTo(List.of("key2Value", "key2Value2"));
        assertThat(rft.getStringProperty("key5")).isEqualTo("key5Value");
        assertThat(rft.getFiles()).hasSize(1);
        assertThat(rft.getFiles().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(rft.getFiles().get(0).getName()).isEqualTo("image1.png");
        assertThat(rft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png"));

        assertThat(rft.getDatum().getMetadataJson()).isNotNull();
        assertThat(rft.getDatumAsPlutusData()).isNotNull();
    }

}
