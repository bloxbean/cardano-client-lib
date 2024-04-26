package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
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

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;

@Slf4j
public class GovTx {
    //TODO -- Read from protocol
    public static final BigInteger DREP_REG_DEPOSIT = adaToLovelace(500.0);
    public static final Amount DUMMY_MIN_OUTPUT_VAL = Amount.ada(1.0);

    protected List<RegDRepCert> dRepRegistrations;
    protected List<DRepDeregestrationContext> dRepDeregestrationContexts;
    protected List<UpdateDRepCert> updateDrepCerts;
    protected List<CreateProposalContext> createProposalContexts;
    protected List<VotingProcedureContext> votingProcedureContexts;
    protected List<VoteDelegCert> voteDelegCerts;

    /**
     * Register DRep
     * @param drepCredential DRep credential
     * @param anchor Anchor
     * @return GovTx
     */
    public GovTx registerDRep(@NonNull Credential drepCredential, Anchor anchor) {
        var regDRepCert = RegDRepCert.builder()
                .drepCredential(drepCredential)
                .anchor(anchor)
                .coin(DREP_REG_DEPOSIT)
                .build();

        if (dRepRegistrations == null)
            dRepRegistrations = new ArrayList<>();

        dRepRegistrations.add(regDRepCert);
        return this;
    }

    /**
     * Register DRep
     * @param drepCredential DRep credential
     * @param drepRegDeposit Deposit amount configured for DRep registration in protocol params
     * @param anchor Anchor
     * @return GovTx
     */
    public GovTx registerDRep(@NonNull Credential drepCredential, @NonNull BigInteger drepRegDeposit, Anchor anchor) {
        var regDRepCert = RegDRepCert.builder()
                .drepCredential(drepCredential)
                .anchor(anchor)
                .coin(drepRegDeposit)
                .build();

        if (dRepRegistrations == null)
            dRepRegistrations = new ArrayList<>();

        dRepRegistrations.add(regDRepCert);
        return this;
    }

    /**
     * Unregister DRep
     * @param drepCredential DRep credential
     * @param refundAddress Refund address
     * @param refundAmount Refund amount
     * @return GovTx
     */
    public GovTx unregisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        if (refundAmount == null)
            refundAmount = DREP_REG_DEPOSIT;

        var unregDRepCert = UnregDRepCert.builder()
                .drepCredential(drepCredential)
                .coin(refundAmount)
                .build();

        if (dRepDeregestrationContexts == null)
            dRepDeregestrationContexts = new ArrayList<>();

