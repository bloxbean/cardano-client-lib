package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

/**
 * Known CIP-113 deployments.
 *
 * <p>A deployment is identified by the transaction that bootstrapped it, but in CIP-113 core
 * v0.0.1 it cannot be discovered from that transaction: the base script, the registry, the
 * inline-datum bound and the unfracking script are compile-time parameters of other validators.
 * The constants below describe them; {@code Cip113ProtocolService#resolveDeployment()} adopts the
 * live coordination datum on top, so an in-place upgrade does not strand a released client.</p>
 *
 * <p><b>None of these deployments can unfrack:</b> each compiled its
 * {@code programmable_logic_global} dispatcher with 28 zero bytes in place of the unfracking
 * script hash.</p>
 */
public final class Cip113Deployments {

    private Cip113Deployments() {}

    /** The transactions publishing each deployment's reference scripts, outputs 0-6. */
    private static final String PREVIEW_REF_SCRIPTS_TX =
            "f543e7b8126102afa7bae9e790d5616d8eff0671635900d9bf33c21a72ccb6dd";
    private static final String PREPROD_REF_SCRIPTS_TX =
            "e24679be141c1b99a49f47fcdc3e46ad09571baf0f8fa0d5b0d6094d55267b59";
    private static final String MAINNET_REF_SCRIPTS_TX =
            "6f25126164d14e6675f5ec09e8e2a0addfe303d8f49879d74eeeccf9c7d52d37";

    /**
     * Preview deployment, bootstrapped by
     * {@code 8e9668a6432ea4567bb1deba919c0f76adcce8373d6d89d1a06faee2c83d00f9}.
     *
     * <p>Every value here comes from the deployer's bootstrap record; the script hashes are
     * re-derived from the vendored blueprint by {@code Cip113DeploymentsTest}, and the
     * coordination datum, reference scripts, issuance template and registry origin node were
     * read back from chain on 2026-10-22 and found to match.</p>
     */
    public static final Cip113Deployment PREVIEW = Cip113Deployment.builder()
            .network(Networks.preview())
            .bootstrapTxHash("8e9668a6432ea4567bb1deba919c0f76adcce8373d6d89d1a06faee2c83d00f9")

            // Coordination UTxO: 8e9668a6…#0, held at the params policy's own script address
            .paramsPolicy("1d2026310c70f07ac25e39d591055723f5f8a84ac0de229eb0525720")

            // The coordination datum's credentials and the scripts they are compiled against.
            .programmableLogicBaseHash("35622813d81ba2d6e068c7d52f6fdad5aa2a5d84b212ec3e28716c16")
            .programmableLogicGlobalHash("3b212b37b1a1fb334539c42e7473376a7fc15b60c26ae0fd4d06279e")
            .issuanceLogicHash("621131d2df1acfdad5928a34572834cde904502441d6d41c642a10f6")
            .transferScriptHash("5fe7c82a32a69e5839e9da77e1e9992c1e55f7d372c3d58d8f744ef7")
            .thirdPartyScriptHash("4c601638f4ee5ad3ba71d4d1b8a0b24b9117b7b3a679c66a96dfb7c6")
            .unfrackingScriptHash("2798c1fcdc5f2b2b4ab9a72ce68add485d81915c5430e4ac79afc87b")
            .unfrackingEnabled(false)
            .upgradeScriptHash("03322dd0912f1c36b67cce2aa5cebe8ff1dde6c7dbf365206b07e2fd")

            // Registry: mint and spend share one script, which is also the nodes' address
            .registryNodeCs("e5b339ef5b16d6c460759aca1d60e5e045a6f01da4a11b4ee0fec09a")

            // Issuance template: 8e9668a6…#2, locked at always-fail
            .issuanceCborHexCs("aed824ff0432c6e40c4cc7381890496dd5bcb9e2fa39af7c8abd88c3")
            .issuanceTemplateRef(ref("8e9668a6432ea4567bb1deba919c0f76adcce8373d6d89d1a06faee2c83d00f9", 2))

            .maxInlineDatumBytes(1024)

