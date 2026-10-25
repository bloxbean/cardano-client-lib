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
third-party transfer, token registration and registry update. Unfracking is temporarily unsupported
on every deployment and throws `UnsupportedOperationException`. Every operation the
adapter advertises is exercised end to end against a Yaci DevKit deployment.

Guarantees in this release:

- Building a plan never changes it: generated core intents are a build-local overlay, so the same
  `Tx`, `TxPlan` or `TxContext` can be built again sequentially. Concurrent use of one authoring
  model is not supported; services and extension descriptors are.
- Conflicting authorizations, multi-policy third-party actions, verification-key operational
  credentials, oversized inline datums and unfracking requests fail before any UTxO
  is selected, with a message naming the cause.
- Deployment resolution is atomic and never assumes a spent output live: a spent
  reference-script outref is skipped, one publishing another script fails resolution.
- Runtime TxPlan variables are resolved structurally, preserving native types; a value is never
  re-read as YAML or as a template.

Known limitations:

- Plain `TxPlan.toYaml()` / `TxPlan.from(yaml)` refuse programmable-token plans, including plans
  that only declare the extension, with an error that points to `TxPlanCodec`, the only supported
  YAML path for them.
- Unfracking Java calls and intent validation throw `UnsupportedOperationException`. The decoder
  remains registered so existing `pt:unfrack` documents report the explicit unsupported error.
  Portable TxFlow compilation rejects them with `TXFLOW_EXTENSION_INTENT_INVALID` before any step
  is submitted. Other programmable-token operations remain supported by registered TxFlow extensions.

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
- `Cip113Protocol.CONTRACT_VERSION` is `0.0.1` and enforced; `UNFRACK` is not advertised while
  unfracking is temporarily unsupported, even when `unfrackingEnabled` is true.

After an in-place upgrade the client follows the credentials in the live coordination datum. An
upgrade installing scripts that expect proofs this release does not build makes transactions fail
in script evaluation (funds are not at risk) until a newer release. The unfracking script is not
in the datum, so a custom deployment that can unfrack keeps its described one.
