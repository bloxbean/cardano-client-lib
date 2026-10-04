package com.bloxbean.cardano.client.test.graalvm.fixture;

import com.bloxbean.cardano.client.test.graalvm.fixture.other.FixtureHidden;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class FixtureChild extends FixtureParent {
    private FixturePart part;
    @JsonIgnore
    private FixtureHidden hidden;

    public FixturePart getPart() {
        return part;
    }

    public void setPart(FixturePart part) {
        this.part = part;
    }

    public FixtureHidden getHidden() {
        return hidden;
    }
}
