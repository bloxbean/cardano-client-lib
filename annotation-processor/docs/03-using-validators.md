# Working with Generated Validators

Generated validator classes provide high-level methods for common smart contract operations: deploying scripts, locking funds, unlocking funds, and minting tokens. This document covers the complete workflow for each operation.

## Validator Setup

Every generated validator needs a network and backend service:

```java
var validator = new HelloWorldValidator(Networks.testnet())
        .withBackendService(backendService);
```

The `withBackendService()` call configures the UTXO supplier, protocol parameters supplier, and transaction processor from a single `BackendService` instance. It also sets the transaction evaluator if the backend provides one.

You can also configure individual suppliers, or override specific ones after calling `withBackendService()`:

```java
var validator = new HelloWorldValidator(Networks.testnet())
        .withUtxoSupplier(utxoSupplier)
        .withProtocolParamsSupplier(protocolParamsSupplier)
        .withTransactionProcessor(transactionProcessor)
        .withTransactionEvaluator(transactionEvaluator);
```

For example, to use a custom transaction evaluator (such as Scalus for local script evaluation) while keeping the rest from the backend service:

```java
var validator = new HelloWorldValidator(Networks.testnet())
        .withBackendService(backendService)
        .withTransactionEvaluator(customEvaluator);
```

## The `@ExtendWith` Annotation

The `@ExtendWith` annotation on your blueprint interface controls which methods the generated validator class has:

```java
@Blueprint(fileInResources = "blueprint/helloworld.json",
           packageName = "com.example.generated")
@ExtendWith(LockUnlockValidatorExtender.class)
public interface MyBlueprint { }
```

Available extenders:

| Extender | Provides |
|---|---|
| `LockUnlockValidatorExtender` | `deploy()`, `lock()`, `unlock()`, `unlockToAddress()`, `unlockToContract()` |
| `MintValidatorExtender` | `deploy()`, `mint()`, `mintToAddress()`, `mintToContract()`, `getPolicyId()` |
| `DeployValidatorExtender` | `deploy()` only |

All extenders include deploy functionality. You can combine multiple extenders:

```java
@ExtendWith({LockUnlockValidatorExtender.class, MintValidatorExtender.class})
```

## TxContext vs Tx Methods

Each operation comes in two flavors:

- **TxContext methods** (e.g., `lock()`, `unlock()`, `mint()`) — return a `TxContext` with backend services pre-configured. Call `.feePayer()`, `.withSigner()`, and `.completeAndWait()` directly.
- **Tx methods** (e.g., `lockTx()`, `unlockTx()`, `mintTx()`) — return a raw `Tx` or `ScriptTx` that you compose manually with `QuickTxBuilder`.

**Use TxContext methods** for simple, standalone transactions. **Use Tx methods** when you need to compose multiple transactions together or need fine-grained control.

## Deploying a Script

Deploying creates a reference script UTXO on-chain. This is optional but recommended — it makes subsequent transactions smaller and cheaper.

### Using TxContext

```java
var deployResult = validator.deploy(account.baseAddress())
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

// Register the reference input for future transactions
validator.withReferenceTxInput(deployResult.getValue(), 0);
```

### Using Tx

```java
var tx = validator.deployTx(account.baseAddress());

var deployResult = new QuickTxBuilder(backendService)
        .compose(tx)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

validator.withReferenceTxInput(deployResult.getValue(), 0);
```

## Using Reference Inputs

After deploying, call `withReferenceTxInput()` to tell the validator to reference the deployed script instead of including it in every transaction:

```java
validator.withReferenceTxInput(txHash, outputIndex);
```

This significantly reduces transaction fees. If you don't call this, the validator attaches the full script to each transaction.

## Lock/Unlock Workflow

The `LockUnlockValidatorExtender` provides methods for spending validators that lock and unlock funds at a script address.

### Locking Funds

Create a datum, then lock funds at the script address:

```java
// Create the datum
Owner datum = new OwnerData();
datum.setOwner(account.getBaseAddress().getPaymentCredentialHash().get());

// Lock funds at the script address
var lockResult = validator.lock(account.baseAddress(), Amount.ada(20), datum)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

The `lock()` method accepts:
- The sender address (where ADA comes from)
- The amount to lock
- The datum (must implement `Data<T>`)

### Unlocking Funds

To unlock, provide the original datum (to find the UTXO), a redeemer, and receiver(s):

```java
var redeemer = new RedeemerData();
redeemer.setMsg("Hello, World!".getBytes(StandardCharsets.UTF_8));

var receiver = new PubKeyReceiver(account.baseAddress(), Amount.ada(20));

