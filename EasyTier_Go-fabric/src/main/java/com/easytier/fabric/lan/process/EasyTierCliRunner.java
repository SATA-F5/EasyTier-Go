package com.easytier.fabric.lan.process;

import com.easytier.fabric.lan.util.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EasyTier CLI 命令封装
 * 统一处理进程调用、超时（5秒）、UTF-8 编码和输出解析
 * 所有 CLI 命令指定 --rpc-portal 127.0.0.1:<port>
 */
public class EasyTierCliRunner {

    private final Path cliBinary;
    private final LogManager logManager;
    private int rpcPort = 15888;
    private static final int TIMEOUT_SECONDS = 5;

    // 用于从 easytier-cli node 输出中提取虚拟 IP
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");

    public EasyTierCliRunner(Path cliBinary, LogManager logManager) {
        this.cliBinary = cliBinary;
        this.logManager = logManager;
    }

    public void setRpcPort(int port) {
        this.rpcPort = port;
    }

    public int getRpcPort() {
        return rpcPort;
    }

    /**
     * 执行 easytier-cli node，返回原始输出
     */
    public String node() {
        return run("node");
    }

    /**
     * 执行 easytier-cli peer，返回原始输出
     */
    public String peer() {
        return run("peer");
    }

    /**
     * 执行 easytier-cli peer list --output json，返回 JSON 格式的节点列表
     */
    public String peerListJson() {
        return run("peer", "list", "--output", "json");
    }

    /**
     * 执行 easytier-cli route，返回原始输出
     */
    public String route() {
        return run("route");
    }

    /**
     * 添加端口转发
     * 封装: easytier-cli port-forward add <proto> 0.0.0.0:<bindPort> <virtualIp>:<mcPort>
     */
    public String portForwardAdd(String protocol, int bindPort, String virtualIp, int mcPort) {
        return run("port-forward", "add", protocol,
                "0.0.0.0:" + bindPort, virtualIp + ":" + mcPort);
    }

    /**
     * 移除端口转发
     * 封装: easytier-cli port-forward remove <proto> 0.0.0.0:<bindPort>
     */
    public String portForwardRemove(String protocol, int bindPort) {
        return run("port-forward", "remove", protocol, "0.0.0.0:" + bindPort);
    }

    /**
     * 列出端口转发规则
     */
    public String portForwardList() {
        return run("port-forward", "list");
    }

    /**
     * 查看 WireGuard 门户信息（可选）
     */
    public String vpnPortal() {
        return run("vpn-portal");
    }

    /**
     * 从 node 输出中提取虚拟 IP
     * @return 虚拟 IP 字符串，未找到返回 null
     */
    public String extractVirtualIp(String nodeOutput) {
        if (nodeOutput == null || nodeOutput.isEmpty()) {
            return null;
        }
        Matcher matcher = IPV4_PATTERN.matcher(nodeOutput);
        while (matcher.find()) {
            String ip = matcher.group();
            // 排除 0.0.0.0, 127.0.0.1 等
            if (!ip.equals("0.0.0.0") && !ip.equals("127.0.0.1")
                    && !ip.startsWith("255.") && !ip.startsWith("224.")) {
                return ip;
            }
        }
        return null;
    }

    /**
     * 获取当前虚拟 IP（执行 node 命令并解析）
     */
    public String getVirtualIp() {
        String output = node();
        return extractVirtualIp(output);
    }

    /**
     * 执行 CLI 命令
     * @param args 命令参数（不含可执行文件和 --rpc-portal）
     * @return 合并后的 stdout/stderr 输出
     */
    public String run(String... args) {
        if (cliBinary == null) {
            return "错误: easytier-cli 路径未设置";
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(cliBinary.toString());
        cmd.add("--rpc-portal");
        cmd.add("127.0.0.1:" + rpcPort);
        for (String arg : args) {
            cmd.add(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误: CLI 命令超时（" + TIMEOUT_SECONDS + "秒）: " + String.join(" ", args);
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (exitCode != 0 && result.isEmpty()) {
                result = "CLI 命令退出码: " + exitCode;
            }
            return result;

        } catch (IOException e) {
            return "错误: 执行 CLI 失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "错误: CLI 命令被中断";
        }
    }
}
