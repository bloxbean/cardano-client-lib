---
title: "Staking & delegation"
description: "Register a stake address, delegate to a pool, withdraw rewards and deregister — the full staking lifecycle with QuickTx."
---

Staking is where most Cardano applications first meet **certificates**. A certificate is not a
payment; it is an instruction to the ledger that changes the state of a stake address. QuickTx
builds them the same way it builds payments — as calls on `Tx` — but they carry one extra
requirement you have to get right: **which keys must sign**.

By the end of this page you will have registered a stake address, delegated it to a pool,
withdrawn rewards, and deregistered it to reclaim the deposit.

**Before you start**: finish [Your first transaction](/learn/simple-transfer/). You need a funded
account on Preview or a [local devnet](/learn/test-with-yaci-devkit/). On a default Yaci DevKit devnet, you can delegate to
its pool, `pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy`.

## The staking lifecycle

Four operations, in order:

1. **Register** the stake address — pays a refundable deposit (2 ADA on mainnet)
2. **Delegate** it to a stake pool — takes effect two epochs later
3. **Withdraw** rewards as they accumulate, which also needs a vote delegation
4. **Deregister** when you are done — refunds the deposit

Steps 1 and 2 are usually done in a single transaction.

## An account's two addresses

Every `Account` carries both a payment and a staking credential:

```java
Account account = Account.createFromMnemonic(Networks.preview(), mnemonic);

account.baseAddress();     // addr_test1... — holds funds, pays fees
account.stakeAddress();    // stake_test1... — accrues rewards, delegates
```

The **base address** already embeds the staking credential — that is what makes it a *base*
address rather than an enterprise address. Funds sitting at your base address are what counts
toward your delegated stake; you do not move ADA to the stake address.

## 1. Register and delegate

Do both in one transaction — there is no reason to pay two fees:

```java
Tx tx = new Tx()
        .registerStakeAddress(account.baseAddress())
        .delegateTo(account.baseAddress(), poolId)
        .from(account.baseAddress());

TxResult result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .withSigner(SignerProviders.stakeKeySignerFrom(account))
        .completeAndWait();
```

`poolId` is the pool's bech32 id (`pool1...`). You can pass a base address to
`registerStakeAddress` and `delegateTo` — the library extracts the staking credential from it.

:::caution[Delegation needs the stake key]
`delegateTo` produces a delegation certificate, and the ledger requires a **witness from the stake
key** to accept it. Miss `stakeKeySignerFrom(...)` and the transaction is rejected at submission
with a missing-witness error.

Registration alone is the exception — a registration certificate does not need the stake key,
only the payment key that pays the deposit. Deregistration and withdrawal do need it.
:::

### Who signs what

| Operation | Payment key | Stake key |
|---|---|---|
| `registerStakeAddress` | Yes | No |
| `delegateTo` | Yes (fees) | **Yes** |
| `delegateVotingPowerTo` | Yes (fees) | **Yes** |
| `withdraw` | Yes (fees) | **Yes** |
| `deregisterStakeAddress` | Yes (fees) | **Yes** |

When several stake addresses are involved, add one `stakeKeySignerFrom(...)` per account.

## 2. When delegation takes effect

Delegation is not immediate, and this surprises people:

| Epoch | What happens |
|---|---|
| `n` | You submit the delegation certificate |
| `n + 1` | Stake snapshot is taken |
| `n + 2` | Your stake is active for the pool |
| `n + 4` | First rewards for that stake appear, at the start of the epoch |

So roughly **15–20 days on mainnet** before the first reward. Preview's epochs last one day. On a
[local devnet](/learn/test-with-yaci-devkit/) they take minutes, or less if you set a shorter epoch
length, which is why a devnet is the right place to test a staking flow end to end.

## 3. Withdraw rewards

The ledger won't let a key-based stake address withdraw until it has delegated its voting power. It
rejects the transaction with `ConwayWdrlNotDelegatedToDRep`, even when the amount is zero. If you
don't want to choose a DRep, delegate to the built-in "always abstain" option. You only need to do
this once:

```java
Tx voteDelegation = new Tx()
        .delegateVotingPowerTo(account.baseAddress(), DRep.abstain())
        .from(account.baseAddress());

new QuickTxBuilder(backendService)
        .compose(voteDelegation)
        .withSigner(SignerProviders.signerFrom(account))
        .withSigner(SignerProviders.stakeKeySignerFrom(account))
        .completeAndWait();
```

Withdrawals must take the **entire** reward balance — the ledger has no partial withdrawal:

```java
AccountInformation info = backendService.getAccountService()
        .getAccountInformation(account.stakeAddress())
        .getValue();

// withdrawableAmount is a lovelace string on AccountInformation.
BigInteger rewardBalance = new BigInteger(info.getWithdrawableAmount());

Tx tx = new Tx()
        .withdraw(account.stakeAddress(), rewardBalance)
        .from(account.baseAddress());

TxResult result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .withSigner(SignerProviders.stakeKeySignerFrom(account))
        .completeAndWait();
```

Query the balance immediately before building. If the amount in the transaction does not match the
reward balance exactly at the moment of validation, the transaction fails — so a value you cached
earlier, or one that crossed an epoch boundary, will be rejected.

To send the rewards somewhere other than the change address, use the three-argument form:
`withdraw(stakeAddress, amount, receiverAddress)`.

## 4. Deregister

Deregistration refunds the deposit and stops delegation:

```java
Tx tx = new Tx()
        .deregisterStakeAddress(account.baseAddress())
        .from(account.baseAddress());

TxResult result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .withSigner(SignerProviders.stakeKeySignerFrom(account))
        .completeAndWait();
```

Withdraw any outstanding rewards **first**. Deregistering with a non-zero reward balance is
rejected.

## Governance delegation is separate

In the Conway era a stake address has *two* independent delegations:

- **`delegateTo(address, poolId)`** — block-production stake, earns rewards
- **`delegateVotingPowerTo(address, drep)`** — governance voting power

Delegating to a pool does not give your voting power to anyone, and delegating voting power to a
DRep does not change which pool you stake with. The one link is that withdrawing rewards
[needs a vote delegation](#3-withdraw-rewards). See
[Governance API](/governance/overview/) for the voting side.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Missing witness / `MissingVKeyWitnessesUTXOW` | No `stakeKeySignerFrom(...)` on a certificate that needs it |
| `StakeKeyRegisteredDELEG` | Address already registered — skip registration and just delegate |
| `StakeKeyNotRegisteredDELEG` | Delegating before registering, or after deregistering |
| `ConwayWdrlNotDelegatedToDRep` | The stake address hasn't delegated its voting power; see [Withdraw rewards](#3-withdraw-rewards) |
| `ConwayIncompleteWithdrawals` | Amount does not exactly equal the current reward balance |
| Delegated but no rewards | Fewer than 4 epochs have passed, or the pool produced no blocks |

## Next

- [Calling a Plutus script](/learn/plutus-scripts/) — the previous step, if you skipped it
- [Governance API](/governance/overview/) — DReps, voting and proposals
- [CIP-113 programmable tokens](/learn/cip113-programmable-tokens/) — build a token with its own transfer rules
- [What's next](/learn/whats-next/) — where to go after the guided path
