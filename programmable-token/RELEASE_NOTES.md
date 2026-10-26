# Programmable Token experimental release notes

The initial `cardano-client-programmable-token` surface is experimental while CIP-113 and its
reference contracts remain early.

- TxPlan extension id: `programmable-token`
- TxPlan schema version: `1`
- Default document namespace: `pt`
- Initial protocol: `cip-113`
- Reference CIP-113 contract suite used for qualification: core `0.0.1` (upstream `6b75ba3`);
  the exact snapshot is pinned in `src/it/resources/blueprint/cip113/compatibility.yml`
- Supported deployment: the explicit `Cip113Deployment` supplied to the service
  (`Cip113Deployments.PREVIEW`, `PREPROD` and `MAINNET` are built in); persisted plans pin its
  network id, bootstrap transaction and params policy, and a plan naming another
  `contract_version` is refused, all before chain access.

The supported authoring operations are transfer (with an optional inline datum), mint, burn,
third-party transfer, third-party burn, token registration, registry update, and unfracking (only on a deployment
whose dispatcher was compiled against a real unfracking script — none of the public ones). Every operation the
adapter advertises is exercised end to end against a Yaci DevKit deployment.

Guarantees in this release:

- Building a plan never changes it: generated core intents are a build-local overlay, so the same
  `Tx`, `TxPlan` or `TxContext` can be built again sequentially. Concurrent use of one authoring
  model is not supported; services and extension descriptors are.
- Conflicting authorizations, multi-policy third-party actions, verification-key operational
  credentials, oversized inline datums and unfracking a token without a hook fail before any UTxO
  is selected, with a message naming the cause.
- Deployment resolution is atomic and never assumes a spent output live: a spent
  reference-script outref is skipped, one publishing another script fails resolution.
- Runtime TxPlan variables are resolved structurally, preserving native types; a value is never
  re-read as YAML or as a template.

Known limitations:

- Plain `TxPlan.toYaml()` / `TxPlan.from(yaml)` refuse programmable-token plans, including plans
  that only declare the extension, with an error that points to `TxPlanCodec`, the only supported
  YAML path for them.
- TxFlow and TxFlowStream `TxPlan` steps/items that use QuickTx extensions are not supported yet;
  flow validation refuses them (`TXFLOW_NON_PORTABLE_EXTENSION`) before any step is submitted.

Public types may still change before the module is promoted to beta.

## CIP-113 core v0.0.1

The adapter now builds transactions for CIP-113 core v0.0.1 only; the shapes of reference suite
`0.5.0-alpha.2` are gone. Base-script spends go through the `programmable_logic_global`
dispatcher, delegate redeemers carry no `params_idx`, mints and burns also withdraw from
`issuance_logic`, the registry is one script, and the coordination datum has six fields.

Breaking API changes:

- `Cip113Deployment`: added `programmableLogicGlobalHash`, `issuanceLogicHash`,
  `unfrackingEnabled`, `referenceScripts`, `issuanceTemplateRef` and their address helpers;
  removed `registrySpendScriptHash` (`registryNodeCs` is also the registry address now).
- `Cip113Deployments`: `PREVIEW` describes the v0.0.1 Preview deployment; `PREPROD` and `MAINNET`
  are new; `fromBootstrapTx` is removed, because a v0.0.1 deployment cannot be reconstructed from
  one transaction.
- `ProgrammableLogicGlobalParams` holds the six v0.0.1 fields.
- `Cip113Redeemers`: `spendVia*` replaced by `baseSpend` and `transferAct` / `thirdPartyAct` /
  `unfrackingAct`; `transfer`, `thirdParty` and `unfracking` lost their `paramsIdx` argument;
  added `issuanceMint`, `issuanceLogic` and `registrySpend`.
- `DeploymentScripts`: `registrySpend()` and `registryMint()` replaced by `registry()`; added
  `programmableLogicGlobal()`, `issuanceLogic()` and `replacePublished`.
- `Cip113Protocol.CONTRACT_VERSION` is `0.0.1` and enforced; `UNFRACK` is advertised only when the
  deployment enables it.

Added: `Cip113Deployment.smartWalletKey` (`SmartWalletKey.PAYMENT` by default, or `STAKE`) selects
which owner credential keys a smart wallet: the wallet a sender spends from, the wallet plain
receivers are mapped to, and the required signer. `SmartWalletAddress.ofOwner` and `ownerSigner`
apply it. The default keeps the previous behaviour. The service writes `smart_wallet_key` into a plan's deployment metadata, and a plan naming a key
other than the configured one is refused before chain access.

