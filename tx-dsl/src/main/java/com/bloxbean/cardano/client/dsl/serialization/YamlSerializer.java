package com.bloxbean.cardano.client.dsl.serialization;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.dsl.intention.DonationIntention;
import com.bloxbean.cardano.client.dsl.intention.MintingIntention;
import com.bloxbean.cardano.client.dsl.intention.PaymentIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeRegistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeDeregistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeDelegationIntention;
import com.bloxbean.cardano.client.dsl.intention.StakeWithdrawalIntention;
import com.bloxbean.cardano.client.dsl.intention.DRepRegistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.DRepDeregistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.DRepUpdateIntention;
import com.bloxbean.cardano.client.dsl.intention.GovernanceProposalIntention;
import com.bloxbean.cardano.client.dsl.intention.GovernanceVoteIntention;
import com.bloxbean.cardano.client.dsl.intention.VotingDelegationIntention;
import com.bloxbean.cardano.client.dsl.model.TransactionDocument;
import com.bloxbean.cardano.client.dsl.variable.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.InfoAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.HardForkInitiationAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.TreasuryWithdrawalsAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.NoConfidence;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.UpdateCommittee;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.NewConstitution;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.util.Map;

/**
 * Handles YAML serialization and deserialization of transaction documents.
 */
public class YamlSerializer {

    private static final ObjectMapper yamlMapper;

    static {
        YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        yamlMapper = new ObjectMapper(yamlFactory);

        // Configure to exclude null and empty fields
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Register custom serializers for better YAML readability
        SimpleModule customSerializationModule = new SimpleModule();
        customSerializationModule.addSerializer(Credential.class, new CredentialSerializer.Serializer());
        customSerializationModule.addDeserializer(Credential.class, new CredentialSerializer.Deserializer());
        customSerializationModule.addSerializer(Anchor.class, new AnchorSerializer.Serializer());
        customSerializationModule.addDeserializer(Anchor.class, new AnchorSerializer.Deserializer());
        customSerializationModule.addSerializer(byte[].class, new ByteArrayHexSerializer.Serializer());
        customSerializationModule.addDeserializer(byte[].class, new ByteArrayHexSerializer.Deserializer());
        
        yamlMapper.registerModule(customSerializationModule);

        // Configure polymorphic type handling for intentions
        yamlMapper.registerSubtypes(
            PaymentIntention.class,
            DonationIntention.class,
            MintingIntention.class,
            StakeRegistrationIntention.class,
            StakeDeregistrationIntention.class,
            StakeDelegationIntention.class,
            StakeWithdrawalIntention.class,
            DRepRegistrationIntention.class,
            DRepDeregistrationIntention.class,
            DRepUpdateIntention.class,
            GovernanceProposalIntention.class,
            GovernanceVoteIntention.class,
            VotingDelegationIntention.class
        );
        
        // Configure polymorphic type handling for GovAction subtypes
        yamlMapper.registerSubtypes(
            InfoAction.class,
            ParameterChangeAction.class,
            HardForkInitiationAction.class,
            TreasuryWithdrawalsAction.class,
            NoConfidence.class,
            UpdateCommittee.class,
            NewConstitution.class
        );
    }

    /**
     * Serialize a TransactionDocument to YAML string.
     *
     * @param document the document to serialize
     * @return YAML string representation
     */
    public static String serialize(TransactionDocument document) {
        try {
            return yamlMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to YAML", e);
        }
    }

    /**
     * Deserialize a YAML string to TransactionDocument.
     * This method first resolves any variable placeholders before Jackson deserialization.
     *
     * @param yaml the YAML string
     * @return deserialized TransactionDocument
     */
    public static TransactionDocument deserialize(String yaml) {
        return deserialize(yaml, null);
    }

    /**
     * Deserialize a YAML string to TransactionDocument with additional variable overrides.
     *
     * @param yaml the YAML string
     * @param additionalVariables additional variables to override YAML variables
     * @return deserialized TransactionDocument
     */
    public static TransactionDocument deserialize(String yaml, Map<String, Object> additionalVariables) {
        try {
            // Step 1: Extract variables from the YAML
            Map<String, Object> variables = VariableResolver.extractVariables(yaml);

            // Step 2: Apply additional variable overrides if provided
            if (additionalVariables != null) {
                variables.putAll(additionalVariables);
            }

            // Step 3: Resolve variable placeholders in the YAML content
            String resolvedYaml = VariableResolver.resolveVariables(yaml, variables);

            // Step 4: Deserialize the resolved YAML with Jackson
            return yamlMapper.readValue(resolvedYaml, TransactionDocument.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from YAML", e);
        }
    }
}
