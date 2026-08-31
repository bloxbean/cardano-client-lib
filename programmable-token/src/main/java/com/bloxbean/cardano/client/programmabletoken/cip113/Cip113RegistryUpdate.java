package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts a desired CIP-113 registry node state into neutral update payload data. */
public final class Cip113RegistryUpdate {
    private Cip113RegistryUpdate() { }

    public static Map<String, Object> from(RegistryNode node) {
        if (node == null) throw new IllegalArgumentException("node is required");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("next", node.getNext());
        data.put("minting_logic_script", Cip113Registration.credential(node.getMintingLogicScript()));
        data.put("transfer_logic_script", Cip113Registration.credential(node.getTransferLogicScript()));
        data.put("third_party_transfer_logic_script",
                Cip113Registration.credential(node.getThirdPartyTransferLogicScript()));
        data.put("unfracking_logic_script", Cip113Registration.credential(node.getUnfrackingLogicScript()));
        data.put("global_state_cs", node.getGlobalStateCs());
        return data;
    }
}
