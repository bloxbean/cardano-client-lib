package com.bloxbean.cardano.client.cip.cip67;

import com.bloxbean.cardano.client.crypto.CRC8;
import com.bloxbean.cardano.client.util.IntUtil;

import java.nio.ByteBuffer;

public class CIP67 {

    private CIP67() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Converts a label to the CIP67 asset name
     * @param label decimal range: [0, 65535]
     * @return prefix as bytes representation
     */
    public static byte[] labelToPrefix(int label) {
        int crc = CRC8.applyCRC8(IntUtil.intToByteArray(label));
        
        int value = 1;
        value = label << 12;
        value += crc << 4;
        return IntUtil.intToByteArray(value);
    }

    /**
     * Gets the asset name label from CIP67 asset name. In this method no verification is done.
     * @param labelBytes prefix as byte representation
     * @return decimal range: [0, 65535]
     */
    public static int prefixToLabel(byte[] labelBytes) {
        int assetName = ByteBuffer.wrap(labelBytes).getInt();
        int labelNum = assetName >> 12;
        labelNum = labelNum & 0x3FFFFFF; // to avoid wrong padding
        return labelNum;
    }

    /**
     * Verifies if the asset name is valid with CIP67. 
     * @param assetName prefix to check
     * @return true if valid padding and checksum check passed
     */
    public static boolean isValidAssetName(int assetName) {
        return isValidPadding(assetName) && verifyCheckSum(assetName);
    }

    /**
     * Verification if the asset name contains leading and following 0000 
     * @param assetName prefix to check
     * @return true if padding is valid
     */
    private static boolean isValidPadding(int assetName) {
        return (assetName & 0xC000003) == 0;
    }

    /**
     * Verification if the label and CRC-8 checksum are valid within the asset name
     * @param assetName prefix to check
     * @return true if checksum is verified
     */
    private static boolean verifyCheckSum(int assetName) {
        byte[] labelAsBytes = IntUtil.intToByteArray(assetName);
        int label = prefixToLabel(labelAsBytes);
        int checkSum = getCheckSum(labelAsBytes);
        return checkSum == CRC8.applyCRC8(IntUtil.intToByteArray(label));
    }

    /**
     * Extracts the checksum out of the asset name
     * @param labelBytes asset label as bytes
     * @return calculated checksum according to CRC-8
     */
    private static int getCheckSum(byte[] labelBytes) {
        int assetName = ByteBuffer.wrap(labelBytes).getInt();
        assetName = assetName >> 4;
        byte[] intToByteArray = IntUtil.intToByteArray(assetName);
        return intToByteArray[3]; // get the last Byte from label without the zero padding
    }

}