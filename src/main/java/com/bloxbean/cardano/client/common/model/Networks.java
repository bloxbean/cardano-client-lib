package com.bloxbean.cardano.client.common.model;

public class Networks {
    public static Network.ByReference mainnet() {
        Network.ByReference mainnet = new Network.ByReference();
        mainnet.network_id = 0b0001;
        mainnet.protocol_magic = 764824073;

        return mainnet;
    }

    public static Network.ByReference testnet() {
        Network.ByReference testnet = new Network.ByReference();
        testnet.network_id = 0b0000;
        testnet.protocol_magic = 1097911063;

        return testnet;
    }
}
