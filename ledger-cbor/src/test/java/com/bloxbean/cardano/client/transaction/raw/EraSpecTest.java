package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EraSpecTest {

    /** ADR 0003 I2: adding an {@link Era} constant without a row fails here. */
    @Test
    void everyEraHasARow() {
        for (Era era : Era.values())
            assertThat(EraSpec.of(era).era).isEqualTo(era);
    }

    /** ADR 0003 D3's table. {@code tools/cddl-table-check} holds it against the pinned CDDL. */
    @Test
    void everyEraNamesItsLayouts() {
        for (EraSpec row : List.of(EraSpec.SHELLEY, EraSpec.ALLEGRA, EraSpec.MARY))
            assertThat(row.blockLayout).isEqualTo(BlockLayout.PARALLEL_4);
        for (EraSpec row : List.of(EraSpec.ALONZO, EraSpec.BABBAGE, EraSpec.CONWAY))
            assertThat(row.blockLayout).isEqualTo(BlockLayout.PARALLEL_5);
        for (EraSpec row : List.of(EraSpec.SHELLEY, EraSpec.ALLEGRA, EraSpec.MARY, EraSpec.ALONZO))
            assertThat(row.headerLayout).isEqualTo(HeaderLayout.FLAT_15);
        for (EraSpec row : List.of(EraSpec.BABBAGE, EraSpec.CONWAY))
            assertThat(row.headerLayout).isEqualTo(HeaderLayout.NESTED_10);
    }

    @Test
    void theLayoutsAreTheirNames() {
        assertThat(BlockLayout.PARALLEL_4.items).isEqualTo(4);
        assertThat(BlockLayout.PARALLEL_4.invalidTransactions()).isFalse();
        assertThat(BlockLayout.PARALLEL_5.items).isEqualTo(5);
        assertThat(BlockLayout.PARALLEL_5.invalidTransactions()).isTrue();
        assertThat(HeaderLayout.FLAT_15.body.arity()).isEqualTo(15);
        assertThat(HeaderLayout.NESTED_10.body.arity()).isEqualTo(10);
    }

    @Test
    void newestIsTheLastRow() {
        assertThat(EraSpec.newest()).isEqualTo(Era.Conway);
    }

    @Test
    void everyEraIsFoundByItsIndex() {
        for (Era era : Era.values())
            assertThat(EraSpec.ofIndex(era.value).era).isEqualTo(era);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 8, 9, 1000, Long.MAX_VALUE, -1})
    void anIndexWithoutARowIsUnsupported(long index) {
        assertThatThrownBy(() -> EraSpec.ofIndex(index)).isInstanceOfSatisfying(UnsupportedEraException.class,
                e -> assertThat(e.eraIndex()).isEqualTo(index));
    }

    /** ADR 0003 I1: the build keeps the models out of this module, so the reader cannot import one. */
    @Test
    void noModelIsOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("com.bloxbean.cardano.client.transaction.spec.Transaction"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.bloxbean.cardano.client.plutus.spec.PlutusData"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
