package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCredential;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistration;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.RegistryNodeSpec;

/** CIP-113-specific registration data factory for the neutral authoring facade. */
public final class Cip113Registration {
    private Cip113Registration() { }

    public static ProgrammableTokenRegistration from(RegistryNodeSpec spec) {
        spec.validate();
        return ProgrammableTokenRegistration.builder()
                .mintingLogicScript(ProgrammableTokenCredential.from(spec.getMintingLogicScript()))
                .transferLogicScript(ProgrammableTokenCredential.from(spec.getTransferLogicScript()))
                .thirdPartyTransferLogicScript(ProgrammableTokenCredential.from(
                        spec.getThirdPartyTransferLogicScript()))
                .unfrackingLogicScript(ProgrammableTokenCredential.from(
                        spec.getUnfrackingLogicScript()))
                .globalStateCs(spec.getGlobalStateCs())
                .build();
    }

    /** Convert the neutral typed registration value into the CIP-113 registry-node model. */
    public static RegistryNodeSpec toSpec(ProgrammableTokenRegistration registration) {
        if (registration == null) throw new IllegalArgumentException("registration is required");
        return RegistryNodeSpec.builder()
                .mintingLogicScript(registration.getMintingLogicScript().toCredential())
                .transferLogicScript(registration.getTransferLogicScript().toCredential())
                .thirdPartyTransferLogicScript(
                        registration.getThirdPartyTransferLogicScript().toCredential())
                .unfrackingLogicScript(registration.getUnfrackingLogicScript().toCredential())
                .globalStateCs(registration.getGlobalStateCs())
                .build();
    }
}