var unlockResult = validator.unlock(
            datum, redeemer,
            List.of(receiver),
            new ChangeReceiver(account.baseAddress()))
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

The `unlock()` method finds the UTXO at the script address by matching the datum's inline representation.

### Unlocking with a Known UTXO

If you already have the UTXO (e.g., from a query), pass it directly to avoid the lookup:

```java
var utxo = ScriptUtxoFinders.findFirstByInlineDatum(
        new DefaultUtxoSupplier(backendService.getUtxoService()),
        validator.getScriptAddress(),
        datum.toPlutusData());

var unlockResult = validator.unlock(
            utxo.get(), redeemer,
            List.of(receiver),
            new ChangeReceiver(account.baseAddress()))
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Unlock to a Regular Address

A convenience method that sends all unlocked funds to a single address:

```java
var unlockResult = validator.unlockToAddress(datum, redeemer, receiverAddress)
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Unlock to a Contract Address

Send unlocked funds to another script address with a new datum:

```java
Owner newDatum = new OwnerData();
newDatum.setOwner(newOwnerKeyHash);

var unlockResult = validator.unlockToContract(
            datum, redeemer,
            validator.getScriptAddress(), newDatum)
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

You can also pass raw `PlutusData` as the datum:

```java
var unlockResult = validator.unlockToContract(
            datum, redeemer,
            scriptAddress, BigIntPlutusData.of(42))
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

## Minting Workflow

The `MintValidatorExtender` provides methods for minting validators.

### Basic Minting

Mint tokens and distribute them to multiple receivers:

```java
var validator = new MintValidator(Networks.testnet())
        .withBackendService(backendService);

// Deploy (optional)
var deployResult = validator.deploy(account.baseAddress())
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

// Mint tokens
var mintAsset1 = new MintAsset("TokenA", BigInteger.valueOf(100), receiver1);
var mintAsset2 = new MintAsset("TokenB", BigInteger.valueOf(50), receiver2);

var mintResult = validator.mint(
            ActionData.of(Action.Mint),
            mintAsset1, mintAsset2)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

`MintAsset` takes an asset name, quantity, and receiver address. Assets with the same name going to the same receiver are automatically aggregated.

### Mint to a Regular Address

Send all minted tokens to a single address:

```java
Asset asset1 = new Asset("MyToken", BigInteger.valueOf(100));
Asset asset2 = new Asset("MyOtherToken", BigInteger.valueOf(200));

var mintResult = validator.mintToAddress(
            ActionData.of(Action.Mint),
            List.of(asset1, asset2),
            receiverAddress)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Mint to a Contract Address

Send minted tokens to a script address with a datum:

```java
var mintResult = validator.mintToContract(
            ActionData.of(Action.Mint),
            List.of(asset1, asset2),
            scriptAddress,
            () -> ConstrPlutusData.of(3))   // Data lambda as inline datum
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Getting the Policy ID

```java
String policyId = validator.getPolicyId();
```

## Composing Multiple Transactions

Use the `*Tx` methods to compose multiple operations in a single submission:

```java
// Create separate Tx objects
var unlockTx1 = validator.unlockToAddressTx(datum1, redeemer1, receiver1);
var unlockTx2 = validator.unlockToAddressTx(datum2, redeemer2, receiver2);
var paymentTx = new Tx()
        .payToAddress(receiver3, Amount.ada(5))
        .from(account.baseAddress());

// Compose and submit together
var result = new QuickTxBuilder(backendService)
        .compose(paymentTx, unlockTx1, unlockTx2)
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .withReferenceScripts(validator.getPlutusScript())
        .completeAndWait(System.out::println);
```

> **Note:** When using `*Tx` methods without a reference input, you may need to attach the script as a reference script via `withReferenceScripts()`.

## Utility Methods and Static Fields

All generated validators provide these instance methods:

```java
// Get the script address (derived from the compiled code hash)
String scriptAddress = validator.getScriptAddress();

// Get the Plutus script object
PlutusScript plutusScript = validator.getPlutusScript();
```

And these static constants:

```java
// The script hash (hex string)
String hash = HelloWorldValidator.HASH;

// The compiled script code (CBOR hex string)
String code = HelloWorldValidator.COMPILED_CODE;
```

The `HASH` constant is useful when registering scripts with custom evaluators or looking up script references.

## Next Steps

- [Understanding Generated Code](02-generated-code.md) — learn about the generated class hierarchy
- [Shared Types and plutus-aiken](04-shared-types-and-plutus-aiken.md) — reuse Aiken stdlib types
- [Advanced Topics](06-advanced-topics.md) — tuples, RawData, and more
