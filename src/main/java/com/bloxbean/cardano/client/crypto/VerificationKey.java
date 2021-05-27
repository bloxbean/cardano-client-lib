package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class VerificationKey {
    private final static Logger LOG = LoggerFactory.getLogger(VerificationKey.class);

    private String type = "PaymentVerificationKeyShelley_ed25519";
    private String description = "Payment Verification Key";
    private String cborHex;

    public VerificationKey() {
        this.type = "PaymentVerificationKeyShelley_ed25519";
        this.description = "Payment Verification Key";
    }

    public VerificationKey(String cborHex) {
        this();
        this.cborHex = cborHex;
    }

    @JsonIgnore
    private byte[] bytes;

    public byte[] getBytes() {
        if(cborHex != null) {
            try {
                return KeyGenCborUtil.cborToBytes(cborHex);
            } catch (Exception e) {
                LOG.error("Cbor decode error", e);
                return null;
            }
        }

        return null;
    }

    public static VerificationKey create(byte[] bytes) throws CborSerializationException {
        String cbor = KeyGenCborUtil.bytesToCbor(bytes);
        return new VerificationKey(cbor);
    }
}
