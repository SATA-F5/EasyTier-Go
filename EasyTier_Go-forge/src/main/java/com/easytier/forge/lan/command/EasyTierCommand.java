package com.easytier.forge.lan.command;

import com.easytier.forge.lan.EasyTierService;
import com.easytier.forge.lan.config.ConfigManager;
import com.easytier.forge.lan.config.TomlConfig;
import com.easytier.forge.lan.config.Version;
import com.easytier.forge.lan.process.EasyTierCliRunner;
import com.easytier.forge.lan.process.EasyTierProcessManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * /easytier 服务端指令
 */
public class EasyTierCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("easytier")
                        .requires(source -> source.hasPermission(2))

                        .then(Commands.literal("start")
                                .then(Commands.argument("network_name", StringArgumentType.word())
                                        .then(Commands.argument("relay_server", StringArgumentType.string())
                                                .executes(EasyTierCommand::startNoSecret)
                                                .then(Commands.argument("network_secret", StringArgumentType.string())
                                                        .executes(EasyTierCommand::startWithSecret)
                                                        .then(Commands.argument("version", StringArgumentType.word())
                                                                .executes(EasyTierCommand::startWithVersion))))))

                        .then(Commands.literal("stop")
                                .executes(EasyTierCommand::stop))

                        .then(Commands.literal("status")
                                .executes(EasyTierCommand::status))

                        .then(Commands.literal("peer")
                                .executes(EasyTierCommand::peer))

                        .then(Commands.literal("node")
                                .executes(EasyTierCommand::node))

                        .then(Commands.literal("route")
                                .executes(EasyTierCommand::route))

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

                        .then(Commands.literal("config")
                                .then(Commands.literal("list")
                                        .executes(EasyTierCommand::configList))
                                .then(Commands.literal("use")
                                        .then(Commands.argument("network_name", StringArgumentType.word())
                                                .executes(EasyTierCommand::configUseNoVersion)
                                                .then(Commands.argument("version", StringArgumentType.word())
                                                        .executes(EasyTierCommand::configUseWithVersion))))
                                .then(Commands.literal("save")
                                        .executes(EasyTierCommand::configSaveNoVersion)
                                        .then(Commands.argument("version", StringArgumentType.word())
                                                .executes(EasyTierCommand::configSaveWithVersion)))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("network_name", StringArgumentType.word())
                                                .executes(EasyTierCommand::configDeleteNoVersion)
                                                .then(Commands.argument("version", StringArgumentType.word())
                                                        .executes(EasyTierCommand::configDeleteWithVersion)))))

                        .then(Commands.literal("reload")
                                .executes(EasyTierCommand::reload))

                        .then(Commands.argument("network_name", StringArgumentType.word())
                                .then(Commands.argument("relay_server", StringArgumentType.string())
                                        .executes(EasyTierCommand::legacyStartNoSecret)
                                        .then(Commands.argument("network_secret", StringArgumentType.string())
                                                .executes(EasyTierCommand::legacyStartWithSecret))))

                        .executes(EasyTierCommand::showUsage)
        );
    }

    private static int startNoSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                "", null);
    }

    private static int startWithSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"), null);
    }

    private static int startWithVersion(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"),
                StringArgumentType.getString(ctx, "version"));
    }

    private static int legacyStartNoSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                "", null);
    }

    private static int legacyStartWithSecret(CommandContext<CommandSourceStack> ctx) {
        return doStart(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "relay_server"),
                StringArgumentType.getString(ctx, "network_secret"), null);
    }

    private static int doStart(CommandContext<CommandSourceStack> ctx,
                               String name, String relay, String secret, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();

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

        Version version;
        if (versionStr != null) {
            version = Version.parse(versionStr);
            if (version == null) {
                ctx.getSource().sendFailure(Component.literal("版本格式错误，应为 v1.0 格式"));
                return 0;
            }
        } else {
            TomlConfig latest = service.getLatestVersion(name);
            version = (latest != null && latest.getVersion() != null)
                    ? latest.getVersion() : new Version(1, 0);
        }

        boolean ok = service.start(name, version, relay, secret, "et_forge", true);
        if (ok) {
            String fileName = com.easytier.forge.lan.config.FileSanitizer.sanitize(name) + "_" + version + ".toml";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "EasyTier 已启动: " + name + " @ " + version + " (" + fileName + ")"), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("EasyTier 启动失败，请查看日志"));
            return 0;
        }
    }

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

    private static int status(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        EasyTierProcessManager pm = service.getProcessManager();

        if (!service.isRunning()) {
            ctx.getSource().sendSuccess(() -> Component.literal("EasyTier 未运行"), false);
            return 1;
        }

        TomlConfig cfg = pm.getCurrentConfig();
        String name = cfg != null && cfg.getNetworkName() != null ? cfg.getNetworkName() : "?";
        String ver = cfg != null && cfg.getVersion() != null ? cfg.getVersion().toString() : "?";
        String fileName = cfg != null ? cfg.toFileName() : "?";
        String ip = service.getVirtualIp();

        StringBuilder sb = new StringBuilder();
        sb.append("运行中: ").append(name).append(" @ ").append(ver)
                .append("（").append(fileName).append("）");
        if (ip != null) {
            sb.append("，虚拟 IP ").append(ip);
        } else {
            sb.append("，虚拟 IP 待分配");
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

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
        service.getProcessManager().removePortForward(protocol, bindPort);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "端口转发已移除: " + protocol + " 0.0.0.0:" + bindPort), true);
        return 1;
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

    private static int configList(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        Map<String, List<TomlConfig>> all = service.scanAllConfigs();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("(无配置文件)"), false);
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
        ctx.getSource().sendSuccess(() -> Component.literal(finalOutput), false);
        return 1;
    }

    private static int configUseNoVersion(CommandContext<CommandSourceStack> ctx) {
        return doConfigUse(ctx, StringArgumentType.getString(ctx, "network_name"), null);
    }

    private static int configUseWithVersion(CommandContext<CommandSourceStack> ctx) {
        return doConfigUse(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "version"));
    }

    private static int doConfigUse(CommandContext<CommandSourceStack> ctx, String name, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();

        Version version;
        if (versionStr != null) {
            version = Version.parse(versionStr);
            if (version == null) {
                ctx.getSource().sendFailure(Component.literal("版本格式错误"));
                return 0;
            }
        } else {
            TomlConfig latest = cm.getLatestVersion(name);
            if (latest == null || latest.getVersion() == null) {
                ctx.getSource().sendFailure(Component.literal("未找到网络配置: " + name));
                return 0;
            }
            version = latest.getVersion();
        }

        if (!cm.configExists(name, version)) {
            ctx.getSource().sendFailure(Component.literal("配置不存在: " + name + "_" + version + ".toml"));
            return 0;
        }

        TomlConfig cfg = cm.loadConfig(name, version);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendFailure(Component.literal("配置已损坏"));
            return 0;
        }

        cm.writeActive(new ConfigManager.ActiveConfig(name, version.toString(), true));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已设置活动配置: " + name + " @ " + version
                        + "\n中继: " + (cfg.getRelayServer() != null ? cfg.getRelayServer() : "?")
                        + "\n密码: " + (cfg.getNetworkSecret() != null && !cfg.getNetworkSecret().isEmpty() ? "已设置" : "无")), false);
        return 1;
    }

    private static int configSaveNoVersion(CommandContext<CommandSourceStack> ctx) {
        return doConfigSave(ctx, null);
    }

    private static int configSaveWithVersion(CommandContext<CommandSourceStack> ctx) {
        return doConfigSave(ctx, StringArgumentType.getString(ctx, "version"));
    }

    private static int doConfigSave(CommandContext<CommandSourceStack> ctx, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();
        EasyTierProcessManager pm = service.getProcessManager();
        TomlConfig current = pm.getCurrentConfig();
        if (current == null) {
            ctx.getSource().sendFailure(Component.literal("当前没有运行中的配置"));
            return 0;
        }

        if (versionStr != null) {
            Version v = Version.parse(versionStr);
            if (v == null) {
                ctx.getSource().sendFailure(Component.literal("版本格式错误"));
                return 0;
            }
            current.setVersion(v);
        } else {
            Version next = service.getConfigManager().getNextVersion(current.getNetworkName());
            current.setVersion(next);
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

    private static int configDeleteNoVersion(CommandContext<CommandSourceStack> ctx) {
        return doConfigDelete(ctx, StringArgumentType.getString(ctx, "network_name"), null);
    }

    private static int configDeleteWithVersion(CommandContext<CommandSourceStack> ctx) {
        return doConfigDelete(ctx,
                StringArgumentType.getString(ctx, "network_name"),
                StringArgumentType.getString(ctx, "version"));
    }

    private static int doConfigDelete(CommandContext<CommandSourceStack> ctx, String name, String versionStr) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();

        if (service.isRunning() && service.getProcessManager().getCurrentConfig() != null) {
            TomlConfig running = service.getProcessManager().getCurrentConfig();
            if (name.equals(running.getNetworkName())) {
                if (versionStr == null
                        || (running.getVersion() != null && running.getVersion().toString().equals(versionStr))) {
                    ctx.getSource().sendFailure(Component.literal("正在运行的配置无法删除，请先停止"));
                    return 0;
                }
            }
        }

        if (versionStr == null) {
            int count = cm.deleteNetwork(name);
            if (count > 0) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "已删除网络 " + name + " 的 " + count + " 个版本"), true);
                return 1;
            } else {
                ctx.getSource().sendFailure(Component.literal("未找到网络配置: " + name));
                return 0;
            }
        } else {
            Version v = Version.parse(versionStr);
            if (v == null) {
                ctx.getSource().sendFailure(Component.literal("版本格式错误"));
                return 0;
            }
            boolean ok = cm.deleteConfig(name, v);
            if (ok) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "已删除: " + name + "_" + v + ".toml"), true);
                return 1;
            } else {
                ctx.getSource().sendFailure(Component.literal("配置不存在: " + name + "_" + v + ".toml"));
                return 0;
            }
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        EasyTierService service = EasyTierService.getInstance();
        ConfigManager cm = service.getConfigManager();
        ConfigManager.ActiveConfig active = cm.readActive();

        if (active == null || active.network_name == null) {
            ctx.getSource().sendFailure(Component.literal(
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
            ctx.getSource().sendFailure(Component.literal(
                    "配置文件不存在: " + active.network_name + "_" + version + ".toml，请执行 /easytier config list 查看可用配置"));
            return 0;
        }

        TomlConfig cfg = cm.loadConfig(active.network_name, version);
        if (cfg == null || cfg.isCorrupted()) {
            ctx.getSource().sendFailure(Component.literal("配置文件已损坏"));
            return 0;
        }

        if (service.isRunning()) {
            service.stop();
        }

        boolean ok = service.start(
                cfg.getNetworkName(), version,
                cfg.getRelayServer() != null ? cfg.getRelayServer() : "",
                cfg.getNetworkSecret() != null ? cfg.getNetworkSecret() : "",
                cfg.getDevName() != null ? cfg.getDevName() : "et_forge",
                cfg.isDhcp());

        if (ok) {
            final String restartName = active.network_name;
            final Version restartVersion = version;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "已重启: " + restartName + " @ " + restartVersion), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("重启失败，请查看日志"));
            return 0;
        }
    }

    private static int showUsage(CommandContext<CommandSourceStack> ctx) {
        String usage = "EasyTier 命令用法:\n"
                + "/easytier start <网络名称> <中继服务器> [密码] [版本]  - 启动 EasyTier\n"
                + "/easytier stop                                      - 停止 EasyTier\n"
                + "/easytier status                                    - 查看状态\n"
                + "/easytier peer                                      - 查看节点列表\n"
                + "/easytier node                                      - 查看本节点信息\n"
                + "/easytier route                                     - 查看路由表\n"
                + "/easytier portforward add <tcp|udp> <端口> <虚拟IP> <MC端口>\n"
                + "/easytier portforward remove <tcp|udp> <端口>\n"
                + "/easytier portforward list\n"
                + "/easytier config list\n"
                + "/easytier config use <网络名称> [版本]\n"
                + "/easytier config save [版本]\n"
                + "/easytier config delete <网络名称> [版本]\n"
                + "/easytier reload\n"
                + "兼容: /easytier <网络名称> <中继服务器> [密码]";
        ctx.getSource().sendSuccess(() -> Component.literal(usage), false);
        return 1;
    }
}
