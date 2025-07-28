---
description: Comprehensive guide to common utility classes including HexUtil, JsonUtil, StringUtils, CardanoConstants, ADAConversionUtil, Tuple, Triple, Try utilities, and exception handling
sidebar_label: Common Utilities
sidebar_position: 1
---

# Common Utilities

This guide covers the essential utility classes that form the foundation of the Cardano Client Library. These utilities provide consistent, reliable operations for data conversion, error handling, JSON processing, and functional programming patterns.

:::tip Prerequisites
Basic understanding of Java programming and the Cardano blockchain concepts is recommended.
:::

## HexUtil - Hexadecimal Operations

The `HexUtil` class provides bidirectional conversion between byte arrays and hexadecimal strings, essential for blockchain data handling.

### Core Methods

```java
import com.bloxbean.cardano.client.util.HexUtil;

public class HexUtilExample {
    
    public void demonstrateHexOperations() {
        // Convert byte array to hex string
        byte[] data = {0x48, 0x65, 0x6c, 0x6c, 0x6f}; // "Hello" in bytes
        String hex = HexUtil.encodeHexString(data);
        System.out.println("Hex: " + hex); // Output: 48656c6c6f
        
        // Convert with 0x prefix
        String hexWithPrefix = HexUtil.encodeHexString(data, true);
        System.out.println("With prefix: " + hexWithPrefix); // Output: 0x48656c6c6f
        
        // Convert hex string back to bytes
        byte[] decoded = HexUtil.decodeHexString(hex);
        System.out.println("Decoded: " + new String(decoded)); // Output: Hello
        
        // Handle individual bytes
        byte singleByte = 0x41; // 'A'
        String hexByte = HexUtil.byteToHex(singleByte);
        System.out.println("Byte to hex: " + hexByte); // Output: 41
        
        byte backToByte = HexUtil.hexToByte("41");
        System.out.println("Hex to byte: " + (char)backToByte); // Output: A
    }
}
```

### Practical Usage Examples

```java
// Working with transaction hashes
String txHash = "a1b2c3d4e5f6..."; // Transaction hash from blockchain
byte[] hashBytes = HexUtil.decodeHexString(txHash);

// Encoding script hashes
byte[] scriptHash = calculateScriptHash(script);
String scriptHashHex = HexUtil.encodeHexString(scriptHash);

// Working with public keys
byte[] publicKeyBytes = account.getPublicKey().getKeyData();
String publicKeyHex = HexUtil.encodeHexString(publicKeyBytes);

// Null safety - returns null for null input
byte[] nullBytes = null;
String result = HexUtil.encodeHexString(nullBytes); // Returns null
```

### Error Handling

```java
public class SafeHexOperations {
    
    public static byte[] safeDecodeHex(String hexString) {
        try {
            if (hexString == null || hexString.isEmpty()) {
                return new byte[0];
            }
            
            // Remove 0x prefix if present
            if (hexString.startsWith("0x")) {
                hexString = hexString.substring(2);
            }
            
            return HexUtil.decodeHexString(hexString);
            
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid hex string: " + hexString);
            return new byte[0];
        }
    }
    
    public static String safeEncodeHex(byte[] data) {
        if (data == null) {
            return "";
        }
        return HexUtil.encodeHexString(data);
    }
}
```

## JsonUtil - JSON Processing

The `JsonUtil` class provides JSON formatting and parsing capabilities, primarily used for debugging and data visualization.

### Core Methods

```java
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtilExample {
    
    public void demonstrateJsonOperations() {
        // Convert object to pretty JSON
        Transaction tx = createSampleTransaction();
        String prettyJson = JsonUtil.getPrettyJson(tx);
        System.out.println("Transaction JSON:\n" + prettyJson);
        
        // Format existing JSON string
        String compactJson = "{\"name\":\"Alice\",\"age\":30,\"city\":\"New York\"}";
        String formatted = JsonUtil.getPrettyJson(compactJson);
        System.out.println("Formatted JSON:\n" + formatted);
        
        // Parse JSON string to JsonNode
        JsonNode node = JsonUtil.parseJson(compactJson);
        if (node != null) {
            String name = node.get("name").asText();
            int age = node.get("age").asInt();
            System.out.println("Parsed: " + name + ", age " + age);
        }
    }
}
```

