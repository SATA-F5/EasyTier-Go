package com.easytier.fabric.lan.mixin;

import com.easytier.fabric.lan.EasyTierService;
import com.easytier.fabric.lan.client.EasyTierConfigScreen;
import com.easytier.fabric.lan.platform.PlatformDetector;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * 多人游戏主界面注入 EasyTier 状态按钮
 * 仅显示运行状态，点击打开配置界面
 */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    private ButtonWidget easyTierButton;
    private EasyTierService service;
    private int tickCounter = 0;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        Path configDir = this.client.runDirectory.toPath().resolve("config").resolve("easytier-go");
        service = EasyTierService.getInstance(configDir);

        // 在屏幕右上角添加一个小的状态按钮
        int btnW = 90;
        int btnH = 18;
        int btnX = this.width - btnW - 8;
        int btnY = 6;

        easyTierButton = ButtonWidget.builder(
                        Text.literal(getButtonLabel()),
                        btn -> {
                            if (this.client != null) {
                                this.client.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .dimensions(btnX, btnY, btnW, btnH)
                .build();
        this.addDrawableChild(easyTierButton);
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
            easyTierButton.setMessage(Text.literal(getButtonLabel()));
        }
    }
}
