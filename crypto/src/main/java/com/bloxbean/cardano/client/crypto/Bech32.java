package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.util.Tuple;
import org.bouncycastle.util.Arrays;

import java.util.ArrayList;
import java.util.List;

public class Bech32 {

    private static final int TotalMaxLength = 108; //103 = mainnet length of a delegation address, 108 = testnet length
    private static final int CheckSumSize = 6;
    private static final int HrpMinLength = 1;
    private static final int HrpMaxLength = 83;
    private static final int HrpMinValue = 33;
    private static final int HrpMaxValue = 126;
    private static final char Separator = '1';
    private static final String B32Chars = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";


    public static class Bech32Data {
        public final String hrp;
        public final byte[] data;
        public final byte ver;

        private Bech32Data(final String hrp, final byte[] data, byte ver) {
            this.hrp = hrp;
            this.data = data;
            this.ver = ver;
        }
    }

    public static boolean isValid(String bech32EncodedString) {
        if (!hasValidChars(bech32EncodedString)) {
            return false;
        }

        Tuple<String, byte[]> data = bech32Decode(bech32EncodedString);
        if (data._2.length < CheckSumSize) {
            return false;
        }

        return verifyChecksum(data._1, data._2);
    }

    public static boolean hasValidChars(String bech32EncodedString) {

        if ((bech32EncodedString == null || bech32EncodedString.isEmpty()) || bech32EncodedString.length() > TotalMaxLength) {
            return false;
        }

        // Reject mixed upper and lower characters.
        if (!bech32EncodedString.toLowerCase().equals(bech32EncodedString) && !bech32EncodedString.toUpperCase().equals(bech32EncodedString)) {
            return false;
        }

        // Check if it has a separator
        int sepIndex = bech32EncodedString.lastIndexOf(Separator);
        if (sepIndex == -1) {
            return false;
        }

        // Validate human readable part
        String hrp = bech32EncodedString.substring(0, sepIndex);
        if (!isValidHrp(hrp)) {
            return false;
        }

        // Validate data part
        String data = bech32EncodedString.substring(sepIndex + 1);
        if (data.length() < CheckSumSize || data.chars().anyMatch(x -> B32Chars.indexOf(x) == -1)) {
            return false;
        }

        return true;
    }

    private static boolean isValidHrp(String hrp) {
        return hrp != null &&
                hrp.trim().length() > 0 &&
                hrp.length() >= HrpMinLength &&
                hrp.length() < HrpMaxLength &&
                hrp.chars().allMatch(character -> character >= HrpMinValue && character <= HrpMaxValue);
    }


    private static int polymod(final byte[] values) {
        int c = 1;
        for (byte v_i : values) {
            int c0 = (c >>> 25) & 0xff;
            c = ((c & 0x1ffffff) << 5) ^ (v_i & 0xff);
            if ((c0 & 1) != 0) c ^= 0x3b6a57b2;
            if ((c0 & 2) != 0) c ^= 0x26508e6d;
            if ((c0 & 4) != 0) c ^= 0x1ea119fa;
            if ((c0 & 8) != 0) c ^= 0x3d4233dd;
            if ((c0 & 16) != 0) c ^= 0x2a1462b3;
        }
        return c;
    }

    private static byte[] expandHrp(String hrp) {
        byte[] result = new byte[(2 * hrp.length()) + 1];
        for (int i = 0; i < hrp.length(); i++) {
            result[i] = (byte) (((int) hrp.charAt(i)) >> 5);
            result[i + hrp.length() + 1] = (byte) (((int) hrp.charAt(i)) & 0b0001_1111 /*=31*/);
        }
        return result;
    }

    private static boolean verifyChecksum(String hrp, byte[] data) {
        byte[] temp = Arrays.concatenate(expandHrp(hrp), data);
        return polymod(temp) == 1;
    }


