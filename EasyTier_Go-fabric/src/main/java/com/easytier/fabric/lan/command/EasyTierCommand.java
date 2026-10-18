package com.easytier.fabric.lan.command;

import com.easytier.fabric.lan.EasyTierService;
import com.easytier.fabric.lan.config.ConfigManager;
import com.easytier.fabric.lan.config.TomlConfig;
import com.easytier.fabric.lan.config.Version;
import com.easytier.fabric.lan.process.EasyTierCliRunner;
import com.easytier.fabric.lan.process.EasyTierProcessManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

/**
 * /easytier 服务端指令
 * 权限等级 2（管理员/OP）
 *
 * 指令树:
 * /easytier start <network_name> <relay_server> [network_secret] [version]
 * /easytier stop
 * /easytier status
 * /easytier peer
 * /easytier node
 * /easytier route
 * /easytier portforward add <tcp|udp> <bind_port> <virtual_ip> <mc_port>
 * /easytier portforward remove <tcp|udp> <bind_port>
 * /easytier portforward list
 * /easytier config list
 * /easytier config use <network_name> [version]
 * /easytier config save [version]
 * /easytier config delete <network_name> [version]
 * /easytier reload
 *
 * 兼容旧形式: /easytier <network_name> <relay_server> [network_secret]
 */
