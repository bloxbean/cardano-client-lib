# cardano-client-lib (Under development)

A client library for Cardano in Java. It currently uses [cardano-serialization-lib](https://github.com/Emurgo/cardano-serialization-lib) rust library though JNI.

# Build

```
git clone https://github.com/bloxbean/cardano-client-lib.git
git submodule update --init --recursive

. script/build-<os>-<arch>.sh

./gradlew build farJar
```

- Get base address from mnemonic :
```
java -jar build/libs/cardano-client-lib-all-<version>.jar from-mnemonic <24w mnemonic>  <no_of_addresses> [mainnet|testnet]

java -jar build/libs/cardano-client-lib-all-1.0-SNAPSHOT.jar from-mnemonic "explain fuel jar lawn action transfer pottery best measure tortoise buyer off buffalo drift pupil enjoy armor bean replace utility when unknown scissors slush" 30 mainnet
```

- Generate new address
```
java -jar build/libs/cardano-client-lib-all-<version>.jar generate  <no_of_addresses> [mainnet|testnet]

java -jar build/libs/cardano-client-lib-all-1.0-SNAPSHOT.jar generate 5 mainnet
```
