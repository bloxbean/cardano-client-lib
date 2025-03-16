package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepType;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GovIdTest {

    @Test
    void ccColdFromScriptHash() {
        String scriptHash ="00000000000000000000000000000000000000000000000000000000";
        String ccColdId = GovId.ccColdFromScriptHash(HexUtil.decodeHexString(scriptHash));

        assertThat(ccColdId).isEqualTo("cc_cold1zvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6kflvs");
    }

    @Test
    void ccHotFromKeyHashBytes() {
        String keyHash ="00000000000000000000000000000000000000000000000000000000";
        String ccHotId = GovId.ccHotFromKeyHash(HexUtil.decodeHexString(keyHash));

        assertThat(ccHotId).isEqualTo("cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7");
    }

    @Test
    void drepFromKeyHash() {
        String keyHash = "00000000000000000000000000000000000000000000000000000000";
        String drepId = GovId.drepFromKeyHash(HexUtil.decodeHexString(keyHash));

        assertThat(drepId).isEqualTo("drep1ygqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq7vlc9n");
    }

    @Test
    void keyType() {
        var credType = GovId.credType("drep1ygqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq7vlc9n");
        assertThat(credType).isEqualTo(CredentialType.Key);
    }

    @Test
    void keyType2() {
        var credType = GovId.credType("cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7");
        assertThat(credType).isEqualTo(CredentialType.Key);
    }

    @Test
    void scriptType() {
        var credType = GovId.credType("cc_cold1zvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6kflvs");
        assertThat(credType).isEqualTo(CredentialType.Script);
    }

    @Test
    void keyTypeFromIdBytes() {
        var credType = GovId.credTypeFromIdBytes("0200000000000000000000000000000000000000000000000000000000");
        assertThat(credType).isEqualTo(CredentialType.Key);
    }

    @Test
    void scriptTypeFromIdBytes() {
        var credType = GovId.credTypeFromIdBytes("1300000000000000000000000000000000000000000000000000000000");
        assertThat(credType).isEqualTo(CredentialType.Script);
    }

    @Test
    void govActionId() {
        String txHash = "0000000000000000000000000000000000000000000000000000000000000000";
        int index = 17;

        String govActionId = GovId.govAction(txHash, index);
        assertThat(govActionId).isEqualTo("gov_action1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqpzklpgpf");
    }

    @Test
    void govActionId2() {
        String txHash = "1111111111111111111111111111111111111111111111111111111111111111";
        int index = 0;

        String govActionId = GovId.govAction(txHash, index);
        assertThat(govActionId).isEqualTo("gov_action1zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygsq6dmejn");
    }

    @Test
    void drepFromDrepId() {
        String drepId = "drep1ygqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq7vlc9n";
        DRep dRep = GovId.toDrep(drepId);

        assertThat(dRep.getHash()).isEqualTo("00000000000000000000000000000000000000000000000000000000");
        assertThat(dRep.getType()).isEqualTo(DRepType.ADDR_KEYHASH);
    }

    @Test
    void drepFromDrepId_1() {
        String drepId = "drep1y2jmg4g450lced7q9n34rq6d5vjwkm0ugx6h0894u6ur92s9txn3a";
        DRep dRep = GovId.toDrep(drepId);

        assertThat(dRep.getHash()).isEqualTo("a5b45515a3ff8cb7c02ce351834da324eb6dfc41b5779cb5e6b832aa");
        assertThat(dRep.getType()).isEqualTo(DRepType.ADDR_KEYHASH);
    }

    @Test
    void drepFromDrepId_invlid_keylength() {
        assertThrows(IllegalArgumentException.class, () -> {
            String drepId = "drep15k6929drl7xt0spvudgcxndryn4kmlzpk4meed0xhqe25nle07s";
            GovId.toDrep(drepId);
        });
    }

    @Test
    void drepFromDrepId_invlid_drepid() {
        assertThrows(IllegalArgumentException.class, () -> {
            String drepId = "cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7";
            GovId.toDrep(drepId);
        });
    }

    @Test
    void ccHotToCredential() {
        String drepId = "cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7";
        Credential credential = GovId.ccHotToCredential(drepId);

        assertThat(credential.getBytes()).isEqualTo(HexUtil.decodeHexString("00000000000000000000000000000000000000000000000000000000"));
        assertThat(credential.getType()).isEqualTo(CredentialType.Key);
    }

    @Test
    void ccHotToCredential_invalid_prefix() {
        assertThrows(IllegalArgumentException.class, () -> {
            String drepId = "cc_cold1zvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6kflvs";
            GovId.ccHotToCredential(drepId);
        });
    }

    @Test
    void ccColdToCredential() {
        String drepId = "cc_cold1zvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6kflvs";
        Credential credential = GovId.ccColdToCredential(drepId);

        assertThat(credential.getBytes()).isEqualTo(HexUtil.decodeHexString("00000000000000000000000000000000000000000000000000000000"));
        assertThat(credential.getType()).isEqualTo(CredentialType.Script);
    }

    @Test
    void ccColdToCredential_invalid_prefix() {
        assertThrows(IllegalArgumentException.class, () -> {
            String drepId = "cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7";
            GovId.ccColdToCredential(drepId);
        });
    }

    @Test
    void ccColdToCredential_invlid_keylength() {
        assertThrows(IllegalArgumentException.class, () -> {
            String drepId = "cc_cold1lmaet9hdvu9d9jvh34u0un4ndw3yewaq5ch6fnwsctw02xxwylj";
            GovId.ccColdToCredential(drepId);
        });
    }

    /**. TODO : Add tests for commented test cases
    @Test
    void ccHotFromKeyHash() {
        String keyHash = "00000000000000000000000000000000000000000000000000000000";
        String ccHotId = GovId.ccHotFromKeyHash(keyHash);

        assertThat(ccHotId).isEqualTo("cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7");
    }

    @Test
    void ccColdFromKeyHash() {

    }

    @Test
    void drepFromScriptHash() {

    }
    **/
}
