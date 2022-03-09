package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;

public class KoiosBaseTest {

    protected KoiosBackendService backendService;

    public KoiosBaseTest() {
        backendService = new KoiosBackendService(Constant.KOIOS_TESTNET_URL);
    }
}
