package com.bloxbean.cardano.client.crypto.vrf.cardano;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.config.CryptoExtConfiguration;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import com.bloxbean.cardano.client.crypto.vrf.VrfVerifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Cardano Praos leader eligibility check.
 * <p>
 * Provides range extension (deriving leader value and nonce value from a VRF output)
 * and the leader eligibility threshold comparison per Ouroboros Praos.
 * <p>
 * Reference: {@code vrfLeaderValue}, {@code vrfNonceValue} from
 * {@code ouroboros-consensus-protocol/.../Protocol/Praos/VRF.hs}
 * and {@code checkLeaderValue} from
 * {@code cardano-protocol-tpraos/.../BHeader.hs}
 */
public class CardanoLeaderCheck {

    private static final byte DOMAIN_LEADER = 0x4C; // 'L'
    private static final byte DOMAIN_NONCE = 0x4E;  // 'N'
    private static final MathContext MC = new MathContext(40, RoundingMode.HALF_EVEN);

    private CardanoLeaderCheck() {
    }

    /**
     * Derive the leader value from a VRF output (Praos range extension).
     * <p>
     * Computes {@code Blake2b_256("L" || vrfOutput)}.
     *
     * @param vrfOutput the raw VRF output (64 bytes from SHA-512)
     * @return 32-byte leader value hash
     */
    public static byte[] vrfLeaderValue(byte[] vrfOutput) {
        if (vrfOutput == null) {
            throw new IllegalArgumentException("vrfOutput must not be null");
        }
        byte[] input = new byte[1 + vrfOutput.length];
        input[0] = DOMAIN_LEADER;
        System.arraycopy(vrfOutput, 0, input, 1, vrfOutput.length);
        return Blake2bUtil.blake2bHash256(input);
    }

    /**
     * Derive the nonce value from a VRF output (Praos range extension).
     * <p>
     * Per the Haskell reference ({@code vrfNonceValue} in Praos/VRF.hs), this applies
     * double hashing: {@code Blake2b_256(Blake2b_256("N" || vrfOutput))}.
     * The inner hash applies the domain-separated hash, and the outer hash converts
     * the result into a {@code Nonce} value for epoch nonce evolution.
     *
     * @param vrfOutput the raw VRF output (64 bytes from SHA-512)
     * @return 32-byte nonce value hash
     */
    public static byte[] vrfNonceValue(byte[] vrfOutput) {
        if (vrfOutput == null) {
            throw new IllegalArgumentException("vrfOutput must not be null");
        }
        byte[] input = new byte[1 + vrfOutput.length];
        input[0] = DOMAIN_NONCE;
        System.arraycopy(vrfOutput, 0, input, 1, vrfOutput.length);
        byte[] firstHash = Blake2bUtil.blake2bHash256(input);
        return Blake2bUtil.blake2bHash256(firstHash);
    }

    /**
     * Check whether a leader value satisfies the Praos eligibility threshold.
     * <p>
     * The check is: {@code certNat < certNatMax * (1 - (1 - f)^sigma)}
     * where:
     * <ul>
     *   <li>{@code certNat} = leader hash interpreted as big-endian unsigned integer</li>
     *   <li>{@code certNatMax} = 2^(8 * leaderHash.length) (2^256 for Praos, 2^512 for TPraos)</li>
     *   <li>{@code f} = active slot coefficient</li>
     *   <li>{@code sigma} = pool's relative stake (0..1)</li>
     * </ul>
     *
     * @param leaderHash      the leader value hash (32 bytes for Praos, 64 bytes for TPraos)
     * @param sigma           the pool's relative active stake (0 to 1)
     * @param activeSlotCoeff the active slot coefficient f (e.g., 0.05)
     * @return true if the pool is eligible to lead this slot
     */
    public static boolean checkLeaderValue(byte[] leaderHash, BigDecimal sigma, BigDecimal activeSlotCoeff) {
        if (leaderHash == null || leaderHash.length == 0) {
            throw new IllegalArgumentException("leaderHash must not be null or empty");
        }
        if (sigma.signum() < 0 || sigma.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("sigma must be between 0 and 1 inclusive");
        }
        if (activeSlotCoeff.signum() <= 0 || activeSlotCoeff.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("activeSlotCoeff must be between 0 (exclusive) and 1 inclusive");
        }

        // certNat = unsigned big-endian integer from leaderHash
        BigInteger certNat = new BigInteger(1, leaderHash);

        // certNatMax = 2^(8 * length)
        BigInteger certNatMax = BigInteger.TWO.pow(8 * leaderHash.length);

        // threshold = 1 - (1 - f)^sigma
        // Special cases to avoid ln(0) or trivial results
        if (sigma.signum() == 0) {
            return false; // threshold = 0, no certNat < 0
        }
        if (activeSlotCoeff.compareTo(BigDecimal.ONE) == 0) {
            // (1 - 1)^sigma = 0 for sigma > 0, so threshold = 1
            // certNat < certNatMax is always true for valid hashes
            return true;
        }

        // Using exp(sigma * ln(1 - f)) for (1 - f)^sigma
        BigDecimal oneMinusF = BigDecimal.ONE.subtract(activeSlotCoeff, MC);
        BigDecimal lnOneMinusF = ln(oneMinusF, MC);
        BigDecimal exponent = sigma.multiply(lnOneMinusF, MC);
        BigDecimal powResult = exp(exponent, MC);
        BigDecimal threshold = BigDecimal.ONE.subtract(powResult, MC);

        // Compare: certNat < floor(certNatMax * threshold)
        BigDecimal product = new BigDecimal(certNatMax).multiply(threshold, MC);
        BigInteger rhs = product.toBigInteger(); // truncates toward zero (effectively floor for positive)
        return certNat.compareTo(rhs) < 0;
    }

