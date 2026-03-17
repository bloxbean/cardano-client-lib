package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetadataConverterGenerator}.
 * Tests inspect the generated Java source text to assert correct code structure.
 *
 * Organised per-type: each scalar type has ONE home containing serialisation,
 * deserialisation, and encoding-variant tests. Cross-cutting concerns
 * (KeyMapping, DirectFieldAccess) are consolidated at the end.
 */
@SuppressWarnings("java:S5976") // Individual test methods provide clearer failure messages than parameterized tests for codegen assertions
class MetadataConverterGeneratorTest {

    private MetadataConverterGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MetadataConverterGenerator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generate(List<MetadataFieldInfo> fields) {
        TypeSpec typeSpec = generator.generate("com.test", "Order", fields);
        return JavaFile.builder("com.test", typeSpec).build().toString();
    }

    private String generateRecord(List<MetadataFieldInfo> fields) {
        TypeSpec typeSpec = generator.generate("com.test", "Order", fields, -1, true);
        return JavaFile.builder("com.test", typeSpec).build().toString();
    }

    /** Field with explicit getter/setter (standard POJO). */
    private MetadataFieldInfo field(String name, String javaType) {
        return field(name, name, javaType);
    }

    /** Field with explicit getter/setter and a custom metadata key. */
    private MetadataFieldInfo field(String name, String metadataKey, String javaType) {
        String cap = name.substring(0, 1).toUpperCase() + name.substring(1);
        MetadataFieldInfo f = new MetadataFieldInfo();
        f.setJavaFieldName(name);
        f.setMetadataKey(metadataKey);
        f.setJavaTypeName(javaType);
        f.setGetterName("get" + cap);
        f.setSetterName("set" + cap);
        return f;
    }

    /** Field with no getter/setter — direct public field access. */
    private MetadataFieldInfo directField(String name, String javaType) {
        MetadataFieldInfo f = new MetadataFieldInfo();
        f.setJavaFieldName(name);
        f.setMetadataKey(name);
        f.setJavaTypeName(javaType);
        f.setGetterName(null);
        f.setSetterName(null);
        return f;
    }

    /** Field with explicit getter/setter, custom metadata key, and output type override. */
    private MetadataFieldInfo fieldEnc(String name, String javaType, MetadataFieldType enc) {
        MetadataFieldInfo f = field(name, javaType);
        f.setEnc(enc);
        return f;
    }

    /**
     * Boolean field using {@code isX()} getter naming (mirrors how the annotation processor
     * resolves boolean getters in real POJOs).
     */
    private MetadataFieldInfo boolField(String name, String javaType) {
        String cap = name.substring(0, 1).toUpperCase() + name.substring(1);
        MetadataFieldInfo f = new MetadataFieldInfo();
        f.setJavaFieldName(name);
        f.setMetadataKey(name);
        f.setJavaTypeName(javaType);
        f.setGetterName("is" + cap);
        f.setSetterName("set" + cap);
        return f;
    }

    /** Boolean field with output type override, using {@code isX()} getter. */
    private MetadataFieldInfo boolFieldEnc(String name, String javaType, MetadataFieldType enc) {
        MetadataFieldInfo f = boolField(name, javaType);
        f.setEnc(enc);
        return f;
    }

