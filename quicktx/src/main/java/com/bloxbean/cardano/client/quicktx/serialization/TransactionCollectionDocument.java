package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document for handling multiple transactions in a single YAML document.
 * This is the main entry point for creating and parsing multi-transaction YAML.
 */
public class TransactionCollectionDocument {

    private Map<String, Object> variables = new HashMap<>();
    private List<AbstractTx<?>> transactions = new ArrayList<>();

    public TransactionCollectionDocument() {
    }

    /**
     * Add a variable to the document.
     * @param key variable name
     * @param value variable value
     * @return this document for method chaining
     */
    public TransactionCollectionDocument addVariable(String key, Object value) {
        variables.put(key, value);
        return this;
    }

    /**
     * Set all variables at once.
     * @param variables the variables map
     * @return this document for method chaining
     */
    public TransactionCollectionDocument setVariables(Map<String, Object> variables) {
        this.variables = new HashMap<>(variables);
        return this;
    }

    /**
     * Add a transaction to the collection.
     * @param transaction the transaction to add (Tx or ScriptTx)
     * @return this document for method chaining
     */
    public TransactionCollectionDocument addTransaction(AbstractTx<?> transaction) {
        this.transactions.add(transaction);
        return this;
    }

    /**
     * Get all variables.
     * @return variables map
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * Get all transactions.
     * @return list of transactions
     */
    public List<AbstractTx<?>> getTransactions() {
        return transactions;
    }

    /**
     * Serialize this collection to YAML format.
     * @return YAML string representation
     */
    public String toYaml() {
        TransactionDocument doc = new TransactionDocument();
        doc.setVariables(variables);

        // Convert each transaction to TxEntry
        List<TransactionDocument.TxEntry> entries = new ArrayList<>();

        for (AbstractTx<?> tx : transactions) {
            if (tx instanceof Tx) {
                Tx regularTx = (Tx) tx;
                TransactionDocument.TxContent content = new TransactionDocument.TxContent();
                content.setIntentions(regularTx.getIntentions());

                // Set attributes from the transaction
                content.setFrom(regularTx.getSender());
                content.setChangeAddress(regularTx.getPublicChangeAddress());
                // Note: other attributes like fromWallet, collectFrom would be set here
                // when available from the transaction object

                entries.add(new TransactionDocument.TxEntry(content));

            } else if (tx instanceof ScriptTx) {
                ScriptTx scriptTx = (ScriptTx) tx;
                TransactionDocument.ScriptTxContent content = new TransactionDocument.ScriptTxContent();
                content.setIntentions(scriptTx.getIntentions());
                content.setChangeAddress(scriptTx.getPublicChangeAddress());

                // Set change datum attributes if present
                try {
                    String changeDatumHex = scriptTx.getChangeDatumHex();
                    if (changeDatumHex != null && !changeDatumHex.isEmpty())
                        content.setChangeDatum(changeDatumHex);
                } catch (Exception e) {
                    // ignore serialization error for datum
                }
                String changeDatumHash = scriptTx.getChangeDatumHash();
                if (changeDatumHash != null && !changeDatumHash.isEmpty())
                    content.setChangeDatumHash(changeDatumHash);

                // Note: scripts and collectFrom configurations would be set here
                // when available from the ScriptTx object

                entries.add(new TransactionDocument.TxEntry(content));
            }
        }

        doc.setTransaction(entries);
        return YamlSerializer.serialize(doc);
    }

    /**
     * Deserialize YAML string to a collection of transactions.
     * @param yaml the YAML string
     * @return list of reconstructed transactions
     * @throws RuntimeException if deserialization fails
     */
    public static List<AbstractTx<?>> fromYaml(String yaml) {
        TransactionDocument doc = YamlSerializer.deserialize(yaml, TransactionDocument.class);
        List<AbstractTx<?>> transactions = new ArrayList<>();
        Map<String, Object> vars = doc.getVariables();

        for (TransactionDocument.TxEntry entry : doc.getTransaction()) {
            if (entry.isTx()) {
                // Create Tx from TxContent (no variable resolution, no legacy processing)
                Tx tx = new Tx();
                TransactionDocument.TxContent content = entry.getTx();

                if (content.getFrom() != null) {
                    String resolvedFrom = VariableResolver.resolve(content.getFrom(), vars);
                    tx.from(resolvedFrom);
                }
                if (content.getChangeAddress() != null) {
                    String resolvedChangeAddr = VariableResolver.resolve(content.getChangeAddress(), vars);
                    tx.withChangeAddress(resolvedChangeAddr);
                }

                if (content.getIntentions() != null) {
                    for (var intention : content.getIntentions()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = intention.resolveVariables(vars);
                        tx.addIntention(resolvedIntention);
                    }
                }

                // Set variables from YAML for intention processing
                tx.setVariables(vars);
                transactions.add(tx);

            } else if (entry.isScriptTx()) {
                // Create ScriptTx from ScriptTxContent (no variable resolution, no legacy processing)
                ScriptTx scriptTx = new ScriptTx();
                TransactionDocument.ScriptTxContent content = entry.getScriptTx();

                // Apply change address and optional change datum/datum hash (with variable resolution)
                if (content.getChangeAddress() != null) {
                    String changeAddr = VariableResolver.resolve(content.getChangeAddress(), vars);
                    if (content.getChangeDatum() != null && !content.getChangeDatum().isEmpty()) {
                        String resolvedDatumHex = VariableResolver.resolve(content.getChangeDatum(), vars);
                        try {
                            var pd = com.bloxbean.cardano.client.plutus.spec.PlutusData.deserialize(
                                    com.bloxbean.cardano.client.util.HexUtil.decodeHexString(resolvedDatumHex));
                            scriptTx.withChangeAddress(changeAddr, pd);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to deserialize change_datum", e);
                        }
                    } else if (content.getChangeDatumHash() != null && !content.getChangeDatumHash().isEmpty()) {
                        String resolvedHash = VariableResolver.resolve(content.getChangeDatumHash(), vars);
                        scriptTx.withChangeAddress(changeAddr, resolvedHash);
                    } else {
                        scriptTx.withChangeAddress(changeAddr);
                    }
                }

                if (content.getIntentions() != null) {
                    for (var intention : content.getIntentions()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = intention.resolveVariables(vars);
                        scriptTx.addIntention(resolvedIntention);
                    }
                }

                // Set variables from YAML for intention processing
                scriptTx.setVariables(vars);
                
                // Note: Script attachments and collectFrom configurations can be restored in a later phase.
                transactions.add(scriptTx);
            }
        }

        return transactions;
    }

    /**
     * Create a collection document from a single transaction.
     * @param transaction the transaction
     * @return collection document with single transaction
     */
    public static TransactionCollectionDocument fromTransaction(AbstractTx<?> transaction) {
        return new TransactionCollectionDocument().addTransaction(transaction);
    }


    /**
     * Create a collection document from multiple transactions.
     * @param transactions the transactions
     * @return collection document with all transactions
     */
    public static TransactionCollectionDocument fromTransactions(List<AbstractTx<?>> transactions) {
        TransactionCollectionDocument doc = new TransactionCollectionDocument();
        transactions.forEach(doc::addTransaction);
        return doc;
    }
}
