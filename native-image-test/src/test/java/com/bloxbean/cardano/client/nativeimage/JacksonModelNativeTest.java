package com.bloxbean.cardano.client.nativeimage;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.WalletUtxo;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.transaction.Nonce;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.cert.RegDRepCert;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson binding of CCL models. Inputs are fixed JSON written on the JVM, and outputs are checked field by field
 * through the JSON tree, so a registration missing for either direction fails here.
 */
class JacksonModelNativeTest {
    private static final String KEY_HASH = "8c4e9eee8ee96d3d6e6a3e0b5d1bd7b8a4f4b3e1d7b1d2b6e9d0d2e1";
    private static final String SECRET_KEY_CBOR = "5820" + "11".repeat(32);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void transactionToJsonWritesGovernanceCertificate() throws Exception {
        RegDRepCert cert = RegDRepCert.builder()
                .drepCredential(Credential.fromKey(KEY_HASH))
                .coin(BigInteger.valueOf(500_000_000L))
                .anchor(new Anchor("https://example.com/drep.json", new byte[32]))
                .build();
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder().certs(List.of(cert)).fee(BigInteger.valueOf(170_000)).build())
                .build();

        // Transaction.toJson() falls back to toString() when Jackson cannot serialize, so parse the result.
        JsonNode json = mapper.readTree(tx.toJson());

        JsonNode written = json.path("body").path("certs").path(0);
        assertEquals("REG_DREP_CERT", written.path("type").asText());
        assertEquals("Key", written.path("drepCredential").path("type").asText());
        assertEquals("jE6e7o7pbT1uaj4LXRvXuKT0s+HXsdK26dDS4Q==", written.path("drepCredential").path("bytes").asText());
        assertEquals(500_000_000L, written.path("coin").asLong());
        assertEquals("https://example.com/drep.json", written.path("anchor").path("anchorUrl").asText());
    }

    @Test
    void protocolParamUpdateWritesExtraEntropy() throws Exception {
        ProtocolParamUpdate update = ProtocolParamUpdate.builder()
                .minFeeA(BigInteger.valueOf(44))
                .extraEntropy(new Nonce(new byte[]{1, 2, 3}))
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(update));

        assertEquals(44, json.path("minFeeA").asInt());
        assertEquals("AQID", json.path("extraEntropy").path("hash").asText());
    }

    @Test
    void secretKeyFromCardanoCliKeyFile() throws Exception {
        String keyFile = "{\"type\":\"PaymentSigningKeyShelley_ed25519\",\"description\":\"Payment Signing Key\","
                + "\"cborHex\":\"" + SECRET_KEY_CBOR + "\"}";

        SecretKey key = mapper.readValue(keyFile, SecretKey.class);

        assertEquals("PaymentSigningKeyShelley_ed25519", key.getType());
        assertEquals(SECRET_KEY_CBOR, key.getCborHex());
        assertEquals(32, key.getBytes().length);
    }

    @Test
    void policyWithPolicyKeys() throws Exception {
        String json = "{\"name\":\"p\",\"policyScript\":{\"type\":\"sig\",\"keyHash\":\"" + KEY_HASH + "\"},"
                + "\"policyKeys\":[{\"type\":\"PaymentSigningKeyShelley_ed25519\",\"description\":\"Payment Signing Key\","
                + "\"cborHex\":\"" + SECRET_KEY_CBOR + "\"}]}";

        Policy policy = mapper.readValue(json, Policy.class);

        assertEquals("p", policy.getName());
        assertEquals("28c0c2f35a27674ecf94776df14d7469738518c7c0eccc66b83b32ab", policy.getPolicyId());
        assertEquals(SECRET_KEY_CBOR, policy.getPolicyKeys().get(0).getCborHex());

        JsonNode written = mapper.readTree(mapper.writeValueAsString(policy));
        assertEquals(KEY_HASH, written.path("policyScript").path("keyHash").asText());
        assertEquals(SECRET_KEY_CBOR, written.path("policyKeys").path(0).path("cborHex").asText());
    }

    @Test
    void walletUtxoWithDerivationPath() throws Exception {
        String json = "{\"tx_hash\":\"" + "aa".repeat(32) + "\",\"output_index\":1,\"address\":\"addr_test1xyz\","
                + "\"amount\":[{\"unit\":\"lovelace\",\"quantity\":3000000}],"
                + "\"derivation_path\":{\"purpose\":{\"value\":1852,\"harden\":true},\"coinType\":{\"value\":1815,\"harden\":true},"
                + "\"account\":{\"value\":2,\"harden\":true},\"role\":{\"value\":0,\"harden\":false},"
                + "\"index\":{\"value\":0,\"harden\":false}}}";

        WalletUtxo utxo = mapper.readValue(json, WalletUtxo.class);

        assertEquals("aa".repeat(32), utxo.getTxHash());
        assertEquals(1, utxo.getOutputIndex());
        assertEquals(BigInteger.valueOf(3_000_000), utxo.getAmount().get(0).getQuantity());
        assertEquals(1852, utxo.getDerivationPath().getPurpose().getValue());
        assertEquals(2, utxo.getDerivationPath().getAccount().getValue());
        assertTrue(utxo.getDerivationPath().getAccount().isHarden());

        JsonNode written = mapper.readTree(mapper.writeValueAsString(utxo));
        assertEquals(1815, written.path("derivation_path").path("coinType").path("value").asInt());
    }

    @Test
    void utxoFromSnakeCaseJson() throws Exception {
        String json = "{\"tx_hash\":\"" + "bb".repeat(32) + "\",\"output_index\":0,\"address\":\"addr_test1abc\","
                + "\"amount\":[{\"unit\":\"lovelace\",\"quantity\":1000000}],\"inline_datum\":\"d87980\"}";

        Utxo utxo = mapper.readValue(json, Utxo.class);

        assertEquals("bb".repeat(32), utxo.getTxHash());
        assertEquals("d87980", utxo.getInlineDatum());
        assertEquals("lovelace", utxo.getAmount().get(0).getUnit());
    }

    @Test
    void plutusDataJson() throws Exception {
        PlutusData data = ConstrPlutusData.of(1,
                BigIntPlutusData.of(42),
                BytesPlutusData.of(HexUtil.decodeHexString("cafe")),
                ListPlutusData.of(BigIntPlutusData.of(7)));

        String json = PlutusDataJsonConverter.toJson(data);
        PlutusData read = PlutusDataJsonConverter.toPlutusData(json);

        assertEquals(1, mapper.readTree(json).path("constructor").asInt());
        assertEquals(data.serializeToHex(), read.serializeToHex());
    }

    @Test
    void plutusBlueprint() throws Exception {
        PlutusContractBlueprint blueprint;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("native/blueprint.json")) {
            assertNotNull(in);
            blueprint = PlutusBlueprintLoader.loadBlueprint(in);
        }

        assertEquals(PlutusVersion.v2, blueprint.getPreamble().getPlutusVersion());
        assertEquals("complex_structures.hello_world", blueprint.getValidators().get(0).getTitle());
        assertEquals("3e3223a680da18336236e86cef31068b5df3b96c2bb0cb2d765f8af5", blueprint.getValidators().get(0).getHash());
        assertEquals(5, blueprint.getDefinitions().size());
        assertEquals("Datum", blueprint.getValidators().get(0).getRedeemer().getSchema().getRefSchema().getTitle());
    }
}
