package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;

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
        Array proposalProcedureArray = (Array) di;
        List<DataItem> diList = proposalProcedureArray.getDataItems();

        BigInteger deposit = getBigInteger(diList.get(0));

        String rewardAddress;
        try {
            rewardAddress = AddressUtil.bytesToAddress(((ByteString) diList.get(1)).getBytes());
        } catch (Exception e) {
            throw new CborRuntimeException("Bytes cannot be converted to bech32 reward address", e);
        }

        GovAction govAction = GovAction.deserialize((Array) diList.get(2));
        Anchor anchor = Anchor.deserialize((Array) diList.get(3));

        return new ProposalProcedure(deposit, rewardAddress, govAction, anchor);
    }

}
