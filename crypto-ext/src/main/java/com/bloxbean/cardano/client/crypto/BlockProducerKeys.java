package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.crypto.kes.KesException;
import com.bloxbean.cardano.client.crypto.kes.OpCert;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Convenience class to load block producer keys from TextEnvelope JSON files.
 * <p>
 * Loads:
 * <ul>
 *   <li>VRF signing key (64 bytes: 32-byte seed + 32-byte public key)</li>
 *   <li>KES signing key (608 bytes: Sum6Kes tree state)</li>
 *   <li>Operational certificate (KES vkey, counter, period, cold signature, cold vkey)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockProducerKeys {

    private byte[] vrfSkey;     // 64 bytes (seed + pk)
    private byte[] kesSkey;     // 608 bytes
    private OpCert opCert;

    /**
     * Load block producer keys from TextEnvelope JSON files.
     *
     * @param vrfSkeyPath  path to VRF signing key file (e.g., delegate1.vrf.skey)
     * @param kesSkeyPath  path to KES signing key file (e.g., delegate1.kes.skey)
     * @param opCertPath   path to operational certificate file (e.g., opcert1.cert)
     * @return loaded BlockProducerKeys
     */
    public static BlockProducerKeys load(Path vrfSkeyPath, Path kesSkeyPath, Path opCertPath) {
        byte[] vrfSkey = loadKeyFromTextEnvelope(vrfSkeyPath);
        byte[] kesSkey = loadKeyFromTextEnvelope(kesSkeyPath);
        OpCert opCert = OpCert.fromFile(opCertPath);

        return BlockProducerKeys.builder()
                .vrfSkey(vrfSkey)
                .kesSkey(kesSkey)
                .opCert(opCert)
                .build();
    }

    /**
     * Load raw key bytes from a TextEnvelope JSON file.
     * The cborHex field contains CBOR-wrapped key bytes.
     */
    private static byte[] loadKeyFromTextEnvelope(Path path) {
        try {
            String json = Files.readString(path);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);
            JsonNode cborHexNode = node.get("cborHex");
            if (cborHexNode == null) {
                throw new KesException("Missing 'cborHex' field in TextEnvelope: " + path);
            }
            String cborHex = cborHexNode.asText();
            return KeyGenCborUtil.cborToBytes(cborHex);
        } catch (IOException e) {
            throw new KesException("Failed to read key file: " + path, e);
        } catch (Exception e) {
            throw new KesException("Failed to parse key from TextEnvelope: " + path, e);
        }
    }
}
