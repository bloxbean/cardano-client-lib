package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Cip113DomainTest {

    /**
     * The worked example from CIP-0113 itself. Decoding its published smart-wallet address gives
     * the base script hash as the payment part and the user's <b>payment</b> key hash as the stake
     * part — so this pins both the derivation and the choice of credential.
     */
    private static final String OWNER_ADDRESS =
            "addr_test1qra006fdksqadv3z09a8lqf4aw8n62nmgkra9narr683x9rn2r00tud22p3ylwhk4s85764ndh5zdpnmfmfqleagml4qkhan0d";
    private static final String EXPECTED_SMART_WALLET_BY_PAYMENT_KEY =
            "addr_test1zreps2cq5daaw3hzq46untcp4vcnzgsn29xdx8589c9z50h67l5jmdqp66ezy7t607qnt6u08548k3v86t86x850zv2q8hv2dj";
    private static final String EXPECTED_SMART_WALLET_BY_STAKE_KEY =
            "addr_test1zreps2cq5daaw3hzq46untcp4vcnzgsn29xdx8589c9z50nn2r00tud22p3ylwhk4s85764ndh5zdpnmfmfqleagml4qe650tm";

    /**
     * The base script hash the CIP-0113 text's worked example is expressed against.
     *
     * <p>Kept here as a test fixture rather than a shipped constant: it belongs to an older
     * Preview instance nobody should transact with, and the only thing it is good for is checking
     * the derivation against a value someone else published. That check is worth keeping — it is
     * the only external confirmation that {@link SmartWalletAddress} is right.</p>
     */
    private static Cip113Deployment cipPublishedExample() {
        return Cip113Deployment.builder()
                .network(Networks.preview())
                .programmableLogicBaseHash("f2182b00a37bd746e20575c9af01ab31312213514cd31e872e0a2a3e")
                .build();
    }

    @Test
    void smartWalletDerivation_matchesTheCipsPublishedExample() {
        Address owner = new Address(OWNER_ADDRESS);
        Cip113Deployment cipDeployment = cipPublishedExample();

        assertThat(SmartWalletAddress.ofPaymentCredential(cipDeployment, owner).toBech32())
                .isEqualTo(EXPECTED_SMART_WALLET_BY_PAYMENT_KEY);

        assertThat(SmartWalletAddress.ofStakeCredential(cipDeployment, owner).toBech32())
                .isEqualTo(EXPECTED_SMART_WALLET_BY_STAKE_KEY);
    }

    @Test
    void smartWalletIsRecognisedAsOneOfOurs() {
        Address wallet = new Address(EXPECTED_SMART_WALLET_BY_PAYMENT_KEY);
        Cip113Deployment cipDeployment = cipPublishedExample();
        assertThat(SmartWalletAddress.isSmartWallet(cipDeployment, wallet)).isTrue();
        assertThat(SmartWalletAddress.isSmartWallet(cipDeployment, new Address(OWNER_ADDRESS))).isFalse();

        // A deployment with a different base script does not recognise it. Address derivation is
        // per-deployment, and that is the property being pinned.
        assertThat(SmartWalletAddress.isSmartWallet(Cip113Deployments.PREVIEW, wallet)).isFalse();
    }

    @Test
    void policyOrderingIsUnsignedBytewise() {
        // 0x80 must sort ABOVE 0x7f. Signed byte comparison would get this backwards, and the
        // registry's covering-node proofs depend on matching the on-chain ordering exactly.
        assertThat(PolicyOrdering.compare("7f", "80")).isNegative();
        assertThat(PolicyOrdering.compare("80", "7f")).isPositive();
        assertThat(PolicyOrdering.compare("ff", "00")).isPositive();
        assertThat(PolicyOrdering.compare("aabb", "aabb")).isZero();
        assertThat(PolicyOrdering.compare("aa", "aabb")).isNegative();   // prefix sorts first
    }

    @Test
    void coveringNodeSpansTheGapBetweenKeyAndNext() {
        assertThat(PolicyOrdering.covers("10", "30", "20")).isTrue();
        assertThat(PolicyOrdering.covers("10", "30", "30")).isFalse();   // equal to next
        assertThat(PolicyOrdering.covers("10", "30", "10")).isFalse();   // equal to key
        assertThat(PolicyOrdering.covers("10", "30", "40")).isFalse();   // beyond next
        assertThat(PolicyOrdering.covers("10", "30", "05")).isFalse();   // below key
    }

    @Test
    void coveringNodeHandlesTheTailWrapAround() {
        // The last node points back at a key that is not greater than its own, so it covers
        // everything above it.
        assertThat(PolicyOrdering.covers("f0", "00", "ff")).isTrue();
        assertThat(PolicyOrdering.covers("f0", "00", "10")).isFalse();
    }

    @Test
    void registryNodeRoundTripsThroughPlutusData() {
        RegistryNode node = RegistryNode.builder()
                .key("00216cc4179840e4d355e60cf071137e317d94a8de0fccf43b4b514a")
                .next("ffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                .mintingLogicScript(Credential.fromScript("11111111111111111111111111111111111111111111111111111111"))
                .transferLogicScript(Credential.fromScript("22222222222222222222222222222222222222222222222222222222"))
                .thirdPartyTransferLogicScript(Credential.fromKey("33333333333333333333333333333333333333333333333333333333"))
                .unfrackingLogicScript(Credential.fromKey(""))
                .globalStateCs("44444444444444444444444444444444444444444444444444444444")
                .build();

        PlutusData encoded = node.toPlutusData();
        RegistryNode decoded = RegistryNode.fromPlutusData(encoded);

        assertThat(decoded).isEqualTo(node);
        assertThat(decoded.hasGlobalState()).isTrue();
        // Credential kind must survive: constructor 0 is a key, 1 is a script.
        assertThat(decoded.getMintingLogicScript().getType())
                .isEqualTo(node.getMintingLogicScript().getType());
        assertThat(decoded.getThirdPartyTransferLogicScript().getType())
                .isEqualTo(node.getThirdPartyTransferLogicScript().getType());
    }

    @Test
    void registryNodeWithoutGlobalStateEncodesAnEmptyByteString() {
        RegistryNode node = minimalNode().toBuilder().globalStateCs(null).build();
        assertThat(RegistryNode.fromPlutusData(node.toPlutusData()).hasGlobalState()).isFalse();
    }

    @Test
    void policyIdDerivationRejectsAKeyCredential() {
        // On-chain the check is `expect Script(hashed_param)`, so a token whose issuance
        // authority is a bare key can never be registered — fail early and say why.
        assertThatThrownBy(() -> PolicyIdDerivation.derive(
                new com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex(new byte[]{1}, new byte[]{2}),
                Credential.fromKey("33333333333333333333333333333333333333333333333333333333")))
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("must be a Script credential");
    }

    @Test
    void policyIdDerivationIsDeterministicAnd28Bytes() {
        var template = new com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex(
                new byte[]{0x59, 0x01, 0x23}, new byte[]{0x00, 0x01});
        Credential issuance =
                Credential.fromScript("11111111111111111111111111111111111111111111111111111111");

        String first = PolicyIdDerivation.derive(template, issuance);
        String second = PolicyIdDerivation.derive(template, issuance);

        assertThat(first).isEqualTo(second).hasSize(56);

        // A different issuance credential must yield a different policy — that binding is the
        // whole point of the derivation.
        String other = PolicyIdDerivation.derive(template,
                Credential.fromScript("22222222222222222222222222222222222222222222222222222222"));
        assertThat(other).isNotEqualTo(first);
    }

    // ---------------------------------------------------------------------------------
    // Fixtures captured from Preview on 2026-08-26, deployment
    // a432339cbd7318222c8c51ed4fb52ee4c68f676037622aa7361dd45d897324a4.
    // These are real on-chain bytes, so they pin the codecs against the deployed contracts
    // rather than against my reading of plutus.json.
    // ---------------------------------------------------------------------------------

    /** Inline datum of the coordination UTxO a432339c…#0. */
    private static final String COORDINATION_DATUM_HEX =
            "d8799f581c9aeda27e8b7e8c0077af9d6d8077b61d4e4a8b25368280ad26dc00c8"
            + "d87a9f581c698c48a630206282690774aebcfa9410895c09f85bc103b19f9888dcff"
            + "d87a9f581c971606541dfdc9e411ba722880d783165f044cc541c17225f35d1e59ff"
            + "d87a9f581c8d2d24f8203f6049c3f36576c1628856b8012b3c10db36f7182233f4ff"
            + "d87a9f581cd4be7708df51b14718d19888db5ad8e417eda138cf83f030bf7ab857ff"
            + "d87a9f581c4861aca31fe0581ff2a16d180f26ac2b4feeb71ca5fd2a86b7927bb5ff"
            + "190800ff";

    /** Inline datum of a live registry node — fb4a2d00…#2, a token that declares global state. */
    private static final String REGISTRY_NODE_DATUM_HEX =
            "d8799f581c722c5ea0cccbcc91e825fc4eddce5f3417999b3c0128b5c4d056bf38"
            + "581ca014170337df4e7caee63b5b481539a974e362013e8009356bcacbc7"
            + "d87a9f581c3d0c7f82a57150c2b2d2436c60c4660147188fba770a341fe57f6dd5ff"
            + "d87a9f581c78e194485cc8bae98876aea4a1cf54a91347e53b6988f7c6743c5c2eff"
            + "d87a9f581c90208f687a99bad0b5fb4091573fcd3b1af7215e9e59998506a6339bff"
            + "d8799f40ff"
            + "581c4e69ae690e8fa2d38bccbf536bbeb89c344f1f50faaa2b44a92f2cf1ff";

    @Test
    void decodesTheLiveCoordinationDatum() throws Exception {
        var params = ProgrammableLogicGlobalParams.fromPlutusData(
                PlutusData.deserialize(HexUtil.decodeHexString(COORDINATION_DATUM_HEX)));

        assertThat(params.getRegistryNodeCs())
                .isEqualTo("9aeda27e8b7e8c0077af9d6d8077b61d4e4a8b25368280ad26dc00c8");
        assertThat(hex(params.getProgLogicCred()))
                .isEqualTo("698c48a630206282690774aebcfa9410895c09f85bc103b19f9888dc");
        assertThat(hex(params.getTransferCred()))
                .isEqualTo("971606541dfdc9e411ba722880d783165f044cc541c17225f35d1e59");
        assertThat(hex(params.getThirdPartyCred()))
                .isEqualTo("8d2d24f8203f6049c3f36576c1628856b8012b3c10db36f7182233f4");
        assertThat(hex(params.getUnfrackingCred()))
                .isEqualTo("d4be7708df51b14718d19888db5ad8e417eda138cf83f030bf7ab857");
        assertThat(hex(params.getUpgradeCred()))
                .isEqualTo("4861aca31fe0581ff2a16d180f26ac2b4feeb71ca5fd2a86b7927bb5");
        assertThat(params.getMaxInlineDatumBytes()).isEqualTo(2048);

        // All five delegates are script credentials on this deployment.
        assertThat(params.getProgLogicCred().getType()).isEqualTo(CredentialType.Script);
        assertThat(params.getUpgradeCred().getType()).isEqualTo(CredentialType.Script);
    }

    @Test
    void theBakedInPreviewDeploymentMatchesTheLiveCoordinationDatum() throws Exception {
        var live = Cip113Deployments.PREVIEW.withResolvedParams(
                ProgrammableLogicGlobalParams.fromPlutusData(
                        PlutusData.deserialize(HexUtil.decodeHexString(COORDINATION_DATUM_HEX))));

        // Resolving from chain must not change the constants — if this fails, either the
        // deployment was upgraded in place or the constants drifted.
        assertThat(live).isEqualTo(Cip113Deployments.PREVIEW);

        assertThat(Cip113Deployments.PREVIEW.registryAddress().toBech32())
                .isEqualTo("addr_test1wqr9pu02kzxggerr4ncrwrwu2zlqtkhzfsefepst2aazz5srqp5fw");
    }

    @Test
    void decodesALiveRegistryNode() throws Exception {
        RegistryNode node = RegistryNode.fromPlutusData(
                PlutusData.deserialize(HexUtil.decodeHexString(REGISTRY_NODE_DATUM_HEX)));

        assertThat(node.getKey()).isEqualTo("722c5ea0cccbcc91e825fc4eddce5f3417999b3c0128b5c4d056bf38");
        assertThat(node.getNext()).isEqualTo("a014170337df4e7caee63b5b481539a974e362013e8009356bcacbc7");
        assertThat(hex(node.getMintingLogicScript()))
                .isEqualTo("3d0c7f82a57150c2b2d2436c60c4660147188fba770a341fe57f6dd5");
        assertThat(hex(node.getTransferLogicScript()))
                .isEqualTo("78e194485cc8bae98876aea4a1cf54a91347e53b6988f7c6743c5c2e");
        assertThat(hex(node.getThirdPartyTransferLogicScript()))
                .isEqualTo("90208f687a99bad0b5fb4091573fcd3b1af7215e9e59998506a6339b");

        // Unfracking forbidden: the empty-vkey sentinel, constructor 0 with a zero-length hash.
        assertThat(node.getUnfrackingLogicScript().getType()).isEqualTo(CredentialType.Key);
        assertThat(node.getUnfrackingLogicScript().getBytes()).isEmpty();

        assertThat(node.hasGlobalState()).isTrue();
        assertThat(node.getGlobalStateCs())
                .isEqualTo("4e69ae690e8fa2d38bccbf536bbeb89c344f1f50faaa2b44a92f2cf1");

        // Re-encoding must reproduce the on-chain bytes exactly.
        assertThat(HexUtil.encodeHexString(node.toPlutusData().serializeToBytes()))
                .isEqualTo(REGISTRY_NODE_DATUM_HEX);
    }

    @Test
    void tailNodeSentinelIsWiderThanAPolicyId() {
        // The live registry's tail node points at a 30-byte 0xff… sentinel, which is longer than
        // any 28-byte policy. Ordering must handle unequal lengths, not assume 28 bytes.
        String tailKey = "edadfd4b7d1327f90570e6b3efb852d6dfbf6e02da62ec9f3a39be24";
        String sentinel = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        assertThat(PolicyOrdering.compare(tailKey, sentinel)).isNegative();
        assertThat(PolicyOrdering.covers(tailKey, sentinel,
                "ffffffffffffffffffffffffffffffffffffffffffffffffffff")).isTrue();
    }

    @Test
    void originNodeCoversEverythingBelowItsNext() {
        // The live origin node has an empty key.
        assertThat(PolicyOrdering.covers("", "36af3f76af2dd0f09c2d45c11a668efb81096488251ef640d685dacf",
                "0011223344556677889900112233445566778899001122334455")).isTrue();
        assertThat(PolicyOrdering.covers("", "36af3f76af2dd0f09c2d45c11a668efb81096488251ef640d685dacf",
                "9911223344556677889900112233445566778899001122334455")).isFalse();
    }

    @Test
    void assembledIssuanceScriptReproducesTheOnChainPolicyId() throws Exception {
        // Grounded end to end: the real issuance template from the Preview deployment, the real
        // always-true issuance credential, and the policy id that deployment actually reports for
        // it. If the assembly, the CBOR wrapping or the hashing were wrong, this would not match.
        var template = previewIssuanceTemplate();
        Credential issuance =
                Credential.fromScript("4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10");

        String policyId = PolicyIdDerivation.derive(template, issuance);
        assertThat(policyId)
                .isEqualTo("3658dd8748c040ddbf15e54311816a76aeeb7d1fd218e39c1ddb4f21");

        // The load-bearing invariant behind minting: the script CCL attaches and the policy it
        // mints under agree by construction — no UPLC applier, no on-chain lookup.
        assertThat(PolicyIdDerivation.issuanceScript(template, issuance).getPolicyId())
                .as("the assembled script must hash to the policy it mints under")
                .isEqualToIgnoringCase(policyId);
    }

    @Test
    void assembledScriptIsExactlyPrefixCredentialPostfix() {
        var template = new com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex(
                HexUtil.decodeHexString("5906900101"), HexUtil.decodeHexString("ff004c01"));
        Credential issuance =
                Credential.fromScript("4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10");

        assertThat(HexUtil.encodeHexString(
                PolicyIdDerivation.issuanceScriptBytes(template, issuance)))
                .isEqualTo("5906900101"
                        + "4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10"
                        + "ff004c01");
    }

    private static com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex
            previewIssuanceTemplate() throws Exception {
        var props = new java.util.Properties();
        try (var in = Cip113DomainTest.class.getResourceAsStream(
                "/cip113/preview-issuance-template.properties")) {
            props.load(in);
        }
        return new com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex(
                HexUtil.decodeHexString(props.getProperty("prefix")),
                HexUtil.decodeHexString(props.getProperty("postfix")));
    }

    @Test
    void aDifferentIssuanceCredentialYieldsADifferentScriptAndPolicy() throws Exception {
        var template = new com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex(
                new byte[]{0x59, 0x01}, new byte[]{0x00});

        var a = PolicyIdDerivation.issuanceScript(template,
                Credential.fromScript("11111111111111111111111111111111111111111111111111111111"));
        var b = PolicyIdDerivation.issuanceScript(template,
                Credential.fromScript("22222222222222222222222222222222222222222222222222222222"));

        assertThat(a.getPolicyId()).isNotEqualTo(b.getPolicyId());
    }

    private static String hex(Credential credential) {
        return HexUtil.encodeHexString(credential.getBytes());
    }

    private static RegistryNode minimalNode() {
        Credential any = Credential.fromScript("11111111111111111111111111111111111111111111111111111111");
        return RegistryNode.builder()
                .key("00").next("ff")
                .mintingLogicScript(any).transferLogicScript(any)
                .thirdPartyTransferLogicScript(any).unfrackingLogicScript(any)
                .globalStateCs("")
                .build();
    }
}
