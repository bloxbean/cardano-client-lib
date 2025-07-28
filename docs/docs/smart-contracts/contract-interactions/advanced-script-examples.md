---
description: Complete implementations of advanced smart contracts including DEX swaps, NFT marketplaces, staking rewards distribution, governance voting, and cross-contract interactions
sidebar_label: Advanced Script Examples
sidebar_position: 3
---

# Advanced Script Examples

This guide provides complete, production-ready implementations of advanced smart contracts using the Cardano Client Library. Learn how to build DEX swaps, NFT marketplaces, staking reward distributors, governance voting systems, and cross-contract interactions.

:::tip Prerequisites
Understanding of [Interaction Patterns](./interaction-patterns.md), [PlutusData Types](../plutus-basics/plutus-data-types.md), and [Script Execution](../plutus-basics/script-execution.md) is required.
:::

## DEX Swap Implementation

A complete decentralized exchange implementation with liquidity pools, automated market making, and slippage protection.

### Core DEX Components

```java
import com.bloxbean.cardano.client.quicktx.*;
import com.bloxbean.cardano.client.plutus.spec.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

// Liquidity pool datum
@Constr(alternative = 0)
public class LiquidityPoolDatum implements Data<LiquidityPoolDatum> {
    @PlutusField(order = 0)
    private byte[] poolId;                  // Unique pool identifier
    
    @PlutusField(order = 1)
    private byte[] tokenAPolicyId;          // First token policy
    
    @PlutusField(order = 2)
    private byte[] tokenAName;              // First token name
    
    @PlutusField(order = 3)
    private byte[] tokenBPolicyId;          // Second token policy
    
    @PlutusField(order = 4)
    private byte[] tokenBName;              // Second token name
    
    @PlutusField(order = 5)
    private BigInteger reserveA;            // Token A reserves
    
    @PlutusField(order = 6)
    private BigInteger reserveB;            // Token B reserves
    
    @PlutusField(order = 7)
    private BigInteger totalLiquidity;      // Total LP tokens
    
    @PlutusField(order = 8)
    private BigInteger feeNumerator;        // Fee percentage numerator
    
    @PlutusField(order = 9)
    private BigInteger feeDenominator;      // Fee percentage denominator
    
    @PlutusField(order = 10)
    private byte[] lpTokenPolicy;           // LP token policy ID
    
    @PlutusField(order = 11)
    private BigInteger lastInteraction;     // Last interaction timestamp
    
    // Getters, setters, and PlutusData conversion methods
}

// Swap order datum
@Constr(alternative = 0)
public class SwapOrderDatum implements Data<SwapOrderDatum> {
    @PlutusField(order = 0)
    private byte[] trader;                  // Trader's address
    
    @PlutusField(order = 1)
    private byte[] inputToken;              // Token being sold
    
    @PlutusField(order = 2)
    private BigInteger inputAmount;         // Amount being sold
    
    @PlutusField(order = 3)
    private byte[] outputToken;             // Token being bought
    
    @PlutusField(order = 4)
    private BigInteger minOutputAmount;     // Minimum acceptable output
    
    @PlutusField(order = 5)
    private BigInteger deadline;            // Order expiration
    
    @PlutusField(order = 6)
    private byte[] poolId;                  // Target pool ID
    
    @PlutusField(order = 7)
    private BigInteger maxSlippage;         // Maximum slippage in basis points
}

// DEX redeemer actions
@Constr(alternative = 0)
public class DexRedeemer implements Data<DexRedeemer> {
    @PlutusField(order = 0)
    private BigInteger action;              // 0=swap, 1=addLiquidity, 2=removeLiquidity
    
    @PlutusField(order = 1)
    private PlutusData actionData;          // Action-specific data
}
```

### DEX Core Implementation

