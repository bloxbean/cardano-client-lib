package com.bloxbean.cardano.client.cip.cip67;

import com.bloxbean.cardano.client.crypto.CRC8;
import com.bloxbean.cardano.client.crypto.Utils;

import java.nio.ByteBuffer;

public class CIP67AssetNameUtil {

    private CIP67AssetNameUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Converts a label to the CIP67 asset name
     * @param label decimal range: [0, 65535]
     * @return prefix as bytes representation
     */
    public static byte[] labelToPrefix(int label) {
        byte[] labelBytes = new byte[4];
        Utils.uint32ToByteArrayBE(label, labelBytes, 0);
        int crc = CRC8.applyCRC8(labelBytes);
        
        int prefix = 1;
        prefix = label << 12;
        prefix += crc << 4;
        byte[] prefixBytes = new byte[4];
        Utils.uint32ToByteArrayBE(prefix, prefixBytes, 0);
        return prefixBytes;
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
        byte[] assetNamesAsBytes = new byte[4];
        Utils.uint32ToByteArrayBE(assetName,assetNamesAsBytes,0);
        int label = prefixToLabel(assetNamesAsBytes);
        int checkSum = getCheckSum(assetNamesAsBytes);
        byte[] labelAsBytes = new byte[4];
        Utils.uint32ToByteArrayBE(label,labelAsBytes,0);
        return checkSum == CRC8.applyCRC8(labelAsBytes);
    }

    /**
     * Extracts the checksum out of the asset name
     * @param labelBytes asset label as bytes
     * @return calculated checksum according to CRC-8
     */
    private static int getCheckSum(byte[] labelBytes) {
        int assetName = ByteBuffer.wrap(labelBytes).getInt();
        assetName = assetName >> 4;
        byte[] shiftedAssetNameBytes = new byte[4];
        Utils.uint32ToByteArrayBE(assetName, shiftedAssetNameBytes, 0);
        return shiftedAssetNameBytes[3]; // get the last Byte from label without the zero padding
    }

}