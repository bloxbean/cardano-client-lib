---
description: Comprehensive guide to backend service configuration including API key management, connection pooling, failover strategies, rate limiting, and error handling patterns
sidebar_label: Backend Configuration
sidebar_position: 2
---

# Backend Configuration and Setup

Proper backend configuration is crucial for production-ready Cardano applications. This guide covers advanced configuration techniques, connection management, security practices, and resilience patterns for all supported backend providers.

## API Key Management

### Secure Key Storage

**Environment Variables (Recommended):**
```java
public class SecureBackendConfig {
    
    public BackendService createSecureBackend() {
        // Get API key from environment variables
        String apiKey = System.getenv("CARDANO_API_KEY");
        String backendUrl = System.getenv("CARDANO_BACKEND_URL");
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("CARDANO_API_KEY environment variable not set");
        }
        
        return new BFBackendService(backendUrl, apiKey);
    }
    
    public void demonstrateEnvironmentSetup() {
        // Set environment variables before starting application
        // Linux/macOS: export CARDANO_API_KEY="your_key_here"
        // Windows: set CARDANO_API_KEY=your_key_here
        
        // Or in Docker:
        // docker run -e CARDANO_API_KEY="your_key_here" your-app
        
        // Or in Kubernetes:
        // env:
        //   - name: CARDANO_API_KEY
        //     valueFrom:
        //       secretKeyRef:
        //         name: cardano-secrets
        //         key: api-key
    }
}
```

**Configuration Files with Encryption:**
```java
public class EncryptedConfigManager {
    private final AESUtil encryption;
    
    public BackendService createFromEncryptedConfig(String configPath) {
        try {
            // Load encrypted configuration
            Properties encryptedProps = loadProperties(configPath);
            
            // Decrypt sensitive values
            String apiKey = encryption.decrypt(encryptedProps.getProperty("api.key.encrypted"));
            String backendUrl = encryptedProps.getProperty("backend.url");
            
            return new BFBackendService(backendUrl, apiKey);
            
        } catch (Exception e) {
            throw new ConfigurationException("Failed to load encrypted configuration", e);
        }
    }
    
    public void createEncryptedConfig(String plainApiKey, String outputPath) {
        Properties props = new Properties();
        props.setProperty("api.key.encrypted", encryption.encrypt(plainApiKey));
        props.setProperty("backend.url", "https://cardano-mainnet.blockfrost.io/api/v0/");
        props.setProperty("config.version", "1.0");
        
        saveProperties(props, outputPath);
    }
}
```

**AWS Secrets Manager Integration:**
```java
public class AWSSecretsBackendConfig {
    private final SecretsManagerClient secretsClient;
    
    public BackendService createFromAWSSecrets(String secretName) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
            
            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            String secretString = response.secretString();
            
            // Parse JSON secret
            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretJson = mapper.readTree(secretString);
            
            String apiKey = secretJson.get("api_key").asText();
            String backendUrl = secretJson.get("backend_url").asText();
            
            return new BFBackendService(backendUrl, apiKey);
            
        } catch (Exception e) {
            throw new ConfigurationException("Failed to retrieve secrets from AWS", e);
        }
    }
    
    public void demonstrateSecretStructure() {
        // AWS Secrets Manager secret structure:
        String secretExample = """
            {
                "api_key": "mainnet_abc123...",
                "backend_url": "https://cardano-mainnet.blockfrost.io/api/v0/",
                "environment": "production",
                "rate_limit": 10
            }
            """;
        
        System.out.println("Example secret structure: " + secretExample);
    }
}
```

### Key Rotation Strategies

**Automated Key Rotation:**
```java
public class ApiKeyRotationManager {
    private volatile BackendService currentBackend;
    private final ScheduledExecutorService scheduler;
    private final ApiKeyProvider keyProvider;
    
    public ApiKeyRotationManager(ApiKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Rotate keys every 30 days
        scheduler.scheduleAtFixedRate(this::rotateApiKey, 0, 30, TimeUnit.DAYS);
    }
    
    private void rotateApiKey() {
        try {
            // Get new API key
            String newApiKey = keyProvider.getNextApiKey();
            String backendUrl = keyProvider.getBackendUrl();
            
            // Create new backend service
            BackendService newBackend = new BFBackendService(backendUrl, newApiKey);
            
            // Test new backend
            if (testBackendConnection(newBackend)) {
                // Atomic swap
                BackendService oldBackend = this.currentBackend;
                this.currentBackend = newBackend;
                
                // Cleanup old backend
                if (oldBackend != null) {
                    cleanupBackend(oldBackend);
                }
                
                log.info("API key rotation completed successfully");
            } else {
                log.error("New API key validation failed, keeping current key");
            }
            
        } catch (Exception e) {
            log.error("API key rotation failed: {}", e.getMessage(), e);
        }
    }
    
    private boolean testBackendConnection(BackendService backend) {
        try {
            // Simple connectivity test
            backend.getEpochService().getLatest();
            return true;
        } catch (Exception e) {
            log.warn("Backend connection test failed: {}", e.getMessage());
            return false;
        }
    }
    
    public BackendService getCurrentBackend() {
        return currentBackend;
    }
}
```

