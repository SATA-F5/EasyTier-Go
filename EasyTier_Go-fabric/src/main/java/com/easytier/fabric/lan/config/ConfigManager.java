package com.easytier.fabric.lan.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 配置文件管理器
 * 负责扫描、加载、保存、删除 configs/ 下的 TOML 配置
 * 配置列表和版本列表通过扫描目录动态生成，不维护额外索引文件
 */
public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("EasyTierConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path baseDir;
    private final Path configsDir;

    public ConfigManager(Path baseDir) {
        this.baseDir = baseDir;
        this.configsDir = baseDir.resolve("configs");
        try {
            Files.createDirectories(configsDir);
        } catch (IOException e) {
            LOGGER.error("无法创建配置目录: {}", e.getMessage());
        }
    }

    public Path getConfigsDir() {
        return configsDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * 扫描所有 TOML 配置，按网络名称分组
     * key = 网络名称（instance_name），value = 该网络的所有版本（按版本号升序）
     */
    public Map<String, List<TomlConfig>> scanAllConfigs() {
        Map<String, List<TomlConfig>> result = new LinkedHashMap<>();
        if (!Files.exists(configsDir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir, "*.toml")) {
            for (Path file : stream) {
                TomlConfig cfg = loadConfig(file);
                if (cfg == null) {
                    continue;
                }
                String name = cfg.getInstanceName();
                if (name == null || name.isEmpty()) {
                    // 从文件名解析
                    String[] parsed = FileSanitizer.parseFileName(file.getFileName().toString());
                    if (parsed != null) {
                        name = parsed[0];
                        cfg.setInstanceName(name);
                    } else {
                        name = file.getFileName().toString();
                    }
                }
                result.computeIfAbsent(name, k -> new ArrayList<>()).add(cfg);
            }
        } catch (IOException e) {
            LOGGER.error("扫描配置目录失败: {}", e.getMessage());
        }

        // 每个网络内按版本排序
        for (List<TomlConfig> list : result.values()) {
            list.sort((a, b) -> {
                Version va = a.getVersion();
                Version vb = b.getVersion();
                if (va == null && vb == null) return 0;
                if (va == null) return -1;
                if (vb == null) return 1;
                return va.compareTo(vb);
            });
        }

        return result;
    }

    /**
     * 获取所有网络名称列表
     */
    public List<String> listNetworkNames() {
        return new ArrayList<>(scanAllConfigs().keySet());
    }

    /**
     * 获取指定网络的所有版本
     */
    public List<TomlConfig> getVersions(String networkName) {
        Map<String, List<TomlConfig>> all = scanAllConfigs();
        return all.getOrDefault(networkName, Collections.emptyList());
    }

    /**
     * 获取指定网络的最新版本
     */
    public TomlConfig getLatestVersion(String networkName) {
        List<TomlConfig> versions = getVersions(networkName);
        if (versions.isEmpty()) {
            return null;
        }
        return versions.get(versions.size() - 1);
    }

    /**
     * 根据网络名称和版本加载配置
     */
    public TomlConfig loadConfig(String networkName, Version version) {
        String fileName = FileSanitizer.sanitize(networkName) + "_" + version.toString() + ".toml";
        Path file = configsDir.resolve(fileName);
        return loadConfig(file);
    }

    /**
     * 从文件加载配置
     */
    public TomlConfig loadConfig(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            TomlConfig config = TomlParser.parse(content);
            config.setSourceFileName(file.getFileName().toString());

            // 从文件名补充版本（如果 TOML 注释中没有）
            if (config.getVersion() == null) {
                String[] parsed = FileSanitizer.parseFileName(file.getFileName().toString());
                if (parsed != null) {
                    Version v = Version.parse(parsed[1]);
                    if (v != null) {
                        config.setVersion(v);
                    }
                }
            }

            // 字段缺失时用默认值补全（仅内存，不写盘）
            if (config.getDevName() == null || config.getDevName().isEmpty()) {
                config.setDevName("et_fabric");
                LOGGER.warn("配置 {} 缺少 dev_name，使用默认值 et_fabric", file.getFileName());
            }
            if (config.getListeners() == null || config.getListeners().isEmpty()) {
                List<String> defaultListeners = new ArrayList<>();
                defaultListeners.add("tcp://0.0.0.0:11010");
                defaultListeners.add("udp://0.0.0.0:11010");
                defaultListeners.add("wg://0.0.0.0:11011");
                config.setListeners(defaultListeners);
                LOGGER.warn("配置 {} 缺少 listeners，使用默认值", file.getFileName());
            }

            return config;
        } catch (IOException e) {
            LOGGER.error("读取配置失败 {}: {}", file, e.getMessage());
            TomlConfig corrupted = new TomlConfig();
            corrupted.setCorrupted(true);
            corrupted.setSourceFileName(file.getFileName().toString());
            return corrupted;
        }
    }

    /**
     * 保存配置为指定版本
     * 如果文件已存在则覆盖
     */
    public boolean saveConfig(TomlConfig config) {
        if (config.getVersion() == null) {
            config.setVersion(new Version(1, 0));
        }
        String fileName = config.toFileName();
        Path file = configsDir.resolve(fileName);
        try {
            String content = TomlGenerator.generate(config);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            LOGGER.info("配置已保存: {}", fileName);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存配置失败 {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * 保存为次版本 +1 的新版本
     * 返回新版本号
     */
    public Version saveAsNewVersion(TomlConfig config) {
        TomlConfig latest = getLatestVersion(config.getNetworkName());
        Version newVersion;
        if (latest != null && latest.getVersion() != null) {
            newVersion = latest.getVersion().nextMinor();
        } else {
            newVersion = new Version(1, 0);
        }
        config.setVersion(newVersion);
        if (saveConfig(config)) {
            return newVersion;
        }
        return null;
    }

    /**
     * 删除指定网络的指定版本
     * 如果是该网络最后一个版本，则等价删除整个网络配置
     */
    public boolean deleteConfig(String networkName, Version version) {
        String fileName = FileSanitizer.sanitize(networkName) + "_" + version.toString() + ".toml";
        Path file = configsDir.resolve(fileName);
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                LOGGER.info("配置已删除: {}", fileName);
                return true;
            }
            return false;
        } catch (IOException e) {
            LOGGER.error("删除配置失败 {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * 删除指定网络的所有版本
     */
    public int deleteNetwork(String networkName) {
        List<TomlConfig> versions = getVersions(networkName);
        int count = 0;
        for (TomlConfig cfg : versions) {
            if (cfg.getVersion() != null && deleteConfig(networkName, cfg.getVersion())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 检查配置文件是否存在
     */
    public boolean configExists(String networkName, Version version) {
        String fileName = FileSanitizer.sanitize(networkName) + "_" + version.toString() + ".toml";
        return Files.exists(configsDir.resolve(fileName));
    }

    /**
     * 获取指定网络的下一个可用版本号
     */
    public Version getNextVersion(String networkName) {
        TomlConfig latest = getLatestVersion(networkName);
        if (latest != null && latest.getVersion() != null) {
            return latest.getVersion().nextMinor();
        }
        return new Version(1, 0);
    }

    // ==================== active.json 管理 ====================

    /**
     * 读取 active.json
     */
    public ActiveConfig readActive() {
        Path file = baseDir.resolve("active.json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(content, ActiveConfig.class);
        } catch (Exception e) {
            LOGGER.warn("读取 active.json 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入 active.json
     */
    public boolean writeActive(ActiveConfig active) {
        Path file = baseDir.resolve("active.json");
        try {
            Files.writeString(file, GSON.toJson(active), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.error("写入 active.json 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * active.json 数据结构
     */
    public static class ActiveConfig {
        public String network_name;
        public String version;
        public boolean auto_start;

        public ActiveConfig() {
        }

        public ActiveConfig(String networkName, String version, boolean autoStart) {
            this.network_name = networkName;
            this.version = version;
            this.auto_start = autoStart;
        }
    }
}
