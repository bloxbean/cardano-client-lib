/**
 * Copyright (c) 2017-2020 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package com.bloxbean.cardano.client.crypto.bip32.util;

import com.bloxbean.cardano.client.crypto.CryptoException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

//This file is originally from https://github.com/semuxproject/semux-core
/**
 * Utility class for Hmac SHA-512
 */
public class Hmac {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HMAC_SHA512 = "HmacSHA512";

    /**
     * Returns the HmacSHA512 digest.
     *
     * @param message
     *            message
     * @param secret
     *            secret
     * @return a digest
     */
    public static byte[] hmac256(byte[] message, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret, HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new CryptoException("Unable to perform HmacSHA256.", e);
        }
    }

    /**
     * Returns the HmacSHA512 digest.
     *
     * @param message
     *            message
     * @param secret
     *            secret
     * @return a digest
     */
    public static byte[] hmac512(byte[] message, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec keySpec = new SecretKeySpec(secret, HMAC_SHA512);
            mac.init(keySpec);
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new CryptoException("Unable to perform HmacSHA512.", e);
        }
    }
}
