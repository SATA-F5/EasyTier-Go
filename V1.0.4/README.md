# EasyTier Go V1.0.4 发布文件

本目录包含 V1.0.4 的源码包与已编译模组，适用于 Minecraft 1.20.1。

## 文件说明

| 文件 | 说明 | 适用端 |
| --- | --- | --- |
| `easytier_go-forge-1.0.4.jar` | 已编译模组 | Forge 47.x |
| `easytier_go-forge-1.0.4-sources.jar` | Java 源码 jar（IDE 附加源码用） | Forge |
| `easytier_go-forge-1.0.4-source.zip` | 完整源码包（含 Gradle 构建文件） | Forge |
| `easytier-go-fabric-1.0.4.jar` | 已编译模组 | Fabric Loader ≥ 0.15.0 |
| `easytier-go-fabric-1.0.4-sources.jar` | Java 源码 jar（IDE 附加源码用） | Fabric |
| `easytier-go-fabric-1.0.4-source.zip` | 完整源码包（含 Gradle 构建文件） | Fabric |
| `SHA256SUMS.txt` | 以上文件的 SHA-256 校验值 | - |

> `sources.jar` 由 Gradle 的 `withSourcesJar()` 生成；`source.zip` 由 `git archive` 从本次发布提交导出，二者内容一致。

## 安装

### Forge

1. 安装 Minecraft 1.20.1 + Forge 47.x。
2. 将 `easytier_go-forge-1.0.4.jar` 放入 `.minecraft/mods/`。
3. 启动游戏。首次启动 EasyTier 时会提示并自动下载对应平台的 EasyTier CLI。

### Fabric

1. 安装 Minecraft 1.20.1 + Fabric Loader ≥ 0.15.0。
2. 将 Fabric API 与 `easytier-go-fabric-1.0.4.jar` 放入 `.minecraft/mods/`。
3. 启动游戏。

## 校验

```bash
sha256sum -c SHA256SUMS.txt
```

Windows PowerShell：

```powershell
Get-FileHash .\easytier_go-forge-1.0.4.jar -Algorithm SHA256
```

## V1.0.4 修复摘要

此前输入框组件对所有输入内容执行纯数字过滤，导致配置界面的网络名称、中继服务器、网络密码、网卡名无法输入字母或中文。V1.0.4 已移除全局过滤，数字限制仅保留在"虚拟端口"字段。详细分析与排查记录见 [BUGFIX_AUDIT.md](BUGFIX_AUDIT.md)。
