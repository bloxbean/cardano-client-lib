package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.DeploymentScripts;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.RegistryLookup;

import java.util.List;

/**
 * Read side for programmable tokens.
 *
 * <p>Kept separate from transaction building because these are backend calls, and they follow
 * CCL's backend conventions: anything touching the chain returns {@link Result}. The two
 * methods that do no I/O — address derivation and policy-id derivation, both pure hashing —
 * return their values directly.</p>
 *
 * <p>This interface is also where indexer-specific implementations plug in.</p>
 */
public interface Cip113ProtocolService {

    /** The deployment this API reads. */
    Cip113Deployment deployment();

    /** Where a user's programmable tokens live. Pure — no I/O. */
    Address smartWalletAddress(Address ownerAddress);

    /**
     * This deployment's script resolver, shared by the protocol materializer.
     *
     * <p>It is a {@link com.bloxbean.cardano.client.api.ScriptSupplier}, so it can also be handed
     * to {@code QuickTxBuilder.TxContext.withScriptSupplier(...)} to make CIP-113 scripts
     * resolvable by hash for the rest of a build. Register applied scripts on it when the chain
     * has not revealed them yet.</p>
     */
    DeploymentScripts scripts();

    /**
     * Protocol parameters, used to size min-ADA on programmable outputs.
     *
     * <p>Programmable outputs declare their lovelace up front — it is part of the declared value
     * so coin selection covers it, and it cannot be corrected later without appending an input and
     * renumbering the orderings the redeemers point at.</p>
     */
    ProtocolParamsSupplier protocolParamsSupplier();

    /**
     * The UTxO holding a token's global-state NFT, or null if none can be found.
     *
     * <p>A registry node may declare a {@code global_state_cs}. When it does, that token's logic
     * scripts expect the global-state UTxO among the reference inputs, so the builder has to find
     * it — the policy is known but the asset name and holder are not, which makes it a chain
     * lookup rather than a derivation.</p>
     */
    Utxo globalStateUtxo(String globalStateCs);

    /** Advanced registry lookup used by the build adapter and indexer-backed implementations. */
    RegistryLookup registryLookup();

    /**
     * The coordination UTxO carrying the protocol-params NFT, available once
     * {@link #resolveDeployment()} has run. Every CIP-113 redeemer indexes into the reference
     * inputs to find it, so it is mandatory on every programmable transaction.
     */
    Utxo coordinationUtxo();

    /**
     * The issuance-template UTxO whose datum carries the prefix/postfix a policy id is derived
     * from, available once {@link #resolveDeployment()} has run.
     */
    Utxo issuanceTemplateUtxo();

    /**
     * Complete a deployment by walking its bootstrap transaction and reading the coordination
     * datum. Returns a deployment with every credential resolved from chain.
     */
    Result<Cip113Deployment> resolveDeployment();

    /** Everything in a user's smart wallet, programmable or not. */
    Result<List<Amount>> getBalance(Address ownerAddress);

    /** Just the programmable holdings — policies that are registered. */
    Result<List<Amount>> getProgrammableBalance(Address ownerAddress);

    Result<List<Utxo>> getUtxos(Address ownerAddress);

    Result<Boolean> isProgrammable(String policyId);

    Result<RegistryNode> getRegistryNode(String policyId);

    Result<List<RegistryNode>> getRegistry();

    /**
     * A token's policy id, derived from its issuance credential and the on-chain issuance
     * template. Requires one read to fetch the template, so it returns a {@link Result}.
     */
    Result<String> derivePolicyId(Credential mintingLogicScript);
}
