package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.api.model.Result;
import rest.koios.client.backend.api.network.NetworkService;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class KoiosNetworkService implements NetworkInfoService {

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private final NetworkService networkService;

    public KoiosNetworkService(NetworkService networkService) {
        this.networkService = networkService;
    }

    @Override
    public Result<Genesis> getNetworkInfo() {
        try {
            rest.koios.client.backend.api.base.Result<rest.koios.client.backend.api.network.model.Genesis> genesisInfoResult = networkService.getGenesisInfo();
            if (!genesisInfoResult.isSuccessful()) {
                return Result.error(genesisInfoResult.getResponse()).code(genesisInfoResult.getCode());
            }
            rest.koios.client.backend.api.network.model.Genesis gen = genesisInfoResult.getValue();
            Genesis genesis = new Genesis();
            genesis.setActiveSlotsCoefficient(new BigDecimal(gen.getActiveslotcoeff()));
            genesis.setUpdateQuorum(Integer.valueOf(gen.getUpdatequorum()));
            genesis.setMaxLovelaceSupply(gen.getMaxlovelacesupply());
            genesis.setNetworkMagic(Integer.valueOf(gen.getNetworkmagic()));
            genesis.setEpochLength(Integer.valueOf(gen.getEpochlength()));
            long systemStart = simpleDateFormat.parse(gen.getSystemstart()).getTime()/1000;
            genesis.setSystemStart((int) systemStart);
            genesis.setSlotsPerKesPeriod(Integer.valueOf(gen.getSlotsperkesperiod()));
            genesis.setSlotLength(Integer.valueOf(gen.getSlotlength()));
            genesis.setMaxKesEvolutions(Integer.valueOf(gen.getMaxkesrevolutions()));
            genesis.setSecurityParam(Integer.valueOf(gen.getSecurityparam()));
            return Result.success("OK").withValue(genesis).code(200);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            return Result.error(e.getMessage());
        } catch (ParseException e) {
            return Result.error("Failed to Parse System Start Date").code(500);
        }
    }
}