            // Reference scripts, published in f543e7b8…
            .referenceScript("35622813d81ba2d6e068c7d52f6fdad5aa2a5d84b212ec3e28716c16", ref(PREVIEW_REF_SCRIPTS_TX, 0)) // programmable_logic_base
            .referenceScript("3b212b37b1a1fb334539c42e7473376a7fc15b60c26ae0fd4d06279e", ref(PREVIEW_REF_SCRIPTS_TX, 1)) // programmable_logic_global
            .referenceScript("5fe7c82a32a69e5839e9da77e1e9992c1e55f7d372c3d58d8f744ef7", ref(PREVIEW_REF_SCRIPTS_TX, 2)) // transfer
            .referenceScript("4c601638f4ee5ad3ba71d4d1b8a0b24b9117b7b3a679c66a96dfb7c6", ref(PREVIEW_REF_SCRIPTS_TX, 3)) // third_party
            .referenceScript("2798c1fcdc5f2b2b4ab9a72ce68add485d81915c5430e4ac79afc87b", ref(PREVIEW_REF_SCRIPTS_TX, 4)) // unfracking
            .referenceScript("621131d2df1acfdad5928a34572834cde904502441d6d41c642a10f6", ref(PREVIEW_REF_SCRIPTS_TX, 5)) // issuance_logic
            .referenceScript("03322dd0912f1c36b67cce2aa5cebe8ff1dde6c7dbf365206b07e2fd", ref(PREVIEW_REF_SCRIPTS_TX, 6)) // upgrade_multisig
            .build();

    /**
     * Preprod deployment, bootstrapped by
     * {@code f4118e53fc0fac1dddf96c6dcc3b4670265f25558d9943feb4ee564488f5c896}.
     *
     * <p>Every value here comes from the deployer's bootstrap record; the script hashes are
     * re-derived from the vendored blueprint by {@code Cip113DeploymentsTest}, and the
     * coordination datum, reference scripts, issuance template and registry origin node were
     * read back from chain on 2026-10-22 and found to match.</p>
     */
    public static final Cip113Deployment PREPROD = Cip113Deployment.builder()
            .network(Networks.preprod())
            .bootstrapTxHash("f4118e53fc0fac1dddf96c6dcc3b4670265f25558d9943feb4ee564488f5c896")

            // Coordination UTxO: f4118e53…#0, held at the params policy's own script address
            .paramsPolicy("b4a18f212ea3415750f709f3516357b1a72bd30346d194e563af3b00")

            // The coordination datum's credentials and the scripts they are compiled against.
            .programmableLogicBaseHash("be59f7750a5d947bb649e70d574d066791ec34a1dfee2a087c8511e3")
            .programmableLogicGlobalHash("b4e81fa9a3291025cd279067daf4e43578d4c3f759e5490958f58cf6")
            .issuanceLogicHash("23b58b985141fe5ffd2eb93c5ee4fa0e11d46a934c2837c0230071fd")
            .transferScriptHash("5ff03dd084ec86a164dbf9bfe890605fe9fcba8d6657328eab8bb350")
            .thirdPartyScriptHash("4bd96a9082194ff43ddd10d3092167f5856375959ff0ca1e1478ef6a")
            .unfrackingScriptHash("e0a7938719cf21dfb39454c6dfb180207c52b006c02293b9cce37f18")
            .unfrackingEnabled(false)
            .upgradeScriptHash("2bb086c379276cbdd8571eecbc16c33512a93068d6a2359ab63b2e1b")

            // Registry: mint and spend share one script, which is also the nodes' address
            .registryNodeCs("3083d387537f9318b6ff5aeab7de5f5629d26495319b8fafd409222a")

            // Issuance template: f4118e53…#2, locked at always-fail
            .issuanceCborHexCs("aeb9c3fbfa1150f128f5806e797c84294cfb4936a061fcf5d3975ca7")
            .issuanceTemplateRef(ref("f4118e53fc0fac1dddf96c6dcc3b4670265f25558d9943feb4ee564488f5c896", 2))

            .maxInlineDatumBytes(1024)

            // Reference scripts, published in e24679be…
            .referenceScript("be59f7750a5d947bb649e70d574d066791ec34a1dfee2a087c8511e3", ref(PREPROD_REF_SCRIPTS_TX, 0)) // programmable_logic_base
            .referenceScript("b4e81fa9a3291025cd279067daf4e43578d4c3f759e5490958f58cf6", ref(PREPROD_REF_SCRIPTS_TX, 1)) // programmable_logic_global
            .referenceScript("5ff03dd084ec86a164dbf9bfe890605fe9fcba8d6657328eab8bb350", ref(PREPROD_REF_SCRIPTS_TX, 2)) // transfer
            .referenceScript("4bd96a9082194ff43ddd10d3092167f5856375959ff0ca1e1478ef6a", ref(PREPROD_REF_SCRIPTS_TX, 3)) // third_party
            .referenceScript("e0a7938719cf21dfb39454c6dfb180207c52b006c02293b9cce37f18", ref(PREPROD_REF_SCRIPTS_TX, 4)) // unfracking
            .referenceScript("23b58b985141fe5ffd2eb93c5ee4fa0e11d46a934c2837c0230071fd", ref(PREPROD_REF_SCRIPTS_TX, 5)) // issuance_logic
            .referenceScript("2bb086c379276cbdd8571eecbc16c33512a93068d6a2359ab63b2e1b", ref(PREPROD_REF_SCRIPTS_TX, 6)) // upgrade_multisig
            .build();

