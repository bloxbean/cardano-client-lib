package com.bloxbean.cardano.client.util.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Jackson deserializer that converts IP string to InetAddress for JSON/YAML deserialization.
 * Can be used with @JsonDeserialize annotation on InetAddress fields.
 */
public class InetAddressDeserializer extends StdDeserializer<InetAddress> {

    public InetAddressDeserializer() {
        this(null);
    }

    public InetAddressDeserializer(Class<?> clazz) {
        super(clazz);
    }

    @Override
    public InetAddress deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        String ipAddress = jp.getValueAsString();
        if (ipAddress == null || ipAddress.isEmpty()) {
            return null;
        }
        try {
            return InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            throw new IOException("Invalid IP address: " + ipAddress, e);
        }
    }
}