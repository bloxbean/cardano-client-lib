package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.raw.GovActionKind;
import com.bloxbean.cardano.client.transaction.raw.RawGovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.transaction.raw.RawWithdrawal;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


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
    private byte[] policyHash;

    public void addWithdrawal(Withdrawal withdrawal) {
        if(withdrawals == null)
            withdrawals = new ArrayList<>();

        withdrawals.add(withdrawal);
    }

    @Override
    @SneakyThrows
    public Array serialize(Era era) {
        Objects.requireNonNull(withdrawals);

        Array array = new Array();
        array.add(new UnsignedInteger(2));

        array.add(Withdrawal.serializeInLedgerOrder(withdrawals));

        if (policyHash != null && policyHash.length > 0) {
            if (policyHash.length != 28)
                throw new IllegalArgumentException("Policy hash length should be 28 bytes");
            array.add(new ByteString(policyHash));
        } else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static TreasuryWithdrawalsAction deserialize(Array govActionArray) {
        return from(RawGovAction.lenient(GovActionKind.TREASURY_WITHDRAWALS, RawModels.span(govActionArray),
                RawModels.READ_IN));
    }

    /**
     * @param raw a treasury withdrawal as received
     * @return the model
     */
    static TreasuryWithdrawalsAction from(RawGovAction raw) {
        List<Withdrawal> withdrawals = new ArrayList<>();
        for (RawWithdrawal withdrawal : raw.withdrawals())
            withdrawals.add(new Withdrawal(RawModels.rewardAddress(withdrawal.rewardAccount()), withdrawal.coin()));
        return new TreasuryWithdrawalsAction(withdrawals, raw.policyHash().orElse(null));
    }
}