```java
public class DecentralizedExchange {
    private final PlutusV2Script poolScript;
    private final PlutusV2Script orderScript;
    private final PlutusV2Script lpMintingPolicy;
    private final QuickTxBuilder txBuilder;
    
    private static final BigInteger FEE_NUMERATOR = BigInteger.valueOf(3);      // 0.3% fee
    private static final BigInteger FEE_DENOMINATOR = BigInteger.valueOf(1000);
    
    public DecentralizedExchange(PlutusV2Script poolScript, PlutusV2Script orderScript, 
                                PlutusV2Script lpMintingPolicy, QuickTxBuilder txBuilder) {
        this.poolScript = poolScript;
        this.orderScript = orderScript;
        this.lpMintingPolicy = lpMintingPolicy;
        this.txBuilder = txBuilder;
    }
    
    // Create a new liquidity pool
    public Result<String> createPool(Account creator, Asset tokenA, Asset tokenB, 
                                   BigInteger initialA, BigInteger initialB) {
        // Generate unique pool ID
        byte[] poolId = generatePoolId(tokenA, tokenB);
        
        // Calculate initial liquidity
        BigInteger initialLiquidity = sqrt(initialA.multiply(initialB));
        
        // Create pool datum
        LiquidityPoolDatum poolDatum = new LiquidityPoolDatum();
        poolDatum.setPoolId(poolId);
        poolDatum.setTokenAPolicyId(tokenA.getPolicyId().getBytes());
        poolDatum.setTokenAName(tokenA.getAssetName().getBytes());
        poolDatum.setTokenBPolicyId(tokenB.getPolicyId().getBytes());
        poolDatum.setTokenBName(tokenB.getAssetName().getBytes());
        poolDatum.setReserveA(initialA);
        poolDatum.setReserveB(initialB);
        poolDatum.setTotalLiquidity(initialLiquidity);
        poolDatum.setFeeNumerator(FEE_NUMERATOR);
        poolDatum.setFeeDenominator(FEE_DENOMINATOR);
        poolDatum.setLpTokenPolicy(lpMintingPolicy.getPolicyId().getBytes());
        poolDatum.setLastInteraction(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        
        // Create LP tokens
        String lpTokenName = "LP_" + tokenA.getAssetName() + "_" + tokenB.getAssetName();
        Asset lpToken = Asset.builder()
            .policyId(lpMintingPolicy.getPolicyId())
            .assetName(lpTokenName)
            .amount(initialLiquidity)
            .build();
        
        // Build pool creation transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        
        ScriptTx poolTx = new ScriptTx()
            .mintAssets(lpMintingPolicy, createMintRedeemer(poolDatum), lpToken)
            .payTo(poolAddress, Amount.builder()
                .asset(tokenA.getPolicyId(), tokenA.getAssetName(), initialA)
                .asset(tokenB.getPolicyId(), tokenB.getAssetName(), initialB)
                .coin(BigInteger.valueOf(2000000)) // Min ADA
                .build())
            .attachDatum(poolDatum.toPlutusData())
            .payTo(creator.baseAddress(), Amount.builder()
                .asset(lpToken.getPolicyId(), lpToken.getAssetName(), initialLiquidity)
                .build())
            .from(creator.baseAddress());
            
        return txBuilder.compose(poolTx)
            .withSigner(SignerProviders.signerFrom(creator))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Execute a token swap
    public Result<String> swap(Account trader, String inputTokenId, BigInteger inputAmount,
                             String outputTokenId, BigInteger minOutputAmount, String poolId) {
        // Find pool UTXO
        LiquidityPoolDatum poolDatum = findPool(poolId);
        Utxo poolUtxo = findPoolUtxo(poolDatum);
        
        // Calculate swap output
        SwapCalculation calc = calculateSwapOutput(
            poolDatum, inputTokenId, inputAmount, outputTokenId
        );
        
        // Verify slippage protection
        if (calc.outputAmount.compareTo(minOutputAmount) < 0) {
            throw new IllegalStateException(
                "Output amount " + calc.outputAmount + " below minimum " + minOutputAmount
            );
        }
        
        // Create swap redeemer
        SwapActionData swapData = new SwapActionData();
        swapData.setInputToken(inputTokenId.getBytes());
        swapData.setInputAmount(inputAmount);
        swapData.setOutputToken(outputTokenId.getBytes());
        swapData.setOutputAmount(calc.outputAmount);
        swapData.setTrader(trader.getBaseAddress().getBytes());
        
        DexRedeemer redeemer = new DexRedeemer();
        redeemer.setAction(BigInteger.ZERO); // Swap action
        redeemer.setActionData(swapData.toPlutusData());
        
        // Update pool reserves
        LiquidityPoolDatum newPoolDatum = updatePoolAfterSwap(
            poolDatum, inputTokenId, inputAmount, outputTokenId, calc.outputAmount
        );
        
        // Build swap transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        
        ScriptTx swapTx = new ScriptTx()
            .collectFrom(poolUtxo, redeemer.toPlutusData())
            .payTo(poolAddress, Amount.builder()
                .asset(getTokenPolicy(inputTokenId), getTokenName(inputTokenId), 
                       newPoolDatum.getReserveA())
                .asset(getTokenPolicy(outputTokenId), getTokenName(outputTokenId), 
                       newPoolDatum.getReserveB())
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(newPoolDatum.toPlutusData())
            .payTo(trader.baseAddress(), Amount.builder()
                .asset(getTokenPolicy(outputTokenId), getTokenName(outputTokenId), 
                       calc.outputAmount)
                .build())
            .attachSpendingValidator(poolScript)
            .withRequiredSigners(trader.getBaseAddress());
            
        return txBuilder.compose(swapTx)
            .withSigner(SignerProviders.signerFrom(trader))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Add liquidity to pool
    public Result<String> addLiquidity(Account provider, String poolId, 
                                     BigInteger amountA, BigInteger amountB) {
        LiquidityPoolDatum poolDatum = findPool(poolId);
        Utxo poolUtxo = findPoolUtxo(poolDatum);
        
        // Calculate liquidity tokens to mint
        BigInteger liquidityMinted = calculateLiquidityMinted(
            poolDatum, amountA, amountB
        );
        
        // Create add liquidity redeemer
        AddLiquidityData addData = new AddLiquidityData();
        addData.setProvider(provider.getBaseAddress().getBytes());
        addData.setAmountA(amountA);
        addData.setAmountB(amountB);
        addData.setLiquidityMinted(liquidityMinted);
        
        DexRedeemer redeemer = new DexRedeemer();
        redeemer.setAction(BigInteger.ONE); // Add liquidity action
        redeemer.setActionData(addData.toPlutusData());
        
        // Update pool datum
        LiquidityPoolDatum newPoolDatum = new LiquidityPoolDatum();
        copyPoolDatum(poolDatum, newPoolDatum);
        newPoolDatum.setReserveA(poolDatum.getReserveA().add(amountA));
        newPoolDatum.setReserveB(poolDatum.getReserveB().add(amountB));
        newPoolDatum.setTotalLiquidity(poolDatum.getTotalLiquidity().add(liquidityMinted));
        newPoolDatum.setLastInteraction(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        
        // Create LP tokens
        Asset lpToken = Asset.builder()
            .policyId(lpMintingPolicy.getPolicyId())
            .assetName(getLpTokenName(poolDatum))
            .amount(liquidityMinted)
            .build();
        
        // Build transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        
        ScriptTx liquidityTx = new ScriptTx()
            .collectFrom(poolUtxo, redeemer.toPlutusData())
            .mintAssets(lpMintingPolicy, createMintRedeemer(newPoolDatum), lpToken)
            .payTo(poolAddress, Amount.builder()
                .asset(getTokenAPolicyId(poolDatum), getTokenAName(poolDatum), 
                       newPoolDatum.getReserveA())
                .asset(getTokenBPolicyId(poolDatum), getTokenBName(poolDatum), 
                       newPoolDatum.getReserveB())
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(newPoolDatum.toPlutusData())
            .payTo(provider.baseAddress(), Amount.builder()
                .asset(lpToken.getPolicyId(), lpToken.getAssetName(), liquidityMinted)
                .build())
            .attachSpendingValidator(poolScript)
            .withRequiredSigners(provider.getBaseAddress());
            
        return txBuilder.compose(liquidityTx)
            .withSigner(SignerProviders.signerFrom(provider))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Remove liquidity from pool
    public Result<String> removeLiquidity(Account provider, String poolId, 
                                        BigInteger liquidityAmount) {
        LiquidityPoolDatum poolDatum = findPool(poolId);
        Utxo poolUtxo = findPoolUtxo(poolDatum);
        
        // Calculate tokens to return
        BigInteger amountA = liquidityAmount.multiply(poolDatum.getReserveA())
            .divide(poolDatum.getTotalLiquidity());
        BigInteger amountB = liquidityAmount.multiply(poolDatum.getReserveB())
            .divide(poolDatum.getTotalLiquidity());
        
        // Create remove liquidity redeemer
        RemoveLiquidityData removeData = new RemoveLiquidityData();
        removeData.setProvider(provider.getBaseAddress().getBytes());
        removeData.setLiquidityBurned(liquidityAmount);
        removeData.setAmountA(amountA);
        removeData.setAmountB(amountB);
        
        DexRedeemer redeemer = new DexRedeemer();
        redeemer.setAction(BigInteger.valueOf(2)); // Remove liquidity action
        redeemer.setActionData(removeData.toPlutusData());
        
        // Update pool datum
        LiquidityPoolDatum newPoolDatum = new LiquidityPoolDatum();
        copyPoolDatum(poolDatum, newPoolDatum);
        newPoolDatum.setReserveA(poolDatum.getReserveA().subtract(amountA));
        newPoolDatum.setReserveB(poolDatum.getReserveB().subtract(amountB));
        newPoolDatum.setTotalLiquidity(poolDatum.getTotalLiquidity().subtract(liquidityAmount));
        newPoolDatum.setLastInteraction(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        
        // Burn LP tokens
        Asset lpTokenBurn = Asset.builder()
            .policyId(lpMintingPolicy.getPolicyId())
            .assetName(getLpTokenName(poolDatum))
            .amount(liquidityAmount.negate()) // Negative for burning
            .build();
        
        // Build transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        
        ScriptTx removeTx = new ScriptTx()
            .collectFrom(poolUtxo, redeemer.toPlutusData())
            .mintAssets(lpMintingPolicy, createBurnRedeemer(poolDatum), lpTokenBurn)
            .payTo(poolAddress, Amount.builder()
                .asset(getTokenAPolicyId(poolDatum), getTokenAName(poolDatum), 
                       newPoolDatum.getReserveA())
                .asset(getTokenBPolicyId(poolDatum), getTokenBName(poolDatum), 
                       newPoolDatum.getReserveB())
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(newPoolDatum.toPlutusData())
            .payTo(provider.baseAddress(), Amount.builder()
                .asset(getTokenAPolicyId(poolDatum), getTokenAName(poolDatum), amountA)
                .asset(getTokenBPolicyId(poolDatum), getTokenBName(poolDatum), amountB)
                .build())
            .attachSpendingValidator(poolScript)
            .withRequiredSigners(provider.getBaseAddress());
            
        return txBuilder.compose(removeTx)
            .withSigner(SignerProviders.signerFrom(provider))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // AMM calculations
    private SwapCalculation calculateSwapOutput(LiquidityPoolDatum pool, 
                                               String inputToken, BigInteger inputAmount,
                                               String outputToken) {
        BigInteger inputReserve, outputReserve;
        
        if (isTokenA(pool, inputToken)) {
            inputReserve = pool.getReserveA();
            outputReserve = pool.getReserveB();
        } else {
            inputReserve = pool.getReserveB();
            outputReserve = pool.getReserveA();
        }
        
        // Apply fee
        BigInteger inputWithFee = inputAmount.multiply(FEE_DENOMINATOR.subtract(FEE_NUMERATOR));
        
        // Calculate output using constant product formula
        // outputAmount = (inputWithFee * outputReserve) / (inputReserve * FEE_DENOMINATOR + inputWithFee)
        BigInteger numerator = inputWithFee.multiply(outputReserve);
        BigInteger denominator = inputReserve.multiply(FEE_DENOMINATOR).add(inputWithFee);
        BigInteger outputAmount = numerator.divide(denominator);
        
        // Calculate price impact
        BigDecimal priceImpact = calculatePriceImpact(
            inputAmount, outputAmount, inputReserve, outputReserve
        );
        
        return new SwapCalculation(outputAmount, priceImpact);
    }
    
    private BigInteger calculateLiquidityMinted(LiquidityPoolDatum pool, 
                                               BigInteger amountA, BigInteger amountB) {
        if (pool.getTotalLiquidity().equals(BigInteger.ZERO)) {
            return sqrt(amountA.multiply(amountB));
        }
        
        // liquidityMinted = min(amountA * totalLiquidity / reserveA, 
        //                      amountB * totalLiquidity / reserveB)
        BigInteger liquidityA = amountA.multiply(pool.getTotalLiquidity())
            .divide(pool.getReserveA());
        BigInteger liquidityB = amountB.multiply(pool.getTotalLiquidity())
            .divide(pool.getReserveB());
        
        return liquidityA.min(liquidityB);
    }
    
    private BigDecimal calculatePriceImpact(BigInteger inputAmount, BigInteger outputAmount,
                                           BigInteger inputReserve, BigInteger outputReserve) {
        // Calculate spot price before swap
        BigDecimal spotPrice = new BigDecimal(outputReserve)
            .divide(new BigDecimal(inputReserve), 10, RoundingMode.HALF_UP);
        
        // Calculate execution price
        BigDecimal executionPrice = new BigDecimal(outputAmount)
            .divide(new BigDecimal(inputAmount), 10, RoundingMode.HALF_UP);
        
        // Price impact = (spotPrice - executionPrice) / spotPrice * 100
        BigDecimal impact = spotPrice.subtract(executionPrice)
            .divide(spotPrice, 10, RoundingMode.HALF_UP)
            .multiply(new BigDecimal(100));
        
        return impact;
    }
    
    private BigInteger sqrt(BigInteger n) {
        if (n.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }
        
        BigInteger x = n;
        BigInteger y = x.add(BigInteger.ONE).divide(BigInteger.valueOf(2));
        
        while (y.compareTo(x) < 0) {
            x = y;
            y = x.add(n.divide(x)).divide(BigInteger.valueOf(2));
        }
        
        return x;
    }
    
    private static class SwapCalculation {
        final BigInteger outputAmount;
        final BigDecimal priceImpact;
        
        SwapCalculation(BigInteger outputAmount, BigDecimal priceImpact) {
            this.outputAmount = outputAmount;
            this.priceImpact = priceImpact;
        }
    }
}
```

### Advanced DEX Features

```java
// Limit order implementation
@Constr(alternative = 0)
public class LimitOrderDatum implements Data<LimitOrderDatum> {
    @PlutusField(order = 0)
    private byte[] orderId;
    
    @PlutusField(order = 1)
    private byte[] maker;                   // Order creator
    
    @PlutusField(order = 2)
    private byte[] sellToken;
    
    @PlutusField(order = 3)
    private BigInteger sellAmount;
    
    @PlutusField(order = 4)
    private byte[] buyToken;
    
    @PlutusField(order = 5)
    private BigInteger buyAmount;
    
    @PlutusField(order = 6)
    private BigInteger expiration;
    
    @PlutusField(order = 7)
    private BigInteger partialFillAllowed;  // 0=no, 1=yes
    
    @PlutusField(order = 8)
    private BigInteger filledAmount;        // Amount already filled
}

public class LimitOrderBook {
    private final PlutusV2Script orderScript;
    private final QuickTxBuilder txBuilder;
    
    public Result<String> createLimitOrder(Account maker, Asset sellAsset, BigInteger sellAmount,
                                         Asset buyAsset, BigInteger buyAmount, 
                                         long expirationHours, boolean allowPartialFill) {
        LimitOrderDatum orderDatum = new LimitOrderDatum();
        orderDatum.setOrderId(generateOrderId());
        orderDatum.setMaker(maker.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        orderDatum.setSellToken(encodeAsset(sellAsset));
        orderDatum.setSellAmount(sellAmount);
        orderDatum.setBuyToken(encodeAsset(buyAsset));
        orderDatum.setBuyAmount(buyAmount);
        orderDatum.setExpiration(BigInteger.valueOf(
            System.currentTimeMillis() / 1000 + expirationHours * 3600
        ));
        orderDatum.setPartialFillAllowed(allowPartialFill ? BigInteger.ONE : BigInteger.ZERO);
        orderDatum.setFilledAmount(BigInteger.ZERO);
        
        String orderAddress = AddressUtil.getEnterprise(orderScript, Networks.testnet()).toBech32();
        
        Tx orderTx = new Tx()
            .payTo(orderAddress, Amount.builder()
                .asset(sellAsset.getPolicyId(), sellAsset.getAssetName(), sellAmount)
                .coin(BigInteger.valueOf(2000000)) // Min ADA
                .build())
            .attachDatum(orderDatum.toPlutusData())
            .from(maker.baseAddress());
            
        return txBuilder.compose(orderTx)
            .withSigner(SignerProviders.signerFrom(maker))
            .completeAndSubmit();
    }
    
    public Result<String> fillLimitOrder(LimitOrderDatum orderDatum, Account taker, 
                                       BigInteger fillAmount) {
        // Verify order not expired
        if (System.currentTimeMillis() / 1000 > orderDatum.getExpiration().longValue()) {
            throw new IllegalStateException("Order expired");
        }
        
        // Calculate amounts
        BigInteger remainingSell = orderDatum.getSellAmount()
            .subtract(orderDatum.getFilledAmount());
        BigInteger actualFillAmount = fillAmount.min(remainingSell);
        
        if (!orderDatum.getPartialFillAllowed().equals(BigInteger.ONE) && 
            !actualFillAmount.equals(remainingSell)) {
            throw new IllegalStateException("Partial fill not allowed");
        }
        
        // Calculate buy amount proportionally
        BigInteger buyAmountRequired = actualFillAmount
            .multiply(orderDatum.getBuyAmount())
            .divide(orderDatum.getSellAmount());
        
        // Update order datum
        LimitOrderDatum updatedDatum = new LimitOrderDatum();
        copyOrderDatum(orderDatum, updatedDatum);
        updatedDatum.setFilledAmount(orderDatum.getFilledAmount().add(actualFillAmount));
        
        // Build fill transaction
        Utxo orderUtxo = findOrderUtxo(orderDatum);
        String orderAddress = AddressUtil.getEnterprise(orderScript, Networks.testnet()).toBech32();
        
        ScriptTx fillTx = new ScriptTx()
            .collectFrom(orderUtxo, createFillRedeemer(taker, actualFillAmount))
            .payTo(decodeAddress(orderDatum.getMaker()), Amount.builder()
                .asset(decodePolicyId(orderDatum.getBuyToken()), 
                       decodeAssetName(orderDatum.getBuyToken()), buyAmountRequired)
                .build())
            .payTo(taker.baseAddress(), Amount.builder()
                .asset(decodePolicyId(orderDatum.getSellToken()), 
                       decodeAssetName(orderDatum.getSellToken()), actualFillAmount)
                .build());
        
        // Return remaining order if partially filled
        if (updatedDatum.getFilledAmount().compareTo(orderDatum.getSellAmount()) < 0) {
            BigInteger remainingAmount = orderDatum.getSellAmount()
                .subtract(updatedDatum.getFilledAmount());
            fillTx.payTo(orderAddress, Amount.builder()
                .asset(decodePolicyId(orderDatum.getSellToken()), 
                       decodeAssetName(orderDatum.getSellToken()), remainingAmount)
                .coin(orderUtxo.getAmount().getCoin())
                .build())
                .attachDatum(updatedDatum.toPlutusData());
        }
        
        fillTx.attachSpendingValidator(orderScript)
              .withRequiredSigners(taker.getBaseAddress());
              
        return txBuilder.compose(fillTx)
            .withSigner(SignerProviders.signerFrom(taker))
            .withTxEvaluator()
            .completeAndSubmit();
    }
}
```

