package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDecoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataEncoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Integration test POJO exercising {@code @MetadataEncoder}/{@code @MetadataDecoder}
 * with both stateless and stateful (context-injected) adapters.
 * <p>
 * Scenarios:
 * <ul>
 *   <li>{@code upperName} — stateless encoder (UpperCaseEncoder, has no-arg constructor)</li>
 *   <li>{@code prefixedTag} — stateful encoder+decoder (PrefixEncoder/PrefixDecoder,
 *       require injected prefix string — analogous to NetworkType or a Spring bean)</li>
 *   <li>{@code encoderOnlyTag} — encoder-only with context injection, deserialization uses built-in</li>
 *   <li>{@code decoderOnlyTag} — decoder-only with context injection, serialization uses built-in</li>
 *   <li>{@code plainTag} — baseline: no encoder/decoder</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@MetadataType(label = 1905)
public class MetadataEncoderDecoder {

    @MetadataField(key = "test_id", required = true)
    private String testId;

    /** Simple stateless encoder: stores name in UPPER CASE on-chain. */
    @MetadataEncoder(UpperCaseEncoder.class)
    @MetadataField(key = "upper_name")
    private String upperName;

    /** Stateful encoder+decoder: prepends/strips a configurable prefix (injected context). */
    @MetadataEncoder(PrefixEncoder.class)
    @MetadataDecoder(PrefixDecoder.class)
    @MetadataField(key = "prefixed_tag")
    private String prefixedTag;

    /** Encoder-only with injected context: prefix is added, built-in String deserialization. */
    @MetadataEncoder(PrefixEncoder.class)
    @MetadataField(key = "enc_only_tag")
    private String encoderOnlyTag;

    /** Decoder-only with injected context: built-in serialization, prefix stripped on decode. */
    @MetadataDecoder(PrefixDecoder.class)
    @MetadataField(key = "dec_only_tag")
    private String decoderOnlyTag;

    /** Plain field — no encoder/decoder, baseline. */
    @MetadataField(key = "plain_tag")
    private String plainTag;
}
