package com.bloxbean.cardano.client.cip.cip113.tx;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultScriptSupplier;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.client.cip.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.cip.cip113.Cip113Exception;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves a deployment's <i>applied</i> scripts, by role or by hash.
 *
 * <p>Every CIP-113 validator is parameterized, so a blueprint only ever gives the unapplied
 * script — its hash matches nothing on chain. Applied scripts are recoverable two ways, and this
 * class is the union of both: whoever applied the parameters can {@link #register} them
 * directly, and anything not registered falls through to a {@link ScriptSupplier} — a Plutus
 * script must be revealed the first time it is used, so once a deployment has seen any activity
 * a backend can serve its scripts by hash. Either way, no UPLC parameter applier is needed to
 * build a transaction.</p>
 *
 * <p>This <b>is</b> a {@link ScriptSupplier}, so it drops straight into the places CCL already
 * takes one — {@code QuickTxBuilder.TxContext.withScriptSupplier(...)} and
 * {@code TxBuilderContext.setScriptSupplier(...)} — and a caller who wants CIP-113 scripts
 * resolvable throughout a build wires it there rather than through anything CIP-113-specific.
 * It also composes the other way: pass any {@code ScriptSupplier} as the fallback and registry
 * scripts can come from a file, an indexer, or a {@code DefaultScriptRegistry}.</p>
 *
 * <p>Results are cached: these are immutable per deployment and each is 1-2 KB.</p>
 */
public class DeploymentScripts implements ScriptSupplier {

    private final ScriptSupplier fallback;
    private final Supplier<Cip113Deployment> deployment;
    private final Map<String, PlutusScript> cache = new HashMap<>();

    /** Script hash -> the UTxO publishing it as a reference script, when the chain has one. */
    private final Map<String, Utxo> published = new HashMap<>();

    public DeploymentScripts(ScriptService scriptService, Cip113Deployment deployment) {
        this(supplierFor(scriptService), () -> deployment);
    }

    public DeploymentScripts(ScriptSupplier fallback, Cip113Deployment deployment) {
        this(fallback, () -> deployment);
    }

    public DeploymentScripts(ScriptService scriptService, Supplier<Cip113Deployment> deployment) {
        this(supplierFor(scriptService), deployment);
    }

    /**
     * Track a deployment that is still being resolved.
     *
     * <p>A deployment starts as little more than a bootstrap transaction hash and gains its script
     * hashes only once it has been resolved from chain. Reading it through a supplier means a
     * resolver built before that point still sees the resolved hashes afterwards, rather than the
     * nulls it was constructed with.</p>
     *
     * @param fallback consulted for any script not registered locally; may be null, in which case
     *                 every script must be registered
     */
    public DeploymentScripts(ScriptSupplier fallback, Supplier<Cip113Deployment> deployment) {
        this.fallback = fallback;
        this.deployment = deployment;
    }

    private static ScriptSupplier supplierFor(ScriptService scriptService) {
        return scriptService == null ? null : new DefaultScriptSupplier(scriptService);
    }

    /**
     * Supply an applied script directly, keyed by its own hash.
     *
     * <p>A backend can only serve a script the chain has already revealed, so a deployment that
     * was just bootstrapped — or one whose validators are published purely as reference scripts —
     * has nothing to look up yet. Whoever applied the parameters is holding the script anyway, so
     * handing it over here removes the round trip and the ordering constraint with it.</p>
     *
     * @return this, for chaining
     */
    public DeploymentScripts register(PlutusScript script) {
        if (script == null) {
            throw new Cip113Exception("Cannot register a null script.");
        }

        byte[] scriptHash;
        try {
            scriptHash = script.getScriptHash();
        } catch (Exception e) {
            throw new Cip113Exception("Could not hash a script being registered", e);
        }

        // HexUtil.encodeHexString returns null for a null argument rather than throwing, so an
        // unhashable script would otherwise key the cache off an NPE inside toLowerCase().
        if (scriptHash == null) {
            throw new Cip113Exception("A script being registered has no hash, so there is no key to"
                    + " cache it under. Its serialized form is empty, which means the script did"
                    + " not survive whatever produced it — check the applied-parameter step.");
        }

        cache.put(HexUtil.encodeHexString(scriptHash).toLowerCase(), script);
        return this;
    }

    /**
     * Record that a script is published on chain as a reference script.
     *
     * <p>A transaction that can reference a script instead of carrying it pays for a UTxO pointer
     * rather than the script bytes, which for the CIP-113 delegates is the difference between a
     * few dozen bytes and several kilobytes on every transfer.</p>
     *
     * @return this, for chaining
     */
    public DeploymentScripts publishedAt(String scriptHash, Utxo utxo) {
        if (scriptHash != null && utxo != null) published.put(scriptHash.toLowerCase(), utxo);
        return this;
    }

    /** The UTxO publishing {@code scriptHash} as a reference script, if one is known. */
    public Optional<Utxo> publishedAt(String scriptHash) {
        return scriptHash == null ? Optional.empty()
                : Optional.ofNullable(published.get(scriptHash.toLowerCase()));
    }

    /** {@link #register(PlutusScript)} for a whole deployment's worth of scripts. */
    public DeploymentScripts registerAll(Collection<PlutusScript> scripts) {
        scripts.forEach(this::register);
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registered scripts first, then the fallback supplier. Returns empty rather than throwing,
     * per the {@link ScriptSupplier} contract — {@link #byHash} is the variant that explains what
     * went missing and why.</p>
     */
    @Override
    public Optional<PlutusScript> getScript(String scriptHash) {
        if (scriptHash == null || scriptHash.isEmpty()) return Optional.empty();
        PlutusScript registered = cache.get(scriptHash.toLowerCase());
        if (registered != null) return Optional.of(registered);
        if (fallback == null) return Optional.empty();
        Optional<PlutusScript> fetched = fallback.getScript(scriptHash.toLowerCase());
        fetched.ifPresent(script -> cache.put(scriptHash.toLowerCase(), script));
        return fetched;
    }

    /** The base script every programmable token sits at — needed to spend one. */
    public PlutusScript programmableLogicBase() {
        return byHash(deployment.get().getProgrammableLogicBaseHash(), "programmable_logic_base");
    }

    /** The core transfer delegate, invoked via withdraw-zero. */
    public PlutusScript transferDelegate() {
        return byHash(deployment.get().getTransferScriptHash(), "transfer");
    }

    /** The core third-party delegate. */
    public PlutusScript thirdPartyDelegate() {
        return byHash(deployment.get().getThirdPartyScriptHash(), "third_party");
    }

    /** Spend script guarding registry nodes — needed to insert or update one. */
    public PlutusScript registrySpend() {
        return byHash(deployment.get().getRegistrySpendScriptHash(), "registry_spend");
    }

    /** Minting policy for registry-node NFTs. */
    public PlutusScript registryMint() {
        return byHash(deployment.get().getRegistryNodeCs(), "registry_mint");
    }

    /**
     * Resolve a script by hash, or fail with a reason.
     *
     * <p>The {@link ScriptSupplier} contract returns an empty Optional for anything it cannot
     * find, which is the right shape for a supplier but a poor one for a builder: the caller is
     * left holding an absent script with no idea whether the hash was wrong, the deployment
     * unresolved, or the backend simply unable to serve it yet.</p>
     */
    public PlutusScript byHash(String scriptHash, String what) {
        if (scriptHash == null || scriptHash.isBlank()) {
            throw new Cip113Exception("No script hash for " + what
                    + " — the deployment is not fully resolved. Call resolveDeployment() first.");
        }
        return getScript(scriptHash).orElseThrow(() -> new Cip113Exception(
                "Could not resolve the " + what + " script (" + scriptHash + ")."
                + (fallback == null
                        ? " No fallback ScriptSupplier is configured, so every script must be"
                          + " handed over with register(...)."
                        : " A backend only serves a script the chain has already revealed, so a"
                          + " freshly bootstrapped deployment has nothing to look up. Hand the"
                          + " applied script over with register(...) instead.")));
    }
}
