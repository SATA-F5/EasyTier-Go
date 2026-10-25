package com.easytier.fabric.lan.config;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TOML 配置生成器
 * 按照规格模板生成 EasyTier 配置文件
 */
public final class TomlGenerator {

    private TomlGenerator() {
    }

    /**
     * 生成完整的 TOML 配置内容
     */
    public static String generate(TomlConfig config) {
        StringBuilder sb = new StringBuilder();

        // 顶部注释（网络信息）
        sb.append("# easytier-go config").append(System.lineSeparator());
        sb.append("# network_name = ").append(nullSafe(config.getNetworkName())).append(System.lineSeparator());
        sb.append("# generated    = ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .append(System.lineSeparator());

        // 基础字段
        sb.append("instance_name = \"").append(escape(config.getInstanceName())).append("\"")
                .append(System.lineSeparator());
        sb.append("instance_id = \"").append(escape(config.getInstanceId())).append("\"")
                .append(System.lineSeparator());
        sb.append("dhcp = ").append(config.isDhcp()).append(System.lineSeparator());

        if (!config.isDhcp() && config.getManualIpv4() != null && !config.getManualIpv4().isEmpty()) {
            sb.append("ipv4 = \"").append(escape(config.getManualIpv4())).append("\"")
                    .append(System.lineSeparator());
        }

        // listeners
        sb.append("listeners = [").append(System.lineSeparator());
        List<String> listeners = config.getListeners();
        for (int i = 0; i < listeners.size(); i++) {
            sb.append("    \"").append(escape(listeners.get(i))).append("\"");
            if (i < listeners.size() - 1) {
                sb.append(",");
            }
            sb.append(System.lineSeparator());
        }
        sb.append("]").append(System.lineSeparator());

        // rpc_portal（固定本地 RPC 端口，便于 CLI 连接）
        sb.append("rpc_portal = \"127.0.0.1:15888\"").append(System.lineSeparator());

        // dev_name（顶层属性，不是 [flags] 下）
        sb.append("dev_name = \"").append(escape(config.getDevName())).append("\"")
                .append(System.lineSeparator());

        // network_identity
        sb.append(System.lineSeparator());
        sb.append("[network_identity]").append(System.lineSeparator());
        sb.append("network_name = \"").append(escape(config.getNetworkName())).append("\"")
                .append(System.lineSeparator());
        // network_secret 为空时省略
        if (config.getNetworkSecret() != null && !config.getNetworkSecret().isEmpty()) {
            sb.append("network_secret = \"").append(escape(config.getNetworkSecret())).append("\"")
                    .append(System.lineSeparator());
        }

        // peer
        sb.append(System.lineSeparator());
        sb.append("[[peer]]").append(System.lineSeparator());
        sb.append("uri = \"").append(escape(normalizeRelayUri(config.getRelayServer()))).append("\"")
                .append(System.lineSeparator());

        // flags：不创建 TUN 设备，使用 smoltcp 用户态协议栈
        // 避免 Windows 上需要管理员权限创建 Wintun/TUN 设备
        sb.append(System.lineSeparator());
        sb.append("[flags]").append(System.lineSeparator());
        sb.append("no_tun = true").append(System.lineSeparator());
        sb.append("use_smoltcp = true").append(System.lineSeparator());

        return sb.toString();
    }

    /**
     * 规范化中继服务器 URI：
     * - 若已带 tcp:// 前缀则原样返回
     * - 否则补全 tcp:// 前缀
     */
    public static String normalizeRelayUri(String relayServer) {
        if (relayServer == null || relayServer.isEmpty()) {
            return "";
        }
        String trimmed = relayServer.trim();
        if (trimmed.startsWith("tcp://") || trimmed.startsWith("udp://")
                || trimmed.startsWith("wg://") || trimmed.startsWith("tls://")) {
            return trimmed;
        }
        return "tcp://" + trimmed;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
