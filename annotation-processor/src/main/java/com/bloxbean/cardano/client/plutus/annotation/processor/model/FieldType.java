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
     * Indicates that this field's on-chain representation is <em>not</em> a {@code ConstrPlutusData}.
     * <p>
     * Most custom/complex types in Plutus are encoded as constructors ({@code ConstrPlutusData}),
     * and the generated deserialization code casts accordingly:
     * <pre>new FooConverter().fromPlutusData((ConstrPlutusData) data.get(i))</pre>
     *
     * However, some shared types use a different encoding:
     * <ul>
     *   <li>Bytes-wrapper types (e.g., {@code VerificationKeyHash}, {@code ScriptHash}) are encoded
     *       as raw {@code BytesPlutusData}</li>
     *   <li>Pair/Tuple types are encoded as {@code ListPlutusData}</li>
     * </ul>
     *
     * When this flag is {@code true}, the generated code passes the raw {@code PlutusData} directly
     * to the converter without casting to {@code ConstrPlutusData}:
     * <pre>new FooConverter().fromPlutusData(data.get(i))</pre>
     */
    private boolean nonConstrPlutusData;

    public boolean isMap() {
        return javaType == JavaType.MAP;
    }

    public boolean isList() {
        return javaType == JavaType.LIST;
    }

}
