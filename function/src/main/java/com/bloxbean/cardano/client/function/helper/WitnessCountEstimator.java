package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.AuthCommitteeHotCert;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.transaction.spec.cert.RegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.ResignCommitteeColdCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredType;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeVoteDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeVoteRegDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UnregDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.UpdateDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.VoteDelegCert;
import com.bloxbean.cardano.client.transaction.spec.cert.VoteRegDelegCert;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAny;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Estimates the number of vkey witnesses a transaction will need beyond those implied by its
 * inputs, by inspecting the transaction itself: certificates, withdrawals, votes, plan-level
 * required signers, and attached native scripts all demand witnesses that
 * {@code UtxoUtil.getNoOfRequiredSigners} (which only sees input UTXOs) cannot know about.
 *
 * <p><b>Spike / prototype</b> for
 * <a href="https://github.com/bloxbean/cardano-client-lib/issues/650">#650</a>: today this count
 * must be supplied by the caller via {@code additionalSignersCount}, which build-unsigned consumers
 * (FFI bindings, services) routinely get wrong — underestimation is rejected by the node with
 * {@code FeeTooSmallUTxO}. By fee-calculation time the body already contains the information, so
 * the library can count it itself.
 *
 * <p>Counting rules: one witness per distinct key-hash credential; script-hash credentials are
 * skipped (witnessed by scripts, not vkeys); native scripts contribute every {@code ScriptPubkey}
 * in their tree ({@code any}/{@code atLeast} therefore over-count — overpaying is safe,
 * underpaying is a rejection). Credentials that coincide with an input's payment key are counted
 * anyway (again: safe direction). Scripts referenced via reference inputs are invisible here and
 * remain the caller's responsibility.
 */
public final class WitnessCountEstimator {

    private WitnessCountEstimator() {
    }

    /**
     * Number of distinct key-hash credentials the transaction's body and native scripts require as
     * witnesses, beyond the input payment keys.
     */
    public static int countBodyImpliedSigners(Transaction transaction) {
        if (transaction == null || transaction.getBody() == null)
            return 0;

        Set<String> keyHashes = new HashSet<>();
        TransactionBody body = transaction.getBody();

        if (body.getCerts() != null) {
            for (Certificate cert : body.getCerts()) {
                addCertificateKeyHashes(cert, keyHashes);
            }
        }

        if (body.getWithdrawals() != null) {
            for (Withdrawal withdrawal : body.getWithdrawals()) {
                addRewardAddressKeyHash(withdrawal.getRewardAddress(), keyHashes);
            }
        }

        if (body.getRequiredSigners() != null) {
            for (byte[] requiredSigner : body.getRequiredSigners()) {
                if (requiredSigner != null)
                    keyHashes.add(HexUtil.encodeHexString(requiredSigner));
            }
        }

        if (body.getVotingProcedures() != null && body.getVotingProcedures().getVoting() != null) {
            for (Voter voter : body.getVotingProcedures().getVoting().keySet()) {
                addVoterKeyHash(voter, keyHashes);
            }
        }

        if (transaction.getWitnessSet() != null && transaction.getWitnessSet().getNativeScripts() != null) {
            for (NativeScript nativeScript : transaction.getWitnessSet().getNativeScripts()) {
                addNativeScriptKeyHashes(nativeScript, keyHashes);
            }
        }

        return keyHashes.size();
    }

