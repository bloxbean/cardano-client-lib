package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Intention for DRep registration operations.
 * Maps to GovTx.registerDRep(Credential, Anchor, PlutusData) operations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DRepRegistrationIntention implements TxIntention {

    // Runtime fields - original objects preserved

    /**
     * DRep credential for registration (runtime object).
     */
    @JsonIgnore
    private Credential drepCredential;

    /**
     * Anchor information for the DRep (runtime object).
     */
    @JsonIgnore
    private Anchor anchor;

    // Serialization fields - computed from runtime objects or set during deserialization

    /**
     * DRep credential as hex string for serialization.
     */
    @JsonProperty("drep_credential_hex")
    private String drepCredentialHex;

    /**
     * DRep credential type for serialization: key_hash or script_hash
     */
    @JsonProperty("drep_credential_type")
    private String drepCredentialType;

    /**
     * Anchor URL for serialization.
     */
    @JsonProperty("anchor_url")
    private String anchorUrl;

    /**
     * Anchor hash as hex for serialization.
     */
    @JsonProperty("anchor_hash")
    private String anchorHash;

    /**
     * Deposit amount for the registration.
     * If not specified, protocol parameter value will be used.
     */
    @JsonProperty("deposit")
    private BigInteger deposit;

    // Optional redeemer for script-based registration
    @JsonIgnore
    private PlutusData redeemer;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemer != null) {
            try { return redeemer.serializeToHex(); } catch (Exception e) { /* ignore */ }
        }
        return redeemerHex;
    }

    /**
     * Get DRep credential hex for serialization.
     */
    @JsonProperty("drep_credential_hex")
    public String getDrepCredentialHex() {
        if (drepCredential != null) {
            try {
                return HexUtil.encodeHexString(drepCredential.getBytes());
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return drepCredentialHex;
    }

    /**
     * Get DRep credential type for serialization.
     */
    @JsonProperty("drep_credential_type")
    public String getDrepCredentialType() {
        if (drepCredential != null) {
            return drepCredential.getType() == com.bloxbean.cardano.client.address.CredentialType.Key ? "key_hash" : "script_hash";
        }
        return drepCredentialType;
    }

    /**
     * Get anchor URL for serialization.
     */
    @JsonProperty("anchor_url")
    public String getAnchorUrl() {
        if (anchor != null) {
            return anchor.getAnchorUrl();
        }
        return anchorUrl;
    }

    /**
     * Get anchor hash for serialization.
     */
    @JsonProperty("anchor_hash")
    public String getAnchorHash() {
        if (anchor != null && anchor.getAnchorDataHash() != null) {
            return HexUtil.encodeHexString(anchor.getAnchorDataHash());
        }
        return anchorHash;
    }

    @Override
    public String getType() {
        return "drep_registration";
    }

    @Override
    public void validate() {
        if (drepCredential == null && (drepCredentialHex == null || drepCredentialHex.isEmpty())) {
            throw new IllegalStateException("DRep credential is required for registration");
        }

        // Validate hex format if provided
        if (drepCredentialHex != null && !drepCredentialHex.isEmpty() && !drepCredentialHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(drepCredentialHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid drep credential hex format");
            }
        }

        if (drepCredentialType != null && !drepCredentialType.isEmpty() && !drepCredentialType.startsWith("${")) {
            if (!"key_hash".equals(drepCredentialType) && !"script_hash".equals(drepCredentialType)) {
                throw new IllegalStateException("Invalid DRep credential type: " + drepCredentialType);
            }
        }

        if (anchorHash != null && !anchorHash.isEmpty() && !anchorHash.startsWith("${")) {
            try {
                HexUtil.decodeHexString(anchorHash);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid anchor hash format: " + anchorHash);
            }
        }

        if (redeemerHex != null && !redeemerHex.isEmpty() && !redeemerHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(redeemerHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    @Override
    public TxIntention resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedDrepCredentialHex = VariableResolver.resolve(drepCredentialHex, variables);
        String resolvedDrepCredentialType = VariableResolver.resolve(drepCredentialType, variables);
        String resolvedAnchorUrl = VariableResolver.resolve(anchorUrl, variables);
        String resolvedAnchorHash = VariableResolver.resolve(anchorHash, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);
        
        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedDrepCredentialHex, drepCredentialHex) || !java.util.Objects.equals(resolvedDrepCredentialType, drepCredentialType) || !java.util.Objects.equals(resolvedAnchorUrl, anchorUrl) || !java.util.Objects.equals(resolvedAnchorHash, anchorHash) || !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)) {
            return this.toBuilder()
                .drepCredentialHex(resolvedDrepCredentialHex)
                .drepCredentialType(resolvedDrepCredentialType)
                .anchorUrl(resolvedAnchorUrl)
                .anchorHash(resolvedAnchorHash)
                .redeemerHex(resolvedRedeemerHex)
                .build();
        }
        
        return this;
    }

    // Factory methods for different use cases

    /**
     * Create DRepRegistrationIntention from runtime Credential.
     */
    public static DRepRegistrationIntention register(Credential drepCredential) {
        return DRepRegistrationIntention.builder()
            .drepCredential(drepCredential)
            .build();
    }

    /**
     * Create DRepRegistrationIntention from runtime Credential and Anchor.
     */
    public static DRepRegistrationIntention register(Credential drepCredential, Anchor anchor) {
        return DRepRegistrationIntention.builder()
            .drepCredential(drepCredential)
            .anchor(anchor)
            .build();
    }

    /**
     * Create DRepRegistrationIntention from hex strings.
     */
    public static DRepRegistrationIntention fromHex(String drepCredentialHex, String anchorUrl, String anchorHash) {
        return DRepRegistrationIntention.builder()
            .drepCredentialHex(drepCredentialHex)
            .anchorUrl(anchorUrl)
            .anchorHash(anchorHash)
            .build();
    }

    // Utility methods


    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for DRep registration");
        }

        // Use the deposit helper to create the output builder
        //deposit here is a custom deposit amount if provided, otherwise,
        //it will be fetched from protocol param in Deposit helper.
        return DepositHelper.createDepositOutputBuilder(from, 
            DepositHelper.DepositType.DREP_REGISTRATION, deposit);
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for DRep registration");
            if (drepCredential == null && (drepCredentialHex == null || drepCredentialHex.isEmpty()))
                throw new TxBuildException("DRep credential is required for registration");
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                Credential cred = drepCredential;
                if (cred == null && drepCredentialHex != null && !drepCredentialHex.isEmpty()) {
                    byte[] bytes = HexUtil.decodeHexString(drepCredentialHex);
                    if ("script_hash".equals(drepCredentialType))
                        cred = Credential.fromScript(bytes);
                    else
                        cred = Credential.fromKey(bytes);
                }
                if (cred == null)
                    throw new TxBuildException("DRep credential resolution failed");

                Anchor anch = anchor;
                if (anch == null && anchorUrl != null) {
                    byte[] hash = (anchorHash != null && !anchorHash.isEmpty()) ? HexUtil.decodeHexString(anchorHash) : null;
                    anch = new Anchor(anchorUrl, hash);
                }

                BigInteger dep = (deposit != null) ? deposit : DepositHelper.getDepositAmount(
                    ctx.getProtocolParams(), DepositHelper.DepositType.DREP_REGISTRATION);

                if (txn.getBody().getCerts() == null) txn.getBody().setCerts(new ArrayList<Certificate>());
                RegDRepCert cert = RegDRepCert.builder()
                        .drepCredential(cred)
                        .anchor(anch)
                        .coin(dep)
                        .build();
                txn.getBody().getCerts().add(cert);

                // Use the deposit helper to deduct the deposit
                DepositHelper.deductDepositFromOutputs(txn, ic.getFromAddress(), dep);

                // Add cert redeemer if provided
                PlutusData rdData = redeemer;
                if (rdData == null && redeemerHex != null && !redeemerHex.isEmpty()) {
                    rdData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
                }
                if (rdData != null) {
                    if (txn.getWitnessSet() == null)
                        txn.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());
                    int certIndex = txn.getBody().getCerts().size() - 1;
                    Redeemer rd = Redeemer.builder()
                            .tag(RedeemerTag.Cert)
                            .data(rdData)
                            .index(java.math.BigInteger.valueOf(certIndex))
                            .exUnits(ExUnits.builder()
                                    .mem(java.math.BigInteger.valueOf(10000))
                                    .steps(java.math.BigInteger.valueOf(1000))
                                    .build())
                            .build();
                    txn.getWitnessSet().getRedeemers().add(rd);
                }
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply DRepRegistrationIntention: " + e.getMessage(), e);
            }
        };
    }

}
