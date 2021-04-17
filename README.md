# cardano-client-lib 

A client library for Cardano in Java. It currently uses [cardano-serialization-lib](https://github.com/Emurgo/cardano-serialization-lib) rust library though JNI.

This project can be used as a library in another Java project or as a standalone utility.

# Use as a library in a Java Project

## Add dependency

- For Maven, add following dependency to project's pom.xml
```
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>0.0.1</version>
        </dependency>
```

- For Gradle, add following dependency to build.gradle

```
compile 'com.bloxbean.cardano:cardano-client-lib:0.0.1'
```

## Account API Usage

- Create a New Account

```aidl
Account account = new Account();   //Create a Mainnet account

Account account = new Account(Networks.mainnet());   //Create a Mainnet account

Account account = new Account(Networks.testnet());  //Create a Testnet account
```
- Get base address, enterprise address, mnemonic
```aidl
String baseAddress = account.baseAddress(0);  //Base address at index=0

String enterpriseAddress = account.account.enterpriseAddress(2);  //Enterprise address at index = 2

String mnemonic = account.mnemonic();  //Get Mnemonic
```

- Get Account from Mnemonic

```aidl
String mnemonic = "...";
Account account = new Account(mnemonic);  //Create a Mainnet account from Mnemonic

Account account = new Account(Networks.testnet(), mnemonic); //Create a Testnet account from Mnemonic
```

# Use as a standalone application
The library also provides some CLI utilities. Download `cardano-client-lib-all-<version>.jar` from release section.

```aidl
$> java -jar cardano-client-lib-all-<version>.jar  account generate [-ea] [-n mainnet|testnet] [-t total_no_of_accounts]
$> java -jar cardano-client-lib-all-<version>.jar  account from-mnemonic [-mn mnemonic] [-ea] [-n <mainnet|testnet>] [-t total_no_of_accounts]
   
   -ea : Also generate Enterprise account
```

Examples:
```aidl
- java -jar cardano-client-lib-all-<version>.jar account generate  //Generate a new mainnet account
- java -jar cardano-client-lib-all-<version>.jar account generate -n testnet  //Generate a new testnet account
- java -jar cardano-client-lib-all-<version>.jar account generate -ea  //Generate a new account and both Base Address and Enterprise address
- java -jar cardano-client-lib-all-<version>.jar account generate -ea -t 5  //Generate a new account and show first 5 Base Addresses and Ent Addresses
- java -jar cardano-client-lib-all-<version>.jar  account from-mnemonic -mn "chimney proof dismiss ..." -t 5 //Generate first 5 mainnet addresses from the mnemonic
- java -jar cardano-client-lib-all-<version>.jar  account from-mnemonic -mn "chimney proof dismiss ..." -t 5 -n testnet //Testnet accounts
```
- Generate a new Mainnet account

```aidl
$> java -jar cardano-client-lib-all-0.0.1.jar account generate

Output: 
Mnemonic  : stable fade square ...
Base Address-0: addr1q9nj6uysd93x ...
```
- Generate a new Testnet account
```aidl
$> java -jar cardano-client-lib-all-0.0.1.jar account generate -n testnet

Output:
Mnemonic  : gauge side mandate sight evoke ...
Base Address-0: addr_test1qqyc4rcuz0wwy...

```





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