### Debugging and Logging Usage

```java
public class TransactionBuilder {
    private static final Logger logger = LoggerFactory.getLogger(TransactionBuilder.class);
    
    public Transaction buildTransaction(TransactionBody body) {
        try {
            Transaction tx = Transaction.builder()
                .body(body)
                .build();
            
            // Log transaction details for debugging
            logger.debug("Built transaction: {}", JsonUtil.getPrettyJson(tx));
            
            return tx;
            
        } catch (Exception e) {
            logger.error("Failed to build transaction from body: {}", 
                        JsonUtil.getPrettyJson(body), e);
            throw e;
        }
    }
    
    public void logTransactionInput(TransactionInput input) {
        logger.info("Processing input: {}", JsonUtil.getPrettyJson(input));
    }
}
```

### Error Handling

```java
public class SafeJsonOperations {
    
    public static String toJsonSafely(Object obj) {
        try {
            return JsonUtil.getPrettyJson(obj);
        } catch (Exception e) {
            // Falls back to toString() automatically in JsonUtil
            return "Failed to serialize to JSON: " + obj.toString();
        }
    }
    
    public static JsonNode parseJsonSafely(String jsonString) {
        try {
            JsonNode node = JsonUtil.parseJson(jsonString);
            return node != null ? node : createEmptyObjectNode();
        } catch (Exception e) {
            System.err.println("JSON parsing failed: " + e.getMessage());
            return createEmptyObjectNode();
        }
    }
    
    private static JsonNode createEmptyObjectNode() {
        return new ObjectMapper().createObjectNode();
    }
}
```

## StringUtils - String Manipulation

The `StringUtils` class provides essential string processing utilities for blockchain data handling.

### Core Methods

```java
import com.bloxbean.cardano.client.util.StringUtils;
import java.util.List;

public class StringUtilsExample {
    
    public void demonstrateStringOperations() {
        // Split string into fixed-length chunks
        String longHash = "a1b2c3d4e5f6789012345678901234567890abcdef";
        List<String> chunks = StringUtils.splitStringEveryNCharacters(longHash, 8);
        System.out.println("Chunks: " + chunks);
        // Output: [a1b2c3d4, e5f67890, 12345678, 90123456, 7890abcd, ef]
        
        // Check if string is empty
        String testString = "";
        boolean isEmpty = StringUtils.isEmpty(testString);
        System.out.println("Is empty: " + isEmpty); // Output: true
        
        String nullString = null;
        boolean isNullEmpty = StringUtils.isEmpty(nullString);
        System.out.println("Is null empty: " + isNullEmpty); // Output: true
        
        // Validate UTF-8 encoding
        byte[] validUtf8 = "Hello, 世界".getBytes(StandardCharsets.UTF_8);
        boolean isValidUtf8 = StringUtils.isUtf8String(validUtf8);
        System.out.println("Is valid UTF-8: " + isValidUtf8); // Output: true
        
        // Invalid UTF-8 sequence
        byte[] invalidUtf8 = {(byte)0xFF, (byte)0xFE, (byte)0xFD};
        boolean isInvalidUtf8 = StringUtils.isUtf8String(invalidUtf8);
        System.out.println("Is invalid UTF-8: " + isInvalidUtf8); // Output: false
    }
}
```

### Practical Applications

