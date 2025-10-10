package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NativeScriptAttachmentIntentTest {

    @Test
    void attach_native_script_serializes_to_yaml() throws Exception {
        // Create a simple native script
        ScriptPubkey script = ScriptPubkey.createWithNewKey()._1;

        Tx tx = new Tx()
                .attachNativeScript(script)
                .from("addr_test1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqkn6z8c");

        String yaml = TxPlan.from(tx).toYaml();

        System.out.println(yaml);

        Tx deTx = (Tx) TxPlan.from(yaml).getTxs().get(0);

        assertThat(yaml).contains("type: native_script");
        assertThat(yaml).contains("script_hex:");

        assertThat(deTx.getIntentions()).isNotEmpty();
        assertThat(deTx.getIntentions().stream().anyMatch(i -> "native_script".equals(i.getType()))).isTrue();
    }
}
