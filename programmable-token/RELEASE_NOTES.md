# Programmable Token experimental release notes

The initial `cardano-client-programmable-token` surface is experimental while CIP-113 and its
reference contracts remain in alpha.

- TxPlan extension id: `programmable-token`
- TxPlan schema version: `1`
- Default document namespace: `pt`
- Initial protocol: `cip-113`
- Reference CIP-113 contract suite used for qualification: `0.5.0-alpha.2` (informational); the
  exact snapshot is pinned in `src/it/resources/blueprint/cip113/compatibility.yml`
- Supported deployment: the explicit `Cip113Deployment` supplied to the service; persisted plans
  pin its network id and bootstrap transaction and are validated against it before chain access.

The supported authoring operations are transfer (with an optional inline datum), mint, burn,
third-party transfer, token registration, registry update, and unfracking. Every operation the
adapter advertises is exercised end to end against a Yaci DevKit deployment.

Guarantees in this release:

- Building a plan never changes it: generated core intents are a build-local overlay, so the same
  `Tx`, `TxPlan` or `TxContext` can be built again sequentially. Concurrent use of one authoring
  model is not supported; services and extension descriptors are.
- Conflicting authorizations, multi-policy third-party actions, verification-key operational
  credentials, oversized inline datums and unfracking a token without a hook fail before any UTxO
  is selected, with a message naming the cause.
- Deployment resolution is atomic and never falls back to an unverified bootstrap output.
- Runtime TxPlan variables are resolved structurally, preserving native types; a value is never
  re-read as YAML or as a template.

Public types may still change before the module is promoted to beta.
