package com.bloxbean.cardano.client.common.model;

import java.util.Objects;

public class Network {

    private int networkId;
    private long protocolMagic;

    public Network(int networkId, long protocolMagic) {
        this.networkId = networkId;
        this.protocolMagic = protocolMagic;
    }

    public int getNetworkId() {
        return networkId;
    }

    public long getProtocolMagic() {
        return protocolMagic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Network network = (Network) o;
        return networkId == network.networkId && protocolMagic == network.protocolMagic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), networkId, protocolMagic);
    }

    @Override
    public String toString() {
        return "Network{" +
                "network_id=" + networkId +
                ", protocol_magic=" + protocolMagic +
                '}';
    }
}
