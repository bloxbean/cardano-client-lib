package com.bloxbean.cardano.client.backend.api;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.ogmios.OgmiosBackendService;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.util.HexUtil;

public class BaseITTest {

    protected String BLOCKFROST = "blockfrost";
    protected String KOIOS = "koios";
    protected String OGMIOS = "ogmios";
    //    protected String CARDANO_GQL = "cardano-gql";
    protected String backendType = BLOCKFROST;

    public BackendService getBackendService() {
        if (BLOCKFROST.equals(backendType)) {
            String bfProjectId = System.getProperty("BF_PROJECT_ID");
            if (bfProjectId == null || bfProjectId.isEmpty()) {
                bfProjectId = System.getenv("BF_PROJECT_ID");
            }
            return new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, bfProjectId);
        } else if (KOIOS.equals(backendType)) {
            return new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL);
        } else if (OGMIOS.equals(backendType)) {
            return new OgmiosBackendService(com.bloxbean.cardano.client.backend.ogmios.Constants.OGMIOS_DANDELION_TESTNET_URL);
//        } else if (CARDANO_GQL.equals(backendType)) {
//            return new GqlBackendService(com.bloxbean.cardano.client.backend.gql.Constants.DANDELION_TESTNET_GQL_URL);
        } else
            return null;
    }

    protected PlutusV2Script getPlutusScript(String aikenCompileCode) {
        //Do double encoding for aiken compileCode
        ByteString bs = new ByteString(HexUtil.decodeHexString(aikenCompileCode));
        try {
            String cborHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(bs));
            return PlutusV2Script.builder()
                    .cborHex(cborHex)
                    .build();
        } catch (CborException e) {
            throw new RuntimeException(e);
        }
    }
}
