package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class MultiHostName implements Relay {
    private String dnsName;

    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(2));

        if (dnsName != null && !dnsName.isEmpty())
            array.add(new UnicodeString(dnsName));
        else
            throw new CborSerializationException("Serialization failed. DNS name can't be null for SingleHostName relay");

        return array;
    }
}
