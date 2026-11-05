package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import com.bloxbean.cardano.client.transaction.raw.RawMir;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.math.BigInteger;
import java.util.LinkedHashMap;

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
    public Array serialize(Era era) throws CborSerializationException {
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
        return from(RawCertificate.lenient(CertKind.MOVE_INSTANTANEOUS_REWARDS, RawModels.span(moveInstantaneousArr)));
    }

    static MoveInstataneous from(RawCertificate cert) throws CborDeserializationException {
        RawMir mir = RawModels.exact(cert).moveInstantaneousReward();
        java.util.Map<StakeCredential, BigInteger> stakeCredentialsMap = new LinkedHashMap<>();
        BigInteger accountingPotCoin = null;
        if (mir.movesToCredentials()) {
            for (java.util.Map.Entry<RawCredential, BigInteger> target : mir.targets())
                stakeCredentialsMap.put(RawModels.stakeCredential(target.getKey()), target.getValue());
        } else {
            accountingPotCoin = mir.coin();
        }
        MirPot pot = mir.namedPot().map(named -> named == RawMir.Pot.RESERVES ? MirPot.RESERVES : MirPot.TREASURY)
                .orElse(null);
        return new MoveInstataneous(pot, accountingPotCoin, stakeCredentialsMap);
    }
}
