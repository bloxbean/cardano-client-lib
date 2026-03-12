package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.ledger.slice.UtxoSlice;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.*;

/**
 * Computes the sets of required VKey hashes and script hashes from a transaction.
 * <p>
 * Sources of required witnesses:
 * <ul>
 *   <li>Input/collateral UTxO addresses (payment credential)</li>
 *   <li>Withdrawal reward addresses (delegation credential)</li>
 *   <li>Certificate credentials (stake/DRep/committee)</li>
 *   <li>Required signers (explicit key hashes)</li>
 *   <li>Mint policy IDs (script hashes)</li>
 *   <li>Voting procedures (voter credentials)</li>
 * </ul>
 * <p>
 * Reference: Haskell Shelley.witsVKeyNeeded, Scalus MissingKeyHashesValidator
 */
public class RequiredWitnessResolver {

    private RequiredWitnessResolver() {}

    /**
     * Result containing required VKey hashes and script hashes.
     */
    public static class WitnessRequirements {
        private final Set<String> requiredVKeyHashes = new LinkedHashSet<>();
        private final Set<String> requiredScriptHashes = new LinkedHashSet<>();

        public Set<String> getRequiredVKeyHashes() {
            return Collections.unmodifiableSet(requiredVKeyHashes);
        }

        public Set<String> getRequiredScriptHashes() {
            return Collections.unmodifiableSet(requiredScriptHashes);
        }

        void addVKeyHash(String hash) {
            if (hash != null) requiredVKeyHashes.add(hash);
        }

        void addVKeyHash(byte[] hash) {
            if (hash != null) requiredVKeyHashes.add(HexUtil.encodeHexString(hash));
        }

        void addScriptHash(String hash) {
            if (hash != null) requiredScriptHashes.add(hash);
        }

        void addScriptHash(byte[] hash) {
            if (hash != null) requiredScriptHashes.add(HexUtil.encodeHexString(hash));
        }

        void addCredential(StakeCredential cred) {
            if (cred == null) return;
            if (cred.getType() == StakeCredType.ADDR_KEYHASH) {
                addVKeyHash(cred.getHash());
            } else {
                addScriptHash(cred.getHash());
            }
        }

        void addCredential(Credential cred) {
            if (cred == null) return;
            if (cred.getType() == CredentialType.Key) {
                addVKeyHash(cred.getBytes());
            } else {
                addScriptHash(cred.getBytes());
            }
        }
    }

    /**
     * Resolve all required witnesses from a transaction and its UTxO context.
     */
    public static WitnessRequirements resolve(Transaction transaction, UtxoSlice utxoSlice) {
        WitnessRequirements reqs = new WitnessRequirements();
        TransactionBody body = transaction.getBody();

        resolveFromInputs(body.getInputs(), utxoSlice, reqs);
        resolveFromInputs(body.getCollateral(), utxoSlice, reqs);
        resolveFromWithdrawals(body.getWithdrawals(), reqs);
        resolveFromCertificates(body.getCerts(), reqs);
        resolveFromRequiredSigners(body.getRequiredSigners(), reqs);
        resolveFromMint(body.getMint(), reqs);
        resolveFromVotingProcedures(body.getVotingProcedures(), reqs);

        return reqs;
    }

    /**
     * Extract payment credentials from input UTxO addresses.
     */
    private static void resolveFromInputs(List<TransactionInput> inputs,
                                           UtxoSlice utxoSlice,
                                           WitnessRequirements reqs) {
        if (inputs == null || utxoSlice == null) return;
        for (TransactionInput input : inputs) {
            utxoSlice.lookup(input).ifPresent(output -> {
                addPaymentCredential(output.getAddress(), reqs);
            });
        }
    }

