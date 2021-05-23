package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ScriptTest {

    @Test
    public void testGetPolicyId() throws CborException {
        ScriptPubkey scriptPubkey = new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        String policyId = scriptPubkey.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptPubkey));

        assertThat(policyId, is("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313"));
    }

    @Test
    public void testSciptAllPolicyId() throws CborException {
        ScriptPubkey scriptPubkey1 = new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        ScriptPubkey scriptPubkey2 = new ScriptPubkey("ee7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        ScriptPubkey scriptPubkey3 = new ScriptPubkey("ff7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");

        ScriptAll scriptAll = new ScriptAll();
        scriptAll.addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String expectedPolicyId = "3e2abf6c1a400037d4c6fad14143553df36c2c5e6ec33c10ae411155";

        String policyId = scriptAll.getPolicyId();
        System.out.println(policyId);
        assertThat(policyId, is(expectedPolicyId));

    }

    @Test
    public void testSciptAnyPolicyId() throws CborException {
        ScriptPubkey scriptPubkey1 = new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        ScriptPubkey scriptPubkey2 = new ScriptPubkey("ef7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        ScriptPubkey scriptPubkey3 = new ScriptPubkey("ff7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");

        ScriptAny scriptAny = new ScriptAny();
        scriptAny.addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String expectedPolicyId = "f63e8acf67374e0aa0482d0055b419b9ce1adf80628a8ee23130782b";

        String policyId = scriptAny.getPolicyId();
        System.out.println(policyId);
        assertThat(policyId, is(expectedPolicyId));

    }

    @Test
    public void testSciptAtLeastPolicyId() throws CborException {
        ScriptPubkey scriptPubkey1 = new ScriptPubkey("2f3d4cf10d0471a1db9f2d2907de867968c27bca6272f062cd1c2413");
        ScriptPubkey scriptPubkey2 = new ScriptPubkey("f856c0c5839bab22673747d53f1ae9eed84afafb085f086e8e988614");
        ScriptPubkey scriptPubkey3 = new ScriptPubkey("b275b08c999097247f7c17e77007c7010cd19f20cc086ad99d398538");

       ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2);
        scriptAtLeast.addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        String expectedPolicyId = "1e3e60975af4971f7cc02ed4d90c87abaafd2dd070a42eafa6f5e939";

        String policyId = scriptAtLeast.getPolicyId();
        System.out.println(policyId);
        assertThat(policyId, is(expectedPolicyId));
    }

    @Test
    public void testRequiredAfterPolicyId() throws CborException {
        RequireTimeAfter requiredAfter = new RequireTimeAfter(1000);
        ScriptPubkey scriptPubkey = new ScriptPubkey("966e394a544f242081e41d1965137b1bb412ac230d40ed5407821c37");

        ScriptAll scriptAll = new ScriptAll()
                .addScript(requiredAfter)
                .addScript(scriptPubkey);

        String policyId = scriptAll.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptAll));

        assertThat(policyId, is("120125c6dea2049988eb0dc8ddcc4c56dd48628d45206a2d0bc7e55b"));
    }

    @Test
    public void testRequiredBeforePolicyId() throws CborException {
        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(2000);
        ScriptPubkey scriptPubkey = new ScriptPubkey("966e394a544f242081e41d1965137b1bb412ac230d40ed5407821c37");

        ScriptAll scriptAll = new ScriptAll()
                .addScript(requireTimeBefore)
                .addScript(scriptPubkey);

        String policyId = scriptAll.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptAll));

        assertThat(policyId, is("d900e9ec3899d67d70050d1f8f4dd0a3c7bb1439e134509ee5c86b01"));
    }

    @Test
    public void testScriptAnyScriptPubRequiredBeforePolicyId() throws CborException {

        ScriptAny scriptAny = new ScriptAny()
                .addScript(new ScriptPubkey("b275b08c999097247f7c17e77007c7010cd19f20cc086ad99d398538"))
                .addScript(new ScriptAll()
                        .addScript(new RequireTimeBefore(3000))
                        .addScript(new ScriptPubkey("966e394a544f242081e41d1965137b1bb412ac230d40ed5407821c37")));

        String policyId = scriptAny.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptAny));

        assertThat(policyId, is("6519f942518b8761f4b02e1403365b7d7befae1eb488b7fffcbab33f"));
    }

}
