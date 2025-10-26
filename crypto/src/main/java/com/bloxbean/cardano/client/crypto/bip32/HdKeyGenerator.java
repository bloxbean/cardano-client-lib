package com.bloxbean.cardano.client.crypto.bip32;

import com.bloxbean.cardano.client.crypto.bip32.key.HdPrivateKey;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.Hmac;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Sha256Hash;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.OSUtil;
import net.i2p.crypto.eddsa.math.GroupElement;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;

//This file is originally from https://github.com/semuxproject/semux-core
//Updated according to Cardano's requirement
public class HdKeyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(HdKeyGenerator.class);

    private static final EdDSAParameterSpec ED25519SPEC = EdDSANamedCurveTable.getByName("ed25519");

    public static final String MASTER_PATH = "m";

    /**
     * Generate root key pair from entropy using ICARUS derivation (default for Cardano)
     *
     * @param entropy the entropy bytes
     * @return the root key pair
     */
    public HdKeyPair getRootKeyPairFromEntropy(byte[] entropy) {
        return getRootKeyPairFromEntropy(entropy, Bip32Type.ICARUS);
    }

    /**
     * Generate root key pair from entropy using the specified BIP32 derivation type.
     * Note: LEDGER and TREZOR require mnemonic phrases, so this method will convert entropy to mnemonic first.
     * For better performance with LEDGER/TREZOR, use {@link #getRootKeyPairFromMnemonic(String, Bip32Type)} instead.
     *
     * @param entropy   the entropy bytes
     * @param bip32Type the BIP32 derivation type
     * @return the root key pair
     */
    public HdKeyPair getRootKeyPairFromEntropy(byte[] entropy, Bip32Type bip32Type) {
        switch (bip32Type) {
            case ICARUS:
                return getRootKeyPairFromEntropyIcarus(entropy);
            case LEDGER:
                return getRootKeyPairFromEntropyLedger(entropy);
            case TREZOR:
                return getRootKeyPairFromEntropyTrezor(entropy);
            default:
                throw new IllegalArgumentException("Unsupported Bip32Type: " + bip32Type);
        }
    }

    /**
     * Generate root key pair from mnemonic phrase using ICARUS derivation (default for Cardano)
     *
     * @param mnemonicPhrase the mnemonic phrase (space-separated words)
     * @return the root key pair
     */
    public HdKeyPair getRootKeyPairFromMnemonic(String mnemonicPhrase) {
        return getRootKeyPairFromMnemonic(mnemonicPhrase, Bip32Type.ICARUS);
    }

    /**
     * Generate root key pair from mnemonic phrase using the specified BIP32 derivation type
     *
     * @param mnemonicPhrase the mnemonic phrase (space-separated words)
     * @param bip32Type      the BIP32 derivation type
     * @return the root key pair
     */
    public HdKeyPair getRootKeyPairFromMnemonic(String mnemonicPhrase, Bip32Type bip32Type) {
        return getRootKeyPairFromMnemonic(mnemonicPhrase, "", bip32Type);
    }

    /**
     * Generate root key pair from mnemonic phrase using the specified BIP32 derivation type with passphrase
     *
     * @param mnemonicPhrase the mnemonic phrase (space-separated words)
     * @param passphrase     the passphrase (empty string for no passphrase)
     * @param bip32Type      the BIP32 derivation type
     * @return the root key pair
     */
    public HdKeyPair getRootKeyPairFromMnemonic(String mnemonicPhrase, String passphrase, Bip32Type bip32Type) {
        if (passphrase == null) {
            passphrase = "";
        }

        switch (bip32Type) {
            case ICARUS:
                return getRootKeyPairFromMnemonicIcarus(mnemonicPhrase, passphrase);
            case LEDGER:
                return getRootKeyPairFromMnemonicLedger(mnemonicPhrase, passphrase);
            case TREZOR:
                return getRootKeyPairFromMnemonicTrezor(mnemonicPhrase, passphrase);
            default:
                throw new IllegalArgumentException("Unsupported Bip32Type: " + bip32Type);
        }
    }

    private HdKeyPair getRootKeyPairFromEntropyIcarus(byte[] entropy) {
        return getRootKeyPairFromEntropyIcarus(entropy, "");
    }

    private HdKeyPair getRootKeyPairFromEntropyIcarus(byte[] entropy, String passphrase) {
        // For ICARUS: passphrase is used as the password parameter in PBKDF2, entropy is the salt
        byte[] xprv = pbkdf2HmacSha512(passphrase.toCharArray(), entropy, 4096, 768);
        xprv[0] &= 248;
        xprv[31] &= 31;
        xprv[31] |= 64;

//        xprv[0] &= 0b1111_1000;
//        xprv[31] &= 0b0001_1111;
//        xprv[31] |= 0b0100_0000;

        return getKeyPairFromSecretKey(xprv, MASTER_PATH);
    }

    private HdKeyPair getRootKeyPairFromEntropyLedger(byte[] entropy) {
        try {
            // Convert entropy to mnemonic phrase and delegate to the mnemonic-based method
            MnemonicCode mnemonicCode = MnemonicCode.INSTANCE;
            List<String> mnemonicWords = mnemonicCode.toMnemonic(entropy);
            String mnemonicPhrase = String.join(" ", mnemonicWords);

            return getRootKeyPairFromMnemonicLedger(mnemonicPhrase, "");
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException("Failed to convert entropy to mnemonic for Ledger derivation", e);
        }
    }

    private HdKeyPair getRootKeyPairFromMnemonicLedger(String mnemonicPhrase, String passphrase) {
        // Step 1: Derive 64 bytes using PBKDF2 with HMAC-SHA512
        // Password: mnemonic phrase (UTF-8 NFKD normalized)
        // Salt: "mnemonic" + passphrase
        String salt = "mnemonic" + passphrase;
        byte[] data = pbkdf2HmacSha512(mnemonicPhrase.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 2048, 512);

        // Step 2: Generate chain code using HMAC-SHA256
        // Message: byte(0x01) + data
        byte[] chainCodeInput = new byte[1 + data.length];
        chainCodeInput[0] = 1;
        System.arraycopy(data, 0, chainCodeInput, 1, data.length);
        byte[] chainCode = Hmac.hmac256(chainCodeInput, "ed25519 seed".getBytes(StandardCharsets.UTF_8));

        // Step 3: Hash repeatedly until we get a valid key
        byte[][] iLiR = hashRepeatedlyLedger(data);
        byte[] iL = iLiR[0];
        byte[] iR = iLiR[1];

        // Step 4: Tweak bits of iL
        tweakBitsLedger(iL);

        // Step 5: Construct the extended private key (96 bytes: 64 for key data, 32 for chain code)
        byte[] xprv = new byte[96];
        System.arraycopy(iL, 0, xprv, 0, 32);
        System.arraycopy(iR, 0, xprv, 32, 32);
        System.arraycopy(chainCode, 0, xprv, 64, 32);

        return getKeyPairFromSecretKey(xprv, MASTER_PATH);
    }

    /**
     * Hash repeatedly using HMAC-SHA512 until the 3rd highest bit of the last byte is not set
     */
    private byte[][] hashRepeatedlyLedger(byte[] message) {
        byte[] hmacResult = Hmac.hmac512(message, "ed25519 seed".getBytes(StandardCharsets.UTF_8));
        byte[] iL = Arrays.copyOfRange(hmacResult, 0, 32);
        byte[] iR = Arrays.copyOfRange(hmacResult, 32, 64);

        // Check if the 3rd highest bit (bit 5, counting from 0) of the last byte is set
        // 0b0010_0000 = 0x20 = 32
        if ((iL[31] & 0x20) != 0) {
            // Recursively hash again with iL + iR
            byte[] combined = new byte[64];
            System.arraycopy(iL, 0, combined, 0, 32);
            System.arraycopy(iR, 0, combined, 32, 32);
            return hashRepeatedlyLedger(combined);
        }

        return new byte[][]{iL, iR};
    }

    /**
     * Tweak bits according to Ledger specification:
     * - Clear the lowest 3 bits
     * - Clear the highest bit
     * - Set the 2nd highest bit
     */
    private void tweakBitsLedger(byte[] data) {
        data[0] &= (byte) 0b1111_1000;   // Clear lowest 3 bits
        data[31] &= 0b0111_1111;  // Clear highest bit
        data[31] |= 0b0100_0000;  // Set 2nd highest bit
    }

    private HdKeyPair getRootKeyPairFromEntropyTrezor(byte[] entropy) {
        try {
            // Convert entropy to mnemonic phrase and delegate to the mnemonic-based method
            MnemonicCode mnemonicCode = MnemonicCode.INSTANCE;
            java.util.List<String> mnemonicWords = mnemonicCode.toMnemonic(entropy);
            String mnemonicPhrase = String.join(" ", mnemonicWords);

            return getRootKeyPairFromMnemonicTrezor(mnemonicPhrase, "");
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException("Failed to convert entropy to mnemonic for Trezor derivation", e);
        }
    }

    private HdKeyPair getRootKeyPairFromMnemonicTrezor(String mnemonicPhrase, String passphrase) {
        String[] words = mnemonicPhrase.trim().split("\\s+");
        int wordCount = words.length;

        // For < 24 words, Trezor uses the same algorithm as ICARUS (with passphrase support)
        if (wordCount < 24) {
            return getRootKeyPairFromMnemonicIcarus(mnemonicPhrase, passphrase);
        }

        // For 24 words, Trezor has a bug where it includes the checksum byte with the entropy
        // This is due to incorrect integer division by 8 when removing the BIP-39 checksum
        try {
            MnemonicCode mnemonicCode = MnemonicCode.INSTANCE;

            // Get the original entropy (32 bytes for 24 words)
            byte[] entropy = mnemonicCode.toEntropy(mnemonicPhrase);

            // For 24 words, the checksum is 8 bits = 1 byte
            // Trezor incorrectly includes this checksum byte with the entropy
            // We need to compute the checksum and append it
            byte[] entropyWithChecksum = new byte[entropy.length + 1];
            System.arraycopy(entropy, 0, entropyWithChecksum, 0, entropy.length);

            // Calculate the checksum byte (first 8 bits of SHA256 hash)
            byte[] hash = Sha256Hash.hash(entropy);
            entropyWithChecksum[entropy.length] = hash[0]; // First byte contains the 8-bit checksum

            // Now use PBKDF2 with the entropy+checksum as salt, passphrase as password
            byte[] xprv = pbkdf2HmacSha512(passphrase.toCharArray(), entropyWithChecksum, 4096, 768);

            // Apply bit tweaking (same as ICARUS)
            xprv[0] &= 248;
            xprv[31] &= 31;
            xprv[31] |= 64;

            return getKeyPairFromSecretKey(xprv, MASTER_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive Trezor key for 24-word mnemonic", e);
        }
    }

    private HdKeyPair getRootKeyPairFromMnemonicIcarus(String mnemonicPhrase, String passphrase) {
        try {
            byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);
            return getRootKeyPairFromEntropyIcarus(entropy, passphrase);
        } catch (MnemonicException e) {
            throw new RuntimeException("Failed to convert mnemonic to entropy for ICARUS derivation", e);
        }
    }

    public HdKeyPair getAccountKeyPairFromSecretKey(byte[] xprv, DerivationPath derivationPath) {
        String accountPath = getPath(MASTER_PATH, derivationPath.getPurpose().getValue(), derivationPath.getPurpose().isHarden());
        accountPath = getPath(accountPath, derivationPath.getCoinType().getValue(), derivationPath.getCoinType().isHarden());
        accountPath = getPath(accountPath, derivationPath.getAccount().getValue(), derivationPath.getAccount().isHarden());
        return getKeyPairFromSecretKey(xprv, accountPath);
    }

    public HdKeyPair getKeyPairFromSecretKey(byte[] xprv, String path) {
        byte[] IL = Arrays.copyOfRange(xprv, 0, 64);
        byte[] IR = Arrays.copyOfRange(xprv, 64, 96);

        byte[] A = ED25519SPEC.getB().scalarMultiply(IL).toByteArray();

        HdPublicKey publicKey = new HdPublicKey();
        HdPrivateKey privateKey = new HdPrivateKey();

        privateKey.setKeyData(IL);
        privateKey.setChainCode(IR);

        publicKey.setKeyData(A);
        publicKey.setChainCode(IR);

        return new HdKeyPair(privateKey, publicKey, path);
    }

    private byte[] pbkdf2HmacSha512(final char[] password, final byte[] salt, final int iterations,
                                    final int keyLength) {

        try {
            if (OSUtil.isAndroid()) {
                PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
                gen.init(new String(password).getBytes(StandardCharsets.UTF_8), salt, iterations);
                byte[] dk = ((KeyParameter) gen.generateDerivedParameters(keyLength)).getKey();
                return dk;
            } else {
                SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
                PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
                SecretKey key = skf.generateSecret(spec);
                return key.getEncoded();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Derive the child key pair (public + private) from the parent key pair.
     *
     * @param parent     the parent key
     * @param child      the child index
     * @param isHardened whether is child index is hardened
     * @return the child key pair
     */
    public HdKeyPair getChildKeyPair(HdKeyPair parent, long child, boolean isHardened) {
        HdPrivateKey privateKey = new HdPrivateKey();
        HdPublicKey publicKey = new HdPublicKey();
        HdKeyPair key = new HdKeyPair(privateKey, publicKey,
                getPath(parent.getPath(), child, isHardened));

        if (isHardened) {
            child += 0x80000000;
        }

        byte[] xChain = parent.getPrivateKey().getChainCode();
        /// backwards hmac order in method?
        byte[] I;
        if (isHardened) {
            // If so (hardened child): let I = HMAC-SHA512(Key = cpar, Data = 0x00 ||
            // ser256(kpar) || ser32(i)). (Note: The 0x00 pads the private key to make it 33
            // bytes long.)
            BigInteger kpar = BytesUtil.parse256(parent.getPrivateKey().getKeyData());
            byte[] data = BytesUtil.merge(new byte[]{0}, BytesUtil.ser256(kpar), BytesUtil.ser32(child));
            I = Hmac.hmac512(data, xChain);
        } else {
            // I = HMAC-SHA512(Key = cpar, Data = serP(point(kpar)) || ser32(i))
            // just use public key
            byte[] data = BytesUtil.merge(parent.getPublicKey().getKeyData(), BytesUtil.ser32(child));
            I = Hmac.hmac512(data, xChain);
        }
        // split into left/right
        byte[] IL = Arrays.copyOfRange(I, 0, 32);
        byte[] IR = Arrays.copyOfRange(I, 32, 64);

        byte[] childNumber = BytesUtil.ser32(child);

        privateKey.setVersion(parent.getPrivateKey().getVersion());
        privateKey.setDepth(parent.getPrivateKey().getDepth() + 1);
        privateKey.setChildNumber(childNumber);
        privateKey.setChainCode(IR);

        publicKey.setVersion(parent.getPublicKey().getVersion());
        publicKey.setDepth(parent.getPublicKey().getDepth() + 1);
        publicKey.setChildNumber(childNumber);
        publicKey.setChainCode(IR);

        //If derivation V2 Shelley
        byte[] kP = parent.getPrivateKey().getKeyData();
        byte[] kLP = Arrays.copyOfRange(kP, 0, 32);
        byte[] kRP = Arrays.copyOfRange(kP, 32, 64);
        byte[] AP = parent.getPublicKey().getKeyData();
        byte[] cP = parent.getPublicKey().getChainCode();

        byte[] Z, c;
        if (isHardened) {
            byte[] data = BytesUtil.merge(new byte[]{0}, kLP, kRP, BytesUtil.ser32LE(child));
            Z = Hmac.hmac512(data, cP);
            data[0] = 1;
            c = Hmac.hmac512(data, cP);
        } else {
            byte[] data = BytesUtil.merge(new byte[]{2}, AP, BytesUtil.ser32LE(child));
            Z = Hmac.hmac512(data, cP);
            data[0] = 3;
            c = Hmac.hmac512(data, cP);
        }
        c = Arrays.copyOfRange(c, 32, 64);
        byte[] ZL = Arrays.copyOfRange(Z, 0, 28);
        byte[] ZR = Arrays.copyOfRange(Z, 32, 64);

        if (logger.isTraceEnabled()) {
            logger.trace("parent, kLP = " + HexUtil.encodeHexString(kLP));
            logger.trace("parent, kRP = " + HexUtil.encodeHexString(kRP));
            logger.trace("parent,  AP = " + HexUtil.encodeHexString(AP));
            logger.trace("parent,  cP = " + HexUtil.encodeHexString(cP));
        }

        BigInteger kLiBI = parseUnsignedLE(ZL)
                .multiply(BigInteger.valueOf(8))
                .add(parseUnsignedLE(kLP));
        BigInteger order = BigInteger.valueOf(2).pow(252)
                .add(new BigInteger("27742317777372353535851937790883648493"));
        if (kLiBI.mod(order).equals(BigInteger.ZERO)) {
            return null;
        }
        IL = serializeUnsignedLE256(kLiBI);

        BigInteger kRiBI = parseUnsignedLE(ZR)
                .add(parseUnsignedLE(kRP))
                .mod(BigInteger.valueOf(2).pow(256));
        IR = serializeUnsignedLE256(kRiBI);

        I = BytesUtil.merge(IL, IR);
        byte[] A = ED25519SPEC.getB().scalarMultiply(IL).toByteArray();

        privateKey.setKeyData(I);
        publicKey.setKeyData(A);

        privateKey.setChainCode(c);
        publicKey.setChainCode(c);

        if (logger.isTraceEnabled()) {
            logger.trace("child, IL = " + HexUtil.encodeHexString(IL));
            logger.trace("child, IR = " + HexUtil.encodeHexString(IR));
            logger.trace("child,  A = " + HexUtil.encodeHexString(A));
            logger.trace("child,  c = " + HexUtil.encodeHexString(c));
        }

        return key;
    }

    /**
     * Derive the public child key from HD parent public key
     *
     * @param parent the parent key
     * @param child  the child index
     * @return the child public key
     */
    public HdPublicKey getChildPublicKey(HdPublicKey parent, int child) {
        HdPublicKey publicKey = new HdPublicKey();
        byte[] AP = parent.getKeyData();

        byte[] pChain = parent.getChainCode();
        byte[] childNumber = BytesUtil.ser32(child);

        //prefix 0x02 for child public key
        byte[] ApLE = serializeUnsignedLE256(parseUnsignedLE(AP));
        byte[] data = BytesUtil.merge(new byte[]{2}, ApLE, BytesUtil.ser32LE(child));
        byte[] Z = Hmac.hmac512(data, pChain);

        //prefix 0x03 for child chain code
        data[0] = 3;
        byte[] c = Hmac.hmac512(data, parent.getChainCode());

        //truncate to right 32 bytes for child chain code
        c = Arrays.copyOfRange(c, 32, 64);

        // split into left (28 bytes) /right (for child public key)
        byte[] ZL = Arrays.copyOfRange(Z, 0, 28);
//      byte[] ZR = Arrays.copyOfRange(Z, 32, 64);

        //Ai <- AP + [8ZL]B,
        BigInteger kLiBI = parseUnsignedLE(ZL)
                .multiply(BigInteger.valueOf(8));

        byte[] kLi = serializeUnsignedLE256(kLiBI);

        GroupElement gp1 = new GroupElement(ED25519SPEC.getCurve(), AP);
        gp1 = gp1.toCached();
        GroupElement groupElement = ED25519SPEC.getB().scalarMultiply(kLi).add(gp1);
        //TODO -- If Ai is the identity point (0, 1), discard the child

        byte[] Ai = groupElement.toByteArray(); //child public key

        publicKey.setVersion(parent.getVersion());
        publicKey.setDepth(parent.getDepth() + 1);
        publicKey.setChildNumber(childNumber);
        publicKey.setChainCode(c);
        publicKey.setKeyData(Ai);

        return publicKey;
    }

    private String getPath(String parentPath, long child, boolean isHardened) {
        if (parentPath == null) {
            parentPath = MASTER_PATH;
        }
        return parentPath + "/" + child + (isHardened ? "'" : "");
    }

    private void reverse(byte[] input) {
        for (int i = 0; i < input.length / 2; i++) {
            byte temp = input[i];
            input[i] = input[input.length - 1 - i];
            input[input.length - 1 - i] = temp;
        }
    }

    private BigInteger parseUnsignedLE(byte[] bytes) {
        byte[] temp = bytes.clone();
        reverse(temp);
        return new BigInteger(1, temp);
    }

    private byte[] serializeUnsignedLE256(BigInteger bi) {
        byte[] temp = bi.toByteArray();
        if (temp.length > 32) {
            temp = Arrays.copyOfRange(temp, temp.length - 32, temp.length);
        }

        reverse(temp);

        if (temp.length < 32) {
            return Arrays.copyOf(temp, 32);
        } else {
            return temp;
        }
    }

    public static byte[] getPublicKey(byte[] privateKey) {
        byte[] IL = Arrays.copyOfRange(privateKey, 0, 32);
        byte[] A = ED25519SPEC.getB().scalarMultiply(IL).toByteArray();
        return A;
    }
}
