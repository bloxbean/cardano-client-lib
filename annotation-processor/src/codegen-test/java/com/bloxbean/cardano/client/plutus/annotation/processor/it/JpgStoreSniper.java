package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * JpgStore Sniper contract (Plutus V3) integration test marker interface.
 *
 * <p>This blueprint was generated from jpgstore/sniper-onchain using Aiken v1.1.21+42babe5.
 * It contains 9 validators across 3 groups (merkle_snipe, policy_snipe, settings)
 * with mint, spend, and else purposes.</p>
 *
 * <p><strong>Implicit V3 Default Test:</strong> This marker deliberately omits the
 * {@code @AikenStdlib} annotation to verify that V3 is the implicit default.
 * Shared types (Address, Credential, OutputReference, PolicyId, AssetName, etc.)
 * should resolve via the V3 registry without explicit annotation.</p>
 *
 * <p><b>Custom types:</b></p>
 * <ul>
 *   <li>SettingsDatum, MerkleSnipeDatum, PolicySnipeDatum</li>
 *   <li>MerkleMintRedeemer, MerkleSpendRedeemer, SpendRedeemer</li>
 * </ul>
 *
 * <p><b>External dependency types (generated, not shared):</b></p>
 * <ul>
 *   <li>aiken_merkle_tree: Proof, ProofItem, Root</li>
 * </ul>
 */
@Blueprint(fileInResources = "blueprint/jpgstore_sniper_aiken_v1_1_21_42babe5.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.jpgstoresniper")
public interface JpgStoreSniper {
}
