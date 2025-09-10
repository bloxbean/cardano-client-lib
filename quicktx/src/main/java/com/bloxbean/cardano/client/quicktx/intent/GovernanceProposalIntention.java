package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Intention for governance proposal operations.
 * Maps to GovTx.createProposal(GovAction, String, Anchor, PlutusData) operations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GovernanceProposalIntention implements TxIntention {

    // Runtime fields - original objects preserved

    /**
     * Governance action for the proposal (runtime object).
     */
    @JsonIgnore
    private GovAction govAction;

    /**
     * Anchor information for the proposal (runtime object).
     */
    @JsonIgnore
    private Anchor anchor;

    // Serialization fields - computed from runtime objects or set during deserialization

    /**
     * Governance action as CBOR hex for serialization.
     * GovAction objects can be complex and need CBOR serialization.
     */
    @JsonProperty("gov_action_hex")
    private String govActionHex;

    /**
     * Return/reward address (stake address) for deposit refund.
     */
    @JsonProperty("return_address")
    private String returnAddress;

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
     * Deposit amount for the proposal.
     * If not specified, protocol parameter value will be used.
     */
    @JsonProperty("deposit")
    private BigInteger deposit;

    // Optional redeemer for proposing
    @com.fasterxml.jackson.annotation.JsonIgnore
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
     * Get governance action hex for serialization.
     */
    @JsonProperty("gov_action_hex")
    public String getGovActionHex() {
        if (govAction != null) {
            try {
                return HexUtil.encodeHexString(CborSerializationUtil.serialize(govAction.serialize()));
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return govActionHex;
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
        return "governance_proposal";
    }

    @Override
    public void validate() {
        if (govAction == null && (govActionHex == null || govActionHex.isEmpty())) {
            throw new IllegalStateException("Governance action is required for proposal");
        }

        if (returnAddress == null || returnAddress.isEmpty()) {
            throw new IllegalStateException("Return address is required for governance proposal");
        }

        // Validate hex format if provided
        if (govActionHex != null && !govActionHex.isEmpty() && !govActionHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(govActionHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid governance action hex format: " + govActionHex);
            }
        }

        if (anchorHash != null && !anchorHash.isEmpty() && !anchorHash.startsWith("${")) {
            try {
                HexUtil.decodeHexString(anchorHash);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid anchor hash format: " + anchorHash);
            }
        }

        if (deposit != null && deposit.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalStateException("Deposit amount cannot be negative");
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

        String resolvedGovActionHex = VariableResolver.resolve(govActionHex, variables);
        String resolvedReturnAddress = VariableResolver.resolve(returnAddress, variables);
        String resolvedAnchorUrl = VariableResolver.resolve(anchorUrl, variables);
        String resolvedAnchorHash = VariableResolver.resolve(anchorHash, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedGovActionHex, govActionHex) || !java.util.Objects.equals(resolvedReturnAddress, returnAddress) || !java.util.Objects.equals(resolvedAnchorUrl, anchorUrl) || !java.util.Objects.equals(resolvedAnchorHash, anchorHash) || !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)) {
            return this.toBuilder()
                .govActionHex(resolvedGovActionHex)
                .returnAddress(resolvedReturnAddress)
                .anchorUrl(resolvedAnchorUrl)
                .anchorHash(resolvedAnchorHash)
                .redeemerHex(resolvedRedeemerHex)
                .build();
        }

        return this;
    }

    // Factory methods for different use cases

    /**
     * Create GovernanceProposalIntention from runtime objects.
     */
    public static GovernanceProposalIntention create(GovAction govAction, String returnAddress) {
        return GovernanceProposalIntention.builder()
            .govAction(govAction)
            .returnAddress(returnAddress)
            .build();
    }

    /**
     * Create GovernanceProposalIntention with anchor.
     */
    public static GovernanceProposalIntention create(GovAction govAction, String returnAddress, Anchor anchor) {
        return GovernanceProposalIntention.builder()
            .govAction(govAction)
            .returnAddress(returnAddress)
            .anchor(anchor)
            .build();
    }

    /**
     * Create GovernanceProposalIntention from hex strings.
     */
    public static GovernanceProposalIntention fromHex(String govActionHex, String returnAddress, String anchorUrl, String anchorHash) {
        return GovernanceProposalIntention.builder()
            .govActionHex(govActionHex)
            .returnAddress(returnAddress)
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
        return govAction != null || anchor != null;
    }

    /**
     * Check if this intention needs deserialization from stored data.
     */
    @JsonIgnore
    public boolean needsDeserialization() {
        return !hasRuntimeObjects() &&
               (govActionHex != null && !govActionHex.isEmpty());
    }

    /**
     * Check if anchor information is available.
     */
    @JsonIgnore
    public boolean hasAnchor() {
        return anchor != null ||
               (anchorUrl != null && !anchorUrl.isEmpty());
    }

    /**
     * Check if deposit is specified.
     */
    @JsonIgnore
    public boolean hasCustomDeposit() {
        return deposit != null;
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank())
            throw new TxBuildException("From address is required for governance proposal");

        // Use the deposit helper to create the output builder
        return DepositHelper.createDepositOutputBuilder(from,
            DepositHelper.DepositType.GOV_ACTION_PROPOSAL, deposit);
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for governance proposal");
            if (returnAddress == null || returnAddress.isBlank())
                throw new TxBuildException("Return address is required for governance proposal");
            if (govAction == null && (govActionHex == null || govActionHex.isEmpty()))
                throw new TxBuildException("Governance action is required for proposal");
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                GovAction action = govAction;
                if (action == null && govActionHex != null && !govActionHex.isEmpty()) {
                    Array arr = (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(govActionHex));
                    action = GovAction.deserialize(arr);
                }
                if (action == null)
                    throw new TxBuildException("Governance action resolution failed");

                Anchor anch = anchor;
                if (anch == null && anchorUrl != null) {
                    byte[] hash = (anchorHash != null && !anchorHash.isEmpty()) ? HexUtil.decodeHexString(anchorHash) : null;
                    anch = new Anchor(anchorUrl, hash);
                }

                BigInteger dep = (deposit != null) ? deposit :
                    DepositHelper.getDepositAmount(ctx.getProtocolParams(), DepositHelper.DepositType.GOV_ACTION_PROPOSAL);

                if (txn.getBody().getProposalProcedures() == null)
                    txn.getBody().setProposalProcedures(new UniqueList<>());
                txn.getBody().getProposalProcedures().add(ProposalProcedure.builder()
                        .govAction(action)
                        .deposit(dep)
                        .rewardAccount(returnAddress)
                        .anchor(anch)
                        .build());

                // Use the deposit helper to deduct the deposit
                DepositHelper.deductDepositFromOutputs(txn, ic.getFromAddress(), dep);

                // Add proposing redeemer if provided
                PlutusData rdData = redeemer;
                if (rdData == null && redeemerHex != null && !redeemerHex.isEmpty()) {
                    rdData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
                }
                if (rdData != null) {
                    if (txn.getWitnessSet() == null)
                        txn.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());
                    int idx = txn.getBody().getProposalProcedures().size() - 1;
                    Redeemer rd = Redeemer.builder()
                            .tag(RedeemerTag.Proposing)
                            .data(rdData)
                            .index(java.math.BigInteger.valueOf(idx))
                            .exUnits(ExUnits.builder()
                                    .mem(java.math.BigInteger.valueOf(10000))
                                    .steps(java.math.BigInteger.valueOf(1000))
                                    .build())
                            .build();
                    txn.getWitnessSet().getRedeemers().add(rd);
                }
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply GovernanceProposalIntention: " + e.getMessage(), e);
            }
        };
    }
}
