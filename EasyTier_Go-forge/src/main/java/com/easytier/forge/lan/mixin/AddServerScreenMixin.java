package com.easytier.forge.lan.mixin;

import com.easytier.forge.lan.EasyTierForgeLan;
import com.easytier.forge.lan.client.AutoConnectConfig;
import com.easytier.forge.lan.client.EasyTierConfigScreen;
import com.easytier.forge.lan.platform.PlatformDetector;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * 在"添加服务器/编辑服务器"界面中注入 EasyTier 联机配置按钮和自动连接开关
 */
@Mixin(EditServerScreen.class)
public abstract class AddServerScreenMixin extends Screen {

    private CycleButton<Boolean> autoConnectToggle;
    private AutoConnectConfig autoConnectConfig;
    private EditBox addressField;

    protected AddServerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        int cx = this.width / 2;

        Path configDir = EasyTierForgeLan.getConfigDir();
        autoConnectConfig = new AutoConnectConfig(configDir);

        for (var child : this.children()) {
            if (child instanceof EditBox field) {
                addressField = field;
                break;
            }
        }

        AbstractWidget resourcePackButton = null;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                String msg = widget.getMessage().getString();
                if (msg.contains("服务器资源包") || msg.contains("Server Resource Packs")
                        || msg.contains("资源包") || msg.contains("Resource Pack")) {
                    resourcePackButton = widget;
                    break;
                }
            }
        }

        int buttonY;
        if (resourcePackButton != null) {
            buttonY = resourcePackButton.getY() + resourcePackButton.getHeight() + 4;
        } else {
            buttonY = this.height / 4 + 108;
        }

        int shift = 44;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.getY() > buttonY) {
                widget.setY(widget.getY() + shift);
            }
        }

        Button easyTierButton = Button.builder(
                        Component.literal("EasyTier 联机配置"),
                        btn -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .bounds(cx - 100, buttonY, 95, 20)
                .build();
        this.addRenderableWidget(easyTierButton);

        String currentAddr = addressField != null ? addressField.getValue().trim() : "";
        boolean autoEnabled = autoConnectConfig.isEnabled(currentAddr);
        autoConnectToggle = CycleButton.<Boolean>builder(val ->
                        Component.literal(val ? "开" : "关"))
                .withValues(List.of(true, false))
                .withInitialValue(autoEnabled)
                .create(cx + 5, buttonY, 95, 20,
                        Component.literal("自动连接"),
                        (btn, val) -> {
                            if (addressField != null) {
                                String addr = addressField.getValue().trim();
                                if (!addr.isEmpty()) {
                                    AutoConnectConfig.AutoConnectEntry entry = autoConnectConfig.get(addr);
                                    entry.enabled = val;
                                    autoConnectConfig.put(addr, entry);
                                }
                            }
                        });
        this.addRenderableWidget(autoConnectToggle);

        int hintY = buttonY + 24;
        this.addRenderableWidget(new StringWidget(
                cx - 100, hintY, 200, 10,
                Component.literal("开启后连接服务器时自动启动 EasyTier 并转发端口"),
                this.font));
    }
}
