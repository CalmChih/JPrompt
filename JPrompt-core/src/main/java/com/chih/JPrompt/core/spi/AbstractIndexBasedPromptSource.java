package com.chih.JPrompt.core.spi;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.support.DebouncedFileWatcher;
import com.chih.JPrompt.core.support.PromptParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * 基于索引的 PromptSource 泛型基类
 * <p>
 * 实现了"Index-Only"模式的通用逻辑：
 * 1. 双层索引结构（正向索引 + 反向索引）
 * 2. 增量更新机制
 * 3. 防抖处理
 * 4. Cache Miss 回源
 * </p>
 *
 * @param <T> 资源类型（File 或 Spring Resource）
 * @author lizhiyuan
 * @since 2025/12/07
 */
public abstract class AbstractIndexBasedPromptSource<T> implements PromptSource {

    private static final Logger log = LoggerFactory.getLogger(AbstractIndexBasedPromptSource.class);

    // === 核心数据结构 (Index-Only 模式) ===

    /**
     * 正向索引：Prompt Key -> Resource Object
     * 用于根据 prompt key 快速定位对应的资源对象
     * 只存储资源引用，不存储内容，实现零 OOM 风险
     */
    protected final Map<String, T> keyToIndex = new ConcurrentHashMap<>();

    /**
     * 反向索引：Resource ID -> Set<Prompt Key>
     * 用于计算资源删除时需要清理的 prompt keys
     * 支持一个资源文件包含多个 prompt 定义的场景
     */
    protected final Map<String, Set<String>> sourceToKeys = new ConcurrentHashMap<>();

    /**
     * 资源对象缓存：Resource ID -> Resource Object
     * 避免重复创建相同的资源对象，提高内存使用效率
     * 与 keyToIndex 分离，防止循环引用和内存泄漏
     */
    protected final Map<String, T> resourceCache = new ConcurrentHashMap<>();

    // === 热更新支持 (防抖机制) ===

    /**
     * 待更新的 Prompt 元数据暂存区 (Accumulator)
     * 用于收集文件变更后的新增/修改的 prompt 元数据
     * 线程安全，支持并发写入
     */
    protected final Map<String, PromptMeta> pendingUpdates = new ConcurrentHashMap<>();

    /**
     * 待删除的 Prompt Key 集合
     * 用于收集文件变更后需要删除的 prompt keys
     * 使用 ConcurrentHashMap.newKeySet() 创建线程安全的 Set
     */
    protected final Set<String> pendingRemoves = ConcurrentHashMap.newKeySet();

    // === 监控与错误处理 ===

    /**
     * 加载失败的资源记录表
     * Key: Resource ID, Value: 异常信息
     * 用于问题排查和错误监控
     */
    protected final Map<String, Throwable> loadErrors = new ConcurrentHashMap<>();

    /**
     * 防抖文件监听器
     * 监听文件系统变更，并通过防抖机制避免频繁更新
     */
    protected final DebouncedFileWatcher fileWatcher;

    /**
     * 变更事件监听器
     * 当检测到 prompt 变更时，通过此监听器通知外部组件
     * 使用 volatile 保证可见性
     */
    protected volatile Consumer<PromptChangeEvent> changeListener;

    /**
     * 启动阶段事件缓存队列
     * <p>
     * 防止在监听器注册前的变更事件丢失。
     * 当 changeListener 为 null 时，变更事件会被暂存到此队列中。
     * 监听器注册后，这些事件会被立即重放。
     * </p>
     */
    private final Queue<PromptChangeEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    /**
     * 是否已注册监听器的标记
     */
    private volatile boolean listenerRegistered = false;

    /**
     * 构造函数（使用默认防抖时间 500ms）
     *
     * 使用默认配置创建 PromptSource，适合大多数使用场景
     * - 防抖延迟：500ms
     * - 线程池：内部自动创建
     */
    protected AbstractIndexBasedPromptSource() {
        this(500L, null, null);
    }

    /**
     * 构造函数（支持自定义防抖时间和线程池）
     *
     * @param debounceDelayMs 防抖延迟时间（毫秒）
     *        文件变更后等待的时间，用于合并连续的变更事件
     * @param watcherExecutor 文件监听线程池（可选）
     *        用于处理文件系统事件的线程池，为 null 时内部创建
     * @param debounceExecutor 防抖定时器线程池（可选）
     *        用于处理防抖延迟的线程池，为 null 时内部创建
     */
    protected AbstractIndexBasedPromptSource(long debounceDelayMs,
                                           ExecutorService watcherExecutor,
                                           ScheduledExecutorService debounceExecutor) {
        this.fileWatcher = new DebouncedFileWatcher(
                this::handleFileChange,    /* 文件变更回调 */
                this::notifyManager,       /* 防抖结束通知 */
                debounceDelayMs,           /* 防抖延迟时间 */
                watcherExecutor,           /* 文件监听线程池 */
                debounceExecutor           /* 防抖定时器线程池 */
        );
    }

