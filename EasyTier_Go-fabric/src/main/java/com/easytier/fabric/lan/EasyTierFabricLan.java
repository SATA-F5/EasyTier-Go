package com.easytier.fabric.lan;

import com.easytier.fabric.lan.command.EasyTierCommand;
import com.easytier.fabric.lan.config.ConfigManager;
import com.easytier.fabric.lan.config.TomlConfig;
import com.easytier.fabric.lan.platform.PlatformDetector;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        ConfigManager cm = service.getConfigManager();

        // 检查全局自动启动开关
        if (!cm.isAutoStartOnWorldLoad()) {
            return;
        }

        // 读取世界级 active.json
        String worldName = server.getSaveProperties().getLevelName();
        ConfigManager.ActiveConfig active = cm.readWorldActive(worldName);
        if (active == null || active.network_name == null || active.network_name.isEmpty()) {
            return;
        }
        if (!active.auto_start) {
            return;
        }

        TomlConfig cfg = cm.loadConfig(active.network_name);
        if (cfg != null && !cfg.isCorrupted()) {
            String startName = cfg.getNetworkName() != null && !cfg.getNetworkName().isEmpty()
                    ? cfg.getNetworkName() : active.network_name;
            LOGGER.info("自动启动 EasyTier: {}", startName);
            boolean started = service.start(
                    startName,
                    cfg.getRelayServer() != null ? cfg.getRelayServer() : "",
                    cfg.getNetworkSecret() != null ? cfg.getNetworkSecret() : "",
                    cfg.getDevName() != null ? cfg.getDevName() : "et_fabric",
                    cfg.isDhcp());
            if (!started) {
                LOGGER.error("自动启动 EasyTier 失败: {}", startName);
            }
        } else {
            LOGGER.warn("自动启动配置不存在或已损坏: {}", active.network_name);
        }
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }
}
