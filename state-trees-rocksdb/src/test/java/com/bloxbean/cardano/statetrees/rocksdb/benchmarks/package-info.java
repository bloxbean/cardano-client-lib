/**
 * Performance benchmarks for RocksDB modernization impact assessment.
 * 
 * <p>This package contains comprehensive performance benchmarks designed to
 * establish baseline metrics and measure the performance impact of modernization
 * changes. The benchmarks help ensure that code quality improvements don't
 * introduce performance regressions.</p>
 * 
 * <p><b>Benchmark Categories:</b></p>
 * <ul>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.benchmarks.RocksDbKeyBenchmark} - 
 *       Type-safe key system performance</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.benchmarks.ResourceManagementBenchmark} - 
 *       Resource management overhead</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.benchmarks.RocksDbModernizationBenchmarkSuite} - 
 *       Comprehensive benchmark suite</li>
 * </ul>
 * 
 * <p><b>Usage Patterns:</b></p>
 * <ul>
 *   <li>Baseline measurement before modernization changes</li>
 *   <li>Regression testing after each modernization phase</li>
 *   <li>Performance comparison between old and new implementations</li>
 *   <li>Memory usage analysis for modernized components</li>
 * </ul>
 * 
 * <p><b>Running Benchmarks:</b></p>
 * <pre>{@code
 * // Run complete benchmark suite
 * RocksDbModernizationBenchmarkSuite.main(new String[]{});
 * 
 * // Run individual benchmarks
 * RocksDbKeyBenchmark keyBench = new RocksDbKeyBenchmark();
 * keyBench.runSimplePerformanceTest();
 * 
 * ResourceManagementBenchmark resBench = new ResourceManagementBenchmark();
 * resBench.runSimplePerformanceTest();
 * }</pre>
 * 
 * <p><b>JMH Integration:</b> The benchmarks are designed to work with JMH
 * (Java Microbenchmark Harness) when available, but also provide simple
 * performance test runners for environments without JMH. The JMH annotations
 * are commented out but can be enabled when the JMH framework is added to
 * the project dependencies.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
package com.bloxbean.cardano.statetrees.rocksdb.benchmarks;