# cardano-client-backend-koios

### Cardano Koios Backend implementation for [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)

## Dependency

**Maven**

```
<dependency>
     <groupId>com.bloxbean.cardano</groupId>
     <artifactId>cardano-client-lib</artifactId>
     <version>0.1.5</version>
</dependency>
<dependency>
     <groupId>com.bloxbean.cardano</groupId>
     <artifactId>cardano-client-backend-koios</artifactId>
     <version>0.1.5</version>
</dependency>
```

**Gradle**

```
  implementation('com.bloxbean.cardano:cardano-client-lib:0.1.5')
  implementation('com.bloxbean.cardano:cardano-client-backend-koios:0.1.5')
```

**Get BackendService instance for Cardano Koios backend**

```
BackendService backendService = new KoiosBackendService(<Koios Instance Url>);
```

**Example:**

BackendService using Global Testnet Koios endpoint.

```
BackendService backendService = new KoiosBackendService(Constant.KOIOS_TESTNET_URL);
```

**Note:** You can get other services from BackendService instance. For detailed api usage, check [cardano-client-lib](https://github.com/bloxbean/cardano-client-lib) project

**Build**

From top level project folder (Exp: cd ~/cardano-client-lib)

```
$> ./gradlew -p ./backends/cardano-koios/ clean build
```
