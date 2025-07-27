---
description: Plutus Blueprint Code Generation
sidebar_label: Plutus Blueprint Code Generation
sidebar_position: 3
---

# Using Plutus Blueprint Annotations for Code Generation

:::info

**Available in 0.6.0-beta1 and later.**

**This is a preview feature and is subject to change.**

:::

This section describes how to use Plutus Blueprint Annotations to generate code from a Plutus Blueprint JSON file. 
For details about the Plutus Contract Blueprint CIP, refer to the [CIP-57 documentation](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0057).

A Plutus Blueprint JSON file (e.g., `plutus.json`) can be generated during the compilation of your smart contract. For example, 
if you are using Aiken for smart contract development, the `plutus.json` file is automatically generated during the compilation of your project.

## Annotations

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
