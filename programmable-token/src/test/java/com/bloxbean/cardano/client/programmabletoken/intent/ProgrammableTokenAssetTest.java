package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Asset names are validated where the plan is authored, naming the operation and the entry. */
class ProgrammableTokenAssetTest {

    private static ProgrammableMintIntent mintOf(ProgrammableTokenAsset asset) {
        return ProgrammableMintIntent.builder()
                .policy(ProgrammableTokenPolicyRef.policyId("aa".repeat(28)))
                .receiver("addr_test1owner")
                .assets(List.of(asset))
                .issuanceRedeemer(PlutusDataValue.of(BigIntPlutusData.of(0)))
                .build();
    }

    @Test
    void invalidNamesFailEarlyAndNameTheEntry() {
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset(null, BigInteger.ONE)).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mint assets[0]").hasMessageContaining("name is required");
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset("0x00ff", BigInteger.ONE)).validate())
                .hasMessageContaining("without a 0x prefix");
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset("abc", BigInteger.ONE)).validate())
                .hasMessageContaining("even-length hexadecimal");
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset("zz", BigInteger.ONE)).validate())
                .hasMessageContaining("even-length hexadecimal");
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset("00".repeat(33), BigInteger.ONE)).validate())
                .hasMessageContaining("33 bytes").hasMessageContaining("at most 32 bytes");
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset("00", null)).validate())
                .hasMessageContaining("quantity is required");
        assertThatThrownBy(() -> mintOf(new ProgrammableTokenAsset("00", BigInteger.ZERO)).validate())
                .hasMessageContaining("mint assets[0]").hasMessageContaining("positive");
    }

    @Test
    void validNamesRoundTripCanonically() {
        ProgrammableTokenAsset empty = new ProgrammableTokenAsset("", BigInteger.ONE);
        ProgrammableTokenAsset longest = new ProgrammableTokenAsset("AB".repeat(32), BigInteger.TEN);

        assertThatCode(() -> mintOf(empty).validate()).doesNotThrowAnyException();
        assertThatCode(() -> mintOf(longest).validate()).doesNotThrowAnyException();

        assertThat(longest.getName()).as("canonical lowercase").isEqualTo("ab".repeat(32));
        assertThat(HexUtil.encodeHexString(longest.toLedgerAsset().getNameAsBytes()))
                .isEqualTo("ab".repeat(32));
        assertThat(empty.toLedgerAsset().getNameAsBytes()).isEmpty();
        assertThat(ProgrammableTokenAsset.from(new Asset("0x" + "AB".repeat(32), BigInteger.TEN)))
                .isEqualTo(longest);
    }
}
