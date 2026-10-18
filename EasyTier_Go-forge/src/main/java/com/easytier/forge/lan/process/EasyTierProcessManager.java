package com.easytier.forge.lan.process;

import com.easytier.forge.lan.binary.BinaryExtractor;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.platform.PlatformDetector;
import com.easytier.forge.lan.util.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EasyTier 核心进程管理器
 */
public class EasyTierProcessManager {

    private final BinaryExtractor binaryExtractor;
    private final EasyTierCliRunner cliRunner;
    private final LogManager logManager;

    private Process currentProcess;
    private TomlConfig currentConfig;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread logReaderThread;
    private String virtualIp;
    private final List<PortForwardRule> activePortForwards = new ArrayList<>();

    private static final int STOP_TIMEOUT_SECONDS = 3;
    private static final int DHCP_WAIT_POLL_INTERVAL_MS = 1000;
    private static final int DHCP_WAIT_MAX_SECONDS = 15;

    public EasyTierProcessManager(BinaryExtractor binaryExtractor, LogManager logManager) {
        this.binaryExtractor = binaryExtractor;
        this.logManager = logManager;
        this.cliRunner = new EasyTierCliRunner(binaryExtractor.getCliBinary(), logManager);
    }

    public EasyTierCliRunner getCliRunner() {
        return cliRunner;
    }

    public boolean isRunning() {
        return running.get() && currentProcess != null && currentProcess.isAlive();
    }

    public TomlConfig getCurrentConfig() {
        return currentConfig;
    }

    public String getVirtualIp() {
        return virtualIp;
    }

    public List<PortForwardRule> getActivePortForwards() {
        return new ArrayList<>(activePortForwards);
    }

    public long getPid() {
        if (currentProcess != null && currentProcess.isAlive()) {
            return currentProcess.pid();
        }
        return -1;
    }

    public synchronized boolean start(TomlConfig config, Path configFile) {
        if (isRunning()) {
            logManager.warn("EasyTier 已在运行中，请先停止");
            return false;
        }

        cleanupLeftoverProcesses();
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}

        if (isPortInUse(11010) || isPortInUse(11011)) {
            logManager.warn("端口 11010/11011 仍被占用，尝试再次清理...");
            cleanupLeftoverProcesses();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            if (isPortInUse(11010) || isPortInUse(11011)) {
                logManager.error("端口 11010/11011 被其他进程占用，无法启动 EasyTier。请手动关闭占用进程或使用'清理进程'按钮。");
                return false;
            }
        }

        if (!preStartChecks()) {
            return false;
        }

        if (!binaryExtractor.isReady()) {
            logManager.info("正在提取 EasyTier 二进制...");
            if (!binaryExtractor.extract()) {
                logManager.error("二进制提取失败，无法启动");
                return false;
            }
        }

