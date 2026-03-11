# Unified Tx API & Deposit Resolution

**Status**: Implemented
**Modules**: `quicktx`, `transaction-spec`
**Since**: Cardano Client Library 0.8.0-preview

## 1. Overview

This document describes two closely related changes to the QuickTx transaction-building API:

1. **Unified Tx API** — All smart-contract operations (`collectFrom`, `mintAsset`, `attachValidator`, etc.) are now available directly on the `Tx` class. The separate `ScriptTx` class is deprecated. Users no longer need to decide upfront whether a transaction involves scripts.

2. **Unified Deposit Resolution** — Protocol deposits (stake registration, pool registration, DRep registration, governance proposals) are resolved through a single Phase 4 step that works identically for all transaction types. There are no dummy outputs and no separate code paths for script vs. non-script transactions.

Together, these changes mean you can build any Cardano transaction — from a simple ADA transfer to a multi-script governance action with deposits — using a single `Tx` class and a predictable four-phase build pipeline.

---

## 2. Unified Tx API

### 2.1 What Changed

Previously, building a transaction that involved Plutus scripts required using `ScriptTx`:

```java
// OLD — had to know upfront that you need ScriptTx
ScriptTx scriptTx = new ScriptTx()
    .collectFrom(utxo, redeemer)
    .payToAddress(receiver, Amount.ada(10))
    .attachSpendingValidator(script)
    .withChangeAddress(changeAddr);
```

If you started with `Tx` and later realised you needed a script input, you had to rewrite using `ScriptTx`. This created confusion and made it impossible to mix script and non-script operations in a single transaction object.

Now, all script operations live directly on `Tx`:

```java
// NEW — one class for everything
Tx tx = new Tx()
    .collectFrom(utxo, redeemer)           // script input
    .payToAddress(receiver, Amount.ada(10)) // regular payment
    .attachSpendingValidator(script)
    .withChangeAddress(changeAddr);
```

### 2.2 Before and After

**Before** — Separate classes forced early decisions:
```java
// Simple payment → Tx
Tx simpleTx = new Tx()
    .payToAddress(receiver, Amount.ada(50))
    .from(sender);

// Script interaction → ScriptTx (different class!)
ScriptTx scriptTx = new ScriptTx()
    .collectFrom(scriptUtxo, redeemer)
    .payToAddress(receiver, Amount.ada(10)) 
    .attachSpendingValidator(validator);

// Could NOT mix in one object
```

**After** — Everything in one class:
```java
// Simple payment
Tx simpleTx = new Tx()
    .payToAddress(receiver, Amount.ada(50))
    .from(sender);

// Script interaction — same class
Tx scriptTx = new Tx()
    .collectFrom(scriptUtxo, redeemer)
    .attachSpendingValidator(validator)
    .from(sender);

// Mix freely
Tx mixedTx = new Tx()
    .collectFrom(scriptUtxo, redeemer)
    .payToAddress(receiver, Amount.ada(10))
    .mintAsset(mintScript, asset, mintRedeemer, receiver)
    .registerStakeAddress(stakeAddr)
    .attachSpendingValidator(validator)
    .from(sender);
```

### 2.3 Script Methods on Tx

All methods below are available on `Tx` and return `Tx` for fluent chaining.

| Category | Methods |
|----------|---------|
| **Script Inputs** | `collectFrom(Utxo, redeemer)`, `collectFrom(Utxo, redeemer, datum)`, `collectFrom(List<Utxo>, ...)`, `collectFrom(scriptAddress, Predicate, ...)`, `collectFrom(scriptAddress, UtxoFilterSpec, ...)`, `collectFromList(scriptAddress, Predicate<List>, ...)` |
| **Reference Inputs** | `readFrom(Utxo...)`, `readFrom(TransactionInput...)`, `readFrom(txHash, outputIndex)` |
| **Minting (with script)** | `mintAsset(PlutusScript, asset, redeemer)`, `mintAsset(PlutusScript, assets, redeemer, receiver)`, `mintAsset(PlutusScript, assets, redeemer, receiver, outputDatum)`, `mintAsset(policyId, asset, redeemer)`, `mintAsset(policyId, assets, redeemer, receiver)`, `mintAsset(policyId, assets, redeemer, receiver, outputDatum)` |
| **Validator Attachment** | `attachSpendingValidator(PlutusScript)`, `attachMintValidator(PlutusScript)`, `attachCertificateValidator(PlutusScript)` |
| **Script-Protected Staking** | `registerStakeAddress(stakeAddr, redeemer, refInput)`, `deregisterStakeAddress(stakeAddr, redeemer, refInput)`, `delegateTo(stakeAddr, poolId, redeemer, refInput)` |
| **Script-Protected Governance** | `registerDRep(drep, redeemer, refInput)`, `unregisterDRep(drep, redeemer, refInput)`, `createProposal(proposal, redeemer, refInput)` |

