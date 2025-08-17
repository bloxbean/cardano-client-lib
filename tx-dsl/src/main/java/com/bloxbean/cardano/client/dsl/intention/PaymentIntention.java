package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.dsl.TxDsl;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Intention for payment operations (payToAddress).
 */
public class PaymentIntention implements TxIntention {
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("amounts")
    private List<Amount> amounts;
    
    // Default constructor for Jackson
    public PaymentIntention() {
    }
    
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
    public void apply(TxDsl txDsl) {
        if (amounts.size() == 1) {
            txDsl.payToAddress(address, amounts.get(0));
        } else {
            txDsl.payToAddress(address, amounts);
        }
    }
    
    public String getAddress() {
        return address;
    }
    
    public List<Amount> getAmounts() {
        return amounts;
    }
}