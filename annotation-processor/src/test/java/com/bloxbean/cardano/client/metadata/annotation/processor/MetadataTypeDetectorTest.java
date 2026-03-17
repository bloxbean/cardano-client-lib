package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.function.Consumer;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetadataTypeDetector}.
 * Uses compile-testing to get a real {@code ProcessingEnvironment}.
 */
class MetadataTypeDetectorTest {

    // ── Test harness ────────────────────────────────────────────────────

    record DetectResult(Compilation compilation, MetadataTypeDetector.FieldTypeResult typeResult,
                        MetadataTypeDetector.AdapterDetectionResult adapterResult) {}

    private void detectAndAssert(Consumer<DetectResult> assertions, String source) {
        detectAndAssert(assertions, JavaFileObjects.forSourceString("com.test.TestClass", source));
    }

    private void detectAndAssert(Consumer<DetectResult> assertions, String source, JavaFileObject... extras) {
        JavaFileObject[] all = new JavaFileObject[extras.length + 1];
        all[0] = JavaFileObjects.forSourceString("com.test.TestClass", source);
        System.arraycopy(extras, 0, all, 1, extras.length);
        detectAndAssert(assertions, all);
    }

    private void detectAndAssert(Consumer<DetectResult> assertions, JavaFileObject... sources) {
        TypeDetectorCapturingProcessor processor = new TypeDetectorCapturingProcessor();
        Compilation compilation = javac()
                .withProcessors(processor)
                .compile(sources);
        assertions.accept(new DetectResult(compilation, processor.typeResult, processor.adapterResult));
    }

    @SupportedAnnotationTypes("com.bloxbean.cardano.client.metadata.annotation.MetadataType")
    @SupportedSourceVersion(SourceVersion.RELEASE_17)
    static class TypeDetectorCapturingProcessor extends AbstractProcessor {
        MetadataTypeDetector.FieldTypeResult typeResult;
        MetadataTypeDetector.AdapterDetectionResult adapterResult;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    if (!(element instanceof TypeElement typeElement)) continue;
                    MetadataTypeDetector detector = new MetadataTypeDetector(processingEnv, processingEnv.getMessager());

