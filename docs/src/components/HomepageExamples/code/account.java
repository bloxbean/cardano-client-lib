// Create mainnet account from mnemonic
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
Account mainnetAccount = new Account(Networks.mainnet(), mnemonic);

// Create testnet account from mnemonic
Account testnetAccount = new Account(Networks.testnet(), mnemonic);

// Generate new mainnet account with random mnemonic
Account newMainnetAccount = new Account(Networks.mainnet());

// Generate new testnet account with random mnemonic
Account newTestnetAccount = new Account(Networks.testnet());

// Get account addresses
String baseAddress = mainnetAccount.baseAddress();
String stakeAddress = mainnetAccount.stakeAddress();
String enterpriseAddress = mainnetAccount.enterpriseAddress();

// Access account keys
String mnemonic = mainnetAccount.mnemonic();
String privateKey = mainnetAccount.privateKeyHex();
String publicKey = mainnetAccount.publicKeyHex();
