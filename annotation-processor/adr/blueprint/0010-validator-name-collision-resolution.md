# ADR 0010: Validator Name Collision Resolution

- Status: Accepted
- Date: 2026-02-25
- Owners: Cardano Client Lib maintainers
- Related: CIP-113 (Programmable Token Standard), ADR-0004

## Context

CIP-57 blueprint validators have hierarchical dot-separated titles that represent their module path within the contract:

```
config.config_mint_validator.mint
config.config_spend_validator.spend
power_users.mint.mint
power_users.manage.else
```

When generating Java classes, the processor must derive a unique class name from each validator title. The naive approach of using only the **last token** (e.g., `mint`, `spend`, `else`) causes name collisions in blueprints with many validators.

### Real-World Problem

CIP-113 token contracts have 20+ validators, many sharing the same final name:

| Validator Title | Last Token | Collision? |
|----------------|------------|------------|
| `config.config_mint_validator.mint` | `mint` | Yes |
| `power_users.mint.mint` | `mint` | Yes |
| `config.config_spend_validator.spend` | `spend` | Yes |
| `nft.spend.spend` | `spend` | Yes |
| `power_users.manage.else` | `else` | Yes |
| `config.config_mint_validator.else` | `else` | Yes |

Using only the last token would produce multiple classes named `Mint`, `Spend`, and `Else` in the same package.

## Decision

Use a hierarchical naming strategy that incorporates enough of the validator title to ensure uniqueness:

### Algorithm

```
calculateValidatorName(validatorTitle):
  1. Split title by "." into tokens
  2. If tokens.length <= 2: use last token
  3. If tokens.length > 2: skip first token, join remaining with "_"
  4. Apply NamingStrategy.toClassName() for PascalCase conversion
```

### Key Design Choices

**First token = package namespace**: The first token represents the top-level module/package namespace (e.g., `config`, `power_users`, `nft`). It is already captured in the package path by `PackageResolver.getValidatorNamespace()`, so including it in the class name would be redundant.

**Join with underscores**: Remaining tokens are joined with `_` before PascalCase conversion. This preserves the hierarchical structure in the class name.

**Threshold at >2**: With 2 or fewer tokens (e.g., `cardano_aftermarket.beacon_script`), the last token is already unique because there is no deep nesting. The collision problem only arises with 3+ level hierarchies.

### Examples

| Validator Title | Tokens | Strategy | Raw Name | PascalCase Class |
|----------------|--------|----------|----------|-----------------|
| `cardano_aftermarket.beacon_script` | 2 | Last token | `beacon_script` | `BeaconScript` |
| `config.config_mint_validator.mint` | 3 | Skip first, join rest | `config_mint_validator_mint` | `ConfigMintValidatorMint` |
| `config.config_spend_validator.spend` | 3 | Skip first, join rest | `config_spend_validator_spend` | `ConfigSpendValidatorSpend` |
| `power_users.mint.mint` | 3 | Skip first, join rest | `mint_mint` | `MintMint` |
| `power_users.manage.else` | 3 | Skip first, join rest | `manage_else` | `ManageElse` |
| `nft.spend.spend` | 3 | Skip first, join rest | `spend_spend` | `SpendSpend` |

All CIP-113 validators now produce unique class names.

## Implementation

### ValidatorProcessor.calculateValidatorName()

```java
String calculateValidatorName(String validatorTitle) {
    String[] titleTokens = validatorTitle.split("\\.");

    if (titleTokens.length > 2) {
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 1; i < titleTokens.length; i++) {
            if (i > 1) nameBuilder.append("_");
            nameBuilder.append(titleTokens[i]);
        }
        return nameBuilder.toString();
    }

    return titleTokens[titleTokens.length - 1];
}
```

### Full Pipeline

```
Validator Title: "config.config_mint_validator.mint"
  │
  ├─ PackageResolver.getValidatorNamespace() → "config" (package path)
  │
  ├─ calculateValidatorName() → "config_mint_validator_mint" (raw name)
  │
  └─ NamingStrategy.toClassName() → "ConfigMintValidatorMint" (Java class name)
```

The final generated class is placed in the validator's namespace package:
```
<basePackage>.config.ConfigMintValidatorMint
```

## Rationale

1. **Uniqueness**: Including the middle tokens ensures different module paths produce different class names, even when the final action name is shared.
2. **No redundancy**: Skipping the first token avoids duplicating the package namespace in the class name.
3. **Backward compatibility**: Validators with simple 1-2 part titles (the common case before CIP-113) continue to use just the last token, producing the same class names as before.
4. **Readability**: The PascalCase conversion of joined tokens produces descriptive class names that reflect the validator's location in the contract hierarchy.

## Alternatives Considered

### 1. Use Full Title (All Tokens)
**Example**: `config.config_mint_validator.mint` → `ConfigConfigMintValidatorMint`
**Rejected**: Redundant — the first token is already in the package path. Produces unnecessarily long names.

### 2. Use Only Last Two Tokens
**Example**: `config.config_mint_validator.mint` → `ConfigMintValidatorMint`
**Rejected**: Works for 3-token titles but could still collide with deeper hierarchies. The current approach generalizes better.

### 3. Append Numeric Suffix on Collision
**Example**: `Mint`, `Mint2`, `Mint3`
**Rejected**: Loses semantic meaning. Developers cannot tell which `Mint` corresponds to which validator without consulting the blueprint.

## Consequences

### Positive
- All CIP-113 validators produce unique class names.
- Class names are descriptive and reflect the validator's module hierarchy.
- Simple validators (1-2 tokens) are unaffected — backward compatible.

### Negative
- Longer class names for deeply nested validators (e.g., `ConfigMintValidatorMint` vs `Mint`).
  - **Mitigation**: This is a feature, not a bug — the longer names disambiguate validators that would otherwise collide.

## References

- `ValidatorProcessor.calculateValidatorName()` — name resolution logic
- `PackageResolver.getValidatorNamespace()` — package/namespace resolution
- `NamingStrategy.toClassName()` — PascalCase conversion
- Commit `5dd5d2d5`: feat: Support opaque PlutusData types and fix validator name collisions
- CIP-113: Programmable Token Standard (source of complex validator hierarchies)
