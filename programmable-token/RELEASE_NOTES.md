# Programmable Token experimental release notes

The initial `cardano-client-programmable-token` surface is experimental while CIP-113 and its
reference contracts remain in alpha.

- TxPlan extension id: `programmable-token`
- TxPlan schema version: `1`
- Default document namespace: `pt`
- Initial protocol: `cip-113`
- Supported CIP-113 contract surface: `0.5.0-alpha.2`
- Supported deployment: the explicit `Cip113Deployment` supplied to the service; persisted plans
  pin its bootstrap transaction when available.

The supported authoring operations are transfer, mint, burn, third-party transfer, token
registration, and registry update. Unfracking is represented in the domain vocabulary for future
protocols but is not advertised as a CIP-113 adapter capability and fails before chain access.

Compatibility guarantees are limited to the extension schema and protocol metadata checks in this
experimental release. Public types may still change before the module is promoted to beta.
