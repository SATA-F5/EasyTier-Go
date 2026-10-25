package com.easytier.forge.lan;

import com.easytier.forge.lan.command.EasyTierCommand;
import com.easytier.forge.lan.config.ConfigManager;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.platform.PlatformDetector;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * EasyTier Forge LAN 模组主入口
 */
@Mod(EasyTierForgeLan.MOD_ID)
public class EasyTierForgeLan {

    public static final String MOD_ID = "easytier_go";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static MinecraftServer currentServer;

    /**
     * 模组数据目录：<游戏目录>/config/easytier_go
     * 使用 FMLPaths.GAMEDIR，避免不同启动器工作目录不一致导致配置分裂。
     */
    public static Path getConfigDir() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("easytier_go");
    }

    public EasyTierForgeLan() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("EasyTier Forge LAN 正在初始化...");

        PlatformDetector.detect();

        Path gameDir = getConfigDir();
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
    public void onServerStarted(ServerStartedEvent event) {
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
            Path gameDir = getConfigDir();
            service = EasyTierService.getInstance(gameDir);
        }

        ConfigManager cm = service.getConfigManager();

        // 检查全局自动启动开关
        if (!cm.isAutoStartOnWorldLoad()) {
            return;
        }

        // 读取世界级 active.json
        String worldName = server.getWorldData().getLevelName();
        ConfigManager.ActiveConfig active = cm.readWorldActive(worldName);
        if (active == null || active.network_name == null || active.network_name.isEmpty()) {
            return;
        }
        if (!active.auto_start) {
            return;
        }

        TomlConfig cfg = cm.loadConfig(active.network_name);
        if (cfg == null || cfg.isCorrupted()) {
            LOGGER.warn("自动启动配置不存在或已损坏: {}", active.network_name);
            return;
        }

        String startName = cfg.getNetworkName() != null && !cfg.getNetworkName().isEmpty()
                ? cfg.getNetworkName() : active.network_name;
        String startRelay = cfg.getRelayServer() != null ? cfg.getRelayServer() : "";
        String startSecret = cfg.getNetworkSecret() != null ? cfg.getNetworkSecret() : "";
        String startDev = cfg.getDevName() != null ? cfg.getDevName() : "et_forge";
        boolean startDhcp = cfg.isDhcp();

        LOGGER.info("自动启动 EasyTier: {}", startName);

        // 启动流程包含进程清理与等待，放到后台线程避免阻塞服务器启动
        final EasyTierService starterService = service;
        Thread starter = new Thread(() -> {
            if (!starterService.start(startName, startRelay, startSecret, startDev, startDhcp)) {
                LOGGER.error("自动启动 EasyTier 失败: {}", startName);
            }
        }, "easytier-autostart");
        starter.setDaemon(true);
        starter.start();
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }
}
