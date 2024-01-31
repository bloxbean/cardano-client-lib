# cardano-client-backend-ogmios

**Status :** Stable

Ogmios Version : 6.0.0 and later
Kupo Version : 2.7.0 and later

This backend module currently supports following services in [Ogmios](https://ogmios.dev) and [Kupo](https://cardanosolutions.github.io/kupo/).
The following apis provides enough data to build and submit a transaction.

Ogmios service provides protocol paramters and transaction submission / evaluation capabilities.

Kupo as a chain-indexer provides unspent transactions for an address.

## Ogmios
- OgmiosTransactionService
```java  
     - Result<String> submitTransaction(byte[] cborData)
     - Result<List<EvaluationResult>> evaluateTx(byte[] cborData)
```
- OgmiosEpochService
```java
     - Result<ProtocolParams> getProtocolParameters()
```

To get an instance of OgmiosBackendService

```java
BackendService backendService = new OgmiosBackendService("http://host:port")

ProtocolParams protocolParams = backendService.getEpochService().getProtocolParameters().getValue();


```

## Kupo 

- KupoUtxoService
```java
    - Result<List<Utxo>> getUtxos(String address, int count, int page)
```

Get an instance of KupoUtxoService

```java
UtxoService kupoUtxoService = new KupoUtxoService("http://<kupo_host>:<port>");
```

Create an utxo supplier to use with TxBuilder api
```java
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(kupoUtxoService);
```

## Kupomios (Ogmios + Kupo)

Kupomios is a combination of Ogmios and Kupo. It provides all the services provided by Ogmios and Kupo
backend services.

To get an instance of KupomiosBackendService

```java
BackendService kupmiosBackendService = new KupmiosBackendService(OGMIOS_HTTP_URL, KUPO_HTTP_URL);
```