  // === 抽象方法：子类实现资源操作差异化逻辑 ===

    /**
     * 打开资源流
     *
     * 子类需要实现具体的流打开逻辑，支持不同的资源类型：
     * - FileResource: 使用 Files.newInputStream()
     * - Spring Resource: 使用 resource.getInputStream()
     *
     * @param resource 资源对象
     * @return 输入流，调用者负责关闭
     * @throws Exception IO异常或资源访问异常
     */
    protected abstract InputStream openStream(T resource) throws Exception;

    /**
     * 获取资源的唯一标识（用于索引键）
     *
     * 此标识符用作索引的键，必须保证：
     * 1. 唯一性：不同的资源应有不同的标识
     * 2. 稳定性：同一资源的标识不应随时间变化
     * 3. 可比较：支持 Map 的键操作
     *
     * @param resource 资源对象
     * @return 唯一标识字符串
     */
    protected abstract String getResourceId(T resource);

    /**
     * 判断资源是否存在
     *
     * 用于资源删除检测和加载失败处理。
     * 子类需要根据资源类型实现具体的存在性检查：
     * - 文件资源: Files.exists()
     * - Classpath资源: 尝试打开流验证
     * - Spring资源: resource.exists()
     *
     * @param resource 资源对象
     * @return 如果资源存在返回 true，否则返回 false
     */
    protected abstract boolean exists(T resource);

    /**
     * 从 File 对象转换回资源对象（用于文件监听回调）
     *
     * 当文件系统监听器检测到文件变更时，需要将 File 对象
     * 转换为具体的资源类型，以便后续处理。
     *
     * @param file 发生变更的文件对象
     * @return 对应的资源对象，如果不支持该文件类型则返回 null
     */
    protected abstract T resolveResourceFromFile(File file);

    /**
     * 获取资源描述（用于日志和错误信息）
     *
     * 返回人类可读的资源描述信息，用于：
     * - 日志记录
     * - 错误消息
     * - 调试信息
     *
     * @param resource 资源对象
     * @return 描述字符串，应包含足够的上下文信息
     */
    protected abstract String getResourceDescription(T resource);

    // === 核心通用逻辑 ===

    /**
     * 刷新单个资源的索引（核心复用逻辑）
     *
     * 这是 Index-Only 模式的核心方法，负责：
     * 1. 检测资源删除情况
     * 2. 解析资源内容
     * 3. 更新双向索引
     * 4. 计算变更事件
     *
     * @param resource 资源对象
     * @param isIncremental 是否为增量更新（热更新时为 true，初始加载时为 false）
     */
    protected void refreshIndex(T resource, boolean isIncremental) {
        String resourceId = getResourceId(resource);

        try {
            // A. 处理资源删除情况
            // 当资源文件被删除时，需要清理相关的索引和缓存
            if (!exists(resource)) {
                handleResourceRemoval(resourceId, isIncremental);
                return;
            }

            // B. 解析资源内容（瞬时内存，解析完即释放）
            // 使用统一的 PromptParser 解析各种格式的资源
            Map<String, PromptMeta> newPrompts = parseResource(resource);

            // C. 更新双向索引并计算变更事件
            // 传递实际的 resource 对象，避免缓存循环引用
            updateIndexAndCalculateChanges(resourceId, newPrompts, resource, isIncremental);

        } catch (Exception e) {
            // 记录加载错误，但不影响其他资源的处理
            log.error("Failed to refresh resource: {}", resourceId, e);
            loadErrors.put(resourceId, e);
        }
    }

    /**
     * 处理资源删除
     *
     * 当资源文件被删除时，需要：
     * 1. 从反向索引中查找相关的 prompt keys
     * 2. 从正向索引中删除这些 keys
     * 3. 如果是增量更新，添加到待删除集合
     * 4. 清理相关的缓存和错误记录
     *
     * @param resourceId 被删除资源的ID
     * @param isIncremental 是否为增量更新
     */
    private void handleResourceRemoval(String resourceId, boolean isIncremental) {
        // 从反向索引中查找该资源包含的所有 prompt keys
        Set<String> removedKeys = sourceToKeys.remove(resourceId);

        if (removedKeys != null) {
            // 从正向索引中删除这些 keys
            removedKeys.forEach(keyToIndex::remove);

            if (isIncremental) {
                // 增量更新时，添加到待删除集合
                pendingRemoves.addAll(removedKeys);

                // 处理"修改后又删除"的情况：
                // 如果某个 key 同时在待更新和待删除集合中，说明是反复修改后删除
                // 需要从待更新集合中移除，避免不一致的状态
                removedKeys.forEach(pendingUpdates::remove);
            }
        }

        // 清除该资源的加载错误记录
        loadErrors.remove(resourceId);

        log.info("Resource removed: {} (affected {} prompt keys)", resourceId,
                 removedKeys != null ? removedKeys.size() : 0);
    }

