---
description: Complete tutorial for Blueprint annotation processor setup and code generation with advanced patterns
sidebar_label: Code Generation Tutorial  
sidebar_position: 3
---

# Blueprint Code Generation Tutorial

This comprehensive tutorial guides you through setting up and using the Blueprint annotation processor to generate type-safe Java classes from Aiken smart contracts. Learn how to configure your build environment, create blueprint interfaces, and work with generated validator classes.

:::info Prerequisites
Before starting this tutorial, ensure you understand [Blueprint Integration](./aiken-blueprint-integration.md) concepts and have an Aiken project with compiled blueprints.
:::

## Overview

The Blueprint code generation system provides:
- **Type-safe validator classes** - Generated from blueprint metadata
- **Automatic PlutusData conversion** - Seamless datum/redeemer handling  
- **Build integration** - Maven/Gradle annotation processor support
- **Extender interfaces** - Additional functionality through composition
- **IDE support** - Generated sources with proper IDE integration

## Prerequisites and Setup

### Project Requirements

- Java 11 or higher
- Maven 3.6+ or Gradle 7.0+
- Aiken-compiled smart contract with blueprint JSON
- Cardano Client Library 0.6.0-beta1 or later

### Aiken Contract Preparation

First, ensure your Aiken contract generates a proper blueprint:

```rust
// validators/token_marketplace.ak
use aiken/hash.{Blake2b_224, Hash}
use aiken/transaction.{ScriptContext}
use aiken/transaction/credential.{VerificationKey}

type MarketplaceDatum {
  seller: Hash<Blake2b_224, VerificationKey>,
  asset_policy: ByteArray,
  asset_name: ByteArray,
  price: Int,
  created_at: Int,
}

type MarketplaceRedeemer {
  action: MarketplaceAction,
}

type MarketplaceAction {
  Buy { buyer: Hash<Blake2b_224, VerificationKey> }
  Cancel
  UpdatePrice { new_price: Int }
}

validator {
  fn marketplace(datum: MarketplaceDatum, redeemer: MarketplaceRedeemer, context: ScriptContext) -> Bool {
    // Implementation logic...
    True
  }
}
```

Build your Aiken project to generate the blueprint:

```bash
cd aiken-project
aiken build
# Generates plutus.json with blueprint
```

## Build Configuration

### Maven Setup

Add the annotation processor and dependencies to your `pom.xml`:

