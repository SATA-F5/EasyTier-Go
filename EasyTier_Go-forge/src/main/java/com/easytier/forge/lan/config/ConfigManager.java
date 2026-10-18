package com.easytier.forge.lan.config;

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

/**
 * 配置文件管理器
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

    public List<String> listNetworkNames() {
        return new ArrayList<>(scanAllConfigs().keySet());
    }

    public List<TomlConfig> getVersions(String networkName) {
        Map<String, List<TomlConfig>> all = scanAllConfigs();
        return all.getOrDefault(networkName, Collections.emptyList());
    }

    public TomlConfig getLatestVersion(String networkName) {
        List<TomlConfig> versions = getVersions(networkName);
        if (versions.isEmpty()) {
            return null;
        }
        return versions.get(versions.size() - 1);
    }

    public TomlConfig loadConfig(String networkName, Version version) {
        String fileName = FileSanitizer.sanitize(networkName) + "_" + version.toString() + ".toml";
        Path file = configsDir.resolve(fileName);
        return loadConfig(file);
    }

    public TomlConfig loadConfig(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            TomlConfig config = TomlParser.parse(content);
            config.setSourceFileName(file.getFileName().toString());

            if (config.getVersion() == null) {
                String[] parsed = FileSanitizer.parseFileName(file.getFileName().toString());
                if (parsed != null) {
                    Version v = Version.parse(parsed[1]);
                    if (v != null) {
                        config.setVersion(v);
                    }
                }
            }

            if (config.getDevName() == null || config.getDevName().isEmpty()) {
                config.setDevName("et_forge");
                LOGGER.warn("配置 {} 缺少 dev_name，使用默认值 et_forge", file.getFileName());
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

    public boolean configExists(String networkName, Version version) {
        String fileName = FileSanitizer.sanitize(networkName) + "_" + version.toString() + ".toml";
        return Files.exists(configsDir.resolve(fileName));
    }

    public Version getNextVersion(String networkName) {
        TomlConfig latest = getLatestVersion(networkName);
        if (latest != null && latest.getVersion() != null) {
            return latest.getVersion().nextMinor();
        }
        return new Version(1, 0);
    }

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
