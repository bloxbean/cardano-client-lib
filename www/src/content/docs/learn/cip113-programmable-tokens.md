---
title: "Programmable Tokens with CIP-113"
description: "Build a token that can't move without its issuer's approval: register, mint, transfer, seize and burn it on Preview."
---

A native Cardano token moves freely once it's minted. Nobody can block a transfer, freeze a holder or recover stolen funds, which rules out many regulated assets. [CIP-113](https://cips.cardano.org/cip/CIP-0113) adds rules without a hard fork: tokens sit at a shared script, and they change hands only if the token's own logic script approves.

In this tutorial you'll create a token that can't move without its issuer's signature, then take it through its whole life on the Preview testnet:

1. Register the token and mint 1,000 to Alice.
2. Watch a transfer fail because the issuer didn't approve it, then send an approved one to Bob.
3. Seize tokens from Bob without his signature.
4. Burn what you seized.

You finish with Alice holding 900 tokens, Bob holding 60, and a total supply of 960.

:::caution[Experimental]
CIP-113 has the status **Proposed**, and `cardano-client-programmable-token` is experimental, so its public types may change before the module reaches beta. Use this on testnets for now.
:::

**You need:** Java 17+, and some experience with [QuickTx](/quicktx/overview/). If you haven't built a transaction with this library yet, start with [your first transaction](/learn/simple-transfer/).

## How CIP-113 works

Four ideas explain most of what follows.

**Smart wallet.** Programmable tokens never sit at an ordinary address. Each holder has a smart wallet: an address whose payment part is the shared `programmable_logic_base` script and whose stake part identifies the owner. Anyone can derive it from the owner's normal address, so a receiver doesn't have to set anything up. Once minted, tokens move only between smart wallets.

**Registry.** A shared on-chain linked list with one node per token. Each node records the scripts that govern that token, and anyone can add a node.

**Substandard.** Those scripts are the token's rules, and you write them:

| Script | Field in CCL | Runs when |
|---|---|---|
| Minting logic | `mintingLogicScript` | Tokens are minted or burned |
| Transfer logic | `transferLogicScript` | A holder moves or burns their own tokens |
| Third-party logic | `thirdPartyTransferLogicScript` | Someone acts on another holder's tokens, such as a seizure |

The CIP calls the minting logic `issuanceLogicScript`. A fourth script, `unfrackingLogicScript`, defaults to forbidden; see [Limits](#limits).

**Withdraw-zero.** Logic scripts run as a withdrawal of 0 lovelace from the script's own reward account. A script can then take part in a transaction without holding anything, but its reward account has to be registered first.

```text
                 shared protocol, already deployed
   +-----------------------------------------------------------+
   |  registry  -->  your token's node  -->  your logic scripts |
   +-----------------------------------------------------------+
                               |
                     checked on every move
                               |
   Alice's smart wallet  ------+------>  Bob's smart wallet
   (base script + Alice's key)           (base script + Bob's key)
```

You don't deploy any of this. The library ships descriptions of the public deployments as `Cip113Deployments.PREVIEW`, `PREPROD` and `MAINNET`. This tutorial uses Preview because a local Yaci DevKit has no CIP-113 deployment, and the library has no public API for creating one.

When you build a transaction, the library finds the registry node, adds the protocol's reference inputs, writes its redeemers and sizes the min-ADA on each output. You only state what you want to happen.

:::note[Two addresses, one owner]
Alice's tokens sit in her smart wallet, but her fees and collateral come from her normal address, so a holder who sends tokens needs ADA there too.

The CIP lets an application key smart wallets by the owner's payment or stake credential, and the two choices give the same person different addresses. CCL defaults to `SmartWalletKey.PAYMENT`, which puts the owner's *payment* credential in the stake part of the address. Everyone holding or reading a token has to agree on the choice.
:::

## Set up your project

Add these dependencies. The programmable-token module isn't part of the `cardano-client-lib` aggregate, so it has to be listed on its own.

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-lib:0.8.0-pre7'
    implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.8.0-pre7'
    implementation 'com.bloxbean.cardano:cardano-client-programmable-token:0.8.0-pre7'
    implementation 'com.bloxbean.cardano:aiken-java-binding:0.1.0'
}
```

`aiken-java-binding` applies your script's parameter and evaluates scripts locally. It bundles native libraries for macOS, Windows and x86-64 Linux, but not ARM64 Linux.

You also need a free [Blockfrost](https://blockfrost.io) project for Preview and a funded wallet. Create a wallet with this class:

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;

public class CreateWallet {

    public static void main(String[] args) {
        Account account = new Account(Networks.preview());
        System.out.println("Mnemonic: " + account.mnemonic());
        System.out.println("Fund this address: " + account.baseAddress());
    }
}
```