## Connection Pooling and Timeouts

### Advanced HTTP Client Configuration

**Production HTTP Client Setup:**
```java
public class ProductionBackendConfig {
    
    public BackendService createProductionBackend() {
        // SSL/TLS Configuration
        SSLContext sslContext = createSecureSSLContext();
        
        // Connection pooling
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        
        // Pool configuration
        connectionManager.setMaxTotal(200);              // Total connections
        connectionManager.setDefaultMaxPerRoute(50);     // Per-route connections
        connectionManager.setValidateAfterInactivity(30000); // 30 seconds
        connectionManager.setDefaultSocketConfig(
            SocketConfig.custom()
                .setSoTimeout(30000)      // Socket timeout
                .setSoKeepAlive(true)     // Keep-alive
                .setSoReuseAddress(true)  // Reuse address
                .build()
        );
        
        // Request configuration
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(5000)    // Time to get connection from pool
            .setConnectTimeout(10000)            // Time to establish connection
            .setSocketTimeout(30000)             // Time to wait for data
            .setExpectContinueEnabled(false)     // Disable 100-continue
            .setRedirectsEnabled(false)          // Disable redirects
            .build();
        
        // Retry handler
        HttpRequestRetryHandler retryHandler = (exception, executionCount, context) -> {
            if (executionCount >= 3) return false;
            
            if (exception instanceof InterruptedIOException) return false;
            if (exception instanceof UnknownHostException) return false;
            if (exception instanceof SSLException) return false;
            
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            
            return !(request instanceof HttpEntityEnclosingRequest);
        };
        
        // Build HTTP client
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setRetryHandler(retryHandler)
            .setSSLContext(sslContext)
            .setUserAgent("CardanoClientLib/1.0")
            .build();
        
        return new BFBackendService(
            "https://cardano-mainnet.blockfrost.io/api/v0/",
            getApiKey(),
            httpClient
        );
    }
    
    private SSLContext createSecureSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, null, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }
}
```

**Connection Pool Monitoring:**
```java
public class ConnectionPoolMonitor {
    private final PoolingHttpClientConnectionManager connectionManager;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler;
    
    public ConnectionPoolMonitor(PoolingHttpClientConnectionManager cm, MeterRegistry registry) {
        this.connectionManager = cm;
        this.meterRegistry = registry;
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        setupMetrics();
        startMonitoring();
    }
    
    private void setupMetrics() {
        Gauge.builder("http.connections.total")
            .description("Total HTTP connections")
            .register(meterRegistry, () -> connectionManager.getTotalStats().getMax());
            
        Gauge.builder("http.connections.available")
            .description("Available HTTP connections")
            .register(meterRegistry, () -> connectionManager.getTotalStats().getAvailable());
            
        Gauge.builder("http.connections.leased")
            .description("Leased HTTP connections")
            .register(meterRegistry, () -> connectionManager.getTotalStats().getLeased());
            
        Gauge.builder("http.connections.pending")
            .description("Pending HTTP connections")
            .register(meterRegistry, () -> connectionManager.getTotalStats().getPending());
    }
    
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            PoolStats stats = connectionManager.getTotalStats();
            
            log.debug("Connection pool stats - Total: {}, Available: {}, Leased: {}, Pending: {}",
                stats.getMax(), stats.getAvailable(), stats.getLeased(), stats.getPending());
            
            // Check for pool exhaustion
            if (stats.getAvailable() == 0 && stats.getPending() > 5) {
                log.warn("Connection pool exhaustion detected - Available: {}, Pending: {}",
                    stats.getAvailable(), stats.getPending());
            }
            
            // Check for connection leaks
            if (stats.getLeased() > stats.getMax() * 0.8) {
                log.warn("High connection usage detected - Leased: {}, Max: {}",
                    stats.getLeased(), stats.getMax());
            }
            
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    public PoolStats getPoolStats() {
        return connectionManager.getTotalStats();
    }
    
    public void closeIdleConnections() {
        connectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
    }
}
```

### Timeout Configuration Best Practices

