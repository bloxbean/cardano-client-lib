package com.bloxbean.cardano.client.quicktx.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PlanContext holds optional composition context for QuickTxBuilder integration.
 * This information is used during transaction building and submission but is not
 * part of the transaction itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanContext {

    /**
     * Fee payer address.
     * Used by QuickTxBuilder for fee calculation and payment.
     */
    @JsonProperty("fee_payer")
    private String feePayer;

    /**
     * Collateral payer address for script transactions.
     * Used when the transaction contains Plutus scripts.
     */
    @JsonProperty("collateral_payer")
    private String collateralPayer;

    /**
     * UTXO selection strategy identifier.
     * Examples: "DEFAULT", "LARGEST_FIRST", "RANDOM_IMPROVE"
     */
    @JsonProperty("utxo_selection_strategy")
    private String utxoSelectionStrategy;

    /**
     * Signer reference or alias.
     * This is an opaque identifier, not actual key material.
     * The application must map this to actual signing capability.
     */
    @JsonProperty("signer")
    private String signer;

    /**
     * Maximum fee limit in lovelace.
     * Transaction building will fail if fees exceed this limit.
     */
    @JsonProperty("max_fee")
    private String maxFee; // Stored as string for precision

    /**
     * Time-to-live (TTL) for the transaction.
     * Slot number after which the transaction becomes invalid.
     */
    @JsonProperty("ttl")
    private Long ttl;

    /**
     * Validity start slot.
     * Transaction is valid starting from this slot.
     */
    @JsonProperty("validity_start")
    private Long validityStart;

    /**
     * Network ID.
     * 0 for testnet, 1 for mainnet.
     */
    @JsonProperty("network_id")
    private Integer networkId;

    /**
     * Additional context parameters.
     * Allows for extension without modifying the schema.
     */
    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    /**
     * Creates a deep copy of this context.
     */
    public PlanContext deepCopy() {
        return PlanContext.builder()
            .feePayer(this.feePayer)
            .collateralPayer(this.collateralPayer)
            .utxoSelectionStrategy(this.utxoSelectionStrategy)
            .signer(this.signer)
            .maxFee(this.maxFee)
            .ttl(this.ttl)
            .validityStart(this.validityStart)
            .networkId(this.networkId)
            .parameters(this.parameters != null ? new java.util.HashMap<>(this.parameters) : null)
            .build();
    }

    // Convenience methods

    /**
     * Get max fee as Long.
     */
    public Long getMaxFeeAsLong() {
        return maxFee != null ? Long.parseLong(maxFee) : null;
    }

    /**
     * Set max fee from Long.
     */
    public void setMaxFeeFromLong(Long fee) {
        this.maxFee = fee != null ? fee.toString() : null;
    }

    /**
     * Add a parameter to the context.
     */
    public PlanContext addParameter(String key, Object value) {
        if (parameters == null) {
            parameters = new java.util.HashMap<>();
        }
        parameters.put(key, value);
        return this;
    }

    /**
     * Check if this is a mainnet context.
     */
    public boolean isMainnet() {
        return networkId != null && networkId == 1;
    }

    /**
     * Check if this is a testnet context.
     */
    public boolean isTestnet() {
        return networkId == null || networkId == 0;
    }
}
