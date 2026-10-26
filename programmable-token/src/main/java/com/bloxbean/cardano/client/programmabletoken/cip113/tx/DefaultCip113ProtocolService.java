package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
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
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Default read side, backed by a {@link BackendService}.
 *
 * <p>Deployment resolution starts from the configured {@link Cip113Deployment}: it locates the
 * live coordination UTxO by its NFT and adopts the credentials its datum names, finds the
 * issuance template, and verifies the configured reference scripts. What the chain cannot
 * state — the base script, registry and inline-datum bound — comes from the configuration.</p>
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
        return registryLookup(utxoSupplier);
    }

    /** Scans the registry through the given supplier, bound to the resolved deployment. */
    @Override
    public RegistryLookup registryLookup(UtxoSupplier buildSupplier) {
        return new RegistryLookup.Scanning(buildSupplier, resolved().getDeployment());
    }

    /** The live coordination UTxO. Resolves the deployment on first use if needed. */
    @Override
    public Utxo coordinationUtxo() {
        return resolved().getCoordinationUtxo();
    }

    /**
     * The output carrying the protocol-params NFT at the resolved coordination address, as the
     * build's supplier sees it. The deployment was resolved from the coordination datum, so a
     * visible output with another datum fails the build instead of using stale parameters.
     */
    @Override
    public Utxo coordinationUtxo(UtxoSupplier buildSupplier) {
        Resolved state = resolved();
        Utxo resolvedCoordination = state.getCoordinationUtxo();
        String unit = state.getDeployment().getParamsPolicy() + HexUtil.encodeHexString(
                Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME.getBytes(StandardCharsets.UTF_8));
        Utxo visible = utxoCarrying(buildSupplier, resolvedCoordination.getAddress(), unit)
                .orElseThrow(() -> new Cip113Exception("No output carrying the protocol-params NFT " + unit
                        + " is visible at " + resolvedCoordination.getAddress() + " to this build. An earlier"
                        + " transaction may have spent the coordination UTxO; build after it confirms, or"
                        + " fund this TxFlow step from the step that recreates it."));
        if (!Objects.equals(visible.getInlineDatum(), resolvedCoordination.getInlineDatum()))
            throw new Cip113Exception("The coordination UTxO " + visible.getTxHash() + "#" + visible.getOutputIndex()
                    + " carries other protocol parameters than the resolved deployment. Resolve the"
                    + " deployment again before building.");
        return visible;
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
        return SmartWalletAddress.ofOwner(deployment, ownerAddress);
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
        requireDescribed(configured);

        // The coordination UTxO sits at the params policy's own script address (mint and spend
        // share one hash: protocol_params.ak). An in-place upgrade spends and recreates it, so the
        // live one has to be found by following its NFT.
        Utxo coordination = liveCoordinationUtxo(configured);
        if (coordination.getInlineDatum() == null || coordination.getInlineDatum().isEmpty()) {
            throw new Cip113Exception("The coordination UTxO " + coordination.getTxHash() + "#"
                    + coordination.getOutputIndex() + " has no inline datum — the CIP-113"
                    + " validators require one.");
        }
        ProgrammableLogicGlobalParams datum = ProgrammableLogicGlobalParams.fromPlutusData(
                PlutusData.deserialize(HexUtil.decodeHexString(coordination.getInlineDatum())));

        Cip113Deployment deployment = configured.withResolvedParams(datum);
        warnIfUpgraded(configured, deployment);

        Utxo template = issuanceTemplateUtxo(deployment);
        Map<String, Utxo> referenceScripts = verifyReferenceScripts(deployment);

        // Last, after everything that can fail: a failed resolution leaves the previously
        // published reference scripts as they were.
        scripts.replacePublished(referenceScripts);
        return new Resolved(deployment, coordination, template);
    }

    /**
     * Refuse a deployment that does not describe the values the chain cannot supply: the base
     * script, registry, inline-datum bound and template policy are compile-time parameters of
     * other scripts.
     */
    private static void requireDescribed(Cip113Deployment d) {
        List<String> missing = new ArrayList<>();
        if (d.getNetwork() == null) missing.add("network");
        if (isBlank(d.getParamsPolicy())) missing.add("paramsPolicy");
        if (isBlank(d.getProgrammableLogicBaseHash())) missing.add("programmableLogicBaseHash");
        if (isBlank(d.getRegistryNodeCs())) missing.add("registryNodeCs");
        if (isBlank(d.getIssuanceCborHexCs())) missing.add("issuanceCborHexCs");
        if (d.getMaxInlineDatumBytes() <= 0) missing.add("maxInlineDatumBytes");
        if (!missing.isEmpty()) {
            throw new Cip113Exception("The CIP-113 deployment does not describe " + missing
                    + ". These are compile-time parameters of the v0.0.1 validators and cannot be"
                    + " read from chain; use one of Cip113Deployments or build a complete"
                    + " Cip113Deployment from the deployer's bootstrap record.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Follow an in-place upgrade, loudly. The datum is authenticated by its NFT and only the
     * upgrade authority can rewrite it; the warning exists because a replaced script may expect
     * proofs this release does not build.
     */
    private static void warnIfUpgraded(Cip113Deployment configured, Cip113Deployment live) {
        warnIfChanged("programmable_logic_global", configured.getProgrammableLogicGlobalHash(),
                live.getProgrammableLogicGlobalHash());
        warnIfChanged("issuance_logic", configured.getIssuanceLogicHash(), live.getIssuanceLogicHash());
        warnIfChanged("transfer", configured.getTransferScriptHash(), live.getTransferScriptHash());
        warnIfChanged("third_party", configured.getThirdPartyScriptHash(), live.getThirdPartyScriptHash());
    }

    private static void warnIfChanged(String role, String configured, String live) {
        if (configured != null && !configured.equalsIgnoreCase(live)) {
            log.warn("CIP-113 coordination datum names {} {} where the configured deployment has {}:"
                    + " the deployment was upgraded in place. Following the live datum.",
                    role, live, configured);
        }
    }

    /** The UTxO at the params policy's script address carrying the protocol-params NFT. */
    private Utxo liveCoordinationUtxo(Cip113Deployment deployment) {
        String address = deployment.protocolParamsAddress().toBech32();
        String unit = deployment.getParamsPolicy() + HexUtil.encodeHexString(
                Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME.getBytes(StandardCharsets.UTF_8));
        return utxoCarrying(address, unit).orElseThrow(() -> new Cip113Exception(
                "Could not find the live UTxO carrying the protocol-params NFT " + unit + " at "
                + address + ". Either paramsPolicy is wrong for this network, or the backend has"
                + " not indexed the deployment."));
    }

    /** The unspent output at {@code address} holding {@code unit}, read through the UTxO supplier. */
    private Optional<Utxo> utxoCarrying(String address, String unit) {
        return utxoCarrying(utxoSupplier, address, unit);
    }

    private static Optional<Utxo> utxoCarrying(UtxoSupplier supplier, String address, String unit) {
        for (Utxo utxo : supplier.getAll(address)) {
            for (Amount amount : utxo.getAmount()) {
                if (unit.equals(amount.getUnit())) return Optional.of(utxo);
            }
        }
        return Optional.empty();
    }

    /**
     * The issuance-template UTxO. It is locked at an always-fail script forever, so a configured
     * outref is read directly and must still carry the template NFT. Without one, it is found by
     * asking the backend who holds the NFT; null when the backend cannot say, since only
     * registration and policy-id derivation need it.
     */
    private Utxo issuanceTemplateUtxo(Cip113Deployment deployment) {
        String unit = deployment.getIssuanceCborHexCs() + HexUtil.encodeHexString(
                Cip113Deployment.ISSUANCE_CBOR_HEX_ASSET_NAME.getBytes(StandardCharsets.UTF_8));
        TransactionInput ref = deployment.getIssuanceTemplateRef();
        if (ref != null) {
            TxContentUtxoOutputs output = transactionUtxos(ref.getTransactionId()).getOutputs().stream()
                    .filter(o -> o.getOutputIndex() == ref.getIndex())
                    .findFirst()
                    .orElseThrow(() -> new Cip113Exception("Issuance-template outref "
                            + ref.getTransactionId() + "#" + ref.getIndex() + " does not exist."));
            return utxoCarrying(output.getAddress(), unit)
                    .filter(u -> u.getTxHash().equalsIgnoreCase(ref.getTransactionId())
                            && u.getOutputIndex() == ref.getIndex())
                    .orElseThrow(() -> new Cip113Exception("Issuance-template outref "
                            + ref.getTransactionId() + "#" + ref.getIndex() + " is not an unspent"
                            + " output carrying " + unit + ". The deployment description is wrong"
                            + " for this network."));
        }
        try {
            Result<List<AssetAddress>> holders =
                    backendService.getAssetService().getAllAssetAddresses(unit);
            if (holders.isSuccessful() && holders.getValue() != null) {
                for (AssetAddress holder : holders.getValue()) {
                    Optional<Utxo> found = utxoCarrying(holder.getAddress(), unit);
                    if (found.isPresent()) return found.get();
                }
            }
            log.warn("No holder of the issuance-template NFT {} was found; registration and policy"
                    + " id derivation will be unavailable.", unit);
        } catch (Exception e) {
            log.warn("Could not look up the issuance-template NFT {}: {}", unit, e.getMessage());
        }
        return null;
    }

    /**
     * Check every configured reference script and return the live ones, by script hash.
     *
     * <p>An outref publishing another script than its key names means the description is wrong,
     * and resolution fails. An outref that has since been spent — or one the backend reports no
     * reference script for — is skipped with a warning: the script is then supplied by hash and
     * carried as a witness, which costs fees but not correctness.</p>
     */
    private Map<String, Utxo> verifyReferenceScripts(Cip113Deployment deployment) {
        Map<String, Utxo> live = new HashMap<>();
        Map<String, TxContentUtxo> txs = new HashMap<>();
        Map<String, List<Utxo>> unspentByAddress = new HashMap<>();
        for (Map.Entry<String, TransactionInput> entry : deployment.getReferenceScripts().entrySet()) {
            String scriptHash = entry.getKey().toLowerCase();
            TransactionInput ref = entry.getValue();
            String outref = ref.getTransactionId() + "#" + ref.getIndex();

            TxContentUtxo tx = txs.computeIfAbsent(ref.getTransactionId(), this::transactionUtxos);
            TxContentUtxoOutputs output = tx.getOutputs().stream()
                    .filter(o -> o.getOutputIndex() == ref.getIndex())
                    .findFirst()
                    .orElseThrow(() -> new Cip113Exception("Reference-script outref " + outref
                            + " for " + scriptHash + " does not exist."));
            if (output.getReferenceScriptHash() == null || output.getReferenceScriptHash().isEmpty()) {
                log.warn("The backend reports no reference script at {} for {}; it cannot be verified,"
                        + " so transactions will carry the script as a witness instead.", outref, scriptHash);
                continue;
            }
            if (!scriptHash.equalsIgnoreCase(output.getReferenceScriptHash())) {
                throw new Cip113Exception("Reference-script outref " + outref + " publishes "
                        + output.getReferenceScriptHash() + ", not " + scriptHash + " as the"
                        + " deployment says. The deployment description is wrong for this network.");
            }

            List<Utxo> unspent = unspentByAddress.computeIfAbsent(output.getAddress(), utxoSupplier::getAll);
            Optional<Utxo> utxo = unspent.stream()
                    .filter(u -> u.getTxHash().equalsIgnoreCase(ref.getTransactionId())
                            && u.getOutputIndex() == ref.getIndex())
                    .findFirst();
            if (utxo.isPresent()) {
                live.put(scriptHash, utxo.get());
            } else {
                log.warn("Reference script {} at {} has been spent; transactions will carry the"
                        + " script as a witness instead.", scriptHash, outref);
            }
        }
        return live;
    }

    private TxContentUtxo transactionUtxos(String txHash) {
        try {
            Result<TxContentUtxo> result = backendService.getTransactionService().getTransactionUtxos(txHash);
            if (!result.isSuccessful() || result.getValue() == null) {
                throw new Cip113Exception("Could not read transaction " + txHash + ": " + result.getResponse());
            }
            return result.getValue();
        } catch (Cip113Exception e) {
            throw e;
        } catch (Exception e) {
            throw new Cip113Exception("Could not read transaction " + txHash, e);
        }
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
        return globalStateUtxo(globalStateCs, utxoSupplier);
    }

    /** As {@link #globalStateUtxo(String)}, reading the holders' UTxOs through the build's supplier. */
    @Override
    public Utxo globalStateUtxo(String globalStateCs, UtxoSupplier buildSupplier) {
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
                    Optional<Utxo> found = utxoCarrying(buildSupplier, holder.getAddress(), unit);
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
            // A 56-character unit is a registered policy's empty asset name, which the builder can
            // transfer like any other, so it is part of the programmable balance too.
            for (Amount amount : all.getValue()) {
                String policy = AssetUtil.getPolicyId(amount.getUnit());
                if (policy != null && registered.contains(policy)) programmable.add(amount);
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
                return Result.error("No issuance-template UTxO could be found;"
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
}
