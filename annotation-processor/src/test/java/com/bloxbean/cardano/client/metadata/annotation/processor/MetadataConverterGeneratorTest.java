package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetadataConverterGenerator}.
 * Tests inspect the generated Java source text to assert correct code structure.
 *
 * Organised by concern:
 * - ClassStructure   : generated class/method shape
 * - ToMetadataMap    : serialisation per field type, null-checks, key mapping, direct-field access
 * - FromMetadataMap  : deserialisation per field type, MetadataList reassembly, key mapping, direct-field access
 * - MultipleFields   : all fields together
 */
public class MetadataConverterGeneratorTest {

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
    private MetadataFieldInfo fieldAs(String name, String javaType, MetadataFieldType as) {
        MetadataFieldInfo f = field(name, javaType);
        f.setAs(as);
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
    private MetadataFieldInfo boolFieldAs(String name, String javaType, MetadataFieldType as) {
        MetadataFieldInfo f = boolField(name, javaType);
        f.setAs(as);
        return f;
    }

    /** List field with explicit getter/setter. */
    private MetadataFieldInfo listField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.List<" + elementType + ">");
        f.setElementTypeName(elementType);
        return f;
    }

    /** Set field with explicit getter/setter. */
    private MetadataFieldInfo setField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.Set<" + elementType + ">");
        f.setElementTypeName(elementType);
        return f;
    }

    /** SortedSet field with explicit getter/setter. */
    private MetadataFieldInfo sortedSetField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.SortedSet<" + elementType + ">");
        f.setElementTypeName(elementType);
        return f;
    }

    /** Optional field with explicit getter/setter. */
    private MetadataFieldInfo optionalField(String name, String elementType) {
        MetadataFieldInfo f = field(name, "java.util.Optional<" + elementType + ">");
        f.setElementTypeName(elementType);
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
        void emptyFieldList_stillGeneratesValidClass() {
            String src = generate(List.of());
            assertTrue(src.contains("MetadataMap map = MetadataBuilder.createMap()"));
            assertTrue(src.contains("return map"));
            assertTrue(src.contains("Order obj = new Order()"));
            assertTrue(src.contains("return obj"));
        }
    }

    // =========================================================================
    // toMetadataMap
    // =========================================================================

    @Nested
    class ToMetadataMap {

        @Nested
        class StringFields {

            @Test
            void shortString_storedDirectlyInMap() {
                String src = generate(List.of(field("note", "java.lang.String")));
                // else branch — direct put without wrapping
                assertTrue(src.contains("map.put(\"note\", order.getNote())"));
            }

            @Test
            void longString_byteCountCheckedAgainst64() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("getBytes("));
                assertTrue(src.contains("UTF_8"));
                assertTrue(src.contains("> 64"));
            }

            @Test
            void longString_splitWithStringUtils() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("splitStringEveryNCharacters"));
                assertTrue(src.contains("splitStringEveryNCharacters(order.getNote(), 64)"));
            }

            @Test
            void longString_chunksStoredAsMetadataList() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("MetadataList _chunks = MetadataBuilder.createList()"));
                assertTrue(src.contains("_chunks.add(_part)"));
                assertTrue(src.contains("map.put(\"note\", _chunks)"));
            }

            @Test
            void stringField_hasNullGuard() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("if (order.getNote() != null)"));
            }
        }

        @Nested
        class NumericFields {

            @Test
            void bigIntegerField_storedDirectly() {
                String src = generate(List.of(field("amount", "java.math.BigInteger")));
                assertTrue(src.contains("map.put(\"amount\", order.getAmount())"));
            }

            @Test
            void bigIntegerField_hasNullGuard() {
                String src = generate(List.of(field("amount", "java.math.BigInteger")));
                assertTrue(src.contains("if (order.getAmount() != null)"));
            }

            @Test
            void longBoxed_wrappedInBigIntegerValueOf() {
                String src = generate(List.of(field("ts", "java.lang.Long")));
                assertTrue(src.contains("BigInteger.valueOf(order.getTs())"));
            }

            @Test
            void longBoxed_hasNullGuard() {
                String src = generate(List.of(field("ts", "java.lang.Long")));
                assertTrue(src.contains("if (order.getTs() != null)"));
            }

            @Test
            void longPrimitive_wrappedInBigIntegerValueOf() {
                String src = generate(List.of(field("ts", "long")));
                assertTrue(src.contains("BigInteger.valueOf(order.getTs())"));
            }

            @Test
            void longPrimitive_noNullGuard() {
                String src = generate(List.of(field("ts", "long")));
                assertFalse(src.contains("if (order.getTs() != null)"));
            }

            @Test
            void integerBoxed_castToLongThenWrapped() {
                String src = generate(List.of(field("qty", "java.lang.Integer")));
                assertTrue(src.contains("BigInteger.valueOf((long) order.getQty())"));
            }

            @Test
            void integerBoxed_hasNullGuard() {
                String src = generate(List.of(field("qty", "java.lang.Integer")));
                assertTrue(src.contains("if (order.getQty() != null)"));
            }

            @Test
            void intPrimitive_castToLongThenWrapped() {
                String src = generate(List.of(field("qty", "int")));
                assertTrue(src.contains("BigInteger.valueOf((long) order.getQty())"));
            }

            @Test
            void intPrimitive_noNullGuard() {
                String src = generate(List.of(field("qty", "int")));
                assertFalse(src.contains("if (order.getQty() != null)"));
            }
        }

        @Nested
        class ByteArrayFields {

            @Test
            void byteArrayField_storedDirectly() {
                String src = generate(List.of(field("sig", "byte[]")));
                assertTrue(src.contains("map.put(\"sig\", order.getSig())"));
            }

            @Test
            void byteArrayField_hasNullGuard() {
                String src = generate(List.of(field("sig", "byte[]")));
                assertTrue(src.contains("if (order.getSig() != null)"));
            }

            @Test
            void byteArrayField_doesNotEmitStringSplitLogic() {
                String src = generate(List.of(field("sig", "byte[]")));
                assertFalse(src.contains("splitStringEveryNCharacters"));
                assertFalse(src.contains("getBytes("));
            }
        }

        @Nested
        class KeyMapping {

            @Test
            void defaultKey_usesFieldName() {
                String src = generate(List.of(field("recipient", "java.lang.String")));
                assertTrue(src.contains("map.put(\"recipient\""));
            }

            @Test
            void customKey_usesMetadataKeyInsteadOfFieldName() {
                String src = generate(List.of(field("referenceId", "ref_id", "java.lang.String")));
                assertTrue(src.contains("map.put(\"ref_id\""));
                assertFalse(src.contains("map.put(\"referenceId\""));
            }
        }

        @Nested
        class DirectFieldAccess {

            @Test
            void stringField_readsPublicFieldDirectly() {
                String src = generate(List.of(directField("note", "java.lang.String")));
                assertTrue(src.contains("order.note"));
                assertFalse(src.contains("order.getNote()"));
            }

            @Test
            void bigIntegerField_readsPublicFieldDirectly() {
                String src = generate(List.of(directField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("order.amount"));
                assertFalse(src.contains("order.getAmount()"));
            }
        }
    }

    // =========================================================================
    // fromMetadataMap
    // =========================================================================

    @Nested
    class FromMetadataMap {

        @Nested
        class StringFields {

            @Test
            void plainString_assignedDirectly() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("if (v instanceof String)"));
                assertTrue(src.contains("obj.setNote((String) v)"));
            }

            @Test
            void chunkedString_metadataListBranchPresent() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("else if (v instanceof MetadataList)"));
            }

            @Test
            void chunkedString_chunksReassembledWithStringBuilder() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertTrue(src.contains("StringBuilder _sb = new StringBuilder()"));
                assertTrue(src.contains("MetadataList _list = (MetadataList) v"));
                assertTrue(src.contains("_list.size()"));
                assertTrue(src.contains("_list.getValueAt(_i)"));
                assertTrue(src.contains("_sb.append((String) _chunk)"));
                assertTrue(src.contains("obj.setNote(_sb.toString())"));
            }

            @Test
            void stringField_doesNotEmitBigIntegerCast() {
                String src = generate(List.of(field("note", "java.lang.String")));
                assertFalse(src.contains("longValue()"));
                assertFalse(src.contains("intValue()"));
            }
        }

        @Nested
        class NumericFields {

            @Test
            void bigIntegerField_castFromBigIntegerDirectly() {
                String src = generate(List.of(field("amount", "java.math.BigInteger")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
                assertTrue(src.contains("obj.setAmount((BigInteger) v)"));
            }

            @Test
            void longBoxed_extractedFromBigIntegerViaLongValue() {
                String src = generate(List.of(field("ts", "java.lang.Long")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
                assertTrue(src.contains("obj.setTs(((BigInteger) v).longValue())"));
            }

            @Test
            void longPrimitive_extractedFromBigIntegerViaLongValue() {
                String src = generate(List.of(field("ts", "long")));
                assertTrue(src.contains("obj.setTs(((BigInteger) v).longValue())"));
            }

            @Test
            void integerBoxed_extractedFromBigIntegerViaIntValue() {
                String src = generate(List.of(field("qty", "java.lang.Integer")));
                assertTrue(src.contains("if (v instanceof BigInteger)"));
                assertTrue(src.contains("obj.setQty(((BigInteger) v).intValue())"));
            }

            @Test
            void intPrimitive_extractedFromBigIntegerViaIntValue() {
                String src = generate(List.of(field("qty", "int")));
                assertTrue(src.contains("obj.setQty(((BigInteger) v).intValue())"));
            }

            @Test
            void numericField_doesNotEmitStringBuilder() {
                String src = generate(List.of(field("amount", "java.math.BigInteger")));
                assertFalse(src.contains("StringBuilder"));
            }
        }

        @Nested
        class ByteArrayFields {

            @Test
            void byteArrayField_castFromByteArray() {
                String src = generate(List.of(field("sig", "byte[]")));
                assertTrue(src.contains("if (v instanceof byte[])"));
                assertTrue(src.contains("obj.setSig((byte[]) v)"));
            }

            @Test
            void byteArrayField_doesNotEmitStringBuilder() {
                String src = generate(List.of(field("sig", "byte[]")));
                assertFalse(src.contains("StringBuilder"));
            }
        }

        @Nested
        class KeyMapping {

            @Test
            void defaultKey_readsFromFieldName() {
                String src = generate(List.of(field("recipient", "java.lang.String")));
                assertTrue(src.contains("map.get(\"recipient\")"));
            }

            @Test
            void customKey_readsFromMetadataKey() {
                String src = generate(List.of(field("referenceId", "ref_id", "java.lang.String")));
                assertTrue(src.contains("map.get(\"ref_id\")"));
                assertFalse(src.contains("map.get(\"referenceId\")"));
            }
        }

        @Nested
        class DirectFieldAccess {

            @Test
            void stringField_assignsPublicFieldDirectly() {
                String src = generate(List.of(directField("note", "java.lang.String")));
                assertTrue(src.contains("obj.note = (String) v"));
                assertFalse(src.contains("obj.setNote("));
            }

            @Test
            void bigIntegerField_assignsPublicFieldDirectly() {
                String src = generate(List.of(directField("amount", "java.math.BigInteger")));
                assertTrue(src.contains("obj.amount = (BigInteger) v"));
                assertFalse(src.contains("obj.setAmount("));
            }
        }
    }

    // =========================================================================
    // Multiple fields
    // =========================================================================

    @Nested
    class MultipleFields {

        private final List<MetadataFieldInfo> FIELDS = List.of(
                field("recipient", "java.lang.String"),
                field("amount",    "java.math.BigInteger"),
                field("timestamp", "java.lang.Long"),
                field("quantity",  "int"),
                field("sig",       "byte[]")
        );

        @Test
        void toMetadataMap_allFieldsHavePutStatements() {
            String src = generate(FIELDS);
            assertTrue(src.contains("map.put(\"recipient\""));
            assertTrue(src.contains("map.put(\"amount\","));
            assertTrue(src.contains("map.put(\"timestamp\","));
            assertTrue(src.contains("map.put(\"quantity\","));
            assertTrue(src.contains("map.put(\"sig\","));
        }

        @Test
        void fromMetadataMap_allFieldsHaveGetStatements() {
            String src = generate(FIELDS);
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
    // short / Short
    // =========================================================================

    @Nested
    class ShortFieldsDefault {

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

        @Test
        void asString_toMetadataMap_emitsStringValueOf() {
            String src = generate(List.of(fieldAs("count", "short", MetadataFieldType.STRING)));
            assertTrue(src.contains("map.put(\"count\", String.valueOf(order.getCount()))"));
        }

        @Test
        void asString_fromMetadataMap_parsesShort() {
            String src = generate(List.of(fieldAs("count", "short", MetadataFieldType.STRING)));
            assertTrue(src.contains("Short.parseShort((String) v)"));
        }
    }

    // =========================================================================
    // byte / Byte
    // =========================================================================

    @Nested
    class ByteFieldsDefault {

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

        @Test
        void asString_toMetadataMap_emitsStringValueOf() {
            String src = generate(List.of(fieldAs("b", "byte", MetadataFieldType.STRING)));
            assertTrue(src.contains("map.put(\"b\", String.valueOf(order.getB()))"));
        }

        @Test
        void asString_fromMetadataMap_parsesByte() {
            String src = generate(List.of(fieldAs("b", "byte", MetadataFieldType.STRING)));
            assertTrue(src.contains("Byte.parseByte((String) v)"));
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

        @Test
        void asString_toMetadataMap_emitsStringValueOf() {
            String src = generate(List.of(boolFieldAs("active", "boolean", MetadataFieldType.STRING)));
            assertTrue(src.contains("map.put(\"active\", String.valueOf(order.isActive()))"));
        }

        @Test
        void asString_fromMetadataMap_parsesBoolean() {
            String src = generate(List.of(boolFieldAs("active", "boolean", MetadataFieldType.STRING)));
            assertTrue(src.contains("Boolean.parseBoolean((String) v)"));
        }

        @Test
        void asString_doesNotEmitBigIntegerCheck() {
            String src = generate(List.of(boolFieldAs("active", "boolean", MetadataFieldType.STRING)));
            assertFalse(src.contains("BigInteger.ONE.equals"));
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
            String srcAsString = generate(List.of(fieldAs("price", "java.math.BigDecimal", MetadataFieldType.STRING)));
            // as=STRING on BigDecimal is a no-op — output identical to DEFAULT
            assertEquals(srcDefault, srcAsString);
        }
    }

    // =========================================================================
    // as = STRING  (force numeric/BigInteger/String → String on chain)
    // =========================================================================

    @Nested
    class AsString {

        @Nested
        class ToMetadataMap {

            @Test
            void intField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldAs("code", "int", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"code\", String.valueOf(order.getCode()))"));
            }

            @Test
            void integerBoxedField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldAs("code", "java.lang.Integer", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"code\", String.valueOf(order.getCode()))"));
            }

            @Test
            void longField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldAs("ts", "long", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"ts\", String.valueOf(order.getTs()))"));
            }

            @Test
            void longBoxedField_serializedAsStringValueOf() {
                String src = generate(List.of(fieldAs("ts", "java.lang.Long", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"ts\", String.valueOf(order.getTs()))"));
            }

            @Test
            void bigIntegerField_serializedViaToString() {
                String src = generate(List.of(fieldAs("amount", "java.math.BigInteger", MetadataFieldType.STRING)));
                assertTrue(src.contains("map.put(\"amount\", order.getAmount().toString())"));
            }

            @Test
            void stringField_asString_stillUses64ByteSplitLogic() {
                String src = generate(List.of(fieldAs("note", "java.lang.String", MetadataFieldType.STRING)));
                assertTrue(src.contains("splitStringEveryNCharacters"));
                assertTrue(src.contains("UTF_8"));
            }

            @Test
            void intField_asString_doesNotEmitBigIntegerValueOf() {
                String src = generate(List.of(fieldAs("code", "int", MetadataFieldType.STRING)));
                assertFalse(src.contains("BigInteger.valueOf"));
            }
        }

        @Nested
        class FromMetadataMap {

            @Test
            void intField_parsedViaIntegerParseInt() {
                String src = generate(List.of(fieldAs("code", "int", MetadataFieldType.STRING)));
                assertTrue(src.contains("if (v instanceof String)"));
                assertTrue(src.contains("Integer.parseInt((String) v)"));
            }

            @Test
            void integerBoxedField_parsedViaIntegerParseInt() {
                String src = generate(List.of(fieldAs("code", "java.lang.Integer", MetadataFieldType.STRING)));
                assertTrue(src.contains("Integer.parseInt((String) v)"));
            }

            @Test
            void longField_parsedViaLongParseLong() {
                String src = generate(List.of(fieldAs("ts", "long", MetadataFieldType.STRING)));
                assertTrue(src.contains("Long.parseLong((String) v)"));
            }

            @Test
            void longBoxedField_parsedViaLongParseLong() {
                String src = generate(List.of(fieldAs("ts", "java.lang.Long", MetadataFieldType.STRING)));
                assertTrue(src.contains("Long.parseLong((String) v)"));
            }

            @Test
            void bigIntegerField_parsedViaNewBigInteger() {
                String src = generate(List.of(fieldAs("amount", "java.math.BigInteger", MetadataFieldType.STRING)));
                assertTrue(src.contains("new BigInteger((String) v)"));
            }

            @Test
            void intField_asString_doesNotEmitBigIntegerInstanceofCheck() {
                String src = generate(List.of(fieldAs("code", "int", MetadataFieldType.STRING)));
                assertFalse(src.contains("instanceof BigInteger"));
            }
        }
    }

    // =========================================================================
    // as = STRING_HEX  (byte[] ↔ hex String)
    // =========================================================================

    @Nested
    class AsStringHex {

        @Test
        void toMetadataMap_byteArray_encodedWithHexUtil() {
            String src = generate(List.of(fieldAs("data", "byte[]", MetadataFieldType.STRING_HEX)));
            assertTrue(src.contains("HexUtil.encodeHexString(order.getData())"));
        }

        @Test
        void toMetadataMap_byteArray_storedUnderCorrectKey() {
            String src = generate(List.of(fieldAs("data", "byte[]", MetadataFieldType.STRING_HEX)));
            assertTrue(src.contains("map.put(\"data\","));
        }

        @Test
        void toMetadataMap_byteArray_hasNullGuard() {
            String src = generate(List.of(fieldAs("data", "byte[]", MetadataFieldType.STRING_HEX)));
            assertTrue(src.contains("if (order.getData() != null)"));
        }

        @Test
        void fromMetadataMap_hexString_decodedWithHexUtil() {
            String src = generate(List.of(fieldAs("data", "byte[]", MetadataFieldType.STRING_HEX)));
            assertTrue(src.contains("if (v instanceof String)"));
            assertTrue(src.contains("HexUtil.decodeHexString((String) v)"));
        }

        @Test
        void fromMetadataMap_doesNotEmitByteArrayInstanceofCheck() {
            String src = generate(List.of(fieldAs("data", "byte[]", MetadataFieldType.STRING_HEX)));
            assertFalse(src.contains("instanceof byte[]"));
        }
    }

    // =========================================================================
    // as = STRING_BASE64  (byte[] ↔ Base64 String)
    // =========================================================================

    @Nested
    class AsStringBase64 {

        @Test
        void toMetadataMap_byteArray_encodedWithBase64() {
            String src = generate(List.of(fieldAs("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
            assertTrue(src.contains("Base64.getEncoder().encodeToString(order.getSig())"));
        }

        @Test
        void toMetadataMap_byteArray_storedUnderCorrectKey() {
            String src = generate(List.of(fieldAs("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
            assertTrue(src.contains("map.put(\"sig\","));
        }

        @Test
        void toMetadataMap_byteArray_hasNullGuard() {
            String src = generate(List.of(fieldAs("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
            assertTrue(src.contains("if (order.getSig() != null)"));
        }

        @Test
        void fromMetadataMap_base64String_decodedWithBase64() {
            String src = generate(List.of(fieldAs("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
            assertTrue(src.contains("if (v instanceof String)"));
            assertTrue(src.contains("Base64.getDecoder().decode((String) v)"));
        }

        @Test
        void fromMetadataMap_doesNotEmitByteArrayInstanceofCheck() {
            String src = generate(List.of(fieldAs("sig", "byte[]", MetadataFieldType.STRING_BASE64)));
            assertFalse(src.contains("instanceof byte[]"));
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
                assertTrue(src.contains("_list.add(_el)"));
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
                assertTrue(src.contains("_list.add(BigInteger.valueOf((long) _el))"));
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
                assertTrue(src.contains("_list.add(BigInteger.valueOf(_el))"));
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
                assertTrue(src.contains("_list.add(_el ? BigInteger.ONE : BigInteger.ZERO)"));
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
                assertTrue(src.contains("_list.add(_el)"));
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
                assertTrue(src.contains("_list.add(BigInteger.valueOf((long) _el))"));
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
                assertTrue(src.contains("_list.add(_el ? BigInteger.ONE : BigInteger.ZERO)"));
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
                assertTrue(src.contains("_list.add(_el)"));
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
                assertTrue(src.contains("_list.add(BigInteger.valueOf((long) _el))"));
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
                assertTrue(src.contains("_list.add(_el ? BigInteger.ONE : BigInteger.ZERO)"));
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
                assertTrue(src.contains("map.put(\"amount\", order.getAmount().get())"));
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
    }
}