                    for (Element enclosed : typeElement.getEnclosedElements()) {
                        if (!(enclosed instanceof VariableElement ve)) continue;
                        if (enclosed.getKind() == ElementKind.FIELD && !ve.getSimpleName().toString().startsWith("$")) {
                            String fieldName = ve.getSimpleName().toString();
                            String typeName = ve.asType().toString();
                            adapterResult = detector.detectAdapter(ve, fieldName);
                            if (adapterResult == null) {
                                typeResult = detector.detectFieldType(ve, fieldName, typeName);
                            }
                            return true;
                        }
                    }
                }
            }
            return true;
        }
    }

    // ── Scalar detection ────────────────────────────────────────────────

    @Nested
    class ScalarDetection {

        @Test
        void detectsStringField() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.enumType()).isFalse();
                assertThat(r.typeResult.nestedType()).isFalse();
                assertThat(r.typeResult.collectionType()).isFalse();
                assertThat(r.typeResult.mapType()).isFalse();
                assertThat(r.typeResult.optionalType()).isFalse();
                assertThat(r.typeResult.polymorphicType()).isFalse();
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
        void detectsBigIntegerField() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.enumType()).isFalse();
                assertThat(r.typeResult.mapType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.math.BigInteger;
                @MetadataType
                public class TestClass {
                    private BigInteger amount;
                    public BigInteger getAmount() { return amount; }
                    public void setAmount(BigInteger amount) { this.amount = amount; }
                }
            """);
        }

        @Test
        void detectsBooleanField() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.enumType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private boolean active;
                    public boolean isActive() { return active; }
                    public void setActive(boolean active) { this.active = active; }
                }
            """);
        }

        @Test
        void unsupportedTypeReturnsNull() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNull();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private Thread unsupported;
                    public Thread getUnsupported() { return unsupported; }
                    public void setUnsupported(Thread t) { this.unsupported = t; }
                }
            """);
        }
    }

    // ── Enum detection ──────────────────────────────────────────────────

    @Nested
    class EnumDetection {

        @Test
        void detectsEnumField() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.enumType()).isTrue();
                assertThat(r.typeResult.nestedType()).isFalse();
                assertThat(r.typeResult.collectionType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private Status status;
                    public Status getStatus() { return status; }
                    public void setStatus(Status s) { this.status = s; }
                }
            """,
            JavaFileObjects.forSourceString("com.test.Status", """
                package com.test;
                public enum Status { ACTIVE, INACTIVE }
            """));
        }
    }

    // ── Collection detection ────────────────────────────────────────────

    @Nested
    class CollectionDetection {

        @Test
        void detectsListOfStrings() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.collectionType()).isTrue();
                assertThat(r.typeResult.collectionKind()).isEqualTo("java.util.List");
                assertThat(r.typeResult.elementTypeName()).isEqualTo("java.lang.String");
                assertThat(r.typeResult.mapType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private List<String> tags;
                    public List<String> getTags() { return tags; }
                    public void setTags(List<String> tags) { this.tags = tags; }
                }
            """);
        }

        @Test
        void detectsSetOfIntegers() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.collectionType()).isTrue();
                assertThat(r.typeResult.collectionKind()).isEqualTo("java.util.Set");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Set;
                @MetadataType
                public class TestClass {
                    private Set<Integer> ids;
                    public Set<Integer> getIds() { return ids; }
                    public void setIds(Set<Integer> ids) { this.ids = ids; }
                }
            """);
        }
    }

    // ── Map detection ───────────────────────────────────────────────────

    @Nested
    class MapDetection {

        @Test
        void detectsMapStringString() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.mapType()).isTrue();
                assertThat(r.typeResult.mapKeyTypeName()).isEqualTo("java.lang.String");
                assertThat(r.typeResult.mapValueTypeName()).isEqualTo("java.lang.String");
                assertThat(r.typeResult.mapValueEnumType()).isFalse();
                assertThat(r.typeResult.mapValueNestedType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<String, String> attrs;
                    public Map<String, String> getAttrs() { return attrs; }
                    public void setAttrs(Map<String, String> a) { this.attrs = a; }
                }
            """);
        }

        @Test
        void rejectsUnsupportedMapKeyType() {
            detectAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("Map key type must be");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<Double, String> bad;
                    public Map<Double, String> getBad() { return bad; }
                    public void setBad(Map<Double, String> b) { this.bad = b; }
                }
            """);
        }
    }

    // ── Optional detection ──────────────────────────────────────────────

    @Nested
    class OptionalDetection {

        @Test
        void detectsOptionalString() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.optionalType()).isTrue();
                assertThat(r.typeResult.elementTypeName()).isEqualTo("java.lang.String");
                assertThat(r.typeResult.collectionType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Optional;
                @MetadataType
                public class TestClass {
                    private Optional<String> desc;
                    public Optional<String> getDesc() { return desc; }
                    public void setDesc(Optional<String> d) { this.desc = d; }
                }
            """);
        }
    }

    // ── Nested type detection ───────────────────────────────────────────

    @Nested
    class NestedTypeDetection {

        @Test
        void detectsNestedMetadataType() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.nestedType()).isTrue();
                assertThat(r.typeResult.nestedConverterFqn()).endsWith("InnerMetadataConverter");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private Inner nested;
                    public Inner getNested() { return nested; }
                    public void setNested(Inner n) { this.nested = n; }
                }
            """,
            JavaFileObjects.forSourceString("com.test.Inner", """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class Inner {
                    private String value;
                    public String getValue() { return value; }
                    public void setValue(String v) { this.value = v; }
                }
            """));
        }
    }

    // ── Adapter detection ───────────────────────────────────────────────

    @Nested
    class AdapterDetection {

        @Test
        void detectsAdapterField() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.adapterResult).isNotNull();
                assertThat(r.adapterResult.adapterFqn()).isEqualTo("com.test.MyAdapter");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                @MetadataType
                public class TestClass {
                    @MetadataField(adapter = MyAdapter.class)
                    private java.time.Instant ts;
                    public java.time.Instant getTs() { return ts; }
                    public void setTs(java.time.Instant ts) { this.ts = ts; }
                }
            """,
            JavaFileObjects.forSourceString("com.test.MyAdapter", """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;
                import java.math.BigInteger;
                import java.time.Instant;
                public class MyAdapter implements MetadataTypeAdapter<Instant> {
                    @Override public Object toMetadata(Instant value) { return BigInteger.valueOf(value.getEpochSecond()); }
                    @Override public Instant fromMetadata(Object metadata) { return Instant.ofEpochSecond(((BigInteger)metadata).longValue()); }
                }
            """));
        }

        @Test
        void noAdapterReturnsNull() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.adapterResult).isNull();
                assertThat(r.typeResult).isNotNull();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String plain;
                    public String getPlain() { return plain; }
                    public void setPlain(String p) { this.plain = p; }
                }
            """);
        }

        @Test
        void adapterWithDefaultValueFails() {
            detectAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("'adapter' and 'defaultValue' are mutually exclusive");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
                @MetadataType
                public class TestClass {
                    @MetadataField(adapter = MyAdapter.class, defaultValue = "123")
                    private java.time.Instant ts;
                    public java.time.Instant getTs() { return ts; }
                    public void setTs(java.time.Instant ts) { this.ts = ts; }
                }
            """,
            JavaFileObjects.forSourceString("com.test.MyAdapter", """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;
                import java.math.BigInteger;
                import java.time.Instant;
                public class MyAdapter implements MetadataTypeAdapter<Instant> {
                    @Override public Object toMetadata(Instant value) { return BigInteger.valueOf(value.getEpochSecond()); }
                    @Override public Instant fromMetadata(Object metadata) { return Instant.ofEpochSecond(((BigInteger)metadata).longValue()); }
                }
            """));
        }
    }

    // ── Leaf type classification ─────────────────────────────────────────

    @Nested
    class LeafClassification {

        @Test
        void classifiesScalarType() {
            // Test via static method — no ProcessingEnvironment needed for scalar check
            MetadataTypeDetector detector = null;
            // We need a ProcessingEnvironment, so test through compile-testing
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.collectionType()).isTrue();
                assertThat(r.typeResult.elementEnumType()).isFalse();
                assertThat(r.typeResult.elementNestedType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private List<String> items;
                    public List<String> getItems() { return items; }
                    public void setItems(List<String> items) { this.items = items; }
                }
            """);
        }

        @Test
        void classifiesEnumElement() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.collectionType()).isTrue();
                assertThat(r.typeResult.elementEnumType()).isTrue();
                assertThat(r.typeResult.elementNestedType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private List<Status> statuses;
                    public List<Status> getStatuses() { return statuses; }
                    public void setStatuses(List<Status> s) { this.statuses = s; }
                }
            """,
            JavaFileObjects.forSourceString("com.test.Status", """
                package com.test;
                public enum Status { ON, OFF }
            """));
        }

        @Test
        void classifiesNestedElement() {
            detectAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.typeResult).isNotNull();
                assertThat(r.typeResult.collectionType()).isTrue();
                assertThat(r.typeResult.elementNestedType()).isTrue();
                assertThat(r.typeResult.elementEnumType()).isFalse();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private List<Inner> items;
                    public List<Inner> getItems() { return items; }
                    public void setItems(List<Inner> items) { this.items = items; }
                }
            """,
            JavaFileObjects.forSourceString("com.test.Inner", """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class Inner {
                    private String v;
                    public String getV() { return v; }
                    public void setV(String v) { this.v = v; }
                }
            """));
        }
    }
}
