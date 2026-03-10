# Blueprint JSON Format (CIP-57)

The annotation processor reads [CIP-57](https://cips.cardano.org/cip/CIP-0057/) Plutus blueprint JSON files as input. This document explains the format and how it maps to generated code.

## Overview

CIP-57 defines a standard JSON schema for describing Plutus smart contracts. The Aiken compiler produces this file automatically when you build your project. Other Plutus compilers (Plutarch, PlutusTx) can also produce CIP-57 blueprints.

## Blueprint Structure

A blueprint JSON has three top-level sections:

```json
{
  "preamble": { ... },
  "validators": [ ... ],
  "definitions": { ... }
}
```

### Preamble

Metadata about the project:

```json
{
  "preamble": {
    "title": "satran004/param-contract",
    "description": "Aiken contracts for project 'satran004/param-contract'",
    "version": "0.0.0",
    "plutusVersion": "v2",
    "compiler": {
      "name": "Aiken",
      "version": "v1.0.29-alpha+16fb02e"
    },
    "license": "Apache-2.0"
  }
}
```

The `plutusVersion` field (`"v1"`, `"v2"`, or `"v3"`) determines which Plutus language version the compiled script uses.

### Validators

Each validator entry describes a single on-chain validator script:

```json
{
  "title": "helloworld.hello_world",
  "datum": {
    "title": "datum",
    "schema": {
      "$ref": "#/definitions/helloworld~1Owner"
    }
  },
  "redeemer": {
    "title": "redeemer",
    "schema": {
      "$ref": "#/definitions/helloworld~1Redeemer"
    }
  },
  "compiledCode": "58e90100003232...",
  "hash": "c1fe430f19ac248a8a7ea47db106002c4327e542c3fdc60ad6481103"
}
```

Key fields:
- **`title`** — the validator name in `module.function_name` format. This becomes the generated Java class name (e.g., `HelloWorldValidator`).
- **`datum`** — the datum schema (spending validators only). References a definition.
- **`redeemer`** — the redeemer schema. References a definition.
- **`compiledCode`** — the compiled Plutus script as a hex-encoded CBOR string.
- **`hash`** — the script hash, used to derive the script address.

Minting validators have only a `redeemer` and no `datum`:

```json
{
  "title": "mint_validator.mint",
  "redeemer": {
    "title": "rdmr",
    "schema": {
      "$ref": "#/definitions/mint_validator~1Action"
    }
  },
  "compiledCode": "585f010000...",
  "hash": "a06b4b8191ce3ac2fb1744d8a67e2c572f899eaa004a0bf476bd9095"
}
```

### Definitions

The `definitions` section contains reusable type schemas referenced by validators via JSON Pointer (`$ref`):

```json
{
  "definitions": {
    "ByteArray": {
      "dataType": "bytes"
    },
    "helloworld/Owner": {
      "title": "Owner",
      "anyOf": [
        {
          "title": "Owner",
          "dataType": "constructor",
          "index": 0,
          "fields": [
            {
              "title": "owner",
              "$ref": "#/definitions/ByteArray"
            }
          ]
        }
      ]
    },
    "mint_validator/Action": {
      "title": "Action",
      "anyOf": [
        {
          "title": "Mint",
          "dataType": "constructor",
          "index": 0,
          "fields": []
        },
        {
          "title": "Burn",
          "dataType": "constructor",
          "index": 1,
          "fields": []
        }
      ]
    }
  }
}
```

## How Definitions Map to Java Classes

| Definition Pattern | Java Output |
|---|---|
| `"dataType": "bytes"` | `byte[]` field |
| `"dataType": "integer"` | `BigInteger` field |
| `"dataType": "#string"` | `String` field |
| `"dataType": "constructor"` with fields | `@Data` class (product type) |
| `"anyOf"` with one constructor | Single class |
| `"anyOf"` with multiple constructors, all empty fields | Java `enum` |
| `"anyOf"` with multiple constructors, some with fields | Interface + variant classes in sub-package |

### Definition Keys as Class Names

The definition key (e.g., `helloworld/Owner`) determines the generated class name. The processor:

1. Takes the last segment after `/` → `Owner`
2. Applies PascalCase naming
3. Escapes Java keywords if needed

For namespaced keys like `aiken/transaction/credential/Credential`, only the final segment `Credential` becomes the class name.

## JSON Pointer References

Schema references use [RFC 6901 JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901) syntax. The key escaping rules are:

- `/` in definition keys is escaped as `~1`
- `~` in definition keys is escaped as `~0`

So a reference to the definition key `helloworld/Owner` becomes:
```json
{ "$ref": "#/definitions/helloworld~1Owner" }
```

## Aiken Compiler Output

When building an Aiken project, the blueprint is generated at:

```
your-aiken-project/plutus.json
```

Copy this file to your Java project's resources directory.

### Aiken Version Differences

The Aiken compiler has evolved its schema output across versions:

- **Aiken v1.0.x** — uses `$` prefix for generic type parameters (e.g., `Option$Int`)
- **Aiken v1.1.x** — uses angle brackets (e.g., `Option<Int>`)

The annotation processor handles both formats automatically.

### Aiken Stdlib Versions

Different Aiken stdlib versions produce different schemas for standard types like `Credential` and `Address`. See [Shared Types and plutus-aiken](04-shared-types-and-plutus-aiken.md) for details on how this is handled.

## Next Steps

- [Understanding Generated Code](02-generated-code.md) — how these schemas become Java classes
- [Getting Started](01-getting-started.md) — set up code generation in your project
