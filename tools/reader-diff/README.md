# reader-diff

A manual check for moving CCL's and Yaci's decoders onto one reader (ADR 0003, invariants I5, I6 and I10). It compares
two builds on the same inputs and reports every input where they differ in accepting or rejecting, or in the model they
return. It also records the parsing speed and allocation of a build. It is run by hand, not in CI.

It is a **standalone Gradle build**, not part of the root build, as it needs Yaci, which depends on CCL. The root
`settings.gradle` does not include it.

## How it works

A *side* is a directory of jars: one Yaci build and one CCL build, with everything they need. Two sides run in one JVM,
each in its own class loader, whose parent is the platform loader, so they share only JDK classes. No old source is
copied or relocated.

`Probes`, compiled with this tool, is loaded inside each side's loader, against that side's classes. Only Java types
cross to the tool: a probe is a `Function<byte[], String>` from input bytes to an outcome:

| Outcome | Meaning |
|---|---|
| `A:<json>` | Accepted, with the model as canonical JSON |
| `R:<exception class>` | Rejected |
| `C:<error class>` | Stack overflow |
| `U:<message>` | Accepted, but the model could not be written. Always reported as a difference |

A class that cannot link, or running out of memory, fails the run: it says nothing about the input.

The canonical JSON (`CanonicalJson`) has to miss nothing, or the check is worthless:

- Jackson from the side's own class path, reading **fields**, with the models' Jackson annotations off: `@JsonIgnore`
  and custom serializers describe an API, not the model.
- Each object carries its runtime class in `@class`, as `StakeRegistration` and `StakeDeregistration` have the same
  fields.
- Properties are sorted and byte arrays are hex. A `HashMap`, `HashSet` or `Set.of` is sorted, as its order means
  nothing. A `LinkedHashMap`, `LinkedHashSet` or sorted collection keeps its order, as callers see it.
- A cbor-java `DataItem` kept in a model is the hex of its encoding, in its own order. Its fields hold hashes that start
  from an enum's identity hash, so they differ between class loaders.

`FieldCoverage` pins this by walking a model by reflection next to its JSON: every field of every object reached must
appear with its value. `CanonicalJsonTest` runs it on a real block; `-Pcorpus=<file>` and `-Pdataset=<dir>/conway` run
it over a whole corpus and the dataset, which reach many more classes. It can only reach classes the inputs produce.

## Build

Java 25 is required, and the build uses its own Gradle wrapper. The tool's own classes (streaming, splitting blocks, the
expected values) compile against one CCL and Yaci, which never mix with the sides. The CCL version must be resolvable:

```bash
cd tools/reader-diff
./gradlew --no-watch-fs --console=plain installDist test \
    -PcclVersion=0.8.0-lc-base-SNAPSHOT -Dmaven.repo.local=/Volumes/data2/yano-try/cbor-adr/lc/m2
```

## Sides

```bash
./side.sh base 0.8.0-lc-base-SNAPSHOT 0.5.0-pre15   # <label> <cclVersion> <yaciVersion>
```

This resolves the jars from `/Volumes/data2/yano-try/cbor-adr/lc/m2` and Central into
`/Volumes/data2/yano-try/cbor-adr/lc/sides/<label>/`. Every `cardano-client-*` module is forced to the CCL version,
except artifacts with `jellyfish` or `merkle` in their name.

## Commands

Set `R=build/install/reader-diff/bin/reader-diff`.

### dump

```bash
$R dump --host=localhost --port=3002 --network=mainnet --stop-slot=200800000 --every=430 --out=blocks.bin
```

Streams Shelley-to-Conway blocks over node-to-node block-fetch and writes a sample: a 4-byte big-endian length and the
raw bytes, per block. `--every=N` keeps one block in N per era; `--limit=N` keeps at most N blocks per era. It reads the
whole range, as a node cannot serve a block without its point, and parses blocks no further than the era and slot.

### diff

```bash
$R diff --old=<sideDir> --new=<sideDir> --blocks=blocks.bin --out=<dir> [--allow=<file>] [--workers=N]
$R diff --old=<sideDir> --new=<sideDir> --stream --host=localhost --port=3002 --network=mainnet \
    --stop-slot=200800000 --out=<dir> [--allow=<file>] [--workers=N]
```

Per block, two kinds of probe run on both sides:

