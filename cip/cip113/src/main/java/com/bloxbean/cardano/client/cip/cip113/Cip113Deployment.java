package com.bloxbean.cardano.client.cip.cip113;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.cip.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.common.model.Network;
import lombok.Builder;
import lombok.Value;

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

    /** Core delegate invoked via withdraw-zero on an ordinary transfer. */
    String transferScriptHash;

    /** Core delegate invoked via withdraw-zero on an admin action. */
    String thirdPartyScriptHash;

    String unfrackingScriptHash;

    /** Upgrade authority's withdraw-zero credential. Read by {@code coordination_spend} only. */
    String upgradeScriptHash;

    /** Spend script guarding registry nodes; also their address. */
    String registrySpendScriptHash;

    /** Policy of the NFTs marking registry nodes. A node's asset name is its token's policy id. */
    String registryNodeCs;

    /** Policy of the NFT marking the issuance-template UTxO. */
    String issuanceCborHexCs;

    /** Cap on the size of a base-script output's inline datum, from the coordination datum. */
    int maxInlineDatumBytes;

    /** Asset name of the coordination NFT. */
    public static final String PROTOCOL_PARAMS_ASSET_NAME = "ProtocolParams";

    /** Asset name of the issuance-template NFT. */
    public static final String ISSUANCE_CBOR_HEX_ASSET_NAME = "IssuanceCborHex";

    public Credential programmableLogicBaseCredential() {
        return Credential.fromScript(programmableLogicBaseHash);
    }

    public Credential transferCredential() {
        return Credential.fromScript(transferScriptHash);
    }

    public Credential thirdPartyCredential() {
        return Credential.fromScript(thirdPartyScriptHash);
    }

    /** Address of the registry linked list. Payment credential only, no stake part. */
    public Address registryAddress() {
        return AddressProvider.getEntAddress(Credential.fromScript(registrySpendScriptHash), network);
    }

    /** Reward address whose withdraw-zero invokes the core transfer delegate. */
    public String transferRewardAddress() {
        return AddressProvider.getRewardAddress(transferCredential(), network).toBech32();
    }

    /** Reward address whose withdraw-zero invokes the core third-party delegate. */
    public String thirdPartyRewardAddress() {
        return AddressProvider.getRewardAddress(thirdPartyCredential(), network).toBech32();
    }

    /**
     * Merge the live coordination datum into this deployment.
     *
     * <p>This is the robust path: the datum <i>is</i> the deployment descriptor, so reading it
     * picks up in-place delegate upgrades that leave the base script hash — and therefore every
     * token address — unchanged.</p>
     */
    public Cip113Deployment withResolvedParams(ProgrammableLogicGlobalParams params) {
        return toBuilder()
                .registryNodeCs(params.getRegistryNodeCs())
                .programmableLogicBaseHash(hexOf(params.getProgLogicCred()))
                .transferScriptHash(hexOf(params.getTransferCred()))
                .thirdPartyScriptHash(hexOf(params.getThirdPartyCred()))
                .unfrackingScriptHash(hexOf(params.getUnfrackingCred()))
                .upgradeScriptHash(hexOf(params.getUpgradeCred()))
                .maxInlineDatumBytes(params.getMaxInlineDatumBytes())
                .build();
    }

    private static String hexOf(Credential credential) {
        return com.bloxbean.cardano.client.util.HexUtil.encodeHexString(credential.getBytes());
    }
}
