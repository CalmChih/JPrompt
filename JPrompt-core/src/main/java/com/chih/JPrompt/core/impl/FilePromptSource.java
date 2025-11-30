package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.spi.PromptSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * 基于文件的 Prompt 源实现
 * <p>
 * 策略：
 * 1. 优先读取 Jar 包外部文件 (支持热更新)
 * 2. 外部文件不存在时，读取 Classpath 内部文件 (保底)
 * </p>
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class FilePromptSource implements PromptSource {
    
    private static final Logger log = LoggerFactory.getLogger(FilePromptSource.class);
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final String classpathPath;
    private final File externalFile;
    private Runnable changeCallback;
    
    public FilePromptSource(String fileName) {
        this.classpathPath = fileName;
        // 假设外部文件就在当前运行目录下
        this.externalFile = new File("./" + fileName);
        startWatcher();
    }
    
    @Override
    public Map<String, PromptMeta> loadAll() {
        try {
            InputStream is = null;
            if (externalFile.exists()) {
                log.info("Loading prompts from EXTERNAL file: {}", externalFile.getAbsolutePath());
                is = new FileInputStream(externalFile);
            } else {
                log.info("Loading prompts from CLASSPATH: {}", classpathPath);
                is = this.getClass().getClassLoader().getResourceAsStream(classpathPath);
            }
            
            if (is != null) {
                return mapper.readValue(is, new TypeReference<Map<String, PromptMeta>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to load prompt config", e);
        }
        return Collections.emptyMap();
    }
    
    @Override
    public void onChange(Runnable callback) {
        this.changeCallback = callback;
    }
    
    /**
     * 启动守护线程监听外部文件变化
     */
    private void startWatcher() {
        Thread watcher = new Thread(() -> {
            long lastModified = externalFile.exists() ? externalFile.lastModified() : 0;
            while (true) {
                try {
                    Thread.sleep(2000);
                    // 只有当外部文件存在时才监听
                    if (externalFile.exists()) {
                        long current = externalFile.lastModified();
                        // 检测到修改，或者文件刚刚被创建
                        if (current > lastModified) {
                            lastModified = current;
                            log.info("Detected prompt file change, reloading...");
                            if (changeCallback != null) {
                                changeCallback.run();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        watcher.setDaemon(true);
        watcher.setName("Prompt-File-Watcher");
        watcher.start();
    }
}