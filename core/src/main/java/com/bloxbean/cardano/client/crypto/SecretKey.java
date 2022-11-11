package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@NoArgsConstructor
public class SecretKey {

    private String type = "PaymentVerificationKeyShelley_ed25519";
    private String description = "Payment Signing Key";
    private String cborHex;

    @JsonIgnore
    private byte[] bytes;

    public SecretKey(String cborHex) {
        this();
        this.cborHex = cborHex;
    }

    public byte[] getBytes()  {
        if(cborHex != null) {
            try {
                return KeyGenCborUtil.cborToBytes(cborHex);
            } catch (Exception e) {
                log.error("Cbor decode error", e);
                return null;
            }
        }

        return null;
    }

    public static SecretKey create(byte[] bytes) throws CborSerializationException {
        String cbor = KeyGenCborUtil.bytesToCbor(bytes);
        return new SecretKey(cbor);
    }
}
