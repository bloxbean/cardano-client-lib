package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
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

        return Comparator.comparing(withdrawal -> {
            Address address = new Address(withdrawal.getRewardAddress());
            return AddressProvider.getDelegationCredential(address)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No delegation credential found for address: " + withdrawal.getRewardAddress()));
        }, credentialComparator);
    }

    /**
     * Returns a new list of withdrawals sorted in ledger order.
     *
     * @param withdrawals list of withdrawals to sort
     * @return sorted copy of the withdrawal list
     */
    public static List<Withdrawal> getSortedWithdrawals(@NonNull List<Withdrawal> withdrawals) {
        return withdrawals.stream()
                .sorted(getWithdrawalComparator())
                .collect(Collectors.toList());
    }

    /**
     * Returns the index of a withdrawal in the list by its reward address.
     *
     * @param withdrawals list of withdrawals (should be sorted)
     * @param rewardAddress bech32 reward address to find
     * @return index of the withdrawal, or -1 if not found
     */
    public static int getIndexByRewardAddress(@NonNull List<Withdrawal> withdrawals, @NonNull String rewardAddress) {
        return IntStream.range(0, withdrawals.size())
                .filter(i -> rewardAddress.equals(withdrawals.get(i).getRewardAddress()))
                .findFirst()
                .orElse(-1);
    }

    /**
     * Returns the index of a withdrawal in the list by its stake key hash (delegation credential hash).
     *
     * @param withdrawals list of withdrawals (should be sorted)
     * @param stakeKeyHash hex-encoded stake key hash to find
     * @return index of the withdrawal, or -1 if not found
     */
    public static int getIndexByStakeKeyHash(@NonNull List<Withdrawal> withdrawals, @NonNull String stakeKeyHash) {
        return IntStream.range(0, withdrawals.size())
                .filter(i -> {
                    Address address = new Address(withdrawals.get(i).getRewardAddress());
                    String hash = HexUtil.encodeHexString(
                            AddressProvider.getDelegationCredentialHash(address)
                                    .orElse(new byte[0])
                    );
                    return stakeKeyHash.equals(hash);
                })
                .findFirst()
                .orElse(-1);
    }
}
