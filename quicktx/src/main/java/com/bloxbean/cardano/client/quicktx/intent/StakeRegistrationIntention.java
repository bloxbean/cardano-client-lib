package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Intention for registering a stake address.
 * Captures the stake address that needs to be registered.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StakeRegistrationIntention implements TxIntention {

    /**
     * Stake address to register.
     * Should be a base address or stake address with delegation credential.
     */
    @JsonProperty("stake_address")
    private String stakeAddress;

    @Override
    public String getType() {
        return "stake_registration";
    }


    @Override
    public void validate() {
        TxIntention.super.validate();
        if (stakeAddress == null || stakeAddress.isEmpty()) {
            throw new IllegalStateException("Stake address is required for stake registration");
        }
    }

    // Factory methods for clean API

    /**
     * Create a stake registration intention.
     */
    public static StakeRegistrationIntention register(String stakeAddress) {
        return StakeRegistrationIntention.builder()
            .stakeAddress(stakeAddress)
            .build();
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a dummy output to fromAddress equal to keyDeposit to drive input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for stake registration");
        }

        return (ctx, outputs) -> {
            try {
                String keyDepositStr = ctx.getProtocolParams().getKeyDeposit();
                if (keyDepositStr == null || keyDepositStr.isEmpty()) {
                    throw new TxBuildException("Protocol parameter keyDeposit not available");
                }
                BigInteger keyDeposit = new BigInteger(keyDepositStr);
                outputs.add(new TransactionOutput(from, Value.builder().coin(keyDeposit).build()));
            } catch (Exception e) {
                throw new TxBuildException("Failed to add deposit output for stake registration: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            // Validate presence
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for stake registration");
            }
            String resolvedStake = ic.resolveVariable(stakeAddress);
            if (resolvedStake == null || resolvedStake.isBlank()) {
                throw new TxBuildException("Stake address is required for stake registration");
            }
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                String resolvedStake = ic.resolveVariable(stakeAddress);
                String from = ic.getFromAddress();

                // Build StakeRegistration certificate
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

                // Add certificate
                if (txn.getBody().getCerts() == null) {
                    txn.getBody().setCerts(new ArrayList<Certificate>());
                }
                txn.getBody().getCerts().add(new StakeRegistration(stakeCredential));

                // Deduct deposit from the output to fromAddress
                BigInteger keyDeposit = new BigInteger(ctx.getProtocolParams().getKeyDeposit());
                txn.getBody().getOutputs().stream()
                        .filter(to -> to.getAddress().equals(from) && to.getValue() != null
                                && to.getValue().getCoin() != null
                                && to.getValue().getCoin().compareTo(keyDeposit) >= 0)
                        .sorted(Comparator.comparing(o -> o.getValue().getCoin(), Comparator.reverseOrder()))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            to.getValue().setCoin(to.getValue().getCoin().subtract(keyDeposit));
                            var ma = to.getValue().getMultiAssets();
                            if (to.getValue().getCoin().equals(BigInteger.ZERO) && (ma == null || ma.isEmpty())) {
                                txn.getBody().getOutputs().remove(to);
                            }
                        }, () -> {
                            throw new TxBuildException("Output for from address not found to remove deposit: " + from);
                        });
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply StakeRegistrationIntention: " + e.getMessage(), e);
            }
        };
    }
}
