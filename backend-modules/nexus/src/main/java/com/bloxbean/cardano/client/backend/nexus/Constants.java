package com.bloxbean.cardano.client.backend.nexus;

public class Constants {

    private Constants() {
    }

    // Nexus selects the Cardano network via a `?network=` query param, so mainnet/preprod/preview share one base URL.
    public static final String MAINNET_URL = "https://nexus.gerowallet.io";
    public static final String PREPROD_URL = "https://nexus.gerowallet.io";
    public static final String PREVIEW_URL = "https://nexus.gerowallet.io";
    public static final String DEFAULT_URL = "https://nexus.gerowallet.io";
}
