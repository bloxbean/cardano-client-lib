package com.bloxbean.cardano.client.cip.cip113;

import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vendored blueprint is the source of truth; this holds the hand-written codecs to it.
 *
 * <p>{@code Cip113Data}, {@code Cip113Redeemers} and the model classes encode constructor indices
 * and field order by hand. Nothing stops those from drifting when the blueprint is re-vendored —
 * and the drift is invisible until a validator rejects a transaction with no trace, because a
 * mis-ordered field is still perfectly well-formed CBOR.</p>
 *
 * <p>Checking against the blueprint costs one test and catches the drift at build time. It is the
 * cheap half of what generating the types from the blueprint would buy: generation would also
 * remove the hand-written code, but it would replace this module's public model API with generated
 * variant classes and converters, and no module in this repo wires the annotation processor into a
 * main source set. The trade was made deliberately — see {@code docs/plans/}.</p>
 */
class BlueprintCodecAgreementTest {

    private static PlutusContractBlueprint blueprint;

    @BeforeAll
    static void load() {
        blueprint = PlutusBlueprintLoader.loadBlueprint(
                BlueprintCodecAgreementTest.class.getResourceAsStream(
                        "/blueprint/cip113/plutus.json"));
    }

    /** Every constructor of a definition, by its declared index. */
    private static Map<Integer, BlueprintSchema> constructors(String definition) {
        BlueprintSchema schema = blueprint.getDefinitions().get(definition);
        assertThat(schema)
                .as("the blueprint no longer defines %s — the vendored version changed shape,"
                        + " and the hand-written codec for it is now guesswork", definition)
                .isNotNull();
        return schema.getAnyOf().stream()
                .collect(Collectors.toMap(BlueprintSchema::getIndex, c -> c));
    }

    private static List<String> fieldNames(BlueprintSchema constructor) {
        if (constructor.getFields() == null) return List.of();
        return constructor.getFields().stream()
                .map(BlueprintSchema::getTitle)
                .collect(Collectors.toList());
    }

    private static void assertConstructor(String definition, int index, String... expectedFields) {
        BlueprintSchema constructor = constructors(definition).get(index);
        assertThat(constructor)
                .as("%s has no constructor at index %d", definition, index)
                .isNotNull();
        assertThat(fieldNames(constructor))
                .as("%s constructor %d ('%s'): field order is positional in CBOR, so a change here"
                        + " silently mis-decodes rather than failing",
                        definition, index, constructor.getTitle())
                .containsExactly(expectedFields);
    }

    @Test
    void registryNodeMatchesTheBlueprint() {
        assertConstructor("registry_node/RegistryNode", 0,
                "key", "next", "minting_logic_script", "transfer_logic_script",
                "third_party_transfer_logic_script", "unfracking_logic_script", "global_state_cs");
    }

    /**
     * The three dispatch arms, in the order {@code Cip113Redeemers} hard-codes them: transfer 0,
     * third-party 1, unfracking 2. Naming the wrong arm authorises the wrong delegate.
     */
    @Test
    void baseSpendRedeemerArmsMatchTheBlueprint() {
        Map<Integer, BlueprintSchema> arms = constructors("types/BaseSpendRedeemer");

        assertThat(arms.get(0).getTitle()).isEqualTo("SpendViaTransfer");
        assertThat(arms.get(1).getTitle()).isEqualTo("SpendViaThirdParty");
        assertThat(arms.get(2).getTitle()).isEqualTo("SpendViaUnfracking");

        assertConstructor("types/BaseSpendRedeemer", 0, "params_idx", "wdrl_idx");
        assertConstructor("types/BaseSpendRedeemer", 1, "params_idx", "wdrl_idx");
        assertConstructor("types/BaseSpendRedeemer", 2, "params_idx", "wdrl_idx");
    }

    @Test
    void transferRedeemerMatchesTheBlueprint() {
        assertConstructor("types/TransferRedeemer", 0, "params_idx", "proofs");
    }

    @Test
    void thirdPartyRedeemerMatchesTheBlueprint() {
        assertConstructor("types/ThirdPartyRedeemer", 0,
                "params_idx", "registry_node_idx", "outputs_start_idx");
    }

    /**
     * {@code RefInput} is 0 and {@code OutputIndex} is 1. Swapping them would make an ordinary mint
     * read an output index as a reference-input index — in range, wrong UTxO, no trace.
     */
    @Test
    void mintingRegistryProofMatchesTheBlueprint() {
        Map<Integer, BlueprintSchema> arms = constructors("types/MintingRegistryProof");
        assertThat(arms.get(0).getTitle()).isEqualTo("RefInput");
        assertThat(arms.get(1).getTitle()).isEqualTo("OutputIndex");

        assertConstructor("types/MintingRegistryProof", 0, "index");
        assertConstructor("types/MintingRegistryProof", 1, "index");
    }

    @Test
    void registryRedeemerMatchesTheBlueprint() {
        Map<Integer, BlueprintSchema> arms = constructors("types/RegistryRedeemer");
        assertThat(arms.get(0).getTitle()).isEqualTo("RegistryInit");
        assertThat(arms.get(1).getTitle()).isEqualTo("RegistryInsert");

        assertConstructor("types/RegistryRedeemer", 0);
        assertConstructor("types/RegistryRedeemer", 1, "key", "minting_logic_script");
    }

    /**
     * There is deliberately no upstream constructor for a node update — {@code registry_spend}
     * recognises one by the absence of a registry-node mint. If a constructor for it ever appears,
     * {@code updateRegistryNode} should be revisited rather than left on the implicit path.
     */
    @Test
    void registryRedeemerStillHasNoUpdateConstructor() {
        assertThat(constructors("types/RegistryRedeemer"))
                .as("upstream added a RegistryRedeemer constructor; updateRegistryNode assumes an"
                        + " update is signalled only by minting no node NFT")
                .hasSize(2);
    }

    @Test
    void registryProofMatchesTheBlueprint() {
        Map<Integer, BlueprintSchema> arms = constructors("types/RegistryProof");
        assertThat(arms.get(0).getTitle()).isEqualTo("TokenExists");
        assertThat(arms.get(1).getTitle()).isEqualTo("TokenDoesNotExist");
    }

    @Test
    void vendoredBlueprintIsTheVersionThisModuleWasWrittenAgainst() {
        assertThat(blueprint.getPreamble().getVersion())
                .as("the codecs, the policy-id derivation and the index discipline were all read"
                        + " off this version; re-vendoring is a review, not a drop-in")
                .isEqualTo("0.5.0-alpha.2");
    }
}
