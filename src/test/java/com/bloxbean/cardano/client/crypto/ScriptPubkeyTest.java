package com.bloxbean.cardano.client.crypto;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.transaction.model.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ScriptPubkeyTest {

    @Test
    public void testGetPolicyId() throws CborException {
        ScriptPubkey scriptPubkey = new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        String policyId = scriptPubkey.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptPubkey));

        assertThat(policyId, is("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313"));
    }

}
