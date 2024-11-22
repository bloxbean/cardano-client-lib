package com.bloxbean.cardano.client.cip.cip8.builder;

import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import com.bloxbean.cardano.client.cip.cip8.Headers;
import com.bloxbean.cardano.client.cip.cip8.SigContext;
import com.bloxbean.cardano.client.cip.cip8.SigStructure;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Data
public class COSESign1Builder {
    private Headers headers;
    private byte[] payload;
    private byte[] externalAad;
    private boolean isPayloadExternal;
    private boolean hashed;

    public COSESign1Builder(Headers headers, byte[] payload, boolean isPayloadExternal) {
        this.headers = headers;
        this.payload = payload;
        this.isPayloadExternal = isPayloadExternal;
    }

    public SigStructure makeDataToSign() {
        Headers headersCopy = headers.copy();
        headersCopy.unprotected().addOtherHeader("hashed", hashed ? SimpleValue.TRUE : SimpleValue.FALSE);

        byte[] finalPayload;
        if (isPayloadExternal) {
            finalPayload = payload.clone();
        } else {
            finalPayload = hashed ? Blake2bUtil.blake2bHash224(payload): payload.clone();
        }

        return new SigStructure()
                .sigContext(SigContext.Signature1)
                .bodyProtected(headersCopy._protected())
                .externalAad(externalAad != null ? externalAad.clone() : new byte[0])
                .payload(finalPayload);
    }

    public COSESign1 build(byte[] signedSigStructure) {
        Headers allHeader = headers.copy();
        allHeader.unprotected().addOtherHeader("hashed", hashed ? SimpleValue.TRUE : SimpleValue.FALSE);

        byte[] finalPayload;
        if (hashed) { //blake2b224 hash
            finalPayload = Blake2bUtil.blake2bHash224(payload);
        } else
            finalPayload = payload.clone();

        return new COSESign1()
                .headers(allHeader)
                .payload(isPayloadExternal ? null : finalPayload)
                .signature(signedSigStructure);
    }
}
