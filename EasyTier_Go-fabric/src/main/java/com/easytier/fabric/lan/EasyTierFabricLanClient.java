package com.easytier.fabric.lan;

import com.easytier.fabric.lan.platform.PlatformDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;

/**
 * 客户端侧初始化
 */
public class EasyTierFabricLanClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 确保平台探测已执行
        PlatformDetector.detect();

        // 客户端侧使用游戏运行目录下的 config/easytier-go
        Path gameDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("easytier-go");
        EasyTierService service = EasyTierService.getInstance(gameDir);
        service.getLogManager().info("客户端初始化完成");

        // 客户端退出时停止进程
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            EasyTierService.getInstance().stop();
        });
    }
}