### 2.4 How Script Detection Works

The builder does **not** rely on the class type alone. Instead, it uses data-driven detection via `hasScriptIntents()` in `AbstractTx`:

```java
// AbstractTx.java (line 386)
public boolean hasScriptIntents() {
    if (intentions == null || intentions.isEmpty()) return false;
    return intentions.stream().anyMatch(intent ->
            intent instanceof ScriptCollectFromIntent ||
            intent instanceof ScriptMintingIntent ||
            intent.hasRedeemer()
    );
}
```

`QuickTxBuilder` uses this to apply script-specific defaults (change address, from address) when needed:

```java
// QuickTxBuilder.java (line 614)
if (tx instanceof ScriptTx || tx.hasScriptIntents()) {
    // Set feePayer as change address and from address if not already set
}
```

This means:
- A `Tx` with only `payToAddress` → treated as a regular transaction
- A `Tx` with `collectFrom(utxo, redeemer)` → automatically detected as script-bearing
- A legacy `ScriptTx` → still works via `instanceof` check

### 2.5 Backward Compatibility

`ScriptTx` is annotated `@Deprecated` but remains fully functional. Existing code using `ScriptTx` will continue to work without changes. The deprecation is a signal to migrate to `Tx` at your convenience.

Key behavioral difference preserved: `ScriptTx.setDefaultFrom()` unconditionally overwrites the sender (legacy behavior), while `Tx.setDefaultFrom()` respects an explicit `from()` call and will not overwrite it.

---

## 3. Unified Deposit Resolution

### 3.1 What Changed

Previously, deposits were handled through dummy outputs and different code paths depending on the transaction type. This led to subtle bugs — a `payToAddress` output could be silently consumed to pay for a deposit, or UTXO selection could conflict with the deposit amount.

Now, deposit resolution is a dedicated **Phase 4** in the build pipeline. Deposit intents (`registerStakeAddress`, `registerPool`, `registerDRep`, `createProposal`) produce **no outputs** during Phase 1. Instead, Phase 4 determines how to fund the deposit after all other transaction building is complete.

### 3.2 The Four-Phase Build Pipeline

Every transaction goes through these phases in `AbstractTx.complete()`:

```
Phase 1: Collect Outputs
    ↓  Each intent's outputBuilder() is called.
    ↓  Deposit intents return null — no outputs created.
    ↓  Regular intents (payToAddress, mintAsset) create outputs.
    ↓
Phase 2: UTXO Selection
    ↓  Select inputs to cover payment outputs + fees.
    ↓  Creates ChangeOutput for excess funds.
    ↓  Deposits are NOT included in this calculation.
    ↓
Phase 3: Apply Certificates & Proposals
    ↓  Intents add certificates, withdrawals, proposals, etc.
    ↓  to the transaction body.
    ↓
Phase 4: Resolve Deposits
    ↓  DepositResolvers.resolveDeposits() runs.
    ↓  If no deposit intents → no-op.
    ↓  Otherwise, deducts deposit from outputs or selects new UTXOs.
    ↓
    → Fee calculation, signing, submission
```

The code in `AbstractTx.complete()` (line 435):
```java
// Phase 4: Resolve deposits — runs for ALL Txs with deposit intents
txBuilder = txBuilder.andThen(
    DepositResolvers.resolveDeposits(intentions, depositPayerAddress, getFromAddress(), depositMode)
);
```

### 3.3 Deposit Payer — Who Pays the Deposit?

When a transaction includes a deposit, someone needs to provide the ADA. The deposit payer is determined by a clear fallback hierarchy:

