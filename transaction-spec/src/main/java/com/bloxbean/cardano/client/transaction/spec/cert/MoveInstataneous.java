package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.*;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toInt;
import static com.bloxbean.cardano.client.transaction.spec.cert.MirPot.RESERVES;
import static com.bloxbean.cardano.client.transaction.spec.cert.MirPot.TREASURY;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
//TODO -- Integration tests pending
public class MoveInstataneous implements Certificate {
    private final CertificateType type = CertificateType.MOVE_INSTATENEOUS_REWARDS_CERT;

    //determines where the funds are drawn from
    private MirPot pot;

    private BigInteger accountingPotCoin; //the funds are given to the other accounting pot
    private java.util.Map<StakeCredential, BigInteger> stakeCredentialCoinMap; //funds are moved to stake credentials

    @Override
    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(6));

        if (pot == null)
            throw new CborSerializationException("pot can't be null");
        //move_instantaneous_reward
        Array moveInstArray = new Array();
        moveInstArray.add(new UnsignedInteger(pot.getValue()));

        if (stakeCredentialCoinMap != null && stakeCredentialCoinMap.size() > 0) {
            Map map = new Map();
            map.setChunked(true); //Set to chunked true after testing existing data.
            for (StakeCredential stakeCredential : stakeCredentialCoinMap.keySet()) {
                BigInteger deltaCoin = stakeCredentialCoinMap.get(stakeCredential);
                map.put(stakeCredential.serialize(), new UnsignedInteger(deltaCoin));
            }
            moveInstArray.add(map);
        } else {
            moveInstArray.add(new UnsignedInteger(accountingPotCoin));
        }

        array.add(moveInstArray);

        return array;
    }

    public static MoveInstataneous deserialize(Array moveInstantaneousArr) throws CborDeserializationException {
        List<DataItem> dataItemList = moveInstantaneousArr.getDataItems();
        if (dataItemList == null || dataItemList.size() != 2) {
            throw new CborDeserializationException("MoveInstantaneous Rewards deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 6)
            throw new CborDeserializationException("MoveInstantaneous Rewards deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        List<DataItem> moveInstDIList = ((Array) dataItemList.get(1)).getDataItems();

        MirPot pot = null;
        int fundsDrawnFrom = toInt(moveInstDIList.get(0));
        if (fundsDrawnFrom == 0)
            pot = RESERVES;
        if (fundsDrawnFrom == 1)
            pot = TREASURY;

        java.util.Map stakeCredentialsMap = new LinkedHashMap();
        BigInteger accountingPotCoin = null;
        DataItem fundsMoveDI = moveInstDIList.get(1);
        if (fundsMoveDI.getMajorType() == MajorType.MAP) { //funds are moved to stake credentials
            co.nstant.in.cbor.model.Map fundsMoveDIMap = (co.nstant.in.cbor.model.Map) fundsMoveDI;

            Collection<DataItem> keys = fundsMoveDIMap.getKeys();
            for (DataItem key : keys) {
                DataItem deltaCoinDI = fundsMoveDIMap.get(key);
                BigInteger deltaCoinValue = getBigInteger(deltaCoinDI);
                StakeCredential stakeCredential = StakeCredential.deserialize((Array) key);
                stakeCredentialsMap.put(stakeCredential, deltaCoinValue);
            }
        } else { //funds are given to the other accounting pot
            accountingPotCoin = getBigInteger(fundsMoveDI);
        }

        return new MoveInstataneous(pot, accountingPotCoin, stakeCredentialsMap);

    }
}
