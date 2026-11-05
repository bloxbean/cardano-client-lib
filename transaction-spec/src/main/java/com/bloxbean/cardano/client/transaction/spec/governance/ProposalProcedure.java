package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawProposal;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Objects;

/**
 * proposal_procedure =
 *   [ deposit : coin
 *   , reward_account
 *   , gov_action
 *   , anchor
 *   ]
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProposalProcedure {
    private BigInteger deposit;
    //Bech32 reward address
    private String rewardAccount;
    private GovAction govAction;
    private Anchor anchor;

    public DataItem serialize() {
        Objects.requireNonNull(deposit);
        Objects.requireNonNull(rewardAccount);
        Objects.requireNonNull(govAction);
        Objects.requireNonNull(anchor);

        Array array = new Array();
        array.add(new UnsignedInteger(deposit));

        try {
            byte[] addressBytes = AddressUtil.addressToBytes(rewardAccount);
            array.add(new ByteString(addressBytes));
        } catch (AddressExcepion e) {
            throw new CborRuntimeException("Unable to get address bytes from rewardAddress", e);
        }

        array.add(govAction.serialize());
        array.add(anchor.serialize());

        return array;
    }


    public static ProposalProcedure deserialize(DataItem di) {
        return from(RawProposal.lenient(RawModels.span(di), RawModels.READ_IN));
    }

    /**
     * @param raw a proposal procedure as received
     * @return the model
     */
    public static ProposalProcedure from(RawProposal raw) {
        BigInteger deposit = raw.deposit();
        String rewardAddress = RawModels.rewardAddress(raw.rewardAccount());
        return new ProposalProcedure(deposit, rewardAddress, GovAction.from(raw.govAction()),
                RawModels.anchor(raw.anchor()));
    }

}
