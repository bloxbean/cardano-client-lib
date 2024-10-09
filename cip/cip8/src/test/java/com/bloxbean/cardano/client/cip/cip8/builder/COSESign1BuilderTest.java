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
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class COSESign1BuilderTest extends COSEBaseTest {

    String mnemonic = "nice orient enjoy teach jump office alert inquiry apart unaware seat tumble unveil device have bullet morning eyebrow time image embody divide version uniform";
    Account account = new Account(Networks.testnet(), mnemonic);

    @Test
    void buildCOSESign1() throws CborException {
        HeaderMap pHeaderMap = new HeaderMap()
                .algorithmId(14)
                .contentType(-1000);

        HeaderMap unpHeadermap = new HeaderMap()
                .addOtherHeader(-100L, new UnicodeString("Some header value"));

        Headers headers = new Headers()
                ._protected(new ProtectedHeaderMap(pHeaderMap))
                .unprotected(unpHeadermap);

        byte[] payload = "Hello World".getBytes();
        System.out.println("Payload: " + HexUtil.encodeHexString(payload));
        COSESign1Builder coseSign1Builder = new COSESign1Builder(headers, payload, false)
                .hashed(true);

        SigStructure sigStructure = coseSign1Builder.makeDataToSign();
        byte[] signedSigStructure =
                Configuration.INSTANCE.getSigningProvider()
                        .signExtended(sigStructure.serializeAsBytes(), account.privateKeyBytes(), account.publicKeyBytes());

        COSESign1 coseSign1 = coseSign1Builder.build(signedSigStructure);
        String serHex = HexUtil.encodeHexString(coseSign1.serializeAsBytes());

        //This hex is the result from message-signing rust impl. (Check cose_sign1_builder.rs)
        String expected = "8447a2010e033903e7a2386371536f6d65206865616465722076616c756566686173686564f5581c19790463ef4ad09bdb724e3a6550c640593d4870f6e192ac8147f35d58400a810f4fef824d98bb3d08a93f32b2bffb236ecc87100142911605509b953701b0680ce347a13d54e6f626c1f368e69e422d75870db21f8c8ad9f1e40f51ca04";
        COSESign1 coseSign12 = COSESign1.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serHex)).get(0));

        System.out.println("Serialized Hex: " + serHex.length());
        System.out.println("Expected Hex: " + expected.length());

        assertThat(serHex).isEqualTo(expected);
        assertThat(coseSign12).isEqualTo(coseSign1);
    }

    @Test
    void buildCOSESign1_withPayLoadExTrue_additionalHeaders() throws CborException {
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
        COSESign1Builder coseSign1Builder = new COSESign1Builder(headers, payload, true)
                .hashed(true);

        SigStructure sigStructure = coseSign1Builder.makeDataToSign();
        byte[] signedSigStructure =
                Configuration.INSTANCE.getSigningProvider()
                        .signExtended(sigStructure.serializeAsBytes(), account.privateKeyBytes(), account.publicKeyBytes());

        COSESign1 coseSign1 = coseSign1Builder.build(signedSigStructure);
        String serHex = HexUtil.encodeHexString(coseSign1.serializeAsBytes());

        //This hex is the result from message-signing rust impl.
        String expected = "8458baa7010e02816d637269746963616c6974792d31033903e704430102030543040506064304050607828340a10281055840030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303038340a20103028108584005050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505aa01181802816d637269746963616c6974792d32033907cf04430102030543040506064304050607828340a10281145840030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303038340a20103028108584005050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505050505386371736f6d65206865616465722076616c7565646b6579314307080966686173686564f5f65840ba0e7cb56486b33c1fc3fb2730968cf46b9215bcf57dfdec15102cad72391b4ac2f5c6a23fe3e2545b6d0d2381fc5fbb090467e02f74d57eee8380b8cf9d1605";
        COSESign1 coseSign12 = COSESign1.deserialize(CborDecoder.decode(HexUtil.decodeHexString(serHex)).get(0));

        assertThat(serHex).isEqualTo(expected);
        assertThat(coseSign12).isEqualTo(coseSign1);

        //Added hashed key, so that assert is ok. hashed key is added by the lib
        upHeaderMap.otherHeaders().put("hashed", SimpleValue.TRUE);
        assertThat(coseSign12.headers().unprotected()).isEqualTo(upHeaderMap);
    }

}
