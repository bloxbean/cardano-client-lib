#!/bin/bash

# MPT Performance Benchmark Runner
# 
# This script runs the JMH benchmarks and generates performance reports
# to track the impact of refactoring changes on MPT performance.
#
# Usage:
#   ./benchmark.sh [baseline|compare|continuous]
#
# Modes:
#   baseline   - Run all benchmarks and save as baseline
#   compare    - Run benchmarks and compare with baseline
#   continuous - Run lightweight benchmark suite for CI

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$SCRIPT_DIR/benchmark-results"
BASELINE_FILE="$RESULTS_DIR/baseline.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Ensure results directory exists
mkdir -p "$RESULTS_DIR"

print_header() {
    echo -e "${GREEN}=== MPT Performance Benchmarking ===${NC}"
    echo "Timestamp: $(date)"
    echo "Git commit: $(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
    echo "Git branch: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')"
    echo ""
}

run_benchmarks() {
    local mode="$1"
    local output_file="$2"
    local benchmark_pattern="${3:-.*}"
    
    echo -e "${YELLOW}Running benchmarks (pattern: $benchmark_pattern)...${NC}"
    
    cd "$PROJECT_DIR"
    
    if [[ "$mode" == "continuous" ]]; then
        # Lighter settings for CI/continuous integration
        ./gradlew :state-trees:jmh \
            -Pjmh.include="$benchmark_pattern" \
            -Pjmh.wi=1 \
            -Pjmh.i=2 \
            -Pjmh.f=1 \
            -Pjmh.t=1 \
            -Pjmh.rff="$output_file"
    else
        # Full benchmark settings
        ./gradlew :state-trees:jmh \
            -Pjmh.include="$benchmark_pattern" \
            -Pjmh.rff="$output_file"
    fi
    
    echo -e "${GREEN}Benchmarks completed. Results saved to: $output_file${NC}"
}

compare_results() {
    local current_file="$1"
    local baseline_file="$2"
    
    if [[ ! -f "$baseline_file" ]]; then
        echo -e "${YELLOW}No baseline found at $baseline_file${NC}"
        echo "Run './benchmark.sh baseline' first to establish baseline metrics."
        return 1
    fi
    
    echo -e "${YELLOW}Comparing results with baseline...${NC}"
    
    # Simple comparison using jq (if available)
    if command -v jq >/dev/null 2>&1; then
        echo "Performance comparison:"
        echo "Benchmark | Current (ops/s) | Baseline (ops/s) | Change"
        echo "---------|----------------|----------------|-------"
        
        # Extract and compare key metrics
        jq -r '.[] | select(.benchmark) | "\(.benchmark)|\(.primaryMetric.score)|\(.primaryMetric.scoreUnit)"' "$current_file" > /tmp/current_metrics.txt
        jq -r '.[] | select(.benchmark) | "\(.benchmark)|\(.primaryMetric.score)|\(.primaryMetric.scoreUnit)"' "$baseline_file" > /tmp/baseline_metrics.txt
        
        while IFS='|' read -r benchmark current_score unit; do
            baseline_score=$(grep "^$benchmark|" /tmp/baseline_metrics.txt | cut -d'|' -f2 || echo "0")
            if [[ "$baseline_score" != "0" && "$baseline_score" != "" ]]; then
                change=$(echo "scale=2; ($current_score - $baseline_score) / $baseline_score * 100" | bc -l 2>/dev/null || echo "N/A")
                printf "%-40s | %12.2f | %12.2f | %6s%%\n" "$benchmark" "$current_score" "$baseline_score" "$change"
            else
                printf "%-40s | %12.2f | %12s | %6s\n" "$benchmark" "$current_score" "N/A" "NEW"
            fi
        done < /tmp/current_metrics.txt
        
        # Cleanup
        rm -f /tmp/current_metrics.txt /tmp/baseline_metrics.txt
    else
        echo "Install 'jq' and 'bc' for detailed performance comparison."
        echo "Current results: $current_file"
        echo "Baseline results: $baseline_file"
    fi
}

run_baseline() {
    print_header
    echo -e "${YELLOW}Running baseline benchmark suite...${NC}"
    
    run_benchmarks "full" "$BASELINE_FILE"
    
    echo -e "${GREEN}Baseline established!${NC}"
    echo "Results saved to: $BASELINE_FILE"
}

run_compare() {
    print_header
    echo -e "${YELLOW}Running comparison benchmark suite...${NC}"
    
    local current_file="$RESULTS_DIR/current-$(date +%Y%m%d-%H%M%S).json"
    run_benchmarks "full" "$current_file"
    
    compare_results "$current_file" "$BASELINE_FILE"
}

run_continuous() {
    print_header
    echo -e "${YELLOW}Running continuous integration benchmark suite...${NC}"
    
    local ci_file="$RESULTS_DIR/ci-$(date +%Y%m%d-%H%M%S).json"
    
    # Run core benchmarks with lighter settings
    run_benchmarks "continuous" "$ci_file" ".*Core.*"
    
    # Basic performance regression check
    if [[ -f "$BASELINE_FILE" ]]; then
        echo -e "${YELLOW}Checking for performance regressions...${NC}"
        compare_results "$ci_file" "$BASELINE_FILE"
    else
        echo -e "${YELLOW}No baseline available for regression check.${NC}"
    fi
}

show_help() {
    echo "Usage: $0 [mode] [options]"
    echo ""
    echo "Modes:"
    echo "  baseline   - Run full benchmark suite and save as baseline"
    echo "  compare    - Run full benchmark suite and compare with baseline"
    echo "  continuous - Run lightweight benchmark suite for CI"
    echo "  help       - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 baseline                    # Establish performance baseline"
    echo "  $0 compare                     # Compare current performance with baseline"
    echo "  $0 continuous                  # Quick CI benchmark check"
    echo ""
    echo "Results are saved to: $RESULTS_DIR"
}

# Main execution
case "${1:-help}" in
    "baseline")
        run_baseline
        ;;
    "compare")
        run_compare
        ;;
    "continuous")
        run_continuous
        ;;
    "help"|*)
        show_help
        ;;
esac