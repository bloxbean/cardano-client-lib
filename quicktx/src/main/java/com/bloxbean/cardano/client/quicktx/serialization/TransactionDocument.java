package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.intent.TxInputIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxScriptAttachmentIntent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Root document structure for unified transaction YAML format.
 * Supports both single and multiple transactions (tx and scriptTx) in a single document.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDocument {

    @JsonProperty("version")
    private String version = "1.0";

    @JsonProperty("variables")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)  // Don't show if empty
    private Map<String, Object> variables = new HashMap<>();

    @JsonProperty("context")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TxContext context;

    @JsonProperty("transaction")
    private List<TxEntry> transaction = new ArrayList<>();

    /**
     * Entry in the unified transaction list - supports both tx and scriptTx
     */
    @Data
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
     * Regular transaction content with attributes and intents
     */
    @Data
    @NoArgsConstructor
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

        @JsonProperty("intents")
        private List<TxIntent> intents;

        @JsonProperty("scripts")
        private List<TxScriptAttachmentIntent> scripts;

        public void setIntents(List<TxIntent> intents) {
            this.intents = intents;
        }
    }

    /**
     * Script transaction content with attributes and intents
     */
    @Data
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

        @JsonProperty("inputs")
        private List<TxInputIntent> inputs;

        @JsonProperty("intents")
        private List<TxIntent> intents;

        @JsonProperty("scripts")
        private List<TxScriptAttachmentIntent> scripts;
    }

    /**
     * UTXO input reference for serialization
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UtxoInput {
        @JsonProperty("tx_hash")
        private String txHash;

        @JsonProperty("output_index")
        private Integer outputIndex;
    }

    /**
     * Configuration for ScriptTx collectFrom operations
     */
    @Data
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

    }

    /**
     * Configuration for validator script attachments
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidatorAttachment {
        @JsonProperty("type")
        private ValidatorType type;

        @JsonProperty("version")
        private PlutusVersion version;

        @JsonProperty("cbor_hex")
        private String cborHex;
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

    /**
     * Transaction context properties for serialization.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TxContext {
        @JsonProperty("fee_payer")
        private String feePayer;

        @JsonProperty("collateral_payer")
        private String collateralPayer;

        @JsonProperty("required_signers")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Set<String> requiredSigners;

        @JsonProperty("valid_from_slot")
        private Long validFromSlot;

        @JsonProperty("valid_to_slot")
        private Long validToSlot;
    }
}