```java
public class DataProcessingExample {
    
    // Format long transaction hashes for display
    public List<String> formatTransactionHash(String txHash) {
        if (StringUtils.isEmpty(txHash)) {
            return Collections.emptyList();
        }
        
        // Split into readable chunks
        return StringUtils.splitStringEveryNCharacters(txHash, 16);
    }
    
    // Validate metadata text encoding
    public boolean validateMetadataText(byte[] metadataBytes) {
        return StringUtils.isUtf8String(metadataBytes);
    }
    
    // Safe string validation
    public boolean isValidInput(String input) {
        return !StringUtils.isEmpty(input) && input.trim().length() > 0;
    }
    
    // Process address chunks for QR codes
    public List<String> createQRCodeSegments(String address) {
        // Split address into QR-friendly segments
        return StringUtils.splitStringEveryNCharacters(address, 25);
    }
}
```

## CardanoConstants - Blockchain Constants

The `CardanoConstants` class provides essential Cardano blockchain constants.

### Available Constants

```java
import com.bloxbean.cardano.client.common.CardanoConstants;
import java.math.BigInteger;

public class ConstantsExample {
    
    public void demonstrateConstants() {
        // Lovelace currency identifier
        String currency = CardanoConstants.LOVELACE;
        System.out.println("Currency: " + currency); // Output: lovelace
        
        // One ADA in lovelace
        BigInteger oneAda = CardanoConstants.ONE_ADA;
        System.out.println("One ADA: " + oneAda + " lovelace"); // Output: 1000000 lovelace
        
        // Calculate multiple ADA amounts
        BigInteger fiveAda = CardanoConstants.ONE_ADA.multiply(BigInteger.valueOf(5));
        System.out.println("Five ADA: " + fiveAda + " lovelace");
    }
}
```

### Usage in Calculations

```java
public class FeeCalculator {
    
    public BigInteger calculateMinFee(int txSize) {
        // Base fee calculation (simplified)
        BigInteger baseFee = BigInteger.valueOf(155381); // Base fee in lovelace
        BigInteger sizeComponent = BigInteger.valueOf(txSize).multiply(BigInteger.valueOf(44));
        
        return baseFee.add(sizeComponent);
    }
    
    public boolean hasEnoughAda(BigInteger balance, int requiredAda) {
        BigInteger required = CardanoConstants.ONE_ADA.multiply(BigInteger.valueOf(requiredAda));
        return balance.compareTo(required) >= 0;
    }
    
    public String formatLovelaceAsAda(BigInteger lovelace) {
        if (lovelace.equals(BigInteger.ZERO)) {
            return "0 ADA";
        }
        
        BigDecimal ada = new BigDecimal(lovelace).divide(new BigDecimal(CardanoConstants.ONE_ADA), 6, RoundingMode.HALF_UP);
        return ada.toPlainString() + " ADA";
    }
}
```

## ADAConversionUtil - Currency Conversion

The `ADAConversionUtil` class provides precise currency conversion between ADA, lovelace, and generic assets.

### Core Conversion Methods

```java
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import java.math.BigDecimal;
import java.math.BigInteger;

public class ConversionExample {
    
    public void demonstrateConversions() {
        // ADA to Lovelace conversions
        BigDecimal adaAmount = new BigDecimal("1.5");
        BigInteger lovelace = ADAConversionUtil.adaToLovelace(adaAmount);
        System.out.println(adaAmount + " ADA = " + lovelace + " lovelace");
        // Output: 1.5 ADA = 1500000 lovelace
        
        // Double ADA to Lovelace
        double adaDouble = 2.75;
        BigInteger lovelaceFromDouble = ADAConversionUtil.adaToLovelace(adaDouble);
        System.out.println(adaDouble + " ADA = " + lovelaceFromDouble + " lovelace");
        // Output: 2.75 ADA = 2750000 lovelace
        
        // Lovelace to ADA conversion
        BigInteger lovelaceAmount = BigInteger.valueOf(3250000);
        BigDecimal ada = ADAConversionUtil.lovelaceToAda(lovelaceAmount);
        System.out.println(lovelaceAmount + " lovelace = " + ada + " ADA");
        // Output: 3250000 lovelace = 3.250000 ADA
    }
}
```

### Generic Asset Conversions

