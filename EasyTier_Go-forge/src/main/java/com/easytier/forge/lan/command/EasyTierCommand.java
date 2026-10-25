package com.easytier.forge.lan.command;

import com.easytier.forge.lan.EasyTierService;
import com.easytier.forge.lan.config.ConfigManager;
import com.easytier.forge.lan.config.FileSanitizer;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.process.EasyTierCliRunner;
import com.easytier.forge.lan.process.EasyTierProcessManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;

/**
 * /easytier 服务端指令
 * 权限等级 2（管理员/OP）
 *
 * 指令树:
 * /easytier start <network_name> <relay_server> [network_secret]
 * /easytier stop
 * /easytier status
 * /easytier peer
 * /easytier node
 * /easytier route
 * /easytier portforward add <tcp|udp> <bind_port> <virtual_ip> <mc_port>
 * /easytier portforward remove <tcp|udp> <bind_port>
 * /easytier portforward list
 * /easytier config list
 * /easytier config use <network_name>
 * /easytier config save
 * /easytier config delete <network_name>
 * /easytier autostart on [network_name]
 * /easytier autostart off
 * /easytier autostart status
 * /easytier reload
 *
 * 兼容旧形式: /easytier <network_name> <relay_server> [network_secret]
 */
public class EasyTierCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("easytier")
                        .requires(source -> source.hasPermission(2))

                        // ===== start =====
                        .then(Commands.literal("start")
                                .then(Commands.argument("network_name", StringArgumentType.word())
                                        .then(Commands.argument("relay_server", StringArgumentType.string())
                                                .executes(EasyTierCommand::startNoSecret)
                                                .then(Commands.argument("network_secret", StringArgumentType.string())
                                                        .executes(EasyTierCommand::startWithSecret)))))

                        // ===== stop =====
                        .then(Commands.literal("stop")
                                .executes(EasyTierCommand::stop))

                        // ===== status =====
                        .then(Commands.literal("status")
                                .executes(EasyTierCommand::status))

                        // ===== peer =====
                        .then(Commands.literal("peer")
                                .executes(EasyTierCommand::peer))

                        // ===== node =====
                        .then(Commands.literal("node")
                                .executes(EasyTierCommand::node))

                        // ===== route =====
                        .then(Commands.literal("route")
                                .executes(EasyTierCommand::route))

                        // ===== portforward =====
                        .then(Commands.literal("portforward")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("protocol", StringArgumentType.word())
                                                .then(Commands.argument("bind_port", IntegerArgumentType.integer(1, 65535))
                                                        .then(Commands.argument("virtual_ip", StringArgumentType.word())
                                                                .then(Commands.argument("mc_port", IntegerArgumentType.integer(1, 65535))
                                                                        .executes(EasyTierCommand::portForwardAdd))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("protocol", StringArgumentType.word())
                                                .then(Commands.argument("bind_port", IntegerArgumentType.integer(1, 65535))
                                                        .executes(EasyTierCommand::portForwardRemove))))
                                .then(Commands.literal("list")
                                        .executes(EasyTierCommand::portForwardList)))

                        // ===== config =====
                        .then(Commands.literal("config")
                                .then(Commands.literal("list")
                                        .executes(EasyTierCommand::configList))
                                .then(Commands.literal("use")
                                        .then(Commands.argument("network_name", StringArgumentType.word())
                                                .executes(EasyTierCommand::configUse)))
                                .then(Commands.literal("save")
                                        .executes(EasyTierCommand::configSave))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("network_name", StringArgumentType.word())
                                                .executes(EasyTierCommand::configDelete))))

                        // ===== reload =====
                        .then(Commands.literal("reload")
                                .executes(EasyTierCommand::reload))

                        // ===== autostart =====
                        .then(Commands.literal("autostart")
                                .executes(EasyTierCommand::autoStartStatus)
                                .then(Commands.literal("on")
                                        .executes(ctx -> doAutoStart(ctx, true, null))
                                        .then(Commands.argument("network_name", StringArgumentType.word())
                                                .executes(ctx -> doAutoStart(ctx, true,
                                                        StringArgumentType.getString(ctx, "network_name")))))
                                .then(Commands.literal("off")
                                        .executes(ctx -> doAutoStart(ctx, false, null)))
                                .then(Commands.literal("status")
                                        .executes(EasyTierCommand::autoStartStatus)))

                        // ===== 旧形式兼容: /easytier <name> <server> [secret] =====
                        .then(Commands.argument("network_name", StringArgumentType.word())
                                .then(Commands.argument("relay_server", StringArgumentType.string())
                                        .executes(EasyTierCommand::legacyStartNoSecret)
                                        .then(Commands.argument("network_secret", StringArgumentType.string())
                                                .executes(EasyTierCommand::legacyStartWithSecret))))

                        // 无参数显示用法
                        .executes(EasyTierCommand::showUsage)
        );
    }

    // ==================== start ====================

    private static int startNoSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                "");
    }

    private static int startWithSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"));
    }

    private static int legacyStartNoSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                "");
    }

    private static int legacyStartWithSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"));
    }

    private static int doStart(CommandContext<CommandSourceStack> ctx,
                               String name, String relay, String secret) {
        EasyTierService service = EasyTierService.getInstance();

        // Android 阻断
        if (service.isAndroid()) {
            ctx.getSource().sendFailure(Component.literal(
                    "EasyTier Forge LAN 不支持 Android 平台。请使用 EasyTier 官方 Android 应用建立虚拟网络，然后在 Minecraft 中直接连接虚拟 IP。"));
            return 0;
        }
        if (!service.isPlatformSupported()) {
            ctx.getSource().sendFailure(Component.literal("当前平台不支持 EasyTier"));
            return 0;
        }

        if (service.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("EasyTier 已在运行中，请先执行 /easytier stop"));
            return 0;
        }

        // 参数校验
        if (name == null || name.isEmpty() || name.length() > 32) {
            ctx.getSource().sendFailure(Component.literal("网络名称必须为 1-32 个字符，允许字母、数字、下划线、连字符"));
            return 0;
        }
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            ctx.getSource().sendFailure(Component.literal("网络名称只能包含字母、数字、下划线、连字符"));
            return 0;
        }
        if (relay == null || relay.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("中继服务器不能为空，格式 host:port 或 tcp://host:port"));
            return 0;
        }

        boolean ok = service.start(name, relay, secret, "et_forge", true);
        if (ok) {
            String fileName = FileSanitizer.sanitize(name) + ".toml";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "EasyTier 已启动: " + name + " (" + fileName + ")"), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("EasyTier 启动失败，请查看日志"));
            return 0;
        }
    }

    // ==================== stop ====================

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("EasyTier 未在运行"));
            return 0;
        }
        service.stop();
        ctx.getSource().sendSuccess(() -> Component.literal("EasyTier 已停止"), true);
        return 1;
    }

    // ==================== status ====================

    private static int status(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        EasyTierProcessManager pm = service.getProcessManager();

        if (!service.isRunning()) {
            ctx.getSource().sendSuccess(() -> Component.literal("EasyTier 未运行"), false);
            return 1;
        }

        TomlConfig cfg = pm.getCurrentConfig();
        String name = cfg != null && cfg.getNetworkName() != null ? cfg.getNetworkName() : "?";
        String fileName = cfg != null ? cfg.toFileName() : "?";
        String ip = service.getVirtualIp();

        StringBuilder sb = new StringBuilder();
        sb.append("运行中: ").append(name).append("（").append(fileName).append("）");
        if (ip != null) {
            sb.append("，虚拟 IP ").append(ip);
        } else {
            sb.append("，虚拟 IP 待分配");
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    // ==================== peer / node / route ====================

    private static int peer(CommandContext<CommandSourceStack> ctx) {
        return runCliAndFeedback(ctx, "peer");
    }

    private static int node(CommandContext<CommandSourceStack> ctx) {
        return runCliAndFeedback(ctx, "node");
    }

    private static int route(CommandContext<CommandSourceStack> ctx) {
        return runCliAndFeedback(ctx, "route");
    }

    private static int runCliAndFeedback(CommandContext<CommandSourceStack> ctx, String command) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("EasyTier 未在运行"));
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
        ctx.getSource().sendSuccess(() -> Component.literal(finalOutput), false);
        return 1;
    }

    // ==================== portforward ====================

    private static int portForwardAdd(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("EasyTier 未在运行"));
            return 0;
        }
        String protocol = StringArgumentType.getString(ctx, "protocol").toLowerCase();
        if (!protocol.equals("tcp") && !protocol.equals("udp")) {
            ctx.getSource().sendFailure(Component.literal("协议必须是 tcp 或 udp"));
            return 0;
        }
        int bindPort = IntegerArgumentType.getInteger(ctx, "bind_port");
        String virtualIp = StringArgumentType.getString(ctx, "virtual_ip");
        int mcPort = IntegerArgumentType.getInteger(ctx, "mc_port");

        boolean ok = service.getProcessManager().addPortForward(protocol, bindPort, virtualIp, mcPort);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "端口转发已添加: " + protocol + " 0.0.0.0:" + bindPort + " -> " + virtualIp + ":" + mcPort), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("端口转发添加失败"));
            return 0;
        }
    }

    private static int portForwardRemove(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("EasyTier 未在运行"));
            return 0;
        }
        String protocol = StringArgumentType.getString(ctx, "protocol").toLowerCase();
        int bindPort = IntegerArgumentType.getInteger(ctx, "bind_port");
        boolean ok = service.getProcessManager().removePortForward(protocol, bindPort);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "端口转发已移除: " + protocol + " 0.0.0.0:" + bindPort), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(
                    "端口转发移除失败: " + protocol + " 0.0.0.0:" + bindPort));
            return 0;
        }
    }

    private static int portForwardList(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        if (!service.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("EasyTier 未在运行"));
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
        ctx.getSource().sendSuccess(() -> Component.literal(finalOutput), false);
        return 1;
    }

    // ==================== config ====================

    private static int configList(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        Map<String, TomlConfig> all = service.scanAllConfigs();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("(无配置文件)"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("配置列表:\n");
        for (Map.Entry<String, TomlConfig> entry : all.entrySet()) {
            sb.append("  ").append(entry.getKey());
            TomlConfig cfg = entry.getValue();
            if (cfg.isCorrupted()) {
                sb.append(" [损坏]");
            } else {
                sb.append(" (").append(cfg.toFileName()).append(")");
            }
            sb.append("\n");
        }
        String finalOutput = sb.toString().trim();
        ctx.getSource().sendSuccess(() -> Component.literal(finalOutput), false);
        return 1;
    }

    private static int configUse(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        String name = StringArgumentType.getString(ctx, "network_name");

        if (!cm.configExists(name)) {
            ctx.getSource().sendFailure(Component.literal(
                    "配置不存在: " + FileSanitizer.sanitize(name) + ".toml"));
            return 0;
        }

        TomlConfig cfg = cm.loadConfig(name);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendFailure(Component.literal("配置已损坏"));
            return 0;
        }

        String actualName = cfg.getNetworkName() != null && !cfg.getNetworkName().isEmpty()
                ? cfg.getNetworkName() : name;
        cm.writeActive(new ConfigManager.ActiveConfig(actualName, true));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已设置活动配置: " + actualName
                        + "\n文件: " + cfg.toFileName()
                        + "\n中继: " + (cfg.getRelayServer() != null ? cfg.getRelayServer() : "?")
                        + "\n密码: " + (cfg.getNetworkSecret() != null && !cfg.getNetworkSecret().isEmpty() ? "已设置" : "无")), false);
        return 1;
    }

    private static int configSave(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        EasyTierProcessManager pm = service.getProcessManager();
        TomlConfig current = pm.getCurrentConfig();
        if (current == null) {
            ctx.getSource().sendFailure(Component.literal("当前没有运行中的配置"));
            return 0;
        }

        boolean ok = service.getConfigManager().saveConfig(current);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "配置已保存: " + current.toFileName()), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("保存失败"));
            return 0;
        }
    }

    private static int configDelete(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        String name = StringArgumentType.getString(ctx, "network_name");

        // 检查正在运行的配置
        if (service.isRunning() && service.getProcessManager().getCurrentConfig() != null) {
            TomlConfig running = service.getProcessManager().getCurrentConfig();
            if (name.equalsIgnoreCase(running.getNetworkName())) {
                ctx.getSource().sendFailure(Component.literal("正在运行的配置无法删除，请先停止"));
                return 0;
            }
        }

        boolean ok = cm.deleteConfig(name);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "已删除: " + FileSanitizer.sanitize(name) + ".toml"), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(
                    "配置不存在: " + FileSanitizer.sanitize(name) + ".toml"));
            return 0;
        }
    }

    // ==================== reload ====================

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        ConfigManager.ActiveConfig active = cm.readActive();
        if (active == null || active.network_name == null || !cm.configExists(active.network_name)) {
            // 兼容只用 /easytier autostart on 写入的世界级 active.json
            ConfigManager.ActiveConfig worldActive = cm.readWorldActive(currentWorldName(ctx));
            if (worldActive != null && worldActive.network_name != null) {
                active = worldActive;
            }
        }

        if (active == null || active.network_name == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "未设置活动配置，请先执行 /easytier config use <network_name>"));
            return 0;
        }

        if (!cm.configExists(active.network_name)) {
            ctx.getSource().sendFailure(Component.literal(
                    "配置文件不存在: " + FileSanitizer.sanitize(active.network_name)
                            + ".toml，请执行 /easytier config list 查看可用配置"));
            return 0;
        }

        TomlConfig cfg = cm.loadConfig(active.network_name);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendFailure(Component.literal("配置文件已损坏"));
            return 0;
        }

        // 停止当前实例（如果在运行）
        if (service.isRunning()) {
            service.stop();
        }

        String name = cfg.getNetworkName() != null && !cfg.getNetworkName().isEmpty()
                ? cfg.getNetworkName() : active.network_name;
        boolean ok = service.start(
                name,
                cfg.getRelayServer() != null ? cfg.getRelayServer() : "",
                cfg.getNetworkSecret() != null ? cfg.getNetworkSecret() : "",
                cfg.getDevName() != null ? cfg.getDevName() : "et_forge",
                cfg.isDhcp());

        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal("已重启: " + name), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("重启失败，请查看日志"));
            return 0;
        }
    }

    // ==================== autostart ====================

    private static int autoStartStatus(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        String worldName = currentWorldName(ctx);

        boolean globalEnabled = cm.isAutoStartOnWorldLoad();
        ConfigManager.ActiveConfig active = cm.readWorldActive(worldName);

        StringBuilder sb = new StringBuilder();
        sb.append("服务器自启动开关: ").append(globalEnabled ? "开启" : "关闭");
        sb.append("\n世界: ").append(worldName);
        if (active != null && active.network_name != null && !active.network_name.isEmpty()) {
            sb.append("\n世界配置: ").append(active.network_name);
            if (!cm.configExists(active.network_name)) {
                sb.append("（配置文件不存在）");
            }
            sb.append("\n世界自启动: ").append(active.auto_start ? "开启" : "关闭");
        } else {
            sb.append("\n世界配置: 未设置");
        }

        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int doAutoStart(CommandContext<CommandSourceStack> ctx,
                                   boolean enabled, String requestedName) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        String worldName = currentWorldName(ctx);

        if (!enabled) {
            boolean switchOk = cm.setAutoStartOnWorldLoad(false);
            boolean worldOk = true;
            ConfigManager.ActiveConfig existing = cm.readWorldActive(worldName);
            if (switchOk && existing != null && existing.network_name != null && !existing.network_name.isEmpty()) {
                worldOk = cm.writeWorldActive(worldName, new ConfigManager.ActiveConfig(existing.network_name, false));
            }
            if (switchOk && worldOk) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "已关闭服务器世界自启动（世界: " + worldName + "）"), true);
                return 1;
            } else {
                ctx.getSource().sendFailure(Component.literal("关闭自启动失败，请检查 server.toml 写入权限"));
                return 0;
            }
        }

        String name = requestedName;
        if (name == null || name.isEmpty()) {
            TomlConfig current = service.getProcessManager().getCurrentConfig();
            if (current != null && current.getNetworkName() != null && !current.getNetworkName().isEmpty()) {
                name = current.getNetworkName();
            } else if (service.getCurrentNetworkName() != null && !service.getCurrentNetworkName().isEmpty()) {
                name = service.getCurrentNetworkName();
            }
        }
        if (name == null || name.isEmpty()) {
            List<String> configs = cm.listNetworkNames();
            if (configs.size() == 1) {
                name = configs.get(0);
            } else {
                String available = configs.isEmpty() ? "无" : String.join(", ", configs);
                ctx.getSource().sendFailure(Component.literal(
                        "请指定网络配置: /easytier autostart on <网络名称>（可用配置: " + available + "）"));
                return 0;
            }
        }

        if (!cm.configExists(name)) {
            ctx.getSource().sendFailure(Component.literal(
                    "配置不存在: " + FileSanitizer.sanitize(name) + ".toml"));
            return 0;
        }
        TomlConfig cfg = cm.loadConfig(name);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendFailure(Component.literal("配置已损坏: " + FileSanitizer.sanitize(name) + ".toml"));
            return 0;
        }
        if (cfg.getNetworkName() != null && !cfg.getNetworkName().isEmpty()) {
            name = cfg.getNetworkName();
        }

        boolean switchOk = cm.setAutoStartOnWorldLoad(true);
        boolean worldOk = switchOk && cm.writeWorldActive(worldName, new ConfigManager.ActiveConfig(name, true));
        if (switchOk && worldOk) {
            final String startedName = name;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "已开启服务器世界自启动: " + startedName + "（世界: " + worldName + "）"), true);
            return 1;
        } else {
            if (switchOk) {
                cm.setAutoStartOnWorldLoad(false);
            }
            ctx.getSource().sendFailure(Component.literal("开启自启动失败，请检查配置目录写入权限"));
            return 0;
        }
    }

    private static String currentWorldName(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) {
            return "world";
        }
        String name = server.getWorldData().getLevelName();
        return name != null && !name.isEmpty() ? name : "world";
    }

    // ==================== usage ====================

    private static int showUsage(CommandContext<CommandSourceStack> ctx) {
        String usage = """
                EasyTier 命令用法:
                /easytier start <网络名称> <中继服务器> [密码]  - 启动 EasyTier
                /easytier stop                                  - 停止 EasyTier
                /easytier status                                - 查看状态
                /easytier peer                                  - 查看节点列表
                /easytier node                                  - 查看本节点信息
                /easytier route                                 - 查看路由表
                /easytier portforward add <tcp|udp> <端口> <虚拟IP> <MC端口>
                /easytier portforward remove <tcp|udp> <端口>
                /easytier portforward list
                /easytier config list
                /easytier config use <网络名称>
                /easytier config save
                /easytier config delete <网络名称>
                /easytier autostart on [网络名称]
                /easytier autostart off
                /easytier autostart status
                /easytier reload
                兼容: /easytier <网络名称> <中继服务器> [密码]""";
        ctx.getSource().sendSuccess(() -> Component.literal(usage), false);
        return 1;
    }
}
