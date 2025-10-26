package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WithdrawalUtil {

    /**
     * Returns a comparator that compares Withdrawals by their stake key hash in lexicographic order.
     * This comparator can be used to sort withdrawals to ensure correct redeemer indices for script-based withdrawals.
     * @return Comparator for Withdrawal objects
     */
    public static Comparator<Withdrawal> getWithdrawalComparator() {
        return Comparator.comparing(w -> {
            Address addr = new Address(w.getRewardAddress());
            return addr.getDelegationCredentialHash()
                    .map(HexUtil::encodeHexString)
                    .orElseThrow(() -> new IllegalArgumentException("Unable to extract stake key hash from reward address: " + w.getRewardAddress()));
        });
    }

    /**
     * Sorts the list of Withdrawal by stake key hash. The withdrawals list in transaction body is sorted by the
     * stake key hash of the reward address to ensure correct redeemer indices for script-based withdrawals.
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