```java
public class AssetConversionExample {
    
    public void demonstrateAssetConversions() {
        // Convert asset with 8 decimal places (like many tokens)
        BigInteger assetAmount = BigInteger.valueOf(123456789L); // Raw amount
        long decimals = 8;
        
        BigDecimal decimalAmount = ADAConversionUtil.assetToDecimal(assetAmount, decimals);
        System.out.println("Asset amount: " + decimalAmount);
        // Output: Asset amount: 1.23456789
        
        // Convert back to raw amount
        BigDecimal inputAmount = new BigDecimal("5.25");
        BigInteger rawAmount = ADAConversionUtil.assetFromDecimal(inputAmount, decimals);
        System.out.println("Raw amount: " + rawAmount);
        // Output: Raw amount: 525000000
    }
    
    // Practical token handling
    public void handleTokenTransfer(String tokenAmount, long tokenDecimals) {
        try {
            BigDecimal amount = new BigDecimal(tokenAmount);
            BigInteger rawAmount = ADAConversionUtil.assetFromDecimal(amount, tokenDecimals);
            
            // Use rawAmount in transaction
            System.out.println("Converting " + amount + " tokens to " + rawAmount + " raw units");
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid token amount: " + tokenAmount);
        }
    }
}
```

### Precision and Validation

```java
public class PrecisionHandler {
    
    public static class ConversionResult {
        private final BigInteger amount;
        private final boolean isValid;
        private final String error;
        
        public ConversionResult(BigInteger amount, boolean isValid, String error) {
            this.amount = amount;
            this.isValid = isValid;
            this.error = error;
        }
        
        // Getters
        public BigInteger getAmount() { return amount; }
        public boolean isValid() { return isValid; }
        public String getError() { return error; }
    }
    
    public static ConversionResult safeAdaToLovelace(String adaAmount) {
        try {
            BigDecimal ada = new BigDecimal(adaAmount);
            
            // Validate positive amount
            if (ada.compareTo(BigDecimal.ZERO) < 0) {
                return new ConversionResult(BigInteger.ZERO, false, "Amount cannot be negative");
            }
            
            // Check precision (max 6 decimal places for ADA)
            if (ada.scale() > 6) {
                return new ConversionResult(BigInteger.ZERO, false, "ADA precision cannot exceed 6 decimal places");
            }
            
            BigInteger lovelace = ADAConversionUtil.adaToLovelace(ada);
            return new ConversionResult(lovelace, true, null);
            
        } catch (NumberFormatException e) {
            return new ConversionResult(BigInteger.ZERO, false, "Invalid number format: " + adaAmount);
        }
    }
    
    public static String formatAdaAmount(BigInteger lovelace) {
        if (lovelace == null || lovelace.compareTo(BigInteger.ZERO) < 0) {
            return "0.000000 ADA";
        }
        
        BigDecimal ada = ADAConversionUtil.lovelaceToAda(lovelace);
        return String.format("%.6f ADA", ada);
    }
}
```

## Tuple and Triple - Data Containers

Tuple and Triple classes provide type-safe containers for multiple related values.

### Tuple Usage

```java
import com.bloxbean.cardano.client.util.Tuple;

public class TupleExample {
    
    // Method returning multiple values
    public Tuple<String, BigInteger> getAccountInfo(Account account) {
        String address = account.baseAddress().toBech32();
        BigInteger balance = getBalance(account);
        
        return new Tuple<>(address, balance);
    }
    
    // Processing tuple results
    public void processAccountData() {
        Account account = Account.createFromMnemonic(mnemonic);
        Tuple<String, BigInteger> accountInfo = getAccountInfo(account);
        
        String address = accountInfo._1;
        BigInteger balance = accountInfo._2;
        
        System.out.println("Address: " + address);
        System.out.println("Balance: " + balance + " lovelace");
    }
    
    // Tuple in collections
    public List<Tuple<String, String>> getAssetPairs() {
        return Arrays.asList(
            new Tuple<>("ADA", "lovelace"),
            new Tuple<>("USDC", "USD Coin"),
            new Tuple<>("AGIX", "SingularityNET Token")
        );
    }
}
```

