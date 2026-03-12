# Understanding Generated Code

When you build your project with a `@Blueprint`-annotated interface, the annotation processor generates several types of Java classes from the blueprint JSON. This document explains what gets generated and how to use it.

## Package Structure

Generated classes are organized under the `packageName` you specified in the `@Blueprint` annotation:

```
<packageName>/
├── <validatorModule>/
│   ├── <ValidatorName>Validator.java        # Validator wrapper
│   └── model/
│       ├── <TypeName>.java                  # Abstract model classes or interfaces
│       ├── <EnumName>.java                  # Enum types
│       ├── <interfacename>/                 # Sub-package for interface variants
│       │   └── <VariantName>.java           # Variant implementing the interface
│       ├── impl/
│       │   └── <TypeName>Data.java          # Concrete implementations with serialization
│       └── converter/
│           └── <TypeName>Converter.java     # Serialization/deserialization logic
```

For example, with `packageName = "com.example.generated"` and a `helloworld.hello_world` validator:

```
com/example/generated/
├── helloworld/
│   ├── HelloWorldValidator.java
│   └── model/
│       ├── Owner.java
│       ├── Redeemer.java
│       ├── impl/
│       │   ├── OwnerData.java
│       │   └── RedeemerData.java
│       └── converter/
│           ├── OwnerConverter.java
│           └── RedeemerConverter.java
```

## Schema Type Mapping

The annotation processor classifies each definition in the blueprint and generates the appropriate Java type.

### Product Types (Single Constructor with Fields)

A definition with `anyOf` containing a single constructor maps to an **abstract class**:

```json
{
  "title": "Owner",
  "anyOf": [{
    "title": "Owner",
    "dataType": "constructor",
    "index": 0,
    "fields": [
      { "title": "owner", "$ref": "#/definitions/ByteArray" }
    ]
  }]
}
```

Generates:

```java
// Owner.java — abstract model class
@Constr(alternative = 0)
public abstract class Owner implements Data<Owner> {
    private byte[] owner;

    public byte[] getOwner() { return owner; }
    public void setOwner(byte[] owner) { this.owner = owner; }
}

// impl/OwnerData.java — concrete implementation
public class OwnerData extends Owner implements Data<Owner> {
    private static OwnerConverter converter = new OwnerConverter();

    @Override
    public ConstrPlutusData toPlutusData() {
        return converter.toPlutusData(this);
    }

    public static Owner fromPlutusData(ConstrPlutusData data) {
        return converter.fromPlutusData(data);
    }

    public static Owner deserialize(String cborHex) {
        return converter.deserialize(cborHex);
    }

    public static Owner deserialize(byte[] cborBytes) {
        return converter.deserialize(cborBytes);
    }
}
```

### Enum Types (Multiple Constructors, No Fields)

A definition with multiple constructors where **none** have fields maps to a **Java enum**:

```json
{
  "title": "Action",
  "anyOf": [
    { "title": "Mint", "dataType": "constructor", "index": 0, "fields": [] },
    { "title": "Burn", "dataType": "constructor", "index": 1, "fields": [] }
  ]
}
```

Generates:

```java
public enum Action {
    Mint(0),
    Burn(1);

    private final int alternative;
    Action(int alternative) { this.alternative = alternative; }
    // ...
}
```

### Sum Types / Interfaces (Multiple Constructors, Some with Fields)

A definition with multiple constructors where **at least one** has fields maps to an **interface** with variant classes in a **sub-package** named after the interface:

```json
{
  "title": "Credential",
  "anyOf": [
    {
      "title": "VerificationKey",
      "dataType": "constructor",
      "index": 0,
      "fields": [{ "title": "hash", "$ref": "#/definitions/ByteArray" }]
    },
    {
      "title": "Script",
      "dataType": "constructor",
      "index": 1,
      "fields": [{ "title": "hash", "$ref": "#/definitions/ByteArray" }]
    }
  ]
}
```

Generates:

```java
// model/Credential.java — marker interface (root model package)
@Constr
public interface Credential {
}

// model/credential/VerificationKey.java — variant class (sub-package)
@Constr(alternative = 0)
public abstract class VerificationKey implements Data<VerificationKey>, Credential {
    private byte[] hash;
    // getters/setters
}

// model/credential/Script.java — variant class (sub-package)
@Constr(alternative = 1)
public abstract class Script implements Data<Script>, Credential {
    private byte[] hash;
    // getters/setters
}

// Plus corresponding *Data and *Converter classes in impl/ and converter/ sub-packages
```

