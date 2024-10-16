package com.bloxbean.cardano.client.cip.cip8.builder;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.cip.cip8.*;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class COSESignBuilderTest extends COSEBaseTest {
    String mnemonic1 = "nice orient enjoy teach jump office alert inquiry apart unaware seat tumble unveil device have bullet morning eyebrow time image embody divide version uniform";
    Account account1 = new Account(Networks.testnet(), mnemonic1);

    String mnemonic2 = "carbon time empty obey bicycle choice mind kitchen shadow call strike skull check flag series deal garlic wing uphold problem bamboo winner install price";
    Account account2 = new Account(Networks.testnet(), mnemonic2);

    @Test
    void buildCOSESign() throws CborException {
        HeaderMap pHeaderMap = new HeaderMap()
                .algorithmId(14)
                .contentType(-1000);

        HeaderMap unpHeadermap = new HeaderMap()
                .addOtherHeader(-100L, new UnicodeString("Some header value"));

        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap(pHeaderMap))
                .unprotected(unpHeadermap);

        byte[] payload = "Hello World".getBytes();
        COSESignBuilder coseSignBuilder = new COSESignBuilder(headers, payload, false)
                .hashed(true);

        COSESign coseSign = coseSignBuilder.build(getSignatures(coseSignBuilder));
        String serHex = HexUtil.encodeHexString(coseSign.serializeAsBytes());
        System.out.println(serHex);

        //This hex is the result from message-signing rust impl. (Check cose_sign_builder.rs)
        String expected = "8447a2010e033903e7a1386371536f6d65206865616465722076616c7565581c19790463ef4ad09bdb724e3a6550c640593d4870f6e192ac8147f35d828340a238c77819616e6f74686572206164646974696f6e616c20686561646572646b6579316a6b6579312076616c7565584098b74a575e435c5506ec80bc4b47aceba462a4edaf785c345c022acb80957ddbdb36177f3a95cee97efdb474bbdcb66db0fe93e9b011523a8a36d8b443dbb5008340a239018f781a616e6f74686572206164646974696f6e616c2068656164657232646b6579326a6b6579322076616c756558406161857b10b1bfa62bdf6f3ae9d751cc361446af41ec79fa2fca8fe67d27f3d8622ad99786539aa3dedd4d7456d5e13d5474f3d72babd37f6dbe09bfc8c12701";
        COSESign expectedCoseSign2 = COSESign.deserialize(CborDecoder.decode(HexUtil.decodeHexString(expected)).get(0));

        //rust message-signing lib doesn't add hashed key to the unprotected headers. So, we are just checking the signatures here
        //But rust COSESign1Builder adds hashed key to the unprotected headers. Not sure why?
        assertThat(coseSign.signatures().get(0).signature()).endsWith(expectedCoseSign2.signatures().get(0).signature());
        assertThat(coseSign.signatures().get(1).signature()).endsWith(expectedCoseSign2.signatures().get(1).signature());
    }

    @Test
    void buildCOSESign_withPayLoadExTrue_additionalHeaders() throws CborException {
        byte[] keyId = new byte[]{1, 2, 3};
        byte[] initVector = new byte[]{4, 5, 6};
        byte[] partialInitVector = new byte[]{4, 5, 6};
        HeaderMap pHeaderMap = new HeaderMap()
                .algorithmId(14)
                .addCriticality("criticality-1")
                .contentType(-1000)
                .keyId(keyId)
                .initVector(initVector)
                .partialInitVector(partialInitVector)
                .counterSignature(List.of(
                        new COSESignature()
                                .headers(new Headers()
                                        ._protected(new ProtectedHeaderMap())
                                        .unprotected(new HeaderMap()
                                                .addCriticality(5)
                                        )
                                ).signature(getBytes(3, 64)),
                        new COSESignature()
                                .headers(new Headers()
                                        ._protected(new ProtectedHeaderMap())
                                        .unprotected(new HeaderMap()
                                                .addCriticality(8)
                                                .algorithmId(3)
                                        )
                                ).signature(getBytes(5, 64))

                ));

        HeaderMap upHeaderMap = new HeaderMap()
                .algorithmId(24)
                .addCriticality("criticality-2")
                .contentType(-2000)
                .keyId(keyId)
                .initVector(initVector)
                .partialInitVector(partialInitVector)
                .counterSignature(List.of(
                        new COSESignature()
                                .headers(new Headers()
                                        ._protected(new ProtectedHeaderMap())
                                        .unprotected(new HeaderMap()
                                                .addCriticality(20)
                                        )
                                )
                                .signature(getBytes(3, 64)),
                        new COSESignature()
                                .headers(new Headers()
                                        ._protected(new ProtectedHeaderMap())
                                        .unprotected(new HeaderMap()
                                                .addCriticality(8)
                                                .algorithmId(3)
                                        )
                                ).signature(getBytes(5, 64))

                ))
                .addOtherHeader(-100l, new UnicodeString("some header value"))
                .addOtherHeader("key1", new ByteString(new byte[]{7, 8, 9}));

        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap(pHeaderMap))
                .unprotected(upHeaderMap);

        byte[] payload = "Hello World".getBytes();
        COSESignBuilder coseSignBuilder = new COSESignBuilder(headers, payload, true)
                .hashed(true);

        COSESign coseSign = coseSignBuilder.build(getSignatures(coseSignBuilder));
        String serHex = HexUtil.encodeHexString(coseSign.serializeAsBytes());
        System.out.println(serHex);

        //This hex is the result from message-signing rust impl.
        String expected = "8458baa7010e02816d637269746963616c6974792d31033903e704430102030543040506064304050607828340a10281055840030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303038340a20103028108584005050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505aa01181802816d637269746963616c6974792d32033907cf04430102030543040506064304050607828340a10281145840030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303038340a20103028108584005050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505386371736f6d65206865616465722076616c7565646b6579314307080966686173686564f5f6828340a238c77819616e6f74686572206164646974696f6e616c20686561646572646b6579316a6b6579312076616c7565584026cc5be8ffdab97d527384871e3d448877502349544bcd37c7e3fce789ebfa7cf4867577117bad4129db0da702899662ef227c60fbe3bd87008be8082118f3068340a239018f781a616e6f74686572206164646974696f6e616c2068656164657232646b6579326a6b6579322076616c7565584021c9ba0f90c4970e931ab9f3dc9262128396f9a15a2022e09e1e906d13dd7ed6dbb036427ddefd95d4df5466b10355e4b90e382724d82d35457a7f33d2231402";
        COSESign coseSign2 = COSESign.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serHex)).get(0));

        assertThat(serHex).isEqualTo(expected);
        assertThat(coseSign2).isEqualTo(coseSign);

        //Added hashed key, so that assert is ok. hashed key is added by the lib
        upHeaderMap.otherHeaders().put("hashed", SimpleValue.TRUE);
        assertThat(coseSign2.headers().unprotected()).isEqualTo(upHeaderMap);
    }

    private List<COSESignature> getSignatures(COSESignBuilder coseSignBuilder) {
        SigStructure sigStructure = coseSignBuilder.makeDataToSign();
        SigningProvider signingProvider = Configuration.INSTANCE.getSigningProvider();
        byte[] signedSigStructure1 =
                signingProvider.signExtended(sigStructure.serializeAsBytes(), account1.privateKeyBytes(), account1.publicKeyBytes());
        byte[] signedSigStructure2 =
                signingProvider.signExtended(sigStructure.serializeAsBytes(), account2.privateKeyBytes(), account2.publicKeyBytes());

        COSESignature signature1 = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap())
                        .unprotected(new HeaderMap()
                                .addOtherHeader(-200, new UnicodeString("another additional header"))
                                .addOtherHeader("key1", new UnicodeString("key1 value"))
                        ))
                .signature(signedSigStructure1);

        COSESignature signature2 = new COSESignature()
                .headers(new Headers()
                        ._protected(new ProtectedHeaderMap())
                        .unprotected(new HeaderMap()
                                .addOtherHeader(-400, new UnicodeString("another additional header2"))
                                .addOtherHeader("key2", new UnicodeString("key2 value"))
                        ))
                .signature(signedSigStructure2);

        return List.of(signature1, signature2);
    }
}
