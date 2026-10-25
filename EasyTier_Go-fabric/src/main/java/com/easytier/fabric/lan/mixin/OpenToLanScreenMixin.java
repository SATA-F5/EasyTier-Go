package com.easytier.fabric.lan.mixin;

import com.easytier.fabric.lan.client.EasyTierConfigScreen;
import com.easytier.fabric.lan.platform.PlatformDetector;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在"对局域网开放"界面中注入 EasyTier 联机配置按钮
 * 位置：创建局域网世界 和 取消 按钮中间
 */
@Mixin(OpenToLanScreen.class)
public abstract class OpenToLanScreenMixin extends Screen {

    protected OpenToLanScreenMixin(Text title) {
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

        // 原生布局：创建局域网世界 [centerX-155, w150]，取消 [centerX+5, w150]
        // 重新排布为三按钮：创建局域网世界 | EasyTier | 取消，窄屏时自动收缩
        int gap = 5;
        int totalW = Math.min(350, Math.max(240, this.width - 20));
        int midW = Math.min(60, Math.max(40, totalW / 5));
        int btnW = Math.max(40, (totalW - midW - gap * 2) / 2);
        totalW = btnW * 2 + midW + gap * 2;
        int startX = centerX - totalW / 2;

        int createX = startX;
        int easyTierX = startX + btnW + gap;
        int cancelX = startX + btnW + gap + midW + gap;

        // 遍历已有按钮，调整位置
        for (var child : this.children()) {
            if (child instanceof ClickableWidget widget) {
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

        // EasyTier 按钮放在中间
        ButtonWidget easyTierButton = ButtonWidget.builder(
                        Text.literal("EasyTier"),
                        btn -> {
                            if (this.client != null) {
                                this.client.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .dimensions(easyTierX, buttonY, midW, 20)
                .build();

        this.addDrawableChild(easyTierButton);
    }
}