public class EasyTierCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("easytier")
                        .requires(source -> source.hasPermissionLevel(2))

                        // ===== start =====
                        .then(CommandManager.literal("start")
                                .then(CommandManager.argument("network_name", StringArgumentType.word())
                                        .then(CommandManager.argument("relay_server", StringArgumentType.string())
                                                .executes(EasyTierCommand::startNoSecret)
                                                .then(CommandManager.argument("network_secret", StringArgumentType.string())
                                                        .executes(EasyTierCommand::startWithSecret)
                                                        .then(CommandManager.argument("version", StringArgumentType.word())
                                                                .executes(EasyTierCommand::startWithVersion))))))

                        // ===== stop =====
                        .then(CommandManager.literal("stop")
                                .executes(EasyTierCommand::stop))

                        // ===== status =====
                        .then(CommandManager.literal("status")
                                .executes(EasyTierCommand::status))

                        // ===== peer =====
                        .then(CommandManager.literal("peer")
                                .executes(EasyTierCommand::peer))

                        // ===== node =====
                        .then(CommandManager.literal("node")
                                .executes(EasyTierCommand::node))

                        // ===== route =====
                        .then(CommandManager.literal("route")
                                .executes(EasyTierCommand::route))

                        // ===== portforward =====
                        .then(CommandManager.literal("portforward")
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("protocol", StringArgumentType.word())
                                                .then(CommandManager.argument("bind_port", IntegerArgumentType.integer(1, 65535))
                                                        .then(CommandManager.argument("virtual_ip", StringArgumentType.word())
                                                                .then(CommandManager.argument("mc_port", IntegerArgumentType.integer(1, 65535))
                                                                        .executes(EasyTierCommand::portForwardAdd))))))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("protocol", StringArgumentType.word())
                                                .then(CommandManager.argument("bind_port", IntegerArgumentType.integer(1, 65535))
                                                        .executes(EasyTierCommand::portForwardRemove))))
                                .then(CommandManager.literal("list")
                                        .executes(EasyTierCommand::portForwardList)))

                        // ===== config =====
                        .then(CommandManager.literal("config")
                                .then(CommandManager.literal("list")
                                        .executes(EasyTierCommand::configList))
                                .then(CommandManager.literal("use")
                                        .then(CommandManager.argument("network_name", StringArgumentType.word())
                                                .executes(EasyTierCommand::configUseNoVersion)
                                                .then(CommandManager.argument("version", StringArgumentType.word())
                                                        .executes(EasyTierCommand::configUseWithVersion))))
                                .then(CommandManager.literal("save")
                                        .executes(EasyTierCommand::configSaveNoVersion)
                                        .then(CommandManager.argument("version", StringArgumentType.word())
                                                .executes(EasyTierCommand::configSaveWithVersion)))
                                .then(CommandManager.literal("delete")
                                        .then(CommandManager.argument("network_name", StringArgumentType.word())
                                                .executes(EasyTierCommand::configDeleteNoVersion)
                                                .then(CommandManager.argument("version", StringArgumentType.word())
                                                        .executes(EasyTierCommand::configDeleteWithVersion)))))

                        // ===== reload =====
                        .then(CommandManager.literal("reload")
                                .executes(EasyTierCommand::reload))

                        // ===== 旧形式兼容: /easytier <name> <server> [secret] =====
                        .then(CommandManager.argument("network_name", StringArgumentType.word())
                                .then(CommandManager.argument("relay_server", StringArgumentType.string())
                                        .executes(EasyTierCommand::legacyStartNoSecret)
                                        .then(CommandManager.argument("network_secret", StringArgumentType.string())
                                                .executes(EasyTierCommand::legacyStartWithSecret))))

                        // 无参数显示用法
                        .executes(EasyTierCommand::showUsage)
        );
    }

    // ==================== start ====================

    private static int startNoSecret(CommandContext<ServerCommandSource> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                "", null);
    }

    private static int startWithSecret(CommandContext<ServerCommandSource> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"), null);
    }

    private static int startWithVersion(CommandContext<ServerCommandSource> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"),
                StringArgumentType.getString(ctx, "version"));
    }

    private static int legacyStartNoSecret(CommandContext<ServerCommandSource> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                "", null);
    }

    private static int legacyStartWithSecret(CommandContext<ServerCommandSource> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"), null);
    }

    private static int doStart(CommandContext<ServerCommandSource> ctx,
                               String name, String relay, String secret, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();

        // Android 阻断
        if (service.isAndroid()) {
            ctx.getSource().sendError(Text.literal(
                    "EasyTier Fabric LAN 不支持 Android 平台。请使用 EasyTier 官方 Android 应用建立虚拟网络，然后在 Minecraft 中直接连接虚拟 IP。"));
            return 0;
        }
        if (!service.isPlatformSupported()) {
            ctx.getSource().sendError(Text.literal("当前平台不支持 EasyTier"));
            return 0;
        }

        if (service.isRunning()) {
            ctx.getSource().sendError(Text.literal("EasyTier 已在运行中，请先执行 /easytier stop"));
            return 0;
        }

        // 参数校验
        if (name == null || name.isEmpty() || name.length() > 32) {
            ctx.getSource().sendError(Text.literal("网络名称必须为 1-32 个字符，允许字母、数字、下划线、连字符"));
            return 0;
        }
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            ctx.getSource().sendError(Text.literal("网络名称只能包含字母、数字、下划线、连字符"));
            return 0;
        }
        if (relay == null || relay.isEmpty()) {
            ctx.getSource().sendError(Text.literal("中继服务器不能为空，格式 host:port 或 tcp://host:port"));
            return 0;
        }

        // 确定版本
        Version version;
        if (versionStr != null) {
            version = Version.parse(versionStr);
            if (version == null) {
                ctx.getSource().sendError(Text.literal("版本格式错误，应为 v1.0 格式"));
                return 0;
            }
        } else {
            // 扫描取最大版本，不存在则 v1.0
            TomlConfig latest = service.getLatestVersion(name);
            version = (latest != null && latest.getVersion() != null)
                    ? latest.getVersion() : new Version(1, 0);
        }

        boolean ok = service.start(name, version, relay, secret, "et_fabric", true);
        if (ok) {
            String fileName = com.easytier.fabric.lan.config.FileSanitizer.sanitize(name) + "_" + version + ".toml";
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "EasyTier 已启动: " + name + " @ " + version + " (" + fileName + ")"), true);
            return 1;
        } else {
            ctx.getSource().sendError(Text.literal("EasyTier 启动失败，请查看日志"));
            return 0;
        }
    }

    // ==================== stop ====================

    private static int stop(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendError(Text.literal("EasyTier 未在运行"));
            return 0;
        }
        service.stop();
        ctx.getSource().sendFeedback(() -> Text.literal("EasyTier 已停止"), true);
        return 1;
    }

    // ==================== status ====================

    private static int status(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        EasyTierProcessManager pm = service.getProcessManager();

        if (!service.isRunning()) {
            ctx.getSource().sendFeedback(() -> Text.literal("EasyTier 未运行"), false);
            return 1;
        }

        TomlConfig cfg = pm.getCurrentConfig();
        String name = cfg != null && cfg.getNetworkName() != null ? cfg.getNetworkName() : "?";
        String ver = cfg != null && cfg.getVersion() != null ? cfg.getVersion().toString() : "?";
        String fileName = cfg != null ? cfg.toFileName() : "?";
        String ip = service.getVirtualIp();
        long pid = -1;

        StringBuilder sb = new StringBuilder();
        sb.append("运行中: ").append(name).append(" @ ").append(ver)
                .append("（").append(fileName).append("）");
        if (ip != null) {
            sb.append("，虚拟 IP ").append(ip);
        } else {
            sb.append("，虚拟 IP 待分配");
        }

        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
        return 1;
    }

    // ==================== peer / node / route ====================

    private static int peer(CommandContext<ServerCommandSource> ctx) {
        return runCliAndFeedback(ctx, "peer");
    }

    private static int node(CommandContext<ServerCommandSource> ctx) {
        return runCliAndFeedback(ctx, "node");
    }

    private static int route(CommandContext<ServerCommandSource> ctx) {
        return runCliAndFeedback(ctx, "route");
    }

    private static int runCliAndFeedback(CommandContext<ServerCommandSource> ctx, String command) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendError(Text.literal("EasyTier 未在运行"));
            return 0;
        }
        EasyTierCliRunner cli = service.getCliRunner();
        String output;
        switch (command) {
            case "peer": output = cli.peer(); break;
            case "node": output = cli.node(); break;
            case "route": output = cli.route(); break;
            default: output = "未知命令";
        }
        String finalOutput = output.isEmpty() ? "(无输出)" : output;
        ctx.getSource().sendFeedback(() -> Text.literal(finalOutput), false);
        return 1;
    }

    // ==================== portforward ====================

    private static int portForwardAdd(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendError(Text.literal("EasyTier 未在运行"));
            return 0;
        }
        String protocol = StringArgumentType.getString(ctx, "protocol").toLowerCase();
        if (!protocol.equals("tcp") && !protocol.equals("udp")) {
            ctx.getSource().sendError(Text.literal("协议必须是 tcp 或 udp"));
            return 0;
        }
        int bindPort = IntegerArgumentType.getInteger(ctx, "bind_port");
        String virtualIp = StringArgumentType.getString(ctx, "virtual_ip");
        int mcPort = IntegerArgumentType.getInteger(ctx, "mc_port");

        boolean ok = service.getProcessManager().addPortForward(protocol, bindPort, virtualIp, mcPort);
        if (ok) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "端口转发已添加: " + protocol + " 0.0.0.0:" + bindPort + " -> " + virtualIp + ":" + mcPort), true);
            return 1;
        } else {
            ctx.getSource().sendError(Text.literal("端口转发添加失败"));
            return 0;
        }
    }

    private static int portForwardRemove(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendError(Text.literal("EasyTier 未在运行"));
            return 0;
        }
        String protocol = StringArgumentType.getString(ctx, "protocol").toLowerCase();
        int bindPort = IntegerArgumentType.getInteger(ctx, "bind_port");
        service.getProcessManager().removePortForward(protocol, bindPort);
        ctx.getSource().sendFeedback(() -> Text.literal(
                "端口转发已移除: " + protocol + " 0.0.0.0:" + bindPort), true);
        return 1;
    }

    private static int portForwardList(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendError(Text.literal("EasyTier 未在运行"));
            return 0;
        }
        String output = service.getCliRunner().portForwardList();
        List<EasyTierProcessManager.PortForwardRule> rules = service.getProcessManager().getActivePortForwards();
        StringBuilder sb = new StringBuilder();
        if (!output.isEmpty() && !output.startsWith("错误")) {
            sb.append(output);
        }
        if (!rules.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("已添加的转发规则:\n");
            for (EasyTierProcessManager.PortForwardRule r : rules) {
                sb.append("  ").append(r.protocol).append(" 0.0.0.0:").append(r.bindPort)
                        .append(" -> ").append(r.virtualIp).append(":").append(r.mcPort).append("\n");
            }
        }
        if (sb.length() == 0) {
            sb.append("(无端口转发规则)");
        }
        String finalOutput = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(finalOutput), false);
        return 1;
    }

    // ==================== config ====================

    private static int configList(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        Map<String, List<TomlConfig>> all = service.scanAllConfigs();
        if (all.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("(无配置文件)"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("配置列表:\n");
        for (Map.Entry<String, List<TomlConfig>> entry : all.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ");
            List<TomlConfig> versions = entry.getValue();
            for (int i = 0; i < versions.size(); i++) {
                TomlConfig cfg = versions.get(i);
                if (cfg.isCorrupted()) {
                    sb.append("[损坏]");
                } else {
                    sb.append(cfg.getVersion() != null ? cfg.getVersion().toString() : "v?");
                }
                if (i < versions.size() - 1) sb.append(", ");
            }
            if (!versions.isEmpty()) {
                TomlConfig latest = versions.get(versions.size() - 1);
                if (!latest.isCorrupted() && latest.getVersion() != null) {
                    sb.append(" (latest=").append(latest.getVersion()).append(")");
                }
            }
            sb.append("\n");
        }
        String finalOutput = sb.toString().trim();
        ctx.getSource().sendFeedback(() -> Text.literal(finalOutput), false);
        return 1;
    }

    private static int configUseNoVersion(CommandContext<ServerCommandSource> ctx) {
        return doConfigUse(ctx, StringArgumentType.getString(ctx, "network_name"), null);
    }

    private static int configUseWithVersion(CommandContext<ServerCommandSource> ctx) {
        return doConfigUse(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "version"));
    }

    private static int doConfigUse(CommandContext<ServerCommandSource> ctx, String name, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();

        Version version;
        if (versionStr != null) {
            version = Version.parse(versionStr);
            if (version == null) {
                ctx.getSource().sendError(Text.literal("版本格式错误"));
                return 0;
            }
        } else {
            TomlConfig latest = cm.getLatestVersion(name);
            if (latest == null || latest.getVersion() == null) {
                ctx.getSource().sendError(Text.literal("未找到网络配置: " + name));
                return 0;
            }
            version = latest.getVersion();
        }

        if (!cm.configExists(name, version)) {
            ctx.getSource().sendError(Text.literal("配置不存在: " + name + "_" + version + ".toml"));
            return 0;
        }

        TomlConfig cfg = cm.loadConfig(name, version);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendError(Text.literal("配置已损坏"));
            return 0;
        }

        cm.writeActive(new ConfigManager.ActiveConfig(name, version.toString(), true));
        ctx.getSource().sendFeedback(() -> Text.literal(
                "已设置活动配置: " + name + " @ " + version
                        + "\n中继: " + (cfg.getRelayServer() != null ? cfg.getRelayServer() : "?")
                        + "\n密码: " + (cfg.getNetworkSecret() != null && !cfg.getNetworkSecret().isEmpty() ? "已设置" : "无")), false);
        return 1;
    }

    private static int configSaveNoVersion(CommandContext<ServerCommandSource> ctx) {
        return doConfigSave(ctx, null);
    }

    private static int configSaveWithVersion(CommandContext<ServerCommandSource> ctx) {
        return doConfigSave(ctx, StringArgumentType.getString(ctx, "version"));
    }

    private static int doConfigSave(CommandContext<ServerCommandSource> ctx, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();
        EasyTierProcessManager pm = service.getProcessManager();
        TomlConfig current = pm.getCurrentConfig();
        if (current == null) {
            ctx.getSource().sendError(Text.literal("当前没有运行中的配置"));
            return 0;
        }

        if (versionStr != null) {
            Version v = Version.parse(versionStr);
            if (v == null) {
                ctx.getSource().sendError(Text.literal("版本格式错误"));
                return 0;
            }
            current.setVersion(v);
        } else {
            // 次版本 +1
            Version next = service.getConfigManager().getNextVersion(current.getNetworkName());
            current.setVersion(next);
        }

        boolean ok = service.getConfigManager().saveConfig(current);
        if (ok) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "配置已保存: " + current.toFileName()), true);
            return 1;
        } else {
            ctx.getSource().sendError(Text.literal("保存失败"));
            return 0;
        }
    }

    private static int configDeleteNoVersion(CommandContext<ServerCommandSource> ctx) {
        return doConfigDelete(ctx, StringArgumentType.getString(ctx, "network_name"), null);
    }

    private static int configDeleteWithVersion(CommandContext<ServerCommandSource> ctx) {
        return doConfigDelete(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "version"));
    }

    private static int doConfigDelete(CommandContext<ServerCommandSource> ctx, String name, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();

        // 检查正在运行的版本
        if (service.isRunning() && service.getProcessManager().getCurrentConfig() != null) {
            TomlConfig running = service.getProcessManager().getCurrentConfig();
            if (name.equals(running.getNetworkName())) {
                if (versionStr == null
                        || (running.getVersion() != null && running.getVersion().toString().equals(versionStr))) {
                    ctx.getSource().sendError(Text.literal("正在运行的配置无法删除，请先停止"));
                    return 0;
                }
            }
        }

        if (versionStr == null) {
            // 删除整个网络配置
            int count = cm.deleteNetwork(name);
            if (count > 0) {
                ctx.getSource().sendFeedback(() -> Text.literal(
                        "已删除网络 " + name + " 的 " + count + " 个版本"), true);
                return 1;
            } else {
                ctx.getSource().sendError(Text.literal("未找到网络配置: " + name));
                return 0;
            }
        } else {
            Version v = Version.parse(versionStr);
            if (v == null) {
                ctx.getSource().sendError(Text.literal("版本格式错误"));
                return 0;
            }
            boolean ok = cm.deleteConfig(name, v);
            if (ok) {
                ctx.getSource().sendFeedback(() -> Text.literal(
                        "已删除: " + name + "_" + v + ".toml"), true);
                return 1;
            } else {
                ctx.getSource().sendError(Text.literal("配置不存在: " + name + "_" + v + ".toml"));
                return 0;
            }
        }
    }

    // ==================== reload ====================

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        ConfigManager.ActiveConfig active = cm.readActive();

        if (active == null || active.network_name == null) {
            ctx.getSource().sendError(Text.literal(
                    "未设置活动配置，请先执行 /easytier config use <network_name> [version]"));
            return 0;
        }

        Version version = active.version != null ? Version.parse(active.version) : null;
        if (version == null) {
            TomlConfig latest = cm.getLatestVersion(active.network_name);
            if (latest != null && latest.getVersion() != null) {
                version = latest.getVersion();
            } else {
                version = new Version(1, 0);
            }
        }

        if (!cm.configExists(active.network_name, version)) {
            ctx.getSource().sendError(Text.literal(
                    "配置文件不存在: " + active.network_name + "_" + version + ".toml，请执行 /easytier config list 查看可用配置"));
            return 0;
        }

        TomlConfig cfg = cm.loadConfig(active.network_name, version);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendError(Text.literal("配置文件已损坏"));
            return 0;
        }

        // 停止当前实例（如果在运行）
        if (service.isRunning()) {
            service.stop();
        }

        boolean ok = service.start(
                cfg.getNetworkName(), version,
                cfg.getRelayServer() != null ? cfg.getRelayServer() : "",
                cfg.getNetworkSecret() != null ? cfg.getNetworkSecret() : "",
                cfg.getDevName() != null ? cfg.getDevName() : "et_fabric",
                cfg.isDhcp());

        if (ok) {
            final String restartName = active.network_name;
            final Version restartVersion = version;
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "已重启: " + restartName + " @ " + restartVersion), true);
            return 1;
        } else {
            ctx.getSource().sendError(Text.literal("重启失败，请查看日志"));
            return 0;
        }
    }

    // ==================== usage ====================

    private static int showUsage(CommandContext<ServerCommandSource> ctx) {
        String usage = """
                EasyTier 命令用法:
                /easytier start <网络名称> <中继服务器> [密码] [版本]  - 启动 EasyTier
                /easytier stop                                      - 停止 EasyTier
                /easytier status                                    - 查看状态
                /easytier peer                                      - 查看节点列表
                /easytier node                                      - 查看本节点信息
                /easytier route                                     - 查看路由表
                /easytier portforward add <tcp|udp> <端口> <虚拟IP> <MC端口>
                /easytier portforward remove <tcp|udp> <端口>
                /easytier portforward list
                /easytier config list
                /easytier config use <网络名称> [版本]
                /easytier config save [版本]
                /easytier config delete <网络名称> [版本]
                /easytier reload
                兼容: /easytier <网络名称> <中继服务器> [密码]""";
        ctx.getSource().sendFeedback(() -> Text.literal(usage), false);
        return 1;
    }
}
