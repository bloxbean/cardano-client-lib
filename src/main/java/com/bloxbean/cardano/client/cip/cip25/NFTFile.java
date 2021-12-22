package com.bloxbean.cardano.client.cip.cip25;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.cip.cip25.NFTMetadataUtil.addSingleOrMultipleValue;
import static com.bloxbean.cardano.client.metadata.cbor.MetadataHelper.extractActualValue;

public class NFTFile extends NFTProperties {
    public static final String NAME_KEY = "name";
    public static final String MEDIA_TYPE_KEY = "mediaType";
    public static final String SRC_KEY = "src";

    private NFTFile() {

    }

    private NFTFile(Map map) {
        super(map);
    }

    /**
     * Create a new NFTFile
     *
     * @return NFTFile
     */
    public static NFTFile create() {
        return new NFTFile();
    }

    static NFTFile create(Map map) {
        return new NFTFile(map);
    }

    /**
     * Set name attribute
     *
     * @param name
     * @return NFTFile
     */
    public NFTFile name(String name) {
        put(NAME_KEY, name);
        return this;
    }

    /**
     * Get name attribute
     *
     * @return NFTFile
     */
    public String getName() {
        return (String) get(NAME_KEY);
    }

    /**
     * Set mediaType attribute
     *
     * @param mediaType
     * @return NFTFile
     */
    public NFTFile mediaType(String mediaType) {
        put(MEDIA_TYPE_KEY, mediaType);
        return this;
    }

    /**
     * Get media type attribute
     *
     * @return mediaType
     */
    public String getMediaType() {
        return (String) get(MEDIA_TYPE_KEY);
    }

    /**
     * Set src for the NFTFile
     * Call this method multiple times to set multiple srcs
     *
     * @param uri
     * @return NFTFile
     */
    public NFTFile src(String uri) {
        addSingleOrMultipleValue(this, SRC_KEY, uri);

        return this;
    }

    /**
     * Add src to the src list.
     * This will replace any existing value for src
     *
     * @param uris
     * @return NFTFile
     */
    public NFTFile setsrcs(List<String> uris) {
        if (uris == null || uris.size() == 0)
            return this;

        CBORMetadataList list = new CBORMetadataList();
        for (String uri : uris) {
            list.add(uri);
        }
        put(SRC_KEY, list);

        return this;
    }

    /**
     * Get single src or multiple src as List
     *
     * @return List of src
     */
    public List<String> getSrcs() {
        Object value = get(SRC_KEY);

        if (value instanceof String)
            return Arrays.asList((String) value);
        else if (value instanceof CBORMetadataList) {
            Array array = ((CBORMetadataList) value).getArray();
            return array.getDataItems().stream().map(di -> (String) extractActualValue(di)).collect(Collectors.toList());
        } else
            return null;
    }

    /**
     * Add a string property
     * @param name
     * @param value
     * @return
     */
    @Override
    public NFTFile property(String name, String value) {
        return (NFTFile) super.property(name, value);
    }

    /**
     * Add a map property
     * @param name
     * @param values
     * @return
     */
    @Override
    public NFTFile property(String name, java.util.Map<String, String> values) {
        return (NFTFile) super.property(name, values);
    }

    /**
     * Add a list property
     * @param name
     * @param values
     * @return
     */
    @Override
    public NFTFile property(String name, List<String> values) {
        return (NFTFile)super.property(name, values);
    }

    public String toString() {
        try {
            return toJson();
        } catch (Exception e) {
            return super.toString();
        }
    }

}
