package com.easytier.forge.lan;

import com.easytier.forge.lan.binary.BinaryExtractor;
import com.easytier.forge.lan.config.ConfigManager;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.config.Version;
import com.easytier.forge.lan.platform.PlatformDetector;
import com.easytier.forge.lan.process.EasyTierCliRunner;
import com.easytier.forge.lan.process.EasyTierProcessManager;
import com.easytier.forge.lan.util.LogManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * EasyTier 核心服务
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
    private volatile String currentVersion = "";
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

    public String getCurrentVersion() {
        return currentVersion;
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

    public void refreshPeerCountAsync() {
        executor.submit(() -> {
            try {
                String output = processManager.getCliRunner().peer();
                if (output != null) {
                    String[] lines = output.split("\\r?\\n");
                    int count = 0;
                    for (String line : lines) {
                        if (line != null && line.trim().length() > 0 && !line.contains("PEER") && !line.contains("---")) {
                            count++;
                        }
                    }
                    peerCount = count;
                }
            } catch (Exception e) {
                // 忽略
            }
        });
    }

    public boolean isPlatformSupported() {
        return PlatformDetector.isSupported();
    }

    public boolean isAndroid() {
        return PlatformDetector.isAndroid();
    }

    public boolean start(String networkName, Version version, String relayServer,
                         String networkSecret, String devName, boolean dhcp) {
        try {
            return startInternal(networkName, version, relayServer, networkSecret, devName, dhcp);
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

    private boolean startInternal(String networkName, Version version, String relayServer,
                                  String networkSecret, String devName, boolean dhcp) {
        if (!isPlatformSupported()) {
            logManager.error("当前平台不支持 EasyTier");
            return false;
        }

        if (processManager.isRunning()) {
            logManager.warn("EasyTier 已在运行中，请先停止");
            return false;
        }

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
        boolean fileExists = configManager.configExists(networkName, version);

        if (fileExists) {
            config = configManager.loadConfig(networkName, version);
            if (config == null || config.isCorrupted()) {
                logManager.error("配置文件损坏: " + networkName + "_" + version + ".toml");
                return false;
            }
            logManager.info("加载已有配置: " + config.toFileName());
            config.setNetworkSecret(networkSecret);
            config.setDevName(devName);
            config.setRelayServer(relayServer);
            configManager.saveConfig(config);
        } else {
            config = new TomlConfig();
            config.setNetworkName(networkName);
            config.setInstanceName(networkName);
            config.setRelayServer(relayServer);
            config.setNetworkSecret(networkSecret);
            config.setDevName(devName);
            config.setDhcp(dhcp);
            config.setVersion(version);
            if (!configManager.saveConfig(config)) {
                logManager.error("保存配置失败");
                return false;
            }
            logManager.info("生成新配置: " + config.toFileName());
        }

        String safeName = com.easytier.forge.lan.config.FileSanitizer.sanitize(networkName);
        logManager.startNewLog(safeName, version.toString(), false, null);
        logManager.info("配置文件: " + config.toFileName());
        logManager.info("启动配置: " + networkName + " @ " + version + " (" + config.toFileName() + ")");

        Path configFile = configManager.getConfigsDir().resolve(config.toFileName());
        logManager.info("调用进程管理器启动...");
        boolean result = processManager.start(config, configFile);
        logManager.info("进程管理器返回: " + result);
        if (result) {
            this.currentNetworkName = networkName;
            this.currentRelayServer = relayServer;
            this.currentVersion = version != null ? version.toString() : "";
            this.currentConfigFile = config.toFileName();
            this.currentInstanceId = config.getInstanceId() != null ? config.getInstanceId() : "";
            this.peerCount = -1;
        }
        return result;
    }

    public void stop() {
        processManager.stop();
        this.currentNetworkName = "";
        this.currentRelayServer = "";
        this.currentVersion = "";
        this.currentConfigFile = "";
        this.currentInstanceId = "";
        this.peerCount = -1;
        logManager.info("EasyTier 已停止");
    }

    public void cleanupAllProcesses() {
        processManager.cleanupAllProcesses();
        logManager.info("已清理所有 EasyTier 进程");
    }

    public CompletableFuture<Boolean> startAsync(String networkName, Version version, String relayServer,
                                                  String networkSecret, String devName, boolean dhcp) {
        if (pendingStart.get() != null && !pendingStart.get().isDone()) {
            logManager.warn("已有启动任务在执行中");
            return pendingStart.get();
        }
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                start(networkName, version, relayServer, networkSecret, devName, dhcp), executor);
        pendingStart.set(future);
        return future;
    }

    public CompletableFuture<Void> stopAsync() {
        return CompletableFuture.runAsync(this::stop, executor);
    }

    public CompletableFuture<String> refreshAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String ip = processManager.refreshVirtualIp();
            if (ip != null) {
                logManager.info("当前虚拟IP: " + ip);
            }
            return ip;
        }, executor);
    }

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

    public String getVirtualIp() {
        return processManager.getVirtualIp();
    }

    public String refreshVirtualIp() {
        return processManager.refreshVirtualIp();
    }

    public boolean isRunning() {
        return processManager.isRunning();
    }

    public List<String> listNetworks() {
        return configManager.listNetworkNames();
    }

    public List<TomlConfig> listVersions(String networkName) {
        return configManager.getVersions(networkName);
    }

    public TomlConfig getLatestVersion(String networkName) {
        return configManager.getLatestVersion(networkName);
    }

    public Map<String, List<TomlConfig>> scanAllConfigs() {
        return configManager.scanAllConfigs();
    }
}