- `yaci-block`: `BlockSerializer.INSTANCE.deserialize(bytes)`, with Yaci's `returnBlockCbor`, `returnTxBodyCbor` and
  `returnFullTxCbor` on, so those fields are compared as well.
- `ccl-tx`: for each transaction, `Transaction.deserialize(txBytes)`, with the JSON and the hex of `serialize()`. The
  tool splits the block with its own `RawBlock`, so both sides get the same bytes.

The exit code is 1 when there are differences. In `<dir>`:

- `summary.txt`: counts per probe, and per probe and era, of each outcome pair; the allowed fields; the rejection
  classes.
- `differences.jsonl`: the first 200 of each kind, per probe, with the input's hex and the JSON path of the first
  differing field.

| Pair | Meaning | A difference? |
|---|---|---|
| `A/A equal` | Same model | No |
| `A/A allowed` | Models differ only in fields on the approved list | No, and counted per field |
| `A/A different` | Models differ in a field not on the list | Yes |
| `A/A expected-mismatch` | A listed field's new value is not the expected one | Yes |
| `R/R`, `C/C` | Both reject. The classes may differ, and are listed | No |
| `A/R`, `R/A`, `A/C`, `C/A`, `R/C`, `C/R` | Old/new outcomes differ | Yes |
| `split-failed` | The tool's `RawBlock` could not split the block | Yes |

### Approved differences

`--allow=<file>` lists JSON paths on ADR 0003 D8's approved list. `d8-corrections.allow` holds the current ones:

```
header.headerBody.blockHash expected=block-header-hash
```

`[*]` in a path stands for any array index. A listed field is never dropped. Its differences are counted per path, and
where a line has `expected=`, the new value is checked **on every input of the probe, whether or not the sides differ**,
against a value the tool computes from the input's original bytes, using its own `CborSpan` and Blake2b, independently
of both sides:

| Name | Applies to | Value |
|---|---|---|
| `block-header-hash` | `yaci-block`, `yaci:block` | Blake2b-256 of the header's bytes in the block |
| `header-hash` | `yaci:header` | Blake2b-256 of the input |

The summary shows how many inputs match on each side. So a self-diff with the file also measures how many real headers
Yaci's hash gets wrong today, which ADR 0003 D8 leaves open.

### diff-dataset

```bash
$R diff-dataset --old=<sideDir> --new=<sideDir> --out=<dir> [--allow=<file>] [--workers=N] \
    --dataset=/Volumes/data2/yano-try/cbor-adr/corpora-pinned/cardano-cbor-dataset/dataset/conway
```

Runs `ccl:<rule>` and `yaci:<rule>` on each sample of the rules that have a decoder (`DatasetDecoders`, which mirrors
Yano's `CborRule` and `CborPlacedRules` without depending on Yano) and reports as `diff` does, with `valid` and `invalid`
as the groups. Yano's gates on a sample's major type are left out, so more input reaches the decoders.

### bench

```bash
$R bench --side=<sideDir> --blocks=blocks.bin --out=base.json [--rounds=5]
```

The baseline for I10. It runs the bare parsers of `yaci-block` and `ccl-tx` on one thread, with no JSON and Yaci's
configuration at its defaults. Transactions are split beforehand. After two warm-up passes, each round is one pass, and
the JSON holds the wall time and allocated bytes per block of every round, with the mean, minimum, maximum, standard
deviation and spread. To compare two sides, compare their spreads as well as their means, on an otherwise idle machine.
The load average at the start is recorded.

## Limits

- `yaci:<part of a header>` rules, such as `yaci:kes_period`, have no expected hash: their input is not the header. Their
  `headerBody.blockHash` differences are counted through the list.
- A stack overflow's depth depends on the thread and the JIT, so a `C:` outcome near the limit can vary between runs.
- Equal `R/R` classes are not required: a mapper may reasonably fail with another exception. The classes are listed so a
  change can be seen.
- `yaci-block` runs with Yaci's CBOR flags on, and a class loader has one `YaciConfig`, so the flags-off path is not
  compared. The two can differ in accepting: the dataset's `block/manual-invalid/auxiliary-data-key-not-uint` is accepted
  with the flags off and rejected with `IndexOutOfBoundsException` with them on.
- `.gitignore` covers build output only. Keep run output outside this directory.
