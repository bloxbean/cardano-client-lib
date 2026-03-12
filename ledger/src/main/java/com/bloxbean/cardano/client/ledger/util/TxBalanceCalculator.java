package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.ledger.slice.UtxoSlice;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the consumed and produced values for the value conservation check.
 * <p>
 * The invariant: consumed(tx) == produced(tx)
 * <p>
 * Consumed = inputs + positive_mint + withdrawals + stake_refunds + drep_refunds
 * Produced = outputs + negative_mint(burns) + fee + stake_deposits + pool_deposits
 *          + drep_deposits + proposal_deposits + treasury_donation
 * <p>
 * Reference: Scalus TxBalance.scala, Haskell Shelley.Rules.Utxo.validateValueNotConservedUTxO
 */
public class TxBalanceCalculator {

    private TxBalanceCalculator() {}

    /**
     * Compute the total consumed value.
     *
     * @param utxoSlice  the UTxO slice for resolving inputs
     * @param tx         the transaction
     * @param pp         protocol parameters
     * @return the consumed value, or null if an input is missing from the UTxO set
     */
    public static Value consumed(UtxoSlice utxoSlice, Transaction tx, ProtocolParams pp) {
        TransactionBody body = tx.getBody();
        Value result = Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build();

        // 1. Sum of input UTxO values
        if (body.getInputs() != null && utxoSlice != null) {
            for (TransactionInput input : body.getInputs()) {
                var outputOpt = utxoSlice.lookup(input);
                if (outputOpt.isEmpty()) return null; // Missing input
                result = result.plus(outputOpt.get().getValue());
            }
        }

        // 2. Positive mint (minted tokens go to consumed side)
        if (body.getMint() != null && !body.getMint().isEmpty()) {
            List<MultiAsset> positiveMint = filterMint(body.getMint(), true);
            if (!positiveMint.isEmpty()) {
                result = result.plus(new Value(BigInteger.ZERO, positiveMint));
            }
        }

        // 3. Withdrawal ADA
        if (body.getWithdrawals() != null) {
            BigInteger totalWithdrawals = BigInteger.ZERO;
            for (Withdrawal w : body.getWithdrawals()) {
                if (w.getCoin() != null) {
                    totalWithdrawals = totalWithdrawals.add(w.getCoin());
                }
            }
            result = result.plus(new Value(totalWithdrawals, null));
        }

        // 4. Certificate refunds (stake + DRep deregistrations)
        BigInteger refunds = computeTotalRefunds(body.getCerts(), pp);
        if (refunds.signum() > 0) {
            result = result.plus(new Value(refunds, null));
        }

        return result;
    }

    /**
     * Compute the total produced value.
     *
     * @param tx the transaction
     * @param pp protocol parameters
     * @return the produced value
     */
    public static Value produced(Transaction tx, ProtocolParams pp) {
        TransactionBody body = tx.getBody();
        Value result = Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build();

        // 1. Sum of output values
        if (body.getOutputs() != null) {
            for (TransactionOutput output : body.getOutputs()) {
                if (output.getValue() != null) {
                    result = result.plus(output.getValue());
                }
            }
        }

        // 2. Negative mint (burned tokens, sign-reversed, go to produced side)
        if (body.getMint() != null && !body.getMint().isEmpty()) {
            List<MultiAsset> negativeMint = filterMint(body.getMint(), false);
            if (!negativeMint.isEmpty()) {
                // Negate the negative values so they become positive on the produced side
                List<MultiAsset> burnedPositive = new ArrayList<>();
                for (MultiAsset ma : negativeMint) {
                    List<Asset> positiveAssets = new ArrayList<>();
                    for (Asset asset : ma.getAssets()) {
                        positiveAssets.add(Asset.builder()
                                .name(asset.getName())
                                .value(asset.getValue().negate())
                                .build());
                    }
                    burnedPositive.add(MultiAsset.builder()
                            .policyId(ma.getPolicyId())
                            .assets(positiveAssets)
                            .build());
                }
                result = result.plus(new Value(BigInteger.ZERO, burnedPositive));
            }
        }

        // 3. Fee
        if (body.getFee() != null) {
            result = result.plus(new Value(body.getFee(), null));
        }

        // 4. Certificate deposits (stake + pool + DRep registrations)
        BigInteger deposits = computeTotalDeposits(body.getCerts(), pp);
        if (deposits.signum() > 0) {
            result = result.plus(new Value(deposits, null));
        }

        // 5. Proposal deposits
        if (body.getProposalProcedures() != null) {
            BigInteger proposalDeposits = BigInteger.ZERO;
            for (ProposalProcedure proposal : body.getProposalProcedures()) {
                if (proposal.getDeposit() != null) {
                    proposalDeposits = proposalDeposits.add(proposal.getDeposit());
                }
            }
            if (proposalDeposits.signum() > 0) {
                result = result.plus(new Value(proposalDeposits, null));
            }
        }

        // 6. Treasury donation
        if (body.getDonation() != null && body.getDonation().signum() > 0) {
            result = result.plus(new Value(body.getDonation(), null));
        }

        return result;
    }

