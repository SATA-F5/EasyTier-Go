package com.easytier.forge.lan.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 运行时平台与架构探测
 * 在模组初始化最早期执行，结果缓存在静态字段。
 * Android 平台必须主动阻断。
 */
public final class PlatformDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("EasyTierPlatform");

    private static volatile Platform detectedPlatform = null;
    private static volatile Architecture detectedArch = null;
    private static volatile Boolean isAndroid = null;
    private static volatile String rawOsName = null;
    private static volatile String rawOsArch = null;

    private PlatformDetector() {
    }

    /**
     * 执行探测并缓存结果。可重复调用，首次之后直接返回缓存。
     */
    public static synchronized void detect() {
        if (detectedPlatform != null) {
            return;
        }

        rawOsName = System.getProperty("os.name", "");
        rawOsArch = System.getProperty("os.arch", "");
        String osName = rawOsName.toLowerCase();
        String osArch = rawOsArch.toLowerCase();

        // Android 检测（满足任一即判定）
        boolean android = false;
        if (osName.contains("android")) {
            android = true;
        }
        String javaVendor = System.getProperty("java.vendor", "");
        if (javaVendor.toLowerCase().contains("android")) {
            android = true;
        }
        try {
            Class.forName("android.app.Activity");
            android = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("android.net.VpnService");
            android = true;
        } catch (ClassNotFoundException ignored) {
        }
        isAndroid = android;

        // 平台判定
        if (android) {
            detectedPlatform = Platform.UNSUPPORTED;
        } else if (osName.contains("win")) {
            detectedPlatform = Platform.WINDOWS;
        } else if (osName.contains("mac")) {
            detectedPlatform = Platform.MACOS;
        } else if (osName.contains("linux")) {
            detectedPlatform = Platform.LINUX;
        } else {
            detectedPlatform = Platform.UNSUPPORTED;
        }

        // 架构判定
        if (osArch.equals("amd64") || osArch.equals("x86_64")) {
            detectedArch = Architecture.X86_64;
        } else if (osArch.equals("aarch64") || osArch.equals("arm64")) {
            detectedArch = Architecture.ARM64;
        } else if (osArch.equals("x86") || osArch.equals("i386") || osArch.equals("i686")) {
            // 32 位 x86 不支持
            detectedArch = Architecture.UNSUPPORTED;
        } else {
            detectedArch = Architecture.UNSUPPORTED;
        }

        if (android) {
            LOGGER.error("EasyTier Forge LAN 不支持 Android 平台。"
                    + "EasyTier 在 Android 上需要 VpnService/Magisk 集成，无法通过桌面 CLI 方式调用。"
                    + "替代方案：在 Android 上使用 EasyTier 官方应用建立虚拟网络，然后在 Minecraft 中直接连接虚拟 IP。");
        } else if (detectedPlatform == Platform.UNSUPPORTED || detectedArch == Architecture.UNSUPPORTED) {
            LOGGER.error("EasyTier Forge LAN 不支持当前平台：os.name='{}', os.arch='{}'。"
                    + "支持的平台：Windows 10/11 (x86_64/arm64)、Linux glibc (x86_64/aarch64)、macOS 12+ (x86_64/aarch64)。",
                    rawOsName, rawOsArch);
        } else {
            LOGGER.info("EasyTier 平台探测：{} / {}", detectedPlatform, detectedArch);
        }
    }

    public static Platform getPlatform() {
        if (detectedPlatform == null) {
            detect();
        }
        return detectedPlatform;
    }

    public static Architecture getArchitecture() {
        if (detectedArch == null) {
            detect();
        }
        return detectedArch;
    }

    public static boolean isAndroid() {
        if (isAndroid == null) {
            detect();
        }
        return isAndroid;
    }

    public static boolean isSupported() {
        return getPlatform().isSupported() && getArchitecture().isSupported();
    }

    public static String getRawOsName() {
        return rawOsName;
    }

    public static String getRawOsArch() {
        return rawOsArch;
    }

    /**
     * 获取当前平台可执行文件后缀（Windows 为 .exe，其他为空）
     */
    public static String getExecutableSuffix() {
        return getPlatform().isWindows() ? ".exe" : "";
    }

    /**
     * 获取二进制资源目录相对路径，如 "easytier/windows-x86_64"
     */
    public static String getBinaryResourcePath() {
        if (!isSupported()) {
            return null;
        }
        return "easytier/" + getPlatform().getBinaryDirName(getArchitecture());
    }
}
