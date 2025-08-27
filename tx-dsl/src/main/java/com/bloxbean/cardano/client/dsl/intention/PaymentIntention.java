package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Unified intention for all payment operations (payToAddress and payToContract).
 * Supports optional fields for scripts, datum, and other payment variants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntention implements TxIntention {
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("amounts")
    private List<Amount> amounts;
    
    // Optional fields for payment variants
    
    @JsonProperty("script")
    private Script script;  // For payToAddress(..., script) - native Jackson serialization
    
    @JsonProperty("script_ref_bytes")
    private byte[] scriptRefBytes;  // For direct script reference bytes
    
    @JsonProperty("datum_hex")
    private String datumHex;  // For payToContract - PlutusData.serializeToHex()
    
    @JsonProperty("datum_hash")
    private String datumHash;  // For payToContract with hash
    
    @JsonProperty("ref_script")
    private Script refScript;  // For contract payments with reference scripts
    
    // Legacy constructors for backward compatibility
    public PaymentIntention(String address, Amount amount) {
        this.address = address;
        this.amounts = List.of(amount);
    }
    
    public PaymentIntention(String address, List<Amount> amounts) {
        this.address = address;
        this.amounts = amounts;
    }
    
    @Override
    public String getType() {
        return "payment";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve address from variables if it's a variable
        String resolvedAddress = IntentionHelper.resolveVariable(address, variables);
        
        // Resolve amounts (they may contain variables)
        List<Amount> resolvedAmounts = amounts;
        // Note: Amount variable resolution can be added later if needed
        
        // Smart dispatch based on available fields
        if (datumHex != null) {
            // This is a contract payment
            try {
                PlutusData datum = PlutusData.deserialize(
                    CborSerializationUtil.deserialize(HexUtil.decodeHexString(datumHex))
                );
                
                if (refScript != null) {
                    // Contract payment with reference script
                    if (resolvedAmounts.size() == 1) {
                        tx.payToContract(resolvedAddress, resolvedAmounts.get(0), datum, refScript);
                    } else {
                        tx.payToContract(resolvedAddress, resolvedAmounts, datum, refScript);
                    }
                } else {
                    // Contract payment without reference script
                    if (resolvedAmounts.size() == 1) {
                        tx.payToContract(resolvedAddress, resolvedAmounts.get(0), datum);
                    } else {
                        tx.payToContract(resolvedAddress, resolvedAmounts, datum);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize datum from hex: " + datumHex, e);
            }
        } else if (datumHash != null) {
            // Contract payment with datum hash
            if (resolvedAmounts.size() == 1) {
                tx.payToContract(resolvedAddress, resolvedAmounts.get(0), datumHash);
            } else {
                tx.payToContract(resolvedAddress, resolvedAmounts, datumHash);
            }
        } else if (script != null) {
            // Regular payment with script
            if (resolvedAmounts.size() == 1) {
                tx.payToAddress(resolvedAddress, resolvedAmounts.get(0), script);
            } else {
                tx.payToAddress(resolvedAddress, resolvedAmounts, script);
            }
        } else if (scriptRefBytes != null) {
            // Regular payment with script reference bytes
            tx.payToAddress(resolvedAddress, resolvedAmounts, scriptRefBytes);
        } else {
            // Regular payment
            if (resolvedAmounts.size() == 1) {
                tx.payToAddress(resolvedAddress, resolvedAmounts.get(0));
            } else {
                tx.payToAddress(resolvedAddress, resolvedAmounts);
            }
        }
    }
    
    // Factory methods for clean API
    public static PaymentIntention toAddress(String address) {
        return PaymentIntention.builder().address(address).build();
    }
    
    public static PaymentIntention toContract(String address) {
        return PaymentIntention.builder().address(address).build();
    }
    
    // Convenience methods for setting PlutusData
    public PaymentIntention withDatum(PlutusData datum) {
        this.datumHex = datum.serializeToHex();
        return this;
    }
    
    // Check if this is a contract payment
    @JsonIgnore
    public boolean isContractPayment() {
        return datumHex != null || datumHash != null;
    }
    
    // Check if this has a script attachment
    @JsonIgnore
    public boolean hasScript() {
        return script != null || scriptRefBytes != null || refScript != null;
    }
}