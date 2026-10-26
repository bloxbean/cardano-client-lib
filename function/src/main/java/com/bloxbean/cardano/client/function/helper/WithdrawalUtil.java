package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class for sorting withdrawals in the order the Cardano ledger uses.
 * The ledger sorts withdrawals by reward account credential: script credentials come before
 * key credentials, then credentials of the same type are sorted by hash. Redeemer indices
 * must match positions in this sorted list.
 */
public class WithdrawalUtil {

    /**
     * Returns a comparator that sorts Withdrawals in ledger order: script credentials first,
     * then key credentials, and by credential hash within each type.
     *
     * @return Comparator for Withdrawal objects
     */
    public static Comparator<Withdrawal> getWithdrawalComparator() {
        Comparator<Credential> credentialComparator = Comparator
                .comparing((Credential credential) -> credential.getType() == CredentialType.Script ? 0 : 1)
                .thenComparing(credential -> HexUtil.encodeHexString(credential.getBytes()));

        return Comparator.comparing(w -> {
            Address addr = new Address(w.getRewardAddress());
            return addr.getDelegationCredential()
                    .orElseThrow(() -> new IllegalArgumentException("Unable to extract delegation credential from reward address: " + w.getRewardAddress()));
        }, credentialComparator);
    }

    /**
     * Returns a new list of withdrawals sorted in ledger order: script credentials before key credentials,
     * then by hash within each type, to ensure correct redeemer indices for script-based withdrawals.
     * This method is useful to get the final index of a Withdrawal in the list of Withdrawal in the transaction body.
     * @param withdrawals list of withdrawals
     * @return sorted list of Withdrawal
     */
    public static List<Withdrawal> getSortedWithdrawals(@NonNull List<Withdrawal> withdrawals) {
        return withdrawals.stream()
                .sorted(getWithdrawalComparator())
                .collect(Collectors.toList());
    }

    /**
     * Returns the index of the Withdrawal in the sorted list of Withdrawal
     * @param withdrawals list of Withdrawal (must be sorted)
     * @param rewardAddress reward address of the Withdrawal
     * @return index of the Withdrawal in the list of Withdrawal
     */
    public static int getIndexByRewardAddress(@NonNull List<Withdrawal> withdrawals, @NonNull String rewardAddress) {
        return IntStream.range(0, withdrawals.size())
                .filter(i -> rewardAddress.equals(withdrawals.get(i).getRewardAddress()))
                .findFirst()
                .orElse(-1);
    }

    /**
     * Returns the index of the Withdrawal in the sorted list of Withdrawal by stake key hash.
     * This is useful for finding the redeemer index for script-based withdrawals where you have
     * the script hash (which is the stake credential hash).
     * @param withdrawals list of Withdrawal (must be sorted)
     * @param stakeKeyHash hex-encoded stake key hash (script hash for script-based reward addresses)
     * @return index of the Withdrawal in the list of Withdrawal, or -1 if not found
     */
    public static int getIndexByStakeKeyHash(@NonNull List<Withdrawal> withdrawals, @NonNull String stakeKeyHash) {
        return IntStream.range(0, withdrawals.size())
                .filter(i -> {
                    Address addr = new Address(withdrawals.get(i).getRewardAddress());
                    String withdrawalStakeKeyHash = addr.getDelegationCredentialHash()
                            .map(HexUtil::encodeHexString)
                            .orElse("");
                    return stakeKeyHash.equals(withdrawalStakeKeyHash);
                })
                .findFirst()
                .orElse(-1);
    }
}
