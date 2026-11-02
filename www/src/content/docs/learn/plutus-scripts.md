---
title: "Calling a Plutus script"
description: "Lock ADA at a Plutus script and unlock it with a redeemer, using QuickTx."
---

A Plutus script can hold funds, and it runs when someone tries to spend them. In this tutorial you'll lock 5 tADA at a script on the Preview testnet, then unlock them.

**Before you start**, follow [Your first transaction](/learn/simple-transfer/) to [create an account](/learn/simple-transfer/#create-sender-and-receiver-accounts), fund it from the [faucet](/learn/simple-transfer/#topup-sender-account-with-test-ada) and get a Blockfrost project id. The [dependencies](/start-here/installation/) are `cardano-client-lib` and `cardano-client-backend-blockfrost`.

The code reads your settings from the environment:

```bash
export BF_PROJECT_ID=<your Blockfrost Preview project id>
export TUTORIAL_MNEMONIC="<your 24 words>"
```

## Step 1: Load the script

This tutorial uses the smallest possible validator, which approves every transaction. It lets you focus on the transaction shape.

```java
PlutusV3Script script = PlutusV3Script.builder()
        .type("PlutusScriptV3")
        .cborHex("46450101002499")
        .build();
String scriptAddress = AddressProvider.getEntAddress(script, Networks.preview()).toBech32();
```

:::caution[Anyone can spend from this script]
Because it approves everything, anyone can take what you lock here. Use small testnet amounts only. A real validator enforces rules, like the one in the [CIP-113 tutorial](/learn/cip113-programmable-tokens/#step-2-write-the-tokens-rule).
:::

## Step 2: Lock funds

Locking is an ordinary payment to the script's address, with a *datum*: data stored with the funds for the script to read later.

```java
PlutusData datum = BigIntPlutusData.of(42);

Tx tx = new Tx()
        .payToContract(scriptAddress, Amount.ada(5), datum)
        .from(user.baseAddress());
```

No script runs at this point. Scripts run only when funds are spent.

## Step 3: Unlock funds

Spending from a script takes a *redeemer*, which is the input you give the script, and the script itself.

```java
Utxo locked = backend.getUtxoService().getTxOutput(lockTxHash, 0).getValue();
PlutusData redeemer = BigIntPlutusData.of(7);

Tx tx = new Tx()
        .collectFrom(locked, redeemer)
        .payToAddress(user.baseAddress(), Amount.ada(5))
        .attachSpendingValidator(script);

TxResult result = new QuickTxBuilder(backend)
        .compose(tx)
        .feePayer(user.baseAddress())
        .collateralPayer(user.baseAddress())
        .withSigner(SignerProviders.signerFrom(user))
        .completeAndWait(CONFIRM_TIMEOUT);
```

- `getTxOutput(lockTxHash, 0)` reads the exact output you created, since other people's funds sit at this shared address too. The payment is the transaction's first output.
- `feePayer` is needed because the transaction has no `from`. Your account pays the fee.
- `collateralPayer` names who provides collateral. If a script fails on chain, the network takes collateral instead of a fee. You lose it only in that case.

The complete code pauses for a minute between the two transactions. Even after a transaction is confirmed, a backend's UTxO view can lag behind it, so the unlock could otherwise pick an input that was just spent.

## Run it

<details>
<summary>CallPlutusScript.java</summary>

```java
package tutorial;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.TxStatus;

import java.time.Duration;

public class CallPlutusScript {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SETTLE_TIME = Duration.ofSeconds(60);

    public static void main(String[] args) throws Exception {
        BackendService backend = new BFBackendService(
                Constants.BLOCKFROST_PREVIEW_URL, System.getenv("BF_PROJECT_ID"));
        Account user = new Account(Networks.preview(), System.getenv("TUTORIAL_MNEMONIC"));

        run(backend, user);
    }

    public static void run(BackendService backend, Account user) throws Exception {
        PlutusV3Script script = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();
        String scriptAddress = AddressProvider.getEntAddress(script, Networks.preview()).toBech32();
        System.out.println("Script address: " + scriptAddress);

        String lockTxHash = lock(backend, user, scriptAddress);
        Thread.sleep(SETTLE_TIME.toMillis());
        unlock(backend, user, script, lockTxHash);
    }

    private static String lock(BackendService backend, Account user, String scriptAddress) {
        PlutusData datum = BigIntPlutusData.of(42);

        Tx tx = new Tx()
                .payToContract(scriptAddress, Amount.ada(5), datum)
                .from(user.baseAddress());

        TxResult result = new QuickTxBuilder(backend)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(user))
                .completeAndWait(CONFIRM_TIMEOUT);

        return confirmed("Lock", result);
    }

    private static void unlock(BackendService backend, Account user, PlutusV3Script script,
                               String lockTxHash) throws Exception {
        Utxo locked = backend.getUtxoService().getTxOutput(lockTxHash, 0).getValue();
        PlutusData redeemer = BigIntPlutusData.of(7);

        Tx tx = new Tx()
                .collectFrom(locked, redeemer)
                .payToAddress(user.baseAddress(), Amount.ada(5))
                .attachSpendingValidator(script);

        TxResult result = new QuickTxBuilder(backend)
                .compose(tx)
                .feePayer(user.baseAddress())
                .collateralPayer(user.baseAddress())
                .withSigner(SignerProviders.signerFrom(user))
                .completeAndWait(CONFIRM_TIMEOUT);

        confirmed("Unlock", result);
    }

    private static String confirmed(String step, TxResult result) {
        if (result.getTxStatus() != TxStatus.CONFIRMED)
            throw new IllegalStateException(step + " not confirmed (" + result.getTxStatus() + "): " + result.getResponse());
        System.out.println(step + " tx: " + result.getTxHash());
        return result.getTxHash();
    }
}
```

</details>

Running it on Preview prints something like this, with your own hashes:

```text
Script address: addr_test1wqvxuvh64q9zdqgrjt76d42eclk5wgdxtnsun4808cwg0dqxy2mj0
Lock tx: 812e9ca565a2ef20accc92c5cf074879873fe2906a9cbdaaa72ffdcfc7fe33d8
Unlock tx: 0f5465e37d25ecdbf037b1a446fc8f588cab90082a7380db4c5fbc86efcdbae8
```

## Evaluate scripts locally

To size a transaction, the library needs to know how much CPU and memory its scripts use. By default it asks the backend. You can run the scripts in your own process instead, which also shows a script failure before you submit. Add one of these to the unlock's builder chain.

[Aiken Java Binding](/smart-contracts/aiken/):

```java
.withTxEvaluator(new AikenTransactionEvaluator(backend))
```

[Scalus](/smart-contracts/scalus/), which also runs on ARM64 Linux:

```java
ProtocolParams params = backend.getEpochService().getProtocolParameters().getValue();
UtxoSupplier utxos = new DefaultUtxoSupplier(backend.getUtxoService());
```

```java
.withTxEvaluator(new ScalusTransactionEvaluator(SlotConfig.preview(), params, utxos))
```

## Next steps

- [Plutus API](/smart-contracts/plutus-api/): load scripts from a blueprint and build datums and redeemers.
- [Blueprint code generation](/smart-contracts/blueprint-codegen/): generate Java classes from your validator.
- [CIP-113 programmable tokens](/learn/cip113-programmable-tokens/): a real validator in a complete application.
- [cardano-client-examples](https://github.com/bloxbean/cardano-client-examples) has more samples, such as [an always-true script with QuickTx](https://github.com/bloxbean/cardano-client-examples/blob/main/src/test/java/com/bloxbean/cardano/client/example/quicktx/AlwaysTrueScriptTest.java). They aren't updated with every release.
