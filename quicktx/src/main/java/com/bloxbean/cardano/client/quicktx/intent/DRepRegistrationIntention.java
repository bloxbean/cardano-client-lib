package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
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
                throw new IllegalStateException("Invalid DRep credential hex format: " + drepCredentialHex);
            }
        }

        if (anchorHash != null && !anchorHash.isEmpty() && !anchorHash.startsWith("${")) {
            try {
                HexUtil.decodeHexString(anchorHash);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid anchor hash format: " + anchorHash);
            }
        }
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

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return drepCredential != null || anchor != null;
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
     * Check if anchor information is available.
     */
    @JsonIgnore
    public boolean hasAnchor() {
        return anchor != null ||
               (anchorUrl != null && !anchorUrl.isEmpty());
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for DRep registration");
        }

        return (ctx, outputs) -> {
            BigInteger dep = (deposit != null) ? deposit : ctx.getProtocolParams().getDrepDeposit();
            outputs.add(new TransactionOutput(from, Value.builder().coin(dep).build()));
        };
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
                    cred = Credential.fromKey(HexUtil.decodeHexString(drepCredentialHex));
                }
                if (cred == null)
                    throw new TxBuildException("DRep credential resolution failed");

                Anchor anch = anchor;
                if (anch == null && anchorUrl != null) {
                    byte[] hash = (anchorHash != null && !anchorHash.isEmpty()) ? HexUtil.decodeHexString(anchorHash) : null;
                    anch = new Anchor(anchorUrl, hash);
                }

                BigInteger dep = (deposit != null) ? deposit : ctx.getProtocolParams().getDrepDeposit();

                if (txn.getBody().getCerts() == null) txn.getBody().setCerts(new ArrayList<Certificate>());
                RegDRepCert cert = RegDRepCert.builder()
                        .drepCredential(cred)
                        .anchor(anch)
                        .coin(dep)
                        .build();
                txn.getBody().getCerts().add(cert);

                // Deduct deposit
                String from = ic.getFromAddress();
                txn.getBody().getOutputs().stream()
                        .filter(to -> to.getAddress().equals(from)
                                && to.getValue() != null && to.getValue().getCoin() != null
                                && to.getValue().getCoin().compareTo(dep) >= 0)
                        .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            to.getValue().setCoin(to.getValue().getCoin().subtract(dep));
                            var ma = to.getValue().getMultiAssets();
                            if (to.getValue().getCoin().equals(BigInteger.ZERO) && (ma == null || ma.isEmpty())) {
                                txn.getBody().getOutputs().remove(to);
                            }
                        }, () -> {
                            throw new TxBuildException("Output for from address not found to remove DRep deposit amount: " + from);
                        });
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply DRepRegistrationIntention: " + e.getMessage(), e);
            }
        };
    }
}
