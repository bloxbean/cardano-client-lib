package com.bloxbean.cardano.client.transaction.spec.cert;

public enum CertificateType {
    STAKE_REGISTRATION(0),
    STAKE_DEREGISTRATION(1),
    STAKE_DELEGATION(2),
    POOL_REGISTRATION(3),
    POOL_RETIREMENT(4),
    GENESIS_KEY_DELEGATION(5),
    MOVE_INSTATENEOUS_REWARDS_CERT(6),

    //conway
    //Delegation certs
    REG_CERT(7),
    UNREG_CERT(8),
    VOTE_DELEG_CERT(9),
    STAKE_VOTE_DELEG_CERT(10),
    STAKE_REG_DELEG_CERT(11),
    VOTE_REG_DELEG_CERT(12),
    STAKE_VOTE_REG_DELEG_CERT(13),

    //Gov certs
    AUTH_COMMITTEE_HOT_CERT(14),
    RESIGN_COMMITTEE_COLD_CERT(15),
    REG_DREP_CERT(16),
    UNREG_DREP_CERT(17),
    UPDATE_DREP_CERT(18);

    private int value;
    CertificateType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
