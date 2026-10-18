package com.easytier.fabric.lan;

import com.easytier.fabric.lan.command.EasyTierCommand;
import com.easytier.fabric.lan.config.ConfigManager;
import com.easytier.fabric.lan.config.TomlConfig;
import com.easytier.fabric.lan.config.Version;
import com.easytier.fabric.lan.platform.PlatformDetector;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * EasyTier Fabric LAN 模组主入口
 * 服务端和客户端共用初始化逻辑
 */
public class EasyTierFabricLan implements ModInitializer {

    public static final String MOD_ID = "easytier-go";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer currentServer;

    @Override
    public void onInitialize() {
        LOGGER.info("EasyTier Fabric LAN 正在初始化...");

        // 最早期执行平台探测
        PlatformDetector.detect();

        // 初始化服务（使用运行目录下的 config/easytier-go）
        Path gameDir = Paths.get("").toAbsolutePath().resolve("config").resolve("easytier-go");
        EasyTierService service = EasyTierService.getInstance(gameDir);
        service.getLogManager().info("EasyTier Fabric LAN 初始化，平台: "
                + PlatformDetector.getPlatform() + " / " + PlatformDetector.getArchitecture());

        if (!PlatformDetector.isSupported()) {
            LOGGER.warn("当前平台不支持 EasyTier，模组功能将被禁用");
        }

        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EasyTierCommand.register(dispatcher));

        // 服务端生命周期
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            onServerStarted(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EasyTierService.getInstance().stop();
            currentServer = null;
        });

        LOGGER.info("EasyTier Fabric LAN 初始化完成");
    }

    /**
     * 服务端启动后处理：世界加载自动组网
     */
    private void onServerStarted(MinecraftServer server) {
        if (!PlatformDetector.isSupported()) {
            return;
        }

        EasyTierService service = EasyTierService.getInstance();
        Path serverDir = service.getBaseDir().resolve("server");

        // 检查全局自动启动开关
        boolean autoStart = false;
        Path serverConfig = service.getBaseDir().resolve("server.toml");
        if (Files.exists(serverConfig)) {
            try {
                String content = Files.readString(serverConfig);
                autoStart = content.contains("auto_start_on_world_load")
                        && content.contains("true");
            } catch (Exception e) {
                LOGGER.warn("读取 server.toml 失败: {}", e.getMessage());
            }
        }

        if (!autoStart) {
            return;
        }

        // 读取世界级 active.json
        String worldName = server.getSaveProperties().getLevelName();
        Path worldActive = serverDir.resolve(worldName).resolve("active.json");
        if (!Files.exists(worldActive)) {
            return;
        }

        try {
            String content = Files.readString(worldActive);
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            String networkName = json.has("network_name") ? json.get("network_name").getAsString() : null;
            String versionStr = json.has("version") ? json.get("version").getAsString() : "v1.0";
            boolean worldAutoStart = !json.has("auto_start") || json.get("auto_start").getAsBoolean();

            if (networkName != null && worldAutoStart) {
                Version version = Version.parse(versionStr);
                if (version == null) version = new Version(1, 0);

                ConfigManager cm = service.getConfigManager();
                TomlConfig cfg = cm.loadConfig(networkName, version);
                if (cfg != null && !cfg.isCorrupted()) {
                    LOGGER.info("自动启动 EasyTier: {} @ {}", networkName, version);
                    service.start(
                            cfg.getNetworkName(), version,
                            cfg.getRelayServer() != null ? cfg.getRelayServer() : "",
                            cfg.getNetworkSecret() != null ? cfg.getNetworkSecret() : "",
                            cfg.getDevName() != null ? cfg.getDevName() : "et_fabric",
                            cfg.isDhcp());
                }
            }
        } catch (Exception e) {
            LOGGER.error("自动启动 EasyTier 失败: {}", e.getMessage());
        }
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }
}
