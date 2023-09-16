package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.bloxbean.cardano.client.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import rest.koios.client.backend.api.script.ScriptService;
import rest.koios.client.backend.api.script.model.DatumInfo;
import rest.koios.client.backend.api.script.model.NativeScript;
import rest.koios.client.backend.api.script.model.PlutusScript;
import rest.koios.client.backend.api.transactions.TransactionsService;
import rest.koios.client.backend.api.transactions.model.TxInfo;
import rest.koios.client.backend.api.transactions.model.TxPlutusContract;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.util.List;

/**
 * Koios Script Service
 */
public class KoiosScriptService implements com.bloxbean.cardano.client.backend.api.ScriptService {

    /**
     * Script Service
     */
    private final ScriptService scriptService;
    private final TransactionsService transactionsService;

    /**
     * KoiosScriptService Constructor
     *
     * @param scriptService scriptService
     */
    public KoiosScriptService(ScriptService scriptService, TransactionsService transactionsService) {
        this.scriptService = scriptService;
        this.transactionsService = transactionsService;
    }

    @Override
    public Result<ScriptDatum> getScriptDatum(String datumHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<DatumInfo>> datumInfoListResult = scriptService.getDatumInformation(List.of(datumHash), Options.EMPTY);
            if (!datumInfoListResult.isSuccessful()) {
                return Result.error(datumInfoListResult.getResponse()).code(datumInfoListResult.getCode());
            }
            if (datumInfoListResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToScriptDatum(datumInfoListResult.getValue().get(0));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<ScriptDatum> convertToScriptDatum(DatumInfo datumInfo) {
        ScriptDatum scriptDatum = new ScriptDatum();
        scriptDatum.setJsonValue(datumInfo.getValue());
        return Result.success("OK").withValue(scriptDatum).code(200);
    }

    @Override
    public Result<ScriptDatumCbor> getScriptDatumCbor(String datumHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<DatumInfo>> datumInfoListResult = scriptService.getDatumInformation(List.of(datumHash), Options.EMPTY);
            if (!datumInfoListResult.isSuccessful()) {
                return Result.error(datumInfoListResult.getResponse()).code(datumInfoListResult.getCode());
            }
            if (datumInfoListResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            ScriptDatumCbor scriptDatumCbor = new ScriptDatumCbor();
            scriptDatumCbor.setCbor(datumInfoListResult.getValue().get(0).getBytes());
            return Result.success("OK").withValue(scriptDatumCbor).code(200);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<JsonNode> getNativeScriptJson(String scriptHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<NativeScript>> nativeScriptListResult =
                    scriptService.getNativeScriptList(Options.builder()
                            .option(Filter.of("script_hash", FilterType.EQ, scriptHash))
                            .build());
            if (!nativeScriptListResult.isSuccessful()) {
                return Result.error(nativeScriptListResult.getResponse()).code(nativeScriptListResult.getCode());
            }
            if (nativeScriptListResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return Result.success("OK").withValue(nativeScriptListResult.getValue().get(0).getScript()).code(200);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<String> getPlutusScriptCbor(String scriptHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<PlutusScript>> plutusScriptListResult =
                    scriptService.getPlutusScriptList(Options.builder()
                            .option(Filter.of("script_hash", FilterType.EQ, scriptHash))
                            .build());
            if (!plutusScriptListResult.isSuccessful()) {
                return Result.error(plutusScriptListResult.getResponse()).code(plutusScriptListResult.getCode());
            } else if (plutusScriptListResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            rest.koios.client.backend.api.base.Result<TxInfo> txInfoResult = transactionsService
                    .getTransactionInformation(plutusScriptListResult.getValue().get(0).getCreationTxHash());
            if (!txInfoResult.isSuccessful()) {
                return Result.error(txInfoResult.getResponse()).code(txInfoResult.getCode());
            } else if (txInfoResult.getValue() == null) {
                return Result.error("Not Found").code(404);
            }
            String txPlutusContractV1Cbor = txInfoResult.getValue().getPlutusContracts().stream()
                    .filter(plutusContract -> plutusContract.getScriptHash().equals(scriptHash)).findFirst()
                    .map(TxPlutusContract::getBytecode)
                    .orElse(null);
            if (!StringUtils.isEmpty(txPlutusContractV1Cbor)) {
                return Result.success("OK").withValue(txPlutusContractV1Cbor).code(200);
            }
            String txPlutusContractV2Cbor = txInfoResult.getValue().getOutputs().stream()
                    .filter(txIO -> txIO.getReferenceScript() != null &&
                            txIO.getReferenceScript().getHash().equals(scriptHash)).findFirst()
                    .map(txIO -> txIO.getReferenceScript().getBytes())
                    .orElse(null);
            if (!StringUtils.isEmpty(txPlutusContractV2Cbor)) {
                return Result.success("OK").withValue(txPlutusContractV2Cbor).code(200);
            }
            return Result.error("Not Found").code(404);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }
}
