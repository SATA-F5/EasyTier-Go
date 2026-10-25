package com.easytier.forge.lan.mixin;

import com.easytier.forge.lan.client.EasyTierConfigScreen;
import com.easytier.forge.lan.platform.PlatformDetector;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在暂停菜单（ESC菜单）中注入 EasyTier 联机按钮
 */
@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        int centerX = this.width / 2;

        int quitButtonY = -1;
        int shift = 24;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                String msg = widget.getMessage().getString();
                if (msg.contains("保存并退回") || msg.contains("退回到标题")
                        || msg.contains("Save and Quit") || msg.contains("退出游戏")) {
                    quitButtonY = widget.getY();
                    widget.setY(quitButtonY + shift);
                }
            }
        }

        int buttonY;
        if (quitButtonY > 0) {
            buttonY = quitButtonY;
        } else {
            buttonY = this.height / 4 + 104;
        }

        Button easyTierButton = Button.builder(
                        Component.literal("EasyTier 联机配置"),
                        btn -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .bounds(centerX - 102, buttonY, 204, 20)
                .build();

        this.addRenderableWidget(easyTierButton);
    }
}
