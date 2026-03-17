package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.function.Consumer;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetadataFieldValidator}.
 * Uses compile-testing to get a real {@code ProcessingEnvironment}.
 */
class MetadataFieldValidatorTest {

    // ── Test harness ────────────────────────────────────────────────────

    record ValidateResult(Compilation compilation, MetadataFieldValidator.MetadataKeyAndEncoding keyEnc) {}

    private void validateAndAssert(Consumer<ValidateResult> assertions, String source) {
        validateAndAssert(assertions, JavaFileObjects.forSourceString("com.test.TestClass", source));
    }

    private void validateAndAssert(Consumer<ValidateResult> assertions, JavaFileObject... sources) {
        ValidatorCapturingProcessor processor = new ValidatorCapturingProcessor();
        Compilation compilation = javac()
                .withProcessors(processor)
                .compile(sources);
        assertions.accept(new ValidateResult(compilation, processor.keyEnc));
    }

    @SupportedAnnotationTypes("com.bloxbean.cardano.client.metadata.annotation.MetadataType")
    @SupportedSourceVersion(SourceVersion.RELEASE_17)
    static class ValidatorCapturingProcessor extends AbstractProcessor {
        MetadataFieldValidator.MetadataKeyAndEncoding keyEnc;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    if (!(element instanceof TypeElement typeElement)) continue;
                    MetadataFieldValidator validator = new MetadataFieldValidator(processingEnv.getMessager());

                    for (Element enclosed : typeElement.getEnclosedElements()) {
                        if (!(enclosed instanceof VariableElement ve)) continue;
                        if (enclosed.getKind() != ElementKind.FIELD) continue;
                        if (ve.getSimpleName().toString().startsWith("$")) continue;

                        String fieldName = ve.getSimpleName().toString();
                        String typeName = ve.asType().toString();
                        keyEnc = validator.resolveMetadataKeyAndEncoding(
                                ve, fieldName, typeName, false, false, false, null);
                        return true;
                    }
                }
            }
            return true;
        }
    }

    // ── Key resolution ──────────────────────────────────────────────────

    @Nested
    class KeyResolution {

        @Test
        void defaultKeyIsFieldName() {
            validateAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.keyEnc).isNotNull();
                assertThat(r.keyEnc.metadataKey()).isEqualTo("name");
                assertThat(r.keyEnc.enc()).isEqualTo(MetadataFieldType.DEFAULT);
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void customKeyOverridesFieldName() {
            validateAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.keyEnc).isNotNull();
                assertThat(r.keyEnc.metadataKey()).isEqualTo("display_name");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                @MetadataType
                public class TestClass {
                    @MetadataField(key = "display_name")
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }
    }

    // ── Required and default value ──────────────────────────────────────

    @Nested
    class RequiredAndDefault {

        @Test
        void requiredFieldIsMarked() {
            validateAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.keyEnc).isNotNull();
                assertThat(r.keyEnc.required()).isTrue();
                assertThat(r.keyEnc.defaultValue()).isNull();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                @MetadataType
                public class TestClass {
                    @MetadataField(required = true)
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void defaultValueIsCaputred() {
            validateAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.keyEnc).isNotNull();
                assertThat(r.keyEnc.required()).isFalse();
                assertThat(r.keyEnc.defaultValue()).isEqualTo("unknown");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                @MetadataType
                public class TestClass {
                    @MetadataField(defaultValue = "unknown")
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void requiredAndDefaultAreMutuallyExclusive() {
            validateAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("'required' and 'defaultValue' are mutually exclusive");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                @MetadataType
                public class TestClass {
                    @MetadataField(required = true, defaultValue = "x")
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }
    }

    // ── Encoding validation ─────────────────────────────────────────────

    @Nested
    class EncodingValidation {

        @Test
        void stringEncIsValid() {
            validateAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.keyEnc).isNotNull();
                assertThat(r.keyEnc.enc()).isEqualTo(MetadataFieldType.STRING);
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING)
                    private int count;
                    public int getCount() { return count; }
                    public void setCount(int c) { this.count = c; }
                }
            """);
        }

        @Test
        void hexEncOnByteArrayIsValid() {
            validateAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.keyEnc).isNotNull();
                assertThat(r.keyEnc.enc()).isEqualTo(MetadataFieldType.STRING_HEX);
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING_HEX)
                    private byte[] data;
                    public byte[] getData() { return data; }
                    public void setData(byte[] d) { this.data = d; }
                }
            """);
        }

        @Test
        void hexEncOnStringFails() {
            validateAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("only valid for byte[] fields");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING_HEX)
                    private String name;
                    public String getName() { return name; }
                    public void setName(String n) { this.name = n; }
                }
            """);
        }

        @Test
        void stringEncOnByteArrayFails() {
            validateAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("ambiguous for byte[]");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING)
                    private byte[] data;
                    public byte[] getData() { return data; }
                    public void setData(byte[] d) { this.data = d; }
                }
            """);
        }
    }
}
