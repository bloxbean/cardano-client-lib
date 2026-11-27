---
title: "Native tokens"
description: "Create your own token with a native script policy, then send it to several receivers in one transaction."
---

Cardano tracks tokens in the ledger itself, so you don't need a smart contract to hold or send them. In this tutorial you'll mint your own token and send it to three receivers in a single transaction.

**Before you start**, [start Yaci DevKit](/learn/test-with-yaci-devkit/) and fund an account, or use an account you funded on Preview. The [dependencies](/start-here/installation/) are `cardano-client-lib` and `cardano-client-backend-blockfrost`.

## Step 1: Create the policy

A token's identity has two parts: a policy id and an asset name. The policy is a script that decides who can mint. Its hash is the policy id.

```java
Keys policyKeys = KeyGenUtil.generateKey();
ScriptPubkey policy = ScriptPubkey.create(policyKeys.getVkey());
String policyId = policy.getPolicyId();
```

This policy has one rule: the new key must sign. Whoever holds the key can mint more of this token later, so save it if you want to do that:

```java
String policyKeyHex = policyKeys.getSkey().getCborHex();
```

To cap the supply, add a deadline to the policy, as in [Minting an NFT](/learn/minting-an-nft/).

## Step 2: Mint

```java
Asset token = new Asset("TutorialToken", BigInteger.valueOf(1_000_000));

Tx mint = new Tx()
        .mintAssets(policy, token, sender.baseAddress())
        .from(sender.baseAddress());

TxResult minted = new QuickTxBuilder(backend)
        .compose(mint)
        .withSigner(SignerProviders.signerFrom(sender))
        .withSigner(SignerProviders.signerFrom(policyKeys.getSkey()))
        .completeAndWait(Duration.ofMinutes(5));
```

There are two signers: your account pays the fee, and the policy key authorizes the mint.

Quantities on-chain are whole numbers. A token with "6 decimals" is a display choice: 1,500,000 units appear as 1.5 in wallets that know about the decimals.

## Step 3: Send it to several receivers

One Cardano transaction can have many outputs, so this costs one fee and either all of it happens or none does. A token's unit is its policy id followed by its hex-encoded name.

```java
String unit = AssetUtil.getUnit(policyId, token);

Tx distribute = new Tx()
        .payToAddress(receiver1, Amount.asset(unit, 100))
        .payToAddress(receiver2, Amount.asset(unit, 250))
        .payToAddress(receiver3, Amount.asset(unit, 50))
        .from(sender.baseAddress());

TxResult sent = new QuickTxBuilder(backend)
        .compose(distribute)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait(Duration.ofMinutes(5));
```

Every output on Cardano must carry a minimum amount of ada. You don't need to work this out: QuickTx adds it to each output, paid by the sender.

For a long list of receivers, call `payToAddress` in a loop. A transaction has a size limit (16 KB on current networks), so split very long lists across several transactions.

## Run it

<details>
<summary>TokenDistribution.java</summary>

```java
package tutorial;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.TxStatus;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

public class TokenDistribution {

    public static void main(String[] args) throws Exception {
        // Yaci DevKit. For Preview, see "Move to a public network" on the DevKit page.
        BackendService backend = new BFBackendService("http://localhost:8080/api/v1/", "dummy-key");
        Account sender = Account.createFromMnemonic(Networks.testnet(), System.getenv("TUTORIAL_MNEMONIC"));

        String receiver1 = new Account(Networks.testnet()).baseAddress();
        String receiver2 = new Account(Networks.testnet()).baseAddress();
        String receiver3 = new Account(Networks.testnet()).baseAddress();

        // 1. The policy: this key must sign
        Keys policyKeys = KeyGenUtil.generateKey();
        ScriptPubkey policy = ScriptPubkey.create(policyKeys.getVkey());
        String policyId = policy.getPolicyId();

        // 2. Mint to the sender
        Asset token = new Asset("TutorialToken", BigInteger.valueOf(1_000_000));
        Tx mint = new Tx()
                .mintAssets(policy, token, sender.baseAddress())
                .from(sender.baseAddress());

        TxResult minted = new QuickTxBuilder(backend)
                .compose(mint)
                .withSigner(SignerProviders.signerFrom(sender))
                .withSigner(SignerProviders.signerFrom(policyKeys.getSkey()))
                .completeAndWait(Duration.ofMinutes(5));
        check("Mint", minted);

        // A backend's UTxO view can lag behind a confirmed transaction
        String unit = AssetUtil.getUnit(policyId, token);
        waitForToken(backend, sender.baseAddress(), unit);

        // 3. Send to three receivers in one transaction
        Tx distribute = new Tx()
                .payToAddress(receiver1, Amount.asset(unit, 100))
                .payToAddress(receiver2, Amount.asset(unit, 250))
                .payToAddress(receiver3, Amount.asset(unit, 50))
                .from(sender.baseAddress());

        TxResult sent = new QuickTxBuilder(backend)
                .compose(distribute)
                .withSigner(SignerProviders.signerFrom(sender))
                .completeAndWait(Duration.ofMinutes(5));
        check("Distribute", sent);

        System.out.println("Unit: " + unit);
        System.out.println("Receiver 2 holds: " + waitForToken(backend, receiver2, unit));
    }

    static void check(String step, TxResult result) {
        if (result.getTxStatus() != TxStatus.CONFIRMED)
            throw new IllegalStateException(step + " not confirmed (" + result.getTxStatus() + "): " + result.getResponse());
        System.out.println(step + ": " + result.getTxHash());
    }

    static BigInteger waitForToken(BackendService backend, String address, String unit) throws Exception {
        for (int attempt = 0; attempt < 12; attempt++) {
            List<Utxo> holding = backend.getUtxoService().getUtxos(address, unit, 1, 1).getValue();
            if (holding != null && !holding.isEmpty())
                return holding.get(0).getAmount().stream()
                        .filter(amount -> unit.equals(amount.getUnit()))
                        .map(Amount::getQuantity)
                        .findFirst().orElseThrow();
            Thread.sleep(5_000);
        }
        throw new IllegalStateException("Token not found yet: " + unit);
    }
}
```

</details>

Set `TUTORIAL_MNEMONIC` to the words of a funded account. On DevKit, you can use the [default mnemonic](/learn/test-with-yaci-devkit/#fund-an-account). The output has this shape, with your own values:

```text
Mint: <transaction hash>
Distribute: <transaction hash>
Unit: <policy id>5475746f7269616c546f6b656e
Receiver 2 holds: 250
```

## Next

- [Minting an NFT](/learn/minting-an-nft/): a token with a quantity of 1, a time-locked policy and metadata.
- [CIP-25](/standards/cip25/) and [CIP-68](/standards/cip68/): the token metadata standards.
