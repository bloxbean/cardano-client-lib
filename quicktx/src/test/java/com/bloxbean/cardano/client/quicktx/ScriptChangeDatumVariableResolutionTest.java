package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptChangeDatumVariableResolutionTest {

    @Test
    void resolves_variables_for_change_address_and_change_datum() throws Exception {
        String addrVar = "addr_test1vqxyzvar";
        String datumHex = BigIntPlutusData.of(77).serializeToHex();

        String yaml = "version: '1.0'\n" +
                "variables:\n" +
                "  ca: " + addrVar + "\n" +
                "  dh: " + datumHex + "\n" +
                "transaction:\n" +
                "  - scriptTx:\n" +
                "      change_address: ${ca}\n" +
                "      change_datum: ${dh}\n" +
                "      intentions: []\n";

        ScriptTx tx = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(tx.getPublicChangeAddress()).isEqualTo(addrVar);
        assertThat(tx.getChangeDatumHex()).isEqualTo(datumHex);
        assertThat(tx.getChangeDatumHash()).isNull();
    }

    @Test
    void resolves_variables_for_change_address_and_change_datum_hash() {
        String addrVar = "addr_test1vqpqrhash";
        String datumHash = "9e1199a988ba72ffd6e9c269cadb3b25b8e4acff2e3dce4aef3793110255fc10";

        String yaml = "version: '1.0'\n" +
                "variables:\n" +
                "  ca: " + addrVar + "\n" +
                "  dh: " + datumHash + "\n" +
                "transaction:\n" +
                "  - scriptTx:\n" +
                "      change_address: ${ca}\n" +
                "      change_datum_hash: ${dh}\n" +
                "      intentions: []\n";

        ScriptTx tx = (ScriptTx) TxPlan.fromYaml(yaml).get(0);
        assertThat(tx.getPublicChangeAddress()).isEqualTo(addrVar);
        assertThat(tx.getChangeDatumHash()).isEqualTo(datumHash);
        assertThat(tx.getChangeDatumHex()).isNull();
    }
}