## NFT Marketplace Contract

A complete NFT marketplace with listing, bidding, royalties, and collection management.

### NFT Marketplace Components

```java
// NFT listing datum
@Constr(alternative = 0)
public class NFTListingDatum implements Data<NFTListingDatum> {
    @PlutusField(order = 0)
    private byte[] listingId;
    
    @PlutusField(order = 1)
    private byte[] seller;                  // Seller's address
    
    @PlutusField(order = 2)
    private byte[] nftPolicyId;
    
    @PlutusField(order = 3)
    private byte[] nftTokenName;
    
    @PlutusField(order = 4)
    private BigInteger price;               // Listing price
    
    @PlutusField(order = 5)
    private byte[] paymentToken;            // Payment token (ADA or custom)
    
    @PlutusField(order = 6)
    private BigInteger royaltyPercentage;   // Royalty in basis points
    
    @PlutusField(order = 7)
    private byte[] royaltyAddress;          // Creator royalty address
    
    @PlutusField(order = 8)
    private BigInteger listingExpiration;
    
    @PlutusField(order = 9)
    private BigInteger marketplaceFee;      // Marketplace fee in basis points
    
    @PlutusField(order = 10)
    private Map<byte[], byte[]> metadata;   // Additional listing metadata
}

// Auction datum
@Constr(alternative = 0)
public class AuctionDatum implements Data<AuctionDatum> {
    @PlutusField(order = 0)
    private byte[] auctionId;
    
    @PlutusField(order = 1)
    private byte[] seller;
    
    @PlutusField(order = 2)
    private byte[] nftPolicyId;
    
    @PlutusField(order = 3)
    private byte[] nftTokenName;
    
    @PlutusField(order = 4)
    private BigInteger startingBid;
    
    @PlutusField(order = 5)
    private BigInteger minBidIncrement;
    
    @PlutusField(order = 6)
    private BigInteger currentBid;
    
    @PlutusField(order = 7)
    private byte[] currentBidder;
    
    @PlutusField(order = 8)
    private BigInteger auctionEnd;
    
    @PlutusField(order = 9)
    private BigInteger extensionPeriod;     // Time extension on new bid
    
    @PlutusField(order = 10)
    private List<BidHistory> bidHistory;
}

@Constr(alternative = 0)
public class BidHistory implements Data<BidHistory> {
    @PlutusField(order = 0)
    private byte[] bidder;
    
    @PlutusField(order = 1)
    private BigInteger amount;
    
    @PlutusField(order = 2)
    private BigInteger timestamp;
}
```

### NFT Marketplace Implementation

