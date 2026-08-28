package com.bloxbean.cardano.client.cip.cip113;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The three canonical orderings a CIP-113 redeemer indexes into.
 *
 * <p>All of them differ from the order CCL appends things in, so every index carried by a
 * CIP-113 redeemer has to be computed against these lists rather than against insertion
 * order. Each lookup throws rather than returning {@code -1}: a silently wrong index
 * produces a script failure at submit time with no useful message, whereas a thrown
 * exception naming the missing element is debuggable.</p>
 */
public final class LedgerOrdering {

    private LedgerOrdering() {}

    // ---------------------------------------------------------------- inputs

    /** Inputs as the ledger orders them: by (transaction id, index). */
    public static List<TransactionInput> sortedInputs(Transaction txn) {
        return sortInputs(txn.getBody().getInputs());
    }

    /**
     * Reference inputs as the ledger orders them.
     *
     * <p>CCL has no equivalent of {@code RedeemerUtil.getSortedInputs} for reference
     * inputs — {@code ReferenceInputIntent} appends them in call order and never sorts —
     * so this is the only way to resolve a {@code params_idx} or {@code node_idx}.</p>
     */
    public static List<TransactionInput> sortedReferenceInputs(Transaction txn) {
        return sortInputs(txn.getBody().getReferenceInputs());
    }

    private static List<TransactionInput> sortInputs(List<TransactionInput> inputs) {
        List<TransactionInput> copy = inputs == null ? new ArrayList<>() : new ArrayList<>(inputs);
        copy.sort(Comparator.comparing(TransactionInput::getTransactionId)
                .thenComparing(TransactionInput::getIndex));
        return copy;
    }

    public static int indexOfInput(Transaction txn, String txHash, int outputIndex) {
        return require(indexOf(sortedInputs(txn), txHash, outputIndex),
                "input " + txHash + "#" + outputIndex);
    }

    public static int indexOfReferenceInput(Transaction txn, String txHash, int outputIndex) {
        return require(indexOf(sortedReferenceInputs(txn), txHash, outputIndex),
                "reference input " + txHash + "#" + outputIndex);
    }

    private static int indexOf(List<TransactionInput> sorted, String txHash, int outputIndex) {
        for (int i = 0; i < sorted.size(); i++) {
            TransactionInput in = sorted.get(i);
            if (in.getTransactionId().equals(txHash) && in.getIndex() == outputIndex) return i;
        }
        return -1;
    }

    // ----------------------------------------------------------- withdrawals

    /**
     * Withdrawals as the ledger orders them: <b>every script credential before every key
     * credential</b>, then bytewise by hash within each kind.
     *
     * <p>The ledger keys withdrawals by {@code RewardAccount = (network, credential)} and
     * cardano-ledger's {@code Credential} derives {@code Ord} with {@code ScriptHashObj}
     * declared before {@code KeyHashObj}. CCL's {@code WithdrawalUtil} comparator sorts by
     * credential hash alone, ignoring the kind — so a transaction mixing a withdraw-zero
     * script withdrawal with a real reward withdrawal can get a wrong {@code Reward}
     * redeemer index from it.</p>
     *
     * <p><b>Verified empirically against a Conway ledger</b> (Yaci DevKit devnet,
     * {@code Cip113EndToEndIT.step11_withdrawalOrderingWithMixedCredentials}): a transfer carrying
     * two script withdrawals and one key withdrawal whose hash sorts <i>before</i> both script
     * hashes validated on chain. Hash-only ordering would have placed that key withdrawal at index
     * 0 and shifted both script redeemers by one, so the transaction could only succeed if the
     * ledger really does sort script credentials first. It did. That also confirms
     * {@code WithdrawalUtil}'s comparator is wrong for mixed-credential withdrawals — worth
     * proposing upstream. This class deliberately does not depend on it.</p>
     */
    public static List<Withdrawal> sortedWithdrawals(Transaction txn) {
        List<Withdrawal> copy = txn.getBody().getWithdrawals() == null
                ? new ArrayList<>() : new ArrayList<>(txn.getBody().getWithdrawals());
        copy.sort(withdrawalComparator());
        return copy;
    }

    public static Comparator<Withdrawal> withdrawalComparator() {
        return (a, b) -> {
            Credential ca = credentialOf(a.getRewardAddress());
            Credential cb = credentialOf(b.getRewardAddress());
            boolean scriptA = ca.getType() == CredentialType.Script;
            boolean scriptB = cb.getType() == CredentialType.Script;
            if (scriptA != scriptB) return scriptA ? -1 : 1;      // scripts first
            return PolicyOrdering.compare(ca.getBytes(), cb.getBytes());
        };
    }

    public static int indexOfWithdrawal(Transaction txn, Credential credential) {
        List<Withdrawal> sorted = sortedWithdrawals(txn);
        for (int i = 0; i < sorted.size(); i++) {
            Credential c = credentialOf(sorted.get(i).getRewardAddress());
            if (c.getType() == credential.getType()
                    && HexUtil.encodeHexString(c.getBytes()).equals(HexUtil.encodeHexString(credential.getBytes()))) {
                return i;
            }
        }
        throw new Cip113Exception("Withdrawal not found for credential "
                + HexUtil.encodeHexString(credential.getBytes())
                + ". Present: " + describe(sorted));
    }

    private static Credential credentialOf(String rewardAddressBech32) {
        Address address = new Address(rewardAddressBech32);
        return AddressProvider.getDelegationCredential(address)
                .orElseThrow(() -> new Cip113Exception(
                        "No delegation credential in reward address " + rewardAddressBech32));
    }

    private static String describe(List<Withdrawal> sorted) {
        StringBuilder sb = new StringBuilder();
        for (Withdrawal w : sorted) sb.append(w.getRewardAddress()).append(' ');
        return sb.toString().trim();
    }

    private static int require(int index, String what) {
        if (index < 0) {
            throw new Cip113Exception("Could not resolve " + what
                    + " in the transaction's canonical ordering. The element was never added,"
                    + " or balancing changed the transaction after it was resolved.");
        }
        return index;
    }
}
