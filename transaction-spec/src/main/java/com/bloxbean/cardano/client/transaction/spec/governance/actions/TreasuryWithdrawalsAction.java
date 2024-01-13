package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import lombok.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toBytes;

/**
 * {@literal
 * treasury_withdrawals_action = (2, { reward_account => coin })
 * }
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class TreasuryWithdrawalsAction implements GovAction {
    private final GovActionType type = GovActionType.TREASURY_WITHDRAWALS_ACTION;

    @Builder.Default
    private List<Withdrawal> withdrawals = new ArrayList<>();

    public void addWithdrawal(Withdrawal withdrawal) {
        if(withdrawals == null)
            withdrawals = new ArrayList<>();

        withdrawals.add(withdrawal);
    }

    @Override
    @SneakyThrows
    public Array serialize() {
        Objects.requireNonNull(withdrawals);

        Array array = new Array();
        array.add(new UnsignedInteger(2));

        Map withdrawalMap = new Map();
        for (Withdrawal withdrawal : withdrawals) {
            withdrawal.serialize(withdrawalMap);
        }

        array.add(withdrawalMap);
        return array;
    }

    public static TreasuryWithdrawalsAction deserialize(Array govActionArray) {
        List<DataItem> govActionDIList = govActionArray.getDataItems();

        Map map = (Map) govActionDIList.get(1);
        List<Withdrawal> withdrawals = new ArrayList<>();

        Collection<DataItem> keys;
        keys = map.getKeys();
        for (DataItem key : keys) {
            String rewardAddress;
            try {
                rewardAddress = AddressUtil.bytesToAddress(toBytes(key));
            } catch (Exception e) {
                throw new CborRuntimeException("Bytes cannot be converted to bech32 address", e);
            }

            BigInteger coin = CborSerializationUtil.getBigInteger(map.get(key));
            withdrawals.add(new Withdrawal(rewardAddress, coin));
        }

        return new TreasuryWithdrawalsAction(withdrawals);
    }
}
