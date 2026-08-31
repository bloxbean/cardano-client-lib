package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the registry linked list.
 *
 * <p>This is the extension point for indexers. The default implementation scans every UTxO at
 * the registry address, which is O(all registered tokens) per lookup. A backend that can
 * query asset holders should override {@link #byPolicy} with an exact lookup, since a node's
 * NFT asset name <i>is</i> the token's policy id — {@code assets/{registryNodeCs}{policyId}}
 * addresses it directly. The covering-node search has no exact form and always scans.</p>
 */
public interface RegistryLookup {

    /** The node registering {@code policyId}, if the token is registered at all. */
    Optional<RegistryNodeUtxo> byPolicy(String policyId);

    /**
     * The node proving {@code policyId} is <i>not</i> registered: the one whose key is the
     * largest still below it, and whose next is above it.
     */
    RegistryNodeUtxo coveringNode(String policyId);

    List<RegistryNodeUtxo> all();

    /** A registry node together with the UTxO carrying it. */
    @Value
    class RegistryNodeUtxo {
        Utxo utxo;
        RegistryNode datum;
    }

    /**
     * Drop any cached view of the registry.
     *
     * <p>Implementations are free to cache — a scan is expensive and the registry changes rarely —
     * while transaction builds always request a fresh immutable snapshot. This method is an
     * implementation hook; application code does not need to coordinate invalidation.</p>
     */
    default void invalidate() {
    }

    /** Scanning implementation. Correct everywhere, cheap nowhere. */
    class Scanning implements RegistryLookup {
        private final UtxoSupplier utxoSupplier;
        private final Cip113Deployment deployment;
        private List<RegistryNodeUtxo> cache;

        public Scanning(UtxoSupplier utxoSupplier, Cip113Deployment deployment) {
            this.utxoSupplier = utxoSupplier;
            this.deployment = deployment;
        }

        /** {@inheritDoc} */
        @Override
        public void invalidate() {
            cache = null;
        }

        @Override
        public List<RegistryNodeUtxo> all() {
            if (cache != null) return cache;

            String registryAddress = deployment.registryAddress().toBech32();
            List<RegistryNodeUtxo> nodes = new ArrayList<>();
            for (Utxo utxo : utxoSupplier.getAll(registryAddress)) {
                if (utxo.getInlineDatum() == null || utxo.getInlineDatum().isEmpty()) continue;
                if (!carriesRegistryNft(utxo)) continue;
                try {
                    PlutusData data = PlutusData.deserialize(HexUtil.decodeHexString(utxo.getInlineDatum()));
                    nodes.add(new RegistryNodeUtxo(utxo, RegistryNode.fromPlutusData(data)));
                } catch (Exception e) {
                    // This UTxO carries a registry NFT, so it *is* a node — skipping it would
                    // make its policy read as unregistered, and payToAddress would then route a
                    // programmable token down the ordinary path, straight past the rules that
                    // exist to constrain it. Fail closed, naming the UTxO.
                    throw new Cip113Exception("Registry node " + utxo.getTxHash() + "#"
                            + utxo.getOutputIndex() + " carries a registry NFT but its inline datum"
                            + " could not be decoded. Treating it as absent would report its policy"
                            + " as unregistered and silently move that token as an ordinary asset,"
                            + " so the scan fails instead. The deployment's node datum format and"
                            + " this library's decoder have diverged.", e);
                }
            }
            cache = nodes;
            return nodes;
        }

        private boolean carriesRegistryNft(Utxo utxo) {
            String cs = deployment.getRegistryNodeCs();
            if (cs == null || cs.isEmpty()) {
                // Without the node policy every UTxO at the address looks like a node, which is
                // how an unresolved deployment used to produce a plausible but wrong registry.
                throw new Cip113Exception("The deployment has no registry node policy, so registry"
                        + " nodes cannot be told apart from anything else sitting at the same"
                        + " address. Resolve the deployment before scanning the registry.");
            }
            for (Amount amount : utxo.getAmount()) {
                if (amount.getUnit() != null && amount.getUnit().startsWith(cs)) return true;
            }
            return false;
        }

        @Override
        public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
            // Standalone membership reads are live. Transaction builds take one explicit,
            // immutable snapshot and do not call this scanning implementation repeatedly.
            invalidate();
            return all().stream()
                    .filter(n -> n.getDatum().getKey().equalsIgnoreCase(policyId))
                    .findFirst();
        }

        @Override
        public RegistryNodeUtxo coveringNode(String policyId) {
            return all().stream()
                    .filter(n -> PolicyOrdering.covers(
                            n.getDatum().getKey(), n.getDatum().getNext(), policyId))
                    .findFirst()
                    .orElseThrow(() -> new Cip113Exception(
                            "No covering node found for policy " + policyId
                            + ". The registry scan returned " + all().size() + " node(s);"
                            + " check the deployment's registry address and node policy."));
        }
    }
}
