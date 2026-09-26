package com.bloxbean.cardano.client.cip.cip113.model;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.Builder;
import lombok.Value;

/**
 * A registry node: one programmable token's on-chain configuration.
 *
 * <p>Constructor 0, <b>seven positional fields in this exact order</b>
 * ({@code registry_node/RegistryNode} in plutus.json). Reordering them produces silently
 * invalid CBOR, so the order here is load-bearing:</p>
 *
 * <pre>
 * 0 key                                bytes(28)  policy id            IMMUTABLE
 * 1 next                               bytes(28)  linked-list pointer  IMMUTABLE
 * 2 minting_logic_script               Credential                      IMMUTABLE (bound to key)
 * 3 transfer_logic_script              Credential                      mutable
 * 4 third_party_transfer_logic_script  Credential                      mutable
 * 5 unfracking_logic_script            Credential                      mutable
 * 6 global_state_cs                    bytes(0|28)                     mutable
 * </pre>
 */
@Value
@Builder(toBuilder = true)
public class RegistryNode {
    String key;
    String next;
    Credential mintingLogicScript;
    Credential transferLogicScript;
    Credential thirdPartyTransferLogicScript;
    Credential unfrackingLogicScript;
    String globalStateCs;

    public ConstrPlutusData toPlutusData() {
        return ConstrPlutusData.of(0,
                Cip113Data.bytesOfHex(key),
                Cip113Data.bytesOfHex(next),
                Cip113Data.credential(mintingLogicScript),
                Cip113Data.credential(transferLogicScript),
                Cip113Data.credential(thirdPartyTransferLogicScript),
                Cip113Data.credential(unfrackingLogicScript),
                Cip113Data.bytesOfHex(globalStateCs == null ? "" : globalStateCs));
    }

    public static RegistryNode fromPlutusData(PlutusData data) {
        ConstrPlutusData c = Cip113Data.asConstr(data, "RegistryNode", 0, 7);
        return RegistryNode.builder()
                .key(Cip113Data.hex(Cip113Data.field(c, 0)))
                .next(Cip113Data.hex(Cip113Data.field(c, 1)))
                .mintingLogicScript(Cip113Data.toCredential(Cip113Data.field(c, 2)))
                .transferLogicScript(Cip113Data.toCredential(Cip113Data.field(c, 3)))
                .thirdPartyTransferLogicScript(Cip113Data.toCredential(Cip113Data.field(c, 4)))
                .unfrackingLogicScript(Cip113Data.toCredential(Cip113Data.field(c, 5)))
                .globalStateCs(Cip113Data.hex(Cip113Data.field(c, 6)))
                .build();
    }

    /** True when this token declares a global-state NFT that transfers must reference. */
    public boolean hasGlobalState() {
        return globalStateCs != null && !globalStateCs.isEmpty();
    }
}