    /** List field with explicit getter/setter. */
    private MetadataFieldInfo listField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.List<" + elementType + ">");
        f.setElement(new MetadataFieldInfo.ElementInfo(elementType, MetadataFieldInfo.LeafClassification.NONE, null, null));
        f.setCollectionType(true);
        f.setCollectionKind(COLLECTION_LIST);
        return f;
    }

    /** List field with explicit getter/setter and encoding override. */
    private MetadataFieldInfo listFieldEnc(String name, String elementType, MetadataFieldType enc) {
        MetadataFieldInfo f = listField(name, elementType);
        f.setEnc(enc);
        return f;
    }

    /** Set field with explicit getter/setter. */
    private MetadataFieldInfo setField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.Set<" + elementType + ">");
        f.setElement(new MetadataFieldInfo.ElementInfo(elementType, MetadataFieldInfo.LeafClassification.NONE, null, null));
        f.setCollectionType(true);
        f.setCollectionKind(COLLECTION_SET);
        return f;
    }

    /** SortedSet field with explicit getter/setter. */
    private MetadataFieldInfo sortedSetField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.SortedSet<" + elementType + ">");
        f.setElement(new MetadataFieldInfo.ElementInfo(elementType, MetadataFieldInfo.LeafClassification.NONE, null, null));
        f.setCollectionType(true);
        f.setCollectionKind(COLLECTION_SORTED_SET);
        return f;
    }

    /** Optional field with explicit getter/setter. */
    private MetadataFieldInfo optionalField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.Optional<" + elementType + ">");
        f.setElement(new MetadataFieldInfo.ElementInfo(elementType, MetadataFieldInfo.LeafClassification.NONE, null, null));
        f.setOptionalType(true);
        return f;
    }

    /** Map field with explicit getter/setter and configurable key/value types. */
    private MetadataFieldInfo mapField(String name, String keyType, String valueType) {
        MetadataFieldInfo f = field(name, "java.util.Map<" + keyType + ", " + valueType + ">");
        f.setMapType(new MetadataFieldInfo.MapTypeInfo(keyType, valueType, MetadataFieldInfo.LeafClassification.NONE, null, null));
        return f;
    }

    // =========================================================================
    // Class structure
    // =========================================================================

    @Nested
    class ClassStructure {

        @Test
        void generatesCorrectClassName() {
            String src = generate(List.of());
            assertTrue(src.contains("class OrderMetadataConverter"));
        }

        @Test
        void generatedClassIsPublic() {
            String src = generate(List.of());
            assertTrue(src.contains("public class OrderMetadataConverter"));
        }

        @Test
        void implementsMetadataConverterInterface() {
            String src = generate(List.of());
            assertTrue(src.contains("implements MetadataConverter<Order>"),
                    "Should implement MetadataConverter<Order>: " + src);
        }

        @Test
        void implementsLabeledMetadataConverterWhenLabelSet() {
            TypeSpec typeSpec = generator.generate("com.test", "Order", List.of(), 721);
            String src = JavaFile.builder("com.test", typeSpec).build().toString();
            assertTrue(src.contains("implements LabeledMetadataConverter<Order>"),
                    "Should implement LabeledMetadataConverter<Order> when label >= 0: " + src);
        }

        @Test
        void containsToMetadataMapMethod() {
            String src = generate(List.of());
            assertTrue(src.contains("public MetadataMap toMetadataMap(Order order)"));
        }

        @Test
        void containsFromMetadataMapMethod() {
            String src = generate(List.of());
            assertTrue(src.contains("public Order fromMetadataMap(MetadataMap map)"));
        }

        @Test
        void methodsHaveOverrideAnnotation() {
            String src = generate(List.of());
            assertTrue(src.contains("@Override"), "Generated methods should have @Override: " + src);
        }

        @Test
        void emptyFieldList_stillGeneratesValidClass() {
            String src = generate(List.of());
            assertTrue(src.contains("MetadataMap map = MetadataBuilder.createMap()"));
            assertTrue(src.contains("return map"));
            assertTrue(src.contains("Order obj = new Order()"));
            assertTrue(src.contains("return obj"));
        }

    }

    // =========================================================================
    // String fields
    // =========================================================================

    @Nested
    class StringFields {

        @Test
        void toMetadataMap_shortString_storedDirectlyInMap() {
            String src = generate(List.of(field("note", "java.lang.String")));
            // else branch — direct put without wrapping
            assertTrue(src.contains("map.put(\"note\", order.getNote())"));
        }

        @Test
        void toMetadataMap_longString_byteCountCheckedAgainst64() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("getBytes("));
            assertTrue(src.contains("UTF_8"));
            assertTrue(src.contains("> 64"));
        }

        @Test
        void toMetadataMap_longString_splitWithStringUtils() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("splitStringEveryNCharacters"));
            assertTrue(src.contains("splitStringEveryNCharacters(order.getNote(), 64)"));
        }

        @Test
        void toMetadataMap_longString_chunksStoredAsMetadataList() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("MetadataList _chunks = MetadataBuilder.createList()"));
            assertTrue(src.contains("_chunks.add(_part)"));
            assertTrue(src.contains("map.put(\"note\", _chunks)"));
        }

        @Test
        void toMetadataMap_stringField_hasNullGuard() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("if (order.getNote() != null)"));
        }

        @Test
        void fromMetadataMap_plainString_assignedDirectly() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("if (v instanceof String)"));
            assertTrue(src.contains("obj.setNote((String) v)"));
        }

        @Test
        void fromMetadataMap_chunkedString_metadataListBranchPresent() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("else if (v instanceof MetadataList)"));
        }

        @Test
        void fromMetadataMap_chunkedString_chunksReassembledWithStringBuilder() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertTrue(src.contains("StringBuilder _sb = new StringBuilder()"));
            assertTrue(src.contains("MetadataList _list = (MetadataList) v"));
            assertTrue(src.contains("_list.size()"));
            assertTrue(src.contains("_list.getValueAt(_i)"));
            assertTrue(src.contains("_sb.append((String) _chunk)"));
            assertTrue(src.contains("obj.setNote(_sb.toString())"));
        }

        @Test
        void fromMetadataMap_stringField_doesNotEmitBigIntegerCast() {
            String src = generate(List.of(field("note", "java.lang.String")));
            assertFalse(src.contains("longValue()"));
            assertFalse(src.contains("intValue()"));
        }

        @Test
        void stringField_asString_stillUses64ByteSplitLogic() {
            String src = generate(List.of(fieldEnc("note", "java.lang.String", MetadataFieldType.STRING)));
            assertTrue(src.contains("splitStringEveryNCharacters"));
            assertTrue(src.contains("UTF_8"));
        }
    }

    // =========================================================================
    // BigInteger fields
    // =========================================================================

    @Nested
    class BigIntegerFields {

        @Test
        void toMetadataMap_bigIntegerField_storedDirectly() {
            String src = generate(List.of(field("amount", "java.math.BigInteger")));
            assertTrue(src.contains("_putBigInt(map, \"amount\", order.getAmount())"));
        }

        @Test
        void toMetadataMap_bigIntegerField_hasNullGuard() {
            String src = generate(List.of(field("amount", "java.math.BigInteger")));
            assertTrue(src.contains("if (order.getAmount() != null)"));
        }

        @Test
        void fromMetadataMap_bigIntegerField_castFromBigIntegerDirectly() {
            String src = generate(List.of(field("amount", "java.math.BigInteger")));
            assertTrue(src.contains("if (v instanceof BigInteger)"));
            assertTrue(src.contains("obj.setAmount((BigInteger) v)"));
        }

        @Test
        void fromMetadataMap_numericField_doesNotEmitStringBuilder() {
            String src = generate(List.of(field("amount", "java.math.BigInteger")));
            assertFalse(src.contains("StringBuilder"));
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_bigIntegerField_serializedViaToString() {
                String src = generate(List.of(fieldEnc("amount", "java.math.BigInteger", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"amount\", order.getAmount().toString())"));
            }

            @Test
            void fromMetadataMap_bigIntegerField_parsedViaNewBigInteger() {
                String src = generate(List.of(fieldEnc("amount", "java.math.BigInteger", MetadataFieldType.STRING)));
                assertTrue(src.contains("new BigInteger((String) v)"));
            }
        }
    }

    // =========================================================================
    // Integer / int fields
    // =========================================================================

    @Nested
    class IntegerFields {

        @Test
        void toMetadataMap_integerBoxed_castToLongThenWrapped() {
            String src = generate(List.of(field("qty", "java.lang.Integer")));
            assertTrue(src.contains("BigInteger.valueOf((long) order.getQty())"));
        }

        @Test
        void toMetadataMap_integerBoxed_hasNullGuard() {
            String src = generate(List.of(field("qty", "java.lang.Integer")));
            assertTrue(src.contains("if (order.getQty() != null)"));
        }

        @Test
        void toMetadataMap_intPrimitive_castToLongThenWrapped() {
            String src = generate(List.of(field("qty", "int")));
            assertTrue(src.contains("BigInteger.valueOf((long) order.getQty())"));
        }

        @Test
        void toMetadataMap_intPrimitive_noNullGuard() {
            String src = generate(List.of(field("qty", "int")));
            assertFalse(src.contains("if (order.getQty() != null)"));
        }

        @Test
        void fromMetadataMap_integerBoxed_extractedFromBigIntegerViaIntValue() {
            String src = generate(List.of(field("qty", "java.lang.Integer")));
            assertTrue(src.contains("if (v instanceof BigInteger)"));
            assertTrue(src.contains("obj.setQty(((BigInteger) v).intValue())"));
        }

        @Test
        void fromMetadataMap_intPrimitive_extractedFromBigIntegerViaIntValue() {
            String src = generate(List.of(field("qty", "int")));
            assertTrue(src.contains("obj.setQty(((BigInteger) v).intValue())"));
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_intField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldEnc("code", "int", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"code\", String.valueOf(order.getCode()))"));
            }

            @Test
            void toMetadataMap_integerBoxedField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldEnc("code", "java.lang.Integer", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"code\", String.valueOf(order.getCode()))"));
            }

            @Test
            void toMetadataMap_intField_asString_doesNotEmitBigIntegerValueOf() {
                String src = generate(List.of(fieldEnc("code", "int", MetadataFieldType.STRING)));
                assertFalse(src.contains("BigInteger.valueOf"));
            }

            @Test
            void fromMetadataMap_intField_parsedViaIntegerParseInt() {
                String src = generate(List.of(fieldEnc("code", "int", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (v instanceof String)"));
                assertTrue(src.contains("Integer.parseInt((String) v)"));
            }

            @Test
            void fromMetadataMap_integerBoxedField_parsedViaIntegerParseInt() {
                String src = generate(List.of(fieldEnc("code", "java.lang.Integer", MetadataFieldType.STRING)));
                assertTrue(src.contains("Integer.parseInt((String) v)"));
            }

            @Test
            void fromMetadataMap_intField_asString_doesNotEmitBigIntegerInstanceofCheck() {
                String src = generate(List.of(fieldEnc("code", "int", MetadataFieldType.STRING)));
                assertFalse(src.contains("instanceof BigInteger"));
            }
        }
    }

    // =========================================================================
    // Long / long fields
    // =========================================================================

    @Nested
    class LongFields {

        @Test
        void toMetadataMap_longBoxed_wrappedInBigIntegerValueOf() {
            String src = generate(List.of(field("ts", "java.lang.Long")));
            assertTrue(src.contains("BigInteger.valueOf(order.getTs())"));
        }

        @Test
        void toMetadataMap_longBoxed_hasNullGuard() {
            String src = generate(List.of(field("ts", "java.lang.Long")));
            assertTrue(src.contains("if (order.getTs() != null)"));
        }

        @Test
        void toMetadataMap_longPrimitive_wrappedInBigIntegerValueOf() {
            String src = generate(List.of(field("ts", "long")));
            assertTrue(src.contains("BigInteger.valueOf(order.getTs())"));
        }

        @Test
        void toMetadataMap_longPrimitive_noNullGuard() {
            String src = generate(List.of(field("ts", "long")));
            assertFalse(src.contains("if (order.getTs() != null)"));
        }

        @Test
        void fromMetadataMap_longBoxed_extractedFromBigIntegerViaLongValue() {
            String src = generate(List.of(field("ts", "java.lang.Long")));
            assertTrue(src.contains("if (v instanceof BigInteger)"));
            assertTrue(src.contains("obj.setTs(((BigInteger) v).longValue())"));
        }

        @Test
        void fromMetadataMap_longPrimitive_extractedFromBigIntegerViaLongValue() {
            String src = generate(List.of(field("ts", "long")));
            assertTrue(src.contains("obj.setTs(((BigInteger) v).longValue())"));
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_longField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldEnc("ts", "long", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"ts\", String.valueOf(order.getTs()))"));
            }

            @Test
            void toMetadataMap_longBoxedField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldEnc("ts", "java.lang.Long", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"ts\", String.valueOf(order.getTs()))"));
            }

            @Test
            void fromMetadataMap_longField_parsedViaLongParseLong() {
                String src = generate(List.of(fieldEnc("ts", "long", MetadataFieldType.STRING)));
                assertTrue(src.contains("Long.parseLong((String) v)"));
            }

            @Test
            void fromMetadataMap_longBoxedField_parsedViaLongParseLong() {
                String src = generate(List.of(fieldEnc("ts", "java.lang.Long", MetadataFieldType.STRING)));
                assertTrue(src.contains("Long.parseLong((String) v)"));
            }
        }
    }

    // =========================================================================
    // short / Short
    // =========================================================================

    @Nested
    class ShortFields {

        @Test
        void toMetadataMap_primitive_emitsBigIntegerValueOf() {
            String src = generate(List.of(field("count", "short")));
            assertTrue(src.contains("BigInteger.valueOf((long) order.getCount())"));
        }

        @Test
        void toMetadataMap_primitive_noNullGuard() {
            String src = generate(List.of(field("count", "short")));
            assertFalse(src.contains("if (order.getCount() != null)"));
        }

        @Test
        void toMetadataMap_boxed_hasNullGuard() {
            String src = generate(List.of(field("count", "java.lang.Short")));
            assertTrue(src.contains("if (order.getCount() != null)"));
        }

        @Test
        void fromMetadataMap_primitive_extractsShortValue() {
            String src = generate(List.of(field("count", "short")));
            assertTrue(src.contains("if (v instanceof BigInteger)"));
            assertTrue(src.contains("((BigInteger) v).shortValue()"));
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_emitsStringValueOf() {
                String src = generate(List.of(fieldEnc("count", "short", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"count\", String.valueOf(order.getCount()))"));
            }

            @Test
            void fromMetadataMap_parsesShort() {
                String src = generate(List.of(fieldEnc("count", "short", MetadataFieldType.STRING)));
                assertTrue(src.contains("Short.parseShort((String) v)"));
            }
        }
    }

    // =========================================================================
    // byte / Byte
    // =========================================================================

    @Nested
    class ByteFields {

        @Test
        void toMetadataMap_primitive_emitsBigIntegerValueOf() {
            String src = generate(List.of(field("b", "byte")));
            assertTrue(src.contains("BigInteger.valueOf((long) order.getB())"));
        }

        @Test
        void toMetadataMap_primitive_noNullGuard() {
            String src = generate(List.of(field("b", "byte")));
            assertFalse(src.contains("if (order.getB() != null)"));
        }

        @Test
        void toMetadataMap_boxed_hasNullGuard() {
            String src = generate(List.of(field("b", "java.lang.Byte")));
            assertTrue(src.contains("if (order.getB() != null)"));
        }

        @Test
        void fromMetadataMap_primitive_extractsByteValue() {
            String src = generate(List.of(field("b", "byte")));
            assertTrue(src.contains("((BigInteger) v).byteValue()"));
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_emitsStringValueOf() {
                String src = generate(List.of(fieldEnc("b", "byte", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"b\", String.valueOf(order.getB()))"));
            }

            @Test
            void fromMetadataMap_parsesByte() {
                String src = generate(List.of(fieldEnc("b", "byte", MetadataFieldType.STRING)));
                assertTrue(src.contains("Byte.parseByte((String) v)"));
            }
        }
    }

    // =========================================================================
    // boolean / Boolean
    // =========================================================================

    @Nested
    class BooleanFields {

        @Test
        void toMetadataMap_primitive_emitsTernaryBigInteger() {
            String src = generate(List.of(boolField("active", "boolean")));
            assertTrue(src.contains("order.isActive() ? BigInteger.ONE : BigInteger.ZERO"));
        }

        @Test
        void toMetadataMap_primitive_noNullGuard() {
            String src = generate(List.of(boolField("active", "boolean")));
            assertFalse(src.contains("if (order.isActive() != null)"));
        }

        @Test
        void toMetadataMap_boxed_hasNullGuard() {
            String src = generate(List.of(boolField("active", "java.lang.Boolean")));
            assertTrue(src.contains("if (order.isActive() != null)"));
        }

        @Test
        void fromMetadataMap_default_checksBigIntegerOne() {
            String src = generate(List.of(boolField("active", "boolean")));
            assertTrue(src.contains("BigInteger.ONE.equals(v)"));
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_emitsStringValueOf() {
                String src = generate(List.of(boolFieldEnc("active", "boolean", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"active\", String.valueOf(order.isActive()))"));
            }

            @Test
            void fromMetadataMap_parsesBoolean() {
                String src = generate(List.of(boolFieldEnc("active", "boolean", MetadataFieldType.STRING)));
                assertTrue(src.contains("Boolean.parseBoolean((String) v)"));
            }

            @Test
            void doesNotEmitBigIntegerCheck() {
                String src = generate(List.of(boolFieldEnc("active", "boolean", MetadataFieldType.STRING)));
                assertFalse(src.contains("BigInteger.ONE.equals"));
            }
        }
    }

    // =========================================================================
    // char / Character
    // =========================================================================

    @Nested
    class CharFields {

        @Test
        void toMetadataMap_primitive_emitsStringValueOf() {
            String src = generate(List.of(field("code", "char")));
            assertTrue(src.contains("map.put(\"code\", String.valueOf(order.getCode()))"));
        }

        @Test
        void toMetadataMap_primitive_noNullGuard() {
            String src = generate(List.of(field("code", "char")));
            assertFalse(src.contains("if (order.getCode() != null)"));
        }

        @Test
        void toMetadataMap_boxed_hasNullGuard() {
            String src = generate(List.of(field("code", "java.lang.Character")));
            assertTrue(src.contains("if (order.getCode() != null)"));
        }

        @Test
        void fromMetadataMap_extractsCharAtZero() {
            String src = generate(List.of(field("code", "char")));
            assertTrue(src.contains("((String) v).charAt(0)"));
        }

        @Test
        void fromMetadataMap_doesNotEmitBigIntegerCheck() {
            String src = generate(List.of(field("code", "char")));
            assertFalse(src.contains("instanceof BigInteger"));
        }
    }

    // =========================================================================
    // double / Double
    // =========================================================================

    @Nested
    class DoubleFields {

        @Test
        void toMetadataMap_primitive_emitsStringValueOf() {
            String src = generate(List.of(field("price", "double")));
            assertTrue(src.contains("map.put(\"price\", String.valueOf(order.getPrice()))"));
        }

        @Test
        void toMetadataMap_primitive_noNullGuard() {
            String src = generate(List.of(field("price", "double")));
            assertFalse(src.contains("if (order.getPrice() != null)"));
        }

        @Test
        void toMetadataMap_boxed_hasNullGuard() {
            String src = generate(List.of(field("price", "java.lang.Double")));
            assertTrue(src.contains("if (order.getPrice() != null)"));
        }

        @Test
        void fromMetadataMap_parsesDoubleFromString() {
            String src = generate(List.of(field("price", "double")));
            assertTrue(src.contains("Double.parseDouble((String) v)"));
        }

        @Test
        void toMetadataMap_doesNotEmitBigIntegerValueOf() {
            String src = generate(List.of(field("price", "double")));
            assertFalse(src.contains("BigInteger.valueOf"));
        }
    }

    // =========================================================================
    // float / Float
    // =========================================================================

    @Nested
    class FloatFields {

        @Test
        void toMetadataMap_primitive_emitsStringValueOf() {
            String src = generate(List.of(field("weight", "float")));
            assertTrue(src.contains("map.put(\"weight\", String.valueOf(order.getWeight()))"));
        }

        @Test
        void toMetadataMap_primitive_noNullGuard() {
            String src = generate(List.of(field("weight", "float")));
            assertFalse(src.contains("if (order.getWeight() != null)"));
        }

        @Test
        void fromMetadataMap_parsesFloatFromString() {
            String src = generate(List.of(field("weight", "float")));
            assertTrue(src.contains("Float.parseFloat((String) v)"));
        }
    }

    // =========================================================================
    // BigDecimal  (→ Cardano text via toPlainString)
    // =========================================================================

    @Nested
    class BigDecimalFields {

        @Test
        void toMetadataMap_serialisedViaToPlainString() {
            String src = generate(List.of(field("price", "java.math.BigDecimal")));
            assertTrue(src.contains("order.getPrice().toPlainString()"));
        }

        @Test
        void toMetadataMap_doesNotUseStringValueOf() {
            String src = generate(List.of(field("price", "java.math.BigDecimal")));
            // String.valueOf can produce scientific notation — must not be used
            assertFalse(src.contains("String.valueOf(order.getPrice())"));
        }

        @Test
        void toMetadataMap_doesNotEmitBigIntegerValueOf() {
            String src = generate(List.of(field("price", "java.math.BigDecimal")));
            assertFalse(src.contains("BigInteger.valueOf"));
        }

        @Test
        void toMetadataMap_hasNullGuard() {
            String src = generate(List.of(field("price", "java.math.BigDecimal")));
            assertTrue(src.contains("if (order.getPrice() != null)"));
        }

        @Test
        void fromMetadataMap_parsedViaNewBigDecimal() {
            String src = generate(List.of(field("price", "java.math.BigDecimal")));
            assertTrue(src.contains("if (v instanceof String)"));
            assertTrue(src.contains("new BigDecimal((String) v)"));
        }

        @Test
        void asString_toMetadataMap_sameAsDefault() {
            String srcDefault = generate(List.of(field("price", "java.math.BigDecimal")));
            String srcAsString = generate(List.of(fieldEnc("price", "java.math.BigDecimal", MetadataFieldType.STRING)));
            // as=STRING on BigDecimal is a no-op — output identical to DEFAULT
            assertEquals(srcDefault, srcAsString);
        }
    }

    // =========================================================================
    // byte[] fields
    // =========================================================================

    @Nested
    class ByteArrayFields {

        @Test
        void toMetadataMap_byteArrayField_storedDirectly() {
            String src = generate(List.of(field("sig", "byte[]")));
            assertTrue(src.contains("map.put(\"sig\", order.getSig())"));
        }

        @Test
        void toMetadataMap_byteArrayField_hasNullGuard() {
            String src = generate(List.of(field("sig", "byte[]")));
            assertTrue(src.contains("if (order.getSig() != null)"));
        }

        @Test
        void toMetadataMap_byteArrayField_doesNotEmitStringSplitLogic() {
            String src = generate(List.of(field("sig", "byte[]")));
            assertFalse(src.contains("splitStringEveryNCharacters"));
            assertFalse(src.contains("getBytes("));
        }

        @Test
        void fromMetadataMap_byteArrayField_castFromByteArray() {
            String src = generate(List.of(field("sig", "byte[]")));
            assertTrue(src.contains("if (v instanceof byte[])"));
            assertTrue(src.contains("obj.setSig((byte[]) v)"));
        }

        @Test
        void fromMetadataMap_byteArrayField_doesNotEmitStringBuilder() {
            String src = generate(List.of(field("sig", "byte[]")));
            assertFalse(src.contains("StringBuilder"));
        }

        @Nested
        class HexEncoding {

            @Test
            void toMetadataMap_byteArray_encodedWithHexUtil() {
                String src = generate(List.of(fieldEnc("data", "byte[]", MetadataFieldType.STRING_HEX)));
                assertTrue(src.contains("HexUtil.encodeHexString(order.getData())"));
            }

            @Test
            void toMetadataMap_byteArray_storedUnderCorrectKey() {
                String src = generate(List.of(fieldEnc("data", "byte[]", MetadataFieldType.STRING_HEX)));
                assertTrue(src.contains("map.put(\"data\","));
            }

            @Test
            void toMetadataMap_byteArray_hasNullGuard() {
                String src = generate(List.of(fieldEnc("data", "byte[]", MetadataFieldType.STRING_HEX)));
                assertTrue(src.contains("if (order.getData() != null)"));
            }

            @Test
            void fromMetadataMap_hexString_decodedWithHexUtil() {
                String src = generate(List.of(fieldEnc("data", "byte[]", MetadataFieldType.STRING_HEX)));
                assertTrue(src.contains("if (v instanceof String)"));
                assertTrue(src.contains("HexUtil.decodeHexString((String) v)"));
            }

            @Test
            void fromMetadataMap_doesNotEmitByteArrayInstanceofCheck() {
                String src = generate(List.of(fieldEnc("data", "byte[]", MetadataFieldType.STRING_HEX)));
                assertFalse(src.contains("instanceof byte[]"));
            }
        }

        @Nested
        class Base64Encoding {

            @Test
            void toMetadataMap_byteArray_encodedWithBase64() {
                String src = generate(List.of(fieldEnc("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertTrue(src.contains("Base64.getEncoder().encodeToString(order.getSig())"));
            }

            @Test
            void toMetadataMap_byteArray_storedUnderCorrectKey() {
                String src = generate(List.of(fieldEnc("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertTrue(src.contains("map.put(\"sig\","));
            }

            @Test
            void toMetadataMap_byteArray_hasNullGuard() {
                String src = generate(List.of(fieldEnc("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertTrue(src.contains("if (order.getSig() != null)"));
            }

            @Test
            void fromMetadataMap_base64String_decodedWithBase64() {
                String src = generate(List.of(fieldEnc("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertTrue(src.contains("if (v instanceof String)"));
                assertTrue(src.contains("Base64.getDecoder().decode((String) v)"));
            }

            @Test
            void fromMetadataMap_doesNotEmitByteArrayInstanceofCheck() {
                String src = generate(List.of(fieldEnc("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertFalse(src.contains("instanceof byte[]"));
            }
        }
    }

    // =========================================================================
    // URI fields
    // =========================================================================

    @Nested
    class UriFields {

        @Test
        void toMetadataMap_storedViaToString() {
            String src = generate(List.of(field("website", "java.net.URI")));
            assertTrue(src.contains("map.put(\"website\", order.getWebsite().toString())"));
        }

        @Test
        void toMetadataMap_nullChecked() {
            String src = generate(List.of(field("website", "java.net.URI")));
            assertTrue(src.contains("if (order.getWebsite() != null)"));
        }

        @Test
        void fromMetadataMap_instanceofStringGuard() {
            String src = generate(List.of(field("website", "java.net.URI")));
            assertTrue(src.contains("if (v instanceof String)"));
        }

        @Test
        void fromMetadataMap_uriCreate() {
            String src = generate(List.of(field("website", "java.net.URI")));
            assertTrue(src.contains("URI.create((String) v)"));
        }

        @Test
        void fromMetadataMap_setterCalled() {
            String src = generate(List.of(field("website", "java.net.URI")));
            assertTrue(src.contains("obj.setWebsite(URI.create((String) v))"));
        }

        @Test
        void toMetadataMap_customKey() {
            String src = generate(List.of(field("website", "url", "java.net.URI")));
            assertTrue(src.contains("map.put(\"url\", order.getWebsite().toString())"));
        }
    }

    // =========================================================================
    // URL fields
    // =========================================================================

    @Nested
    class UrlFields {

        @Test
        void toMetadataMap_storedViaToString() {
            String src = generate(List.of(field("website", "java.net.URL")));
            assertTrue(src.contains("map.put(\"website\", order.getWebsite().toString())"));
        }

        @Test
        void toMetadataMap_nullChecked() {
            String src = generate(List.of(field("website", "java.net.URL")));
            assertTrue(src.contains("if (order.getWebsite() != null)"));
        }

        @Test
        void fromMetadataMap_instanceofStringGuard() {
            String src = generate(List.of(field("website", "java.net.URL")));
            assertTrue(src.contains("if (v instanceof String)"));
        }

        @Test
        void fromMetadataMap_newUrlCalled() {
            String src = generate(List.of(field("website", "java.net.URL")));
            assertTrue(src.contains("new URL((String) v)"));
        }

        @Test
        void fromMetadataMap_malformedUrlExceptionCaught() {
            String src = generate(List.of(field("website", "java.net.URL")));
            assertTrue(src.contains("catch (MalformedURLException _e)"));
            assertTrue(src.contains("throw new IllegalArgumentException(\"Malformed URL: \" + v, _e)"));
        }

        @Test
        void fromMetadataMap_setterCalled() {
            String src = generate(List.of(field("website", "java.net.URL")));
            assertTrue(src.contains("obj.setWebsite(new URL((String) v))"));
        }

        @Test
        void toMetadataMap_customKey() {
            String src = generate(List.of(field("website", "url", "java.net.URL")));
            assertTrue(src.contains("map.put(\"url\", order.getWebsite().toString())"));
        }
    }

    // =========================================================================
    // UUID fields
    // =========================================================================

    @Nested
    class UuidFields {

        @Test
        void toMetadataMap_storedViaToString() {
            String src = generate(List.of(field("id", "java.util.UUID")));
            assertTrue(src.contains("map.put(\"id\", order.getId().toString())"));
        }

        @Test
        void toMetadataMap_nullChecked() {
            String src = generate(List.of(field("id", "java.util.UUID")));
            assertTrue(src.contains("if (order.getId() != null)"));
        }

        @Test
        void fromMetadataMap_instanceofStringGuard() {
            String src = generate(List.of(field("id", "java.util.UUID")));
            assertTrue(src.contains("if (v instanceof String)"));
        }

        @Test
        void fromMetadataMap_uuidFromString() {
            String src = generate(List.of(field("id", "java.util.UUID")));
            assertTrue(src.contains("UUID.fromString((String) v)"));
        }

        @Test
        void fromMetadataMap_setterCalled() {
            String src = generate(List.of(field("id", "java.util.UUID")));
            assertTrue(src.contains("obj.setId(UUID.fromString((String) v))"));
        }

        @Test
        void toMetadataMap_customKey() {
            String src = generate(List.of(field("id", "txId", "java.util.UUID")));
            assertTrue(src.contains("map.put(\"txId\", order.getId().toString())"));
        }
    }

    // =========================================================================
    // Currency fields
    // =========================================================================

    @Nested
    class CurrencyFields {

        @Test
        void toMetadataMap_storedViaCurrencyCode() {
            String src = generate(List.of(field("currency", "java.util.Currency")));
            assertTrue(src.contains("map.put(\"currency\", order.getCurrency().getCurrencyCode())"));
        }

        @Test
        void toMetadataMap_nullChecked() {
            String src = generate(List.of(field("currency", "java.util.Currency")));
            assertTrue(src.contains("if (order.getCurrency() != null)"));
        }

        @Test
        void fromMetadataMap_instanceofStringGuard() {
            String src = generate(List.of(field("currency", "java.util.Currency")));
            assertTrue(src.contains("if (v instanceof String)"));
        }

        @Test
        void fromMetadataMap_currencyGetInstance() {
            String src = generate(List.of(field("currency", "java.util.Currency")));
            assertTrue(src.contains("Currency.getInstance((String) v)"));
        }

        @Test
        void fromMetadataMap_setterCalled() {
            String src = generate(List.of(field("currency", "java.util.Currency")));
            assertTrue(src.contains("obj.setCurrency(Currency.getInstance((String) v))"));
        }

        @Test
        void toMetadataMap_customKey() {
            String src = generate(List.of(field("currency", "ccy", "java.util.Currency")));
            assertTrue(src.contains("map.put(\"ccy\", order.getCurrency().getCurrencyCode())"));
        }
    }

    // =========================================================================
    // Locale fields
    // =========================================================================

    @Nested
    class LocaleFields {

        @Test
        void toMetadataMap_storedViaLanguageTag() {
            String src = generate(List.of(field("locale", "java.util.Locale")));
            assertTrue(src.contains("map.put(\"locale\", order.getLocale().toLanguageTag())"));
        }

        @Test
        void toMetadataMap_nullChecked() {
            String src = generate(List.of(field("locale", "java.util.Locale")));
            assertTrue(src.contains("if (order.getLocale() != null)"));
        }

        @Test
        void fromMetadataMap_instanceofStringGuard() {
            String src = generate(List.of(field("locale", "java.util.Locale")));
            assertTrue(src.contains("if (v instanceof String)"));
        }

        @Test
        void fromMetadataMap_forLanguageTag() {
            String src = generate(List.of(field("locale", "java.util.Locale")));
            assertTrue(src.contains("Locale.forLanguageTag((String) v)"));
        }

        @Test
        void fromMetadataMap_setterCalled() {
            String src = generate(List.of(field("locale", "java.util.Locale")));
            assertTrue(src.contains("obj.setLocale(Locale.forLanguageTag((String) v))"));
        }

        @Test
        void toMetadataMap_customKey() {
            String src = generate(List.of(field("locale", "lang", "java.util.Locale")));
            assertTrue(src.contains("map.put(\"lang\", order.getLocale().toLanguageTag())"));
        }
    }

    // =========================================================================
    // Enum fields
    // =========================================================================

    @Nested
    class EnumFields {

        /** Creates a field info representing an enum type. */
        private MetadataFieldInfo enumField(String name, String enumFqn) {
            MetadataFieldInfo f = field(name, enumFqn);
            f.setEnumType(true);
            return f;
        }

        @Test
        void toMetadataMap_storedViaName() {
            String src = generate(List.of(enumField("status", "com.example.Status")));
            assertTrue(src.contains("map.put(\"status\", order.getStatus().name())"));
        }

        @Test
        void toMetadataMap_nullChecked() {
            String src = generate(List.of(enumField("status", "com.example.Status")));
            assertTrue(src.contains("if (order.getStatus() != null)"));
        }

        @Test
        void fromMetadataMap_instanceofStringGuard() {
            String src = generate(List.of(enumField("status", "com.example.Status")));
            assertTrue(src.contains("if (v instanceof String)"));
        }

        @Test
        void fromMetadataMap_valueOfCalled() {
            String src = generate(List.of(enumField("status", "com.example.Status")));
            assertTrue(src.contains("Status.valueOf((String) v)"));
        }

        @Test
        void fromMetadataMap_setterCalled() {
            String src = generate(List.of(enumField("status", "com.example.Status")));
            assertTrue(src.contains("obj.setStatus(Status.valueOf((String) v))"));
        }

        @Test
        void toMetadataMap_customKey() {
            MetadataFieldInfo f = enumField("status", "com.example.Status");
            f.setMetadataKey("st");
            String src = generate(List.of(f));
            assertTrue(src.contains("map.put(\"st\", order.getStatus().name())"));
        }
    }

    // =========================================================================
    // Instant fields
    // =========================================================================

    @Nested
    class InstantFields {

        // --- DEFAULT (epoch seconds) ---

        @Nested
        class DefaultEncoding {

            @Test
            void toMetadataMap_storesEpochSeconds() {
                String src = generate(List.of(field("createdAt", "java.time.Instant")));
                assertTrue(src.contains("BigInteger.valueOf(order.getCreatedAt().getEpochSecond())"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(field("createdAt", "java.time.Instant")));
                assertTrue(src.contains("if (order.getCreatedAt() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfBigIntegerGuard() {
                String src = generate(List.of(field("createdAt", "java.time.Instant")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
            }

            @Test
            void fromMetadataMap_restoredViaOfEpochSecond() {
                String src = generate(List.of(field("createdAt", "java.time.Instant")));
                assertTrue(src.contains("Instant.ofEpochSecond(((BigInteger) v).longValue())"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(field("createdAt", "java.time.Instant")));
                assertTrue(src.contains("obj.setCreatedAt(Instant.ofEpochSecond(((BigInteger) v).longValue()))"));
            }
        }

        // --- STRING (ISO-8601) ---

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_storesIso8601() {
                String src = generate(List.of(fieldEnc("createdAt", "java.time.Instant", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"createdAt\", order.getCreatedAt().toString())"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(fieldEnc("createdAt", "java.time.Instant", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (order.getCreatedAt() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfStringGuard() {
                String src = generate(List.of(fieldEnc("createdAt", "java.time.Instant", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (v instanceof String)"));
            }

            @Test
            void fromMetadataMap_restoredViaInstantParse() {
                String src = generate(List.of(fieldEnc("createdAt", "java.time.Instant", MetadataFieldType.STRING)));
                assertTrue(src.contains("Instant.parse((String) v)"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(fieldEnc("createdAt", "java.time.Instant", MetadataFieldType.STRING)));
                assertTrue(src.contains("obj.setCreatedAt(Instant.parse((String) v))"));
            }

            @Test
            void toMetadataMap_noChunkingApplied() {
                // ISO-8601 instant strings are always < 64 bytes — no splitStringEveryNCharacters
                String src = generate(List.of(fieldEnc("createdAt", "java.time.Instant", MetadataFieldType.STRING)));
                assertFalse(src.contains("splitStringEveryNCharacters"));
            }
        }
    }

    // =========================================================================
    // Duration fields
    // =========================================================================

    @Nested
    class DurationFields {

        // --- DEFAULT (total seconds) ---

        @Nested
        class DefaultEncoding {

            @Test
            void toMetadataMap_storesTotalSeconds() {
                String src = generate(List.of(field("duration", "java.time.Duration")));
                assertTrue(src.contains("BigInteger.valueOf(order.getDuration().getSeconds())"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(field("duration", "java.time.Duration")));
                assertTrue(src.contains("if (order.getDuration() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfBigIntegerGuard() {
                String src = generate(List.of(field("duration", "java.time.Duration")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
            }

            @Test
            void fromMetadataMap_restoredViaOfSeconds() {
                String src = generate(List.of(field("duration", "java.time.Duration")));
                assertTrue(src.contains("Duration.ofSeconds(((BigInteger) v).longValue())"));
            }
        }

        // --- STRING (ISO-8601) ---

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_storesIso8601() {
                String src = generate(List.of(fieldEnc("duration", "java.time.Duration", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"duration\", order.getDuration().toString())"));
            }

            @Test
            void fromMetadataMap_restoredViaParse() {
                String src = generate(List.of(fieldEnc("duration", "java.time.Duration", MetadataFieldType.STRING)));
                assertTrue(src.contains("Duration.parse((String) v)"));
            }
        }
    }

    // =========================================================================
    // LocalDate fields
    // =========================================================================

    @Nested
    class LocalDateFields {

        @Nested
        class DefaultEncoding {

            @Test
            void toMetadataMap_storesEpochDay() {
                String src = generate(List.of(field("date", "java.time.LocalDate")));
                assertTrue(src.contains("BigInteger.valueOf(order.getDate().toEpochDay())"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(field("date", "java.time.LocalDate")));
                assertTrue(src.contains("if (order.getDate() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfBigIntegerGuard() {
                String src = generate(List.of(field("date", "java.time.LocalDate")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
            }

            @Test
            void fromMetadataMap_restoredViaOfEpochDay() {
                String src = generate(List.of(field("date", "java.time.LocalDate")));
                assertTrue(src.contains("LocalDate.ofEpochDay(((BigInteger) v).longValue())"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(field("date", "java.time.LocalDate")));
                assertTrue(src.contains("obj.setDate(LocalDate.ofEpochDay(((BigInteger) v).longValue()))"));
            }
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_storesIso8601() {
                String src = generate(List.of(fieldEnc("date", "java.time.LocalDate", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"date\", order.getDate().toString())"));
            }

            @Test
            void fromMetadataMap_instanceOfStringGuard() {
                String src = generate(List.of(fieldEnc("date", "java.time.LocalDate", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (v instanceof String)"));
            }

            @Test
            void fromMetadataMap_restoredViaLocalDateParse() {
                String src = generate(List.of(fieldEnc("date", "java.time.LocalDate", MetadataFieldType.STRING)));
                assertTrue(src.contains("LocalDate.parse((String) v)"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(fieldEnc("date", "java.time.LocalDate", MetadataFieldType.STRING)));
                assertTrue(src.contains("obj.setDate(LocalDate.parse((String) v))"));
            }

            @Test
            void toMetadataMap_noChunkingApplied() {
                String src = generate(List.of(fieldEnc("date", "java.time.LocalDate", MetadataFieldType.STRING)));
                assertFalse(src.contains("splitStringEveryNCharacters"));
            }
        }
    }

    // =========================================================================
    // LocalDateTime fields
    // =========================================================================

    @Nested
    class LocalDateTimeFields {

        @Nested
        class DefaultEncoding {

            @Test
            void toMetadataMap_storesIso8601() {
                String src = generate(List.of(field("ts", "java.time.LocalDateTime")));
                assertTrue(src.contains("map.put(\"ts\", order.getTs().toString())"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(field("ts", "java.time.LocalDateTime")));
                assertTrue(src.contains("if (order.getTs() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfStringGuard() {
                String src = generate(List.of(field("ts", "java.time.LocalDateTime")));
                assertTrue(src.contains("if (v instanceof String)"));
            }

            @Test
            void fromMetadataMap_restoredViaLocalDateTimeParse() {
                String src = generate(List.of(field("ts", "java.time.LocalDateTime")));
                assertTrue(src.contains("LocalDateTime.parse((String) v)"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(field("ts", "java.time.LocalDateTime")));
                assertTrue(src.contains("obj.setTs(LocalDateTime.parse((String) v))"));
            }

            @Test
            void toMetadataMap_noChunkingApplied() {
                // ISO-8601 datetime strings are always < 64 bytes
                String src = generate(List.of(field("ts", "java.time.LocalDateTime")));
                assertFalse(src.contains("splitStringEveryNCharacters"));
            }
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_stringIsNoopSameAsDefault() {
                // enc=STRING on LocalDateTime is identical to DEFAULT — both store ISO-8601
                String srcDefault = generate(List.of(field("ts", "java.time.LocalDateTime")));
                String srcString  = generate(List.of(fieldEnc("ts", "java.time.LocalDateTime", MetadataFieldType.STRING)));
                assertEquals(srcDefault, srcString);
            }
        }
    }

    // =========================================================================
    // java.util.Date fields
    // =========================================================================

    @Nested
    class DateFields {

        @Nested
        class DefaultEncoding {

            @Test
            void toMetadataMap_storesEpochMillis() {
                String src = generate(List.of(field("updatedAt", "java.util.Date")));
                assertTrue(src.contains("BigInteger.valueOf(order.getUpdatedAt().getTime())"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(field("updatedAt", "java.util.Date")));
                assertTrue(src.contains("if (order.getUpdatedAt() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfBigIntegerGuard() {
                String src = generate(List.of(field("updatedAt", "java.util.Date")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
            }

            @Test
            void fromMetadataMap_restoredViaNewDate() {
                String src = generate(List.of(field("updatedAt", "java.util.Date")));
                assertTrue(src.contains("new Date(((BigInteger) v).longValue())"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(field("updatedAt", "java.util.Date")));
                assertTrue(src.contains("obj.setUpdatedAt(new Date(((BigInteger) v).longValue()))"));
            }
        }

        @Nested
        class StringEncoding {

            @Test
            void toMetadataMap_storesIso8601ViaInstant() {
                String src = generate(List.of(fieldEnc("updatedAt", "java.util.Date", MetadataFieldType.STRING)));
                assertTrue(src.contains("order.getUpdatedAt().toInstant().toString()"));
            }

            @Test
            void toMetadataMap_nullChecked() {
                String src = generate(List.of(fieldEnc("updatedAt", "java.util.Date", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (order.getUpdatedAt() != null)"));
            }

            @Test
            void fromMetadataMap_instanceOfStringGuard() {
                String src = generate(List.of(fieldEnc("updatedAt", "java.util.Date", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (v instanceof String)"));
            }

            @Test
            void fromMetadataMap_restoredViaDateFrom() {
                String src = generate(List.of(fieldEnc("updatedAt", "java.util.Date", MetadataFieldType.STRING)));
                assertTrue(src.contains("Date.from(Instant.parse((String) v))"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(fieldEnc("updatedAt", "java.util.Date", MetadataFieldType.STRING)));
                assertTrue(src.contains("obj.setUpdatedAt(Date.from(Instant.parse((String) v)))"));
            }
        }
    }

    // =========================================================================
    // List<T> fields
    // =========================================================================

    @Nested
    class ListFields {

        @Nested
        class StringElements {

            @Test
            void toMetadataMap_createsMetadataList() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("MetadataList _list = MetadataBuilder.createList()"));
            }

            @Test
            void toMetadataMap_iteratesOverGetterResult() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("for (String _el : order.getTags())"));
            }

            @Test
            void toMetadataMap_skipsNullElements() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el != null)"));
            }

            @Test
            void toMetadataMap_shortString_addsDirectly() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("_list.add(_el)"));
            }

            @Test
            void toMetadataMap_longString_createsSubList() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("MetadataList _elChunks = MetadataBuilder.createList()"));
                assertTrue(src.contains("splitStringEveryNCharacters(_el, 64)"));
                assertTrue(src.contains("_elChunks.add(_part)"));
                assertTrue(src.contains("_list.add(_elChunks)"));
            }

            @Test
            void toMetadataMap_checksByteLengthAgainst64() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("_el.getBytes("));
                assertTrue(src.contains("> 64"));
            }

            @Test
            void toMetadataMap_putsListUnderKey() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("map.put(\"tags\", _list)"));
            }

            @Test
            void toMetadataMap_fieldHasNullGuard() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("if (order.getTags() != null)"));
            }

            @Test
            void fromMetadataMap_checksInstanceOfMetadataList() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("if (v instanceof MetadataList)"));
            }

            @Test
            void fromMetadataMap_createsArrayList() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("_result = new ArrayList<>()"));
            }

            @Test
            void fromMetadataMap_plainStringBranch() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el instanceof String)"));
                assertTrue(src.contains("_result.add((String) _el)"));
            }

            @Test
            void fromMetadataMap_chunkedStringBranch() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("else if (_el instanceof MetadataList)"));
                assertTrue(src.contains("StringBuilder _sb = new StringBuilder()"));
                assertTrue(src.contains("_elList.getValueAt(_j)"));
                assertTrue(src.contains("_sb.append((String) _chunk)"));
                assertTrue(src.contains("_result.add(_sb.toString())"));
            }

            @Test
            void fromMetadataMap_callsSetter() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("obj.setTags(_result)"));
            }
        }

        @Nested
        class BigIntegerElements {

            @Test
            void toMetadataMap_addsElementDirectly() {
                String src = generate(List.of(listField("amounts", "java.math.BigInteger")));
                assertTrue(src.contains("for (BigInteger _el : order.getAmounts())"));
                assertTrue(src.contains("_addBigInt(_list, _el)"));
            }

            @Test
            void fromMetadataMap_castsBigInteger() {
                String src = generate(List.of(listField("amounts", "java.math.BigInteger")));
                assertTrue(src.contains("if (_el instanceof BigInteger)"));
                assertTrue(src.contains("_result.add((BigInteger) _el)"));
            }
        }

        @Nested
        class IntegerElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(listField("ids", "java.lang.Integer")));
                assertTrue(src.contains("for (Integer _el : order.getIds())"));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf((long) _el))"));
            }

            @Test
            void fromMetadataMap_extractsIntValue() {
                String src = generate(List.of(listField("ids", "java.lang.Integer")));
                assertTrue(src.contains("if (_el instanceof BigInteger)"));
                assertTrue(src.contains("_result.add(((BigInteger) _el).intValue())"));
            }
        }

        @Nested
        class LongElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(listField("timestamps", "java.lang.Long")));
                assertTrue(src.contains("for (Long _el : order.getTimestamps())"));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf(_el))"));
            }

            @Test
            void fromMetadataMap_extractsLongValue() {
                String src = generate(List.of(listField("timestamps", "java.lang.Long")));
                assertTrue(src.contains("_result.add(((BigInteger) _el).longValue())"));
            }
        }

        @Nested
        class BooleanElements {

            @Test
            void toMetadataMap_encodesAsBigIntegerOnOrZero() {
                String src = generate(List.of(listField("flags", "java.lang.Boolean")));
                assertTrue(src.contains("for (Boolean _el : order.getFlags())"));
                assertTrue(src.contains("_addBigInt(_list, _el ? BigInteger.ONE : BigInteger.ZERO)"));
            }

            @Test
            void fromMetadataMap_decodesBigIntegerOne() {
                String src = generate(List.of(listField("flags", "java.lang.Boolean")));
                assertTrue(src.contains("if (_el instanceof BigInteger)"));
                assertTrue(src.contains("_result.add(BigInteger.ONE.equals(_el))"));
            }
        }

        @Nested
        class DoubleElements {

            @Test
            void toMetadataMap_encodesAsStringValueOf() {
                String src = generate(List.of(listField("rates", "java.lang.Double")));
                assertTrue(src.contains("for (Double _el : order.getRates())"));
                assertTrue(src.contains("_list.add(String.valueOf(_el))"));
            }

            @Test
            void fromMetadataMap_parsesDouble() {
                String src = generate(List.of(listField("rates", "java.lang.Double")));
                assertTrue(src.contains("if (_el instanceof String)"));
                assertTrue(src.contains("_result.add(Double.parseDouble((String) _el))"));
            }
        }

        @Nested
        class FloatElements {

            @Test
            void toMetadataMap_encodesAsStringValueOf() {
                String src = generate(List.of(listField("weights", "java.lang.Float")));
                assertTrue(src.contains("_list.add(String.valueOf(_el))"));
            }

            @Test
            void fromMetadataMap_parsesFloat() {
                String src = generate(List.of(listField("weights", "java.lang.Float")));
                assertTrue(src.contains("_result.add(Float.parseFloat((String) _el))"));
            }
        }

        @Nested
        class BigDecimalElements {

            @Test
            void toMetadataMap_encodesViaToPlainString() {
                String src = generate(List.of(listField("prices", "java.math.BigDecimal")));
                assertTrue(src.contains("for (BigDecimal _el : order.getPrices())"));
                assertTrue(src.contains("_list.add(_el.toPlainString())"));
            }

            @Test
            void fromMetadataMap_parsesViaBigDecimalConstructor() {
                String src = generate(List.of(listField("prices", "java.math.BigDecimal")));
                assertTrue(src.contains("if (_el instanceof String)"));
                assertTrue(src.contains("_result.add(new BigDecimal((String) _el))"));
            }
        }

        @Nested
        class ByteArrayElements {

            @Test
            void toMetadataMap_addsElementDirectly() {
                String src = generate(List.of(listField("payloads", "byte[]")));
                assertTrue(src.contains("for (byte[] _el : order.getPayloads())"));
                assertTrue(src.contains("_list.add(_el)"));
            }

            @Test
            void fromMetadataMap_castsByteArray() {
                String src = generate(List.of(listField("payloads", "byte[]")));
                assertTrue(src.contains("if (_el instanceof byte[])"));
                assertTrue(src.contains("_result.add((byte[]) _el)"));
            }

            @Test
            void toMetadataMap_hexEncoding_usesHexUtil() {
                String src = generate(List.of(listFieldEnc("hashes", "byte[]", MetadataFieldType.STRING_HEX)));
                assertTrue(src.contains("HexUtil.encodeHexString(_el)"),
                        "Should encode byte[] elements as hex strings");
            }

            @Test
            void fromMetadataMap_hexEncoding_decodesHexString() {
                String src = generate(List.of(listFieldEnc("hashes", "byte[]", MetadataFieldType.STRING_HEX)));
                assertTrue(src.contains("HexUtil.decodeHexString((String) _el)"),
                        "Should decode hex string elements back to byte[]");
            }

            @Test
            void toMetadataMap_base64Encoding_usesBase64Encoder() {
                String src = generate(List.of(listFieldEnc("blobs", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertTrue(src.contains("Base64.getEncoder().encodeToString(_el)"),
                        "Should encode byte[] elements as Base64 strings");
            }

            @Test
            void fromMetadataMap_base64Encoding_decodesBase64String() {
                String src = generate(List.of(listFieldEnc("blobs", "byte[]", MetadataFieldType.STRING_BASE64)));
                assertTrue(src.contains("Base64.getDecoder().decode((String) _el)"),
                        "Should decode Base64 string elements back to byte[]");
            }
        }

        @Nested
        class ShortElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(listField("counts", "java.lang.Short")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf((long) _el))"));
            }

            @Test
            void fromMetadataMap_extractsShortValue() {
                String src = generate(List.of(listField("counts", "java.lang.Short")));
                assertTrue(src.contains("_result.add(((BigInteger) _el).shortValue())"));
            }
        }

        @Nested
        class ByteElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(listField("bytes", "java.lang.Byte")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf((long) _el))"));
            }

            @Test
            void fromMetadataMap_extractsByteValue() {
                String src = generate(List.of(listField("bytes", "java.lang.Byte")));
                assertTrue(src.contains("_result.add(((BigInteger) _el).byteValue())"));
            }
        }

        @Nested
        class CharacterElements {

            @Test
            void toMetadataMap_encodesAsStringValueOf() {
                String src = generate(List.of(listField("chars", "java.lang.Character")));
                assertTrue(src.contains("_list.add(String.valueOf(_el))"));
            }

            @Test
            void fromMetadataMap_extractsCharAtZero() {
                String src = generate(List.of(listField("chars", "java.lang.Character")));
                assertTrue(src.contains("_result.add(((String) _el).charAt(0))"));
            }
        }

        @Nested
        class UriElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(listField("links", "java.net.URI")));
                assertTrue(src.contains("_list.add(_el.toString())"));
            }

            @Test
            void fromMetadataMap_createsViaUriCreate() {
                String src = generate(List.of(listField("links", "java.net.URI")));
                assertTrue(src.contains("_result.add(URI.create((String) _el))"));
            }
        }

        @Nested
        class UrlElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(listField("links", "java.net.URL")));
                assertTrue(src.contains("_list.add(_el.toString())"));
            }

            @Test
            void fromMetadataMap_createsViaNewUrl() {
                String src = generate(List.of(listField("links", "java.net.URL")));
                assertTrue(src.contains("_result.add(new URL((String) _el))"));
                assertTrue(src.contains("catch (MalformedURLException _e)"));
            }
        }

        @Nested
        class UuidElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(listField("ids", "java.util.UUID")));
                assertTrue(src.contains("_list.add(_el.toString())"));
            }

            @Test
            void fromMetadataMap_parsesViaFromString() {
                String src = generate(List.of(listField("ids", "java.util.UUID")));
                assertTrue(src.contains("_result.add(UUID.fromString((String) _el))"));
            }
        }

        @Nested
        class CurrencyElements {

            @Test
            void toMetadataMap_encodesViaCurrencyCode() {
                String src = generate(List.of(listField("currencies", "java.util.Currency")));
                assertTrue(src.contains("_list.add(_el.getCurrencyCode())"));
            }

            @Test
            void fromMetadataMap_parsesViaGetInstance() {
                String src = generate(List.of(listField("currencies", "java.util.Currency")));
                assertTrue(src.contains("_result.add(Currency.getInstance((String) _el))"));
            }
        }

        @Nested
        class LocaleElements {

            @Test
            void toMetadataMap_encodesViaLanguageTag() {
                String src = generate(List.of(listField("locales", "java.util.Locale")));
                assertTrue(src.contains("_list.add(_el.toLanguageTag())"));
            }

            @Test
            void fromMetadataMap_parsesViaForLanguageTag() {
                String src = generate(List.of(listField("locales", "java.util.Locale")));
                assertTrue(src.contains("_result.add(Locale.forLanguageTag((String) _el))"));
            }
        }

        @Nested
        class InstantElements {

            @Test
            void toMetadataMap_encodesAsEpochSeconds() {
                String src = generate(List.of(listField("times", "java.time.Instant")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf(_el.getEpochSecond()))"));
            }

            @Test
            void fromMetadataMap_restoresViaOfEpochSecond() {
                String src = generate(List.of(listField("times", "java.time.Instant")));
                assertTrue(src.contains("_result.add(Instant.ofEpochSecond(((BigInteger) _el).longValue()))"));
            }
        }

        @Nested
        class LocalDateElements {

            @Test
            void toMetadataMap_encodesAsEpochDay() {
                String src = generate(List.of(listField("dates", "java.time.LocalDate")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf(_el.toEpochDay()))"));
            }

            @Test
            void fromMetadataMap_restoresViaOfEpochDay() {
                String src = generate(List.of(listField("dates", "java.time.LocalDate")));
                assertTrue(src.contains("_result.add(LocalDate.ofEpochDay(((BigInteger) _el).longValue()))"));
            }
        }

        @Nested
        class LocalDateTimeElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(listField("timestamps", "java.time.LocalDateTime")));
                assertTrue(src.contains("_list.add(_el.toString())"));
            }

            @Test
            void fromMetadataMap_restoresViaParse() {
                String src = generate(List.of(listField("timestamps", "java.time.LocalDateTime")));
                assertTrue(src.contains("_result.add(LocalDateTime.parse((String) _el))"));
            }
        }

        @Nested
        class DateElements {

            @Test
            void toMetadataMap_encodesAsEpochMillis() {
                String src = generate(List.of(listField("dates", "java.util.Date")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf(_el.getTime()))"));
            }

            @Test
            void fromMetadataMap_restoresViaNewDate() {
                String src = generate(List.of(listField("dates", "java.util.Date")));
                assertTrue(src.contains("_result.add(new Date(((BigInteger) _el).longValue()))"));
            }
        }

        @Nested
        class DurationElements {

            @Test
            void toMetadataMap_encodesAsTotalSeconds() {
                String src = generate(List.of(listField("durations", "java.time.Duration")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf(_el.getSeconds()))"));
            }

            @Test
            void fromMetadataMap_restoresViaOfSeconds() {
                String src = generate(List.of(listField("durations", "java.time.Duration")));
                assertTrue(src.contains("_result.add(Duration.ofSeconds(((BigInteger) _el).longValue()))"));
            }
        }

        @Nested
        class NullGuard {

            @Test
            void listField_alwaysHasNullGuardForList() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("if (order.getTags() != null)"));
            }

            @Test
            void listField_nullElementsWithinListAreSkipped() {
                String src = generate(List.of(listField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el != null)"));
            }

            @Test
            void multipleListFields_noVariableNameCollision() {
                String src = generate(List.of(
                        listField("tags", "java.lang.String"),
                        listField("ids", "java.lang.Integer")
                ));
                assertTrue(src.contains("map.put(\"tags\", _list)"));
                assertTrue(src.contains("map.put(\"ids\", _list)"));
                assertTrue(src.contains("obj.setTags(_result)"));
                assertTrue(src.contains("obj.setIds(_result)"));
            }
        }
    }

    // =========================================================================
    // Set<T> fields
    // =========================================================================

    @Nested
    class SetFields {

        @Nested
        class ContainerType {

            @Test
            void toMetadataMap_sameAsListSerialisation() {
                String srcSet  = generate(List.of(setField("tags", "java.lang.String")));
                // Serialisation loop is identical — both use MetadataList _list
                assertTrue(srcSet.contains("MetadataList _list = MetadataBuilder.createList()"));
                assertTrue(srcSet.contains("for (String _el : order.getTags())"));
                assertTrue(srcSet.contains("map.put(\"tags\", _list)"));
            }

            @Test
            void fromMetadataMap_declaredTypeIsSet() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("Set<String> _result"));
                assertFalse(src.contains("List<String> _result"));
            }

            @Test
            void fromMetadataMap_implementationIsLinkedHashSet() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("new LinkedHashSet<>()"));
                assertFalse(src.contains("new ArrayList<>()"));
            }

            @Test
            void fieldHasNullGuard() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("if (order.getTags() != null)"));
            }

            @Test
            void nullElementsWithinSetAreSkipped() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el != null)"));
            }
        }

        @Nested
        class StringElements {

            @Test
            void toMetadataMap_shortString_addsDirectly() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("_list.add(_el)"));
            }

            @Test
            void toMetadataMap_longString_createsSubList() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("MetadataList _elChunks = MetadataBuilder.createList()"));
                assertTrue(src.contains("splitStringEveryNCharacters(_el, 64)"));
                assertTrue(src.contains("_list.add(_elChunks)"));
            }

            @Test
            void fromMetadataMap_plainStringBranch() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el instanceof String)"));
                assertTrue(src.contains("_result.add((String) _el)"));
            }

            @Test
            void fromMetadataMap_chunkedStringBranch() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("else if (_el instanceof MetadataList)"));
                assertTrue(src.contains("_result.add(_sb.toString())"));
            }

            @Test
            void fromMetadataMap_callsSetter() {
                String src = generate(List.of(setField("tags", "java.lang.String")));
                assertTrue(src.contains("obj.setTags(_result)"));
            }
        }

        @Nested
        class BigIntegerElements {

            @Test
            void toMetadataMap_addsElementDirectly() {
                String src = generate(List.of(setField("amounts", "java.math.BigInteger")));
                assertTrue(src.contains("for (BigInteger _el : order.getAmounts())"));
                assertTrue(src.contains("_addBigInt(_list, _el)"));
            }

            @Test
            void fromMetadataMap_castsBigInteger() {
                String src = generate(List.of(setField("amounts", "java.math.BigInteger")));
                assertTrue(src.contains("if (_el instanceof BigInteger)"));
                assertTrue(src.contains("_result.add((BigInteger) _el)"));
            }
        }

        @Nested
        class IntegerElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(setField("ids", "java.lang.Integer")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf((long) _el))"));
            }

            @Test
            void fromMetadataMap_extractsIntValue() {
                String src = generate(List.of(setField("ids", "java.lang.Integer")));
                assertTrue(src.contains("_result.add(((BigInteger) _el).intValue())"));
            }
        }

        @Nested
        class BooleanElements {

            @Test
            void toMetadataMap_encodesAsBigIntegerOneOrZero() {
                String src = generate(List.of(setField("flags", "java.lang.Boolean")));
                assertTrue(src.contains("_addBigInt(_list, _el ? BigInteger.ONE : BigInteger.ZERO)"));
            }

            @Test
            void fromMetadataMap_decodesBigIntegerOne() {
                String src = generate(List.of(setField("flags", "java.lang.Boolean")));
                assertTrue(src.contains("_result.add(BigInteger.ONE.equals(_el))"));
            }
        }

        @Nested
        class BigDecimalElements {

            @Test
            void toMetadataMap_encodesViaToPlainString() {
                String src = generate(List.of(setField("prices", "java.math.BigDecimal")));
                assertTrue(src.contains("_list.add(_el.toPlainString())"));
            }

            @Test
            void fromMetadataMap_parsesViaBigDecimalConstructor() {
                String src = generate(List.of(setField("prices", "java.math.BigDecimal")));
                assertTrue(src.contains("_result.add(new BigDecimal((String) _el))"));
            }
        }

        @Nested
        class MixedListAndSet {

            @Test
            void listAndSetInSameClass_noVariableNameCollision() {
                String src = generate(List.of(
                        listField("tags", "java.lang.String"),
                        setField("ids", "java.lang.Integer")
                ));
                assertTrue(src.contains("List<String> _result"));
                assertTrue(src.contains("Set<Integer> _result"));
                assertTrue(src.contains("new ArrayList<>()"));
                assertTrue(src.contains("new LinkedHashSet<>()"));
            }
        }
    }

    // =========================================================================
    // SortedSet<T> fields
    // =========================================================================

    @Nested
    class SortedSetFields {

        @Nested
        class ContainerType {

            @Test
            void toMetadataMap_sameAsListSerialisation() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("MetadataList _list = MetadataBuilder.createList()"));
                assertTrue(src.contains("for (String _el : order.getTags())"));
                assertTrue(src.contains("map.put(\"tags\", _list)"));
            }

            @Test
            void fromMetadataMap_declaredTypeIsSortedSet() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("SortedSet<String> _result"));
                assertFalse(src.contains("List<String> _result"));
                // "SortedSet<String>" contains "Set<String>" as a substring, so we check
                // for the plain-Set declaration with a leading space to avoid false positive
                assertFalse(src.contains(" Set<String> _result"));
            }

            @Test
            void fromMetadataMap_implementationIsTreeSet() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("new TreeSet<>()"));
                assertFalse(src.contains("new ArrayList<>()"));
                assertFalse(src.contains("new LinkedHashSet<>()"));
            }

            @Test
            void fieldHasNullGuard() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("if (order.getTags() != null)"));
            }

            @Test
            void nullElementsWithinSetAreSkipped() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el != null)"));
            }
        }

        @Nested
        class StringElements {

            @Test
            void toMetadataMap_shortString_addsDirectly() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("_list.add(_el)"));
            }

            @Test
            void toMetadataMap_longString_createsSubList() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("MetadataList _elChunks = MetadataBuilder.createList()"));
                assertTrue(src.contains("splitStringEveryNCharacters(_el, 64)"));
                assertTrue(src.contains("_list.add(_elChunks)"));
            }

            @Test
            void fromMetadataMap_plainStringBranch() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("if (_el instanceof String)"));
                assertTrue(src.contains("_result.add((String) _el)"));
            }

            @Test
            void fromMetadataMap_chunkedStringBranch() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("else if (_el instanceof MetadataList)"));
                assertTrue(src.contains("_result.add(_sb.toString())"));
            }

            @Test
            void fromMetadataMap_callsSetter() {
                String src = generate(List.of(sortedSetField("tags", "java.lang.String")));
                assertTrue(src.contains("obj.setTags(_result)"));
            }
        }

        @Nested
        class BigIntegerElements {

            @Test
            void toMetadataMap_addsElementDirectly() {
                String src = generate(List.of(sortedSetField("amounts", "java.math.BigInteger")));
                assertTrue(src.contains("for (BigInteger _el : order.getAmounts())"));
                assertTrue(src.contains("_addBigInt(_list, _el)"));
            }

            @Test
            void fromMetadataMap_castsBigInteger() {
                String src = generate(List.of(sortedSetField("amounts", "java.math.BigInteger")));
                assertTrue(src.contains("if (_el instanceof BigInteger)"));
                assertTrue(src.contains("_result.add((BigInteger) _el)"));
            }
        }

        @Nested
        class IntegerElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(sortedSetField("ids", "java.lang.Integer")));
                assertTrue(src.contains("_addBigInt(_list, BigInteger.valueOf((long) _el))"));
            }

            @Test
            void fromMetadataMap_extractsIntValue() {
                String src = generate(List.of(sortedSetField("ids", "java.lang.Integer")));
                assertTrue(src.contains("_result.add(((BigInteger) _el).intValue())"));
            }
        }

        @Nested
        class BooleanElements {

            @Test
            void toMetadataMap_encodesAsBigIntegerOneOrZero() {
                String src = generate(List.of(sortedSetField("flags", "java.lang.Boolean")));
                assertTrue(src.contains("_addBigInt(_list, _el ? BigInteger.ONE : BigInteger.ZERO)"));
            }

            @Test
            void fromMetadataMap_decodesBigIntegerOne() {
                String src = generate(List.of(sortedSetField("flags", "java.lang.Boolean")));
                assertTrue(src.contains("_result.add(BigInteger.ONE.equals(_el))"));
            }
        }

        @Nested
        class BigDecimalElements {

            @Test
            void toMetadataMap_encodesViaToPlainString() {
                String src = generate(List.of(sortedSetField("prices", "java.math.BigDecimal")));
                assertTrue(src.contains("_list.add(_el.toPlainString())"));
            }

            @Test
            void fromMetadataMap_parsesViaBigDecimalConstructor() {
                String src = generate(List.of(sortedSetField("prices", "java.math.BigDecimal")));
                assertTrue(src.contains("_result.add(new BigDecimal((String) _el))"));
            }
        }

        @Nested
        class MixedAllCollections {

            @Test
            void listSetAndSortedSet_noVariableNameCollision() {
                String src = generate(List.of(
                        listField("tags", "java.lang.String"),
                        setField("ids", "java.lang.Integer"),
                        sortedSetField("amounts", "java.math.BigInteger")
                ));
                assertTrue(src.contains("List<String> _result"));
                assertTrue(src.contains("Set<Integer> _result"));
                assertTrue(src.contains("SortedSet<BigInteger> _result"));
                assertTrue(src.contains("new ArrayList<>()"));
                assertTrue(src.contains("new LinkedHashSet<>()"));
                assertTrue(src.contains("new TreeSet<>()"));
            }
        }
    }

    // =========================================================================
    // Optional<T> fields
    // =========================================================================

    @Nested
    class OptionalFields {

        @Nested
        class ContainerType {

            @Test
            void toMetadataMap_hasNullGuard() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("if (order.getTags() != null)"));
            }

            @Test
            void toMetadataMap_emitsIsPresentCheck() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("if (order.getTags().isPresent())"));
            }

            @Test
            void fromMetadataMap_presentBranch_wrapsWithOptionalOf() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("Optional.of("));
            }

            @Test
            void fromMetadataMap_absentBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("Optional.empty()"));
            }
        }

        @Nested
        class StringElements {

            @Test
            void toMetadataMap_usesGetDotGet() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("order.getTags().get()"));
            }

            @Test
            void toMetadataMap_longString_chunksViaGet() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("splitStringEveryNCharacters(order.getTags().get(), 64)"));
            }

            @Test
            void fromMetadataMap_instanceOfString_setsOptionalOf() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("if (v instanceof String)"));
                assertTrue(src.contains("obj.setTags(Optional.of((String) v))"));
            }

            @Test
            void fromMetadataMap_metadataListBranch_setsOptionalOfAssembled() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("else if (v instanceof MetadataList)"));
                assertTrue(src.contains("obj.setTags(Optional.of(_sb.toString()))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("tags", "java.lang.String")));
                assertTrue(src.contains("obj.setTags(Optional.empty())"));
            }
        }

        @Nested
        class BigIntegerElements {

            @Test
            void toMetadataMap_putsGetResult() {
                String src = generate(List.of(optionalField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("_putBigInt(map, \"amount\", order.getAmount().get())"));
            }

            @Test
            void fromMetadataMap_instanceOfBigInteger_setsOptionalOf() {
                String src = generate(List.of(optionalField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
                assertTrue(src.contains("obj.setAmount(Optional.of((BigInteger) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("obj.setAmount(Optional.empty())"));
            }
        }

        @Nested
        class IntegerElements {

            @Test
            void toMetadataMap_castToLongThenWrapped() {
                String src = generate(List.of(optionalField("qty", "java.lang.Integer")));
                assertTrue(src.contains("BigInteger.valueOf((long) order.getQty().get())"));
            }

            @Test
            void fromMetadataMap_instanceOfBigInteger_extractsIntValue() {
                String src = generate(List.of(optionalField("qty", "java.lang.Integer")));
                assertTrue(src.contains("obj.setQty(Optional.of(((BigInteger) v).intValue()))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("qty", "java.lang.Integer")));
                assertTrue(src.contains("obj.setQty(Optional.empty())"));
            }
        }

        @Nested
        class BooleanElements {

            @Test
            void toMetadataMap_ternaryBigInteger() {
                String src = generate(List.of(optionalField("active", "java.lang.Boolean")));
                assertTrue(src.contains("order.getActive().get() ? BigInteger.ONE : BigInteger.ZERO"));
            }

            @Test
            void fromMetadataMap_instanceOfBigInteger_checksBigIntegerOne() {
                String src = generate(List.of(optionalField("active", "java.lang.Boolean")));
                assertTrue(src.contains("obj.setActive(Optional.of(BigInteger.ONE.equals(v)))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("active", "java.lang.Boolean")));
                assertTrue(src.contains("obj.setActive(Optional.empty())"));
            }
        }

        @Nested
        class BigDecimalElements {

            @Test
            void toMetadataMap_usesToPlainString() {
                String src = generate(List.of(optionalField("price", "java.math.BigDecimal")));
                assertTrue(src.contains("order.getPrice().get().toPlainString()"));
            }

            @Test
            void fromMetadataMap_parsedViaNewBigDecimal() {
                String src = generate(List.of(optionalField("price", "java.math.BigDecimal")));
                assertTrue(src.contains("obj.setPrice(Optional.of(new BigDecimal((String) v)))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("price", "java.math.BigDecimal")));
                assertTrue(src.contains("obj.setPrice(Optional.empty())"));
            }
        }

        @Nested
        class ByteArrayElements {

            @Test
            void toMetadataMap_putsGetResult() {
                String src = generate(List.of(optionalField("sig", "byte[]")));
                assertTrue(src.contains("map.put(\"sig\", order.getSig().get())"));
            }

            @Test
            void fromMetadataMap_instanceOfByteArray_setsOptionalOf() {
                String src = generate(List.of(optionalField("sig", "byte[]")));
                assertTrue(src.contains("if (v instanceof byte[])"));
                assertTrue(src.contains("obj.setSig(Optional.of((byte[]) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("sig", "byte[]")));
                assertTrue(src.contains("obj.setSig(Optional.empty())"));
            }
        }

        @Nested
        class LongElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(optionalField("x", "java.lang.Long")));
                assertTrue(src.contains("BigInteger.valueOf(order.getX().get())"));
            }

            @Test
            void fromMetadataMap_extractsLongValue() {
                String src = generate(List.of(optionalField("x", "java.lang.Long")));
                assertTrue(src.contains("Optional.of(((BigInteger) v).longValue())"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.lang.Long")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class ShortElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(optionalField("x", "java.lang.Short")));
                assertTrue(src.contains("BigInteger.valueOf((long) order.getX().get())"));
            }

            @Test
            void fromMetadataMap_extractsShortValue() {
                String src = generate(List.of(optionalField("x", "java.lang.Short")));
                assertTrue(src.contains("Optional.of(((BigInteger) v).shortValue())"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.lang.Short")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class ByteElements {

            @Test
            void toMetadataMap_wrapsInBigIntegerValueOf() {
                String src = generate(List.of(optionalField("x", "java.lang.Byte")));
                assertTrue(src.contains("BigInteger.valueOf((long) order.getX().get())"));
            }

            @Test
            void fromMetadataMap_extractsByteValue() {
                String src = generate(List.of(optionalField("x", "java.lang.Byte")));
                assertTrue(src.contains("Optional.of(((BigInteger) v).byteValue())"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.lang.Byte")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class CharacterElements {

            @Test
            void toMetadataMap_encodesAsStringValueOf() {
                String src = generate(List.of(optionalField("x", "java.lang.Character")));
                assertTrue(src.contains("String.valueOf(order.getX().get())"));
            }

            @Test
            void fromMetadataMap_extractsCharAtZero() {
                String src = generate(List.of(optionalField("x", "java.lang.Character")));
                assertTrue(src.contains("Optional.of(((String) v).charAt(0))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.lang.Character")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class DoubleElements {

            @Test
            void toMetadataMap_encodesAsStringValueOf() {
                String src = generate(List.of(optionalField("x", "java.lang.Double")));
                assertTrue(src.contains("String.valueOf(order.getX().get())"));
            }

            @Test
            void fromMetadataMap_parsesDouble() {
                String src = generate(List.of(optionalField("x", "java.lang.Double")));
                assertTrue(src.contains("Optional.of(Double.parseDouble((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.lang.Double")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class FloatElements {

            @Test
            void toMetadataMap_encodesAsStringValueOf() {
                String src = generate(List.of(optionalField("x", "java.lang.Float")));
                assertTrue(src.contains("String.valueOf(order.getX().get())"));
            }

            @Test
            void fromMetadataMap_parsesFloat() {
                String src = generate(List.of(optionalField("x", "java.lang.Float")));
                assertTrue(src.contains("Optional.of(Float.parseFloat((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.lang.Float")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class UriElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(optionalField("x", "java.net.URI")));
                assertTrue(src.contains("order.getX().get().toString()"));
            }

            @Test
            void fromMetadataMap_createsViaUriCreate() {
                String src = generate(List.of(optionalField("x", "java.net.URI")));
                assertTrue(src.contains("Optional.of(URI.create((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.net.URI")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class UrlElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(optionalField("x", "java.net.URL")));
                assertTrue(src.contains("order.getX().get().toString()"));
            }

            @Test
            void fromMetadataMap_createsViaNewUrl() {
                String src = generate(List.of(optionalField("x", "java.net.URL")));
                assertTrue(src.contains("Optional.of(new URL((String) v))"));
                assertTrue(src.contains("catch (MalformedURLException _e)"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.net.URL")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class UuidElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(optionalField("x", "java.util.UUID")));
                assertTrue(src.contains("order.getX().get().toString()"));
            }

            @Test
            void fromMetadataMap_parsesViaFromString() {
                String src = generate(List.of(optionalField("x", "java.util.UUID")));
                assertTrue(src.contains("Optional.of(UUID.fromString((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.util.UUID")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class CurrencyElements {

            @Test
            void toMetadataMap_encodesViaCurrencyCode() {
                String src = generate(List.of(optionalField("x", "java.util.Currency")));
                assertTrue(src.contains("order.getX().get().getCurrencyCode()"));
            }

            @Test
            void fromMetadataMap_parsesViaGetInstance() {
                String src = generate(List.of(optionalField("x", "java.util.Currency")));
                assertTrue(src.contains("Optional.of(Currency.getInstance((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.util.Currency")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class LocaleElements {

            @Test
            void toMetadataMap_encodesViaLanguageTag() {
                String src = generate(List.of(optionalField("x", "java.util.Locale")));
                assertTrue(src.contains("order.getX().get().toLanguageTag()"));
            }

            @Test
            void fromMetadataMap_parsesViaForLanguageTag() {
                String src = generate(List.of(optionalField("x", "java.util.Locale")));
                assertTrue(src.contains("Optional.of(Locale.forLanguageTag((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.util.Locale")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class InstantElements {

            @Test
            void toMetadataMap_encodesAsEpochSeconds() {
                String src = generate(List.of(optionalField("x", "java.time.Instant")));
                assertTrue(src.contains("BigInteger.valueOf(order.getX().get().getEpochSecond())"));
            }

            @Test
            void fromMetadataMap_restoresViaOfEpochSecond() {
                String src = generate(List.of(optionalField("x", "java.time.Instant")));
                assertTrue(src.contains("Optional.of(Instant.ofEpochSecond(((BigInteger) v).longValue()))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.time.Instant")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class LocalDateElements {

            @Test
            void toMetadataMap_encodesAsEpochDay() {
                String src = generate(List.of(optionalField("x", "java.time.LocalDate")));
                assertTrue(src.contains("BigInteger.valueOf(order.getX().get().toEpochDay())"));
            }

            @Test
            void fromMetadataMap_restoresViaOfEpochDay() {
                String src = generate(List.of(optionalField("x", "java.time.LocalDate")));
                assertTrue(src.contains("Optional.of(LocalDate.ofEpochDay(((BigInteger) v).longValue()))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.time.LocalDate")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class LocalDateTimeElements {

            @Test
            void toMetadataMap_encodesViaToString() {
                String src = generate(List.of(optionalField("x", "java.time.LocalDateTime")));
                assertTrue(src.contains("order.getX().get().toString()"));
            }

            @Test
            void fromMetadataMap_restoresViaParse() {
                String src = generate(List.of(optionalField("x", "java.time.LocalDateTime")));
                assertTrue(src.contains("Optional.of(LocalDateTime.parse((String) v))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.time.LocalDateTime")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class DateElements {

            @Test
            void toMetadataMap_encodesAsEpochMillis() {
                String src = generate(List.of(optionalField("x", "java.util.Date")));
                assertTrue(src.contains("BigInteger.valueOf(order.getX().get().getTime())"));
            }

            @Test
            void fromMetadataMap_restoresViaNewDate() {
                String src = generate(List.of(optionalField("x", "java.util.Date")));
                assertTrue(src.contains("Optional.of(new Date(((BigInteger) v).longValue()))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.util.Date")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }

        @Nested
        class DurationElements {

            @Test
            void toMetadataMap_encodesAsTotalSeconds() {
                String src = generate(List.of(optionalField("x", "java.time.Duration")));
                assertTrue(src.contains("BigInteger.valueOf(order.getX().get().getSeconds())"));
            }

            @Test
            void fromMetadataMap_restoresViaOfSeconds() {
                String src = generate(List.of(optionalField("x", "java.time.Duration")));
                assertTrue(src.contains("Optional.of(Duration.ofSeconds(((BigInteger) v).longValue()))"));
            }

            @Test
            void fromMetadataMap_elseBranch_setsOptionalEmpty() {
                String src = generate(List.of(optionalField("x", "java.time.Duration")));
                assertTrue(src.contains("obj.setX(Optional.empty())"));
            }
        }
    }

    // =========================================================================
    // Enum in collections / Optional
    // =========================================================================

    @Nested
    class EnumInCollections {

        private static final String ENUM_FQN = "com.example.OrderStatus";

        /** Marks an existing collection / Optional field as having an enum element type. */
        private MetadataFieldInfo asEnumElement(MetadataFieldInfo f) {
            f.setElement(new MetadataFieldInfo.ElementInfo(f.getElementTypeName(), new MetadataFieldInfo.LeafClassification(true, false, null), null, null));
            return f;
        }

        @Nested
        class ElementCodeShared {
            // The per-element serialization/deserialization code (_list.add, _result.add)
            // is identical across List, Set and SortedSet — verify once here.

            @Test
            void toMetadataMap_addsEnumNameToList() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("_list.add(_el.name())"),
                        "Expected _list.add(_el.name()) but got:\n" + src);
            }

            @Test
            void toMetadataMap_iteratesWithEnumType() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("OrderStatus _el"), "Expected loop variable of enum type");
            }

            @Test
            void fromMetadataMap_checksMetadataList() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("if (v instanceof MetadataList)"));
            }

            @Test
            void fromMetadataMap_checksStringInstance() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("if (_el instanceof String)"));
            }

            @Test
            void fromMetadataMap_addsViaEnumValueOf() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("OrderStatus.valueOf((String) _el)"),
                        "Expected OrderStatus.valueOf((String) _el) but got:\n" + src);
            }

            @Test
            void fromMetadataMap_setsResult() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("obj.setStatuses(_result)"));
            }
        }

        @Nested
        class ContainerTypes {

            @Test
            void list_usesArrayList() {
                String src = generate(List.of(asEnumElement(listField("statuses", ENUM_FQN))));
                assertTrue(src.contains("new ArrayList<>()"));
            }

            @Test
            void set_usesLinkedHashSet() {
                String src = generate(List.of(asEnumElement(setField("statuses", ENUM_FQN))));
                assertTrue(src.contains("new LinkedHashSet<>()"));
            }

            @Test
            void sortedSet_usesTreeSet() {
                String src = generate(List.of(asEnumElement(sortedSetField("statuses", ENUM_FQN))));
                assertTrue(src.contains("new TreeSet<>()"));
            }
        }

        @Nested
        class OptionalOfEnum {

            @Test
            void toMetadataMap_presentValue_storesName() {
                String src = generate(List.of(asEnumElement(optionalField("status", ENUM_FQN))));
                assertTrue(src.contains("order.getStatus().get().name()"),
                        "Expected .get().name() for present Optional enum\n" + src);
            }

            @Test
            void toMetadataMap_wrapsInIsPresentCheck() {
                String src = generate(List.of(asEnumElement(optionalField("status", ENUM_FQN))));
                assertTrue(src.contains("if (order.getStatus().isPresent())"));
            }

            @Test
            void fromMetadataMap_checksStringInstance() {
                String src = generate(List.of(asEnumElement(optionalField("status", ENUM_FQN))));
                assertTrue(src.contains("if (v instanceof String)"));
            }

            @Test
            void fromMetadataMap_setsOptionalOf() {
                String src = generate(List.of(asEnumElement(optionalField("status", ENUM_FQN))));
                assertTrue(src.contains("Optional.of(OrderStatus.valueOf((String) v))"),
                        "Expected Optional.of(OrderStatus.valueOf(...)) but got:\n" + src);
            }

            @Test
            void fromMetadataMap_setsOptionalEmpty_onMismatch() {
                String src = generate(List.of(asEnumElement(optionalField("status", ENUM_FQN))));
                assertTrue(src.contains("Optional.empty()"));
            }

            @Test
            void fromMetadataMap_setterCalled() {
                String src = generate(List.of(asEnumElement(optionalField("status", ENUM_FQN))));
                assertTrue(src.contains("obj.setStatus("));
            }
        }
    }

    // =========================================================================
    // Key mapping
    // =========================================================================

    @Nested
    class KeyMapping {

        @Test
        void toMetadataMap_defaultKey_usesFieldName() {
            String src = generate(List.of(field("recipient", "java.lang.String")));
            assertTrue(src.contains("map.put(\"recipient\""));
        }

        @Test
        void toMetadataMap_customKey_usesMetadataKeyInsteadOfFieldName() {
            String src = generate(List.of(field("referenceId", "ref_id", "java.lang.String")));
            assertTrue(src.contains("map.put(\"ref_id\""));
            assertFalse(src.contains("map.put(\"referenceId\""));
        }

        @Test
        void fromMetadataMap_defaultKey_readsFromFieldName() {
            String src = generate(List.of(field("recipient", "java.lang.String")));
            assertTrue(src.contains("map.get(\"recipient\")"));
        }

        @Test
        void fromMetadataMap_customKey_readsFromMetadataKey() {
            String src = generate(List.of(field("referenceId", "ref_id", "java.lang.String")));
            assertTrue(src.contains("map.get(\"ref_id\")"));
            assertFalse(src.contains("map.get(\"referenceId\")"));
        }
    }

    // =========================================================================
    // Direct-field access
    // =========================================================================

    @Nested
    class DirectFieldAccess {

        @Nested
        class ScalarFields {

            @Test
            void toMetadataMap_stringField_readsPublicFieldDirectly() {
                String src = generate(List.of(directField("note", "java.lang.String")));
                assertTrue(src.contains("order.note"));
                assertFalse(src.contains("order.getNote()"));
            }

            @Test
            void toMetadataMap_bigIntegerField_readsPublicFieldDirectly() {
                String src = generate(List.of(directField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("order.amount"));
                assertFalse(src.contains("order.getAmount()"));
            }

            @Test
            void fromMetadataMap_stringField_assignsPublicFieldDirectly() {
                String src = generate(List.of(directField("note", "java.lang.String")));
                assertTrue(src.contains("obj.note = (String) v"));
                assertFalse(src.contains("obj.setNote("));
            }

            @Test
            void fromMetadataMap_bigIntegerField_assignsPublicFieldDirectly() {
                String src = generate(List.of(directField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("obj.amount = (BigInteger) v"));
                assertFalse(src.contains("obj.setAmount("));
            }
        }

        @Nested
        class OptionalFields {

            /** Optional field with no getter/setter — direct public field access. */
            private MetadataFieldInfo directOptionalField(String name, String elementType) {
                MetadataFieldInfo f = new MetadataFieldInfo();
                f.setJavaFieldName(name);
                f.setMetadataKey(name);
                f.setJavaTypeName("java.util.Optional<" + elementType + ">");
                f.setElement(new MetadataFieldInfo.ElementInfo(elementType, MetadataFieldInfo.LeafClassification.NONE, null, null));
                f.setOptionalType(true);
                return f;
            }

            @Nested
            class StringElements {

                @Test
                void toMetadataMap_readsDirectField() {
                    String src = generate(List.of(directOptionalField("tags", "java.lang.String")));
                    assertTrue(src.contains("order.tags.get()"));
                    assertFalse(src.contains("order.getTags()"));
                }

                @Test
                void fromMetadataMap_assignsDirectField() {
                    String src = generate(List.of(directOptionalField("tags", "java.lang.String")));
                    assertTrue(src.contains("obj.tags = Optional.of("));
                    assertFalse(src.contains("obj.setTags("));
                }

                @Test
                void fromMetadataMap_assignsOptionalEmpty() {
                    String src = generate(List.of(directOptionalField("tags", "java.lang.String")));
                    assertTrue(src.contains("obj.tags = Optional.empty()"));
                }
            }

            @Nested
            class BigIntegerElements {

                @Test
                void toMetadataMap_readsDirectField() {
                    String src = generate(List.of(directOptionalField("amount", "java.math.BigInteger")));
                    assertTrue(src.contains("order.amount.get()"));
                    assertFalse(src.contains("order.getAmount()"));
                }

                @Test
                void fromMetadataMap_assignsDirectField() {
                    String src = generate(List.of(directOptionalField("amount", "java.math.BigInteger")));
                    assertTrue(src.contains("obj.amount = Optional.of("));
                    assertFalse(src.contains("obj.setAmount("));
                }

                @Test
                void fromMetadataMap_assignsOptionalEmpty() {
                    String src = generate(List.of(directOptionalField("amount", "java.math.BigInteger")));
                    assertTrue(src.contains("obj.amount = Optional.empty()"));
                }
            }

            @Nested
            class IntegerElements {

                @Test
                void toMetadataMap_readsDirectField() {
                    String src = generate(List.of(directOptionalField("qty", "java.lang.Integer")));
                    assertTrue(src.contains("order.qty.get()"));
                    assertFalse(src.contains("order.getQty()"));
                }

                @Test
                void fromMetadataMap_assignsDirectField() {
                    String src = generate(List.of(directOptionalField("qty", "java.lang.Integer")));
                    assertTrue(src.contains("obj.qty = Optional.of("));
                    assertFalse(src.contains("obj.setQty("));
                }

                @Test
                void fromMetadataMap_assignsOptionalEmpty() {
                    String src = generate(List.of(directOptionalField("qty", "java.lang.Integer")));
                    assertTrue(src.contains("obj.qty = Optional.empty()"));
                }
            }
        }

        @Nested
        class EnumFields {

            @Test
            void toMetadataMap_directField_noGetter() {
                MetadataFieldInfo f = new MetadataFieldInfo();
                f.setJavaFieldName("status");
                f.setMetadataKey("status");
                f.setJavaTypeName("com.example.Status");
                f.setEnumType(true);
                String src = generate(List.of(f));
                assertTrue(src.contains("map.put(\"status\", order.status.name())"));
            }

            @Test
            void fromMetadataMap_directField_noSetter() {
                MetadataFieldInfo f = new MetadataFieldInfo();
                f.setJavaFieldName("status");
                f.setMetadataKey("status");
                f.setJavaTypeName("com.example.Status");
                f.setEnumType(true);
                String src = generate(List.of(f));
                assertTrue(src.contains("obj.status = Status.valueOf((String) v)"));
            }
        }
    }

    // =========================================================================
    // Multiple fields
    // =========================================================================

    @Nested
    class MultipleFields {

        private final List<MetadataFieldInfo> fields = List.of(
                field("recipient", "java.lang.String"),
                field("amount",    "java.math.BigInteger"),
                field("timestamp", "java.lang.Long"),
                field("quantity",  "int"),
                field("sig",       "byte[]")
        );

        @Test
        void toMetadataMap_allFieldsHavePutStatements() {
            String src = generate(fields);
            assertTrue(src.contains("map.put(\"recipient\""));
            assertTrue(src.contains("_putBigInt(map, \"amount\","));
            assertTrue(src.contains("_putBigInt(map, \"timestamp\","));
            assertTrue(src.contains("_putBigInt(map, \"quantity\","));
            assertTrue(src.contains("map.put(\"sig\","));
        }

        @Test
        void fromMetadataMap_allFieldsHaveGetStatements() {
            String src = generate(fields);
            assertTrue(src.contains("map.get(\"recipient\")"));
            assertTrue(src.contains("map.get(\"amount\")"));
            assertTrue(src.contains("map.get(\"timestamp\")"));
            assertTrue(src.contains("map.get(\"quantity\")"));
            assertTrue(src.contains("map.get(\"sig\")"));
        }

        @Test
        void multipleStringFields_eachGetsOwnSplitBlock() {
            List<MetadataFieldInfo> twoStrings = List.of(
                    field("name", "java.lang.String"),
                    field("note", "java.lang.String")
            );
            String src = generate(twoStrings);
            // Both fields should have their own splitting checks
            assertTrue(src.contains("splitStringEveryNCharacters(order.getName(), 64)"));
            assertTrue(src.contains("splitStringEveryNCharacters(order.getNote(), 64)"));
        }

        @Test
        void multipleStringFields_eachGetsOwnMetadataListReassembly() {
            List<MetadataFieldInfo> twoStrings = List.of(
                    field("name", "java.lang.String"),
                    field("note", "java.lang.String")
            );
            String src = generate(twoStrings);
            assertTrue(src.contains("map.get(\"name\")"));
            assertTrue(src.contains("map.get(\"note\")"));
            assertTrue(src.contains("obj.setName(_sb.toString())"));
            assertTrue(src.contains("obj.setNote(_sb.toString())"));
        }
    }

    // =========================================================================
    // Integer/Long/BigInteger Map key support
    // =========================================================================

    @Nested
    class IntegerKeyMaps {

        @Test
        void integerKeyMap_serialization_usesBigIntegerValueOf() {
            MetadataFieldInfo f = mapField("scores", INTEGER, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains("BigInteger.valueOf(_entry.getKey())"),
                    "Should use BigInteger.valueOf for Integer keys");
        }

        @Test
        void integerKeyMap_deserialization_usesIntValue() {
            MetadataFieldInfo f = mapField("scores", INTEGER, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains("_k instanceof BigInteger"),
                    "Should check for BigInteger on-chain key");
            assertTrue(src.contains(".intValue()"),
                    "Should narrow BigInteger to int via intValue()");
        }

        @Test
        void longKeyMap_serialization_usesBigIntegerValueOf() {
            MetadataFieldInfo f = mapField("labels", LONG, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains("BigInteger.valueOf(_entry.getKey())"),
                    "Should use BigInteger.valueOf for Long keys");
        }

        @Test
        void longKeyMap_deserialization_usesLongValue() {
            MetadataFieldInfo f = mapField("labels", LONG, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains(".longValue()"),
                    "Should narrow BigInteger to long via longValue()");
        }

        @Test
        void bigIntegerKeyMap_serialization_directKey() {
            MetadataFieldInfo f = mapField("amounts", BIG_INTEGER, STRING);
            String src = generate(List.of(f));
            // BigInteger keys should not use BigInteger.valueOf
            assertFalse(src.contains("BigInteger.valueOf(_entry.getKey())"),
                    "BigInteger keys should not wrap in BigInteger.valueOf");
        }

        @Test
        void bigIntegerKeyMap_deserialization_directCast() {
            MetadataFieldInfo f = mapField("amounts", BIG_INTEGER, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains("_k instanceof BigInteger"),
                    "Should check for BigInteger on-chain key");
            assertFalse(src.contains(".intValue()"));
            assertFalse(src.contains(".longValue()"));
        }

        @Test
        void integerKeyMap_withIntegerValue() {
            MetadataFieldInfo f = mapField("counts", INTEGER, INTEGER);
            String src = generate(List.of(f));
            assertTrue(src.contains("BigInteger.valueOf(_entry.getKey())"));
            assertTrue(src.contains(".intValue()"));
        }

        @Test
        void stringKeyMap_unchanged() {
            MetadataFieldInfo f = mapField("settings", STRING, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains("_k instanceof String"),
                    "String keys should use instanceof String");
            assertFalse(src.contains("BigInteger.valueOf(_entry.getKey())"),
                    "String keys should not use BigInteger.valueOf");
        }

        @Test
        void byteArrayKeyMap_serialization_directKey() {
            MetadataFieldInfo f = mapField("labels", BYTE_ARRAY, STRING);
            String src = generate(List.of(f));
            // byte[] keys should pass through directly, no BigInteger.valueOf
            assertFalse(src.contains("BigInteger.valueOf(_entry.getKey())"),
                    "byte[] keys should not wrap in BigInteger.valueOf");
            assertTrue(src.contains("_entry.getKey()"),
                    "byte[] keys should pass through directly");
        }

        @Test
        void byteArrayKeyMap_deserialization_instanceofByteArray() {
            MetadataFieldInfo f = mapField("labels", BYTE_ARRAY, STRING);
            String src = generate(List.of(f));
            assertTrue(src.contains("_k instanceof byte[]"),
                    "Should check for byte[] on-chain key");
            assertTrue(src.contains("(byte[]) _k"),
                    "Should cast to byte[]");
        }

        @Test
        void byteArrayKeyMap_withBigIntegerValue() {
            MetadataFieldInfo f = mapField("amounts", BYTE_ARRAY, BIG_INTEGER);
            String src = generate(List.of(f));
            assertTrue(src.contains("_k instanceof byte[]"));
            assertTrue(src.contains("_putBigInt(_maplabels") || src.contains("_putBigInt(_mapamounts"),
                    "BigInteger values with byte[] keys should use _putBigInt");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Record support
    // ─────────────────────────────────────────────────────────────────────

    /** Record-mode field: getter = component name, no setter, recordMode = true. */
    private MetadataFieldInfo recordField(String name, String javaType) {
        MetadataFieldInfo f = new MetadataFieldInfo();
        f.setJavaFieldName(name);
        f.setMetadataKey(name);
        f.setJavaTypeName(javaType);
        f.setGetterName(name); // record accessor: name() not getName()
        f.setSetterName(null);
        f.setRecordMode(true);
        return f;
    }

    private MetadataFieldInfo recordListField(String name, String elementType) {
        MetadataFieldInfo f = recordField(name, "java.util.List<" + elementType + ">");
        f.setElement(new MetadataFieldInfo.ElementInfo(elementType, MetadataFieldInfo.LeafClassification.NONE, null, null));
        f.setCollectionType(true);
        f.setCollectionKind(COLLECTION_LIST);
        return f;
    }

    @Nested
    class RecordSupport {

        @Test
        void fromMetadataMap_usesLocalVarsAndConstructor() {
            MetadataFieldInfo nameF = recordField("name", STRING);
            MetadataFieldInfo ageF = recordField("age", PRIM_INT);

            String src = generateRecord(List.of(nameF, ageF));

            // Should NOT contain "obj = new Order()" (POJO pattern)
            assertFalse(src.contains("Order obj = new Order()"),
                    "Record mode should not create obj via no-arg constructor");

            // Should declare local vars with defaults
            assertTrue(src.contains("String _name = null"),
                    "Should declare String local with null default");
            assertTrue(src.contains("int _age = 0"),
                    "Should declare int local with 0 default");

            // Should assign to locals, not setters
            assertTrue(src.contains("_name = (String) v") || src.contains("_name = ("),
                    "Should assign to _name local");
            assertTrue(src.contains("_age = ((BigInteger) v)"),
                    "Should assign to _age local");

            // Should return via constructor
            assertTrue(src.contains("return new Order(_name, _age)"),
                    "Should return via canonical constructor");
        }

        @Test
        void toMetadataMap_usesRecordAccessors() {
            MetadataFieldInfo nameF = recordField("name", STRING);
            String src = generateRecord(List.of(nameF));

            // Record accessor is name() not getName()
            assertTrue(src.contains("order.name()"),
                    "Should use record accessor name() for serialization");
            assertFalse(src.contains("order.getName()"),
                    "Should not use getter-style accessor");
        }

        @Test
        void primitiveDefaults() {
            MetadataFieldInfo longF = recordField("count", PRIM_LONG);
            MetadataFieldInfo boolF = recordField("active", PRIM_BOOLEAN);
            MetadataFieldInfo doubleF = recordField("rate", PRIM_DOUBLE);
            MetadataFieldInfo charF = recordField("grade", PRIM_CHAR);

            String src = generateRecord(List.of(longF, boolF, doubleF, charF));

            assertTrue(src.contains("long _count = 0L"), "long default should be 0L");
            assertTrue(src.contains("boolean _active = false"), "boolean default should be false");
            assertTrue(src.contains("double _rate = 0.0d"), "double default should be 0.0d");
            assertTrue(src.contains("char _grade = '\\0'"), "char default should be '\\0'");
        }

        @Test
        void referenceTypeDefaults() {
            MetadataFieldInfo biF = recordField("amount", BIG_INTEGER);
            String src = generateRecord(List.of(biF));

            assertTrue(src.contains("BigInteger _amount = null"),
                    "Reference types should default to null");
        }

        @Test
        void listField_inRecord() {
            MetadataFieldInfo tagsF = recordListField("tags", STRING);
            String src = generateRecord(List.of(tagsF));

            // Local var declaration
            assertTrue(src.contains("_tags = null") || src.contains("List<String> _tags = null"),
                    "List field should be declared as local");

            // Constructor call
            assertTrue(src.contains("return new Order(_tags)"),
                    "Should pass list local to constructor");
        }

        @Test
        void multipleFields_constructorOrderPreserved() {
            MetadataFieldInfo f1 = recordField("a", STRING);
            MetadataFieldInfo f2 = recordField("b", PRIM_INT);
            MetadataFieldInfo f3 = recordField("c", BIG_INTEGER);

            String src = generateRecord(List.of(f1, f2, f3));

            assertTrue(src.contains("return new Order(_a, _b, _c)"),
                    "Constructor args should match field declaration order");
        }
    }

    // -------------------------------------------------------------------------
    // Required & DefaultValue
    // -------------------------------------------------------------------------

    @Nested
    class RequiredAndDefaultValue {

        @Test
        void required_string_emitsNullCheckAndThrow() {
            MetadataFieldInfo f = field("refId", STRING);
            f.setRequired(true);
            f.setMetadataKey("ref_id");

            String src = generate(List.of(f));

            assertTrue(src.contains("v = map.get(\"ref_id\")"), "Should get by metadata key");
            assertTrue(src.contains("if (v == null)"), "Should check for null");
            assertTrue(src.contains("throw new IllegalArgumentException(\"Required metadata key 'ref_id' is missing\")"),
                    "Should throw with key name");
        }

        @Test
        void required_int_emitsNullCheckAndThrow() {
            MetadataFieldInfo f = field("count", PRIM_INT);
            f.setRequired(true);

            String src = generate(List.of(f));

            assertTrue(src.contains("if (v == null)"), "Should check for null");
            assertTrue(src.contains("throw new IllegalArgumentException(\"Required metadata key 'count' is missing\")"),
                    "Should throw with key name");
        }

        @Test
        void defaultValue_string_emitsNullFallback() {
            MetadataFieldInfo f = field("status", STRING);
            f.setDefaultValue("UNKNOWN");

            String src = generate(List.of(f));

            assertTrue(src.contains("if (v == null)"), "Should check for null");
            assertTrue(src.contains("v = \"UNKNOWN\""), "Should assign string default");
        }

        @Test
        void defaultValue_int_emitsBigIntegerFallback() {
            MetadataFieldInfo f = field("count", PRIM_INT);
            f.setDefaultValue("42");

            String src = generate(List.of(f));

            assertTrue(src.contains("if (v == null)"), "Should check for null");
            assertTrue(src.contains("v = java.math.BigInteger.valueOf(42L)"),
                    "Should assign BigInteger default for int field");
        }

        @Test
        void defaultValue_long_emitsBigIntegerFallback() {
            MetadataFieldInfo f = field("timestamp", LONG);
            f.setDefaultValue("1000");

            String src = generate(List.of(f));

            assertTrue(src.contains("v = java.math.BigInteger.valueOf(1000L)"),
                    "Should assign BigInteger default for Long field");
        }

        @Test
        void defaultValue_bigInteger_emitsNewBigInteger() {
            MetadataFieldInfo f = field("amount", BIG_INTEGER);
            f.setDefaultValue("999999999999999999");

            String src = generate(List.of(f));

            assertTrue(src.contains("v = new java.math.BigInteger(\"999999999999999999\")"),
                    "Should use BigInteger constructor for BigInteger field");
        }

        @Test
        void defaultValue_booleanTrue_emitsBigIntegerOne() {
            MetadataFieldInfo f = boolField("active", PRIM_BOOLEAN);
            f.setDefaultValue("true");

            String src = generate(List.of(f));

            assertTrue(src.contains("v = java.math.BigInteger.valueOf(1L)"),
                    "Boolean true should map to BigInteger 1");
        }

        @Test
        void defaultValue_booleanFalse_emitsBigIntegerZero() {
            MetadataFieldInfo f = boolField("active", PRIM_BOOLEAN);
            f.setDefaultValue("false");

            String src = generate(List.of(f));

            assertTrue(src.contains("v = java.math.BigInteger.valueOf(0L)"),
                    "Boolean false should map to BigInteger 0");
        }

        @Test
        void defaultValue_enum_emitsStringFallback() {
            MetadataFieldInfo f = field("priority", "com.test.Priority");
            f.setEnumType(true);
            f.setDefaultValue("HIGH");

            String src = generate(List.of(f));

            assertTrue(src.contains("v = \"HIGH\""),
                    "Enum default should be a string literal");
        }

        @Test
        void required_recordMode_emitsNullCheckAndThrow() {
            MetadataFieldInfo f = recordField("refId", STRING);
            f.setRequired(true);

            String src = generateRecord(List.of(f));

            assertTrue(src.contains("if (v == null)"), "Should check for null in record mode");
            assertTrue(src.contains("throw new IllegalArgumentException"),
                    "Should throw in record mode");
        }

        @Test
        void defaultValue_recordMode_emitsNullFallback() {
            MetadataFieldInfo f = recordField("status", STRING);
            f.setDefaultValue("PENDING");

            String src = generateRecord(List.of(f));

            assertTrue(src.contains("v = \"PENDING\""),
                    "Should assign default in record mode");
        }

        @Test
        void noRequiredOrDefault_noExtraEmission() {
            MetadataFieldInfo f = field("name", STRING);
            // Neither required nor defaultValue set

            String src = generate(List.of(f));

            // Should NOT contain the required/default patterns
            assertFalse(src.contains("throw new IllegalArgumentException"),
                    "Should not throw when not required");
        }

        @Test
        void serialization_unaffected_byRequired() {
            MetadataFieldInfo f = field("refId", STRING);
            f.setRequired(true);

            String src = generate(List.of(f));

            // toMetadataMap should NOT contain the required check
            String toMap = src.substring(src.indexOf("toMetadataMap"), src.indexOf("fromMetadataMap"));
            assertFalse(toMap.contains("throw new IllegalArgumentException"),
                    "Serialization should not be affected by required");
        }

        @Test
        void serialization_unaffected_byDefaultValue() {
            MetadataFieldInfo f = field("status", STRING);
            f.setDefaultValue("UNKNOWN");

            String src = generate(List.of(f));

            String toMap = src.substring(src.indexOf("toMetadataMap"), src.indexOf("fromMetadataMap"));
            assertFalse(toMap.contains("UNKNOWN"),
                    "Serialization should not be affected by defaultValue");
        }
    }

    // =========================================================================
    // Polymorphic fields
    // =========================================================================

    @Nested
    class PolymorphicFields {

        private MetadataFieldInfo polyField(String name) {
            MetadataFieldInfo f = field(name, "com.test.Media");
            f.setPolymorphic(new MetadataFieldInfo.PolymorphicInfo("type", List.of(
                    new MetadataFieldInfo.PolymorphicSubtypeInfo("image",
                            "com.test.ImageMediaMetadataConverter", "com.test.ImageMedia"),
                    new MetadataFieldInfo.PolymorphicSubtypeInfo("audio",
                            "com.test.AudioMediaMetadataConverter", "com.test.AudioMedia")
            )));
            return f;
        }

        @Test
        void serialization_emitsInstanceofChain() {
            String src = generate(List.of(polyField("media")));

            String toMap = src.substring(src.indexOf("toMetadataMap"), src.indexOf("fromMetadataMap"));
            assertTrue(toMap.contains("instanceof ImageMedia"), "Should emit instanceof for ImageMedia");
            assertTrue(toMap.contains("instanceof AudioMedia"), "Should emit instanceof for AudioMedia");
            assertTrue(toMap.contains("_polyMap.put(\"type\", \"image\")"), "Should inject discriminator for image");
            assertTrue(toMap.contains("_polyMap.put(\"type\", \"audio\")"), "Should inject discriminator for audio");
            assertTrue(toMap.contains("new ImageMediaMetadataConverter().toMetadataMap"), "Should call ImageMedia converter");
            assertTrue(toMap.contains("new AudioMediaMetadataConverter().toMetadataMap"), "Should call AudioMedia converter");
        }

        @Test
        void deserialization_emitsDiscriminatorDispatch() {
            String src = generate(List.of(polyField("media")));

            String fromMap = src.substring(src.indexOf("fromMetadataMap"));
            assertTrue(fromMap.contains("_polyMap.get(\"type\")"), "Should read discriminator key");
            assertTrue(fromMap.contains("\"image\".equals(_disc)"), "Should check image discriminator");
            assertTrue(fromMap.contains("\"audio\".equals(_disc)"), "Should check audio discriminator");
            assertTrue(fromMap.contains("new ImageMediaMetadataConverter().fromMetadataMap"), "Should dispatch to ImageMedia converter");
            assertTrue(fromMap.contains("new AudioMediaMetadataConverter().fromMetadataMap"), "Should dispatch to AudioMedia converter");
        }

        @Test
        void serialization_wrapsInNullCheck() {
            String src = generate(List.of(polyField("media")));

            String toMap = src.substring(src.indexOf("toMetadataMap"), src.indexOf("fromMetadataMap"));
            assertTrue(toMap.contains("if (order.getMedia() != null)"), "Should null-check polymorphic field");
        }

        @Test
        void recordMode_emitsLocalVariable() {
            MetadataFieldInfo f = polyField("media");
            f.setRecordMode(true);

            String src = generateRecord(List.of(f));

            String fromMap = src.substring(src.indexOf("fromMetadataMap"));
            assertTrue(fromMap.contains("_media = new ImageMediaMetadataConverter().fromMetadataMap"),
                    "Record mode should assign to local variable");
        }
    }

    // =========================================================================
    // Custom Adapter
    // =========================================================================

    @Nested
    class CustomAdapter {

        private MetadataFieldInfo adapterField(String name, String javaType, String adapterFqn) {
            MetadataFieldInfo f = field(name, javaType);
            f.setAdapterType(true);
            f.setAdapterFqn(adapterFqn);
            return f;
        }

        @Test
        void serializesWithAdapter() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            assertTrue(src.contains("_putAdapted(map, \"timestamp\", _epochSecondsAdapter.toMetadata(order.getTimestamp()))"),
                    "Should call adapter's toMetadata via cached static field: " + src);
        }

        @Test
        void deserializesWithAdapter() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            String fromMap = src.substring(src.indexOf("fromMetadataMap"));
            assertTrue(fromMap.contains("_epochSecondsAdapter.fromMetadata(v)"),
                    "Should call adapter's fromMetadata via cached static field: " + fromMap);
        }

        @Test
        void deserializationCastsToFieldType() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            String fromMap = src.substring(src.indexOf("fromMetadataMap"));
            assertTrue(fromMap.contains("(java.time.Instant)"),
                    "Should cast fromMetadata result to the field type: " + fromMap);
        }

        @Test
        void deserializationNullChecksValue() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            String fromMap = src.substring(src.indexOf("fromMetadataMap"));
            assertTrue(fromMap.contains("if (v != null)"),
                    "Should null-check v before calling adapter: " + fromMap);
        }

        @Test
        void serializationNullChecksGetExpression() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            String toMap = src.substring(src.indexOf("toMetadataMap"), src.indexOf("fromMetadataMap"));
            assertTrue(toMap.contains("if (order.getTimestamp() != null)"),
                    "Should null-check the get expression: " + toMap);
        }

        @Test
        void recordModeDeserializesWithAdapter() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            f.setRecordMode(true);
            f.setGetterName("timestamp");
            f.setSetterName(null);

            String src = generateRecord(List.of(f));
            String fromMap = src.substring(src.indexOf("fromMetadataMap"));
            assertTrue(fromMap.contains("_timestamp = (java.time.Instant) _epochSecondsAdapter.fromMetadata(v)"),
                    "Record mode should assign to local variable: " + fromMap);
        }

        @Test
        void generatesAdapterInstanceField() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            assertTrue(src.contains("private final EpochSecondsAdapter _epochSecondsAdapter"),
                    "Should generate instance adapter field: " + src);
            assertFalse(src.contains("private static final EpochSecondsAdapter"),
                    "Should NOT generate static adapter field: " + src);
        }

        @Test
        void generatesAdapterHelperMethod() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            assertTrue(src.contains("private void _putAdapted(MetadataMap _m, String _k, Object _v)"),
                    "Should generate instance _putAdapted helper: " + src);
        }

        @Test
        void noAdapterHelper_whenNoAdapterFields() {
            MetadataFieldInfo f = field("name", STRING);
            String src = generate(List.of(f));
            assertFalse(src.contains("_putAdapted"),
                    "Should not generate _putAdapted when no adapter fields: " + src);
        }

        @Test
        void generatesNoArgConstructor() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            assertTrue(src.contains("this(DefaultAdapterResolver.INSTANCE)"),
                    "No-arg constructor should delegate to resolver: " + src);
        }

        @Test
        void generatesResolverConstructor() {
            MetadataFieldInfo f = adapterField("timestamp", "java.time.Instant",
                    "com.example.EpochSecondsAdapter");
            String src = generate(List.of(f));
            assertTrue(src.contains("MetadataAdapterResolver resolver"),
                    "Should generate resolver constructor: " + src);
            assertTrue(src.contains("resolver.resolve(EpochSecondsAdapter.class)"),
                    "Should resolve adapter via resolver: " + src);
        }

        @Test
        void noConstructors_whenNoAdapterFields() {
            MetadataFieldInfo f = field("name", STRING);
            String src = generate(List.of(f));
            assertFalse(src.contains("MetadataAdapterResolver"),
                    "Should not generate resolver constructor without adapters: " + src);
        }
    }

    // =========================================================================
    // Encoder / Decoder fields
    // =========================================================================

    @Nested
    class EncoderDecoder {

        private MetadataFieldInfo encoderField(String name, String javaType, String encoderFqn) {
            MetadataFieldInfo f = field(name, javaType);
            f.setEncoderType(true);
            f.setEncoderFqn(encoderFqn);
            return f;
        }

        private MetadataFieldInfo decoderField(String name, String javaType, String decoderFqn) {
            MetadataFieldInfo f = field(name, javaType);
            f.setDecoderType(true);
            f.setDecoderFqn(decoderFqn);
            return f;
        }

        private MetadataFieldInfo encoderDecoderField(String name, String javaType,
                                                       String encoderFqn, String decoderFqn) {
            MetadataFieldInfo f = field(name, javaType);
            f.setEncoderType(true);
            f.setEncoderFqn(encoderFqn);
            f.setDecoderType(true);
            f.setDecoderFqn(decoderFqn);
            return f;
        }

        @Test
        void encoderOnly_serializesWithEncoder() {
            MetadataFieldInfo f = encoderField("slot", "long",
                    "com.example.SlotToEpochEncoder");
            String src = generate(List.of(f));
            assertTrue(src.contains("_putAdapted(map, \"slot\", _slotToEpochEncoder.toMetadata(order.getSlot()))"),
                    "Should serialize using encoder: " + src);
        }

        @Test
        void encoderOnly_deserializationFallsBackToBuiltIn() {
            MetadataFieldInfo f = encoderField("slot", "long",
                    "com.example.SlotToEpochEncoder");
            String src = generate(List.of(f));
            // Should NOT contain decoder call, should use built-in long handling
            assertFalse(src.contains("_slotToEpochEncoder.fromMetadata"),
                    "Encoder-only should not call fromMetadata: " + src);
            assertTrue(src.contains("((BigInteger) v).longValue()"),
                    "Should fall back to built-in long deserialization: " + src);
        }

        @Test
        void decoderOnly_deserializesWithDecoder() {
            MetadataFieldInfo f = decoderField("slot", "long",
                    "com.example.EpochToSlotDecoder");
            String src = generate(List.of(f));
            assertTrue(src.contains("_epochToSlotDecoder.fromMetadata(v)"),
                    "Should deserialize using decoder: " + src);
        }

        @Test
        void decoderOnly_serializationFallsBackToBuiltIn() {
            MetadataFieldInfo f = decoderField("slot", "long",
                    "com.example.EpochToSlotDecoder");
            String src = generate(List.of(f));
            // Should NOT contain encoder call, should use built-in long handling
            assertFalse(src.contains("_epochToSlotDecoder.toMetadata"),
                    "Decoder-only should not call toMetadata: " + src);
        }

        @Test
        void bothEncoderAndDecoder_usesSeparateClasses() {
            MetadataFieldInfo f = encoderDecoderField("slot", "long",
                    "com.example.SlotToEpochEncoder", "com.example.EpochToSlotDecoder");
            String src = generate(List.of(f));
            assertTrue(src.contains("_slotToEpochEncoder.toMetadata(order.getSlot())"),
                    "Should use encoder for serialization: " + src);
            assertTrue(src.contains("_epochToSlotDecoder.fromMetadata(v)"),
                    "Should use decoder for deserialization: " + src);
        }

        @Test
        void generatesResolverConstructor() {
            MetadataFieldInfo f = encoderField("slot", "long",
                    "com.example.SlotToEpochEncoder");
            String src = generate(List.of(f));
            assertTrue(src.contains("MetadataAdapterResolver resolver"),
                    "Should generate resolver constructor: " + src);
            assertTrue(src.contains("resolver.resolve(SlotToEpochEncoder.class)"),
                    "Should resolve encoder via resolver: " + src);
        }

        @Test
        void generatesInstanceField() {
            MetadataFieldInfo f = encoderField("slot", "long",
                    "com.example.SlotToEpochEncoder");
            String src = generate(List.of(f));
            assertTrue(src.contains("private final SlotToEpochEncoder _slotToEpochEncoder"),
                    "Should generate instance field: " + src);
        }

        @Test
        void recordModeWithEncoder() {
            MetadataFieldInfo f = encoderField("slot", "long",
                    "com.example.SlotToEpochEncoder");
            f.setRecordMode(true);
            String src = generate(List.of(f));
            assertTrue(src.contains("_slotToEpochEncoder.toMetadata"),
                    "Record mode should use encoder: " + src);
        }
    }
}
