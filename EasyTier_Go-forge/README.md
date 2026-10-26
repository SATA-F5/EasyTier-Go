# EasyTier Go (Forge)

基于 EasyTier CLI 的 Minecraft 1.20.1 Forge 客户端/服务端模组，通过 GitHub 自动下载 EasyTier 二进制实现远程去中心化联机。玩家在"对局域网开放"界面中配置 EasyTier 网络，启动后获得虚拟 IP，其他玩家通过虚拟 IP + 端口直接连接，无需公网 IP 或手动端口映射。

本版本（1.0.4）同步自 Fabric 端 1.0.4 源码，并保留 Forge 平台适配。

> **V1.0.4 重点修复**：配置界面的网络名称、中继服务器、网络密码、网卡名等文本输入框曾被数字过滤器误伤，导致无法输入字母、中文等内容。现在只有"虚拟端口"字段启用数字过滤，其余文本字段恢复自由输入，并支持中文输入法。详见 [V1.0.4 更新日志](#v104-更新日志)。

## 功能特性

- 游戏内一键配置 EasyTier 网络，无需手动操作命令行
- 首次启动自动从 GitHub 下载 EasyTier CLI（13 个镜像加速，最后回退原链接）
- 在"对局域网开放"、暂停菜单、多人游戏、添加服务器、直接连接界面注入 EasyTier 入口
- 多配置管理：每个网络对应一个配置文件（如 `mc.toml`），不再使用版本号
- 自动迁移旧版 `<名称>_vX.Y.toml` 配置文件
- 端口转发支持：策略 A（转发到虚拟端口）和策略 B（直连实际端口）
- 实时日志显示（线程安全），同时写入日志文件
- 完整的 `/easytier` 服务端命令树，含服务器世界自启动
- 跨平台支持：Windows / Linux / macOS（x86_64 和 arm64）
- Android 平台主动检测并阻断，引导使用官方 Android 应用

## 环境要求

- Minecraft 1.20.1
- Forge 47.x（构建使用 47.3.0）
- Java 17+
- 支持平台：
  - Windows 10/11 (x86_64 / arm64)
  - Linux glibc (x86_64 / aarch64)
  - macOS 12+ (x86_64 / aarch64)
- 不支持：Android、Windows 7、32 位 x86

## 构建方法

```bat
gradlew.bat build
```

构建产物位于 `build/libs/easytier_go-1.0.4.jar`，源码包为 `build/libs/easytier_go-1.0.4-sources.jar`。

## 安装方法

1. 将 `easytier_go-forge-1.0.4.jar` 放入 Minecraft 的 `mods` 文件夹
2. 启动游戏
3. 首次启动 EasyTier 时会提示并自动下载对应平台的 EasyTier CLI

## 使用方法

### 客户端（单人游戏联机）

1. 进入单人世界
2. 按 `Esc` 打开菜单，点击"EasyTier 联机配置"
3. 填写配置：
   - **网络名称**（必填）：虚拟局域网名称，所有联机玩家必须相同
   - **中继服务器**（必填）：如 `public.easytier.cn:11010`
   - **网络密码**（选填）：虚拟局域网密码
   - **虚拟网卡名**（高级）：默认 `et_forge`
   - **虚拟端口**：默认 `25565`
4. 点击"启动"
5. 等待虚拟 IP 分配，界面会显示转发地址
6. 朋友在"直接连接"中输入该地址即可加入

### 端口转发策略

- **策略 A（推荐）**：启用端口转发，好友通过 `<虚拟IP>:<虚拟端口>` 连接
- **策略 B**：不转发，好友直接连接 `<虚拟IP>:<实际Minecraft端口>`

### 服务端命令

```text
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

之后服务器每次启动并加载该世界时，会自动使用 `config/easytier_go/configs/mc.toml` 启动 EasyTier。

关闭自启动：

```text
/easytier autostart off
```

查看当前状态：

```text
/easytier autostart status
```

### 公共中继服务器

点击配置界面中的"中继服务器列表"按钮，或访问：
https://info.qtet.cn/uptime/easytier

## 配置文件

配置存储在 `config/easytier_go/` 目录：

```text
config/easytier_go/
├── configs/           # TOML 配置文件（<名称>.toml，如 mc.toml）
├── bin/               # 解压后的 EasyTier 二进制
├── logs/              # 日志文件
├── active.json        # 活动配置记录
├── autoconnect.json   # 多人游戏自动连接配置
├── server.toml        # 服务器世界自启动开关
└── server/<世界名>/active.json
```

TOML 配置示例：

```toml
# easytier-forge config
# network_name = my-mc-network
# generated    = 2026-10-25T09:00:00Z
instance_name = "my-mc-network"
instance_id = "a1b2c3d4e5f6"
dhcp = true
listeners = [
    "tcp://0.0.0.0:11010",
    "udp://0.0.0.0:11010",
    "wg://0.0.0.0:11011",
]
rpc_portal = "127.0.0.1:15888"
dev_name = "et_forge"

[network_identity]
network_name = "my-mc-network"
network_secret = "password"

[[peer]]
uri = "tcp://public.easytier.cn:11010"

[flags]
no_tun = true
use_smoltcp = true
```

## 注意事项

1. 模组默认使用 `--no-tun` + `smoltcp` 用户态协议栈，不需要管理员/root 权限，也不依赖 `/dev/net/tun`
2. 只有在你自行改用 TUN 模式时，才需要 `/dev/net/tun` 和相应权限
3. macOS 上首次运行可能被 Gatekeeper 拦截，执行 `xattr -d com.apple.quarantine easytier-core` 或在系统设置中放行
4. 所有联机玩家必须使用相同的网络名称和密码
5. 中继服务器仅用于节点发现和中继，数据传输优先 P2P 直连
6. 关闭游戏时模组会自动停止 EasyTier 进程
7. Android 不支持本模组，请使用 EasyTier 官方 Android 应用建立虚拟网络后直接连接虚拟 IP

## V1.0.4 更新日志

### 修复：配置界面无法输入字母/中文（严重）

- **问题现象**：配置界面中"网络名称"等输入框只能输入数字，字母、中文及其他字符全部被丢弃。
- **根本原因**：输入框组件 `PlaceholderTextFieldWidget` 对所有输入内容执行了 `replaceAll("[^0-9]", "")`，而配置界面里除"虚拟端口"外的字段都是文本字段。字符在进入输入框前就被过滤掉，与输入法、键盘布局无关。
- **修复方式**：
  - 移除全局数字过滤，默认允许任意字符；
  - 数字过滤改为显式开关 `setNumericOnly(boolean)`，只有"虚拟端口"字段开启；
  - Forge 端改用 `EditBox#setFilter` 实现，粘贴、拖入文本等路径同样遵守各自字段的限制；
  - Fabric 端不再重写 `write(String)`，改用 `setTextPredicate`，避免再次误伤所有字段。
- **验证**：网络名称、中继服务器、网络密码、网卡名可输入字母、数字、中文及常见符号；虚拟端口仍只接受 0-9。

### 其他

- 配置界面在窗口缩放、从状态面板/下载确认界面返回时会保留已填写的表单内容（Forge）。
- 版本号统一升级为 `1.0.4`，构建产物与源码包见仓库根目录 `V1.0.4/`。

## 致谢

- [EasyTier](https://easytier.cn) - 去中心化异地组网方案
- [Forge](https://files.minecraftforge.net) - Mod 加载器