Send at least 50 tADA to the printed address from the [testnet faucet](https://docs.cardano.org/cardano-testnets/tools/faucet), selecting Preview. Then export your settings:

```bash
export BF_PROJECT_ID=<your Blockfrost Preview project id>
export TUTORIAL_MNEMONIC="<the words printed above>"
```

## Step 1: Connect to the deployment

The code below derives three accounts from your mnemonic. The issuer is account 0, the address you funded. Alice and Bob are accounts 1 and 2.

```java
public class IssuerApprovedToken {

    /** issuer_approved.issuer_approved.withdraw from plutus.json, before the issuer is applied. */
    private static final String COMPILED_CODE =
            "5895010100229800aba2aba1aab9faab9eaab9dab9a9bae002488888896600264646644b3001"
            + "3370e900218039baa00189991198008009bac300c300d300d300d300d300d300d300d300d30"
            + "0a3754601800c601460106ea8004896600200314a115980099b8f375c601800200f14a31330"
            + "02002300d0014020805a2c8030c020004c020c024004c020004c010dd5004452689b2b200401";

    private static final String ASSET_NAME = "TutorialToken";

    /** How long to let the backend's UTxO view catch up after a confirmed transaction. */
    private static final Duration SETTLE_TIME = Duration.ofSeconds(60);

    private final BackendService backend;
    private final Network network;
    private final Account issuer;
    private final Account alice;
    private final Account bob;

    private final Cip113ProgrammableTokenService tokens;
    private final Cip113ProtocolService protocol;
    private final Address issuerAddress;

    private PlutusScript logicScript;
    private Credential logic;
    private String policyId;

    public IssuerApprovedToken(BackendService backend, Cip113ProgrammableTokenService tokens,
                               Network network, Account issuer, Account alice, Account bob) {
        this.backend = backend;
        this.network = network;
        this.issuer = issuer;
        this.alice = alice;
        this.bob = bob;
        this.issuerAddress = new Address(issuer.baseAddress());
        this.tokens = tokens;
        this.protocol = tokens.advanced();
    }

    public static void main(String[] args) throws Exception {
        String projectId = System.getenv("BF_PROJECT_ID");
        String mnemonic = System.getenv("TUTORIAL_MNEMONIC");
        Network network = Networks.preview();

        BackendService backend = new BFBackendService(Constants.BLOCKFROST_PREVIEW_URL, projectId);

        // One mnemonic, three separate accounts: the issuer and two holders.
        Account issuer = Account.createFromMnemonic(network, mnemonic, 0, 0);
        Account alice = Account.createFromMnemonic(network, mnemonic, 1, 0);
        Account bob = Account.createFromMnemonic(network, mnemonic, 2, 0);

        Cip113ProgrammableTokenService tokens =
                Cip113ProgrammableTokenService.create(backend, Cip113Deployments.PREVIEW);

        new IssuerApprovedToken(backend, tokens, network, issuer, alice, bob).run();
    }

    public void run() throws Exception {
        prepareLogicScript();
        fundAlice();
        registerRewardAccount();
        registerAndMint();
        printBalances("after mint");
        transferWithoutApproval();
        transfer();
        printBalances("after transfer");
        seize();
        printBalances("after seizure");
        burn();
        printBalances("final");
    }

    // The methods below go here.
}
```

`Cip113ProgrammableTokenService` is the entry point. `tokens.extension()` plugs the protocol into QuickTx, and `tokens.advanced()` gives you the read side used throughout this tutorial.

The sections below add one method each to this class, and the [complete class](#run-it) is at the end.

## Step 2: Write the token's rule

This token has a single rule, written in [Aiken](https://aiken-lang.org): the issuer must have signed.

```rust
use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use cardano/address.{Credential}
use cardano/transaction.{Transaction}

/// Passes only when `issuer` signed the transaction.
validator issuer_approved(issuer: VerificationKeyHash) {
  withdraw(_redeemer: Data, _account: Credential, tx: Transaction) {
    list.has(tx.extra_signatories, issuer)
  }

  else(_) {
    fail
  }
}
```

You don't need Aiken installed. The compiled validator is the `COMPILED_CODE` constant in the class above, built with Aiken v1.1.23 and stdlib v3.0.0.

One script serves all three roles here, so there's only one reward account to register. A real token would usually have a separate script for each role.

`issuer` is a parameter, so every issuer gets a different script hash. That matters because a token's policy id is derived from its minting script's hash. You don't choose the policy id, and two tokens with the same minting script would be the same token. Your parameterized script gives you a policy nobody else in the shared registry has.

```java
private void prepareLogicScript() throws Exception {
    byte[] issuerKeyHash = issuer.hdKeyPair().getPublicKey().getKeyHash();

    String applied = AikenScriptUtil.applyParamToScript(
            ListPlutusData.of(BytesPlutusData.of(issuerKeyHash)), COMPILED_CODE);
    logicScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(applied, PlutusVersion.v3);
    logic = Credential.fromScript(logicScript.getPolicyId());

    // Nothing on chain has used this script yet, so no backend can serve it.
    protocol.scripts().register(logicScript);

    policyId = value(protocol.derivePolicyId(logic));
    System.out.println("Logic script : " + logicScript.getPolicyId());
    System.out.println("Policy id    : " + policyId);
}
```

The `register` call is needed because a backend can serve a script only after the chain has revealed it, and yours hasn't been used yet.

## Step 3: Fund Alice and register the reward account

Alice needs ADA at her normal address for fees and collateral:

```java
private void fundAlice() {
    Tx tx = new Tx()
            .payToAddress(alice.baseAddress(), Amount.ada(30))
            .from(issuer.baseAddress());

    check("Fund alice", new QuickTxBuilder(backend)
            .compose(tx)
            .withSigner(SignerProviders.signerFrom(issuer))
            .completeAndWait());
}
```

Every submission in this tutorial goes through `check`, which stops on failure and then pauses:

```java
private static void check(String step, Result<String> result) {
    if (!result.isSuccessful())
        throw new IllegalStateException(step + " failed: " + result.getResponse());
    System.out.printf("%-12s : %s%n", step, result.getValue());
    settle();
}

private static void settle() {
    try {
        Thread.sleep(SETTLE_TIME.toMillis());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
    }
}
```

The pause matters on a public network. `completeAndWait()` returns once the transaction is confirmed, but Blockfrost's UTxO queries can lag behind that: we measured anywhere from 1 to 37 seconds on Preview. Without the pause, the next transaction can pick a UTxO that was just spent. A pause keeps this tutorial simple, but a production application should chain transactions properly, for example with [TxFlow](/txflow/overview/).

Next, register the script's reward account so that withdraw-zero can run it. This needs no script witness. The check for `StakeKeyRegistered` lets you run this step again safely.

```java
private void registerRewardAccount() {
    String rewardAddress = AddressProvider.getRewardAddress(logic, network).toBech32();

    Result<String> result = new QuickTxBuilder(backend)
            .compose(new Tx()
                    .registerStakeAddress(rewardAddress)
                    .from(issuer.baseAddress()))
            .withSigner(SignerProviders.signerFrom(issuer))
            .completeAndWait();

    if (!result.isSuccessful() && result.getResponse().contains("StakeKeyRegistered")) {
        System.out.println("Reward account already registered");
        return;
    }
    check("Reward acct", result);
}
```

## Step 4: Register the token and mint

Every programmable-token transaction goes through this helper:

```java
private QuickTxBuilder.TxContext builder(Tx tx, Account sender) {
    return new QuickTxBuilder(backend)
            .withExtension(tokens.extension())
            .compose(tx)
            .withSigner(SignerProviders.signerFrom(sender))
            .withTxEvaluator(new AikenTransactionEvaluator(backend));
}
```

`withExtension` is what turns verbs like `register` and `mint` into a real transaction. `withTxEvaluator` runs the scripts locally, so a script failure stops the build before anything is submitted.

Now register the token and mint its first supply in one transaction:

```java
private void registerAndMint() {
    RegistryNodeSpec spec = RegistryNodeSpec.builder()
            .mintingLogicScript(logic)
            .transferLogicScript(logic)
            .thirdPartyTransferLogicScript(logic)
            .build();

    ProgrammableTokenTx tx = new ProgrammableTokenTx()
            .from(issuer.baseAddress())
            .register("tutorial", Cip113Registration.from(spec), PlutusData.unit())
            .mint(ProgrammableTokenPolicyRef.named("tutorial"), alice.baseAddress(),
                    List.of(new Asset(ASSET_NAME, BigInteger.valueOf(1000))),
                    PlutusData.unit(), null);

    check("Register", builder(tx, issuer)
            .withRequiredSigners(issuerAddress)
            .completeAndWait());
}
```

- `RegistryNodeSpec` names the scripts for each role.
- `ProgrammableTokenPolicyRef.named("tutorial")` points the mint at the registration in the same transaction. The name exists only inside this transaction. Later mints use the literal policy id.
- `alice.baseAddress()` is Alice's normal address. The library maps it to her smart wallet, so the tokens land there.
- `PlutusData.unit()` is the redeemer. This validator ignores its redeemer.
- `withRequiredSigners` declares the issuer. A script sees only the signers a transaction declares, so `tx.extra_signatories` would otherwise be empty.

Registration spends a node of the shared registry, so it can collide with someone else registering at the same moment. If that happens, run it again.

## Step 5: Check the balances

```java
private void printBalances(String label) {
    System.out.printf("%-15s alice=%s bob=%s issuer=%s%n", label,
            balance(alice), balance(bob), balance(issuer));
}

private BigInteger balance(Account holder) {
    String unit = policyId + HexUtil.encodeHexString(ASSET_NAME.getBytes(StandardCharsets.UTF_8));
    return value(protocol.getProgrammableBalance(new Address(holder.baseAddress()))).stream()
            .filter(amount -> unit.equals(amount.getUnit()))
            .map(Amount::getQuantity)
            .reduce(BigInteger.ZERO, BigInteger::add);
}
```

`getProgrammableBalance` takes the owner's normal address and reads their smart wallet. It returns only tokens that are in the registry. Other calls on `protocol` you may find useful are `smartWalletAddress`, `isProgrammable` and `getRegistryNode`.

After the mint you'll see:

```text
after mint      alice=1000 bob=0 issuer=0
```

## Step 6: Try a transfer without approval

Alice tries to send 100 tokens to Bob, signing alone:

```java
private void transferWithoutApproval() {
    ProgrammableTokenTx tx = new ProgrammableTokenTx()
            .from(alice.baseAddress())
            .transfer(bob.baseAddress(), Amount.asset(policyId, ASSET_NAME, 100), PlutusData.unit());

    try {
        builder(tx, alice).completeAndWait();
    } catch (TxBuildException e) {
        System.out.println("Rejected     : " + e.getMessage());
        return;
    }
    throw new IllegalStateException("The transfer should not have passed without the issuer");
}
```

The library declares Alice as a required signer because her wallet is being spent, and that satisfies the protocol. But the token's transfer logic also runs, finds no issuer signature and fails. Evaluation stops the build, so nothing is submitted and no fee or collateral is at risk.

The failure is thrown as a `TxBuildException` with this message:

```text
Rejected     : Error while evaluating script cost
```

If you have an SLF4J provider on the classpath, the evaluator also logs the reason. `tag: "Withdraw"` tells you that a withdraw-zero script said no:

```text
AIKEN: RedeemerError { tag: "Withdraw", index: 2, err: Machine(EvaluationFailure, ...) }
```

## Step 7: Send an approved transfer

The same transaction goes through once the issuer approves it. Both parts matter: `withRequiredSigners` declares the issuer, and `withSigner` adds the signature.

```java
private void transfer() {
    ProgrammableTokenTx tx = new ProgrammableTokenTx()
            .from(alice.baseAddress())
            .transfer(bob.baseAddress(), Amount.asset(policyId, ASSET_NAME, 100), PlutusData.unit());

    check("Transfer", builder(tx, alice)
            .withRequiredSigners(issuerAddress)
            .withSigner(SignerProviders.signerFrom(issuer))
            .completeAndWait());
}
```

```text
after transfer  alice=900 bob=100 issuer=0
```

## Step 8: Seize tokens

Now the issuer takes 40 tokens from Bob. Bob doesn't sign, and he doesn't need any ADA either.

```java
private void seize() {
    ProgrammableTokenTx tx = new ProgrammableTokenTx()
            .from(issuer.baseAddress())
            .thirdPartyTransfer(bob.baseAddress(), issuer.baseAddress(),
                    Amount.asset(policyId, ASSET_NAME, 40), PlutusData.unit());

    check("Seize", builder(tx, issuer)
            .withRequiredSigners(issuerAddress)
            .completeAndWait());
}
```

`thirdPartyTransfer` takes the holder, the receiver, the amount and a redeemer. The library selects Bob's UTxOs, spends them under the token's third-party logic, and keeps whatever isn't seized in his smart wallet.

Unlike a transfer, this verb declares no signer for you, so `withRequiredSigners` is what makes the issuer's signature visible to the script.

```text
after seizure   alice=900 bob=60 issuer=40
```

:::danger[Holders can't refuse a seizure]
Whoever the third-party logic accepts can take tokens from any smart wallet. If a token's third-party logic always succeeds, **anyone can take anyone's tokens**. The library can't prevent that, because it's how the token was designed. Before you accept a token, read its rules with `protocol.getRegistryNode(policyId)`.
:::

## Step 9: Burn tokens

The issuer now burns the 40 seized tokens:

```java
private void burn() {
    ProgrammableTokenTx tx = new ProgrammableTokenTx()
            .from(issuer.baseAddress())
            .burn(policyId, List.of(new Asset(ASSET_NAME, BigInteger.valueOf(40))),
                    BurnAuthorization.of(PlutusData.unit(), PlutusData.unit()));

    check("Burn", builder(tx, issuer).completeAndWait());
}
```

A burn has its own verb because it needs two approvals: the transfer logic spends the tokens from the wallet, and the minting logic approves the negative mint. `BurnAuthorization` carries a redeemer for each. This token uses one script for both roles, and that script runs once, so you pass the same value twice.

There's no `withRequiredSigners` here. The issuer is spending their own smart wallet, so the library already declares them as the owner, and that's the same key the script checks.

```text
final           alice=900 bob=60 issuer=0
```

## Run it

Here is the complete class:

<details>
<summary>IssuerApprovedToken.java</summary>

```java
package tutorial;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Registration;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.RegistryNodeSpec;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class IssuerApprovedToken {

    /** issuer_approved.issuer_approved.withdraw from plutus.json, before the issuer is applied. */
    private static final String COMPILED_CODE =
            "5895010100229800aba2aba1aab9faab9eaab9dab9a9bae002488888896600264646644b3001"
            + "3370e900218039baa00189991198008009bac300c300d300d300d300d300d300d300d300d30"
            + "0a3754601800c601460106ea8004896600200314a115980099b8f375c601800200f14a31330"
            + "02002300d0014020805a2c8030c020004c020c024004c020004c010dd5004452689b2b200401";

    private static final String ASSET_NAME = "TutorialToken";

    /** How long to let the backend's UTxO view catch up after a confirmed transaction. */
    private static final Duration SETTLE_TIME = Duration.ofSeconds(60);

    private final BackendService backend;
    private final Network network;
    private final Account issuer;
    private final Account alice;
    private final Account bob;

    private final Cip113ProgrammableTokenService tokens;
    private final Cip113ProtocolService protocol;
    private final Address issuerAddress;

    private PlutusScript logicScript;
    private Credential logic;
    private String policyId;

    public IssuerApprovedToken(BackendService backend, Cip113ProgrammableTokenService tokens,
                               Network network, Account issuer, Account alice, Account bob) {
        this.backend = backend;
        this.network = network;
        this.issuer = issuer;
        this.alice = alice;
        this.bob = bob;
        this.issuerAddress = new Address(issuer.baseAddress());
        this.tokens = tokens;
        this.protocol = tokens.advanced();
    }

    public static void main(String[] args) throws Exception {
        String projectId = System.getenv("BF_PROJECT_ID");
        String mnemonic = System.getenv("TUTORIAL_MNEMONIC");
        Network network = Networks.preview();

        BackendService backend = new BFBackendService(Constants.BLOCKFROST_PREVIEW_URL, projectId);

        // One mnemonic, three separate accounts: the issuer and two holders.
        Account issuer = Account.createFromMnemonic(network, mnemonic, 0, 0);
        Account alice = Account.createFromMnemonic(network, mnemonic, 1, 0);
        Account bob = Account.createFromMnemonic(network, mnemonic, 2, 0);

        Cip113ProgrammableTokenService tokens =
                Cip113ProgrammableTokenService.create(backend, Cip113Deployments.PREVIEW);

        new IssuerApprovedToken(backend, tokens, network, issuer, alice, bob).run();
    }

    public void run() throws Exception {
        prepareLogicScript();
        fundAlice();
        registerRewardAccount();
        registerAndMint();
        printBalances("after mint");
        transferWithoutApproval();
        transfer();
        printBalances("after transfer");
        seize();
        printBalances("after seizure");
        burn();
        printBalances("final");
    }

    // -- Step: the token's logic script

    private void prepareLogicScript() throws Exception {
        byte[] issuerKeyHash = issuer.hdKeyPair().getPublicKey().getKeyHash();

        String applied = AikenScriptUtil.applyParamToScript(
                ListPlutusData.of(BytesPlutusData.of(issuerKeyHash)), COMPILED_CODE);
        logicScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(applied, PlutusVersion.v3);
        logic = Credential.fromScript(logicScript.getPolicyId());

        // Nothing on chain has used this script yet, so no backend can serve it.
        protocol.scripts().register(logicScript);

        policyId = value(protocol.derivePolicyId(logic));
        System.out.println("Logic script : " + logicScript.getPolicyId());
        System.out.println("Policy id    : " + policyId);
    }

    private void fundAlice() {
        Tx tx = new Tx()
                .payToAddress(alice.baseAddress(), Amount.ada(30))
                .from(issuer.baseAddress());

        check("Fund alice", new QuickTxBuilder(backend)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(issuer))
                .completeAndWait());
    }

    // -- Step: withdraw-zero needs a registered reward account

    private void registerRewardAccount() {
        String rewardAddress = AddressProvider.getRewardAddress(logic, network).toBech32();

        Result<String> result = new QuickTxBuilder(backend)
                .compose(new Tx()
                        .registerStakeAddress(rewardAddress)
                        .from(issuer.baseAddress()))
                .withSigner(SignerProviders.signerFrom(issuer))
                .completeAndWait();

        if (!result.isSuccessful() && result.getResponse().contains("StakeKeyRegistered")) {
            System.out.println("Reward account already registered");
            return;
        }
        check("Reward acct", result);
    }

    // -- Step: register the token and mint its first supply

    private void registerAndMint() {
        RegistryNodeSpec spec = RegistryNodeSpec.builder()
                .mintingLogicScript(logic)
                .transferLogicScript(logic)
                .thirdPartyTransferLogicScript(logic)
                .build();

        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(issuer.baseAddress())
                .register("tutorial", Cip113Registration.from(spec), PlutusData.unit())
                .mint(ProgrammableTokenPolicyRef.named("tutorial"), alice.baseAddress(),
                        List.of(new Asset(ASSET_NAME, BigInteger.valueOf(1000))),
                        PlutusData.unit(), null);

        check("Register", builder(tx, issuer)
                .withRequiredSigners(issuerAddress)
                .completeAndWait());
    }

    // -- Step: a transfer without the issuer's signature

    private void transferWithoutApproval() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(alice.baseAddress())
                .transfer(bob.baseAddress(), Amount.asset(policyId, ASSET_NAME, 100), PlutusData.unit());

        try {
            builder(tx, alice).completeAndWait();
        } catch (TxBuildException e) {
            System.out.println("Rejected     : " + e.getMessage());
            return;
        }
        throw new IllegalStateException("The transfer should not have passed without the issuer");
    }

    // -- Step: the same transfer, approved

    private void transfer() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(alice.baseAddress())
                .transfer(bob.baseAddress(), Amount.asset(policyId, ASSET_NAME, 100), PlutusData.unit());

        check("Transfer", builder(tx, alice)
                .withRequiredSigners(issuerAddress)
                .withSigner(SignerProviders.signerFrom(issuer))
                .completeAndWait());
    }

    // -- Step: seize tokens without the holder's signature

    private void seize() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(issuer.baseAddress())
                .thirdPartyTransfer(bob.baseAddress(), issuer.baseAddress(),
                        Amount.asset(policyId, ASSET_NAME, 40), PlutusData.unit());

        check("Seize", builder(tx, issuer)
                .withRequiredSigners(issuerAddress)
                .completeAndWait());
    }

    // -- Step: burn

    private void burn() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx()
                .from(issuer.baseAddress())
                .burn(policyId, List.of(new Asset(ASSET_NAME, BigInteger.valueOf(40))),
                        BurnAuthorization.of(PlutusData.unit(), PlutusData.unit()));

        check("Burn", builder(tx, issuer).completeAndWait());
    }

    // -- Helpers

    private QuickTxBuilder.TxContext builder(Tx tx, Account sender) {
        return new QuickTxBuilder(backend)
                .withExtension(tokens.extension())
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(sender))
                .withTxEvaluator(new AikenTransactionEvaluator(backend));
    }

    private void printBalances(String label) {
        System.out.printf("%-15s alice=%s bob=%s issuer=%s%n", label,
                balance(alice), balance(bob), balance(issuer));
    }

    private BigInteger balance(Account holder) {
        String unit = policyId + HexUtil.encodeHexString(ASSET_NAME.getBytes(StandardCharsets.UTF_8));
        return value(protocol.getProgrammableBalance(new Address(holder.baseAddress()))).stream()
                .filter(amount -> unit.equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static void check(String step, Result<String> result) {
        if (!result.isSuccessful())
            throw new IllegalStateException(step + " failed: " + result.getResponse());
        System.out.printf("%-12s : %s%n", step, result.getValue());
        settle();
    }

    private static void settle() {
        try {
            Thread.sleep(SETTLE_TIME.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T value(Result<T> result) {
        if (!result.isSuccessful()) throw new IllegalStateException(result.getResponse());
        return result.getValue();
    }
}
```

</details>

Running it on Preview prints something like this, with your own hashes:

```text
Logic script : 88b22cd904b2aee70c4401e1946d7f9dc64f994f2cd78d63506659e7
Policy id    : 6eea33c0c1b7604ff56e3906ed5b4ebac98791beec17b9d6a6eb30cb
Fund alice   : 31035ba6028e7503a46855b76ee670cb40e9e250d245156c6bfc9dc305363c4c
Reward acct  : d1eb35c75cea0267263af683b753e9f259a2b0278edc45af4baddcdcb5dfff26
Register     : 1888b7ded91938a781bdaf2b190d0b0d2a7209c6c7266620c35da43276019a76
after mint      alice=1000 bob=0 issuer=0
Rejected     : Error while evaluating script cost
Transfer     : dd17f6d0ab4bd396d0e813f2266681b974ca3f97204c84ad37b34047fc11bcc4
after transfer  alice=900 bob=100 issuer=0
Seize        : bffa140c42094db1409d568f1bcb8f5fe56c68b4046b05a1dc155f0055d34db9
after seizure   alice=900 bob=60 issuer=40
Burn         : 2b93d79a3a754975507ded23ff6763bd9ef94af5ec440660722c8c3291957b25
final           alice=900 bob=60 issuer=0
```

The whole run cost about 7 tADA from the issuer and 1.6 tADA from Alice, on top of the 30 tADA the issuer sends her.

You can look up each transaction hash in a [Preview explorer](https://preview.cardanoscan.io).

This story runs once per mnemonic, because the token stays registered. To run it from the start again, create a new wallet. With the pauses, a run takes about six minutes.

## What you built

| Action | Verb | Who signs | Logic that runs |
|---|---|---|---|
| Register and mint | `register`, `mint` | Issuer | Minting |
| Transfer | `transfer` | Holder and issuer | Transfer |
| Seize | `thirdPartyTransfer` | Issuer | Third-party |
| Burn | `burn` | Holder (here, the issuer) | Transfer and minting |

The "issuer" requirement comes from your script, not from the protocol. A different script gives you different rules, and the Java code barely changes.

## Troubleshooting

| What you see | Cause | Fix |
|---|---|---|
| `All inputs are spent. Transaction has probably already been included` | The backend returned a UTxO that a confirmed transaction had already spent | Wait and retry, or increase `SETTLE_TIME` |
| `Could not resolve the minting logic script ...` | The chain hasn't revealed your script, and you didn't hand it over | Call `protocol.scripts().register(script)` before building |
| `ConwayWithdrawalsMissingAccounts` in the submit response | The script's reward account isn't registered | Run [Step 3](#step-3-fund-alice-and-register-the-reward-account) |
| `No covering registry node for policy ...` | That policy is already registered, usually because you ran the story twice | Skip `register`, or create a new wallet |
| `Error while evaluating script cost` | A script rejected the transaction, usually because the issuer isn't declared | Add `withRequiredSigners(issuerAddress)` |
| `MissingVKeyWitnessesUTXOW` in the submit response | A signer was declared but didn't sign | Add the matching `withSigner(...)` |
| `Not enough plain ADA to fund this transaction safely` | The sender's normal address has too little ADA in ADA-only UTxOs | Send ADA to that address; about 4.5 ADA covered a transfer in testing |
| `Smart wallet ... does not hold enough` | The sender's smart wallet doesn't have those tokens | Check `getProgrammableBalance` |

## Limits

- **Logic credentials must be scripts.** Registrations and updates that name a verification key are refused, so you can't create a token this library couldn't operate.
- **Unfracking isn't supported.** `unfrack(...)` throws `UnsupportedOperationException` on every deployment, and none of the public deployments can unfrack.
- **A third-party transaction acts on one policy,** and owner transfers and burns can't share it.
- **One `Tx` per transaction.** Write all programmable-token operations of a transaction in a single `Tx`, because splitting them across `compose(tx1, tx2)` is refused.
- **Each programmable output holds one policy,** and inline datums are limited by the deployment's `max_inline_datum_bytes`.
- **Protocol upgrades and authority changes** aren't authored by this module.

## Next steps

- Give each role its own script. For example, keep transfers open and add a denylist using `globalStateCs` on `RegistryNodeSpec`.
- Read the [module README](https://github.com/bloxbean/cardano-client-lib/blob/master/programmable-token/README.md) for `thirdPartyBurn`, `updateRegistry`, inline datums and custom deployments.
- Store these transactions as YAML with [TxPlan](/txplan/overview/). Programmable-token plans go through `tokens.txPlanCodec()`, not plain `TxPlan.toYaml()`.
- Run several steps in sequence with [TxFlow](/txflow/overview/), registering the extension on the runtime.
