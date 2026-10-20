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
     * 规则：
     * 1. \ / : * ? " < > | 及控制字符替换为 _
     * 2. 去除首尾空格和点号
     * 3. 不超过 64 个 UTF-8 字符，超出截断
     * 4. 空结果或全为 _ 时回退为 network
     * 5. Windows 保留设备名自动追加 _net 后缀
     */
    public static String sanitize(String name) {
        if (name == null) {
            return "network";
        }

        // 1. 替换非法字符
        String result = ILLEGAL_CHARS.matcher(name).replaceAll("_");

        // 2. 去除首尾空格和点号
        result = result.trim();
        while (result.startsWith(".")) {
            result = result.substring(1).trim();
        }
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1).trim();
        }

        // 3. UTF-8 字符数不超过 64
        if (!result.isEmpty()) {
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 64) {
                // 按字符截断，确保不超过 64 字节
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

        // 4. 空或全为 _ 时回退
        if (result.isEmpty() || result.chars().allMatch(c -> c == '_')) {
            result = "network";
        }

        // 5. Windows 保留设备名
        if (WINDOWS_RESERVED.contains(result.toUpperCase())) {
            result = result + "_net";
        }

        return result;
    }

    /**
     * 从文件名解析网络名称和版本
     * 文件名格式: <sanitized_name>_v<major>.<minor>.toml
     * 从右往左匹配最后一段 v<数字>.<数字>
     *
     * @return 长度为2的数组: [sanitizedName, versionString]，解析失败返回 null
     */
    public static String[] parseFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(".toml")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - 5);
        // 从右往左匹配 _v\d+\.\d+
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
