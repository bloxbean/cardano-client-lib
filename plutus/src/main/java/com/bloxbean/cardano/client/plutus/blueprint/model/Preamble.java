package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preamble {
    String title;
    String description;
    String version;
    PlutusVersion plutusVersion;
    String license;
}
