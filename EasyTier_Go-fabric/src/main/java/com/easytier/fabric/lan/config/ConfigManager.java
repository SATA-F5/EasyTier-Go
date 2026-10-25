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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置文件管理器
 * 负责扫描、加载、保存、删除 configs/ 下的 TOML 配置
 * 每个网络只对应一个配置文件：<网络名称>.toml（如 mc.toml），不再使用版本号
 * 配置列表通过扫描目录动态生成，不维护额外索引文件
 */
public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("EasyTierConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern LEGACY_FILE_NAME = Pattern.compile("^(.+)_v(\\d+)\\.(\\d+)$");

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
        migrateLegacyFileNames();
    }

    public Path getConfigsDir() {
        return configsDir;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * 扫描所有 TOML 配置
     * key = 网络名称，value = 该网络唯一的配置
     */
    public synchronized Map<String, TomlConfig> scanAllConfigs() {
        Map<String, TomlConfig> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (!Files.exists(configsDir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir, "*.toml")) {
            for (Path file : stream) {
                // 已存在同名新格式文件时，忽略残留的旧版 <名称>_vX.Y.toml
                Matcher legacyMatcher = LEGACY_FILE_NAME.matcher(baseName(file));
                if (legacyMatcher.matches()
                        && Files.exists(configsDir.resolve(legacyMatcher.group(1) + ".toml"))) {
                    continue;
                }
                TomlConfig cfg = loadConfig(file);
                if (cfg == null) {
                    continue;
                }
                String name = cfg.getNetworkName();
                if (name == null || name.isEmpty()) {
                    name = cfg.getInstanceName();
                }
                if (name == null || name.isEmpty()) {
                    name = baseName(file);
                }
                if (name != null && !name.isEmpty()) {
                    cfg.setNetworkName(name);
                    result.put(name, cfg);
                }
            }
        } catch (IOException e) {
            LOGGER.error("扫描配置目录失败: {}", e.getMessage());
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
     * 根据网络名称加载配置
     */
    public TomlConfig loadConfig(String networkName) {
        if (networkName == null || networkName.isEmpty()) {
            return null;
        }
        Path file = findConfigFile(networkName);
        return file != null ? loadConfig(file) : null;
    }

    /**
     * 查找网络对应的配置文件。
     * 优先使用新格式 <名称>.toml；如果迁移失败或文件是后来放入的，
     * 回退到旧格式 <名称>_v<主>.<次>.toml 中版本最高的一个。
     */
    private Path findConfigFile(String networkName) {
        String safe = FileSanitizer.sanitize(networkName);
        Path target = configsDir.resolve(safe + ".toml");
        if (Files.exists(target)) {
            return target;
        }

        Path best = null;
        int bestVersion = Integer.MIN_VALUE;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir, "*.toml")) {
            for (Path file : stream) {
                Matcher matcher = LEGACY_FILE_NAME.matcher(baseName(file));
                if (!matcher.matches() || !safe.equalsIgnoreCase(matcher.group(1))) {
                    continue;
                }
                int version = legacyVersion(file);
                if (version > bestVersion) {
                    bestVersion = version;
                    best = file;
                }
            }
        } catch (IOException ignored) {
            // 目录不可读时返回 null
        }
        return best;
    }

    /**
     * 从文件加载配置
     */
    public synchronized TomlConfig loadConfig(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            TomlConfig config = TomlParser.parse(content);
            config.setSourceFileName(file.getFileName().toString());

            // 网络名称为空时从 instance_name 或文件名补全，避免保存/启动路径不一致
            if (config.getNetworkName() == null || config.getNetworkName().isEmpty()) {
                String instanceName = config.getInstanceName();
                config.setNetworkName(instanceName != null && !instanceName.isEmpty()
                        ? instanceName : baseName(file));
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
     * 保存配置。文件名固定为 <sanitize(network_name)>.toml，如果已存在则覆盖。
     */
    public synchronized boolean saveConfig(TomlConfig config) {
        if (config == null || config.getNetworkName() == null || config.getNetworkName().isEmpty()) {
            LOGGER.error("保存配置失败: 网络名称为空");
            return false;
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
     * 删除指定网络的配置文件
     */
    public synchronized boolean deleteConfig(String networkName) {
        if (networkName == null || networkName.isEmpty()) {
            return false;
        }
        String safe = FileSanitizer.sanitize(networkName);
        boolean deleted = false;

        Path target = configsDir.resolve(safe + ".toml");
        try {
            if (Files.exists(target)) {
                Files.delete(target);
                LOGGER.info("配置已删除: {}", target.getFileName());
                deleted = true;
            }
        } catch (IOException e) {
            LOGGER.error("删除配置失败 {}: {}", target.getFileName(), e.getMessage());
            return false;
        }

        // 同时清理同名旧格式文件，避免删除后旧版本“复活”
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir, "*.toml")) {
            for (Path file : stream) {
                Matcher matcher = LEGACY_FILE_NAME.matcher(baseName(file));
                if (matcher.matches() && safe.equalsIgnoreCase(matcher.group(1))) {
                    Files.delete(file);
                    LOGGER.info("旧配置已删除: {}", file.getFileName());
                    deleted = true;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("清理旧配置失败: {}", e.getMessage());
        }

        // 如果删除的是活动配置，同时清理 base active.json，避免 reload 指向已删除的配置
        try {
            ActiveConfig active = readActive();
            if (active != null && active.network_name != null
                    && active.network_name.equalsIgnoreCase(networkName)) {
                Files.deleteIfExists(baseDir.resolve("active.json"));
            }
        } catch (Exception e) {
            LOGGER.warn("清理 active.json 失败: {}", e.getMessage());
        }

        return deleted;
    }

    /**
     * 检查配置文件是否存在
     */
    public boolean configExists(String networkName) {
        if (networkName == null || networkName.isEmpty()) {
            return false;
        }
        return findConfigFile(networkName) != null;
    }

    /**
     * 将旧版 <名称>_v<主>.<次>.toml 迁移为 <名称>.toml。
     * 同一网络存在多个旧版本时，迁移版本号最高的一个；若目标文件已存在则不覆盖。
     */
    private void migrateLegacyFileNames() {
        if (!Files.exists(configsDir)) {
            return;
        }
        List<Path> legacyFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir, "*.toml")) {
            for (Path file : stream) {
                if (LEGACY_FILE_NAME.matcher(baseName(file)).matches()) {
                    legacyFiles.add(file);
                }
            }
        } catch (IOException e) {
            return;
        }
        if (legacyFiles.isEmpty()) {
            return;
        }

        // 版本号高的优先
        legacyFiles.sort(Comparator.comparingInt(ConfigManager::legacyVersion).reversed());
        for (Path file : legacyFiles) {
            String base = baseName(file);
            Matcher matcher = LEGACY_FILE_NAME.matcher(base);
            if (!matcher.matches()) {
                continue;
            }
            String name = matcher.group(1);
            Path target = configsDir.resolve(name + ".toml");
            if (Files.exists(target)) {
                continue;
            }
            try {
                Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
                LOGGER.info("已迁移旧配置文件名: {} -> {}", file.getFileName(), target.getFileName());
            } catch (Exception e) {
                try {
                    Files.move(file, target);
                    LOGGER.info("已迁移旧配置文件名: {} -> {}", file.getFileName(), target.getFileName());
                } catch (Exception ignored) {
                    LOGGER.warn("迁移旧配置文件名失败: {}", file.getFileName());
                }
            }
        }
    }

    private static int legacyVersion(Path file) {
        Matcher matcher = LEGACY_FILE_NAME.matcher(baseName(file));
        if (!matcher.matches()) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(2)) * 10000 + Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static String baseName(Path file) {
        String fileName = file.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".toml")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    // ==================== active.json 管理 ====================

    /**
     * 服务端自启动开关文件：<baseDir>/server.toml
     */
    private Path getServerSwitchFile() {
        return baseDir.resolve("server.toml");
    }

    /**
     * 世界级活动配置文件：<baseDir>/server/<world>/active.json
     */
    public Path getWorldActiveFile(String worldName) {
        String world = worldName != null && !worldName.isEmpty() ? worldName : "world";
        world = FileSanitizer.sanitize(world);
        return baseDir.resolve("server").resolve(world).resolve("active.json");
    }

    /**
     * 是否启用“世界加载时自动启动 EasyTier”
     */
    public boolean isAutoStartOnWorldLoad() {
        Path file = getServerSwitchFile();
        if (!Files.exists(file)) {
            return false;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String[] lines = content.split("\\r?\\n");
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                if ("auto_start_on_world_load".equals(key)) {
                    String value = line.substring(eq + 1).trim();
                    int comment = value.indexOf('#');
                    if (comment >= 0) {
                        value = value.substring(0, comment).trim();
                    }
                    return Boolean.parseBoolean(value);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("读取 server.toml 失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 写入“世界加载时自动启动 EasyTier”开关（保留 server.toml 中的其他配置）
     */
    public boolean setAutoStartOnWorldLoad(boolean enabled) {
        Path file = getServerSwitchFile();
        String newLine = "auto_start_on_world_load = " + enabled;
        List<String> lines = new ArrayList<>();
        boolean found = false;
        try {
            Files.createDirectories(baseDir);
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    int eq = trimmed.indexOf('=');
                    if (!trimmed.startsWith("#") && eq > 0
                            && "auto_start_on_world_load".equals(trimmed.substring(0, eq).trim())) {
                        lines.add(newLine);
                        found = true;
                    } else {
                        lines.add(line);
                    }
                }
            }
            if (!found) {
                lines.add(newLine);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.error("写入 server.toml 失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 读取世界级 active.json
     */
    public ActiveConfig readWorldActive(String worldName) {
        Path file = getWorldActiveFile(worldName);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            return GSON.fromJson(content, ActiveConfig.class);
        } catch (Exception e) {
            LOGGER.warn("读取世界 active.json 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入世界级 active.json
     */
    public boolean writeWorldActive(String worldName, ActiveConfig active) {
        Path file = getWorldActiveFile(worldName);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(active), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.error("写入世界 active.json 失败: {}", e.getMessage());
            return false;
        }
    }

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
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
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
     * active.json 数据结构（不再包含版本号）
     */
    public static class ActiveConfig {
        public String network_name;
        public boolean auto_start = true;

        public ActiveConfig() {
        }

        public ActiveConfig(String networkName, boolean autoStart) {
            this.network_name = networkName;
            this.auto_start = autoStart;
        }
    }
}
