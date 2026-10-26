# Easytier Go - Minecraft 远程去中心化联机模组

基于 EasyTier CLI 的 Minecraft 1.20.1 Fabric 客户端模组，通过 GitHub 自动下载 EasyTier 二进制实现远程去中心化联机。玩家在"对局域网开放"界面中配置 EasyTier 网络，启动后获得虚拟 IP，其他玩家通过虚拟 IP + 端口直接连接，无需公网 IP 或手动端口映射。

> **V1.0.4 重点修复**：1.0.3 的输入框组件对所有字段执行纯数字过滤，导致配置界面无法输入带字母或中文的网络名称、密码、中继地址。现在只有"虚拟端口"字段限制为数字，其余字段恢复自由输入。详见 [V1.0.4 更新日志](#v104-更新日志)。

## 功能特性

- 游戏内一键配置 EasyTier 网络，无需手动操作命令行
- 首次启动自动从 GitHub 下载 EasyTier CLI（13个镜像加速），免安装
- 在"对局域网开放"界面直接注入 EasyTier 配置按钮
- 多配置管理：每个网络对应一个配置文件（如 `mc.toml`），不再使用版本号
- 端口转发支持：策略 A（转发到虚拟端口）和策略 B（直连实际端口）
- 实时日志显示，同时写入日志文件
- 完整的 `/easytier` 服务端命令树
- 跨平台支持：Windows / Linux / macOS（x86_64 和 arm64）
- Android 平台主动检测并阻断，引导使用官方 Android 应用
- macOS Gatekeeper 提示、Linux TUN 权限检查、glibc 兼容性提示

## 环境要求

- Minecraft 1.20.1
- Fabric Loader >= 0.15.0
- Fabric API
- Java 17+
- 支持平台：
  - Windows 10/11 (x86_64 / arm64)
  - Linux glibc (x86_64 / aarch64)
  - macOS 12+ (x86_64 / aarch64)
- 不支持：Android、Windows 7、32 位 x86

## 构建方法

### 使用 Gradle Wrapper

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

构建产物位于 `build/libs/easytier-go-1.0.4.jar`

### 使用系统 Gradle

需要 Gradle 8.5+：

```bash
gradle build
```

## 安装方法

1. 将构建好的 `easytier-go-1.0.4.jar` 放入 Minecraft 的 `mods` 文件夹
2. 确保已安装 Fabric API
3. 将 EasyTier 二进制放入 `src/main/resources/easytier/` 对应平台目录后重新构建，或手动放置到 `config/easytier-go/bin/`
4. 启动游戏

### 二进制放置说明

模组 jar 内 `resources/easytier/` 按平台目录放置二进制：

```
resources/easytier/
├── windows-x86_64/   (easytier-core.exe, easytier-cli.exe, wintun.dll)
├── windows-arm64/    (easytier-core.exe, easytier-cli.exe, wintun.dll)
├── linux-x86_64/     (easytier-core, easytier-cli)
├── linux-aarch64/    (easytier-core, easytier-cli)
├── macos-x86_64/     (easytier-core, easytier-cli)
└── macos-aarch64/    (easytier-core, easytier-cli)
```

下载地址：https://github.com/EasyTier/EasyTier/releases

模组启动时自动将当前平台二进制解压到 `config/easytier-go/bin/`，Linux/macOS 自动设置可执行权限。

## 使用方法

### 客户端（单人游戏联机）

1. 进入单人世界
2. 按 `Esc` 打开菜单，点击"对局域网开放"
3. 在"创建局域网世界"按钮旁边点击 **EasyTier** 按钮
4. 填写配置：
   - **配置项**：选择已有配置或新建
   - **网络名称**（必填）：虚拟局域网名称，所有联机玩家必须相同
   - **中继服务器**（必填）：如 `public.easytier.cn:11010`
   - **网络密码**（选填）：虚拟局域网密码
   - **虚拟网卡名**（高级）：默认 `et_fabric`
5. 点击"启动"
6. 等待虚拟 IP 分配，将连接地址告诉朋友
   - 虚拟 IP 以 `easytier-cli peer` 输出中 `cost=Local` 的行为准，自动转换为标准 IPv4（不带 `/24` 掩码）
   - 该 IP 仅保存在内存中的全局变量里，不会写入 TOML 或其他数据文件
7. 朋友在"直接连接"中输入该地址即可加入

### 端口转发策略

- **策略 A（推荐）**：启用端口转发，好友通过 `<虚拟IP>:<虚拟端口>` 连接
- **策略 B**：不转发，好友直接连接 `<虚拟IP>:<实际Minecraft端口>`

### 服务端命令

```
/easytier start <网络名称> <中继服务器> [密码]      # 启动 EasyTier
/easytier stop                                       # 停止 EasyTier
/easytier status                                     # 查看运行状态
/easytier peer                                       # 查看节点列表
/easytier node                                       # 查看本节点信息
/easytier route                                      # 查看路由表
/easytier portforward add <tcp|udp> <端口> <虚拟IP> <MC端口>
/easytier portforward remove <tcp|udp> <端口>
/easytier portforward list
/easytier config list                                # 列出所有配置
/easytier config use <网络名称>                      # 设置活动配置
/easytier config save                                # 保存当前配置
/easytier config delete <网络名称>                   # 删除配置
/easytier autostart on [网络名称]                    # 开启服务器世界自启动
/easytier autostart off                              # 关闭服务器世界自启动
/easytier autostart status                           # 查看自启动状态
/easytier reload                                     # 从 active.json 重启
```

兼容旧形式：`/easytier <网络名称> <中继服务器> [密码]`

### 服务端自动启动

在服务器控制台执行一次：

```text
/easytier autostart on mc
```

即可开启当前世界的 EasyTier 自启动，等价于自动写入：

```text
config/easytier-go/server.toml
config/easytier-go/server/<世界名>/active.json
```

之后服务器每次启动并加载该世界时，会自动使用 `configs/mc.toml` 启动 EasyTier。

关闭自启动：

```text
/easytier autostart off
```

查看当前状态：

```text
/easytier autostart status
```

### 公共中继服务器

点击配置界面中的"中继列表"按钮，或访问：
https://info.qtet.cn/uptime/easytier

## 配置文件

配置存储在 `config/easytier-go/` 目录：

```
config/easytier-go/
├── configs/           # TOML 配置文件（<名称>.toml，如 mc.toml）
├── bin/               # 解压后的 EasyTier 二进制
├── logs/              # 日志文件
└── active.json        # 活动配置记录
```

TOML 配置示例：

```toml
# easytier-go config
# network_name = my-mc-network
# generated    = 2026-10-17T10:05:00Z
instance_name = "my-mc-network"
instance_id = "a1b2c3d4e5f6"
dhcp = true
listeners = [
    "tcp://0.0.0.0:11010",
    "udp://0.0.0.0:11010",
    "wg://0.0.0.0:11011",
]

[network_identity]
network_name = "my-mc-network"
network_secret = "password"

[[peer]]
uri = "tcp://public.easytier.cn:11010"

[flags]
dev_name = "et_fabric"
```

## 项目结构

```
easytier-mc/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── LICENSE
├── README.md
└── src/main/
    ├── java/com/easytier/fabric/lan/
    │   ├── EasyTierFabricLan.java        # 主入口
    │   ├── EasyTierFabricLanClient.java  # 客户端入口
    │   ├── EasyTierService.java          # 核心服务（整合所有组件）
    │   ├── platform/
    │   │   ├── Platform.java             # 平台枚举
    │   │   ├── Architecture.java         # 架构枚举
    │   │   └── PlatformDetector.java     # 平台探测与 Android 阻断
    │   ├── config/
    │   │   ├── ConfigManager.java        # 配置扫描/加载/保存/删除
    │   │   ├── TomlConfig.java           # TOML 配置模型
    │   │   ├── TomlGenerator.java        # TOML 生成器
    │   │   ├── TomlParser.java           # TOML 解析器
    │   │   └── FileSanitizer.java        # 文件名安全处理
    │   ├── binary/
    │   │   └── BinaryExtractor.java      # 二进制解压与权限设置
    │   ├── process/
    │   │   ├── EasyTierProcessManager.java  # 核心进程生命周期
    │   │   └── EasyTierCliRunner.java       # CLI 命令封装
    │   ├── client/
    │   │   └── EasyTierConfigScreen.java # 配置界面
    │   ├── command/
    │   │   └── EasyTierCommand.java      # /easytier 命令树
    │   ├── mixin/
    │   │   └── OpenToLanScreenMixin.java # 按钮注入
    │   └── util/
    │       └── LogManager.java           # 日志管理
    └── resources/
        ├── fabric.mod.json
        ├── easytier-go.mixins.json
        ├── assets/easytier-go/lang/
        │   ├── zh_cn.json
        │   └── en_us.json
        └── easytier/                     # 各平台二进制（需自行放入）
```

## 注意事项

1. 模组默认使用 `--no-tun` + `smoltcp` 用户态协议栈，不需要管理员/root 权限，也不依赖 `/dev/net/tun`
2. 只有在你自行改用 TUN 模式时，才需要 `/dev/net/tun` 和相应权限（如 `sudo setcap cap_net_admin+ep easytier-core`）
3. macOS 上首次运行可能被 Gatekeeper 拦截，执行 `xattr -d com.apple.quarantine easytier-core` 或在系统设置中放行
4. 所有联机玩家必须使用相同的网络名称和密码
5. 中继服务器仅用于节点发现和中继，数据传输优先 P2P 直连
6. 关闭游戏时模组会自动停止 EasyTier 进程
7. Android 不支持本模组，请使用 EasyTier 官方 Android 应用建立虚拟网络后直接连接虚拟 IP

## V1.0.4 更新日志

### 修复：配置界面无法输入字母/中文（严重）

- **问题现象**：配置界面的"网络名称"等输入框只能输入数字，字母、中文等字符完全无法录入。
- **根本原因**：`PlaceholderTextFieldWidget` 重写了 `write(String)` 并对所有文本无条件执行 `replaceAll("[^0-9]", "")`，因此所有使用该组件的字段（网络名称、中继服务器、网络密码、网卡名、虚拟端口）都变成了纯数字输入框。
- **修复方式**：
  - 删除 `write` 中的全局过滤；
  - 新增显式开关 `setNumericOnly(boolean)`，内部用 `setTextPredicate` 实现；
  - 仅"虚拟端口"字段调用 `setNumericOnly(true)`，其余字段默认接受任意字符。
- **验证**：网络名称、中继服务器、网络密码、网卡名可输入字母、数字、中文及常见符号；虚拟端口仍只接受 0-9。

### 其他

- 版本号统一升级为 `1.0.4`，构建产物与源码包见仓库根目录 `V1.0.4/`。

## 致谢

- [EasyTier](https://easytier.cn) - 去中心化异地组网方案
- [Fabric](https://fabricmc.net) - Mod 加载器