```java
public class NFTMarketplace {
    private final PlutusV2Script marketplaceScript;
    private final PlutusV2Script auctionScript;
    private final QuickTxBuilder txBuilder;
    
    private static final BigInteger MARKETPLACE_FEE = BigInteger.valueOf(250); // 2.5%
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);
    
    // List NFT for direct sale
    public Result<String> listNFT(Account seller, Asset nft, BigInteger price,
                                Asset paymentToken, BigInteger royaltyPercentage,
                                String royaltyAddress, long listingDays) {
        NFTListingDatum listing = new NFTListingDatum();
        listing.setListingId(generateListingId());
        listing.setSeller(seller.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        listing.setNftPolicyId(nft.getPolicyId().getBytes());
        listing.setNftTokenName(nft.getAssetName().getBytes());
        listing.setPrice(price);
        listing.setPaymentToken(encodeAsset(paymentToken));
        listing.setRoyaltyPercentage(royaltyPercentage);
        listing.setRoyaltyAddress(AddressUtil.fromBech32(royaltyAddress).getBytes());
        listing.setListingExpiration(BigInteger.valueOf(
            System.currentTimeMillis() / 1000 + listingDays * 24 * 3600
        ));
        listing.setMarketplaceFee(MARKETPLACE_FEE);
        listing.setMetadata(new HashMap<>());
        
        String marketplaceAddress = AddressUtil.getEnterprise(
            marketplaceScript, Networks.testnet()
        ).toBech32();
        
        Tx listingTx = new Tx()
            .payTo(marketplaceAddress, Amount.builder()
                .asset(nft.getPolicyId(), nft.getAssetName(), BigInteger.ONE)
                .coin(BigInteger.valueOf(2000000)) // Min ADA
                .build())
            .attachDatum(listing.toPlutusData())
            .from(seller.baseAddress());
            
        return txBuilder.compose(listingTx)
            .withSigner(SignerProviders.signerFrom(seller))
            .completeAndSubmit();
    }
    
    // Buy listed NFT
    public Result<String> buyNFT(NFTListingDatum listing, Account buyer) {
        // Verify listing not expired
        if (System.currentTimeMillis() / 1000 > listing.getListingExpiration().longValue()) {
            throw new IllegalStateException("Listing expired");
        }
        
        // Calculate payment distribution
        PaymentDistribution distribution = calculatePaymentDistribution(listing);
        
        // Find listing UTXO
        Utxo listingUtxo = findListingUtxo(listing);
        
        // Build purchase transaction
        ScriptTx purchaseTx = new ScriptTx()
            .collectFrom(listingUtxo, createPurchaseRedeemer(buyer));
        
        // Pay seller
        purchaseTx.payTo(decodeAddress(listing.getSeller()), Amount.builder()
            .asset(decodePolicyId(listing.getPaymentToken()),
                   decodeAssetName(listing.getPaymentToken()),
                   distribution.sellerAmount)
            .build());
        
        // Pay royalties if applicable
        if (distribution.royaltyAmount.compareTo(BigInteger.ZERO) > 0) {
            purchaseTx.payTo(decodeAddress(listing.getRoyaltyAddress()), Amount.builder()
                .asset(decodePolicyId(listing.getPaymentToken()),
                       decodeAssetName(listing.getPaymentToken()),
                       distribution.royaltyAmount)
                .build());
        }
        
        // Pay marketplace fee
        purchaseTx.payTo(getMarketplaceFeeAddress(), Amount.builder()
            .asset(decodePolicyId(listing.getPaymentToken()),
                   decodeAssetName(listing.getPaymentToken()),
                   distribution.marketplaceFee)
            .build());
        
        // Transfer NFT to buyer
        purchaseTx.payTo(buyer.baseAddress(), Amount.builder()
            .asset(new String(listing.getNftPolicyId()),
                   new String(listing.getNftTokenName()),
                   BigInteger.ONE)
            .build());
        
        purchaseTx.attachSpendingValidator(marketplaceScript)
                  .withRequiredSigners(buyer.getBaseAddress());
                  
        return txBuilder.compose(purchaseTx)
            .withSigner(SignerProviders.signerFrom(buyer))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Create NFT auction
    public Result<String> createAuction(Account seller, Asset nft, BigInteger startingBid,
                                      BigInteger minBidIncrement, long auctionDays,
                                      long extensionMinutes) {
        AuctionDatum auction = new AuctionDatum();
        auction.setAuctionId(generateAuctionId());
        auction.setSeller(seller.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        auction.setNftPolicyId(nft.getPolicyId().getBytes());
        auction.setNftTokenName(nft.getAssetName().getBytes());
        auction.setStartingBid(startingBid);
        auction.setMinBidIncrement(minBidIncrement);
        auction.setCurrentBid(BigInteger.ZERO);
        auction.setCurrentBidder(new byte[0]);
        auction.setAuctionEnd(BigInteger.valueOf(
            System.currentTimeMillis() / 1000 + auctionDays * 24 * 3600
        ));
        auction.setExtensionPeriod(BigInteger.valueOf(extensionMinutes * 60));
        auction.setBidHistory(new ArrayList<>());
        
        String auctionAddress = AddressUtil.getEnterprise(
            auctionScript, Networks.testnet()
        ).toBech32();
        
        Tx auctionTx = new Tx()
            .payTo(auctionAddress, Amount.builder()
                .asset(nft.getPolicyId(), nft.getAssetName(), BigInteger.ONE)
                .coin(BigInteger.valueOf(2000000)) // Min ADA
                .build())
            .attachDatum(auction.toPlutusData())
            .from(seller.baseAddress());
            
        return txBuilder.compose(auctionTx)
            .withSigner(SignerProviders.signerFrom(seller))
            .completeAndSubmit();
    }
    
    // Place bid on auction
    public Result<String> placeBid(AuctionDatum auction, Account bidder, BigInteger bidAmount) {
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Verify auction still active
        if (currentTime > auction.getAuctionEnd().longValue()) {
            throw new IllegalStateException("Auction ended");
        }
        
        // Verify bid amount
        BigInteger minBid = auction.getCurrentBid().equals(BigInteger.ZERO) ?
            auction.getStartingBid() :
            auction.getCurrentBid().add(auction.getMinBidIncrement());
            
        if (bidAmount.compareTo(minBid) < 0) {
            throw new IllegalArgumentException(
                "Bid amount " + bidAmount + " below minimum " + minBid
            );
        }
        
        // Find auction UTXO
        Utxo auctionUtxo = findAuctionUtxo(auction);
        
        // Create bid history entry
        BidHistory newBid = new BidHistory();
        newBid.setBidder(bidder.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        newBid.setAmount(bidAmount);
        newBid.setTimestamp(BigInteger.valueOf(currentTime));
        
        // Update auction datum
        AuctionDatum updatedAuction = new AuctionDatum();
        copyAuctionDatum(auction, updatedAuction);
        updatedAuction.setCurrentBid(bidAmount);
        updatedAuction.setCurrentBidder(newBid.getBidder());
        updatedAuction.getBidHistory().add(newBid);
        
        // Extend auction if bid near end
        long timeUntilEnd = auction.getAuctionEnd().longValue() - currentTime;
        if (timeUntilEnd < auction.getExtensionPeriod().longValue()) {
            updatedAuction.setAuctionEnd(
                BigInteger.valueOf(currentTime).add(auction.getExtensionPeriod())
            );
        }
        
        // Build bid transaction
        String auctionAddress = AddressUtil.getEnterprise(
            auctionScript, Networks.testnet()
        ).toBech32();
        
        ScriptTx bidTx = new ScriptTx()
            .collectFrom(auctionUtxo, createBidRedeemer(bidder, bidAmount));
        
        // Return previous bid if exists
        if (!auction.getCurrentBid().equals(BigInteger.ZERO)) {
            bidTx.payTo(decodeAddress(auction.getCurrentBidder()), 
                Amount.ada(auction.getCurrentBid()));
        }
        
        // Place new bid
        bidTx.payTo(auctionAddress, Amount.builder()
            .asset(new String(auction.getNftPolicyId()),
                   new String(auction.getNftTokenName()),
                   BigInteger.ONE)
            .coin(bidAmount.add(BigInteger.valueOf(2000000))) // Bid + min ADA
            .build())
            .attachDatum(updatedAuction.toPlutusData())
            .attachSpendingValidator(auctionScript)
            .withRequiredSigners(bidder.getBaseAddress());
            
        return txBuilder.compose(bidTx)
            .withSigner(SignerProviders.signerFrom(bidder))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Settle auction after end
    public Result<String> settleAuction(AuctionDatum auction, Account settler) {
        // Verify auction ended
        if (System.currentTimeMillis() / 1000 <= auction.getAuctionEnd().longValue()) {
            throw new IllegalStateException("Auction still active");
        }
        
        // Verify there's a winning bid
        if (auction.getCurrentBid().equals(BigInteger.ZERO)) {
            return cancelAuction(auction, settler);
        }
        
        // Find auction UTXO
        Utxo auctionUtxo = findAuctionUtxo(auction);
        
        // Calculate payment distribution (including fees and royalties)
        PaymentDistribution distribution = calculateAuctionDistribution(auction);
        
        // Build settlement transaction
        ScriptTx settleTx = new ScriptTx()
            .collectFrom(auctionUtxo, createSettleRedeemer(settler));
        
        // Pay seller
        settleTx.payTo(decodeAddress(auction.getSeller()), 
            Amount.ada(distribution.sellerAmount));
        
        // Pay marketplace fee
        settleTx.payTo(getMarketplaceFeeAddress(), 
            Amount.ada(distribution.marketplaceFee));
        
        // Transfer NFT to winner
        settleTx.payTo(decodeAddress(auction.getCurrentBidder()), Amount.builder()
            .asset(new String(auction.getNftPolicyId()),
                   new String(auction.getNftTokenName()),
                   BigInteger.ONE)
            .build());
        
        settleTx.attachSpendingValidator(auctionScript)
                .withRequiredSigners(settler.getBaseAddress());
                
        return txBuilder.compose(settleTx)
            .withSigner(SignerProviders.signerFrom(settler))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private PaymentDistribution calculatePaymentDistribution(NFTListingDatum listing) {
        BigInteger total = listing.getPrice();
        
        // Calculate marketplace fee
        BigInteger marketplaceFee = total.multiply(listing.getMarketplaceFee())
            .divide(BASIS_POINTS);
        
        // Calculate royalty
        BigInteger royaltyAmount = total.multiply(listing.getRoyaltyPercentage())
            .divide(BASIS_POINTS);
        
        // Seller gets remainder
        BigInteger sellerAmount = total.subtract(marketplaceFee).subtract(royaltyAmount);
        
        return new PaymentDistribution(sellerAmount, royaltyAmount, marketplaceFee);
    }
    
    private static class PaymentDistribution {
        final BigInteger sellerAmount;
        final BigInteger royaltyAmount;
        final BigInteger marketplaceFee;
        
        PaymentDistribution(BigInteger sellerAmount, BigInteger royaltyAmount, 
                          BigInteger marketplaceFee) {
            this.sellerAmount = sellerAmount;
            this.royaltyAmount = royaltyAmount;
            this.marketplaceFee = marketplaceFee;
        }
    }
}
```

## Staking Rewards Distribution

A complete staking rewards distribution system with automatic calculations and claim management.

### Staking Rewards Components

```java
// Staking pool datum
@Constr(alternative = 0)
public class StakingPoolDatum implements Data<StakingPoolDatum> {
    @PlutusField(order = 0)
    private byte[] poolId;
    
    @PlutusField(order = 1)
    private byte[] rewardToken;             // Token distributed as rewards
    
    @PlutusField(order = 2)
    private byte[] stakingToken;            // Token being staked
    
    @PlutusField(order = 3)
    private BigInteger totalStaked;         // Total staked amount
    
    @PlutusField(order = 4)
    private BigInteger rewardPerSecond;     // Reward emission rate
    
    @PlutusField(order = 5)
    private BigInteger lastUpdateTime;      // Last reward calculation
    
    @PlutusField(order = 6)
    private BigInteger accumulatedRewardPerShare; // Accumulated rewards per staked token
    
    @PlutusField(order = 7)
    private BigInteger startTime;           // Pool start time
    
    @PlutusField(order = 8)
    private BigInteger endTime;             // Pool end time
    
    @PlutusField(order = 9)
    private BigInteger minStakeAmount;      // Minimum stake requirement
    
    @PlutusField(order = 10)
    private Map<byte[], StakerInfo> stakers; // Staker information
}

@Constr(alternative = 0)
public class StakerInfo implements Data<StakerInfo> {
    @PlutusField(order = 0)
    private BigInteger stakedAmount;
    
    @PlutusField(order = 1)
    private BigInteger rewardDebt;          // Already distributed rewards
    
    @PlutusField(order = 2)
    private BigInteger lastStakeTime;
    
    @PlutusField(order = 3)
    private BigInteger pendingRewards;      // Unclaimed rewards
}

// Individual staking position
@Constr(alternative = 0)
public class StakingPositionDatum implements Data<StakingPositionDatum> {
    @PlutusField(order = 0)
    private byte[] staker;
    
    @PlutusField(order = 1)
    private byte[] poolId;
    
    @PlutusField(order = 2)
    private BigInteger stakedAmount;
    
    @PlutusField(order = 3)
    private BigInteger stakeTimestamp;
    
    @PlutusField(order = 4)
    private BigInteger lastClaimTime;
    
    @PlutusField(order = 5)
    private BigInteger lockDuration;        // Optional lock period
}
```

### Staking Rewards Implementation

