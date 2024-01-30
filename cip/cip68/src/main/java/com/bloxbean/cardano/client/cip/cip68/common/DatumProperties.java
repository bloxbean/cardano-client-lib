package com.bloxbean.cardano.client.cip.cip68.common;

import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.util.PlutusDataPrettyPrinter;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.fromByteString;
import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.toByteString;

public class DatumProperties<T> {
    protected MapPlutusData mapPlutusData;

    public DatumProperties() {
        this.mapPlutusData = new MapPlutusData();
    }

    public DatumProperties(MapPlutusData mapPlutusData) {
        this.mapPlutusData = mapPlutusData;
    }

    public T property(String name, String value) {
        mapPlutusData.put(toByteString(name), toByteString(value));
        return (T) this;
    }

    public String getStringProperty(String name) {
        var datumValue = (BytesPlutusData) mapPlutusData.getMap().get(toByteString(name));
        if (datumValue == null)
            return null;
        return fromByteString(datumValue);
    }

    public T property(String name, int value) {
        mapPlutusData.put(toByteString(name), BigIntPlutusData.of(value));
        return (T) this;
    }

    public Integer getIntProperty(String name) {
        var datumValue = (BigIntPlutusData) mapPlutusData.getMap().get(toByteString(name));
        if (datumValue == null)
            return null;
        return datumValue.getValue().intValue();
    }

    public T property(String name, long value) {
        mapPlutusData.put(toByteString(name), BigIntPlutusData.of(value));
        return (T) this;
    }

    public Long getLongProperty(String name) {
        var datumValue = (BigIntPlutusData) mapPlutusData.getMap().get(toByteString(name));
        if (datumValue == null)
            return null;
        return datumValue.getValue().longValue();
    }

    public T property(String name, BigInteger value) {
        mapPlutusData.put(toByteString(name), BigIntPlutusData.of(value));
        return (T) this;
    }

    public BigInteger getBigIntegerProperty(String name) {
        var datumValue = (BigIntPlutusData) mapPlutusData.getMap().get(toByteString(name));
        if (datumValue == null)
            return null;
        return datumValue.getValue();
    }

    public T property(String name, List<String> values) {
        if (values == null || values.isEmpty())
            return (T) this;

        List<BytesPlutusData> valuesPlutusDataList = values.stream()
                .map(CIP68Util::toByteString)
                .collect(Collectors.toList());

        mapPlutusData.put(toByteString(name), ListPlutusData.of(valuesPlutusDataList.toArray(new BytesPlutusData[0])));
        return (T) this;
    }

    /**
     * Get a list property
     *
     * @param name
     * @return
     */
    public List<String> getListProperty(String name) {
        ListPlutusData listPlutusData = (ListPlutusData) mapPlutusData.getMap().get(toByteString(name));
        if (listPlutusData == null)
            return null;

        List<BytesPlutusData> values = listPlutusData.getPlutusDataList().stream()
                .map(plutusData -> (BytesPlutusData) plutusData)
                .collect(Collectors.toList());

        return values.stream()
                .map(CIP68Util::fromByteString)
                .collect(Collectors.toList());
    }

    public T property(PlutusData key, PlutusData value) {
        mapPlutusData.put(key, value);
        return (T) this;
    }

    public PlutusData getProperty(PlutusData key) {
        return mapPlutusData.getMap().get(key);
    }

    public MapPlutusData toPlutusData() {
        return mapPlutusData;
    }

    public String toJson() {
        try {
            return PlutusDataPrettyPrinter.toJson(mapPlutusData);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
