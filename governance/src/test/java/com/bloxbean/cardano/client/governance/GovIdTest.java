package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GovIdTest {

    @Test
    void ccColdFromKeyHash() {

    }

    @Test
    void ccColdFromScriptHash() {
        String scriptHash ="00000000000000000000000000000000000000000000000000000000";
        String ccColdId = GovId.ccColdFromScriptHash(HexUtil.decodeHexString(scriptHash));

        assertThat(ccColdId).isEqualTo("cc_cold1zvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6kflvs");
    }

    @Test
    void ccHotFromKeyHash() {
//        String keyHash ="00000000000000000000000000000000000000000000000000000000";
//        String ccHotId = GovId.ccHotFromKeyHash(keyHash);
//
//        assertThat(ccHotId).isEqualTo("cc_hot1qgqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqvcdjk7");
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
    void drepFromScriptHash() {

    }
}
