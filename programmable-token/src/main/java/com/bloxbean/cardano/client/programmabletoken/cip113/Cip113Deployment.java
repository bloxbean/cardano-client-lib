package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

/**
 * One CIP-113 deployment — the shared Layer-1/Layer-2 infrastructure a set of programmable
 * tokens lives on.
 *
 * <p>The standard identifies a version by the hash of the bootstrap transaction that
 * initialised the framework, so this object doubles as the version descriptor. A successor
 * standard would be a new deployment type plus a new internal strategy; the public
 * transaction API does not change.</p>
 */
@Value
@Builder(toBuilder = true)
public class Cip113Deployment {

    /** The "CIP-113 version" — the bootstrap transaction hash. Informational. */
    String bootstrapTxHash;

    Network network;

    /** One-shot NFT policy marking the coordination UTxO. The deployment's permanent anchor. */
    String paramsPolicy;

    /** Payment credential of every programmable-token UTxO. */
    String programmableLogicBaseHash;

    /** Dispatcher every base-script spend withdraws. Coordination datum field 0. */
    String programmableLogicGlobalHash;

    /** Replaceable issuance rules, withdrawn on every mint and burn. Coordination datum field 1. */
    String issuanceLogicHash;

    /** Core delegate invoked via withdraw-zero on an ordinary transfer. */
    String transferScriptHash;

    /** Core delegate invoked via withdraw-zero on an admin action. */
    String thirdPartyScriptHash;

    String unfrackingScriptHash;

    /**
     * Whether the dispatcher accepts {@code UnfrackingAct}. It is compiled against the unfracking
     * hash it will require; every public v0.0.1 deployment used 28 zero bytes, which no script
     * has, so none of them can unfrack.
     */
    boolean unfrackingEnabled;

    /** Upgrade authority's withdraw-zero credential. Read by {@code coordination_spend} only. */
    String upgradeScriptHash;

    /**
     * Policy of the NFTs marking registry nodes. A node's asset name is its token's policy id.
     * Mint and spend share one registry script, so this is also the nodes' address.
     */
    String registryNodeCs;

    /** Policy of the NFT marking the issuance-template UTxO. */
    String issuanceCborHexCs;

    /**
     * Where the issuance template lives. Locked at an always-fail script forever, so this never
     * changes. Optional: without it the template is found by asking the backend who holds its NFT.
     */
    TransactionInput issuanceTemplateRef;

    /** Cap on the size of a base-script output's inline datum. A compile-time parameter of the delegates. */
    int maxInlineDatumBytes;

    /** Where scripts are published as reference scripts, by script hash. Verified at resolution. */
    @Singular("referenceScript")
    Map<String, TransactionInput> referenceScripts;

    /** Asset name of the coordination NFT. */
    public static final String PROTOCOL_PARAMS_ASSET_NAME = "ProtocolParams";

    /** Asset name of the issuance-template NFT. */
    public static final String ISSUANCE_CBOR_HEX_ASSET_NAME = "IssuanceCborHex";

    public Credential programmableLogicBaseCredential() {
        return Credential.fromScript(programmableLogicBaseHash);
    }

    public Credential programmableLogicGlobalCredential() {
        return Credential.fromScript(programmableLogicGlobalHash);
    }

    public Credential issuanceLogicCredential() {
        return Credential.fromScript(issuanceLogicHash);
    }

    public Credential transferCredential() {
        return Credential.fromScript(transferScriptHash);
    }

    public Credential thirdPartyCredential() {
        return Credential.fromScript(thirdPartyScriptHash);
    }

    /** Core delegate invoked via withdraw-zero on a holder-driven unfracking. */
    public Credential unfrackingCredential() {
        return Credential.fromScript(unfrackingScriptHash);
    }

    /** Address of the registry linked list. Payment credential only, no stake part. */
    public Address registryAddress() {
        return AddressProvider.getEntAddress(Credential.fromScript(registryNodeCs), network);
    }

    /** Address of the coordination UTxO: the params policy's own script, no stake part. */
    public Address protocolParamsAddress() {
        return AddressProvider.getEntAddress(Credential.fromScript(paramsPolicy), network);
    }

    /** Reward address whose withdraw-zero invokes the dispatcher. */
    public String programmableLogicGlobalRewardAddress() {
        return AddressProvider.getRewardAddress(programmableLogicGlobalCredential(), network).toBech32();
    }

    /** Reward address whose withdraw-zero invokes issuance_logic. */
    public String issuanceLogicRewardAddress() {
        return AddressProvider.getRewardAddress(issuanceLogicCredential(), network).toBech32();
    }

    /** Reward address whose withdraw-zero invokes the core transfer delegate. */
    public String transferRewardAddress() {
        return AddressProvider.getRewardAddress(transferCredential(), network).toBech32();
    }

    /** Reward address whose withdraw-zero invokes the core third-party delegate. */
    public String thirdPartyRewardAddress() {
        return AddressProvider.getRewardAddress(thirdPartyCredential(), network).toBech32();
    }

    /** Reward address whose withdraw-zero invokes the core unfracking delegate. */
    public String unfrackingRewardAddress() {
        return AddressProvider.getRewardAddress(unfrackingCredential(), network).toBech32();
    }

    /**
     * Merge the live coordination datum into this deployment.
     *
     * <p>This is the robust path: the datum names what an in-place upgrade may swap — the
     * dispatcher, issuance logic and delegates — while the base script hash, and therefore every
     * token address, stays unchanged.</p>
     */
    public Cip113Deployment withResolvedParams(ProgrammableLogicGlobalParams params) {
        return toBuilder()
                .programmableLogicGlobalHash(hexOf(params.getProgrammableLogicGlobalCred()))
                .issuanceLogicHash(hexOf(params.getIssuanceLogicCred()))
                .transferScriptHash(hexOf(params.getTransferCred()))
                .thirdPartyScriptHash(hexOf(params.getThirdPartyCred()))
                .upgradeScriptHash(hexOf(params.getUpgradeCred()))
                .build();
    }

    private static String hexOf(Credential credential) {
        return com.bloxbean.cardano.client.util.HexUtil.encodeHexString(credential.getBytes());
    }
}