```
1. Explicit depositPayer(address) on TxContext    ← highest priority
       ↓ (if not set)
2. from(address) on the Tx                        ← normal sender
       ↓ (if not set, and Tx has script intents)
3. feePayer(address) on TxContext                  ← automatic fallback
```

**How it works in code**: `DepositResolvers` receives `depositPayerAddress` and `fromAddress`. It uses `depositPayerAddress != null ? depositPayerAddress : fromAddress`. The `fromAddress` is set either explicitly via `Tx.from()` or automatically via `setDefaultFrom()` which uses the fee payer.

**Examples**:

```java
// Case 1: Deposit paid by the sender (most common)
Tx tx = new Tx()
    .registerStakeAddress(stakeAddr)
    .from(myAddr);   // ← deposit comes from myAddr

builder.compose(tx)
    .withSigner(signerFrom(myAccount))
    .completeAndWait();
```

```java
// Case 2: Explicit deposit payer different from sender
Tx tx = new Tx()
    .collectFrom(scriptUtxo, redeemer)
    .registerStakeAddress(stakeAddr)
    .attachSpendingValidator(script)
    .from(scriptFunder);

builder.compose(tx)
    .feePayer(feePayerAddr)
    .depositPayer(treasuryAddr)         // ← deposits from treasury
    .withSigner(signerFrom(treasury))
    .withSigner(signerFrom(feePayerAccount))
    .completeAndWait();
```

```java
// Case 3: Script Tx — feePayer is automatic fallback
Tx tx = new Tx()
    .collectFrom(scriptUtxo, redeemer)
    .registerStakeAddress(stakeAddr)
    .attachSpendingValidator(script);
    // no from() — setDefaultFrom() will use feePayer

builder.compose(tx)
    .feePayer(myAddr)                   // ← deposit comes from myAddr (via fallback)
    .withSigner(signerFrom(myAccount))
    .completeAndWait();
```

### 3.4 Deposit Modes — How Deposits Are Funded

Once the deposit payer address is determined, the `DepositMode` controls **how** the deposit amount is sourced from the transaction. Set it via `TxContext.depositMode()`:

```java
builder.compose(tx)
    .depositMode(DepositMode.AUTO)  // default
    .completeAndWait();
```

#### The Four Modes

| Mode | Behavior | Best For |
|------|----------|----------|
| **AUTO** (default) | Smart fallback chain — tries the safest option first, then progressively relaxes | Most transactions |
| **CHANGE_OUTPUT** | Only deducts from `ChangeOutput` instances at the deposit payer address | When you want strict control |
| **ANY_OUTPUT** | Deducts from any output at the deposit payer address, including `payToAddress` outputs | When outputs are interchangeable |
| **NEW_UTXO_SELECTION** | Always selects fresh UTXOs; never touches existing outputs | Script inputs / complex txs |

#### AUTO Mode in Detail

AUTO adapts its behavior based on the `mergeOutputs` setting in `TxBuilderContext`:

**When `mergeOutputs = false`** (default):
```
1. Find a ChangeOutput at deposit payer address → deduct deposit
       ↓ (not found or insufficient)
2. Select new UTXOs from deposit payer address → add inputs + change
       ↓ (UTXO selection fails)
3. Find ANY output at deposit payer address → deduct deposit
       ↓ (nothing works)
   ERROR: "Cannot resolve deposit"
```

**When `mergeOutputs = true`**:
```
1. Find ANY output at deposit payer address → deduct deposit
   (no ChangeOutput distinction exists when outputs are merged)
       ↓ (not found or insufficient)
2. Select new UTXOs from deposit payer address → add inputs + change
       ↓ (nothing works)
   ERROR: "Cannot resolve deposit"
```

#### CHANGE_OUTPUT Mode

Strict mode — only uses change outputs (the excess from UTXO selection). Fails if no suitable change output exists.

```java
builder.compose(tx)
    .depositMode(DepositMode.CHANGE_OUTPUT)
    .completeAndWait();
// Throws TxBuildException if no change output covers the deposit
```

#### ANY_OUTPUT Mode

Relaxed mode — will deduct from any output at the deposit payer address, including explicit `payToAddress` outputs.

```java
builder.compose(tx)
    .depositMode(DepositMode.ANY_OUTPUT)
    .completeAndWait();
// May reduce a payToAddress output to cover the deposit
```

