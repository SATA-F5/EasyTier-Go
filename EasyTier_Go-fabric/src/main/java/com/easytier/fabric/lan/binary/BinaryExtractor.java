package com.easytier.fabric.lan.binary;

import com.easytier.fabric.lan.platform.Architecture;
import com.easytier.fabric.lan.platform.PlatformDetector;
import com.easytier.fabric.lan.util.LogManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * EasyTier CLI 下载与提取器
 * 从 GitHub Releases 下载当前平台对应的 zip 包，
 * 通过镜像列表依次尝试，最后回退原始 GitHub 链接。
 * 解压到 config/easytier-fabric/bin/ 并设置可执行权限。
 */
public class BinaryExtractor {

    /** GitHub 加速镜像列表，按优先级排列 */
    private static final String[] MIRRORS = {
            "https://raw.ihtw.moe/",
            "https://gh.zwy.one/",
            "https://gh.llkk.cc/",
            "https://ghfast.top/",
            "https://gh.h233.eu.org/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://gh.xxooo.cf/",
            "https://ghfile.geekertao.top/",
            "https://ghproxy.cxkpro.top/",
            "https://git.yylx.win/",
            "https://cdn.crashmc.com/",
            "https://githubproxy.cc/",
    };

    private static final String GITHUB_API =
            "https://api.github.com/repos/EasyTier/EasyTier/releases/latest";
    private static final String GITHUB_LATEST_DOWNLOAD =
            "https://github.com/EasyTier/EasyTier/releases/latest/download/";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int API_TIMEOUT_MS = 6000;

    private final Path binDir;
    private final Path versionFile;
    private final LogManager logManager;
    private Path coreBinary;
    private Path cliBinary;
    private String cachedVersion;

    public BinaryExtractor(Path baseDir, LogManager logManager) {
        this.binDir = baseDir.resolve("bin");
        this.versionFile = baseDir.resolve("bin").resolve("version.txt");
        this.logManager = logManager;
    }

    /**
     * 确保二进制可用：已存在且版本最新则跳过，否则从 GitHub 下载并解压
     */
    public boolean extract() {
        return extract(false);
    }

    /**
     * 确保二进制可用
     * @param force 强制重新下载
     */
    public boolean extract(boolean force) {
        if (!PlatformDetector.isSupported()) {
            logManager.error("当前平台不支持，无法获取 EasyTier 二进制");
            return false;
        }

        try {
            Files.createDirectories(binDir);
        } catch (IOException e) {
            logManager.error("无法创建 bin 目录: " + e.getMessage());
            return false;
        }

        String suffix = PlatformDetector.getExecutableSuffix();
        coreBinary = binDir.resolve("easytier-core" + suffix);
        cliBinary = binDir.resolve("easytier-cli" + suffix);

        // 已存在且非空
        if (isFileNonEmpty(coreBinary) && isFileNonEmpty(cliBinary)) {
            if (!force) {
                logManager.info("EasyTier 二进制已存在，跳过下载: " + binDir);
                ensureExecutable();
                return true;
            }
            logManager.info("强制重新下载 EasyTier 二进制...");
        } else {
            logManager.info("EasyTier 二进制不存在，开始下载...");
        }

        // 解析最新版本号
        String latestVersion = fetchLatestVersion();
        if (latestVersion != null) {
            logManager.info("最新版本: " + latestVersion);
            // 检查本地版本
            String localVersion = readLocalVersion();
            if (localVersion != null && localVersion.equals(latestVersion) && !force) {
                if (isFileNonEmpty(coreBinary) && isFileNonEmpty(cliBinary)) {
                    logManager.info("本地版本已是最新 (" + latestVersion + ")，跳过下载");
                    ensureExecutable();
                    return true;
                }
            }
        }

        // 解析下载地址
        String downloadUrl = resolveDownloadUrl();
        if (downloadUrl == null) {
            logManager.error("无法解析 EasyTier 下载地址");
            return false;
        }

        // 下载（带镜像回退）
        Path zipFile = binDir.resolve("easytier-download.zip");
        boolean downloaded = downloadWithMirrors(downloadUrl, zipFile);
        if (!downloaded) {
            logManager.error("所有镜像及原始链接均下载失败");
            return false;
        }

        // 解压
        logManager.info("下载完成，正在解压...");
        if (!unzipAndRelocate(zipFile)) {
            logManager.error("解压失败");
            return false;
        }

        // 清理 zip
        try {
            Files.deleteIfExists(zipFile);
        } catch (IOException ignored) {
        }

        // 设置权限
        ensureExecutable();

        // 验证
        if (!isFileNonEmpty(coreBinary) || !isFileNonEmpty(cliBinary)) {
            logManager.error("解压后未找到 easytier-core 或 easytier-cli，发布包结构可能已变更");
            return false;
        }

        // 写入版本号
        if (latestVersion != null) {
            writeLocalVersion(latestVersion);
        }

        logManager.info("EasyTier 二进制已就绪: " + binDir);
        return true;
    }

