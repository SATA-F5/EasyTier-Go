package com.easytier.forge.lan;

import com.easytier.forge.lan.command.EasyTierCommand;
import com.easytier.forge.lan.config.ConfigManager;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.config.Version;
import com.easytier.forge.lan.platform.PlatformDetector;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * EasyTier Forge LAN 模组主入口
 */
@Mod(EasyTierForgeLan.MOD_ID)
public class EasyTierForgeLan {

    public static final String MOD_ID = "easytier_go";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static MinecraftServer currentServer;

    public EasyTierForgeLan() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("EasyTier Forge LAN 正在初始化...");

        PlatformDetector.detect();

        Path gameDir = Paths.get("").toAbsolutePath().resolve("config").resolve("easytier_go");
        EasyTierService service = EasyTierService.getInstance(gameDir);
        service.getLogManager().info("EasyTier Forge LAN 初始化，平台: "
                + PlatformDetector.getPlatform() + " / " + PlatformDetector.getArchitecture());

        if (!PlatformDetector.isSupported()) {
            LOGGER.warn("当前平台不支持 EasyTier，模组功能将被禁用");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                EasyTierService.getInstance().stop();
            } catch (Exception ignored) {
            }
        }, "easytier-shutdown"));

        LOGGER.info("EasyTier Forge LAN 初始化完成");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            EasyTierCommand.register(event.getDispatcher());
        } catch (Throwable t) {
            LOGGER.error("注册 EasyTier 命令失败", t);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        currentServer = event.getServer();
        onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        try {
            EasyTierService.getInstance().stop();
        } catch (Exception ignored) {
        }
        currentServer = null;
    }

    /**
     * 服务端启动后处理：世界加载自动组网
     */
    private void onServerStarted(MinecraftServer server) {
        if (!PlatformDetector.isSupported()) {
            return;
        }

        EasyTierService service;
        try {
            service = EasyTierService.getInstance();
        } catch (IllegalStateException e) {
            Path gameDir = Paths.get("").toAbsolutePath().resolve("config").resolve("easytier_go");
            service = EasyTierService.getInstance(gameDir);
        }

        Path serverDir = service.getBaseDir().resolve("server");

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

        String worldName = server.getWorldData().getLevelName();
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
                            cfg.getDevName() != null ? cfg.getDevName() : "et_forge",
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
