package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalUtilTest {

    @Test
    void testGetWithdrawalComparator() {
        // Create multiple script stake addresses with different hashes
        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1";
        PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1";
        PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290030a4c2c6eb40095cd1";
        PlutusScript plutusScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        // Create reward addresses from these scripts
        String scriptStakeAddress1 = AddressProvider.getRewardAddress(plutusScript1, Networks.testnet()).toBech32();
        String scriptStakeAddress2 = AddressProvider.getRewardAddress(plutusScript2, Networks.testnet()).toBech32();
        String scriptStakeAddress3 = AddressProvider.getRewardAddress(plutusScript3, Networks.testnet()).toBech32();

        // Create withdrawals in random order
        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(scriptStakeAddress2, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress3, BigInteger.ZERO));

        // Get the comparator
        Comparator<Withdrawal> comparator = WithdrawalUtil.getWithdrawalComparator();

        // Sort using the comparator
        withdrawals.sort(comparator);

        // Verify the withdrawals are sorted by stake key hash
        assertThat(withdrawals).hasSize(3);

        // Extract stake key hashes and verify they are in lexicographic order
        List<String> stakeKeyHashes = new ArrayList<>();
        for (Withdrawal w : withdrawals) {
            Address addr = new Address(w.getRewardAddress());
            String skh = HexUtil.encodeHexString(addr.getDelegationCredentialHash().get());
            stakeKeyHashes.add(skh);
        }

        List<String> sortedStakeKeyHashes = new ArrayList<>(stakeKeyHashes);
        Collections.sort(sortedStakeKeyHashes);

        assertThat(stakeKeyHashes).isEqualTo(sortedStakeKeyHashes);
    }

    @Test
    void testGetSortedWithdrawals_withMultipleWithdrawals() {
        // Create multiple script stake addresses with different hashes
        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1";
        PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1";
        PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290030a4c2c6eb40095cd1";
        PlutusScript plutusScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        // Create reward addresses from these scripts
        String scriptStakeAddress1 = AddressProvider.getRewardAddress(plutusScript1, Networks.testnet()).toBech32();
        String scriptStakeAddress2 = AddressProvider.getRewardAddress(plutusScript2, Networks.testnet()).toBech32();
        String scriptStakeAddress3 = AddressProvider.getRewardAddress(plutusScript3, Networks.testnet()).toBech32();

        // Create withdrawals in random order
        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(scriptStakeAddress2, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress3, BigInteger.ZERO));

        // Sort withdrawals
        List<Withdrawal> sortedWithdrawals = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        // Verify the withdrawals are sorted by stake key hash
        assertThat(sortedWithdrawals).hasSize(3);

        // Extract stake key hashes and verify they are in lexicographic order
        List<String> stakeKeyHashes = new ArrayList<>();
        for (Withdrawal w : sortedWithdrawals) {
            Address addr = new Address(w.getRewardAddress());
            String skh = HexUtil.encodeHexString(addr.getDelegationCredentialHash().get());
            stakeKeyHashes.add(skh);
        }

        List<String> sortedStakeKeyHashes = new ArrayList<>(stakeKeyHashes);
        Collections.sort(sortedStakeKeyHashes);

        assertThat(stakeKeyHashes).isEqualTo(sortedStakeKeyHashes);
    }

    @Test
    void testGetSortedWithdrawals_withSingleWithdrawal() {
        Account account = new Account(Networks.testnet());
        String rewardAddress = account.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(rewardAddress, BigInteger.valueOf(1000)));

        List<Withdrawal> sortedWithdrawals = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        assertThat(sortedWithdrawals).hasSize(1);
        assertThat(sortedWithdrawals.get(0).getRewardAddress()).isEqualTo(rewardAddress);
        assertThat(sortedWithdrawals.get(0).getCoin()).isEqualTo(BigInteger.valueOf(1000));
    }

    @Test
    void testGetSortedWithdrawals_emptyList() {
        List<Withdrawal> withdrawals = new ArrayList<>();
        List<Withdrawal> sortedWithdrawals = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        assertThat(sortedWithdrawals).isEmpty();
    }

    @Test
    void testGetSortedWithdrawals_doesNotModifyOriginalList() {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        String rewardAddress1 = account1.stakeAddress();
        String rewardAddress2 = account2.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        Withdrawal w1 = new Withdrawal(rewardAddress1, BigInteger.ZERO);
        Withdrawal w2 = new Withdrawal(rewardAddress2, BigInteger.ZERO);
        withdrawals.add(w2);
        withdrawals.add(w1);

        // Store original order
        String originalFirstAddress = withdrawals.get(0).getRewardAddress();

        // Sort
        List<Withdrawal> sortedWithdrawals = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        // Verify original list is not modified
        assertThat(withdrawals.get(0).getRewardAddress()).isEqualTo(originalFirstAddress);
        assertThat(withdrawals).hasSize(2);
    }

    @Test
    void testGetIndexByRewardAddress_found() {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());
        Account account3 = new Account(Networks.testnet());

        String rewardAddress1 = account1.stakeAddress();
        String rewardAddress2 = account2.stakeAddress();
        String rewardAddress3 = account3.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(rewardAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(rewardAddress2, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(rewardAddress3, BigInteger.ZERO));

        int index = WithdrawalUtil.getIndexByRewardAddress(withdrawals, rewardAddress2);

        assertThat(index).isEqualTo(1);
    }

    @Test
    void testGetIndexByRewardAddress_notFound() {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        String rewardAddress1 = account1.stakeAddress();
        String rewardAddress2 = account2.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(rewardAddress1, BigInteger.ZERO));

        int index = WithdrawalUtil.getIndexByRewardAddress(withdrawals, rewardAddress2);

        assertThat(index).isEqualTo(-1);
    }

    @Test
    void testGetIndexByRewardAddress_firstElement() {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());

        String rewardAddress1 = account1.stakeAddress();
        String rewardAddress2 = account2.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(rewardAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(rewardAddress2, BigInteger.ZERO));

        int index = WithdrawalUtil.getIndexByRewardAddress(withdrawals, rewardAddress1);

        assertThat(index).isEqualTo(0);
    }

    @Test
    void testGetIndexByRewardAddress_lastElement() {
        Account account1 = new Account(Networks.testnet());
        Account account2 = new Account(Networks.testnet());
        Account account3 = new Account(Networks.testnet());

        String rewardAddress1 = account1.stakeAddress();
        String rewardAddress2 = account2.stakeAddress();
        String rewardAddress3 = account3.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(rewardAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(rewardAddress2, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(rewardAddress3, BigInteger.ZERO));

        int index = WithdrawalUtil.getIndexByRewardAddress(withdrawals, rewardAddress3);

        assertThat(index).isEqualTo(2);
    }

    @Test
    void testGetIndexByStakeKeyHash_found() throws CborSerializationException {
        // Create multiple script stake addresses with different hashes
        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1";
        PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1";
        PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

        String aikenCompileCode3 = "581801000032223253330043370e00290030a4c2c6eb40095cd1";
        PlutusScript plutusScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);

        // Create reward addresses from these scripts
        String scriptStakeAddress1 = AddressProvider.getRewardAddress(plutusScript1, Networks.testnet()).toBech32();
        String scriptStakeAddress2 = AddressProvider.getRewardAddress(plutusScript2, Networks.testnet()).toBech32();
        String scriptStakeAddress3 = AddressProvider.getRewardAddress(plutusScript3, Networks.testnet()).toBech32();

        // Create and sort withdrawals
        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(scriptStakeAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress2, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress3, BigInteger.ZERO));

        List<Withdrawal> sortedWithdrawals = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        // Get the stake key hash for script 2 and find its index
        String scriptHash2 = HexUtil.encodeHexString(plutusScript2.getScriptHash());
        int index = WithdrawalUtil.getIndexByStakeKeyHash(sortedWithdrawals, scriptHash2);

        // Verify the index is correct
        assertThat(index).isNotEqualTo(-1);
        assertThat(sortedWithdrawals.get(index).getRewardAddress()).isEqualTo(scriptStakeAddress2);

        // Verify the stake key hash matches
        Address addr = new Address(sortedWithdrawals.get(index).getRewardAddress());
        String stakeKeyHash = HexUtil.encodeHexString(addr.getDelegationCredentialHash().get());
        assertThat(stakeKeyHash).isEqualTo(scriptHash2);
    }

    @Test
    void testGetIndexByStakeKeyHash_notFound() {
        Account account = new Account(Networks.testnet());
        String rewardAddress = account.stakeAddress();

        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(rewardAddress, BigInteger.ZERO));

        // Try to find a non-existent stake key hash
        int index = WithdrawalUtil.getIndexByStakeKeyHash(withdrawals, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

        assertThat(index).isEqualTo(-1);
    }

    @Test
    void testGetIndexByStakeKeyHash_multipleWithdrawals() throws CborSerializationException {
        // Create multiple script stake addresses
        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1";
        PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);
        //Script Hash: d03d3ceb2311d77292ec5473128a88c72f50c6a1aa5523ebef31aa97

        String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1";
        PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);
        //Script Hash: 749482b2fe4ac715bdeadc67db1f42600483ebb1913fa80a26411a63

        String aikenCompileCode3 = "581801000032223253330043370e00290030a4c2c6eb40095cd1";
        PlutusScript plutusScript3 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode3, PlutusVersion.v2);
        //Script Hash: c0c358fb55489fc40ccf24f1c54bb2ed1c937267d2ac724b6f5804b3

        // Create reward addresses
        String scriptStakeAddress1 = AddressProvider.getRewardAddress(plutusScript1, Networks.testnet()).toBech32();
        String scriptStakeAddress2 = AddressProvider.getRewardAddress(plutusScript2, Networks.testnet()).toBech32();
        String scriptStakeAddress3 = AddressProvider.getRewardAddress(plutusScript3, Networks.testnet()).toBech32();

        System.out.println("Script Hash1: " + HexUtil.encodeHexString(plutusScript1.getScriptHash()));
        System.out.println("Script Hash2: " + HexUtil.encodeHexString(plutusScript2.getScriptHash()));
        System.out.println("Script Hash3: " + HexUtil.encodeHexString(plutusScript3.getScriptHash()));

        // Create withdrawals in random order and sort
        List<Withdrawal> withdrawals = new ArrayList<>();
        withdrawals.add(new Withdrawal(scriptStakeAddress3, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress1, BigInteger.ZERO));
        withdrawals.add(new Withdrawal(scriptStakeAddress2, BigInteger.ZERO));

        List<Withdrawal> sortedWithdrawals = WithdrawalUtil.getSortedWithdrawals(withdrawals);

        // Find each script's index
        String scriptHash1 = HexUtil.encodeHexString(plutusScript1.getScriptHash());
        String scriptHash2 = HexUtil.encodeHexString(plutusScript2.getScriptHash());
        String scriptHash3 = HexUtil.encodeHexString(plutusScript3.getScriptHash());

        int index1 = WithdrawalUtil.getIndexByStakeKeyHash(sortedWithdrawals, scriptHash1);
        int index2 = WithdrawalUtil.getIndexByStakeKeyHash(sortedWithdrawals, scriptHash2);
        int index3 = WithdrawalUtil.getIndexByStakeKeyHash(sortedWithdrawals, scriptHash3);

        // All indices should be unique and in range 0-2
        assertThat(index1).isBetween(0, 2);
        assertThat(index2).isBetween(0, 2);
        assertThat(index3).isBetween(0, 2);
        assertThat(List.of(index1, index2, index3)).containsExactlyInAnyOrder(0, 1, 2);

        assertThat(sortedWithdrawals.get(0).getRewardAddress()).isEqualTo(scriptStakeAddress2);
        assertThat(sortedWithdrawals.get(1).getRewardAddress()).isEqualTo(scriptStakeAddress3);
        assertThat(sortedWithdrawals.get(2).getRewardAddress()).isEqualTo(scriptStakeAddress1);

        // Verify each index points to the correct withdrawal
        assertThat(sortedWithdrawals.get(index1).getRewardAddress()).isEqualTo(scriptStakeAddress1);
        assertThat(sortedWithdrawals.get(index2).getRewardAddress()).isEqualTo(scriptStakeAddress2);
        assertThat(sortedWithdrawals.get(index3).getRewardAddress()).isEqualTo(scriptStakeAddress3);
    }
}
