package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.dsl.intention.FromIntention;
import com.bloxbean.cardano.client.dsl.intention.PaymentIntention;
import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.bloxbean.cardano.client.dsl.model.TransactionDocument;
import com.bloxbean.cardano.client.dsl.serialization.YamlSerializer;
import com.bloxbean.cardano.client.quicktx.Tx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorator for Tx that adds serialization capabilities while maintaining 100% API compatibility.
 * This class wraps a Tx instance and delegates all operations while capturing intentions for serialization.
 */
public class TxDsl {
    private final Tx tx;
    private final List<TxIntention> intentions;
    private final Map<String, Object> variables;
    
    /**
     * Creates a new TxDsl instance wrapping a new Tx.
     */
    public TxDsl() {
        this.tx = new Tx();
        this.intentions = new ArrayList<>();
        this.variables = new HashMap<>();
    }
    
    /**
     * Returns the wrapped Tx instance for use with QuickTxBuilder.
     * This maintains 100% compatibility with existing code.
     * 
     * @return the wrapped Tx instance
     */
    public Tx unwrap() {
        return tx;
    }
    
    /**
     * Pay to an address with a specific amount.
     * Delegates to the wrapped Tx and captures the intention.
     * 
     * @param address the receiver address
     * @param amount the amount to send
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, Amount amount) {
        intentions.add(new PaymentIntention(address, amount));
        tx.payToAddress(address, amount);
        return this;
    }
    
    /**
     * Pay to an address with multiple amounts.
     * Delegates to the wrapped Tx and captures the intention.
     * 
     * @param address the receiver address
     * @param amounts the amounts to send
     * @return this TxDsl for method chaining
     */
    public TxDsl payToAddress(String address, List<Amount> amounts) {
        intentions.add(new PaymentIntention(address, amounts));
        tx.payToAddress(address, amounts);
        return this;
    }
    
    /**
     * Set the sender address for the transaction.
     * Delegates to the wrapped Tx and captures the intention.
     * 
     * @param sender the sender address
     * @return this TxDsl for method chaining
     */
    public TxDsl from(String sender) {
        intentions.add(new FromIntention(sender));
        tx.from(sender);
        return this;
    }
    
    /**
     * Get the captured intentions for serialization.
     * 
     * @return unmodifiable list of intentions
     */
    public List<TxIntention> getIntentions() {
        return Collections.unmodifiableList(intentions);
    }
    
    /**
     * Serialize this TxDsl to YAML format.
     * 
     * @return YAML string representation
     */
    public String toYaml() {
        TransactionDocument doc = new TransactionDocument(intentions);
        doc.setVariables(variables);
        
        // Note: Context is now handled by TxDslBuilder, not TxDsl
        
        return YamlSerializer.serialize(doc);
    }
    
    /**
     * Create a TxDsl from YAML string.
     * 
     * @param yaml the YAML string
     * @return reconstructed TxDsl
     */
    public static TxDsl fromYaml(String yaml) {
        TransactionDocument doc = YamlSerializer.deserialize(yaml);
        TxDsl txDsl = new TxDsl();
        
        // Set variables
        if (doc.getVariables() != null) {
            txDsl.variables.putAll(doc.getVariables());
        }
        
        // Replay intentions from the first transaction entry (MVP: support single tx)
        if (doc.getTransaction() != null && !doc.getTransaction().isEmpty()) {
            TransactionDocument.TxEntry firstTx = doc.getTransaction().get(0);
            if (firstTx.getTx() != null && firstTx.getTx().getIntentions() != null) {
                for (TxIntention intention : firstTx.getTx().getIntentions()) {
                    intention.apply(txDsl);
                }
            }
        }
        
        // Note: Context is now handled by TxDslBuilder, not TxDsl
        
        return txDsl;
    }
    
    /**
     * Add a variable for template substitution.
     * 
     * @param name variable name
     * @param value variable value
     * @return this TxDsl for method chaining
     */
    public TxDsl withVariable(String name, Object value) {
        variables.put(name, value);
        return this;
    }
    
    /**
     * Get the variables map for external access.
     * 
     * @return unmodifiable view of variables map
     */
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }
}