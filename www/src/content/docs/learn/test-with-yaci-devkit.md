---
title: "Test locally with Yaci DevKit"
description: "Run a local Cardano devnet, fund accounts instantly, and move the same code to a public testnet when you're ready."
---

You can run every tutorial on this site in one of two places: a devnet on your own machine, or a public test network. Both use the same code, and only the backend line changes.

| | Yaci DevKit (local) | Preview or Preprod (public) |
|---|---|---|
| **Setup** | Install and start it once | Nothing to install. Blockfrost needs a free project id; Koios doesn't |
| **Test ada** | Funded accounts, plus a top-up command | A [faucet](https://docs.cardano.org/cardano-testnets/tools/faucet) |
| **Block time** | 1 second by default | About 20 seconds on average |
| **Epoch length** | 500 slots by default, and you can change it | 1 day on Preview, 5 days on Preprod |
| **Who sees it** | Only you | Everyone, permanently |
| **Start over** | Reset whenever you like | Not possible |

Use DevKit while you're developing, because the feedback is fast and mistakes cost nothing. Before you release, run once on Preview: a public network has real delays that a devnet hides.

## Start a devnet

Follow [Get started with Yaci DevKit](https://devkit.yaci.xyz/#get-started). It covers Homebrew, npm, Docker and Zip. With Homebrew on macOS 15+ or Linux, it's two commands:

```bash
brew install bloxbean/tap/yaci-devkit
yaci-devkit up --enable-yaci-store
```

Keep that terminal open, because its prompt is where you'll type DevKit commands. In another terminal, check that the devnet's API is answering:

```bash
curl http://localhost:8080/api/v1/epochs/latest
```

## Connect from Java

DevKit includes Yaci Store, which serves a Blockfrost-compatible API. So you use the Blockfrost backend with a local URL, and any value for the key:

```java
BackendService backend = new BFBackendService("http://localhost:8080/api/v1/", "dummy-key");
```

## Fund an account

A new devnet has 20 funded addresses, each with 10,000 ada, derived from one well-known mnemonic. Use it to get going right away:

```java
String mnemonic = "test test test test test test test test test test test test "
        + "test test test test test test test test test test test sauce";
Account sender = Account.createFromMnemonic(Networks.testnet(), mnemonic);
```

:::danger[Devnet only]
Everyone knows this mnemonic. Never send real funds to its addresses.
:::

To fund any other address, use `topup` at the DevKit prompt. This sends it 100 ada:

```bash
topup <address> 100
```

Tests can do the same over HTTP:

```bash
curl -X POST http://localhost:10000/local-cluster/api/addresses/topup \
  -H "Content-Type: application/json" \
  -d '{"address": "<address>", "adaAmount": 100}'
```

## Send a transaction

This is the transaction from [Your first transaction](/learn/simple-transfer/), running on your devnet. You need `cardano-client-lib` and `cardano-client-backend-blockfrost` from [Installation](/start-here/installation/).

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;

public class LocalTransfer {

    public static void main(String[] args) {
        BackendService backend = new BFBackendService("http://localhost:8080/api/v1/", "dummy-key");

        String mnemonic = "test test test test test test test test test test test test "
                + "test test test test test test test test test test test sauce";
        Account sender = Account.createFromMnemonic(Networks.testnet(), mnemonic);
        Account receiver = new Account(Networks.testnet());

        Tx tx = new Tx()
                .payToAddress(receiver.baseAddress(), Amount.ada(10))
                .from(sender.baseAddress());

        TxResult result = new QuickTxBuilder(backend)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(sender))
                .completeAndWait();

        System.out.println(result.getTxStatus() + " " + result.getTxHash());
    }
}
```

It prints `CONFIRMED` and the transaction hash within a few seconds.

## Move to a public network

Change the backend, and use an account you funded from the [faucet](https://docs.cardano.org/cardano-testnets/tools/faucet) in place of the devnet mnemonic. The rest of your code stays the same:

```java
// Preview, through Blockfrost
BackendService backend = new BFBackendService(Constants.BLOCKFROST_PREVIEW_URL, "<Blockfrost Project Id>");
```

Expect two differences:

- **Slower confirmation.** Allow minutes rather than seconds, for example with `completeAndWait(Duration.ofMinutes(5))`. If it times out, the status is `TIMEOUT` and the transaction may still land.
- **Delays in the backend's view.** A confirmed transaction's outputs can take a little while to appear in UTxO queries, so a step that spends them straight away may not find them. Retry that query instead of assuming they're there.

## Going further

- **Staking and governance.** The [staking](/learn/staking-and-delegation/) lifecycle spans several epochs, which takes minutes on a devnet instead of days. The `--epoch-length` option of [`create-node`](https://devkit.yaci.xyz/commands) makes them shorter.
- **Starting over.** `reset` at the DevKit prompt deletes the devnet's data and restarts it.
- **Rollbacks.** DevKit can also [simulate rollbacks](https://devkit.yaci.xyz/rollback-testing), which is useful for testing [TxFlow](/txflow/overview/).
- **Other commands.** See the [command reference](https://devkit.yaci.xyz/commands) and the [services DevKit can run](https://devkit.yaci.xyz/services).

## Next

Next, [mint and distribute your own tokens](/learn/native-tokens/).
