package com.easytier.forge.lan.config;

import java.util.Objects;

/**
 * 配置版本号，格式 v<major>.<minor>
 * 按数字比较，v1.10 > v1.9
 */
public final class Version implements Comparable<Version> {

    private final int major;
    private final int minor;

    public Version(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    /**
     * 解析版本字符串，如 "v1.1"
     */
    public static Version parse(String versionStr) {
        if (versionStr == null) {
            return null;
        }
        String s = versionStr.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        String[] parts = s.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        try {
            int maj = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());
            return new Version(maj, min);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 次版本 +1，返回新版本
     */
    public Version nextMinor() {
        return new Version(major, minor + 1);
    }

    /**
     * 主版本 +1，次版本归零
     */
    public Version nextMajor() {
        return new Version(major + 1, 0);
    }

    @Override
    public int compareTo(Version other) {
        int maj = Integer.compare(this.major, other.major);
        if (maj != 0) {
            return maj;
        }
        return Integer.compare(this.minor, other.minor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version)) return false;
        Version version = (Version) o;
        return major == version.major && minor == version.minor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }

    @Override
    public String toString() {
        return "v" + major + "." + minor;
    }
}
