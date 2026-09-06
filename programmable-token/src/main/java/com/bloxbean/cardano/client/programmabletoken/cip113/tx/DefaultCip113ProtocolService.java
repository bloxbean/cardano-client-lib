package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Default read side, backed by a {@link BackendService}.
 *
 * <p>Deployment resolution walks the bootstrap transaction: the standard identifies a version
 * by that transaction's hash, and its outputs carry the coordination NFT (whose datum is the
 * deployment descriptor) and the issuance template.</p>
 *
 * <p>Resolution publishes one immutable {@link Resolved} state, atomically. Concurrent first
 * callers wait for the same attempt and receive the same result — success or failure — and a
 * failed attempt is forgotten, so the next caller retries. {@link #resolveDeployment()} always
 * performs a fresh resolution and, when it succeeds, replaces the published state. An
 * application-scoped instance is safe to share between independent concurrent builds.</p>
 */
@Slf4j
public class DefaultCip113ProtocolService implements Cip113ProtocolService {

    private final BackendService backendService;
    private final UtxoSupplier utxoSupplier;
    private final Cip113Deployment configured;
    private final DeploymentScripts scripts;
    private final ProtocolParamsSupplier protocolParamsSupplier;

    private final Object resolutionLock = new Object();
    /** The in-flight or completed resolution shared by every caller; null until first use. */
    private CompletableFuture<Resolved> resolution;

    /** Everything one successful resolution produced, published as a unit. */
    @Value
    private static class Resolved {
        Cip113Deployment deployment;
        Utxo coordinationUtxo;
        Utxo issuanceTemplateUtxo;
    }

    public DefaultCip113ProtocolService(BackendService backendService, Cip113Deployment deployment) {
        this.backendService = backendService;
        this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        this.configured = deployment;
        this.scripts = new DeploymentScripts(backendService.getScriptService(), this::deployment);
        this.protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
    }

    /** The resolved deployment when resolution has succeeded, else the configured one. */
    @Override
    public Cip113Deployment deployment() {
        Resolved resolved = publishedOrNull();
        return resolved != null ? resolved.getDeployment() : configured;
    }

    /**
     * A registry lookup bound to the resolved deployment.
     *
     * <p>The scanning lookup is stateless, so a fresh instance per call costs nothing and can
     * never bind to a deployment that has since been re-resolved.</p>
     */
    @Override
    public RegistryLookup registryLookup() {
        return new RegistryLookup.Scanning(utxoSupplier, resolved().getDeployment());
    }

    /** The live coordination UTxO. Resolves the deployment on first use if needed. */
    @Override
    public Utxo coordinationUtxo() {
        return resolved().getCoordinationUtxo();
    }

    /** The issuance-template UTxO. Resolves the deployment on first use if needed. */
    @Override
    public Utxo issuanceTemplateUtxo() {
        return resolved().getIssuanceTemplateUtxo();
    }

    /** Protocol parameters, for sizing min-ADA on programmable outputs. */
    @Override
    public ProtocolParamsSupplier protocolParamsSupplier() {
        return protocolParamsSupplier;
    }

    /**
     * This deployment's script resolver, shared by every transaction the api builds.
     *
     * <p>Exposed so a caller that already holds applied scripts can hand them over once —
     * {@code api.scripts().registerAll(...)} — instead of per transaction. That matters right
     * after a bootstrap, when the chain has not revealed the scripts yet and no backend can
     * serve them. Deliberately does not force resolution: registering scripts is exactly what a
     * caller does <i>before</i> anything is on chain to resolve against.</p>
     */
    @Override
    public DeploymentScripts scripts() {
        return scripts;
    }

    /**
     * Pure once the base script hash is known — which it is for a fully specified deployment.
     * A deployment that is still just a bootstrap hash is resolved first.
     */
    @Override
    public Address smartWalletAddress(Address ownerAddress) {
        Cip113Deployment deployment = deployment();
        if (deployment.getProgrammableLogicBaseHash() == null) deployment = resolved().getDeployment();
        return SmartWalletAddress.ofPaymentCredential(deployment, ownerAddress);
    }

    // ------------------------------------------------------------ deployment

    /**
     * Resolve now, from chain, and publish the result on success.
     *
     * <p>Callers who want the failure as a value rather than an exception call this directly;
     * every other method resolves on demand and throws {@link Cip113Exception} instead. A
     * failed explicit resolution leaves any previously published state in place.</p>
     */
    @Override
    public Result<Cip113Deployment> resolveDeployment() {
        try {
            Resolved fresh = resolveFromChain();
            synchronized (resolutionLock) {
                resolution = CompletableFuture.completedFuture(fresh);
            }
            return Result.success("OK").withValue(fresh.getDeployment());
        } catch (Exception e) {
            log.warn("CIP-113 deployment resolution failed for bootstrap {}: {}",
                    configured.getBootstrapTxHash(), e.getMessage());
            return Result.error("Failed to resolve deployment: " + e.getMessage());
        }
    }

    private Resolved publishedOrNull() {
        CompletableFuture<Resolved> current;
        synchronized (resolutionLock) {
            current = resolution;
        }
        return current != null && current.isDone() && !current.isCompletedExceptionally()
                ? current.join() : null;
    }

    /**
     * The published state, resolving it on first use.
     *
     * <p>Exactly one caller performs the chain reads; concurrent callers join the same future
     * and see the same outcome. A failure clears the future after delivering it, so a later
     * caller retries rather than inheriting a stale error.</p>
     */
    private Resolved resolved() {
        CompletableFuture<Resolved> future;
        boolean resolver = false;
        synchronized (resolutionLock) {
            if (resolution == null) {
                resolution = new CompletableFuture<>();
                resolver = true;
            }
            future = resolution;
        }
        if (resolver) {
            try {
                future.complete(resolveFromChain());
            } catch (Exception e) {
                synchronized (resolutionLock) {
                    if (resolution == future) resolution = null;
                }
                log.warn("CIP-113 deployment resolution failed for bootstrap {}: {}",
                        configured.getBootstrapTxHash(), e.getMessage());
                future.completeExceptionally(e);
            }
        }
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Cip113Exception) throw (Cip113Exception) cause;
            throw new Cip113Exception("Could not resolve the CIP-113 deployment from bootstrap"
                    + " transaction " + configured.getBootstrapTxHash() + ": " + cause.getMessage(), cause);
        }
    }

    private Resolved resolveFromChain() throws Exception {
        String bootstrapTxHash = configured.getBootstrapTxHash();
        Result<TxContentUtxo> bootstrap =
                backendService.getTransactionService().getTransactionUtxos(bootstrapTxHash);
        if (!bootstrap.isSuccessful()) {
            throw new Cip113Exception("Could not read bootstrap transaction " + bootstrapTxHash
                    + ": " + bootstrap.getResponse());
        }

        Utxo params = null;
        Utxo template = null;
        String paramsPolicy = null;
        String templatePolicy = null;

        for (TxContentUtxoOutputs output : bootstrap.getValue().getOutputs()) {
            for (TxContentOutputAmount amount : output.getAmount()) {
                String unit = amount.getUnit();
                if (unit == null || "lovelace".equals(unit) || unit.length() <= 56) continue;
                String policy = unit.substring(0, 56);
                String name = assetNameOf(unit);

                if (Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME.equals(name)) {
                    params = toUtxo(bootstrapTxHash, output);
                    paramsPolicy = policy;
                } else if (Cip113Deployment.ISSUANCE_CBOR_HEX_ASSET_NAME.equals(name)) {
                    template = toUtxo(bootstrapTxHash, output);
                    templatePolicy = policy;
                }
            }
        }

        if (params == null) {
            throw new Cip113Exception("Bootstrap transaction " + bootstrapTxHash
                    + " has no output carrying a '" + Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME
                    + "' NFT. Either the hash is wrong, or this deployment was bootstrapped"
                    + " differently than expected.");
        }

        // The bootstrap output only tells us the policies. An in-place upgrade spends and
        // recreates the coordination UTxO, so the *live* one has to be found by following its
        // NFT — otherwise every transaction would reference a spent output.
        Utxo coordination = liveCoordinationUtxo(paramsPolicy, params);
        if (coordination.getInlineDatum() == null || coordination.getInlineDatum().isEmpty()) {
            throw new Cip113Exception("The coordination UTxO " + coordination.getTxHash() + "#"
                    + coordination.getOutputIndex() + " has no inline datum — the CIP-113"
                    + " validators require one.");
        }
        ProgrammableLogicGlobalParams datum = ProgrammableLogicGlobalParams.fromPlutusData(
                PlutusData.deserialize(HexUtil.decodeHexString(coordination.getInlineDatum())));

        Cip113Deployment deployment = configured.toBuilder()
                .paramsPolicy(paramsPolicy)
                .issuanceCborHexCs(templatePolicy)
                .registrySpendScriptHash(configured.getRegistrySpendScriptHash() != null
                        ? configured.getRegistrySpendScriptHash()
                        : registrySpendHashFrom(bootstrap.getValue().getOutputs(),
                                datum.getRegistryNodeCs()))
                .build()
                .withResolvedParams(datum);

        // Whatever the bootstrap published as a reference script, record where it lives, so a
        // transaction can point at it instead of carrying the bytes.
        for (TxContentUtxoOutputs output : bootstrap.getValue().getOutputs()) {
            String refHash = output.getReferenceScriptHash();
            if (refHash == null || refHash.isEmpty()) continue;
            scripts.publishedAt(refHash, toUtxo(bootstrapTxHash, output));
        }

        return new Resolved(deployment, coordination, template);
    }

    /**
     * The UTxO currently carrying the protocol-params NFT.
     *
     * <p>Asks the backend who holds the asset first. When that lookup is unavailable or empty,
     * the bootstrap output is used only after positively verifying that it is still unspent and
     * still carries the NFT — never assumed. Anything else fails resolution here, where the unit
     * and the bootstrap transaction can be named, instead of surfacing later as a script error
     * against a spent reference input.</p>
     */
    private Utxo liveCoordinationUtxo(String policy, Utxo bootstrapOutput) {
        String unit = policy + HexUtil.encodeHexString(
                Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME.getBytes(StandardCharsets.UTF_8));
        try {
            Result<List<AssetAddress>> holders =
                    backendService.getAssetService().getAllAssetAddresses(unit);
            if (holders.isSuccessful() && holders.getValue() != null) {
                for (AssetAddress holder : holders.getValue()) {
                    Optional<Utxo> found = utxoCarrying(holder.getAddress(), unit);
                    if (found.isPresent()) return found.get();
                }
            } else {
                log.debug("Asset-holder lookup for {} answered {}; verifying the bootstrap output",
                        unit, holders.getResponse());
            }
        } catch (Exception e) {
            log.debug("Asset-holder lookup for {} failed; verifying the bootstrap output", unit, e);
        }

        Optional<Utxo> verified = utxoCarrying(bootstrapOutput.getAddress(), unit);
        if (verified.isPresent()) return verified.get();

        throw new Cip113Exception("Could not find the live UTxO carrying the protocol-params NFT "
                + unit + ". The bootstrap output " + bootstrapOutput.getTxHash() + "#"
                + bootstrapOutput.getOutputIndex() + " at " + bootstrapOutput.getAddress()
                + " is spent or no longer carries it, and the backend reported no other holder."
                + " Check the bootstrap transaction hash and the backend's asset indexing.");
    }

    /** The unspent output at {@code address} holding {@code unit}, read through the UTxO supplier. */
    private Optional<Utxo> utxoCarrying(String address, String unit) {
        for (Utxo utxo : utxoSupplier.getAll(address)) {
            for (Amount amount : utxo.getAmount()) {
                if (unit.equals(amount.getUnit())) return Optional.of(utxo);
            }
        }
        return Optional.empty();
    }

    /**
     * Find the script the registry's nodes live at, from the bootstrap transaction.
     *
     * <p>The coordination datum names the registry's <i>minting</i> policy but not the spend
     * script guarding its nodes. The bootstrap transaction created the origin node, so whichever
     * output carries a registry-node NFT is sitting at the registry address.</p>
     *
     * @return the script hash, or null if no output carries a node NFT
     */
    private static String registrySpendHashFrom(List<TxContentUtxoOutputs> outputs,
                                                String registryNodeCs) {
        if (registryNodeCs == null || registryNodeCs.isEmpty()) return null;
        for (TxContentUtxoOutputs output : outputs) {
            for (TxContentOutputAmount amount : output.getAmount()) {
                String unit = amount.getUnit();
                // The origin node's key is empty, so its NFT has an empty asset name and the unit
                // is exactly the 56-character policy id — the one node this must not skip.
                if (unit == null || unit.length() < 56) continue;
                if (!unit.substring(0, 56).equalsIgnoreCase(registryNodeCs)) continue;
                return new Address(output.getAddress()).getPaymentCredentialHash()
                        .map(HexUtil::encodeHexString)
                        .orElse(null);
            }
        }
        return null;
    }

    /**
     * Find the UTxO carrying a token's global-state NFT.
     *
     * <p>Only the policy is known, so this goes policy → its assets → their holders → that
     * holder's UTxOs. Returns null rather than throwing: the caller turns absence into a message
     * that names the token whose state is missing.</p>
     */
    @Override
    public Utxo globalStateUtxo(String globalStateCs) {
        if (globalStateCs == null || globalStateCs.isEmpty()) return null;
        try {
            var assets = backendService.getAssetService().getAllPolicyAssets(globalStateCs);
            if (!assets.isSuccessful() || assets.getValue() == null) return null;
            for (var asset : assets.getValue()) {
                String unit = asset.getAsset();
                if (unit == null) continue;
                var holders = backendService.getAssetService().getAllAssetAddresses(unit);
                if (!holders.isSuccessful() || holders.getValue() == null) continue;
                for (var holder : holders.getValue()) {
                    Optional<Utxo> found = utxoCarrying(holder.getAddress(), unit);
                    if (found.isPresent()) return found.get();
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve the global-state UTxO for policy {}", globalStateCs, e);
        }
        return null;
    }

    // --------------------------------------------------------------- queries

    @Override
    public Result<List<Utxo>> getUtxos(Address ownerAddress) {
        try {
            String wallet = smartWalletAddress(ownerAddress).toBech32();
            return Result.success("OK").withValue(utxoSupplier.getAll(wallet));
        } catch (Exception e) {
            return Result.error("Failed to read smart-wallet UTxOs: " + e.getMessage());
        }
    }

    @Override
    public Result<List<Amount>> getBalance(Address ownerAddress) {
        Result<List<Utxo>> utxos = getUtxos(ownerAddress);
        if (!utxos.isSuccessful()) return Result.error(utxos.getResponse());
        return Result.success("OK").withValue(sum(utxos.getValue()));
    }

    @Override
    public Result<List<Amount>> getProgrammableBalance(Address ownerAddress) {
        Result<List<Amount>> all = getBalance(ownerAddress);
        if (!all.isSuccessful()) return all;

        try {
            Set<String> registered = registryLookup().all().stream()
                    .map(node -> node.getDatum().getKey().toLowerCase())
                    .collect(Collectors.toSet());
            List<Amount> programmable = new ArrayList<>();
            for (Amount amount : all.getValue()) {
                String unit = amount.getUnit();
                if (unit == null || "lovelace".equals(unit) || unit.length() <= 56) continue;
                if (registered.contains(unit.substring(0, 56).toLowerCase())) programmable.add(amount);
            }
            return Result.success("OK").withValue(programmable);
        } catch (Exception e) {
            return Result.error("Registry lookup failed: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> isProgrammable(String policyId) {
        try {
            return Result.success("OK").withValue(registryLookup().byPolicy(policyId).isPresent());
        } catch (Exception e) {
            return Result.error("Registry lookup failed: " + e.getMessage());
        }
    }

    @Override
    public Result<RegistryNode> getRegistryNode(String policyId) {
        try {
            Optional<RegistryLookup.RegistryNodeUtxo> node = registryLookup().byPolicy(policyId);
            if (node.isEmpty()) return Result.error("Policy " + policyId + " is not registered");
            return Result.success("OK").withValue(node.get().getDatum());
        } catch (Exception e) {
            return Result.error("Registry lookup failed: " + e.getMessage());
        }
    }

    @Override
    public Result<List<RegistryNode>> getRegistry() {
        try {
            List<RegistryNode> nodes = new ArrayList<>();
            registryLookup().all().forEach(n -> nodes.add(n.getDatum()));
            return Result.success("OK").withValue(nodes);
        } catch (Exception e) {
            return Result.error("Registry scan failed: " + e.getMessage());
        }
    }

    @Override
    public Result<String> derivePolicyId(Credential mintingLogicScript) {
        try {
            Utxo template = issuanceTemplateUtxo();
            if (template == null) {
                return Result.error("No issuance-template UTxO found in the bootstrap transaction;"
                        + " a policy id cannot be derived without its prefix/postfix.");
            }
            IssuanceCborHex issuance = IssuanceCborHex.fromPlutusData(
                    PlutusData.deserialize(HexUtil.decodeHexString(template.getInlineDatum())));
            return Result.success("OK").withValue(PolicyIdDerivation.derive(issuance, mintingLogicScript));
        } catch (Exception e) {
            return Result.error("Policy id derivation failed: " + e.getMessage());
        }
    }

    // --------------------------------------------------------------- helpers

    private static List<Amount> sum(List<Utxo> utxos) {
        Map<String, BigInteger> totals = new LinkedHashMap<>();
        for (Utxo utxo : utxos) {
            for (Amount amount : utxo.getAmount()) {
                totals.merge(amount.getUnit(), amount.getQuantity(), BigInteger::add);
            }
        }
        List<Amount> result = new ArrayList<>();
        totals.forEach((unit, qty) -> result.add(Amount.builder().unit(unit).quantity(qty).build()));
        return result;
    }

    private static String assetNameOf(String unit) {
        String hexName = unit.substring(56);
        if (hexName.isEmpty()) return "";
        return new String(HexUtil.decodeHexString(hexName), StandardCharsets.UTF_8);
    }

    private static Utxo toUtxo(String txHash, TxContentUtxoOutputs output) {
        List<Amount> amounts = new ArrayList<>();
        for (TxContentOutputAmount a : output.getAmount()) {
            amounts.add(Amount.builder().unit(a.getUnit()).quantity(new BigInteger(a.getQuantity())).build());
        }
        return Utxo.builder()
                .txHash(txHash)
                .outputIndex(output.getOutputIndex())
                .address(output.getAddress())
                .amount(amounts)
                .dataHash(output.getDataHash())
                .inlineDatum(output.getInlineDatum())
                .referenceScriptHash(output.getReferenceScriptHash())
                .build();
    }
}