    /**
     * Compute total deposits from certificates.
     * Includes: StakeRegistration (keyDeposit), RegCert, StakeRegDelegCert,
     * VoteRegDelegCert, StakeVoteRegDelegCert, PoolRegistration (poolDeposit),
     * RegDRepCert (explicit deposit).
     */
    static BigInteger computeTotalDeposits(List<Certificate> certs, ProtocolParams pp) {
        if (certs == null || certs.isEmpty()) return BigInteger.ZERO;

        BigInteger keyDeposit = pp.getKeyDeposit() != null ? new BigInteger(pp.getKeyDeposit()) : BigInteger.ZERO;
        BigInteger poolDeposit = pp.getPoolDeposit() != null ? new BigInteger(pp.getPoolDeposit()) : BigInteger.ZERO;

        BigInteger total = BigInteger.ZERO;

        for (Certificate cert : certs) {
            if (cert instanceof StakeRegistration) {
                // Legacy cert type 0 uses params default
                total = total.add(keyDeposit);
            } else if (cert instanceof RegCert regCert) {
                // Conway cert type 7 with explicit deposit
                total = total.add(regCert.getCoin() != null ? regCert.getCoin() : keyDeposit);
            } else if (cert instanceof StakeRegDelegCert stakeRegDeleg) {
                total = total.add(stakeRegDeleg.getCoin() != null ? stakeRegDeleg.getCoin() : keyDeposit);
            } else if (cert instanceof VoteRegDelegCert voteRegDeleg) {
                total = total.add(voteRegDeleg.getCoin() != null ? voteRegDeleg.getCoin() : keyDeposit);
            } else if (cert instanceof StakeVoteRegDelegCert stakeVoteRegDeleg) {
                total = total.add(stakeVoteRegDeleg.getCoin() != null ? stakeVoteRegDeleg.getCoin() : keyDeposit);
            } else if (cert instanceof PoolRegistration) {
                total = total.add(poolDeposit);
            } else if (cert instanceof RegDRepCert regDRep) {
                // DRep registration uses explicit deposit from cert
                total = total.add(regDRep.getCoin() != null ? regDRep.getCoin() : BigInteger.ZERO);
            }
        }

        return total;
    }

    /**
     * Compute total refunds from certificates.
     * Includes: StakeDeregistration (keyDeposit), UnregCert (explicit refund),
     * UnregDRepCert (explicit refund).
     * <p>
     * Note: For full accuracy with same-tx register+deregister tracking,
     * this would need AccountsSlice/DRepsSlice lookups. For M2, we use
     * the explicit refund amount from the certificate or the params default.
     */
    static BigInteger computeTotalRefunds(List<Certificate> certs, ProtocolParams pp) {
        if (certs == null || certs.isEmpty()) return BigInteger.ZERO;

        BigInteger keyDeposit = pp.getKeyDeposit() != null ? new BigInteger(pp.getKeyDeposit()) : BigInteger.ZERO;

        BigInteger total = BigInteger.ZERO;

        for (Certificate cert : certs) {
            if (cert instanceof StakeDeregistration) {
                // Legacy cert type 1 uses params default
                total = total.add(keyDeposit);
            } else if (cert instanceof UnregCert unregCert) {
                // Conway cert type 8 with explicit refund
                total = total.add(unregCert.getCoin() != null ? unregCert.getCoin() : keyDeposit);
            } else if (cert instanceof UnregDRepCert unregDRep) {
                // DRep deregistration uses explicit refund from cert
                total = total.add(unregDRep.getCoin() != null ? unregDRep.getCoin() : BigInteger.ZERO);
            }
        }

        return total;
    }

    /**
     * Filter mint to only positive (minted) or only negative (burned) quantities.
     *
     * @param mint     the mint multi-asset list
     * @param positive true for minted tokens (positive), false for burned (negative)
     * @return filtered multi-asset list
     */
    private static List<MultiAsset> filterMint(List<MultiAsset> mint, boolean positive) {
        List<MultiAsset> filtered = new ArrayList<>();

        for (MultiAsset ma : mint) {
            List<Asset> matchingAssets = new ArrayList<>();
            for (Asset asset : ma.getAssets()) {
                if (positive && asset.getValue().signum() > 0) {
                    matchingAssets.add(asset);
                } else if (!positive && asset.getValue().signum() < 0) {
                    matchingAssets.add(asset);
                }
            }
            if (!matchingAssets.isEmpty()) {
                filtered.add(MultiAsset.builder()
                        .policyId(ma.getPolicyId())
                        .assets(matchingAssets)
                        .build());
            }
        }

        return filtered;
    }
}
