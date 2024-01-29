package com.bloxbean.cardano.client.cip.cip68;

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

class CIP68NFTTest {

    @Test
    void testAssetName() {
        CIP68NFT cip68NFT = CIP68NFT.create()
                .name("GiveYouUp");

        assertEquals(222, cip68NFT.getAssetNameLabel());
        assertThat(cip68NFT.getAssetNameAsHex()).isEqualTo("0x000de14047697665596f755570");
    }

    @Test
    public void testCIP68NFT_properties() throws Exception {
        CIP68NFT nft = CIP68NFT.create()
                .name("GiveYouUp")
                .image("https://xyz.com/image1.png")
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


        assertThat(nft.getAssetNameAsHex()).isEqualTo("0x000de14047697665596f755570");
        assertThat(nft.getName()).isEqualTo("GiveYouUp");
        assertThat(nft.getAssetNameAsBytes()).isEqualTo(HexUtil.decodeHexString("0x000de14047697665596f755570"));
        assertThat(nft.getImage()).isEqualTo("https://xyz.com/image1.png");
        assertThat(nft.getDescription()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(nft.getFiles()).hasSize(2);
        assertThat(nft.getFiles().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(nft.getFiles().get(0).getName()).isEqualTo("image1.png");
        assertThat(nft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png"));
        assertThat(nft.getFiles().get(1).getMediaType()).isEqualTo("image/png");
        assertThat(nft.getFiles().get(1).getName()).isEqualTo("image2.png");
        assertThat(nft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png"));
        assertThat(nft.getStringProperty("key1")).isEqualTo("key1Value");
        assertThat(nft.getListProperty("key2")).isEqualTo(List.of("key2Value", "key2Value2"));
        assertThat(nft.getStringProperty("long_desc")).isEqualTo("This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.This is a long desc.");
        assertThat(nft.getBigIntegerProperty("key6")).isEqualTo(BigInteger.valueOf(1001));
        assertThat(nft.getIntProperty("key7")).isEqualTo(200);
        assertThat(nft.getLongProperty("key8")).isEqualTo(500L);
        assertThat(nft.getProperty(ListPlutusData.of(BytesPlutusData.of("key9"), BytesPlutusData.of("key10")))).isEqualTo(BytesPlutusData.of("key11"));
    }

    @Test
    public void testCIP68NFT_multipleSrcs() throws Exception {
        CIP68NFT nft = CIP68NFT.create()
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


        assertThat(nft.getAssetNameAsHex()).isEqualTo("0x000de14047697665596f755570");
        assertThat(nft.getName()).isEqualTo("GiveYouUp");
        assertThat(nft.getAssetNameAsBytes()).isEqualTo(HexUtil.decodeHexString("0x000de14047697665596f755570"));
        assertThat(nft.getImage()).isEqualTo("https://xyz.com/image1.png");
        assertThat(nft.getDescription()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(nft.getFiles()).hasSize(2);
        assertThat(nft.getFiles().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(nft.getFiles().get(0).getName()).isEqualTo("image1.png");
        assertThat(nft.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png", "https://xyz.com/image1_1.png", "https://xyz.com/image1_2.png"));
        assertThat(nft.getFiles().get(1).getMediaType()).isEqualTo("image/png");
        assertThat(nft.getFiles().get(1).getName()).isEqualTo("image2.png");
        assertThat(nft.getFiles().get(1).getSrcs()).isEqualTo(List.of("https://xyz.com/image2.png", "https://xyz.com/image2_1.png", "https://xyz.com/image2_2.png"));

        assertThat(nft.getStringProperty("key1")).isEqualTo("key1Value");

    }

    @Test
    public void testCIP68NFT_verifyMetadataJson() throws Exception {
        CIP68NFT nft = CIP68NFT.create()
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
        JsonNode metadataJson = objectMapper.readTree(nft.getMetadataJson());

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
        CIP68NFT nft = CIP68NFT.create()
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

        nft.getDatum().extra(BytesPlutusData.of("This is extra data"));

        System.out.println(PlutusDataJsonConverter.toJson(nft.getDatumAsPlutusData()));

        var datumPlutusData = nft.getDatumAsPlutusData();

        var dataList = datumPlutusData.getData().getPlutusDataList();
        var metadataMap = (MapPlutusData) dataList.get(0);
        var version = ((BigIntPlutusData) dataList.get(1)).getValue().intValue();
        var extra = dataList.get(2);

        System.out.println(nft.getDatum().getMetadataJson());

        assertThat(datumPlutusData.getAlternative()).isEqualTo(0);
        assertThat(version).isEqualTo(1);
        assertThat(extra).isEqualTo(BytesPlutusData.of("This is extra data"));
        assertThat(metadataMap.getMap().get(BytesPlutusData.of("name"))).isEqualTo(BytesPlutusData.of("GiveYouUp"));
    }

    @Test
    @SneakyThrows
    public void deserialize_fromDatumBytes() {
        String datum = "d8799fa7446b657931496b65793156616c7565446b6579329f496b65793256616c75654a6b65793256616c756532ff446b657935496b65793556616c7565446e616d655743495036382d4e46542d313730363533313830393830324566696c65739fa343737263581a68747470733a2f2f78797a2e636f6d2f696d616765312e706e67446e616d654a696d616765312e706e67496d656469615479706549696d6167652f706e67ff45696d616765581a68747470733a2f2f78797a2e636f6d2f696d616765312e706e674b6465736372697074696f6e581b54686973206973206d79206669727374204349502d3638204e4654014a45787472612074657874ff";

        CIP68NFT cip68NFT = CIP68NFT.fromDatum(HexUtil.decodeHexString(datum));

        assertThat(cip68NFT.getAssetNameAsHex()).isEqualTo("0x000de14043495036382d4e46542d31373036353331383039383032");
        assertThat(cip68NFT.getAssetNameLabel()).isEqualTo(222);
        assertThat(cip68NFT.getName()).isEqualTo("CIP68-NFT-1706531809802");

        assertThat(cip68NFT.getReferenceToken().getAssetNameAsHex()).isEqualTo("0x000643b043495036382d4e46542d31373036353331383039383032");
        assertThat(cip68NFT.getReferenceToken().getAssetNameLabel()).isEqualTo(100);
        assertThat(cip68NFT.getReferenceToken().getName()).isEqualTo("CIP68-NFT-1706531809802");

        assertThat(cip68NFT.getImage()).isEqualTo("https://xyz.com/image1.png");
        assertThat(cip68NFT.getDescription()).isEqualTo("This is my first CIP-68 NFT");
        assertThat(cip68NFT.getStringProperty("key1")).isEqualTo("key1Value");
        assertThat(cip68NFT.getListProperty("key2")).isEqualTo(List.of("key2Value", "key2Value2"));
        assertThat(cip68NFT.getStringProperty("key5")).isEqualTo("key5Value");
        assertThat(cip68NFT.getFiles()).hasSize(1);
        assertThat(cip68NFT.getFiles().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(cip68NFT.getFiles().get(0).getName()).isEqualTo("image1.png");
        assertThat(cip68NFT.getFiles().get(0).getSrcs()).isEqualTo(List.of("https://xyz.com/image1.png"));

        assertThat(cip68NFT.getDatum().getMetadataJson()).isNotNull();
        assertThat(cip68NFT.getDatumAsPlutusData()).isNotNull();
    }

}
