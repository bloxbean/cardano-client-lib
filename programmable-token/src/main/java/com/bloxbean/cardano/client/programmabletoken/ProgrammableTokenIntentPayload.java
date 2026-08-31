package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.PlutusDataYamlUtil;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProgrammableTokenIntentPayload {
    private ProgrammableTokenIntentPayload() { }

    static Map<String, Object> policy(ProgrammableTokenPolicyRef ref) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (ref.getPolicyId() != null) map.put("policy_id", ref.getPolicyId());
        else map.put("policy_ref", ref.getName());
        return map;
    }

    static Map<String, Object> amount(Amount amount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("unit", amount.getUnit());
        map.put("quantity", amount.getQuantity());
        return map;
    }

    static List<Map<String, Object>> assets(List<Asset> assets) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset asset : assets) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", HexUtil.encodeHexString(asset.getNameAsBytes()));
            map.put("quantity", asset.getValue());
            result.add(map);
        }
        return result;
    }

    static Object plutus(PlutusData data) {
        return data == null ? null : PlutusDataYamlUtil.toYamlNode(data);
    }
}
