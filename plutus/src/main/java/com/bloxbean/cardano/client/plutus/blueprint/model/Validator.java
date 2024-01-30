package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Validator {
    private String title;
    private String description;
    private BlueprintDatum datum;
    private BlueprintDatum redeemer;
    private List<BlueprintDatum> parameters;
    private String compiledCode;
    private String hash;
}
