# ADR metadata/0001: Annotation Processor Core Design

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

Cardano transaction metadata requires serializing Java POJOs into `MetadataMap` (Cardano's
key-value CBOR structure) and deserializing them back. This is a common pattern in any
application that attaches structured data to Cardano transactions.

Two broad implementation strategies exist:

1. **Runtime reflection** — Inspect class fields at runtime and serialize dynamically.
2. **Compile-time code generation** — Generate a typed converter class at build time.

The project already contains an annotation processor for Plutus blueprint types
(`@Constr`, processed by `ConstrAnnotationProcessor`). The metadata processor follows
the same architectural pattern to keep the two features consistent.

## Decision

Implement metadata serialization as a **Java annotation processor** that generates a
`{ClassName}MetadataConverter` class at compile time, using JavaPoet for code generation.

### Key design choices within this decision

#### 1. Compile-time generation over runtime reflection

Generated converters are plain Java classes with no reflection at all. The trade-offs:

| Concern | Compile-time generation | Runtime reflection |
|---|---|---|
| IDE support | Full — typed method calls | None |
| Error detection | At build time | At runtime |
| Startup overhead | None | Reflection cost |
| Debuggability | Read the generated source | Stack traces through reflection |
| Tooling complexity | Requires annotation processor | No extra tooling |

Compile-time generation wins for a library targeting production blockchain use.

#### 2. JavaPoet for code generation

JavaPoet is already on the classpath (used by the blueprint processor). Reusing it
avoids a new dependency and keeps the code generation style consistent across both
processors.

#### 3. Generated converter in the same package as the annotated class

`SampleOrder` → `SampleOrderMetadataConverter` in the same package.

Alternatives considered:
- **`.converter` subpackage** — adds a package import for no practical benefit; rejected.
- **Root package** — breaks encapsulation; rejected.

#### 4. Opt-out field inclusion (all fields by default)

All non-static, non-`@MetadataIgnore` fields are included automatically. `@MetadataField`
is only needed to rename the metadata key or override the output type.

Alternative: **opt-in** (require `@MetadataField` on every field). Rejected because it
is verbose and error-prone — forgetting an annotation silently omits a field.

#### 5. Missing accessor handling — WARNING, not ERROR

If a field has no getter/setter and is not `public`, the processor emits a compile-time
WARNING and skips the field. A compile ERROR would break builds for classes with
intentionally un-serialized private fields. A warning surfaces the issue without blocking.

#### 6. Lombok detection via raw Class.forName reflection

The processor checks for `@lombok.Data`, `@lombok.Getter`, `@lombok.Setter` at
annotation-processing time using `Class.forName`. This is the same pattern used in the
blueprint processor and avoids a hard Lombok compile dependency.

#### 7. @MetadataIgnore for explicit exclusion

A dedicated `@MetadataIgnore` annotation (rather than overloading `@MetadataField`) keeps
intent clear: `@MetadataField` is for configuration, `@MetadataIgnore` is for exclusion.

## Consequences

### Positive
- Generated sources are readable, debuggable, and version-controllable.
- No runtime overhead; no reflection.
- Compile-time errors for unsupported field types.
- Consistent architecture with the blueprint annotation processor.

### Neutral
- Consumers must have the annotation processor on the compile classpath (standard
  for annotation-processor-based libraries).
- Generated files appear in the build output directory, not in `src/`.

### Negative
- Requires a build step; classes are not available without compilation.
- Adding new supported types requires updating the processor (cannot be done at runtime).

## Related

- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0003: 64-Byte String Chunking Ownership
- ADR metadata/0004: @MetadataField(enc=…) Encoding Override Mechanism
- Existing `ConstrAnnotationProcessor` (blueprint processor, same module)
