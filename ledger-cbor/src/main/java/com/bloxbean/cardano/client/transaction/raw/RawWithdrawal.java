package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An entry of the {@code withdrawals} map, {@code reward_account => coin}.
 *
 * @param rewardAccount the reward account's bytes, of any length
 * @param coin          the amount; null if the view was made without one
 */
public record RawWithdrawal(byte[] rewardAccount, BigInteger coin) {
    /** The {@code withdrawals} rule. */
    static final RecordSpec RECORD = RecordSpec.entries("withdrawals", FieldType.REWARD_ACCOUNT,
            FieldSpec.of(0, FieldType.COIN, false, Era.Shelley, null));

    /**
     * @param account the reward account as encoded
     * @param coin    the amount as encoded, or null for an account with no amount
     * @return the view
     * @throws CborRuntimeException if the account is not a byte string or the amount not an unsigned integer
     */
    public static RawWithdrawal of(CborSpan account, CborSpan coin) {
        return new RawWithdrawal(RawItems.bytes(account), coin != null ? RawItems.unsigned(coin) : null);
    }

    // One entry per reward account: see RawItems.distinct.
    static List<RawWithdrawal> read(CborSpan map) {
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.distinct(map, "withdrawals map");
        List<RawWithdrawal> withdrawals = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            withdrawals.add(of(entry.getKey(), entry.getValue()));
        return withdrawals;
    }
}
