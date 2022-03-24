# cardano-client-backend-ogmios

**Status :** Experimental

This backend module currently supports following services

- TransactionService
```java  
     - Result<String> submitTransaction(byte[] cborData)
     - Result<List<EvaluationResult>> evaluateTx(byte[] cborData)
```

To get an instance of OgmiosBackendService

```java
BackendService backendService = new OgmiosBackendService("ws://host:port")

```
