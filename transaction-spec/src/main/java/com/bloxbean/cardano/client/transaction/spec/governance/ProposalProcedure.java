package com.bloxbean.cardano.client.transaction.spec.governance;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
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
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    /**
     * Writes the proposal in an era, which its governance action is written in: a parameter update's Dijkstra keys
     * only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era the era to write it in
     * @return the proposal
     */
    public DataItem serialize(Era era) {
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

        array.add(govAction.serialize(era));
        array.add(anchor.serialize());

        return array;
    }


    public static ProposalProcedure deserialize(DataItem di) {
        return from(RawProposal.lenient(CborSerializationUtil.span(di), RawModels.READ_IN));
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
