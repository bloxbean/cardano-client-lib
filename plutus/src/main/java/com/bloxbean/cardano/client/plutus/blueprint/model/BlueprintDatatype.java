package com.bloxbean.cardano.client.plutus.blueprint.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum BlueprintDatatype {
    @JsonAlias({"integer", "#integer"})
    integer,
    @JsonAlias({"bytes", "#bytes"})
    bytes,
    @JsonAlias({"list", "#list"})
    list,
    map,
    constructor,
    @JsonAlias({"string", "#string"})
    string,
    @JsonAlias({"boolean", "#boolean", "bool", "#bool"})
    bool,
     @JsonAlias({"unit", "#unit"})
    pair,
}
