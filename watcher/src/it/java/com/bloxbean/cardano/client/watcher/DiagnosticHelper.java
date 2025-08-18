package com.bloxbean.cardano.client.watcher;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;

/**
 * Helper class for diagnosing failures in integration tests.
 */
public class DiagnosticHelper {
    
    /**
     * Print detailed diagnostic information about a failed BasicWatchHandle.
     * 
     * @param handle the BasicWatchHandle to diagnose
     */
    public static void printFailureDiagnostics(BasicWatchHandle handle) {
        if (handle == null) {
            System.out.println("❌ Handle is null - cannot diagnose");
            return;
        }
        
        WatchStatus status = handle.getStatus();
        System.out.println("\n🔍 === DETAILED FAILURE DIAGNOSTICS ===");
        System.out.println("📊 Chain Status: " + status);
        System.out.println("🆔 Chain ID: " + handle.getChainId());
        
        if (status == WatchStatus.FAILED) {
            // Check for overall chain error
            handle.getError().ifPresentOrElse(
                error -> {
                    System.out.println("\n📋 CHAIN-LEVEL ERROR:");
                    System.out.println("  🚨 Type: " + error.getClass().getSimpleName());
                    System.out.println("  💬 Message: " + error.getMessage());
                    
                    if (error.getCause() != null) {
                        System.out.println("  🔍 Root Cause: " + error.getCause().getClass().getSimpleName());
                        System.out.println("  💭 Root Message: " + error.getCause().getMessage());
                    }
                    
                    System.out.println("\n📚 FULL STACK TRACE:");
                    error.printStackTrace();
                },
                () -> System.out.println("\n⚠️  No chain-level error recorded")
            );
            
            // Check individual step results
            System.out.println("\n🔧 STEP-BY-STEP ANALYSIS:");
            if (handle.getStepResults().isEmpty()) {
                System.out.println("  📭 No step results recorded");
            } else {
                handle.getStepResults().forEach((stepId, stepResult) -> {
                    System.out.println("  📦 Step '" + stepId + "':");
                    System.out.println("    🎯 Status: " + (stepResult.isSuccessful() ? "✅ SUCCESS" : "❌ FAILED"));
                    System.out.println("    🕐 Completed: " + stepResult.getCompletedAt());
                    
                    if (stepResult.getTransactionHash() != null) {
                        System.out.println("    🔗 TX Hash: " + stepResult.getTransactionHash());
                    }
                    
                    if (!stepResult.isSuccessful() && stepResult.getError() != null) {
                        Throwable stepError = stepResult.getError();
                        System.out.println("    ❌ Step Error: " + stepError.getClass().getSimpleName());
                        System.out.println("    💬 Message: " + stepError.getMessage());
                        
                        if (stepError.getCause() != null) {
                            System.out.println("    🔍 Root Cause: " + stepError.getCause().getClass().getSimpleName());
                            System.out.println("    💭 Root Message: " + stepError.getCause().getMessage());
                        }
                        
                        System.out.println("    📚 Step Stack Trace:");
                        stepError.printStackTrace();
                    }
                    
                    System.out.println();
                });
            }
            
            // Show step statuses
            System.out.println("📊 STEP STATUS SUMMARY:");
            if (handle.getStepStatuses().isEmpty()) {
                System.out.println("  📭 No step statuses recorded");
            } else {
                handle.getStepStatuses().forEach((stepId, stepStatus) -> {
                    System.out.println("  🏷️  Step '" + stepId + "': " + stepStatus);
                });
            }
            
            // Show timing information
            System.out.println("\n⏱️  TIMING INFORMATION:");
            System.out.println("  🚀 Started: " + handle.getStartedAt());
            handle.getCompletedAt().ifPresentOrElse(
                completed -> System.out.println("  🏁 Completed: " + completed),
                () -> System.out.println("  ⏳ Still running or not completed")
            );
            
        } else {
            System.out.println("ℹ️  Status is not FAILED (" + status + "), no detailed diagnostics needed");
        }
        
        System.out.println("=== END DIAGNOSTICS ===\n");
    }
    
    /**
     * Print a summary of what likely went wrong based on common patterns.
     * 
     * @param handle the BasicWatchHandle to analyze
     */
    public static void printLikelyCauses(BasicWatchHandle handle) {
        System.out.println("🔍 === LIKELY CAUSES ANALYSIS ===");
        
        if (handle.getError().isPresent()) {
            Throwable error = handle.getError().get();
            String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
            String errorType = error.getClass().getSimpleName().toLowerCase();
            
            if (errorMessage.contains("connection") || errorMessage.contains("timeout")) {
                System.out.println("🌐 LIKELY CAUSE: Network/Connection Issue");
                System.out.println("   • Check if Yaci DevKit is running at " + "http://localhost:8080");
                System.out.println("   • Verify network connectivity");
                System.out.println("   • Check firewall settings");
            }
            
            if (errorMessage.contains("insufficient") || errorMessage.contains("balance")) {
                System.out.println("💰 LIKELY CAUSE: Insufficient Balance");
                System.out.println("   • Check sender account has enough ADA");
                System.out.println("   • Verify UTXOs are available");
                System.out.println("   • Consider transaction fees");
            }
            
            if (errorMessage.contains("signature") || errorMessage.contains("witness")) {
                System.out.println("✍️ LIKELY CAUSE: Signature/Witness Issue");
                System.out.println("   • Verify signer is correct for the address");
                System.out.println("   • Check key derivation paths");
                System.out.println("   • Ensure private key matches address");
            }
            
            if (errorMessage.contains("script") || errorMessage.contains("plutus")) {
                System.out.println("📜 LIKELY CAUSE: Script Execution Issue");
                System.out.println("   • Check script logic and constraints");
                System.out.println("   • Verify script inputs and redeemers");
                System.out.println("   • Check execution units");
            }
            
            if (errorType.contains("unsupported") || errorMessage.contains("not implemented")) {
                System.out.println("🚧 LIKELY CAUSE: Feature Not Implemented");
                System.out.println("   • This is expected in MVP - feature not fully implemented yet");
                System.out.println("   • Check if you're hitting a stub method");
                System.out.println("   • Look for TODO comments in stack trace");
            }
            
            if (errorMessage.contains("api") || errorMessage.contains("endpoint")) {
                System.out.println("🔌 LIKELY CAUSE: API/Backend Issue");
                System.out.println("   • Check Yaci DevKit API compatibility");
                System.out.println("   • Verify endpoint URLs are correct");
                System.out.println("   • Check API response format");
            }
        }
        
        System.out.println("=== END LIKELY CAUSES ===\n");
    }
}