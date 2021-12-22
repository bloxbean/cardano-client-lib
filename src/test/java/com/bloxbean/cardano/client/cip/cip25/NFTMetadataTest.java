package com.bloxbean.cardano.client.cip.cip25;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class NFTMetadataTest {

    @Test
    void createNFTMetadata() {
        String policyId_1 = "1e80fa1859c59b18ff4895a2c481cced459c6b4fcd6c445b5e907a92";
        String policyId_2 = "72ae99570a3b5dfdcff84bdd0e0b4805743ee3c3c27ab3ad6a3ce45b";

        NFT nft1 = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .image("ipfs://someimageurl-1")
                .mediaType("image/png")
                .description("NFT-1 description")
                .addFile(NFTFile.create()
                        .name("nft1-file1")
                        .mediaType("plain/text")
                        .src("http://nft1-file1"))
                .addFile(NFTFile.create()
                        .name("nft1-file2")
                        .mediaType("plain/text")
                        .src("http://nft1-file2"));

        NFT nft2 = NFT.create()
                .assetName("assetname-nft2")
                .name("nft2")
                .image("ipfs://someimageurl-2")
                .image("ipfs://someimageurl-2.1")
                .mediaType("image/png")
                .description("NFT-2 description")
                .description("NFT-2.1 description")
                .addFile(NFTFile.create()
                        .name("nft2-file1")
                        .mediaType("plain/text")
                        .src("http://nft2-file1"))
                .addFile(NFTFile.create()
                        .name("nft2-file2")
                        .mediaType("plain/text")
                        .src("http://nft2-file2")
                        .src("http://nft2-file2.1"));


        NFT nft3 = NFT.create()
                .assetName("assetname-nft3")
                .name("nft3")
                .setImages(Arrays.asList("ipfs://someimageurl-3", "ipfs://someimageurl-3.1"))
                .mediaType("image/png")
                .description("NFT-3 description")
                .addFile(NFTFile.create()
                        .name("nft3-file1")
                        .mediaType("plain/text")
                        .setsrcs(Arrays.asList("http://nft3-file1", "http://nft3-file1.1")));

        NFTMetadata nftMetadata = NFTMetadata.create()
                .addNFT(policyId_1, nft1)
                .addNFT(policyId_2, nft2)
                .addNFT(policyId_2, nft3);

        String json = nftMetadata.toString();

        System.out.println(json);

        NFT rNft1 = nftMetadata.getNFT(policyId_1, nft1.getAssetName());
        NFT rNft2 = nftMetadata.getNFT(policyId_2, nft2.getAssetName());
        NFT rNft3 = nftMetadata.getNFT(policyId_2, nft3.getAssetName());
        NFT rNftNotAvailable = nftMetadata.getNFT(policyId_1, nft2.getAssetName());

        List<NFTFile> rNft1Files = rNft1.getFiles();
        List<NFTFile> rNft2Files = rNft2.getFiles();
        List<NFTFile> rNft3Files = rNft3.getFiles();

        assertThat(rNft1).isNotNull();
        assertThat(rNft2).isNotNull();
        assertThat(rNft3).isNotNull();
        assertThat(rNftNotAvailable).isNull();

        assertThat(nftMetadata.getVersion()).isEqualTo("1.0");

        assertThat(rNft1.getAssetName()).isEqualTo(nft1.getAssetName());
        assertThat(rNft1.getName()).isEqualTo(nft1.getName());
        assertThat(rNft1.getImages()).isEqualTo(nft1.getImages());
        assertThat(rNft1.getMediaType()).isEqualTo(nft1.getMediaType());
        assertThat(rNft1.getDescriptions()).isEqualTo(nft1.getDescriptions());

        assertThat(rNft2.getAssetName()).isEqualTo(nft2.getAssetName());
        assertThat(rNft2.getName()).isEqualTo(nft2.getName());
        assertThat(rNft2.getImages()).isEqualTo(nft2.getImages());
        assertThat(rNft2.getMediaType()).isEqualTo(nft2.getMediaType());
        assertThat(rNft2.getDescriptions()).isEqualTo(nft2.getDescriptions());
        assertThat(rNft2.getDescriptions()).hasSize(2);
        assertThat(rNft2.getImages()).hasSize(2);

        assertThat(rNft3.getAssetName()).isEqualTo(nft3.getAssetName());
        assertThat(rNft3.getName()).isEqualTo(nft3.getName());
        assertThat(rNft3.getImages()).isEqualTo(nft3.getImages());
        assertThat(rNft3.getMediaType()).isEqualTo(nft3.getMediaType());
        assertThat(rNft3.getDescriptions()).isEqualTo(nft3.getDescriptions());
        assertThat(rNft3.getDescriptions()).hasSize(1);
        assertThat(rNft3.getImages()).hasSize(2);

        //Check file attributes
        assertThat(rNft1Files.get(0).getName()).isEqualTo(nft1.getFiles().get(0).getName());
        assertThat(rNft1Files.get(0).getMediaType()).isEqualTo(nft1.getFiles().get(0).getMediaType());
        assertThat(rNft1Files.get(0).getSrcs()).isEqualTo(nft1.getFiles().get(0).getSrcs());

        assertThat(rNft1Files.get(1).getName()).isEqualTo(nft1.getFiles().get(1).getName());
        assertThat(rNft1Files.get(1).getMediaType()).isEqualTo(nft1.getFiles().get(1).getMediaType());
        assertThat(rNft1Files.get(1).getSrcs()).isEqualTo(nft1.getFiles().get(1).getSrcs());

        assertThat(rNft2Files.get(1).getName()).isEqualTo(nft2.getFiles().get(1).getName());
        assertThat(rNft2Files.get(1).getMediaType()).isEqualTo(nft2.getFiles().get(1).getMediaType());
        assertThat(rNft2Files.get(1).getSrcs()).isEqualTo(nft2.getFiles().get(1).getSrcs());
        assertThat(rNft2Files.get(1).getSrcs()).hasSize(2);
        assertThat(rNft2Files.get(0).getSrcs()).hasSize(1);

        assertThat(rNft3Files.get(0).getName()).isEqualTo(nft3.getFiles().get(0).getName());
        assertThat(rNft3Files.get(0).getMediaType()).isEqualTo(nft3.getFiles().get(0).getMediaType());
        assertThat(rNft3Files.get(0).getSrcs()).isEqualTo(nft3.getFiles().get(0).getSrcs());
        assertThat(rNft3Files.get(0).getSrcs()).hasSize(2);


        //serialize & deserialize and check serialize bytes
        byte[] serializeBytes = nftMetadata.serialize();
        NFTMetadata deSerNFTMetadata = NFTMetadata.create(serializeBytes);

        byte[] deSerializeBytes = deSerNFTMetadata.serialize();
        assertThat(deSerializeBytes).isEqualTo(serializeBytes);
    }

    @Test
    void createMetadata_whenInvokeImageAndsetImages_throwsError() {
        NFT nft = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .setImages(Arrays.asList("someImageUri1", "someImageUri2"))
                .image("anotherImage");

        List<String> images = nft.getImages();
        assertThat(images).hasSize(3);
        assertThat(images.get(0)).isEqualTo("someImageUri1");
        assertThat(images.get(1)).isEqualTo("someImageUri2");
        assertThat(images.get(2)).isEqualTo("anotherImage");
    }

    @Test
    void createMetadata_willReplacePrevImage_whenInvokesetImagesAndImage() {
        NFT nft = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .image("originalImage")
                .setImages(Arrays.asList("someImageUri1", "someImageUri2"));

        List<String> images = nft.getImages();
        assertThat(images).hasSize(2);
        assertThat(images.get(0)).isEqualTo("someImageUri1");
        assertThat(images.get(1)).isEqualTo("someImageUri2");
    }

    @Test
    void createMetadata_willReplacePrevSrc_whenInvokeSrcAndsetSrcs() {
        NFT nft = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .addFile(NFTFile.create()
                        .name("nft2-file1")
                        .mediaType("plain/text")
                        .src("originalSrc")
                        .setsrcs(Arrays.asList("src1", "src2")));

        List<String> srcList = nft.getFiles().get(0).getSrcs();
        assertThat(srcList).hasSize(2);
        assertThat(srcList.get(0)).isEqualTo("src1");
        assertThat(srcList.get(1)).isEqualTo("src2");
    }

    @Test
    void createMetadata_willAddSrc_whenInvokesetSrcsAndSrc() {
        NFT nft = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .addFile(NFTFile.create()
                        .name("nft2-file1")
                        .mediaType("plain/text")
                        .setsrcs(Arrays.asList("src1", "src2"))
                        .src("anotherSrc"));

        List<String> srcList = nft.getFiles().get(0).getSrcs();
        assertThat(srcList).hasSize(3);
        assertThat(srcList.get(0)).isEqualTo("src1");
        assertThat(srcList.get(1)).isEqualTo("src2");
        assertThat(srcList.get(2)).isEqualTo("anotherSrc");
    }

    @Test
    void removeNFT() {
        String policyId_1 = "1e80fa1859c59b18ff4895a2c481cced459c6b4fcd6c445b5e907a92";
        String policyId_2 = "2222221859c59b18ff4895a2c481cced459c6b4fcd6c445b5e907a92";

        NFT nft1 = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .image("ipfs://someimageurl-1")
                .mediaType("image/png")
                .description("NFT-1 description")
                .addFile(NFTFile.create()
                        .name("nft1-file1")
                        .mediaType("plain/text")
                        .src("http://nft1-file1"))
                .addFile(NFTFile.create()
                        .name("nft1-file2")
                        .mediaType("plain/text")
                        .src("http://nft1-file2"));

        NFT nft2 = NFT.create()
                .assetName("assetname-nft2")
                .name("nft2")
                .image("ipfs://someimageurl-2")
                .mediaType("image/png")
                .description("NFT-2 description");

        NFT nft3 = NFT.create()
                .assetName("assetname-nft3")
                .name("nft2")
                .image("ipfs://someimageurl-3")
                .mediaType("image/png")
                .description("NFT-3 description");

        NFTMetadata metadata = NFTMetadata.create()
                .addNFT(policyId_1, nft1)
                .addNFT(policyId_1, nft2)
                .addNFT(policyId_2, nft3);

        //remove nft2
        metadata.removeNFT(policyId_1, nft2.getAssetName());

        //asserts
        NFT removeNFT = metadata.getNFT(policyId_1, nft2.getAssetName());
        assertNull(removeNFT);
    }

    @Test
    void addStringProperty() {
        String policyId_1 = "1e80fa1859c59b18ff4895a2c481cced459c6b4fcd6c445b5e907a92";

        NFT nft1 = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .image("ipfs://someimageurl-1")
                .mediaType("image/png")
                .description("NFT-1 description")
                .property("prop1", "propValue1")
                .property("prop2", "propValue2")
                .addFile(NFTFile.create()
                        .name("nft1-file1")
                        .mediaType("plain/text")
                        .src("http://nft1-file1")
                        .property("fileprop1", "filepropvalue1"))
                .addFile(NFTFile.create()
                        .name("nft1-file2")
                        .mediaType("plain/text")
                        .src("http://nft1-file2")
                        .property("fileprop2", "filepropvalue2"));

        NFTMetadata metadata = NFTMetadata.create()
                .addNFT(policyId_1, nft1);

        NFT rNft = metadata.getNFT(policyId_1, nft1.getAssetName());

        System.out.println(metadata);

        assertThat(rNft.getProperty("prop1")).isEqualTo("propValue1");
        assertThat(rNft.getProperty("prop2")).isEqualTo("propValue2");

        assertThat(rNft.getFiles().get(0).getProperty("fileprop1")).isEqualTo("filepropvalue1");
        assertThat(rNft.getFiles().get(1).getProperty("fileprop2")).isEqualTo("filepropvalue2");

    }

    @Test
    void addMapAndListProperty() {
        String policyId_1 = "1e80fa1859c59b18ff4895a2c481cced459c6b4fcd6c445b5e907a92";

        java.util.Map<String, String> propsMap = new HashMap<>();
        propsMap.put("key1", "value1");
        propsMap.put("key2", "value2");

        java.util.Map<String, String> filePropsMap = new HashMap<>();
        filePropsMap.put("keya", "valuea");
        filePropsMap.put("keyb", "valueb");

        NFT nft1 = NFT.create()
                .assetName("assetname-nft1")
                .name("nft1")
                .image("ipfs://someimageurl-1")
                .mediaType("image/png")
                .description("NFT-1 description")
                .property("prop1", "propValue1")
                .property("prop2", "propValue2")
                .property("prop3", propsMap)
                .property("prop4", Arrays.asList("listprop1", "listprop2", "listprop3"))
                .addFile(NFTFile.create()
                        .name("nft1-file1")
                        .mediaType("plain/text")
                        .src("http://nft1-file1")
                        .property("fileprop1", "filepropvalue1")
                        .property("fileprop-1.1", filePropsMap))
                .addFile(NFTFile.create()
                        .name("nft1-file2")
                        .mediaType("plain/text")
                        .src("http://nft1-file2")
                        .property("fileprop2", "filepropvalue2")
                        .property("fileprop3", Arrays.asList("listpropa", "listpropb", "listpropc")));

        NFTMetadata metadata = NFTMetadata.create()
                .addNFT(policyId_1, nft1);

        NFT rNft = metadata.getNFT(policyId_1, nft1.getAssetName());

        System.out.println(metadata);

        assertThat(rNft.getProperty("prop1")).isEqualTo("propValue1");
        assertThat(rNft.getProperty("prop2")).isEqualTo("propValue2");

        assertThat(rNft.getMapProperty("prop3")).containsEntry("key1", "value1");
        assertThat(rNft.getMapProperty("prop3")).containsEntry("key2", "value2");

        assertThat(rNft.getFiles().get(0).getMapProperty("fileprop-1.1")).containsAllEntriesOf(filePropsMap);

        assertThat(rNft.getListProperty("prop4")).containsExactly("listprop1", "listprop2", "listprop3");
        assertThat(rNft.getFiles().get(1).getListProperty("fileprop3")).containsExactly("listpropa", "listpropb", "listpropc");

    }

}