    private static Tuple<String, byte[]> bech32Decode(String bech32EncodedString) {

        bech32EncodedString = bech32EncodedString.toLowerCase();

        int separatorIndex = bech32EncodedString.lastIndexOf(Separator);
        String hrp = bech32EncodedString.substring(0, separatorIndex);
        String data = bech32EncodedString.substring(separatorIndex + 1);

        byte[] b32Arr = new byte[data.length()];
        for (int i = 0; i < data.length(); i++) {
            b32Arr[i] = (byte) B32Chars.indexOf(data.charAt(i));
        }

        return new Tuple(hrp, b32Arr);
    }

    private static byte[] convertBits(byte[] data, int fromBits, int toBits, boolean pad) {
        // TODO: Optimize Looping
        // We can use a method similar to BIP39 here to avoid the nested loop, usage of List, increase the speed,
        // and shorten this function to 3 lines.
        // Or convert to ulong[], loop through it (3 times) take 5 bits at a time or 8 bits at a time...
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        int maxacc = (1 << (fromBits + toBits - 1)) - 1;

        List<Byte> result = new ArrayList<>();
        for (byte _b : data) {
            // Speed doesn't matter for this class but we can skip this check for 8 to 5 conversion.
            int b = Byte.toUnsignedInt(_b);
            if ((b >> fromBits) > 0) {
                System.out.println("a");
                return null;
            }
            acc = ((acc << fromBits) | b) & maxacc;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                result.add((byte) ((acc >> bits) & maxv));
            }
        }
        if (pad) {
            if (bits > 0) {
                result.add((byte) ((acc << (toBits - bits)) & maxv));
            }
        } else if (bits >= fromBits || (byte) ((acc << (toBits - bits)) & maxv) != 0) {
            System.out.println("b");
            return null;
        }

        byte[] res = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            res[i] = result.get(i);
        }

        return res;

    }

    public static Bech32Data decode(String bech32EncodedString) {
        Tuple<String, byte[]> bech32Data = bech32Decode(bech32EncodedString);

        String hrp = bech32Data._1;
        byte[] b32Arr = bech32Data._2;

        if (b32Arr.length < CheckSumSize) {
            throw new RuntimeException("Invalid data length.");
        }
        if (!verifyChecksum(hrp, b32Arr)) {
            throw new RuntimeException("Invalid checksum.");
        }


        byte[] data = Arrays.copyOfRange(b32Arr, 0, b32Arr.length - CheckSumSize);
        byte[] b256Arr = convertBits(data, 5, 8, false);
        if (b256Arr == null) {
            throw new RuntimeException("Invalid data format.");
        }

        byte witVer = b32Arr[0];
        return new Bech32Data(hrp, b256Arr, witVer);
    }

    public static String encode(byte[] data, String hrp) {
        if (data == null || data.length == 0)
            throw new RuntimeException("Data can not be null or empty.");
        if (!isValidHrp(hrp))
            throw new RuntimeException("Invalid HRP.");

        byte[] b32Arr = convertBits(data, 8, 5, true);
        byte[] checksum = calculateCheckSum(hrp, b32Arr);

        b32Arr = Arrays.concatenate(b32Arr, checksum);
        StringBuilder result = new StringBuilder(b32Arr.length + 1 + hrp.length());
        result.append(hrp).append(Separator);
        for (byte b : b32Arr) {
            result.append(B32Chars.charAt(b));
        }

        return result.toString();
    }

    private static byte[] calculateCheckSum(String hrp, byte[] data) {
        // expand hrp, append data to it, and then add 6 zero bytes at the end.
        byte[] bytes = Arrays.concatenate(Arrays.concatenate(expandHrp(hrp), data), new byte[CheckSumSize]);

        // get polymod of the whole data and then flip the least significant bit.
        int pm = polymod(bytes) ^ 1; //

        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) {
            result[i] = (byte) ((pm >> 5 * (5 - i)) & 0b0001_1111 /*=31*/);
        }
        return result;
    }

}

