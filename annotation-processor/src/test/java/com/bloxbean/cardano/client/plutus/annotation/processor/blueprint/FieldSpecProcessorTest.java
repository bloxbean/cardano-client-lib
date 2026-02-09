package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.annotation.processing.ProcessingEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FieldSpecProcessor}.
 */
class FieldSpecProcessorTest {

    private FieldSpecProcessor fieldSpecProcessor;

    @BeforeEach
    void setUp() {
        // Create a mock Blueprint annotation
        Blueprint mockAnnotation = Mockito.mock(Blueprint.class);
        Mockito.when(mockAnnotation.packageName()).thenReturn("com.test");

        // Create minimal mocks for dependencies
        ProcessingEnvironment mockProcessingEnv = Mockito.mock(ProcessingEnvironment.class);
        GeneratedTypesRegistry mockRegistry = Mockito.mock(GeneratedTypesRegistry.class);
        SharedTypeLookup mockSharedTypeLookup = Mockito.mock(SharedTypeLookup.class);

        fieldSpecProcessor = new FieldSpecProcessor(
                mockAnnotation,
                mockProcessingEnv,
                mockRegistry,
                mockSharedTypeLookup
        );
    }

    /**
     * Tests for resolveTitleFromDefinitionKey() method.
     *
     * <p>This method is critical for CIP-57 compliance - it handles optional "title" fields
     * by falling back to extracting class names from definition keys.</p>
     */
    @Nested
    class ResolveTitleFromDefinitionKeyTests {

        @Test
        void shouldReturnNull_whenSchemaIsNull() {
            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", null);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_whenDefinitionKeyIsNull_andSchemaHasNoTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey(null, schema);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_whenDefinitionKeyIsEmpty_andSchemaHasNoTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("", schema);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnSchemaTitle_whenTitleIsPresent() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("Action");

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/SomethingElse", schema);

            assertThat(result).isEqualTo("Action");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_simpleKey() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("Int", schema);

            assertThat(result).isEqualTo("Int");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_pathWithSlashes() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", schema);

            assertThat(result).isEqualTo("Data");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_multiLevelPath() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("cardano/transaction/OutputReference", schema);

            assertThat(result).isEqualTo("OutputReference");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_withJsonPointerEscaping() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types~1custom~1Data", schema);

            assertThat(result).isEqualTo("Data");
        }

        @Test
        void shouldReturnNull_whenSchemaHasEmptyTitle_andDefinitionKeyIsNull() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("");  // Empty title

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey(null, schema);

            assertThat(result).isNull();
        }

        @Test
        void shouldExtractClassName_whenSchemaHasEmptyTitle_andDefinitionKeyIsValid() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("");  // Empty title

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/order/Action", schema);

            assertThat(result).isEqualTo("Action");
        }

        @Test
        void shouldPreferSchemaTitle_overDefinitionKey() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("CustomName");

            // Even though definition key would extract "Data", schema title takes precedence
            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", schema);

            assertThat(result).isEqualTo("CustomName");
        }

        @Test
        void shouldHandleWhitespaceTitle_asEmpty() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("   ");  // Whitespace - should be treated as present

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", schema);

            // Note: Current implementation treats whitespace as valid title
            assertThat(result).isEqualTo("   ");
        }

        @Test
        void shouldExtractClassName_forTwoSegmentPath() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("cardano/Credential", schema);

            assertThat(result).isEqualTo("Credential");
        }
    }
}
