# Getting Started with Blueprint Code Generation

The Cardano Client Library (CCL) includes an annotation processor that generates Java classes from [CIP-57 Plutus blueprint](https://cips.cardano.org/cip/CIP-0057/) JSON files. These generated classes give you type-safe datums, redeemers, and validator wrappers — so you can interact with Cardano smart contracts without manually encoding or decoding Plutus data.

## Prerequisites

- **Java 17+**
- **Gradle** / **Maven**, etc build system
- A compiled Plutus or Aiken smart contract with a **CIP-57 blueprint JSON** file

## Adding Dependencies

> **Note:** Replace `${ccl.version}` with the latest version from [Maven Central](https://central.sonatype.com/search?q=com.bloxbean.cardano).

### Gradle

```groovy
dependencies {
    // Annotation processor (compile-time only)
    annotationProcessor 'com.bloxbean.cardano:cardano-client-lib-annotation-processor:${ccl.version}'

    // Runtime dependencies
    implementation 'com.bloxbean.cardano:cardano-client-lib-plutus:${ccl.version}'
    implementation 'com.bloxbean.cardano:cardano-client-lib-quicktx:${ccl.version}'

    // Optional: pre-built Aiken stdlib types (recommended for Aiken contracts)
    implementation 'com.bloxbean.cardano:cardano-client-lib-plutus-aiken:${ccl.version}'

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
    implementation("com.bloxbean.cardano:cardano-client-lib-plutus:${ccl.version}")
    implementation("com.bloxbean.cardano:cardano-client-lib-quicktx:${ccl.version}")

    // Optional: pre-built Aiken stdlib types (recommended for Aiken contracts)
    implementation("com.bloxbean.cardano:cardano-client-lib-plutus-aiken:${ccl.version}")

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
        <artifactId>cardano-client-lib-plutus</artifactId>
        <version>${ccl.version}</version>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib-quicktx</artifactId>
        <version>${ccl.version}</version>
    </dependency>

    <!-- Optional: pre-built Aiken stdlib types (recommended for Aiken contracts) -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib-plutus-aiken</artifactId>
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
                    <!-- Include if using plutus-aiken shared types -->
                    <path>
                        <groupId>com.bloxbean.cardano</groupId>
                        <artifactId>cardano-client-lib-plutus-aiken</artifactId>
                        <version>${ccl.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Creating the Blueprint Interface

1. **Place your blueprint JSON** in `src/main/resources/blueprint/`. For example:
   ```
   src/main/resources/blueprint/helloworld.json
   ```

2. **Create a marker interface** annotated with `@Blueprint`:

```java
package com.example.myproject;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;

@Blueprint(
    fileInResources = "blueprint/helloworld.json",
    packageName = "com.example.myproject.generated"
)
@ExtendWith(LockUnlockValidatorExtender.class)
public interface MyBlueprint {
}
```

The annotation attributes:
- **`fileInResources`** — path to the blueprint JSON relative to your resources directory
- **`packageName`** — base package for generated classes
- **`file`** — (alternative) absolute file path to the blueprint JSON

The `@ExtendWith` annotation adds high-level convenience methods to the generated validator class. Available extenders:
- `LockUnlockValidatorExtender.class` — lock/unlock operations (also includes deploy)
- `MintValidatorExtender.class` — minting operations (also includes deploy)

You can combine multiple extenders:

```java
@ExtendWith({LockUnlockValidatorExtender.class, MintValidatorExtender.class})
```

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

For a blueprint with a `helloworld.hello_world` validator containing `Owner` datum and `Redeemer` redeemer, you'll get:

```
com/example/myproject/generated/
├── helloworld/
│   ├── HelloWorldValidator.java          # Validator wrapper with lock/unlock methods
│   └── model/
│       ├── Owner.java                    # Datum abstract class
│       ├── Redeemer.java                 # Redeemer abstract class
│       ├── impl/
│       │   ├── OwnerData.java            # Concrete datum with serialization
│       │   └── RedeemerData.java         # Concrete redeemer with serialization
│       └── converter/
│           ├── OwnerConverter.java        # Serialization logic
│           └── RedeemerConverter.java     # Serialization logic
```

## Minimal End-to-End Example

Here's a complete example that deploys a script, locks funds, and unlocks them:

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.PubKeyReceiver;

// These are generated classes:
import com.example.myproject.generated.helloworld.HelloWorldValidator;
import com.example.myproject.generated.helloworld.model.Owner;
import com.example.myproject.generated.helloworld.model.impl.OwnerData;
import com.example.myproject.generated.helloworld.model.impl.RedeemerData;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class HelloWorldExample {
    public static void main(String[] args) {
        // 1. Set up backend and account
        var backendService = new BFBackendService(
            "https://cardano-preprod.blockfrost.io/api/", "<your-project-id>");
        var account = new Account(Networks.testnet(), "<your-mnemonic>");

        // 2. Create validator instance
        var validator = new HelloWorldValidator(Networks.testnet())
                .withBackendService(backendService);

        // 3. Deploy the script as a reference input (optional but recommended)
        var deployResult = validator.deploy(account.baseAddress())
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        // Use the deployed reference input for cheaper future transactions
        validator.withReferenceTxInput(deployResult.getValue(), 0);

        // 4. Create a datum and lock funds at the script address
        Owner datum = new OwnerData();
        datum.setOwner(account.getBaseAddress().getPaymentCredentialHash().get());

        var lockResult = validator.lock(account.baseAddress(), Amount.ada(10), datum)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println("Lock tx: " + lockResult.getValue());

        // 5. Unlock funds from the script address
        var redeemer = new RedeemerData();
        redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

        var receiver = new PubKeyReceiver(account.baseAddress(), Amount.ada(10));

        var unlockResult = validator.unlock(
                    datum, redeemer,
                    List.of(receiver),
                    new ChangeReceiver(account.baseAddress()))
                .feePayer(account.baseAddress())
                .withRequiredSigners(account.getBaseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println("Unlock tx: " + unlockResult.getValue());
    }
}
```

## Next Steps

- [Understanding Generated Code](02-generated-code.md) — learn about the generated class hierarchy
- [Working with Validators](03-using-validators.md) — detailed lock/unlock and minting workflows
- [Shared Types and plutus-aiken](04-shared-types-and-plutus-aiken.md) — reuse Aiken stdlib types
- [Blueprint JSON Format](05-blueprint-json-format.md) — understand the input format
- [Advanced Topics](06-advanced-topics.md) — configuration options and edge cases
