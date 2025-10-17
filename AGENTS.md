# Repository Guidelines

## Project Structure & Modules
- Root is a Gradle multi-module Java 11 project. Key modules include `core`, `core-api`, `function`, `quicktx`, `cip/*`, `backend` + `backend-modules/*`, `watcher`, `tx-dsl`, `groovy-dsl`, and `integration-test`.
- Source layout: `src/main/java`, unit tests in `src/test/java`, integration tests in `src/it/java` (and the `integration-test` module).
- Artifacts publish as `cardano-client-<module>`.

## Build, Test, and Dev Commands
- Build all: `./gradlew clean build`
- Build one module: `./gradlew :core:build`
- Unit tests: `./gradlew test`
- Integration tests: `./gradlew integrationTest` or `./gradlew :watcher:integrationTest` (uses `src/it/java`).
- Publish to local Maven: `./gradlew publishToMavenLocal`
- Useful properties: `-PskipSigning`, `-Psigning.password=...`; env for CI/publish: `MAVEN_USERNAME`, `MAVEN_PASSWORD`.
- JDK: Use Java 11 (`./gradlew -v` to verify).

## Coding Style & Naming
- Language: Java. Follow standard Java conventions.
- Classes: PascalCase; methods/fields: lowerCamelCase; constants: UPPER_SNAKE_CASE; packages: lowercase.
- Indentation: 4 spaces; UTF-8 source; avoid wildcard imports.
- Logging: use SLF4J; do not use `System.out.println`.
- Nullability: prefer Optional/clear preconditions; validate public inputs.
- Use lombok annotations for reducing boilerplate code.
- Prefer package imports instead of fully qualified class names

## Testing Guidelines
- Frameworks: JUnit 5, Mockito, AssertJ/Hamcrest.
- File/Type names: `<ClassName>Test` in `src/test/java`; integration tests live under `src/it/java`.
- Run: `./gradlew test`, `./gradlew integrationTest`.
- Config: some ITs read `BF_PROJECT_ID` (set via `-DBF_PROJECT_ID=...` or Gradle property). Keep tests deterministic and independent.

## Commit & PR Guidelines
- Style: Prefer Conventional Commits (e.g., `feat:`, `fix:`, `refactor:`, `docs:`). Use present tense, imperative mood.
- Branches: `feature/<short-topic>` or `fix/<issue-id>`.
- PRs: include a clear summary, rationale, linked issues (`Closes #123`), tests for changes, and docs updates (README/ADR) when behavior/API changes. Add screenshots for diagrams/visual outputs when relevant.
- CI: Jenkins uses Java 11 and Gradle (`clean build`, then `publish`). Keep main green; do not break `./gradlew build`.

## Security & Configuration
- Never commit secrets. Use env vars for credentials (`MAVEN_USERNAME`, `MAVEN_PASSWORD`, `SIGNING_PASSWORD`).
- Review external I/O paths; validate network calls in `backend` modules. Prefer immutable models and defensive copies in public APIs.