    private static void addCertificateKeyHashes(Certificate cert, Set<String> keyHashes) {
        if (cert instanceof StakeRegistration stakeRegistration) {
            // Pre-Conway stake registration needs no witness, but budgeting one is the safe
            // direction and Conway-era RegCert (below) does need it.
            addStakeCredential(stakeRegistration.getStakeCredential(), keyHashes);
        } else if (cert instanceof StakeDeregistration stakeDeregistration) {
            addStakeCredential(stakeDeregistration.getStakeCredential(), keyHashes);
        } else if (cert instanceof StakeDelegation stakeDelegation) {
            addStakeCredential(stakeDelegation.getStakeCredential(), keyHashes);
        } else if (cert instanceof RegCert regCert) {
            addStakeCredential(regCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof UnregCert unregCert) {
            addStakeCredential(unregCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof VoteDelegCert voteDelegCert) {
            addStakeCredential(voteDelegCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof StakeVoteDelegCert stakeVoteDelegCert) {
            addStakeCredential(stakeVoteDelegCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof StakeRegDelegCert stakeRegDelegCert) {
            addStakeCredential(stakeRegDelegCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof VoteRegDelegCert voteRegDelegCert) {
            addStakeCredential(voteRegDelegCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof StakeVoteRegDelegCert stakeVoteRegDelegCert) {
            addStakeCredential(stakeVoteRegDelegCert.getStakeCredential(), keyHashes);
        } else if (cert instanceof RegDRepCert regDRepCert) {
            addCredential(regDRepCert.getDrepCredential(), keyHashes);
        } else if (cert instanceof UnregDRepCert unregDRepCert) {
            addCredential(unregDRepCert.getDrepCredential(), keyHashes);
        } else if (cert instanceof UpdateDRepCert updateDRepCert) {
            addCredential(updateDRepCert.getDrepCredential(), keyHashes);
        } else if (cert instanceof AuthCommitteeHotCert authCommitteeHotCert) {
            addCredential(authCommitteeHotCert.getCommitteeColdCredential(), keyHashes);
        } else if (cert instanceof ResignCommitteeColdCert resignCommitteeColdCert) {
            addCredential(resignCommitteeColdCert.getCommitteeColdCredential(), keyHashes);
        } else if (cert instanceof PoolRegistration poolRegistration) {
            if (poolRegistration.getOperator() != null)
                keyHashes.add(HexUtil.encodeHexString(poolRegistration.getOperator()));
            // Pool owners must also witness; kept out of the spike for discussion (they live in
            // poolOwners as stake key hashes).
        } else if (cert instanceof PoolRetirement poolRetirement) {
            if (poolRetirement.getPoolKeyHash() != null)
                keyHashes.add(HexUtil.encodeHexString(poolRetirement.getPoolKeyHash()));
        }
        // GenesisKeyDelegation / MoveInstataneous: legacy, out of scope.
    }

    private static void addStakeCredential(StakeCredential credential, Set<String> keyHashes) {
        if (credential != null && credential.getType() == StakeCredType.ADDR_KEYHASH
                && credential.getHash() != null) {
            keyHashes.add(HexUtil.encodeHexString(credential.getHash()));
        }
    }

    private static void addCredential(Credential credential, Set<String> keyHashes) {
        if (credential != null && credential.getType() == CredentialType.Key
                && credential.getBytes() != null) {
            keyHashes.add(HexUtil.encodeHexString(credential.getBytes()));
        }
    }

    private static void addRewardAddressKeyHash(String rewardAddress, Set<String> keyHashes) {
        if (rewardAddress == null || rewardAddress.isEmpty())
            return;
        try {
            new Address(rewardAddress).getDelegationCredential()
                    .filter(credential -> credential.getType() == CredentialType.Key)
                    .ifPresent(credential -> keyHashes.add(HexUtil.encodeHexString(credential.getBytes())));
        } catch (Exception e) {
            // Unparseable address: leave it unbudgeted; the build surfaces the real error.
        }
    }

    private static void addVoterKeyHash(Voter voter, Set<String> keyHashes) {
        if (voter == null || voter.getCredential() == null)
            return;
        VoterType type = voter.getType();
        if (type == VoterType.DREP_KEY_HASH
                || type == VoterType.CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH
                || type == VoterType.STAKING_POOL_KEY_HASH) {
            keyHashes.add(HexUtil.encodeHexString(voter.getCredential().getBytes()));
        }
    }

    private static void addNativeScriptKeyHashes(NativeScript script, Set<String> keyHashes) {
        if (script instanceof ScriptPubkey scriptPubkey) {
            if (scriptPubkey.getKeyHash() != null)
                keyHashes.add(scriptPubkey.getKeyHash());
        } else if (script instanceof ScriptAll scriptAll) {
            scriptAll.getScripts().forEach(s -> addNativeScriptKeyHashes(s, keyHashes));
        } else if (script instanceof ScriptAny scriptAny) {
            scriptAny.getScripts().forEach(s -> addNativeScriptKeyHashes(s, keyHashes));
        } else if (script instanceof ScriptAtLeast scriptAtLeast) {
            scriptAtLeast.getScripts().forEach(s -> addNativeScriptKeyHashes(s, keyHashes));
        }
        // RequireTimeBefore / RequireTimeAfter carry no keys.
    }
}
