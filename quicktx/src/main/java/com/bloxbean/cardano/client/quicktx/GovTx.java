package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GovTx {
    protected List<DRepRegestrationContext> dRepRegistrationContexts;
    protected List<DRepDeregestrationContext> dRepDeregestrationContexts;
    protected List<UpdateDRepContext> updateDRepContexts;
    protected List<CreateProposalContext> createProposalContexts;
    protected List<VotingProcedureContext> votingProcedureContexts;
    protected List<VotingDelegationContext> votingDelegationContexts;

    public GovTx() {

    }

    /**
     * Register DRep
     * @param drepCredential DRep credential
     * @param anchor Anchor
     * @param redeemer Redeemer
     * @return GovTx
     */
    public GovTx registerDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        var regDRepCert = RegDRepCert.builder()
                .drepCredential(drepCredential)
                .anchor(anchor)
                .coin(null)
                .build();

        if (dRepRegistrationContexts == null)
            dRepRegistrationContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Cert)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        var drepRegistrationContext = new DRepRegestrationContext(regDRepCert, _redeemer);
        dRepRegistrationContexts.add(drepRegistrationContext);
        return this;
    }

    /**
     * Unregister DRep
     * @param drepCredential DRep credential
     * @param refundAddress Refund address
     * @param refundAmount Refund amount
     * @param redeemer Redeemer
     * @return GovTx
     */
    public GovTx unregisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount, PlutusData redeemer) {
        var unregDRepCert = UnregDRepCert.builder()
                .drepCredential(drepCredential)
                .coin(refundAmount)
                .build();

        if (dRepDeregestrationContexts == null)
            dRepDeregestrationContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Cert)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        dRepDeregestrationContexts.add(new DRepDeregestrationContext(unregDRepCert, refundAddress, _redeemer));
        return this;
    }

    /**
     * Update DRep
     * @param drepCredential DRep credential
     * @param anchor Anchor
     * @param redeemer Redeemer
     * @return GovTx
     */
    public GovTx updateDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        var updateDRepCert = UpdateDRepCert.builder()
                .drepCredential(drepCredential)
                .anchor(anchor)
                .build();

        if (updateDRepContexts == null)
            updateDRepContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Cert)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        updateDRepContexts.add(new UpdateDRepContext(updateDRepCert, _redeemer));
        return this;
    }

    /**
     * Create Gov Action Proposal
     * @param govAction Gov Action
     * @param returnAddress Refund stake address
     * @param anchor Anchor
     * @param redeemer Redeemer
     * @return GovTx
     */
    public GovTx createProposal(@NonNull GovAction govAction, @NonNull String returnAddress, Anchor anchor, PlutusData redeemer) {
        //TODO -- Add a check for only allowed GovAction types with policy
        if (createProposalContexts == null)
            createProposalContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Proposing)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        //TODO -- remove custom gov action deposit later
        var createProposalContext = new CreateProposalContext(null, returnAddress, govAction, anchor, _redeemer);

        createProposalContexts.add(createProposalContext);

        return this;
    }

    /**
     * Create a voting procedure
     * @param voter Voter
     * @param govActionId GovActionId
     * @param vote Vote
     * @param anchor Anchor
     * @param redeemer Redeemer
     * @return GovTx
     */
    public GovTx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor, PlutusData redeemer) {
        if (votingProcedureContexts == null)
            votingProcedureContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Voting)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        votingProcedureContexts.add(new VotingProcedureContext(voter, govActionId, new VotingProcedure(vote, anchor), _redeemer));
        return this;
    }

    /**
     * Delegate Votes to DRep
     * @param address Address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param drep DRep to delegate
     * @param redeemer Redeemer
     * @return GovTx
     */
    public GovTx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep, PlutusData redeemer) {
        byte[] delegationHash = address.getDelegationCredentialHash()
                .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));

        StakeCredential stakeCredential = null;
        if (address.isStakeKeyHashInDelegationPart())
            stakeCredential = StakeCredential.fromKeyHash(delegationHash);
        else if (address.isScriptHashInDelegationPart())
            stakeCredential = StakeCredential.fromScriptHash(delegationHash);

        var voteDelegation = VoteDelegCert.builder()
                .stakeCredential(stakeCredential)
                .drep(drep)
                .build();

        if (votingDelegationContexts == null)
            votingDelegationContexts = new ArrayList<>();

        Redeemer _redeemer = null;
        if (redeemer != null) {
            _redeemer = Redeemer.builder()
                    .tag(RedeemerTag.Cert)
                    .data(redeemer)
                    .index(BigInteger.valueOf(1)) //dummy value
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000)) // Some dummy value
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
        }

        votingDelegationContexts.add(new VotingDelegationContext(voteDelegation, _redeemer));
        return this;
    }

    /**
     * Return TxBuilder, payments to build a drep transaction
     *
     * @param fromAddress
     * @return Tuple<List<PaymentContext>, TxBuilder>
     */
    Tuple<List<DepositRefundContext>, TxBuilder> build(String fromAddress, String changeAddress) {
        List<DepositRefundContext> depositRefundContexts = buildGovernancePayments(fromAddress, changeAddress);

        TxBuilder txBuilder = (context, txn) -> {
        };
        txBuilder = buildDRepRegistration(txBuilder, fromAddress);
        txBuilder = buildDRepDeRegistration(txBuilder, fromAddress);
        txBuilder = buildDRepUpdate(txBuilder, fromAddress);
        txBuilder = buildCreateProposal(txBuilder, fromAddress);
        txBuilder = buildCreateVotingProcedures(txBuilder);
        txBuilder = buildVoteDelegations(txBuilder);

        return new Tuple<>(depositRefundContexts, txBuilder);
    }

    private List<DepositRefundContext> buildGovernancePayments(String fromAddress, String changeAddress) {
        List<DepositRefundContext> depositRefundContexts = new ArrayList<>();

        if ((dRepRegistrationContexts == null || dRepRegistrationContexts.size() == 0)
                && (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0)
                && (createProposalContexts == null || createProposalContexts.size() == 0)) {
            return depositRefundContexts;
        }

        if (dRepRegistrationContexts != null && dRepRegistrationContexts.size() > 0) {
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.DREP_REGISTRATION, dRepRegistrationContexts.size()));
        }

        if (dRepDeregestrationContexts != null && dRepDeregestrationContexts.size() > 0) {
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.DREP_DEREGISTRATION));
        }

        /**
        if (createProposalContexts != null && createProposalContexts.size() > 0) {
            var totalDeposit = createProposalContexts.stream()
                    .map(c -> c.deposit)
                    .reduce(BigInteger::add);

            if (totalDeposit.isPresent()) {
                paymentContexts.add(new GovTx.PaymentContext(fromAddress, Amount.lovelace(totalDeposit.get())));
            }
        }**/
        if (createProposalContexts != null && createProposalContexts.size() > 0) {
            depositRefundContexts.add(new DepositRefundContext(fromAddress, DepositRefundType.GOV_ACTION, createProposalContexts.size()));
        }

        return depositRefundContexts;
    }

    private TxBuilder buildDRepRegistration(TxBuilder txBuilder, String fromAddress) {
        if (dRepRegistrationContexts == null || dRepRegistrationContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (dRepRegistrationContexts == null || dRepRegistrationContexts.size() == 0) {
                return;
            }

            //Add DRep registration certificate
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            for (DRepRegestrationContext dRepRegestrationContext: dRepRegistrationContexts) {
                //set drep deposit from protocol params if not set
                if (dRepRegestrationContext.getRegDrepCert().getCoin() == null) {
                    var updatedDRepRegCert = RegDRepCert.builder()
                            .drepCredential(dRepRegestrationContext.getRegDrepCert().getDrepCredential())
                            .anchor(dRepRegestrationContext.getRegDrepCert().getAnchor())
                            .coin(context.getProtocolParams().getDrepDeposit())
                            .build();
                    dRepRegestrationContext.setRegDrepCert(updatedDRepRegCert);
                    certificates.add(updatedDRepRegCert);
                } else {
                    certificates.add(dRepRegestrationContext.getRegDrepCert());
                }

                if (dRepRegestrationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = dRepRegestrationContext.redeemer;
                    redeemer.setIndex(certificates.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }
            }

            BigInteger totalDRepRegDeposit = dRepRegistrationContexts.stream()
                    .map(c -> c.getRegDrepCert().getCoin())
                    .reduce(BigInteger::add)
                    .orElse(BigInteger.ZERO);
            log.debug("Total drep registration deposit: " + totalDRepRegDeposit);

            txn.getBody().getOutputs()
                    .stream().filter(to -> to.getAddress().equals(fromAddress) && to.getValue().getCoin().compareTo(totalDRepRegDeposit) >= 0)
                    .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        //Remove the deposit amount from the from addres output
                        to.getValue().setCoin(to.getValue().getCoin().subtract(totalDRepRegDeposit));

                        if (to.getValue().getCoin().equals(BigInteger.ZERO) && to.getValue().getMultiAssets() == null && to.getValue().getMultiAssets().size() == 0) {
                            txn.getBody().getOutputs().remove(to);
                        }
                    }, () -> {
                        throw new TxBuildException("Output for from address not found to remove deposit amount: " + fromAddress);
                    });

        });
        return txBuilder;
    }

    private TxBuilder buildDRepDeRegistration(TxBuilder txBuilder, String fromAddress) {
        if (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0) {
                return;
            }

            //Add DRep de-registration certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            if (txn.getWitnessSet() == null) {
                txn.setWitnessSet(new TransactionWitnessSet());
            }

            for (DRepDeregestrationContext dRepDeregestrationContext : dRepDeregestrationContexts) {
                //If refund amt is null, set it from protocol params
                if (dRepDeregestrationContext.getUnregDrepCert().getCoin() == null) {
                    var unRegDrepCert = UnregDRepCert.builder()
                            .drepCredential(dRepDeregestrationContext.getUnregDrepCert().getDrepCredential())
                            .coin(context.getProtocolParams().getDrepDeposit())
                            .build();
                    dRepDeregestrationContext.setUnregDrepCert(unRegDrepCert);
                    certificates.add(unRegDrepCert);
                } else {
                    certificates.add(dRepDeregestrationContext.getUnregDrepCert());
                }

                if (dRepDeregestrationContext.refundAddress == null)
                    dRepDeregestrationContext.refundAddress = fromAddress;

                if (dRepDeregestrationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = dRepDeregestrationContext.redeemer;
                    redeemer.setIndex(certificates.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }

                //Add deposit refund
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(dRepDeregestrationContext.refundAddress))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Add deposit amount to the change address
                            to.getValue().setCoin(to.getValue().getCoin().add(dRepDeregestrationContext.getUnregDrepCert().getCoin()));
                        }, () -> {
                            TransactionOutput transactionOutput = new TransactionOutput(dRepDeregestrationContext.refundAddress,
                                    Value.builder().coin(dRepDeregestrationContext.getUnregDrepCert().getCoin()).build());
                            txn.getBody().getOutputs().add(transactionOutput);
                        });
            }
        });
        return txBuilder;
    }

    private TxBuilder buildDRepUpdate(TxBuilder txBuilder, String fromAddress) {
        if (updateDRepContexts == null || updateDRepContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (updateDRepContexts == null || updateDRepContexts.size() == 0) {
                return;
            }

            //Add DRep update certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            for (UpdateDRepContext updateDRepContext: updateDRepContexts) {
                certificates.add(updateDRepContext.getUpdateDRepCert());

                if (updateDRepContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = updateDRepContext.redeemer;
                    redeemer.setIndex(certificates.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }
            }
        });
        return txBuilder;
    }

    private TxBuilder buildCreateProposal(TxBuilder txBuilder, String fromAddress) {
        if (createProposalContexts == null || createProposalContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (createProposalContexts == null || createProposalContexts.size() == 0) {
                return;
            }

            var proposalProcedures = txn.getBody().getProposalProcedures();
            if (proposalProcedures == null) {
                proposalProcedures = new ArrayList<>();
                txn.getBody().setProposalProcedures(proposalProcedures);
            }

            for (var proposal: createProposalContexts) {
                if (proposal.deposit == null)
                    proposal.setDeposit(context.getProtocolParams().getGovActionDeposit());

                proposalProcedures.add(ProposalProcedure.builder()
                        .govAction(proposal.govAction)
                        .deposit(proposal.deposit)
                        .rewardAccount(proposal.returnAddress)
                        .anchor(proposal.anchor)
                        .build()
                );

                if (proposal.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = proposal.redeemer;
                    redeemer.setIndex(proposalProcedures.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }
            }

            var totalDeposit = createProposalContexts.stream()
                    .map(c -> c.deposit)
                    .reduce(BigInteger::add)
                    .orElse(BigInteger.ZERO);

            txn.getBody().getOutputs()
                    .stream().filter(to -> to.getAddress().equals(fromAddress) && to.getValue().getCoin().compareTo(totalDeposit) >= 0)
                    .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        //Remove the deposit amount from the from address output
                        to.getValue().setCoin(to.getValue().getCoin().subtract(totalDeposit));

                        if (to.getValue().getCoin().equals(BigInteger.ZERO) && to.getValue().getMultiAssets() == null && to.getValue().getMultiAssets().size() == 0) {
                            txn.getBody().getOutputs().remove(to);
                        }
                    }, () -> {
                        throw new TxBuildException("Output for from address not found to remove deposit amount: " + fromAddress);
                    });

        });
        return txBuilder;
    }

    private TxBuilder buildCreateVotingProcedures(TxBuilder txBuilder) {
        if (votingProcedureContexts == null || votingProcedureContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (votingProcedureContexts == null || votingProcedureContexts.size() == 0) {
                return;
            }

            var votingProcedures = txn.getBody().getVotingProcedures();
            if (votingProcedures == null) {
                votingProcedures = new VotingProcedures();
                txn.getBody().setVotingProcedures(votingProcedures);
            }

            int index = 0;
            for (var votingProcedureContext : votingProcedureContexts) {
                votingProcedures.add(votingProcedureContext.voter, votingProcedureContext.govActionId, votingProcedureContext.votingProcedure);

                if (votingProcedureContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = votingProcedureContext.redeemer;
                    redeemer.setIndex(votingProcedures.getVoting().size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }

                index++;
            }
        });

        return txBuilder;
    }

    private TxBuilder buildVoteDelegations(TxBuilder txBuilder) {
        if (votingDelegationContexts == null || votingDelegationContexts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (votingDelegationContexts == null || votingDelegationContexts.size() == 0) {
                return;
            }

            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            for (var votingDelegationContext : votingDelegationContexts) {
                certificates.add(votingDelegationContext.getVoteDelegCert());

                if (votingDelegationContext.redeemer != null) {
                    //Add redeemer to witness set
                    Redeemer redeemer = votingDelegationContext.redeemer;
                    redeemer.setIndex(certificates.size() - 1);
                    txn.getWitnessSet().getRedeemers().add(redeemer);
                }
            }
        });

        return txBuilder;
    }

    @Data
    @AllArgsConstructor
    static class DRepRegestrationContext {
        private RegDRepCert regDrepCert;
        private Redeemer redeemer;
    }

    @Data
    @AllArgsConstructor
    static class DRepDeregestrationContext {
        private UnregDRepCert unregDrepCert;
        private String refundAddress;
//        private BigInteger refundAmount;
        private Redeemer redeemer;
    }

    @Data
    @AllArgsConstructor
    static class UpdateDRepContext {
        private UpdateDRepCert updateDRepCert;
        private Redeemer redeemer;
    }

    @Data
    @AllArgsConstructor
    static class CreateProposalContext {
        private BigInteger deposit;
        private String returnAddress; //stake address
        private GovAction govAction;
        private Anchor anchor;
        private Redeemer redeemer;
    }

    @Data
    @AllArgsConstructor
    static class VotingProcedureContext {
        private Voter voter;
        private GovActionId govActionId;
        private VotingProcedure votingProcedure;
        private Redeemer redeemer;
    }

    @Data
    @AllArgsConstructor
    static class VotingDelegationContext {
        private VoteDelegCert voteDelegCert;
        private Redeemer redeemer;
    }

}

