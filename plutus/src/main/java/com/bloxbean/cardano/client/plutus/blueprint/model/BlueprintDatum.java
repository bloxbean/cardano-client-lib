package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintDatum {
    private String title;
    private String description;
    private Object purpose;
    private BlueprintSchema schema;

}
