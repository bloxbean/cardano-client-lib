package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintSchema {

    @JsonAlias({"$ref", "ref"})
    private String ref;

    @JsonIgnore
    private BlueprintSchema refSchema;

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
    private BlueprintSchema items;
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
    private List<BlueprintSchema> fields;

    // Datatype = "pair"
    private BlueprintSchema left;
    private BlueprintSchema right;

    public void copyFrom(BlueprintSchema blueprintSchema) {
        this.refSchema = blueprintSchema;

        this.anyOf = this.anyOf != null ? this.anyOf : blueprintSchema.anyOf;
        this.oneOf = this.oneOf != null ? this.oneOf : blueprintSchema.oneOf;
        this.allOf = this.allOf != null ? this.allOf : blueprintSchema.allOf;
        this.notOf = this.notOf != null ? this.notOf : blueprintSchema.notOf;
        this.title = this.title != null ? this.title : blueprintSchema.title;
        this.description = this.description != null ? this.description : blueprintSchema.description;
        this.dataType = this.dataType != null ? this.dataType : blueprintSchema.dataType;
        this.comment = this.comment != null ? this.comment : blueprintSchema.comment;
        this.enumLiterals = this.enumLiterals != null ? this.enumLiterals : blueprintSchema.enumLiterals;
        this.maxLength = this.maxLength != 0 ? this.maxLength : blueprintSchema.maxLength;
        this.minLength = this.minLength != 0 ? this.minLength : blueprintSchema.minLength;
        this.multipleOf = this.multipleOf != 0 ? this.multipleOf : blueprintSchema.multipleOf;
        this.maximum = this.maximum != 0 ? this.maximum : blueprintSchema.maximum;
        this.exclusiveMaximum = this.exclusiveMaximum != 0 ? this.exclusiveMaximum : blueprintSchema.exclusiveMaximum;
        this.minimum = this.minimum != 0 ? this.minimum : blueprintSchema.minimum;
        this.exclusiveMinimum = this.exclusiveMinimum != 0 ? this.exclusiveMinimum : blueprintSchema.exclusiveMinimum;
        this.items = this.items != null ? this.items : blueprintSchema.items;
        this.maxItems = this.maxItems != 0 ? this.maxItems : blueprintSchema.maxItems;
        this.minItems = this.minItems != 0 ? this.minItems : blueprintSchema.minItems;
        this.uniqueItems = this.uniqueItems || blueprintSchema.uniqueItems;
        this.keys = this.keys != null ? this.keys : blueprintSchema.keys;
        this.values = this.values != null ? this.values : blueprintSchema.values;
        this.index = this.index != 0 ? this.index : blueprintSchema.index;
        this.fields = this.fields != null ? this.fields : blueprintSchema.fields;
        this.left = this.left != null ? this.left : blueprintSchema.left;
        this.right = this.right != null ? this.right : blueprintSchema.right;
    }
}
