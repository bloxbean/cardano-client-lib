package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCredential;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistryUpdate;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;

/** Converts between the CIP-113 registry node and the typed programmable-token update value. */
public final class Cip113RegistryUpdate {
    private Cip113RegistryUpdate() { }

    public static ProgrammableTokenRegistryUpdate from(RegistryNode node) {
        if (node == null) throw new IllegalArgumentException("node is required");
        return ProgrammableTokenRegistryUpdate.builder()
                .next(node.getNext())
                .mintingLogicScript(ProgrammableTokenCredential.from(node.getMintingLogicScript()))
                .transferLogicScript(ProgrammableTokenCredential.from(node.getTransferLogicScript()))
                .thirdPartyTransferLogicScript(ProgrammableTokenCredential.from(
                        node.getThirdPartyTransferLogicScript()))
                .unfrackingLogicScript(ProgrammableTokenCredential.from(
                        node.getUnfrackingLogicScript()))
                .globalStateCs(node.getGlobalStateCs())
                .build();
    }

    /** Convert the neutral typed update value into the CIP-113 registry-node model. */
    public static RegistryNode toNode(String policyId, ProgrammableTokenRegistryUpdate update) {
        if (update == null) throw new IllegalArgumentException("update is required");
        return RegistryNode.builder()
                .key(policyId)
                .next(required(update.getNext(), "next"))
                .mintingLogicScript(update.getMintingLogicScript().toCredential())
                .transferLogicScript(update.getTransferLogicScript().toCredential())
                .thirdPartyTransferLogicScript(
                        update.getThirdPartyTransferLogicScript().toCredential())
                .unfrackingLogicScript(update.getUnfrackingLogicScript().toCredential())
                .globalStateCs(update.getGlobalStateCs())
                .build();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
