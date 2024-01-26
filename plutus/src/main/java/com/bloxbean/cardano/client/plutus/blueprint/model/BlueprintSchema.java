package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintSchema {
    // for nested schemas
    private List<BlueprintSchema> anyOf;
    private List<BlueprintSchema> oneOf;
    private List<BlueprintSchema> allOf;
    private List<BlueprintSchema> notOf;

    // base fields
    private String title;
    private String description;
    private BlueprintDatatype dataType;
    @JsonAlias({"$comment", "comment"})
    private String comment;

    // Datatype = "bytes"
    @JsonAlias("enum")
    private String[] enumLiterals; // hex-encoded Stringliterals
    private int maxLength;
    private int minLength;

    //Datatype = "integer"
    private int multipleOf;
    private int maximum;
    private int exclusiveMaximum;
    private int minimum;
    private int exclusiveMinimum;

    //Datatype = "list"
    private List<BlueprintSchema> items;
    private int maxItems;
    private int minItems;
    private boolean uniqueItems;

    // Datatype = "map"
    private BlueprintSchema keys;
    private BlueprintSchema values;
//    private int maxItems; // Field already defined in datatype list, leaving it here to avoid confusion to the CIP57 spec
//    private int minItems; // Field already defined in datatype list, leaving it here to avoid confusion to the CIP57 spec

    // Datatype = "constructor"
    private int index;
    private List<Map<String, String>> fields;

}
