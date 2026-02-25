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
     * True when the type's converter accepts raw {@code PlutusData} rather than {@code ConstrPlutusData}.
     * Applies to bytes-wrapper shared types (e.g., VerificationKeyHash) and list-based shared types (e.g., Pair).
     */
    private boolean rawPlutusDataConverter;

    public boolean isMap() {
        return javaType == JavaType.MAP;
    }

    public boolean isList() {
        return javaType == JavaType.LIST;
    }

}
