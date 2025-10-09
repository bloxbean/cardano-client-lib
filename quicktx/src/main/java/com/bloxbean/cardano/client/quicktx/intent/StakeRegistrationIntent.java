package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
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
public class StakeRegistrationIntent implements TxIntent {

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
        TxIntent.super.validate();
        if (stakeAddress == null || stakeAddress.isEmpty()) {
            throw new IllegalStateException("Stake address is required for stake registration");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedStakeAddress = VariableResolver.resolve(stakeAddress, variables);

        if (!resolvedStakeAddress.equals(stakeAddress)) {
            return this.toBuilder()
                .stakeAddress(resolvedStakeAddress)
                .build();
        }

        return this;
    }

    /**
     * Create a stake registration intention.
     */
    public static StakeRegistrationIntent register(String stakeAddress) {
        return StakeRegistrationIntent.builder()
            .stakeAddress(stakeAddress)
            .build();
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a dummy output to fromAddress equal to keyDeposit to drive input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for stake registration");
        }

        // Use the deposit helper to create the output builder
        return DepositHelper.createDepositOutputBuilder(from,
            DepositHelper.DepositType.STAKE_KEY_REGISTRATION);
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            // Context-specific check only
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for stake registration");
            }
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                String from = ic.getFromAddress();

                // Build StakeRegistration certificate (stakeAddress already resolved during YAML parsing)
                Address addr = new Address(stakeAddress);
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

                // Use the deposit helper to deduct the deposit
                BigInteger keyDeposit = DepositHelper.getDepositAmount(
                    ctx.getProtocolParams(), DepositHelper.DepositType.STAKE_KEY_REGISTRATION);
                DepositHelper.deductDepositFromOutputs(txn, from, keyDeposit);
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply StakeRegistrationIntention: " + e.getMessage(), e);
            }
        };
    }
}
