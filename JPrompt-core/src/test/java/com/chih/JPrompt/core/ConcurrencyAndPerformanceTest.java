package com.chih.JPrompt.core;

import com.chih.JPrompt.core.impl.FilePromptSource;
import com.chih.JPrompt.core.support.FileResource;
import com.chih.JPrompt.core.support.PromptParser;
import com.chih.JPrompt.core.support.PromptObjectMapperFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 并发和性能测试
 *
 * 测试 JPrompt 核心组件的并发安全性和性能表现，包括：
 * - 并发访问安全性
 * - 高并发下的性能表现
 * - 热更新的并发处理
 * - 资源争用情况
 */
@DisplayName("并发和性能测试")
class ConcurrencyAndPerformanceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("PromptParser 并发解析测试")
    void testPromptParserConcurrentParsing() throws Exception {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("concurrent-test.yaml");
        String content = """
            concurrent-test:
              id: concurrent-test
              template: Hello {{name}}!
              description: Concurrent parsing test
            """;
        Files.write(testFile, content.getBytes());

        int threadCount = 20;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        /* 并发解析测试 */
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        Map<String, ?> result = PromptParser.parse(
                            Files.newInputStream(testFile), testFile.toString()
                        );
                        if (result != null && result.containsKey("concurrent-test")) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        /* 验证结果 */
        int expectedSuccess = threadCount * iterationsPerThread;
        assertThat(successCount.get()).isEqualTo(expectedSuccess);
        assertThat(errorCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("PromptObjectMapperFactory 并发创建测试")
    void testPromptObjectMapperFactoryConcurrentCreation() throws Exception {
        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger yamlMapperCount = new AtomicInteger(0);
        AtomicInteger jsonMapperCount = new AtomicInteger(0);
        AtomicInteger lenientMapperCount = new AtomicInteger(0);

        /* 并发创建 ObjectMapper */
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        /* 创建不同类型的 mapper */
                        switch (threadIndex % 3) {
                            case 0:
                                var yamlMapper = PromptObjectMapperFactory.createYamlMapper();
                                if (yamlMapper != null) yamlMapperCount.incrementAndGet();
                                break;
                            case 1:
                                var jsonMapper = PromptObjectMapperFactory.createJsonMapper();
                                if (jsonMapper != null) jsonMapperCount.incrementAndGet();
                                break;
                            case 2:
                                var lenientMapper = PromptObjectMapperFactory.createLenientMapper();
                                if (lenientMapper != null) lenientMapperCount.incrementAndGet();
                                break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        /* 验证所有 mapper 都被成功创建 */
        int expectedPerType = (threadCount / 3) * iterationsPerThread;
        assertThat(yamlMapperCount.get()).isGreaterThan(expectedPerType - 10); /* 允许小范围误差 */
        assertThat(jsonMapperCount.get()).isGreaterThan(expectedPerType - 10);
        assertThat(lenientMapperCount.get()).isGreaterThan(expectedPerType - 10);
    }

    @Test
    @DisplayName("FileResource 并发访问测试")
    void testFileResourceConcurrentAccess() throws Exception {
        /* 创建多个测试文件 */
        List<Path> testFiles = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Path file = tempDir.resolve("test" + i + ".yaml");
            String content = String.format("""
                id: test%d
                template: Template %d content
                """, i, i);
            Files.write(file, content.getBytes());
            testFiles.add(file);
        }

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        /* 并发访问 FileResource */
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (Path testFile : testFiles) {
                        FileResource resource = FileResource.fromFile(testFile);

                        /* 测试各种操作 */
                        if (resource.exists() && resource.getFilename() != null) {
                            try (var input = resource.getInputStream()) {
                                /* 读取流内容 */
                                if (input.read() != -1) {
                                    successCount.incrementAndGet();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        /* 验证结果 */
        int expectedSuccess = threadCount * testFiles.size();
        assertThat(successCount.get()).isGreaterThan(expectedSuccess * 80 / 100); /* 允许80%以上成功 */
        assertThat(errorCount.get()).isLessThan(expectedSuccess / 10); /* 错误率低于10% */
    }

    @Test
    @DisplayName("FilePromptSource 并发读取测试")
    void testFilePromptSourceConcurrentRead() throws Exception {
        /* 创建测试文件 */
        for (int i = 0; i < 10; i++) {
            Path file = tempDir.resolve("prompt" + i + ".yaml");
            String content = String.format("""
                prompt%d:
                  id: prompt%d
                  template: This is prompt %d
                  description: Description for prompt %d
                """, i, i, i, i);
            Files.write(file, content.getBytes());
        }

        FilePromptSource source = new FilePromptSource(tempDir.toString());

        try {
            int threadCount = 30;
            int iterationsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger readCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            /* 并发读取 */
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < iterationsPerThread; j++) {
                            /* 测试 loadAll */
                            Map<String, ?> prompts = source.loadAll();
                            if (prompts != null && !prompts.isEmpty()) {
                                readCount.incrementAndGet();
                            }

                            /* 测试 load */
                            for (int k = 0; k < 10; k++) {
                                Object prompt = source.load("prompt" + k);
                                if (prompt != null) {
                                    readCount.incrementAndGet();
                                }
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            /* 验证结果 */
            int expectedReads = threadCount * iterationsPerThread * (1 + 10); /* loadAll + 10 * load */
            assertThat(readCount.get()).isGreaterThan(expectedReads * 80 / 100);
            assertThat(errorCount.get()).isLessThan(expectedReads / 10);

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("PromptParser 性能基准测试")
    void testPromptParserPerformanceBenchmark() throws Exception {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("benchmark.yaml");
        String content = """
            benchmark-test:
              id: benchmark-test
              template: Hello {{name}}! Welcome to {{place}}.
              description: Performance benchmark test
              parameters:
                name: string
                place: string
            """;
        Files.write(testFile, content.getBytes());

        int warmupIterations = 1000;
        int benchmarkIterations = 10000;

        /* 预热 */
        for (int i = 0; i < warmupIterations; i++) {
            try (var input = Files.newInputStream(testFile)) {
                PromptParser.parse(input, testFile.toString());
            }
        }

        /* 性能基准测试 */
        long startTime = System.nanoTime();
        for (int i = 0; i < benchmarkIterations; i++) {
            try (var input = Files.newInputStream(testFile)) {
                PromptParser.parse(input, testFile.toString());
            }
        }
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgTimePerParse = (double) durationMs / benchmarkIterations;

        /* 性能断言：平均每次解析应该小于1ms */
        assertThat(avgTimePerParse).isLessThan(1.0);
        assertThat(durationMs).isLessThan(5000); /* 总时间应该小于5秒 */
    }

    @Test
    @DisplayName("FileResource 并发创建性能测试")
    void testFileResourceConcurrentCreationPerformance() throws Exception {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("perf-test.yaml");
        String content = "id: perf-test\ntemplate: performance test";
        Files.write(testFile, content.getBytes());

        int threadCount = 10;
        int creationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        /* 并发创建 FileResource */
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < creationsPerThread; j++) {
                        FileResource resource = FileResource.fromFile(testFile);
                        assertThat(resource).isNotNull();
                        assertThat(resource.exists()).isTrue();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        int totalCreations = threadCount * creationsPerThread;
        double avgTimePerCreation = (double) durationMs / totalCreations;

        /* 性能断言：平均每次创建应该小于1ms */
        assertThat(avgTimePerCreation).isLessThan(1.0);
        assertThat(durationMs).isLessThan(5000); /* 总时间应该小于5秒 */
    }

    @Test
    @DisplayName("并发热更新模拟测试")
    void testConcurrentHotUpdateSimulation() throws Exception {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("hotupdate.yaml");
        String initialContent = """
            id: hotupdate-test
            template: Initial content
            version: 1
            """;
        Files.write(testFile, initialContent.getBytes());

        FilePromptSource source = new FilePromptSource(testFile.toString());

        try {
            int readerThreads = 15;
            int writerThreads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
            CountDownLatch latch = new CountDownLatch(readerThreads + writerThreads);
            AtomicInteger readCount = new AtomicInteger(0);
            AtomicInteger writeCount = new AtomicInteger(0);

            /* 启动读取线程 */
            for (int i = 0; i < readerThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 200; j++) {
                            Map<String, ?> prompts = source.loadAll();
                            if (prompts != null) {
                                readCount.incrementAndGet();
                            }
                            Thread.sleep(1); /* 短暂休息 */
                        }
                    } catch (Exception e) {
                        /* 忽略读取异常 */
                    } finally {
                        latch.countDown();
                    }
                });
            }

            /* 启动写入线程 */
            for (int i = 0; i < writerThreads; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 20; j++) {
                            String content = String.format("""
                                id: hotupdate-test
                                template: Updated content by writer %d, iteration %d
                                version: %d
                                """, threadIndex, j, j + 2);

                            Files.write(testFile, content.getBytes());
                            writeCount.incrementAndGet();

                            Thread.sleep(50); /* 等待热更新处理 */
                        }
                    } catch (Exception e) {
                        /* 忽略写入异常 */
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            /* 验证并发操作没有导致严重问题 */
            assertThat(readCount.get()).isGreaterThan(1000);
            assertThat(writeCount.get()).isEqualTo(writerThreads * 20);

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("内存泄漏检测测试")
    void testMemoryLeakDetection() throws Exception {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("memory-test.yaml");
        String content = """
            memory-test:
              id: memory-test
              template: Memory leak test content
            """;
        Files.write(testFile, content.getBytes());

        Runtime runtime = Runtime.getRuntime();

        /* 记录初始内存 */
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        /* 创建和销毁大量资源 */
        for (int i = 0; i < 1000; i++) {
            FilePromptSource source = new FilePromptSource(testFile.toString());
            source.loadAll();
            source.close();

            if (i % 100 == 0) {
                System.gc();
            }
        }

        /* 强制垃圾回收 */
        System.gc();
        Thread.sleep(1000);
        System.gc();

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        /* 内存增长应该在合理范围内（小于10MB） */
        assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024);
    }
}