```java
public class StakingRewardsDistributor {
    private final PlutusV2Script poolScript;
    private final PlutusV2Script positionScript;
    private final QuickTxBuilder txBuilder;
    
    // Create staking pool
    public Result<String> createStakingPool(Account creator, Asset stakingToken, 
                                          Asset rewardToken, BigInteger totalRewards,
                                          BigInteger rewardPerSecond, 
                                          long durationDays, BigInteger minStake) {
        long currentTime = System.currentTimeMillis() / 1000;
        
        StakingPoolDatum pool = new StakingPoolDatum();
        pool.setPoolId(generatePoolId());
        pool.setRewardToken(encodeAsset(rewardToken));
        pool.setStakingToken(encodeAsset(stakingToken));
        pool.setTotalStaked(BigInteger.ZERO);
        pool.setRewardPerSecond(rewardPerSecond);
        pool.setLastUpdateTime(BigInteger.valueOf(currentTime));
        pool.setAccumulatedRewardPerShare(BigInteger.ZERO);
        pool.setStartTime(BigInteger.valueOf(currentTime));
        pool.setEndTime(BigInteger.valueOf(currentTime + durationDays * 24 * 3600));
        pool.setMinStakeAmount(minStake);
        pool.setStakers(new HashMap<>());
        
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        
        Tx poolTx = new Tx()
            .payTo(poolAddress, Amount.builder()
                .asset(rewardToken.getPolicyId(), rewardToken.getAssetName(), totalRewards)
                .coin(BigInteger.valueOf(5000000)) // Min ADA
                .build())
            .attachDatum(pool.toPlutusData())
            .from(creator.baseAddress());
            
        return txBuilder.compose(poolTx)
            .withSigner(SignerProviders.signerFrom(creator))
            .completeAndSubmit();
    }
    
    // Stake tokens
    public Result<String> stake(Account staker, String poolId, BigInteger stakeAmount) {
        StakingPoolDatum pool = findPool(poolId);
        Utxo poolUtxo = findPoolUtxo(pool);
        
        // Verify pool is active
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime < pool.getStartTime().longValue() || 
            currentTime > pool.getEndTime().longValue()) {
            throw new IllegalStateException("Pool not active");
        }
        
        // Verify minimum stake
        if (stakeAmount.compareTo(pool.getMinStakeAmount()) < 0) {
            throw new IllegalArgumentException("Below minimum stake amount");
        }
        
        // Update pool rewards
        StakingPoolDatum updatedPool = updatePoolRewards(pool, currentTime);
        
        // Get or create staker info
        byte[] stakerAddress = staker.getBaseAddress().getPaymentCredentialHash().orElseThrow();
        StakerInfo stakerInfo = updatedPool.getStakers().get(stakerAddress);
        
        if (stakerInfo == null) {
            stakerInfo = new StakerInfo();
            stakerInfo.setStakedAmount(BigInteger.ZERO);
            stakerInfo.setRewardDebt(BigInteger.ZERO);
            stakerInfo.setLastStakeTime(BigInteger.valueOf(currentTime));
            stakerInfo.setPendingRewards(BigInteger.ZERO);
        } else {
            // Calculate pending rewards for existing stake
            BigInteger pending = calculatePendingRewards(stakerInfo, updatedPool);
            stakerInfo.setPendingRewards(stakerInfo.getPendingRewards().add(pending));
        }
        
        // Update staker info
        stakerInfo.setStakedAmount(stakerInfo.getStakedAmount().add(stakeAmount));
        stakerInfo.setRewardDebt(
            stakerInfo.getStakedAmount()
                .multiply(updatedPool.getAccumulatedRewardPerShare())
                .divide(BigInteger.valueOf(1e12))
        );
        updatedPool.getStakers().put(stakerAddress, stakerInfo);
        
        // Update total staked
        updatedPool.setTotalStaked(updatedPool.getTotalStaked().add(stakeAmount));
        
        // Create staking position NFT (optional)
        StakingPositionDatum position = new StakingPositionDatum();
        position.setStaker(stakerAddress);
        position.setPoolId(pool.getPoolId());
        position.setStakedAmount(stakeAmount);
        position.setStakeTimestamp(BigInteger.valueOf(currentTime));
        position.setLastClaimTime(BigInteger.valueOf(currentTime));
        position.setLockDuration(BigInteger.ZERO); // No lock for this example
        
        // Build stake transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        Asset stakingAsset = decodeAsset(pool.getStakingToken());
        
        ScriptTx stakeTx = new ScriptTx()
            .collectFrom(poolUtxo, createStakeRedeemer(staker, stakeAmount))
            .payTo(poolAddress, Amount.builder()
                .asset(stakingAsset.getPolicyId(), stakingAsset.getAssetName(),
                       poolUtxo.getAmount().getAsset(stakingAsset.getPolicyId(), 
                                                    stakingAsset.getAssetName())
                           .orElse(BigInteger.ZERO).add(stakeAmount))
                .asset(decodeAsset(pool.getRewardToken()).getPolicyId(),
                       decodeAsset(pool.getRewardToken()).getAssetName(),
                       poolUtxo.getAmount().getAsset(
                           decodeAsset(pool.getRewardToken()).getPolicyId(),
                           decodeAsset(pool.getRewardToken()).getAssetName()
                       ).orElse(BigInteger.ZERO))
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(updatedPool.toPlutusData())
            .from(staker.baseAddress())
            .attachSpendingValidator(poolScript)
            .withRequiredSigners(staker.getBaseAddress());
            
        return txBuilder.compose(stakeTx)
            .withSigner(SignerProviders.signerFrom(staker))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Claim rewards
    public Result<String> claimRewards(Account staker, String poolId) {
        StakingPoolDatum pool = findPool(poolId);
        Utxo poolUtxo = findPoolUtxo(pool);
        
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Update pool rewards
        StakingPoolDatum updatedPool = updatePoolRewards(pool, currentTime);
        
        // Get staker info
        byte[] stakerAddress = staker.getBaseAddress().getPaymentCredentialHash().orElseThrow();
        StakerInfo stakerInfo = updatedPool.getStakers().get(stakerAddress);
        
        if (stakerInfo == null || stakerInfo.getStakedAmount().equals(BigInteger.ZERO)) {
            throw new IllegalStateException("No staked amount found");
        }
        
        // Calculate total rewards
        BigInteger pendingRewards = calculatePendingRewards(stakerInfo, updatedPool);
        BigInteger totalRewards = stakerInfo.getPendingRewards().add(pendingRewards);
        
        if (totalRewards.equals(BigInteger.ZERO)) {
            throw new IllegalStateException("No rewards to claim");
        }
        
        // Update staker info
        stakerInfo.setPendingRewards(BigInteger.ZERO);
        stakerInfo.setRewardDebt(
            stakerInfo.getStakedAmount()
                .multiply(updatedPool.getAccumulatedRewardPerShare())
                .divide(BigInteger.valueOf(1e12))
        );
        updatedPool.getStakers().put(stakerAddress, stakerInfo);
        
        // Build claim transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        Asset rewardAsset = decodeAsset(pool.getRewardToken());
        
        ScriptTx claimTx = new ScriptTx()
            .collectFrom(poolUtxo, createClaimRedeemer(staker))
            .payTo(staker.baseAddress(), Amount.builder()
                .asset(rewardAsset.getPolicyId(), rewardAsset.getAssetName(), totalRewards)
                .build())
            .payTo(poolAddress, Amount.builder()
                .asset(decodeAsset(pool.getStakingToken()).getPolicyId(),
                       decodeAsset(pool.getStakingToken()).getAssetName(),
                       poolUtxo.getAmount().getAsset(
                           decodeAsset(pool.getStakingToken()).getPolicyId(),
                           decodeAsset(pool.getStakingToken()).getAssetName()
                       ).orElse(BigInteger.ZERO))
                .asset(rewardAsset.getPolicyId(), rewardAsset.getAssetName(),
                       poolUtxo.getAmount().getAsset(rewardAsset.getPolicyId(), 
                                                    rewardAsset.getAssetName())
                           .orElse(BigInteger.ZERO).subtract(totalRewards))
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(updatedPool.toPlutusData())
            .attachSpendingValidator(poolScript)
            .withRequiredSigners(staker.getBaseAddress());
            
        return txBuilder.compose(claimTx)
            .withSigner(SignerProviders.signerFrom(staker))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Unstake tokens
    public Result<String> unstake(Account staker, String poolId, BigInteger unstakeAmount) {
        StakingPoolDatum pool = findPool(poolId);
        Utxo poolUtxo = findPoolUtxo(pool);
        
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Update pool rewards
        StakingPoolDatum updatedPool = updatePoolRewards(pool, currentTime);
        
        // Get staker info
        byte[] stakerAddress = staker.getBaseAddress().getPaymentCredentialHash().orElseThrow();
        StakerInfo stakerInfo = updatedPool.getStakers().get(stakerAddress);
        
        if (stakerInfo == null || 
            stakerInfo.getStakedAmount().compareTo(unstakeAmount) < 0) {
            throw new IllegalStateException("Insufficient staked amount");
        }
        
        // Calculate and add pending rewards
        BigInteger pendingRewards = calculatePendingRewards(stakerInfo, updatedPool);
        BigInteger totalRewards = stakerInfo.getPendingRewards().add(pendingRewards);
        
        // Update staker info
        stakerInfo.setStakedAmount(stakerInfo.getStakedAmount().subtract(unstakeAmount));
        stakerInfo.setPendingRewards(BigInteger.ZERO);
        stakerInfo.setRewardDebt(
            stakerInfo.getStakedAmount()
                .multiply(updatedPool.getAccumulatedRewardPerShare())
                .divide(BigInteger.valueOf(1e12))
        );
        
        if (stakerInfo.getStakedAmount().equals(BigInteger.ZERO)) {
            updatedPool.getStakers().remove(stakerAddress);
        } else {
            updatedPool.getStakers().put(stakerAddress, stakerInfo);
        }
        
        // Update total staked
        updatedPool.setTotalStaked(updatedPool.getTotalStaked().subtract(unstakeAmount));
        
        // Build unstake transaction
        String poolAddress = AddressUtil.getEnterprise(poolScript, Networks.testnet()).toBech32();
        Asset stakingAsset = decodeAsset(pool.getStakingToken());
        Asset rewardAsset = decodeAsset(pool.getRewardToken());
        
        ScriptTx unstakeTx = new ScriptTx()
            .collectFrom(poolUtxo, createUnstakeRedeemer(staker, unstakeAmount))
            .payTo(staker.baseAddress(), Amount.builder()
                .asset(stakingAsset.getPolicyId(), stakingAsset.getAssetName(), unstakeAmount)
                .asset(rewardAsset.getPolicyId(), rewardAsset.getAssetName(), totalRewards)
                .build())
            .payTo(poolAddress, Amount.builder()
                .asset(stakingAsset.getPolicyId(), stakingAsset.getAssetName(),
                       poolUtxo.getAmount().getAsset(stakingAsset.getPolicyId(), 
                                                    stakingAsset.getAssetName())
                           .orElse(BigInteger.ZERO).subtract(unstakeAmount))
                .asset(rewardAsset.getPolicyId(), rewardAsset.getAssetName(),
                       poolUtxo.getAmount().getAsset(rewardAsset.getPolicyId(), 
                                                    rewardAsset.getAssetName())
                           .orElse(BigInteger.ZERO).subtract(totalRewards))
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(updatedPool.toPlutusData())
            .attachSpendingValidator(poolScript)
            .withRequiredSigners(staker.getBaseAddress());
            
        return txBuilder.compose(unstakeTx)
            .withSigner(SignerProviders.signerFrom(staker))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Update pool rewards calculation
    private StakingPoolDatum updatePoolRewards(StakingPoolDatum pool, long currentTime) {
        StakingPoolDatum updated = new StakingPoolDatum();
        copyPoolDatum(pool, updated);
        
        if (updated.getTotalStaked().equals(BigInteger.ZERO)) {
            updated.setLastUpdateTime(BigInteger.valueOf(currentTime));
            return updated;
        }
        
        // Calculate time elapsed
        long lastUpdate = Math.max(
            pool.getLastUpdateTime().longValue(),
            pool.getStartTime().longValue()
        );
        long endTime = Math.min(currentTime, pool.getEndTime().longValue());
        long timeElapsed = endTime - lastUpdate;
        
        if (timeElapsed > 0) {
            // Calculate new rewards
            BigInteger newRewards = pool.getRewardPerSecond()
                .multiply(BigInteger.valueOf(timeElapsed));
            
            // Update accumulated reward per share (multiplied by 1e12 for precision)
            BigInteger rewardPerShare = newRewards
                .multiply(BigInteger.valueOf(1e12))
                .divide(pool.getTotalStaked());
            
            updated.setAccumulatedRewardPerShare(
                pool.getAccumulatedRewardPerShare().add(rewardPerShare)
            );
        }
        
        updated.setLastUpdateTime(BigInteger.valueOf(currentTime));
        return updated;
    }
    
    // Calculate pending rewards for a staker
    private BigInteger calculatePendingRewards(StakerInfo staker, StakingPoolDatum pool) {
        BigInteger accumulatedReward = staker.getStakedAmount()
            .multiply(pool.getAccumulatedRewardPerShare())
            .divide(BigInteger.valueOf(1e12));
        
        return accumulatedReward.subtract(staker.getRewardDebt());
    }
}
```

