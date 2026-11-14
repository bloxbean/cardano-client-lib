package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An entry of a Dijkstra transaction body's {@code account_balance_intervals} or
 * {@code starting_account_balance_intervals}, {@code reward_account => account_balance_interval}: the balance the
 * account must have, exactly ({@code coin}) or within bounds ({@code [inclusive_lower / nil, exclusive_upper / nil]}).
 * Both bounds null is read: rejecting it is policy (ADR 0003 D5).
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 *
 * @param rewardAccount the reward account's bytes, of any length
 * @param exact         the exact balance, or null for the bounds form
 * @param lower         the inclusive lower bound, or null if there is none or the balance is exact
 * @param upper         the exclusive upper bound, or null if there is none or the balance is exact
 */
public record RawAccountBalanceInterval(byte[] rewardAccount, BigInteger exact, BigInteger lower, BigInteger upper) {
    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_ARRAY = 4;

    /** The bounds form, {@code [coin / nil, coin / nil]}. */
    static final RecordSpec BOUNDS = RecordSpec.positional("account_balance_bounds", FieldType.OPTIONAL_COIN,
            FieldType.OPTIONAL_COIN);
    /** The {@code account_balance_interval} rule: a coin or the bounds. */
    static final RecordSpec RECORD = RecordSpec.byMajorType("account_balance_interval",
            FieldSpec.of(MAJOR_UNSIGNED, FieldType.COIN, false, Era.Dijkstra, null),
            FieldSpec.of(MAJOR_ARRAY, FieldType.ACCOUNT_BALANCE_INTERVAL, false, Era.Dijkstra, null, BOUNDS));
    /** The {@code account_balance_intervals} rule, a map of reward accounts to intervals. */
    static final RecordSpec MAP = RecordSpec.entries("account_balance_intervals", FieldType.REWARD_ACCOUNT,
            FieldSpec.of(0, FieldType.ACCOUNT_BALANCE_INTERVAL, false, Era.Dijkstra, null));

    // Every entry in encoded order.
    static List<RawAccountBalanceInterval> read(CborSpan map) {
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.map(map, MAP.name());
        List<RawAccountBalanceInterval> intervals = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            intervals.add(of(entry.getKey(), entry.getValue()));
        return intervals;
    }

    /**
     * @throws CborRuntimeException if the account is not a byte string, or the interval neither an unsigned integer
     *                              nor an array of two that are each one or null
     */
    private static RawAccountBalanceInterval of(CborSpan account, CborSpan interval) {
        byte[] rewardAccount = RawItems.bytes(account);
        if (RawItems.content(interval).majorType() == MAJOR_UNSIGNED)
            return new RawAccountBalanceInterval(rewardAccount, RawItems.unsigned(interval), null, null);
        PositionalRecord bounds = PositionalRecord.of(BOUNDS, interval, true);
        return new RawAccountBalanceInterval(rewardAccount, null,
                RawItems.nullable(bounds.value(FieldType.OPTIONAL_COIN), RawItems::unsigned),
                RawItems.nullable(bounds.value(FieldType.OPTIONAL_COIN, 1), RawItems::unsigned));
    }
}
