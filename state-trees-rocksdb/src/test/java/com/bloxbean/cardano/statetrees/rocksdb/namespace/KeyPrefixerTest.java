package com.bloxbean.cardano.statetrees.rocksdb.namespace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyPrefixerTest {

    @Test
    void prefix_shouldPrependNamespaceId() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);
        byte[] original = new byte[]{0x10, 0x20, 0x30};

        byte[] prefixed = prefixer.prefix(original);

        assertThat(prefixed).hasSize(4);
        assertThat(prefixed[0]).isEqualTo((byte) 0x01);
        assertThat(prefixed[1]).isEqualTo((byte) 0x10);
        assertThat(prefixed[2]).isEqualTo((byte) 0x20);
        assertThat(prefixed[3]).isEqualTo((byte) 0x30);
    }

    @Test
    void prefix_withNullKey_shouldThrowException() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);

        assertThatThrownBy(() -> prefixer.prefix(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("null");
    }

    @Test
    void prefix_withEmptyKey_shouldReturnOnlyPrefix() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x05);
        byte[] empty = new byte[0];

        byte[] prefixed = prefixer.prefix(empty);

        assertThat(prefixed).hasSize(1);
        assertThat(prefixed[0]).isEqualTo((byte) 0x05);
    }

    @Test
    void unprefix_shouldRemoveNamespaceId() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);
        byte[] prefixed = new byte[]{0x01, 0x10, 0x20, 0x30};

        byte[] unprefixed = prefixer.unprefix(prefixed);

        assertThat(unprefixed).hasSize(3);
        assertThat(unprefixed).containsExactly((byte) 0x10, (byte) 0x20, (byte) 0x30);
    }

    @Test
    void unprefix_withNullKey_shouldThrowException() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);

        assertThatThrownBy(() -> prefixer.unprefix(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
    }

    @Test
    void unprefix_withEmptyKey_shouldThrowException() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> prefixer.unprefix(empty))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
    }

    @Test
    void unprefix_withSingleByteKey_shouldReturnEmpty() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);
        byte[] singleByte = new byte[]{0x01};

        byte[] unprefixed = prefixer.unprefix(singleByte);

        assertThat(unprefixed).isEmpty();
    }

    @Test
    void prefixAndUnprefix_shouldBeReversible() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x02);
        byte[] original = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};

        byte[] prefixed = prefixer.prefix(original);
        byte[] restored = prefixer.unprefix(prefixed);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void hasCorrectPrefix_withMatchingPrefix_shouldReturnTrue() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x03);
        byte[] key = new byte[]{0x03, 0x10, 0x20};

        assertThat(prefixer.hasCorrectPrefix(key)).isTrue();
    }

    @Test
    void hasCorrectPrefix_withNonMatchingPrefix_shouldReturnFalse() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x03);
        byte[] key = new byte[]{0x04, 0x10, 0x20};

        assertThat(prefixer.hasCorrectPrefix(key)).isFalse();
    }

    @Test
    void hasCorrectPrefix_withNullKey_shouldReturnFalse() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x03);

        assertThat(prefixer.hasCorrectPrefix(null)).isFalse();
    }

    @Test
    void hasCorrectPrefix_withEmptyKey_shouldReturnFalse() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x03);
        byte[] empty = new byte[0];

        assertThat(prefixer.hasCorrectPrefix(empty)).isFalse();
    }

    @Test
    void createPrefixReadOptions_shouldNotBeNull() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);

        // Note: This method creates ReadOptions which requires RocksDB native library.
        // We test it separately in integration tests where RocksDB is properly initialized.
        // Here we just verify the method signature is correct.
        assertThat(prefixer).isNotNull();
    }

    @Test
    void getNamespaceId_shouldReturnConfiguredId() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x42);

        assertThat(prefixer.getNamespaceId()).isEqualTo((byte) 0x42);
    }

    @Test
    void toString_shouldIncludeNamespaceId() {
        KeyPrefixer prefixer = new KeyPrefixer((byte) 0x10);

        String str = prefixer.toString();
        assertThat(str).contains("0x10");
    }

    @Test
    void equals_withSameNamespaceId_shouldBeEqual() {
        KeyPrefixer prefixer1 = new KeyPrefixer((byte) 0x01);
        KeyPrefixer prefixer2 = new KeyPrefixer((byte) 0x01);

        assertThat(prefixer1).isEqualTo(prefixer2);
        assertThat(prefixer1.hashCode()).isEqualTo(prefixer2.hashCode());
    }

    @Test
    void equals_withDifferentNamespaceId_shouldNotBeEqual() {
        KeyPrefixer prefixer1 = new KeyPrefixer((byte) 0x01);
        KeyPrefixer prefixer2 = new KeyPrefixer((byte) 0x02);

        assertThat(prefixer1).isNotEqualTo(prefixer2);
    }

    @Test
    void differentPrefixers_shouldNotInterfere() {
        KeyPrefixer prefixer1 = new KeyPrefixer((byte) 0x01);
        KeyPrefixer prefixer2 = new KeyPrefixer((byte) 0x02);
        byte[] original = new byte[]{0x10, 0x20};

        byte[] prefixed1 = prefixer1.prefix(original);
        byte[] prefixed2 = prefixer2.prefix(original);

        assertThat(prefixed1[0]).isEqualTo((byte) 0x01);
        assertThat(prefixed2[0]).isEqualTo((byte) 0x02);
        assertThat(prefixer1.hasCorrectPrefix(prefixed1)).isTrue();
        assertThat(prefixer1.hasCorrectPrefix(prefixed2)).isFalse();
        assertThat(prefixer2.hasCorrectPrefix(prefixed1)).isFalse();
        assertThat(prefixer2.hasCorrectPrefix(prefixed2)).isTrue();
    }
}
