package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableBurnIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableMintIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableRegisterIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTokenAsset;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTransferIntent;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProgrammableTokenApiTest {

    @Test
    void facadeRecordsTypedSemanticOperationsWithoutMaterializing() {
        Amount amount = Amount.builder().unit("11".repeat(28) + "00ff")
                .quantity(BigInteger.TEN).build();
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(address())
                .transfer(address(), amount, BigIntPlutusData.of(1));

        assertThat(tx.getIntentions()).singleElement().isInstanceOfSatisfying(
                ProgrammableTransferIntent.class, intent -> {
                    assertThat(intent.getExtensionId()).isEqualTo("programmable-token");
                    assertThat(intent.getOperation()).isEqualTo("transfer");
                    assertThat(intent.getReceiver()).isEqualTo(address());
                    assertThat(intent.getAmount()).isEqualTo(amount);
                    assertThat(intent.getTransferRedeemer().requireResolved())
                            .isEqualTo(BigIntPlutusData.of(1));
                });
    }

    @Test
    void authoringAndCompositionPerformNoChainIoOrProtocolMaterialization() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        ProtocolParamsSupplier protocolParamsSupplier = mock(ProtocolParamsSupplier.class);
        AtomicInteger materializers = new AtomicInteger();
        ProgrammableTokenExtension extension = extension(protocol(materializers));
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(address())
                .transfer(address(), Amount.asset("11".repeat(28) + "00", 1),
                        BigIntPlutusData.of(0));

        new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension)
                .compose(tx);

        assertThat(materializers).hasValue(0);
        verifyNoInteractions(utxoSupplier, protocolParamsSupplier);
    }

    @Test
    void serviceCreatesPlanCodecsForDefaultAndCustomNamespaces() {
        ProgrammableTokenProtocol protocol = protocol(new AtomicInteger());
        ProgrammableTokenExtension extension = extension(protocol);
        ProgrammableTokenService service = new ProgrammableTokenService() {
            @Override public ProgrammableTokenProtocolDescriptor protocol() {
                return protocol.descriptor();
            }
            @Override public Set<ProgrammableTokenCapability> capabilities() {
                return protocol.capabilities();
            }
            @Override public ProgrammableTokenExtension extension() {
                return extension;
            }
        };
        ProgrammableTokenTx tx = new ProgrammableTokenTx().transfer(address(),
                Amount.asset("ab".repeat(28) + "00", 1), BigIntPlutusData.of(7));

        String defaultYaml = service.txPlanCodec().toYaml(
                extension.configure(TxPlan.from(tx)));
        String customYaml = service.txPlanCodec("tokens").toYaml(
                extension.configure(TxPlan.from(tx), "tokens"));

        assertThat(defaultYaml).contains("type: pt:transfer");
        assertThat(customYaml).contains("type: tokens:transfer");
    }

    @Test
    void contractVersionIsInformationalAndPreservedWithoutCompatibilityValidation() {
        ProgrammableTokenExtension extension = extension(protocol(new AtomicInteger()));
        TxPlanCodec codec = codec(extension);
        String yaml = """
                version: '1.0'
                extensions:
                  pt:
                    extension: programmable-token
                    schema_version: '1'
                    protocol: test-protocol
                    contract_version: future-public-deployment
                    deployment:
                      network: preview
                transaction: []
                """;

        TxPlan restored = codec.fromYaml(yaml);
        String canonical = codec.toYaml(restored);

        assertThat(restored.getExtensions().get("pt").getContractVersion())
                .isEqualTo("future-public-deployment");
        assertThat(canonical).contains("contract_version: future-public-deployment");
    }

    @Test
    void programmableTransferIntentRoundTripsWithDefaultNamespace() {
        ProgrammableTokenExtension extension = extension(protocol(new AtomicInteger()));
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .transfer(address(), Amount.builder()
                                .unit("ab".repeat(28) + "3078ff")
                                .quantity(BigInteger.ONE).build(),
                        BigIntPlutusData.of(7));
        TxPlan plan = extension.configure(TxPlan.from(tx));
        TxPlanCodec codec = codec(extension);

        String yaml = codec.toYaml(plan);
        TxPlan restored = codec.fromYaml(yaml);
        String restoredYaml = codec.toYaml(restored);

        assertThat(yaml).contains("extension: programmable-token")
                .contains("protocol: test-protocol")
                .contains("type: pt:transfer")
                .contains("transfer_redeemer:");
        assertThat(restored.getTxs().get(0).getIntentions()).singleElement()
                .isInstanceOfSatisfying(ProgrammableTransferIntent.class, intent -> {
                    assertThat(intent.getAmount().getUnit())
                            .isEqualTo("ab".repeat(28) + "3078ff");
                    assertThat(intent.getAmount().getQuantity()).isEqualTo(BigInteger.ONE);
                    assertThat(intent.getTransferRedeemer().requireResolved())
                            .isEqualTo(BigIntPlutusData.of(7));
                });
        assertThat(restoredYaml).isEqualTo(yaml);
    }

    @Test
    void mintBurnAndRegisterIntentsRoundTripAsConcreteTypes() {
        String policyId = "aa".repeat(28);
        ProgrammableTokenRegistration registration = ProgrammableTokenRegistration.builder()
                .mintingLogicScript(ProgrammableTokenCredential.from(
                        Credential.fromScript("11".repeat(28))))
                .transferLogicScript(ProgrammableTokenCredential.from(
                        Credential.fromScript("22".repeat(28))))
                .thirdPartyTransferLogicScript(ProgrammableTokenCredential.from(
                        Credential.fromKey("33".repeat(28))))
                .unfrackingLogicScript(ProgrammableTokenCredential.from(
                        Credential.fromKey(new byte[0])))
                .build();
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .register("named-policy", registration, PlutusData.unit())
                .mint(ProgrammableTokenPolicyRef.named("named-policy"), address(),
                        List.of(new Asset("0x00ff", BigInteger.TEN)),
                        BigIntPlutusData.of(1), BigIntPlutusData.of(2))
                .burn(policyId, List.of(new Asset("0x01", BigInteger.ONE)),
                        BurnAuthorization.of(BigIntPlutusData.of(3), BigIntPlutusData.of(4)));
        ProgrammableTokenExtension extension = extension(protocol(new AtomicInteger()));
        TxPlanCodec codec = codec(extension);

        String yaml = codec.toYaml(extension.configure(TxPlan.from(tx)));
        TxPlan restored = codec.fromYaml(yaml);

        assertThat(yaml)
                .contains("minting_logic_script:", "transfer_logic_script:",
                        "third_party_transfer_logic_script:", "unfracking_logic_script:")
                .doesNotContain("mintingLogicScript", "transferLogicScript");

        assertThat(restored.getTxs().get(0).getIntentions())
                .hasExactlyElementsOfTypes(ProgrammableRegisterIntent.class,
                        ProgrammableMintIntent.class, ProgrammableBurnIntent.class);
        ProgrammableRegisterIntent restoredRegister = (ProgrammableRegisterIntent)
                restored.getTxs().get(0).getIntentions().get(0);
        ProgrammableMintIntent restoredMint = (ProgrammableMintIntent)
                restored.getTxs().get(0).getIntentions().get(1);
        ProgrammableBurnIntent restoredBurn = (ProgrammableBurnIntent)
                restored.getTxs().get(0).getIntentions().get(2);

        assertThat(restoredRegister.getName()).isEqualTo("named-policy");
        assertThat(restoredRegister.getRegistration()).isEqualTo(registration);
        assertThat(restoredMint.getPolicy().getName()).isEqualTo("named-policy");
        assertThat(restoredMint.getAssets()).containsExactly(
                new ProgrammableTokenAsset("00ff", BigInteger.TEN));
        assertThat(restoredMint.getInlineDatum().requireResolved())
                .isEqualTo(BigIntPlutusData.of(2));
        assertThat(restoredBurn.getPolicy().getPolicyId()).isEqualTo(policyId);
        assertThat(restoredBurn.getTransferRedeemer().requireResolved())
                .isEqualTo(BigIntPlutusData.of(3));
        assertThat(restoredBurn.getIssuanceRedeemer().requireResolved())
                .isEqualTo(BigIntPlutusData.of(4));
    }

    @Test
    void handWrittenProgrammableTokenYamlParsesToTypedSemanticIntent() {
        ProgrammableTokenExtension extension = extension(protocol(new AtomicInteger()));
        TxPlanCodec codec = codec(extension);
        String unit = "cd".repeat(28) + "00ff";
        String yaml = """
                version: '1.0'
                extensions:
                  pt:
                    extension: programmable-token
                    schema_version: '1'
                    protocol: test-protocol
                    contract_version: '1'
                    deployment:
                      network: preview
                transaction:
                  - tx:
                      from: %s
                      intents:
                        - type: pt:transfer
                          receiver: %s
                          unit: %s
                          quantity: 3
                          transfer_redeemer:
                            int: 9
                """.formatted(address(), address(), unit);

        TxPlan restored = codec.fromYaml(yaml);

        assertThat(restored.getExtensions().get("pt").getExtension())
                .isEqualTo(ProgrammableTokenExtension.ID);
        assertThat(restored.getTxs()).singleElement().isInstanceOfSatisfying(Tx.class, tx -> {
            assertThat(tx.getSender()).isEqualTo(address());
            assertThat(tx.getIntentions()).singleElement().isInstanceOfSatisfying(
                    ProgrammableTransferIntent.class, intent -> {
                        assertThat(intent.getReceiver()).isEqualTo(address());
                        assertThat(intent.getAmount()).isEqualTo(Amount.asset(unit, 3));
                        assertThat(intent.getTransferRedeemer().requireResolved())
                                .isEqualTo(BigIntPlutusData.of(9));
                    });
        });
    }

    @Test
    void intentResolvesStructuredTemplateWithoutRawYamlSubstitution() throws Exception {
        String unit = "cd".repeat(28) + "00ff";
        ProgrammableTransferIntent unresolved = unresolvedMapper().readValue("""
                type: programmable-token:transfer
                receiver: %s
                unit: %s
                quantity: 3
                transfer_redeemer:
                  constructor: 0
                  fields:
                    - bytes: ${owner_pkh}
                    - int: ${nonce}
                """.formatted(address(), unit), ProgrammableTransferIntent.class);

        assertThat(unresolved.getTransferRedeemer().isResolved()).isFalse();

        ProgrammableTransferIntent resolved = (ProgrammableTransferIntent)
                unresolved.resolveVariables(Map.of("owner_pkh", "ab".repeat(28), "nonce", 17));

        assertThat(resolved.getTransferRedeemer().requireResolved()).isEqualTo(
                ConstrPlutusData.of(0, BytesPlutusData.of(
                                HexUtil.decodeHexString("ab".repeat(28))),
                        BigIntPlutusData.of(17)));
    }

    @Test
    void burnHexTemplatesResolveThenSerializeCanonically() throws Exception {
        String policyId = "aa".repeat(28);
        String transferCbor = ConstrPlutusData.of(0).serializeToHex();
        String issuanceCbor = BigIntPlutusData.of(23).serializeToHex();
        ProgrammableBurnIntent unresolved = unresolvedMapper().readValue("""
                type: programmable-token:burn
                policy_id: %s
                assets:
                  - name: 00ff
                    quantity: 1
                transfer_redeemer_hex: ${transfer_cbor}
                issuance_redeemer_hex: ${issuance_cbor}
                """.formatted(policyId), ProgrammableBurnIntent.class);

        assertThat(unresolved.getTransferRedeemer().getForm())
                .isEqualTo(PlutusDataValue.Form.CBOR_HEX);

        ProgrammableBurnIntent resolved = (ProgrammableBurnIntent) unresolved.resolveVariables(
                Map.of("transfer_cbor", transferCbor, "issuance_cbor", issuanceCbor));
        String canonical = YamlSerializer.getYamlMapper().writeValueAsString(resolved);

        assertThat(resolved.getTransferRedeemer().requireResolved().serializeToHex())
                .isEqualTo(ConstrPlutusData.of(0).serializeToHex());
        assertThat(resolved.getIssuanceRedeemer().requireResolved())
                .isEqualTo(BigIntPlutusData.of(23));
        assertThat(canonical).contains("transfer_redeemer:", "issuance_redeemer:")
                .doesNotContain("transfer_redeemer_hex", "issuance_redeemer_hex");
    }

    @Test
    void competingAndMissingRedeemerRepresentationsFailClearly() throws Exception {
        String transferCbor = BigIntPlutusData.of(1).serializeToHex();
        String yaml = """
                type: programmable-token:transfer
                receiver: %s
                unit: %s00
                quantity: 1
                transfer_redeemer:
                  int: 1
                transfer_redeemer_hex: %s
                """.formatted(address(), "aa".repeat(28), transferCbor);

        assertThatThrownBy(() -> unresolvedMapper().readValue(
                yaml, ProgrammableTransferIntent.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transfer_redeemer_hex");

        ProgrammableTransferIntent missing = ProgrammableTransferIntent.builder()
                .receiver(address()).amount(Amount.asset("aa".repeat(28) + "00", 1)).build();
        assertThatThrownBy(missing::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transfer_redeemer is required");
    }

    @Test
    void txPlanBurnAcceptsVariableCborHexAndEmitsCanonicalStructuredData() {
        ProgrammableTokenExtension extension = extension(protocol(new AtomicInteger()));
        TxPlanCodec codec = codec(extension);
        String policyId = "aa".repeat(28);
        String transferCbor = ConstrPlutusData.of(0).serializeToHex();
        String issuanceCbor = BigIntPlutusData.of(31).serializeToHex();
        String yaml = """
                version: '1.0'
                variables:
                  transfer_cbor: %s
                  issuance_cbor: %s
                extensions:
                  pt:
                    extension: programmable-token
                    schema_version: '1'
                    protocol: test-protocol
                    contract_version: '1'
                    deployment:
                      network: preview
                transaction:
                  - tx:
                      from: %s
                      intents:
                        - type: pt:burn
                          policy_id: %s
                          assets:
                            - name: 00ff
                              quantity: 1
                          transfer_redeemer_hex: ${transfer_cbor}
                          issuance_redeemer_hex: ${issuance_cbor}
                """.formatted(transferCbor, issuanceCbor, address(), policyId);

        TxPlan restored = codec.fromYaml(yaml);
        ProgrammableBurnIntent burn = (ProgrammableBurnIntent)
                restored.getTxs().get(0).getIntentions().get(0);
        String canonical = codec.toYaml(restored);

        assertThat(burn.getTransferRedeemer().requireResolved().serializeToHex())
                .isEqualTo(transferCbor);
        assertThat(burn.getIssuanceRedeemer().requireResolved().serializeToHex())
                .isEqualTo(issuanceCbor);
        assertThat(canonical).contains("transfer_redeemer:", "issuance_redeemer:")
                .doesNotContain("transfer_redeemer_hex", "issuance_redeemer_hex");
    }

    @Test
    void neutralPublicApiDoesNotExposeCip113Types() {
        List<Class<?>> neutralTypes = List.of(ProgrammableTokenTx.class,
                ProgrammableTokenExtension.class, ProgrammableTokenService.class,
                ProgrammableTokenProtocol.class, ProgrammableTokenRegistration.class,
                ProgrammableTokenPolicyRef.class, ProgrammableTokenRegistryUpdate.class,
                BurnAuthorization.class, ProgrammableTransferIntent.class,
                ProgrammableMintIntent.class, ProgrammableBurnIntent.class,
                ProgrammableRegisterIntent.class);

        for (Class<?> type : neutralTypes) {
            assertThat(java.util.Arrays.stream(type.getDeclaredFields())
                    .map(field -> field.getType().getName())
                    .filter(name -> name.contains(".cip113")))
                    .as(type.getName()).isEmpty();
            assertThat(java.util.Arrays.stream(type.getDeclaredMethods())
                    .flatMap(method -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(method.getReturnType()),
                            java.util.Arrays.stream(method.getParameterTypes())))
                    .map(Class::getName).filter(name -> name.contains(".cip113")))
                    .as(type.getName()).isEmpty();
        }
    }

    private static ProgrammableTokenExtension extension(ProgrammableTokenProtocol protocol) {
        return ProgrammableTokenExtension.builder().protocol(protocol)
                .deployment(Map.of("network", "preview")).build();
    }

    private static ObjectMapper unresolvedMapper() {
        ObjectMapper mapper = YamlSerializer.getYamlMapper().copy();
        mapper.registerSubtypes(
                new NamedType(ProgrammableTransferIntent.class, "programmable-token:transfer"),
                new NamedType(ProgrammableBurnIntent.class, "programmable-token:burn"));
        return mapper;
    }

    private static TxPlanCodec codec(ProgrammableTokenExtension extension) {
        return TxPlanCodec.builder()
                .withExtension(ProgrammableTokenExtension.DEFAULT_NAMESPACE, extension).build();
    }

    private static ProgrammableTokenProtocol protocol(AtomicInteger materializers) {
        return new ProgrammableTokenProtocol() {
            @Override public ProgrammableTokenProtocolDescriptor descriptor() {
                return ProgrammableTokenProtocolDescriptor.builder()
                        .id("test-protocol").contractVersion("1").build();
            }
            @Override public Set<ProgrammableTokenCapability> capabilities() {
                return java.util.EnumSet.allOf(ProgrammableTokenCapability.class);
            }
            @Override public TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
                materializers.incrementAndGet();
                return new TxBuildExtension() { };
            }
        };
    }

    private static String address() {
        return AddressProvider.getEntAddress(
                Credential.fromKey("22".repeat(28)), Networks.testnet()).toBech32();
    }
}
