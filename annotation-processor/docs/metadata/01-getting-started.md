# Getting Started with Metadata Code Generation

The Cardano Client Library (CCL) includes an annotation processor that generates type-safe converter classes for Cardano transaction metadata. Annotate your Java classes or records with `@MetadataType`, and the processor generates `{ClassName}MetadataConverter` classes that handle serialization to and deserialization from on-chain metadata maps — no manual CBOR encoding required.

## Prerequisites

- **Java 17+**
- **Gradle** / **Maven** build system

## Adding Dependencies

> **Note:** Replace `${ccl.version}` with the latest version from [Maven Central](https://central.sonatype.com/search?q=com.bloxbean.cardano).

### Gradle

```groovy
dependencies {
    // Annotation processor (compile-time only)
    annotationProcessor 'com.bloxbean.cardano:cardano-client-lib-annotation-processor:${ccl.version}'

    // Runtime dependencies
    implementation 'com.bloxbean.cardano:cardano-client-lib-metadata:${ccl.version}'
    implementation 'com.bloxbean.cardano:cardano-client-lib-quicktx:${ccl.version}'

    // A backend provider (pick one)
    implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:${ccl.version}'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Annotation processor (compile-time only)
    annotationProcessor("com.bloxbean.cardano:cardano-client-lib-annotation-processor:${ccl.version}")

    // Runtime dependencies
    implementation("com.bloxbean.cardano:cardano-client-lib-metadata:${ccl.version}")
    implementation("com.bloxbean.cardano:cardano-client-lib-quicktx:${ccl.version}")

    // A backend provider (pick one)
    implementation("com.bloxbean.cardano:cardano-client-backend-blockfrost:${ccl.version}")
}
```

### Maven

```xml
<dependencies>
    <!-- Runtime dependencies -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib-metadata</artifactId>
        <version>${ccl.version}</version>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib-quicktx</artifactId>
        <version>${ccl.version}</version>
    </dependency>

    <!-- A backend provider (pick one) -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
        <version>${ccl.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.bloxbean.cardano</groupId>
                        <artifactId>cardano-client-lib-annotation-processor</artifactId>
                        <version>${ccl.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Annotating Your First Class

Create a simple Java record annotated with `@MetadataType`:

```java
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

@MetadataType
public record TokenInfo(
        @MetadataField(key = "policy_id", required = true) String policyId,
        @MetadataField(key = "asset_name", required = true) String assetName,
        int decimals
) {}
```

The `@MetadataType` annotation tells the processor to generate a converter for this class. `@MetadataField` customizes individual field serialization — here it overrides the metadata map key and marks fields as required during deserialization.

## Building the Project

Run your Gradle build:

```bash
./gradlew clean build
```

The annotation processor runs during compilation and generates Java source files. You can find them in:

```
build/generated/sources/annotationProcessor/java/main/
```

## What Gets Generated

For a class named `TokenInfo`, the processor generates a single converter class:

```
com/example/
├── TokenInfo.java                          # Your source class
└── TokenInfoMetadataConverter.java         # Generated converter
```

The naming convention is always `{ClassName}MetadataConverter`. The converter implements `MetadataConverter<TokenInfo>` with `toMetadataMap` and `fromMetadataMap` methods.

When you specify a label (e.g., `@MetadataType(label = 721)`), the converter also implements `LabeledMetadataConverter<T>`, adding `toMetadata` and `fromMetadata` methods that wrap/unwrap the map under that label.

## Minimal End-to-End Example

Here is a complete example that creates metadata, attaches it to a transaction, submits it, and reads it back from the chain:

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;

// Your model class (with @MetadataType(label = 721))
import com.example.Cip25NftMetadata;
// Generated converter
import com.example.Cip25NftMetadataMetadataConverter;

public class MetadataExample {
    public static void main(String[] args) {
        // 1. Set up backend and account
        var backendService = new BFBackendService(
            "https://cardano-preprod.blockfrost.io/api/", "<your-project-id>");
        var account = new Account(Networks.testnet(), "<your-mnemonic>");

        // 2. Create your metadata object
        Cip25NftMetadata nft = new Cip25NftMetadata();
        nft.setName("MyNFT#001");
        nft.setImage("ipfs://QmXyZ...");

        // 3. Convert to Metadata using the generated converter
        var converter = new Cip25NftMetadataMetadataConverter();
        Metadata metadata = converter.toMetadata(nft);

        // 4. Attach metadata to a transaction and submit
        Tx tx = new Tx()
                .payToAddress(account.baseAddress(), Amount.ada(1.5))
                .attachMetadata(metadata)
                .from(account.baseAddress());

        var result = new QuickTxBuilder(backendService)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println("Tx hash: " + result.getValue());
    }
}
```

## Custom Type Adapters

The processor handles standard Java types out of the box. For custom on-chain representations — such as storing `Instant` as epoch seconds instead of an ISO-8601 string — use the `adapter` attribute:

```java
@MetadataField(adapter = EpochAdapter.class)
private Instant mintedAt;
```

See [Custom Type Adapters](06-advanced-topics.md#custom-type-adapters) for details on implementing your own adapter.

## Next Steps

- [Overview](00-overview.md) — feature highlights, architecture, and documentation roadmap
- [Annotations Reference](02-annotations-reference.md) — full details on `@MetadataType`, `@MetadataField`, `@MetadataIgnore`, and polymorphic annotations
- [Understanding Generated Converters](03-generated-converters.md) — how to use `MetadataConverter` and `LabeledMetadataConverter`
- [Supported Types](04-supported-types.md) — complete list of supported Java types
- [Class Support and Patterns](05-class-support.md) — records, POJOs, Lombok, and inheritance
- [Advanced Topics](06-advanced-topics.md) — custom adapters, polymorphic types, and real-world examples
