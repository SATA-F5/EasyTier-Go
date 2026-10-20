package com.easytier.forge.lan.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 多人游戏自动连接配置
 * 存储每个服务器地址对应的 EasyTier 自动连接设置
 */
public class AutoConnectConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, AutoConnectEntry>>() {}.getType();

    private final Path configFile;
    private final Map<String, AutoConnectEntry> entries = new HashMap<>();

    public static class AutoConnectEntry {
        public boolean enabled;
        public String networkName;
        public String version;
        public String relayServer;
        public String networkSecret;
        public int virtualPort;
        public boolean portForward;

        public AutoConnectEntry() {
            this.enabled = false;
            this.networkName = "";
            this.version = "v1.0";
            this.relayServer = "";
            this.networkSecret = "";
            this.virtualPort = 25565;
            this.portForward = true;
        }
    }

    public AutoConnectConfig(Path baseDir) {
        this.configFile = baseDir.resolve("autoconnect.json");
        load();
    }

    public void load() {
        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                Map<String, AutoConnectEntry> loaded = GSON.fromJson(json, MAP_TYPE);
                if (loaded != null) {
                    entries.putAll(loaded);
                }
            }
        } catch (IOException e) {
            // 使用默认空配置
        }
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(entries));
        } catch (IOException e) {
            // 静默失败
        }
    }

    public AutoConnectEntry get(String serverAddress) {
        return entries.computeIfAbsent(serverAddress, k -> new AutoConnectEntry());
    }

    public void put(String serverAddress, AutoConnectEntry entry) {
        entries.put(serverAddress, entry);
        save();
    }

    public boolean isEnabled(String serverAddress) {
        AutoConnectEntry entry = entries.get(serverAddress);
        return entry != null && entry.enabled;
    }
}
