EasyTier 二进制下载说明
========================

本模组采用 GitHub 下载模式，不在 jar 中内置二进制。

首次启动 EasyTier 时，模组会自动从 GitHub Releases 下载当前平台对应的
EasyTier CLI zip 包，解压到 config/easytier-fabric/bin/ 目录。

下载时依次尝试以下镜像，最后回退原始 GitHub 链接：
  1.  https://raw.ihtw.moe/
  2.  https://gh.zwy.one/
  3.  https://gh.llkk.cc/
  4.  https://ghfast.top/
  5.  https://gh.h233.eu.org/
  6.  https://gh-proxy.com/
  7.  https://ghproxy.net/
  8.  https://gh.xxooo.cf/
  9.  https://ghfile.geekertao.top/
  10. https://ghproxy.cxkpro.top/
  11. https://git.yylx.win/
  12. https://cdn.crashmc.com/
  13. https://githubproxy.cc/
  14. https://github.com/ (原始链接)

也可以手动下载 EasyTier CLI zip 包，将 easytier-core 和 easytier-cli
（Windows 还需 wintun.dll）解压到 config/easytier-fabric/bin/ 目录。

下载地址: https://github.com/EasyTier/EasyTier/releases

Android 不提供二进制，且在运行时主动阻断。
