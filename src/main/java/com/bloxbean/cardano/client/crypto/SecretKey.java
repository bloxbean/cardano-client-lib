package com.bloxbean.cardano.client.crypto;

import co.nstant.in.cbor.CborException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class SecretKey {
    private static final Logger LOG = LoggerFactory.getLogger(SecretKey.class);

    private String type = "PaymentVerificationKeyShelley_ed25519";
    private String description = "Payment Signing Key";
    private String cborHex;

    @JsonIgnore
    private byte[] bytes;

    public SecretKey() {
        this.type = "PaymentVerificationKeyShelley_ed25519";
        this.description = "Payment Signing Key";
    }

    public SecretKey(String cborHex) {
        this();
        this.cborHex = cborHex;
    }

    public byte[] getBytes()  {
        if(cborHex != null) {
            try {
                return KeyGenCborUtil.cborToBytes(cborHex);
            } catch (CborException e) {
                LOG.error("Cbor decode error", e);
                return null;
            }
        }

        return null;
    }

    public static SecretKey create(byte[] bytes) throws CborException {
        String cbor = KeyGenCborUtil.bytesToCbor(bytes);
        return new SecretKey(cbor);
    }
}
