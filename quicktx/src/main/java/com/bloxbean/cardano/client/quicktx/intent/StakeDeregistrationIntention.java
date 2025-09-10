package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
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
 * Intention for deregistering a stake address.
 * Captures the stake address to deregister and optional refund address.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StakeDeregistrationIntention implements TxIntention {

    /**
     * Stake address to deregister.
     * Should be a base address or stake address with delegation credential.
     */
    @JsonProperty("stake_address")
    private String stakeAddress;

    /**
     * Optional refund address for the key deposit.
     * If not specified, refund goes to the change address.
     */
    @JsonProperty("refund_address")
    private String refundAddress;

    /**
     * Optional redeemer for script-based deregistration (runtime object).
     */
    @JsonIgnore
    private PlutusData redeemer;

    /**
     * Optional redeemer as CBOR hex for serialization.
     */
    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemer != null) {
            try {
                return redeemer.serializeToHex();
            } catch (Exception e) {
                // ignore and fall through
            }
        }
        return redeemerHex;
    }

    @Override
    public String getType() {
        return "stake_deregistration";
    }

    @Override
    public void validate() {
        TxIntention.super.validate();
        if (stakeAddress == null || stakeAddress.isEmpty()) {
            throw new IllegalStateException("Stake address is required for stake deregistration");
        }
        if (redeemerHex != null && !redeemerHex.isEmpty() && !redeemerHex.startsWith("${")) {
            try { HexUtil.decodeHexString(redeemerHex); } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    // Factory methods for clean API

    /**
     * Create a stake deregistration intention.
     */
    public static StakeDeregistrationIntention deregister(String stakeAddress) {
        return StakeDeregistrationIntention.builder()
            .stakeAddress(stakeAddress)
            .build();
    }

    /**
     * Create a stake deregistration intention with refund address.
     */
    public static StakeDeregistrationIntention deregister(String stakeAddress, String refundAddress) {
        return StakeDeregistrationIntention.builder()
            .stakeAddress(stakeAddress)
            .refundAddress(refundAddress)
            .build();
    }

    // Convenience methods

    /**
     * Set refund address for the key deposit.
     */
    public StakeDeregistrationIntention withRefundAddress(String refundAddress) {
        this.refundAddress = refundAddress;
        return this;
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a dummy output (1 ADA) to fromAddress to trigger input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for stake deregistration");
        }

        // Use helper to create smart dummy output that merges with existing outputs
        return DepositHelper.createDummyOutputBuilder(from, ADAConversionUtil.adaToLovelace(1));
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for stake deregistration");
            }
            String resolvedStake = ic.resolveVariable(stakeAddress);
            if (resolvedStake == null || resolvedStake.isBlank()) {
                throw new TxBuildException("Stake address is required for stake deregistration");
            }
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                String resolvedStake = ic.resolveVariable(stakeAddress);
                String resolvedRefund = refundAddress != null ? ic.resolveVariable(refundAddress) : null;
                String from = ic.getFromAddress();

                // Default refund to from address if not provided
                String targetRefundAddr = (resolvedRefund != null && !resolvedRefund.isBlank()) ? resolvedRefund : from;

                // Build StakeDeregistration certificate
                Address addr = new Address(resolvedStake);
                byte[] delegationHash = addr.getDelegationCredentialHash()
                        .orElseThrow(() -> new TxBuildException("Invalid stake address. No delegation credential"));

                StakeCredential stakeCredential;
                if (addr.isStakeKeyHashInDelegationPart())
                    stakeCredential = StakeCredential.fromKeyHash(delegationHash);
                else if (addr.isScriptHashInDelegationPart())
                    stakeCredential = StakeCredential.fromScriptHash(delegationHash);
                else
                    throw new TxBuildException("Unsupported delegation credential type in address");

                if (txn.getBody().getCerts() == null) {
                    txn.getBody().setCerts(new ArrayList<Certificate>());
                }
                txn.getBody().getCerts().add(new StakeDeregistration(stakeCredential));

                // If redeemer provided, add to witness set with Cert tag
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

                // Use helper to add deposit refund to refund address
                BigInteger keyDeposit = DepositHelper.getDepositAmount(
                    ctx.getProtocolParams(), DepositHelper.DepositType.STAKE_KEY_REGISTRATION);
                DepositHelper.addRefundToOutputs(txn, targetRefundAddr, keyDeposit);
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply StakeDeregistrationIntention: " + e.getMessage(), e);
            }
        };
    }
}
