package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;

/**
 * Known CIP-113 deployments.
 *
 * <p>A deployment is identified by the transaction that bootstrapped it. Everything else —
 * the coordination NFT policy, the delegate script hashes, the registry node policy — is
 * discoverable from that transaction's outputs and the coordination datum, which is why
 * {@code Cip113ProtocolService#resolveDeployment()} exists. The constants below are the
 * resolved values, baked in so the happy path needs no discovery round-trip.</p>
 */
public final class Cip113Deployments {

    private Cip113Deployments() {}

    /**
     * Preview deployment, bootstrapped by
     * {@code a432339cbd7318222c8c51ed4fb52ee4c68f676037622aa7361dd45d897324a4}.
     *
     * <p>Every value here was read from chain on 2026-08-26 and cross-checked: the
     * {@code registryNodeCs} in the coordination datum matches the policy of the origin node's
     * NFT, and the coordination UTxO is still unspent at the bootstrap output — so no in-place
     * upgrade has happened on this deployment yet.</p>
     *
     * <p><b>This is not the deployment described in CIP-0113's text.</b> The CIP publishes a
     * base script hash of {@code f2182b00…2e0a2a3e} and bootstrap tx {@code 61fae36e…87fbd93e};
     * this deployment's base script is {@code 698c48a6…9f9888dc}. The published one is an older
     * instance. Address derivation therefore differs between the two, which is exactly the
     * spec-versus-deployment drift the plan flags as this project's main risk — and the reason
     * a deployment is a parameter rather than a constant baked into the transaction builder.</p>
     */
    public static final Cip113Deployment PREVIEW = Cip113Deployment.builder()
            .network(Networks.preview())
            .bootstrapTxHash("a432339cbd7318222c8c51ed4fb52ee4c68f676037622aa7361dd45d897324a4")

            // Coordination UTxO: a432339c…#0, held at
            // addr_test1wqgt2xufwszw8084m9njfn9pwq6yn4dysel0cc5ydlrc23s2qfa2v
            .paramsPolicy("ea423f5e7d078fb6c7d2505bee02b567eaece043e257fdd601cdaf59")

            // The five credentials in the coordination datum, all Script credentials.
            .programmableLogicBaseHash("698c48a630206282690774aebcfa9410895c09f85bc103b19f9888dc")
            .transferScriptHash("971606541dfdc9e411ba722880d783165f044cc541c17225f35d1e59")
            .thirdPartyScriptHash("8d2d24f8203f6049c3f36576c1628856b8012b3c10db36f7182233f4")
            .unfrackingScriptHash("d4be7708df51b14718d19888db5ad8e417eda138cf83f030bf7ab857")
            .upgradeScriptHash("4861aca31fe0581ff2a16d180f26ac2b4feeb71ca5fd2a86b7927bb5")

            // Registry: addr_test1wqr9pu02kzxggerr4ncrwrwu2zlqtkhzfsefepst2aazz5srqp5fw
            .registrySpendScriptHash("0650f1eab08c846463acf0370ddc50be05dae24c329c860b577a2152")
            .registryNodeCs("9aeda27e8b7e8c0077af9d6d8077b61d4e4a8b25368280ad26dc00c8")

            // Issuance template: a432339c…#2, held at
            // addr_test1wp0lxsu6kkc9nzylhtekqx2jwhvyw80f4kfeuxakcwnmwnqk4p58x
            .issuanceCborHexCs("36480294379b6196a91bd7ac82b6f36cedf38a8b098fe5a2e7f52c7a")

            .maxInlineDatumBytes(2048)
            .build();

    /** A deployment skeleton to be completed from chain by walking its bootstrap transaction. */
    public static Cip113Deployment fromBootstrapTx(String bootstrapTxHash, Network network) {
        return Cip113Deployment.builder()
                .bootstrapTxHash(bootstrapTxHash)
                .network(network)
                .build();
    }
}