    /**
     * Mainnet deployment, bootstrapped by
     * {@code bfefbd222e40d88f5d4454e92b24062533070f41a3e25c0a23383264650cdb72}.
     *
     * <p>Every value here comes from the deployer's bootstrap record; the script hashes are
     * re-derived from the vendored blueprint by {@code Cip113DeploymentsTest}, and the
     * coordination datum, reference scripts, issuance template and registry origin node were
     * read back from chain on 2026-10-22 and found to match.</p>
     */
    public static final Cip113Deployment MAINNET = Cip113Deployment.builder()
            .network(Networks.mainnet())
            .bootstrapTxHash("bfefbd222e40d88f5d4454e92b24062533070f41a3e25c0a23383264650cdb72")

            // Coordination UTxO: bfefbd22…#0, held at the params policy's own script address
            .paramsPolicy("3a511ec25af655c1aef31d0d9c0c6154eae7321045b1fa46fac55ecd")

            // The coordination datum's credentials and the scripts they are compiled against.
            .programmableLogicBaseHash("d91d08e381f8ef95ffbb3f8048f020d7361ded8f3abfdf66c25fa838")
            .programmableLogicGlobalHash("46b61f0584f21481b22ea18f945e48ac4148f50fc3ade494a7762440")
            .issuanceLogicHash("ed3fb7e046b4f3387e0749fb46847dbc15e85e61aaa76558f968136d")
            .transferScriptHash("cb418959c00d6779e176fcf6227824a6ac3b5bea7a672ef66a216dae")
            .thirdPartyScriptHash("92e307862c5374ee78f3cfdaaa45447f7f0db6776d54fa5f8df1a85c")
            .unfrackingScriptHash("d431d4cba00d664acb127bb391c211086ca72fe570fd85f139af578e")
            .unfrackingEnabled(false)
            .upgradeScriptHash("2ab871806b01d07fe7d93bf45ff4ce97bd156d168d7a08078ac8a517")

            // Registry: mint and spend share one script, which is also the nodes' address
            .registryNodeCs("484e733d122af44e6101988bcc47ed261a7af5c43c797e89d60e3075")

            // Issuance template: bfefbd22…#2, locked at always-fail
            .issuanceCborHexCs("8151b7a3d4c17bfd1d3779c2726e76a06db47e36ba6c160d9a2d60f6")
            .issuanceTemplateRef(ref("bfefbd222e40d88f5d4454e92b24062533070f41a3e25c0a23383264650cdb72", 2))

            .maxInlineDatumBytes(1024)

            // Reference scripts, published in 6f251261…
            .referenceScript("d91d08e381f8ef95ffbb3f8048f020d7361ded8f3abfdf66c25fa838", ref(MAINNET_REF_SCRIPTS_TX, 0)) // programmable_logic_base
            .referenceScript("46b61f0584f21481b22ea18f945e48ac4148f50fc3ade494a7762440", ref(MAINNET_REF_SCRIPTS_TX, 1)) // programmable_logic_global
            .referenceScript("cb418959c00d6779e176fcf6227824a6ac3b5bea7a672ef66a216dae", ref(MAINNET_REF_SCRIPTS_TX, 2)) // transfer
            .referenceScript("92e307862c5374ee78f3cfdaaa45447f7f0db6776d54fa5f8df1a85c", ref(MAINNET_REF_SCRIPTS_TX, 3)) // third_party
            .referenceScript("d431d4cba00d664acb127bb391c211086ca72fe570fd85f139af578e", ref(MAINNET_REF_SCRIPTS_TX, 4)) // unfracking
            .referenceScript("ed3fb7e046b4f3387e0749fb46847dbc15e85e61aaa76558f968136d", ref(MAINNET_REF_SCRIPTS_TX, 5)) // issuance_logic
            .referenceScript("2ab871806b01d07fe7d93bf45ff4ce97bd156d168d7a08078ac8a517", ref(MAINNET_REF_SCRIPTS_TX, 6)) // upgrade_multisig
            .build();

    private static TransactionInput ref(String txHash, int index) {
        return TransactionInput.builder().transactionId(txHash).index(index).build();
    }
}