## Governance Voting Contract

A complete on-chain governance system with proposals, voting, and execution.

### Governance Components

```java
// Governance proposal datum
@Constr(alternative = 0)
public class ProposalDatum implements Data<ProposalDatum> {
    @PlutusField(order = 0)
    private byte[] proposalId;
    
    @PlutusField(order = 1)
    private byte[] proposer;
    
    @PlutusField(order = 2)
    private BigInteger proposalType;        // 0=parameter, 1=upgrade, 2=treasury
    
    @PlutusField(order = 3)
    private PlutusData proposalData;        // Type-specific proposal data
    
    @PlutusField(order = 4)
    private BigInteger votingStart;
    
    @PlutusField(order = 5)
    private BigInteger votingEnd;
    
    @PlutusField(order = 6)
    private BigInteger executionDelay;      // Time delay after voting ends
    
    @PlutusField(order = 7)
    private BigInteger forVotes;
    
    @PlutusField(order = 8)
    private BigInteger againstVotes;
    
    @PlutusField(order = 9)
    private BigInteger quorumRequired;      // Minimum participation
    
    @PlutusField(order = 10)
    private BigInteger passingThreshold;    // Percentage needed to pass
    
    @PlutusField(order = 11)
    private BigInteger proposalState;       // 0=pending, 1=active, 2=passed, 3=failed, 4=executed
    
    @PlutusField(order = 12)
    private Map<byte[], VoteRecord> votes;  // Individual vote records
}

@Constr(alternative = 0)
public class VoteRecord implements Data<VoteRecord> {
    @PlutusField(order = 0)
    private BigInteger voteType;            // 0=against, 1=for
    
    @PlutusField(order = 1)
    private BigInteger votingPower;
    
    @PlutusField(order = 2)
    private BigInteger timestamp;
}

// Governance token staking for voting power
@Constr(alternative = 0)
public class GovernanceStakeDatum implements Data<GovernanceStakeDatum> {
    @PlutusField(order = 0)
    private byte[] staker;
    
    @PlutusField(order = 1)
    private BigInteger stakedAmount;
    
    @PlutusField(order = 2)
    private BigInteger stakingTime;
    
    @PlutusField(order = 3)
    private BigInteger votingPowerMultiplier; // Based on lock duration
    
    @PlutusField(order = 4)
    private BigInteger unlockTime;
    
    @PlutusField(order = 5)
    private List<byte[]> activeProposals;   // Proposals voted on
}
```

### Governance Implementation

```java
public class GovernanceContract {
    private final PlutusV2Script governanceScript;
    private final PlutusV2Script stakingScript;
    private final PlutusV2Script treasuryScript;
    private final QuickTxBuilder txBuilder;
    
    private static final BigInteger PROPOSAL_THRESHOLD = BigInteger.valueOf(100000); // Min tokens to propose
    private static final BigInteger DEFAULT_QUORUM = BigInteger.valueOf(4000); // 40%
    private static final BigInteger DEFAULT_PASSING = BigInteger.valueOf(5100); // 51%
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);
    
    // Create governance proposal
    public Result<String> createProposal(Account proposer, ProposalType type, 
                                       PlutusData proposalData, long votingDays,
                                       long executionDelayHours) {
        // Verify proposer has enough governance tokens
        BigInteger proposerBalance = getGovernanceTokenBalance(proposer);
        if (proposerBalance.compareTo(PROPOSAL_THRESHOLD) < 0) {
            throw new IllegalStateException("Insufficient governance tokens to propose");
        }
        
        long currentTime = System.currentTimeMillis() / 1000;
        
        ProposalDatum proposal = new ProposalDatum();
        proposal.setProposalId(generateProposalId());
        proposal.setProposer(proposer.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        proposal.setProposalType(BigInteger.valueOf(type.ordinal()));
        proposal.setProposalData(proposalData);
        proposal.setVotingStart(BigInteger.valueOf(currentTime + 3600)); // 1 hour delay
        proposal.setVotingEnd(BigInteger.valueOf(currentTime + 3600 + votingDays * 24 * 3600));
        proposal.setExecutionDelay(BigInteger.valueOf(executionDelayHours * 3600));
        proposal.setForVotes(BigInteger.ZERO);
        proposal.setAgainstVotes(BigInteger.ZERO);
        proposal.setQuorumRequired(DEFAULT_QUORUM);
        proposal.setPassingThreshold(DEFAULT_PASSING);
        proposal.setProposalState(BigInteger.ZERO); // Pending
        proposal.setVotes(new HashMap<>());
        
        String governanceAddress = AddressUtil.getEnterprise(
            governanceScript, Networks.testnet()
        ).toBech32();
        
        Tx proposalTx = new Tx()
            .payTo(governanceAddress, Amount.builder()
                .coin(BigInteger.valueOf(5000000)) // Min ADA
                .build())
            .attachDatum(proposal.toPlutusData())
            .from(proposer.baseAddress());
            
        return txBuilder.compose(proposalTx)
            .withSigner(SignerProviders.signerFrom(proposer))
            .completeAndSubmit();
    }
    
    // Cast vote on proposal
    public Result<String> castVote(ProposalDatum proposal, Account voter, 
                                 boolean voteFor, BigInteger votingPower) {
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Verify voting is active
        if (currentTime < proposal.getVotingStart().longValue() ||
            currentTime > proposal.getVotingEnd().longValue()) {
            throw new IllegalStateException("Voting not active");
        }
        
        // Verify voter hasn't already voted
        byte[] voterAddress = voter.getBaseAddress().getPaymentCredentialHash().orElseThrow();
        if (proposal.getVotes().containsKey(voterAddress)) {
            throw new IllegalStateException("Already voted");
        }
        
        // Verify voting power
        BigInteger actualVotingPower = calculateVotingPower(voter);
        if (actualVotingPower.compareTo(votingPower) < 0) {
            throw new IllegalArgumentException("Insufficient voting power");
        }
        
        // Find proposal UTXO
        Utxo proposalUtxo = findProposalUtxo(proposal);
        
        // Create vote record
        VoteRecord vote = new VoteRecord();
        vote.setVoteType(voteFor ? BigInteger.ONE : BigInteger.ZERO);
        vote.setVotingPower(votingPower);
        vote.setTimestamp(BigInteger.valueOf(currentTime));
        
        // Update proposal datum
        ProposalDatum updatedProposal = new ProposalDatum();
        copyProposalDatum(proposal, updatedProposal);
        
        if (voteFor) {
            updatedProposal.setForVotes(proposal.getForVotes().add(votingPower));
        } else {
            updatedProposal.setAgainstVotes(proposal.getAgainstVotes().add(votingPower));
        }
        
        updatedProposal.getVotes().put(voterAddress, vote);
        
        // Update proposal state if first vote
        if (proposal.getProposalState().equals(BigInteger.ZERO) &&
            currentTime >= proposal.getVotingStart().longValue()) {
            updatedProposal.setProposalState(BigInteger.ONE); // Active
        }
        
        // Build vote transaction
        String governanceAddress = AddressUtil.getEnterprise(
            governanceScript, Networks.testnet()
        ).toBech32();
        
        ScriptTx voteTx = new ScriptTx()
            .collectFrom(proposalUtxo, createVoteRedeemer(voter, voteFor, votingPower))
            .payTo(governanceAddress, Amount.builder()
                .coin(proposalUtxo.getAmount().getCoin())
                .build())
            .attachDatum(updatedProposal.toPlutusData())
            .attachSpendingValidator(governanceScript)
            .withRequiredSigners(voter.getBaseAddress());
            
        return txBuilder.compose(voteTx)
            .withSigner(SignerProviders.signerFrom(voter))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Finalize proposal after voting ends
    public Result<String> finalizeProposal(ProposalDatum proposal, Account finalizer) {
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Verify voting has ended
        if (currentTime <= proposal.getVotingEnd().longValue()) {
            throw new IllegalStateException("Voting still active");
        }
        
        // Verify not already finalized
        if (!proposal.getProposalState().equals(BigInteger.ONE)) {
            throw new IllegalStateException("Proposal not in active state");
        }
        
        // Calculate total votes
        BigInteger totalVotes = proposal.getForVotes().add(proposal.getAgainstVotes());
        BigInteger totalSupply = getTotalVotingPower();
        
        // Check quorum
        BigInteger participation = totalVotes.multiply(BASIS_POINTS).divide(totalSupply);
        boolean quorumMet = participation.compareTo(proposal.getQuorumRequired()) >= 0;
        
        // Check passing threshold
        boolean passed = false;
        if (quorumMet && totalVotes.compareTo(BigInteger.ZERO) > 0) {
            BigInteger forPercentage = proposal.getForVotes()
                .multiply(BASIS_POINTS)
                .divide(totalVotes);
            passed = forPercentage.compareTo(proposal.getPassingThreshold()) >= 0;
        }
        
        // Find proposal UTXO
        Utxo proposalUtxo = findProposalUtxo(proposal);
        
        // Update proposal datum
        ProposalDatum updatedProposal = new ProposalDatum();
        copyProposalDatum(proposal, updatedProposal);
        updatedProposal.setProposalState(passed ? BigInteger.valueOf(2) : BigInteger.valueOf(3));
        
        // Build finalize transaction
        String governanceAddress = AddressUtil.getEnterprise(
            governanceScript, Networks.testnet()
        ).toBech32();
        
        ScriptTx finalizeTx = new ScriptTx()
            .collectFrom(proposalUtxo, createFinalizeRedeemer(finalizer))
            .payTo(governanceAddress, Amount.builder()
                .coin(proposalUtxo.getAmount().getCoin())
                .build())
            .attachDatum(updatedProposal.toPlutusData())
            .attachSpendingValidator(governanceScript)
            .withRequiredSigners(finalizer.getBaseAddress());
            
        return txBuilder.compose(finalizeTx)
            .withSigner(SignerProviders.signerFrom(finalizer))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Execute passed proposal
    public Result<String> executeProposal(ProposalDatum proposal, Account executor) {
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Verify proposal passed
        if (!proposal.getProposalState().equals(BigInteger.valueOf(2))) {
            throw new IllegalStateException("Proposal not passed");
        }
        
        // Verify execution delay has passed
        long executionTime = proposal.getVotingEnd().longValue() + 
                            proposal.getExecutionDelay().longValue();
        if (currentTime < executionTime) {
            throw new IllegalStateException("Execution delay not met");
        }
        
        // Execute based on proposal type
        return switch (proposal.getProposalType().intValue()) {
            case 0 -> executeParameterChange(proposal, executor);
            case 1 -> executeUpgrade(proposal, executor);
            case 2 -> executeTreasuryProposal(proposal, executor);
            default -> throw new IllegalArgumentException("Unknown proposal type");
        };
    }
    
    // Stake governance tokens for voting power
    public Result<String> stakeForVoting(Account staker, BigInteger amount, long lockMonths) {
        // Calculate voting power multiplier based on lock duration
        BigInteger multiplier = calculateMultiplier(lockMonths);
        
        GovernanceStakeDatum stake = new GovernanceStakeDatum();
        stake.setStaker(staker.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        stake.setStakedAmount(amount);
        stake.setStakingTime(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        stake.setVotingPowerMultiplier(multiplier);
        stake.setUnlockTime(BigInteger.valueOf(
            System.currentTimeMillis() / 1000 + lockMonths * 30 * 24 * 3600
        ));
        stake.setActiveProposals(new ArrayList<>());
        
        String stakingAddress = AddressUtil.getEnterprise(
            stakingScript, Networks.testnet()
        ).toBech32();
        
        Asset govToken = getGovernanceToken();
        
        Tx stakeTx = new Tx()
            .payTo(stakingAddress, Amount.builder()
                .asset(govToken.getPolicyId(), govToken.getAssetName(), amount)
                .coin(BigInteger.valueOf(2000000)) // Min ADA
                .build())
            .attachDatum(stake.toPlutusData())
            .from(staker.baseAddress());
            
        return txBuilder.compose(stakeTx)
            .withSigner(SignerProviders.signerFrom(staker))
            .completeAndSubmit();
    }
    
    private Result<String> executeParameterChange(ProposalDatum proposal, Account executor) {
        // Decode parameter change data
        ParameterChangeData paramData = ParameterChangeData.fromPlutusData(
            (ConstrPlutusData) proposal.getProposalData()
        );
        
        // Find governance UTXO
        Utxo proposalUtxo = findProposalUtxo(proposal);
        
        // Update protocol parameters
        // This would interact with the protocol parameter update mechanism
        ScriptTx executeTx = new ScriptTx()
            .collectFrom(proposalUtxo, createExecuteRedeemer(executor))
            .attachSpendingValidator(governanceScript)
            .withRequiredSigners(executor.getBaseAddress());
        
        // Add parameter update certificate or interaction
        // Implementation depends on specific parameter update mechanism
        
        return txBuilder.compose(executeTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private Result<String> executeTreasuryProposal(ProposalDatum proposal, Account executor) {
        // Decode treasury proposal data
        TreasuryProposalData treasuryData = TreasuryProposalData.fromPlutusData(
            (ConstrPlutusData) proposal.getProposalData()
        );
        
        // Find treasury UTXO
        Utxo treasuryUtxo = findTreasuryUtxo(treasuryData.getAmount());
        
        // Execute treasury withdrawal
        ScriptTx treasuryTx = new ScriptTx()
            .collectFrom(treasuryUtxo, createTreasuryRedeemer(proposal))
            .payTo(decodeAddress(treasuryData.getRecipient()), 
                   Amount.ada(treasuryData.getAmount()))
            .attachSpendingValidator(treasuryScript);
        
        // Update proposal state
        ProposalDatum executedProposal = new ProposalDatum();
        copyProposalDatum(proposal, executedProposal);
        executedProposal.setProposalState(BigInteger.valueOf(4)); // Executed
        
        Utxo proposalUtxo = findProposalUtxo(proposal);
        String governanceAddress = AddressUtil.getEnterprise(
            governanceScript, Networks.testnet()
        ).toBech32();
        
        treasuryTx.collectFrom(proposalUtxo, createExecuteRedeemer(executor))
                  .payTo(governanceAddress, Amount.builder()
                      .coin(proposalUtxo.getAmount().getCoin())
                      .build())
                  .attachDatum(executedProposal.toPlutusData())
                  .attachSpendingValidator(governanceScript)
                  .withRequiredSigners(executor.getBaseAddress());
                  
        return txBuilder.compose(treasuryTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private BigInteger calculateVotingPower(Account voter) {
        // Find voter's staked governance tokens
        List<GovernanceStakeDatum> stakes = findVoterStakes(voter);
        
        return stakes.stream()
            .map(stake -> stake.getStakedAmount()
                .multiply(stake.getVotingPowerMultiplier())
                .divide(BigInteger.valueOf(100)))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }
    
    private BigInteger calculateMultiplier(long lockMonths) {
        // Linear multiplier: 100% for no lock, up to 300% for 36 months
        if (lockMonths == 0) return BigInteger.valueOf(100);
        if (lockMonths >= 36) return BigInteger.valueOf(300);
        
        return BigInteger.valueOf(100 + (lockMonths * 200 / 36));
    }
    
    public enum ProposalType {
        PARAMETER_CHANGE,
        PROTOCOL_UPGRADE,
        TREASURY_WITHDRAWAL
    }
}
```

