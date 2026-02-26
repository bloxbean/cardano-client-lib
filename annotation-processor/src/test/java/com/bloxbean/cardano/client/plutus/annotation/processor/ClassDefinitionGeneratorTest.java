package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClassDefinitionGenerator}'s type detection logic
 * ({@code isDataType()} and {@code isRawDataType()}) by compiling a model
 * with fields of each shared-type family and inspecting the generated converter source.
 *
 * <p>The test resource {@code SharedTypeFieldModel.java} has three fields:
 * <ul>
 *   <li>{@code Address address}       — implements {@code Data<T>} → dataType</li>
 *   <li>{@code VerificationKeyHash vkh} — implements {@code RawData} → rawDataType</li>
 *   <li>{@code Model2 nested}          — {@code @Constr} class → regular (converter-delegated)</li>
 * </ul>
 */
@DisplayName("ClassDefinitionGenerator — shared type detection")
public class ClassDefinitionGeneratorTest {

    private static String converterSource;

    @BeforeAll
    static void compileModel() throws IOException {
        Compilation compilation = javac()
                .withProcessors(new ConstrAnnotationProcessor())
                .compile(
                        JavaFileObjects.forResource("SharedTypeFieldModel.java"),
                        JavaFileObjects.forResource("Model2.java"));

        assertThat(compilation).succeeded();

        JavaFileObject converter = compilation.generatedSourceFiles().stream()
                .filter(f -> f.getName().contains("SharedTypeFieldModelConverter"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SharedTypeFieldModelConverter not generated"));

        converterSource = converter.getCharContent(true).toString();
    }

    @Nested
    @DisplayName("isDataType() — Data<T> interface detection")
    class DataTypeDetection {

        @Test
        @DisplayName("should generate inline toPlutusData() for Data<T> field (Address)")
        void dataTypeField_shouldGenerateInlineToPlutusData() {
            // Data<T> shared types serialize via obj.getField().toPlutusData() — not through a converter
            assertThat(converterSource).contains("obj.getAddress().toPlutusData()");
        }

        @Test
        @DisplayName("should deserialize Data<T> field with ConstrPlutusData cast")
        void dataTypeField_shouldDeserializeWithConstrCast() {
            // Data<T> types are constr-based, so deserialization casts to ConstrPlutusData
            assertThat(converterSource).contains("Address.fromPlutusData(((ConstrPlutusData)");
        }

        @Test
        @DisplayName("should NOT delegate Data<T> field through a generated converter")
        void dataTypeField_shouldNotUseConverter() {
            assertThat(converterSource).doesNotContain("AddressConverter");
        }
    }

    @Nested
    @DisplayName("isRawDataType() — RawData interface detection")
    class RawDataTypeDetection {

        @Test
        @DisplayName("should generate inline toPlutusData() for RawData field (VerificationKeyHash)")
        void rawDataTypeField_shouldGenerateInlineToPlutusData() {
            // RawData shared types also serialize inline
            assertThat(converterSource).contains("obj.getVkh().toPlutusData()");
        }

        @Test
        @DisplayName("should deserialize RawData field WITHOUT ConstrPlutusData cast")
        void rawDataTypeField_shouldDeserializeWithoutConstrCast() {
            // RawData types use raw PlutusData — no ConstrPlutusData cast
            assertThat(converterSource).contains("VerificationKeyHash.fromPlutusData(data.getPlutusDataList()");
            assertThat(converterSource).doesNotContain("VerificationKeyHash.fromPlutusData((ConstrPlutusData)");
        }

        @Test
        @DisplayName("should NOT delegate RawData field through a generated converter")
        void rawDataTypeField_shouldNotUseConverter() {
            assertThat(converterSource).doesNotContain("VerificationKeyHashConverter");
        }
    }

    @Nested
    @DisplayName("Regular @Constr types — neither Data<T> nor RawData")
    class RegularConstrType {

        @Test
        @DisplayName("should delegate @Constr field through generated converter for serialization")
        void constrTypeField_shouldUsConverterForSerialization() {
            assertThat(converterSource).contains("Model2Converter().toPlutusData(obj.getNested())");
        }

        @Test
        @DisplayName("should delegate @Constr field through generated converter for deserialization with ConstrPlutusData cast")
        void constrTypeField_shouldUseConverterForDeserialization() {
            assertThat(converterSource).contains("Model2Converter().fromPlutusData(((ConstrPlutusData)");
        }

        @Test
        @DisplayName("should NOT inline toPlutusData() for @Constr field")
        void constrTypeField_shouldNotInlineToPlutusData() {
            assertThat(converterSource).doesNotContain("obj.getNested().toPlutusData()");
        }
    }
}