**Timeout Strategy by Environment:**
```java
public class TimeoutConfiguration {
    
    public static class TimeoutSettings {
        private final int connectionTimeout;
        private final int socketTimeout;
        private final int connectionRequestTimeout;
        
        public TimeoutSettings(int connectionTimeout, int socketTimeout, int connectionRequestTimeout) {
            this.connectionTimeout = connectionTimeout;
            this.socketTimeout = socketTimeout;
            this.connectionRequestTimeout = connectionRequestTimeout;
        }
        
        // Getters...
    }
    
    public TimeoutSettings getTimeoutSettings(Environment env) {
        switch (env) {
            case DEVELOPMENT:
                return new TimeoutSettings(
                    5000,   // 5s connection timeout
                    15000,  // 15s socket timeout
                    2000    // 2s connection request timeout
                );
                
            case TESTING:
                return new TimeoutSettings(
                    3000,   // 3s connection timeout
                    10000,  // 10s socket timeout
                    1000    // 1s connection request timeout
                );
                
            case PRODUCTION:
                return new TimeoutSettings(
                    10000,  // 10s connection timeout
                    30000,  // 30s socket timeout
                    5000    // 5s connection request timeout
                );
                
            default:
                throw new IllegalArgumentException("Unknown environment: " + env);
        }
    }
    
    public BackendService createTimeoutConfiguredBackend(Environment env) {
        TimeoutSettings timeouts = getTimeoutSettings(env);
        
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(timeouts.getConnectionRequestTimeout())
            .setConnectTimeout(timeouts.getConnectionTimeout())
            .setSocketTimeout(timeouts.getSocketTimeout())
            .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();
        
        return new BFBackendService(getBackendUrl(), getApiKey(), httpClient);
    }
}
```

## Failover and Redundancy Strategies

### Multi-Backend Failover

**Round-Robin with Health Checking:**
```java
public class FailoverBackendService implements BackendService {
    private final List<BackendService> backends;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final Set<BackendService> unhealthyBackends = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService healthChecker;
    
    public FailoverBackendService(List<BackendService> backends) {
        this.backends = new ArrayList<>(backends);
        this.healthChecker = Executors.newScheduledThreadPool(1);
        
        // Health check every 30 seconds
        healthChecker.scheduleAtFixedRate(this::performHealthChecks, 0, 30, TimeUnit.SECONDS);
    }
    
    private BackendService getNextHealthyBackend() {
        List<BackendService> healthyBackends = backends.stream()
            .filter(backend -> !unhealthyBackends.contains(backend))
            .collect(Collectors.toList());
        
        if (healthyBackends.isEmpty()) {
            // All backends unhealthy, try the first one anyway
            log.warn("All backends are marked unhealthy, attempting first backend");
            return backends.get(0);
        }
        
        int index = currentIndex.getAndIncrement() % healthyBackends.size();
        return healthyBackends.get(index);
    }
    
    private void performHealthChecks() {
        for (BackendService backend : backends) {
            CompletableFuture.supplyAsync(() -> isBackendHealthy(backend))
                .thenAccept(healthy -> {
                    if (healthy) {
                        if (unhealthyBackends.remove(backend)) {
                            log.info("Backend restored to healthy state: {}", getBackendIdentifier(backend));
                        }
                    } else {
                        if (unhealthyBackends.add(backend)) {
                            log.warn("Backend marked as unhealthy: {}", getBackendIdentifier(backend));
                        }
                    }
                })
                .exceptionally(ex -> {
                    unhealthyBackends.add(backend);
                    log.error("Health check failed for backend: {}", getBackendIdentifier(backend), ex);
                    return null;
                });
        }
    }
    
    private boolean isBackendHealthy(BackendService backend) {
        try {
            // Simple health check - get latest epoch
            backend.getEpochService().getLatest();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private <T> T executeWithFailover(Function<BackendService, T> operation) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt < backends.size(); attempt++) {
            BackendService backend = getNextHealthyBackend();
            
            try {
                return operation.apply(backend);
            } catch (Exception e) {
                lastException = e;
                unhealthyBackends.add(backend);
                log.warn("Backend operation failed, trying next backend: {}", e.getMessage());
            }
        }
        
        throw new BackendFailoverException("All backends failed", lastException);
    }
    
    @Override
    public EpochService getEpochService() {
        return new EpochService() {
            @Override
            public Result<Epoch> getLatest() throws ApiException {
                return executeWithFailover(backend -> backend.getEpochService().getLatest());
            }
            
            @Override
            public Result<Epoch> getEpoch(Integer epochNumber) throws ApiException {
                return executeWithFailover(backend -> backend.getEpochService().getEpoch(epochNumber));
            }
        };
    }
    
    // Implement other BackendService methods similarly...
}
```

**Circuit Breaker Pattern:**
```java
public class CircuitBreakerBackendService implements BackendService {
    private final BackendService delegate;
    private final CircuitBreaker circuitBreaker;
    
    public CircuitBreakerBackendService(BackendService delegate) {
        this.delegate = delegate;
        this.circuitBreaker = CircuitBreaker.ofDefaults("backend");
        
        configureCircuitBreaker();
    }
    
    private void configureCircuitBreaker() {
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("Circuit breaker state transition: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
        
        circuitBreaker.getEventPublisher()
            .onCallNotPermitted(event -> 
                log.warn("Circuit breaker call not permitted"));
        
        circuitBreaker.getEventPublisher()
            .onFailureRateExceeded(event -> 
                log.error("Circuit breaker failure rate exceeded: {}%", 
                    event.getFailureRate()));
    }
    
    @Override
    public EpochService getEpochService() {
        return new EpochService() {
            @Override
            public Result<Epoch> getLatest() throws ApiException {
                return circuitBreaker.executeSupplier(() -> 
                    delegate.getEpochService().getLatest());
            }
            
            @Override
            public Result<Epoch> getEpoch(Integer epochNumber) throws ApiException {
                return circuitBreaker.executeSupplier(() -> 
                    delegate.getEpochService().getEpoch(epochNumber));
            }
        };
    }
    
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }
    
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }
}
```

