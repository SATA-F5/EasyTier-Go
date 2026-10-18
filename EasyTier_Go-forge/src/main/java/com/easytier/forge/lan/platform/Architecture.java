package com.easytier.forge.lan.platform;

/**
 * CPU 架构枚举
 */
public enum Architecture {
    X86_64,
    ARM64,
    UNSUPPORTED;

    public boolean isSupported() {
        return this != UNSUPPORTED;
    }

    public String getDirSuffix() {
        switch (this) {
            case X86_64:
                return "x86_64";
            case ARM64:
                return "aarch64";
            default:
                return "unsupported";
        }
    }
}
