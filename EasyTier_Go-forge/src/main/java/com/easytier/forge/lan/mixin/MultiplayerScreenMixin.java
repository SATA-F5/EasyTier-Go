package com.easytier.forge.lan.mixin;

import com.easytier.forge.lan.EasyTierService;
import com.easytier.forge.lan.client.EasyTierConfigScreen;
import com.easytier.forge.lan.platform.PlatformDetector;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * 多人游戏主界面注入 EasyTier 状态按钮
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    private Button easyTierButton;
    private EasyTierService service;
    private int tickCounter = 0;

    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        Path configDir = this.minecraft.gameDirectory.toPath().resolve("config").resolve("easytier_go");
        service = EasyTierService.getInstance(configDir);

        int btnW = 90;
        int btnH = 18;
        int btnX = this.width - btnW - 8;
        int btnY = 6;

        easyTierButton = Button.builder(
                        Component.literal(getButtonLabel()),
                        btn -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .bounds(btnX, btnY, btnW, btnH)
                .build();
        this.addRenderableWidget(easyTierButton);
    }

    private String getButtonLabel() {
        if (service == null) return "EasyTier";
        boolean running = service.isRunning();
        boolean pending = service.isStartPending();
        if (pending) return "EasyTier: 启动中";
        return running ? "EasyTier: 运行中" : "EasyTier: 已停止";
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (tickCounter >= 100 && easyTierButton != null) {
            tickCounter = 0;
            easyTierButton.setMessage(Component.literal(getButtonLabel()));
        }
    }
}