### Geographic Redundancy

**Multi-Region Configuration:**
```java
public class MultiRegionBackendConfig {
    
    public static class RegionConfig {
        private final String region;
        private final String endpoint;
        private final String apiKey;
        private final int priority; // Lower number = higher priority
        
        // Constructor and getters...
    }
    
    public FailoverBackendService createMultiRegionBackend() {
        List<RegionConfig> regions = Arrays.asList(
            new RegionConfig("us-east-1", "https://cardano-mainnet.blockfrost.io/api/v0/", 
                           getApiKey("us-east-1"), 1),
            new RegionConfig("eu-west-1", "https://cardano-mainnet-eu.blockfrost.io/api/v0/", 
                           getApiKey("eu-west-1"), 2),
            new RegionConfig("ap-southeast-1", "https://cardano-mainnet-ap.blockfrost.io/api/v0/", 
                           getApiKey("ap-southeast-1"), 3)
        );
        
        // Sort by priority
        regions.sort(Comparator.comparingInt(RegionConfig::getPriority));
        
        List<BackendService> backends = regions.stream()
            .map(this::createBackendForRegion)
            .collect(Collectors.toList());
        
        return new FailoverBackendService(backends);
    }
    
    private BackendService createBackendForRegion(RegionConfig region) {
        // Configure timeouts based on region
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(getTimeoutForRegion(region.getRegion()))
            .setSocketTimeout(30000)
            .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();
        
        return new BFBackendService(region.getEndpoint(), region.getApiKey(), httpClient);
    }
    
    private int getTimeoutForRegion(String region) {
        // Adjust timeouts based on expected latency
        switch (region) {
            case "us-east-1": return 5000;   // Local region
            case "eu-west-1": return 8000;   // Cross-Atlantic
            case "ap-southeast-1": return 12000; // Cross-Pacific
            default: return 10000;
        }
    }
}
```

## Rate Limiting Handling

### Intelligent Rate Limiting

**Adaptive Rate Limiter:**
```java
public class AdaptiveRateLimitedBackendService implements BackendService {
    private final BackendService delegate;
    private final RateLimiter rateLimiter;
    private final AtomicInteger currentLimit;
    private final Queue<Long> recentRequests;
    private final int maxBurstSize;
    
    public AdaptiveRateLimitedBackendService(BackendService delegate, int initialRate) {
        this.delegate = delegate;
        this.currentLimit = new AtomicInteger(initialRate);
        this.rateLimiter = RateLimiter.create(initialRate);
        this.recentRequests = new ConcurrentLinkedQueue<>();
        this.maxBurstSize = initialRate * 2;
    }
    
    private <T> T executeWithRateLimit(Supplier<T> operation) {
        // Clean old requests (older than 1 minute)
        long oneMinuteAgo = System.currentTimeMillis() - 60000;
        recentRequests.removeIf(timestamp -> timestamp < oneMinuteAgo);
        
        // Acquire permit
        rateLimiter.acquire();
        recentRequests.offer(System.currentTimeMillis());
        
        try {
            T result = operation.get();
            
            // Success - potentially increase rate
            adjustRateOnSuccess();
            return result;
            
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                adjustRateOnRateLimit();
                
                // Wait and retry
                try {
                    Thread.sleep(calculateBackoffDelay());
                    return executeWithRateLimit(operation);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during rate limit backoff", ie);
                }
            } else {
                throw e;
            }
        }
    }
    
    private void adjustRateOnSuccess() {
        int current = currentLimit.get();
        int requestsInLastMinute = recentRequests.size();
        
        // If we're using less than 80% of capacity, slowly increase
        if (requestsInLastMinute < current * 0.8 && current < maxBurstSize) {
            int newLimit = Math.min(current + 1, maxBurstSize);
            if (currentLimit.compareAndSet(current, newLimit)) {
                rateLimiter.setRate(newLimit);
                log.debug("Increased rate limit to {}", newLimit);
            }
        }
    }
    
    private void adjustRateOnRateLimit() {
        int current = currentLimit.get();
        int newLimit = Math.max(current / 2, 1); // Halve the rate, minimum 1
        
        if (currentLimit.compareAndSet(current, newLimit)) {
            rateLimiter.setRate(newLimit);
            log.warn("Decreased rate limit to {} due to rate limiting", newLimit);
        }
    }
    
    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("rate limit") ||
            message.contains("429") ||
            message.contains("Too Many Requests")
        );
    }
    
    private long calculateBackoffDelay() {
        // Exponential backoff with jitter
        long baseDelay = 1000; // 1 second
        long jitter = (long) (Math.random() * 500); // 0-500ms jitter
        return baseDelay + jitter;
    }
    
    @Override
    public EpochService getEpochService() {
        return new EpochService() {
            @Override
            public Result<Epoch> getLatest() throws ApiException {
                return executeWithRateLimit(() -> delegate.getEpochService().getLatest());
            }
            
            @Override
            public Result<Epoch> getEpoch(Integer epochNumber) throws ApiException {
                return executeWithRateLimit(() -> delegate.getEpochService().getEpoch(epochNumber));
            }
        };
    }
    
    public int getCurrentRateLimit() {
        return currentLimit.get();
    }
    
    public int getRecentRequestCount() {
        return recentRequests.size();
    }
}
```

