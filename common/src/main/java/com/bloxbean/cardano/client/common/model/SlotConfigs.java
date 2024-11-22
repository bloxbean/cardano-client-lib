package com.bloxbean.cardano.client.common.model;

public class SlotConfigs {

    public static SlotConfig mainnet() {
        return new SlotConfig(1000, 4492800L, 1596059091000L);
    }

    public static SlotConfig preprod() {
        return new SlotConfig(1000, 86400L, 1655769600000L);
    }

    public static SlotConfig preview() {
        return new SlotConfig(1000, 0L, 1666656000000L);
    }

}
