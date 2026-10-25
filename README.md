# Easytier Go - Minecraft 远程去中心化联机模组

基于 EasyTier CLI 的 Minecraft 1.20.1 Fabric 客户端模组，通过 GitHub 自动下载 EasyTier 二进制实现远程去中心化联机。玩家在"对局域网开放"界面中配置 EasyTier 网络，启动后获得虚拟 IP，其他玩家通过虚拟 IP + 端口直接连接，无需公网 IP 或手动端口映射。

## 功能特性

- 游戏内一键配置 EasyTier 网络，无需手动操作命令行
- 首次启动自动从 GitHub 下载 EasyTier CLI（13个镜像加速），免安装
- 在"对局域网开放"界面直接注入 EasyTier 配置按钮
- 多配置管理：按网络名称 + 版本号（v1.0, v1.1...）管理多套配置
- 端口转发支持：策略 A（转发到虚拟端口）和策略 B（直连实际端口）
- 实时日志显示，同时写入日志文件
- 完整的 `/easytier` 服务端命令树
- 跨平台支持：Windows / Linux / macOS（x86_64 和 arm64）
- Android 平台主动检测并阻断，引导使用官方 Android 应用
- macOS Gatekeeper 提示、Linux TUN 权限检查、glibc 兼容性提示

## 环境要求

- Minecraft 1.20.1
- Fabric/Forge Loader
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

构建产物位于 `build/libs/easytier-go-1.0.0.jar`

### 使用系统 Gradle

需要 Gradle 8.5+：

```bash
gradle build
```

## 安装方法

1. 将构建好的 `easytier-go-1.0.0.jar` 放入 Minecraft 的 `mods` 文件夹
2. 确保已安装 Fabric API
3. 将 EasyTier 二进制放入 `src/main/resources/easytier/` 对应平台目录后重新构建，或手动放置到 `config/easytier-fabric/bin/`
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

模组启动时自动将当前平台二进制解压到 `config/easytier-fabric/bin/`，Linux/macOS 自动设置可执行权限。

## 使用方法

### 客户端（单人游戏联机）

1. 进入单人世界
2. 按 `Esc` 打开菜单，点击"对局域网开放"
3. 在"创建局域网世界"按钮旁边点击 **EasyTier** 按钮
4. 填写配置：
   - **配置项**：选择已有配置或新建
   - **网络名称**（必填）：虚拟局域网名称，所有联机玩家必须相同
   - **版本**：选择配置版本，可保存为新版本
   - **中继服务器**（必填）：如 `public.easytier.cn:11010`
   - **网络密码**（选填）：虚拟局域网密码
   - **虚拟网卡名**（高级）：默认 `et_fabric`
5. 点击"启动"
6. 等待虚拟 IP 分配，将连接地址告诉朋友
7. 朋友在"直接连接"中输入该地址即可加入

### 端口转发策略

- **策略 A（推荐）**：启用端口转发，好友通过 `<虚拟IP>:<虚拟端口>` 连接
- **策略 B**：不转发，好友直接连接 `<虚拟IP>:<实际Minecraft端口>`

### 服务端命令

```
# ==================== 基础控制 ====================
/easytier start <网络名称> <中继服务器> [密码]                 # 启动 EasyTier，自动生成或加载 <网络名称>.toml
/easytier stop                                                 # 停止当前 EasyTier 进程
/easytier status                                               # 查看运行状态、配置文件和虚拟 IP
/easytier reload                                               # 从 active.json 重启当前配置
/easytier                                                      # 显示完整用法

# ==================== 节点与路由 ====================
/easytier peer                                                 # 查看节点列表（含本机，cost=Local）
/easytier node                                                 # 查看本节点信息
/easytier route                                                # 查看路由表

# ==================== 端口转发 ====================
/easytier portforward add <tcp|udp> <绑定端口> <虚拟IP> <MC端口>  # 添加端口转发，如 tcp 25565 10.1.1.2 25565
/easytier portforward remove <tcp|udp> <绑定端口>               # 移除指定协议和绑定端口的转发
/easytier portforward list                                     # 查看当前端口转发规则

# ==================== 配置管理 ====================
/easytier config list                                          # 列出 config/easytier_go/configs/ 下所有配置
/easytier config use <网络名称>                                 # 设置 active.json 中的活动配置
/easytier config save                                          # 保存当前运行中的配置（覆盖同名 TOML）
/easytier config delete <网络名称>                              # 删除指定网络的配置文件

# ==================== 服务器世界自启动 ====================
/easytier autostart on [网络名称]                               # 开启世界加载自动组网；省略名称时自动选择当前/唯一配置
/easytier autostart off                                        # 关闭世界加载自动组网
/easytier autostart status                                     # 查看自启动开关、世界配置和配置文件状态
/easytier autostart                                            # 等价于 autostart status
```

兼容旧形式：`/easytier <网络名称> <中继服务器> [密码]`

### 公共中继服务器

点击配置界面中的"中继列表"按钮，或访问：
https://info.qtet.cn/uptime/easytier

## 配置文件

配置存储在 `config/easytier-fabric/` 目录：

```
config/easytier-fabric/
├── configs/           # TOML 配置文件 (<名称>_<版本>.toml)
├── bin/                 # 解压后的 EasyTier 二进制
├── logs/                # 日志文件
└── active.json        # 活动配置记录
```

TOML 配置示例：

```toml
# easytier-fabric config
# network_name = my-mc-network
# version      = v1.1
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
    │   │   ├── Version.java              # 版本号比较
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
        ├── easytier-fabric-lan.mixins.json
        ├── assets/easytier-fabric-lan/lang/
        │   ├── zh_cn.json
        │   └── en_us.json
        └── easytier/                     # 各平台二进制
```

## 注意事项

1. EasyTier 创建 TUN 虚拟网卡需要管理员/root 权限
2. Linux 上需确保 `/dev/net/tun` 可用，必要时执行 `sudo setcap cap_net_admin+ep easytier-core`
3. macOS 上首次运行可能被 Gatekeeper 拦截，执行 `xattr -d com.apple.quarantine easytier-core` 或在系统设置中放行
4. 所有联机玩家必须使用相同的网络名称和密码
5. 中继服务器仅用于节点发现和中继，数据传输优先 P2P 直连
6. 关闭游戏时模组会自动停止 EasyTier 进程
7. Android 不支持本模组，请使用 EasyTier 官方 Android 应用建立虚拟网络后直接连接虚拟 IP

## 致谢

- [EasyTier](https://easytier.cn) - 去中心化异地组网方案
- [Fabric](https://fabricmc.net) - Mod 加载器
