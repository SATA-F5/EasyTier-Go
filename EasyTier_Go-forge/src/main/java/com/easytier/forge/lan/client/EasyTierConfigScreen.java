package com.easytier.forge.lan.client;

import com.easytier.forge.lan.EasyTierService;
import com.easytier.forge.lan.config.FileSanitizer;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.config.Version;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * EasyTier 联机配置界面（自适应布局）
 */
public class EasyTierConfigScreen extends Screen {

    private final Screen parent;
    private final EasyTierService service;

    private int logX, logY, logW, logH;
    private ConfigListWidget configList;
    private final List<String> logLines = new ArrayList<>();
    private static final int MAX_LOG_LINES = 1000;
    private int logVScroll = 0;
    private int logHScroll = 0;
    private int logMaxLineWidth = 0;

    private PlaceholderTextFieldWidget networkNameField;
    private PlaceholderTextFieldWidget relayServerField;
    private PlaceholderTextFieldWidget networkSecretField;
    private PlaceholderTextFieldWidget devNameField;
    private PlaceholderTextFieldWidget virtualPortField;
    private CycleButton<Boolean> portForwardToggle;
    private CycleButton<Boolean> autoRefreshToggle;
    private StringWidget forwardAddressText;
    private StringWidget statusText;
    private StringWidget fileNameText;
    private String currentInstanceId;
    private String currentFileName = "";
    private String selectedNetwork = "";

    private int addrAreaX, addrAreaY, addrAreaW, addrAreaH;

    private Button startButton;
    private Button stopButton;
    private Button refreshButton;
    private Button applyPortForwardBtn;

    private static final int MARGIN = 6;
    private static final int GAP = 3;
    private static final int TITLE_H = 20;
    private static final int FIELD_H = 18;
    private static final int LABEL_H = 10;
    private static final int BTN_ROW_H = 20;
    private static final int LOG_BG = 0xFF000000;
    private static final int LOG_TEXT = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0x909090;
    private static final int MIN_LEFT_W = 100;
    private static final int MIN_RIGHT_W = 200;
    private static final int LOG_TITLE_H = 12;
    private static final int SCROLLBAR_W = 4;

    private int autoRefreshCounter = 0;
    private int leftPanelW;
    private int rightPanelX;
    private int rightPanelW;
    private String errorMessage = null;
    private long errorMessageTime = 0;

    public EasyTierConfigScreen(Screen parent) {
        super(Component.literal("EasyTier 联机配置"));
        this.parent = parent;
        this.service = EasyTierService.getInstance();
    }

