package com.bloxbean.cardano.client.spec;

public enum EraSerializationConfig {
    INSTANCE();

    private boolean useConwayEraFormat = false;

    EraSerializationConfig() {
    }

    public boolean useConwayEraFormat() {
        return useConwayEraFormat;
    }

    public void setUseConwayEraFormat(boolean useConwayEraFormat) {
        this.useConwayEraFormat = useConwayEraFormat;
    }
}
