package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProgrammableTokenApiTest {

    @Test
    void facadeRecordsExplicitSemanticOperationsWithoutMaterializing() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(address())
                .transfer(address(),
                        Amount.builder().unit("11".repeat(28) + "00ff").quantity(java.math.BigInteger.TEN).build(),
                        BigIntPlutusData.of(1));

        assertThat(tx.getIntentions()).hasSize(1);
        ExtensionIntent intent = (ExtensionIntent) tx.getIntentions().get(0);
        assertThat(intent.getExtensionId()).isEqualTo("programmable-token");
        assertThat(intent.getOperation()).isEqualTo("transfer");
        assertThat(intent.getPayload()).containsEntry("unit", "11".repeat(28) + "00ff");
    }

    @Test
    void programmableTokenIntentRoundTripsWithDefaultNamespace() {
        ProgrammableTokenProtocol protocol = protocol();
        ProgrammableTokenExtension extension = ProgrammableTokenExtension.builder()
                .protocol(protocol).deployment(java.util.Map.of("network", "preview")).build();
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .transfer(address(),
                        Amount.builder().unit("ab".repeat(28) + "3078ff").quantity(java.math.BigInteger.ONE).build(),
                        BigIntPlutusData.of(7));
        TxPlan plan = extension.configure(TxPlan.from(tx));
        TxPlanCodec codec = TxPlanCodec.builder()
                .withExtension(ProgrammableTokenExtension.DEFAULT_NAMESPACE, extension).build();

        String yaml = codec.toYaml(plan);
        TxPlan restored = codec.fromYaml(yaml);
        String restoredYaml = codec.toYaml(restored);
        ExtensionIntent restoredIntent = (ExtensionIntent) restored.getTxs().get(0).getIntentions().get(0);

        assertThat(yaml).contains("extension: programmable-token")
                .contains("protocol: test-protocol")
                .contains("type: pt:transfer")
                .contains("transfer_redeemer:");
        assertThat(restoredIntent.getPayload().get("unit")).isEqualTo("ab".repeat(28) + "3078ff");
        assertThat(restoredYaml).isEqualTo(yaml);
    }

    @Test
    void neutralPublicApiDoesNotExposeCip113Types() {
        List<Class<?>> neutralTypes = List.of(ProgrammableTokenTx.class,
                ProgrammableTokenExtension.class, ProgrammableTokenService.class,
                ProgrammableTokenProtocol.class, ProgrammableTokenRegistration.class,
                ProgrammableTokenPolicyRef.class, BurnAuthorization.class);

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

    private static ProgrammableTokenProtocol protocol() {
        return new ProgrammableTokenProtocol() {
            @Override public ProgrammableTokenProtocolDescriptor descriptor() {
                return ProgrammableTokenProtocolDescriptor.builder()
                        .id("test-protocol").contractVersion("1").build();
            }
            @Override public Set<ProgrammableTokenCapability> capabilities() {
                return java.util.EnumSet.allOf(ProgrammableTokenCapability.class);
            }
            @Override public TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
                return new TxBuildExtension() { };
            }
        };
    }

    private static String address() {
        return AddressProvider.getEntAddress(
                Credential.fromKey("22".repeat(28)), Networks.testnet()).toBech32();
    }
}
