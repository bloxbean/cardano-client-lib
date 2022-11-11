package com.bloxbean.cardano.client.common.model;

public class Networks {
    public static Network mainnet() {
        Network mainnet = new Network(0b0001, 764824073);
        return mainnet;
    }

    public static Network testnet() {
        Network testnet = new Network(0b0000, 1097911063);
        return testnet;
    }

    public static Network preprod() {
        Network preprod = new Network(0b0000, 1);
        return preprod;
    }

    public static Network preview() {
        Network preview = new Network(0b0000, 2);
        return preview;
    }
}
