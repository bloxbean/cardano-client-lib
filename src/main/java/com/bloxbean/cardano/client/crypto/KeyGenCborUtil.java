package com.bloxbean.cardano.client.crypto;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.util.HexUtil;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class KeyGenCborUtil {

    public static String bytesToCbor(byte[] bytes) throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();

        List<DataItem> dataItems = cborBuilder.add(bytes).build();
        new CborEncoder(baos).nonCanonical().encode(dataItems);
        byte[] encodedBytes = baos.toByteArray();

        return Hex.toHexString(encodedBytes);
    }

    public static byte[] cborToBytes(String cbor) throws CborException {
        List<DataItem> dataItemList = CborDecoder.decode(HexUtil.decodeHexString(cbor));
        byte[] bytes = ((ByteString)dataItemList.get(0)).getBytes();
        return bytes;
    }

}
