# Kotlin 发布候选

`.github/workflows/kotlin-preview.yml` 提供手动触发的发布候选流程。它复用 Kotlin 三平台构建与测试工作流，全部成功后收集 Linux Deb、Windows MSI 和 macOS DMG。Linux 同时执行现有 GUI、托盘、HUD 及打包运行检查；Windows/macOS 的构建通过不等于原生桌面人工验收。

## 生成可审查制品

在 GitHub Actions 选择 **Kotlin preview candidate**，选定要验证的分支或提交对应的 ref，保留 `dry_run=true`。流程只生成 `VoidMei-Kotlin-preview` artifact，不创建 Release。内容包括：

- 三个平台的安装包，直接复制同一次工作流中已测试的构建制品，不重新编译。
- `SHA256SUMS`：安装包 SHA-256 校验和。
- `manifest.json`：版本、完整源码提交号、原安装包文件名、平台、构建 runner 架构、大小和哈希。
- `release-notes.md`：预览说明及迁移文档位置。

版本来自 `desktop/build.gradle.kts` 的 `packageVersion`。缺少平台、一个平台出现多个安装包、空文件、文件名版本不符或无法读取唯一版本时失败，不生成可发布的成功制品。每个构建 job 上传 kotlin-build.json，记录该 job 的提交号、版本、runner 架构及安装包哈希。汇总端要求元数据与所选提交及实际文件一致；输出安装包文件名包含架构。汇总端额外通过 dpkg-deb 读取 Linux 包内的 Package、Version、Architecture，核对 voidmei 包名、上游版本及架构，并将控制字段写入 manifest。允许上游版本后带 Debian 修订号。Windows 构建端通过 Windows Installer 只读查询 MSI 的 ProductName、ProductVersion 和 Template Summary，校验产品、版本及平台后写入元数据；汇总端再次核对这些字段。该读取脚本尚待真实 Windows runner 执行验证。macOS 构建端只读挂载 DMG，读取唯一应用的 Info.plist，核对 CFBundleName、CFBundleShortVersionString，并通过 lipo 检查 CFBundleExecutable 指定的启动程序是否包含 runner 对应架构；读取后卸载，失败则终止构建元数据步骤。汇总端再次核对这些身份字段并写入 manifest。该检查仅覆盖应用启动程序，不认证全部内嵌运行库，也不启动应用；真实 macOS 命令调用尚待 macOS runner 验证。

下载 artifact 并解压后，可在其目录执行 `sha256sum -c SHA256SUMS`。安装包不包含 FM 数据；设置和目前已知限制见同一提交的 [试用步骤](kotlin-quick-start.md) 与 [迁移记录](kotlin-migration.md)。

## 创建草稿

需要发布候选草稿时，手动运行同一流程并设置 `dry_run=false`。仍须先完成该次全部构建和测试；随后使用该次产物创建 **draft + prerelease**，标签为 `kotlin-<版本>-preview-<提交前12位>`，目标为完整源码提交号。

只有创建草稿的 job 获得 `contents: write`，构建和制品整理保持只读权限。工作流不自动公开草稿、不替换旧版稳定发行入口、不覆盖已存在的同名 Release。公开发布前应检查该次安装包和迁移验收结果；不要把创建草稿视为跨平台验收完成。

默认 Nix 构建、运行与开发入口已切换到 Kotlin，旧版入口为 #legacy-java；数字标签的旧版发行工作流仍未切换。此候选流程用于提供可复现的 Kotlin 三平台交付制品。流程文件已在本地通过 actionlint，制品整理脚本通过单元测试；远程工作流是否通过应以对应 Actions run 为准。
