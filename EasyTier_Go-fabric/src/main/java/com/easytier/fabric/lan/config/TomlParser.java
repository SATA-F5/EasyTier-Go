package com.easytier.fabric.lan.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级 TOML 解析器，仅支持 EasyTier 配置使用的子集：
 * - 顶层 key = "value" / key = true / key = 123
 * - [section] 下的 key = value
 * - [[array_of_tables]] 下的 key = value
 * - key = [ "a", "b" ] 数组
 * - # 注释（顶部注释中提取生成时间）
 */
public final class TomlParser {

    private TomlParser() {
    }

    /**
     * 解析 TOML 文本为 TomlConfig
     */
    public static TomlConfig parse(String content) {
        TomlConfig config = new TomlConfig();
        if (content == null || content.isEmpty()) {
            config.setCorrupted(true);
            return config;
        }

        try {
            String[] lines = content.split("\\r?\\n");
            String currentSection = "";
            boolean inArrayTable = false;
            Map<String, String> topLevel = new LinkedHashMap<>();
            Map<String, String> networkIdentity = new LinkedHashMap<>();
            Map<String, String> flags = new LinkedHashMap<>();
            Map<String, String> peer = new LinkedHashMap<>();
            List<String> listeners = new ArrayList<>();
            String generatedFromComment = null;

            for (String rawLine : lines) {
                String line = rawLine.trim();

                // 提取顶部注释中的生成时间
                if (line.startsWith("#")) {
                    String commentContent = line.substring(1).trim();
                    if (commentContent.startsWith("generated")) {
                        int eq = commentContent.indexOf('=');
                        if (eq >= 0) {
                            generatedFromComment = commentContent.substring(eq + 1).trim();
                        }
                    }
                    continue;
                }

                if (line.isEmpty()) {
                    continue;
                }

                // section header
                if (line.startsWith("[[") && line.endsWith("]]")) {
                    currentSection = line.substring(2, line.length() - 2).trim();
                    inArrayTable = true;
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                    inArrayTable = false;
                    continue;
                }

                // key = value
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                // 数组值 listeners = [ ... ]（可能跨多行，这里只处理单行）
                if (value.startsWith("[")) {
                    String arrayContent = extractArrayContent(content, line, rawLine);
                    listeners = parseStringArray(arrayContent);
                    continue;
                }

                String unquoted = unquote(value);

                if (currentSection.isEmpty()) {
                    topLevel.put(key, unquoted);
                } else if (currentSection.equals("network_identity")) {
                    networkIdentity.put(key, unquoted);
                } else if (currentSection.equals("flags")) {
                    flags.put(key, unquoted);
                } else if (inArrayTable && currentSection.equals("peer")) {
                    peer.put(key, unquoted);
                }
            }

            // 填充 config
            config.setInstanceName(topLevel.getOrDefault("instance_name", ""));
            String instanceId = topLevel.get("instance_id");
            config.setInstanceId(instanceId != null && !instanceId.isEmpty()
                    ? instanceId : TomlConfig.generateInstanceId());
            config.setDhcp(Boolean.parseBoolean(topLevel.getOrDefault("dhcp", "true")));
            if (topLevel.containsKey("ipv4")) {
                config.setManualIpv4(topLevel.get("ipv4"));
                config.setDhcp(false);
            }
            if (!listeners.isEmpty()) {
                config.setListeners(listeners);
            }

            config.setNetworkName(networkIdentity.getOrDefault("network_name", ""));
            config.setNetworkSecret(networkIdentity.getOrDefault("network_secret", ""));
            // dev_name 优先从顶层读取，兼容旧格式从 [flags] 读取
            String devName = topLevel.get("dev_name");
            if (devName == null || devName.isEmpty()) {
                devName = flags.getOrDefault("dev_name", "et_fabric");
            }
            config.setDevName(devName);

            String peerUri = peer.get("uri");
            if (peerUri != null) {
                // 去掉 tcp:// 前缀存储原始中继地址
                config.setRelayServer(stripProtocol(peerUri));
            }

            config.setGeneratedTimestamp(generatedFromComment);

            // 校验关键字段
            if (config.getNetworkName().isEmpty() && config.getInstanceName().isEmpty()) {
                config.setCorrupted(true);
            }

        } catch (Exception e) {
            config.setCorrupted(true);
        }

        return config;
    }

    /**
     * 从内容中提取数组的完整内容（处理跨行数组）
     */
    private static String extractArrayContent(String fullContent, String firstLine, String rawFirstLine) {
        if (firstLine.contains("]")) {
            int start = firstLine.indexOf('[');
            int end = firstLine.indexOf(']', start);
            return firstLine.substring(start + 1, end);
        }
        // 跨行数组
        int contentIdx = fullContent.indexOf(rawFirstLine);
        if (contentIdx < 0) {
            return "";
        }
        int bracketStart = fullContent.indexOf('[', contentIdx);
        int bracketEnd = fullContent.indexOf(']', bracketStart);
        if (bracketEnd < 0) {
            return "";
        }
        return fullContent.substring(bracketStart + 1, bracketEnd);
    }

    private static List<String> parseStringArray(String arrayContent) {
        List<String> result = new ArrayList<>();
        String[] items = arrayContent.split(",");
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(unquote(trimmed));
            }
        }
        return result;
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
            value = value.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static String stripProtocol(String uri) {
        if (uri == null) {
            return "";
        }
        for (String proto : new String[]{"tcp://", "udp://", "wg://", "tls://"}) {
            if (uri.startsWith(proto)) {
                return uri.substring(proto.length());
            }
        }
        return uri;
    }
}
