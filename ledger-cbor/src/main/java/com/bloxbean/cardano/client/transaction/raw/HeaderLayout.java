package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

import static com.bloxbean.cardano.client.transaction.raw.FieldType.HASH32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.KES_PERIOD;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.KES_VKEY;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.MAJOR_PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.MINOR_PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPERATIONAL_CERT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.OPTIONAL_HASH32;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.PROTOCOL_VERSION;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.SEQUENCE_NUMBER;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.SIGNATURE;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.UINT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VKEY;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VRF_CERT;
import static com.bloxbean.cardano.client.transaction.raw.FieldType.VRF_VKEY;

/**
 * The layouts of a {@code header_body}, which {@link EraSpec} names per era, read from the pinned CDDL
 * ({@code eras/*}{@code /impl/cddl/data}). To Alonzo, the operational certificate and the protocol version are groups
 * in line and there are two VRF results; from Babbage, each group is an array of its own and there is one VRF result.
 */
enum HeaderLayout {
    //   block_number, slot, prev_hash, issuer_vkey, vrf_vkey, VRF results, block_body_size, block_body_hash, groups
    FLAT_15(UINT, UINT, OPTIONAL_HASH32, VKEY, VRF_VKEY, VRF_CERT, VRF_CERT, UINT, HASH32,
            KES_VKEY, SEQUENCE_NUMBER, KES_PERIOD, SIGNATURE, MAJOR_PROTOCOL_VERSION, MINOR_PROTOCOL_VERSION),
    NESTED_10(UINT, UINT, OPTIONAL_HASH32, VKEY, VRF_VKEY, VRF_CERT, UINT, HASH32, OPERATIONAL_CERT, PROTOCOL_VERSION);

    final RecordSpec body;

    HeaderLayout(FieldType... items) {
        this.body = RecordSpec.positional("header_body", items);
    }

    /**
     * @param body a header body as encoded
     * @return the layout with as many items as the body has
     */
    static HeaderLayout ofShape(CborSpan body) {
        int items = RawItems.array(body, "header_body").size();
        for (HeaderLayout layout : values()) {
            if (layout.body.arity() == items)
                return layout;
        }
        throw RawTx.error("A header_body has 15 or 10 items, found " + items, body);
    }
}
