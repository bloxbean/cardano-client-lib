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
}
