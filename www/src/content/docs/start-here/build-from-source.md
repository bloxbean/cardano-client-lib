---
title: "Build from source"
description: How to build from source ?
---

You need Java 17.

```
git clone https://github.com/bloxbean/cardano-client-lib.git
cd cardano-client-lib

export JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
./gradlew clean build
```

# Run Integration Tests
```
export BF_PROJECT_ID=<Blockfrost Project Id>
./gradlew :integration-test:integrationTest -PBF_PROJECT_ID=${BF_PROJECT_ID}
```