#### NEW_UTXO_SELECTION Mode

Isolated mode — selects additional UTXOs from the deposit payer address. Never modifies existing outputs. Creates a new change output for the remainder.

```java
builder.compose(tx)
    .depositMode(DepositMode.NEW_UTXO_SELECTION)
    .completeAndWait();
// Adds new inputs to the transaction; existing outputs untouched
```

### 3.5 ChangeOutput — How User Outputs Are Protected

`ChangeOutput` is a marker subclass of `TransactionOutput` in the `transaction-spec` module:

```java
public class ChangeOutput extends TransactionOutput {
    public ChangeOutput(String address, Value value) {
        super(address, value);
    }
}
```

During UTXO selection (Phase 2), the builder creates `ChangeOutput` instances for excess funds returned to the sender. These are distinguishable from user-declared outputs created by `payToAddress()`.

**Why this matters**: In AUTO and CHANGE_OUTPUT modes, deposit resolution prefers deducting from `ChangeOutput` instances. This protects user-declared outputs — if you call `payToAddress(addr, Amount.ada(100))`, that 100 ADA will not be silently reduced to pay for a deposit.

`ChangeOutput` serializes identically to `TransactionOutput` — the distinction only exists during transaction building.

### 3.6 Deposit Types Supported

Phase 4 handles deposits for four protocol operations:

| Operation | Intent Class | Deposit Source |
|-----------|-------------|----------------|
| Stake key registration | `StakeRegistrationIntent` | Protocol param: `keyDeposit` |
| Pool registration | `PoolRegistrationIntent` | Protocol param: `poolDeposit` (skipped for pool updates) |
| DRep registration | `DRepRegistrationIntent` | Custom amount or protocol param: `dRepDeposit` |
| Governance proposal | `GovernanceProposalIntent` | Custom amount or protocol param: `govActionDeposit` |

All four intent classes return `null` from `outputBuilder()`, ensuring no dummy outputs pollute the transaction during Phase 1.

---

## 4. YAML / TxPlan Support

The `TxPlan` serialization format supports deposit configuration:

```yaml
context:
  fee_payer: "addr_test1qz..."
  deposit_payer: "addr_test1qp..."    # optional — explicit deposit payer
  deposit_mode: "AUTO"                 # AUTO | CHANGE_OUTPUT | ANY_OUTPUT | NEW_UTXO_SELECTION

transactions:
  - type: tx
    intents:
      - type: pay_to_address
        address: "addr_test1qr..."
        amount: 50000000
      - type: register_stake_address
        address: "stake_test1uz..."
```

When `deposit_payer` is omitted, the normal fallback hierarchy applies. When `deposit_mode` is omitted, it defaults to `AUTO`.

---

## 5. Scenario Walkthroughs

### Case 1: Regular Tx + Deposit (Common Case)

A user registers a stake address while sending ADA to a friend:

```java
Tx tx = new Tx()
    .payToAddress(friendAddr, Amount.ada(50))
    .registerStakeAddress(stakeAddr)
    .from(myAddr);

Result<String> result = builder.compose(tx)
    .withSigner(signerFrom(myAccount))
    .completeAndWait();
```

**What happens**:
1. Phase 1: `payToAddress` creates a 50 ADA output. `registerStakeAddress` returns null.
2. Phase 2: UTXO selection picks inputs for 50 ADA + fees. Creates `ChangeOutput` for excess.
3. Phase 3: Stake registration certificate added to transaction body.
4. Phase 4 (AUTO): Finds `ChangeOutput` at `myAddr` → deducts 2 ADA deposit from it.

### Case 2: payToAddress to Self + Deposit (ChangeOutput Protection)

User sends ADA to their own address and registers a stake key:

```java
Tx tx = new Tx()
    .payToAddress(myAddr, Amount.ada(100))  // explicit output to self
    .registerStakeAddress(stakeAddr)
    .from(myAddr);

Result<String> result = builder.compose(tx)
    .withSigner(signerFrom(myAccount))
    .completeAndWait();
```