    @Override
    protected void init() {
        leftPanelW = Math.max(MIN_LEFT_W, (int) (this.width * 0.30));
        rightPanelX = MARGIN + leftPanelW + GAP * 2;
        rightPanelW = this.width - rightPanelX - MARGIN;
        if (rightPanelW < MIN_RIGHT_W) {
            leftPanelW = Math.max(100, this.width - MIN_RIGHT_W - MARGIN * 2 - GAP * 2);
            rightPanelX = MARGIN + leftPanelW + GAP * 2;
            rightPanelW = this.width - rightPanelX - MARGIN;
        }

        int contentTop = TITLE_H + GAP;
        int bottomReserve = BTN_ROW_H * 2 + GAP * 3 + 4;
        int availableH = this.height - contentTop - bottomReserve;
        if (availableH < 120) availableH = 120;

        int idealRightH = (LABEL_H + GAP * 2)
                + (LABEL_H + 1 + FIELD_H + GAP) * 4
                + (FIELD_H + GAP);

        int dynGap = GAP;
        int dynFieldH = FIELD_H;
        int dynLabelH = LABEL_H;
        if (idealRightH > availableH) {
            double ratio = (double) availableH / idealRightH;
            dynGap = Math.max(1, (int) (GAP * ratio));
            dynFieldH = Math.max(14, (int) (FIELD_H * ratio));
            dynLabelH = Math.max(8, (int) (LABEL_H * ratio));
        }
        final int fGap = dynGap;
        final int fFieldH = dynFieldH;
        final int fLabelH = dynLabelH;

        int logAreaH = (int) (availableH * 0.50);
        int listAreaTotalH = (int) (availableH * 0.28);
        int addrAreaH = availableH - logAreaH - listAreaTotalH - fGap * 2;

        logX = MARGIN;
        logY = contentTop;
        logW = leftPanelW;
        logH = Math.max(50, logAreaH);

        int listAreaY = logY + logH + fGap;
        int listTitleY = listAreaY;
        int listY = listTitleY + LOG_TITLE_H + 1;
        int listH = Math.max(30, listAreaTotalH - LOG_TITLE_H - 1);

        configList = new ConfigListWidget(this.minecraft, leftPanelW, listH, listY, listY + listH, 16,
                name -> onConfigSelected(name));
        configList.setLeftPos(logX);
        this.addRenderableWidget(configList);

        addrAreaX = logX;
        addrAreaY = listY + listH + fGap;
        addrAreaW = leftPanelW;
        addrAreaH = Math.max(40, addrAreaH);

        int addrY = addrAreaY + 2;
        forwardAddressText = new StringWidget(addrAreaX + 3, addrY, addrAreaW - 6, fLabelH,
                Component.literal("转发地址: 未获取虚拟IP"), this.font);
        forwardAddressText.alignLeft();
        this.addRenderableWidget(forwardAddressText);
        addrY += fLabelH + 2;

        int copyBtnW = (addrAreaW - fGap - 6) / 2;
        addButtonDyn(addrAreaX + 3, addrY, copyBtnW, fFieldH, "复制IP", btn -> copyVirtualIp());
        addButtonDyn(addrAreaX + 3 + copyBtnW + fGap, addrY, copyBtnW, fFieldH, "复制地址", btn -> copyConnectionAddress());

        int rx = rightPanelX;
        int ry = contentTop;
        int fieldW = rightPanelW;
        int halfW = (fieldW - fGap) / 2;

        statusText = new StringWidget(rx, ry, halfW, fLabelH,
                Component.literal("状态: 检测中..."), this.font);
        statusText.alignLeft();
        this.addRenderableWidget(statusText);
        fileNameText = new StringWidget(rx + halfW + fGap, ry, halfW, fLabelH,
                Component.literal(""), this.font);
        fileNameText.alignLeft();
        this.addRenderableWidget(fileNameText);
        ry += fLabelH + fGap * 2;

        ry = addLabelDyn(rx, ry, "网络名称", fieldW, fLabelH);
        networkNameField = addTextFieldDyn(rx, ry, fieldW, fFieldH, "my-mc-network");
        networkNameField.setMaxLength(32);
        ry += fFieldH + fGap;

        ry = addLabelDyn(rx, ry, "中继服务器 (host:port)", fieldW, fLabelH);
        relayServerField = addTextFieldDyn(rx, ry, fieldW, fFieldH, "public.easytier.cn:11010");
        relayServerField.setMaxLength(128);
        ry += fFieldH + fGap;

        ry = addLabelDyn(rx, ry, "网络密码 (留空表示无密码)", fieldW, fLabelH);
        networkSecretField = addTextFieldDyn(rx, ry, fieldW, fFieldH, "");
        networkSecretField.setMaxLength(128);
        ry += fFieldH + fGap;

        ry = addLabelDyn(rx, ry, "网卡名", halfW, fLabelH);
        devNameField = addTextFieldDyn(rx, ry, halfW, fFieldH, "et_forge");
        devNameField.setMaxLength(32);
        addLabelDyn(rx + halfW + fGap, ry - fLabelH - 1, "虚拟端口", halfW, fLabelH);
        virtualPortField = addTextFieldDyn(rx + halfW + fGap, ry, halfW, fFieldH, "25565");
        virtualPortField.setNumericOnly(true);
        virtualPortField.setMaxLength(5);
        virtualPortField.setValue("25565");
        ry += fFieldH + fGap;

        int toggleW = (fieldW - fGap) / 2;
        portForwardToggle = addCyclingDyn(rx, ry, toggleW, fFieldH,
                List.of(true, false), true, "端口转发",
                (btn, val) -> onPortForwardToggle(val));
        autoRefreshToggle = addCyclingDyn(rx + toggleW + fGap, ry, toggleW, fFieldH,
                List.of(false, true), false, "自动刷新",
                (btn, val) -> {});

        currentInstanceId = TomlConfig.generateInstanceId();
        updateFileNamePreview();

        refreshConfigList();

        int btnY1 = this.height - BTN_ROW_H * 2 - fGap * 2 - 2;
        int btnY2 = this.height - BTN_ROW_H - fGap - 2;

        int fullW = this.width - MARGIN * 2;
        int btnW4 = (fullW - fGap * 3) / 4;
        if (btnW4 < 60) btnW4 = 60;
        int btnW5 = (fullW - fGap * 4) / 5;
        if (btnW5 < 50) btnW5 = 50;
        int bx = MARGIN;
        startButton = addButtonDyn(bx, btnY1, btnW4, BTN_ROW_H, "启动", btn -> onToggleStartStop());
        bx += btnW4 + fGap;
        addButtonDyn(bx, btnY1, btnW4, BTN_ROW_H, "状态面板", btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new EasyTierStatusScreen(this, service.getBaseDir()));
            }
        });
        bx += btnW4 + fGap;
        refreshButton = addButtonDyn(bx, btnY1, btnW4, BTN_ROW_H, "刷新", btn -> onRefresh());
        bx += btnW4 + fGap;
        addButtonDyn(bx, btnY1, btnW4, BTN_ROW_H, "中继服务器列表", btn -> onOpenRelayList());

        applyPortForwardBtn = addButtonDyn(MARGIN, btnY2, btnW5, BTN_ROW_H, "端口转发", btn -> onApplyPortForward());
        addButtonDyn(MARGIN + (btnW5 + fGap), btnY2, btnW5, BTN_ROW_H, "保存此新版", btn -> onSaveNewVersion());
        addButtonDyn(MARGIN + (btnW5 + fGap) * 2, btnY2, btnW5, BTN_ROW_H, "删除此版本", btn -> onDeleteVersion());
        addButtonDyn(MARGIN + (btnW5 + fGap) * 3, btnY2, btnW5, BTN_ROW_H, "清理进程", btn -> {
            service.cleanupAllProcesses();
            appendLog("[INFO] 已执行清理所有 EasyTier 进程");
        });
        addButtonDyn(MARGIN + (btnW5 + fGap) * 4, btnY2, btnW5, BTN_ROW_H, "关闭", btn -> onCloseButton());

        service.getLogManager().setUiCallback(line -> {
            synchronized (logLines) {
                logLines.add(line);
                int w = this.font.width(line);
                if (w > logMaxLineWidth) logMaxLineWidth = w;
                while (logLines.size() > MAX_LOG_LINES) {
                    logLines.remove(0);
                }
            }
        });

        if (!service.isPlatformSupported()) {
            startButton.active = false;
            appendLog("[ERROR] 当前平台不支持 EasyTier");
            if (service.isAndroid()) {
                appendLog("[ERROR] Android 不支持，请使用 EasyTier 官方 Android 应用");
            }
        }

        updateButtonStates();
    }

    private void refreshConfigList() {
        List<String> networks = safeListNetworks();
        String sel = selectedNetwork.isEmpty() ? (networks.isEmpty() ? "" : networks.get(0)) : selectedNetwork;
        configList.updateEntries(networks, sel);
    }

    private List<String> safeListNetworks() {
        try {
            List<String> networks = service.listNetworks();
            return networks != null ? networks : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void onConfigSelected(String name) {
        if (networkNameField == null || relayServerField == null) return;
        selectedNetwork = name;
        TomlConfig latest = service.getLatestVersion(name);
        if (latest != null) {
            networkNameField.setValue(latest.getNetworkName() != null ? latest.getNetworkName() : name);
            relayServerField.setValue(latest.getRelayServer() != null ? latest.getRelayServer() : "");
            networkSecretField.setValue(latest.getNetworkSecret() != null ? latest.getNetworkSecret() : "");
            devNameField.setValue(latest.getDevName() != null ? latest.getDevName() : "et_forge");
            currentInstanceId = latest.getInstanceId() != null ? latest.getInstanceId() : TomlConfig.generateInstanceId();
            appendLog("[INFO] 已加载配置: " + name);
        } else {
            networkNameField.setValue(name);
            appendLog("[INFO] 新建配置: " + name);
        }
        updateFileNamePreview();
    }

    private void onToggleStartStop() {
        if (service.isRunning() || service.isStartPending()) {
            onStop();
        } else {
            onStart();
        }
    }

    private void onStart() {
        String name = networkNameField.getValue().trim();
        String relay = relayServerField.getValue().trim();

        if (name.isEmpty()) {
            appendLog("[ERROR] 网络名称不能为空");
            return;
        }
        if (relay.isEmpty()) {
            appendLog("[ERROR] 中继服务器不能为空");
            return;
        }
        if (service.isStartPending()) {
            appendLog("[WARN] 启动任务正在执行中，请稍候");
            return;
        }

        Version version = new Version(1, 0);
        String secret = networkSecretField.getValue().trim();
        String devName = devNameField.getValue().trim();

        appendLog("[INFO] 正在启动 EasyTier（异步）...");
        startButton.active = false;
        startButton.setMessage(Component.literal("启动中..."));

        service.startAsync(name, version, relay, secret, devName, true)
                .thenAcceptAsync(ok -> {
                    if (ok != null && ok) {
                        appendLog("[INFO] EasyTier 启动成功，等待虚拟IP分配...");
                        clearError();
                        if (portForwardToggle != null && portForwardToggle.getValue()) {
                            appendLog("[INFO] 端口转发已启用，将在虚拟IP分配后自动配置...");
                            autoApplyPortForward();
                        }
                    } else {
                        showError("EasyTier 启动失败，请查看日志详情");
                    }
                    updateButtonStates();
                }, Runnable::run)
                .exceptionally(ex -> {
                    StringBuilder err = new StringBuilder("启动异常: " + ex.getMessage());
                    Throwable cause = ex.getCause();
                    while (cause != null) {
                        err.append(" | 原因: ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
                        cause = cause.getCause();
                    }
                    showError(err.toString());
                    updateButtonStates();
                    return null;
                });
    }

    private void onStop() {
        if (!service.isRunning()) {
            appendLog("[WARN] EasyTier 未运行");
            return;
        }
        appendLog("[INFO] 正在停止 EasyTier（异步）...");
        startButton.active = false;
        service.stopAsync().thenRunAsync(() -> {
            appendLog("[INFO] EasyTier 已停止");
            updateButtonStates();
        }, Runnable::run);
    }

    private void onRefresh() {
        if (!service.isRunning()) {
            appendLog("[WARN] EasyTier 未运行");
            return;
        }
        appendLog("[INFO] 正在刷新节点信息（异步）...");
        refreshButton.active = false;
        service.refreshAsync().thenAcceptAsync(ip -> {
            appendLog("---- 节点信息 ----");
            try {
                String nodeInfo = service.getCliRunner().node();
                appendLog(nodeInfo.isEmpty() ? "(无输出)" : nodeInfo);
            } catch (Exception e) {
                appendLog("[ERROR] node 查询失败: " + e.getMessage());
            }
            try {
                String peerInfo = service.getCliRunner().peer();
                appendLog(peerInfo.isEmpty() ? "(无输出)" : peerInfo);
            } catch (Exception e) {
                appendLog("[ERROR] peer 查询失败: " + e.getMessage());
            }
            if (ip != null) {
                appendLog("[INFO] 当前虚拟IP: " + ip);
            }
            updateAddressDisplay();
            refreshButton.active = true;
        }, Runnable::run);
    }

    private void onOpenRelayList() {
        Util.getPlatform().openUri("https://info.qtet.cn/uptime/easytier");
    }

    private void onApplyPortForward() {
        if (!service.isRunning()) {
            appendLog("[ERROR] EasyTier 未运行，无法应用端口转发");
            return;
        }
        String ip = service.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            appendLog("[ERROR] 虚拟IP未获取，请等待或点击刷新");
            return;
        }
        int mcPort = getMinecraftPort();
        if (mcPort <= 0) {
            appendLog("[ERROR] 未检测到 Minecraft 局域网端口，请先对局域网开放");
            return;
        }
        if (!portForwardToggle.getValue()) {
            appendLog("[INFO] 端口转发已禁用，仅显示直连地址");
            return;
        }
        int vPort;
        String vPortStr = virtualPortField.getValue().trim();
        if (vPortStr.isEmpty()) {
            appendLog("[ERROR] 虚拟端口不能为空，请输入 1-65535 之间的端口号");
            return;
        }
        try {
            vPort = Integer.parseInt(vPortStr);
        } catch (NumberFormatException e) {
            appendLog("[ERROR] 虚拟端口格式错误: '" + vPortStr + "'，请输入纯数字（1-65535）");
            return;
        }
        if (vPort < 1 || vPort > 65535) {
            appendLog("[ERROR] 虚拟端口超出范围: " + vPort + "，有效范围 1-65535");
            return;
        }
        boolean ok = service.getProcessManager().addPortForward("tcp", vPort, ip, mcPort);
        if (ok) {
            appendLog("[INFO] 端口转发已添加: tcp 0.0.0.0:" + vPort + " -> " + ip + ":" + mcPort);
            appendLog("[INFO] 好友连接地址: " + ip + ":" + vPort);
        } else {
            appendLog("[ERROR] 端口转发添加失败");
        }
        updateAddressDisplay();
    }

    private void autoApplyPortForward() {
        new Thread(() -> {
            try {
                String ip = null;
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(1000);
                    ip = service.getVirtualIp();
                    if (ip != null && !ip.isEmpty()) {
                        break;
                    }
                }
                if (ip == null || ip.isEmpty()) {
                    appendLog("[WARN] 自动端口转发：虚拟IP分配超时，请手动点击刷新后再应用端口转发");
                    return;
                }

                int mcPort = getMinecraftPort();
                if (mcPort <= 0) {
                    appendLog("[INFO] 自动端口转发：未检测到 Minecraft 局域网端口（请先对局域网开放），跳过自动转发");
                    return;
                }

                int vPort = 25565;
                String vPortStr = virtualPortField != null ? virtualPortField.getValue().trim() : "";
                if (!vPortStr.isEmpty()) {
                    try {
                        int parsed = Integer.parseInt(vPortStr);
                        if (parsed >= 1 && parsed <= 65535) {
                            vPort = parsed;
                        } else {
                            appendLog("[WARN] 自动端口转发：虚拟端口超出范围 " + parsed + "，使用默认 25565");
                        }
                    } catch (NumberFormatException e) {
                        appendLog("[WARN] 自动端口转发：虚拟端口格式错误 '" + vPortStr + "'，使用默认 25565");
                    }
                }

                boolean ok = service.getProcessManager().addPortForward("tcp", vPort, ip, mcPort);
                if (ok) {
                    appendLog("[INFO] 自动端口转发成功: tcp 0.0.0.0:" + vPort + " -> " + ip + ":" + mcPort);
                    appendLog("[INFO] 好友连接地址: " + ip + ":" + vPort);
                    updateAddressDisplay();
                } else {
                    appendLog("[ERROR] 自动端口转发失败，请手动点击端口转发按钮重试");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "easytier-auto-portforward").start();
    }

    private void onSaveNewVersion() {
        String name = networkNameField.getValue().trim();
        if (name.isEmpty()) {
            appendLog("[ERROR] 请先填写网络名称");
            return;
        }
        TomlConfig config = buildConfigFromUi();
        Version newVer = service.getConfigManager().saveAsNewVersion(config);
        if (newVer != null) {
            appendLog("[INFO] 已保存为新版本: " + newVer);
            refreshConfigList();
            selectedNetwork = name;
            configList.updateEntries(safeListNetworks(), name);
        } else {
            appendLog("[ERROR] 保存失败");
        }
    }

    private void onDeleteVersion() {
        String name = networkNameField.getValue().trim();
        if (name.isEmpty()) return;

        if (service.isRunning() && service.getProcessManager().getCurrentConfig() != null) {
            TomlConfig running = service.getProcessManager().getCurrentConfig();
            if (name.equals(running.getNetworkName())) {
                appendLog("[ERROR] 正在运行的配置无法删除，请先停止");
                return;
            }
        }

        boolean deleted = service.getConfigManager().deleteConfig(name, new Version(1, 0));
        if (deleted) {
            appendLog("[INFO] 已删除配置: " + name);
            refreshConfigList();
            selectedNetwork = "";
        } else {
            appendLog("[ERROR] 删除失败");
        }
    }

    private void onCloseButton() {
        if (service.isRunning()) {
            appendLog("[INFO] EasyTier 将在后台继续运行");
        }
        service.getLogManager().setUiCallback(null);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void onPortForwardToggle(boolean enabled) {
        virtualPortField.setEditable(enabled);
        if (applyPortForwardBtn != null) {
            applyPortForwardBtn.active = enabled && service.isRunning();
        }
    }

    private TomlConfig buildConfigFromUi() {
        TomlConfig config = new TomlConfig();
        config.setNetworkName(networkNameField.getValue().trim());
        config.setInstanceName(networkNameField.getValue().trim());
        config.setInstanceId(currentInstanceId);
        config.setRelayServer(relayServerField.getValue().trim());
        config.setNetworkSecret(networkSecretField.getValue().trim());
        config.setDevName(devNameField.getValue().trim());
        config.setDhcp(true);
        config.setVersion(new Version(1, 0));
        return config;
    }

    private int getMinecraftPort() {
        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
            return this.minecraft.getSingleplayerServer().getPort();
        }
        return -1;
    }

    private void copyVirtualIp() {
        String ip = service.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            appendLog("[WARN] 虚拟IP未获取");
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(ip);
        appendLog("[INFO] 已复制虚拟IP: " + ip);
    }

    private void copyConnectionAddress() {
        String ip = service.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            appendLog("[WARN] 虚拟IP未获取");
            return;
        }
        String addr;
        if (portForwardToggle.getValue()) {
            addr = ip + ":" + virtualPortField.getValue().trim();
        } else {
            int mcPort = getMinecraftPort();
            addr = ip + ":" + (mcPort > 0 ? mcPort : 25565);
        }
        this.minecraft.keyboardHandler.setClipboard(addr);
        appendLog("[INFO] 已复制连接地址: " + addr);
    }

    private void updateAddressDisplay() {
        if (forwardAddressText == null) return;
        String ip = service.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            forwardAddressText.setMessage(Component.literal("转发地址: 未获取虚拟IP"));
            return;
        }
        String vPort = virtualPortField != null ? virtualPortField.getValue().trim() : "25565";
        forwardAddressText.setMessage(Component.literal("转发地址: " + ip + ":" + vPort));
    }

    private void updateFileNamePreview() {
        String name = networkNameField != null ? networkNameField.getValue().trim() : "";
        String safe = FileSanitizer.sanitize(name);
        currentFileName = safe + "_v1.0.toml";
        if (fileNameText != null) {
            fileNameText.setMessage(Component.literal("文件: " + currentFileName));
        }
    }

    private void updateButtonStates() {
        if (startButton == null) return;
        boolean running = service.isRunning();
        boolean pending = service.isStartPending();
        startButton.active = service.isPlatformSupported() && !pending;
        if (pending) {
            startButton.setMessage(Component.literal("启动中..."));
        } else if (running) {
            startButton.setMessage(Component.literal("停止"));
        } else {
            startButton.setMessage(Component.literal("启动"));
        }
        refreshButton.active = running;
        applyPortForwardBtn.active = running && portForwardToggle.getValue();
    }

    private int addLabelDyn(int x, int y, String text, int w, int labelH) {
        StringWidget widget = new StringWidget(x, y, w, labelH,
                Component.literal(text), this.font);
        widget.alignLeft();
        this.addRenderableWidget(widget);
        return y + labelH + 1;
    }

    private PlaceholderTextFieldWidget addTextFieldDyn(int x, int y, int w, int h, String placeholder) {
        PlaceholderTextFieldWidget field = new PlaceholderTextFieldWidget(this.font, x, y, w, h, Component.literal(""));
        field.setPlaceholder(Component.literal(placeholder));
        this.addRenderableWidget(field);
        return field;
    }

    private Button addButtonDyn(int x, int y, int w, int h, String text, Button.OnPress action) {
        Button btn = Button.builder(Component.literal(text), action)
                .bounds(x, y, w, h).build();
        this.addRenderableWidget(btn);
        return btn;
    }

    private <T> CycleButton<T> addCyclingDyn(int x, int y, int w, int h,
                                              List<T> values, T initial, String key,
                                              CycleButton.OnValueChange<T> callback) {
        CycleButton<T> btn = CycleButton.<T>builder(val -> Component.literal(val.toString()))
                .withValues(values)
                .withInitialValue(initial)
                .create(x, y, w, h, Component.literal(key), callback);
        this.addRenderableWidget(btn);
        return btn;
    }

    private void appendLog(String line) {
        synchronized (logLines) {
            logLines.add(line);
            int w = this.font.width(line);
            if (w > logMaxLineWidth) logMaxLineWidth = w;
            while (logLines.size() > MAX_LOG_LINES) {
                logLines.remove(0);
            }
        }
    }

    private void showError(String msg) {
        this.errorMessage = msg;
        this.errorMessageTime = System.currentTimeMillis();
        appendLog("[ERROR] " + msg);
    }

    private void clearError() {
        this.errorMessage = null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        if (statusText != null) {
            boolean running = service.isRunning();
            String status = running ? "运行中" : (service.isStartPending() ? "启动中..." : "已停止");
            int statusColor = running ? 0x55FF55 : (service.isStartPending() ? 0xFFFF55 : 0xFF5555);
            statusText.setMessage(Component.literal("状态: " + status));
            statusText.setColor(statusColor);
        }

        super.render(guiGraphics, mouseX, mouseY, delta);

        renderLogArea(guiGraphics);

        if (configList != null) {
            int titleY = configList.getTopY() - LOG_TITLE_H - 1;
            guiGraphics.fill(logX, titleY, logX + logW, titleY + LOG_TITLE_H, 0xFF1A1A1A);
            drawBorder(guiGraphics, logX, titleY, logW, LOG_TITLE_H, 0xFF333333);
            guiGraphics.drawString(this.font, "配置列表", logX + 4, titleY + 2, LABEL_COLOR, false);
        }
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + 1, color);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, color);
        guiGraphics.fill(x, y, x + 1, y + h, color);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void renderLogArea(GuiGraphics guiGraphics) {
        guiGraphics.fill(logX, logY, logX + logW, logY + logH, LOG_BG);
        drawBorder(guiGraphics, logX, logY, logW, logH, 0xFF444444);

        guiGraphics.fill(logX, logY, logX + logW, logY + LOG_TITLE_H, 0xFF1A1A1A);
        guiGraphics.drawString(this.font, "运行日志", logX + 4, logY + 2, 0xAAAAAA, false);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - errorMessageTime;
            if (elapsed < 15000) {
                boolean blink = (elapsed / 500) % 2 == 0;
                int errColor = blink ? 0xFFFF5555 : 0xFFAA0000;
                String errLabel = "错误!";
                int labelW = this.font.width(errLabel);
                guiGraphics.drawString(this.font, errLabel, logX + logW - labelW - 4, logY + 2, errColor, false);
            } else {
                errorMessage = null;
            }
        }

        int textTop = logY + LOG_TITLE_H + 1;
        int textH = logH - LOG_TITLE_H - 2;
        int lineH = this.font.lineHeight + 1;
        int visibleLines = Math.max(1, textH / lineH);

        int maxHScroll = Math.max(0, logMaxLineWidth - (logW - 8));
        if (logHScroll > maxHScroll) logHScroll = maxHScroll;
        if (logHScroll < 0) logHScroll = 0;

        guiGraphics.enableScissor(logX, textTop, logX + logW, textTop + textH);
        try {
            synchronized (logLines) {
                int startIdx = Math.max(0, logLines.size() - visibleLines - logVScroll);
                int endIdx = Math.min(logLines.size(), startIdx + visibleLines);
                int drawY = textTop;
                for (int i = startIdx; i < endIdx; i++) {
                    String line = logLines.get(i);
                    int color = LOG_TEXT;
                    if (line.contains("[ERROR]")) color = 0xFF5555;
                    else if (line.contains("[WARN]")) color = 0xFFAA00;
                    else if (line.contains("[INFO]")) color = 0x55FF55;
                    guiGraphics.drawString(this.font, line, logX + 3 - logHScroll, drawY, color, false);
                    drawY += lineH;
                }
            }
        } finally {
            guiGraphics.disableScissor();
        }

        int totalLines = logLines.size();
        if (totalLines > visibleLines) {
            int scrollbarX = logX + logW - SCROLLBAR_W - 1;
            int scrollbarY = textTop;
            int scrollbarH = textH;
            int thumbH = Math.max(8, (int) ((long) scrollbarH * visibleLines / totalLines));
            int thumbY = scrollbarY + (int) ((long) (scrollbarH - thumbH) * logVScroll / Math.max(1, totalLines - visibleLines));
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_W, scrollbarY + scrollbarH, 0xFF333333);
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, 0xFF666666);
        }

        if (logMaxLineWidth > logW - 8) {
            int hScrollbarY = logY + logH - SCROLLBAR_W - 1;
            int hScrollbarX = logX + 2;
            int hScrollbarW = logW - 4;
            int hThumbW = Math.max(20, (int) ((long) hScrollbarW * (logW - 8) / logMaxLineWidth));
            int hThumbX = hScrollbarX + (int) ((long) (hScrollbarW - hThumbW) * logHScroll / Math.max(1, maxHScroll));
            guiGraphics.fill(hScrollbarX, hScrollbarY, hScrollbarX + hScrollbarW, hScrollbarY + SCROLLBAR_W, 0xFF333333);
            guiGraphics.fill(hThumbX, hScrollbarY, hThumbX + hThumbW, hScrollbarY + SCROLLBAR_W, 0xFF666666);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= logX && mouseX <= logX + logW && mouseY >= logY && mouseY <= logY + logH) {
            int textTop = logY + LOG_TITLE_H + 1;
            int textH = logH - LOG_TITLE_H - 2;
            int lineH = this.font.lineHeight + 1;
            int visibleLines = Math.max(1, textH / lineH);
            int maxVScroll = Math.max(0, logLines.size() - visibleLines);

            if (hasShiftDown()) {
                int maxHScroll = Math.max(0, logMaxLineWidth - (logW - 8));
                logHScroll = Math.max(0, Math.min(maxHScroll, logHScroll - (int) (amount * 20)));
            } else {
                logVScroll = Math.max(0, Math.min(maxVScroll, logVScroll + (int) amount));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private boolean draggingVScroll = false;
    private boolean draggingHScroll = false;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int textTop = logY + LOG_TITLE_H + 1;
        int textH = logH - LOG_TITLE_H - 2;
        int lineH = this.font.lineHeight + 1;
        int visibleLines = Math.max(1, textH / lineH);
        int totalLines = logLines.size();

        if (totalLines > visibleLines) {
            int scrollbarX = logX + logW - SCROLLBAR_W - 1;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_W
                    && mouseY >= textTop && mouseY <= textTop + textH) {
                draggingVScroll = true;
                updateVScrollFromMouse(mouseY, textTop, textH, visibleLines, totalLines);
                return true;
            }
        }

        if (logMaxLineWidth > logW - 8) {
            int hScrollbarY = logY + logH - SCROLLBAR_W - 1;
            if (mouseX >= logX + 2 && mouseX <= logX + logW - 2
                    && mouseY >= hScrollbarY && mouseY <= hScrollbarY + SCROLLBAR_W) {
                draggingHScroll = true;
                updateHScrollFromMouse(mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int textTop = logY + LOG_TITLE_H + 1;
        int textH = logH - LOG_TITLE_H - 2;
        int lineH = this.font.lineHeight + 1;
        int visibleLines = Math.max(1, textH / lineH);
        int totalLines = logLines.size();

        if (draggingVScroll && totalLines > visibleLines) {
            updateVScrollFromMouse(mouseY, textTop, textH, visibleLines, totalLines);
            return true;
        }
        if (draggingHScroll) {
            updateHScrollFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingVScroll = false;
        draggingHScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateVScrollFromMouse(double mouseY, int textTop, int textH, int visibleLines, int totalLines) {
        int thumbH = Math.max(8, (int) ((long) textH * visibleLines / totalLines));
        int maxThumbY = textH - thumbH;
        double ratio = Math.max(0, Math.min(1, (mouseY - textTop - thumbH / 2.0) / maxThumbY));
        int maxVScroll = totalLines - visibleLines;
        logVScroll = (int) (ratio * maxVScroll);
    }

    private void updateHScrollFromMouse(double mouseX) {
        int hScrollbarX = logX + 2;
        int hScrollbarW = logW - 4;
        int maxHScroll = Math.max(0, logMaxLineWidth - (logW - 8));
        double ratio = Math.max(0, Math.min(1, (mouseX - hScrollbarX) / hScrollbarW));
        logHScroll = (int) (ratio * maxHScroll);
    }

    @Override
    public void tick() {
        super.tick();
        if (startButton != null) {
            updateButtonStates();
        }
        updateAddressDisplay();
        updateFileNamePreview();

        if (autoRefreshToggle != null && autoRefreshToggle.getValue() && service.isRunning()) {
            autoRefreshCounter++;
            if (autoRefreshCounter >= 100) {
                autoRefreshCounter = 0;
                onRefresh();
            }
        }
    }

    @Override
    public void onClose() {
        service.getLogManager().setUiCallback(null);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