### Triple Usage

```java
import com.bloxbean.cardano.client.util.Triple;

public class TripleExample {
    
    // Method returning three related values
    public Triple<String, BigInteger, Integer> getTransactionInfo(String txHash) {
        TransactionDetails tx = getTransactionDetails(txHash);
        
        String status = tx.getStatus();
        BigInteger fee = tx.getFee();
        Integer confirmations = tx.getConfirmations();
        
        return new Triple<>(status, fee, confirmations);
    }
    
    // Processing triple results
    public void processTransactionData(String txHash) {
        Triple<String, BigInteger, Integer> txInfo = getTransactionInfo(txHash);
        
        String status = txInfo._1;
        BigInteger fee = txInfo._2;
        Integer confirmations = txInfo._3;
        
        System.out.println("Transaction Status: " + status);
        System.out.println("Fee: " + fee + " lovelace");
        System.out.println("Confirmations: " + confirmations);
    }
    
    // Triple for coordinate or RGB data
    public Triple<Integer, Integer, Integer> parseRGBColor(String colorHex) {
        if (colorHex.length() != 6) {
            return new Triple<>(0, 0, 0); // Default to black
        }
        
        int r = Integer.parseInt(colorHex.substring(0, 2), 16);
        int g = Integer.parseInt(colorHex.substring(2, 4), 16);
        int b = Integer.parseInt(colorHex.substring(4, 6), 16);
        
        return new Triple<>(r, g, b);
    }
}
```

### Functional Programming with Tuples

```java
public class FunctionalTupleExample {
    
    public Stream<Tuple<String, BigInteger>> processAccounts(List<Account> accounts) {
        return accounts.stream()
            .map(account -> new Tuple<>(
                account.baseAddress().toBech32(),
                getBalance(account)
            ))
            .filter(tuple -> tuple._2.compareTo(BigInteger.ZERO) > 0) // Filter non-zero balances
            .sorted((t1, t2) -> t2._2.compareTo(t1._2)); // Sort by balance descending
    }
    
    public Map<String, BigInteger> tuplesToMap(List<Tuple<String, BigInteger>> tuples) {
        return tuples.stream()
            .collect(Collectors.toMap(
                tuple -> tuple._1,
                tuple -> tuple._2
            ));
    }
}
```

## Try - Functional Error Handling

The `Try` class provides monadic error handling for operations that may throw exceptions.

### Basic Try Usage

```java
import com.bloxbean.cardano.client.util.Try;
import com.bloxbean.cardano.client.function.helper.CheckedFunction;

public class TryExample {
    
    public void demonstrateBasicTry() {
        // Execute operation that might throw exception
        Try<BigInteger> result = Try.of(() -> {
            String numberStr = "1000000";
            return new BigInteger(numberStr);
        });
        
        if (result.isSuccess()) {
            BigInteger value = result.get();
            System.out.println("Success: " + value);
        } else {
            Exception error = result.getException();
            System.err.println("Error: " + error.getMessage());
        }
    }
    
    public void demonstrateTryWithDefault() {
        // Try parsing with default fallback
        String invalidNumber = "not-a-number";
        
        BigInteger value = Try.of(() -> new BigInteger(invalidNumber))
            .getOrElse(BigInteger.ZERO);
        
        System.out.println("Value: " + value); // Output: Value: 0
    }
    
    public void demonstrateTryWithCustomException() {
        Try<String> result = Try.of(() -> {
            throw new RuntimeException("Something went wrong");
        });
        
        try {
            String value = result.orElseThrow(() -> 
                new IllegalStateException("Operation failed"));
        } catch (IllegalStateException e) {
            System.err.println("Custom exception: " + e.getMessage());
        }
    }
}
```

### Practical Applications

