---
title: "Governance API"
description: "APIs for Cardano governance operations (DRep, Voting, Proposals)"
sidebar_position: 1
---

# Governance API

The Governance API provides comprehensive functionality for participating in Cardano's on-chain governance system. It supports DRep registration and management, governance proposal creation, voting, and delegation operations through the QuickTx framework.

## Key Features

- **DRep Management**: Register, update, and deregister as a Delegated Representative
- **Proposal Creation**: Create various types of governance proposals (Info, Constitution, Parameter Changes, etc.)
- **Voting System**: Vote on governance proposals with different voter types
- **Delegation**: Delegate voting power to DReps
- **Anchor Support**: Attach metadata anchors to proposals and votes
- **Multi-Signature Support**: Support for committee and stake pool voting

## Core Classes

### Tx Class
The main class for building governance transactions, extending QuickTx functionality.

### Governance Actions
- `InfoAction` - Information proposals
- `NewConstitution` - Constitution update proposals
- `NoConfidence` - No confidence proposals
- `ParameterChangeAction` - Protocol parameter changes
- `HardForkInitiationAction` - Hard fork initiation
- `TreasuryWithdrawalsAction` - Treasury withdrawal proposals
- `UpdateCommittee` - Committee update proposals

### Supporting Classes
- `Voter` - Represents different voter types (DRep, SPO, Committee)
- `Vote` - Vote options (Yes, No, Abstain)
- `Anchor` - Metadata anchor for proposals
- `GovActionId` - References to governance actions

## Usage Examples

### Setup

Create QuickTxBuilder and Account instances for governance operations:

```java
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

Account account = new Account(Networks.preprod(), "your mnemonic words");
String address = account.baseAddress();
```

### DRep Management

Manage Delegated Representative (DRep) registration and operations:

#### Register DRep

Register an account as a Delegated Representative:

```java
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx drepRegTx = new Tx()
        .registerDRep(account, anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(drepRegTx)
        .withSigner(SignerProviders.signerFrom(account))
        .withSigner(SignerProviders.signerFrom(account.drepHdKeyPair()))
        .completeAndWait(System.out::println);
```

#### Deregister DRep

Deregister an account as a DRep:

```java
Tx tx = new Tx()
        .unregisterDRep(account.drepCredential())
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

#### Update DRep

Update DRep information (remove anchor):

```java
 Tx drepRegTx = new Tx()
        .updateDRep(account.drepCredential())
        .from(senderAddr);

Result<String> result = quickTxBuilder.compose(drepRegTx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

Update DRep information with a new anchor:

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

### Governance Proposals

Create various types of governance proposals using the `createProposal()` method. Any account can create a proposal — a DRep key signature is **not** required.

#### Info Proposal

Create an information proposal:

```java
var govAction = new InfoAction();
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(govAction, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

#### Constitution Proposal

Create a new constitution proposal:

```java
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

var govAction = new NewConstitution();
govAction.setPrevGovActionId(new GovActionId("<prevGovActionTxHash>", prevGovActionIndex));
govAction.setConstitution(Constitution.builder()
        .anchor(anchor)
        .build());

Tx tx = new Tx()
        .createProposal(govAction, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

#### No Confidence Proposal

Create a no confidence proposal:

```java
var noConfidence = new NoConfidence();
noConfidence.setPrevGovActionId(new GovActionId("<prevGovActionTxHash>", prevGovActionIndex));
var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(noConfidence, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

#### Parameter Change Proposal

Create a protocol parameter change proposal:

```java
var parameterChange = new ParameterChangeAction();
parameterChange.setPrevGovActionId(new GovActionId("529736be1fac33431667f2b66231b7b66d4c7a3975319ddac7cfb17dcb5c4145", 0));
parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
        .minPoolCost(ADAConversionUtil.adaToLovelace(100))
        .build());

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);
        
Tx tx = new Tx()
        .createProposal(parameterChange, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

#### Hard Fork Initiation Proposal

Create a hard fork initiation proposal:

```java
var hardforkInitiation = new HardForkInitiationAction();
hardforkInitiation.setPrevGovActionId(new GovActionId("416f7f01c548a85546aa5bbd155b34bb2802df68e08db4e843ef6da764cd8f7e", 0));
hardforkInitiation.setProtocolVersion(new ProtocolVersion(9, 0));

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(hardforkInitiation, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

#### Treasury Withdrawal Proposal

Create a treasury withdrawal proposal:
```java
var treasuryWithdrawalsAction = new TreasuryWithdrawalsAction();
treasuryWithdrawalsAction.addWithdrawal(new Withdrawal("<stake_address>", adaToLovelace(20)));

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(treasuryWithdrawalsAction, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

#### Update Committee Proposal

Create a committee update proposal:

```java
var updateCommittee = new UpdateCommittee();
updateCommittee.setPrevGovActionId(new GovActionId("b3ce0371310a07a797657d19453d953bb352b6841c2f5c5e0bd2557189ef5c3a", 0));
updateCommittee.setQuorumThreshold(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(3)));

var anchor = new Anchor("<anchor_url>", <anchor_datahash>);

Tx tx = new Tx()
        .createProposal(updateCommittee, account.stakeAddress(), anchor)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(s -> System.out.println(s));
```

### Voting

Vote on governance proposals:

```java
var voter = new Voter(VoterType.DREP_KEY_HASH, account.drepCredential());
var govActionId = new GovActionId("5655fbb4ceafd34296fe58f6e3d28b8ff663a89e84aa0edd77bd02fe379cef4c", 0); //some gov action id

Tx tx = new Tx()
        .createVote(voter, govActionId, Vote.NO)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.drepKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Vote Delegation

Delegate voting power to a DRep:

```java
DRep drep = DRepId.toDrep(drepId, DRepType.ADDR_KEYHASH);

Tx tx = new Tx()
        .delegateVotingPowerTo(account, drep)
        .from(address);

Result<String> result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.stakeKeySignerFrom(account))
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```
