package com.bloxbean.cardano.client.cip.cip113;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.cip.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.ByteArrayOutputStream;

/**
 * Derives a programmable token's policy id.
 *
 * <p>A token's policy id <i>is</i> {@code issuance_mint} parameterized by its issuance
 * credential — but it never needs a UPLC parameter applier, because the reference
 * implementation derives it by direct byte assembly and hashing
 * ({@code lib/utils.ak:apply_hashed_parameter}):</p>
 *
 * <pre>
 * policyId = blake2b_224( 0x03 ‖ prefix ‖ mintingLogicScriptHash ‖ postfix )
 * </pre>
 *
 * <p>{@code 0x03} is the Plutus V3 version header; {@code prefix} and {@code postfix} come
 * from the on-chain issuance-template UTxO. {@code registry_mint} re-checks this binding at
 * registration, so a node cannot lie about which policy it governs.</p>
 */
public final class PolicyIdDerivation {

    private static final byte PLUTUS_V3_VERSION_HEADER = 0x03;

    private PolicyIdDerivation() {}

    /**
     * @param mintingLogicScript must be a {@link CredentialType#Script} credential — the
     *                           on-chain check is {@code expect Script(hashed_param)}, so a
     *                           token whose issuance authority is a bare key cannot be
     *                           registered at all.
     */
    public static String derive(IssuanceCborHex template, Credential mintingLogicScript) {
        byte[] script = issuanceScriptBytes(template, mintingLogicScript);

        ByteArrayOutputStream hashed = new ByteArrayOutputStream();
        hashed.write(PLUTUS_V3_VERSION_HEADER);
        writeAll(hashed, script);

        return HexUtil.encodeHexString(Blake2bUtil.blake2bHash224(hashed.toByteArray()));
    }

    /**
     * The token's {@code issuance_mint} script, assembled rather than compiled.
     *
     * <p>Applying a parameter to a Plutus script normally needs a UPLC applier, and a freshly
     * registered token's script has never been used on chain so it cannot be fetched by hash
     * either. Neither is necessary here: the issuance template is stored as a prefix and postfix
     * with the parameter hole between them, so the applied script is literally
     * {@code prefix ‖ mintingLogicScriptHash ‖ postfix}. That is the same byte sequence
     * {@link #derive} hashes, which is what guarantees the script and the policy id agree.</p>
     */
    public static byte[] issuanceScriptBytes(IssuanceCborHex template, Credential mintingLogicScript) {
        if (mintingLogicScript.getType() != CredentialType.Script) {
            throw new Cip113Exception(
                    "minting_logic_script must be a Script credential — the policy id is derived"
                    + " from the issuance template parameterized with its hash, and the on-chain"
                    + " check rejects a VerificationKey credential.");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, template.getPrefixCborHex());
        writeAll(out, mintingLogicScript.getBytes());
        writeAll(out, template.getPostfixCborHex());
        return out.toByteArray();
    }

    /**
     * The token's {@code issuance_mint} script, ready to attach as a minting validator.
     *
     * <p>Its hash is the token's policy id by construction.</p>
     */
    public static PlutusScript issuanceScript(IssuanceCborHex template, Credential mintingLogicScript) {
        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                HexUtil.encodeHexString(issuanceScriptBytes(template, mintingLogicScript)),
                PlutusVersion.v3);
    }

    private static void writeAll(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
