package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Intention for minting assets with native scripts.
 * Supports minting single or multiple assets with optional receiver addresses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("minting")
public class MintingIntention implements TxIntention {
    
    @JsonProperty("policy_script")
    private NativeScript policyScript;
    
    @JsonProperty("assets")
    private List<Asset> assets;
    
    @JsonProperty("receiver")
    private String receiver; // Optional receiver address
    
    // Legacy constructors for backward compatibility
    public MintingIntention(NativeScript policyScript, Asset asset) {
        this.policyScript = policyScript;
        this.assets = List.of(asset);
        this.receiver = null;
    }
    
    public MintingIntention(NativeScript policyScript, Asset asset, String receiver) {
        this.policyScript = policyScript;
        this.assets = List.of(asset);
        this.receiver = receiver;
    }
    
    public MintingIntention(NativeScript policyScript, List<Asset> assets) {
        this.policyScript = policyScript;
        this.assets = assets;
        this.receiver = null;
    }
    
    @Override
    public String getType() {
        return "minting";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve receiver from variables if it's a variable
        String resolvedReceiver = receiver != null ? 
            IntentionHelper.resolveVariable(receiver, variables) : null;
        
        // Smart dispatch based on whether receiver is specified - calling tx methods directly
        if (resolvedReceiver != null) {
            // Mint to specific receiver
            if (assets.size() == 1) {
                tx.mintAssets(policyScript, assets.get(0), resolvedReceiver);
            } else {
                tx.mintAssets(policyScript, assets, resolvedReceiver);
            }
        } else {
            // Mint without specific receiver (will use payToAddress methods)
            if (assets.size() == 1) {
                tx.mintAssets(policyScript, assets.get(0));
            } else {
                tx.mintAssets(policyScript, assets);
            }
        }
    }
    
    // Factory methods for clean API
    public static MintingIntention mint(NativeScript policyScript, Asset asset) {
        return new MintingIntention(policyScript, asset);
    }
    
    public static MintingIntention mint(NativeScript policyScript, List<Asset> assets) {
        return new MintingIntention(policyScript, assets);
    }
    
    public static MintingIntention burn(NativeScript policyScript, Asset asset) {
        return new MintingIntention(policyScript, asset);
    }
    
    public static MintingIntention burn(NativeScript policyScript, List<Asset> assets) {
        return new MintingIntention(policyScript, assets);
    }
}