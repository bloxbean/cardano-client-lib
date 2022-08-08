# cardano-client-backend-ogmios

**Status :** Beta

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
BackendService backendService = new OgmiosBackendService("ws://host:port")

ProtocolParams protocolParams = backendService.getEpochService().getProtocolParameters().getValue();


```
**Note:** If you want to query other data from Ogmios server, you can use [ogmios-java-client](https://github.com/adabox-aio/ogmios-java-client)
 library. (Maintained by [adabox-aio](https://adabox.io/))

<em>Some of the classes in Ogmios backend module were originally written for [ogmios-java-client](https://github.com/adabox-aio/ogmios-java-client), 
but copied to this module to provide only minimum required apis.</em>

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
