package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoyaltyAddressUtilTest {

    // Real mainnet addresses for testing
    private static final String BASE_KEY_KEY =
            "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x";
    private static final String ENTERPRISE_KEY =
            "addr1vx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzers66hrl8";

    @Test
    void testBaseAddress_roundTrip() {
        Address original = new Address(BASE_KEY_KEY);
        ConstrPlutusData plutusData = RoyaltyAddressUtil.toPlutusData(original);

        // outer constr is alternative 0
        assertThat(plutusData.getAlternative()).isEqualTo(0);

        // reconstruct
        Address reconstructed = RoyaltyAddressUtil.fromPlutusData(plutusData, Networks.mainnet());
        assertThat(reconstructed.toBech32()).isEqualTo(original.toBech32());
    }

    @Test
    void testEnterpriseAddress_roundTrip() {
        Address original = new Address(ENTERPRISE_KEY);
        ConstrPlutusData plutusData = RoyaltyAddressUtil.toPlutusData(original);

        // stake option must be None (alternative 1)
        ConstrPlutusData stakeOption = (ConstrPlutusData) plutusData.getData().getPlutusDataList().get(1);
        assertThat(stakeOption.getAlternative()).isEqualTo(1);

        Address reconstructed = RoyaltyAddressUtil.fromPlutusData(plutusData, Networks.mainnet());
        assertThat(reconstructed.toBech32()).isEqualTo(original.toBech32());
    }

    @Test
    void testBaseAddress_stakeOptionIsSomeInline() {
        Address addr = new Address(BASE_KEY_KEY);
        ConstrPlutusData plutusData = RoyaltyAddressUtil.toPlutusData(addr);

        // stake option = Some(Inline(cred)) = Constr(0, [Constr(0, [cred])])
        ConstrPlutusData stakeOption = (ConstrPlutusData) plutusData.getData().getPlutusDataList().get(1);
        assertThat(stakeOption.getAlternative()).isEqualTo(0); // Some

        ConstrPlutusData referencedCred = (ConstrPlutusData) stakeOption.getData().getPlutusDataList().get(0);
        assertThat(referencedCred.getAlternative()).isEqualTo(0); // Inline

        // inner credential must have alternative 0 (VerificationKey)
        ConstrPlutusData innerCred = (ConstrPlutusData) referencedCred.getData().getPlutusDataList().get(0);
        assertThat(innerCred.getAlternative()).isEqualTo(0);
    }
}
