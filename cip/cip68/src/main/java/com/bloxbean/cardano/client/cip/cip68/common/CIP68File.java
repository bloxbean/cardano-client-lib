package com.bloxbean.cardano.client.cip.cip68.common;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.fromByteString;
import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.toByteString;

public class CIP68File extends DatumProperties<CIP68File> {
    public static final String NAME_KEY = "name";
    public static final String MEDIA_TYPE_KEY = "mediaType";
    public static final String SRC_KEY = "src";

    private CIP68File() {
        super();
    }

    private CIP68File(MapPlutusData map) {
        super(map);
    }

    /**
     * Create a new CIP68File
     *
     * @return CIP68File
     */
    public static CIP68File create() {
        return new CIP68File();
    }

    public static CIP68File create(MapPlutusData map) {
        return new CIP68File(map);
    }

    /**
     * Set name attribute
     *
     * @param name
     * @return CIP68File
     */
    public CIP68File name(String name) {
        property(NAME_KEY, name);
        return this;
    }

    /**
     * Get name attribute
     *
     * @return CIP68File
     */
    public String getName() {
        return getStringProperty(NAME_KEY);
    }

    /**
     * Set mediaType attribute
     *
     * @param mediaType
     * @return CIP68File
     */
    public CIP68File mediaType(String mediaType) {
        property(MEDIA_TYPE_KEY, mediaType);
        return this;
    }

    /**
     * Get media type attribute
     *
     * @return mediaType
     */
    public String getMediaType() {
        return getStringProperty(MEDIA_TYPE_KEY);
    }

    /**
     * Set src for the CIP68File
     * Call this method multiple times to set multiple srcs
     *
     * @param uri
     * @return CIP68File
     */
    public CIP68File src(String uri) {
        CIP68Util.addSingleOrMultipleValues(mapPlutusData, SRC_KEY, uri);

        return this;
    }

    /**
     * Add src to the src list.
     * This will replace any existing value for src
     *
     * @param uris
     * @return CIP68File
     */
    public CIP68File setsrcs(List<String> uris) {
        if (uris == null || uris.size() == 0)
            return this;

        ListPlutusData list = new ListPlutusData();
        for (String uri : uris) {
            list.add(toByteString(uri));
        }
        property(toByteString(SRC_KEY), list);

        return this;
    }

    /**
     * Get single src or multiple src as List
     *
     * @return List of src
     */
    public List<String> getSrcs() {
        PlutusData value = getProperty(toByteString(SRC_KEY));

        if (value instanceof BytesPlutusData)
            return Arrays.asList(fromByteString((BytesPlutusData) value));
        else if (value instanceof ListPlutusData) {
            var list = (ListPlutusData) value;
            return list.getPlutusDataList()
                    .stream()
                    .map(plutusData -> (BytesPlutusData) plutusData)
                    .map(CIP68Util::fromByteString)
                    .collect(Collectors.toList());
        } else
            return Collections.emptyList();
    }

}
