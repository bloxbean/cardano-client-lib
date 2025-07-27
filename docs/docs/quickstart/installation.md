---
description: Complete installation and setup guide for Cardano Client Lib
sidebar_label: Installation & Setup
sidebar_position: 1
---

# Installation & Setup

Get Cardano Client Lib up and running in your Java project. This guide covers everything from basic setup to advanced configuration options.

## Quick Start

For most projects, you'll need just two dependencies:
1. **Core library** - Contains all the main functionality
2. **Backend provider** - Connects to the Cardano blockchain

### Maven (Recommended for Most Projects)

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- Core Cardano Client Lib -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>0.6.6</version>
    </dependency>
    
    <!-- Backend Provider (choose one) -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
        <version>0.6.6</version>
    </dependency>
</dependencies>
```

### Gradle

Add to your `build.gradle`:

```gradle
dependencies {
    // Core Cardano Client Lib
    implementation 'com.bloxbean.cardano:cardano-client-lib:0.6.6'
    
    // Backend Provider (choose one)
    implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.6.6'
}
```

:::tip Quick Setup
This basic setup gives you access to all QuickTx APIs and Blockfrost backend integration - perfect for getting started!
:::

## Requirements

### System Requirements
- **Java**: 11 or higher (recommended: Java 17 LTS)
- **Build Tool**: Maven 3.6+ or Gradle 6.0+
- **Memory**: Minimum 512MB heap space
- **Network**: Internet access for blockchain connectivity

### Supported Platforms
- ✅ **Linux** (Ubuntu, CentOS, Alpine)
- ✅ **macOS** (Intel and Apple Silicon)
- ✅ **Windows** (Windows 10/11)
- ✅ **Docker** containers
- ✅ **Cloud platforms** (AWS, GCP, Azure)

## Backend Providers

Choose a backend provider based on your needs:

### Blockfrost (Recommended for Beginners)

**Best for**: Development, prototyping, small to medium applications

```xml
<!-- Maven -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-blockfrost</artifactId>
    <version>0.6.6</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.6.6'
```

**Features**:
- 🟢 Easy setup with API key
- 🟢 Excellent uptime and reliability
- 🟢 Generous free tier
- 🟢 Great for development and testing
- 🟡 Rate limits on free tier

**Setup**: Get a free API key at [blockfrost.io](https://blockfrost.io)

### Koios (Community-Driven)

**Best for**: Community projects, no registration required

```xml
<!-- Maven -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-koios</artifactId>
    <version>0.6.6</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.bloxbean.cardano:cardano-client-backend-koios:0.6.6'
```

**Features**:
- 🟢 No registration required
- 🟢 Community-maintained
- 🟢 Free to use
- 🟡 Performance varies by load
- 🟡 Limited SLA

**Setup**: No API key needed, just configure the endpoint

### Ogmios + Kupo (Advanced)

**Best for**: High-performance applications, custom deployments

```xml
<!-- Maven -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-ogmios</artifactId>
    <version>0.6.6</version>
</dependency>
```

```gradle
// Gradle
implementation 'com.bloxbean.cardano:cardano-client-backend-ogmios:0.6.6'
```

**Features**:
- 🟢 Direct node connection
- 🟢 Maximum performance
- 🟢 No rate limits
- 🟢 Full control over infrastructure
- 🔴 Requires running your own Cardano node
- 🔴 More complex setup

**Setup**: Requires Cardano node + Ogmios + Kupo services

## Project Setup Examples

### Simple Wallet Application

Perfect for basic wallet functionality:

```xml
<!-- Maven pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>0.6.6</version>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
        <version>0.6.6</version>
    </dependency>
</dependencies>
```

### DeFi Application

Includes additional modules for complex transactions:

```xml
<!-- Maven pom.xml -->
<dependencies>
    <!-- Core functionality -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>0.6.6</version>
    </dependency>
    
    <!-- Backend for blockchain connectivity -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
        <version>0.6.6</version>
    </dependency>
    
    <!-- CIP standards for NFTs and metadata -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-cip</artifactId>
        <version>0.6.6</version>
    </dependency>
