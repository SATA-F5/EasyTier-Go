package com.easytier.fabric.lan.client;

import com.easytier.fabric.lan.EasyTierService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * EasyTier 连接状态面板
 * 上半部分显示基本状态（两列布局），下半部分显示节点列表
 * 每5秒自动刷新
 */
public class EasyTierStatusScreen extends Screen {

    private final Screen parent;
    private final EasyTierService service;
    private int tickCounter = 0;

    private TextWidget statusWidget;
    private TextWidget networkWidget;
    private TextWidget versionWidget;
    private TextWidget ipWidget;
    private TextWidget relayWidget;
    private TextWidget pidWidget;
    private TextWidget peerCountWidget;

    private final List<String> peerLines = new ArrayList<>();
    private int tableStartY = 0;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private int viewHeight = 0;

    private static final int MARGIN = 12;
    private static final int LINE_H = 11;
    private static final int ROW_H = 14;
    private static final int SCROLLBAR_W = 4;
    private static final int TITLE_AREA_H = 14;

    public EasyTierStatusScreen(Screen parent, Path configDir) {
        super(Text.literal("EasyTier 连接状态"));
        this.parent = parent;
        this.service = EasyTierService.getInstance(configDir);
    }

    @Override
    protected void init() {
        this.clearChildren();

        int contentW = this.width - MARGIN * 2;
        int colW = contentW / 2;
        int labelW = 62;
        int valueW = colW - labelW;

        // 标题
        this.addDrawableChild(new TextWidget(this.width / 2 - 100, 8, 200, 16,
                Text.literal("EasyTier 连接状态"), this.textRenderer));

        // 基本状态（两列布局）
        int topY = 28;
        int y = topY;

        // 第一行：运行状态 | 网络名称
        addBasicRow("运行状态:", MARGIN, y, MARGIN + labelW, valueW, w -> statusWidget = w);
        addBasicRow("网络名称:", MARGIN + colW, y, MARGIN + colW + labelW, valueW, w -> networkWidget = w);
        y += ROW_H;

        // 第二行：虚拟IP | 版本
        addBasicRow("虚拟 IP:", MARGIN, y, MARGIN + labelW, valueW, w -> ipWidget = w);
        addBasicRow("版本:", MARGIN + colW, y, MARGIN + colW + labelW, valueW, w -> versionWidget = w);
        y += ROW_H;

        // 第三行：中继服务器 | 进程PID
        addBasicRow("中继服务器:", MARGIN, y, MARGIN + labelW, valueW, w -> relayWidget = w);
        addBasicRow("进程 PID:", MARGIN + colW, y, MARGIN + colW + labelW, valueW, w -> pidWidget = w);
        y += ROW_H;

        // 第四行：节点数量
        addBasicRow("节点数量:", MARGIN, y, MARGIN + labelW, valueW, w -> peerCountWidget = w);
        y += ROW_H + 6;

        tableStartY = y;

        // 底部按钮
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

    private void addBasicRow(String label, int labelX, int y, int valueX, int valueW,
                             java.util.function.Consumer<TextWidget> setter) {
        this.addDrawableChild(new TextWidget(labelX, y, 60, 12,
                Text.literal(label), this.textRenderer));
        TextWidget value = new TextWidget(valueX, y, valueW, 12,
                Text.literal("--"), this.textRenderer);
        this.addDrawableChild(value);
        setter.accept(value);
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
        setText(ipWidget, safeStr(service.getVirtualIp()));
        setText(relayWidget, safeStr(service.getCurrentRelayServer()));

        long pid = service.getProcessPid();
        setText(pidWidget, pid > 0 ? String.valueOf(pid) : "--");

        int peerCount = service.getPeerCount();
        setText(peerCountWidget, peerCount >= 0 ? String.valueOf(peerCount) : "--");

        refreshPeerList();

        if (running) {
            service.refreshPeerCountAsync();
        }
    }

    /**
     * 获取节点列表，解析原生表格数据，生成原生 UI 文本行
     */
    private void refreshPeerList() {
        peerLines.clear();

        if (!service.isRunning()) {
            peerLines.add("EasyTier 未运行，无节点数据");
            return;
        }

        String output = service.getCliRunner().peer();
        if (output == null || output.isEmpty()) {
            peerLines.add("无法获取节点列表");
            return;
        }

        if (output.startsWith("错误") || output.startsWith("Error")) {
            peerLines.add("获取节点列表失败: " + output);
            return;
        }

        // 解析原生表格
        String[] rawLines = output.split("\n");
        List<String[]> rows = new ArrayList<>();
        for (String line : rawLines) {
            if (line.trim().startsWith("|")) {
                String[] parts = line.split("\\|", -1);
                List<String> cells = new ArrayList<>();
                for (int i = 1; i < parts.length - 1; i++) {
                    cells.add(parts[i].trim());
                }
                if (cells.isEmpty()) continue;
                boolean isSep = cells.stream().allMatch(c -> c.matches("^-+$") || c.isEmpty());
                if (isSep) continue;
                // 跳过表头行（ipv4/hostname...）
                boolean isHeader = cells.stream().anyMatch(c ->
                        c.equalsIgnoreCase("ipv4") || c.equalsIgnoreCase("hostname"));
                if (isHeader) continue;
                rows.add(cells.toArray(new String[0]));
            }
        }

        if (rows.isEmpty()) {
            peerLines.add("暂无连接节点");
            return;
        }

        // 每个节点显示为两行文本，字段固定顺序
        for (int idx = 0; idx < rows.size(); idx++) {
            String[] r = rows.get(idx);
            // 列顺序：ipv4, hostname, cost, lat, loss, rx, tx, tunnel, NAT, version
            String ipv4 = getCell(r, 0);
            String hostname = getCell(r, 1);
            String cost = getCell(r, 2);
            String lat = getCell(r, 3);
            String loss = getCell(r, 4);
            String rx = getCell(r, 5);
            String tx = getCell(r, 6);
            String tunnel = getCell(r, 7);
            String nat = getCell(r, 8);
            String version = getCell(r, 9);

            peerLines.add("[" + (idx + 1) + "] " + hostname + "  (" + cost + ")");
            peerLines.add("    ip: " + ipv4 + "  |  ver: " + version);
            peerLines.add("    lat: " + lat + " ms  |  loss: " + loss + "  |  tun: " + tunnel);
            peerLines.add("    rx: " + rx + "  |  tx: " + tx + "  |  NAT: " + nat);
            peerLines.add("");
        }
    }

    private String getCell(String[] row, int index) {
        if (index < row.length) {
            String val = row[index].trim();
            return val.isEmpty() ? "-" : val;
        }
        return "-";
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
        // 不透明背景
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        super.render(context, mouseX, mouseY, delta);

        // 区域定义
        int viewTop = tableStartY + TITLE_AREA_H;
        int viewBottom = this.height - 36;
        viewHeight = viewBottom - viewTop;

        // 标题
        context.drawTextWithShadow(this.textRenderer, "连接节点列表:", MARGIN, tableStartY, 0x55FFFF);

        // 计算内容总高度
        int totalH = 0;
        for (String line : peerLines) {
            totalH += line.isEmpty() ? LINE_H / 2 : LINE_H;
        }
        contentHeight = totalH;

        // 限制滚动范围
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        // 滚动区域背景（略微加深）
        int contentRight = this.width - MARGIN - (maxScroll > 0 ? SCROLLBAR_W + 4 : 0);
        context.fill(MARGIN - 2, viewTop - 2, contentRight + 2, viewBottom, 0xFF101010);

        // 启用 scissor 裁剪
        context.enableScissor(MARGIN - 2, viewTop, contentRight + 2, viewBottom);

        int y = viewTop - (int) scrollOffset;
        for (String line : peerLines) {
            if (line.isEmpty()) {
                y += LINE_H / 2;
                continue;
            }
            int color;
            if (line.startsWith("[")) {
                color = 0xFFD700;
            } else if (line.startsWith("    ip:")) {
                color = 0x90EE90;
            } else {
                color = 0xBBBBBB;
            }
            context.drawTextWithShadow(this.textRenderer, line, MARGIN, y, color);
            y += LINE_H;
        }

        context.disableScissor();

        // 绘制滚动条
        if (maxScroll > 0) {
            int barX = this.width - MARGIN - SCROLLBAR_W;
            // 轨道
            context.fill(barX, viewTop, barX + SCROLLBAR_W, viewBottom, 0xFF333333);
            // 滑块
            int thumbH = Math.max(20, (int) ((double) viewHeight / contentHeight * viewHeight));
            int thumbY = viewTop + (int) ((double) scrollOffset / maxScroll * (viewHeight - thumbH));
            context.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int viewTop = tableStartY + TITLE_AREA_H;
        int viewBottom = this.height - 36;
        if (mouseY >= viewTop && mouseY <= viewBottom) {
            scrollOffset -= amount * LINE_H * 3;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            if (scrollOffset < 0) scrollOffset = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
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
