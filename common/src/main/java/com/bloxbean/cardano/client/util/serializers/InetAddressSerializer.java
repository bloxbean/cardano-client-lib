package com.bloxbean.cardano.client.util.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Jackson serializer that converts InetAddress (Inet4Address/Inet6Address) to IP string for JSON/YAML serialization.
 * Can be used with @JsonSerialize annotation on InetAddress fields.
 */
public class InetAddressSerializer extends StdSerializer<InetAddress> {

    public InetAddressSerializer() {
        this(null);
    }

    public InetAddressSerializer(Class<InetAddress> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(InetAddress value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.getHostAddress());
        }
    }
}