package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import lombok.extern.slf4j.Slf4j;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default read side, backed by a {@link BackendService}.
 *
 * <p>Deployment resolution walks the bootstrap transaction: the standard identifies a version
 * by that transaction's hash, and its outputs carry the coordination NFT (whose datum is the
 * deployment descriptor) and the issuance template.</p>
 */
@Slf4j
public class DefaultCip113ProtocolService implements Cip113ProtocolService {

    private final BackendService backendService;
    private final UtxoSupplier utxoSupplier;
    private Cip113Deployment deployment;
    private RegistryLookup registryLookup;

    private Utxo coordinationUtxo;
    private Utxo issuanceTemplateUtxo;
    private DeploymentScripts scripts;
    private ProtocolParamsSupplier protocolParamsSupplier;

    /** Whether {@link #resolveDeployment()} has completed successfully at least once. */
    private boolean resolved;
    /** Guards against re-entering resolution from a method resolution itself calls. */
    private boolean resolving;

    public DefaultCip113ProtocolService(BackendService backendService, Cip113Deployment deployment) {
        this.backendService = backendService;
        this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        this.deployment = deployment;
    }

    @Override
    public Cip113Deployment deployment() {
        return deployment;
    }

    /**
     * The registry lookup, built against the <i>resolved</i> deployment.
     *
     * <p>{@link RegistryLookup.Scanning} captures the deployment it is given, and resolution
     * replaces that object — so one built too early would keep scanning for a null registry
     * policy forever. Resolving first makes that unrepresentable.</p>
     */
    @Override
    public RegistryLookup registryLookup() {
        if (registryLookup == null) {
            ensureResolved();
            registryLookup = new RegistryLookup.Scanning(utxoSupplier, deployment);
        }
        return registryLookup;
    }

    /**
     * Resolve the deployment now if it has not been resolved yet.
     *
     * <p>Everything below needs values that only exist after the bootstrap transaction has been
     * walked — the live coordination UTxO, the issuance template, the base script hash. Leaving
     * that to the caller made correct use depend on call order, and getting the order wrong failed
     * late and obscurely: a null coordination UTxO surfaces as a script error deep in evaluation,
     * and a registry lookup built too early binds to an unresolved deployment permanently.</p>
     *
     * <p>Resolution is idempotent and the deployment is immutable on chain, so doing it on demand
     * costs one lookup and removes the ordering constraint entirely. Callers who want to see the
     * failure as a value rather than an exception can still call {@link #resolveDeployment()}
     * themselves first.</p>
     */
    private void ensureResolved() {
        if (resolved || resolving) return;
        resolving = true;
        try {
            Result<Cip113Deployment> result = resolveDeployment();
            if (!result.isSuccessful()) {
                throw new Cip113Exception("Could not resolve the CIP-113 deployment from bootstrap"
                        + " transaction " + deployment.getBootstrapTxHash() + ": "
                        + result.getResponse());
            }
        } finally {
            resolving = false;
        }
    }

    /** The live coordination UTxO. Resolves the deployment on first use if needed. */
    @Override
    public Utxo coordinationUtxo() {
        if (coordinationUtxo == null) ensureResolved();
        return coordinationUtxo;
    }

    /** The issuance-template UTxO. Resolves the deployment on first use if needed. */
    @Override
    public Utxo issuanceTemplateUtxo() {
        if (issuanceTemplateUtxo == null) ensureResolved();
        return issuanceTemplateUtxo;
    }

    /** Protocol parameters, for sizing min-ADA on programmable outputs. Cached per service. */
    @Override
    public ProtocolParamsSupplier protocolParamsSupplier() {
        if (protocolParamsSupplier == null) {
            protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        }
        return protocolParamsSupplier;
    }

