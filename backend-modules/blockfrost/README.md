# cardano-client-backend-blockfrost

### Cardano Blockfrost Backend implementation for [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)

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
     <artifactId>cardano-client-backend-blockfrost</artifactId>
     <version>{version}</version>
</dependency>
```

**Gradle**

```
  implementation('com.bloxbean.cardano:cardano-client-lib:{version}')
  implementation('com.bloxbean.cardano:cardano-client-backend-blockfrost:{version}')
```

**Get BackendService instance for Cardano Blockfrost backend**

```
BackendService backendService = new BFBackendService(<Blockfrost API Url>, <Blockfrost Project Id>)
```

**Example:**

BackendService using Blockfrost Testnet endpoint.

```
BackendService backendService = new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, <Blockfrost_Project_Id>);
```

**Note:** You can get other services from BackendService instance. For detailed api usage, check [cardano-client-lib](https://github.com/bloxbean/cardano-client-lib) project

**Build**

From top level project folder (Exp: cd ~/cardano-client-lib)

```
$> ./gradlew -p ./backend-modules/blockfrost/ clean build
```

