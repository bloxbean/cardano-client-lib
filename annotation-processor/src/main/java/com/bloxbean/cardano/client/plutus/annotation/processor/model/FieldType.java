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
    private List<FieldType> genericTypes = new ArrayList<>();
}
