# Easytier Go - Minecraft 远程去中心化联机模组

基于 [EasyTier](https://easytier.cn) CLI 的 Minecraft 1.20.1 联机模组，提供 **Fabric** 与 **Forge** 两个版本。玩家在"对局域网开放"界面中配置 EasyTier 网络，启动后获得虚拟 IP，其他玩家通过虚拟 IP + 端口直接连接，无需公网 IP 或手动端口映射。

- Fabric 端：`EasyTier_Go-fabric/`
- Forge 端：`EasyTier_Go-forge/`

## 当前版本：V1.0.4

> **严重输入 BUG 已修复**：1.0.3 及更早版本的输入框组件会对所有文本字段执行纯数字过滤，导致"网络名称""中继服务器""网络密码""网卡名"无法输入字母、中文等任何非数字内容。V1.0.4 已移除全局过滤，只有"虚拟端口"字段限制为数字。

### 根本原因

`PlaceholderTextFieldWidget` 在 `write(String)` / `insertText(String)` 中对输入文本无条件执行 `replaceAll("[^0-9]", "")`，而该类被配置界面中全部五个输入框复用。字符在进入输入框之前就被丢弃，因此与输入法、键盘布局无关。

### 修复内容

- 移除全局数字过滤，文本字段默认接受任意字符；
- 新增显式开关 `setNumericOnly(boolean)`，仅"虚拟端口"字段开启；
- Fabric 端改用 `setTextPredicate` 实现数字限制，不再重写 `write`；
- Forge 端改用 `EditBox#setFilter`，粘贴、拖入文本等输入路径同样遵守字段限制；
- 输入框恢复字母、数字、中文及常见符号输入，中文输入法可正常使用；
- 版本号统一升级为 `1.0.4`。

## 功能特性

- 游戏内一键配置 EasyTier 网络，无需手动操作命令行
- 首次启动自动从 GitHub 下载 EasyTier CLI（多镜像加速，最后回退原链接），并进行 SHA256 校验
- 在"对局域网开放"、暂停菜单、多人游戏、添加服务器、直接连接界面注入 EasyTier 入口
- 多配置管理：每个网络对应一个独立 TOML 配置文件
- 端口转发：策略 A（转发到虚拟端口）与策略 B（直连实际端口）
- 实时日志显示，同时写入日志文件
- 完整的 `/easytier` 服务端命令树，含服务器世界自启动
- 跨平台支持：Windows / Linux / macOS（x86_64 与 arm64）
- Android 平台主动检测并阻断，引导使用 EasyTier 官方 Android 应用

## 下载与构建产物

V1.0.4 已编译并附源码包，位于仓库根目录 [`V1.0.4/`](V1.0.4/)：

| 文件 | 说明 |
| --- | --- |
| `easytier_go-forge-1.0.4.jar` | Forge 1.20.1 模组 |
| `easytier_go-forge-1.0.4-sources.jar` | Forge 源码 jar |
| `easytier_go-forge-1.0.4-source.zip` | Forge 完整源码包 |
| `easytier-go-fabric-1.0.4.jar` | Fabric 1.20.1 模组 |
| `easytier-go-fabric-1.0.4-sources.jar` | Fabric 源码 jar |
| `easytier-go-fabric-1.0.4-source.zip` | Fabric 完整源码包 |
| `SHA256SUMS.txt` | 上述文件的 SHA-256 校验值 |

安装：将对应 jar 放入 Minecraft `mods` 文件夹（Fabric 端还需安装 Fabric API），启动游戏即可。

## 环境要求

- Minecraft 1.20.1
- Java 17+
- Fabric Loader ≥ 0.15.0 + Fabric API（Fabric 端）
- Forge 47.x（Forge 端，构建使用 47.3.0）
- 支持平台：Windows 10/11、Linux glibc、macOS 12+（x86_64 / arm64）
- 不支持：Android、Windows 7、32 位 x86

## 构建方法

```bat
:: Fabric
cd EasyTier_Go-fabric
gradlew.bat build

:: Forge
cd ..\EasyTier_Go-forge
gradlew.bat build
```

Linux / macOS 将 `gradlew.bat` 换成 `./gradlew`。构建需要 Java 17（或让 Gradle toolchain 自动选择），产物位于各模块 `build/libs/`。

## 使用方法

1. 进入单人世界，按 `Esc` 打开菜单。
2. Fabric 端点击"对局域网开放"旁的 **EasyTier** 按钮；Forge 端点击"EasyTier 联机配置"。
3. 填写网络名称、中继服务器（如 `public.easytier.cn:11010`）、网络密码（可选）。
4. 点击"启动"，等待虚拟 IP 分配。
5. 好友在"直接连接"中输入界面显示的连接地址即可加入。

所有联机玩家必须使用相同的网络名称和密码。各端详细用法、配置目录与 `/easytier` 命令见模块 README：

- [Fabric 使用说明](EasyTier_Go-fabric/README.md)
- [Forge 使用说明](EasyTier_Go-forge/README.md)

## 注意事项

1. 模组默认使用 `--no-tun` + smoltcp 用户态协议栈，通常不需要管理员/root 权限。
2. 自行改用 TUN 模式时，Linux 需确保 `/dev/net/tun` 可用，必要时执行 `sudo setcap cap_net_admin+ep easytier-core`。
3. macOS 首次运行可能被 Gatekeeper 拦截，执行 `xattr -d com.apple.quarantine easytier-core` 或在系统设置中放行。
4. 中继服务器仅用于节点发现和中继，数据传输优先 P2P 直连。
5. 关闭游戏时模组会自动停止 EasyTier 进程。

## 致谢

- [EasyTier](https://easytier.cn) - 去中心化异地组网方案
- [Fabric](https://fabricmc.net) / [Forge](https://files.minecraftforge.net) - Mod 加载器

## 许可证

MIT，见 [LICENSE](LICENSE)。