    /**
     * 更新双向索引并计算变更事件
     *
     * 这是索引更新的核心算法，实现了三个关键步骤：
     * 1. 更新资源缓存
     * 2. 更新反向索引（Resource -> Keys）
     * 3. 更新正向索引（Key -> Resource）并计算变更
     *
     * 变更计算逻辑：
     * - 新增的 key: newPrompts.containsKey(key) && !oldKeys.contains(key)
     * - 删除的 key: oldKeys.contains(key) && !newPrompts.containsKey(key)
     * - 修改的 key: newPrompts.containsKey(key) && oldKeys.contains(key)
     *
     * @param resourceId 资源唯一标识
     * @param newPrompts 解析出的新 prompt 元数据
     * @param resource 资源对象（避免从缓存中获取）
     * @param isIncremental 是否为增量更新
     */
    private void updateIndexAndCalculateChanges(String resourceId,
                                              Map<String, PromptMeta> newPrompts,
                                              T resource,
                                              boolean isIncremental) {
        // 获取该资源历史上包含的所有 prompt keys
        Set<String> oldKeys = sourceToKeys.getOrDefault(resourceId, Collections.emptySet());

        // 获取当前解析出的所有 prompt keys
        Set<String> currentKeys = new HashSet<>(newPrompts.keySet());

        // C1. 更新资源缓存（先更新缓存，避免后续操作找不到资源）
        cacheResource(resourceId, resource);

        // C2. 更新反向索引：Resource ID -> Set<Prompt Keys>
        sourceToKeys.put(resourceId, currentKeys);

        // C3. 更新正向索引并计算变更事件
        // 遍历当前所有的 prompt keys
        for (Map.Entry<String, PromptMeta> entry : newPrompts.entrySet()) {
            String key = entry.getKey();

            // 更新正向索引：Prompt Key -> Resource Object
            keyToIndex.put(key, resource);

            if (isIncremental) {
                // 关键修复：使用与 notifyManager 相同的锁，防止更新丢失
                synchronized (this) {
                    // 增量更新时，添加到待更新集合
                    pendingUpdates.put(key, entry.getValue());

                    // 如果之前在待删除集合中，说明是删除后又重新添加，移除删除标记
                    pendingRemoves.remove(key);
                }
            }
        }

        // C4. 处理该资源中消失的 prompt keys（逻辑删除）
        // 这些 keys 在历史版本中存在，但在新版本中不存在
        for (String oldKey : oldKeys) {
            if (!currentKeys.contains(oldKey)) {
                // 从正向索引中删除
                keyToIndex.remove(oldKey);

                if (isIncremental) {
                    // 关键修复：使用与 notifyManager 相同的锁，防止状态不一致
                    synchronized (this) {
                        // 添加到待删除集合
                        pendingRemoves.add(oldKey);

                        // 如果同时在待更新集合中，移除（避免不一致）
                        pendingUpdates.remove(oldKey);
                    }
                }
            }
        }

        // 清除该资源的加载错误记录（如果之前加载失败的话）
        loadErrors.remove(resourceId);
    }

    /**
     * 获取当前资源对象（使用基类统一的缓存）
     */
    protected T getCurrentResource(String resourceId) {
        return resourceCache.get(resourceId);
    }

    /**
     * 缓存资源对象（供子类调用）
     */
    protected void cacheResource(String resourceId, T resource) {
        resourceCache.put(resourceId, resource);
    }

    /**
     * 移除资源缓存（供子类调用）
     */
    protected T removeResourceCache(String resourceId) {
        return resourceCache.remove(resourceId);
    }

    /**
     * 解析资源（使用统一的 PromptParser）
     */
    protected Map<String, PromptMeta> parseResource(T resource) throws Exception {
        String filename = getResourceFilename(resource);
        try (InputStream is = openStream(resource)) {
            return PromptParser.parse(is, filename);
        }
    }

    /**
     * 获取资源文件名（用于解析时判断文件类型）
     * 子类需要实现此方法，提供适合的文件名
     */
    protected abstract String getResourceFilename(T resource);

    /**
     * 记录资源加载成功的调试日志
     */
    protected void logResourceLoaded(String resourceId) {
        log.debug("Loaded resource: {}", resourceId);
    }

    /**
     * 记录资源加载失败的错误日志
     */
    protected void logResourceLoadFailure(String resourceDescription, Exception e) {
        log.error("Failed to load resource: {}", resourceDescription, e);
    }

    /**
     * 记录资源扫描失败的错误日志
     */
    protected void logResourceScanFailure(String location, Exception e) {
        log.error("Failed to scan location: {}", location, e);
    }

