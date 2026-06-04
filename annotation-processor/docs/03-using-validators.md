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

## Parameterised Validators

Aiken validators can declare compile-time parameters that get baked into the script's compiled code:

```aiken
validator parameterized_lock(authority: VerificationKeyHash) {
  spend(...) { ... }
}
```

The annotation processor recognises the parameters and emits a **different constructor signature** for the generated validator class — `(Network, String applyParamCompiledCode)` instead of just `(Network)`:

```java
public ParameterizedLockSpendValidator(Network network, String applyParamCompiledCode) { ... }
```

`applyParamCompiledCode` is the compiled bytecode **after** the parameters have been applied. You're expected to apply them yourself before constructing the validator. Two new methods are also generated:

- `getApplyParamCompiledCode()` — returns the applied bytecode
- `getApplyParamHash()` — returns the resulting script hash (different per applied param value)

### Applying parameters with Scalus

The CCL ships with Scalus on the test/runtime classpath; `ScalusScriptUtils.applyParamsToScript(String compiledCode, PlutusData... params)` does the UPLC application:

```java
import scalus.bloxbean.ScalusScriptUtils;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;

String appliedCode = applyParamsAndUnwrap(
        ParameterizedLockSpendValidator.COMPILED_CODE,
        BytesPlutusData.of(authorityKeyHash));

var validator = new ParameterizedLockSpendValidator(Networks.testnet(), appliedCode)
        .withBackendService(backendService);
```

### The single-/double-CBOR-wrap gotcha

`COMPILED_CODE` on the generated validator class is the blueprint's **single-CBOR-wrapped** form (per CIP-57). Scalus's `applyParamsToScript` consumes and produces the **double-CBOR-wrapped** form (`Program.fromDoubleCborHex` / `Program.doubleCborHex`). The validator's `applyParamCompiledCode` constructor argument expects single-wrapped (it re-wraps internally via `PlutusBlueprintUtil.getPlutusScriptFromCompiledCode`).

So you need a small bridge: wrap once before Scalus, strip one layer after:

```java
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;

private static String applyParamsAndUnwrap(String singleWrappedHex, PlutusData... params) {
    String doubleWrapped = PlutusBlueprintUtil
            .getPlutusScriptFromCompiledCode(singleWrappedHex, PlutusVersion.v3)
            .getCborHex();
    String appliedDouble = ScalusScriptUtils.applyParamsToScript(doubleWrapped, params);
    ByteString outerByteString = (ByteString) CborSerializationUtil
            .deserialize(HexUtil.decodeHexString(appliedDouble));
    return HexUtil.encodeHexString(outerByteString.getBytes());
}
```

If you pass single-wrapped directly to Scalus, you'll see `io.bullet.borer.Borer$Error$InvalidInputData: Expected ByteString or Array of bytes but got Int (input position 0)` — that's the symptom of feeding single-wrapped into a double-expecting function.

### Worked example

`ParameterizedLockDevnetTest` (under `annotation-processor/src/it/java/.../devnet/plutus/`) drives the full flow end-to-end on a real devnet — applying the param twice with different values, asserting distinct script hashes, then locking and unlocking the bound instance.

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

### Basic minting — one asset, one receiver

The simplest mint operation: mint a single asset and send it to one address.

```java
import com.bloxbean.cardano.client.transaction.spec.Asset;

var validator = new MintPolicyMintValidator(Networks.testnet())
        .withBackendService(backendService);

// Deploy (optional but recommended for re-use)
validator.deploy(account.baseAddress())
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

// Mint 1000 units of "DemoToken" to account1
Asset asset = new Asset("DemoToken", BigInteger.valueOf(1000));

var mintResult = validator
        .mintToAddress(redeemer, asset, account.baseAddress())
        .feePayer(account.baseAddress())
        .withRequiredSigners(account.getBaseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

The `redeemer` here is anything implementing `Data` — typically a generated `*Data` class for ADT redeemers, or a small `() -> ConstrPlutusData.of(0)` lambda when the redeemer is a Java `enum` (see [Enum redeemers](#enum-redeemers) below).

### Multi-asset minting with per-receiver routing

Use `MintAsset` (asset name + quantity + receiver) when you need to mint several assets and route each to a different address:

```java
var mintAsset1 = new MintAsset("TokenA", BigInteger.valueOf(100), receiver1);
var mintAsset2 = new MintAsset("TokenB", BigInteger.valueOf(50), receiver2);

var mintResult = validator.mint(redeemer, mintAsset1, mintAsset2)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

Assets with the same name going to the same receiver are automatically aggregated.

### Mint multiple assets to a single address

```java
Asset asset1 = new Asset("MyToken", BigInteger.valueOf(100));
Asset asset2 = new Asset("MyOtherToken", BigInteger.valueOf(200));

var mintResult = validator
        .mintToAddress(redeemer, List.of(asset1, asset2), receiverAddress)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Enum redeemers

When a mint validator's redeemer is an Aiken sum type with **all zero-field variants** (e.g. `Action { Mint | Burn }`), the processor emits it as a Java `enum`, not as an interface ADT. Since `enum` doesn't implement `Data` directly, wrap a generated `*Converter` call in a lambda:

```java
MintActionConverter converter = new MintActionConverter();
Data<?> redeemer = () -> converter.toPlutusData(MintAction.Mint);
```

See `MintPolicyDevnetTest` under `annotation-processor/src/it/java/.../devnet/plutus/` for the full pattern.

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
