package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TupleInfoTest {

    @Nested
    class Accessors {
        @Test
        void firstAccessor() {
            assertThat(TupleInfo.PAIR.accessor(0)).isEqualTo("getFirst");
        }

        @Test
        void fifthAccessor() {
            assertThat(TupleInfo.QUINTET.accessor(4)).isEqualTo("getFifth");
        }

        @Test
        void ordinalNames() {
            assertThat(TupleInfo.TRIPLE.ordinal(0)).isEqualTo("first");
            assertThat(TupleInfo.TRIPLE.ordinal(2)).isEqualTo("third");
        }
    }

    @Nested
    class FromType {
        @Test
        void pairType() {
            TupleInfo info = TupleInfo.fromType(Type.PAIR);
            assertThat(info).isEqualTo(TupleInfo.PAIR);
            assertThat(info.arity()).isEqualTo(2);
            assertThat(info.tupleClass()).isEqualTo(Pair.class);
        }

        @Test
        void quintetType() {
            TupleInfo info = TupleInfo.fromType(Type.QUINTET);
            assertThat(info).isEqualTo(TupleInfo.QUINTET);
            assertThat(info.arity()).isEqualTo(5);
            assertThat(info.tupleClass()).isEqualTo(Quintet.class);
        }

        @Test
        void nonTupleTypeThrows() {
            assertThatThrownBy(() -> TupleInfo.fromType(Type.INTEGER))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class IsTupleType {
        @Test
        void tupleTypesReturnTrue() {
            assertThat(TupleInfo.isTupleType(Type.PAIR)).isTrue();
            assertThat(TupleInfo.isTupleType(Type.TRIPLE)).isTrue();
            assertThat(TupleInfo.isTupleType(Type.QUARTET)).isTrue();
            assertThat(TupleInfo.isTupleType(Type.QUINTET)).isTrue();
        }

        @Test
        void nonTupleTypesReturnFalse() {
            assertThat(TupleInfo.isTupleType(Type.INTEGER)).isFalse();
            assertThat(TupleInfo.isTupleType(Type.LIST)).isFalse();
        }
    }
}
