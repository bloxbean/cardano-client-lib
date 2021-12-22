# cardano-client-backend-gql

### Cardano GraphQL Backend implementation for [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)

## Dependency

**Maven**

```
<dependency>
     <groupId>com.bloxbean.cardano</groupId>
     <artifactId>cardano-client-lib</artifactId>
     <version>0.1.4</version>
</dependency>
<dependency>
     <groupId>com.bloxbean.cardano</groupId>
     <artifactId>cardano-client-backend-gql</artifactId>
     <version>0.1.4</version>
</dependency>
```

**Gradle**

```
  implementation('com.bloxbean.cardano:cardano-client-lib:0.1.4')
  implementation('com.bloxbean.cardano:cardano-client-backend-gql:0.1.4')
```

**Get BackendService instance for Cardano GraphQL backend**

```
BackendService backendService =
                new GqlBackendService(<Cardano GraphQL Url>);
```

**Example:**

BackendService using Dandelion's GraphQL endpoint.

```
BackendService backendService =
                new GqlBackendService("https://graphql-api.testnet.dandelion.link/");
```

**Note:** You can get other services from BackendService instance. For detailed api usage, check [cardano-client-lib](https://github.com/bloxbean/cardano-client-lib) project

**Build**

From top level project folder (Exp: cd ~/cardano-client-lib)

```
$> ./gradlew -p ./backends/cardano-graphql/ clean build
```

**Generate GraphQL classes**

```
$> cd backends/cardano-graphql
$> sh download_schema.sh 
```