**Priority-Based Rate Limiting:**
```java
public class PriorityRateLimitedBackendService implements BackendService {
    private final BackendService delegate;
    private final Map<Priority, RateLimiter> rateLimiters;
    private final PriorityQueue<QueuedRequest> requestQueue;
    private final ScheduledExecutorService processor;
    
    public enum Priority {
        HIGH(1),
        MEDIUM(2), 
        LOW(3);
        
        private final int value;
        Priority(int value) { this.value = value; }
        public int getValue() { return value; }
    }
    
    public PriorityRateLimitedBackendService(BackendService delegate) {
        this.delegate = delegate;
        this.rateLimiters = Map.of(
            Priority.HIGH, RateLimiter.create(5.0),   // 5 req/sec for high priority
            Priority.MEDIUM, RateLimiter.create(3.0), // 3 req/sec for medium priority
            Priority.LOW, RateLimiter.create(1.0)     // 1 req/sec for low priority
        );
        
        this.requestQueue = new PriorityQueue<>(
            Comparator.comparing(QueuedRequest::getPriority, 
                Comparator.comparing(Priority::getValue))
                .thenComparing(QueuedRequest::getTimestamp)
        );
        
        this.processor = Executors.newSingleThreadScheduledExecutor();
        startRequestProcessor();
    }
    
    private static class QueuedRequest {
        private final Priority priority;
        private final long timestamp;
        private final Supplier<?> operation;
        private final CompletableFuture<Object> future;
        
        // Constructor and getters...
    }
    
    private void startRequestProcessor() {
        processor.scheduleAtFixedRate(() -> {
            while (!requestQueue.isEmpty()) {
                QueuedRequest request = requestQueue.peek();
                RateLimiter limiter = rateLimiters.get(request.getPriority());
                
                if (limiter.tryAcquire()) {
                    requestQueue.poll();
                    processRequest(request);
                } else {
                    break; // Wait for next iteration
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
    
    private void processRequest(QueuedRequest request) {
        try {
            Object result = request.getOperation().get();
            request.getFuture().complete(result);
        } catch (Exception e) {
            request.getFuture().completeExceptionally(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> submitRequest(Priority priority, Supplier<T> operation) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        QueuedRequest request = new QueuedRequest(priority, System.currentTimeMillis(), 
                                                  operation, future);
        requestQueue.offer(request);
        
        return (CompletableFuture<T>) future;
    }
    
    public <T> CompletableFuture<T> executeWithPriority(Priority priority, Supplier<T> operation) {
        return submitRequest(priority, operation);
    }
    
    @Override
    public EpochService getEpochService() {
        return new EpochService() {
            @Override
            public Result<Epoch> getLatest() throws ApiException {
                try {
                    return executeWithPriority(Priority.MEDIUM, 
                        () -> delegate.getEpochService().getLatest()).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new ApiException("Failed to execute with priority", e);
                }
            }
            
            // Other methods...
        };
    }
}
```

## Error Handling Patterns

### Comprehensive Error Classification

