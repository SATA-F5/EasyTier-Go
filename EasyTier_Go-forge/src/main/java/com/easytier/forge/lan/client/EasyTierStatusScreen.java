package com.easytier.forge.lan.client;

import com.easytier.forge.lan.EasyTierService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * EasyTier 连接状态面板
 */
public class EasyTierStatusScreen extends Screen {

    private final Screen parent;
    private final EasyTierService service;
    private int tickCounter = 0;

    private StringWidget statusWidget;
    private StringWidget networkWidget;
    private StringWidget versionWidget;
    private StringWidget configWidget;
    private StringWidget ipWidget;
    private StringWidget relayWidget;
    private StringWidget instanceWidget;
    private StringWidget pidWidget;
    private StringWidget forwardWidget;
    private StringWidget peerWidget;

    public EasyTierStatusScreen(Screen parent, Path configDir) {
        super(Component.literal("EasyTier 连接状态"));
        this.parent = parent;
        this.service = EasyTierService.getInstance(configDir);
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int margin = 16;
        int contentW = this.width - margin * 2;
        int labelW = Math.min(100, contentW / 4);
        int valueW = contentW - labelW - 8;
        int leftX = margin;
        int valueX = margin + labelW + 8;

        this.addRenderableWidget(new StringWidget(this.width / 2 - 80, 12, 160, 20,
                Component.literal("EasyTier 连接状态"), this.font));

        int topY = 36;
        int bottomReserved = 36;
        int availableH = this.height - topY - bottomReserved;
        int rowH = Math.max(16, Math.min(22, availableH / 11));

        int y = topY;
        y = addStatusRow("运行状态:", leftX, y, valueX, valueW, labelW, w -> statusWidget = w, rowH);
        y = addStatusRow("网络名称:", leftX, y, valueX, valueW, labelW, w -> networkWidget = w, rowH);
        y = addStatusRow("版本:", leftX, y, valueX, valueW, labelW, w -> versionWidget = w, rowH);
        y = addStatusRow("配置文件:", leftX, y, valueX, valueW, labelW, w -> configWidget = w, rowH);
        y = addStatusRow("虚拟 IP:", leftX, y, valueX, valueW, labelW, w -> ipWidget = w, rowH);
        y = addStatusRow("中继服务器:", leftX, y, valueX, valueW, labelW, w -> relayWidget = w, rowH);
        y = addStatusRow("实例 ID:", leftX, y, valueX, valueW, labelW, w -> instanceWidget = w, rowH);
        y = addStatusRow("进程 PID:", leftX, y, valueX, valueW, labelW, w -> pidWidget = w, rowH);
        y = addStatusRow("端口转发:", leftX, y, valueX, valueW, labelW, w -> forwardWidget = w, rowH);
        y = addStatusRow("节点数量:", leftX, y, valueX, valueW, labelW, w -> peerWidget = w, rowH);

        int btnY = this.height - 28;
        int btnW = Math.min(120, (contentW - 8) / 2);

        this.addRenderableWidget(Button.builder(
                        Component.literal("立即刷新"),
                        btn -> refreshStatus())
                .bounds(this.width / 2 - btnW - 4, btnY, btnW, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.literal("返回配置"),
                        btn -> {
                            if (this.minecraft != null && parent != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(this.width / 2 + 4, btnY, btnW, 20)
                .build());

        refreshStatus();
    }

    private int addStatusRow(String label, int labelX, int y, int valueX, int valueW, int labelW,
                              java.util.function.Consumer<StringWidget> setter, int rowH) {
        int textY = y + (rowH - 16) / 2;
        this.addRenderableWidget(new StringWidget(labelX, textY, labelW, 16,
                Component.literal(label), this.font));
        StringWidget value = new StringWidget(valueX, textY, valueW, 16,
                Component.literal("--"), this.font);
        this.addRenderableWidget(value);
        setter.accept(value);
        return y + rowH;
    }

    private void refreshStatus() {
        boolean running = service.isRunning();
        boolean pending = service.isStartPending();

        String statusText;
        if (pending) {
            statusText = "启动中...";
        } else if (running) {
            statusText = "运行中";
        } else {
            statusText = "已停止";
        }
        setText(statusWidget, statusText);

        setText(networkWidget, safeStr(service.getCurrentNetworkName()));
        setText(versionWidget, safeStr(service.getCurrentVersion()));
        setText(configWidget, safeStr(service.getCurrentConfigFile()));
        setText(ipWidget, safeStr(service.getVirtualIp()));
        setText(relayWidget, safeStr(service.getCurrentRelayServer()));
        setText(instanceWidget, safeStr(service.getCurrentInstanceId()));

        long pid = service.getProcessPid();
        setText(pidWidget, pid > 0 ? String.valueOf(pid) : "--");

        List<String> forwards = service.getPortForwards();
        if (forwards != null && !forwards.isEmpty()) {
            setText(forwardWidget, forwards.size() + " 条规则");
        } else {
            setText(forwardWidget, "无");
        }

        int peerCount = service.getPeerCount();
        setText(peerWidget, peerCount >= 0 ? String.valueOf(peerCount) : "--");

        if (running) {
            service.refreshPeerCountAsync();
        }
    }

    private String safeStr(String s) {
        return (s != null && !s.isEmpty()) ? s : "--";
    }

    private void setText(StringWidget widget, String text) {
        if (widget != null) {
            widget.setMessage(Component.literal(text != null ? text : "--"));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (tickCounter >= 100) {
            tickCounter = 0;
            refreshStatus();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
