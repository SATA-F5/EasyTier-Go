package com.easytier.fabric.lan.client;

import com.easytier.fabric.lan.EasyTierService;
import com.easytier.fabric.lan.config.FileSanitizer;
import com.easytier.fabric.lan.config.TomlConfig;
import com.easytier.fabric.lan.config.Version;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * EasyTier 联机配置界面（自适应布局）
 * 左栏：运行日志（上）+ 配置选择列表（下）
 * 右栏：编辑区 + 开关 + 连接地址 + 复制按钮
 * 底部：两行操作按钮
 * 日志支持垂直+水平滚动，所有坐标基于 this.width/this.height 计算
 */
public class EasyTierConfigScreen extends Screen {

    private final Screen parent;
    private final EasyTierService service;

    // 左栏
    private int logX, logY, logW, logH;
    private ConfigListWidget configList;
    private final List<String> logLines = new ArrayList<>();
    private static final int MAX_LOG_LINES = 1000;
    private int logVScroll = 0;
    private int logHScroll = 0;
    private int logMaxLineWidth = 0;

    // 右栏
    private PlaceholderTextFieldWidget networkNameField;
    private PlaceholderTextFieldWidget relayServerField;
    private PlaceholderTextFieldWidget networkSecretField;
    private PlaceholderTextFieldWidget devNameField;
    private PlaceholderTextFieldWidget virtualPortField;
    private CyclingButtonWidget<Boolean> portForwardToggle;
    private CyclingButtonWidget<Boolean> autoRefreshToggle;
    private TextWidget forwardAddressText;
    private TextWidget statusText;
    private TextWidget fileNameText;
    private String currentInstanceId;
    private String currentFileName = "";
    private String selectedNetwork = "";

    // 左栏底部地址区
    private int addrAreaX, addrAreaY, addrAreaW, addrAreaH;

    // 操作按钮
    private ButtonWidget startButton;
    private ButtonWidget stopButton;
    private ButtonWidget refreshButton;
    private ButtonWidget applyPortForwardBtn;

