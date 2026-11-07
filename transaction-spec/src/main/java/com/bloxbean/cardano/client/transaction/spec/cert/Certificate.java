package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public interface Certificate {

    static Certificate deserialize(Array certArray) throws CborDeserializationException {
        Objects.requireNonNull(certArray);

        return from(RawCertificate.lenient(CborSerializationUtil.span(certArray)).orElseThrow(() ->
                new CborDeserializationException("Certificate deserialization failed. Unknown type")));
    }

    /**
     * @param cert a certificate as received
     * @return its model
     * @throws CborDeserializationException if it is not what its kind's model reads
     */
    static Certificate from(RawCertificate cert) throws CborDeserializationException {
        CertKind kind = cert.kind();
        switch (kind) {
            case STAKE_REGISTRATION:
                return StakeRegistration.from(cert);
            case STAKE_DEREGISTRATION:
                return StakeDeregistration.from(cert);
            case STAKE_DELEGATION:
                return StakeDelegation.from(cert);
            case POOL_REGISTRATION:
                return PoolRegistration.from(cert);
            case POOL_RETIREMENT:
                return PoolRetirement.from(cert);
            case GENESIS_KEY_DELEGATION:
                return GenesisKeyDelegation.from(cert);
            case MOVE_INSTANTANEOUS_REWARDS:
                return MoveInstataneous.from(cert);
            case REG_CERT:
                return RegCert.from(cert);
            case UNREG_CERT:
                return UnregCert.from(cert);
            case VOTE_DELEG_CERT:
                return VoteDelegCert.from(cert);
            case STAKE_VOTE_DELEG_CERT:
                return StakeVoteDelegCert.from(cert);
            case STAKE_REG_DELEG_CERT:
                return StakeRegDelegCert.from(cert);
            case VOTE_REG_DELEG_CERT:
                return VoteRegDelegCert.from(cert);
            case STAKE_VOTE_REG_DELEG_CERT:
                return StakeVoteRegDelegCert.from(cert);
            case AUTH_COMMITTEE_HOT_CERT:
                return AuthCommitteeHotCert.from(cert);
            case RESIGN_COMMITTEE_COLD_CERT:
                return ResignCommitteeColdCert.from(cert);
            case REG_DREP_CERT:
                return RegDRepCert.from(cert);
            case UNREG_DREP_CERT:
                return UnregDRepCert.from(cert);
            case UPDATE_DREP_CERT:
                return UpdateDRepCert.from(cert);
            default:
                throw new CborDeserializationException("Certificate deserialization failed. Unknown type : " + kind);
        }
    }

    default Array serialize() throws CborSerializationException {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    Array serialize(Era era) throws CborSerializationException;

    @JsonIgnore
    default String getCborHex() throws CborSerializationException {
        try {
            return HexUtil.encodeHexString(CborSerializationUtil.serialize(serialize()));
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serialization error", e);
        }
    }
}
