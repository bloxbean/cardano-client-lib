## Core (cardano-client-core)

Contains low level transaction serialization logic and other common apis / interfaces (Account, Coin selections, UtxoSupplier, ProtocolParamSupplier etc.). 
Also contains high-level api like PaymentTransaction for backward compatibility. But HL api may be removed to a separate module in future release. 

**Dependencies:** common, crypto, transaction-common, address, metadata
                    
