---
title: "Minting an NFT"
description: "Mint a one-of-a-kind token with CIP-25 metadata and a time-locked policy, using QuickTx."
---

An NFT on Cardano is a native token with a quantity of 1, whose policy can't mint any more. In this tutorial you'll create that policy, describe the token with CIP-25 metadata, mint it on the Preview testnet and find it in your wallet.

**Before you start**, follow [Your first transaction](/learn/simple-transfer/) to [create an account](/learn/simple-transfer/#create-sender-and-receiver-accounts), fund it from the [faucet](/learn/simple-transfer/#topup-sender-account-with-test-ada) and get a Blockfrost project id. The [dependencies](/start-here/installation/) are `cardano-client-lib` and `cardano-client-backend-blockfrost`.

The code reads your settings from the environment:

```bash
export BF_PROJECT_ID=<your Blockfrost Preview project id>
export TUTORIAL_MNEMONIC="<your 24 words>"
```

## Step 1: Create the policy

A token's policy decides who can mint it and when. A policy id is the hash of its script, so a new script gives you a new policy.

```java
long deadline = backend.getBlockService().getLatestBlock().getValue().getSlot() + 3600;
Keys policyKeys = KeyGenUtil.generateKey();
ScriptAll policyScript = new ScriptAll()
        .addScript(ScriptPubkey.create(policyKeys.getVkey()))
        .addScript(new RequireTimeBefore(deadline));
Policy policy = new Policy(policyScript, List.of(policyKeys.getSkey()));
```

`ScriptAll` means both conditions must hold: the new key signs, and the transaction is valid only before `deadline`. One slot is one second on Preview, so that's one hour from now.

This is what makes the token one of a kind. A quantity of 1 alone doesn't: without the deadline, the key holder could mint the same token again.

## Step 2: Describe the token

Wallets and explorers read an NFT's name and image from [CIP-25](/standards/cip25/) metadata, which you attach to the minting transaction.

```java
Asset nftAsset = new Asset("MyFirstNft", BigInteger.ONE);
NFT nft = NFT.create()
        .assetName(nftAsset.getName())
        .name("My First NFT")
        .image("ipfs://QmYwAPJzv5CZsnA625s3Xf2nemtYgPpHdWEz79ojWnPbdG")
        .description("Minted with Cardano Client Lib");
NFTMetadata metadata = NFTMetadata.create().addNFT(policy.getPolicyId(), nft);
```

Replace `image` with the location of your own file. This sample points to a placeholder on IPFS.

## Step 3: Mint

```java
Tx tx = new Tx()
        .mintAssets(policy.getPolicyScript(), nftAsset, minter.baseAddress())
        .attachMetadata(metadata)
        .from(minter.baseAddress());

TxResult result = new QuickTxBuilder(backend)
        .compose(tx)
        .validTo(deadline)
        .withSigner(SignerProviders.signerFrom(minter))
        .withSigner(SignerProviders.signerFrom(policy))
        .completeAndWait(Duration.ofMinutes(5));
```

- `validTo(deadline)` is required. The script accepts a transaction only if it declares that it expires before the deadline.
- There are two signers: your account pays the fee, and the policy key authorizes the mint.
- `completeAndWait` stops waiting after its timeout and then reports the status `TIMEOUT`, even though the transaction may still land. That's why the complete code below checks for `TxStatus.CONFIRMED`.

## Step 4: Find your NFT

A token's unit is its policy id followed by its hex-encoded name. Ask the backend which UTxO at your address holds it:

```java
String unit = AssetUtil.getUnit(policy.getPolicyId(), nftAsset);
holding = backend.getUtxoService().getUtxos(minter.baseAddress(), unit, 1, 1).getValue();
```

The complete code retries this for up to a minute, because a backend's UTxO view can lag behind a confirmed transaction.

## Run it

<details>
<summary>MintNft.java</summary>

```java
package tutorial;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.quicktx.TxStatus;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.script.RequireTimeBefore;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

public class MintNft {

    public static void main(String[] args) throws Exception {
        BackendService backend = new BFBackendService(
                Constants.BLOCKFROST_PREVIEW_URL, System.getenv("BF_PROJECT_ID"));
        Account minter = new Account(Networks.preview(), System.getenv("TUTORIAL_MNEMONIC"));

        mint(backend, minter);
    }

    public static void mint(BackendService backend, Account minter) throws Exception {
        // 1. The policy: this key must sign, and only before the deadline
        long deadline = backend.getBlockService().getLatestBlock().getValue().getSlot() + 3600;
        Keys policyKeys = KeyGenUtil.generateKey();
        ScriptAll policyScript = new ScriptAll()
                .addScript(ScriptPubkey.create(policyKeys.getVkey()))
                .addScript(new RequireTimeBefore(deadline));
        Policy policy = new Policy(policyScript, List.of(policyKeys.getSkey()));

        // 2. The token and its CIP-25 metadata
        Asset nftAsset = new Asset("MyFirstNft", BigInteger.ONE);
        NFT nft = NFT.create()
                .assetName(nftAsset.getName())
                .name("My First NFT")
                .image("ipfs://QmYwAPJzv5CZsnA625s3Xf2nemtYgPpHdWEz79ojWnPbdG")
                .description("Minted with Cardano Client Lib");
        NFTMetadata metadata = NFTMetadata.create().addNFT(policy.getPolicyId(), nft);

        // 3. The transaction
        Tx tx = new Tx()
                .mintAssets(policy.getPolicyScript(), nftAsset, minter.baseAddress())
                .attachMetadata(metadata)
                .from(minter.baseAddress());

        TxResult result = new QuickTxBuilder(backend)
                .compose(tx)
                .validTo(deadline)
                .withSigner(SignerProviders.signerFrom(minter))
                .withSigner(SignerProviders.signerFrom(policy))
                .completeAndWait(Duration.ofMinutes(5));

        if (result.getTxStatus() != TxStatus.CONFIRMED)
            throw new IllegalStateException("Not confirmed (" + result.getTxStatus() + "): " + result.getResponse());
        System.out.println("Tx hash   : " + result.getTxHash());

        // 4. Find the token in the minter's wallet
        String unit = AssetUtil.getUnit(policy.getPolicyId(), nftAsset);
        List<Utxo> holding = List.of();
        for (int attempt = 0; attempt < 12 && holding.isEmpty(); attempt++) {
            Thread.sleep(5_000);
            holding = backend.getUtxoService().getUtxos(minter.baseAddress(), unit, 1, 1).getValue();
        }
        if (holding.isEmpty()) throw new IllegalStateException("Token not found yet: " + unit);

        BigInteger quantity = holding.get(0).getAmount().stream()
                .filter(amount -> unit.equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .findFirst().orElseThrow();
        System.out.println("Policy id : " + policy.getPolicyId());
        System.out.println("Quantity  : " + quantity);
    }
}
```

</details>

Running it on Preview prints something like this, with your own values:

```text
Tx hash   : c7050d3519d6d1514c75531e9aa0eb0697d5918882de03d57636245c0b5c1693
Policy id : 787db0464ad195b5c5d0586c3fabfad3d144f6c68e66d1238cf0fef6
Quantity  : 1
```

To see the metadata, look up your transaction hash in a [Preview explorer](https://preview.cardanoscan.io).

## Next steps

- [Native tokens](/learn/native-tokens/): send tokens to many receivers.
- [CIP-68](/standards/cip68/): NFTs whose metadata can be updated.
- [cardano-client-examples](https://github.com/bloxbean/cardano-client-examples) has more minting samples, such as [minting with QuickTx](https://github.com/bloxbean/cardano-client-examples/blob/main/src/test/java/com/bloxbean/cardano/client/example/quicktx/Minting.java) and [minting with several scripts](https://github.com/bloxbean/cardano-client-examples/blob/main/src/test/java/com/bloxbean/cardano/client/example/quicktx/MultiScriptMinting.java). They aren't updated with every release.
