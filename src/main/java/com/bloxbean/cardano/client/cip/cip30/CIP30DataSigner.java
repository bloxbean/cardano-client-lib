package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressService;
import com.bloxbean.cardano.client.cip.cip8.*;
import com.bloxbean.cardano.client.cip.cip8.builder.COSESign1Builder;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

public enum CIP30DataSigner {
    INSTANCE();

    CIP30DataSigner() {

    }

    public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull Account signer)
            throws DataSignError {
        byte[] pvtKey = signer.privateKeyBytes();
        byte[] pubKey = signer.publicKeyBytes();

        return signData(addressBytes, payload, pvtKey, pubKey);
    }

    public DataSignature signData(@NonNull byte[] addressBytes, @NonNull byte[] payload, @NonNull byte[] pvtKey, @NonNull byte[] pubKey)
            throws DataSignError {
        try {
            HeaderMap protectedHeaderMap = new HeaderMap()
                    .algorithmId(-8) //EdDSA
                    .keyId(addressBytes)
                    .addOtherHeader("address", new ByteString(addressBytes));

            Headers headers = new Headers()
                    ._protected(new ProtectedHeaderMap(protectedHeaderMap))
                    .unprotected(new HeaderMap());

            COSESign1Builder coseSign1Builder = new COSESign1Builder(headers, payload, false);

            SigStructure sigStructure = coseSign1Builder.makeDataToSign();

            byte[] signature;

            if (pvtKey.length >= 64) { //64 bytes expanded pvt key
                signature = Configuration.INSTANCE.getSigningProvider().signExtended(sigStructure.serializeAsBytes(), pvtKey);
            } else { //32 bytes pvt key
                signature = Configuration.INSTANCE.getSigningProvider().sign(sigStructure.serializeAsBytes(), pvtKey);
            }

            COSESign1 coseSign1 = coseSign1Builder.build(signature);

            //COSEKey
            COSEKey coseKey = new COSEKey()
                    .keyType(1) //OKP
                    .keyId(addressBytes)
                    .algorithmId(-8) //EdDSA
                    .addOtherHeader(-1, new UnsignedInteger(6)) //crv Ed25519
                    .addOtherHeader(-2, new ByteString(pubKey));  //x pub key used to sign sig_structure

            return new DataSignature(HexUtil.encodeHexString(coseSign1.serializeAsBytes()),
                    HexUtil.encodeHexString(coseKey.serializeAsBytes()));
        } catch (Exception e) {
            throw new DataSignError("Error signing data", e);
        }
    }

    public boolean verify(@NonNull DataSignature dataSignature) {
        COSESign1 coseSign1 = dataSignature.getCOSESign1();
        COSEKey coseKey = dataSignature.getCOSEKey();

        byte[] pubKey = coseKey.otherHeaderAsBytes(-2);
        SigStructure sigStructure = coseSign1.signedData();
        byte[] signature = coseSign1.signature();

        boolean sigVerified = Configuration.INSTANCE.getSigningProvider()
                .verify(signature, sigStructure.serializeAsBytes(), pubKey);

        //Verify address
        byte[] addressBytes  = ((ByteString)coseSign1.headers()._protected().getAsHeaderMap().otherHeaders().get("address")).getBytes();
        Address address = new Address(addressBytes);
        byte[] publicKey = coseKey.otherHeaderAsBytes(-2);
        boolean addressVerified = AddressService.getInstance().verifyAddress(address, publicKey);

        return sigVerified && addressVerified;
    }

}
