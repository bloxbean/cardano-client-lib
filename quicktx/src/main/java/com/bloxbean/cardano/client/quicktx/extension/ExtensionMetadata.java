package com.bloxbean.cardano.client.quicktx.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable metadata binding a document-local namespace to an extension runtime. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtensionMetadata {
    private String extension;

    /** Version of this extension's serialized intent schema, independent of the TxPlan version. */
    @JsonProperty("schema_version")
    private String schemaVersion;

    private String protocol;

    /** Optional extension-defined informational provenance; QuickTx does not interpret it. */
    @JsonProperty("contract_version")
    private String contractVersion;

    @Builder.Default
    private Map<String, Object> deployment = new LinkedHashMap<>();
}
