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
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.StakePoolId;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Intention for delegating a stake address to a stake pool.
 * Captures the stake address and the pool ID for delegation.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StakeDelegationIntention implements TxIntention {

    /**
     * Stake address to delegate.
     * Should be a base address or stake address with delegation credential.
     */
    @JsonProperty("stake_address")
    private String stakeAddress;

    /**
     * Stake pool ID to delegate to.
     * Can be Bech32 encoded (pool1...) or hex encoded.
     */
    @JsonProperty("pool_id")
    private String poolId;

    // Optional redeemer support for script-based delegation
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PlutusData redeemer;

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
        return "stake_delegation";
    }

    @Override
    public void validate() {
        TxIntention.super.validate();
        if (stakeAddress == null || stakeAddress.isEmpty()) {
            throw new IllegalStateException("Stake address is required for stake delegation");
        }
        if (poolId == null || poolId.isEmpty()) {
            throw new IllegalStateException("Pool ID is required for stake delegation");
        }
        if (redeemerHex != null && !redeemerHex.isEmpty() && !redeemerHex.startsWith("${")) {
            try { HexUtil.decodeHexString(redeemerHex); } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    // Factory methods for clean API

    /**
     * Create a stake delegation intention.
     */
    public static StakeDelegationIntention delegateTo(String stakeAddress, String poolId) {
        return StakeDelegationIntention.builder()
            .stakeAddress(stakeAddress)
            .poolId(poolId)
            .build();
    }

    // Convenience methods

    /**
     * Set the pool ID for delegation.
     */
    public StakeDelegationIntention toPool(String poolId) {
        this.poolId = poolId;
        return this;
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a dummy output (1 ADA) to fromAddress to trigger input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for stake delegation");
        }

        return (ctx, outputs) -> {
            outputs.add(new TransactionOutput(from, Value.builder().coin(ADAConversionUtil.adaToLovelace(1)).build()));
        };
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for stake delegation");
            }
            String resolvedStake = ic.resolveVariable(stakeAddress);
            String resolvedPool = ic.resolveVariable(poolId);
            if (resolvedStake == null || resolvedStake.isBlank()) {
                throw new TxBuildException("Stake address is required for stake delegation");
            }
            if (resolvedPool == null || resolvedPool.isBlank()) {
                throw new TxBuildException("Pool id is required for stake delegation");
            }
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            String resolvedStake = ic.resolveVariable(stakeAddress);
            String resolvedPool = ic.resolveVariable(poolId);

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

            StakePoolId stakePoolId = resolvedPool.startsWith("pool")
                    ? StakePoolId.fromBech32PoolId(resolvedPool)
                    : StakePoolId.fromHexPoolId(resolvedPool);

            if (txn.getBody().getCerts() == null) {
                txn.getBody().setCerts(new ArrayList<Certificate>());
            }
            txn.getBody().getCerts().add(new StakeDelegation(stakeCredential, stakePoolId));

            // Add redeemer if provided
            PlutusData rdData = redeemer;
            if (rdData == null && redeemerHex != null && !redeemerHex.isEmpty()) {
                try { rdData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex)); } catch (Exception e) {
                    throw new TxBuildException("Failed to deserialize redeemer hex", e);
                }
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
        };
    }
}
