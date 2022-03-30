# cardano-client-backend-gql

### Cardano GraphQL Backend implementation for [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)

**DEPRECATED**

This backend module has been deprecated. 

Testing scope : Limited


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
     <artifactId>cardano-client-backend-gql</artifactId>
     <version>{version}</version>
</dependency>
```

**Gradle**

```
  implementation('com.bloxbean.cardano:cardano-client-lib:{version}')
  implementation('com.bloxbean.cardano:cardano-client-backend-gql:{version}')
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
$> ./gradlew -p ./backend-modules/cardano-graphql/ clean build
```

**Generate GraphQL classes**

```
$> cd backend-modules/cardano-graphql
$> sh download_schema.sh 
```
