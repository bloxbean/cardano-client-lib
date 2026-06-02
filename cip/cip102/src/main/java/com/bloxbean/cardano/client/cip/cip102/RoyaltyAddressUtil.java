package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.address.*;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.spec.*;

import java.math.BigInteger;

/**
 * Utility class for converting between CCL {@link Address} objects and the Plutus
 * address representation used in CIP-102 royalty datum fields.
 *
 * <h2>Plutus address structure</h2>
 * <pre>
 * Address      = Constr(0, [paymentCredential, Option&lt;stakeCredential&gt;])
 * Credential   = Constr(0, [hash]) -- VerificationKey
 *              | Constr(1, [hash]) -- Script
 * StakeOption  = Constr(1, [])                        -- None (enterprise address)
 *              | Constr(0, [Constr(0, [credential])]) -- Some(Inline(credential))
 *              | Constr(0, [Constr(1, [slot, tx, cert])]) -- Some(Pointer)
 * </pre>
 */
public class RoyaltyAddressUtil {

    private RoyaltyAddressUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Converts a CCL {@link Address} (bech32 or byte-based) to the Plutus
     * {@code ConstrPlutusData} representation expected in the royalty datum.
     *
     * <p>Supported address types: {@link AddressType#Base}, {@link AddressType#Enterprise},
     * {@link AddressType#Ptr}.
     *
     * @param address the Cardano address to convert
     * @return {@code Constr(0, [paymentCredential, stakeOption])}
     * @throws IllegalArgumentException for reward or Byron addresses
     */
    public static ConstrPlutusData toPlutusData(Address address) {
        Credential paymentCred = address.getPaymentCredential()
                .orElseThrow(() -> new IllegalArgumentException("Address has no payment credential: " + address.getAddressType()));

        ConstrPlutusData paymentData = credentialToPlutusData(paymentCred);
        PlutusData stakeOption = buildStakeOption(address);

        return ConstrPlutusData.of(0, paymentData, stakeOption);
    }

    /**
     * Reconstructs a CCL {@link Address} from the Plutus representation stored in a
     * CIP-102 datum. The network must be provided since it is not encoded in the
     * Plutus address.
     *
     * <p>Pointer addresses cannot be reconstructed and will throw
     * {@link UnsupportedOperationException}.
     *
     * @param constr  the Plutus address {@code ConstrPlutusData}
     * @param network the target network (mainnet or testnet)
     * @return reconstructed {@link Address}
     * @throws UnsupportedOperationException for pointer stake credentials
     */
    public static Address fromPlutusData(ConstrPlutusData constr, Network network) {
        var list = constr.getData().getPlutusDataList();
        Credential paymentCred = plutusDataToCredential((ConstrPlutusData) list.get(0));

        ConstrPlutusData stakeOption = (ConstrPlutusData) list.get(1);
        if (stakeOption.getAlternative() == 1) {
            // None → enterprise address
            return AddressProvider.getEntAddress(paymentCred, network);
        }

        // Some(referencedCredential)
        ConstrPlutusData referenced = (ConstrPlutusData) stakeOption.getData().getPlutusDataList().get(0);
        if (referenced.getAlternative() == 0) {
            // Inline(credential)
            Credential stakeCred = plutusDataToCredential(
                    (ConstrPlutusData) referenced.getData().getPlutusDataList().get(0));
            return AddressProvider.getBaseAddress(paymentCred, stakeCred, network);
        }

        // Pointer — cannot reconstruct a CCL Address from pointer data alone
        throw new UnsupportedOperationException("Reconstruction of pointer addresses from Plutus data is not supported");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static PlutusData buildStakeOption(Address address) {
        switch (address.getAddressType()) {
            case Enterprise:
                return ConstrPlutusData.of(1); // None

            case Base: {
                Credential stakeCred = address.getDelegationCredential()
                        .orElseThrow(() -> new IllegalArgumentException("Base address has no delegation credential"));
                ConstrPlutusData stakeCredData = credentialToPlutusData(stakeCred);
                ConstrPlutusData inline = ConstrPlutusData.of(0, stakeCredData); // Inline(cred)
                return ConstrPlutusData.of(0, inline); // Some(Inline)
            }

            case Ptr: {
                PointerAddress ptrAddr = new PointerAddress(address.getBytes());
                Pointer pointer = ptrAddr.getPointer();
                ConstrPlutusData pointerData = ConstrPlutusData.of(1,
                        BigIntPlutusData.of(BigInteger.valueOf(pointer.getSlot())),
                        BigIntPlutusData.of(BigInteger.valueOf(pointer.getTxIndex())),
                        BigIntPlutusData.of(BigInteger.valueOf(pointer.getCertIndex())));
                return ConstrPlutusData.of(0, pointerData); // Some(Pointer)
            }

            default:
                throw new IllegalArgumentException("Unsupported address type for CIP-102 royalty datum: " + address.getAddressType());
        }
    }

    private static ConstrPlutusData credentialToPlutusData(Credential credential) {
        int alternative = credential.getType() == CredentialType.Key ? 0 : 1;
        return ConstrPlutusData.of(alternative, BytesPlutusData.of(credential.getBytes()));
    }

    private static Credential plutusDataToCredential(ConstrPlutusData constr) {
        byte[] hash = ((BytesPlutusData) constr.getData().getPlutusDataList().get(0)).getValue();
        return constr.getAlternative() == 0
                ? Credential.fromKey(hash)
                : Credential.fromScript(hash);
    }
}
