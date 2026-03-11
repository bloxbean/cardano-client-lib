package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalUtilTest {

    // Create reward addresses from known stake key hashes for deterministic testing
    private String createRewardAddress(String stakeKeyHash) {
        Address rewardAddr = AddressProvider.getRewardAddress(
                Credential.fromKey(HexUtil.decodeHexString(stakeKeyHash)),
                Networks.testnet()
        );
        return rewardAddr.toBech32();
    }

    @Test
    void getSortedWithdrawals_multipleWithdrawals_sortedByCredentialHash() {
        // Use stake key hashes that are NOT in alphabetical order
        String hash1 = "aa" + "00".repeat(27);  // aa0000...
        String hash2 = "bb" + "00".repeat(27);  // bb0000...
        String hash3 = "11" + "00".repeat(27);  // 110000...

        String addr1 = createRewardAddress(hash1);
        String addr2 = createRewardAddress(hash2);
        String addr3 = createRewardAddress(hash3);

        // Insert in non-sorted order: bb, aa, 11
        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(addr2, BigInteger.valueOf(200)));
        withdrawals.add(new Withdrawal(addr1, BigInteger.valueOf(100)));
        withdrawals.add(new Withdrawal(addr3, BigInteger.valueOf(300)));

        List<Withdrawal> sorted = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        // Expected order: 11, aa, bb
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).getRewardAddress()).isEqualTo(addr3); // 11...
        assertThat(sorted.get(1).getRewardAddress()).isEqualTo(addr1); // aa...
        assertThat(sorted.get(2).getRewardAddress()).isEqualTo(addr2); // bb...
    }

    @Test
    void getSortedWithdrawals_singleWithdrawal_returnsSameElement() {
        String hash = "ab" + "00".repeat(27);
        String addr = createRewardAddress(hash);

        List<Withdrawal> withdrawals = List.of(new Withdrawal(addr, BigInteger.valueOf(100)));

        List<Withdrawal> sorted = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        assertThat(sorted).hasSize(1);
        assertThat(sorted.get(0).getRewardAddress()).isEqualTo(addr);
    }

    @Test
    void getSortedWithdrawals_emptyList_returnsEmpty() {
        List<Withdrawal> sorted = WithdrawalUtil.getSortedWithdrawals(Collections.emptyList());
        assertThat(sorted).isEmpty();
    }

    @Test
    void getSortedWithdrawals_doesNotMutateOriginal() {
        String hash1 = "cc" + "00".repeat(27);
        String hash2 = "11" + "00".repeat(27);

        String addr1 = createRewardAddress(hash1);
        String addr2 = createRewardAddress(hash2);

        List<Withdrawal> original = new ArrayList<>();
        original.add(new Withdrawal(addr1, BigInteger.valueOf(100)));
        original.add(new Withdrawal(addr2, BigInteger.valueOf(200)));

        List<Withdrawal> sorted = WithdrawalUtil.getSortedWithdrawals(original);

        // Original order should be preserved
        assertThat(original.get(0).getRewardAddress()).isEqualTo(addr1);
        assertThat(original.get(1).getRewardAddress()).isEqualTo(addr2);
        // Sorted order should be different
        assertThat(sorted.get(0).getRewardAddress()).isEqualTo(addr2); // 11...
        assertThat(sorted.get(1).getRewardAddress()).isEqualTo(addr1); // cc...
    }

    @Test
    void getIndexByRewardAddress_found() {
        String hash1 = "11" + "00".repeat(27);
        String hash2 = "aa" + "00".repeat(27);
        String hash3 = "ff" + "00".repeat(27);

        String addr1 = createRewardAddress(hash1);
        String addr2 = createRewardAddress(hash2);
        String addr3 = createRewardAddress(hash3);

        List<Withdrawal> withdrawals = List.of(
                new Withdrawal(addr1, BigInteger.valueOf(100)),
                new Withdrawal(addr2, BigInteger.valueOf(200)),
                new Withdrawal(addr3, BigInteger.valueOf(300))
        );

        assertThat(WithdrawalUtil.getIndexByRewardAddress(withdrawals, addr1)).isEqualTo(0);
        assertThat(WithdrawalUtil.getIndexByRewardAddress(withdrawals, addr2)).isEqualTo(1);
        assertThat(WithdrawalUtil.getIndexByRewardAddress(withdrawals, addr3)).isEqualTo(2);
    }

    @Test
    void getIndexByRewardAddress_notFound() {
        String hash1 = "11" + "00".repeat(27);
        String hashMissing = "99" + "00".repeat(27);

        String addr1 = createRewardAddress(hash1);
        String addrMissing = createRewardAddress(hashMissing);

        List<Withdrawal> withdrawals = List.of(
                new Withdrawal(addr1, BigInteger.valueOf(100))
        );

        assertThat(WithdrawalUtil.getIndexByRewardAddress(withdrawals, addrMissing)).isEqualTo(-1);
    }

    @Test
    void getIndexByStakeKeyHash_found() {
        String hash1 = "11" + "00".repeat(27);
        String hash2 = "aa" + "00".repeat(27);

        String addr1 = createRewardAddress(hash1);
        String addr2 = createRewardAddress(hash2);

        List<Withdrawal> withdrawals = List.of(
                new Withdrawal(addr1, BigInteger.valueOf(100)),
                new Withdrawal(addr2, BigInteger.valueOf(200))
        );

        assertThat(WithdrawalUtil.getIndexByStakeKeyHash(withdrawals, hash1)).isEqualTo(0);
        assertThat(WithdrawalUtil.getIndexByStakeKeyHash(withdrawals, hash2)).isEqualTo(1);
    }

    @Test
    void getIndexByStakeKeyHash_notFound() {
        String hash1 = "11" + "00".repeat(27);
        String hashMissing = "99" + "00".repeat(27);

        String addr1 = createRewardAddress(hash1);

        List<Withdrawal> withdrawals = List.of(
                new Withdrawal(addr1, BigInteger.valueOf(100))
        );

        assertThat(WithdrawalUtil.getIndexByStakeKeyHash(withdrawals, hashMissing)).isEqualTo(-1);
    }

    @Test
    void getWithdrawalComparator_sortsCorrectly() {
        String hash1 = "dd" + "00".repeat(27);
        String hash2 = "22" + "00".repeat(27);

        String addr1 = createRewardAddress(hash1);
        String addr2 = createRewardAddress(hash2);

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(addr1, BigInteger.valueOf(100)));
        withdrawals.add(new Withdrawal(addr2, BigInteger.valueOf(200)));

        withdrawals.sort(WithdrawalUtil.getWithdrawalComparator());

        assertThat(withdrawals.get(0).getRewardAddress()).isEqualTo(addr2); // 22...
        assertThat(withdrawals.get(1).getRewardAddress()).isEqualTo(addr1); // dd...
    }
}