    /**
     * 从 GitHub API 获取最新版本号（通过镜像回退）
     */
    private String fetchLatestVersion() {
        // 尝试直接 API
        try {
            String json = httpGetString(GITHUB_API, API_TIMEOUT_MS);
            if (json != null && !json.isEmpty()) {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("tag_name")) {
                    return root.get("tag_name").getAsString();
                }
            }
        } catch (Exception e) {
            logManager.warn("GitHub API 直连失败: " + e.getMessage());
        }

        // 通过镜像尝试 API
        for (String mirror : MIRRORS) {
            try {
                String mirrorApi = mirror + GITHUB_API;
                String json = httpGetString(mirrorApi, API_TIMEOUT_MS);
                if (json != null && !json.isEmpty() && json.contains("tag_name")) {
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    if (root.has("tag_name")) {
                        logManager.info("通过镜像获取版本信息: " + mirror);
                        return root.get("tag_name").getAsString();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        logManager.warn("无法获取最新版本号，将使用 latest 下载链接");
        return null;
    }

    private String readLocalVersion() {
        try {
            if (Files.exists(versionFile)) {
                return Files.readString(versionFile, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private void writeLocalVersion(String version) {
        try {
            Files.writeString(versionFile, version, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logManager.warn("写入版本文件失败: " + e.getMessage());
        }
    }

    /**
     * 解析当前平台对应的下载地址
     * 优先通过 GitHub API 获取真实资产链接，失败时回退到 latest 重定向地址
     */
    private String resolveDownloadUrl() {
        String assetName = guessAssetName();

        // 尝试 GitHub API（直连）
        try {
            String apiResponse = httpGetString(GITHUB_API, API_TIMEOUT_MS);
            if (apiResponse != null && !apiResponse.isEmpty()) {
                String url = parseAssetUrlFromApi(apiResponse, assetName);
                if (url != null) {
                    logManager.info("通过 GitHub API 解析到下载地址: " + url);
                    return url;
                }
            }
        } catch (Exception e) {
            logManager.warn("GitHub API 访问失败: " + e.getMessage());
        }

        // 通过镜像尝试 API
        for (String mirror : MIRRORS) {
            try {
                String mirrorApi = mirror + GITHUB_API;
                String apiResponse = httpGetString(mirrorApi, API_TIMEOUT_MS);
                if (apiResponse != null && !apiResponse.isEmpty()) {
                    String url = parseAssetUrlFromApi(apiResponse, assetName);
                    if (url != null) {
                        logManager.info("通过镜像解析到下载地址: " + mirror);
                        return url;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 回退：latest 重定向地址
        String fallback = GITHUB_LATEST_DOWNLOAD + assetName;
        logManager.info("使用 latest 重定向地址: " + fallback);
        return fallback;
    }

    /**
     * 从 GitHub API JSON 中匹配当前平台的资产下载地址
     */
    private String parseAssetUrlFromApi(String json, String preferredAssetName) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets == null) return null;

            String platform = getPlatformKeyword();
            String arch = getArchKeyword();
            String altArch = getAltArchKeyword();

            // 优先精确匹配文件名
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (name.equalsIgnoreCase(preferredAssetName)) {
                    return asset.get("browser_download_url").getAsString();
                }
            }

            // 模糊匹配：包含平台和架构关键词
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                String name = asset.get("name").getAsString().toLowerCase();
                if (name.endsWith(".zip")
                        && name.contains(platform)
                        && (name.contains(arch) || (altArch != null && name.contains(altArch)))) {
                    return asset.get("browser_download_url").getAsString();
                }
            }
        } catch (Exception e) {
            logManager.warn("解析 GitHub API 响应失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 猜测当前平台的资产文件名
     */
    private String guessAssetName() {
        return "easytier-" + getPlatformKeyword() + "-" + getArchKeyword() + ".zip";
    }

    private String getPlatformKeyword() {
        switch (PlatformDetector.getPlatform()) {
            case WINDOWS: return "windows";
            case MACOS: return "macos";
            case LINUX: return "linux";
            default: return "unknown";
        }
    }

    private String getArchKeyword() {
        switch (PlatformDetector.getArchitecture()) {
            case X86_64: return "x86_64";
            case ARM64: return "aarch64";
            default: return "unknown";
        }
    }

    /** Windows arm64 资产可能命名为 arm64 而非 aarch64 */
    private String getAltArchKeyword() {
        if (PlatformDetector.getArchitecture() == Architecture.ARM64) {
            return "arm64";
        }
        return null;
    }

    /**
     * 通过镜像列表依次下载，最后尝试原始链接
     */
    private boolean downloadWithMirrors(String githubUrl, Path dest) {
        // 先尝试各个镜像
        for (int i = 0; i < MIRRORS.length; i++) {
            String mirrorUrl = MIRRORS[i] + githubUrl;
            logManager.info("尝试镜像 (" + (i + 1) + "/" + MIRRORS.length + "): " + MIRRORS[i]);
            try {
                if (downloadFile(mirrorUrl, dest)) {
                    logManager.info("镜像下载成功: " + MIRRORS[i]);
                    return true;
                }
            } catch (Exception e) {
                logManager.warn("镜像 " + MIRRORS[i] + " 失败: " + e.getMessage());
            }
        }

        // 最后尝试原始 GitHub 链接
        logManager.info("尝试原始 GitHub 链接...");
        try {
            if (downloadFile(githubUrl, dest)) {
                logManager.info("原始链接下载成功");
                return true;
            }
        } catch (Exception e) {
            logManager.warn("原始链接失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 下载单个文件，跟随重定向
     */
    private boolean downloadFile(String urlStr, Path dest) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "EasytierGo-Mod/1.0");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) {
                // 部分镜像对重定向处理不同，手动跟随一次
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String redirect = conn.getHeaderField("Location");
                    if (redirect != null) {
                        conn.disconnect();
                        return downloadFile(redirect, dest);
                    }
                }
                logManager.warn("HTTP " + code + " for " + urlStr);
                return false;
            }

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            long total = Files.size(dest);
            if (total < 1024) {
                logManager.warn("下载文件过小 (" + total + " bytes)，可能是错误页面");
                return false;
            }
            logManager.info("下载大小: " + (total / 1024) + " KB");
            return true;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * HTTP GET 返回字符串
     */
    private String httpGetString(String urlStr, int timeout) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "EasytierGo-Mod/1.0");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 解压 zip 并将二进制文件移到 bin 目录根
     */
    private boolean unzipAndRelocate(Path zipFile) {
        String suffix = PlatformDetector.getExecutableSuffix();
        String coreName = "easytier-core" + suffix;
        String cliName = "easytier-cli" + suffix;
        List<Path> extracted = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory() || entryName.contains("__MACOSX") || entryName.startsWith(".DS_Store")) {
                    zis.closeEntry();
                    continue;
                }
                String fileName = Path.of(entryName).getFileName().toString();
                Path target = binDir.resolve(fileName);
                Files.createDirectories(target.getParent());
                try {
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    extracted.add(target);
                } catch (IOException e) {
                    logManager.warn("解压文件失败 " + fileName + ": " + e.getMessage());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            logManager.error("解压 zip 失败: " + e.getMessage());
            return false;
        }

        for (Path p : extracted) {
            String fn = p.getFileName().toString();
            if (fn.equals(coreName)) coreBinary = p;
            if (fn.equals(cliName)) cliBinary = p;
        }

        // Windows 上检查 wintun.dll
        if (PlatformDetector.getPlatform().isWindows()) {
            Path wintun = binDir.resolve("wintun.dll");
            if (!Files.exists(wintun)) {
                logManager.warn("未找到 wintun.dll，Windows TUN 驱动可能无法正常工作");
            }
        }

        return true;
    }

    private void ensureExecutable() {
        if (PlatformDetector.getPlatform().isWindows()) return;
        if (coreBinary != null) setExecutable(coreBinary);
        if (cliBinary != null) setExecutable(cliBinary);
    }

    private void setExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            try {
                new ProcessBuilder("chmod", "+x", file.toString())
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ex) {
                logManager.warn("设置可执行权限失败: " + ex.getMessage());
            }
        } catch (IOException e) {
            logManager.warn("设置可执行权限失败: " + e.getMessage());
        }
    }

    private boolean isFileNonEmpty(Path file) {
        try {
            return file != null && Files.exists(file) && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isReady() {
        if (coreBinary == null || cliBinary == null) {
            String suffix = PlatformDetector.getExecutableSuffix();
            coreBinary = binDir.resolve("easytier-core" + suffix);
            cliBinary = binDir.resolve("easytier-cli" + suffix);
        }
        return isFileNonEmpty(coreBinary) && isFileNonEmpty(cliBinary);
    }

    public Path getCoreBinary() {
        if (coreBinary == null) {
            String suffix = PlatformDetector.getExecutableSuffix();
            coreBinary = binDir.resolve("easytier-core" + suffix);
        }
        return coreBinary;
    }

    public Path getCliBinary() {
        if (cliBinary == null) {
            String suffix = PlatformDetector.getExecutableSuffix();
            cliBinary = binDir.resolve("easytier-cli" + suffix);
        }
        return cliBinary;
    }

    public Path getBinDir() {
        return binDir;
    }

    public String getCachedVersion() {
        if (cachedVersion == null) {
            cachedVersion = readLocalVersion();
        }
        return cachedVersion;
    }

    /**
     * 强制重新下载（删除已有文件后重新下载）
     */
    public boolean reExtract() {
        try {
            if (Files.exists(binDir)) {
                Files.walk(binDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException e) {
            logManager.warn("清理旧二进制失败: " + e.getMessage());
        }
        coreBinary = null;
        cliBinary = null;
        cachedVersion = null;
        return extract(true);
    }
}
