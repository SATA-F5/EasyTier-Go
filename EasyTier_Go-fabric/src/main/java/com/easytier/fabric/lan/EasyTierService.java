package com.easytier.fabric.lan;

import com.easytier.fabric.lan.binary.BinaryExtractor;
import com.easytier.fabric.lan.config.ConfigManager;
import com.easytier.fabric.lan.config.FileSanitizer;
import com.easytier.fabric.lan.config.TomlConfig;
import com.easytier.fabric.lan.platform.PlatformDetector;
import com.easytier.fabric.lan.process.EasyTierCliRunner;
import com.easytier.fabric.lan.process.EasyTierProcessManager;
import com.easytier.fabric.lan.util.LogManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * EasyTier 核心服务
 * 整合配置管理、二进制提取、进程管理和 CLI 调用，
 * 为客户端 UI 和服务端命令提供统一的高层接口
 */
public class EasyTierService {

    private static EasyTierService instance;

    private final Path baseDir;
    private final ConfigManager configManager;
    private final LogManager logManager;
    private final BinaryExtractor binaryExtractor;
    private final EasyTierProcessManager processManager;
    private volatile String currentNetworkName = "";
    private volatile String currentRelayServer = "";
    private volatile String currentConfigFile = "";
    private volatile String currentInstanceId = "";
    private volatile int peerCount = -1;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "easytier-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<CompletableFuture<Boolean>> pendingStart = new AtomicReference<>();

    private EasyTierService(Path baseDir) {
        this.baseDir = baseDir;
        this.configManager = new ConfigManager(baseDir);
        this.logManager = new LogManager(baseDir);
        this.binaryExtractor = new BinaryExtractor(baseDir, logManager);
        this.processManager = new EasyTierProcessManager(binaryExtractor, logManager);
    }

    public static synchronized EasyTierService getInstance(Path baseDir) {
        if (instance == null || !instance.baseDir.equals(baseDir)) {
            instance = new EasyTierService(baseDir);
        }
        return instance;
    }

