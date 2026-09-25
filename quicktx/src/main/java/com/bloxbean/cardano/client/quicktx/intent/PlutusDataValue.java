package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.PlutusDataYamlUtil;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;

/**
 * A deferred Plutus-data value supplied as a Java value, structured YAML, or CBOR hex.
 *
 * <p>The value object owns representation and variable resolution, but not requiredness. An
 * owning intent decides whether the corresponding property may be absent. When this object is
 * present it always contains exactly one representation.</p>
 */
public final class PlutusDataValue {
    public enum Form {
        RESOLVED,
        STRUCTURED,
        CBOR_HEX
    }

    private final Form form;
    private final PlutusData value;
    private final JsonNode structured;
    private final String cborHex;

    private PlutusDataValue(Form form, PlutusData value, JsonNode structured, String cborHex) {
        this.form = Objects.requireNonNull(form, "form");
        this.value = value;
        this.structured = structured == null ? null : structured.deepCopy();
        this.cborHex = cborHex;
        int representations = (value == null ? 0 : 1)
                + (structured == null ? 0 : 1)
                + (cborHex == null ? 0 : 1);
        if (representations != 1)
            throw new IllegalArgumentException("Exactly one Plutus-data representation is required");
    }

    public static PlutusDataValue of(PlutusData value) {
        return new PlutusDataValue(Form.RESOLVED,
                Objects.requireNonNull(value, "Plutus data is required"), null, null);
    }

    public static PlutusDataValue ofNullable(PlutusData value) {
        return value == null ? null : of(value);
    }

    public static PlutusDataValue structured(JsonNode value) {
        return new PlutusDataValue(Form.STRUCTURED, null,
                Objects.requireNonNull(value, "Structured Plutus data is required"), null);
    }

    public static PlutusDataValue cborHex(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Plutus-data CBOR hex is required");
        return new PlutusDataValue(Form.CBOR_HEX, null, null, value);
    }

    public static PlutusDataValue readStructured(PlutusDataValue current, JsonNode value,
                                                 String fieldName) {
        rejectSecondRepresentation(current, fieldName);
        return structured(value);
    }

    public static PlutusDataValue readCborHex(PlutusDataValue current, String value,
                                              String fieldName) {
        rejectSecondRepresentation(current, fieldName);
        return cborHex(value);
    }

    private static void rejectSecondRepresentation(PlutusDataValue current, String fieldName) {
        if (current != null)
            throw new IllegalArgumentException("Exactly one of " + fieldName + " and "
                    + fieldName + "_hex may be provided");
    }

    public PlutusDataValue resolve(Map<String, Object> variables) {
        return resolve(variables, "Plutus data");
    }

    public PlutusDataValue resolve(Map<String, Object> variables, String fieldName) {
        if (form == Form.RESOLVED) return this;
        Map<String, Object> resolvedVariables = variables == null ? Map.of() : variables;
        try {
            if (form == Form.STRUCTURED)
                return of(PlutusDataYamlUtil.fromYamlNode(structured, resolvedVariables));

            String resolvedHex = VariableResolver.resolve(cborHex, resolvedVariables);
            if (resolvedHex == null || resolvedHex.isBlank())
                throw new IllegalArgumentException("resolved CBOR hex is empty");
            return of(PlutusData.deserialize(HexUtil.decodeHexString(resolvedHex)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve " + fieldName + ": "
                    + e.getMessage(), e);
        }
    }

    public PlutusData requireResolved() {
        return requireResolved("Plutus data");
    }

    public PlutusData requireResolved(String fieldName) {
        if (form != Form.RESOLVED)
            throw new IllegalStateException(fieldName + " must be resolved before transaction planning");
        return value;
    }

    public Form getForm() {
        return form;
    }

    public boolean isResolved() {
        return form == Form.RESOLVED;
    }

    /** Structured YAML representation, or null when the unresolved source is CBOR hex. */
    public JsonNode structuredForYaml() {
        if (form == Form.CBOR_HEX) return null;
        return form == Form.RESOLVED
                ? PlutusDataYamlUtil.toYamlNode(value) : structured.deepCopy();
    }

    /** CBOR-hex YAML representation, or null for structured and resolved values. */
    public String cborHexForYaml() {
        return form == Form.CBOR_HEX ? cborHex : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PlutusDataValue)) return false;
        PlutusDataValue other = (PlutusDataValue) obj;
        return form == other.form && Objects.equals(value, other.value)
                && Objects.equals(structured, other.structured)
                && Objects.equals(cborHex, other.cborHex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(form, value, structured, cborHex);
    }

    @Override
    public String toString() {
        return "PlutusDataValue{" + form + '}';
    }
}
