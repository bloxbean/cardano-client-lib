package com.bloxbean.cardano.client.cip.cip25;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static com.bloxbean.cardano.client.metadata.cbor.MetadataHelper.extractActualValue;

public class NFTProperties extends CBORMetadataMap {

    public NFTProperties() {
        super();
    }

    public NFTProperties(Map map) {
        super(map);
    }

    /**
     * Add additional properties
     *
     * @param name
     * @param value
     */
    public NFTProperties property(String name, String value) {
        put(name, value);
        return this;
    }

    /**
     * Get additional property by name
     *
     * @param name
     * @return property value
     */
    public String getProperty(String name) {
        return (String) get(name);
    }

    /**
     * Add Additional Key-Value Property Map
     * of type <String, String>
     * or
     * of Type <String, List<String>>
     * @param name Property key name
     * @param values Map<String, String> or Map<String, List<String>
     * @return {@link NFTProperties}
     */
    public NFTProperties property(String name, java.util.Map<String, Object> values) {
        CBORMetadataMap map = new CBORMetadataMap();
        values.forEach((key, value) -> {
            if (value instanceof String) {
                map.put(key, (String) value);
            } else if (value instanceof List<?>) {
                CBORMetadataList cborMetadataList = new CBORMetadataList();
                List<String> list = (List<String>) value;
                list.forEach(cborMetadataList::add);
                map.put(key, cborMetadataList);
            } else {
                throw new IllegalStateException("No Support for " + value.getClass() + " Type of Property");
            }
        });
        put(name, map);
        return this;
    }

    /**
     * Return value as Map for
     *
     * @param name
     * @return
     */
    public java.util.Map<String, Object> getMapProperty(String name) {
        CBORMetadataMap cborMap = (CBORMetadataMap) get(name);
        if (cborMap == null)
            return null;

        Map map = cborMap.getMap();

        Collection<DataItem> dataItems = map.getKeys();
        if (dataItems == null || dataItems.isEmpty())
            return null;

        java.util.Map<String, Object> output = new HashMap<>();
        for (DataItem di : dataItems) {
            output.put((String) extractActualValue(di), extractActualValue(map.get(di)));
        }

        return output;
    }

    /**
     * Add a list property
     *
     * @param name
     * @param values
     * @return NFT
     */
    public NFTProperties property(String name, List<String> values) {
        if (values == null || values.isEmpty())
            return this;

        CBORMetadataList list = new CBORMetadataList();
        values.forEach(list::add);
        put(name, list);

        return this;
    }

    /**
     * Get a list property
     *
     * @param name
     * @return
     */
    public List<String> getListProperty(String name) {
        CBORMetadataList cborList = (CBORMetadataList) get(name);
        if (cborList == null)
            return null;

        Array array = cborList.getArray();

        List<String> output = new ArrayList<>();
        for (DataItem di : array.getDataItems()) {
            output.add((String) extractActualValue(di));
        }

        return output;
    }

}
