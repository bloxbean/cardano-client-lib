package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDelegation;
import com.bloxbean.cardano.client.transaction.spec.cert.StakePoolId;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WitnessCountEstimatorTest {

    private static final byte[] STAKE_KEY = hash((byte) 1);
    private static final byte[] DREP_KEY = hash((byte) 2);
    private static final byte[] REQUIRED_SIGNER = hash((byte) 3);
    private static final byte[] SCRIPT_KEY_A = hash((byte) 4);
    private static final byte[] SCRIPT_KEY_B = hash((byte) 5);

    private static byte[] hash(byte fill) {
        byte[] hash = new byte[28];
        java.util.Arrays.fill(hash, fill);
        return hash;
    }

    private static Transaction txWithBody(TransactionBody body) {
        Transaction transaction = new Transaction();
        transaction.setBody(body);
        return transaction;
    }

    @Test
    void emptyTransaction_countsZero() {
        assertThat(WitnessCountEstimator.countBodyImpliedSigners(txWithBody(new TransactionBody())))
                .isZero();
    }

    @Test
    void stakeAndDrepCertificates_countDistinctKeys() {
        TransactionBody body = new TransactionBody();
        body.getCerts().add(new StakeRegistration(StakeCredential.fromKeyHash(STAKE_KEY)));
        // Same stake key again — must not double-count.
        body.getCerts().add(new StakeDelegation(StakeCredential.fromKeyHash(STAKE_KEY),
                new StakePoolId(hash((byte) 9))));
        body.getCerts().add(RegDRepCert.builder()
                .drepCredential(Credential.fromKey(DREP_KEY))
                .build());

        assertThat(WitnessCountEstimator.countBodyImpliedSigners(txWithBody(body))).isEqualTo(2);
    }

    @Test
    void requiredSigners_counted_andDedupedAgainstCertKeys() {
        TransactionBody body = new TransactionBody();
        body.getCerts().add(new StakeRegistration(StakeCredential.fromKeyHash(STAKE_KEY)));
        body.getRequiredSigners().add(REQUIRED_SIGNER);
        body.getRequiredSigners().add(STAKE_KEY); // duplicate of the cert's key

        assertThat(WitnessCountEstimator.countBodyImpliedSigners(txWithBody(body))).isEqualTo(2);
    }

    @Test
    void nativeScriptPubkeys_countedFromWitnessSet() throws Exception {
        List<ScriptPubkey> keys = new ArrayList<>();
        keys.add(new ScriptPubkey(HexUtil.encodeHexString(SCRIPT_KEY_A)));
        keys.add(new ScriptPubkey(HexUtil.encodeHexString(SCRIPT_KEY_B)));
        ScriptAll scriptAll = new ScriptAll();
        keys.forEach(scriptAll::addScript);

        Transaction transaction = txWithBody(new TransactionBody());
        TransactionWitnessSet witnessSet = new TransactionWitnessSet();
        witnessSet.getNativeScripts().add(scriptAll);
        transaction.setWitnessSet(witnessSet);

        assertThat(WitnessCountEstimator.countBodyImpliedSigners(transaction)).isEqualTo(2);
    }

    @Test
    void scriptHashCredentials_areSkipped() {
        TransactionBody body = new TransactionBody();
        body.getCerts().add(new StakeRegistration(StakeCredential.fromScriptHash(hash((byte) 7))));

        assertThat(WitnessCountEstimator.countBodyImpliedSigners(txWithBody(body))).isZero();
    }
}