```java
public class PracticalTryExample {
    
    // Safe transaction parsing
    public Try<Transaction> parseTransaction(String txData) {
        return Try.of(() -> {
            // This might throw various exceptions
            return Transaction.deserialize(HexUtil.decodeHexString(txData));
        });
    }
    
    // Safe account creation
    public Try<Account> createAccountFromMnemonic(String mnemonic) {
        return Try.of(() -> {
            if (StringUtils.isEmpty(mnemonic)) {
                throw new IllegalArgumentException("Mnemonic cannot be empty");
            }
            return Account.createFromMnemonic(mnemonic);
        });
    }
    
    // Safe backend operations
    public Try<List<Utxo>> getUtxos(String address) {
        return Try.of(() -> {
            return backendService.getUtxos(address);
        });
    }
    
    // Chaining operations with Try
    public Try<String> processTransactionSafely(String txData) {
        return parseTransaction(txData)
            .flatMap(tx -> Try.of(() -> {
                // Validate transaction
                validateTransaction(tx);
                return tx;
            }))
            .flatMap(tx -> Try.of(() -> {
                // Submit transaction
                return submitTransaction(tx);
            }));
    }
}
```

### Stream Integration

```java
public class TryStreamExample {
    
    public List<Account> createAccountsFromMnemonics(List<String> mnemonics) {
        return mnemonics.stream()
            .map(mnemonic -> Try.of(() -> Account.createFromMnemonic(mnemonic)))
            .filter(Try::isSuccess)
            .map(Try::get)
            .collect(Collectors.toList());
    }
    
    public List<Tuple<String, Exception>> getFailedOperations(List<String> operations) {
        return operations.stream()
            .map(op -> {
                Try<String> result = Try.of(() -> processOperation(op));
                return new Tuple<>(op, result.getException());
            })
            .filter(tuple -> tuple._2 != null)
            .collect(Collectors.toList());
    }
    
    // Parallel processing with Try
    public List<Try<BigInteger>> processBalancesParallel(List<String> addresses) {
        return addresses.parallelStream()
            .map(address -> Try.of(() -> getBalance(address)))
            .collect(Collectors.toList());
    }
}
```

## Exception Handling Classes

The library provides domain-specific exception classes for better error management.

### Available Exception Classes

```java
import com.bloxbean.cardano.client.exception.*;

public class ExceptionHandlingExample {
    
    public void demonstrateExceptionTypes() {
        try {
            // Address operations
            Address address = parseAddress("invalid-address");
        } catch (AddressExcepion e) { // Note: typo in class name
            System.err.println("Address parsing failed: " + e.getMessage());
        }
        
        try {
            // CBOR operations
            byte[] cborData = invalidCborData();
            Transaction tx = Transaction.deserialize(cborData);
        } catch (CborDeserializationException e) {
            System.err.println("CBOR deserialization failed: " + e.getMessage());
        } catch (CborSerializationException e) {
            System.err.println("CBOR serialization failed: " + e.getMessage());
        }
    }
    
    // Runtime exception handling
    public void handleRuntimeExceptions() {
        try {
            performAddressOperation();
        } catch (AddressRuntimeException e) {
            System.err.println("Runtime address error: " + e.getMessage());
            // Log stack trace for debugging
            e.printStackTrace();
        } catch (CborRuntimeException e) {
            System.err.println("Runtime CBOR error: " + e.getMessage());
        }
    }
}
```

### Best Practices for Exception Handling

