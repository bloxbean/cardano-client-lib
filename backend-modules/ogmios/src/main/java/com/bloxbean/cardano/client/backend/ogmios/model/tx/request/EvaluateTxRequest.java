package com.bloxbean.cardano.client.backend.ogmios.model.tx.request;

import com.bloxbean.cardano.client.backend.ogmios.model.base.MethodType;
import com.bloxbean.cardano.client.backend.ogmios.model.base.Request;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class EvaluateTxRequest extends Request {
    private static final MethodType METHOD_TYPE = MethodType.EVALUATE_TX;
    private byte[] cborBytes;

    public EvaluateTxRequest(byte[] cborBytes) {
        this.cborBytes = cborBytes;
    }

    @Override
    protected String getMethodType() {
        return METHOD_TYPE.getValue();
    }

    @Override
    public String getArgs() {
        return "\"evaluate\":\"" + HexUtil.encodeHexString(cborBytes) + "\"";
    }

    @Override
    public String getMirror() {
        return "\"object\":\"" + METHOD_TYPE.getValue() + "\",\"msg_id\":" + getMsgId();
    }
}