**What happens**:
- Phase 2 creates both a user output (100 ADA to `myAddr`) and a `ChangeOutput` (excess to `myAddr`).
- Phase 4 (AUTO, mergeOutputs=false): Finds the `ChangeOutput` and deducts deposit from it. The explicit 100 ADA output is **not** touched.
- If there is no change output with enough funds, it falls back to UTXO selection, then to any output.

### Case 3: collectFrom + Deposit (Script Transaction)

Collecting from a script and registering a DRep in one transaction:

```java
Tx tx = new Tx()
    .collectFrom(scriptUtxo, redeemer)
    .registerDRep(drepCredential, anchor)
    .attachSpendingValidator(script)
    .from(myAddr);

Result<String> result = builder.compose(tx)
    .feePayer(myAddr)
    .depositMode(DepositMode.NEW_UTXO_SELECTION)
    .withSigner(signerFrom(myAccount))
    .completeAndWait();
```

**What happens**:
- `hasScriptIntents()` returns true → feePayer used as change address.
- Phase 4 (NEW_UTXO_SELECTION): Selects fresh UTXOs from `myAddr` to cover the DRep deposit. Adds new inputs and a change output. Existing script outputs untouched.

### Case 4: Explicit Deposit Payer Different from Sender

A DAO treasury pays the deposit while a different account funds the transaction:

```java
Tx tx = new Tx()
    .registerStakeAddress(poolStakeAddr)
    .from(operatorAddr);

Result<String> result = builder.compose(tx)
    .feePayer(operatorAddr)
    .depositPayer(treasuryAddr)           // treasury pays the deposit
    .withSigner(signerFrom(operator))
    .withSigner(signerFrom(treasury))     // treasury must also sign
    .completeAndWait();
```

**What happens**:
- Phase 2: UTXO selection uses `operatorAddr` for payments.
- Phase 4: Deposit resolved from `treasuryAddr` (not `operatorAddr`). Selects UTXOs from the treasury address.

### Case 5: Composed Transactions

Two transactions composed together — the second uses the fee payer fallback:

```java
Tx tx1 = new Tx()
    .payToAddress(receiverAddr, Amount.ada(100))
    .from(aliceAddr);

Tx tx2 = new Tx()
    .registerStakeAddress(stakeAddr)
    .from(bobAddr);

Result<String> result = builder.compose(tx1, tx2)
    .feePayer(aliceAddr)
    .withSigner(signerFrom(alice))
    .withSigner(signerFrom(bob))
    .completeAndWait();
```

**What happens**:
- `tx1` creates a payment output and change. `tx2` adds a stake registration certificate.
- Phase 4: Deposit payer for `tx2` is `bobAddr` (from the `from()` call on `tx2`). UTXOs or change from `bobAddr` fund the deposit.

---

## 6. API Quick Reference

### TxContext Methods

```java
builder.compose(tx)
    .feePayer(address)              // Fee payer (also fallback deposit payer)
    .depositPayer(address)          // Explicit deposit payer (overrides from/feePayer)
    .depositMode(DepositMode.AUTO)  // How to fund the deposit
    .withSigner(signer)
    .completeAndWait();
```

### DepositMode Enum

| Value | Description |
|-------|-------------|
| `AUTO` | Smart fallback: ChangeOutput → UTXO selection → any output (varies with mergeOutputs) |
| `CHANGE_OUTPUT` | Only deduct from ChangeOutput instances; error if none found |
| `ANY_OUTPUT` | Deduct from any output at deposit payer address |
| `NEW_UTXO_SELECTION` | Always select new UTXOs; never touch existing outputs |

### Tx Script Methods (Summary)

```java
// Script inputs
tx.collectFrom(utxo, redeemer)
tx.collectFrom(utxos, redeemer, datum)
tx.collectFrom(scriptAddr, predicate, redeemer)
tx.collectFrom(scriptAddr, filterSpec, redeemer, datum)

// Reference inputs
tx.readFrom(utxo1, utxo2)
tx.readFrom(txHash, outputIndex)

// Minting with Plutus script
tx.mintAsset(plutusScript, asset, redeemer)
tx.mintAsset(plutusScript, assets, redeemer, receiver, outputDatum)
tx.mintAsset(policyId, asset, redeemer)

// Validator attachment
tx.attachSpendingValidator(plutusScript)
tx.attachMintValidator(plutusScript)
tx.attachCertificateValidator(plutusScript)
```
