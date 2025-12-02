package com.chih.JPrompt.core.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 支持防抖的文件监听器 (核心基础组件)
 * <p>
 * 封装了 Java NIO WatchService 和 ScheduledExecutorService， 提供高性能、线程安全、防抖动的文件变更监听能力。
 * </p>
 *
 * @author lizhiyuan
 */
public class DebouncedFileWatcher implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(DebouncedFileWatcher.class);
    
    private final WatchService watchService;
    
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    
    private final Set<String> watchedPaths = ConcurrentHashMap.newKeySet();
    
    // 回调函数
    // 单个文件变更时触发 (用于增量更新)
    private final Consumer<File> onFileChange;
    
    // 防抖结束后触发 (用于通知上层刷新)
    private final Runnable onDebounceComplete;
    
    private final long debounceDelayMs;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 线程池
    private final ExecutorService watcherExecutor;
    
    private final ScheduledExecutorService debounceExecutor;
    
    private ScheduledFuture<?> debounceTask;
    
    // 是否由当前类管理线程池生命周期 (如果是外部注入的，则不应该由我关闭)
    private final boolean manageWatcherExecutor;
    
    private final boolean manageDebounceExecutor;
    
    /**
     * 默认构造函数 (保持向后兼容，内部创建线程池)
     */
    public DebouncedFileWatcher(Consumer<File> onFileChange, Runnable onDebounceComplete, long debounceDelayMs) {
        this(onFileChange, onDebounceComplete, debounceDelayMs, null, null);
    }
    
    /**
     * @param onFileChange       当检测到文件变更时立即回调 (可用于增量处理)，参数为变更的文件
     * @param onDebounceComplete 当一系列变更停止后(防抖结束)回调 (可用于触发整体刷新)
     * @param debounceDelayMs    防抖延迟 (毫秒)
     * @param watcherExecutor    监听线程池 (传 null 则内部创建)
     * @param debounceExecutor   防抖调度线程池 (传 null 则内部创建)
     */
    public DebouncedFileWatcher(Consumer<File> onFileChange, Runnable onDebounceComplete, long debounceDelayMs,
            ExecutorService watcherExecutor, ScheduledExecutorService debounceExecutor) {
        this.onFileChange = onFileChange;
        this.onDebounceComplete = onDebounceComplete;
        this.debounceDelayMs = debounceDelayMs;
        
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize WatchService", e);
        }
        
        if (watcherExecutor == null) {
            this.watcherExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "JPrompt-Watcher");
                t.setDaemon(true);
                return t;
            });
            manageWatcherExecutor = true;
        } else {
            this.watcherExecutor = watcherExecutor;
            manageWatcherExecutor = false;
        }
        
        if (debounceExecutor == null) {
            this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "JPrompt-Debouncer");
                t.setDaemon(true);
                return t;
            });
            manageDebounceExecutor = true;
        } else {
            this.debounceExecutor = debounceExecutor;
            manageDebounceExecutor = false;
        }
      
    }
    
    /**
     * 注册监听目录
     */
    public void register(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        try {
            String absPath = directory.getCanonicalPath();
            if (watchedPaths.contains(absPath)) {
                return;
            }
            
            Path dirPath = directory.toPath();
            WatchKey key = dirPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            
            watchKeys.put(key, dirPath);
            watchedPaths.add(absPath);
            log.info("Watching directory: {}", absPath);
            
        } catch (IOException e) {
            log.warn("Failed to register watch for directory: {}", directory, e);
        }
    }
    
    /**
     * 启动监听线程
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            watcherExecutor.submit(this::watchLoop);
        }
    }
    
    private void watchLoop() {
        log.info("JPrompt FileWatcher started.");
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                Path dir = watchKeys.get(key);
                if (dir == null) {
                    key.cancel();
                    continue;
                }
                
                boolean activityDetected = false;
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    Path changedPath = (Path) event.context();
                    String fileName = changedPath.toString();
                    
                    if (shouldIgnore(fileName)) {
                        continue;
                    }
                    
                    // 触发单文件回调
                    if (onFileChange != null) {
                        File file = dir.resolve(changedPath).toFile();
                        if (file.exists()) {
                            try {
                                onFileChange.accept(file);
                                activityDetected = true;
                            } catch (Exception e) {
                                log.error("Error in onFileChange callback", e);
                            }
                        }
                    } else {
                        activityDetected = true;
                    }
                }
                
                if (activityDetected) {
                    triggerDebounce();
                }
                
                if (!key.reset()) {
                    watchKeys.remove(key);
                }
                
            } catch (InterruptedException | ClosedWatchServiceException e) {
                // Normal shutdown
                break;
            } catch (Exception e) {
                log.error("Unexpected error in watcher loop", e);
            }
        }
    }
    
    private synchronized void triggerDebounce() {
        if (onDebounceComplete == null) {
            return;
        }
        
        if (debounceTask != null && !debounceTask.isDone()) {
            debounceTask.cancel(false);
        }
        
        debounceTask = debounceExecutor.schedule(() -> {
            try {
                onDebounceComplete.run();
            } catch (Exception e) {
                log.error("Error in onDebounceComplete callback", e);
            }
        }, debounceDelayMs, TimeUnit.MILLISECONDS);
    }
    
    private boolean shouldIgnore(String fileName) {
        return fileName.endsWith("~") || fileName.startsWith(".") ||
                (!fileName.endsWith(".yaml") && !fileName.endsWith(".yml") &&
                        !fileName.endsWith(".json") && !fileName.endsWith(".md"));
    }
    
    @Override
    public void close() {
        running.set(false);
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) {
        }
        if (manageWatcherExecutor) {
            watcherExecutor.shutdownNow();
        }
        if (manageDebounceExecutor) {
            debounceExecutor.shutdownNow();
        }
        log.info("JPrompt FileWatcher stopped.");
    }
}