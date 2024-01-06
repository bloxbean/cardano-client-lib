package com.bloxbean.cardano.client.cip.cip67;

import com.bloxbean.cardano.client.crypto.CRC8;
import com.bloxbean.cardano.client.util.IntUtil;

import java.nio.ByteBuffer;

public class CIP67 {

    /**
     * Converts a label to the CIP67 asset name
     * @param label decimal range: [0, 65535]
     * @return
     */
    public static byte[] labelToPrefix(int label) {
        int crc = CRC8.applyCRC8(IntUtil.intToByteArray(label));
        
        int value = 1;
        value = label << 12;
        value += crc << 4;
        return IntUtil.intToByteArray(value);
    }

    /**
     * Gets the asset name label from CIP67 asset name
     * @param labelBytes 
     * @return decimal range: [0, 65535]
     */
    public static int prefixToLabel(byte[] labelBytes) {
        int assetName = ByteBuffer.wrap(labelBytes).getInt();
        int labelNum = assetName >> 12;
        return labelNum;
    }

    /**
     * Verifies if the asset name is valid with CIP67. 
     * @param assetName
     * @return
     */
    public static boolean isValidAssetName(int assetName) {
        boolean isValidLabel = true;
        // verify padding
        if(!isValidPadding(assetName) || !verifyCheckSum(assetName)) {
            isValidLabel = false;
        }
        return isValidLabel;
    }

    /**
     * Verification if the asset name contains leading and following 0000 
     * @param assetName
     * @return
     */
    private static boolean isValidPadding(int assetName) {
        boolean isValidPadding = true;
        if((assetName & 0xC000003) != 0) {
            isValidPadding = false;
        }
        return isValidPadding;
    }

    /**
     * Verification if the label and CRC-8 checksum are valid within the asset name
     * @param assetName
     * @return 
     */
    private static boolean verifyCheckSum(int assetName) {
        byte[] labelAsBytes = IntUtil.intToByteArray(assetName);
        int label = prefixToLabel(labelAsBytes);
        int checkSum = getCheckSum(labelAsBytes);
        boolean isValidChecksum = false;
        if(checkSum == CRC8.applyCRC8(IntUtil.intToByteArray(label))) {
            isValidChecksum = true;
        }
        return isValidChecksum;
    }

    /**
     * Extracts the checksum out of the asset name
     * @param labelBytes
     * @return
     */
    private static int getCheckSum(byte[] labelBytes) {
        int assetName = ByteBuffer.wrap(labelBytes).getInt();
        assetName = assetName >> 4;
        byte[] intToByteArray = IntUtil.intToByteArray(assetName);
        return intToByteArray[3]; // get the last Byte from label without the zero padding
    }

}