        dRepDeregestrationContexts.add(new DRepDeregestrationContext(unregDRepCert, refundAddress, refundAmount));
        return this;
    }

    /**
     * Update DRep
     * @param drepCredential DRep credential
     * @param anchor Anchor
     * @return GovTx
     */
    public GovTx updateDRep(@NonNull Credential drepCredential, Anchor anchor) {
        var updateDRepCert = UpdateDRepCert.builder()
                .drepCredential(drepCredential)
                .anchor(anchor)
                .build();

        if (updateDrepCerts == null)
            updateDrepCerts = new ArrayList<>();

        updateDrepCerts.add(updateDRepCert);
        return this;
    }

    /**
     * Create Gov Action Proposal
     * @param govAction Gov Action
     * @param deposit Deposit
     * @param returnAddress Return stake address
     * @param anchor Anchor
     * @return GovTx
     */
    public GovTx createProposal(@NonNull GovAction govAction, @NonNull BigInteger deposit, @NonNull String returnAddress, Anchor anchor) {
        var createProposalContext = new CreateProposalContext(deposit, returnAddress, govAction, anchor);

        if (createProposalContexts == null)
            createProposalContexts = new ArrayList<>();

        createProposalContexts.add(createProposalContext);
        return this;
    }

    /**
     * Create a voting procedure
     * @param voter Voter
     * @param govActionId GovActionId
     * @param vote Vote
     * @param anchor Anchor
     * @return Tx
     */
    public GovTx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor) {
        if (votingProcedureContexts == null)
            votingProcedureContexts = new ArrayList<>();

        votingProcedureContexts.add(new VotingProcedureContext(voter, govActionId, new VotingProcedure(vote, anchor)));
        return this;
    }

    /**
     * Delegate Votes to DRep
     * @param address Address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param drep DRep to delegate
     * @return GovTx
     */
    public GovTx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep) {
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

        if (voteDelegCerts == null)
            voteDelegCerts = new ArrayList<>();

        voteDelegCerts.add(voteDelegation);
        return this;
    }

    /**
     * Return TxBuilder, payments to build a drep transaction
     *
     * @param fromAddress
     * @return Tuple<List<PaymentContext>, TxBuilder>
     */
    Tuple<List<GovTx.PaymentContext>, TxBuilder> build(String fromAddress, String changeAddress) {
        List<GovTx.PaymentContext> paymentContexts = buildGovernancePayments(fromAddress, changeAddress);

        TxBuilder txBuilder = (context, txn) -> {
        };
        txBuilder = buildDRepRegistration(txBuilder, fromAddress);
        txBuilder = buildDRepDeRegistration(txBuilder, fromAddress);
        txBuilder = buildDRepUpdate(txBuilder, fromAddress);
        txBuilder = buildCreateProposal(txBuilder, fromAddress);
        txBuilder = buildCreateVotingProcedures(txBuilder);
        txBuilder = buildVoteDelegations(txBuilder);

        return new Tuple<>(paymentContexts, txBuilder);
    }

    private List<GovTx.PaymentContext> buildGovernancePayments(String fromAddress, String changeAddress) {
        List<GovTx.PaymentContext> paymentContexts = new ArrayList<>();
        if ((dRepRegistrations == null || dRepRegistrations.size() == 0)
                && (dRepDeregestrationContexts == null || dRepDeregestrationContexts.size() == 0)
                && (createProposalContexts == null || createProposalContexts.size() == 0)) {
            return paymentContexts;
        }

        if (dRepRegistrations != null && dRepRegistrations.size() > 0) {
            //Dummy pay to fromAddress to add deposit
            Amount totalDRepRegistrationDepositAmount = Amount.lovelace(DREP_REG_DEPOSIT.multiply(BigInteger.valueOf(dRepRegistrations.size())));
            paymentContexts.add(new GovTx.PaymentContext(fromAddress, totalDRepRegistrationDepositAmount));
        }

        if (dRepDeregestrationContexts != null && dRepDeregestrationContexts.size() > 0) {
            paymentContexts.add(new GovTx.PaymentContext(fromAddress, DUMMY_MIN_OUTPUT_VAL)); //Dummy output to sender fromAddress to trigger input selection
        }

        if (createProposalContexts != null && createProposalContexts.size() > 0) {
            var totalDeposit = createProposalContexts.stream()
                    .map(c -> c.deposit)
                    .reduce(BigInteger::add);

            if (totalDeposit.isPresent()) {
                paymentContexts.add(new GovTx.PaymentContext(fromAddress, Amount.lovelace(totalDeposit.get())));
            }
        }

        return paymentContexts;
    }

    private TxBuilder buildDRepRegistration(TxBuilder txBuilder, String fromAddress) {
        if (dRepRegistrations == null || dRepRegistrations.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (dRepRegistrations == null || dRepRegistrations.size() == 0) {
                return;
            }

            //Add DRep registration certificate
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            certificates.addAll(dRepRegistrations);

            String drepRegDepositParam = DREP_REG_DEPOSIT.toString();//context.getProtocolParams().getKeyDeposit(); //TODO -- Get protocol param
            BigInteger drepRegDeposit = new BigInteger(drepRegDepositParam);
            BigInteger totalDRepRegDeposit = drepRegDeposit.multiply(BigInteger.valueOf(dRepRegistrations.size()));
            log.debug("Total stakekey registration deposit: " + totalDRepRegDeposit);

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
                certificates.add(dRepDeregestrationContext.getUnregDrepCert());

                if (dRepDeregestrationContext.refundAddress == null)
                    dRepDeregestrationContext.refundAddress = fromAddress;

                //Add deposit refund
                txn.getBody().getOutputs()
                        .stream().filter(to -> to.getAddress().equals(dRepDeregestrationContext.refundAddress))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            //Add deposit amount to the change address
                            to.getValue().setCoin(to.getValue().getCoin().add(dRepDeregestrationContext.refundAmount));
                        }, () -> {
                            TransactionOutput transactionOutput = new TransactionOutput(dRepDeregestrationContext.refundAddress,
                                    Value.builder().coin(dRepDeregestrationContext.refundAmount).build());
                            txn.getBody().getOutputs().add(transactionOutput);
                        });
            }
        });
        return txBuilder;
    }

    private TxBuilder buildDRepUpdate(TxBuilder txBuilder, String fromAddress) {
        if (updateDrepCerts == null || updateDrepCerts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (updateDrepCerts == null || updateDrepCerts.size() == 0) {
                return;
            }

            //Add DRep update certificates
            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            certificates.addAll(updateDrepCerts);
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
                proposalProcedures.add(ProposalProcedure.builder()
                        .govAction(proposal.govAction)
                        .deposit(proposal.deposit)
                        .rewardAccount(proposal.returnAddress)
                        .anchor(proposal.anchor)
                        .build()
                );
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

            for (var votingProcedureContext : votingProcedureContexts) {
                votingProcedures.add(votingProcedureContext.voter, votingProcedureContext.govActionId, votingProcedureContext.votingProcedure);
            }
        });

        return txBuilder;
    }

    private TxBuilder buildVoteDelegations(TxBuilder txBuilder) {
        if (voteDelegCerts == null || voteDelegCerts.size() == 0)
            return txBuilder;

        txBuilder = txBuilder.andThen((context, txn) -> {
            if (voteDelegCerts == null || voteDelegCerts.size() == 0) {
                return;
            }

            List<Certificate> certificates = txn.getBody().getCerts();
            if (certificates == null) {
                certificates = new ArrayList<>();
                txn.getBody().setCerts(certificates);
            }

            for (var voteDelegCert : voteDelegCerts) {
                certificates.add(voteDelegCert);
            }
        });

        return txBuilder;
    }

    @Data
    @AllArgsConstructor
    static class DRepDeregestrationContext {
        private UnregDRepCert unregDrepCert;
        private String refundAddress;
        private BigInteger refundAmount;
    }

    @Data
    @AllArgsConstructor
    static class PaymentContext {
        private String address;
        private Amount amount;
    }

    @Data
    @AllArgsConstructor
    static class CreateProposalContext {
        private BigInteger deposit;
        private String returnAddress; //stake address
        private GovAction govAction;
        private Anchor anchor;
    }

    @Data
    @AllArgsConstructor
    static class VotingProcedureContext {
        private Voter voter;
        private GovActionId govActionId;
        private VotingProcedure votingProcedure;
    }

}

