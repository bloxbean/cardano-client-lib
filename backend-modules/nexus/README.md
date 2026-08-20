# cardano-client-backend-nexus

### Nexus Backend implementation for [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)

[Nexus](https://nexus.gerowallet.io) is a multi-chain blockchain data API. This module implements
`BackendService` by wrapping the [nexus-java-client](https://github.com/Gero-Labs/nexus-java-client)
SDK, so the standard Cardano Client Lib services (account, address, asset, block, epoch, metadata,
network, pool, script, transaction, utxo) can run against a Nexus endpoint.

## Dependency

**Maven**

```
<dependency>
     <groupId>com.bloxbean.cardano</groupId>
     <artifactId>cardano-client-lib</artifactId>
     <version>{version}</version>
</dependency>
<dependency>
     <groupId>com.bloxbean.cardano</groupId>
     <artifactId>cardano-client-backend-nexus</artifactId>
     <version>{version}</version>
</dependency>
```

**Gradle**

```
  implementation('com.bloxbean.cardano:cardano-client-lib:{version}')
  implementation('com.bloxbean.cardano:cardano-client-backend-nexus:{version}')
```

## Get a BackendService instance for the Nexus backend

The network is selected via a `Network` argument (Nexus uses a single base URL and switches network
with a `?network=` query param, so mainnet / preprod / preview share one host).

```java
import adlabs.nexus.client.util.Network;

// baseUrl, apiKey, network
BackendService backendService =
        new NexusBackendService("https://nexus.gerowallet.io", "<NEXUS_API_KEY>", Network.MAINNET);
```

There is also a convenience constructor that uses the default Nexus URL and no API key:

```java
BackendService backendService = new NexusBackendService(Network.PREPROD);
```

**Example:**

```java
BackendService backendService =
        new NexusBackendService(Constants.MAINNET_URL, "<NEXUS_API_KEY>", Network.MAINNET);

// most Nexus data endpoints require a valid API key
AssetService assetService = backendService.getAssetService();
Result<Asset> asset = assetService.getAsset("<policyId><assetNameHex>");
```

**Note:** You can get other services from the `BackendService` instance. For detailed api usage, check
the [cardano-client-lib](https://github.com/bloxbean/cardano-client-lib) project.

## Build

From the top level project folder (Exp: cd ~/cardano-client-lib)

```
$> ./gradlew -p ./backend-modules/nexus/ clean build
```

## Limitations

Endpoints with no Nexus SDK equivalent throw `UnsupportedOperationException` (consistent with how the
other backend modules surface their own gaps). These currently include:

* `BlockService::getBlockByNumber`
* `EpochService::getEpoch` (by epoch number)
* `AccountService::getAccountHistory` and `AccountService::getAccountAssets` / `getAllAccountAssets`
* `AssetService::getPolicyAssets` / `getAllPolicyAssets` and `AssetService::getTransactions` (asset transactions)
* `AddressService::getAddressDetails`

Other notes:

* Ordering is not supported where the Nexus endpoint has no `order` parameter; the `order` argument is
  accepted but ignored (e.g. asset addresses).
* Most Nexus data endpoints require a valid API key and an active subscription.
