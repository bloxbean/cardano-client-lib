package com.bloxbean.cardano.client.backend.ogmios;

public class OgmiosBaseTest {
    protected OgmiosBackendService backendService;

    public OgmiosBaseTest() {
        this.backendService = new OgmiosBackendService("ws://192.168.0.228:1337");
    }
}
