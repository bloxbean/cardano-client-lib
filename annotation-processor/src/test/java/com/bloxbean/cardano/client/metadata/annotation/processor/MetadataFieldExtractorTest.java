package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.function.Consumer;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetadataFieldExtractor}.
 * Uses compile-testing to get a real {@code ProcessingEnvironment}.
 */
public class MetadataFieldExtractorTest {

    // ── Test harness ────────────────────────────────────────────────────

    /**
     * Compiles the given source(s) with a processor that captures extracted fields,
     * then runs assertions on the result.
     */
    private void extractAndAssert(Consumer<ExtractResult> assertions, JavaFileObject... sources) {
        CapturingProcessor processor = new CapturingProcessor();
        Compilation compilation = javac()
                .withProcessors(processor)
                .compile(sources);

        // Print diagnostics for debugging
        compilation.diagnostics().forEach(d -> System.out.println(d.getKind() + ": " + d.getMessage(null)));

        assertions.accept(new ExtractResult(compilation, processor.capturedFields, processor.hasLombok));
    }

    private void extractAndAssert(Consumer<ExtractResult> assertions, String source) {
        extractAndAssert(assertions, JavaFileObjects.forSourceString("com.test.TestClass", source));
    }

    record ExtractResult(Compilation compilation, List<MetadataFieldInfo> fields, boolean hasLombok) {}

