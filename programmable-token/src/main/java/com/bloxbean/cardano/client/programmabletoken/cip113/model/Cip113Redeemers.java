package com.bloxbean.cardano.client.programmabletoken.cip113.model;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;
import java.util.Map;

/**
 * The CIP-113 core redeemers. Every one of them carries positional indices into a ledger
 * ordering, which is why they are built at the end of assembly rather than up front.
 *
 * <p>Constructor indices are taken verbatim from {@code plutus.json}.</p>
 */
public final class Cip113Redeemers {

    private Cip113Redeemers() {}

    // ------------------------------------------- BaseSpendRedeemer (PLB spend)

    /**
     * {@code BaseSpendRedeemer{params_idx, wdrl_idx}} — sole constructor 0. {@code wdrl_idx}
     * points at the {@code programmable_logic_global} withdrawal, never at a delegate.
     */
    public static ConstrPlutusData baseSpend(int paramsIdx, int plgWdrlIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(paramsIdx), Cip113Data.i(plgWdrlIdx));
    }

    // ------------------------------------- ProgrammableLogicGlobalRedeemer (PLG)

    /** {@code TransferAct} — constructor 0, no fields. */
    public static ConstrPlutusData transferAct() {
        return ConstrPlutusData.of(0);
    }

    /** {@code ThirdPartyAct} — constructor 1, no fields. */
    public static ConstrPlutusData thirdPartyAct() {
        return ConstrPlutusData.of(1);
    }

    /** {@code UnfrackingAct} — constructor 2, no fields. */
    public static ConstrPlutusData unfrackingAct() {
        return ConstrPlutusData.of(2);
    }

    // ------------------------------------------------------- TransferRedeemer

    /**
     * {@code TransferRedeemer{proofs}} — sole constructor 0.
     *
     * <p>{@code proofs} must hold one entry per distinct non-lovelace policy present in the
     * spent base-script inputs, in ascending unsigned-bytewise policy order — the order the
     * validator walks those inputs' assets.</p>
     */
    public static ConstrPlutusData transfer(List<PlutusData> proofs) {
        return ConstrPlutusData.of(0, Cip113Data.list(proofs));
    }

    /** {@code TokenExists{node_idx}} — constructor 0. */
    public static ConstrPlutusData tokenExists(int nodeIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(nodeIdx));
    }

    /** {@code TokenDoesNotExist{node_idx}} — constructor 1, pointing at the covering node. */
    public static ConstrPlutusData tokenDoesNotExist(int nodeIdx) {
        return ConstrPlutusData.of(1, Cip113Data.i(nodeIdx));
    }

    // -------------------------------------------------- MintingRegistryProof

    /** {@code RefInput{index}} — constructor 0, index into reference inputs. */
    public static ConstrPlutusData mintRefInput(int referenceInputIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(referenceInputIdx));
    }

    /** {@code OutputIndex{index}} — constructor 1, index into <b>outputs</b>. */
    public static ConstrPlutusData mintOutputIndex(int outputIdx) {
        return ConstrPlutusData.of(1, Cip113Data.i(outputIdx));
    }

    /** {@code IssuanceMintRedeemer{params_idx}} — constructor 0, every token's issuance_mint. */
    public static ConstrPlutusData issuanceMint(int paramsIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(paramsIdx));
    }

    /**
     * {@code IssuanceLogicRedeemer = Pairs<PolicyId, MintingRegistryProof>} — issuance_logic's
     * withdraw-zero redeemer, encoded as a Plutus map in the iteration order of {@code proofs}.
     */
    public static MapPlutusData issuanceLogic(Map<String, PlutusData> proofs) {
        MapPlutusData map = new MapPlutusData();
        proofs.forEach((policyId, proof) -> map.put(Cip113Data.bytesOfHex(policyId), proof));
        return map;
    }

    // ------------------------------------------------------ RegistryRedeemer

    /** {@code RegistryInit} — constructor 0, no fields. */
    public static ConstrPlutusData registryInit() {
        return ConstrPlutusData.of(0);
    }

    /** {@code RegistryInsert{key, minting_logic_script}} — constructor 1. */
    public static ConstrPlutusData registryInsert(String policyId, Credential mintingLogicScript) {
        return ConstrPlutusData.of(1,
                Cip113Data.bytesOfHex(policyId),
                Cip113Data.credential(mintingLogicScript));
    }

    /** Spending a registry node: the registry's spend handler accepts any {@code Data}. */
    public static ConstrPlutusData registrySpend() {
        return ConstrPlutusData.of(0);
    }

    // ---------------------------------------------------- ThirdPartyRedeemer

    /** {@code ThirdPartyRedeemer{registry_node_idx, outputs_start_idx}} — constructor 0. */
    public static ConstrPlutusData thirdParty(int registryNodeIdx, int outputsStartIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(registryNodeIdx), Cip113Data.i(outputsStartIdx));
    }

    // ----------------------------------------------------- UnfrackingRedeemer

    /**
     * {@code UnfrackingRedeemer{registry_node_idx, outputs_start_idx}} — constructor 0.
     *
     * <p>Same index-hint discipline as {@link #thirdParty}: the acted-on policy's registry node is
     * a reference input, and every base-script input is paired positionally with a continuing
     * output from {@code outputs_start_idx} on.</p>
     */
    public static ConstrPlutusData unfracking(int registryNodeIdx, int outputsStartIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(registryNodeIdx), Cip113Data.i(outputsStartIdx));
    }
}
