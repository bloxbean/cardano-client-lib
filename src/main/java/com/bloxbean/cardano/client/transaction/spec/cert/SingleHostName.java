package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class SingleHostName implements Relay {
    private int port;
    private String dnsName;

    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(1));

        if (port != 0)
            array.add(new UnsignedInteger(port));
        else
            array.add(SimpleValue.NULL);

        if (dnsName != null && !dnsName.isEmpty())
            array.add(new UnicodeString(dnsName));
        else
            throw new CborSerializationException("Serialization failed. DNS name can't be null for SingleHostName relay");

        return array;
    }
}
