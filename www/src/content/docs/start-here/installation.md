---
title: "Installation"
description: Maven and Gradle dependencies
---

## Versions

- **Latest stable:** 0.7.3
- **Latest preview:** 0.8.0-pre7

Previews contain the newest features, such as TxFlow, TxStream, TxPlan and verified data structures, and their APIs may change before the stable release. See [what's new](/project/whats-new/) and the [release notes](https://github.com/bloxbean/cardano-client-lib/releases).

The stable release needs Java 11 or later, and previews need Java 17 or later.

Throughout this site, replace `<version>` or `{version}` in a dependency with the version you're using. Pages for preview features need the latest preview.

## For release binaries

**For Maven, add the following dependencies to project's pom.xml**

- **Core module**

```xml
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>{version}</version>
        </dependency>
```
- **Backend modules**

    - For backend support, use one of the following supported backend module

```xml
        <!-- For Blockfrost backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-blockfrost</artifactId>
            <version>{version}</version>
        </dependency>

         <!-- For Koios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-koios</artifactId>
            <version>{version}</version>
        </dependency>

         <!-- For Ogmios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-ogmios</artifactId>
            <version>{version}</version>
        </dependency>
```

**For Gradle, add the following dependencies to build.gradle**

- **Core Module**

```
implementation 'com.bloxbean.cardano:cardano-client-lib:<version>'
```
- **Backend modules**

    - For backend support, use one of the following supported backend module

```groovy
//For Blockfrost
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:<version>'

//For Koios
implementation 'com.bloxbean.cardano:cardano-client-backend-koios:<version>'

//For Ogmios
implementation 'com.bloxbean.cardano:cardano-client-backend-ogmios:<version>'

```


## For snapshot binaries

>
> **SNAPSHOT_VERSION :** the version in [gradle.properties](https://github.com/bloxbean/cardano-client-lib/blob/master/gradle.properties), which ends in `-SNAPSHOT`

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
            <url>https://central.sonatype.com/repository/maven-snapshots</url>
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
        url "https://central.sonatype.com/repository/maven-snapshots"
    }
}

implementation 'com.bloxbean.cardano:cardano-client-lib:{SNAPSHOT_VERSION}'
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:{SNAPSHOT_VERSION}'
```
