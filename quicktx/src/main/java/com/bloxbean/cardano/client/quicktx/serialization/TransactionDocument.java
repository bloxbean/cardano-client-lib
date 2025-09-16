package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.intent.TxInputIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxValidatorIntent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root document structure for unified transaction YAML format.
 * Supports both single and multiple transactions (tx and scriptTx) in a single document.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDocument {

    @JsonProperty("version")
    private String version = "1.0";

    @JsonProperty("variables")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)  // Don't show if empty
    private Map<String, Object> variables = new HashMap<>();

    @JsonProperty("transaction")
    private List<TxEntry> transaction = new ArrayList<>();

    public TransactionDocument() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public List<TxEntry> getTransaction() {
        return transaction;
    }

    public void setTransaction(List<TxEntry> transaction) {
        this.transaction = transaction;
    }

    /**
     * Entry in the unified transaction list - supports both tx and scriptTx
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxEntry {
        @JsonProperty("tx")
        private TxContent tx;

        @JsonProperty("scriptTx")
        private ScriptTxContent scriptTx;

        public TxEntry() {
        }

        public TxEntry(TxContent tx) {
            this.tx = tx;
        }

        public TxEntry(ScriptTxContent scriptTx) {
            this.scriptTx = scriptTx;
        }

        public TxContent getTx() {
            return tx;
        }

        public void setTx(TxContent tx) {
            this.tx = tx;
        }

        public ScriptTxContent getScriptTx() {
            return scriptTx;
        }

        public void setScriptTx(ScriptTxContent scriptTx) {
            this.scriptTx = scriptTx;
        }

        /**
         * Check if this entry contains a regular transaction
         */
        public boolean isTx() {
            return tx != null;
        }

        /**
         * Check if this entry contains a script transaction
         */
        public boolean isScriptTx() {
            return scriptTx != null;
        }
    }

    /**
     * Regular transaction content with attributes and intentions
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxContent {
        @JsonProperty("from")
        private String from;

        @JsonProperty("from_wallet")
        private String fromWallet;

        @JsonProperty("change_address")
        private String changeAddress;

        @JsonProperty("collect_from")
        private List<UtxoInput> collectFrom;

        @JsonProperty("inputs")
        private List<TxInputIntent> inputs;

        @JsonProperty("intentions")
        private List<TxIntent> intentions;

        public TxContent() {
        }

        public TxContent(List<TxIntent> intentions) {
            this.intentions = intentions;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getFromWallet() {
            return fromWallet;
        }

        public void setFromWallet(String fromWallet) {
            this.fromWallet = fromWallet;
        }

        public String getChangeAddress() {
            return changeAddress;
        }

        public void setChangeAddress(String changeAddress) {
            this.changeAddress = changeAddress;
        }

        public List<UtxoInput> getCollectFrom() {
            return collectFrom;
        }

        public void setCollectFrom(List<UtxoInput> collectFrom) {
            this.collectFrom = collectFrom;
        }

        public List<TxInputIntent> getInputs() {
            return inputs;
        }

        public void setInputs(List<TxInputIntent> inputs) {
            this.inputs = inputs;
        }

        public List<TxIntent> getIntentions() {
            return intentions;
        }

        public void setIntentions(List<TxIntent> intentions) {
            this.intentions = intentions;
        }
    }

    /**
     * Script transaction content with attributes and intentions
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScriptTxContent {
        @JsonProperty("change_address")
        private String changeAddress;

        @JsonProperty("change_datum")
        private String changeDatum;

        @JsonProperty("change_datum_hash")
        private String changeDatumHash;

        @JsonProperty("collect_from")
        private List<CollectFromConfiguration> collectFrom;

        @JsonProperty("scripts")
        private List<ValidatorAttachment> scripts;

        @JsonProperty("inputs")
        private List<TxInputIntent> inputs;

        @JsonProperty("intentions")
        private List<TxIntent> intentions;

        @JsonProperty("validators")
        private List<TxValidatorIntent> validators;

        public ScriptTxContent() {
        }

        public ScriptTxContent(List<TxIntent> intentions) {
            this.intentions = intentions;
        }

        public String getChangeAddress() {
            return changeAddress;
        }

        public void setChangeAddress(String changeAddress) {
            this.changeAddress = changeAddress;
        }

        public String getChangeDatum() {
            return changeDatum;
        }

        public void setChangeDatum(String changeDatum) {
            this.changeDatum = changeDatum;
        }

        public String getChangeDatumHash() {
            return changeDatumHash;
        }

        public void setChangeDatumHash(String changeDatumHash) {
            this.changeDatumHash = changeDatumHash;
        }

        public List<CollectFromConfiguration> getCollectFrom() {
            return collectFrom;
        }

        public void setCollectFrom(List<CollectFromConfiguration> collectFrom) {
            this.collectFrom = collectFrom;
        }

        public List<ValidatorAttachment> getScripts() {
            return scripts;
        }

        public void setScripts(List<ValidatorAttachment> scripts) {
            this.scripts = scripts;
        }

        public List<TxInputIntent> getInputs() {
            return inputs;
        }

        public void setInputs(List<TxInputIntent> inputs) {
            this.inputs = inputs;
        }

        public List<TxIntent> getIntentions() {
            return intentions;
        }

        public void setIntentions(List<TxIntent> intentions) {
            this.intentions = intentions;
        }

        public List<TxValidatorIntent> getValidators() {
            return validators;
        }

        public void setValidators(List<TxValidatorIntent> validators) {
            this.validators = validators;
        }
    }

    /**
     * UTXO input reference for serialization
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UtxoInput {
        @JsonProperty("tx_hash")
        private String txHash;

        @JsonProperty("output_index")
        private Integer outputIndex;

        public UtxoInput() {
        }

        public UtxoInput(String txHash, Integer outputIndex) {
            this.txHash = txHash;
            this.outputIndex = outputIndex;
        }

        public String getTxHash() {
            return txHash;
        }

        public void setTxHash(String txHash) {
            this.txHash = txHash;
        }

        public Integer getOutputIndex() {
            return outputIndex;
        }

        public void setOutputIndex(Integer outputIndex) {
            this.outputIndex = outputIndex;
        }
    }

    /**
     * Configuration for ScriptTx collectFrom operations
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CollectFromConfiguration {
        @JsonProperty("tx_hash")
        private String txHash;

        @JsonProperty("output_index")
        private Integer outputIndex;

        @JsonProperty("address")
        private String address;

        @JsonProperty("amounts")
        private List<Amount> amounts;

        @JsonProperty("datum_hex")
        private String datumHex;

        @JsonProperty("redeemer_hex")
        private String redeemerHex;

        @JsonProperty("datum_hash")
        private String datumHash;

        @JsonProperty("inline_datum_hex")
        private String inlineDatumHex;

        @JsonProperty("predicate")
        private String predicate;

        public CollectFromConfiguration() {
        }

        public CollectFromConfiguration(String txHash, Integer outputIndex) {
            this.txHash = txHash;
            this.outputIndex = outputIndex;
        }

        public String getTxHash() {
            return txHash;
        }

        public void setTxHash(String txHash) {
            this.txHash = txHash;
        }

        public Integer getOutputIndex() {
            return outputIndex;
        }

        public void setOutputIndex(Integer outputIndex) {
            this.outputIndex = outputIndex;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public List<Amount> getAmounts() {
            return amounts;
        }

        public void setAmounts(List<Amount> amounts) {
            this.amounts = amounts;
        }

        public String getDatumHex() {
            return datumHex;
        }

        public void setDatumHex(String datumHex) {
            this.datumHex = datumHex;
        }

        public String getRedeemerHex() {
            return redeemerHex;
        }

        public void setRedeemerHex(String redeemerHex) {
            this.redeemerHex = redeemerHex;
        }

        public String getDatumHash() {
            return datumHash;
        }

        public void setDatumHash(String datumHash) {
            this.datumHash = datumHash;
        }

        public String getInlineDatumHex() {
            return inlineDatumHex;
        }

        public void setInlineDatumHex(String inlineDatumHex) {
            this.inlineDatumHex = inlineDatumHex;
        }

        public String getPredicate() {
            return predicate;
        }

        public void setPredicate(String predicate) {
            this.predicate = predicate;
        }
    }

    /**
     * Configuration for validator script attachments
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidatorAttachment {
        @JsonProperty("type")
        private ValidatorType type;

        @JsonProperty("version")
        private PlutusVersion version;

        @JsonProperty("cbor_hex")
        private String cborHex;

        public ValidatorAttachment() {
        }

        public ValidatorAttachment(ValidatorType type, PlutusVersion version, String cborHex) {
            this.type = type;
            this.version = version;
            this.cborHex = cborHex;
        }

        public ValidatorType getType() {
            return type;
        }

        public void setType(ValidatorType type) {
            this.type = type;
        }

        public PlutusVersion getVersion() {
            return version;
        }

        public void setVersion(PlutusVersion version) {
            this.version = version;
        }

        public String getCborHex() {
            return cborHex;
        }

        public void setCborHex(String cborHex) {
            this.cborHex = cborHex;
        }
    }

    /**
     * Validator types mapping to ScriptTx attach methods
     */
    public enum ValidatorType {
        @JsonProperty("spend")
        SPEND,

        @JsonProperty("mint")
        MINT,

        @JsonProperty("cert")
        CERT,

        @JsonProperty("reward")
        REWARD,

        @JsonProperty("voting")
        VOTING,

        @JsonProperty("proposing")
        PROPOSING
    }

    /**
     * Plutus script versions
     */
    public enum PlutusVersion {
        @JsonProperty("v1")
        V1,

        @JsonProperty("v2")
        V2,

        @JsonProperty("v3")
        V3
    }
}
