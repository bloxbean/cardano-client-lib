package com.bloxbean.cardano.statetrees.rocksdb.namespace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceOptionsTest {

    @Test
    void defaults_shouldReturnDefaultConfiguration() {
        NamespaceOptions opts = NamespaceOptions.defaults();

        assertThat(opts.columnFamilyPrefix()).isEmpty();
        assertThat(opts.keyPrefix()).isEqualTo((byte) 0x00);
        assertThat(opts.usesDefaultColumnFamily()).isTrue();
        assertThat(opts.usesDefaultKeyPrefix()).isTrue();
    }

    @Test
    void columnFamily_shouldSetCfPrefixWithDefaultKeyPrefix() {
        NamespaceOptions opts = NamespaceOptions.columnFamily("account");

        assertThat(opts.columnFamilyPrefix()).isEqualTo("account");
        assertThat(opts.keyPrefix()).isEqualTo((byte) 0x00);
        assertThat(opts.usesDefaultColumnFamily()).isFalse();
        assertThat(opts.usesDefaultKeyPrefix()).isTrue();
    }

    @Test
    void keyPrefix_shouldSetKeyPrefixWithDefaultCf() {
        NamespaceOptions opts = NamespaceOptions.keyPrefix((byte) 0x01);

        assertThat(opts.columnFamilyPrefix()).isEmpty();
        assertThat(opts.keyPrefix()).isEqualTo((byte) 0x01);
        assertThat(opts.usesDefaultColumnFamily()).isTrue();
        assertThat(opts.usesDefaultKeyPrefix()).isFalse();
    }

    @Test
    void both_shouldSetBothPrefixes() {
        NamespaceOptions opts = NamespaceOptions.both("shard", (byte) 0x05);

        assertThat(opts.columnFamilyPrefix()).isEqualTo("shard");
        assertThat(opts.keyPrefix()).isEqualTo((byte) 0x05);
        assertThat(opts.usesDefaultColumnFamily()).isFalse();
        assertThat(opts.usesDefaultKeyPrefix()).isFalse();
    }

    @Test
    void constructor_withNullCfPrefix_shouldUseEmptyString() {
        NamespaceOptions opts = new NamespaceOptions(null, (byte) 0x00);

        assertThat(opts.columnFamilyPrefix()).isEmpty();
        assertThat(opts.usesDefaultColumnFamily()).isTrue();
    }

    @Test
    void constructor_withReservedKeyPrefix_shouldThrowException() {
        assertThatThrownBy(() -> new NamespaceOptions("", (byte) 0xFF))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserved");
    }

    @Test
    void toString_shouldIncludeBothPrefixes() {
        NamespaceOptions opts = NamespaceOptions.both("test", (byte) 0x10);

        String str = opts.toString();
        assertThat(str).contains("test");
        assertThat(str).contains("0x10");
    }

    @Test
    void toString_withDefaultCf_shouldIndicateDefault() {
        NamespaceOptions opts = NamespaceOptions.defaults();

        String str = opts.toString();
        assertThat(str).contains("<default>");
        assertThat(str).contains("0x00");
    }

    @Test
    void equals_withSameValues_shouldBeEqual() {
        NamespaceOptions opts1 = NamespaceOptions.both("account", (byte) 0x01);
        NamespaceOptions opts2 = NamespaceOptions.both("account", (byte) 0x01);

        assertThat(opts1).isEqualTo(opts2);
        assertThat(opts1.hashCode()).isEqualTo(opts2.hashCode());
    }

    @Test
    void equals_withDifferentCfPrefix_shouldNotBeEqual() {
        NamespaceOptions opts1 = NamespaceOptions.columnFamily("account");
        NamespaceOptions opts2 = NamespaceOptions.columnFamily("storage");

        assertThat(opts1).isNotEqualTo(opts2);
    }

    @Test
    void equals_withDifferentKeyPrefix_shouldNotBeEqual() {
        NamespaceOptions opts1 = NamespaceOptions.keyPrefix((byte) 0x01);
        NamespaceOptions opts2 = NamespaceOptions.keyPrefix((byte) 0x02);

        assertThat(opts1).isNotEqualTo(opts2);
    }

    @Test
    void allKeyPrefixValues_exceptReserved_shouldBeValid() {
        // Test 0x00
        NamespaceOptions opts0 = NamespaceOptions.keyPrefix((byte) 0x00);
        assertThat(opts0.keyPrefix()).isEqualTo((byte) 0x00);

        // Test 0x01-0xFE
        for (int i = 0x01; i <= 0xFE; i++) {
            NamespaceOptions opts = NamespaceOptions.keyPrefix((byte) i);
            assertThat(opts.keyPrefix()).isEqualTo((byte) i);
        }

        // Test 0xFF (reserved) - should throw
        assertThatThrownBy(() -> NamespaceOptions.keyPrefix((byte) 0xFF))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
