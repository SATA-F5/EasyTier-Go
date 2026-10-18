package com.easytier.fabric.lan.client;

import com.easytier.fabric.lan.EasyTierService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

/**
 * EasyTier 连接状态面板
 * 制表型布局显示当前连接状态，每5秒自动刷新
 */
public class EasyTierStatusScreen extends Screen {

    private final Screen parent;
    private final EasyTierService service;
    private int tickCounter = 0;

    private TextWidget statusWidget;
    private TextWidget networkWidget;
    private TextWidget versionWidget;
    private TextWidget configWidget;
    private TextWidget ipWidget;
    private TextWidget relayWidget;
    private TextWidget instanceWidget;
    private TextWidget pidWidget;
    private TextWidget forwardWidget;
    private TextWidget peerWidget;

    public EasyTierStatusScreen(Screen parent, Path configDir) {
        super(Text.literal("EasyTier 连接状态"));
        this.parent = parent;
        this.service = EasyTierService.getInstance(configDir);
    }

    @Override
    protected void init() {
        // 清空可能残留的子元素
        this.clearChildren();

        int margin = 16;
        int contentW = this.width - margin * 2;
        int labelW = Math.min(100, contentW / 4);
        int valueW = contentW - labelW - 8;
        int leftX = margin;
        int valueX = margin + labelW + 8;

        // 标题居中
        this.addDrawableChild(new TextWidget(this.width / 2 - 80, 12, 160, 20,
                Text.literal("EasyTier 连接状态"), this.textRenderer));

        // 计算可用高度，确保按钮在屏幕内
        int topY = 36;
        int bottomReserved = 36; // 按钮高度 + 边距
        int availableH = this.height - topY - bottomReserved;
        int rowH = Math.max(16, Math.min(22, availableH / 11)); // 10行 + 1间距

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

        // 按钮行，固定在底部
        int btnY = this.height - 28;
        int btnW = Math.min(120, (contentW - 8) / 2);

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("立即刷新"),
                        btn -> refreshStatus())
                .dimensions(this.width / 2 - btnW - 4, btnY, btnW, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("返回配置"),
                        btn -> {
                            if (this.client != null && parent != null) {
                                this.client.setScreen(parent);
                            }
                        })
                .dimensions(this.width / 2 + 4, btnY, btnW, 20)
                .build());

        refreshStatus();
    }

    private int addStatusRow(String label, int labelX, int y, int valueX, int valueW, int labelW,
                              java.util.function.Consumer<TextWidget> setter, int rowH) {
        int textY = y + (rowH - 16) / 2;
        this.addDrawableChild(new TextWidget(labelX, textY, labelW, 16,
                Text.literal(label), this.textRenderer));
        TextWidget value = new TextWidget(valueX, textY, valueW, 16,
                Text.literal("--"), this.textRenderer);
        this.addDrawableChild(value);
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

    private void setText(TextWidget widget, String text) {
        if (widget != null) {
            widget.setMessage(Text.literal(text != null ? text : "--"));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 不透明背景，防止上一屏幕元素透过来
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        super.render(context, mouseX, mouseY, delta);
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
    public void close() {
        if (this.client != null && parent != null) {
            this.client.setScreen(parent);
        } else {
            super.close();
        }
    }
}