    /**
     * 记录监听器注册成功的调试日志
     */
    protected void logWatcherRegistered(String directoryPath) {
        log.debug("Registered watcher for directory: {}", directoryPath);
    }

    /**
     * 记录监听器注册失败的调试日志
     */
    protected void logWatcherRegistrationFailure(String resourceDescription, Exception e) {
        log.debug("Failed to register watcher for resource: {}", resourceDescription, e);
    }

    /**
     * 安全地加载资源并记录日志
     */
    protected final void safeLoadResource(T resource) {
        try {
            String resourceId = getResourceId(resource);

            // 缓存资源对象
            cacheResource(resourceId, resource);

            // 刷新索引
            refreshIndex(resource, false);

            // 记录成功日志
            logResourceLoaded(resourceId);

        } catch (Exception e) {
            // 记录失败日志
            logResourceLoadFailure(getResourceDescription(resource), e);
        }
    }

    /**
     * 文件变更回调处理器
     */
    private void handleFileChange(File file) {
        if (!PromptParser.isSupportedFile(file.getName())) {
            return;
        }

        T resource = resolveResourceFromFile(file);
        if (resource != null) {
            refreshIndex(resource, true);
        }
    }

    /**
     * 通知管理器（防抖结束后调用，带事件缓存机制）
     */
    protected void notifyManager() {
        // 原子快照
        Map<String, PromptMeta> updatesSnapshot;
        Set<String> removesSnapshot;

        synchronized (this) {
            if (pendingUpdates.isEmpty() && pendingRemoves.isEmpty()) {
                return;
            }

            updatesSnapshot = new HashMap<>(pendingUpdates);
            removesSnapshot = new HashSet<>(pendingRemoves);

            pendingUpdates.clear();
            pendingRemoves.clear();
        }

        // 创建变更事件
        PromptChangeEvent event = new PromptChangeEvent(updatesSnapshot, removesSnapshot);

        if (changeListener != null) {
            // 监听器已注册，直接通知
            log.info("Debounce finished. Pushing batch updates: {} updated, {} removed.",
                    updatesSnapshot.size(), removesSnapshot.size());
            changeListener.accept(event);
        } else {
            // 监听器未注册，暂存事件防止丢失
            pendingEvents.offer(event);
            log.debug("Change listener not registered, caching event: {} updated, {} removed",
                     updatesSnapshot.size(), removesSnapshot.size());
        }
    }

    // === PromptSource 接口实现 ===

    @Override
    public Map<String, PromptMeta> loadAll() {
        Map<String, PromptMeta> all = new HashMap<>();

        // 使用 Set 去重，避免同一个资源被多次解析
        Collection<T> uniqueResources = new HashSet<>(keyToIndex.values());

        for (T resource : uniqueResources) {
            try {
                all.putAll(parseResource(resource));
            } catch (Exception e) {
                // refreshIndex 阶段已经记录过错误，但需要确保启动错误能被发现
                log.warn("Failed to parse resource during loadAll: {}", getResourceId(resource), e);
            }
        }

        return all;
    }

    @Override
    public PromptMeta load(String key) {
        // Cache Miss 回源：查索引 -> 读文件 -> 解析 -> 返回
        T resource = keyToIndex.get(key);
        if (resource == null) {
            return null;
        }

        try {
            // 这里虽有 IO，但只会解析一个文件，且仅在 Cache Miss 时发生
            return parseResource(resource).get(key);
        } catch (Exception e) {
            log.error("Failed to load prompt: {}", key, e);
            return null;
        }
    }

    @Override
    public void onChange(Consumer<PromptChangeEvent> listener) {
        this.changeListener = listener;
        this.listenerRegistered = true;

        // 重放缓存的事件（如果有的话）
        replayPendingEvents();
    }

    /**
     * 重放缓存中的变更事件
     *
     * 当监听器注册后，立即重放之前缓存的变更事件，
     * 确保启动阶段的文件变更不会丢失。
     */
    private void replayPendingEvents() {
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Replaying {} cached change events from startup phase", pendingEvents.size());

        PromptChangeEvent event;
        int replayedCount = 0;
        while ((event = pendingEvents.poll()) != null) {
            try {
                changeListener.accept(event);
                replayedCount++;
            } catch (Exception e) {
                log.error("Failed to replay cached change event", e);
            }
        }

        log.info("Replayed {} cached change events successfully", replayedCount);
    }

    @Override
    public void close() throws Exception {
        fileWatcher.close();
    }

    // === 辅助方法 ===

    /**
     * 获取当前的错误列表
     */
    public Map<String, Throwable> getLoadErrors() {
        return Collections.unmodifiableMap(loadErrors);
    }

    /**
     * 启动文件监听器
     */
    protected void startWatcher() {
        this.fileWatcher.start();
    }
}