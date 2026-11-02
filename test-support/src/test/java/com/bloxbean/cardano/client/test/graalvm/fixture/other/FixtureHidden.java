package com.bloxbean.cardano.client.test.graalvm.fixture.other;

/** Only reached through a {@code @JsonIgnore} field, so it never needs an entry. */
public class FixtureHidden {
    private long id;

    public long getId() {
        return id;
    }
}