        this.currentConfig = config;

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(binaryExtractor.getCoreBinary().toString());
            cmd.add("-c");
            cmd.add(configFile.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(binaryExtractor.getBinDir().toFile());
            pb.redirectErrorStream(true);

            logManager.info("启动命令: " + String.join(" ", cmd));
            currentProcess = pb.start();
            running.set(true);

            logReaderThread = new Thread(this::readProcessLogs, "easytier-core-log-reader");
            logReaderThread.setDaemon(true);
            logReaderThread.start();

            logManager.info("EasyTier 进程已启动 (PID: " + currentProcess.pid() + ")");

            CompletableFuture.runAsync(this::waitForVirtualIp);

            return true;
        } catch (IOException e) {
            logManager.error("启动失败: " + e.getMessage());
            handlePlatformSpecificStartupError(e.getMessage());
            running.set(false);
            currentProcess = null;
            return false;
        }
    }

    public void cleanupAllProcesses() {
        cleanupLeftoverProcesses();
    }

    private void cleanupLeftoverProcesses() {
        try {
            boolean isWindows = PlatformDetector.getPlatform().isWindows();
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("taskkill", "/F", "/IM", "easytier-core.exe");
            } else {
                pb = new ProcessBuilder("pkill", "-f", "easytier-core");
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(3, TimeUnit.SECONDS);
            logManager.info("已检查并清理残留 EasyTier 进程");
        } catch (Exception e) {
            // 忽略
        }
    }

    private boolean isPortInUse(int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean preStartChecks() {
        if (PlatformDetector.getPlatform().isLinux()) {
            Path tun = Path.of("/dev/net/tun");
            if (!Files.exists(tun)) {
                logManager.error("Linux 上 /dev/net/tun 不可用。请确保内核已加载 tun 模块（modprobe tun），"
                        + "或在容器中授予相应权限。");
                return false;
            }
        }
        return true;
    }

    private void handlePlatformSpecificStartupError(String error) {
        if (error == null) {
            return;
        }
        String lower = error.toLowerCase();

        if (PlatformDetector.getPlatform().isLinux()
                && (lower.contains("glibc") || lower.contains("not found"))) {
            logManager.error("检测到可能的 glibc 兼容性问题。"
                    + "如果使用 musl libc 系统（如 Alpine），请使用 Docker 运行或自行编译 EasyTier。");
        }

        if (PlatformDetector.getPlatform().isLinux()
                && (lower.contains("permission") || lower.contains("operation not permitted"))) {
            logManager.error("权限不足。请尝试: sudo setcap cap_net_admin+ep "
                    + binaryExtractor.getCoreBinary()
                    + "，或以适当权限运行 Minecraft。");
        }

        if (PlatformDetector.getPlatform().isMacOS()) {
            logManager.warn("macOS 上如果二进制被 Gatekeeper 拦截，请执行: "
                    + "xattr -d com.apple.quarantine " + binaryExtractor.getCoreBinary()
                    + "，或在 系统设置 -> 隐私与安全性 中放行。");
        }
    }

    private void readProcessLogs() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logManager.raw(line);
            }
        } catch (IOException e) {
            if (running.get()) {
                logManager.warn("日志读取中断: " + e.getMessage());
            }
        }
    }

    private void waitForVirtualIp() {
        try {
            Thread.sleep(3000);
            for (int i = 0; i < DHCP_WAIT_MAX_SECONDS; i++) {
                if (!isRunning()) {
                    return;
                }
                String ip = cliRunner.getVirtualIp();
                if (ip != null && !ip.isEmpty()) {
                    virtualIp = ip;
                    logManager.info("虚拟 IP 已分配: " + ip);
                    return;
                }
                if (i == 0) {
                    logManager.info("等待虚拟 IP 分配...");
                }
                Thread.sleep(DHCP_WAIT_POLL_INTERVAL_MS);
            }
            logManager.warn("等待虚拟 IP 分配超时（" + DHCP_WAIT_MAX_SECONDS + "秒），"
                    + "请检查中继服务器连接和网络密码");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void stop() {
        cleanupPortForwards();

        if (currentProcess != null && currentProcess.isAlive()) {
            logManager.info("正在停止 EasyTier 进程...");
            currentProcess.destroy();
            try {
                if (!currentProcess.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logManager.warn("进程未在 " + STOP_TIMEOUT_SECONDS + " 秒内退出，强制终止");
                    currentProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                currentProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            logManager.info("EasyTier 进程已停止");
        }

        running.set(false);
        currentProcess = null;
        currentConfig = null;
        virtualIp = null;

        if (logReaderThread != null) {
            logReaderThread.interrupt();
            logReaderThread = null;
        }
    }

    public boolean addPortForward(String protocol, int bindPort, String virtualIp, int mcPort) {
        if (!isRunning()) {
            logManager.error("EasyTier 未运行，无法添加端口转发");
            return false;
        }
        String result = cliRunner.portForwardAdd(protocol, bindPort, virtualIp, mcPort);
        logManager.info("端口转发添加结果: " + result);

        boolean success = !result.startsWith("错误") && !result.toLowerCase().contains("fail");
        if (success) {
            activePortForwards.add(new PortForwardRule(protocol, bindPort, virtualIp, mcPort));
            logManager.info("端口转发已添加: " + protocol + " 0.0.0.0:" + bindPort
                    + " -> " + virtualIp + ":" + mcPort);
        }
        return success;
    }

    public boolean removePortForward(String protocol, int bindPort) {
        String result = cliRunner.portForwardRemove(protocol, bindPort);
        logManager.info("端口转发移除结果: " + result);
        activePortForwards.removeIf(r -> r.protocol.equals(protocol) && r.bindPort == bindPort);
        return true;
    }

    private void cleanupPortForwards() {
        for (PortForwardRule rule : new ArrayList<>(activePortForwards)) {
            try {
                cliRunner.portForwardRemove(rule.protocol, rule.bindPort);
            } catch (Exception ignored) {
            }
        }
        activePortForwards.clear();
    }

    public String refreshVirtualIp() {
        if (!isRunning()) {
            return null;
        }
        String ip = cliRunner.getVirtualIp();
        if (ip != null && !ip.isEmpty()) {
            virtualIp = ip;
        }
        return virtualIp;
    }

    public static class PortForwardRule {
        public final String protocol;
        public final int bindPort;
        public final String virtualIp;
        public final int mcPort;

        public PortForwardRule(String protocol, int bindPort, String virtualIp, int mcPort) {
            this.protocol = protocol;
            this.bindPort = bindPort;
            this.virtualIp = virtualIp;
            this.mcPort = mcPort;
        }
    }
}
