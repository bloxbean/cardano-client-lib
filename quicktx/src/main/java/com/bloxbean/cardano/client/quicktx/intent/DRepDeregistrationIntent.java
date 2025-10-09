package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregDRepCert;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
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
 * Intention for DRep deregistration operations.
 * Maps to GovTx.unregisterDRep(Credential, String, BigInteger, PlutusData) operations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DRepDeregistrationIntent implements TxIntent {
    /**
     * DRep credential for deregistration (runtime object).
     */
    @JsonIgnore
    private Credential drepCredential;

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
     * Address to refund the deposit to.
     * If not specified, the from address will be used.
     */
    @JsonProperty("refund_address")
    private String refundAddress;

    /**
     * Refund amount for the deregistration.
     * If not specified, protocol parameter value will be used.
     */
    @JsonProperty("refund_amount")
    private BigInteger refundAmount;

    // Optional redeemer
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

    @Override
    public String getType() {
        return "drep_deregistration";
    }

    @Override
    public void validate() {
        if (drepCredential == null && (drepCredentialHex == null || drepCredentialHex.isEmpty())) {
            throw new IllegalStateException("DRep credential is required for deregistration");
        }

        // Validate hex format if provided (variables already resolved at this point)
        if (drepCredentialHex != null && !drepCredentialHex.isEmpty()) {
            try {
                HexUtil.decodeHexString(drepCredentialHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid drep credential hex format");
            }
        }

        if (drepCredentialType != null && !drepCredentialType.isEmpty()) {
            if (!"key_hash".equals(drepCredentialType) && !"script_hash".equals(drepCredentialType)) {
                throw new IllegalStateException("Invalid DRep credential type: " + drepCredentialType);
            }
        }

        if (refundAmount != null && refundAmount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalStateException("Refund amount cannot be negative");
        }

        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            try {
                HexUtil.decodeHexString(redeemerHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedDrepCredentialHex = VariableResolver.resolve(drepCredentialHex, variables);
        String resolvedDrepCredentialType = VariableResolver.resolve(drepCredentialType, variables);
        String resolvedRefundAddress = VariableResolver.resolve(refundAddress, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedDrepCredentialHex, drepCredentialHex) || !java.util.Objects.equals(resolvedDrepCredentialType, drepCredentialType) || !java.util.Objects.equals(resolvedRefundAddress, refundAddress) || !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)) {
            return this.toBuilder()
                .drepCredentialHex(resolvedDrepCredentialHex)
                .drepCredentialType(resolvedDrepCredentialType)
                .refundAddress(resolvedRefundAddress)
                .redeemerHex(resolvedRedeemerHex)
                .build();
        }

        return this;
    }

    /**
     * Create DRepDeregistrationIntention from runtime Credential.
     */
    public static DRepDeregistrationIntent deregister(Credential drepCredential) {
        return DRepDeregistrationIntent.builder()
            .drepCredential(drepCredential)
            .build();
    }

    /**
     * Create DRepDeregistrationIntention with refund address.
     */
    public static DRepDeregistrationIntent deregister(Credential drepCredential, String refundAddress) {
        return DRepDeregistrationIntent.builder()
            .drepCredential(drepCredential)
            .refundAddress(refundAddress)
            .build();
    }

    /**
     * Create DRepDeregistrationIntention with refund address and amount.
     */
    public static DRepDeregistrationIntent deregister(Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        return DRepDeregistrationIntent.builder()
            .drepCredential(drepCredential)
            .refundAddress(refundAddress)
            .refundAmount(refundAmount)
            .build();
    }

    /**
     * Create DRepDeregistrationIntention from hex strings.
     */
    public static DRepDeregistrationIntent fromHex(String drepCredentialHex, String refundAddress) {
        return DRepDeregistrationIntent.builder()
            .drepCredentialHex(drepCredentialHex)
            .refundAddress(refundAddress)
            .build();
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a small dummy output to trigger input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for DRep deregistration");
        }

        // Use helper to create smart dummy output that merges with existing outputs
        return DepositHelper.createDummyOutputBuilder(from, ADAConversionUtil.adaToLovelace(1));
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            // Context-specific check only
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for DRep deregistration");
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                Credential cred = drepCredential;
                if (cred == null && drepCredentialHex != null && !drepCredentialHex.isEmpty()) {
                    byte[] bytes = HexUtil.decodeHexString(drepCredentialHex);
                    cred = "script_hash".equals(drepCredentialType) ? Credential.fromScript(bytes) : Credential.fromKey(bytes);
                }
                if (cred == null)
                    throw new TxBuildException("DRep credential resolution failed");

                // Get refund amount (same as original deposit amount)
                BigInteger refund = (refundAmount != null) ? refundAmount :
                    DepositHelper.getDepositAmount(ctx.getProtocolParams(), DepositHelper.DepositType.DREP_REGISTRATION);
                String refundAddr = (refundAddress != null && !refundAddress.isBlank()) ? refundAddress : ic.getFromAddress();

                if (txn.getBody().getCerts() == null) txn.getBody().setCerts(new ArrayList<Certificate>());
                UnregDRepCert cert = UnregDRepCert.builder()
                        .drepCredential(cred)
                        .coin(refund)
                        .build();
                txn.getBody().getCerts().add(cert);

                // Use helper to add refund to outputs
                DepositHelper.addRefundToOutputs(txn, refundAddr, refund);

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
                throw new TxBuildException("Failed to apply DRepDeregistrationIntention: " + e.getMessage(), e);
            }
        };
    }
}
