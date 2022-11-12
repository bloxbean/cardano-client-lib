package com.bloxbean.cardano.client.transaction.spec.script;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testGetPolicyId() throws CborSerializationException {
        ScriptPubkey scriptPubkey = new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884");
        String policyId = scriptPubkey.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptPubkey));

        assertThat(policyId, is("b9bd3fb4511908402fbef848eece773bb44c867c25ac8c08d9ec3313"));
    }

    @Test
    public void testSciptAllPolicyId() throws CborSerializationException {
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
    public void testSciptAnyPolicyId() throws CborSerializationException {
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
    public void testSciptAtLeastPolicyId() throws CborSerializationException {
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
    public void testRequiredAfterPolicyId() throws CborSerializationException {
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
    public void testRequiredBeforePolicyId() throws CborSerializationException {
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
    public void testScriptAnyScriptPubRequiredBeforePolicyId() throws CborSerializationException {

        ScriptAny scriptAny = new ScriptAny()
                .addScript(new ScriptPubkey("b275b08c999097247f7c17e77007c7010cd19f20cc086ad99d398538"))
                .addScript(new ScriptAll()
                        .addScript(new RequireTimeBefore(3000))
                        .addScript(new ScriptPubkey("966e394a544f242081e41d1965137b1bb412ac230d40ed5407821c37")));

        String policyId = scriptAny.getPolicyId();

        System.out.println(JsonUtil.getPrettyJson(scriptAny));

        assertThat(policyId, is("6519f942518b8761f4b02e1403365b7d7befae1eb488b7fffcbab33f"));
    }

    @Test
    public void testJsonSerDeser_whenScriptPubKey() throws JsonProcessingException, CborDeserializationException {
        ScriptPubkey key = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");

        String jsonStr = mapper.writeValueAsString(key);
        System.out.println(jsonStr);

        NativeScript deKey = NativeScript.deserializeJson(jsonStr);

        assertThat(key, equalTo(deKey));
    }

    @Test
    public void testJsonSerDeser_whenScriptPubKeyJackson() throws JsonProcessingException {
        ScriptPubkey scriptPubkey1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");

        String jsonStr = mapper.writeValueAsString(scriptPubkey1);
        System.out.println(jsonStr);

        ScriptPubkey scriptPubkey2 = mapper.readValue(jsonStr, ScriptPubkey.class);

        assertEquals(scriptPubkey1, scriptPubkey2);
    }

    @Test
    public void testJsonSerDeser_whenScriptAll() throws IOException, CborDeserializationException {
        ScriptPubkey key1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");
        ScriptPubkey key2 = new ScriptPubkey("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");
        ScriptPubkey key3 = new ScriptPubkey("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAll scriptAll = new ScriptAll();
        scriptAll.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        String jsonStr = mapper.writeValueAsString(scriptAll);
        System.out.println(jsonStr);

        NativeScript deScript = NativeScript.deserializeJson(jsonStr);

        assertTrue(deScript instanceof ScriptAll);
        assertThat(scriptAll, equalTo(deScript));
    }

    @Test
    public void testJsonSerDeser_whenScriptAllJackson() throws IOException {
        ScriptPubkey key1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");
        ScriptPubkey key2 = new ScriptPubkey("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");
        ScriptPubkey key3 = new ScriptPubkey("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAll scriptAll1 = new ScriptAll();
        scriptAll1.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        String jsonStr = mapper.writeValueAsString(scriptAll1);
        System.out.println(jsonStr);

        ScriptAll scriptAll2 = mapper.readValue(jsonStr, ScriptAll.class);

        assertEquals(scriptAll1, scriptAll2);
    }

    @Test
    public void testJsonSerDeser_whenScriptAny() throws IOException, CborDeserializationException {
        ScriptPubkey key1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");
        ScriptPubkey key2 = new ScriptPubkey("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");
        ScriptPubkey key3 = new ScriptPubkey("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAny scriptAny = new ScriptAny();
        scriptAny.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        String jsonStr = mapper.writeValueAsString(scriptAny);
        System.out.println(jsonStr);

        NativeScript deScript = NativeScript.deserializeJson(jsonStr);

        assertTrue(deScript instanceof ScriptAny);
        assertThat(scriptAny, equalTo(deScript));
    }

    @Test
    public void testJsonSerDeser_whenScriptAnyJackson() throws IOException {
        ScriptPubkey key1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");
        ScriptPubkey key2 = new ScriptPubkey("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");
        ScriptPubkey key3 = new ScriptPubkey("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAny scriptAny1 = new ScriptAny();
        scriptAny1.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        String jsonStr = mapper.writeValueAsString(scriptAny1);
        System.out.println(jsonStr);

        ScriptAny scriptAny2 = mapper.readValue(jsonStr, ScriptAny.class);
        assertEquals(scriptAny1, scriptAny2);
    }

    @Test
    public void testJsonSerDeser_whenScriptAtLeast() throws IOException, CborDeserializationException {
        ScriptPubkey key1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");
        ScriptPubkey key2 = new ScriptPubkey("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");
        ScriptPubkey key3 = new ScriptPubkey("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2);
        scriptAtLeast.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        String jsonStr = mapper.writeValueAsString(scriptAtLeast);
        System.out.println(jsonStr);

        NativeScript deScriptAtLeast = NativeScript.deserializeJson(jsonStr);

        assertTrue(deScriptAtLeast instanceof ScriptAtLeast);
        assertThat(scriptAtLeast, equalTo(deScriptAtLeast));
    }

    @Test
    public void testJsonSerDeser_whenScriptAtLeastJackson() throws IOException {
        ScriptPubkey key1 = new ScriptPubkey("74cfebcf5e97474d7b89c862d7ee7cff22efbb032d4133a1b84cbdcd");
        ScriptPubkey key2 = new ScriptPubkey("710ee487dbbcdb59b5841a00d1029a56a407c722b3081c02470b516d");
        ScriptPubkey key3 = new ScriptPubkey("beed26382ec96254a6714928c3c5bb8227abecbbb095cfeab9fb2dd1");

        ScriptAtLeast scriptAtLeast1 = new ScriptAtLeast(2);
        scriptAtLeast1.addScript(key1)
                .addScript(key2)
                .addScript(key3);

        String jsonStr = mapper.writeValueAsString(scriptAtLeast1);
        System.out.println(jsonStr);

        ScriptAtLeast scriptAtLeast2 = mapper.readValue(jsonStr, ScriptAtLeast.class);

        assertEquals(scriptAtLeast1, scriptAtLeast2);
    }

    @Test
    public void testJsonSerDeSer_whenRequireTimeBefore() throws JsonProcessingException, CborDeserializationException {
        RequireTimeBefore requireTimeBefore = new RequireTimeBefore(30003);

        String jsonStr = mapper.writeValueAsString(requireTimeBefore);
        System.out.println(jsonStr);

        NativeScript deScript = NativeScript.deserializeJson(jsonStr);

        assertTrue(deScript instanceof RequireTimeBefore);
        assertThat(requireTimeBefore, equalTo(deScript));
    }

    @Test
    public void testJsonSerDeSer_whenRequireTimeBeforeJackson() throws JsonProcessingException {
        RequireTimeBefore requireTimeBefore1 = new RequireTimeBefore(30003);

        String jsonStr = mapper.writeValueAsString(requireTimeBefore1);
        System.out.println(jsonStr);

        RequireTimeBefore requireTimeBefore2 = mapper.readValue(jsonStr, RequireTimeBefore.class);

        assertEquals(requireTimeBefore1, requireTimeBefore2);
    }

    @Test
    public void testJsonSerDeSer_whenRequireTimeAfter() throws JsonProcessingException, CborDeserializationException {
        RequireTimeAfter requireTimeAfter = new RequireTimeAfter(20003);

        String jsonStr = mapper.writeValueAsString(requireTimeAfter);
        System.out.println(jsonStr);

        NativeScript deScript = NativeScript.deserializeJson(jsonStr);

        assertTrue(deScript instanceof RequireTimeAfter);
        assertThat(requireTimeAfter, equalTo(deScript));
    }

    @Test
    public void testJsonSerDeSer_whenRequireTimeAfterJackson() throws JsonProcessingException {
        RequireTimeAfter requireTimeAfter1 = new RequireTimeAfter(20003);

        String jsonStr = mapper.writeValueAsString(requireTimeAfter1);
        System.out.println(jsonStr);

        RequireTimeAfter requireTimeAfter2 = mapper.readValue(jsonStr, RequireTimeAfter.class);

        assertEquals(requireTimeAfter1, requireTimeAfter2);
    }
}
