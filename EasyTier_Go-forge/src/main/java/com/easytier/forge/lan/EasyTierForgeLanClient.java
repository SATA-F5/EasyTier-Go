package com.easytier.forge.lan;

import com.easytier.forge.lan.client.ClientScreenEventHandler;
import com.easytier.forge.lan.platform.PlatformDetector;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.file.Path;

/**
 * 客户端侧初始化
 */
@Mod.EventBusSubscriber(modid = EasyTierForgeLan.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EasyTierForgeLanClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PlatformDetector.detect();

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("easytier_go");
        EasyTierService service = EasyTierService.getInstance(gameDir);
        service.getLogManager().info("客户端初始化完成");

        MinecraftForge.EVENT_BUS.register(new ClientScreenEventHandler());
    }
}
