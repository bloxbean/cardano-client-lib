package com.bloxbean.cardano.client.metadata.helper;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.metadata.cbor.MetadataHelper;
import com.bloxbean.cardano.client.metadata.exception.MetadataDeSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.*;

import static co.nstant.in.cbor.model.MajorType.*;

/**
 * Abstract base class for metadata converters that provides common CBOR processing logic.
 * This class extracts the shared functionality from metadata converters to promote code reuse
 * and maintain consistency across different format converters (JSON, YAML, etc.).
 */
public abstract class AbstractMetadataConverter {

    /**
     * Converts CBOR bytes in hex format to a Java Map representation
     * @param hex CBOR data in hexadecimal string format
     * @return Java Map representation of the CBOR metadata
     * @throws CborDeserializationException if CBOR deserialization fails
     * @throws MetadataDeSerializationException if metadata structure is invalid
     */
    protected static java.util.Map<Object, Object> cborHexToJavaMap(String hex) throws CborDeserializationException {
        byte[] cborBytes = HexUtil.decodeHexString(hex);
        List<DataItem> dataItemList = null;
        try {
            dataItemList = CborDecoder.decode(cborBytes);
        } catch (CborException e) {
            throw new CborDeserializationException("Cbor deserialization failed", e);
        }

        if(dataItemList != null && dataItemList.size() > 1)
            throw new MetadataDeSerializationException("Multiple DataItems found at top level. Should be one : " + dataItemList.size());

        java.util.Map<Object, Object> result;
        DataItem dataItem = dataItemList.get(0);
        if(dataItem instanceof Map) {
            result = processMap((Map)dataItem);
        } else {
            throw new MetadataDeSerializationException("Top level object should be a Map : " + dataItem.getMajorType().toString());
        }
        return result;
    }

    /**
     * Processes a CBOR Map into a Java Map
     * @param map CBOR Map to process
     * @return Java Map representation
     */
    protected static java.util.Map<Object, Object> processMap(Map map) {
        java.util.Map<Object, Object> resultMap = new LinkedHashMap<>();
        Collection<DataItem> keys = map.getKeys();
        for(DataItem keyItem: keys) {
            DataItem valueItem = map.get(keyItem);
            Object key = processKey(keyItem);
            Object value = processValue(valueItem);

            resultMap.put(key, value);
        }
        return resultMap;
    }

    /**
     * Processes a CBOR key DataItem
     * @param keyItem CBOR DataItem representing a key
     * @return Java object representation of the key
     * @throws MetadataDeSerializationException if key type is invalid
     */
    protected static Object processKey(DataItem keyItem) {
        if (UNSIGNED_INTEGER.equals(keyItem.getMajorType())){
            return ((UnsignedInteger) keyItem).getValue();
        } else if(NEGATIVE_INTEGER.equals(keyItem.getMajorType())) {
            return ((NegativeInteger) keyItem).getValue();
        } else if (BYTE_STRING.equals(keyItem.getMajorType())) {
            return byteStringToObject((ByteString) keyItem);
        } else if (UNICODE_STRING.equals(keyItem.getMajorType())) {
            return ((UnicodeString) keyItem).getString();
        } else {
            throw new MetadataDeSerializationException("Invalid key type : " + keyItem.getMajorType());
        }
    }

    /**
     * Processes a CBOR value DataItem
     * @param valueItem CBOR DataItem representing a value
     * @return Java object representation of the value
     * @throws MetadataDeSerializationException if value type is unsupported
     */
    protected static Object processValue(DataItem valueItem) {
        if (valueItem == SimpleValue.NULL) {
            return null;
        } else if(UNSIGNED_INTEGER.equals(valueItem.getMajorType())){
            return ((UnsignedInteger)valueItem).getValue();
        } else if(NEGATIVE_INTEGER.equals(valueItem.getMajorType())) {
            return ((NegativeInteger)valueItem).getValue();
        } else if(BYTE_STRING.equals(valueItem.getMajorType())) {
            return byteStringToObject((ByteString) valueItem);
        } else if(UNICODE_STRING.equals(valueItem.getMajorType())) {
            return ((UnicodeString)valueItem).getString();
        } else if(MAP.equals(valueItem.getMajorType())){
            return processMap((Map)valueItem);
        } else if(ARRAY.equals(valueItem.getMajorType())) {
            return processArray((Array)valueItem);
        } else {
            throw new MetadataDeSerializationException("Unsupported type : " + valueItem.getMajorType());
        }
    }

    /**
     * Converts a CBOR ByteString to appropriate Java object
     * @param valueItem CBOR ByteString
     * @return String representation with 0x prefix for byte arrays, or extracted value
     */
    protected static Object byteStringToObject(ByteString valueItem) {
        var extractedValue = MetadataHelper.parseByteString(valueItem);
        if (extractedValue instanceof byte[]) {
            byte[] bytes = (byte[]) extractedValue;
            return "0x" + HexUtil.encodeHexString(bytes);
        } else {
            return extractedValue;
        }
    }

    /**
     * Processes a CBOR Array into a Java List
     * @param array CBOR Array to process
     * @return Java List representation
     */
    protected static Object processArray(Array array) {
        List<DataItem> dataItems = array.getDataItems();
        List<Object> resultList = new ArrayList<>();
        for(DataItem valueItem: dataItems) {
            Object valueObj = processValue(valueItem);
            resultList.add(valueObj);
        }
        return resultList;
    }
}