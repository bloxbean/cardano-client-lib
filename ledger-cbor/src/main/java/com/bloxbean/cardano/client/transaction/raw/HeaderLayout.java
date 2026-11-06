package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.BOOL;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.HASH32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.KES_PERIOD;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.KES_VKEY;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.MAJOR_PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.MINOR_PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPERATIONAL_CERT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_EB_REFERENCES_ANNOUNCEMENT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_HASH32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.SEQUENCE_NUMBER;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.SIGNATURE;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VKEY;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VRF_CERT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VRF_VKEY;

/**
 * The layouts of a {@code header_body}, which {@link EraSpec} names per era, read from the pinned CDDL
 * ({@code eras/*}{@code /impl/cddl/data}). To Alonzo, the operational certificate and the protocol version are groups
 * in line and there are two VRF results; from Babbage, each group is an array of its own and there is one VRF result.
 * Dijkstra's header is the 10-item one that cardano-node 11.2.0 sends, or the 12-item Leios header of its CDDL, whose
 * version pair is {@code header_version_info} and which adds {@code block_body_contains_leios_cert} and
 * {@code eb_references_announcement / nil}; a header is read in the layout with as many items as it has.
 */
enum HeaderLayout {
    //   block_number, slot, prev_hash, issuer_vkey, vrf_vkey, VRF results, block_body_size, block_body_hash, groups
    FLAT_15(UINT, UINT, OPTIONAL_HASH32, VKEY, VRF_VKEY, VRF_CERT, VRF_CERT, UINT32, HASH32,
            KES_VKEY, SEQUENCE_NUMBER, KES_PERIOD, SIGNATURE, MAJOR_PROTOCOL_VERSION, MINOR_PROTOCOL_VERSION),
    NESTED_10(UINT, UINT, OPTIONAL_HASH32, VKEY, VRF_VKEY, VRF_CERT, UINT32, HASH32, OPERATIONAL_CERT,
            PROTOCOL_VERSION),
    /** Experimental: Dijkstra is not final; this may change in any release. */
    NESTED_12(UINT, UINT, OPTIONAL_HASH32, VKEY, VRF_VKEY, VRF_CERT, UINT32, HASH32, OPERATIONAL_CERT,
            PROTOCOL_VERSION, BOOL, OPTIONAL_EB_REFERENCES_ANNOUNCEMENT);

    final RecordSpec body;

    HeaderLayout(FieldType... items) {
        this.body = RecordSpec.positional("header_body", items);
    }

    /**
     * @param body    a header body as encoded
     * @param layouts the layouts it may have
     * @return the layout with as many items as the body has
     */
    static HeaderLayout ofShape(CborSpan body, List<HeaderLayout> layouts) {
        int items = RawItems.array(body, "header_body").size();
        for (HeaderLayout layout : layouts) {
            if (layout.body.arity() == items)
                return layout;
        }
        String arities = layouts.stream().map(layout -> String.valueOf(layout.body.arity()))
                .collect(Collectors.joining(" or "));
        throw RawTx.error("A header_body has " + arities + " items, found " + items, body);
    }
}
