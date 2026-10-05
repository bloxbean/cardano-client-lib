package com.bloxbean.cardano.client.cip.cip113.model;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;

/**
 * The CIP-113 core redeemers. Every one of them carries positional indices into a ledger
 * ordering, which is why they are built at the end of assembly rather than up front.
 *
 * <p>Constructor indices are taken verbatim from {@code plutus.json}.</p>
 */
public final class Cip113Redeemers {

    private Cip113Redeemers() {}

    // ------------------------------------------- BaseSpendRedeemer (PLB spend)

    /** {@code SpendViaTransfer{params_idx, wdrl_idx}} — constructor 0. */
    public static ConstrPlutusData spendViaTransfer(int paramsIdx, int wdrlIdx) {
        return ConstrPlutusData.of(0, Cip113Data.i(paramsIdx), Cip113Data.i(wdrlIdx));
    }

    /** {@code SpendViaThirdParty{params_idx, wdrl_idx}} — constructor 1. */
    public static ConstrPlutusData spendViaThirdParty(int paramsIdx, int wdrlIdx) {
        return ConstrPlutusData.of(1, Cip113Data.i(paramsIdx), Cip113Data.i(wdrlIdx));
    }

    /** {@code SpendViaUnfracking{params_idx, wdrl_idx}} — constructor 2. */
    public static ConstrPlutusData spendViaUnfracking(int paramsIdx, int wdrlIdx) {
        return ConstrPlutusData.of(2, Cip113Data.i(paramsIdx), Cip113Data.i(wdrlIdx));
    }

    // ------------------------------------------------------- TransferRedeemer

    /**
     * {@code TransferRedeemer{params_idx, proofs}} — sole constructor 0.
     *
     * <p>{@code proofs} must hold one entry per distinct non-lovelace policy present in the
     * spent base-script inputs, in ascending unsigned-bytewise policy order — the order the
     * validator walks those inputs' assets.</p>
     */
    public static ConstrPlutusData transfer(int paramsIdx, List<PlutusData> proofs) {
        return ConstrPlutusData.of(0, Cip113Data.i(paramsIdx), Cip113Data.list(proofs));
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

    // ---------------------------------------------------- ThirdPartyRedeemer

    /** {@code ThirdPartyRedeemer{params_idx, registry_node_idx, outputs_start_idx}} — constructor 0. */
    public static ConstrPlutusData thirdParty(int paramsIdx, int registryNodeIdx, int outputsStartIdx) {
        return ConstrPlutusData.of(0,
                Cip113Data.i(paramsIdx), Cip113Data.i(registryNodeIdx), Cip113Data.i(outputsStartIdx));
    }
}
