# Shared Blueprint Type Registry

This module allows annotation-processor users to reuse prebuilt Java types for
schemas that appear repeatedly in CIP-57 blueprints. The registry is *opt-in* and
disabled by default so existing projects keep their current generated sources.

## Enabling the registry

The registry is enabled by default. Add the processor flag
`-Acardano.registry.disable=true` if you need to revert to the
"generate-everything" behaviour. To force-enable or disable explicitly, use the
boolean `cardano.registry.enable` property. Example for Gradle:

```
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ["-Acardano.registry.disable=true"]
}
```

When enabled, the processor loads every implementation of
`com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry`
found on the annotation processor classpath and consults them before generating
new datum classes.

## Built-in mappings

The `plutus-aiken` module ships a default registry (loaded via Java's service loader)
that recognises a growing set of CIP-57 schemas and reuses

- `com.bloxbean.cardano.client.plutus.blueprint.type.Pair` for tuple pairs of
  byte arrays.
- `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential` (with nested
  verification-key/script variants).
- `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential`
  (inline/pointer alternatives).
- `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address` for ledger
  addresses composed of the credential types above.
- Hash wrappers such as `com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKey`,
  `VerificationKeyHash`, `Script`, `ScriptHash`, `Signature`, `DataHash`, and the generic `Hash`.

Additional shared schemas will be added incrementally.

## Providing custom mappings

1. Register your mappings (optionally in a static initializer). The helper lets
   you bind by title without having to construct full schemas:

   ```java
   package com.example.blueprint;

   import com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistryExtensions;

   public final class MyBlueprintTypes implements com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry {
       static {
           BlueprintTypeRegistryExtensions.registerByTitle("MyFoo", "com.example.shared", "MyFoo");
       }

       @Override
        public java.util.Optional<com.bloxbean.cardano.client.plutus.blueprint.registry.RegisteredType> lookup(
                com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignature signature,
                com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema schema,
                com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext context) {
           // We only use the static registration above.
           return java.util.Optional.empty();
       }
   }
   ```

2. Register the implementation by adding the fully qualified class name to
   `META-INF/services/com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry`
   in the jar placed on the annotation processor classpath.

Registries are queried in discovery order; the first registry returning a match
wins. If no registry recognises a schema the processor falls back to generating
classes as before.

## Troubleshooting

- Ensure the registry flag is set on the processor invocation; JVM compiler
  flags on application modules are not sufficient.
- Diagnostics can be added by wrapping the service loader implementation to log
  which signatures were matched while you develop custom mappings.
- If you need the behaviour disabled again, remove the compiler flag and the
  processor reverts to generating all model classes.
