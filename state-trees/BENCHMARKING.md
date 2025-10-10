# MPT Performance Benchmarking

This document describes the performance benchmarking setup for the Merkle Patricia Trie implementation.

## Overview

The benchmarking infrastructure uses JMH (Java Microbenchmark Harness) to measure performance and track the impact of refactoring changes. The goal is to ensure no performance regressions during the code modernization process.

## Benchmark Suites

### 1. MptCoreBenchmark

Core MPT operation benchmarks that measure fundamental performance:

- **sequentialInserts**: Throughput of insert operations into empty trie
- **randomReads**: Random access read performance from populated trie  
- **deletionsWithCompression**: Delete operations with tree compression
- **prefixScanning**: Prefix scan operations with various result sizes
- **mixedWorkload**: Realistic workload mix (60% reads, 30% inserts, 10% deletes)
- **keyOverwrites**: Performance when updating existing keys
- **stateReconstruction**: Trie reconstruction from root hash
- **largeValueOperations**: Operations with larger (1KB) values

### 2. TypeSafeWrappersBenchmark

Benchmarks to verify zero-overhead abstraction for type-safe wrappers:

- **rawHashOperations** vs **nodeHashOperations**: Raw byte[] vs NodeHash wrapper
- **rawNibbleOperations** vs **nibblePathOperations**: Raw int[] vs NibblePath wrapper
- **rawStorageOperations** vs **persistenceLayerOperations**: Direct storage vs NodePersistence
- **objectCreationOverhead**: Allocation overhead of wrapper objects
- **hexStringConversion**: String conversion performance comparison

## Running Benchmarks

### Quick Test (Individual Benchmark)
```bash
# Run specific benchmark pattern
./gradlew :state-trees:jmh -Pjmh.include=".*sequentialInserts.*"
```

### Full Benchmark Suite
```bash
# Run all benchmarks (takes ~10-15 minutes)
./gradlew :state-trees:jmh
```

### Using the Benchmark Script
```bash
# Establish baseline metrics
./benchmark.sh baseline

# Compare current performance with baseline
./benchmark.sh compare

# Quick CI check (lightweight)
./benchmark.sh continuous
```

## Benchmark Configuration

The benchmarks use the following settings for consistent results:

- **Warmup**: 3 iterations, 1 second each
- **Measurement**: 5 iterations, 2 seconds each  
- **Forks**: 1 (single JVM instance)
- **Threads**: 1 (single-threaded execution)
- **JVM**: Server mode with 2GB heap (-server -Xms2g -Xmx2g)

## Understanding Results

### Throughput Metrics
- **ops/s**: Operations per second (higher is better)
- Typical ranges for reference:
  - Sequential inserts (100 items): ~1000 ops/s
  - Sequential inserts (1000 items): ~70 ops/s  
  - Sequential inserts (5000 items): ~10 ops/s

### Performance Scaling
The MPT shows expected O(log n) scaling behavior:
- Performance decreases as trie size increases
- Insertion cost grows with tree depth
- Prefix scans scale with result set size

### Wrapper Overhead
Type-safe wrappers should show minimal overhead:
- NodeHash operations: <5% overhead vs raw bytes
- NibblePath operations: <10% overhead vs raw arrays
- NodePersistence: ~0% overhead (same underlying operations)

## Baseline Tracking

### Establishing Baseline
Run the baseline script after major milestones:
```bash
./benchmark.sh baseline
```

This saves results to `benchmark-results/baseline.json`.

### Performance Regression Detection
Compare current performance with baseline:
```bash
./benchmark.sh compare
```

Look for:
- **>20% degradation**: Investigate immediately
- **10-20% degradation**: Review and consider optimization
- **<10% variation**: Normal JVM measurement noise

### Continuous Integration
Use the continuous mode for regular checks:
```bash
./benchmark.sh continuous
```

This runs a lighter benchmark suite suitable for CI pipelines.

## Profiling and Analysis

### Enable Profilers
Add profiling to identify bottlenecks:
```bash
./gradlew :state-trees:jmh -Pjmh.prof=gc,stack
```

Available profilers:
- **gc**: Garbage collection overhead
- **stack**: Stack sampling profiler
- **comp**: JIT compiler profiling

### Analysis Tips
1. **Warm-up Effects**: Ensure sufficient warm-up iterations
2. **GC Impact**: Monitor allocation rates and GC pressure  
3. **JIT Compilation**: Check for deoptimization issues
4. **Memory Access**: Consider cache locality for large datasets

## File Locations

- **Benchmarks**: `src/jmh/java/com/bloxbean/cardano/statetrees/mpt/`
- **Results**: `build/jmh-results.json`
- **Baseline**: `benchmark-results/baseline.json`
- **Configuration**: `build.gradle` (jmh section)

## Best Practices

### Before Refactoring
1. Run baseline benchmarks
2. Save results with descriptive names
3. Document current performance characteristics

### During Refactoring  
1. Run benchmarks frequently (after major changes)
2. Compare with baseline metrics
3. Investigate any regressions immediately

### After Refactoring
1. Run full benchmark suite
2. Compare final results with baseline
3. Update baseline if improvements are confirmed
4. Document performance impact in commit messages

## Troubleshooting

### Common Issues
- **OutOfMemoryError**: Increase JVM heap size in build.gradle
- **Inconsistent Results**: Ensure system is idle during benchmarking
- **Missing Benchmarks**: Check that classes are in `mpt` package
- **Compilation Errors**: Verify JMH plugin and dependencies

### Environment Requirements
- **Java 11+**: Required for JMH and project compatibility
- **Memory**: Minimum 4GB RAM recommended for benchmarking
- **CPU**: Single-core performance matters (benchmarks use 1 thread)
- **System Load**: Run on idle system for consistent results

## Integration with CI/CD

### GitHub Actions Example
```yaml
- name: Run Performance Benchmarks
  run: ./state-trees/benchmark.sh continuous
  
- name: Upload Benchmark Results
  uses: actions/upload-artifact@v3
  with:
    name: benchmark-results
    path: state-trees/benchmark-results/
```
