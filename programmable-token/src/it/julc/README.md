# TxPlan test substandard

This isolated JuLC project compiles the integration-test substandard to Plutus V3. It is not part
of the CCL Gradle build: CCL keeps its Java 17 baseline, while the JuLC compiler currently requires
Java 25.

To regenerate the checked-in fixture:

1. Activate JDK 25 and verify it with `java -version`.
2. From this directory, run `julc build -v .`.
3. Run `../../../../gradlew -p . syncBlueprint` to update the checked-in blueprint.
4. Update `EXPECTED_HASH` in `TxPlanSubstandardScripts` to the generated validator hash.
5. Run `./gradlew :programmable-token:integrationTest` from the repository root with the normal
   CCL Java 17 toolchain and Yaci DevKit running.

Only the source project and generated `plutus.json` are versioned. JuLC build output and Java 25
classes are ignored. Every JuLC dependency excludes its transitive released CCL artifact so the
fixture build cannot mix it with repository modules.