    public static EasyTierService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("EasyTierService 未初始化，请先调用 getInstance(baseDir)");
        }
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public BinaryExtractor getBinaryExtractor() {
        return binaryExtractor;
    }

    public EasyTierProcessManager getProcessManager() {
        return processManager;
    }

    public EasyTierCliRunner getCliRunner() {
        return processManager.getCliRunner();
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public String getCurrentNetworkName() {
        return currentNetworkName;
    }

    public String getCurrentRelayServer() {
        return currentRelayServer;
    }

    public String getCurrentConfigFile() {
        return currentConfigFile;
    }

    public String getCurrentInstanceId() {
        return currentInstanceId;
    }

    public int getPeerCount() {
        return peerCount;
    }

    public void updatePeerCount(int count) {
        this.peerCount = count;
    }

    public long getProcessPid() {
        return processManager.getPid();
    }

    public List<String> getPortForwards() {
        List<EasyTierProcessManager.PortForwardRule> rules = processManager.getActivePortForwards();
        List<String> result = new java.util.ArrayList<>();
        for (EasyTierProcessManager.PortForwardRule rule : rules) {
            result.add(rule.protocol + " " + rule.bindPort + " -> " + rule.virtualIp + ":" + rule.mcPort);
        }
        return result;
    }

    /**
     * 异步刷新节点数量
     */
    public void refreshPeerCountAsync() {
        executor.submit(() -> {
            try {
                if (!processManager.isRunning()) {
                    return;
                }
                String output = processManager.getCliRunner().peer();
                int count = EasyTierCliRunner.countPeerRows(output);
                if (count >= 0) {
                    peerCount = count;
                }
            } catch (Exception e) {
                // 忽略
            }
        });
    }

    /**
     * 平台是否支持
     */
    public boolean isPlatformSupported() {
        return PlatformDetector.isSupported();
    }

    /**
     * 是否为 Android
     */
    public boolean isAndroid() {
        return PlatformDetector.isAndroid();
    }

    /**
     * 启动 EasyTier
     * 按网络名称查找配置文件，存在则读取，不存在则生成 <名称>.toml
     */
    public synchronized boolean start(String networkName, String relayServer,
                                      String networkSecret, String devName, boolean dhcp) {
        try {
            return startInternal(networkName, relayServer, networkSecret, devName, dhcp);
        } catch (Exception e) {
            logManager.error("启动过程发生未捕获异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                logManager.error("  原因: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
            return false;
        }
    }

    private boolean startInternal(String networkName, String relayServer,
                                  String networkSecret, String devName, boolean dhcp) {
        if (!isPlatformSupported()) {
            logManager.error("当前平台不支持 EasyTier");
            return false;
        }

        if (processManager.isRunning()) {
            logManager.warn("EasyTier 已在运行中，请先停止");
            return false;
        }

        // 校验必填
        if (networkName == null || networkName.trim().isEmpty()) {
            logManager.error("网络名称不能为空");
            return false;
        }
        if (relayServer == null || relayServer.trim().isEmpty()) {
            logManager.error("中继服务器不能为空");
            return false;
        }

        logManager.info("开始启动流程: network=" + networkName + ", relay=" + relayServer);

        TomlConfig config;
        boolean fileExists = configManager.configExists(networkName);

        if (fileExists) {
            config = configManager.loadConfig(networkName);
            if (config == null || config.isCorrupted()) {
                logManager.error("配置文件损坏: " + FileSanitizer.sanitize(networkName) + ".toml");
                return false;
            }
            logManager.info("加载已有配置: " + config.toFileName());
            // 重新保存配置，确保新增字段（如 smoltcp）写入配置文件
            config.setNetworkSecret(networkSecret);
            config.setDevName(devName);
            config.setRelayServer(relayServer);
            configManager.saveConfig(config);
        } else {
            // 生成新配置
            config = new TomlConfig();
            config.setNetworkName(networkName);
            config.setInstanceName(networkName);
            config.setRelayServer(relayServer);
            config.setNetworkSecret(networkSecret);
            config.setDevName(devName);
            config.setDhcp(dhcp);
            if (!configManager.saveConfig(config)) {
                logManager.error("保存配置失败");
                return false;
            }
            logManager.info("生成新配置: " + config.toFileName());
        }

        // 启动新日志文件
        String safeName = FileSanitizer.sanitize(networkName);
        logManager.startNewLog(safeName, false, null);
        logManager.info("配置文件: " + config.toFileName());
        logManager.info("启动配置: " + networkName + " (" + config.toFileName() + ")");

        // 启动进程
        Path configFile = configManager.getConfigsDir().resolve(config.toFileName());
        logManager.info("调用进程管理器启动...");
        boolean result = processManager.start(config, configFile);
        logManager.info("进程管理器返回: " + result);
        if (result) {
            this.currentNetworkName = networkName;
            this.currentRelayServer = relayServer;
            this.currentConfigFile = config.toFileName();
            this.currentInstanceId = config.getInstanceId() != null ? config.getInstanceId() : "";
            this.peerCount = -1;
        }
        return result;
    }

    /**
     * 停止 EasyTier
     */
    public synchronized void stop() {
        processManager.stop();
        this.currentNetworkName = "";
        this.currentRelayServer = "";
        this.currentConfigFile = "";
        this.currentInstanceId = "";
        this.peerCount = -1;
        logManager.info("EasyTier 已停止");
    }

    /**
     * 清理所有残留的 easytier-core 进程
     */
    public synchronized void cleanupAllProcesses() {
        processManager.cleanupAllProcesses();
        this.currentNetworkName = "";
        this.currentRelayServer = "";
        this.currentConfigFile = "";
        this.currentInstanceId = "";
        this.peerCount = -1;
        logManager.info("已清理所有 EasyTier 进程");
    }

    /**
     * 异步启动 EasyTier（不阻塞 UI 线程）
     * 返回 CompletableFuture，完成时包含启动结果
     */
    public CompletableFuture<Boolean> startAsync(String networkName, String relayServer,
                                                  String networkSecret, String devName, boolean dhcp) {
        if (pendingStart.get() != null && !pendingStart.get().isDone()) {
            logManager.warn("已有启动任务在执行中");
            return pendingStart.get();
        }
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                start(networkName, relayServer, networkSecret, devName, dhcp), executor);
        pendingStart.set(future);
        return future;
    }

    /**
     * 异步停止 EasyTier
     */
    public CompletableFuture<Void> stopAsync() {
        return CompletableFuture.runAsync(this::stop, executor);
    }

    /**
     * 异步刷新虚拟 IP 和节点信息
     */
    public CompletableFuture<String> refreshAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String ip = processManager.refreshVirtualIp();
            if (ip != null) {
                logManager.info("当前虚拟IP: " + ip);
            }
            return ip;
        }, executor);
    }

    /**
     * 异步确保二进制已下载
     */
    public CompletableFuture<Boolean> ensureBinaryAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (binaryExtractor.isReady()) return true;
            return binaryExtractor.extract();
        }, executor);
    }

    public boolean isStartPending() {
        CompletableFuture<Boolean> f = pendingStart.get();
        return f != null && !f.isDone();
    }

    /**
     * 获取当前虚拟 IP
     */
    public String getVirtualIp() {
        String ip = processManager.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            ip = EasyTierCliRunner.getGlobalVirtualIp();
        }
        return ip;
    }

    /**
     * 刷新虚拟 IP
     */
    public String refreshVirtualIp() {
        return processManager.refreshVirtualIp();
    }

    /**
     * 是否运行中
     */
    public boolean isRunning() {
        return processManager.isRunning();
    }

    /**
     * 获取所有网络名称
     */
    public List<String> listNetworks() {
        return configManager.listNetworkNames();
    }

    /**
     * 获取指定网络的配置
     */
    public TomlConfig getConfig(String networkName) {
        return configManager.loadConfig(networkName);
    }

    /**
     * 扫描所有配置
     */
    public Map<String, TomlConfig> scanAllConfigs() {
        return configManager.scanAllConfigs();
    }
}