**Error Classification System:**
```java
public class BackendErrorHandler {
    
    public enum ErrorType {
        NETWORK_ERROR,
        RATE_LIMIT_ERROR,
        AUTHENTICATION_ERROR,
        VALIDATION_ERROR,
        SERVER_ERROR,
        TIMEOUT_ERROR,
        UNKNOWN_ERROR
    }
    
    public static class ErrorContext {
        private final ErrorType type;
        private final String message;
        private final Exception originalException;
        private final boolean retryable;
        private final long retryAfterMs;
        
        // Constructor and getters...
    }
    
    public ErrorContext classifyError(Exception e) {
        String message = e.getMessage();
        
        // Network errors
        if (e instanceof ConnectException || e instanceof UnknownHostException) {
            return new ErrorContext(ErrorType.NETWORK_ERROR, message, e, true, 5000);
        }
        
        // Timeout errors
        if (e instanceof SocketTimeoutException || message.contains("timeout")) {
            return new ErrorContext(ErrorType.TIMEOUT_ERROR, message, e, true, 2000);
        }
        
        // HTTP status based classification
        if (e instanceof HttpException) {
            HttpException httpEx = (HttpException) e;
            int statusCode = httpEx.getStatusCode();
            
            switch (statusCode) {
                case 401:
                case 403:
                    return new ErrorContext(ErrorType.AUTHENTICATION_ERROR, message, e, false, 0);
                    
                case 429:
                    long retryAfter = parseRetryAfter(httpEx.getHeaders());
                    return new ErrorContext(ErrorType.RATE_LIMIT_ERROR, message, e, true, retryAfter);
                    
                case 400:
                case 422:
                    return new ErrorContext(ErrorType.VALIDATION_ERROR, message, e, false, 0);
                    
                case 500:
                case 502:
                case 503:
                case 504:
                    return new ErrorContext(ErrorType.SERVER_ERROR, message, e, true, 10000);
            }
        }
        
        return new ErrorContext(ErrorType.UNKNOWN_ERROR, message, e, false, 0);
    }
    
    private long parseRetryAfter(Map<String, String> headers) {
        String retryAfter = headers.get("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter) * 1000; // Convert seconds to milliseconds
            } catch (NumberFormatException e) {
                // Could be a date format, but we'll default to 60 seconds
                return 60000;
            }
        }
        return 60000; // Default 60 seconds
    }
    
    public <T> T executeWithErrorHandling(Supplier<T> operation, int maxRetries) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
                
            } catch (Exception e) {
                lastException = e;
                ErrorContext errorContext = classifyError(e);
                
                log.warn("Backend operation failed (attempt {}/{}): {} - {}", 
                    attempt, maxRetries, errorContext.getType(), errorContext.getMessage());
                
                if (!errorContext.isRetryable() || attempt == maxRetries) {
                    break;
                }
                
                try {
                    Thread.sleep(calculateBackoffDelay(attempt, errorContext.getRetryAfterMs()));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
            }
        }
        
        throw new BackendException("Operation failed after " + maxRetries + " attempts", lastException);
    }
    
    private long calculateBackoffDelay(int attempt, long retryAfterMs) {
        // Use retry-after if provided, otherwise exponential backoff
        if (retryAfterMs > 0) {
            return retryAfterMs;
        }
        
        long baseDelay = 1000; // 1 second
        long exponentialDelay = baseDelay * (1L << (attempt - 1)); // 2^(attempt-1)
        long jitter = (long) (Math.random() * 1000); // 0-1000ms jitter
        
        return Math.min(exponentialDelay + jitter, 30000); // Max 30 seconds
    }
}
```

### Resilient Backend Wrapper

**Production-Ready Backend Wrapper:**
```java
public class ResilientBackendService implements BackendService {
    private final BackendService delegate;
    private final BackendErrorHandler errorHandler;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    
    public ResilientBackendService(BackendService delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.errorHandler = new BackendErrorHandler();
        this.circuitBreaker = CircuitBreaker.ofDefaults("backend-service");
        this.rateLimiter = RateLimiter.create(10.0); // 10 requests per second
        this.meterRegistry = meterRegistry;
        
        setupMetrics();
        configureCircuitBreaker();
    }
    
    private void setupMetrics() {
        Timer.builder("backend.request.duration")
            .description("Backend request duration")
            .register(meterRegistry);
            
        Counter.builder("backend.request.total")
            .description("Total backend requests")
            .register(meterRegistry);
            
        Counter.builder("backend.request.errors")
            .description("Backend request errors")
            .register(meterRegistry);
    }
    
    private void configureCircuitBreaker() {
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                Counter.builder("backend.circuit_breaker.state_transition")
                    .tag("from", event.getStateTransition().getFromState().toString())
                    .tag("to", event.getStateTransition().getToState().toString())
                    .register(meterRegistry)
                    .increment();
            });
    }
    
    private <T> T executeResilient(String operationName, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Rate limiting
            rateLimiter.acquire();
            
            // Circuit breaker + error handling
            T result = circuitBreaker.executeSupplier(() -> 
                errorHandler.executeWithErrorHandling(operation, 3));
            
            // Success metrics
            Counter.builder("backend.request.total")
                .tag("operation", operationName)
                .tag("status", "success")
                .register(meterRegistry)
                .increment();
            
            return result;
            
        } catch (Exception e) {
            // Error metrics
            Counter.builder("backend.request.total")
                .tag("operation", operationName)
                .tag("status", "error")
                .register(meterRegistry)
                .increment();
                
            Counter.builder("backend.request.errors")
                .tag("operation", operationName)
                .tag("error_type", errorHandler.classifyError(e).getType().toString())
                .register(meterRegistry)
                .increment();
            
            throw e;
            
        } finally {
            sample.stop(Timer.builder("backend.request.duration")
                .tag("operation", operationName)
                .register(meterRegistry));
        }
    }
    
    @Override
    public EpochService getEpochService() {
        return new EpochService() {
            @Override
            public Result<Epoch> getLatest() throws ApiException {
                return executeResilient("epoch.latest", () -> delegate.getEpochService().getLatest());
            }
            
            @Override
            public Result<Epoch> getEpoch(Integer epochNumber) throws ApiException {
                return executeResilient("epoch.get", () -> delegate.getEpochService().getEpoch(epochNumber));
            }
        };
    }
    
    // Implement other services similarly...
    
    public BackendHealth getHealth() {
        return BackendHealth.builder()
            .circuitBreakerState(circuitBreaker.getState())
            .circuitBreakerMetrics(circuitBreaker.getMetrics())
            .currentRateLimit(rateLimiter.getRate())
            .build();
    }
}
```

