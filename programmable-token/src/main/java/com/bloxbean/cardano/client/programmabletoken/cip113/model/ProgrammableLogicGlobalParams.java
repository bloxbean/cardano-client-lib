package com.bloxbean.cardano.client.programmabletoken.cip113.model;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Datum of the coordination UTxO — the deployment descriptor.
 *
 * <p>Constructor 0, six fields in order
 * ({@code programmable_logic/params/ProtocolParams}):</p>
 *
 * <pre>
 * 0 programmable_logic_global_cred  Credential          the dispatcher every base-script spend withdraws
 * 1 issuance_logic_cred             Credential          replaceable half of issuance
 * 2 transfer_cred                   Credential          core delegate for ordinary transfers
 * 3 third_party_cred                Credential          core delegate for admin actions
 * 4 upgrade_cred                    Credential
 * 5 pending_upgrade_cred            Option&lt;Credential&gt;
 * </pre>
 *
 * <p>Reading this datum is the robust way to resolve a deployment: it survives in-place
 * upgrades where a delegate is swapped without the base script hash changing. The base script,
 * registry, unfracking script and inline-datum bound are not in it — they are compile-time
 * parameters of the validators and come from the {@code Cip113Deployment}.</p>
 */
@Value
@Builder
public class ProgrammableLogicGlobalParams {
    Credential programmableLogicGlobalCred;
    Credential issuanceLogicCred;
    Credential transferCred;
    Credential thirdPartyCred;
    Credential upgradeCred;
    /** {@code null} when no successor authority is nominated. */
    Credential pendingUpgradeCred;

    public static ProgrammableLogicGlobalParams fromPlutusData(PlutusData data) {
        ConstrPlutusData c = Cip113Data.asConstr(data, "ProgrammableLogicGlobalParams", 0, 6);
        return ProgrammableLogicGlobalParams.builder()
                .programmableLogicGlobalCred(Cip113Data.toCredential(Cip113Data.field(c, 0)))
                .issuanceLogicCred(Cip113Data.toCredential(Cip113Data.field(c, 1)))
                .transferCred(Cip113Data.toCredential(Cip113Data.field(c, 2)))
                .thirdPartyCred(Cip113Data.toCredential(Cip113Data.field(c, 3)))
                .upgradeCred(Cip113Data.toCredential(Cip113Data.field(c, 4)))
                .pendingUpgradeCred(optionalCredential(Cip113Data.field(c, 5)))
                .build();
    }

    public ConstrPlutusData toPlutusData() {
        return ConstrPlutusData.of(0,
                Cip113Data.credential(programmableLogicGlobalCred),
                Cip113Data.credential(issuanceLogicCred),
                Cip113Data.credential(transferCred),
                Cip113Data.credential(thirdPartyCred),
                Cip113Data.credential(upgradeCred),
                pendingUpgradeCred == null
                        ? ConstrPlutusData.of(1)
                        : ConstrPlutusData.of(0, Cip113Data.credential(pendingUpgradeCred)));
    }

    /** {@code Option}: Some = constructor 0 with one field, None = constructor 1 with none. */
    private static Credential optionalCredential(PlutusData data) {
        ConstrPlutusData option = Cip113Data.asConstr(data, "Option<Credential>");
        List<PlutusData> fields = option.getData().getPlutusDataList();
        if (option.getAlternative() == 1 && fields.isEmpty()) return null;
        if (option.getAlternative() == 0 && fields.size() == 1) return Cip113Data.toCredential(fields.get(0));
        throw new Cip113Exception("pending_upgrade_cred is neither None nor Some(Credential)");
    }
}
