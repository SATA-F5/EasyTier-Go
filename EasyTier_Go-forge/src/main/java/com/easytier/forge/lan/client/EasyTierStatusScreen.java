package com.easytier.forge.lan.client;

import com.easytier.forge.lan.EasyTierService;
import com.easytier.forge.lan.process.EasyTierCliRunner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EasyTierStatusScreen extends Screen {

    private final Screen parent;
    private final EasyTierService service;
    private int tickCounter = 0;

    private StringWidget statusWidget;
    private StringWidget networkWidget;
    private StringWidget configFileWidget;
    private StringWidget ipWidget;
    private StringWidget relayWidget;
    private StringWidget pidWidget;
    private StringWidget peerCountWidget;

    private final List<String> peerLines = new ArrayList<>();
    private volatile boolean peerLoading = false;
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
        super(Component.literal("EasyTier 连接状态"));
        this.parent = parent;
        this.service = EasyTierService.getInstance(configDir);
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int contentW = this.width - MARGIN * 2;
        int colW = contentW / 2;
        int labelW = 62;
        int valueW = colW - labelW;

        this.addRenderableWidget(new StringWidget(this.width / 2 - 100, 8, 200, 16,
                Component.literal("EasyTier 连接状态"), this.font));

        int topY = 28;
        int y = topY;

        addBasicRow("运行状态:", MARGIN, y, MARGIN + labelW, valueW, w -> statusWidget = w);
        addBasicRow("网络名称:", MARGIN + colW, y, MARGIN + colW + labelW, valueW, w -> networkWidget = w);
        y += ROW_H;

        addBasicRow("虚拟 IP:", MARGIN, y, MARGIN + labelW, valueW, w -> ipWidget = w);
        addBasicRow("配置文件:", MARGIN + colW, y, MARGIN + colW + labelW, valueW, w -> configFileWidget = w);
        y += ROW_H;

        addBasicRow("中继服务器:", MARGIN, y, MARGIN + labelW, valueW, w -> relayWidget = w);
        addBasicRow("进程 PID:", MARGIN + colW, y, MARGIN + colW + labelW, valueW, w -> pidWidget = w);
        y += ROW_H;

        addBasicRow("节点数量:", MARGIN, y, MARGIN + labelW, valueW, w -> peerCountWidget = w);
        y += ROW_H + 6;

        tableStartY = y;

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

    private void addBasicRow(String label, int labelX, int y, int valueX, int valueW,
                             java.util.function.Consumer<StringWidget> setter) {
        this.addRenderableWidget(new StringWidget(labelX, y, 60, 12,
                Component.literal(label), this.font));
        StringWidget value = new StringWidget(valueX, y, valueW, 12,
                Component.literal("--"), this.font);
        this.addRenderableWidget(value);
        setter.accept(value);
    }

    private void refreshStatus() {
        boolean running = service.isRunning();
        boolean pending = service.isStartPending();

        String statusText = pending ? "启动中..." : (running ? "运行中" : "已停止");
        setText(statusWidget, statusText);
        setText(networkWidget, safeStr(service.getCurrentNetworkName()));
        setText(configFileWidget, safeStr(service.getCurrentConfigFile()));
        setText(ipWidget, safeStr(service.getVirtualIp()));
        setText(relayWidget, safeStr(service.getCurrentRelayServer()));

        long pid = service.getProcessPid();
        setText(pidWidget, pid > 0 ? String.valueOf(pid) : "--");

        int peerCount = service.getPeerCount();
        setText(peerCountWidget, peerCount >= 0 ? String.valueOf(peerCount) : "--");

        refreshPeerListAsync();
    }

    /**
     * 后台获取节点列表，避免 CLI 调用阻塞渲染线程
     */
    private void refreshPeerListAsync() {
        if (peerLoading) {
            return;
        }
        if (!service.isRunning()) {
            peerLoading = false;
            peerLines.clear();
            peerLines.add("EasyTier 未运行，无节点数据");
            return;
        }

        peerLoading = true;
        if (peerLines.isEmpty()) {
            peerLines.add("正在获取节点列表...");
        }
        CompletableFuture.supplyAsync(() -> service.getCliRunner().peer())
                .thenAccept(output -> {
                    int count = EasyTierCliRunner.countPeerRows(output);
                    List<String> newLines = parsePeerOutput(output);
                    runOnClientThread(() -> {
                        peerLines.clear();
                        peerLines.addAll(newLines);
                        if (count >= 0) {
                            service.updatePeerCount(count);
                            setText(peerCountWidget, String.valueOf(count));
                        }
                        peerLoading = false;
                    });
                })
                .exceptionally(ex -> {
                    runOnClientThread(() -> {
                        peerLines.clear();
                        peerLines.add("获取节点列表失败: " + ex.getMessage());
                        peerLoading = false;
                    });
                    return null;
                });
    }

    private void runOnClientThread(Runnable action) {
        if (this.minecraft != null) {
            this.minecraft.execute(action);
        } else {
            action.run();
        }
    }

    /**
     * 解析 peer 表格输出。表头列顺序不固定时也能正确取值。
     */
    private List<String> parsePeerOutput(String output) {
        List<String> result = new ArrayList<>();
        if (output == null || output.isEmpty()) {
            result.add("无法获取节点列表");
            return result;
        }
        if (output.startsWith("错误") || output.startsWith("Error")) {
            result.add("获取节点列表失败: " + output);
            return result;
        }

        List<String> headers = null;
        List<String[]> rows = new ArrayList<>();
        for (String rawLine : output.split("\\R")) {
            String line = rawLine.replaceAll("\u001B\\[[;\\d]*m", "")
                    .replace('│', '|').replace('┃', '|').trim();
            List<String> cells = splitPeerRow(line);
            if (cells.isEmpty()) {
                continue;
            }
            boolean isSep = cells.stream().allMatch(c -> c.matches("^[-=:]+$") || c.isEmpty());
            if (isSep) {
                continue;
            }
            boolean isHeader = cells.stream().anyMatch(c ->
                    c.equalsIgnoreCase("ipv4") || c.equalsIgnoreCase("hostname"));
            if (isHeader) {
                headers = cells;
                continue;
            }
            if (headers == null) {
                continue;
            }
            rows.add(cells.toArray(new String[0]));
        }

        if (rows.isEmpty()) {
            result.add("暂无连接节点");
            return result;
        }

        int ipv4Idx = columnIndex(headers, "ipv4", 0);
        int hostnameIdx = columnIndex(headers, "hostname", 1);
        int costIdx = columnIndex(headers, "cost", 2);
        int latIdx = columnIndex(headers, "lat", 3);
        int lossIdx = columnIndex(headers, "loss", 4);
        int rxIdx = columnIndex(headers, "rx", 5);
        int txIdx = columnIndex(headers, "tx", 6);
        int tunnelIdx = columnIndex(headers, "tunnel", 7);
        int natIdx = columnIndex(headers, "nat", 8);
        int versionIdx = columnIndex(headers, "version", 9);

        for (int idx = 0; idx < rows.size(); idx++) {
            String[] r = rows.get(idx);
            String ipv4 = getCell(r, ipv4Idx);
            String hostname = getCell(r, hostnameIdx);
            String cost = getCell(r, costIdx);
            String lat = getCell(r, latIdx);
            String loss = getCell(r, lossIdx);
            String rx = getCell(r, rxIdx);
            String tx = getCell(r, txIdx);
            String tunnel = getCell(r, tunnelIdx);
            String nat = getCell(r, natIdx);
            String version = getCell(r, versionIdx);

            result.add("[" + (idx + 1) + "] " + hostname + "  (" + cost + ")");
            result.add("    ip: " + ipv4 + "  |  ver: " + version);
            result.add("    lat: " + lat + " ms  |  loss: " + loss + "  |  tun: " + tunnel);
            result.add("    rx: " + rx + "  |  tx: " + tx + "  |  NAT: " + nat);
            result.add("");
        }
        return result;
    }

    private List<String> splitPeerRow(String line) {
        List<String> cells = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return cells;
        }
        if (line.indexOf('|') >= 0) {
            String[] parts = line.split("\\|", -1);
            int start = 0;
            int end = parts.length;
            while (start < end && parts[start].trim().isEmpty()) {
                start++;
            }
            while (end > start && parts[end - 1].trim().isEmpty()) {
                end--;
            }
            for (int i = start; i < end; i++) {
                cells.add(parts[i].trim());
            }
        } else {
            if (line.matches("^[+\\-=─━┌┐└┘├┤┬┴┼\\s]+$")) {
                return cells;
            }
            for (String part : line.split("\\s+")) {
                if (!part.isEmpty()) {
                    cells.add(part);
                }
            }
        }
        return cells;
    }

    private int columnIndex(List<String> headers, String name, int fallback) {
        if (headers == null) {
            return fallback;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).toLowerCase().startsWith(name)) {
                return i;
            }
        }
        return fallback;
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

    private void setText(StringWidget widget, String text) {
        if (widget != null) {
            widget.setMessage(Component.literal(text != null ? text : "--"));
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        super.render(context, mouseX, mouseY, delta);

        int viewTop = tableStartY + TITLE_AREA_H;
        int viewBottom = this.height - 36;
        viewHeight = viewBottom - viewTop;

        context.drawString(this.font, "连接节点列表:", MARGIN, tableStartY, 0x55FFFF, false);

        int totalH = 0;
        for (String line : peerLines) {
            totalH += line.isEmpty() ? LINE_H / 2 : LINE_H;
        }
        contentHeight = totalH;

        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int contentRight = this.width - MARGIN - (maxScroll > 0 ? SCROLLBAR_W + 4 : 0);
        context.fill(MARGIN - 2, viewTop - 2, contentRight + 2, viewBottom, 0xFF101010);

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
            context.drawString(this.font, line, MARGIN, y, color, false);
            y += LINE_H;
        }
        context.disableScissor();

        if (maxScroll > 0) {
            int barX = this.width - MARGIN - SCROLLBAR_W;
            context.fill(barX, viewTop, barX + SCROLLBAR_W, viewBottom, 0xFF333333);
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
    public void onClose() {
        if (this.minecraft != null && parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
