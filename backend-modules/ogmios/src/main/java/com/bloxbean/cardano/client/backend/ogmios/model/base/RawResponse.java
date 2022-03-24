package com.bloxbean.cardano.client.backend.ogmios.model.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawResponse {
    private String type;
    private String version;
    private String servicename;
    private String methodname;
    private JsonNode fault;
    private JsonNode result;
    private JsonNode reflection;
}
