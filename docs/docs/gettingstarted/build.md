---
description: How to build from source ?
sidebar_position: 9
---

# Build

```
git clone https://github.com/bloxbean/cardano-client-lib.git

export JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
./gradlew clean build
```

# Run Integration Tests
```
export BF_PROJECT_ID=<Blockfrost Project Id>
./gradlew :integration-test:integrationTest -PBF_PROJECT_ID=${BF_PROJECT_ID}
```
