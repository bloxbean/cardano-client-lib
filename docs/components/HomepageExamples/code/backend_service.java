//Blockfrost Backend Service
BackendService backendService =
        new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "<Project_id>")

//Koios Backend Service
BackendService koiosBackendService =
        new KoiosBackendService(Constants.KOIOS_PREPROD_URL);

//Ogmios Backend Service
BackendService ogmiosBackendService = new OgmiosBackendService(OGMIOS_URL);

//Kupo Utxo Service
KupoUtxoService kupoUtxoService = new KupoUtxoService(KUPO_URL);
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(kupoUtxoService);