## Configuration Validation

### Comprehensive Configuration Validation

**Configuration Validator:**
```java
public class BackendConfigValidator {
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        // Constructor and getters...
        
        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
        }
        
        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, Collections.emptyList());
        }
    }
    
    public ValidationResult validateConfiguration(BackendConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate API key
        validateApiKey(config.getApiKey(), errors);
        
        // Validate URLs
        validateUrl(config.getBackendUrl(), "Backend URL", errors);
        
        // Validate timeouts
        validateTimeouts(config, errors, warnings);
        
        // Validate connection pool settings
        validateConnectionPool(config, errors, warnings);
        
        // Validate SSL settings
        validateSslSettings(config, warnings);
        
        // Test connectivity
        validateConnectivity(config, errors);
        
        if (errors.isEmpty()) {
            return new ValidationResult(true, errors, warnings);
        } else {
            return new ValidationResult(false, errors, warnings);
        }
    }
    
    private void validateApiKey(String apiKey, List<String> errors) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            errors.add("API key is required");
            return;
        }
        
        if (apiKey.length() < 10) {
            errors.add("API key appears to be too short");
        }
        
        if (apiKey.startsWith("test_") || apiKey.startsWith("demo_")) {
            errors.add("Test/demo API key detected - not suitable for production");
        }
        
        if (apiKey.equals("your_api_key_here") || apiKey.equals("replace_me")) {
            errors.add("Placeholder API key detected - please configure actual API key");
        }
    }
    
    private void validateUrl(String url, String fieldName, List<String> errors) {
        if (url == null || url.trim().isEmpty()) {
            errors.add(fieldName + " is required");
            return;
        }
        
        try {
            URL parsedUrl = new URL(url);
            
            if (!parsedUrl.getProtocol().equals("https")) {
                errors.add(fieldName + " must use HTTPS protocol");
            }
            
            if (parsedUrl.getHost().equals("localhost") || parsedUrl.getHost().equals("127.0.0.1")) {
                errors.add(fieldName + " points to localhost - not suitable for production");
            }
            
        } catch (MalformedURLException e) {
            errors.add(fieldName + " is not a valid URL: " + e.getMessage());
        }
    }
    
    private void validateTimeouts(BackendConfig config, List<String> errors, List<String> warnings) {
        if (config.getConnectionTimeout() <= 0) {
            errors.add("Connection timeout must be positive");
        } else if (config.getConnectionTimeout() < 1000) {
            warnings.add("Connection timeout is very low (" + config.getConnectionTimeout() + "ms)");
        } else if (config.getConnectionTimeout() > 60000) {
            warnings.add("Connection timeout is very high (" + config.getConnectionTimeout() + "ms)");
        }
        
        if (config.getSocketTimeout() <= 0) {
            errors.add("Socket timeout must be positive");
        } else if (config.getSocketTimeout() < 5000) {
            warnings.add("Socket timeout is very low (" + config.getSocketTimeout() + "ms)");
        }
        
        if (config.getConnectionRequestTimeout() <= 0) {
            errors.add("Connection request timeout must be positive");
        }
    }
    
    private void validateConnectionPool(BackendConfig config, List<String> errors, List<String> warnings) {
        if (config.getMaxTotalConnections() <= 0) {
            errors.add("Max total connections must be positive");
        } else if (config.getMaxTotalConnections() > 1000) {
            warnings.add("Max total connections is very high (" + config.getMaxTotalConnections() + ")");
        }
        
        if (config.getMaxPerRouteConnections() <= 0) {
            errors.add("Max per-route connections must be positive");
        } else if (config.getMaxPerRouteConnections() > config.getMaxTotalConnections()) {
            errors.add("Max per-route connections cannot exceed max total connections");
        }
    }
    
    private void validateSslSettings(BackendConfig config, List<String> warnings) {
        if (!config.isSslVerificationEnabled()) {
            warnings.add("SSL verification is disabled - not recommended for production");
        }
        
        if (config.getSslProtocol() != null && !config.getSslProtocol().startsWith("TLS")) {
            warnings.add("SSL protocol should be TLS-based for security");
        }
    }
    
    private void validateConnectivity(BackendConfig config, List<String> errors) {
        try {
            // Create a test backend service
            BackendService testBackend = createTestBackend(config);
            
            // Test basic connectivity
            testBackend.getEpochService().getLatest();
            
        } catch (Exception e) {
            errors.add("Connectivity test failed: " + e.getMessage());
        }
    }
    
    private BackendService createTestBackend(BackendConfig config) {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000) // Short timeout for validation
            .setSocketTimeout(10000)
            .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();
        
        return new BFBackendService(config.getBackendUrl(), config.getApiKey(), httpClient);
    }
    
    public void printValidationReport(ValidationResult result) {
        if (result.isValid()) {
            System.out.println("✅ Configuration validation passed");
        } else {
            System.out.println("❌ Configuration validation failed");
            result.getErrors().forEach(error -> System.out.println("  ERROR: " + error));
        }
        
        if (!result.getWarnings().isEmpty()) {
            System.out.println("⚠️  Configuration warnings:");
            result.getWarnings().forEach(warning -> System.out.println("  WARNING: " + warning));
        }
    }
}
```

