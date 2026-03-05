package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldType {

    private Type type;
    private JavaType javaType;
    private String encoding;
    private boolean isCollection;
    private String fqTypeName; //Fully qualified type name. This can be used to get the exact type.
    private List<FieldType> genericTypes = new ArrayList<>();
    /**
     * {@code true} when the type implements {@code RawData} — i.e., its on-chain
     * encoding is raw {@code PlutusData} (e.g. {@code BytesPlutusData}) rather than
     * {@code ConstrPlutusData}.
     * <p>
     * When set, the generated deserialization code passes the raw {@code PlutusData}
     * directly to the converter without casting to {@code ConstrPlutusData}:
     * <pre>new FooConverter().fromPlutusData(data.get(i))</pre>
     */
    private boolean rawDataType;

    /**
     * Fully-qualified converter class name for this type (e.g.,
     * {@code "com.example.Credential.CredentialConverter"} for an interface type,
     * {@code "com.example.converter.AddressConverter"} for a top-level type).
     * <p>
     * When set, {@code getConverterClassFromField} uses this directly via
     * {@code ClassName.bestGuess()} instead of applying heuristic rules.
     */
    private String converterClassFqn;

    /**
     * {@code true} when the type implements {@code Data<T>} — a constr-based shared
     * type that has its own {@code toPlutusData()} instance method and
     * {@code fromPlutusData()} static method.
     * <p>
     * When set, the generated converter inlines calls directly:
     * <pre>obj.getField().toPlutusData()</pre>
     * <pre>Type.fromPlutusData(data)</pre>
     * instead of delegating through a generated converter class.
     */
    private boolean dataType;

    /**
     * Convenience: {@code true} when this field's type is any kind of shared type
     * (either {@link #dataType} or {@link #rawDataType}).
     */
    public boolean isSharedType() {
        return dataType || rawDataType;
    }

    public boolean isMap() {
        return javaType == JavaType.MAP;
    }

    public boolean isList() {
        return javaType == JavaType.LIST;
    }

}
