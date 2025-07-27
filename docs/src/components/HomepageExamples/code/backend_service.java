// Blockfrost Backend Service (Testnet)
BackendService blockfrostService = new BFBackendService(
    "https://cardano-testnet.blockfrost.io/api/v0/",
    System.getenv("BLOCKFROST_PROJECT_ID")
);

// Koios Community Backend Service  
BackendService koiosService = new KoiosBackendService(
    "https://preprod.koios.rest/api/v1/"
);

// Local Ogmios + Kupo Backend Service
String ogmiosUrl = "ws://localhost:1337";
String kupoUrl = "http://localhost:1442";

OgmiosBackendService ogmiosService = new OgmiosBackendService(ogmiosUrl);
KupoUtxoService kupoUtxoService = new KupoUtxoService(kupoUrl);

// Create custom backend with Ogmios + Kupo
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(kupoUtxoService);
ProtocolParamsSupplier protocolParamsSupplier = 
    new DefaultProtocolParamsSupplier(ogmiosService.getEpochService());
