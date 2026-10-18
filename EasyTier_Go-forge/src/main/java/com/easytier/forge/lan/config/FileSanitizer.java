package com.easytier.forge.lan.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件名安全处理
 * 将网络名称 sanitize 为可安全写入磁盘的文件名片段
 */
public final class FileSanitizer {

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1f]");
    private static final Set<String> WINDOWS_RESERVED = new HashSet<>(Arrays.asList(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    ));

    private FileSanitizer() {
    }

    /**
     * 将网络名称 sanitize 为安全的文件名片段
     */
    public static String sanitize(String name) {
        if (name == null) {
            return "network";
        }

        String result = ILLEGAL_CHARS.matcher(name).replaceAll("_");

        result = result.trim();
        while (result.startsWith(".")) {
            result = result.substring(1).trim();
        }
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1).trim();
        }

        if (!result.isEmpty()) {
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 64) {
                StringBuilder sb = new StringBuilder();
                int byteCount = 0;
                for (int i = 0; i < result.length(); i++) {
                    char c = result.charAt(i);
                    int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
                    if (byteCount + charBytes > 64) {
                        break;
                    }
                    sb.append(c);
                    byteCount += charBytes;
                }
                result = sb.toString();
            }
        }

        if (result.isEmpty() || result.chars().allMatch(c -> c == '_')) {
            result = "network";
        }

        if (WINDOWS_RESERVED.contains(result.toUpperCase())) {
            result = result + "_net";
        }

        return result;
    }

    /**
     * 从文件名解析网络名称和版本
     */
    public static String[] parseFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(".toml")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - 5);
        int lastUnderscore = base.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return null;
        }
        String versionPart = base.substring(lastUnderscore + 1);
        if (!versionPart.matches("v\\d+\\.\\d+")) {
            return null;
        }
        String namePart = base.substring(0, lastUnderscore);
        if (namePart.isEmpty()) {
            return null;
        }
        return new String[]{namePart, versionPart};
    }
}
