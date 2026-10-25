package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

/**
 * Jackson deserializer for an {@code Integer} field that must be written as a YAML/JSON integer.
 * Jackson otherwise coerces {@code 2.9} to {@code 2}, {@code -0.5} to {@code 0} and {@code '2'} to {@code 2}
 * before any setter can validate the value.
 */
public class StrictIntegerDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                || parser.getNumberType() != JsonParser.NumberType.INT) {
            throw JsonMappingException.from(parser,
                    parser.currentName() + " must be an integer, but was: " + parser.getText());
        }
        return parser.getIntValue();
    }
}
