package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Accessors(fluent = true)
@Data
@NoArgsConstructor
public class HeaderMap {
    //INT(1) key type
    private Object algorithmId;

    //INT(2) key type
    private List<Object> criticality = new ArrayList<>();

    //INT(3) key type
    private Object contentType;

    //INT(4) key type
    private byte[] keyId;

    //INT(5) key type
    private byte[] initVector;

    //INT(6) key type
    private byte[] partialInitVector;

    //INT(7) key type
    private List<COSESignature> counterSignature = new ArrayList<>();

    //all other headers not listed above
    //@Builder.Default
    private LinkedHashMap<Object, DataItem> otherHeaders = new LinkedHashMap<>();

    public static HeaderMap deserialize(@NonNull DataItem dataItem) {
        if (!MajorType.MAP.equals(dataItem.getMajorType())) {
            throw new CborRuntimeException("De-serialization error. Expected type: Map, Found : " + dataItem.getMajorType());
        }

        Map map = (Map) dataItem;
        HeaderMap headerMap = new HeaderMap();

        Collection<DataItem> keyDIs = map.getKeys();

        for (DataItem keyDI : keyDIs) {
            DataItem valueDI = map.get(keyDI);

            if (keyDI.equals(new UnsignedInteger(1))) {
                //algorithm id
                headerMap.algorithmId = decodeIntOrTextTypeFromDataItem(valueDI);
            } else if (keyDI.equals(new UnsignedInteger(2))) {
                //criticality
                if (valueDI != null && valueDI.getMajorType().equals(MajorType.ARRAY)) {
                    Array criticalityArray = (Array) valueDI;
                    headerMap.criticality = criticalityArray.getDataItems().stream()
                            .map(di -> decodeIntOrTextTypeFromDataItem(di))
                            .collect(Collectors.toList());
                }
            } else if (keyDI.equals(new UnsignedInteger(3))) {
                //content type
                if (valueDI != null) {
                    headerMap.contentType = decodeIntOrTextTypeFromDataItem(valueDI);
                }
            } else if (keyDI.equals(new UnsignedInteger(4))) {
                //key id
                if (valueDI != null) {
                    headerMap.keyId = ((ByteString) valueDI).getBytes();
                }
            } else if (keyDI.equals(new UnsignedInteger(5))) {
                //initvector
                if (valueDI != null) {
                    headerMap.initVector = ((ByteString) valueDI).getBytes();
                }
            } else if (keyDI.equals(new UnsignedInteger(6))) {
                //partial initvector
                if (valueDI != null) {
                    headerMap.partialInitVector = ((ByteString) valueDI).getBytes();
                }
            } else if (keyDI.equals(new UnsignedInteger(7))) {
                //counter signature
                if (valueDI != null && valueDI.getMajorType().equals(MajorType.ARRAY)) {
                    Array counterSigArray = (Array) valueDI;

                    List<DataItem> counterSigDIs = counterSigArray.getDataItems();
                    if (counterSigDIs.size() > 0 && MajorType.ARRAY.equals(counterSigDIs.get(0))) { // [+ COSESignature]
                        headerMap.counterSignature = counterSigDIs.stream()
                                .map(di -> COSESignature.deserialize(di))
                                .collect(Collectors.toList());
                    } else { // COSESignature
                        headerMap.addSignature(COSESignature.deserialize(counterSigArray));
                    }
                }
            } else {
                //other headers
                headerMap.otherHeaders.put(decodeIntOrTextTypeFromDataItem(keyDI), valueDI);
            }
        }

        return headerMap;
    }

    private static DataItem getIntOrTextTypeFromObject(@NonNull Object value) {
        if (value instanceof Long) {
            if (((Long) value).longValue() >= 0)
                return new UnsignedInteger((Long) value);
            else
                return new NegativeInteger((Long) value);
        } else if (value instanceof Integer) {
            if (((Integer) value).intValue() >= 0)
                return new UnsignedInteger((Integer) value);
            else
                return new NegativeInteger((Integer) value);
        } else if (value instanceof String) {
            return new UnicodeString((String) value);
        } else {
            throw new CborRuntimeException(String.format("Serialization error. Expected type: long/String, found: %s", value.getClass()));
        }
    }

    private static Object decodeIntOrTextTypeFromDataItem(@NonNull DataItem dataItem) {
        if (MajorType.UNSIGNED_INTEGER.equals(dataItem.getMajorType())) {
            return ((UnsignedInteger) dataItem).getValue().longValue();
        } else if (MajorType.NEGATIVE_INTEGER.equals(dataItem.getMajorType())) {
            return ((NegativeInteger) dataItem).getValue().longValue();
        } else if (MajorType.UNICODE_STRING.equals(dataItem.getMajorType())) {
            return ((UnicodeString) dataItem).getString();
        } else
            throw new CborRuntimeException(String.format("De-serialization error: Unexpected data type: " + dataItem.getMajorType()));
    }

    public HeaderMap algorithmId(long algorithmId) {
        this.algorithmId = algorithmId;
        return this;
    }

    public HeaderMap algorithmId(String algorithmId) {
        this.algorithmId = algorithmId;
        return this;
    }

    public HeaderMap addCriticality(long crit) {
        this.criticality.add(crit);
        return this;
    }

    public HeaderMap addCriticality(String crit) {
        this.criticality.add(crit);
        return this;
    }

    public HeaderMap contentType(long contentType) {
        this.contentType = contentType;
        return this;
    }

    public HeaderMap contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public HeaderMap addOtherHeader(BigInteger key, DataItem value) {
        otherHeaders.put(key, value);
        return this;
    }

    public HeaderMap addOtherHeader(long key, DataItem value) {
        otherHeaders.put(key, value);
        return this;
    }

    public HeaderMap addOtherHeader(String key, DataItem value) {
        otherHeaders.put(key, value);
        return this;
    }

    public HeaderMap addSignature(COSESignature signature) {
        counterSignature.add(signature);
        return this;
    }

    public Map serialize() {
        Map map = new Map();

        if (algorithmId != null) {
            map.put(new UnsignedInteger(1), getIntOrTextTypeFromObject(algorithmId));
        }

        if (criticality != null && criticality.size() > 0) {
            Array valueArray = new Array();
            criticality.stream().forEach(crit -> {
                valueArray.add(getIntOrTextTypeFromObject(crit));
            });
            map.put(new UnsignedInteger(2), valueArray);
        }

        if (contentType != null) {
            map.put(new UnsignedInteger(3), getIntOrTextTypeFromObject(contentType));
        }

        if (keyId != null) {
            map.put(new UnsignedInteger(4), new ByteString(keyId));
        }

        if (initVector != null) {
            map.put(new UnsignedInteger(5), new ByteString(initVector));
        }

        if (partialInitVector != null) {
            map.put(new UnsignedInteger(6), new ByteString(partialInitVector));
        }

        if (counterSignature != null && counterSignature.size() > 0) {
            List<DataItem> values = counterSignature.stream().map(coseSignature -> coseSignature.serialize())
                    .collect(Collectors.toList());

            if (values.size() == 1)
                map.put(new UnsignedInteger(7), values.get(0));
            else {
                Array valueArray = new Array();
                values.forEach(dataItem -> valueArray.add(dataItem));
                map.put(new UnsignedInteger(7), valueArray);
            }
        }

        //Other headers
        otherHeaders.forEach((key, value) -> {
            map.put(getIntOrTextTypeFromObject(key), value);
        });

        return map;
    }
}
