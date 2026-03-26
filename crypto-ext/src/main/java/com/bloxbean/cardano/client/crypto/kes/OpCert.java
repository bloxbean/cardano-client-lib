package com.bloxbean.cardano.client.crypto.kes;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.crypto.KeyGenCborUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Operational certificate for Cardano block production.
 * <p>
 * CBOR structure (from opcert files):
 * <pre>
 *   array(2):
 *     [0] array(4):  -- certificate body
 *       [0] bytes(32)  -- KES verification key (hot key)
 *       [1] uint       -- sequence number (counter)
 *       [2] uint       -- KES period (start period)
 *       [3] bytes(64)  -- Ed25519 signature from cold key
 *     [1] bytes(32)    -- cold verification key
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpCert {

    private byte[] kesVkey;       // 32 bytes
    private long counter;         // sequence number
    private long kesPeriod;       // KES start period
    private byte[] coldSignature; // 64 bytes (Ed25519)
    private byte[] coldVkey;      // 32 bytes

    /**
     * Parse an operational certificate from raw CBOR bytes.
     *
     * @param cbor the raw CBOR bytes
     * @return the parsed OpCert
     * @throws KesException if parsing fails
     */
    public static OpCert fromCbor(byte[] cbor) {
        try {
            List<DataItem> items = CborDecoder.decode(cbor);
            if (items.isEmpty()) {
                throw new KesException("Empty CBOR data for OpCert");
            }

            Array outer = (Array) items.get(0);
            List<DataItem> outerItems = outer.getDataItems();
            if (outerItems.size() < 2) {
                throw new KesException("OpCert CBOR must have 2 elements, got " + outerItems.size());
            }

            Array body = (Array) outerItems.get(0);
            List<DataItem> bodyItems = body.getDataItems();
            if (bodyItems.size() < 4) {
                throw new KesException("OpCert body must have 4 elements, got " + bodyItems.size());
            }

            byte[] kesVkey = ((ByteString) bodyItems.get(0)).getBytes();
            long counter = ((UnsignedInteger) bodyItems.get(1)).getValue().longValueExact();
            long kesPeriod = ((UnsignedInteger) bodyItems.get(2)).getValue().longValueExact();
            byte[] coldSignature = ((ByteString) bodyItems.get(3)).getBytes();
            byte[] coldVkey = ((ByteString) outerItems.get(1)).getBytes();

            if (kesVkey.length != 32) {
                throw new KesException("Invalid KES vkey size. Expected 32 bytes, got " + kesVkey.length);
            }
            if (coldSignature.length != 64) {
                throw new KesException("Invalid cold signature size. Expected 64 bytes, got " + coldSignature.length);
            }
            if (coldVkey.length != 32) {
                throw new KesException("Invalid cold vkey size. Expected 32 bytes, got " + coldVkey.length);
            }

            return OpCert.builder()
                    .kesVkey(kesVkey)
                    .counter(counter)
                    .kesPeriod(kesPeriod)
                    .coldSignature(coldSignature)
                    .coldVkey(coldVkey)
                    .build();
        } catch (KesException e) {
            throw e;
        } catch (Exception e) {
            throw new KesException("Failed to parse OpCert CBOR", e);
        }
    }

    /**
     * Parse an operational certificate from a TextEnvelope JSON string.
     *
     * @param json the TextEnvelope JSON string
     * @return the parsed OpCert
     * @throws KesException if parsing fails
     */
    public static OpCert fromTextEnvelope(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);
            String cborHex = node.get("cborHex").asText();
            byte[] cbor = HexUtil.decodeHexString(cborHex);
            return fromCbor(cbor);
        } catch (KesException e) {
            throw e;
        } catch (Exception e) {
            throw new KesException("Failed to parse OpCert TextEnvelope", e);
        }
    }

    /**
     * Load an operational certificate from a TextEnvelope JSON file.
     *
     * @param path path to the opcert file
     * @return the parsed OpCert
     * @throws KesException if loading or parsing fails
     */
    public static OpCert fromFile(Path path) {
        try {
            String json = Files.readString(path);
            return fromTextEnvelope(json);
        } catch (KesException e) {
            throw e;
        } catch (IOException e) {
            throw new KesException("Failed to read OpCert file: " + path, e);
        }
    }
}
