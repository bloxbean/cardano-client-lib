package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.TxInputIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxValidatorIntent;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plan for handling multiple transactions in a single YAML document.
 * This is the main entry point for all YAML serialization/deserialization operations.
 */
public class TxPlan {

    private Map<String, Object> variables = new HashMap<>();
    private List<AbstractTx<?>> txList = new ArrayList<>();

    // TxContext properties for serialization
    private String feePayer;
    private String collateralPayer;
    private Set<String> requiredSigners = new HashSet<>();
    private Long validFromSlot;
    private Long validToSlot;

    public TxPlan() {
    }

    /**
     * Add a variable to the plan.
     * @param key variable name
     * @param value variable value
     * @return this plan for method chaining
     */
    public TxPlan addVariable(String key, Object value) {
        variables.put(key, value);
        return this;
    }

    /**
     * Set all variables at once.
     * @param variables the variables map
     * @return this document for method chaining
     */
    public TxPlan setVariables(Map<String, Object> variables) {
        this.variables = new HashMap<>(variables);
        return this;
    }

    /**
     * Add a transaction to the plan.
     * @param transaction the transaction to add (Tx or ScriptTx)
     * @return this plan for method chaining
     */
    public TxPlan addTransaction(AbstractTx<?> transaction) {
        this.txList.add(transaction);
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
    public List<AbstractTx<?>> getTxs() {
        return txList;
    }

    /**
     * Set the fee payer address.
     * Naming aligned with QuickTxBuilder.TxContext for consistency.
     * @param address the fee payer address
     * @return this plan for method chaining
     */
    public TxPlan feePayer(String address) {
        this.feePayer = address;
        return this;
    }

    /**
     * Get the fee payer address.
     * @return fee payer address
     */
    public String getFeePayer() {
        return feePayer;
    }

    /**
     * Set the collateral payer address.
     * Naming aligned with QuickTxBuilder.TxContext for consistency.
     * @param address the collateral payer address
     * @return this plan for method chaining
     */
    public TxPlan collateralPayer(String address) {
        this.collateralPayer = address;
        return this;
    }

    /**
     * Get the collateral payer address.
     * @return collateral payer address
     */
    public String getCollateralPayer() {
        return collateralPayer;
    }

    /**
     * Add required signers (as hex-encoded credential hashes).
     * Naming aligned with QuickTxBuilder.TxContext for consistency.
     * @param credentials required signer credentials as hex strings
     * @return this plan for method chaining
     */
    public TxPlan withRequiredSigners(String... credentials) {
        if (credentials != null) {
            for (String credential : credentials) {
                this.requiredSigners.add(credential);
            }
        }
        return this;
    }

    /**
     * Set all required signers at once.
     * @param requiredSigners set of required signer credentials as hex strings
     * @return this plan for method chaining
     */
    public TxPlan setRequiredSigners(Set<String> requiredSigners) {
        this.requiredSigners = new HashSet<>(requiredSigners);
        return this;
    }

    /**
     * Get required signers.
     * @return set of required signer credentials as hex strings
     */
    public Set<String> getRequiredSigners() {
        return requiredSigners;
    }

    /**
     * Set validity window start slot.
     * Naming aligned with QuickTxBuilder.TxContext for consistency.
     * @param slot the start slot
     * @return this plan for method chaining
     */
    public TxPlan validFrom(long slot) {
        this.validFromSlot = slot;
        return this;
    }

    /**
     * Get validity window start slot.
     * @return start slot (null if no lower bound)
     */
    public Long getValidFromSlot() {
        return validFromSlot;
    }

    /**
     * Set validity window end slot.
     * Naming aligned with QuickTxBuilder.TxContext for consistency.
     * @param slot the end slot
     * @return this plan for method chaining
     */
    public TxPlan validTo(long slot) {
        this.validToSlot = slot;
        return this;
    }

    /**
     * Get validity window end slot.
     * @return end slot (null if no upper bound)
     */
    public Long getValidToSlot() {
        return validToSlot;
    }

    /**
     * Serialize this plan to YAML format.
     * @return YAML string representation
     */
    public String toYaml() {
        TransactionDocument doc = new TransactionDocument();
        doc.setVariables(variables);

        // Set context properties if any are specified
        if (feePayer != null || collateralPayer != null || !requiredSigners.isEmpty() ||
            validFromSlot != null || validToSlot != null) {
            TransactionDocument.TxContext context = new TransactionDocument.TxContext();
            context.setFeePayer(feePayer);
            context.setCollateralPayer(collateralPayer);
            if (!requiredSigners.isEmpty()) {
                context.setRequiredSigners(requiredSigners);
            }
            context.setValidFromSlot(validFromSlot);
            context.setValidToSlot(validToSlot);
            doc.setContext(context);
        }

        // Convert each transaction to TxEntry
        List<TransactionDocument.TxEntry> entries = new ArrayList<>();

        for (AbstractTx<?> tx : txList) {
            if (tx instanceof Tx) {
                Tx regularTx = (Tx) tx;
                TransactionDocument.TxContent content = new TransactionDocument.TxContent();

                // Separate input intentions from regular intentions
                List<TxInputIntent> inputIntentions = new ArrayList<>();
                List<TxIntent> regularIntentions = new ArrayList<>();

                if (regularTx.getIntentions() != null) {
                    for (TxIntent intention : regularTx.getIntentions()) {
                        if (intention instanceof TxInputIntent) {
                            inputIntentions.add((TxInputIntent) intention);
                        } else {
                            regularIntentions.add(intention);
                        }
                    }
                }

                // Set inputs and intentions separately
                if (!inputIntentions.isEmpty()) {
                    content.setInputs(inputIntentions);
                }
                if (!regularIntentions.isEmpty()) {
                    content.setIntents(regularIntentions);
                }

                // Set attributes from the transaction
                content.setFrom(regularTx.getSender());
                content.setChangeAddress(regularTx.getPublicChangeAddress());
                // Note: other attributes like fromWallet, collectFrom would be set here
                // when available from the transaction object

                entries.add(new TransactionDocument.TxEntry(content));

            } else if (tx instanceof ScriptTx) {
                ScriptTx scriptTx = (ScriptTx) tx;
                TransactionDocument.ScriptTxContent content = new TransactionDocument.ScriptTxContent();

                // Separate input intentions from regular intentions
                List<TxInputIntent> inputIntentions = new ArrayList<>();
                List<TxValidatorIntent> validatorIntentions = new ArrayList<>();
                List<TxIntent> regularIntentions = new ArrayList<>();

                if (scriptTx.getIntentions() != null) {
                    for (TxIntent intention : scriptTx.getIntentions()) {
                        if (intention instanceof TxInputIntent) {
                            inputIntentions.add((TxInputIntent) intention);
                        } else if (intention instanceof TxValidatorIntent) {
                            validatorIntentions.add((TxValidatorIntent) intention);
                        } else {
                            regularIntentions.add(intention);
                        }
                    }
                }

                // Set inputs and intentions separately
                if (!inputIntentions.isEmpty()) {
                    content.setInputs(inputIntentions);
                }
                if (!regularIntentions.isEmpty()) {
                    content.setIntents(regularIntentions);
                }
                if (!validatorIntentions.isEmpty()) {
                    content.setValidators(validatorIntentions);
                }

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
     * Deserialize YAML string to a complete TxPlan with context.
     * @param yaml the YAML string
     * @return reconstructed TxPlan with all properties restored
     * @throws RuntimeException if deserialization fails
     */
    public static TxPlan from(String yaml) {
        TransactionDocument doc = YamlSerializer.deserialize(yaml, TransactionDocument.class);
        TxPlan plan = new TxPlan();

        // Restore variables
        if (doc.getVariables() != null) {
            plan.setVariables(doc.getVariables());
        }

        // Restore context properties
        if (doc.getContext() != null) {
            TransactionDocument.TxContext context = doc.getContext();
            if (context.getFeePayer() != null) {
                plan.feePayer(context.getFeePayer());
            }
            if (context.getCollateralPayer() != null) {
                plan.collateralPayer(context.getCollateralPayer());
            }
            if (context.getRequiredSigners() != null) {
                plan.setRequiredSigners(context.getRequiredSigners());
            }
            if (context.getValidFromSlot() != null) {
                plan.validFrom(context.getValidFromSlot());
            }
            if (context.getValidToSlot() != null) {
                plan.validTo(context.getValidToSlot());
            }
        }

        // Restore transactions (reuse existing logic)
        List<AbstractTx<?>> transactions = getTxs(doc);
        for (AbstractTx<?> tx : transactions) {
            plan.addTransaction(tx);
        }

        return plan;
    }

    /**
     * Deserialize YAML string to a collection of transactions.
     * @param yaml the YAML string
     * @return list of reconstructed transactions
     * @throws RuntimeException if deserialization fails
     */
    public static List<AbstractTx<?>> getTxs(String yaml) {
        TransactionDocument doc = YamlSerializer.deserialize(yaml, TransactionDocument.class);
        return getTxs(doc);
    }

    /**
     * Internal method to extract transactions from TransactionDocument.
     * @param doc the parsed document
     * @return list of reconstructed transactions
     */
    private static List<AbstractTx<?>> getTxs(TransactionDocument doc) {
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

                // Add input intentions first (if present)
                if (content.getInputs() != null) {
                    for (var inputIntention : content.getInputs()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = inputIntention.resolveVariables(vars);
                        tx.addIntention(resolvedIntention);
                    }
                }

                // Then add regular intentions
                if (content.getIntents() != null) {
                    for (var intention : content.getIntents()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = intention.resolveVariables(vars);
                        tx.addIntention(resolvedIntention);
                    }
                }

                // Backward compatibility: if no inputs section but intentions contain input intentions,
                // still process them (this ensures old YAML files still work)
                if (content.getInputs() == null && content.getIntents() != null) {
                    // Input intentions are already included in the intentions processing above
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
                            var pd = PlutusData.deserialize(HexUtil.decodeHexString(resolvedDatumHex));
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

                // Add input intentions first (if present)
                if (content.getInputs() != null) {
                    for (var inputIntention : content.getInputs()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = inputIntention.resolveVariables(vars);
                        scriptTx.addIntention(resolvedIntention);
                    }
                }

                // Then add regular intentions
                if (content.getIntents() != null) {
                    for (var intention : content.getIntents()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = intention.resolveVariables(vars);
                        scriptTx.addIntention(resolvedIntention);
                    }
                }

                // Validator intentions
                if (content.getValidators() != null) {
                    for (var validatorIntention : content.getValidators()) {
                        // Resolve variables using each intention's own resolveVariables method
                        var resolvedIntention = validatorIntention.resolveVariables(vars);
                        scriptTx.addIntention(resolvedIntention);
                    }
                }

                if (content.getInputs() == null && content.getIntents() != null) {
                    // Input intentions are already included in the intentions processing above
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
     * Create a plan from a single transaction.
     * @param transaction the transaction
     * @return plan with single transaction
     */
    public static TxPlan from(AbstractTx<?> transaction) {
        return new TxPlan().addTransaction(transaction);
    }


    /**
     * Create a plan from multiple transactions.
     * @param transactions the transactions
     * @return plan with all transactions
     */
    public static TxPlan from(List<AbstractTx<?>> transactions) {
        TxPlan doc = new TxPlan();
        transactions.forEach(doc::addTransaction);
        return doc;
    }

    /**
     * Convenience method to directly convert a Tx or ScriptTx to YAML.
     * Note: This produces YAML without any context properties (no fee_payer, collateral_payer, etc).
     * For YAML with context properties, use TxPlan.from(transaction).feePayer(...).toYaml()
     * @param transaction the transaction to serialize (Tx or ScriptTx)
     * @return YAML string representation of the transaction without context
     */
    public static String toYaml(AbstractTx<?> transaction) {
        return from(transaction).toYaml();
    }

    /**
     * Convenience method to directly convert multiple transactions to YAML.
     * Note: This produces YAML without any context properties (no fee_payer, collateral_payer, etc).
     * For YAML with context properties, use TxPlan.from(transactions).feePayer(...).toYaml()
     * @param transactions the list of transactions to serialize
     * @return YAML string representation of the transactions without context
     */
    public static String toYaml(List<AbstractTx<?>> transactions) {
        return from(transactions).toYaml();
    }
}