    /**
     * A processor that captures the output of {@link MetadataFieldExtractor}
     * instead of generating code.
     */
    @SupportedAnnotationTypes("com.bloxbean.cardano.client.metadata.annotation.MetadataType")
    @SupportedSourceVersion(SourceVersion.RELEASE_17)
    static class CapturingProcessor extends AbstractProcessor {
        List<MetadataFieldInfo> capturedFields = new ArrayList<>();
        boolean hasLombok;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    if (!(element instanceof TypeElement typeElement)) continue;
                    MetadataFieldExtractor extractor = new MetadataFieldExtractor(processingEnv, processingEnv.getMessager());
                    hasLombok = extractor.detectLombok(typeElement);
                    extractor.validateNoArgConstructor(typeElement, hasLombok);
                    capturedFields.addAll(extractor.extractFields(typeElement, hasLombok));
                }
            }
            return true;
        }
    }

    // ── Scalar fields ───────────────────────────────────────────────────

    @Nested
    class ScalarFields {

        @Test
        void extractsStringField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertEquals("name", f.getJavaFieldName());
                assertEquals("name", f.getMetadataKey());
                assertEquals("java.lang.String", f.getJavaTypeName());
                assertEquals("getName", f.getGetterName());
                assertEquals("setName", f.getSetterName());
                assertFalse(f.isEnumType());
                assertFalse(f.isNestedType());
                assertFalse(f.isMapType());
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
        void extractsMultipleScalarTypes() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(3, r.fields.size());

                assertEquals("java.lang.String", r.fields.get(0).getJavaTypeName());
                assertEquals("java.math.BigInteger", r.fields.get(1).getJavaTypeName());
                assertEquals("long", r.fields.get(2).getJavaTypeName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.math.BigInteger;
                @MetadataType
                public class TestClass {
                    private String name;
                    private BigInteger amount;
                    private long timestamp;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    public BigInteger getAmount() { return amount; }
                    public void setAmount(BigInteger amount) { this.amount = amount; }
                    public long getTimestamp() { return timestamp; }
                    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
                }
            """);
        }

        @Test
        void extractsPublicFieldWithoutAccessors() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertEquals("name", f.getJavaFieldName());
                assertNull(f.getGetterName());
                assertNull(f.getSetterName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public String name;
                }
            """);
        }

        @Test
        void extractsBooleanFieldWithIsGetter() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals("isActive", r.fields.get(0).getGetterName());
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
    }

    // ── @MetadataField key and encoding ─────────────────────────────────

    @Nested
    class MetadataKeyAndEncoding {

        @Test
        void customMetadataKey() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals("referenceId", r.fields.get(0).getJavaFieldName());
                assertEquals("ref_id", r.fields.get(0).getMetadataKey());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    @MetadataField(key = "ref_id")
                    private String referenceId;
                    public String getReferenceId() { return referenceId; }
                    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
                }
            """);
        }

        @Test
        void stringEncoding() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals(MetadataFieldType.STRING, r.fields.get(0).getEnc());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING)
                    private Integer statusCode;
                    public Integer getStatusCode() { return statusCode; }
                    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
                }
            """);
        }

        @Test
        void stringHexEncodingForByteArray() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals(MetadataFieldType.STRING_HEX, r.fields.get(0).getEnc());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING_HEX)
                    private byte[] payload;
                    public byte[] getPayload() { return payload; }
                    public void setPayload(byte[] payload) { this.payload = payload; }
                }
            """);
        }

        @Test
        void stringHexOnNonByteArrayEmitsError() {
            extractAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("enc=STRING_HEX");
                assertThat(r.compilation).hadErrorContaining("only valid for byte[]");
                assertEquals(0, r.fields.size());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING_HEX)
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void stringEncOnByteArrayEmitsError() {
            extractAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("enc=STRING");
                assertThat(r.compilation).hadErrorContaining("ambiguous for byte[]");
                assertEquals(0, r.fields.size());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING)
                    private byte[] payload;
                    public byte[] getPayload() { return payload; }
                    public void setPayload(byte[] payload) { this.payload = payload; }
                }
            """);
        }

        @Test
        void encOnCollectionFieldResetToDefault() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.compilation).hadWarningContaining("enc=...) is not supported");
                assertEquals(1, r.fields.size());
                assertEquals(MetadataFieldType.DEFAULT, r.fields.get(0).getEnc());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING)
                    private java.util.List<String> tags;
                    public java.util.List<String> getTags() { return tags; }
                    public void setTags(java.util.List<String> tags) { this.tags = tags; }
                }
            """);
        }
    }

    // ── @MetadataIgnore and static fields ───────────────────────────────

    @Nested
    class FieldSkipping {

        @Test
        void skipsIgnoredField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals("name", r.fields.get(0).getJavaFieldName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    private String name;
                    @MetadataIgnore
                    private String internalId;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    public String getInternalId() { return internalId; }
                    public void setInternalId(String internalId) { this.internalId = internalId; }
                }
            """);
        }

        @Test
        void skipsStaticField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals("name", r.fields.get(0).getJavaFieldName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private static final String CONSTANT = "hello";
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void skipsUnsupportedType() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals("name", r.fields.get(0).getJavaFieldName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String name;
                    private Object unsupported;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    public Object getUnsupported() { return unsupported; }
                    public void setUnsupported(Object unsupported) { this.unsupported = unsupported; }
                }
            """);
        }

        @Test
        void skipsPrivateFieldWithoutAccessor() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals("name", r.fields.get(0).getJavaFieldName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String name;
                    private String secret;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    // no getter/setter for 'secret'
                }
            """);
        }
    }

    // ── Enum fields ────────────────────────────────────────────────────

    @Nested
    class EnumFields {

        @Test
        void detectsEnumField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isEnumType());
                assertEquals("com.test.Status", f.getJavaTypeName());
            },
                JavaFileObjects.forSourceString("com.test.Status", """
                    package com.test;
                    public enum Status { ACTIVE, INACTIVE }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class TestClass {
                        private Status status;
                        public Status getStatus() { return status; }
                        public void setStatus(Status status) { this.status = status; }
                    }
                """)
            );
        }
    }

    // ── Collection fields ──────────────────────────────────────────────

    @Nested
    class CollectionFields {

        @Test
        void extractsListField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertEquals("java.lang.String", f.getElementTypeName());
                assertTrue(f.getJavaTypeName().startsWith(COLLECTION_LIST));
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
        void extractsSetField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertEquals("java.lang.String", f.getElementTypeName());
                assertTrue(f.getJavaTypeName().startsWith(COLLECTION_SET));
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Set;
                @MetadataType
                public class TestClass {
                    private Set<String> tags;
                    public Set<String> getTags() { return tags; }
                    public void setTags(Set<String> tags) { this.tags = tags; }
                }
            """);
        }

        @Test
        void extractsOptionalField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertEquals("java.lang.String", f.getElementTypeName());
                assertTrue(f.getJavaTypeName().startsWith("java.util.Optional"));
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Optional;
                @MetadataType
                public class TestClass {
                    private Optional<String> nickname;
                    public Optional<String> getNickname() { return nickname; }
                    public void setNickname(Optional<String> nickname) { this.nickname = nickname; }
                }
            """);
        }

        @Test
        void extractsListOfEnums() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isElementEnumType());
                assertEquals("com.test.Color", f.getElementTypeName());
            },
                JavaFileObjects.forSourceString("com.test.Color", """
                    package com.test;
                    public enum Color { RED, GREEN, BLUE }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.List;
                    @MetadataType
                    public class TestClass {
                        private List<Color> colors;
                        public List<Color> getColors() { return colors; }
                        public void setColors(List<Color> colors) { this.colors = colors; }
                    }
                """)
            );
        }
    }

    // ── Nested @MetadataType fields ────────────────────────────────────

    @Nested
    class NestedTypeFields {

        @Test
        void detectsNestedType() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                // Both @MetadataType classes are processed; find the field from TestClass
                MetadataFieldInfo f = r.fields.stream()
                        .filter(fi -> fi.getJavaFieldName().equals("address"))
                        .findFirst().orElseThrow();
                assertTrue(f.isNestedType());
                assertEquals("com.test.AddressMetadataConverter", f.getNestedConverterFqn());
            },
                JavaFileObjects.forSourceString("com.test.Address", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class Address {
                        public String street;
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class TestClass {
                        private Address address;
                        public Address getAddress() { return address; }
                        public void setAddress(Address address) { this.address = address; }
                    }
                """)
            );
        }

        @Test
        void detectsListOfNestedType() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                MetadataFieldInfo f = r.fields.stream()
                        .filter(fi -> fi.getJavaFieldName().equals("items"))
                        .findFirst().orElseThrow();
                assertTrue(f.isElementNestedType());
                assertEquals("com.test.ItemMetadataConverter", f.getNestedConverterFqn());
            },
                JavaFileObjects.forSourceString("com.test.Item", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class Item {
                        public String name;
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.List;
                    @MetadataType
                    public class TestClass {
                        private List<Item> items;
                        public List<Item> getItems() { return items; }
                        public void setItems(List<Item> items) { this.items = items; }
                    }
                """)
            );
        }
    }

    // ── Map<String, V> fields ──────────────────────────────────────────

    @Nested
    class MapFields {

        @Test
        void detectsMapStringString() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertEquals("java.lang.String", f.getMapKeyTypeName());
                assertEquals("java.lang.String", f.getMapValueTypeName());
                assertFalse(f.isMapValueEnumType());
                assertFalse(f.isMapValueNestedType());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<String, String> metadata;
                    public Map<String, String> getMetadata() { return metadata; }
                    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
                }
            """);
        }

        @Test
        void detectsMapWithEnumValue() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertTrue(f.isMapValueEnumType());
            },
                JavaFileObjects.forSourceString("com.test.Priority", """
                    package com.test;
                    public enum Priority { LOW, MEDIUM, HIGH }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.Map;
                    @MetadataType
                    public class TestClass {
                        private Map<String, Priority> priorities;
                        public Map<String, Priority> getPriorities() { return priorities; }
                        public void setPriorities(Map<String, Priority> priorities) { this.priorities = priorities; }
                    }
                """)
            );
        }

        @Test
        void detectsMapWithNestedValue() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                MetadataFieldInfo f = r.fields.stream()
                        .filter(fi -> fi.getJavaFieldName().equals("configs"))
                        .findFirst().orElseThrow();
                assertTrue(f.isMapType());
                assertTrue(f.isMapValueNestedType());
                assertEquals("com.test.ConfigMetadataConverter", f.getMapValueConverterFqn());
            },
                JavaFileObjects.forSourceString("com.test.Config", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class Config {
                        public String value;
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.Map;
                    @MetadataType
                    public class TestClass {
                        private Map<String, Config> configs;
                        public Map<String, Config> getConfigs() { return configs; }
                        public void setConfigs(Map<String, Config> configs) { this.configs = configs; }
                    }
                """)
            );
        }

        @Test
        void unsupportedMapKeyEmitsError() {
            extractAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("Map key type must be String, Integer, Long, BigInteger, or byte[]");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<Double, String> lookup;
                    public Map<Double, String> getLookup() { return lookup; }
                    public void setLookup(Map<Double, String> lookup) { this.lookup = lookup; }
                }
            """);
        }

        @Test
        void integerMapKeyExtractsSuccessfully() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertEquals("java.lang.Integer", f.getMapKeyTypeName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<Integer, String> lookup;
                    public Map<Integer, String> getLookup() { return lookup; }
                    public void setLookup(Map<Integer, String> lookup) { this.lookup = lookup; }
                }
            """);
        }

        @Test
        void longMapKeyExtractsSuccessfully() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertEquals("java.lang.Long", f.getMapKeyTypeName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<Long, String> lookup;
                    public Map<Long, String> getLookup() { return lookup; }
                    public void setLookup(Map<Long, String> lookup) { this.lookup = lookup; }
                }
            """);
        }

        @Test
        void bigIntegerMapKeyExtractsSuccessfully() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertEquals("java.math.BigInteger", f.getMapKeyTypeName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                import java.math.BigInteger;
                @MetadataType
                public class TestClass {
                    private Map<BigInteger, String> lookup;
                    public Map<BigInteger, String> getLookup() { return lookup; }
                    public void setLookup(Map<BigInteger, String> lookup) { this.lookup = lookup; }
                }
            """);
        }

        @Test
        void unsupportedMapValueTypeSkipsField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(0, r.fields.size());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<String, Object> data;
                    public Map<String, Object> getData() { return data; }
                    public void setData(Map<String, Object> data) { this.data = data; }
                }
            """);
        }
    }

    // ── Inheritance ────────────────────────────────────────────────────

    @Nested
    class Inheritance {

        @Test
        void extractsFieldsFromSuperclass() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(2, r.fields.size());

                // Child field first (bottom-up)
                assertEquals("childField", r.fields.get(0).getJavaFieldName());
                assertEquals("parentField", r.fields.get(1).getJavaFieldName());
            },
                JavaFileObjects.forSourceString("com.test.ParentClass", """
                    package com.test;
                    public class ParentClass {
                        private String parentField;
                        public String getParentField() { return parentField; }
                        public void setParentField(String parentField) { this.parentField = parentField; }
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class TestClass extends ParentClass {
                        private String childField;
                        public String getChildField() { return childField; }
                        public void setChildField(String childField) { this.childField = childField; }
                    }
                """)
            );
        }

        @Test
        void childShadowsParentField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                // Only 1 'name' field: child shadows parent
                long nameCount = r.fields.stream()
                        .filter(f -> f.getJavaFieldName().equals("name"))
                        .count();
                assertEquals(1, nameCount);
                // The child's version wins — it's a String here too but the point is no duplicate
                assertEquals("name", r.fields.get(0).getJavaFieldName());
            },
                JavaFileObjects.forSourceString("com.test.ParentClass", """
                    package com.test;
                    public class ParentClass {
                        private String name;
                        public String getName() { return name; }
                        public void setName(String name) { this.name = name; }
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class TestClass extends ParentClass {
                        private String name;
                    }
                """)
            );
        }
    }

    // ── Lombok detection ───────────────────────────────────────────────

    @Nested
    class LombokDetection {

        @Test
        void detectsLombokData() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertTrue(r.hasLombok);
                assertEquals(1, r.fields.size());
                assertEquals("getName", r.fields.get(0).getGetterName());
                assertEquals("setName", r.fields.get(0).getSetterName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import lombok.Data;
                @MetadataType
                @Data
                public class TestClass {
                    private String name;
                }
            """);
        }

        @Test
        void detectsLombokGetterAndSetter() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertTrue(r.hasLombok);
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import lombok.Getter;
                import lombok.Setter;
                @MetadataType
                @Getter @Setter
                public class TestClass {
                    private String name;
                }
            """);
        }

        @Test
        void noLombokWithoutAnnotations() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertFalse(r.hasLombok);
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public String name;
                }
            """);
        }
    }

    // ── No-arg constructor validation ──────────────────────────────────

    @Nested
    class ConstructorValidation {

        @Test
        void noWarningWithExplicitNoArgConstructor() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeededWithoutWarnings();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public String name;
                    public TestClass() {}
                }
            """);
        }

        @Test
        void warningWithoutNoArgConstructor() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.compilation).hadWarningContaining("No public no-arg constructor");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public String name;
                    public TestClass(String name) { this.name = name; }
                }
            """);
        }

        @Test
        void noWarningWithLombokEvenWithoutExplicitConstructor() {
            extractAndAssert(r -> {
                // Lombok @Data generates a no-arg constructor, so no warning expected
                assertThat(r.compilation).succeededWithoutWarnings();
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import lombok.Data;
                @MetadataType
                @Data
                public class TestClass {
                    private String name;
                }
            """);
        }
    }

    // ── Edge cases ─────────────────────────────────────────────────────

    @Nested
    class EdgeCases {

        @Test
        void classWithNoFields() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(0, r.fields.size());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public TestClass() {}
                }
            """);
        }

        @Test
        void defaultEncForScalarField() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals(MetadataFieldType.DEFAULT, r.fields.get(0).getEnc());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    public String name;
                }
            """);
        }

        @Test
        void fieldWithOnlyGetterSkipped() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                // 'secret' has getter but no setter, and is private → skipped
                assertEquals(1, r.fields.size());
                assertEquals("name", r.fields.get(0).getJavaFieldName());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                @MetadataType
                public class TestClass {
                    private String name;
                    private String secret;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    public String getSecret() { return secret; }
                    // no setter for 'secret'
                }
            """);
        }

        @Test
        void stringBase64EncodingForByteArray() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                assertEquals(MetadataFieldType.STRING_BASE64, r.fields.get(0).getEnc());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.*;
                @MetadataType
                public class TestClass {
                    @MetadataField(enc = MetadataFieldType.STRING_BASE64)
                    private byte[] data;
                    public byte[] getData() { return data; }
                    public void setData(byte[] data) { this.data = data; }
                }
            """);
        }
    }

    // ── Composite type fields ─────────────────────────────────────────

    @Nested
    class CompositeFields {

        @Test
        void detectsMapStringListString() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertTrue(f.isMapValueCollectionType());
                assertEquals(COLLECTION_LIST, f.getMapValueCollectionKind());
                assertEquals("java.lang.String", f.getMapValueElementTypeName());
                assertFalse(f.isMapValueElementEnumType());
                assertFalse(f.isMapValueElementNestedType());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private Map<String, List<String>> tagsByCategory;
                    public Map<String, List<String>> getTagsByCategory() { return tagsByCategory; }
                    public void setTagsByCategory(Map<String, List<String>> tagsByCategory) { this.tagsByCategory = tagsByCategory; }
                }
            """);
        }

        @Test
        void detectsMapStringMapStringInteger() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isMapType());
                assertTrue(f.isMapValueMapType());
                assertEquals("java.lang.Integer", f.getMapValueMapValueTypeName());
                assertFalse(f.isMapValueMapValueEnumType());
                assertFalse(f.isMapValueMapValueNestedType());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<String, Map<String, Integer>> nestedScores;
                    public Map<String, Map<String, Integer>> getNestedScores() { return nestedScores; }
                    public void setNestedScores(Map<String, Map<String, Integer>> nestedScores) { this.nestedScores = nestedScores; }
                }
            """);
        }

        @Test
        void detectsListListString() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isElementCollectionType());
                assertEquals(COLLECTION_LIST, f.getElementCollectionKind());
                assertEquals("java.lang.String", f.getElementElementTypeName());
                assertFalse(f.isElementElementEnumType());
                assertFalse(f.isElementElementNestedType());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private List<List<String>> matrix;
                    public List<List<String>> getMatrix() { return matrix; }
                    public void setMatrix(List<List<String>> matrix) { this.matrix = matrix; }
                }
            """);
        }

        @Test
        void detectsListMapStringString() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertEquals(1, r.fields.size());
                MetadataFieldInfo f = r.fields.get(0);
                assertTrue(f.isElementMapType());
                assertEquals("java.lang.String", f.getElementMapValueTypeName());
                assertFalse(f.isElementMapValueEnumType());
                assertFalse(f.isElementMapValueNestedType());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.List;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private List<Map<String, String>> records;
                    public List<Map<String, String>> getRecords() { return records; }
                    public void setRecords(List<Map<String, String>> records) { this.records = records; }
                }
            """);
        }

        @Test
        void detectsMapStringListEnum() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                MetadataFieldInfo f = r.fields.stream()
                        .filter(fi -> fi.getJavaFieldName().equals("statusesByGroup"))
                        .findFirst().orElseThrow();
                assertTrue(f.isMapValueCollectionType());
                assertTrue(f.isMapValueElementEnumType());
                assertEquals("com.test.Priority", f.getMapValueElementTypeName());
            },
                JavaFileObjects.forSourceString("com.test.Priority", """
                    package com.test;
                    public enum Priority { LOW, MEDIUM, HIGH }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.Map;
                    import java.util.List;
                    @MetadataType
                    public class TestClass {
                        private Map<String, List<Priority>> statusesByGroup;
                        public Map<String, List<Priority>> getStatusesByGroup() { return statusesByGroup; }
                        public void setStatusesByGroup(Map<String, List<Priority>> statusesByGroup) { this.statusesByGroup = statusesByGroup; }
                    }
                """)
            );
        }

        @Test
        void detectsMapStringListNested() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                MetadataFieldInfo f = r.fields.stream()
                        .filter(fi -> fi.getJavaFieldName().equals("addressesByType"))
                        .findFirst().orElseThrow();
                assertTrue(f.isMapValueCollectionType());
                assertTrue(f.isMapValueElementNestedType());
                assertEquals("com.test.AddressMetadataConverter", f.getMapValueElementConverterFqn());
            },
                JavaFileObjects.forSourceString("com.test.Address", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class Address {
                        public String street;
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.Map;
                    import java.util.List;
                    @MetadataType
                    public class TestClass {
                        private Map<String, List<Address>> addressesByType;
                        public Map<String, List<Address>> getAddressesByType() { return addressesByType; }
                        public void setAddressesByType(Map<String, List<Address>> addressesByType) { this.addressesByType = addressesByType; }
                    }
                """)
            );
        }

        @Test
        void detectsListMapStringNested() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                MetadataFieldInfo f = r.fields.stream()
                        .filter(fi -> fi.getJavaFieldName().equals("addressRecords"))
                        .findFirst().orElseThrow();
                assertTrue(f.isElementMapType());
                assertTrue(f.isElementMapValueNestedType());
                assertEquals("com.test.AddressMetadataConverter", f.getElementMapValueConverterFqn());
            },
                JavaFileObjects.forSourceString("com.test.Address", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    @MetadataType
                    public class Address {
                        public String street;
                    }
                """),
                JavaFileObjects.forSourceString("com.test.TestClass", """
                    package com.test;
                    import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                    import java.util.List;
                    import java.util.Map;
                    @MetadataType
                    public class TestClass {
                        private List<Map<String, Address>> addressRecords;
                        public List<Map<String, Address>> getAddressRecords() { return addressRecords; }
                        public void setAddressRecords(List<Map<String, Address>> addressRecords) { this.addressRecords = addressRecords; }
                    }
                """)
            );
        }

        @Test
        void rejectsDoubleNesting() {
            extractAndAssert(r -> {
                assertThat(r.compilation).succeeded();
                assertThat(r.compilation).hadWarningContaining("double-nested");
                assertEquals(0, r.fields.size());
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                import java.util.List;
                @MetadataType
                public class TestClass {
                    private Map<String, List<List<String>>> deep;
                    public Map<String, List<List<String>>> getDeep() { return deep; }
                    public void setDeep(Map<String, List<List<String>>> deep) { this.deep = deep; }
                }
            """);
        }

        @Test
        void rejectsInnerMapUnsupportedKey() {
            extractAndAssert(r -> {
                assertThat(r.compilation).failed();
                assertThat(r.compilation).hadErrorContaining("inner Map key type must be String, Integer, Long, BigInteger, or byte[]");
            }, """
                package com.test;
                import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
                import java.util.Map;
                @MetadataType
                public class TestClass {
                    private Map<String, Map<Double, String>> badInner;
                    public Map<String, Map<Double, String>> getBadInner() { return badInner; }
                    public void setBadInner(Map<String, Map<Double, String>> badInner) { this.badInner = badInner; }
                }
            """);
        }
    }
}
