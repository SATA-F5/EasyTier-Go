package com.easytier.forge.lan.config;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TOML 配置生成器
 */
public final class TomlGenerator {

    private TomlGenerator() {
    }

    public static String generate(TomlConfig config) {
        StringBuilder sb = new StringBuilder();

        sb.append("# easytier-forge config").append(System.lineSeparator());
        sb.append("# network_name = ").append(nullSafe(config.getNetworkName())).append(System.lineSeparator());
        sb.append("# version      = ").append(config.getVersion() != null ? config.getVersion().toString() : "v1.0")
                .append(System.lineSeparator());
        sb.append("# generated    = ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .append(System.lineSeparator());

        sb.append("instance_name = \"").append(escape(config.getInstanceName())).append("\"")
                .append(System.lineSeparator());
        sb.append("instance_id = \"").append(escape(config.getInstanceId())).append("\"")
                .append(System.lineSeparator());
        sb.append("dhcp = ").append(config.isDhcp()).append(System.lineSeparator());

        if (!config.isDhcp() && config.getManualIpv4() != null && !config.getManualIpv4().isEmpty()) {
            sb.append("ipv4 = \"").append(escape(config.getManualIpv4())).append("\"")
                    .append(System.lineSeparator());
        }

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

        sb.append("rpc_portal = \"127.0.0.1:15888\"").append(System.lineSeparator());

        sb.append("dev_name = \"").append(escape(config.getDevName())).append("\"")
                .append(System.lineSeparator());

        sb.append(System.lineSeparator());
        sb.append("[network_identity]").append(System.lineSeparator());
        sb.append("network_name = \"").append(escape(config.getNetworkName())).append("\"")
                .append(System.lineSeparator());
        if (config.getNetworkSecret() != null && !config.getNetworkSecret().isEmpty()) {
            sb.append("network_secret = \"").append(escape(config.getNetworkSecret())).append("\"")
                    .append(System.lineSeparator());
        }

        sb.append(System.lineSeparator());
        sb.append("[[peer]]").append(System.lineSeparator());
        sb.append("uri = \"").append(normalizeRelayUri(config.getRelayServer())).append("\"")
                .append(System.lineSeparator());

        sb.append(System.lineSeparator());
        sb.append("[flags]").append(System.lineSeparator());
        sb.append("use_smoltcp = true").append(System.lineSeparator());

        return sb.toString();
    }

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