```xml
<properties>
    <cardano.client.version>0.6.6</cardano.client.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>

<dependencies>
    <!-- Core Cardano Client Library -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>${cardano.client.version}</version>
    </dependency>
    
    <!-- QuickTx API for transaction building -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-quicktx</artifactId>
        <version>${cardano.client.version}</version>
    </dependency>
    
    <!-- Annotation processor for code generation -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-annotation-processor</artifactId>
        <version>${cardano.client.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>17</source>
                <target>17</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.bloxbean.cardano</groupId>
                        <artifactId>cardano-client-annotation-processor</artifactId>
                        <version>${cardano.client.version}</version>
                    </path>
                </annotationProcessorPaths>
                <generatedSourcesDirectory>${project.build.directory}/generated-sources/annotations</generatedSourcesDirectory>
            </configuration>
        </plugin>
        
        <!-- Add generated sources to build path -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <version>3.4.0</version>
            <executions>
                <execution>
                    <id>add-source</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-source</goal>
                    </goals>
                    <configuration>
                        <sources>
                            <source>${project.build.directory}/generated-sources/annotations</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Gradle Setup

Add the annotation processor to your `build.gradle`:

```gradle
plugins {
    id 'java'
    id 'java-library'
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

ext {
    cardanoClientVersion = '0.6.0-beta1'
}

dependencies {
    // Core dependencies
    implementation "com.bloxbean.cardano:cardano-client-lib:${cardanoClientVersion}"
    implementation "com.bloxbean.cardano:cardano-client-quicktx:${cardanoClientVersion}"
    
    // Annotation processor
    annotationProcessor "com.bloxbean.cardano:cardano-client-annotation-processor:${cardanoClientVersion}"
    compileOnly "com.bloxbean.cardano:cardano-client-annotation-processor:${cardanoClientVersion}"
    
    // Test dependencies
    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
    testImplementation 'org.assertj:assertj-core:3.24.2'
}

// Configure annotation processing
compileJava {
    options.annotationProcessorGeneratedSourcesDirectory = file("$buildDir/generated/sources/annotationProcessor/java/main")
}

// Add generated sources to source sets
sourceSets {
    main {
        java {
            srcDirs += ["$buildDir/generated/sources/annotationProcessor/java/main"]
        }
    }
}

test {
    useJUnitPlatform()
}
```

### IDE Configuration

**IntelliJ IDEA:**
1. Enable annotation processing: `Settings → Build → Compiler → Annotation Processors`
2. Check "Enable annotation processing"
3. Set processor path to include the annotation processor JAR
4. Mark generated sources directory as "Generated Sources Root"

**Eclipse:**
1. Right-click project → `Properties → Java Build Path → Source`
2. Add the generated sources folder: `target/generated-sources/annotations`
3. Enable annotation processing in project preferences

## Project Structure Setup

Organize your project for optimal blueprint integration:

```
smart-contract-project/
├── aiken/                          # Aiken smart contracts
│   ├── validators/
│   │   ├── marketplace.ak
│   │   └── token_minting.ak
│   ├── lib/
│   ├── aiken.toml
│   └── plutus.json                 # Generated blueprint
├── java/                           # Java integration
│   └── src/main/
│       ├── java/
│       │   └── com/example/
│       │       └── contracts/
│       │           ├── MarketplaceBlueprint.java
│       │           └── TokenMintingBlueprint.java
│       └── resources/
│           └── blueprints/
│               ├── marketplace.json    # Copied from aiken/
│               └── token-minting.json
├── scripts/
│   └── sync-blueprints.sh         # Automation script
└── pom.xml                        # Build configuration
```

Automation script for syncing blueprints:

```bash
#!/bin/bash
# scripts/sync-blueprints.sh

echo "Building Aiken contracts..."
cd aiken && aiken build

echo "Copying blueprints to Java resources..."
cp plutus.json ../java/src/main/resources/blueprints/marketplace.json

echo "Building Java project..."
cd ../java && mvn compile

echo "Blueprint sync complete!"
```

## Blueprint Annotations

Two main annotations can be used to generate code from the Plutus Blueprint JSON file:

- **`@PlutusBlueprint`**: Specifies the path to the Plutus Blueprint JSON file. This annotation should be used at the interface level.
- **`@ExtendWith`**: Adds additional functionalities to the generated validator class through Extender interfaces.

For more information about Extender interfaces, refer to the [Extender Interfaces](#extend-validator-class-with-custom-extender-interfaces) section.


## How to Use

Before using the annotations, copy the Plutus Blueprint JSON file to the resources folder of your project. 
You can name the Plutus JSON file as you prefer, or use `plutus.json` if you only have one blueprint file in the project.

## Example Directory Structure

```text
src
  |- main
      |- resources
          |- hello_world_plutus.json
```

<details>
<summary>Click here to view the Hello World Plutus Blueprint file</summary>

#### Hello World Plutus Blueprint JSON

```json
{
  "preamble": {
    "title": "aiken_lang/hello_word",
    "description": "Aiken contracts for project 'aiken_lang/hello_word'",
    "version": "0.0.0",
    "plutusVersion": "v2",
    "compiler": {
      "name": "Aiken",
      "version": "v1.0.21-alpha+4b04517"
    },
    "license": "Apache-2.0"
  },
  "validators": [
    {
      "title": "hello_world.hello_world",
      "datum": {
        "title": "datum",
        "schema": {
          "$ref": "#/definitions/hello_world~1Datum"
        }
      },
      "redeemer": {
        "title": "redeemer",
        "schema": {
          "$ref": "#/definitions/hello_world~1Redeemer"
        }
      },
      "compiledCode": "58f2010000323232323232323222232325333008323232533300b002100114a06644646600200200644a66602200229404c8c94ccc040cdc78010028a511330040040013014002375c60240026eb0c038c03cc03cc03cc03cc03cc03cc03cc03cc020c008c020014dd71801180400399b8f375c6002600e00a91010d48656c6c6f2c20576f726c6421002300d00114984d958c94ccc020cdc3a400000226464a66601a601e0042930b1bae300d00130060041630060033253330073370e900000089919299980618070010a4c2c6eb8c030004c01401058c01400c8c014dd5000918019baa0015734aae7555cf2ab9f5742ae881",
      "hash": "6fb13cf9efdbe986e784d1983b21d3fb90231c1745925f536a820fb4"
    }
  ],
  "definitions": {
    "ByteArray": {
      "dataType": "bytes"
    },
    "hello_world/Datum": {
      "title": "Datum",
      "anyOf": [
        {
          "title": "Datum",
          "dataType": "constructor",
          "index": 0,
          "fields": [
            {
              "title": "owner",
              "$ref": "#/definitions/ByteArray"
            }
          ]
        }
      ]
    },
    "hello_world/Redeemer": {
      "title": "Redeemer",
      "anyOf": [
        {
          "title": "Redeemer",
          "dataType": "constructor",
          "index": 0,
          "fields": [
            {
              "title": "msg",
              "$ref": "#/definitions/ByteArray"
            }
          ]
        }
      ]
    }
  }
}
```

</details>

<details>
<summary>Click here to view the Aiken file for the above Plutus Blueprint</summary>

```rust
use aiken/hash.{Blake2b_224, Hash}
use aiken/list
use aiken/transaction.{ScriptContext}
use aiken/transaction/credential.{VerificationKey}
 
type Datum {
  owner: Hash<Blake2b_224, VerificationKey>,
}
 
type Redeemer {
  msg: ByteArray,
}
 
validator {
  fn hello_world(datum: Datum, redeemer: Redeemer, context: ScriptContext) -> Bool {
    let must_say_hello =
      redeemer.msg == "Hello, World!"
 
    let must_be_signed =
      list.has(context.transaction.extra_signatories, datum.owner)
 
    must_say_hello && must_be_signed
  }
}
```
</details>

## Define the Plutus Blueprint Interface

You can now create the Plutus Blueprint interface in your project and annotate it with the `@PlutusBlueprint` annotation.

```java
@Blueprint(fileInResources = "hello_world_plutus.json", packageName = "com.example.helloworld")
public interface HelloWorldBlueprint {
}
```

## Generate Validator, Datum, and Redeemer Classes

Now, run Maven compile or Gradle build to compile the project. The validator class, along with the Datum and Redeemer classes,
will be generated in the build folder's generated source folder.

For Maven, the generated classes will be in the 'generated-sources' folder under the target directory.

The following classes are generated:

- Validator class for each validator in the blueprint JSON file.
- Abstract class for Datum and Redeemer of each validator.
- Implementation (Data) class for Datum and Redeemer of each validator, extending the abstract class and providing utility methods to serialize and deserialize the data.
- Converter class for each Datum and Redeemer class, offering utilities to serialize and deserialize the data.


```java
target
  |- generated-sources
      |- com
          |- example
              |- helloworld
                 |- hello_world
                    |- model
                        |- converter
                            |- DatumDataConverter.java
                            |- RedeemerDataConverter.java
                        |- impl
                            |- DatumData.java
                            |- RedeemerData.java
                        |- Datum.java
                        |- Redeemer.java
                    |- HelloWorldValidator.java  
    
```

### Validator Class

The generated validator class includes the following methods by default:

- `getScriptAddress()`: Returns the address of the validator script.
- `getPlutusScript()`: Returns the `PlutusScript` object from the compiled code.

<details>
<summary>Click here to view <b>HelloWorldValidator.java</b></summary>

```java
public class HelloWorldValidator {
  public static final String TITLE = "hello_world.hello_world";

  public static final String DESCRIPTION = null;

  public static final String COMPILED_CODE = "58f2010000323232323232323222232325333008323232533300b002100114a06644646600200200644a66602200229404c8c94ccc040cdc78010028a511330040040013014002375c60240026eb0c038c03cc03cc03cc03cc03cc03cc03cc03cc020c008c020014dd71801180400399b8f375c6002600e00a91010d48656c6c6f2c20576f726c6421002300d00114984d958c94ccc020cdc3a400000226464a66601a601e0042930b1bae300d00130060041630060033253330073370e900000089919299980618070010a4c2c6eb8c030004c01401058c01400c8c014dd5000918019baa0015734aae7555cf2ab9f5742ae881";

  public static final String HASH = "6fb13cf9efdbe986e784d1983b21d3fb90231c1745925f536a820fb4";

  private Network network;

  private String scriptAddress;

  private PlutusScript plutusScript;

  public HelloWorldValidator(Network network) {
    this.network = network;
  }

  public Network getNetwork() {
    return this.network;
  }

  public void setNetwork(Network network) {
    this.network = network;
  }

  /**
   * Returns the address of the validator script
   */
  public String getScriptAddress() {
    if(scriptAddress == null) {
      var script = getPlutusScript();
      scriptAddress = AddressProvider.getEntAddress(script, network).toBech32();
    }
    return scriptAddress;
  }

  public PlutusScript getPlutusScript() {
    if (plutusScript == null) {
      plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(COMPILED_CODE, PlutusVersion.v2);
    }
    return plutusScript;
  }
}
```
</details>

### Datum Class

The generator creates a Datum class for each Datum in the blueprint JSON file. The Datum class contains the fields defined in the Datum schema and is abstract.

<details>
<summary>Click here to view <b>Datum.java</b></summary>


```java
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;

@Constr(
        alternative = 0
)
public abstract class Datum implements Data<Datum> {
    /**
     *  Index: 0
     */
    private byte[] owner;

    public byte[] getOwner() {
        return this.owner;
    }

    public void setOwner(byte[] owner) {
        this.owner = owner;
    }
}
```
</details>

### Redeemer Class

The generator creates a Redeemer class for each Redeemer in the blueprint JSON file. The Redeemer class contains the fields defined in the Redeemer schema and is abstract.

<details>
<summary>Click here to view <b>Redeemer.java</b></summary>


```java
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;

@Constr(
        alternative = 0
)
public abstract class Redeemer implements Data<Redeemer> {
    /**
     *  Index: 0
     */
    private byte[] msg;

    public byte[] getMsg() {
        return this.msg;
    }

    public void setMsg(byte[] msg) {
        this.msg = msg;
    }
}
```
</details>

### Datum Data Class

A `DatumData` class is generated for each Datum class. This class extends the abstract Datum class and provides utility methods to serialize and deserialize the data.

<details>
<summary>Click here to view <b>DatumData.java</b></summary>

```java 
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.example.helloworld.hello_world.model.Datum;
import com.example.helloworld.hello_world.model.converter.DatumConverter;
import java.lang.Override;
import java.lang.String;

/**
 * Auto generated code. DO NOT MODIFY
 */
public class DatumData extends Datum implements Data<Datum> {
  private static DatumConverter converter = new DatumConverter();

  public DatumData() {
  }

  @Override
  public ConstrPlutusData toPlutusData() {
    return converter.toPlutusData((Datum)this);
  }

  public static Datum fromPlutusData(ConstrPlutusData data) {
    return converter.fromPlutusData(data);
  }

  public static Datum deserialize(String cborHex) {
    return converter.deserialize(cborHex);
  }

  public static Datum deserialize(byte[] cborBytes) {
    return converter.deserialize(cborBytes);
  }
}

```

</details>

### Redeemer Data Class

A `RedeemerData` class is generated for each Redeemer class. This class extends the abstract Redeemer class and provides utility methods to serialize and deserialize the data.

<details>
<summary>Click here to view <b>RedeemerData.java</b></summary>

```java 
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.example.helloworld.hello_world.model.Redeemer;
import com.example.helloworld.hello_world.model.converter.RedeemerConverter;
import java.lang.Override;
import java.lang.String;

/**
 * Auto generated code. DO NOT MODIFY
 */
public class RedeemerData extends Redeemer implements Data<Redeemer> {
  private static RedeemerConverter converter = new RedeemerConverter();

  public RedeemerData() {
  }

  @Override
  public ConstrPlutusData toPlutusData() {
    return converter.toPlutusData((Redeemer)this);
  }

  public static Redeemer fromPlutusData(ConstrPlutusData data) {
    return converter.fromPlutusData(data);
  }

  public static Redeemer deserialize(String cborHex) {
    return converter.deserialize(cborHex);
  }

  public static Redeemer deserialize(byte[] cborBytes) {
    return converter.deserialize(cborBytes);
  }
}

```

</details>

### Datum Converter Class

A `DatumConverter` class is generated for each Datum class. This class provides utility methods to serialize and deserialize the data.
Typically, you don't need to use converter classes directly as the `DatumData` class offers these utility methods.

<details>
<summary>Click here to view <b>DatumConverter.java</b></summary>

```java 
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.example.helloworld.hello_world.model.Datum;
import com.example.helloworld.hello_world.model.impl.DatumData;
import java.lang.Exception;
import java.lang.String;
import java.util.Objects;

/**
 * Auto generated code. DO NOT MODIFY
 */
public class DatumConverter extends BasePlutusDataConverter {
  public ConstrPlutusData toPlutusData(Datum obj) {
    ConstrPlutusData constr = initConstr(0);
    //Field owner
    Objects.requireNonNull(obj.getOwner(), "owner cannot be null");
    constr.getData().add(toPlutusData(obj.getOwner()));

    return constr;
  }

  public Datum fromPlutusData(ConstrPlutusData constr) {
    var obj = new DatumData();
    var data = constr.getData();

    //Field owner
    var owner = ((BytesPlutusData)data.getPlutusDataList().get(0)).getValue();
    obj.setOwner(owner);
    return obj;
  }

  public byte[] serialize(Datum obj) {
    Objects.requireNonNull(obj);;
    try {
      var constr = toPlutusData(obj);
      return CborSerializationUtil.serialize(constr.serialize());
    } catch (Exception e) {
      throw new CborRuntimeException(e);
    }
  }

  public String serializeToHex(Datum obj) {
    Objects.requireNonNull(obj);;
    var constr = toPlutusData(obj);
    return constr.serializeToHex();
  }

  public Datum deserialize(byte[] bytes) {
    Objects.requireNonNull(bytes);;
    try {
      var di = CborSerializationUtil.deserialize(bytes);
      var constr = ConstrPlutusData.deserialize(di);
      return fromPlutusData(constr);
    } catch (Exception e) {
      throw new CborRuntimeException(e);
    }
  }

  public Datum deserialize(String hex) {
    Objects.requireNonNull(hex);;
    var bytes = HexUtil.decodeHexString(hex);
    return deserialize(bytes);
  }
}

```

</details>

### Redeemer Converter Class

A `RedeemerConverter` class is generated for each Redeemer class. This class provides utility methods to serialize and deserialize the data. 
Typically, you don't need to use converter classes directly as the `RedeemerData` class offers these utility methods.

<details>
<summary>Click here to view <b>RedeemerConverter.java</b></summary>

```java 
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.example.helloworld.hello_world.model.Redeemer;
import com.example.helloworld.hello_world.model.impl.RedeemerData;
import java.lang.Exception;
import java.lang.String;
import java.util.Objects;

/**
 * Auto generated code. DO NOT MODIFY
 */
public class RedeemerConverter extends BasePlutusDataConverter {
  public ConstrPlutusData toPlutusData(Redeemer obj) {
    ConstrPlutusData constr = initConstr(0);
    //Field msg
    Objects.requireNonNull(obj.getMsg(), "msg cannot be null");
    constr.getData().add(toPlutusData(obj.getMsg()));

    return constr;
  }

  public Redeemer fromPlutusData(ConstrPlutusData constr) {
    var obj = new RedeemerData();
    var data = constr.getData();

    //Field msg
    var msg = ((BytesPlutusData)data.getPlutusDataList().get(0)).getValue();
    obj.setMsg(msg);
    return obj;
  }

  public byte[] serialize(Redeemer obj) {
    Objects.requireNonNull(obj);;
    try {
      var constr = toPlutusData(obj);
      return CborSerializationUtil.serialize(constr.serialize());
    } catch (Exception e) {
      throw new CborRuntimeException(e);
    }
  }

  public String serializeToHex(Redeemer obj) {
    Objects.requireNonNull(obj);;
    var constr = toPlutusData(obj);
    return constr.serializeToHex();
  }

  public Redeemer deserialize(byte[] bytes) {
    Objects.requireNonNull(bytes);;
    try {
      var di = CborSerializationUtil.deserialize(bytes);
      var constr = ConstrPlutusData.deserialize(di);
      return fromPlutusData(constr);
    } catch (Exception e) {
      throw new CborRuntimeException(e);
    }
  }

  public Redeemer deserialize(String hex) {
    Objects.requireNonNull(hex);;
    var bytes = HexUtil.decodeHexString(hex);
    return deserialize(bytes);
  }
}

```

</details>

## Use Datum and Redeemer classes

You can instantiate Datum and Redeemer objects using the generated Data (implementation) classes.

Use the `DatumData.toPlutusData` or `DatumData.fromPlutusData` methods to convert the Data object to a PlutusData object and vice versa.

Use the `DatumData.serialize` or `DatumData.deserialize` methods to serialize and deserialize the data.


```java
DatumData datum = new DatumData();
```

## Extend Validator Class with Custom Extender Interfaces

You can extend the generated validator class with custom Extender interfaces to provide additional functionalities. 
Extenders are interfaces with default methods that can add functionalities to the generated validator class. Each extender interface extends the `ValidatorExtender` interface.

Several built-in extender interfaces are available, and they can be added to the validator class using the `@ExtendWith` annotation. 
You can also write your own extender interfaces by extending the `ValidatorExtender` interface and use them in the validator class.

Available built-in extender interfaces include:

- **LockUnlockValidatorExtender**: Provides methods to lock and unlock the validator script.
- **MintValidatorExtender**: Provides common methods to mint tokens.
- **DeployValidatorExtender**: Adds functionality to create an output with a reference script for the validator.

**Note**: Additional extender interfaces will be added in the future.

The methods in the built-in extender interfaces are compatible with the **QuickTx API**.

Let's see how to extend the validator class with `ValidatorExtender` interfaces:

```java
@Blueprint(fileInResources = "hello_world_plutus.json", packageName = "com.example.helloworld")
@ExtendWith({LockUnlockValidatorExtender.class, MintValidatorExtender.class})
public interface HelloWorldBlueprint {
    
}
```

In this example, we use both the `LockUnlockValidatorExtender` and `MintValidatorExtender` interfaces to extend the validator class.
Since `LockUnlockValidatorExtender` extends `DeployValidatorExtender`, the validator class will also inherit methods from `DeployValidatorExtender`.

After compiling the project again, the generated validator class will extend the `LockUnlockValidatorExtender` and `MintValidatorExtender` interfaces.

```java
public class HelloWorldValidator extends AbstractValidatorExtender<HelloWorldValidator>
        implements LockUnlockValidatorExtender<HelloWorldValidator>, MintValidatorExtender<HelloWorldValidator> 
``` 

## Using the Extended Validator Class

You can now use the extended validator class to lock, unlock, mint tokens, and create outputs with reference scripts.

Here's an example of using the extended validator class to lock and unlock the validator script. First, a reference output is created with the `deploy` method, and then the `lock` and `unlock` methods are used to lock and unlock the validator script.

<details>
<summary>Click here to see how to lock and unlock using <b>HelloWorldValidator</b></summary>

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.example.helloworld.hello_world.HelloWorldValidator;
import com.example.helloworld.hello_world.model.Datum;
import com.example.helloworld.hello_world.model.impl.DatumData;
import com.example.helloworld.hello_world.model.impl.RedeemerData;

public class HelloWorldTest {
    private static String mnemonic = "test test test test test test test test test test test test test test test test test test test test test test test sauce";
    private static Account account = new Account(Networks.testnet(), mnemonic);
    private static BFBackendService backendService = new BFBackendService("http://localhost:8080/api/v1/", "");

    private HelloWorldValidator helloWorldValidator;

    public HelloWorldTest() {
        this.helloWorldValidator = new HelloWorldValidator(Networks.testnet())
                .withBackendService(backendService);
    }

    public void deploy() {
        var deployTx = helloWorldValidator.deploy(account.baseAddress())
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        this.helloWorldValidator
                        .withReferenceTxInput(deployTx.getValue(), 0);

        System.out.println("Deploy Tx:" + deployTx);
    }

    public Datum lock() {
        var datum = new DatumData();
        datum.setOwner(account.getBaseAddress().getPaymentCredentialHash().get());

        var lockTx = helloWorldValidator.lock(account.baseAddress(), Amount.ada(20), datum)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);

        System.out.println("Lock Tx: " + lockTx);

        return datum;
    }

    public void unlock(Datum datum) {
        var redeemerData = new RedeemerData();
        redeemerData.setMsg("Hello, World!".getBytes());

        var unlockTx = helloWorldValidator.unlockToAddress(datum, redeemerData, account.baseAddress())
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withRequiredSigners(account.getBaseAddress())
                .completeAndWait(System.out::println);

        System.out.println("Unlock Tx: " + unlockTx);
    }

    public static void main(String[] args) {
        HelloWorldTest helloWorldTest = new HelloWorldTest();
        //Deploy and create reference input
        helloWorldTest.deploy();

        //Lock ADA
        var datum = helloWorldTest.lock();

        //Unlock ADA
        helloWorldTest.unlock(datum);

    }
}

```
</details>


## Composing Validator Transactions with Other Transactions or Script Transactions

If you want to compose the validator transaction described above with other transactions, the out-of-box extenders provide
additional methods that return a `ScriptTx` or `Tx` object. These `ScriptTx` or `Tx` objects can then be composed with other transactions (Tx) or script transactions (ScriptTx).

Typically, these methods have names that end with "Tx". For example, in `LockUnlockExtender.java`, here are a few methods that return a `Tx` or `ScriptTx` object:

- `Tx lockTx(String fromAddress, Amount amount, Data datum)`
- `ScriptTx unlockToAddressTx(Data inputDatum, Data redeemer, String receiver)`
- `ScriptTx unlockToContractTx(Data inputDatum, Data redeemer, String receiver, Data outputData)`

## Advanced Blueprint Patterns

### Complex Data Type Generation

For more complex Aiken types, the code generator creates sophisticated Java class hierarchies:

```rust
// Complex Aiken contract with sum types and complex data structures
type OrderStatus {
  Pending
  Confirmed { confirmation_id: ByteArray }
  Shipped { tracking_number: ByteArray, carrier: ByteArray }
  Delivered { signature: ByteArray, delivery_time: Int }
  Cancelled { reason: ByteArray }
}

type OrderDatum {
  order_id: ByteArray,
  buyer: Hash<Blake2b_224, VerificationKey>,
  seller: Hash<Blake2b_224, VerificationKey>,
  items: List<OrderItem>,
  total_price: Int,
  currency: ByteArray,
  status: OrderStatus,
  metadata: Dict<ByteArray, ByteArray>,
  created_at: Int,
  expires_at: Option<Int>,
}

type OrderItem {
  item_id: ByteArray,
  quantity: Int,
  unit_price: Int,
  metadata: Option<Dict<ByteArray, ByteArray>>,
}
```

This generates comprehensive Java classes:

```java
// Generated enum for OrderStatus
public enum OrderStatus implements Data<OrderStatus> {
    PENDING(0),
    CONFIRMED(1),
    SHIPPED(2), 
    DELIVERED(3),
    CANCELLED(4);
    
    // Implementation details...
}

// Generated classes for each variant
@Constr(alternative = 1)
public class ConfirmedStatus extends OrderStatus {
    private byte[] confirmationId;
    // Getters, setters, conversion methods
}

// Main order datum with complex nested types
@Constr(alternative = 0)
public class OrderDatum implements Data<OrderDatum> {
    private byte[] orderId;
    private byte[] buyer;
    private byte[] seller;
    private List<OrderItem> items;
    private BigInteger totalPrice;
    private byte[] currency;
    private OrderStatus status;
    private Map<byte[], byte[]> metadata;
    private BigInteger createdAt;
    private Optional<BigInteger> expiresAt;
    
    // Complete implementation with type-safe conversion
}
```

### Multi-Validator Blueprint Integration

For blueprints containing multiple validators:

```java
@Blueprint(fileInResources = "marketplace.json", packageName = "com.example.marketplace")
@ExtendWith({LockUnlockValidatorExtender.class})
public interface MarketplaceBlueprint {
}
```

This generates separate validator classes for each validator in the blueprint:

```java
// Generated classes:
// - OrderValidator (for order management)
// - PaymentValidator (for escrow payments)  
// - DisputeValidator (for dispute resolution)

// Each with their own datum/redeemer types:
// - OrderDatum, OrderRedeemer
// - PaymentDatum, PaymentRedeemer
// - DisputeDatum, DisputeRedeemer
```

### Custom Extender Interface Creation

Create your own extender interfaces for specialized functionality:

```java
// Custom extender for NFT marketplace functionality
public interface NFTMarketplaceExtender<T extends ValidatorExtender<T>> extends LockUnlockValidatorExtender<T> {
    
    default Tx listItemTx(String fromAddress, NFTListing listing) {
        return new Tx()
            .payTo(getScriptAddress(), Amount.ada(2.0))
            .attachDatum(listing.toPlutusData())
            .from(fromAddress);
    }
    
    default ScriptTx purchaseItemTx(NFTListing listing, PurchaseRedeemer redeemer, String buyerAddress) {
        return new ScriptTx()
            .collectFrom(findListingUtxo(listing), redeemer.toPlutusData())
            .payTo(listing.getSeller(), Amount.ada(listing.getPrice()))
            .payTo(buyerAddress, listing.getNftAsset())
            .attachSpendingValidator(getPlutusScript());
    }
    
    default Utxo findListingUtxo(NFTListing listing) {
        // Custom UTXO lookup logic
        return getUtxoSupplier().getUtxos(getScriptAddress()).stream()
            .filter(utxo -> matchesListing(utxo, listing))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Listing not found"));
    }
    
    private boolean matchesListing(Utxo utxo, NFTListing listing) {
        // Implementation for matching UTXO to listing
        return true;
    }
}
```

Use the custom extender:

```java
@Blueprint(fileInResources = "nft-marketplace.json", packageName = "com.example.nft")
@ExtendWith({NFTMarketplaceExtender.class})
public interface NFTMarketplaceBlueprint {
}
```

### Parameterized Validator Support

For validators with parameters (planned feature):

```java
// Future parameterized validator support
@Blueprint(fileInResources = "timelock.json", packageName = "com.example.timelock")
public interface TimelockBlueprint {
}

// Usage with parameters
TimelockValidator validator = new TimelockValidator(Networks.testnet())
    .withParameters(
        ownerHash,
        BigInteger.valueOf(System.currentTimeMillis() + 3600000) // 1 hour timeout
    );
```

## Testing Generated Classes

### Unit Testing Blueprint Classes

```java
@Test
public class OrderDatumTest {
    
    @Test
    public void testOrderDatumSerialization() {
        // Create test data
        OrderDatum order = new OrderDatum();
        order.setOrderId("ORDER-001".getBytes());
        order.setBuyer("buyer_hash".getBytes());
        order.setSeller("seller_hash".getBytes());
        order.setTotalPrice(BigInteger.valueOf(100000000)); // 100 ADA
        order.setCurrency("ADA".getBytes());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(BigInteger.valueOf(System.currentTimeMillis()));
        
        // Test PlutusData conversion
        ConstrPlutusData plutusData = order.toPlutusData();
        assertNotNull(plutusData);
        assertEquals(0, plutusData.getAlternative());
        
        // Test round-trip serialization
        String hex = plutusData.serializeToHex();
        PlutusData deserialized = PlutusData.deserialize(HexUtil.decodeHexString(hex));
        OrderDatum restored = OrderDatum.fromPlutusData((ConstrPlutusData) deserialized);
        
        assertArrayEquals(order.getOrderId(), restored.getOrderId());
        assertEquals(order.getTotalPrice(), restored.getTotalPrice());
        assertEquals(order.getStatus(), restored.getStatus());
    }
    
    @Test
    public void testComplexOrderStatusHandling() {
        // Test enum variants with data
        ConfirmedStatus confirmed = new ConfirmedStatus();
        confirmed.setConfirmationId("CONF-123".getBytes());
        
        ConstrPlutusData confirmedData = confirmed.toPlutusData();
        assertEquals(1, confirmedData.getAlternative()); // Confirmed = index 1
        
        // Test conversion back
        OrderStatus restored = OrderStatus.fromPlutusData(confirmedData);
        assertTrue(restored instanceof ConfirmedStatus);
        assertArrayEquals("CONF-123".getBytes(), 
            ((ConfirmedStatus) restored).getConfirmationId());
    }
}
```

### Integration Testing with Validators

```java
@SpringBootTest
@TestPropertySource(properties = "cardano.network=testnet")
public class MarketplaceValidatorIntegrationTest {
    
    @Autowired
    private BackendService backendService;
    
    private MarketplaceValidator validator;
    private Account sellerAccount;
    private Account buyerAccount;
    
    @BeforeEach
    public void setup() {
        validator = new MarketplaceValidator(Networks.testnet())
            .withBackendService(backendService);
            
        sellerAccount = new Account(Networks.testnet(), TEST_MNEMONIC_1);
        buyerAccount = new Account(Networks.testnet(), TEST_MNEMONIC_2);
    }
    
    @Test
    public void testCompleteMarketplaceWorkflow() {
        // 1. Deploy validator
        Result<String> deployTx = validator.deploy(sellerAccount.baseAddress())
            .feePayer(sellerAccount.baseAddress())
            .withSigner(SignerProviders.signerFrom(sellerAccount))
            .completeAndWait(System.out::println);
            
        assertThat(deployTx.isSuccessful()).isTrue();
        validator.withReferenceTxInput(deployTx.getValue(), 0);
        
        // 2. Create listing
        OrderDatum listing = createTestListing();
        Result<String> listTx = validator.lock(
            sellerAccount.baseAddress(),
            Amount.ada(2.0), 
            listing
        )
        .feePayer(sellerAccount.baseAddress())
        .withSigner(SignerProviders.signerFrom(sellerAccount))
        .completeAndWait(System.out::println);
        
        assertThat(listTx.isSuccessful()).isTrue();
        
        // 3. Purchase item
        PurchaseRedeemer purchase = new PurchaseRedeemer();
        purchase.setBuyer(buyerAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        
        Result<String> purchaseTx = validator.unlock(listing, purchase, buyerAccount.baseAddress())
            .feePayer(buyerAccount.baseAddress())
            .withSigner(SignerProviders.signerFrom(buyerAccount))
            .completeAndWait(System.out::println);
            
        assertThat(purchaseTx.isSuccessful()).isTrue();
    }
    
    private OrderDatum createTestListing() {
        OrderDatum listing = new OrderDatum();
        listing.setOrderId("TEST-ORDER-001".getBytes());
        listing.setSeller(sellerAccount.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        listing.setTotalPrice(BigInteger.valueOf(50000000)); // 50 ADA
        listing.setCurrency("ADA".getBytes());
        listing.setStatus(OrderStatus.PENDING);
        listing.setCreatedAt(BigInteger.valueOf(System.currentTimeMillis()));
        return listing;
    }
}
```

## Troubleshooting and Common Issues

### Build-time Issues

**Problem**: Annotation processor not running
```
error: cannot find symbol
class MarketplaceValidator
```

**Solution**: 
1. Verify annotation processor dependency is correctly configured
2. Check that the blueprint JSON file exists in resources
3. Ensure annotation processing is enabled in IDE

**Maven Debug**:
```bash
mvn clean compile -X
# Look for annotation processor execution logs
```

**Gradle Debug**:
```bash
./gradlew clean compileJava --debug
# Check annotation processor execution
```

### Runtime Issues

**Problem**: Blueprint loading fails
```
BlueprintLoadException: Failed to load blueprint: blueprints/marketplace.json
```

**Solutions**:
1. Verify file path is correct in `@Blueprint` annotation
2. Check JSON syntax in blueprint file
3. Ensure file is in classpath

```java
// Debug blueprint loading
try {
    InputStream stream = getClass().getResourceAsStream("/blueprints/marketplace.json");
    if (stream == null) {
        System.err.println("Blueprint file not found in classpath");
    }
} catch (Exception e) {
    e.printStackTrace();
}
```

**Problem**: Generated classes compilation errors
```
error: cannot find symbol
method toPlutusData()
```

**Solutions**:
1. Clean and rebuild project: `mvn clean compile`
2. Check that generated sources are added to classpath
3. Verify `Data<T>` interface is properly imported

**Problem**: Schema validation errors
```
SchemaValidationException: Unsupported data type: custom_type
```

**Solutions**:
1. Check Aiken contract uses supported data types
2. Update to latest library version
3. Use standard Aiken types (ByteArray, Int, List, etc.)

### Performance Issues

**Problem**: Slow code generation
**Solutions**:
1. Exclude generated sources from version control
2. Use incremental compilation
3. Consider splitting large blueprints into smaller ones

**Problem**: Large generated classes
**Solutions**:
1. Optimize Aiken data structures
2. Use more specific types instead of generic ByteArray
3. Consider breaking complex types into smaller components

## Best Practices

### 1. Blueprint Organization

```
project-structure/
├── aiken/
│   ├── validators/
│   │   ├── core/           # Core business logic
│   │   ├── governance/     # Governance-specific
│   │   └── utilities/      # Helper validators
│   └── plutus.json
├── java/
│   └── src/main/
│       ├── java/
│       │   └── contracts/
│       │       ├── core/
│       │       ├── governance/
│       │       └── utilities/
│       └── resources/blueprints/
└── tests/
    ├── unit/
    ├── integration/
    └── e2e/
```

### 2. Code Generation Hygiene

```java
// Good: Separate concerns with multiple blueprint interfaces
@Blueprint(fileInResources = "core-validators.json", packageName = "com.app.core")
public interface CoreBlueprint {}

@Blueprint(fileInResources = "governance.json", packageName = "com.app.governance") 
public interface GovernanceBlueprint {}

// Avoid: Single large blueprint for everything
@Blueprint(fileInResources = "everything.json", packageName = "com.app")
public interface EverythingBlueprint {} // Don't do this
```

### 3. Version Management

```java
// Version-aware blueprint loading
public class BlueprintVersionManager {
    private static final String MIN_SUPPORTED_VERSION = "1.0.0";
    
    public static void validateBlueprint(PlutusContractBlueprint blueprint) {
        String version = blueprint.getPreamble().getVersion();
        if (isVersionSupported(version)) {
            throw new UnsupportedVersionException("Blueprint version " + version + " not supported");
        }
    }
    
    private static boolean isVersionSupported(String version) {
        // Implement semantic version checking
        return compareVersions(version, MIN_SUPPORTED_VERSION) >= 0;
    }
}
```

### 4. Error Handling Strategy

```java
// Comprehensive error handling for blueprint operations
public class BlueprintErrorHandler {
    
    public static <T> Result<T> safeExecute(Supplier<T> operation, String operationName) {
        try {
            T result = operation.get();
            return Result.success(result);
        } catch (BlueprintLoadException e) {
            return Result.error("Blueprint loading failed for " + operationName + ": " + e.getMessage());
        } catch (SchemaValidationException e) {
            return Result.error("Schema validation failed for " + operationName + ": " + e.getMessage());
        } catch (Exception e) {
            return Result.error("Unexpected error in " + operationName + ": " + e.getMessage());
        }
    }
}

// Usage
Result<MarketplaceValidator> validatorResult = BlueprintErrorHandler.safeExecute(
    () -> new MarketplaceValidator(Networks.testnet()),
    "validator creation"
);

if (!validatorResult.isSuccessful()) {
    logger.error("Failed to create validator: " + validatorResult.getError());
    return;
}
```

This comprehensive tutorial provides everything needed to successfully implement Blueprint code generation in production applications, from basic setup to advanced patterns and troubleshooting strategies.