## Configuration Templates

### Environment-Specific Templates

**Development Configuration:**
```java
public class DevelopmentBackendConfig {
    
    public BackendService createDevelopmentBackend() {
        return new BFBackendService(
            "https://cardano-testnet.blockfrost.io/api/v0/",
            System.getenv("CARDANO_TESTNET_API_KEY")
        );
    }
    
    public BackendService createLocalBackend() {
        // For local development with Ogmios/Kupo
        return new LocalNodeBackendService(
            "http://localhost:1337",  // Ogmios
            "http://localhost:1442"   // Kupo
        );
    }
}
```

**Production Configuration:**
```java
public class ProductionBackendConfig {
    
    public BackendService createProductionBackend() {
        // Multi-region failover setup
        List<RegionConfig> regions = Arrays.asList(
            new RegionConfig("primary", "https://cardano-mainnet.blockfrost.io/api/v0/", 
                           getSecureApiKey("primary")),
            new RegionConfig("fallback", "https://cardano-mainnet-eu.blockfrost.io/api/v0/", 
                           getSecureApiKey("fallback"))
        );
        
        List<BackendService> backends = regions.stream()
            .map(this::createProductionBackendForRegion)
            .collect(Collectors.toList());
        
        BackendService primaryBackend = new FailoverBackendService(backends);
        
        // Add resilience layers
        primaryBackend = new CircuitBreakerBackendService(primaryBackend);
        primaryBackend = new AdaptiveRateLimitedBackendService(primaryBackend, 20);
        primaryBackend = new ResilientBackendService(primaryBackend, meterRegistry);
        
        return primaryBackend;
    }
    
    private BackendService createProductionBackendForRegion(RegionConfig region) {
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(50);
        connectionManager.setValidateAfterInactivity(30000);
        
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(5000)
            .setConnectTimeout(10000)
            .setSocketTimeout(30000)
            .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setRetryHandler(new StandardHttpRequestRetryHandler(3, true))
            .build();
        
        return new BFBackendService(region.getEndpoint(), region.getApiKey(), httpClient);
    }
}
```

## Summary

### Configuration Best Practices

✅ **Security**
- Store API keys securely (environment variables, secrets managers)
- Use HTTPS exclusively for all communications
- Implement proper SSL/TLS configuration
- Rotate API keys regularly

✅ **Reliability**
- Configure appropriate timeouts for your environment
- Implement circuit breakers for fault tolerance
- Use failover strategies for high availability
- Monitor connection pool health

✅ **Performance**
- Optimize connection pooling settings
- Implement intelligent rate limiting
- Use caching where appropriate
- Monitor and adjust based on metrics

✅ **Monitoring**
- Track all backend operations with metrics
- Implement comprehensive health checks
- Set up alerting for critical failures
- Log errors with proper context

### Next Steps

After configuring your backend services, explore:

- **[Backend Providers](./backend-providers.md)** - Choosing the right provider
- **[QuickTx Performance Guide](../../quicktx/performance.md)** - Transaction optimization
- **[Error Handling Guide](../../quicktx/error-handling.md)** - Comprehensive error management

## Resources

- **[Blockfrost Configuration Guide](https://docs.blockfrost.io/)** - Official Blockfrost documentation
- **[Apache HttpClient Documentation](https://hc.apache.org/httpcomponents-client-ga/)** - HTTP client configuration
- **[Resilience4j Documentation](https://resilience4j.readme.io/)** - Circuit breakers and retry patterns

---

**Proper backend configuration is essential for production Cardano applications. Follow these patterns to build resilient, scalable, and secure blockchain integrations.**