    /**
     * This deployment's script resolver, shared by every transaction the api builds.
     *
     * <p>Exposed so a caller that already holds applied scripts can hand them over once —
     * {@code api.scripts().registerAll(...)} — instead of per transaction. That matters right
     * after a bootstrap, when the chain has not revealed the scripts yet and no backend can
     * serve them.</p>
     *
     * <p>Deliberately does not force resolution: registering scripts is exactly what a caller
     * does <i>before</i> anything is on chain to resolve against.</p>
     */
    @Override
    public DeploymentScripts scripts() {
        if (scripts == null) {
            scripts = new DeploymentScripts(backendService.getScriptService(), this::deployment);
        }
        return scripts;
    }

    /**
     * Pure once the base script hash is known — which it is for a fully specified deployment.
     * A deployment that is still just a bootstrap hash is resolved first, since the address
     * cannot be derived without it.
     */
    @Override
    public Address smartWalletAddress(Address ownerAddress) {
        if (deployment.getProgrammableLogicBaseHash() == null) ensureResolved();
        return SmartWalletAddress.ofPaymentCredential(deployment, ownerAddress);
    }

    // ------------------------------------------------------------ deployment

    @Override
    public Result<Cip113Deployment> resolveDeployment() {
        try {
            Result<TxContentUtxo> bootstrap =
                    backendService.getTransactionService().getTransactionUtxos(deployment.getBootstrapTxHash());
            if (!bootstrap.isSuccessful()) {
                return Result.error("Could not read bootstrap transaction "
                        + deployment.getBootstrapTxHash() + ": " + bootstrap.getResponse());
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
                        params = toUtxo(deployment.getBootstrapTxHash(), output);
                        paramsPolicy = policy;
                    } else if (Cip113Deployment.ISSUANCE_CBOR_HEX_ASSET_NAME.equals(name)) {
                        template = toUtxo(deployment.getBootstrapTxHash(), output);
                        templatePolicy = policy;
                    }
                }
            }

            if (params == null) {
                return Result.error("Bootstrap transaction " + deployment.getBootstrapTxHash()
                        + " has no output carrying a '" + Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME
                        + "' NFT. Either the hash is wrong, or this deployment was bootstrapped"
                        + " differently than expected.");
            }
            if (params.getInlineDatum() == null || params.getInlineDatum().isEmpty()) {
                return Result.error("The coordination UTxO has no inline datum — the CIP-113"
                        + " validators require one.");
            }

            ProgrammableLogicGlobalParams resolvedFrom = ProgrammableLogicGlobalParams.fromPlutusData(
                    PlutusData.deserialize(HexUtil.decodeHexString(params.getInlineDatum())));

            // The bootstrap output only tells us the policies. An in-place upgrade spends and
            // recreates the coordination UTxO, so the *live* one has to be found by following its
            // NFT — otherwise every transaction would reference a spent output.
            this.coordinationUtxo = liveUtxoCarrying(paramsPolicy,
                    Cip113Deployment.PROTOCOL_PARAMS_ASSET_NAME, params);
            this.issuanceTemplateUtxo = template;

            if (!this.coordinationUtxo.getTxHash().equals(params.getTxHash())
                    || this.coordinationUtxo.getOutputIndex() != params.getOutputIndex()) {
                // Re-read the datum: an upgrade may have changed the delegate credentials.
                resolvedFrom = ProgrammableLogicGlobalParams.fromPlutusData(PlutusData.deserialize(
                        HexUtil.decodeHexString(this.coordinationUtxo.getInlineDatum())));
            }
            this.deployment = deployment.toBuilder()
                    .paramsPolicy(paramsPolicy)
                    .issuanceCborHexCs(templatePolicy)
                    .registrySpendScriptHash(deployment.getRegistrySpendScriptHash() != null
                            ? deployment.getRegistrySpendScriptHash()
                            : registrySpendHashFrom(bootstrap.getValue().getOutputs(),
                                    resolvedFrom.getRegistryNodeCs()))
                    .build()
                    .withResolvedParams(resolvedFrom);
            this.registryLookup = null;   // deployment changed, drop any cached scan

            // A lookup built against the pre-resolution deployment is now stale, and so is
            // anything it cached.
            this.registryLookup = null;
            this.resolved = true;

