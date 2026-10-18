package com.easytier.fabric.lan.platform;

/**
 * 支持的操作系统平台枚举
 */
public enum Platform {
    WINDOWS,
    MACOS,
    LINUX,
    UNSUPPORTED;

    public boolean isWindows() {
        return this == WINDOWS;
    }

    public boolean isMacOS() {
        return this == MACOS;
    }

    public boolean isLinux() {
        return this == LINUX;
    }

    public boolean isSupported() {
        return this != UNSUPPORTED;
    }

    /**
     * 返回 resources/easytier/ 下对应的二进制目录名
     */
    public String getBinaryDirName(Architecture arch) {
        String osPart;
        switch (this) {
            case WINDOWS:
                osPart = "windows";
                break;
            case MACOS:
                osPart = "macos";
                break;
            case LINUX:
                osPart = "linux";
                break;
            default:
                return null;
        }
        return osPart + "-" + arch.getDirSuffix();
    }
}
