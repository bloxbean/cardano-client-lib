package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistration;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.RegistryNodeSpec;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/** CIP-113-specific registration data factory for the neutral authoring facade. */
public final class Cip113Registration {
    private Cip113Registration() { }

    public static ProgrammableTokenRegistration from(RegistryNodeSpec spec) {
        spec.validate();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minting_logic_script", credential(spec.getMintingLogicScript()));
        data.put("transfer_logic_script", credential(spec.getTransferLogicScript()));
        data.put("third_party_transfer_logic_script", credential(spec.getThirdPartyTransferLogicScript()));
        data.put("unfracking_logic_script", credential(spec.getUnfrackingLogicScript()));
        if (spec.getGlobalStateCs() != null) data.put("global_state_cs", spec.getGlobalStateCs());
        return ProgrammableTokenRegistration.builder().protocolData(data).build();
    }

    static Map<String, Object> credential(Credential credential) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", credential.getType().name().toLowerCase());
        value.put("hash", HexUtil.encodeHexString(credential.getBytes()));
        return value;
    }
}
