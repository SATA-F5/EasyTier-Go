package com.easytier.fabric.lan.mixin;

import com.easytier.fabric.lan.EasyTierService;
import com.easytier.fabric.lan.client.AutoConnectConfig;
import com.easytier.fabric.lan.client.EasyTierConfigScreen;
import com.easytier.fabric.lan.platform.PlatformDetector;
import net.minecraft.client.gui.screen.DirectConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * 在"直接连接"界面中注入 EasyTier 联机配置按钮和自动连接开关
 * EasyTier 按钮紧贴地址输入框下方
 */
@Mixin(DirectConnectScreen.class)
public abstract class DirectConnectScreenMixin extends Screen {

    private CyclingButtonWidget<Boolean> autoConnectToggle;
    private AutoConnectConfig autoConnectConfig;
    private TextFieldWidget addressField;

    protected DirectConnectScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEasyTierButton(CallbackInfo ci) {
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        int cx = this.width / 2;

        // 初始化自动连接配置
        Path configDir = this.client.runDirectory.toPath().resolve("config").resolve("easytier-go");
        autoConnectConfig = new AutoConnectConfig(configDir);

        // 找到地址输入框作为锚点
        for (var child : this.children()) {
            if (child instanceof TextFieldWidget field) {
                addressField = field;
                break;
            }
        }

        // 计算 EasyTier 按钮位置：紧贴地址输入框下方
        int buttonY;
        if (addressField != null) {
            buttonY = addressField.getY() + addressField.getHeight() + 8;
        } else {
            buttonY = this.height / 4 + 72;
        }

        // 将底部按钮（连接/取消）下移，为 EasyTier 行腾出空间
        int shift = 44;
        for (var child : this.children()) {
            if (child instanceof ClickableWidget widget && widget.getY() > buttonY) {
                widget.setY(widget.getY() + shift);
            }
        }

        // EasyTier 配置按钮（左半）
        ButtonWidget easyTierButton = ButtonWidget.builder(
                        Text.literal("EasyTier 联机配置"),
                        btn -> {
                            if (this.client != null) {
                                this.client.setScreen(new EasyTierConfigScreen(this));
                            }
                        })
                .dimensions(cx - 100, buttonY, 95, 20)
                .build();
        this.addDrawableChild(easyTierButton);

        // 自动连接开关（右半，与 EasyTier 按钮同行）
        String currentAddr = addressField != null ? addressField.getText().trim() : "";
        boolean autoEnabled = autoConnectConfig.isEnabled(currentAddr);
        autoConnectToggle = CyclingButtonWidget.<Boolean>builder(val ->
                        Text.literal(val ? "开" : "关"))
                .values(List.of(true, false))
                .initially(autoEnabled)
                .build(cx + 5, buttonY, 95, 20,
                        Text.literal("自动连接"),
                        (btn, val) -> {
                            if (addressField != null) {
                                String addr = addressField.getText().trim();
                                if (!addr.isEmpty()) {
                                    AutoConnectConfig.AutoConnectEntry entry = autoConnectConfig.get(addr);
                                    entry.enabled = val;
                                    autoConnectConfig.put(addr, entry);
                                }
                            }
                        });
        this.addDrawableChild(autoConnectToggle);

        // 提示文字（EasyTier 行下方）
        int hintY = buttonY + 24;
        this.addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(
                cx - 100, hintY, 200, 10,
                Text.literal("开启后连接服务器时自动启动 EasyTier 并转发端口"),
                this.textRenderer));
    }
}