    // 自适应布局常量
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
        super(Text.literal("EasyTier 联机配置"));
        this.parent = parent;
        this.service = EasyTierService.getInstance();
    }

    @Override
    protected void init() {
        // ===== 自适应面板宽度计算 =====
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

        // ===== 右栏高度预计算与自适应压缩 =====
        int idealRightH = (LABEL_H + GAP * 2)                    // 状态行
                + (LABEL_H + 1 + FIELD_H + GAP) * 4              // 4组标签+输入框
                + (FIELD_H + GAP);                               // 开关行

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

        // ===== 左栏区域划分 =====
        // 日志 50%，配置列表 28%，地址区 22%
        int logAreaH = (int) (availableH * 0.50);
        int listAreaTotalH = (int) (availableH * 0.28);
        int addrAreaH = availableH - logAreaH - listAreaTotalH - fGap * 2;

        // 日志区域
        logX = MARGIN;
        logY = contentTop;
        logW = leftPanelW;
        logH = Math.max(50, logAreaH);

        // 配置列表区域（缩小）
        int listAreaY = logY + logH + fGap;
        int listTitleY = listAreaY;
        int listY = listTitleY + LOG_TITLE_H + 1;
        int listH = Math.max(30, listAreaTotalH - LOG_TITLE_H - 1);

        configList = new ConfigListWidget(this.client, leftPanelW, listH, listY, listY + listH, 16,
                name -> onConfigSelected(name));
        configList.setLeftPos(logX);
        this.addDrawableChild(configList);

        // 转发地址区（左栏底部）
        addrAreaX = logX;
        addrAreaY = listY + listH + fGap;
        addrAreaW = leftPanelW;
        addrAreaH = Math.max(40, addrAreaH);

        int addrY = addrAreaY + 2;
        forwardAddressText = new TextWidget(addrAreaX + 3, addrY, addrAreaW - 6, fLabelH,
                Text.literal("转发地址: 未获取虚拟IP"), this.textRenderer);
        forwardAddressText.alignLeft();
        this.addDrawableChild(forwardAddressText);
        addrY += fLabelH + 2;

        int copyBtnW = (addrAreaW - fGap - 6) / 2;
        addButtonDyn(addrAreaX + 3, addrY, copyBtnW, fFieldH, "复制IP", btn -> copyVirtualIp());
        addButtonDyn(addrAreaX + 3 + copyBtnW + fGap, addrY, copyBtnW, fFieldH, "复制地址", btn -> copyConnectionAddress());

        // ===== 右栏：编辑区 =====
        int rx = rightPanelX;
        int ry = contentTop;
        int fieldW = rightPanelW;
        int halfW = (fieldW - fGap) / 2;

        // 状态 + 文件名（同行）
        statusText = new TextWidget(rx, ry, halfW, fLabelH,
                Text.literal("状态: 检测中..."), this.textRenderer);
        statusText.alignLeft();
        this.addDrawableChild(statusText);
        fileNameText = new TextWidget(rx + halfW + fGap, ry, halfW, fLabelH,
                Text.literal(""), this.textRenderer);
        fileNameText.alignLeft();
        this.addDrawableChild(fileNameText);
        ry += fLabelH + fGap * 2;

        // 网络名称
        ry = addLabelDyn(rx, ry, "网络名称", fieldW, fLabelH);
        networkNameField = addTextFieldDyn(rx, ry, fieldW, fFieldH, "my-mc-network");
        networkNameField.setMaxLength(32);
        ry += fFieldH + fGap;

        // 中继服务器
        ry = addLabelDyn(rx, ry, "中继服务器 (host:port)", fieldW, fLabelH);
        relayServerField = addTextFieldDyn(rx, ry, fieldW, fFieldH, "public.easytier.cn:11010");
        relayServerField.setMaxLength(128);
        ry += fFieldH + fGap;

        // 网络密码
        ry = addLabelDyn(rx, ry, "网络密码 (留空表示无密码)", fieldW, fLabelH);
        networkSecretField = addTextFieldDyn(rx, ry, fieldW, fFieldH, "");
        networkSecretField.setMaxLength(128);
        ry += fFieldH + fGap;

        // 网卡名 + 虚拟端口（同行）
        ry = addLabelDyn(rx, ry, "网卡名", halfW, fLabelH);
        devNameField = addTextFieldDyn(rx, ry, halfW, fFieldH, "et_fabric");
        devNameField.setMaxLength(32);
        addLabelDyn(rx + halfW + fGap, ry - fLabelH - 1, "虚拟端口", halfW, fLabelH);
        virtualPortField = addTextFieldDyn(rx + halfW + fGap, ry, halfW, fFieldH, "25565");
        virtualPortField.setMaxLength(5);
        virtualPortField.setText("25565");
        ry += fFieldH + fGap;

        // 开关行：端口转发 + 自动刷新
        int toggleW = (fieldW - fGap) / 2;
        portForwardToggle = addCyclingDyn(rx, ry, toggleW, fFieldH,
                List.of(true, false), true, "端口转发",
                (btn, val) -> onPortForwardToggle(val));
        autoRefreshToggle = addCyclingDyn(rx + toggleW + fGap, ry, toggleW, fFieldH,
                List.of(false, true), false, "自动刷新",
                (btn, val) -> {});

        // 实例 ID 内部生成
        currentInstanceId = TomlConfig.generateInstanceId();
        updateFileNamePreview();

        // 加载配置列表
        refreshConfigList();

        // ===== 底部操作按钮（两行，跨全宽） =====
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
            if (this.client != null) {
                this.client.setScreen(new EasyTierStatusScreen(this, service.getBaseDir()));
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
        addButtonDyn(MARGIN + (btnW5 + fGap) * 4, btnY2, btnW5, BTN_ROW_H, "关闭", btn -> onClose());

        // 日志回调
        service.getLogManager().setUiCallback(line -> {
            synchronized (logLines) {
                logLines.add(line);
                int w = this.textRenderer.getWidth(line);
                if (w > logMaxLineWidth) logMaxLineWidth = w;
                while (logLines.size() > MAX_LOG_LINES) {
                    logLines.remove(0);
                }
            }
        });

        // 平台不支持
        if (!service.isPlatformSupported()) {
            startButton.active = false;
            appendLog("[ERROR] 当前平台不支持 EasyTier");
            if (service.isAndroid()) {
                appendLog("[ERROR] Android 不支持，请使用 EasyTier 官方 Android 应用");
            }
        }

        updateButtonStates();
    }

    // ==================== 配置列表 ====================

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
            networkNameField.setText(latest.getNetworkName() != null ? latest.getNetworkName() : name);
            relayServerField.setText(latest.getRelayServer() != null ? latest.getRelayServer() : "");
            networkSecretField.setText(latest.getNetworkSecret() != null ? latest.getNetworkSecret() : "");
            devNameField.setText(latest.getDevName() != null ? latest.getDevName() : "et_fabric");
            currentInstanceId = latest.getInstanceId() != null ? latest.getInstanceId() : TomlConfig.generateInstanceId();
            appendLog("[INFO] 已加载配置: " + name);
        } else {
            networkNameField.setText(name);
            appendLog("[INFO] 新建配置: " + name);
        }
        updateFileNamePreview();
    }

    // ==================== 事件处理（异步） ====================

    private void onToggleStartStop() {
        if (service.isRunning() || service.isStartPending()) {
            onStop();
        } else {
            onStart();
        }
    }

    private void onStart() {
        String name = networkNameField.getText().trim();
        String relay = relayServerField.getText().trim();

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

        // 下载知情同意：如果 EasyTier 二进制不存在，先询问用户
        if (!service.getProcessManager().isBinaryReady()) {
            showDownloadConfirm(name, relay);
            return;
        }

        doStart(name, relay);
    }

    /**
     * 显示下载确认对话框
     */
    private void showDownloadConfirm(String name, String relay) {
        if (this.client == null) return;
        net.minecraft.client.gui.screen.ConfirmScreen confirm = new net.minecraft.client.gui.screen.ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        appendLog("[INFO] 用户已同意下载 EasyTier CLI");
                        doStart(name, relay);
                    } else {
                        appendLog("[INFO] 用户取消下载");
                        this.client.setScreen(this);
                    }
                },
                Text.literal("需要下载 EasyTier CLI"),
                Text.literal("首次使用需要从 GitHub 下载 EasyTier 核心程序（约 10-20MB）。\n"
                        + "下载源：GitHub Releases（通过 13 个镜像加速，最后回退原链接）。\n"
                        + "下载后将进行 SHA256 完整性校验。\n\n"
                        + "是否继续？"),
                Text.literal("确认下载"),
                Text.literal("取消")
        );
        this.client.setScreen(confirm);
    }

    private void doStart(String name, String relay) {
        Version version = new Version(1, 0);
        String secret = networkSecretField.getText().trim();
        String devName = devNameField.getText().trim();

        appendLog("[INFO] 正在启动 EasyTier（异步）...");
        startButton.active = false;
        startButton.setMessage(Text.literal("启动中..."));

        service.startAsync(name, version, relay, secret, devName, true)
                .thenAcceptAsync(ok -> {
                    if (ok != null && ok) {
                        appendLog("[INFO] EasyTier 启动成功，等待虚拟IP分配...");
                        clearError();
                        // 启动成功后自动应用端口转发（如果开关开启）
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
        Util.getOperatingSystem().open("https://info.qtet.cn/uptime/easytier");
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
        String vPortStr = virtualPortField.getText().trim();
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

    /**
     * 自动应用端口转发：后台等待虚拟IP分配，然后自动检测 Minecraft 端口并添加转发
     */
    private void autoApplyPortForward() {
        new Thread(() -> {
            try {
                // 等待虚拟IP分配，最多15秒
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

                // 检测 Minecraft 集成服务器端口
                int mcPort = getMinecraftPort();
                if (mcPort <= 0) {
                    appendLog("[INFO] 自动端口转发：未检测到 Minecraft 局域网端口（请先对局域网开放），跳过自动转发");
                    return;
                }

                // 解析虚拟端口，为空或格式错误时回退到默认 25565
                int vPort = 25565;
                String vPortStr = virtualPortField != null ? virtualPortField.getText().trim() : "";
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

                // 添加端口转发
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
        String name = networkNameField.getText().trim();
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
        String name = networkNameField.getText().trim();
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

    private void onClose() {
        if (service.isRunning()) {
            appendLog("[INFO] EasyTier 将在后台继续运行");
        }
        service.getLogManager().setUiCallback(null);
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private void onPortForwardToggle(boolean enabled) {
        virtualPortField.active = enabled;
        if (applyPortForwardBtn != null) {
            applyPortForwardBtn.active = enabled && service.isRunning();
        }
    }

    // ==================== 辅助方法 ====================

    private TomlConfig buildConfigFromUi() {
        TomlConfig config = new TomlConfig();
        config.setNetworkName(networkNameField.getText().trim());
        config.setInstanceName(networkNameField.getText().trim());
        config.setInstanceId(currentInstanceId);
        config.setRelayServer(relayServerField.getText().trim());
        config.setNetworkSecret(networkSecretField.getText().trim());
        config.setDevName(devNameField.getText().trim());
        config.setDhcp(true);
        config.setVersion(new Version(1, 0));
        return config;
    }

    private int getMinecraftPort() {
        if (this.client != null && this.client.getServer() != null) {
            return this.client.getServer().getServerPort();
        }
        return -1;
    }

    private void copyVirtualIp() {
        String ip = service.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            appendLog("[WARN] 虚拟IP未获取");
            return;
        }
        this.client.keyboard.setClipboard(ip);
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
            addr = ip + ":" + virtualPortField.getText().trim();
        } else {
            int mcPort = getMinecraftPort();
            addr = ip + ":" + (mcPort > 0 ? mcPort : 25565);
        }
        this.client.keyboard.setClipboard(addr);
        appendLog("[INFO] 已复制连接地址: " + addr);
    }

    private void updateAddressDisplay() {
        if (forwardAddressText == null) return;
        String ip = service.getVirtualIp();
        if (ip == null || ip.isEmpty()) {
            forwardAddressText.setMessage(Text.literal("转发地址: 未获取虚拟IP"));
            return;
        }
        String vPort = virtualPortField != null ? virtualPortField.getText().trim() : "25565";
        forwardAddressText.setMessage(Text.literal("转发地址: " + ip + ":" + vPort));
    }

    private void updateFileNamePreview() {
        String name = networkNameField != null ? networkNameField.getText().trim() : "";
        String safe = FileSanitizer.sanitize(name);
        currentFileName = safe + "_v1.0.toml";
        if (fileNameText != null) {
            fileNameText.setMessage(Text.literal("文件: " + currentFileName));
        }
    }

    private void updateButtonStates() {
        if (startButton == null) return;
        boolean running = service.isRunning();
        boolean pending = service.isStartPending();
        startButton.active = service.isPlatformSupported() && !pending;
        if (pending) {
            startButton.setMessage(Text.literal("启动中..."));
        } else if (running) {
            startButton.setMessage(Text.literal("停止"));
        } else {
            startButton.setMessage(Text.literal("启动"));
        }
        refreshButton.active = running;
        applyPortForwardBtn.active = running && portForwardToggle.getValue();
    }

    // ==================== 布局辅助 ====================

    private int addLabel(int x, int y, String text) {
        TextWidget widget = new TextWidget(x, y, rightPanelW, LABEL_H,
                Text.literal(text), this.textRenderer);
        widget.alignLeft();
        this.addDrawableChild(widget);
        return y + LABEL_H + 1;
    }

    private PlaceholderTextFieldWidget addTextField(int x, int y, int w, int h, String placeholder) {
        PlaceholderTextFieldWidget field = new PlaceholderTextFieldWidget(this.textRenderer, x, y, w, h, Text.literal(""));
        field.setPlaceholder(Text.literal(placeholder));
        this.addDrawableChild(field);
        return field;
    }

    private ButtonWidget addButton(int x, int y, int w, int h, String text, ButtonWidget.PressAction action) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(text), action)
                .dimensions(x, y, w, h).build();
        this.addDrawableChild(btn);
        return btn;
    }

    private <T> CyclingButtonWidget<T> addCycling(int x, int y, int w, int h,
                                                   List<T> values, T initial, String key,
                                                   CyclingButtonWidget.UpdateCallback<T> callback) {
        CyclingButtonWidget<T> btn = CyclingButtonWidget.<T>builder(val -> Text.literal(val.toString()))
                .values(values)
                .initially(initial)
                .build(x, y, w, h, Text.literal(key), callback);
        this.addDrawableChild(btn);
        return btn;
    }

    // ===== 动态尺寸辅助方法（适配 4:3 等窄屏） =====

    private int addLabelDyn(int x, int y, String text, int w, int labelH) {
        TextWidget widget = new TextWidget(x, y, w, labelH,
                Text.literal(text), this.textRenderer);
        widget.alignLeft();
        this.addDrawableChild(widget);
        return y + labelH + 1;
    }

    private PlaceholderTextFieldWidget addTextFieldDyn(int x, int y, int w, int h, String placeholder) {
        PlaceholderTextFieldWidget field = new PlaceholderTextFieldWidget(this.textRenderer, x, y, w, h, Text.literal(""));
        field.setPlaceholder(Text.literal(placeholder));
        this.addDrawableChild(field);
        return field;
    }

    private ButtonWidget addButtonDyn(int x, int y, int w, int h, String text, ButtonWidget.PressAction action) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(text), action)
                .dimensions(x, y, w, h).build();
        this.addDrawableChild(btn);
        return btn;
    }

    private <T> CyclingButtonWidget<T> addCyclingDyn(int x, int y, int w, int h,
                                                      List<T> values, T initial, String key,
                                                      CyclingButtonWidget.UpdateCallback<T> callback) {
        CyclingButtonWidget<T> btn = CyclingButtonWidget.<T>builder(val -> Text.literal(val.toString()))
                .values(values)
                .initially(initial)
                .build(x, y, w, h, Text.literal(key), callback);
        this.addDrawableChild(btn);
        return btn;
    }

    private void appendLog(String line) {
        synchronized (logLines) {
            logLines.add(line);
            int w = this.textRenderer.getWidth(line);
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

    // ==================== 渲染 ====================

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFFF);

        // 状态文字更新
        if (statusText != null) {
            boolean running = service.isRunning();
            String status = running ? "运行中" : (service.isStartPending() ? "启动中..." : "已停止");
            int statusColor = running ? 0x55FF55 : (service.isStartPending() ? 0xFFFF55 : 0xFF5555);
            statusText.setMessage(Text.literal("状态: " + status));
            statusText.setTextColor(statusColor);
        }

        super.render(context, mouseX, mouseY, delta);

        // ===== 日志区域渲染 =====
        renderLogArea(context);

        // ===== 配置列表标题（绘制在列表控件上方，不重叠） =====
        if (configList != null) {
            int titleY = configList.getTopY() - LOG_TITLE_H - 1;
            context.fill(logX, titleY, logX + logW, titleY + LOG_TITLE_H, 0xFF1A1A1A);
            context.drawBorder(logX, titleY, logW, LOG_TITLE_H, 0xFF333333);
            context.drawTextWithShadow(this.textRenderer, "配置列表", logX + 4, titleY + 2, LABEL_COLOR);
        }
    }

    private void renderLogArea(net.minecraft.client.gui.DrawContext context) {
        // 背景
        context.fill(logX, logY, logX + logW, logY + logH, LOG_BG);
        context.drawBorder(logX, logY, logW, logH, 0xFF444444);

        // 标题栏（在日志区域内部顶部）
        context.fill(logX, logY, logX + logW, logY + LOG_TITLE_H, 0xFF1A1A1A);
        context.drawTextWithShadow(this.textRenderer, "运行日志", logX + 4, logY + 2, 0xAAAAAA);

        // 错误提示（标题栏右侧，红色闪烁）
        if (errorMessage != null && !errorMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - errorMessageTime;
            if (elapsed < 15000) {
                boolean blink = (elapsed / 500) % 2 == 0;
                int errColor = blink ? 0xFFFF5555 : 0xFFAA0000;
                String errLabel = "错误!";
                int labelW = this.textRenderer.getWidth(errLabel);
                context.drawTextWithShadow(this.textRenderer, errLabel, logX + logW - labelW - 4, logY + 2, errColor);
            } else {
                errorMessage = null;
            }
        }

        // 日志文本区域（标题栏下方）
        int textTop = logY + LOG_TITLE_H + 1;
        int textH = logH - LOG_TITLE_H - 2;
        int lineH = this.textRenderer.fontHeight + 1;
        int visibleLines = Math.max(1, textH / lineH);

        // 水平滚动范围限制
        int maxHScroll = Math.max(0, logMaxLineWidth - (logW - 8));
        if (logHScroll > maxHScroll) logHScroll = maxHScroll;
        if (logHScroll < 0) logHScroll = 0;

        // 启用裁剪，防止日志文本溢出区域
        context.enableScissor(logX, textTop, logX + logW, textTop + textH);
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
                    context.drawTextWithShadow(this.textRenderer, line, logX + 3 - logHScroll, drawY, color);
                    drawY += lineH;
                }
            }
        } finally {
            context.disableScissor();
        }

        // 垂直滚动条（右侧）
        int totalLines = logLines.size();
        if (totalLines > visibleLines) {
            int scrollbarX = logX + logW - SCROLLBAR_W - 1;
            int scrollbarY = textTop;
            int scrollbarH = textH;
            int thumbH = Math.max(8, (int) ((long) scrollbarH * visibleLines / totalLines));
            int thumbY = scrollbarY + (int) ((long) (scrollbarH - thumbH) * logVScroll / Math.max(1, totalLines - visibleLines));
            context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_W, scrollbarY + scrollbarH, 0xFF333333);
            context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, 0xFF666666);
        }

        // 水平滚动条（底部）
        if (logMaxLineWidth > logW - 8) {
            int hScrollbarY = logY + logH - SCROLLBAR_W - 1;
            int hScrollbarX = logX + 2;
            int hScrollbarW = logW - 4;
            int hThumbW = Math.max(20, (int) ((long) hScrollbarW * (logW - 8) / logMaxLineWidth));
            int hThumbX = hScrollbarX + (int) ((long) (hScrollbarW - hThumbW) * logHScroll / Math.max(1, maxHScroll));
            context.fill(hScrollbarX, hScrollbarY, hScrollbarX + hScrollbarW, hScrollbarY + SCROLLBAR_W, 0xFF333333);
            context.fill(hThumbX, hScrollbarY, hThumbX + hThumbW, hScrollbarY + SCROLLBAR_W, 0xFF666666);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // 日志区域滚动
        if (mouseX >= logX && mouseX <= logX + logW && mouseY >= logY && mouseY <= logY + logH) {
            int textTop = logY + LOG_TITLE_H + 1;
            int textH = logH - LOG_TITLE_H - 2;
            int lineH = this.textRenderer.fontHeight + 1;
            int visibleLines = Math.max(1, textH / lineH);
            int maxVScroll = Math.max(0, logLines.size() - visibleLines);

            if (hasShiftDown()) {
                // Shift + 滚轮 = 水平滚动
                int maxHScroll = Math.max(0, logMaxLineWidth - (logW - 8));
                logHScroll = Math.max(0, Math.min(maxHScroll, logHScroll - (int) (amount * 20)));
            } else {
                // 普通滚轮 = 垂直滚动（向上滚=查看更早日志，logVScroll增大）
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
        int lineH = this.textRenderer.fontHeight + 1;
        int visibleLines = Math.max(1, textH / lineH);
        int totalLines = logLines.size();

        // 垂直滚动条点击/拖动
        if (totalLines > visibleLines) {
            int scrollbarX = logX + logW - SCROLLBAR_W - 1;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_W
                    && mouseY >= textTop && mouseY <= textTop + textH) {
                draggingVScroll = true;
                updateVScrollFromMouse(mouseY, textTop, textH, visibleLines, totalLines);
                return true;
            }
        }

        // 水平滚动条点击/拖动
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
        int lineH = this.textRenderer.fontHeight + 1;
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
    public void close() {
        service.getLogManager().setUiCallback(null);
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
