---
description: Governance API Usage
sidebar_label: Governance Api
sidebar_position: 1
---

# Governance Api (Preview)

QuickTx Api now supports governance related transactions. It's supported through the existing `Tx` class.

**Version:** 0.5.1 and later

**Note:** This is a preview version and the API is subject to change.

Check out QuickTx Governance API [**integration tests**](https://github.com/bloxbean/cardano-client-lib/blob/master/quicktx/src/it/java/com/bloxbean/cardano/client/quicktx/GovernanceTxIT.java) for more examples.

## Pre-requisites
Create a `QuickTxBuilder` instance and required accounts.

```java
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
Account accout = new Account("your mnemonic words");
String address = account.baseAddress();
```

## 1. DRep API

**Note:** To find the DRepId of an account, you can use the `drepId()` method of the `Account` class.

### Register DRep

The following example shows how to register an account as a DRep.

```java
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx drepRegTx = new Tx()
        .registerDRep(account, anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(drepRegTx)
        .withSigner(SignerProviders.signerFrom(account))
        .withSigner(SignerProviders.signerFrom(account.drepHdKeyPair()))
        .completeAndWait(s -> System.out.println(s));
```

### Deregister DRep

To deregister an account as a DRep, use the `unregisterDRep()` method of the `Tx` class. The transaction needs to be
signed by the account for tx fee payment and by the DRep Key of the account.,

```java
Tx tx = new Tx()
        .unregisterDRep(account.drepCredential())
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Update DRep

To update the DRep information, use the `updateDRep()` method of the `Tx` class. The transaction needs to be signed
by the account for tx fee payment and by the DRep Key of the account.

In the following example, the DRep information is updated to remove the anchor.

```java
 Tx drepRegTx = new Tx()
        .updateDRep(account.drepCredential())
        .from(senderAddr);

Result<String> result = quickTxBuilder.compose(drepRegTx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

To update the DRep information with a new anchor.

```java
var newAnchor = new Anchor("<anchor_url>", "<anchor_datahash>");

Tx drepRegTx = new Tx()
        .updateDRep(account.drepCredential(), newAnchor)
        .from(senderAddr);

Result<String> result = quickTxBuilder.compose(drepRegTx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

## 2. Gov Action Create API

Using the `Tx` class, you can create a governance proposal such as 

- InfoAction
- NewConstitution
- NoConfidence
- ParameterChangeAction
- HardForkInitiationAction
- TreasuryWithdrawalsAction
- UpdateCommittee


Use the createProposal() method of the Tx class to create a proposal. In addition to the GovAction instance, you also need to
specify the amount of ADA to be deposited for the proposal creation and the return address (stake address) to which the
deposit will be returned.

The transaction needs to be signed by the account for tx fee & deposit and by the DRep credential of the account.

The required deposit amount for proposal creation is a protocol parameter (govActionDeposit) and it's currently set to 1000 ADA for Sanchonet.

**Note:** In future versions, the deposit amount will be automatically fetched from the protocol parameters.

### Create a Info Proposal

Use `InfoAction` to create a proposal with anchor for proposal information.

```java
var govAction = new InfoAction();
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(govAction, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Create new Constitution Proposal

Use `NewConstitution` to create a proposal with anchor for new constitution.

```java
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

var govAction = new NewConstitution();
govAction.setPrevGovActionId(new GovActionId("<prevGovActionTxHash>", prevGovActionIndex));
govAction.setConstitution(Constitution.builder()
        .anchor(anchor)
        .build());

Tx tx = new Tx()
        .createProposal(govAction, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Creae a NoConfidence Proposal

Use `NoConfidence` to create a proposal for no confidence.

```java
var noConfidence = new NoConfidence();
noConfidence.setPrevGovActionId(new GovActionId("<prevGovActionTxHash>", prevGovActionIndex));
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(noConfidence, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Create a ParameterChange Proposal

Use `ParameterChangeAction` to create a proposal for parameter change.

In the below example, the minPoolCost parameter is updated to 100 ADA.

```java
var parameterChange = new ParameterChangeAction();
parameterChange.setPrevGovActionId(new GovActionId("529736be1fac33431667f2b66231b7b66d4c7a3975319ddac7cfb17dcb5c4145", 0));
parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
                .minPoolCost(adaToLovelace(100))
                .build());

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);
        
Tx tx = new Tx()
        .createProposal(parameterChange, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Create a HardForkInitiation Proposal

Use `HardForkInitiationAction` to create a proposal for hard fork initiation.

```java
var hardforkInitiation = new HardForkInitiationAction();
hardforkInitiation.setPrevGovActionId(new GovActionId("416f7f01c548a85546aa5bbd155b34bb2802df68e08db4e843ef6da764cd8f7e", 0));
hardforkInitiation.setProtocolVersion(new ProtocolVersion(9, 0));

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(hardforkInitiation, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Create a TreasuryWithdrawal Proposal

Use `TreasuryWithdrawalsAction` to create a proposal for treasury withdrawal.

In the example below, a proposal is created to withdraw 20 ADA from the treasury and send it to the specified stake address.
```java
var treasuryWithdrawalsAction = new TreasuryWithdrawalsAction();
treasuryWithdrawalsAction.addWithdrawal(new Withdrawal("stake_test1ur6l9f5l9jw44kl2nf6nm5kca3nwqqkccwynnjm0h2cv60ccngdwa", adaToLovelace(20)));

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(treasuryWithdrawalsAction, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Create a UpdateCommittee Proposal

Use `UpdateCommittee` to create a proposal for updating the committee information.

In the example below, a proposal is created to update the quorum threshold to 1/3.

```java
var updateCommittee = new UpdateCommittee();
updateCommittee.setPrevGovActionId(new GovActionId("b3ce0371310a07a797657d19453d953bb352b6841c2f5c5e0bd2557189ef5c3a", 0));
updateCommittee.setQuorumThreshold(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(3)));

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(updateCommittee, adaToLovelace(1000), account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

## 3. Vote API

Use the `Tx` class to vote on a governance proposal. 

In addition to the GovActionId, you also need to specify Vote(Yes, No, Abstain), voter and an anchor for the vote information (optional).

The transaction needs to be signed by the account for tx fee payment and by the DRep credential of the account.

```java
var voter = new Voter(VoterType.DREP_KEY_HASH, account.drepCredential());
var govActionId = new GovActionId("5655fbb4ceafd34296fe58f6e3d28b8ff663a89e84aa0edd77bd02fe379cef4c", 0); //some gov action id

Tx tx = new Tx()
        .createVote(voter, govActionId, Vote.NO)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

## 4. Vote Delegation API

Use the `Tx` class to delegate voting rights to a DRep.

```java
DRep drep = DRepId.toDrep(drepId, DRepType.ADDR_KEYHASH);

Tx tx = new Tx()
        .delegateVotingPowerTo(account, drep)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.stakeKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```
