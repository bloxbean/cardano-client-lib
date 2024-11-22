package com.bloxbean.cardano.client.cip.cip8.builder;

import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.cip.cip8.*;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Accessors(fluent = true)
@Data
public class COSESignBuilder {
    private Headers headers;
    private byte[] payload;
    private byte[] externalAad;
    private boolean isPayloadExternal;
    private boolean hashed;

    public COSESignBuilder(Headers headers, byte[] payload, boolean isPayloadExternal) {
        this.headers = headers;
        this.payload = payload;
        this.isPayloadExternal = isPayloadExternal;
    }

    public SigStructure makeDataToSign() {
        Headers headersCopy = headers.copy();

        byte[] finalPayload;
        if (isPayloadExternal) {
            finalPayload = payload.clone();
        } else
            finalPayload = hashed? Blake2bUtil.blake2bHash224(payload): payload.clone();

        return new SigStructure()
                .sigContext(SigContext.Signature)
                .bodyProtected(headersCopy._protected())
                .externalAad(externalAad != null ? externalAad.clone() : new byte[0])
                .payload(finalPayload);
    }

    public COSESign build(List<COSESignature> coseSignatures) {
        Headers allHeader = headers.copy();
        allHeader.unprotected().addOtherHeader("hashed", hashed ? SimpleValue.TRUE : SimpleValue.FALSE);

        byte[] finalPayload;
        if (hashed) { //blake2b224 hash
            finalPayload = Blake2bUtil.blake2bHash224(payload);
        } else
            finalPayload = payload.clone();

        return new COSESign()
                .headers(allHeader)
                .payload(isPayloadExternal ? null : finalPayload)
                .signatures(coseSignatures);
    }
}
