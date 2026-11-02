package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

class UpdateCommitteeTest {
    private static final String MEMBER = "8200581c" + "11".repeat(28);
    private static final String NEW_MEMBER = "8200581c" + "22".repeat(28);

    /**
     * The members to remove are a set (cardano-ledger decodeSet: definite or indefinite, tag 258 allowed). The BREAK of
     * an indefinite one was read as a credential.
     */
    @Test
    void indefiniteMembersForRemovalDecodeAsDefiniteOnes() {
        // [4, null, set<cold credential>, {cold credential => epoch}, unit interval]
        String definite = "8504f6" + "d9010281" + MEMBER + "a1" + NEW_MEMBER + "0a" + "d81e820102";
        UpdateCommittee expected = deserialize(definite);
        assertThat(expected.getMembersForRemoval()).hasSize(1);

        assertThat(deserialize(definite.replace("d9010281" + MEMBER, "d901029f" + MEMBER + "ff"))).isEqualTo(expected);
        assertThat(deserialize(definite.replace("d9010281" + MEMBER, "9f" + MEMBER + "ff"))).isEqualTo(expected);
    }

    private static UpdateCommittee deserialize(String hex) {
        return UpdateCommittee.deserialize((Array) CborSerializationUtil.deserialize(decodeHexString(hex)));
    }
}
