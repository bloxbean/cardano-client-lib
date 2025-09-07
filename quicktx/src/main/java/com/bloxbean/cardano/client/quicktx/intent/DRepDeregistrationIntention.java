package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregDRepCert;
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
public class DRepDeregistrationIntention implements TxIntention {

    // Runtime fields - original objects preserved

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

    @Override
    public String getType() {
        return "drep_deregistration";
    }

    @Override
    public void validate() {
        if (drepCredential == null && (drepCredentialHex == null || drepCredentialHex.isEmpty())) {
            throw new IllegalStateException("DRep credential is required for deregistration");
        }

        // Validate hex format if provided
        if (drepCredentialHex != null && !drepCredentialHex.isEmpty() && !drepCredentialHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(drepCredentialHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid DRep credential hex format: " + drepCredentialHex);
            }
        }

        if (refundAmount != null && refundAmount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalStateException("Refund amount cannot be negative");
        }
    }

    // Factory methods for different use cases

    /**
     * Create DRepDeregistrationIntention from runtime Credential.
     */
    public static DRepDeregistrationIntention deregister(Credential drepCredential) {
        return DRepDeregistrationIntention.builder()
            .drepCredential(drepCredential)
            .build();
    }

    /**
     * Create DRepDeregistrationIntention with refund address.
     */
    public static DRepDeregistrationIntention deregister(Credential drepCredential, String refundAddress) {
        return DRepDeregistrationIntention.builder()
            .drepCredential(drepCredential)
            .refundAddress(refundAddress)
            .build();
    }

    /**
     * Create DRepDeregistrationIntention with refund address and amount.
     */
    public static DRepDeregistrationIntention deregister(Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        return DRepDeregistrationIntention.builder()
            .drepCredential(drepCredential)
            .refundAddress(refundAddress)
            .refundAmount(refundAmount)
            .build();
    }

    /**
     * Create DRepDeregistrationIntention from hex strings.
     */
    public static DRepDeregistrationIntention fromHex(String drepCredentialHex, String refundAddress) {
        return DRepDeregistrationIntention.builder()
            .drepCredentialHex(drepCredentialHex)
            .refundAddress(refundAddress)
            .build();
    }

    // Utility methods

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return drepCredential != null;
    }

    /**
     * Check if this intention needs deserialization from stored data.
     */
    @JsonIgnore
    public boolean needsDeserialization() {
        return !hasRuntimeObjects() &&
               (drepCredentialHex != null && !drepCredentialHex.isEmpty());
    }

    /**
     * Check if refund address is specified.
     */
    @JsonIgnore
    public boolean hasRefundAddress() {
        return refundAddress != null && !refundAddress.isEmpty();
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a small dummy output to trigger input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for DRep deregistration");
        }

        return (ctx, outputs) -> {
            outputs.add(new TransactionOutput(from, Value.builder().coin(ADAConversionUtil.adaToLovelace(1)).build()));
        };
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for DRep deregistration");
            if (drepCredential == null && (drepCredentialHex == null || drepCredentialHex.isEmpty()))
                throw new TxBuildException("DRep credential is required for deregistration");
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                Credential cred = drepCredential;
                if (cred == null && drepCredentialHex != null && !drepCredentialHex.isEmpty()) {
                    cred = Credential.fromKey(HexUtil.decodeHexString(drepCredentialHex));
                }
                if (cred == null)
                    throw new TxBuildException("DRep credential resolution failed");

                BigInteger refund = (refundAmount != null) ? refundAmount : ctx.getProtocolParams().getDrepDeposit();
                String refundAddr = (refundAddress != null && !refundAddress.isBlank()) ? refundAddress : ic.getFromAddress();

                if (txn.getBody().getCerts() == null) txn.getBody().setCerts(new ArrayList<Certificate>());
                UnregDRepCert cert = UnregDRepCert.builder()
                        .drepCredential(cred)
                        .coin(refund)
                        .build();
                txn.getBody().getCerts().add(cert);

                // Add refund to refund address output
                txn.getBody().getOutputs().stream()
                        .filter(to -> to.getAddress().equals(refundAddr))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            to.getValue().setCoin(to.getValue().getCoin().add(refund));
                        }, () -> {
                            txn.getBody().getOutputs().add(new TransactionOutput(refundAddr,
                                    Value.builder().coin(refund).build()));
                        });
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply DRepDeregistrationIntention: " + e.getMessage(), e);
            }
        };
    }
}
