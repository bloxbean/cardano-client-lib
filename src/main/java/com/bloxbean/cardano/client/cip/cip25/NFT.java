package com.bloxbean.cardano.client.cip.cip25;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.cip.cip25.NFTMetadataUtil.addSingleOrMultipleValue;
import static com.bloxbean.cardano.client.metadata.cbor.MetadataHelper.extractActualValue;

public class NFT extends NFTProperties {
    public static final String IMAGE_KEY = "image";
    public static final String MEDIA_TYPE_KEY = "mediaType";
    public static final String DESCRIPTION_KEY = "description";
    public static final String NAME_KEY = "name";
    public static final String FILES_KEY = "files";
    private String assetName;

    private NFT() {

    }

    private NFT(Map map) {
        super(map);
    }

    /**
     * Create an instance of NFT
     * @return
     */
    public static NFT create() {
        return new NFT();
    }

    static NFT create(Map map) {
        return new NFT(map);
    }

    /**
     * Set asset name
     * @param assetName
     * @return
     */
    public NFT assetName(String assetName) {
        this.assetName = assetName;
        return this;
    }

    /**
     * Get asset name
     * @return
     */
    public String getAssetName() {
        return assetName;
    }

    /**
     * Set name attribute
     * @param name
     * @return
     */
    public NFT name(String name) {
        put(NAME_KEY, name);
        return this;
    }

    /**
     * Get name attribute
     * @return
     */
    public String getName() {
        return (String) get(NAME_KEY);
    }

    /**
     * Set image for the NFT
     * Call this method multiple times to set multiple images
     *
     * @param imageUri
     * @return NFT
     */
    public NFT image(String imageUri) {
        addSingleOrMultipleValue(this, IMAGE_KEY, imageUri);

        return this;
    }

    /**
     * Add image to the image list.
     * This will replace any existing value for image
     *
     * @param imageUris
     * @return
     */
    public NFT setImages(List<String> imageUris) {
        if (imageUris == null || imageUris.size() == 0)
            return this;

        CBORMetadataList list = new CBORMetadataList();
        for (String imageUri : imageUris) {
            list.add(imageUri);
        }

        put(IMAGE_KEY, list);

        return this;
    }

    /**
     * Get a single image uri or a list of image uris
     *
     * @return List of image(s)
     */
    public List<String> getImages() {
        Object value = get(IMAGE_KEY);

        if (value instanceof String)
            return Arrays.asList((String) value);
        else if (value instanceof CBORMetadataList) {
            Array array = ((CBORMetadataList) value).getArray();
            return array.getDataItems().stream().map(di -> (String) extractActualValue(di)).collect(Collectors.toList());
        } else
            return null;
    }

    /**
     * Set mediaType
     * @param mediaType
     * @return
     */
    public NFT mediaType(String mediaType) {
        put(MEDIA_TYPE_KEY, mediaType);
        return this;
    }

    public String getMediaType() {
        return (String) get(MEDIA_TYPE_KEY);
    }

    /**
     * Add description
     * Call this method multiple times to add multiple descriptions
     *
     * @param description
     * @return
     */
    public NFT description(String description) {
        addSingleOrMultipleValue(this, DESCRIPTION_KEY, description);

        return this;
    }

    /**
     * Get list of descriptions
     * @return list of descriptions
     */
    public List<String> getDescriptions() {
        Object value = get(DESCRIPTION_KEY);

        if (value instanceof String)
            return Arrays.asList((String) value);
        else if (value instanceof CBORMetadataList) {
            Array array = ((CBORMetadataList) value).getArray();
            return array.getDataItems().stream().map(di -> (String) extractActualValue(di)).collect(Collectors.toList());
        } else
            return null;
    }

    /**
     * Add file info
     * @param nftFile
     * @return
     */
    public NFT addFile(NFTFile nftFile) {
        CBORMetadataList files = (CBORMetadataList) get(FILES_KEY);

        if (files == null) {
            files = new CBORMetadataList();
            put(FILES_KEY, files);
        }

        files.add(nftFile);

        return this;
    }

    /**
     * Get list of files
     * @return List of files
     */
    public List<NFTFile> getFiles() {
        CBORMetadataList files = (CBORMetadataList) get(FILES_KEY);

        List<NFTFile> nftFiles = new ArrayList<>();
        for (DataItem di : files.getArray().getDataItems()) {
            NFTFile nftFile = NFTFile.create((Map) di);
            nftFiles.add(nftFile);
        }

        return nftFiles;
    }

    /**
     * Add a string property
     * @param name
     * @param value
     * @return
     */
    @Override
    public NFT property(String name, String value) {
        return (NFT) super.property(name, value);
    }

    /**
     * Add a map property
     * @param name
     * @param values
     * @return
     */
    @Override
    public NFT property(String name, java.util.Map<String, String> values) {
        return (NFT) super.property(name, values);
    }

    /**
     * Add a list property
     * @param name
     * @param values
     * @return
     */
    @Override
    public NFT property(String name, List<String> values) {
        return (NFT)super.property(name, values);
    }

    public String toString() {
        try {
            return toJson();
        } catch (Exception e) {
            return super.toString();
        }
    }
}
