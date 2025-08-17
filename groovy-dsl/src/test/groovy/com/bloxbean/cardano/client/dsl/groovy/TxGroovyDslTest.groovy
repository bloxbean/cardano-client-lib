package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.api.model.Amount
import com.bloxbean.cardano.client.quicktx.Tx
import spock.lang.Specification

import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.transaction

class TxGroovyDslTest extends Specification {
    
    def "test simple payment with from and payTo"() {
        given:
        def senderAddr = "addr1_sender_address"
        def receiverAddr = "addr1_receiver_address"
        
        when:
        def txDsl = transaction {
            from senderAddr
            payTo receiverAddr, Amount.ada(5)
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
        tx instanceof Tx
    }
    
    def "test payment with closure syntax"() {
        given:
        def senderAddr = "addr1_sender"
        def receiverAddr = "addr1_receiver"
        
        when:
        def txDsl = transaction {
            from senderAddr
            pay {
                to receiverAddr
                amount Amount.ada(10)
            }
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test natural language send syntax"() {
        given:
        def senderAddr = "addr1_sender"
        def receiverAddr = "addr1_receiver"
        
        when:
        def txDsl = transaction {
            from senderAddr
            send Amount.ada(7) to receiverAddr
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test variables definition"() {
        when:
        def txDsl = transaction {
            from '${TREASURY}'
            send Amount.ada(5) to '${EMPLOYEE}'
            
            variables {
                TREASURY = "addr1_treasury"
                EMPLOYEE = "addr1_employee"
            }
        }
        
        then:
        txDsl != null
        def yaml = txDsl.toYaml()
        yaml != null
        yaml.contains('${TREASURY}')
        yaml.contains('${EMPLOYEE}')
    }
    
    def "test withVariable method"() {
        when:
        def txDsl = transaction {
            from '${SENDER}'
            payTo '${RECEIVER}', Amount.ada(3)
        }
        .withVariable("SENDER", "addr1_sender")
        .withVariable("RECEIVER", "addr1_receiver")
        
        then:
        txDsl != null
        def yaml = txDsl.toYaml()
        yaml.contains('SENDER')
        yaml.contains('RECEIVER')
    }
    
    def "test address aliases with propertyMissing"() {
        when:
        def txDsl = transaction {
            variables {
                treasury = "addr1_treasury_real"
                alice = "addr1_alice_real"
            }
            
            from treasury
            send Amount.ada(5) to alice
        }
        
        then:
        txDsl != null
        // The aliases should be resolved to actual addresses
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test amount extensions with ada property"() {
        when:
        def txDsl = transaction {
            from "addr1_sender"
            send 5.ada to "addr1_receiver"
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test amount extensions with lovelace"() {
        when:
        def txDsl = transaction {
            from "addr1_sender"
            send 5000000.lovelace to "addr1_receiver"
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test asset creation with unit"() {
        given:
        // Use a valid hex policy ID (56 hex chars for a real policy)
        def policyId = "1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        // Token name hex encoded (MyToken in hex)
        def tokenNameHex = "4d79546f6b656e"
        def unit = policyId + tokenNameHex  // No dot separator for hex encoded name
        
        when:
        def txDsl = transaction {
            from "addr1_sender"
            send 100.asset(unit) to "addr1_receiver"
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test asset creation with policy and name"() {
        given:
        // Use a valid hex policy ID
        def policyId = "1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        def tokenName = "MyToken"  // Plain text name - will be encoded by the API
        
        when:
        def txDsl = transaction {
            from "addr1_sender"
            send 100.asset(policyId, tokenName) to "addr1_receiver"
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test multiple payments in one transaction"() {
        when:
        def txDsl = transaction {
            from "addr1_sender"
            send 5.ada to "addr1_alice"
            send 3.ada to "addr1_bob"
            send 7.ada to "addr1_charlie"
        }
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
    
    def "test YAML serialization"() {
        given:
        def senderAddr = "addr1_sender"
        def receiverAddr = "addr1_receiver"
        
        when:
        def txDsl = transaction {
            from senderAddr
            send 5.ada to receiverAddr
        }
        def yaml = txDsl.toYaml()
        
        then:
        yaml != null
        yaml.contains("from")
        yaml.contains("payment")
        yaml.contains(senderAddr)
        yaml.contains(receiverAddr)
        yaml.contains("5000000") // 5 ADA in lovelace
    }
    
    def "test loading from YAML"() {
        given:
        def yaml = """
version: 1.0
transaction:
  - tx:
      intentions:
      - type: from
        address: addr1_sender
      - type: payment
        address: addr1_receiver
        amounts:
        - unit: lovelace
          quantity: 5000000
variables: {}
"""
        
        when:
        def txDsl = TxGroovyBuilder.fromYaml(yaml)
        
        then:
        txDsl != null
        def tx = txDsl.unwrap()
        tx != null
    }
}