package com.bloxbean.cardano.client.quicktx.signing;

import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SignerRegistryChainTest {

    @Test
    void resolveReturnsFirstMatchWithoutCallingLaterRegistries() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        SignerBinding binding = new StubBinding("first-match");

        SignerRegistry first = ref -> {
            firstCalls.incrementAndGet();
            return "account://alice".equals(ref) ? Optional.of(binding) : Optional.empty();
        };

        SignerRegistry second = ref -> {
            secondCalls.incrementAndGet();
            return Optional.empty();
        };

        SignerRegistryChain chain = new SignerRegistryChain()
                .add(first)
                .add(second);

        Optional<SignerBinding> resolved = chain.resolve("account://alice");

        assertThat(resolved).contains(binding);
        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(0);
    }

    @Test
    void resolveFallsThroughWhenEarlierRegistryDoesNotContainReference() {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        SignerBinding binding = new StubBinding("fallback");

        SignerRegistry first = ref -> {
            firstCalls.incrementAndGet();
            return Optional.empty();
        };

        SignerRegistry second = ref -> {
            secondCalls.incrementAndGet();
            return "account://bob".equals(ref) ? Optional.of(binding) : Optional.empty();
        };

        SignerRegistryChain chain = new SignerRegistryChain()
                .add(first)
                .add(second);

        Optional<SignerBinding> resolved = chain.resolve("account://bob");

        assertThat(resolved).contains(binding);
        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(1);
    }

    @Test
    void resolveReturnsEmptyWhenNoRegistryMatches() {
        SignerRegistryChain chain = new SignerRegistryChain();
        chain.add(ref -> Optional.empty());
        chain.add(ref -> Optional.empty());

        Optional<SignerBinding> resolved = chain.resolve("account://unknown");

        assertThat(resolved).isEmpty();
    }

    private static class StubBinding implements SignerBinding {
        private final String id;

        private StubBinding(String id) {
            this.id = id;
        }

        @Override
        public TxSigner signerFor(String scope) {
            return this::identity;
        }

        private Transaction identity(TxBuilderContext context, Transaction transaction) {
            return transaction;
        }

        @Override
        public Optional<com.bloxbean.cardano.hdwallet.Wallet> asWallet() {
            return Optional.empty();
        }

        @Override
        public Optional<String> preferredAddress() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return id;
        }
    }
}