```java
public class ExceptionBestPractices {
    
    // Wrap checked exceptions appropriately
    public Account createAccountSafely(String mnemonic) throws AddressExcepion {
        try {
            if (StringUtils.isEmpty(mnemonic)) {
                throw new AddressExcepion("Mnemonic cannot be empty");
            }
            
            return Account.createFromMnemonic(mnemonic);
            
        } catch (IllegalArgumentException e) {
            throw new AddressExcepion("Invalid mnemonic format", e);
        } catch (Exception e) {
            throw new AddressRuntimeException("Unexpected error creating account", e);
        }
    }
    
    // Comprehensive error handling for CBOR operations
    public <T> T deserializeCborSafely(byte[] cborData, Class<T> clazz) 
            throws CborDeserializationException {
        
        if (cborData == null || cborData.length == 0) {
            throw new CborDeserializationException("CBOR data cannot be null or empty");
        }
        
        try {
            // Attempt deserialization
            return performDeserialization(cborData, clazz);
            
        } catch (Exception e) {
            throw new CborDeserializationException(
                "Failed to deserialize CBOR data to " + clazz.getSimpleName(), e);
        }
    }
    
    // Graceful error recovery
    public Optional<Transaction> parseTransactionGracefully(String txHex) {
        try {
            byte[] txBytes = HexUtil.decodeHexString(txHex);
            Transaction tx = Transaction.deserialize(txBytes);
            return Optional.of(tx);
            
        } catch (CborDeserializationException e) {
            logger.warn("Failed to parse transaction: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error parsing transaction", e);
            return Optional.empty();
        }
    }
}
```

## Integration Patterns

### Combining Utilities for Complex Operations

```java
public class IntegratedUtilityExample {
    
    public class TransactionProcessor {
        
        public Try<String> processTransactionHex(String txHex) {
            return Try.of(() -> {
                // Validate input
                if (StringUtils.isEmpty(txHex)) {
                    throw new IllegalArgumentException("Transaction hex cannot be empty");
                }
                
                // Decode hex
                byte[] txBytes = HexUtil.decodeHexString(txHex);
                
                // Deserialize transaction
                Transaction tx = Transaction.deserialize(txBytes);
                
                // Process and return JSON representation
                return JsonUtil.getPrettyJson(tx);
            });
        }
        
        public Tuple<BigInteger, String> calculateFeeAndFormat(Transaction tx) {
            BigInteger fee = tx.getBody().getFee();
            String formattedFee = ADAConversionUtil.lovelaceToAda(fee).toPlainString() + " ADA";
            
            return new Tuple<>(fee, formattedFee);
        }
        
        public Triple<Boolean, String, Exception> validateAndProcess(String txHex) {
            Try<String> result = processTransactionHex(txHex);
            
            if (result.isSuccess()) {
                return new Triple<>(true, result.get(), null);
            } else {
                return new Triple<>(false, null, result.getException());
            }
        }
    }
}
```

### Utility Factory Pattern

```java
public class CardanoUtilities {
    
    // Centralized utility methods combining multiple utility classes
    public static class Conversion {
        public static String formatBalance(BigInteger lovelace) {
            if (lovelace == null) return "0 ADA";
            return ADAConversionUtil.lovelaceToAda(lovelace).toPlainString() + " " + CardanoConstants.LOVELACE;
        }
        
        public static Optional<BigInteger> parseAdaAmount(String adaString) {
            return Try.of(() -> {
                BigDecimal ada = new BigDecimal(adaString);
                return ADAConversionUtil.adaToLovelace(ada);
            }).isSuccess() ? Optional.of(Try.of(() -> {
                BigDecimal ada = new BigDecimal(adaString);
                return ADAConversionUtil.adaToLovelace(ada);
            }).get()) : Optional.empty();
        }
    }
    
    public static class Formatting {
        public static List<String> formatLongHash(String hash) {
            if (StringUtils.isEmpty(hash)) return Collections.emptyList();
            return StringUtils.splitStringEveryNCharacters(hash, 16);
        }
        
        public static String toDebugJson(Object obj) {
            String json = JsonUtil.getPrettyJson(obj);
            return json != null ? json : obj.toString();
        }
    }
    
    public static class Validation {
        public static boolean isValidHex(String hex) {
            if (StringUtils.isEmpty(hex)) return false;
            
            return Try.of(() -> {
                HexUtil.decodeHexString(hex);
                return true;
            }).getOrElse(false);
        }
        
        public static boolean isValidUtf8(byte[] data) {
            return data != null && StringUtils.isUtf8String(data);
        }
    }
}
```

These common utilities form the backbone of the Cardano Client Library, providing essential functionality for data conversion, error handling, and functional programming patterns. Use them consistently throughout your applications for robust and maintainable code.