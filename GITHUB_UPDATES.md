# GitHub 在线更新使用说明

安卓 1.3.0 / code 6 起支持 GitHub Releases 在线更新。当前发布为 1.3.1 / code 7，直接覆盖安装，不要卸载旧版。

## 在 App 里

1. 打开“设置 → 版本与更新”。1.3.1 默认预填官方仓库 `wang503133712-commits/coin-decision`；1.3.0 用户可先填入这个地址检查升级。也支持自定义其他公开仓库的 `用户名/仓库名` 或完整地址。
2. 点击“保存并检查更新”。仓库单独保存在本机，不会修改 API 配置、Key、音效开关或历史。
3. 发现新版后，查看更新说明和大小，点击“下载并安装新版”。下载进度会显示；下载完成后校验包名、同签名及更高 versionCode，再请求系统安装。
4. 首次安装可能需要允许此 App 安装软件。返回后点击“继续安装已校验更新”，并在安卓系统确认。

只在你点击时检查或下载，不在后台自动联网。失败可重试；本地 APK 更新入口继续可用。下载中离开设置会结束该页面的任务，未完成文件不用于安装。GitHub 更新请求不携带 API Key、事项、回答或历史。

## 如何发布

1. 官方 **公开 GitHub 仓库**为 [wang503133712-commits/coin-decision](https://github.com/wang503133712-commits/coin-decision)。
2. 保留本项目原签名，修改 `version.properties`，同时提高 versionName 和 versionCode。例如下一正式版：`versionName=1.3.2`、`versionCode=8`。
3. 用 `./build.ps1 -Tasks @('testReleaseUnitTest','lintRelease','assembleRelease')` 构建。把已签名的 `app/build/outputs/apk/release/app-release.apk` 作为发布附件上传，可改名为 `coin-decision.apk`。
4. 在仓库 Releases 新建正式发布，下一版标签为 `v1.3.2`，填写更新说明，附上该 APK，发布为最新正式版本。源码上传或仅创建 Git 标签不会生成可下载更新。

App 读取最新正式 Release；不接受草稿、预发布或带 beta 等后缀的标签。标签应为 `v主版号.次版号.修订号`，且与 APK 的 versionName 对应。Release 中放一个 APK；若同时发布多个 APK，将供此 App 下载的通用包命名为 `coin-decision.apk`。

下载限制为 150 MB。若 GitHub 返回 SHA-256 digest，会核对完整内容；无论是否提供 digest，都必须经过安卓包名、签名和版号检查。版本名称按数字比较，最终以 APK 的更高 versionCode 为准。

**签名私钥和密码必须私下保留**：`.signing/`、`signing.properties`、`coin-signing-private-backup.zip` 都不要上传 GitHub。源码 ZIP 已排除签名材料；无需把 GitHub 密码或访问令牌写进 App。

## 范围和限制

本版支持公开仓库；私有仓库不支持。手机需要能够访问 GitHub API、GitHub 和其下载 CDN；网络限制或请求限额会显示失败提示，不影响离线投币。安装需用户确认，不能静默替换 App。没有接入无人值守的 GitHub Actions 自动构建。

使用 GitHub 官方 Releases API：[最新正式版本](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)、[发布附件](https://docs.github.com/en/rest/releases/assets)。当前更新功能仅用于安卓工程，原生鸿蒙工程未接入此流程。
