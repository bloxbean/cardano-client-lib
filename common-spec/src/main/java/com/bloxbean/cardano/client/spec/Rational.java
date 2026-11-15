package com.bloxbean.cardano.client.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class Rational extends UnitInterval {
    @JsonCreator
    public Rational(@JsonProperty("numerator") BigInteger numerator,
                    @JsonProperty("denominator") BigInteger denominator) {
        super(numerator, denominator);
    }
}
