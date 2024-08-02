# HD Wallet integration specification

## Motivation 

Hierarchical deterministic wallets are a common practice in blockchains like Bitcoin and Cardano.
The idea behind that is to derive multiple keys (private and public) and addresses from a master key.
The advantage in contrast to individual addresses is that these keys/addresses are linked together through the master key.
Thus it is possible to maintain privacy due to changing public addresses frequently.
Otherwise one could track users through various transactions. 

Therefore it must be possible for users to use this concept via an easy-to-use API within this library.

## Implementation

- HDWallet Class
  - Wrapper for Account - Deriving new Accounts with one Mnemonic
  - Scanning strategy -> 20 consecutive empty addresses
  - First Interface Approach [HDWalletInterface.java](src/main/java/com/bloxbean/cardano/hdwallet/HDWalletInterface.java)
- Transaction Building - extend QuickTxBuilder to use HDWallet
  - Getting UTXOs to pay certain amount
  - Getting Signers for the respective UTXOs spend in the transaction
  - Build and Sign Transactions from HDWallet
- Minting Assets to a specific address 
- Coin Selection Strategies 
  - Support common UTXO SelectionStrategys (Biggest, sequential, ...)