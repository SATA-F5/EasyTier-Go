package com.easytier.fabric.lan.mixin;

import com.easytier.fabric.lan.client.EasyTierConfigScreen;
import com.easytier.fabric.lan.platform.PlatformDetector;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在暂停菜单（ESC菜单）中注入 EasyTier 联机按钮
 * 位置：对局域网开放/选项行下方，保存并退出上方
 * 不依赖"对局域网开放"界面，开启局域网后仍可访问
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        int centerX = this.width / 2;

        // 找到"保存并退回到标题屏幕"按钮，记录其Y坐标并下移24像素
        int quitButtonY = -1;
        int shift = 24;
        for (var child : this.children()) {
            if (child instanceof ClickableWidget widget) {
                String msg = widget.getMessage().getString();
                if (msg.contains("保存并退回") || msg.contains("退回到标题")
                        || msg.contains("Save and Quit") || msg.contains("退出游戏")) {
                    quitButtonY = widget.getY();
                    widget.setY(quitButtonY + shift);
                }
            }
        }

        // EasyTier 按钮放在原"保存并退出"位置（居中全宽按钮）
        // 如果没找到退出按钮，放在选项行下方
        int buttonY;
        if (quitButtonY > 0) {
            buttonY = quitButtonY;
        } else {
            buttonY = this.height / 4 + 104;
        }

        ButtonWidget easyTierButton = ButtonWidget.builder(
                        Text.literal("EasyTier 联机配置"),
                        btn -> {
                            if (this.client != null) {
                                this.client.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .dimensions(centerX - 102, buttonY, 204, 20)
                .build();

        this.addDrawableChild(easyTierButton);
    }
}
