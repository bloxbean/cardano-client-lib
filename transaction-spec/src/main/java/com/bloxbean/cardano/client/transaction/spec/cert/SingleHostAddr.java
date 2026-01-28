package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.*;

import java.net.Inet4Address;
import java.net.Inet6Address;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class SingleHostAddr implements Relay {
    private Integer port;

    @JsonSerialize(using = InetAddressSerializer.class)
    @JsonDeserialize(using = InetAddressDeserializer.class)
    private Inet4Address ipv4;

    @JsonSerialize(using = InetAddressSerializer.class)
    @JsonDeserialize(using = InetAddressDeserializer.class)
    private Inet6Address ipv6;

    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(0));

        if (port != null && port != 0)
            array.add(new UnsignedInteger(port));
        else
            array.add(SimpleValue.NULL);

        if (ipv4 != null)
            array.add(new ByteString(ipv4.getAddress()));
        else
            array.add(SimpleValue.NULL);

        if (ipv6 != null)
            array.add(new ByteString(ipv6.getAddress()));
        else
            array.add(SimpleValue.NULL);

        return array;
    }
}