    /**
     * Extract delegation credentials from withdrawal reward addresses.
     */
    private static void resolveFromWithdrawals(List<Withdrawal> withdrawals,
                                                WitnessRequirements reqs) {
        if (withdrawals == null) return;
        for (Withdrawal w : withdrawals) {
            if (w.getRewardAddress() == null) continue;
            try {
                Address addr = new Address(w.getRewardAddress());
                Optional<Credential> cred = AddressProvider.getDelegationCredential(addr);
                cred.ifPresent(reqs::addCredential);
            } catch (Exception e) {
                // Skip unparseable addresses
            }
        }
    }

    /**
     * Extract credentials from certificates.
     * Each cert type exposes its credential differently.
     */
    private static void resolveFromCertificates(List<Certificate> certs,
                                                 WitnessRequirements reqs) {
        if (certs == null) return;
        for (Certificate cert : certs) {
            // Stake certs with StakeCredential
            if (cert instanceof StakeRegistration c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof StakeDeregistration c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof StakeDelegation c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof RegCert c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof UnregCert c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof VoteDelegCert c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof StakeVoteDelegCert c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof StakeRegDelegCert c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof VoteRegDelegCert c) {
                reqs.addCredential(c.getStakeCredential());
            } else if (cert instanceof StakeVoteRegDelegCert c) {
                reqs.addCredential(c.getStakeCredential());

            // DRep certs with Credential
            } else if (cert instanceof RegDRepCert c) {
                reqs.addCredential(c.getDrepCredential());
            } else if (cert instanceof UnregDRepCert c) {
                reqs.addCredential(c.getDrepCredential());
            } else if (cert instanceof UpdateDRepCert c) {
                reqs.addCredential(c.getDrepCredential());

            // Committee certs with Credential
            } else if (cert instanceof AuthCommitteeHotCert c) {
                reqs.addCredential(c.getCommitteeColdCredential());
            } else if (cert instanceof ResignCommitteeColdCert c) {
                reqs.addCredential(c.getCommitteeColdCredential());

            // Pool certs with raw key hashes
            } else if (cert instanceof PoolRegistration c) {
                // Pool operator and all owners need VKey witnesses
                reqs.addVKeyHash(c.getOperator());
                if (c.getPoolOwners() != null) {
                    for (String ownerHash : c.getPoolOwners()) {
                        reqs.addVKeyHash(ownerHash);
                    }
                }
            } else if (cert instanceof PoolRetirement c) {
                reqs.addVKeyHash(c.getPoolKeyHash());
            }
        }
    }

    /**
     * Required signers are directly listed key hashes.
     */
    private static void resolveFromRequiredSigners(List<byte[]> requiredSigners,
                                                    WitnessRequirements reqs) {
        if (requiredSigners == null) return;
        for (byte[] keyHash : requiredSigners) {
            reqs.addVKeyHash(keyHash);
        }
    }

    /**
     * Mint policy IDs are script hashes.
     */
    private static void resolveFromMint(List<MultiAsset> mint, WitnessRequirements reqs) {
        if (mint == null) return;
        for (MultiAsset ma : mint) {
            if (ma.getPolicyId() != null) {
                reqs.addScriptHash(ma.getPolicyId());
            }
        }
    }

    /**
     * Extract voter credentials from voting procedures.
     */
    private static void resolveFromVotingProcedures(VotingProcedures votingProcedures,
                                                     WitnessRequirements reqs) {
        if (votingProcedures == null || votingProcedures.getVoting() == null) return;
        for (Voter voter : votingProcedures.getVoting().keySet()) {
            if (voter.getCredential() != null) {
                reqs.addCredential(voter.getCredential());
            }
        }
    }

    /**
     * Extract payment credential from a Shelley/Conway address and add to requirements.
     */
    private static void addPaymentCredential(String addressStr, WitnessRequirements reqs) {
        if (addressStr == null) return;
        try {
            Address addr = new Address(addressStr);
            Optional<Credential> cred = AddressProvider.getPaymentCredential(addr);
            cred.ifPresent(reqs::addCredential);
        } catch (Exception e) {
            // Byron address or unparseable — skip
        }
    }
}
