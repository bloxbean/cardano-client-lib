package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.AccountsSlice;
import com.bloxbean.cardano.client.ledger.slice.CommitteeSlice;
import com.bloxbean.cardano.client.ledger.slice.DRepsSlice;
import com.bloxbean.cardano.client.ledger.slice.PoolsSlice;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepType;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Category F: Certificate Validation Rule.
 * <p>
 * Validates certificate semantics including deposit amounts, registration status,
 * pool costs, retirement epochs, DRep/committee membership. Stateful checks
 * gracefully skip when the required state slice is null.
 */
public class CertificateValidationRule implements LedgerRule {

    private static final String RULE_NAME = "CertificateValidation";

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        List<ValidationError> errors = new ArrayList<>();
        TransactionBody body = transaction.getBody();

        // Validate certificates
        List<Certificate> certs = body.getCerts();
        if (certs != null && !certs.isEmpty()) {
            for (int i = 0; i < certs.size(); i++) {
                validateCertificate(context, certs.get(i), i, errors);
            }
        }

        // Validate withdrawals (F-W: withdrawal amounts must drain full reward balance)
        validateWithdrawals(context, body.getWithdrawals(), errors);

        return errors;
    }

    private void validateCertificate(LedgerContext context, Certificate cert, int index,
                                     List<ValidationError> errors) {
        // --- Stake registration certs (old Shelley) ---
        if (cert instanceof StakeRegistration c) {
            validateStakeRegistration(context, c, index, errors);

        } else if (cert instanceof StakeDeregistration c) {
            validateStakeDeregistration(context, c, index, errors);

        } else if (cert instanceof StakeDelegation c) {
            validateStakeDelegation(context, c, index, errors);

        // --- Conway registration/unregistration with deposit ---
        } else if (cert instanceof RegCert c) {
            validateRegCert(context, c, index, errors);

        } else if (cert instanceof UnregCert c) {
            validateUnregCert(context, c, index, errors);

        // --- Delegation certs (no deposit) ---
        } else if (cert instanceof VoteDelegCert c) {
            validateVoteDelegCert(context, c, index, errors);

        } else if (cert instanceof StakeVoteDelegCert c) {
            validateStakeVoteDelegCert(context, c, index, errors);

        // --- Delegation + registration certs (with deposit) ---
        } else if (cert instanceof StakeRegDelegCert c) {
            validateStakeRegDelegCert(context, c, index, errors);

        } else if (cert instanceof VoteRegDelegCert c) {
            validateVoteRegDelegCert(context, c, index, errors);

        } else if (cert instanceof StakeVoteRegDelegCert c) {
            validateStakeVoteRegDelegCert(context, c, index, errors);

        // --- Pool certs ---
        } else if (cert instanceof PoolRegistration c) {
            validatePoolRegistration(context, c, index, errors);

        } else if (cert instanceof PoolRetirement c) {
            validatePoolRetirement(context, c, index, errors);

        // --- DRep certs ---
        } else if (cert instanceof RegDRepCert c) {
            validateRegDRepCert(context, c, index, errors);

        } else if (cert instanceof UnregDRepCert c) {
            validateUnregDRepCert(context, c, index, errors);

        } else if (cert instanceof UpdateDRepCert c) {
            validateUpdateDRepCert(context, c, index, errors);

        // --- Committee certs ---
        } else if (cert instanceof AuthCommitteeHotCert c) {
            validateAuthCommitteeHotCert(context, c, index, errors);

        } else if (cert instanceof ResignCommitteeColdCert c) {
            validateResignCommitteeColdCert(context, c, index, errors);
        }
    }

    // ---- Stake Registration (old Shelley, type 0) ----

    private void validateStakeRegistration(LedgerContext context, StakeRegistration cert,
                                           int index, List<ValidationError> errors) {
        // F-6: credential NOT already registered
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null && accounts.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] StakeRegistration: credential " + hash
                        + " is already registered"));
            }
        }
    }

    // ---- Stake Deregistration (old Shelley, type 1) ----

    private void validateStakeDeregistration(LedgerContext context, StakeDeregistration cert,
                                             int index, List<ValidationError> errors) {
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null) {
                // F-7: credential IS registered
                if (!accounts.isRegistered(hash)) {
                    errors.add(error("Cert[" + index + "] StakeDeregistration: credential " + hash
                            + " is not registered"));
                } else {
                    // F-8: reward balance == 0
                    Optional<BigInteger> balance = accounts.getRewardBalance(hash);
                    if (balance.isPresent() && balance.get().signum() > 0) {
                        errors.add(error("Cert[" + index + "] StakeDeregistration: credential " + hash
                                + " has non-zero reward balance " + balance.get()));
                    }
                }
            }
        }
    }

    // ---- Stake Delegation (type 2) ----

    private void validateStakeDelegation(LedgerContext context, StakeDelegation cert,
                                         int index, List<ValidationError> errors) {
        // F-10: credential registered + pool exists
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null && !accounts.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] StakeDelegation: credential " + hash
                        + " is not registered"));
            }
        }

        PoolsSlice pools = context.getPoolsSlice();
        if (pools != null && cert.getStakePoolId() != null) {
            String poolId = HexUtil.encodeHexString(cert.getStakePoolId().getPoolKeyHash());
            if (!pools.isRegistered(poolId)) {
                errors.add(error("Cert[" + index + "] StakeDelegation: pool " + poolId
                        + " is not registered"));
            }
        }
    }

    // ---- RegCert (Conway, type 7) ----

    private void validateRegCert(LedgerContext context, RegCert cert, int index,
                                 List<ValidationError> errors) {
        // F-1: deposit == pp.keyDeposit
        validateKeyDeposit(context, cert.getCoin(), index, "RegCert", errors);

        // F-6: credential NOT already registered
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null && accounts.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] RegCert: credential " + hash
                        + " is already registered"));
            }
        }
    }

    // ---- UnregCert (Conway, type 8) ----

    private void validateUnregCert(LedgerContext context, UnregCert cert, int index,
                                   List<ValidationError> errors) {
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null) {
                // F-7: credential IS registered
                if (!accounts.isRegistered(hash)) {
                    errors.add(error("Cert[" + index + "] UnregCert: credential " + hash
                            + " is not registered"));
                } else {
                    // F-8: reward balance == 0
                    Optional<BigInteger> balance = accounts.getRewardBalance(hash);
                    if (balance.isPresent() && balance.get().signum() > 0) {
                        errors.add(error("Cert[" + index + "] UnregCert: credential " + hash
                                + " has non-zero reward balance " + balance.get()));
                    }
                    // F-9: refund matches recorded deposit
                    Optional<BigInteger> deposit = accounts.getDeposit(hash);
                    if (deposit.isPresent() && cert.getCoin() != null
                            && deposit.get().compareTo(cert.getCoin()) != 0) {
                        errors.add(error("Cert[" + index + "] UnregCert: refund " + cert.getCoin()
                                + " does not match recorded deposit " + deposit.get()));
                    }
                }
            }
        }
    }

    // ---- VoteDelegCert (type 9) ----

    private void validateVoteDelegCert(LedgerContext context, VoteDelegCert cert,
                                       int index, List<ValidationError> errors) {
        // F-10 (partial): credential registered
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null && !accounts.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] VoteDelegCert: credential " + hash
                        + " is not registered"));
            }
        }

        // DelegateeDRepNotRegisteredDELEG
        validateDRepRegistered(context, cert.getDrep(), index, "VoteDelegCert", errors);
    }

    // ---- StakeVoteDelegCert (type 10) ----

    private void validateStakeVoteDelegCert(LedgerContext context, StakeVoteDelegCert cert,
                                            int index, List<ValidationError> errors) {
        // F-10: credential registered + pool exists
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null) {
            String hash = credHash(cert.getStakeCredential());
            if (hash != null && !accounts.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] StakeVoteDelegCert: credential " + hash
                        + " is not registered"));
            }
        }

        PoolsSlice pools = context.getPoolsSlice();
        if (pools != null && cert.getPoolKeyHash() != null) {
            if (!pools.isRegistered(cert.getPoolKeyHash())) {
                errors.add(error("Cert[" + index + "] StakeVoteDelegCert: pool "
                        + cert.getPoolKeyHash() + " is not registered"));
            }
        }

        // DelegateeDRepNotRegisteredDELEG
        validateDRepRegistered(context, cert.getDrep(), index, "StakeVoteDelegCert", errors);
    }

    // ---- StakeRegDelegCert (type 11) ----

    private void validateStakeRegDelegCert(LedgerContext context, StakeRegDelegCert cert,
                                           int index, List<ValidationError> errors) {
        // F-1: deposit == pp.keyDeposit
        validateKeyDeposit(context, cert.getCoin(), index, "StakeRegDelegCert", errors);

        // Pool exists check
        PoolsSlice pools = context.getPoolsSlice();
        if (pools != null && cert.getPoolKeyHash() != null) {
            if (!pools.isRegistered(cert.getPoolKeyHash())) {
                errors.add(error("Cert[" + index + "] StakeRegDelegCert: pool "
                        + cert.getPoolKeyHash() + " is not registered"));
            }
        }
    }

    // ---- VoteRegDelegCert (type 12) ----

    private void validateVoteRegDelegCert(LedgerContext context, VoteRegDelegCert cert,
                                          int index, List<ValidationError> errors) {
        // F-1: deposit == pp.keyDeposit
        validateKeyDeposit(context, cert.getCoin(), index, "VoteRegDelegCert", errors);

        // DelegateeDRepNotRegisteredDELEG
        validateDRepRegistered(context, cert.getDrep(), index, "VoteRegDelegCert", errors);
    }

    // ---- StakeVoteRegDelegCert (type 13) ----

    private void validateStakeVoteRegDelegCert(LedgerContext context, StakeVoteRegDelegCert cert,
                                               int index, List<ValidationError> errors) {
        // F-1: deposit == pp.keyDeposit
        validateKeyDeposit(context, cert.getCoin(), index, "StakeVoteRegDelegCert", errors);

        // Pool exists check
        PoolsSlice pools = context.getPoolsSlice();
        if (pools != null && cert.getPoolKeyHash() != null) {
            if (!pools.isRegistered(cert.getPoolKeyHash())) {
                errors.add(error("Cert[" + index + "] StakeVoteRegDelegCert: pool "
                        + cert.getPoolKeyHash() + " is not registered"));
            }
        }

        // DelegateeDRepNotRegisteredDELEG
        validateDRepRegistered(context, cert.getDrep(), index, "StakeVoteRegDelegCert", errors);
    }

    // ---- PoolRegistration (type 3) ----

    private void validatePoolRegistration(LedgerContext context, PoolRegistration cert,
                                          int index, List<ValidationError> errors) {
        ProtocolParams pp = context.getProtocolParams();

        // F-2: cost >= pp.minPoolCost
        if (pp != null && pp.getMinPoolCost() != null && cert.getCost() != null) {
            BigInteger minPoolCost = new BigInteger(pp.getMinPoolCost());
            if (cert.getCost().compareTo(minPoolCost) < 0) {
                errors.add(error("Cert[" + index + "] PoolRegistration: cost " + cert.getCost()
                        + " is less than minPoolCost " + minPoolCost));
            }
        }

        // F-3: reward account network matches context.networkId
        if (context.getNetworkId() != null && cert.getRewardAccount() != null) {
            validateRewardAccountNetwork(cert.getRewardAccount(), context, index,
                    "PoolRegistration", errors);
        }

        // PoolMedataHashTooBig: metadata hash must be ≤ 32 bytes
        if (cert.getPoolMetadataHash() != null && !cert.getPoolMetadataHash().isEmpty()) {
            try {
                byte[] hashBytes = HexUtil.decodeHexString(cert.getPoolMetadataHash());
                if (hashBytes.length > 32) {
                    errors.add(error("Cert[" + index + "] PoolRegistration: metadata hash size "
                            + hashBytes.length + " bytes exceeds 32 byte limit"));
                }
            } catch (Exception e) {
                // Invalid hex — skip check
            }
        }
    }

    // ---- PoolRetirement (type 4) ----

    private void validatePoolRetirement(LedgerContext context, PoolRetirement cert,
                                        int index, List<ValidationError> errors) {
        // F-4: epoch in [currentEpoch+1, currentEpoch+eMax]
        long currentEpoch = context.getCurrentEpoch();
        ProtocolParams pp = context.getProtocolParams();
        if (currentEpoch > 0 && pp != null && pp.getEMax() != null) {
            long retireEpoch = cert.getEpoch();
            long minEpoch = currentEpoch + 1;
            long maxEpoch = currentEpoch + pp.getEMax();
            if (retireEpoch < minEpoch || retireEpoch > maxEpoch) {
                errors.add(error("Cert[" + index + "] PoolRetirement: epoch " + retireEpoch
                        + " not in valid range [" + minEpoch + ", " + maxEpoch + "]"));
            }
        }

        // F-11: pool IS registered
        PoolsSlice pools = context.getPoolsSlice();
        if (pools != null && cert.getPoolKeyHash() != null) {
            String poolId = HexUtil.encodeHexString(cert.getPoolKeyHash());
            if (!pools.isRegistered(poolId)) {
                errors.add(error("Cert[" + index + "] PoolRetirement: pool " + poolId
                        + " is not registered"));
            }
        }
    }

    // ---- RegDRepCert (type 16) ----

    private void validateRegDRepCert(LedgerContext context, RegDRepCert cert,
                                     int index, List<ValidationError> errors) {
        // F-5: deposit == pp.drepDeposit
        ProtocolParams pp = context.getProtocolParams();
        if (pp != null && pp.getDrepDeposit() != null && cert.getCoin() != null) {
            if (cert.getCoin().compareTo(pp.getDrepDeposit()) != 0) {
                errors.add(error("Cert[" + index + "] RegDRepCert: deposit " + cert.getCoin()
                        + " does not match pp.drepDeposit " + pp.getDrepDeposit()));
            }
        }

        // Stateful: DRep NOT already registered
        DRepsSlice dreps = context.getDrepsSlice();
        if (dreps != null && cert.getDrepCredential() != null) {
            String hash = credHash(cert.getDrepCredential());
            if (hash != null && dreps.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] RegDRepCert: DRep " + hash
                        + " is already registered"));
            }
        }
    }

    // ---- UnregDRepCert (type 17) ----

    private void validateUnregDRepCert(LedgerContext context, UnregDRepCert cert,
                                       int index, List<ValidationError> errors) {
        // F-12: DRep IS registered, refund matches deposit
        DRepsSlice dreps = context.getDrepsSlice();
        if (dreps != null && cert.getDrepCredential() != null) {
            String hash = credHash(cert.getDrepCredential());
            if (hash != null) {
                if (!dreps.isRegistered(hash)) {
                    errors.add(error("Cert[" + index + "] UnregDRepCert: DRep " + hash
                            + " is not registered"));
                } else {
                    Optional<BigInteger> deposit = dreps.getDeposit(hash);
                    if (deposit.isPresent() && cert.getCoin() != null
                            && deposit.get().compareTo(cert.getCoin()) != 0) {
                        errors.add(error("Cert[" + index + "] UnregDRepCert: refund " + cert.getCoin()
                                + " does not match recorded deposit " + deposit.get()));
                    }
                }
            }
        }
    }

    // ---- UpdateDRepCert (type 18) ----

    private void validateUpdateDRepCert(LedgerContext context, UpdateDRepCert cert,
                                        int index, List<ValidationError> errors) {
        // F-15: DRep IS registered
        DRepsSlice dreps = context.getDrepsSlice();
        if (dreps != null && cert.getDrepCredential() != null) {
            String hash = credHash(cert.getDrepCredential());
            if (hash != null && !dreps.isRegistered(hash)) {
                errors.add(error("Cert[" + index + "] UpdateDRepCert: DRep " + hash
                        + " is not registered"));
            }
        }
    }

    // ---- AuthCommitteeHotCert (type 14) ----

    private void validateAuthCommitteeHotCert(LedgerContext context, AuthCommitteeHotCert cert,
                                              int index, List<ValidationError> errors) {
        CommitteeSlice committee = context.getCommitteeSlice();
        if (committee != null && cert.getCommitteeColdCredential() != null) {
            String hash = credHash(cert.getCommitteeColdCredential());
            if (hash != null) {
                // F-13: cold credential IS committee member
                if (!committee.isMember(hash)) {
                    errors.add(error("Cert[" + index + "] AuthCommitteeHotCert: cold credential " + hash
                            + " is not a committee member"));
                }
                // ConwayCommitteeHasPreviouslyResigned: cannot auth hot key for resigned member
                if (committee.hasResigned(hash)) {
                    errors.add(error("Cert[" + index + "] AuthCommitteeHotCert: cold credential " + hash
                            + " has previously resigned"));
                }
            }
        }
    }

    // ---- ResignCommitteeColdCert (type 15) ----

    private void validateResignCommitteeColdCert(LedgerContext context, ResignCommitteeColdCert cert,
                                                 int index, List<ValidationError> errors) {
        // F-14: cold credential IS committee member
        CommitteeSlice committee = context.getCommitteeSlice();
        if (committee != null && cert.getCommitteeColdCredential() != null) {
            String hash = credHash(cert.getCommitteeColdCredential());
            if (hash != null && !committee.isMember(hash)) {
                errors.add(error("Cert[" + index + "] ResignCommitteeColdCert: cold credential " + hash
                        + " is not a committee member"));
            }
        }
    }

    // ---- Withdrawal validation ----

    private void validateWithdrawals(LedgerContext context, List<Withdrawal> withdrawals,
                                     List<ValidationError> errors) {
        if (withdrawals == null || withdrawals.isEmpty()) return;
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts == null) return;

        for (Withdrawal withdrawal : withdrawals) {
            if (withdrawal.getRewardAddress() == null) continue;
            try {
                Address addr = new Address(withdrawal.getRewardAddress());
                byte[] delegationHash = addr.getDelegationCredentialHash()
                        .orElse(null);
                if (delegationHash == null) continue;

                String hash = HexUtil.encodeHexString(delegationHash);

                // Credential must be registered
                if (!accounts.isRegistered(hash)) {
                    errors.add(error("Withdrawal: credential " + hash + " is not registered"));
                    continue;
                }

                // Withdrawal must drain full reward balance
                Optional<BigInteger> balance = accounts.getRewardBalance(hash);
                if (balance.isPresent() && withdrawal.getCoin() != null
                        && balance.get().compareTo(withdrawal.getCoin()) != 0) {
                    errors.add(error("Withdrawal: amount " + withdrawal.getCoin()
                            + " does not match full reward balance " + balance.get()
                            + " for credential " + hash));
                }
            } catch (Exception e) {
                // Skip unparseable addresses
            }
        }
    }

    // ---- Helper methods ----

    /**
     * DelegateeDRepNotRegisteredDELEG: When delegating to a DRep, the target must be registered.
     * Special DReps (ABSTAIN, NO_CONFIDENCE) are always valid and skip this check.
     */
    private void validateDRepRegistered(LedgerContext context, DRep drep, int index,
                                        String certName, List<ValidationError> errors) {
        if (drep == null) return;
        // Special DReps don't need registration
        if (drep.getType() == DRepType.ABSTAIN || drep.getType() == DRepType.NO_CONFIDENCE) return;

        DRepsSlice dreps = context.getDrepsSlice();
        if (dreps == null) return;

        String hash = drep.getHash();
        if (hash != null && !dreps.isRegistered(hash)) {
            errors.add(error("Cert[" + index + "] " + certName + ": delegatee DRep " + hash
                    + " is not registered"));
        }
    }

    private void validateKeyDeposit(LedgerContext context, BigInteger deposit, int index,
                                    String certName, List<ValidationError> errors) {
        ProtocolParams pp = context.getProtocolParams();
        if (pp != null && pp.getKeyDeposit() != null && deposit != null) {
            BigInteger expectedDeposit = new BigInteger(pp.getKeyDeposit());
            if (deposit.compareTo(expectedDeposit) != 0) {
                errors.add(error("Cert[" + index + "] " + certName + ": deposit " + deposit
                        + " does not match pp.keyDeposit " + expectedDeposit));
            }
        }
    }

    private void validateRewardAccountNetwork(String rewardAccountHex, LedgerContext context,
                                              int index, String certName,
                                              List<ValidationError> errors) {
        try {
            // PoolRegistration stores rewardAccount as hex
            Address addr = new Address(HexUtil.decodeHexString(rewardAccountHex));
            int expectedNetworkInt = context.getNetworkId() == com.bloxbean.cardano.client.spec.NetworkId.MAINNET ? 1 : 0;
            if (addr.getNetwork() != null && addr.getNetwork().getNetworkId() != expectedNetworkInt) {
                errors.add(error("Cert[" + index + "] " + certName + ": reward account network "
                        + addr.getNetwork().getNetworkId()
                        + " does not match expected " + expectedNetworkInt));
            }
        } catch (Exception e) {
            // Skip unparseable addresses
        }
    }

    /**
     * Extract hex-encoded hash from a StakeCredential (pre-Conway certs).
     */
    private String credHash(StakeCredential cred) {
        if (cred == null || cred.getHash() == null) return null;
        return HexUtil.encodeHexString(cred.getHash());
    }

    /**
     * Extract hex-encoded hash from a Credential (Conway certs).
     */
    private String credHash(Credential cred) {
        if (cred == null || cred.getBytes() == null) return null;
        return HexUtil.encodeHexString(cred.getBytes());
    }

    private ValidationError error(String message) {
        return ValidationError.builder()
                .rule(RULE_NAME)
                .message(message)
                .phase(ValidationError.Phase.PHASE_1)
                .build();
    }
}
