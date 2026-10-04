package com.bloxbean.cardano.client.test.graalvm;

import com.bloxbean.cardano.client.test.graalvm.fixture.FixtureChild;
import com.bloxbean.cardano.client.test.graalvm.fixture.FixtureParent;
import com.bloxbean.cardano.client.test.graalvm.fixture.FixturePart;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReachabilityMetadataGuardTest {
    private static final String FIXTURE_PACKAGE = "com.bloxbean.cardano.client.test.graalvm.fixture";

    private static ReachabilityMetadataGuard guard(String artifactId) {
        return ReachabilityMetadataGuard.of(FixtureParent.class, artifactId).packages(FIXTURE_PACKAGE);
    }

    @Test
    void upToDateMetadataHasNoProblems() {
        // FixtureChild also reaches FixtureHidden, but only through a @JsonIgnore field and its getter.
        assertThat(guard("guard-fixture-ok").problems()).isEmpty();
    }

    @Test
    void reportsClassWithoutEntry() {
        assertThat(guard("guard-fixture-missing-entry").problems())
                .anyMatch(p -> p.startsWith("missing entry for " + FixturePart.class.getName()));
    }

    @Test
    void reportsInheritedMethodListedOnSubclass() {
        assertThat(guard("guard-fixture-inherited").problems())
                .containsExactly(FixtureChild.class.getName() + ": listed methods not declared by the type (GraalVM resolves"
                        + " them with getDeclaredMethod; list them on the declaring type, or remove them if they no longer"
                        + " exist) [getName()]");
    }

    @Test
    void reportsMissingDeclaredMethod() {
        assertThat(guard("guard-fixture-missing-method").problems())
                .containsExactly(FixturePart.class.getName() + ": missing methods [setValue(int)]");
    }

    @Test
    void reportsUnregisteredFieldType() {
        // FixturePart is not part of the checked set here, and the metadata does not register it.
        assertThat(ReachabilityMetadataGuard.of(FixtureParent.class, "guard-fixture-missing-entry")
                .types(FixtureChild.class.getName(), FixtureParent.class.getName()).problems())
                .containsExactly(FixtureChild.class.getName() + " reaches " + FixturePart.class.getName()
                        + " (field part), which is not registered in any CCL reachability metadata");
    }

    @Test
    void reportsExcludedTypeThatIsStillRegistered() {
        assertThat(guard("guard-fixture-ok").exclude(FixturePart.class.getName()).problems())
                .containsExactly("excluded type is registered (remove its entry): " + FixturePart.class.getName());
    }
}
