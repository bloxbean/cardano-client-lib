package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BlueprintUtil#isAbstractPlutusDataType(BlueprintSchema)}
 *
 * <p>Tests verify compliance with CIP-57 specification:</p>
 * <blockquote>
 * "The dataType keyword is optional. When missing, the instance is implicitly typed
 * as an opaque Plutus Data."
 * </blockquote>
 */
public class BlueprintUtilTest {

    // ========== Null and Edge Cases ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenSchemaIsNull() {
        assertThat(BlueprintUtil.isAbstractPlutusDataType(null)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenEmptySchema() {
        // An empty schema has no dataType and no structure = opaque PlutusData per CIP-57
        BlueprintSchema schema = new BlueprintSchema();

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    // ========== Opaque PlutusData Cases (should return true) ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenNoDataTypeAndNoStructure() {
        // Per CIP-57: no dataType = opaque PlutusData
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Data");
        schema.setDescription("Any Plutus data.");
        // No dataType, no structure

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenOnlyTitleSet() {
        // Title doesn't matter - only dataType and structure matter
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("SomeArbitraryTitle");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenOnlyDescriptionSet() {
        // Description doesn't matter - only dataType and structure matter
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDescription("Some arbitrary description");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_forRealWorldDataType() {
        // Simulate the actual "Data" type from cip113Token.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Data");
        schema.setDescription("Any Plutus data.");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_forRealWorldRedeemerType() {
        // Simulate the actual "Redeemer" type from cip113Token.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Redeemer");
        schema.setDescription("Any Plutus data.");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    // ========== Concrete Types (should return false) ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataType() {
        // Any dataType makes it a concrete type, not opaque
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeInteger() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.integer);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeList() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.list);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeConstructor() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.constructor);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasAnyOf() {
        // anyOf indicates a sum type with defined alternatives
        BlueprintSchema schema = new BlueprintSchema();
        schema.setAnyOf(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasFields() {
        // fields indicates a constructor with defined structure
        BlueprintSchema schema = new BlueprintSchema();
        schema.setFields(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasItems() {
        // items indicates a list with defined element type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setItems(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasKeys() {
        // keys indicates a map type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setKeys(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasValues() {
        // values indicates a map type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setValues(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasLeft() {
        // left indicates a pair type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setLeft(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasRight() {
        // right indicates a pair type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setRight(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    // ========== Real-World Concrete Type Examples ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forByteArrayType() {
        // ByteArray from aftermarket.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forConstructorType() {
        // GlobalStateDatum from cip113Token.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("GlobalStateDatum");
        schema.setDataType(BlueprintDatatype.constructor);
        schema.setIndex(0);

        BlueprintSchema field = new BlueprintSchema();
        field.setTitle("transfers_paused");
        schema.setFields(Collections.singletonList(field));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forSumType() {
        // Optional type with anyOf
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Optional");

        BlueprintSchema someVariant = new BlueprintSchema();
        someVariant.setTitle("Some");
        someVariant.setDataType(BlueprintDatatype.constructor);

        BlueprintSchema noneVariant = new BlueprintSchema();
        noneVariant.setTitle("None");
        noneVariant.setDataType(BlueprintDatatype.constructor);

        schema.setAnyOf(java.util.Arrays.asList(someVariant, noneVariant));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forListType() {
        // List$ByteArray
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.list);

        BlueprintSchema itemSchema = new BlueprintSchema();
        itemSchema.setDataType(BlueprintDatatype.bytes);
        schema.setItems(Collections.singletonList(itemSchema));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forMapType() {
        // Map type with keys and values
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.map);

        BlueprintSchema keySchema = new BlueprintSchema();
        keySchema.setDataType(BlueprintDatatype.bytes);
        schema.setKeys(keySchema);

        BlueprintSchema valueSchema = new BlueprintSchema();
        valueSchema.setDataType(BlueprintDatatype.integer);
        schema.setValues(valueSchema);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    // ========== Mixed Cases ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeButNoStructure() {
        // Even with no structure, if dataType is set, it's a concrete type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);
        schema.setTitle("Data"); // Title doesn't matter

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenNoDataTypeButHasStructure() {
        // Even without dataType, if there's structure, it's not opaque
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Data"); // Title doesn't matter
        schema.setAnyOf(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }
}
