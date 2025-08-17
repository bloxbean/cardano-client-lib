package com.bloxbean.cardano.client.dsl.groovy


import spock.lang.Specification

import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.transaction

class VariableResolutionTest extends Specification {

    def "test variable resolution from test scope"() {
        given: "variables defined in test scope"
        def senderAddress = "addr1_test_sender"
        def receiverAddress = "addr1_test_receiver"

        when: "using variables directly in transaction"
        def tx = transaction {
            from senderAddress      // Should resolve from test scope, not propertyMissing
            send 5.ada to receiverAddress
        }

        then: "transaction should be created successfully"
        tx != null
        tx.unwrap() != null

        and: "variables should be properly resolved without ${} wrapping"
        def yaml = tx.toYaml()
        yaml.contains(senderAddress)
        yaml.contains(receiverAddress)
        !yaml.contains('${senderAddress}')  // Should NOT be wrapped
        !yaml.contains('${receiverAddress}')
    }

    def "test propertyMissing for undefined variables"() {
        when: "using undefined variables"
        def tx = transaction {
            from unknownVariable  // Should trigger propertyMissing
            send 5.ada to anotherUnknownVar
        }

        then: "variables should be wrapped with ${} syntax"
        def yaml = tx.toYaml()
        yaml.contains('${unknownVariable}')
        yaml.contains('${anotherUnknownVar}')
    }

    def "test address aliases resolution"() {
        when: "using address aliases"
        def tx = transaction {
            variables {
                treasury = "addr1_treasury_real"
                employee = "addr1_employee_real"
            }

            from treasury  // Should resolve from aliases
            send 5.ada to employee
        }

        then: "aliases should be resolved to actual addresses"
        def yaml = tx.toYaml()
        yaml.contains("addr1_treasury_real")
        yaml.contains("addr1_employee_real")
    }
}
