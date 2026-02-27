//Mainnet account from mnemonic
Account mainnetAccount = new Account(mnemonic);

//Testnet account from mnemonic
Account testnetAccount = new Account(Networks.testnet(), mnemonic)

//New mainnet account
Account mainnetAccount = new Account()

//New testnet account
Account testnetAccount = new Account(Networks.testnet())