The sub-package name is the interface name lowercased (e.g., `Credential` → `credential`, `PaymentCredential` → `paymentcredential`). This avoids naming collisions — both `credential.VerificationKey` and `paymentcredential.VerificationKey` can coexist.

The interface converter dispatches based on the `alternative` index:

```java
// model/converter/CredentialConverter.java
public class CredentialConverter {
    public ConstrPlutusData toPlutusData(Credential credential) {
        if (credential instanceof VerificationKey) {
            return new VerificationKeyConverter()
                .toPlutusData((VerificationKey) credential);
        }
        if (credential instanceof Script) {
            return new ScriptConverter()
                .toPlutusData((Script) credential);
        }
        throw new CborRuntimeException("Unsupported type: " + credential.getClass());
    }

    public Credential fromPlutusData(ConstrPlutusData constr) {
        if (constr.getAlternative() == 0)
            return new VerificationKeyConverter().fromPlutusData(constr);
        if (constr.getAlternative() == 1)
            return new ScriptConverter().fromPlutusData(constr);
        throw new CborRuntimeException("Invalid alternative: " + constr.getAlternative());
    }
}
```

## The `Data<T>` Interface

All generated model classes implement `Data<T>`:

```java
public interface Data<T> {
    ConstrPlutusData toPlutusData();
}
```

This is the core serialization contract. Call `toPlutusData()` to convert any model object to its CBOR-compatible `ConstrPlutusData` representation.

## The `RawData` Interface

Types that serialize to raw `PlutusData` (not wrapped in a constructor) implement `RawData`:

```java
public interface RawData {
}
```

For example, byte array wrapper types like `VerificationKeyHash` implement `RawData` and serialize directly to `BytesPlutusData`.

## Serialization and Deserialization

### Creating Objects

Always instantiate the `*Data` class (from the `impl` package), not the abstract parent:

```java
// Correct
Owner owner = new OwnerData();
owner.setOwner(someBytes);

// For enums, use directly
Action action = Action.Mint;
```

### Serializing to PlutusData

```java
ConstrPlutusData plutusData = owner.toPlutusData();
```

### Deserializing from PlutusData

```java
Owner owner = OwnerData.fromPlutusData(constrPlutusData);
```

### CBOR Serialization

```java
// Serialize to CBOR hex
OwnerConverter converter = new OwnerConverter();
String hex = converter.serializeToHex(owner);
byte[] bytes = converter.serialize(owner);

// Deserialize from CBOR
Owner owner = OwnerData.deserialize(hex);
Owner owner = OwnerData.deserialize(bytes);
```

## Primitive Type Mapping

| Plutus Type | JSON Schema | Java Type |
|---|---|---|
| ByteArray / Bytes | `"dataType": "bytes"` | `byte[]` |
| Integer | `"dataType": "integer"` | `BigInteger` |
| String | `"dataType": "#string"` | `String` |
| Bool | Built-in | `boolean` |
| List\<T\> | `"dataType": "list"` | `java.util.List<T>` |
| Map\<K,V\> | `"dataType": "map"` | `java.util.Map<K,V>` |
| Option\<T\> | `"anyOf"` with Some/None | `java.util.Optional<T>` |
| Tuple (2 items) | `"dataType": "list"` (2 items) | `Pair<A,B>` |
| Tuple (3 items) | `"dataType": "list"` (3 items) | `Triple<A,B,C>` |
| Tuple (4 items) | `"dataType": "list"` (4 items) | `Quartet<A,B,C,D>` |
| Tuple (5 items) | `"dataType": "list"` (5 items) | `Quintet<A,B,C,D,E>` |
| Tuple (6+ items) | `"dataType": "list"` (6+ items) | _(rejected — compilation error)_ |
| Data (opaque) | `"title": "Data"` | `PlutusData` |

## Converter Classes

Each generated type has a corresponding converter in the `converter` package. Converters handle the low-level mapping between Java objects and `PlutusData`. You typically don't need to use converters directly — the `*Data` classes expose static methods that delegate to them. However, converters are useful when you need to:

- Serialize/deserialize in bulk
- Integrate with custom serialization pipelines
- Handle polymorphic types through the interface converter

## Next Steps

- [Working with Validators](03-using-validators.md) — use generated validators for transactions
- [Shared Types and plutus-aiken](04-shared-types-and-plutus-aiken.md) — avoid duplicate type generation
- [Advanced Topics](06-advanced-topics.md) — tuples, RawData, and more
