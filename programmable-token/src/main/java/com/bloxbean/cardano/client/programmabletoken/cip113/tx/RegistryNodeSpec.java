package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import lombok.Builder;
import lombok.Value;

/**
 * What a caller decides when registering a new programmable token.
 *
 * <p>Only the token's own rules — the four logic credentials and whether it has global state.
 * The policy id is not a choice: it is derived from the issuance credential, and
 * {@code registry_mint} re-checks that binding on chain.</p>
 */
@Value
@Builder
public class RegistryNodeSpec {

    /**
     * Issuance authority. <b>Must be a script credential</b> — the policy id is derived from its
     * hash, and the on-chain check is {@code expect Script(hashed_param)}.
     */
    Credential mintingLogicScript;

    /** Runs on every ordinary transfer of this token. Must be a 28-byte credential. */
    Credential transferLogicScript;

    /** Runs on admin actions — seizure, clawback. Must be a 28-byte credential. */
    Credential thirdPartyTransferLogicScript;

    /**
     * Gate on holder-driven UTxO restructuring. Defaults to {@link #unfrackingForbidden()},
     * the empty-verification-key sentinel meaning no party may unfrack this token.
     */
    @Builder.Default
    Credential unfrackingLogicScript = unfrackingForbidden();

    /** Policy of this token's global-state NFT, or null when it has none. */
    String globalStateCs;

    /** The empty-vkey sentinel: unfracking is forbidden for this token. */
    public static Credential unfrackingForbidden() {
        return Credential.fromKey(new byte[0]);
    }

    /**
     * Reject specs the on-chain validator would reject, at build time and with a reason.
     *
     * <p>Mirrors {@code linked_list.ak:is_inserted_directory_node}, which requires 28-byte
     * credentials for the transfer and third-party fields — empty credentials belong only to the
     * registry's origin node.</p>
     */
    public void validate() {
        if (mintingLogicScript == null || mintingLogicScript.getType() != CredentialType.Script) {
            throw new Cip113Exception("mintingLogicScript must be a Script credential — the policy"
                    + " id is derived from its hash and the on-chain check rejects a key credential.");
        }
        require28Bytes(transferLogicScript, "transferLogicScript");
        require28Bytes(thirdPartyTransferLogicScript, "thirdPartyTransferLogicScript");

        // Unfracking accepts either a real 28-byte credential or the exact empty-vkey sentinel.
        // An empty *script* credential, or any other length, is rejected on chain.
        if (unfrackingLogicScript == null || unfrackingLogicScript.getBytes() == null) {
            throw new Cip113Exception("unfrackingLogicScript must not be null;"
                    + " use RegistryNodeSpec.unfrackingForbidden() to forbid unfracking.");
        }
        int unfrackingLength = unfrackingLogicScript.getBytes().length;
        boolean sentinel = unfrackingLength == 0
                && unfrackingLogicScript.getType() == CredentialType.Key;
        if (!sentinel && unfrackingLength != 28) {
            throw new Cip113Exception("unfrackingLogicScript must be a 28-byte credential or the"
                    + " empty-verification-key sentinel from unfrackingForbidden(); an empty script"
                    + " credential is rejected on chain.");
        }
        if (globalStateCs != null && !globalStateCs.isEmpty() && globalStateCs.length() != 56) {
            throw new Cip113Exception("globalStateCs must be empty or a 28-byte policy id,"
                    + " but was " + globalStateCs.length() / 2 + " bytes.");
        }
    }

    private static void require28Bytes(Credential credential, String field) {
        if (credential == null || credential.getBytes() == null || credential.getBytes().length != 28) {
            throw new Cip113Exception(field + " must be a 28-byte credential;"
                    + " empty credentials are only valid on the registry's origin node.");
        }
    }
}
