package com.easytier.forge.lan.mixin;

import com.easytier.forge.lan.client.EasyTierConfigScreen;
import com.easytier.forge.lan.platform.PlatformDetector;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在"对局域网开放"界面中注入 EasyTier 联机配置按钮
 */
@Mixin(ShareToLanScreen.class)
public abstract class OpenToLanScreenMixin extends Screen {

    protected OpenToLanScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        int centerX = this.width / 2;
        int buttonY = this.height - 28;

        int btnW = 140;
        int midW = 60;
        int gap = 5;
        int totalW = btnW * 2 + midW + gap * 2;
        int startX = centerX - totalW / 2;

        int createX = startX;
        int easyTierX = startX + btnW + gap;
        int cancelX = startX + btnW + gap + midW + gap;

        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                String msg = widget.getMessage().getString();
                if (msg.contains("创建局域网世界") || msg.contains("Open to LAN") || msg.contains("对外开放")) {
                    widget.setX(createX);
                    widget.setWidth(btnW);
                } else if (msg.equals("取消") || msg.equals("Cancel")) {
                    widget.setX(cancelX);
                    widget.setWidth(btnW);
                }
            }
        }

        Button easyTierButton = Button.builder(
                        Component.literal("EasyTier"),
                        btn -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .bounds(easyTierX, buttonY, midW, 20)
                .build();

        this.addRenderableWidget(easyTierButton);
    }
}
