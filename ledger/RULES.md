# Ledger Rule Coverage Tracker

> **Living document**: Update this file in the same commit as any rule implementation change.
>
> **Reference**: [cardano-ledger Conway-era rules](https://github.com/IntersectMBO/cardano-ledger/tree/master/eras/conway/impl/src/Cardano/Ledger/Conway/Rules)
>
> **Analysis**: See [ADR-017](../adr/017-ledger-rule-coverage-analysis.md) for the initial gap analysis.

---

## Status Legend

| Status | Meaning |
|---|---|
| Implemented | Rule is implemented with unit tests |
| Partial | Placeholder or incomplete implementation |
| Missing | Not yet implemented — see Blocker column |
| N/A | Not applicable to CCL (Phase-2, block-level, or wrapper) |

---

## UTXO Rule (23 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `InputSetEmptyUTxO` | `InputValidationRule` | Implemented | — |
| 2 | `BadInputsUTxO` | `InputValidationRule` | Implemented | — |
| 3 | `BabbageNonDisjointRefInputs` | `InputValidationRule` | Implemented | — |
| 4 | `MaxTxSizeUTxO` | `TxSizeValidationRule` | Implemented | — |
| 5 | `OutsideValidityIntervalUTxO` | `ValidityIntervalRule` | Implemented | — |
| 6 | `WrongNetworkInTxBody` | `NetworkIdValidationRule` | Implemented | — |
| 7 | `WrongNetwork` | `NetworkIdValidationRule` | Implemented | — |
| 8 | `WrongNetworkWithdrawal` | `NetworkIdValidationRule` | Implemented | — |
| 9 | `FeeTooSmallUTxO` | `FeeAndCollateralRule` | Implemented | — |
| 10 | `ValueNotConservedUTxO` | `ValueConservationRule` | Implemented | — |
| 11 | `OutputTooSmallUTxO` | `OutputValidationRule` | Implemented | — |
| 12 | `BabbageOutputTooSmallUTxO` | `OutputValidationRule` | Implemented | — |
| 13 | `OutputTooBigUTxO` | `OutputValidationRule` | Implemented | — |
| 14 | `OutputBootAddrAttrsTooBig` | `FeeAndCollateralRule` | Partial | Byron address decoding needed |
| 15 | `NoCollateralInputs` | `FeeAndCollateralRule` | Implemented | — |
| 16 | `TooManyCollateralInputs` | `FeeAndCollateralRule` | Implemented | — |
| 17 | `ScriptsNotPaidUTxO` | `FeeAndCollateralRule` | Implemented | — |
| 18 | `InsufficientCollateral` | `FeeAndCollateralRule` | Implemented | — |
| 19 | `CollateralContainsNonADA` | `FeeAndCollateralRule` | Implemented | — |
| 20 | `IncorrectTotalCollateralField` | `FeeAndCollateralRule` | Implemented | — |
| 21 | `ExUnitsTooBigUTxO` | `FeeAndCollateralRule` | Implemented | — |
| 22 | `OutsideForecast` | — | Missing | Needs consensus stability window — node concern |
| 23 | `UtxosFailure` (wrapper) | — | N/A | Phase-2 wrapper |

**Coverage: 21/22 applicable (95%)**

---

## UTXOW Rule (19 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `MissingVKeyWitnessesUTXOW` | `WitnessValidationRule` | Implemented | — |
| 2 | `InvalidWitnessesUTXOW` | `WitnessValidationRule` | Implemented | — |
| 3 | `MissingScriptWitnessesUTXOW` | `WitnessValidationRule` | Implemented | — |
| 4 | `ExtraneousScriptWitnessesUTXOW` | `WitnessValidationRule` | Implemented | — |
| 5 | `ScriptWitnessNotValidatingUTXOW` | `WitnessValidationRule` | Implemented | — |
| 6 | `MissingTxBodyMetadataHash` | `WitnessValidationRule` | Implemented | — |
| 7 | `MissingTxMetadata` | `WitnessValidationRule` | Implemented | — |
| 8 | `ConflictingMetadataHash` | `WitnessValidationRule` | Implemented | — |
| 9 | `PPViewHashesDontMatch` | `WitnessValidationRule` | Implemented | — |
| 10 | `MissingRequiredDatums` | `WitnessValidationRule` | Implemented | — |
| 11 | `NotAllowedSupplementalDatums` | `WitnessValidationRule` | Implemented | — |
| 12 | `MissingRedeemers` | `WitnessValidationRule` | Implemented | — |
| 13 | `ExtraRedeemers` | `WitnessValidationRule` | Implemented | — |
| 14 | `UnspendableUTxONoDatumHash` | `WitnessValidationRule` | Implemented | — |
| 15 | `InvalidMetadata` | — | Missing | Integer range check likely handled by CBOR parser |
| 16 | `MalformedScriptWitnesses` | — | Missing | Already handled by CBOR deserialization |
| 17 | `MalformedReferenceScripts` | — | Missing | Already handled by CBOR deserialization |
| 18 | `ScriptIntegrityHashMismatch` | — | N/A | Duplicate of `PPViewHashesDontMatch` |
| 19 | `UtxoFailure` (wrapper) | — | N/A | Wrapper for UTXO sub-rule |

**Coverage: 14/17 applicable (82%)**

---

## DELEG Rule (8 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `IncorrectDepositDELEG` | `CertificateValidationRule` | Implemented | — |
| 2 | `StakeKeyRegisteredDELEG` | `CertificateValidationRule` | Implemented | — |
| 3 | `StakeKeyNotRegisteredDELEG` | `CertificateValidationRule` | Implemented | — |
| 4 | `StakeKeyHasNonZeroAccountBalanceDELEG` | `CertificateValidationRule` | Implemented | — |
| 5 | `DelegateeDRepNotRegisteredDELEG` | `CertificateValidationRule` | Implemented | — |
| 6 | `DelegateeStakePoolNotRegisteredDELEG` | `CertificateValidationRule` | Implemented | — |
| 7 | `DepositIncorrectDELEG` | `CertificateValidationRule` | Implemented | — |
| 8 | `RefundIncorrectDELEG` | `CertificateValidationRule` | Implemented | — |

**Coverage: 8/8 (100%)**

---

## POOL Rule (6 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `StakePoolNotRegisteredOnKeyPOOL` | `CertificateValidationRule` | Implemented | — |
| 2 | `StakePoolRetirementWrongEpochPOOL` | `CertificateValidationRule` | Implemented | — |
| 3 | `StakePoolCostTooLowPOOL` | `CertificateValidationRule` | Implemented | — |
| 4 | `WrongNetworkPOOL` | `CertificateValidationRule` | Implemented | — |
| 5 | `PoolMedataHashTooBig` | `CertificateValidationRule` | Implemented | — |
| 6 | `VRFKeyHashAlreadyRegistered` | — | Missing | Needs `PoolsSlice.isVrfKeyRegistered()` |

**Coverage: 5/6 (83%)**

---

## GOVCERT Rule (6 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `ConwayDRepAlreadyRegistered` | `CertificateValidationRule` | Implemented | — |
| 2 | `ConwayDRepNotRegistered` | `CertificateValidationRule` | Implemented | — |
| 3 | `ConwayDRepIncorrectDeposit` | `CertificateValidationRule` | Implemented | — |
| 4 | `ConwayCommitteeHasPreviouslyResigned` | `CertificateValidationRule` | Implemented | — |
| 5 | `ConwayDRepIncorrectRefund` | `CertificateValidationRule` | Implemented | — |
| 6 | `ConwayCommitteeIsUnknown` | `CertificateValidationRule` | Implemented | — |

**Coverage: 6/6 (100%)**

---

## GOV Rule (21 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `ProposalDepositIncorrect` | `GovernanceValidationRule` | Implemented | — |
| 2 | `ProposalProcedureNetworkIdMismatch` | `GovernanceValidationRule` | Implemented | — |
| 3 | `TreasuryWithdrawalsNetworkIdMismatch` | `GovernanceValidationRule` | Implemented | — |
| 4 | `ZeroTreasuryWithdrawals` | `GovernanceValidationRule` | Implemented | — |
| 5 | `ExpirationEpochTooSmall` | `GovernanceValidationRule` | Implemented | — |
| 6 | `InvalidPrevGovActionId` | `GovernanceValidationRule` | Implemented | — |
| 7 | `GovActionsDoNotExist` | `GovernanceValidationRule` | Implemented | — |
| 8 | `VotersDoNotExist` (DRep) | `GovernanceValidationRule` | Implemented | — |
| 9 | `VotersDoNotExist` (Pool) | `GovernanceValidationRule` | Implemented | — |
| 10 | `VotersDoNotExist` (CC) | `GovernanceValidationRule` | Partial | Needs `CommitteeSlice.getColdCredentialByHot()` reverse lookup |
| 11 | `MalformedProposal` | — | Missing | Needs detailed PParams range spec |
| 12 | `ProposalCantFollow` | — | Missing | Needs previous enacted HardFork version from state |
| 13 | `InvalidGuardrailsScriptHash` | — | Missing | Needs current constitution hash from state |
| 14 | `ConflictingCommitteeUpdate` | `GovernanceValidationRule` | Implemented | — |
| 15 | `DisallowedVoters` | `GovernanceValidationRule` | Implemented | — |
| 16 | `DisallowedProposalDuringBootstrap` | — | Missing | Needs `isBootstrapPhase` flag in `LedgerContext` |
| 17 | `DisallowedVotesDuringBootstrap` | — | Missing | Needs `isBootstrapPhase` flag in `LedgerContext` |
| 18 | `ProposalReturnAccountDoesNotExist` | `GovernanceValidationRule` | Implemented | — |
| 19 | `TreasuryWithdrawalReturnAccountsDoNotExist` | `GovernanceValidationRule` | Implemented | — |
| 20 | `VotingOnExpiredGovAction` | — | Missing | Needs proposal submission epoch from `ProposalsSlice` |
| 21 | `UnelectedCommitteeVoters` | — | Missing | Needs `CommitteeSlice.getColdCredentialByHot()` |

**Coverage: 14/21 (67%)**

---

## LEDGER Rule (6 Haskell failures)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `ConwayTxRefScriptsSizeTooBig` | `FeeAndCollateralRule` | Implemented | — |
| 2 | `ConwayTreasuryValueMismatch` | — | Missing | Needs actual treasury value in `LedgerContext` |
| 3 | `ConwayWdrlNotDelegatedToDRep` | — | Missing | Needs `AccountsSlice.getDRepDelegation()` |
| 4 | `ConwayWithdrawalsMissingAccounts` | `CertificateValidationRule` | Implemented | — |
| 5 | `ConwayIncompleteWithdrawals` | `CertificateValidationRule` | Implemented | — |
| 6 | Wrapper constructors | — | N/A | — |

**Coverage: 3/5 applicable (60%)**

---

## CERTS Rule (1 Haskell failure)

| # | Haskell Constructor | CCL Rule Class | Status | Blocker |
|---|---|---|---|---|
| 1 | `WithdrawalsNotInRewardsCERTS` | `CertificateValidationRule` | Implemented | — |

**Coverage: 1/1 (100%)**

---

## Out-of-Scope

| Category | Owner | Reason |
|---|---|---|
| **UTXOS** (Phase-2 script execution) | `scalus` module | UPLC evaluation delegated to Scalus |
| **BBODY** (block-level validation) | Yaci | Block body size, hash, aggregate ExUnits/ref scripts |
| **Epoch boundary** (RATIFY, ENACT, NEWEPOCH) | Yaci | Infallible transitions, state-heavy |
| **Governance ratification** | Yaci | Proposal voting tallies, enactment |

---

## Summary

| Rule Group | Implemented | Applicable | Coverage |
|---|---|---|---|
| UTXO | 21 | 22 | 95% |
| UTXOW | 14 | 17 | 82% |
| DELEG | 8 | 8 | 100% |
| POOL | 5 | 6 | 83% |
| GOVCERT | 6 | 6 | 100% |
| GOV | 14 | 21 | 67% |
| LEDGER | 3 | 5 | 60% |
| CERTS | 1 | 1 | 100% |
| **Total Phase-1** | **72** | **86** | **84%** |
