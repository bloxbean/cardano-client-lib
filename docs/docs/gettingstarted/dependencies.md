---
description: Maven and Gradle dependencies
sidebar_position: 2
---

# Dependencies

## For release binaries

**For Maven, add the following dependencies to project's pom.xml**

- **Core module**

```xml
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>0.6.3</version>
        </dependency>
```
- **Backend modules**

    - For backend support, use one of the following supported backend module

```xml
        <!-- For Blockfrost backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-blockfrost</artifactId>
            <version>0.6.3</version>
        </dependency>
        
         <!-- For Koios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-koios</artifactId>
            <version>0.6.3</version>
        </dependency>
        
         <!-- For Ogmios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-ogmios</artifactId>
            <version>0.6.3</version>
        </dependency>
```

**For Gradle, add the following dependencies to build.gradle**

- **Core Module**

```
implementation 'com.bloxbean.cardano:cardano-client-lib:0.6.3'
```
- **Backend modules**

    - For backend support, use one of the following supported backend module

```groovy
//For Blockfrost
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.6.3'

//For Koios
implementation 'com.bloxbean.cardano:cardano-client-backend-koios:0.6.3'

//For Ogmios
implementation 'com.bloxbean.cardano:cardano-client-backend-ogmios:0.6.3'

```


## For snapshot binaries

:::info

**SNAPSHOT_VERSION :** 0.5.2-SNAPSHOT (Please verify the latest snapshot version in **gradle.properties**)

:::

- For Maven, add the following dependencies and repository to project's pom.xml
```
    <dependencies>
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>{SNAPSHOT_VERSION}</version>
        </dependency>
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-blockfrost</artifactId>
            <version>{SNAPSHOT_VERSION}</version>
        </dependency>
    </dependencies>
    
    <repositories>
        <repository>
            <id>snapshots-repo</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots</url>
            <releases>
                <enabled>false</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```
- For Gradle, add the following dependencies and repository to build.gradle

```
repositories {
    ...
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots"
    }
}

implementation 'com.bloxbean.cardano:cardano-client-lib:{SNAPSHOT_VERSION}'
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:{SNAPSHOT_VERSION}'
```
