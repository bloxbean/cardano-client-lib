package com.bloxbean.cardano.vds.mpf.compat;

/** Test-only access to the shared fruits list of {@link LegacyStoreFixture}. */
public final class LegacyStoreFixtureAccess {
    private LegacyStoreFixtureAccess() {
    }

    public static String[][] fruits() {
        return LegacyStoreFixture.FRUITS.clone();
    }
}
