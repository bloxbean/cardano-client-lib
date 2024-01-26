package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptDto {
    private String language;
    private Map<String, String> json;
    private String cbor;
}
