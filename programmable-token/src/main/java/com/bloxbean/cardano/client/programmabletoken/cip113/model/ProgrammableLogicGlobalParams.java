package com.bloxbean.cardano.client.programmabletoken.cip113.model;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Builder;
import lombok.Value;

/**
 * Datum of the coordination UTxO — the deployment descriptor.
 *
 * <p>Constructor 0, seven fields in order
 * ({@code programmable_logic/params/ProgrammableLogicGlobalParams}):</p>
 *
 * <pre>
 * 0 registry_node_cs        PolicyId
 * 1 prog_logic_cred         Credential   the base script every programmable token lives at
 * 2 transfer_cred           Credential   core delegate for ordinary transfers
 * 3 third_party_cred        Credential   core delegate for admin actions
 * 4 unfracking_cred         Credential
 * 5 upgrade_cred            Credential
 * 6 max_inline_datum_bytes  Int
 * </pre>
 *
 * <p>Reading this datum is the robust way to resolve a deployment: it survives in-place
 * upgrades where a delegate is swapped without the base script hash changing.</p>
 */
@Value
@Builder
public class ProgrammableLogicGlobalParams {
    String registryNodeCs;
    Credential progLogicCred;
    Credential transferCred;
    Credential thirdPartyCred;
    Credential unfrackingCred;
    Credential upgradeCred;
    int maxInlineDatumBytes;

    public static ProgrammableLogicGlobalParams fromPlutusData(PlutusData data) {
        ConstrPlutusData c = Cip113Data.asConstr(data, "ProgrammableLogicGlobalParams", 0, 7);
        return ProgrammableLogicGlobalParams.builder()
                .registryNodeCs(Cip113Data.hex(Cip113Data.field(c, 0)))
                .progLogicCred(Cip113Data.toCredential(Cip113Data.field(c, 1)))
                .transferCred(Cip113Data.toCredential(Cip113Data.field(c, 2)))
                .thirdPartyCred(Cip113Data.toCredential(Cip113Data.field(c, 3)))
                .unfrackingCred(Cip113Data.toCredential(Cip113Data.field(c, 4)))
                .upgradeCred(Cip113Data.toCredential(Cip113Data.field(c, 5)))
                .maxInlineDatumBytes(Cip113Data.integer(Cip113Data.field(c, 6)).intValue())
                .build();
    }
}
