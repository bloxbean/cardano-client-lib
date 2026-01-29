package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValidatorProcessor}.
 */
public class ValidatorProcessorTest {

    private ValidatorProcessor validatorProcessor;

    @BeforeEach
    void setUp() {
        Blueprint annotation = mock(Blueprint.class);
        ExtendWith extendWith = mock(ExtendWith.class);
        ProcessingEnvironment processingEnv = mock(ProcessingEnvironment.class);
        GeneratedTypesRegistry generatedTypesRegistry = mock(GeneratedTypesRegistry.class);
        SharedTypeLookup sharedTypeLookup = mock(SharedTypeLookup.class);

        validatorProcessor = new ValidatorProcessor(annotation, extendWith, processingEnv,
                generatedTypesRegistry, sharedTypeLookup);
    }

    @Nested
    @DisplayName("calculateValidatorName() - Validator Name Calculation")
    class CalculateValidatorName {

        @Test
        @DisplayName("should use last token for simple 2-part titles")
        void simpleTitle_shouldUseLastToken() {
            // Simple 2-part title (package.name)
            String title = "cardano_aftermarket.beacon_script";

            String result = validatorProcessor.calculateValidatorName(title);

            assertThat(result).isEqualTo("beacon_script");
        }

        @Test
        @DisplayName("should include middle tokens for 3-part titles to avoid collisions")
        void threePartTitle_shouldIncludeMiddleTokens() {
            // Should use tokens after first to create unique names
            String title1 = "config.config_mint_validator.mint";
            String title2 = "power_users.mint.mint";
            String title3 = "users.mint.mint";

            String result1 = validatorProcessor.calculateValidatorName(title1);
            String result2 = validatorProcessor.calculateValidatorName(title2);
            String result3 = validatorProcessor.calculateValidatorName(title3);

            assertThat(result1).isEqualTo("config_mint_validator_mint");
            assertThat(result2).isEqualTo("mint_mint");
            assertThat(result3).isEqualTo("mint_mint");
            // Note: title2 and title3 produce the same name, but they're in different packages
            // (power_users vs users), so there's no actual collision
        }

        @Test
        @DisplayName("should include middle tokens for 'else' suffix validators")
        void elseSuffix_shouldIncludeMiddleTokens() {
            String title1 = "config.config_mint_validator.else";
            String title2 = "power_users.power_users_validator.else";

            String result1 = validatorProcessor.calculateValidatorName(title1);
            String result2 = validatorProcessor.calculateValidatorName(title2);

            assertThat(result1).isEqualTo("config_mint_validator_else");
            assertThat(result2).isEqualTo("power_users_validator_else");
            // ✅ Unique names - no collision!
        }

        @Test
        @DisplayName("should include all tokens after first for 4+ part titles")
        void fourPartTitle_shouldIncludeAllAfterFirst() {
            String title = "module.sub.validator.mint";

            String result = validatorProcessor.calculateValidatorName(title);

            assertThat(result).isEqualTo("sub_validator_mint");
        }

        @Test
        @DisplayName("should handle single token titles")
        void singleToken_shouldReturnAsIs() {
            // Edge case: no dots
            String title = "validator";

            String result = validatorProcessor.calculateValidatorName(title);

            assertThat(result).isEqualTo("validator");
        }

        @Test
        @DisplayName("should handle 'spend' suffix validators")
        void spendSuffix_shouldIncludeMiddleTokens() {
            String title1 = "config.config_spend_validator.spend";
            String title2 = "global_state.global_state_spend_validator.spend";

            String result1 = validatorProcessor.calculateValidatorName(title1);
            String result2 = validatorProcessor.calculateValidatorName(title2);

            assertThat(result1).isEqualTo("config_spend_validator_spend");
            assertThat(result2).isEqualTo("global_state_spend_validator_spend");
            // ✅ Unique names!
        }

        @Nested
        @DisplayName("Real-World Blueprint Examples")
        class RealWorldExamples {

            @Test
            @DisplayName("aftermarket.json validators should work correctly")
            void aftermarketBlueprint() {
                assertThat(validatorProcessor.calculateValidatorName("cardano_aftermarket.aftermarket_observer_script"))
                        .isEqualTo("aftermarket_observer_script");

                assertThat(validatorProcessor.calculateValidatorName("cardano_aftermarket.aftermarket_script"))
                        .isEqualTo("aftermarket_script");

                assertThat(validatorProcessor.calculateValidatorName("cardano_aftermarket.beacon_script"))
                        .isEqualTo("beacon_script");
            }

            @Test
            @DisplayName("cip113Token.json validators should avoid collisions")
            void cip113Blueprint() {
                // Config validators
                assertThat(validatorProcessor.calculateValidatorName("config.config_mint_validator.mint"))
                        .isEqualTo("config_mint_validator_mint");
                assertThat(validatorProcessor.calculateValidatorName("config.config_mint_validator.else"))
                        .isEqualTo("config_mint_validator_else");
                assertThat(validatorProcessor.calculateValidatorName("config.config_spend_validator.spend"))
                        .isEqualTo("config_spend_validator_spend");

                // Global state validators
                assertThat(validatorProcessor.calculateValidatorName("global_state.global_state_mint_validator.mint"))
                        .isEqualTo("global_state_mint_validator_mint");
                assertThat(validatorProcessor.calculateValidatorName("global_state.global_state_spend_validator.spend"))
                        .isEqualTo("global_state_spend_validator_spend");

                // Power users validators
                assertThat(validatorProcessor.calculateValidatorName("power_users.mint.mint"))
                        .isEqualTo("mint_mint");
                assertThat(validatorProcessor.calculateValidatorName("power_users.power_users_validator.spend"))
                        .isEqualTo("power_users_validator_spend");
                assertThat(validatorProcessor.calculateValidatorName("power_users.power_users_validator.else"))
                        .isEqualTo("power_users_validator_else");

                // Users validators
                assertThat(validatorProcessor.calculateValidatorName("users.mint.mint"))
                        .isEqualTo("mint_mint");
                assertThat(validatorProcessor.calculateValidatorName("users.users_validator.spend"))
                        .isEqualTo("users_validator_spend");

                // Minting logic validators
                assertThat(validatorProcessor.calculateValidatorName("minting_logic_script.minting_logic_validator.withdraw"))
                        .isEqualTo("minting_logic_validator_withdraw");

                // Transfer logic validators
                assertThat(validatorProcessor.calculateValidatorName("transfer_logic_script.transfer_logic_validator.withdraw"))
                        .isEqualTo("transfer_logic_validator_withdraw");
                assertThat(validatorProcessor.calculateValidatorName("third_party_transfer_logic_script.third_party_transfer_logic_validator.withdraw"))
                        .isEqualTo("third_party_transfer_logic_validator_withdraw");

                // All names are unique within their context!
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCases {

            @Test
            @DisplayName("should handle empty segments in title")
            void emptySegments() {
                // If blueprint somehow has empty segments (shouldn't happen in practice)
                String title = "module..validator";

                String result = validatorProcessor.calculateValidatorName(title);

                // Should still work, though may produce underscore prefix/suffix
                assertThat(result).isEqualTo("_validator");
            }

            @Test
            @DisplayName("should handle very long titles with many segments")
            void longTitles() {
                String title = "a.b.c.d.e.f.g.h";

                String result = validatorProcessor.calculateValidatorName(title);

                assertThat(result).isEqualTo("b_c_d_e_f_g_h");
            }
        }
    }
}