Changed: a token's global-state UTxO is taken from the caller's own reference inputs (`Tx.readFrom`)
when one of them carries the global-state token; only otherwise is it looked up through the
backend's policy → holders asset index, which Yaci Store, Koios and Ogmios do not offer.

Changed: the sender's key is declared as a required signer only when the sender's own smart wallet
is spent (owner transfer, burn, unfrack). Registrations and mints no longer require it, and
third-party actions no longer declare the acting party's key implicitly. A third-party logic that
checks the actor's signature needs that signer declared by the caller, as with any other
substandard requirement.

Fixed: a registry-node update now funds its fee up front, as every other verb does. A node holding
exactly its continuation's min-ADA left the change at 0 before balancing, so the update was
evaluated without a fee input and the registry's spend handler failed on the empty change output.

Fixed: a mint whose transaction spends the token's global-state UTxO (a substandard enforcing a
supply cap on mint) no longer also adds that UTxO as a reference input, which the ledger rejects.
A global-state UTxO the caller collects is taken as the token's global state.

Fixed: a deployment script read from its published reference input is no longer also carried in
the witness set when the caller declares reference scripts of its own (`withReferenceScripts`),
which the ledger rejected as `ExtraneousScriptWitnessesUTXOW`. The extension now hands the scripts
it references to QuickTx through the new `TxBuildExtension.referenceScripts()` hook, instead of
relying on QuickTx's reference-script resolver, which a caller's own reference scripts switch off.

After an in-place upgrade the client follows the credentials in the live coordination datum. An
upgrade installing scripts that expect proofs this release does not build makes transactions fail
in script evaluation (funds are not at risk) until a newer release. The unfracking script is not
in the datum, so a custom deployment that can unfrack keeps its described one.

## Third-party burn

Added: `ProgrammableTokenTx.thirdPartyBurn(holderUtxoRef, policyId, assets, authorization)` and
`ThirdPartyBurnAuthorization.of(thirdPartyRedeemer, issuanceRedeemer)`, YAML type
`pt:third_party_burn` (`holder_utxo_ref`, `policy_id`, `assets`, `third_party_redeemer`,
`issuance_redeemer`). It burns tokens out of one named holder UTxO under the token's third-party
logic and minting logic: the seizure shape (`ThirdPartyAct`) plus a negative issuance mint whose
`issuance_logic` proof names the same registry node as the `third_party` redeemer, so `third_party`
covers the burn's custody. It requires the `THIRD_PARTY_TRANSFER` and `BURN` capabilities; no new
capability constant was added.

- The verb declares no signer: add the key the token's logic checks with `withRequiredSigners(...)`.
- A token whose third-party logic always succeeds lets anyone burn anyone's tokens of it.
- The named UTxO must be unspent, at a smart wallet of the configured deployment, hold the policy,
  cover the assets, and carry neither a datum hash nor a reference script; each is refused by name.
  A holder UTxO carrying a reference script therefore cannot be third-party burned through CCL.
- One policy per transaction (the `third_party` redeemer names one registry node).

Changed: third-party actions compose as far as the chain allows. Seizures and third-party burns of
one policy, from any number of holders, share a transaction, and `mint` and `register` may join
them. Previously a third-party transaction acted for exactly one holder and admitted nothing else.
Owner transfers, burns and unfracking are still refused next to them, as are two policies, a UTxO
named twice, different redeemers for one logic script, a registration whose covering node is the
acted node, and a mint and burn of one asset name netting to zero.

Changed: a third-party action's continuing outputs are located as the last base-script outputs of
the transaction, matched one for one against the expected continuation of each acted-on input in
ledger order, instead of as the run of outputs at the one holder's address. A seizure into a wallet
that the same transaction also acts on (seize A to B while burning from B) is therefore allowed; only
a seizure back into the very wallet it seizes from is still refused, as a no-op. A base-script
output from a later `compose(...)` fragment, which would land after the continuations, is refused.

Changed: a seized or burnt UTxO that also holds another registered policy is no longer refused; the
`third_party` validator passes that policy through unchanged and reads no other registry node, so
no registry proof is prepared for it.

Fixed: a third-party action now counts its continuing outputs' lovelace when funding the fee up
front. It previously subtracted the acted-on inputs' lovelace from the funding target although the
continuations return it, under-counting the target by that amount for every seized UTxO.
