package com.bloxbean.cardano.client.test.graalvm.fixture.builder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/** Deserialized through its builder, as Lombok's {@code @Jacksonized @Builder} generates. */
@JsonDeserialize(builder = FixtureImmutable.Builder.class)
public class FixtureImmutable {
    private final String name;

    private FixtureImmutable(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public FixtureImmutable build() {
            return new FixtureImmutable(name);
        }
    }
}
