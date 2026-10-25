package com.easytier.forge.lan.client;

import com.easytier.forge.lan.EasyTierForgeLan;
import com.easytier.forge.lan.EasyTierService;
import com.easytier.forge.lan.platform.PlatformDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Path;
import java.util.List;

/**
 * 使用Forge ScreenEvent在各个界面注入EasyTier按钮
 * 替代Mixin方案，避免refmap问题
 */
@OnlyIn(Dist.CLIENT)
public class ClientScreenEventHandler {

    private Button multiplayerStatusButton;
    private EasyTierService multiplayerService;
    private int statusUpdateCounter = 0;

    /**
     * 多人游戏界面右上角状态按钮：定期刷新运行状态
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (multiplayerStatusButton == null || multiplayerService == null) {
            return;
        }
        if (!(Minecraft.getInstance().screen instanceof JoinMultiplayerScreen)) {
            multiplayerStatusButton = null;
            multiplayerService = null;
            return;
        }
        statusUpdateCounter++;
        if (statusUpdateCounter >= 20) {
            statusUpdateCounter = 0;
            multiplayerStatusButton.setMessage(Component.literal(buildStatusLabel(multiplayerService)));
        }
    }

    private String buildStatusLabel(EasyTierService service) {
        if (service == null) {
            return "EasyTier";
        }
        if (service.isStartPending()) {
            return "EasyTier: 启动中";
        }
        return service.isRunning() ? "EasyTier: 运行中" : "EasyTier: 已停止";
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        PlatformDetector.detect();
        if (!PlatformDetector.isSupported()) {
            return;
        }

        if (screen instanceof PauseScreen) {
            injectPauseScreen(screen, event);
        } else if (screen instanceof EditServerScreen) {
            injectEditServerScreen(screen, event);
        } else if (screen instanceof DirectJoinServerScreen) {
            injectDirectConnectScreen(screen, event);
        } else if (screen instanceof ShareToLanScreen) {
            injectOpenToLanScreen(screen, event);
        } else if (screen instanceof JoinMultiplayerScreen) {
            injectMultiplayerScreen(screen, event);
        }
    }

    private void injectPauseScreen(Screen screen, ScreenEvent.Init.Post event) {
        int centerX = screen.width / 2;

        // 找到最下面的按钮（保存并退回），将EasyTier按钮放在它上面
        int quitButtonY = -1;
        int modButtonY = -1;
        int shift = 24;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                String msg = widget.getMessage().getString();
                if (msg.contains("保存并退回") || msg.contains("退回到标题")
                        || msg.contains("Save and Quit") || msg.contains("退出游戏")) {
                    quitButtonY = widget.getY();
                    widget.setY(quitButtonY + shift);
                }
                if (msg.equals("Mod") || msg.equals("模组")) {
                    modButtonY = widget.getY();
                }
            }
        }

        int buttonY;
        if (quitButtonY > 0) {
            buttonY = quitButtonY;
        } else if (modButtonY > 0) {
            buttonY = modButtonY + 24;
        } else {
            buttonY = screen.height / 4 + 128;
        }

        Button easyTierButton = Button.builder(
                        Component.literal("EasyTier 联机配置"),
                        btn -> {
                            if (screen.getMinecraft() != null) {
                                screen.getMinecraft().setScreen(new EasyTierConfigScreen(screen));
                            }
                        })
                .bounds(centerX - 102, buttonY, 204, 20)
                .build();

        event.addListener(easyTierButton);
    }

    private void injectEditServerScreen(Screen screen, ScreenEvent.Init.Post event) {
        injectServerEditScreenCommon(screen, event);
    }

    private void injectDirectConnectScreen(Screen screen, ScreenEvent.Init.Post event) {
        injectServerEditScreenCommon(screen, event);
    }

    private void injectServerEditScreenCommon(Screen screen, ScreenEvent.Init.Post event) {
        int cx = screen.width / 2;

        Path configDir = EasyTierForgeLan.getConfigDir();
        AutoConnectConfig autoConnectConfig = new AutoConnectConfig(configDir);

        // 找到地址输入框：优先选择内容像地址的输入框，否则取最下面的输入框
        EditBox addressLikeField = null;
        EditBox lowestField = null;
        for (var child : screen.children()) {
            if (child instanceof EditBox field) {
                if (lowestField == null || field.getY() > lowestField.getY()) {
                    lowestField = field;
                }
                String text = field.getValue().trim();
                if (addressLikeField == null && (text.contains(":") || text.contains("."))) {
                    addressLikeField = field;
                }
            }
        }
        EditBox addressField = addressLikeField != null ? addressLikeField : lowestField;

        // 用排除法找到资源包按钮：不是完成、不是取消的全宽按钮
        AbstractWidget resourcePackButton = null;
        AbstractWidget doneButton = null;
        AbstractWidget cancelButton = null;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                String msg = widget.getMessage().getString();
                if (msg.equals("完成") || msg.equals("Done")) {
                    doneButton = widget;
                } else if (msg.equals("取消") || msg.equals("Cancel")) {
                    cancelButton = widget;
                } else if (widget.getWidth() > 150) {
                    if (resourcePackButton == null || widget.getY() > resourcePackButton.getY()) {
                        resourcePackButton = widget;
                    }
                }
            }
        }

        // EasyTier区域总高度：按钮20 + 提示文字10 = 30
        int easyTierAreaHeight = 30;
        int offset = easyTierAreaHeight + 4; // 下移完成/取消按钮的距离

        // 把完成按钮和取消按钮下移，为EasyTier区域腾出空间
        if (doneButton != null) {
            doneButton.setY(doneButton.getY() + offset);
        }
        if (cancelButton != null) {
            cancelButton.setY(cancelButton.getY() + offset);
        }

        // 计算EasyTier按钮的Y位置：资源包按钮下面4像素
        int buttonY;
        if (resourcePackButton != null) {
            buttonY = resourcePackButton.getY() + resourcePackButton.getHeight() + 4;
        } else if (addressField != null) {
            buttonY = addressField.getY() + addressField.getHeight() + 12;
        } else {
            buttonY = screen.height / 4 + 108;
        }

        Button easyTierButton = Button.builder(
                        Component.literal("EasyTier 联机配置"),
                        btn -> {
                            if (screen.getMinecraft() != null) {
                                screen.getMinecraft().setScreen(new EasyTierConfigScreen(screen));
                            }
                        })
                .bounds(cx - 100, buttonY, 95, 20)
                .build();
        event.addListener(easyTierButton);

        EditBox finalAddressField = addressField;
        String currentAddr = addressField != null ? addressField.getValue().trim() : "";
        boolean autoEnabled = autoConnectConfig.isEnabled(currentAddr);
        CycleButton<Boolean> autoConnectToggle = CycleButton.<Boolean>builder(val ->
                        Component.literal(val ? "开" : "关"))
                .withValues(List.of(true, false))
                .withInitialValue(autoEnabled)
                .create(cx + 5, buttonY, 95, 20,
                        Component.literal("自动连接"),
                        (btn, val) -> {
                            if (finalAddressField != null) {
                                String addr = finalAddressField.getValue().trim();
                                if (!addr.isEmpty()) {
                                    AutoConnectConfig.AutoConnectEntry entry = autoConnectConfig.get(addr);
                                    entry.enabled = val;
                                    autoConnectConfig.put(addr, entry);
                                }
                            }
                        });
        event.addListener(autoConnectToggle);

        // 提示文字紧接按钮下方
        int hintY = buttonY + 20;
        StringWidget hint = new StringWidget(
                cx - 100, hintY, 200, 10,
                Component.literal("开启后连接服务器时自动启动 EasyTier 并转发端口"),
                screen.getMinecraft().font);
        event.addListener(hint);
    }

    private void injectOpenToLanScreen(Screen screen, ScreenEvent.Init.Post event) {
        int centerX = screen.width / 2;
        int buttonY = screen.height - 28;

        int btnW = 140;
        int midW = 60;
        int gap = 5;
        int totalW = btnW * 2 + midW + gap * 2;
        int startX = centerX - totalW / 2;

        int createX = startX;
        int easyTierX = startX + btnW + gap;
        int cancelX = startX + btnW + gap + midW + gap;

        for (var child : screen.children()) {
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
                            if (screen.getMinecraft() != null) {
                                screen.getMinecraft().setScreen(new EasyTierConfigScreen(screen));
                            }
                        })
                .bounds(easyTierX, buttonY, midW, 20)
                .build();

        event.addListener(easyTierButton);
    }

    private void injectMultiplayerScreen(Screen screen, ScreenEvent.Init.Post event) {
        int btnW = 90;
        int btnH = 18;
        int btnX = screen.width - btnW - 8;
        int btnY = 6;

        Path configDir = screen.getMinecraft().gameDirectory.toPath().resolve("config").resolve("easytier_go");
        EasyTierService service = EasyTierService.getInstance(configDir);

        Button easyTierButton = Button.builder(
                        Component.literal(buildStatusLabel(service)),
                        btn -> {
                            if (screen.getMinecraft() != null) {
                                screen.getMinecraft().setScreen(new EasyTierConfigScreen(screen));
                            }
                        })
                .bounds(btnX, btnY, btnW, btnH)
                .build();
        event.addListener(easyTierButton);

        this.multiplayerStatusButton = easyTierButton;
        this.multiplayerService = service;
        this.statusUpdateCounter = 0;
    }
}
