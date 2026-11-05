package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawWithdrawal;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Withdrawal {
    /**
     * Bech32 reward address
     */
    private String rewardAddress;
    private BigInteger coin;

    public static Withdrawal deserialize(Map withdrawalMap, DataItem addrKey) throws CborDeserializationException {
        Objects.requireNonNull(withdrawalMap);
        Objects.requireNonNull(addrKey);

        DataItem coin = withdrawalMap.get(addrKey);
        try {
            return from(RawWithdrawal.of(RawModels.span(addrKey), coin != null ? RawModels.span(coin) : null));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("Withdrawal deserialization failed", e);
        }
    }

    static Withdrawal from(RawWithdrawal raw) throws CborDeserializationException {
        try {
            return new Withdrawal(AddressUtil.bytesToAddress(raw.rewardAccount()), raw.coin());
        } catch (Exception e) {
            throw new CborDeserializationException("Bytes cannot be converted to bech32 address", e);
        }
    }

    /**
     * For the transaction body, so that a hardware wallet can sign it.
     *
     * @param withdrawals the withdrawals, in any order
     * @return their map, in CIP-21's order
     * @throws AddressExcepion if a reward address cannot be encoded
     */
    public static Map serialize(List<Withdrawal> withdrawals) throws AddressExcepion {
        return toMap(withdrawals, Comparators.WITHDRAWAL_BYTES);
    }

    /**
     * For a treasury withdrawals action.
     *
     * @param withdrawals the withdrawals, in any order
     * @return their map, in Haskell's order
     * @throws AddressExcepion if a reward address cannot be encoded
     * @throws RuntimeException if sorting finds an address that is not a reward address
     */
    public static Map serializeInLedgerOrder(List<Withdrawal> withdrawals) throws AddressExcepion {
        return toMap(withdrawals, Comparators.WITHDRAWAL);
    }

    private static Map toMap(List<Withdrawal> withdrawals, Comparator<Withdrawal> order) throws AddressExcepion {
        List<Withdrawal> sorted = new ArrayList<>(withdrawals);
        sorted.sort(order);

        Map withdrawalMap = new SortedMap();
        for (Withdrawal withdrawal : sorted) {
            withdrawal.serialize(withdrawalMap);
        }
        return withdrawalMap;
    }

    /**
     * Add Withdrawal to Withdrawal map
     *
     * @param withdrawalMap
     * @throws AddressExcepion
     */
    public void serialize(Map withdrawalMap) throws AddressExcepion {
        Objects.requireNonNull(withdrawalMap);

        byte[] addressBytes = AddressUtil.addressToBytes(rewardAddress);
        withdrawalMap.put(new ByteString(addressBytes), new UnsignedInteger(coin));
    }
}