</dependencies>
```

### Smart Contract Application

For Plutus smart contract interactions:

```xml
<!-- Maven pom.xml -->
<dependencies>
    <!-- Core + Backend -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>0.6.6</version>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
        <version>0.6.6</version>
    </dependency>
    
    <!-- Annotation processor for Aiken blueprints -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-annotation-processor</artifactId>
        <version>0.6.6</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Enterprise Application

High-performance setup with multiple backends:

```gradle
// Gradle build.gradle
dependencies {
    // Core library
    implementation 'com.bloxbean.cardano:cardano-client-lib:0.6.6'
    
    // Multiple backends for redundancy
    implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.6.6'
    implementation 'com.bloxbean.cardano:cardano-client-backend-koios:0.6.6'
    implementation 'com.bloxbean.cardano:cardano-client-backend-ogmios:0.6.6'
    
    // All CIP implementations
    implementation 'com.bloxbean.cardano:cardano-client-cip:0.6.6'
    
    // Governance features
    implementation 'com.bloxbean.cardano:cardano-client-governance:0.6.6'
}
```

## Advanced Dependency Management

### Modular Approach

For fine-grained control, use individual modules:

```xml
<!-- Maven: Individual modules -->
<dependencies>
    <!-- Only what you need -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-core</artifactId>
        <version>0.6.6</version>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-quicktx</artifactId>
        <version>0.6.6</version>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
        <version>0.6.6</version>
    </dependency>
</dependencies>
```

### Bill of Materials (BOM)

Ensure version consistency across modules:

```xml
<!-- Maven: Use BOM for version management -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-bom</artifactId>
            <version>0.6.6</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- No version specified - inherited from BOM -->
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
    </dependency>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-backend-blockfrost</artifactId>
    </dependency>
</dependencies>
```

## Environment Configuration

### Development Environment

```java
// Development configuration
public class DevConfig {
    public static BackendService createBackendService() {
        return new BFBackendService(
            Constants.BLOCKFROST_PREPROD_URL,  // Testnet
            System.getenv("BLOCKFROST_PROJECT_ID_PREPROD")
        );
    }
}
```

### Production Environment

```java
// Production configuration
public class ProdConfig {
    public static BackendService createBackendService() {
        // Multiple backends for redundancy
        List<BackendService> backends = Arrays.asList(
            new BFBackendService(
                Constants.BLOCKFROST_MAINNET_URL,
                System.getenv("BLOCKFROST_PROJECT_ID_MAINNET")
            ),
            new KoiosBackendService(KOIOS_MAINNET_URL)
        );
        
        return new LoadBalancedBackendService(backends);
    }
}
```

### Configuration Properties

Create a `application.properties` file:

```properties
# Backend Configuration
cardano.backend.type=blockfrost
cardano.backend.blockfrost.project-id=${BLOCKFROST_PROJECT_ID}
cardano.backend.blockfrost.base-url=https://cardano-mainnet.blockfrost.io/api/v0

# Network Configuration  
cardano.network=mainnet

# Connection Configuration
cardano.connection.timeout=30000
cardano.connection.max-retries=3
```

## Environment Variables

Set these environment variables for secure configuration:

### For Development (Testnet)
```bash
export BLOCKFROST_PROJECT_ID_PREPROD="preprod_your_project_id_here"
export CARDANO_NETWORK="preprod"
```

### For Production (Mainnet)
```bash
export BLOCKFROST_PROJECT_ID_MAINNET="your_mainnet_project_id_here"
export CARDANO_NETWORK="mainnet"
```

### Docker Environment
```dockerfile
# Dockerfile
FROM openjdk:17-jre-slim

# Set environment variables
ENV BLOCKFROST_PROJECT_ID=${BLOCKFROST_PROJECT_ID}
ENV CARDANO_NETWORK=${CARDANO_NETWORK}

# Copy and run your application
COPY target/your-app.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

## Snapshot/Beta Versions

To use the latest development features:

### Maven
```xml
<repositories>
    <repository>
        <id>sonatype-snapshots</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.bloxbean.cardano</groupId>
        <artifactId>cardano-client-lib</artifactId>
        <version>0.7.0-beta4</version>
    </dependency>
</dependencies>
```

### Gradle
```gradle
repositories {
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots"
    }
}

dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-lib:0.7.0-beta4'
}
```

:::warning Beta Versions
Beta and snapshot versions may contain breaking changes. Use stable releases for production applications.
:::

## Verification

Test your setup with this simple verification script:

```java
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.common.model.Networks;

public class VerifySetup {
    public static void main(String[] args) {
        try {
            // Test 1: Account creation
            Account account = new Account(Networks.testnet());
            System.out.println("✅ Account creation works");
            System.out.println("Address: " + account.baseAddress());
            
            // Test 2: Backend service
            String projectId = System.getenv("BLOCKFROST_PROJECT_ID_PREPROD");
            if (projectId != null) {
                BFBackendService backend = new BFBackendService(
                    Constants.BLOCKFROST_PREPROD_URL, 
                    projectId
                );
                System.out.println("✅ Backend service configured");
            } else {
                System.out.println("⚠️ BLOCKFROST_PROJECT_ID_PREPROD not set");
            }
            
            System.out.println("🎉 Setup verification complete!");
            
        } catch (Exception e) {
            System.err.println("❌ Setup verification failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

Expected output:
```
✅ Account creation works
Address: addr_test1qr4z8k2ge3p8f7wqnpvk5t3jx2h8m9x...
✅ Backend service configured
🎉 Setup verification complete!
```

## Troubleshooting

### Common Issues

#### 1. Version Conflicts
```
NoSuchMethodError or ClassNotFoundException
```
**Solution**: Ensure all Cardano Client Lib modules use the same version
```xml
<!-- Use BOM to avoid version conflicts -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-bom</artifactId>
            <version>0.6.6</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 2. Missing Backend Provider
```
No backend service configured
```
**Solution**: Add a backend provider dependency:
```xml
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-blockfrost</artifactId>
    <version>0.6.6</version>
</dependency>
```

#### 3. Java Version Issues
```
UnsupportedClassVersionError
```
**Solution**: Use Java 11 or higher
```bash
# Check Java version
java -version

# Should show 11 or higher
openjdk version "17.0.2" 2022-01-18
```

#### 4. Network Connectivity
```
ConnectException or SocketTimeoutException
```
**Solution**: Check network access and proxy settings
```java
// Configure proxy if needed
System.setProperty("http.proxyHost", "proxy.company.com");
System.setProperty("http.proxyPort", "8080");
System.setProperty("https.proxyHost", "proxy.company.com");
System.setProperty("https.proxyPort", "8080");
```

#### 5. API Key Issues
```
403 Forbidden or 401 Unauthorized
```
**Solution**: Verify your API key and network
- Check Blockfrost project ID is correct
- Ensure using the right network (preprod/mainnet)
- Verify API key has sufficient quota

### Dependency Analysis

Use these commands to debug dependency issues:

#### Maven
```bash
# Show dependency tree
mvn dependency:tree

# Show dependency conflicts
mvn dependency:analyze

# Resolve dependency conflicts
mvn dependency:resolve-sources
```

#### Gradle
```bash
# Show dependency tree
./gradlew dependencies

# Show dependency insights
./gradlew dependencyInsight --dependency cardano-client-lib
```

### Memory Configuration

For production applications, configure JVM memory:

```bash
# Recommended JVM settings
java -Xms512m -Xmx2g -XX:+UseG1GC -jar your-app.jar
```

### Logging Configuration

Add logging to troubleshoot issues:

```xml
<!-- logback.xml -->
<configuration>
    <logger name="com.bloxbean.cardano" level="DEBUG"/>
    <logger name="okhttp3" level="INFO"/>
    
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Next Steps

Once your setup is complete:

1. **[First Transaction](./first-transaction.md)** - Send your first transaction
2. **[Choosing Your Path](./choosing-your-path.md)** - Select the right API approach
3. **[Account Setup](../fundamentals/accounts-and-addresses/account-setup.md)** - Learn about HD wallets and address generation

## Need Help?

- **Setup Issues**: Check our [troubleshooting guide](#troubleshooting)
- **Community**: Join our [Discord](https://discord.gg/JtQ54MSw6p) for setup help
- **Documentation**: Browse our API Reference (coming soon)
- **Examples**: Check the [examples repository](https://github.com/bloxbean/cardano-client-examples)