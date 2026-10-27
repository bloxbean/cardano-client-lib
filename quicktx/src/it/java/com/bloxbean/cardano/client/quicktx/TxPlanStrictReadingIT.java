package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end checks of strict TxPlan reading on Yaci DevKit: documents that pass the published schema and the strict
 * readers build and submit, and mistakes are stopped before anything is built.
 * <p>
 * Run with Yaci DevKit on :8080 (admin :10000): {@code ./gradlew :quicktx:integrationTest --tests TxPlanStrictReadingIT}
 */
public class TxPlanStrictReadingIT extends TestDataBaseIT {

    private static final String SCHEMA_ID = "https://cardano-client.dev/schemas/txplan/v1alpha1";
    private static Schema schema;

    @BeforeAll
    static void loadSchema() throws Exception {
        try (InputStream in = TxPlanStrictReadingIT.class.getResourceAsStream("/schema/txplan-v1alpha1.schema.json")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    builder -> builder.schemas(Map.of(SCHEMA_ID, text))).getSchema(SchemaLocation.of(SCHEMA_ID));
        }
    }

    @Test
    void wholeNumbersWrittenAsFloatsAreSubmittedAsIntegers() throws Exception {
        String yaml = """
                version: 1.0
                variables:
                  second_amount: 3000000.0
                transaction:
                  - tx:
                      from: %s
                      intents:
                        - type: payment
                          address: %s
                          amounts:
                            - unit: lovelace
                              quantity: 20000000.0
                        - type: payment
                          address: %s
                          amounts:
                            - unit: lovelace
                              quantity: ${second_amount}
                """.formatted(sender1Addr, receiver2, receiver3);

        assertThat(schemaErrors(yaml)).isEmpty();
        Result<String> result = submit(TxPlan.from(yaml));

        assertThat(lovelaceSentTo(result.getValue(), receiver2)).isEqualTo(BigInteger.valueOf(20_000_000));
        assertThat(lovelaceSentTo(result.getValue(), receiver3)).isEqualTo(BigInteger.valueOf(3_000_000));
    }

    @Test
    void aMisspelledFieldStopsTheDocumentBeforeAnythingIsBuilt() {
        String yaml = """
                version: 1.0
                transaction:
                  - tx:
                      from: %s
                      intents:
                        - type: payment
                          adress: %s
                          amounts:
                            - unit: lovelace
                              quantity: 2000000
                """.formatted(sender1Addr, receiver2);

        assertThat(schemaErrors(yaml)).isNotEmpty();
        assertThatThrownBy(() -> TxPlan.from(yaml)).hasMessageContaining("adress");
        assertThatThrownBy(() -> TxPlanCodec.builder().build().fromYaml(yaml)).hasMessageContaining("adress");

        // The opt-out reads it the old way: the misspelled field is dropped.
        TxPlan lenient = TxPlanCodec.builder().strictReading(false).build().fromYaml(yaml);
        assertThat(((PaymentIntent) lenient.getTxs().get(0).getIntentions().get(0)).getAddress()).isNull();
    }

    @Test
    void numberLikeMetadataTextSurvivesTheCanonicalWriterOnChain() throws Exception {
        CBORMetadata metadata = new CBORMetadata()
                .put(BigInteger.valueOf(1001), "0x1F")
                .put(BigInteger.valueOf(1002), "20000000.0");
        TxPlan built = TxPlan.from(new Tx()
                .payToAddress(receiver2, Amount.ada(2))
                .attachMetadata(metadata)
                .from(sender1Addr));

        String yaml = built.toYaml();
        assertThat(schemaErrors(yaml)).isEmpty();
        Result<String> result = submit(TxPlan.from(yaml));

        List<MetadataJSONContent> onChain = backendService.getMetadataService()
                .getJSONMetadataByTxnHash(result.getValue()).getValue();
        assertThat(onChain).anySatisfy(entry -> {
            assertThat(entry.getLabel()).isEqualTo("1001");
            assertThat(entry.getJsonMetadata().asText()).isEqualTo("0x1F");
        });
        assertThat(onChain).anySatisfy(entry -> {
            assertThat(entry.getLabel()).isEqualTo("1002");
            assertThat(entry.getJsonMetadata().asText()).isEqualTo("20000000.0");
        });
    }

    @Test
    void codecSnapshotsReadLiterallyAndSubmit() throws Exception {
        String yaml = """
                version: 1.0
                transaction:
                  - tx:
                      from: %s
                      intents:
                        - type: payment
                          address: ${receiver}
                          amounts:
                            - unit: lovelace
                              quantity: ${amount}
                """.formatted(sender1Addr);
        TxPlanCodec codec = TxPlanCodec.builder().build();

        TxPlan bound = codec.fromYaml(yaml, Map.of("receiver", receiver3, "amount", BigInteger.valueOf(4_000_000)));
        String snapshot = codec.toYaml(bound);
        Result<String> result = submit(codec.readLiteral(snapshot));

        assertThat(codec.toYaml(codec.readLiteral(snapshot))).isEqualTo(snapshot);
        assertThat(lovelaceSentTo(result.getValue(), receiver3)).isEqualTo(BigInteger.valueOf(4_000_000));
    }

    private Result<String> submit(TxPlan plan) {
        Result<String> result = quickTxBuilder.compose(plan)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);
        assertThat(result.isSuccessful()).as(result.getResponse()).isTrue();
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
        return result;
    }

    private BigInteger lovelaceSentTo(String txHash, String address) throws Exception {
        return backendService.getTransactionService().getTransactionUtxos(txHash).getValue().getOutputs().stream()
                .filter(output -> output.getAddress().equals(address))
                .map(TxContentUtxoOutputs::getAmount)
                .flatMap(List::stream)
                .filter(amount -> "lovelace".equals(amount.getUnit()))
                .map(TxContentOutputAmount::getQuantity)
                .map(BigInteger::new)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static List<String> schemaErrors(String yaml) {
        try {
            List<Error> errors = schema.validate(YamlSerializer.getYamlMapper().readTree(yaml));
            return errors.stream().map(e -> e.getInstanceLocation() + ": " + e.getMessage()).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
