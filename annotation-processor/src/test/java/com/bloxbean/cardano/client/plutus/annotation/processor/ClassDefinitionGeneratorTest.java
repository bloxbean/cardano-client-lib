package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.JavaType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.ClassName;
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
            assertThat(converterSource).contains("VerificationKeyHash.fromPlutusData(constrData.getPlutusDataList()");
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

    /**
     * Tests for parameterized tuple types (Pair, Triple, Quartet, Quintet).
     *
     * <p>Verifies that {@code detectFieldType()} recognizes parameterized tuple containers
     * and generates inline {@code ListPlutusData} serialization using getter accessors
     * ({@code getFirst()}, {@code getSecond()}, etc.) — NOT {@code toPlutusData()} calls.</p>
     */
    @Nested
    @DisplayName("Parameterized tuple types — Pair, Triple, Quartet, Quintet")
    class TupleTypeDetection {

        private static String tupleConverterSource;

        @BeforeAll
        static void compileTupleModel() throws IOException {
            Compilation compilation = javac()
                    .withProcessors(new ConstrAnnotationProcessor())
                    .compile(JavaFileObjects.forResource("TupleFieldModel.java"));

            assertThat(compilation).succeeded();

            JavaFileObject converter = compilation.generatedSourceFiles().stream()
                    .filter(f -> f.getName().contains("TupleFieldModelConverter"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("TupleFieldModelConverter not generated"));

            tupleConverterSource = converter.getCharContent(true).toString();
        }

        @Test
        @DisplayName("Pair<byte[], byte[]> should generate inline ListPlutusData with getFirst()/getSecond()")
        void pairField_shouldGenerateInlineListPlutusData() {
            assertThat(tupleConverterSource).contains("obj.getPairField().getFirst()");
            assertThat(tupleConverterSource).contains("obj.getPairField().getSecond()");
            assertThat(tupleConverterSource).contains("pairFieldPair.add(");
        }

        @Test
        @DisplayName("Pair field should NOT call toPlutusData() on the Pair itself")
        void pairField_shouldNotCallToPlutusData() {
            assertThat(tupleConverterSource).doesNotContain("obj.getPairField().toPlutusData()");
        }

        @Test
        @DisplayName("Triple<byte[], BigInteger, String> should generate inline serialization with getFirst()/getSecond()/getThird()")
        void tripleField_shouldGenerateInlineSerialization() {
            assertThat(tupleConverterSource).contains("obj.getTripleField().getFirst()");
            assertThat(tupleConverterSource).contains("obj.getTripleField().getSecond()");
            assertThat(tupleConverterSource).contains("obj.getTripleField().getThird()");
        }

        @Test
        @DisplayName("Triple field should NOT call toPlutusData() on the Triple itself")
        void tripleField_shouldNotCallToPlutusData() {
            assertThat(tupleConverterSource).doesNotContain("obj.getTripleField().toPlutusData()");
        }

        @Test
        @DisplayName("Quartet should generate inline serialization with getFourth()")
        void quartetField_shouldGenerateInlineSerialization() {
            assertThat(tupleConverterSource).contains("obj.getQuartetField().getFirst()");
            assertThat(tupleConverterSource).contains("obj.getQuartetField().getFourth()");
        }

        @Test
        @DisplayName("Quartet field should NOT call toPlutusData() on the Quartet itself")
        void quartetField_shouldNotCallToPlutusData() {
            assertThat(tupleConverterSource).doesNotContain("obj.getQuartetField().toPlutusData()");
        }

        @Test
        @DisplayName("Quintet should generate inline serialization with getFifth()")
        void quintetField_shouldGenerateInlineSerialization() {
            assertThat(tupleConverterSource).contains("obj.getQuintetField().getFirst()");
            assertThat(tupleConverterSource).contains("obj.getQuintetField().getFifth()");
        }

        @Test
        @DisplayName("Quintet field should NOT call toPlutusData() on the Quintet itself")
        void quintetField_shouldNotCallToPlutusData() {
            assertThat(tupleConverterSource).doesNotContain("obj.getQuintetField().toPlutusData()");
        }
    }

    /**
     * Tests for {@link ClassDefinitionGenerator#getConverterClassFromField(FieldType)}.
     *
     * <p>This static method generates converter class names from field types.
     * After commit 155bf39a, it uses {@code String.join("", fieldClass.simpleNames())}
     * to handle nested classes correctly.</p>
     */
    @Nested
    @DisplayName("getConverterClassFromField() — nested class converter naming")
    class GetConverterClassFromField {

        @Test
        @DisplayName("top-level class should produce simple converter name")
        void topLevelClass_shouldProduceSimpleConverterName() {
            FieldType fieldType = new FieldType();
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType("com.example.Address", true));

            ClassName result = ClassDefinitionGenerator.getConverterClassFromField(fieldType);

            assertThat(result.packageName()).isEqualTo("com.example.converter");
            assertThat(result.simpleName()).isEqualTo("AddressConverter");
        }

        @Test
        @DisplayName("nested class should produce nested converter in parent interface")
        void nestedClass_shouldProduceNestedConverterName() {
            // Credential.VerificationKey → Credential.VerificationKeyConverter (nested)
            FieldType fieldType = new FieldType();
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType("com.example.Credential.VerificationKey", true));

            ClassName result = ClassDefinitionGenerator.getConverterClassFromField(fieldType);

            assertThat(result.packageName()).isEqualTo("com.example");
            assertThat(result.simpleName()).isEqualTo("VerificationKeyConverter");
            assertThat(result.enclosingClassName().simpleName()).isEqualTo("Credential");
        }

        @Test
        @DisplayName("deeply nested class should produce converter nested in outermost parent")
        void deeplyNestedClass_shouldProduceNestedConverterInOutermostParent() {
            // A.B.C → A.CConverter (nested in outermost)
            FieldType fieldType = new FieldType();
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType("com.example.A.B.C", true));

            ClassName result = ClassDefinitionGenerator.getConverterClassFromField(fieldType);

            assertThat(result.packageName()).isEqualTo("com.example");
            assertThat(result.simpleName()).isEqualTo("CConverter");
            assertThat(result.enclosingClassName().simpleName()).isEqualTo("A");
        }
    }
}
