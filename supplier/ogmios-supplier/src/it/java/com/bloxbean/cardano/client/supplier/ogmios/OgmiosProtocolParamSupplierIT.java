package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OgmiosProtocolParamSupplierIT extends OgmiosBaseTest{

    @Test
    public void testGetLatestProtocolParameters() throws Exception {
        ProtocolParams protocolParams = ogmiosProtocolParamSupplier.getProtocolParams();
        assertThat(protocolParams).isNotNull();
        assertThat(protocolParams.getPoolDeposit()).isEqualTo("500000000");
        assertEquals(protocolParams.getCollateralPercent().intValue(), 150);
        assertThat(protocolParams.getEMax()).isNotNull();
        assertThat(protocolParams.getNOpt()).isNotNull();
    }
}
