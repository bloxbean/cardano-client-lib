package com.bloxbean.cardano.client.cip.cip68.common;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;

import java.nio.charset.StandardCharsets;

import static com.bloxbean.cardano.client.util.StringUtils.isUtf8String;

public class CIP68Util {

    public static void addSingleOrMultipleValues(MapPlutusData mapPlutusData, String key, String value) {
        Object descValue = mapPlutusData.getMap().get(toByteString(key));

        if (descValue == null) {
            mapPlutusData.getMap().put(toByteString(key), toByteString(value));
            return;
        }

        ListPlutusData list = null;
        if (descValue instanceof BytesPlutusData) {
            list = new ListPlutusData();
            list.add((PlutusData) descValue);
            mapPlutusData.put(toByteString(key), list);
        } else if (descValue instanceof ListPlutusData) {
            list = (ListPlutusData) descValue;
        } else {
            mapPlutusData.put(toByteString(key), toByteString(value));
            return;
        }

        list.add(toByteString(value));
    }

    public static BytesPlutusData toByteString(String value) {
        return BytesPlutusData.of(value);
    }

    public static String fromByteString(BytesPlutusData bytesPlutusData) {
        if (bytesPlutusData == null) return null;
        byte[] bytes = bytesPlutusData.getValue();
        if (isUtf8String(bytes)) {
            return new String(bytes, StandardCharsets.UTF_8);
        } else {
            return HexUtil.encodeHexString(bytes, true);
        }
    }

}
