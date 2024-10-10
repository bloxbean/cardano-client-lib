package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.cip.cip8.*;
import com.bloxbean.cardano.client.cip.cip8.builder.COSESign1Builder;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import static com.bloxbean.cardano.client.cip.cip30.CIP30Constant.*;

/**
 * CIP30 signData() implementation to create and verify signature
 */
public enum CIP30DataSigner {

    INSTANCE();

    CIP30DataSigner() {
    }

    /**
     * Sign and create DataSignature in CIP30's signData() format
     *
     * @param addressBytes Address bytes
     * @param payload      payload bytes to sign
     * @param signer       signing account
     * @return DataSignature
     * @throws DataSignError
     */
    public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull Account signer)
            throws DataSignError {
        byte[] pvtKey = signer.privateKeyBytes();
        byte[] pubKey = signer.publicKeyBytes();

        return signData(addressBytes, payload, pvtKey, pubKey, false);
    }

    /**
     * Sign and create DataSignature in CIP30's signData() format
     *
     * @param addressBytes Address bytes
     * @param payload      payload bytes to sign
     * @param signer       signing account
     * @param hashPayload  indicates if the payload is expected to be hashed
     * @return DataSignature
     * @throws DataSignError
     */
    public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull Account signer, boolean hashPayload)
            throws DataSignError {
        byte[] pvtKey = signer.privateKeyBytes();
        byte[] pubKey = signer.publicKeyBytes();

        return signData(addressBytes, payload, pvtKey, pubKey, hashPayload);
    }

    /**
     * Sign and create DataSignature in CIP30's signData() format
     *
     * @param addressBytes Address bytes
     * @param payload      payload bytes to sign
     * @param pvtKey       private key bytes
     * @param pubKey       public key bytes to add
     * @return DataSignature
     * @throws DataSignError
     */
    public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull byte[] pvtKey, @NonNull byte[] pubKey) throws DataSignError {
        return signData(addressBytes, payload, pvtKey, pubKey, false);
    }

    /**
     * Sign and create DataSignature in CIP30's signData() format
     * @param addressBytes Address bytes
     * @param payload payload bytes to sign
     * @param pvtKey  private key bytes
     * @param pubKey public key bytes to add
     * @param hashPayload indicates if the payload is expected to be hashed
     * @return DataSignature
     * @throws DataSignError
     */
    public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull byte[] pvtKey, @NonNull byte[] pubKey, boolean hashPayload)
            throws DataSignError {
        try {
            HeaderMap protectedHeaderMap = new HeaderMap()
                    .algorithmId(ALG_EdDSA) //EdDSA
                    .keyId(addressBytes)
                    .addOtherHeader(ADDRESS_KEY, new ByteString(addressBytes));

            Headers headers = new Headers()
                    ._protected(new ProtectedHeaderMap(protectedHeaderMap))
                    .unprotected(new HeaderMap());

            COSESign1Builder coseSign1Builder = new COSESign1Builder(headers, payload, false).hashed(hashPayload);

            SigStructure sigStructure = coseSign1Builder.makeDataToSign();

            byte[] signature;
            if (pvtKey.length >= 64) { //64 bytes expanded pvt key
                signature = Configuration.INSTANCE.getSigningProvider().signExtended(sigStructure.serializeAsBytes(), pvtKey);
            } else { //32 bytes pvt key
                signature = Configuration.INSTANCE.getSigningProvider().sign(sigStructure.serializeAsBytes(), pvtKey);
            }

            COSESign1 coseSign1 = coseSign1Builder.build(signature);

            COSEKey coseKey = new COSEKey()
                    .keyType(OKP) //OKP
                    .keyId(addressBytes)
                    .algorithmId(ALG_EdDSA) //EdDSA
                    .addOtherHeader(CRV_KEY, new UnsignedInteger(CRV_Ed25519)) //crv Ed25519
                    .addOtherHeader(X_KEY, new ByteString(pubKey));  //x pub key used to sign sig_structure

            String sig = HexUtil.encodeHexString(coseSign1.serializeAsBytes());
            String key = HexUtil.encodeHexString(coseKey.serializeAsBytes());

            return new DataSignature(sig, key);
        } catch (Exception e) {
            throw new DataSignError("Error signing data", e);
        }
    }

    /**
     * Verify CIP30 signData signature
     *
     * @param dataSignature
     * @return true if verification is successful, otherwise false
     */
    public boolean verify(@NonNull DataSignature dataSignature) {
        COSESign1 coseSign1 = dataSignature.coseSign1();
        COSEKey coseKey = dataSignature.coseKey();

        byte[] pubKey = coseKey.otherHeaderAsBytes(X_KEY);
        SigStructure sigStructure = coseSign1.signedData();
        byte[] signature = coseSign1.signature();

        boolean sigVerified = Configuration.INSTANCE.getSigningProvider()
                .verify(signature, sigStructure.serializeAsBytes(), pubKey);

        //Verify address
        byte[] addressBytes = coseSign1.headers()._protected().getAsHeaderMap().otherHeaderAsBytes(ADDRESS_KEY);
        Address address = new Address(addressBytes);

        boolean addressVerified = AddressProvider.verifyAddress(address, pubKey);

        return sigVerified && addressVerified;
    }

}
