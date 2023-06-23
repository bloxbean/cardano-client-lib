package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class QuickTxBaseTest {
    protected ObjectMapper objectMapper = new ObjectMapper();
    protected String protocolParamJsonFile;

    protected Object loadObjectFromJson(String key, Class clazz) throws IOException {
        return objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(protocolParamJsonFile), clazz);
    }

    public static String generateRandomHexValue(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[length];
        secureRandom.nextBytes(randomBytes);

        StringBuilder hexBuilder = new StringBuilder(2 * length);
        for (byte b : randomBytes) {
            hexBuilder.append(String.format("%02x", b));
        }

        return hexBuilder.toString();
    }

    public int generateRandomInteger() {
        Random random = new Random();
        return random.nextInt(10); // Generates a random integer between 0 (inclusive) and 10 (exclusive)
    }

    public String generateRandomAddress() {
        return "addr1" + generateRandomHexValue(32);
    }

    protected Optional<BigInteger> getLovelaceAmountForAddress(List<TransactionOutput> outputs, String address) {
        return outputs.stream().filter(transactionOutput -> transactionOutput.getAddress().equals(address))
                .map(transactionOutput -> transactionOutput.getValue().getCoin())
                .reduce((bigInteger, bigInteger2) -> bigInteger.add(bigInteger2));
    }

    protected Optional<BigInteger> getAssetAmountForAddress(List<TransactionOutput> outputs, String address, String policy, String assetName) {
        return outputs.stream().filter(transactionOutput -> transactionOutput.getAddress().equals(address))
                .map(transactionOutput -> transactionOutput.getValue().getMultiAssets())
                .flatMap(multiAssets -> multiAssets.stream())
                .filter(multiAsset -> multiAsset.getPolicyId().equals(policy))
                .flatMap(multiAsset -> multiAsset.getAssets().stream())
                .filter(asset -> asset.getNameAsHex().equals(HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8), true)))
                .map(asset -> asset.getValue())
                .reduce((bigInteger, bigInteger2) -> bigInteger.add(bigInteger2));
    }
}
