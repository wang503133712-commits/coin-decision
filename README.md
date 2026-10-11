# 偏心硬币 1.4.3

中文安卓日常决策工具。默认联网 AI 根据事项出题，AI 只出题、本地规则算倾向、本地 SecureRandom 投币。应用名集中在 `app/src/main/res/values/strings.xml`。

新版为 `偏心硬币-安卓-1.4.3.apk`，Release 构建、关闭调试，沿用原签名，versionCode 为 11。支持 GitHub Releases 在线检查、下载与校验安装。保留媒体音效、上下翻转、牛奶白/金色界面、居中问号与修改按钮、完整 API 接口。可直接覆盖旧版，不要先卸载。

从 [GitHub Releases 下载安卓安装包](https://github.com/wang503133712-commits/coin-decision/releases/latest)。App 默认预填官方仓库 `wang503133712-commits/coin-decision`，在“设置 → 版本与更新”点击“保存并检查更新”即可；自行设置过其他仓库的用户配置保留。使用与发布见 [GITHUB_UPDATES.md](GITHUB_UPDATES.md)。

## 工程与运行

本次工程位于 `C:\Users\Administrator\Documents\Codex\2026-10-10\files-pasted-by-the-user-app\outputs\coin-decision`。

已找到并复用用户工具链：`F:\ComfyAutomation\android-toolchain`，JDK 17.0.20、Gradle 8.13、SDK platform 36、Build Tools 35.0.0。没有修改原有安卓工程或升级全局工具。采用 AGP 8.11.1、Kotlin 2.0.21、Compose BOM 2024.12.01、Room 2.6.1、DataStore 1.1.1、OkHttp 4.12.0、kotlinx.serialization 1.7.3。最低 Android 8.0 (API 26)，target 35、compile 36。

Windows PowerShell：

```powershell
cd 'C:\Users\Administrator\Documents\Codex\2026-10-10\files-pasted-by-the-user-app\outputs\coin-decision'
.\build.ps1
# 默认执行单元测试、lint、debug APK 构建
.\build.ps1 -Tasks @('connectedDebugAndroidTest')
```

其他电脑先配置 JDK 17、Android SDK 36 和 `local.properties` 的 `sdk.dir`（或 ANDROID_HOME），执行 `gradlew.bat testDebugUnitTest lintDebug assembleDebug`。其他系统使用 `./gradlew` 同样任务。不要提交本地路径或签名文件。

源码 ZIP 不包含私密签名。需在工程根目录私下恢复 `coin-signing-private-backup.zip` 中的 `.signing/` 与 `signing.properties` 后才能构建同签名安装包；不要把该备份上传公共仓库。设备测试会修改受测 App 的数据，只在专用测试设备/模拟器执行。

最终 APK 标准输出：`app/build/outputs/apk/release/app-release.apk`。重建命令为 `./build.ps1 -Tasks @('testReleaseUnitTest','lintRelease','assembleRelease')`。交付目录同时提供带版号的 APK。安装到自己明确选择的设备：

```powershell
& 'F:\ComfyAutomation\android-toolchain\sdk\platform-tools\adb.exe' -s <设备序号> install -r .\app\build\outputs\apk\release\app-release.apk
```

也可把 APK 发到安卓手机，点开后按系统提示安装。旧版“设置 → 版本与更新 → 选择 APK 更新包”也可导入本次 1.4.3 包。新版该入口名为“选择本地 APK 更新包”。仍使用本项目原有固定签名，未上架应用商店。

## 写下问题并让 AI 出题

打开 App，只需填写问题，例如“这周末要不要去露营”或“喝茶还是咖啡”。点击“确认两面含义”，本机提取常见二选一句式；复杂表述经你确认后使用已配置的大模型提取，两种选择展示供核对。模型仍无法明确、多项或过长问题需补充，可展开“手动编辑两种选择”。修改问题时清空上次两种选择，重新提取，防止沿用另一事项的选项。

确认页面和投币前页面的 A、B 选项均可直接点击修改，预填当前内容；取消不更改，保存保留事项和另一面。空白或与另一面相同的选择不能保存。修改后须重新勾选确认；已有题组和回答清空后重新出题，历史保持原快照。

首次及升级默认优先联网 AI 出题。默认 DeepSeek 地址和模型已预设，只需在设置填写个人 API Key 并保存；换其他接口可读取模型列表后选择。核对两种选择后点击“让 AI 生成相关问题”，同意发送本次事项和 A/B 后生成五道相关题目。没有 Key 会引导配置，失败不会自动替换为固定题目。完成五题或主动跳过后投币，结果与历史保留在本机。只有在设置明确关闭“联网 AI 出题”或错误页主动切换，才使用固定的离线通用模板。

界面采用牛奶白底、珍珠白卡片与香槟金硬币，硬币有可读 A/B 与正反标签。流程是一屏一题，点击答案即自动进入下一题，无须点击下一题；主动跳过也自动前进。下一题从右侧进入，上一题从左侧进入；向左滑返回上一题并保留原回答，可重新点选修改；向右滑仅可沿用已回答/跳过的题目前进，未回答时不会越过题目。保留返回按钮方便无障碍操作，过渡中禁用重复点击，旧题回调不改变新题。最后一题完成后进入投币页。编辑事项或 A/B 会作废旧题组和答案。系统字体放大、小屏可以滚动，夜间跟随系统使用暖深色背景、保留金色硬币，历史按需渲染。尊重系统关闭动画设置，音效与振动可在设置中分别关闭。

投币配有真实金属硬币录音片段，替换原电子合成音：抛起约 0.38 秒，落定约 0.90 秒，保留轻弹、旋转与碰撞余响，峰值归一化至 20% 并淡入淡出。音频随 App 打包，跟随媒体音量，媒体音量为零或设置关闭音效时不播放。来源和 CC0 许可见 [AUDIO_LICENSES.md](AUDIO_LICENSES.md)。应用内音量键调整媒体音量，不强制修改音量。动画约 1.6 秒，绕水平轴上下翻转，按钮按压轻缩放，落定轻振动。音效最终响度和振动手感仍需在实体手机上确认。

结果页“看到结果，你的感觉是？”去掉括号说明；点击感受有按压效果，保存成功后显示金色选中态、勾选标记和“已记录你的感受”，振动开启时有轻触反馈。可以更改感受；重开应用保留反馈，投币结果和概率保持原快照。

## 大模型 API（默认 DeepSeek）

服务地址是大模型 HTTPS 接口地址；API Key 是调用密钥，不是账号密码。默认 DeepSeek 地址和模型已预设，无需手填模型 ID。换其他兼容服务时，填写地址和 Key，点击“自动读取可用模型”读取该地址下的 `GET /models`：只有一个模型时自动选用，多个模型时保留已有选择或让你点选，不随意猜测付费模型。列表只代表服务返回的模型，不保证每个模型都支持出题所需的 Chat Completions 和 JSON 输出。接口不提供列表或需要自定义 ID 时，再展开高级设置手填。选择后保存，已有用户的地址和模型不会因升级被覆盖。

读取列表前确认目标地址，只携带 API Key，不发送事项、回答或历史，也不生成题目。新填 Key 在点击保存设置前仅留在本次界面内存；留空时仅使用该地址已加密保存的 Key，改地址不会把旧 Key 自动发过去。列表请求不跟随重定向，限制响应大小并校验模型 ID。

- 默认 Base URL：`https://api.deepseek.com`，路径：`POST /chat/completions`。
- 默认 model：`deepseek-flash`，不自动换成其他付费模型。
- `stream=false`、`response_format={"type":"json_object"}`、`max_tokens=4096`；仅 api.deepseek.com 请求附带 `thinking={"type":"disabled"}`，其他兼容接口省略此专属参数。
- Key 为空返回 NotConfigured，不发送请求。首次（以及每次新出题）发送前明确确认本次事项和 A/B 将发送至配置的地址并可能产生 API 用量。
- 点击“测试已保存配置的连接”，确认少量 API 用量后才执行。测试只发送简短测试内容；取消可中断请求。
- “删除已保存的个人 Key”立即删除密文与 Keystore 加密密钥。
- 生成失败须主动重试或明确选择离线通用题目，绝不暗中降级。online blocked 只允许返回编辑，不能用降级绕过。

本次没有提供真实 Key，真实 DeepSeek 网络联调未执行。MockWebServer 使用本地测试 TLS 和虚构凭据，验证 HTTP 适配器；不把测试响应当作真实联网成功。

接口依据（2026-10-10 核对）：[首次调用](https://api-docs.deepseek.com/)、[JSON 输出](https://api-docs.deepseek.com/guides/json_mode/)、[思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)。工具兼容关系：[AGP 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes)。

## 算法和保存规则

固定立场得分 A 强倾向 1、A 弱倾向 0.75、中立 0.50、B 弱倾向 0.25、B 强倾向 0。主动跳过记 0.50，未答仍是未答，不能进入投币。五题等权：

```text
s = 五题得分之和 / 5
pA = 0.70 × 0.50 + 0.30 × s = 0.35 + 0.30 × s
pB = 1 - pA
抽取 u ∈ [0,1)，严格 u < pA 才为 A，否则为 B
```

s=0/0.5/1 对应 pA=0.35/0.50/0.65。概率不预先取整，显示时才取整。这是规则对本次答案的偏好估计，不是心理学量表、内心想法百分比、成功率或正确率。

Room 保存一份草稿、冻结题组与每枚硬币的完整快照（事项、A/B、题目、选项、答案/跳过、题组和算法版本、来源、模型配置、s、pA/pB、tossId、时间、次数、可选反馈）。Keystore 密钥/API Key 不进入历史。

唯一 tossId 与结果在同一个数据库事务中保存，再播放约 1.6 秒动画。并发点击复用同一已保存结果。重组、旋转、恢复、看历史不重新抽样。进程在事务提交之前终止时没有对用户完成的结果，事务回滚；重新明确投币才可能抽样。保存失败不显示完成结果，当前进程重试复用尚未提交的候选结果。

“明确再投一次”生成新 tossId、保留旧结果并关联同一 decisionId。修改答案后会保存新快照，不重算旧记录。再次投币次数不因删除单条而回退。多投几次不更准确。反馈只保存用户主动选择，不影响结果。

删除单条仅删这枚硬币；如删当前显示结果，同时清除其草稿引用。清空需二次确认，并清除当前草稿/结果引用，防止删除后还保留一份敏感快照。开始新决策保留历史。应用正常覆盖更新保留数据；卸载会删除本地数据，更新时不要先卸载。

## 版本管理与 APK 更新

`version.properties` 是唯一 App 版号来源，当前 versionName=1.4.0、versionCode=8。schemaVersion=1、题组版本=3、算法版本=1、Room 数据库版本=1 分别管理。Git 记录源码、提示词、测试、Room schema、变更日志；不记录本地签名、密钥、缓存、构建文件。此次交付建立 `v1.4.0` 标签，保留之前版本用于升级回归。旧草稿与历史使用原题目快照，不强制改写。

工程目录保留本地 Git 仓库；交付另有源码 ZIP 与 `coin-decision-1.4.0.bundle`，后者可用 `git clone <bundle路径> <新工程目录>` 恢复提交和标签。私密签名需另行恢复。

在“设置 → 版本与更新”中配置公开 GitHub 仓库，检查、下载新版；也可以“选择本地 APK 更新包”导入安装包。App 会检查 APK 可解析、包名相同、签名证书相同、versionCode 严格更高，然后显示版号并调用安卓系统安装器。系统可能要求允许来自本 App 的安装，返回后点击“继续安装已校验更新”。只在点击时联网，安装需系统确认。系统安装器仍负责实际签名与安装验证。

后续更新开发步骤：

1. 修改 `version.properties`，versionCode 必须递增，例如下次 `versionName=1.4.1, versionCode=9`。
2. 固定 applicationId=`cn.coindecision.app`，固定首版签名。不要在 debug 中增加 applicationIdSuffix。
3. 本机 `.signing/coin-test.jks` 与 `signing.properties` 是本 App 新生成的固定测试签名，已被 Git 忽略。必须私下备份；任何能拿到签名文件的人都可能伪造更新。测试 keystore 密码为标准 `android`，不能将这个签名用于正式公开产品。
4. `build.ps1` 在固定签名缺失时必须拒绝生成安装包，以免误用其他机器的默认 debug 签名。正式公开发行前应另建保管严格的发行签名；测试签名与正式签名通常不能互相覆盖，需规划迁移。
5. 涉及 Room 结构变更时提升数据库版本，提供显式非破坏性 Migration 和迁移测试。当前 v1 不需迁移，禁止 destructive migration；已完成快照不重新计算。新程序读取旧版本快照时应兼容旧算法/题组协议。
6. 构建、测试，再用系统安装器或 `adb install -r` 更新，验证已有草稿和历史保留。记录 CHANGELOG，创建对应 Git 标签。

更新源使用 GitHub Releases，无需另建更新服务器。标签与 APK versionName 必须相同；仓库与发布操作参见 GITHUB_UPDATES.md。

## 架构与未来代理契约

单个 app 模块：`ui/` 为 Compose + ViewModel/StateFlow，`domain/` 为类型契约、评分、安全与更新规则，`data/` 为 Room/DataStore/Keystore/安装器，`ai/` 为 DecisionAiService。无注册、支付、广告、社交、云同步或埋点。

公共服务的替换边界是 `DecisionAiService.generate(DecisionRequest): AiResponse`，未来实现 `ProxyDecisionAiService` 并替换 ViewModel 服务选择，不改变本地评分和投币。代理建议 `POST /v1/decision/questions`，使用短期用户访问令牌（绝非 DeepSeek 开发者 Key），请求：

```json
{"schemaVersion":1,"matter":"这周末要不要去露营？","optionA":"去露营","optionB":"不去露营"}
```

响应沿用 AiResponse v1。status 为 ok 时返回五题，各维度一次、每题五个立场一次；blocked/needs_clarification 返回 message 与空题组。当前版本化提示词在 `app/src/main/assets/ai-system-v3.txt`，保留 v1/v2 以便追溯。v3 要求具体相关题干、不照抄固定模板；第四题围绕本次不确定性询问主观稳妥程度，沿用 uncertainty_acceptance 维度 ID，计分方向不变。严格拒绝额外控制字段、重复 ID、缺失枚举、非法长度等；文本只按纯文本渲染。

未来路线 App → 自有服务端 → DeepSeek，开发者 Key 只放服务端，补齐身份校验、限流、用量上限、隐私约定。公共服务端代理仅保留接入边界，本次未开发部署。不得把开发者公共 Key 放入 APK。

## 隐私与边界

非敏感设置保存在 DataStore；个人 Key 使用 Android Keystore AES-GCM 加密后保存在独立 SharedPreferences，排除所有备份（包括历史、草稿与设备迁移）。加密失败或设备密钥丢失时清除不可用 Key 并提示重新输入。Base URL 仅接受无账号、无查询、无片段的 HTTPS；改变地址删除旧 Key，原 Key 不发给新站点。禁止 HTTP 与重定向转发 Authorization。联网只发本次事项、A/B 与系统出题提示，不发送用户回答或历史，无请求日志。

离线保守关键词规则结合 online blocked 校验处理高风险事项，但无法覆盖所有风险，不能把工具当作医疗、财务、法律判断。模板与固定语义样本有人工方向检查，结构测试不代表保证 AI 的语义可靠性。

## 验证

实际执行结果见 `TEST_REPORT.md`，固定方向样本见 `SEMANTIC_REVIEW.md`。单元测试验证评分/概率/随机边界、补充抽样检查、结构契约、TLS 模拟 HTTP 错误和受控重试；设备测试覆盖事务并发、持久化恢复、Key 加密与主机绑定、离线 UI 和风险阻断。真实 Key 联调与实体手机体验未执行，不宣称成功。
