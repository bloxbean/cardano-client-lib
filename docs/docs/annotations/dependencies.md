---
description: Annotation Processor Dependency
sidebar_label: Dependencies
sidebar_position: 1
---

# Dependencies

## Maven

Add the following dependency to pom.xml.

```xml
<dependencies>
    ...
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-annotation-processor</artifactId>
        <version>${cardano.client.version}</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

Alternatively, you can also add the following dependency to plugin  section of pom.xml.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.8.1</version>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>com.bloxbean.cardano</groupId>
                <artifactId>cardano-client-annotation-processor</artifactId>
                <version>${cardano.client.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

## Gradle

//TODO