## Cross-Contract Interactions

Advanced examples of contracts interacting with each other.

### Flash Loan Implementation

```java
// Flash loan provider contract
@Constr(alternative = 0)
public class FlashLoanDatum implements Data<FlashLoanDatum> {
    @PlutusField(order = 0)
    private byte[] poolId;
    
    @PlutusField(order = 1)
    private Map<byte[], BigInteger> availableLiquidity; // Token -> Amount
    
    @PlutusField(order = 2)
    private BigInteger flashLoanFee;        // Fee in basis points
    
    @PlutusField(order = 3)
    private BigInteger totalFeesCollected;
    
    @PlutusField(order = 4)
    private List<byte[]> authorizedCallers; // Whitelisted contracts
}

// Flash loan receiver interface
@Constr(alternative = 0)
public class FlashLoanCallbackDatum implements Data<FlashLoanCallbackDatum> {
    @PlutusField(order = 0)
    private byte[] borrower;
    
    @PlutusField(order = 1)
    private byte[] loanToken;
    
    @PlutusField(order = 2)
    private BigInteger loanAmount;
    
    @PlutusField(order = 3)
    private BigInteger feeAmount;
    
    @PlutusField(order = 4)
    private PlutusData callbackData;        // Arbitrary data for callback
}

public class FlashLoanProvider {
    private final PlutusV2Script flashLoanScript;
    private final QuickTxBuilder txBuilder;
    
    private static final BigInteger DEFAULT_FEE = BigInteger.valueOf(9); // 0.09%
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);
    
    // Execute flash loan with callback
    public Result<String> executeFlashLoan(Account borrower, Asset loanAsset, 
                                         BigInteger loanAmount, 
                                         PlutusV2Script callbackScript,
                                         PlutusData callbackData) {
        // Find flash loan pool
        FlashLoanDatum pool = findFlashLoanPool(loanAsset);
        Utxo poolUtxo = findPoolUtxo(pool);
        
        // Verify liquidity available
        BigInteger available = pool.getAvailableLiquidity()
            .get(encodeAsset(loanAsset).getBytes());
        if (available == null || available.compareTo(loanAmount) < 0) {
            throw new IllegalStateException("Insufficient liquidity");
        }
        
        // Calculate fee
        BigInteger feeAmount = loanAmount.multiply(pool.getFlashLoanFee())
            .divide(BASIS_POINTS);
        
        // Create callback datum
        FlashLoanCallbackDatum callback = new FlashLoanCallbackDatum();
        callback.setBorrower(borrower.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        callback.setLoanToken(encodeAsset(loanAsset).getBytes());
        callback.setLoanAmount(loanAmount);
        callback.setFeeAmount(feeAmount);
        callback.setCallbackData(callbackData);
        
        // Build flash loan transaction with inline callback
        String poolAddress = AddressUtil.getEnterprise(
            flashLoanScript, Networks.testnet()
        ).toBech32();
        String callbackAddress = AddressUtil.getEnterprise(
            callbackScript, Networks.testnet()
        ).toBech32();
        
        // This transaction does everything in one atomic operation
        ScriptTx flashLoanTx = new ScriptTx()
            // 1. Withdraw from flash loan pool
            .collectFrom(poolUtxo, createFlashLoanRedeemer(borrower, loanAmount))
            
            // 2. Send loan to callback contract
            .payTo(callbackAddress, Amount.builder()
                .asset(loanAsset.getPolicyId(), loanAsset.getAssetName(), loanAmount)
                .coin(BigInteger.valueOf(2000000))
                .build())
            .attachDatum(callback.toPlutusData())
            
            // 3. Execute callback logic (this would be expanded based on callback)
            // The callback contract would perform arbitrage, liquidation, etc.
            
            // 4. Return loan + fee to pool
            .payTo(poolAddress, Amount.builder()
                .asset(loanAsset.getPolicyId(), loanAsset.getAssetName(),
                       available.add(feeAmount)) // Original + fee
                .coin(poolUtxo.getAmount().getCoin())
                .build())
            .attachDatum(updatePoolDatum(pool, loanAsset, feeAmount).toPlutusData())
            
            .attachSpendingValidator(flashLoanScript)
            .withRequiredSigners(borrower.getBaseAddress());
            
        return txBuilder.compose(flashLoanTx)
            .withSigner(SignerProviders.signerFrom(borrower))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private FlashLoanDatum updatePoolDatum(FlashLoanDatum pool, Asset loanAsset, 
                                          BigInteger feeAmount) {
        FlashLoanDatum updated = new FlashLoanDatum();
        updated.setPoolId(pool.getPoolId());
        updated.setAvailableLiquidity(new HashMap<>(pool.getAvailableLiquidity()));
        updated.setFlashLoanFee(pool.getFlashLoanFee());
        updated.setTotalFeesCollected(pool.getTotalFeesCollected().add(feeAmount));
        updated.setAuthorizedCallers(pool.getAuthorizedCallers());
        
        // Update liquidity with collected fee
        byte[] assetKey = encodeAsset(loanAsset).getBytes();
        BigInteger currentLiquidity = updated.getAvailableLiquidity().get(assetKey);
        updated.getAvailableLiquidity().put(assetKey, currentLiquidity.add(feeAmount));
        
        return updated;
    }
}

// Example: Arbitrage bot using flash loans
public class ArbitrageBot {
    private final FlashLoanProvider flashLoanProvider;
    private final DecentralizedExchange dexA;
    private final DecentralizedExchange dexB;
    private final PlutusV2Script arbScript;
    
    public Result<String> executeArbitrage(Account trader, Asset tokenA, Asset tokenB,
                                         BigInteger amount, String poolA, String poolB) {
        // Calculate arbitrage opportunity
        ArbitrageOpportunity opportunity = calculateArbitrage(
            tokenA, tokenB, amount, poolA, poolB
        );
        
        if (opportunity.profit.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalStateException("No profitable arbitrage opportunity");
        }
        
        // Create arbitrage callback data
        ArbitrageCallbackData callbackData = new ArbitrageCallbackData();
        callbackData.setTokenA(encodeAsset(tokenA));
        callbackData.setTokenB(encodeAsset(tokenB));
        callbackData.setAmountIn(amount);
        callbackData.setPoolA(poolA.getBytes());
        callbackData.setPoolB(poolB.getBytes());
        callbackData.setExpectedProfit(opportunity.profit);
        
        // Execute flash loan with arbitrage callback
        return flashLoanProvider.executeFlashLoan(
            trader, tokenA, amount, arbScript, callbackData.toPlutusData()
        );
    }
    
    private ArbitrageOpportunity calculateArbitrage(Asset tokenA, Asset tokenB,
                                                   BigInteger amount,
                                                   String poolA, String poolB) {
        // Get prices from both DEXs
        BigInteger outputDexA = dexA.getOutputAmount(tokenA, tokenB, amount, poolA);
        BigInteger outputDexB = dexB.getOutputAmount(tokenB, tokenA, outputDexA, poolB);
        
        // Calculate profit after flash loan fee
        BigInteger flashLoanFee = amount.multiply(DEFAULT_FEE).divide(BASIS_POINTS);
        BigInteger profit = outputDexB.subtract(amount).subtract(flashLoanFee);
        
        return new ArbitrageOpportunity(profit, outputDexA, outputDexB);
    }
    
    private static class ArbitrageOpportunity {
        final BigInteger profit;
        final BigInteger amountAfterDexA;
        final BigInteger amountAfterDexB;
        
        ArbitrageOpportunity(BigInteger profit, BigInteger amountAfterDexA, 
                           BigInteger amountAfterDexB) {
            this.profit = profit;
            this.amountAfterDexA = amountAfterDexA;
            this.amountAfterDexB = amountAfterDexB;
        }
    }
}
```

