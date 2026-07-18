package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.quicktx.script.DefaultScriptRegistry;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptRegistryAttachmentIT {

    @Test
    void yamlPlan_build_resolvesScriptRegistryAttachments() throws Exception {
        String sender = new Account().baseAddress();
        String receiver = new Account().baseAddress();
        PlutusV2Script validator = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        ScriptPubkey nativeScript = ScriptPubkey.createWithNewKey()._1;

        String yaml = """
                version: 1.0
                transaction:
                  - tx:
                      from: %s
                      intents:
                        - type: payment
                          address: %s
                          amounts:
                            - unit: lovelace
                              quantity: 5000000
                      scripts:
                        - type: validator
                          role: mint
                          script_ref: validator://mint
                        - type: native_script
                          script_hash: %s
                """.formatted(sender, receiver, nativeScript.getPolicyId());

        TxPlan plan = TxPlan.from(yaml);
        Transaction transaction = new QuickTxBuilder(
                new FixedUtxoSupplier(sender),
                protocolParamsSupplier(),
                new NoopTransactionProcessor())
                .compose(plan)
                .withScriptRegistry(new DefaultScriptRegistry()
                        .addPlutusScript("validator://mint", validator)
                        .addNativeScript("native://policy", nativeScript))
                .build();

        assertThat(transaction.getWitnessSet().getPlutusV2Scripts()).contains(validator);
        assertThat(transaction.getWitnessSet().getNativeScripts()).contains(nativeScript);
    }

    private ProtocolParamsSupplier protocolParamsSupplier() {
        return () -> {
            try {
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("protocol-params.json");
                if (inputStream != null) {
                    return new ObjectMapper().readValue(inputStream, ProtocolParams.class);
                }

                return new ObjectMapper().readValue(new File("src/test/resources/protocol-params.json"), ProtocolParams.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static class FixedUtxoSupplier implements UtxoSupplier {
        private final String address;

        private FixedUtxoSupplier(String address) {
            this.address = address;
        }

        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            return List.of(Utxo.builder()
                    .address(this.address)
                    .txHash("5c6e2d88f7eeff25871e3572fdb994df65170aa406b211652537ee0c2c360a3f")
                    .outputIndex(0)
                    .amount(List.of(Amount.ada(100)))
                    .build());
        }

        @Override
        public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
            return Optional.empty();
        }
    }

    private static class NoopTransactionProcessor implements TransactionProcessor {
        @Override
        @SuppressWarnings("unchecked")
        public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
            return Result.success("").withValue(Collections.emptyList());
        }

        @Override
        public Result<String> submitTransaction(byte[] cborData) throws ApiException {
            return Result.success("txhash");
        }
    }
}