    /**
     * Verify a VRF proof and check leader eligibility in one step (Praos).
     * <p>
     * This combines VRF verification, range extension, and the leader threshold check.
     *
     * @param vrfVkey         the VRF verification key (32 bytes)
     * @param vrfProof        the VRF proof (80 bytes)
     * @param slot            the slot number
     * @param epochNonce      the epoch nonce (32 bytes)
     * @param sigma           the pool's relative active stake
     * @param activeSlotCoeff the active slot coefficient
     * @return true if the VRF proof is valid AND the pool is eligible to lead
     */
    public static boolean verifyAndCheckLeader(
            byte[] vrfVkey, byte[] vrfProof,
            long slot, byte[] epochNonce,
            BigDecimal sigma, BigDecimal activeSlotCoeff) {

        // 1. Construct VRF input
        byte[] alpha = CardanoVrfInput.mkInputVrf(slot, epochNonce);

        // 2. Verify VRF proof
        VrfVerifier verifier = CryptoExtConfiguration.INSTANCE.getVrfVerifier();
        VrfResult vrfResult = verifier.verify(vrfVkey, vrfProof, alpha);
        if (!vrfResult.isValid()) {
            return false;
        }

        // 3. Range extension: derive leader value
        byte[] leaderValue = vrfLeaderValue(vrfResult.getOutput());

        // 4. Check leader eligibility
        return checkLeaderValue(leaderValue, sigma, activeSlotCoeff);
    }

    /**
     * Compute natural logarithm of x using Taylor series around 1.
     * <p>
     * For 0 < x < 2, uses ln(x) = 2 * sum_{k=0}^{inf} ((x-1)/(x+1))^(2k+1) / (2k+1).
     */
    static BigDecimal ln(BigDecimal x, MathContext mc) {
        if (x.signum() <= 0) {
            throw new ArithmeticException("ln of non-positive number");
        }

        // For x close to 1, use the series directly
        // ln(x) = 2 * arctanh((x-1)/(x+1))
        BigDecimal xMinus1 = x.subtract(BigDecimal.ONE, mc);
        BigDecimal xPlus1 = x.add(BigDecimal.ONE, mc);
        BigDecimal z = xMinus1.divide(xPlus1, mc); // z = (x-1)/(x+1)
        BigDecimal z2 = z.multiply(z, mc);

        BigDecimal sum = z;
        BigDecimal term = z;
        for (int k = 1; k <= 100; k++) {
            term = term.multiply(z2, mc);
            BigDecimal next = term.divide(BigDecimal.valueOf(2 * k + 1), mc);
            sum = sum.add(next, mc);
            if (next.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-mc.getPrecision())) < 0) {
                break;
            }
        }
        return sum.multiply(BigDecimal.valueOf(2), mc);
    }

    /**
     * Compute e^x using Taylor series.
     */
    static BigDecimal exp(BigDecimal x, MathContext mc) {
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (int k = 1; k <= 100; k++) {
            term = term.multiply(x, mc).divide(BigDecimal.valueOf(k), mc);
            sum = sum.add(term, mc);
            if (term.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-mc.getPrecision())) < 0) {
                break;
            }
        }
        return sum;
    }
}