## Testing and Integration

### Comprehensive Testing Suite

```java
@Test
public class AdvancedContractTests {
    private QuickTxBuilder txBuilder;
    private DecentralizedExchange dex;
    private NFTMarketplace marketplace;
    private StakingRewardsDistributor staking;
    private GovernanceContract governance;
    
    @Before
    public void setup() {
        // Initialize test environment
        BackendService backendService = new BlockfrostBackendService(
            Constants.BLOCKFROST_TESTNET_URL, 
            System.getenv("BLOCKFROST_API_KEY")
        );
        
        txBuilder = new QuickTxBuilder(backendService);
        
        // Deploy test contracts
        deployTestContracts();
    }
    
    @Test
    public void testDEXSwapWithSlippage() {
        // Create test pool
        Account creator = new Account(Networks.testnet());
        Asset tokenA = createTestToken("TESTA", 1000000);
        Asset tokenB = createTestToken("TESTB", 1000000);
        
        Result<String> poolResult = dex.createPool(
            creator, tokenA, tokenB, 
            BigInteger.valueOf(100000), BigInteger.valueOf(200000)
        );
        assertTrue(poolResult.isSuccessful());
        
        // Test swap with slippage protection
        Account trader = new Account(Networks.testnet());
        BigInteger swapAmount = BigInteger.valueOf(1000);
        BigInteger minOutput = BigInteger.valueOf(1950); // Expect ~1980, allow 1.5% slippage
        
        Result<String> swapResult = dex.swap(
            trader, tokenA.toString(), swapAmount,
            tokenB.toString(), minOutput, poolResult.getValue()
        );
        assertTrue(swapResult.isSuccessful());
        
        // Verify output amount
        BigInteger actualOutput = getTokenBalance(trader, tokenB);
        assertTrue(actualOutput.compareTo(minOutput) >= 0);
    }
    
    @Test
    public void testNFTAuctionWithExtension() {
        // Create NFT and auction
        Account seller = new Account(Networks.testnet());
        Asset nft = mintTestNFT(seller);
        
        Result<String> auctionResult = marketplace.createAuction(
            seller, nft, BigInteger.valueOf(100), // 100 ADA start
            BigInteger.valueOf(10), // 10 ADA increment
            1, // 1 day auction
            30 // 30 minute extension
        );
        assertTrue(auctionResult.isSuccessful());
        
        // Place bids
        Account bidder1 = new Account(Networks.testnet());
        Account bidder2 = new Account(Networks.testnet());
        
        // First bid
        marketplace.placeBid(getAuctionDatum(auctionResult.getValue()), 
                           bidder1, BigInteger.valueOf(100));
        
        // Wait until near end
        Thread.sleep(23 * 60 * 60 * 1000); // 23 hours
        
        // Last minute bid should extend auction
        AuctionDatum auction = getAuctionDatum(auctionResult.getValue());
        long originalEnd = auction.getAuctionEnd().longValue();
        
        marketplace.placeBid(auction, bidder2, BigInteger.valueOf(110));
        
        // Verify extension
        AuctionDatum updatedAuction = getAuctionDatum(auctionResult.getValue());
        assertTrue(updatedAuction.getAuctionEnd().longValue() > originalEnd);
    }
    
    @Test
    public void testStakingRewardsAccumulation() {
        // Create staking pool
        Account poolCreator = new Account(Networks.testnet());
        Asset stakingToken = createTestToken("STAKE", 10000000);
        Asset rewardToken = createTestToken("REWARD", 1000000);
        
        BigInteger rewardPerSecond = BigInteger.valueOf(10);
        Result<String> poolResult = staking.createStakingPool(
            poolCreator, stakingToken, rewardToken,
            BigInteger.valueOf(864000), // 10 days of rewards
            rewardPerSecond, 10, BigInteger.valueOf(100)
        );
        assertTrue(poolResult.isSuccessful());
        
        // Stake tokens
        Account staker1 = new Account(Networks.testnet());
        Account staker2 = new Account(Networks.testnet());
        
        staking.stake(staker1, poolResult.getValue(), BigInteger.valueOf(1000));
        Thread.sleep(5000); // Wait 5 seconds
        staking.stake(staker2, poolResult.getValue(), BigInteger.valueOf(2000));
        
        // Wait for rewards to accumulate
        Thread.sleep(10000); // 10 seconds
        
        // Check rewards
        BigInteger rewards1 = staking.getPendingRewards(staker1, poolResult.getValue());
        BigInteger rewards2 = staking.getPendingRewards(staker2, poolResult.getValue());
        
        // Staker1 should have ~150 tokens (15 seconds * 10/second)
        // Staker2 should have ~100 tokens (10 seconds * 10/second)
        assertTrue(rewards1.compareTo(BigInteger.valueOf(140)) > 0);
        assertTrue(rewards2.compareTo(BigInteger.valueOf(90)) > 0);
    }
    
    @Test
    public void testGovernanceProposalLifecycle() {
        // Create proposal
        Account proposer = new Account(Networks.testnet());
        fundAccountWithGovTokens(proposer, BigInteger.valueOf(200000));
        
        TreasuryProposalData treasuryData = new TreasuryProposalData();
        treasuryData.setRecipient(new Account(Networks.testnet()).baseAddress().getBytes());
        treasuryData.setAmount(BigInteger.valueOf(50000));
        treasuryData.setReason("Development grant");
        
        Result<String> proposalResult = governance.createProposal(
            proposer, ProposalType.TREASURY_WITHDRAWAL,
            treasuryData.toPlutusData(), 3, 24 // 3 day vote, 24h delay
        );
        assertTrue(proposalResult.isSuccessful());
        
        // Stake and vote
        List<Account> voters = createVoters(5);
        for (int i = 0; i < voters.size(); i++) {
            Account voter = voters.get(i);
            BigInteger stakeAmount = BigInteger.valueOf((i + 1) * 10000);
            
            governance.stakeForVoting(voter, stakeAmount, 6); // 6 month lock
            governance.castVote(
                getProposalDatum(proposalResult.getValue()),
                voter, i < 3, // First 3 vote for, last 2 against
                governance.calculateVotingPower(voter)
            );
        }
        
        // Wait for voting to end
        Thread.sleep(3 * 24 * 60 * 60 * 1000);
        
        // Finalize proposal
        governance.finalizeProposal(
            getProposalDatum(proposalResult.getValue()),
            proposer
        );
        
        // Verify proposal passed (60% voted for)
        ProposalDatum finalizedProposal = getProposalDatum(proposalResult.getValue());
        assertEquals(BigInteger.valueOf(2), finalizedProposal.getProposalState());
        
        // Wait for execution delay
        Thread.sleep(24 * 60 * 60 * 1000);
        
        // Execute proposal
        Result<String> executeResult = governance.executeProposal(
            finalizedProposal, proposer
        );
        assertTrue(executeResult.isSuccessful());
    }
    
    @Test
    public void testCrossContractFlashLoanArbitrage() {
        // Setup two DEXs with price difference
        createArbitrageOpportunity();
        
        // Execute flash loan arbitrage
        Account arbitrageur = new Account(Networks.testnet());
        Asset tokenA = getTestToken("TESTA");
        Asset tokenB = getTestToken("TESTB");
        
        ArbitrageBot bot = new ArbitrageBot(flashLoanProvider, dexA, dexB, arbScript);
        
        BigInteger initialBalance = getTokenBalance(arbitrageur, tokenA);
        
        Result<String> arbResult = bot.executeArbitrage(
            arbitrageur, tokenA, tokenB,
            BigInteger.valueOf(10000), "poolA", "poolB"
        );
        assertTrue(arbResult.isSuccessful());
        
        // Verify profit
        BigInteger finalBalance = getTokenBalance(arbitrageur, tokenA);
        assertTrue(finalBalance.compareTo(initialBalance) > 0);
    }
}
```

## Best Practices

1. **Security Considerations**
   - Always validate inputs and state transitions
   - Implement proper access controls
   - Use time locks for critical operations
   - Include emergency pause mechanisms

2. **Gas Optimization**
   - Minimize on-chain storage
   - Batch operations when possible
   - Use reference inputs for read-only data
   - Optimize datum structures

3. **Upgradability**
   - Design contracts with upgrade paths
   - Use proxy patterns for logic updates
   - Maintain backward compatibility
   - Test migrations thoroughly

4. **Integration Patterns**
   - Use standard interfaces for interoperability
   - Implement proper error handling
   - Emit events for off-chain monitoring
   - Document integration requirements

This comprehensive guide provides production-ready implementations of advanced smart contracts that can be deployed and integrated into real-world applications on the Cardano blockchain.