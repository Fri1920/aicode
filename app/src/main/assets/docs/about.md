# 关于 (About)

“设置”首页「关于」入口（图标为信息圆圈）。这是一个纯展示型页面，无任何持久化设置，点击进入后展示下列信息：

*   **应用信息**：顶部卡片左侧显示 App 图标，右上为应用名 `AiCode`，右下为一句简介。
*   **版本（点击检查更新）**：分组内一行，显示当前版本号 `v<versionName>`（通过系统 PackageManager 读取，即对外发布版本，与 git tag `v<versionName>` 一致）。点击该行会自动通过 GitHub API `https://api.github.com/repos/jieapi/aicode/releases/latest` 查询最新 Release 的 tag，与当前版本号比对：
    *   相等 → 弹窗提示「已经是最新版本」。
    *   不同 → 弹窗「发现新版本 vX」，点击「前往下载」用浏览器打开 GitHub Releases 页面获取最新 APK。
    *   网络失败或解析失败 → 弹窗提示「检查失败」并给出错误信息。
*   **GitHub 仓库**：点击用系统浏览器打开本项目源码仓库 `https://github.com/jieapi/aicode`。
*   **开源许可证**：本项目使用 **GPL-3.0**，点击用浏览器打开完整 LICENSE 文件。