            // Whatever the bootstrap published as a reference script, record where it lives, so a
            // transaction can point at it instead of carrying the bytes. Deployments that did not
            // publish any simply yield nothing here and the scripts go in the witness set.
            for (TxContentUtxoOutputs output : bootstrap.getValue().getOutputs()) {
                String refHash = output.getReferenceScriptHash();
                if (refHash == null || refHash.isEmpty()) continue;
                scripts().publishedAt(refHash,
                        toUtxo(deployment.getBootstrapTxHash(), output));
            }

            return Result.success("OK").withValue(this.deployment);
        } catch (Exception e) {
            return Result.error("Failed to resolve deployment: " + e.getMessage());
        }
    }

    /**
     * The UTxO currently carrying a one-shot NFT.
     *
     * <p>Falls back to the bootstrap output when the backend cannot answer an asset query, which
     * is correct for a deployment that has never been upgraded — but says so, because silently
     * using a spent output would fail much later and much less clearly.</p>
     */
    /**
     * Find the script the registry's nodes live at, from the bootstrap transaction.
     *
     * <p>The coordination datum names the registry's <i>minting</i> policy but not the spend
     * script guarding its nodes, and without that there is no registry address to scan — a service
     * given nothing but a bootstrap hash could resolve everything else and still fail every
     * registry lookup with a null hash.</p>
     *
     * <p>It does not need to be published, because the bootstrap transaction created the origin
     * node: whichever output carries a registry-node NFT is sitting at the registry address, so
     * its payment credential is the answer.</p>
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
     * that names the token whose state is missing, which is more use than a failure here.</p>
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
                    for (Utxo utxo : utxoSupplier.getAll(holder.getAddress())) {
                        for (Amount amount : utxo.getAmount()) {
                            if (unit.equals(amount.getUnit())) return utxo;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve the global-state UTxO for policy {}", globalStateCs, e);
        }
        return null;
    }

    private Utxo liveUtxoCarrying(String policy, String assetName, Utxo bootstrapOutput) {
        String unit = policy + HexUtil.encodeHexString(
                assetName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            Result<List<com.bloxbean.cardano.client.backend.model.AssetAddress>> holders =
                    backendService.getAssetService().getAllAssetAddresses(unit);
            if (holders.isSuccessful() && holders.getValue() != null && !holders.getValue().isEmpty()) {
                String address = holders.getValue().get(0).getAddress();
                for (Utxo utxo : utxoSupplier.getAll(address)) {
                    for (Amount amount : utxo.getAmount()) {
                        if (unit.equals(amount.getUnit())) return utxo;
                    }
                }
            }
        } catch (Exception e) {
            // fall through to the bootstrap output
        }
        return bootstrapOutput;
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
            RegistryLookup lookup = registryLookup();
            lookup.invalidate();
            java.util.Set<String> registered = lookup.all().stream()
                    .map(node -> node.getDatum().getKey().toLowerCase())
                    .collect(java.util.stream.Collectors.toSet());
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
            RegistryLookup lookup = registryLookup();
            lookup.invalidate();
            lookup.all().forEach(n -> nodes.add(n.getDatum()));
            return Result.success("OK").withValue(nodes);
        } catch (Exception e) {
            return Result.error("Registry scan failed: " + e.getMessage());
        }
    }

    @Override
    public Result<String> derivePolicyId(Credential mintingLogicScript) {
        try {
            if (issuanceTemplateUtxo() == null) {
                return Result.error("No issuance-template UTxO found in the bootstrap transaction;"
                        + " a policy id cannot be derived without its prefix/postfix.");
            }
            IssuanceCborHex template = IssuanceCborHex.fromPlutusData(
                    PlutusData.deserialize(HexUtil.decodeHexString(issuanceTemplateUtxo.getInlineDatum())));
            return Result.success("OK").withValue(PolicyIdDerivation.derive(template, mintingLogicScript));
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
        return new String(HexUtil.decodeHexString(hexName), java.nio.charset.StandardCharsets.UTF_8);
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
