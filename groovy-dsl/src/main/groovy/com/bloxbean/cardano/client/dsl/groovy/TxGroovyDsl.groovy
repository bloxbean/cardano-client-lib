package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.api.model.Amount
import com.bloxbean.cardano.client.dsl.TxDsl
import com.bloxbean.cardano.client.quicktx.Tx

/**
 * Groovy DSL for building transactions with natural syntax
 * Wraps TxDsl and provides Groovy-friendly methods
 */
class TxGroovyDsl {
    private TxDsl txDsl = new TxDsl()
    private Map<String, String> addressAliases = [:]
    
    /**
     * Set the sender address
     * Examples: 
     *   from "addr1..."
     *   from treasury  (if treasury is a variable)
     */
    def from(address) {
        String resolvedAddress = resolveAddress(address)
        txDsl.from(resolvedAddress)
        return this
    }
    
    /**
     * Simple payment with closure for configuration
     * Example:
     *   pay {
     *     to "addr1..."
     *     amount 5.ada
     *   }
     */
    def pay(Closure closure) {
        def payment = new PaymentBuilder()
        closure.delegate = payment
        closure.resolveStrategy = Closure.OWNER_FIRST
        closure()
        
        if (payment.amounts) {
            txDsl.payToAddress(payment.recipient, payment.amounts)
        } else if (payment.singleAmount) {
            txDsl.payToAddress(payment.recipient, payment.singleAmount)
        }
        return this
    }
    
    /**
     * Direct payment method
     * Example: payTo "addr1...", 5.ada
     */
    def payTo(address, Amount amount) {
        String resolvedAddress = resolveAddress(address)
        txDsl.payToAddress(resolvedAddress, amount)
        return this
    }
    
    /**
     * Natural language style: send X to Y
     * Example: send 5.ada to "addr1..."
     */
    def send(Amount amount) {
        return new FluentSender(txGroovyDsl: this, amount: amount)
    }
    
    /**
     * Define variables for template substitution
     * Example:
     *   variables {
     *     treasury = "addr1..."
     *     alice = "addr1..."
     *   }
     */
    def variables(Closure closure) {
        closure.delegate = new VariableBuilder(txDsl: txDsl, aliases: addressAliases)
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
        return this
    }
    
    /**
     * Add a single variable
     */
    def withVariable(String name, Object value) {
        txDsl.withVariable(name, value)
        if (value instanceof String && value.startsWith("addr")) {
            addressAliases[name] = value
        }
        return this
    }
    
    /**
     * Get the underlying TxDsl
     */
    TxDsl build() {
        return txDsl
    }
    
    /**
     * Get the underlying Tx for use with QuickTxBuilder
     */
    Tx unwrap() {
        return txDsl.unwrap()
    }
    
    /**
     * Serialize to YAML
     */
    String toYaml() {
        return txDsl.toYaml()
    }
    
    /**
     * Handle undefined properties as variable references
     */
    def propertyMissing(String name) {
        // Check if it's an address alias
        if (addressAliases.containsKey(name)) {
            return addressAliases[name]
        }
        // Return as template variable
        return '${' + name + '}'
    }
    
    /**
     * Resolve address - handles both direct addresses and aliases
     */
    private String resolveAddress(address) {
        if (address instanceof String) {
            return address
        }
        // If it's returned from propertyMissing, it's already resolved
        return address.toString()
    }
    
    /**
     * Helper class for building payments with closure
     */
    static class PaymentBuilder {
        String recipient
        Amount singleAmount
        List<Amount> amounts = []
        
        def to(address) {
            this.recipient = address.toString()
        }
        
        def amount(Amount amt) {
            this.singleAmount = amt
        }
        
        def amounts(List<Amount> amts) {
            this.amounts = amts
        }
    }
    
    /**
     * Helper class for fluent send syntax
     */
    static class FluentSender {
        TxGroovyDsl txGroovyDsl
        Amount amount
        
        def to(address) {
            txGroovyDsl.payTo(address, amount)
            return txGroovyDsl
        }
    }
    
    /**
     * Helper class for variable definition
     */
    static class VariableBuilder {
        TxDsl txDsl
        Map<String, String> aliases
        
        void setProperty(String name, value) {
            txDsl.withVariable(name, value)
            if (value instanceof String && value.startsWith("addr")) {
                aliases[name] = value
            }
        }
    }
}