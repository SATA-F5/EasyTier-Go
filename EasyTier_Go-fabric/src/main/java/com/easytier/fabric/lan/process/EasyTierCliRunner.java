package com.easytier.fabric.lan.process;

import com.easytier.fabric.lan.util.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    /**
     * 严格 IPv4 格式：四段 0-255，不允许 CIDR 后缀
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}"
                    + "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    /**
     * 全局虚拟 IP 缓存。
     * 只保存在内存中，不写入 TOML 配置、active.json 或其他数据文件。
     */
    private static volatile String globalVirtualIp;

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
     * 全局虚拟 IP（仅内存缓存，不落盘）
     */
    public static String getGlobalVirtualIp() {
        return globalVirtualIp;
    }

    /**
     * 清空全局虚拟 IP（停止 EasyTier 时调用）
     */
    public static void clearGlobalVirtualIp() {
        globalVirtualIp = null;
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
     * 从 easytier-cli peer 的表格输出中提取本机虚拟 IP。
     * peer 输出中 cost 为 Local 的那一行就是本机，ipv4 列可能是 CIDR（如 10.1.1.1/24），
     * 这里统一转换成标准点分十进制 IPv4，不带掩码和端口。
     *
     * @return 标准 IPv4 字符串，未找到返回 null
     */
    public String extractVirtualIp(String peerOutput) {
        if (peerOutput == null || peerOutput.isBlank()
                || peerOutput.startsWith("错误") || peerOutput.startsWith("Error")) {
            return null;
        }

        String[] lines = peerOutput.split("\\R");
        int ipv4Column = -1;
        int costColumn = -1;

        for (String rawLine : lines) {
            List<String> cells = splitPeerRow(rawLine);
            if (cells.isEmpty()) {
                continue;
            }

            // 表头：记录 ipv4 / cost 的列下标
            int headerIpv4 = indexOfCell(cells, "ipv4");
            if (headerIpv4 >= 0) {
                ipv4Column = headerIpv4;
                costColumn = indexOfCell(cells, "cost");
                continue;
            }

            if (ipv4Column < 0 || ipv4Column >= cells.size()) {
                continue;
            }
            String cost = costColumn >= 0 && costColumn < cells.size() ? cells.get(costColumn) : "";
            String ip = isLocalCost(cost) ? normalizeIpv4(cells.get(ipv4Column)) : null;
            if (ip != null) {
                return ip;
            }
        }

        // 兜底：某些版本/窄屏输出没有表头，找到包含 cost=Local 的行并取其中第一个合法 IPv4
        for (String rawLine : lines) {
            List<String> cells = splitPeerRow(rawLine);
            boolean localRow = cells.stream().anyMatch(EasyTierCliRunner::isLocalCost);
            if (!localRow) {
                continue;
            }
            for (String cell : cells) {
                String ip = normalizeIpv4(cell);
                if (ip != null) {
                    return ip;
                }
            }
        }
        return null;
    }

    /**
     * 统计 peer 表格中的节点数量（与 peer 列表一致，包含本机行）
     * @return 节点数量，解析失败返回 -1
     */
    public static int countPeerRows(String peerOutput) {
        if (peerOutput == null || peerOutput.isBlank()
                || peerOutput.startsWith("错误") || peerOutput.startsWith("Error")) {
            return -1;
        }

        int ipv4Column = -1;
        int count = 0;
        for (String rawLine : peerOutput.split("\\R")) {
            List<String> cells = splitPeerRow(rawLine);
            if (cells.isEmpty()) {
                continue;
            }
            int headerIpv4 = indexOfCell(cells, "ipv4");
            if (headerIpv4 >= 0) {
                ipv4Column = headerIpv4;
                continue;
            }
            boolean isSeparator = cells.stream().allMatch(c -> c.isEmpty() || c.matches("^[-=:]+$"));
            if (isSeparator) {
                continue;
            }
            if (ipv4Column < 0 || ipv4Column >= cells.size()) {
                continue;
            }
            count++;
        }
        return count;
    }

    /**
     * 获取当前虚拟 IP（以 easytier-cli peer 输出为基准，并写入全局内存缓存）
     */
    public String getVirtualIp() {
        String ip = extractVirtualIp(peer());
        if (ip != null) {
            globalVirtualIp = ip;
            return ip;
        }
        // peer 暂时不可用时返回上一次成功解析的结果（仍在内存中）
        return globalVirtualIp;
    }

    /**
     * 拆分 peer 表格的一行。
     * 兼容 Markdown 表格（|）、制表符表格（│）和空格分隔表格。
     */
    private static List<String> splitPeerRow(String rawLine) {
        List<String> cells = new ArrayList<>();
        if (rawLine == null) {
            return cells;
        }
        String line = rawLine.replaceAll("\u001B\\[[;\\d]*m", "")
                .replace('│', '|').replace('┃', '|').trim();
        if (line.isEmpty()) {
            return cells;
        }

        if (line.indexOf('|') >= 0) {
            String[] parts = line.split("\\|", -1);
            int start = 0;
            int end = parts.length;
            while (start < end && parts[start].trim().isEmpty()) {
                start++;
            }
            while (end > start && parts[end - 1].trim().isEmpty()) {
                end--;
            }
            for (int i = start; i < end; i++) {
                cells.add(parts[i].trim());
            }
        } else {
            // 纯横线分隔行直接跳过
            if (line.matches("^[+\\-=─━┌┐└┘├┤┬┴┼\\s]+$")) {
                return cells;
            }
            for (String part : line.split("\\s+")) {
                if (!part.isEmpty()) {
                    cells.add(part);
                }
            }
        }
        return cells;
    }

    private static int indexOfCell(List<String> cells, String name) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLocalCost(String cost) {
        return cost != null && cost.toLowerCase(Locale.ROOT).startsWith("local");
    }

    /**
     * 将 ipv4 单元格转换为标准 IPv4：
     * - 去掉 CIDR 后缀（10.1.1.1/24 -> 10.1.1.1）
     * - 去掉端口（10.1.1.1:11010 -> 10.1.1.1）
     * - 过滤 0.0.0.0、127.x、169.254.x、组播/广播等非虚拟 IP
     */
    private static String normalizeIpv4(String cell) {
        if (cell == null) {
            return null;
        }
        String ip = cell.trim();
        if (ip.isEmpty() || "-".equals(ip)) {
            return null;
        }

        int slash = ip.indexOf('/');
        if (slash >= 0) {
            ip = ip.substring(0, slash);
        }
        int colon = ip.indexOf(':');
        if (colon >= 0) {
            ip = ip.substring(0, colon);
        }
        ip = ip.trim();

        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return null;
        }
        int firstOctet = Integer.parseInt(ip.substring(0, ip.indexOf('.')));
        // 过滤本机回环、未指定地址、保留/组播/广播地址
        if (firstOctet == 0 || firstOctet == 127 || firstOctet >= 224) {
            return null;
        }
        if (ip.startsWith("169.254.")) {
            return null;
        }
        return ip;
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

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            process = pb.start();
            final Process runningProcess = process;

            // 在独立线程读取输出，避免进程不退出时 readLine() 一直阻塞导致超时失效
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append(System.lineSeparator());
                    }
                } catch (IOException ignored) {
                    // 进程被强制结束时读取会中断，忽略即可
                }
                return output.toString();
            });

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                return "错误: CLI 命令超时（" + TIMEOUT_SECONDS + "秒）: " + String.join(" ", args);
            }

            String result;
            try {
                result = outputFuture.get(2, TimeUnit.SECONDS).trim();
            } catch (Exception e) {
                result = "";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0 && result.isEmpty()) {
                result = "CLI 命令退出码: " + exitCode;
            }
            return result;

        } catch (IOException e) {
            return "错误: 执行 CLI 失败: " + e.getMessage();
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return "错误: CLI 命令被中断";
        }
    }
}
