# 偏心硬币 1.4.2 验证记录

## 1.4.2 编辑两面、自动换题和金属硬币音效

- 最终非调试 Release：versionName=1.4.2、versionCode=10，原包名与固定签名。APK SHA-256：5790c34457b59c4bc04f317de687cd8fa2d5c3f29032a296dfafcb2c9dc24c8c。
- 43 项单元测试全通过，新增 PCM little-endian 解码及非法字节拒绝，实录资源长度、峰值、首尾淡化检查通过；现有 AI、模型列表、概率和更新测试继续通过。lint 0 错误、13 条既有警告，源码 diff 空白检查通过。
- 专用 API 35 模拟器最终 Release 完整回归：27 个用例中 22 个实际通过、5 个按设计留给独立升级/下载阶段而跳过，0 失败。字体放大至 1.3 倍后，9 项界面用例单独重复通过；补充未回答时右滑不越过题目检查。
- 编辑验证：点击 A/B 预填修改、取消保留原文、空白/重复拒绝、另一面与事项保留、修改后重新勾选确认、重建 Activity 后修改保留、生成题目的选项使用新文字；状态测试覆盖投币前修改清空旧题组与回答并回确认页。
- 换题验证：点击答案自动前进，旧题重复回调不推进新题，未知选项拒绝；左滑返回保留原选择，右滑沿用已答题，重选自动前进；主动跳过计分保持中立，最后一题进入投币页，五题概率保持原算法。保留前一题按钮用于无障碍操作，过渡中阻止重复点击。
- 反馈验证：括号说明不存在，点击后保存并显示勾选和选中状态；改选后取消旧选中，重建 Activity 后反馈保留。原投币结果及概率保持。修复异步旧反馈写回另一次投币界面的风险。
- 音频资源来自 Breviceps 的 CC0 真实硬币录音，许可、来源和裁剪区间在 AUDIO_LICENSES.md。Android 设备测试读取实际打包资源、创建 AudioTrack、断言媒体音量通道、PLAYSTATE_PLAYING 及播放位置前进。模拟器无实体扬声器，不能据此断言实体手机的最终响度或主观音色。
- 1.4.1 → 1.4.2 实际同签名升级三个独立阶段通过：旧版写入快照/虚构 Key/自定义模型、新 APK 包名签名与更高版号检查、新版覆盖后历史/结果引用/加密 Key/模型设置保留。专用模拟器已关闭；验证日志及截图在 verification-1.4.2。
- 未使用真实付费 API Key、未在实体手机试听/安装、未重复公网更新下载；API 协议、题目生成提示词、评分和数据库格式保持。历史公网更新验证保留如下。

## 1.4.1 模型列表与可选手填

- Release 构建、原签名、versionName=1.4.1、versionCode=9，APK SHA-256：0add1decfd1693c0b292fe93e981ba9e57e8e1499f034a289a1239deea70efbd。
- 42 项单元测试全部通过；新增模型列表 GET 协议、无 Key 不联网、鉴权/不支持/非法/空/超大列表拒绝、唯一模型自动选择/保留已有选择/多个不猜测等覆盖。列表读取只带虚构 Key，请求体为空。
- 专用 API 35 模拟器运行最终非调试 Release：24 个设备用例，19 个实际通过，5 个按设计留给独立升级或下载阶段而跳过；其中 8 个界面用例另行重复全通过。新增默认模型预设可见、手填默认隐藏且高级设置可展开/收起。
- 首次完整测试出现一项“修改问题后立即确认”的时序断言失败；为该测试增加等待输入状态及确认页面同步，完整回归与界面复测均通过，初始失败日志保留于 device-initial-timing-failure.txt。本轮未以重复运行掩盖失败，也未更改提取算法。
- 1.4.0 → 1.4.1 实际同签名升级：旧版写入历史和虚构 Key、自定义模型；旧版检查新版包名/签名/更高版号；覆盖安装后历史快照、当前结果引用、加密 Key 与已有模型配置保留。三个独立阶段全部通过，记录在 verification-1.4.1。
- lint 0 错误、13 条既有警告；源码 diff 空白检查通过。请求限 HTTPS、不跟随重定向、响应读取有大小限制；改地址/Key、保存或删除 Key 时取消旧请求并清除旧模型列表，旧地址的 Key 不自动用于新地址。
- 没有真实 API Key，模型列表与出题协议使用本地 TLS MockWebServer 和虚构凭据验证；没有进行真实付费模型调用或实体手机安装测试。本轮没有重复公网更新下载，1.3.1 的实际 GitHub 下载验证记录保留如下。

## 1.4.0 自动提取与相关 AI 出题

- Release 构建、原签名、versionName=1.4.0、versionCode=8，APK SHA-256：f1335a2e43ce6b8723c4364d4fb4bb77057cc9a817d16e007405a34411e4d70b。
- 38 项单元测试全部通过；覆盖常见二选一句式、双重否定、开放式/多项/超长输入拒绝、模型提取和澄清/blocked/非法响应、特定事项问题原样透传、兼容接口省略 thinking、现有规则及更新接口。
- 专用 API 35 模拟器上运行非调试 Release 的完整设备回归，23 个用例：18 个实际通过，5 个需要单独阶段的更新用例跳过。新增只有问题的自动提取、更改事项清除旧自动选项、无 Key 进入配置而非固定题目、模型提取先核对再出题、取消/编辑不受旧提取响应覆盖。
- 界面复测曾出现一次提取后断言未找到选项，独立用例复测通过；补充相同输入幂等处理与状态回归，避免重复输入通知清空已提取的选项。最终包重新完整跑 23 个用例通过，随后 7 个界面用例再次独立全通过；初始失败记录与最终日志一并保留。
- 1.3.1 → 1.4.0 实际同签名升级：旧版写入完整历史和虚构 Key、新 APK 包名/签名/更高版本检查、新版覆盖后历史快照/结果引用/密钥保留，3 个独立阶段通过。相关记录在 verification-1.4.0。
- lint 0 错误，13 条既有警告；原始源码 diff 空白检查通过。原算法、API 请求/题目响应契约、数据库与历史快照格式保留，新增提取能力和 v3 提示词。
- 模型调用使用本地 TLS MockWebServer 和虚构凭据验证协议。没有真实 API Key，未进行真实付费调用，无法据此宣称实际模型题目质量已验证；未在实体手机安装测试。本轮没有重新运行真实 GitHub 下载，1.3.1 的实际公网链路验证记录保留如下。

## 1.3.1 GitHub 仓库绑定与发布

2026-10-11。公开仓库 https://github.com/wang503133712-commits/coin-decision 已创建，账号连接和仓库 push/admin 权限实际确认。App 预填这个官方更新源；已保存的自定义仓库不会被覆盖。

- versionName 1.3.1、versionCode 7，保持原包名、签名、API 与数据库；非调试 Release。
- 29 项单元测试通过，0 失败；lint 0 错误、13 项既有警告。
- 1.3.0 → 1.3.1 覆盖升级：APK 检查、历史快照/结果引用及加密虚构 Key 保留，三个独立阶段通过。
- 19 项 Release 设备回归中 14 项通过、5 项需独立参数的用例跳过，0 失败。升级三项、未来同签名 APK 下载一项和真实 GitHub 下载一项，均已在独立阶段执行通过。
- 未来 APK 夹具为 1.3.2/code 8，模拟发布信息到实际安卓包名/签名/版号校验链路通过，错误标签实际被拒绝。
- 已正式发布 v1.3.1，APK、源码 ZIP 和 Git bundle 的下载链接在 Release 页可见，APK digest 与本机一致。GitHub 发布标签对应源码树与本地 v1.3.1 的 Git tree SHA 完全一致：41a1509572202f26f803da6b5742dce0bb7dce02。
- 发布后实际调用 GitHubUpdates，通过公开 GitHub API 检查最新版、经 GitHub CDN 下载完整 APK，核对 SHA-256 与已安装最终 Release 完全相同；设备测试 26.139 秒，OK (1 test)，记录在 verification-1.3.1/github-live-download.txt。这是实际联网结果，没有用模拟响应替代。
- 测试模拟器原本 Wi-Fi 与移动数据均关闭，最初出现 DNS/网络不可达；开启该专用模拟器网络并使用电脑已有代理后通过，未更改宿主电脑网络或 TLS 校验。初始失败记录保留在 network-before-wifi-fix.txt；原生 Git 读取中断通过单次使用已有代理恢复，上传使用 GitHub 连接器完成。

APK SHA-256：`9592ee0cfbc57d31ea814d1252bbff459a3997213c5d78fbc1a98502f478b635`。签名材料、个人 Key、缓存和设备数据不会上传；源码 ZIP 和版本历史 bundle 排除私密签名。

## 1.3.0 GitHub 在线更新

2026-10-11。Release / code 6，原包名、固定签名、API 和数据库格式保留。未配置用户的 GitHub 仓库，未上传或创建公共 Release。

| 检查 | 实际结果 |
|---|---|
| 单元测试 | 29 项通过、0 失败；新增 10 项 GitHub 测试，包含仓库/数字版本、公开 API 请求不带凭据、旧版本、错误发布/附件、HTTP 错误、元数据大小、HTTPS/CDN 地址、下载长度/SHA-256、失败清理和取消清理 |
| 构建与 lint | testReleaseUnitTest、lintRelease、assembleRelease、assembleDebugAndroidTest 全部成功；lint 0 错误、13 项警告未隐藏 |
| 最终 Release 设备回归 | 专用 API 35 模拟器实际非调试 Release：18 项中 14 项通过、4 项升级/下载夹具专用用例按设计跳过，0 失败；4 项均在独立阶段执行通过 |
| 设置 UI 与持久化 | GitHub 输入拒绝非 GitHub 地址；本地更新入口可用；仓库保存/重读独立于 API 设置，保存旧结构的 API 设置不会覆盖更新仓库 |
| 1.2.2 → 1.3.0 | 原交付 Release 覆盖升级；旧版识别 code 6 同签名 APK，历史快照、当前结果引用和加密虚构 Key 保留，三个阶段通过 |
| 下载到真实包校验 | 单独构建 1.3.1/code 7 同签名测试 APK（仅在 work 保存），模拟公开 Release 和附件响应；调用实际 GitHubUpdates → 下载进度/长度/SHA-256 → ApkUpdater → 安卓包解析/签名/版号校验，通过；错标签也实际拒绝并清理 |
| 签名及版本 | apksigner 验证通过，证书 SHA-256 与旧版相同；包名 cn.coindecision.app，versionName 1.3.0、versionCode 6，未启用 application-debuggable |

最终 APK：`偏心硬币-安卓-1.3.0.apk`，7,713,578 字节。SHA-256：`143463da35d5e806ebc1685b7c8c356cd415608eef794a2176b0f53338bed9b5`。

证书 SHA-256：`d8601252ed767ef721913038866fa7a555afeb7afe022d684c74e7a578baaac7`。证据在相邻 `verification-1.3.0/`，含单元 XML、lint XML、构建/升级日志、Release 设备输出和独立下载/真实包检查记录。源码及 Git bundle 导出检查排除签名私钥和密码配置。

公开仓库联网和安装仍依赖手机网络及系统确认。由于用户仓库尚未提供，未执行该仓库的真实 GitHub 发布/下载或实体手机安装器操作；模拟响应测试不描述为真实 GitHub 下载成功。真实 API 调用和实体手机响度沿用此前未实测范围。发布与使用方法见 GITHUB_UPDATES.md。

## 1.2.2 音效与上下翻转修复

2026-10-11。Release / code 5，原包名、签名、API、数据格式保留。

真实 AudioTrack 回归在旧版先失败：音效路由为系统通道 1，媒体通道应为 3；仅修路由后仍失败，播放状态为 STOPPED 1，而不是 PLAYING 3。定位到 MODE_STATIC 初始状态是 STATE_NO_STATIC_DATA，旧代码要求 STATE_INITIALIZED 后才写入，因此写入和播放都被跳过。修正为先完整写入 PCM、再检查初始化状态并播放，测试通过。依据：[Android AudioTrack 状态与静态播放文档](https://developer.android.com/reference/android/media/AudioTrack#STATE_NO_STATIC_DATA)。

| 检查 | 结果 |
|---|---|
| 真正播放音效 | 抛起和落定各创建真实音轨，媒体路由、PLAYING 状态及播放进度前进均断言通过；不是只检查波形 |
| 单元与构建 | 19 项 Release 单元测试通过；lintRelease、assembleRelease 和测试 APK 构建成功；lint 0 错误 |
| 最终非调试包回归 | 实际安装的 Release 上，15 项设备测试中 12 项通过、3 项升级专用测试按设计跳过，0 失败 |
| 覆盖升级 | 原交付的 1.2.1 Release → 1.2.2 Release：更新包识别、历史及当前结果引用保留、加密虚构 Key 保留，3 个独立阶段实际通过 |
| 签名 | apksigner 验证通过，证书与原版相同 |
| 上下翻转画面 | 正常速度手动投币并录制 14 秒视频；逐帧确认硬币翻到水平薄边，再展开落定，实际呈现上下翻转；证据为 manual-animation.mp4 和 animation-frames.png |

APK：`偏心硬币-安卓-1.2.2.apk`。SHA-256：`7d7e4b4733553f5d6f5afb9e11f40d30a101a51e0d47402b47286310835c3840`。

证据保存到相邻 `verification-1.2.2/`：旧版失败记录、仅改路由后的失败、修复后的音轨测试、完整 Release 回归、构建与升级日志。用户需保持设置中音效开启和媒体音量非零；未强制修改音量，未将模拟器播放进度断言描述为实体扬声器试听。真实手机响度、振动和真实 API 仍未实测。

## 安卓自用最终 Release 交付

2026-10-11。版本 1.2.1 / code 4，包名 `cn.coindecision.app`，最低 Android 8.0。仅提升版号并采用 Release 构建，界面、音效、算法、API 和数据格式沿用 1.2.0。

| 检查 | 结果 |
|---|---|
| Release 构建与单元测试 | testReleaseUnitTest、lintRelease、assembleRelease 成功；19 项单元测试通过，0 失败 |
| 关闭调试 | APK badging 无 application-debuggable；实际安装包 flags 无 DEBUGGABLE |
| 签名 | apksigner 验证通过；证书 SHA-256 与 1.2.0 相同 |
| 覆盖更新 | 在专用 API 35 模拟器执行 1.2.0 → 1.2.1 Release；旧版检查新版 APK 成功；保存前后完整历史、当前结果引用和加密虚构 Key 一致，3 个独立阶段全部通过 |
| Release 设备/UI 回归 | 在实际安装的非调试 Release 包运行测试；14 项中 11 项通过、3 项升级专用测试按设计跳过，0 失败；跳过的 3 项已在上述独立阶段执行通过 |
| Lint | 0 错误；现有警告未被关闭或隐藏 |
| 私密材料 | 固定签名文件仍仅保留本机，源码 ZIP 不含签名私钥或密码 |

最终 APK：`偏心硬币-安卓-1.2.1.apk`，7,697,194 字节。

SHA-256：`9afa53abde75cc54ddcb147908a8398e1842de1da5914c12d182afb83bd31b45`。

证书 SHA-256：`d8601252ed767ef721913038866fa7a555afeb7afe022d684c74e7a578baaac7`。

本轮记录在相邻 `verification-1.2.1/`，含 Release 构建日志、单元 XML、lint XML、实际 Release 设备测试输出、三阶段升级结果。正常覆盖安装不会执行测试或清空数据；测试仅在工作区专用模拟器进行。

最终版沿用原本固定签名，以保证已有安装可升级；未上架或公开发行。真实 API Key 调用、实体手机音效响度/振动、各厂商完整安装器点击流程尚未实测，不承诺绝对零 bug。

## 本次最终验证

2026-10-10，版本 1.2.0 / code 3。第四题及 v2 提示词、对称 A/B、问号硬币、居中小按钮、翻转动画和概率说明均已进入最终 APK；接口、旧提示词和数据兼容保留。

| 范围 | 实际结果 |
|---|---|
| 单元测试 | 19 项通过：规则 7、DeepSeek TLS 模拟 11、音效 1；0 失败 |
| 最终设备回归 | API 35 专用模拟器，JUnit XML 共 14 项：11 通过、3 项独立升级用例按设计跳过，0 失败、0 错误 |
| 新界面专项 | 完整五题 → 中性待投硬币 → 修改回答 → 返回待投；断言修改按钮与主按钮水平中心一致、宽度小于主按钮 40%；最终截图另行保存 |
| 问号居中 | 实际 1080×1920 截图：硬币中心 (539.5,566.5)，问号字形边界中心 (539.5,567.0)，偏差 (0,0.5) 像素；字形独立居中，下面的标签不参与定位 |
| 同签名覆盖更新 | 1.1.0/code 2 → 1.2.0/code 3：APK 校验、升级前快照和覆盖后数据/结果引用/加密测试 Key 保留三个独立阶段通过；该检查之后仅调整了绘制居中与颜色 |
| 构建与静态检查 | 单元测试、lint、主 APK、设备测试 APK 构建通过；lint 0 错误、13 警告 |
| 鸿蒙 | 独立原生工程 21 项电脑测试通过；未进行 SDK 编译、签名、HAP 安装或设备验收，不能以 Android 成绩替代 |

证据位于上级 `verification-1.2.0/`，实际待投页与第四题截图分别为 `ready-preview-1.2.png`、`question-four-preview-1.2.png`。本次未重复上一版小屏/大字体全部组合，也没有实体手机或真实 API Key；不能证明绝对零 bug。

最终 APK SHA-256：`3ed2e1f4f51d86c094eb36ebb281c0b0114b62245852ac71e7010a2388e6116c`。

## 1.1.0 历史验证记录

日期：2026-10-10。最终外观：牛奶白底、珍珠白卡片、香槟金硬币。测试在本次新建的独立 Android 15 / API 35 模拟器 `emulator-5560` 中执行，未修改用户原有安卓项目或模拟器数据。

## 三轮验证

| 范围 | 实际结果 |
|---|---|
| 核心逻辑与 HTTP 模拟 | 18 项单元测试通过：规则 6、DeepSeek TLS 模拟 11、音效波形 1。 |
| 原生设备/UI | 13 项设备用例中 10 项通过、3 项专用升级用例按设计跳过；0 失败、0 错误。升级用例另行执行，见下方。 |
| 小屏与大字体 | 360×640 dp、字体 1.5 倍、深色模式、系统动画关闭、网络关闭下，离线 UI 3 项再次通过。 |
| 同签名覆盖升级 | 1.0.0/code 1 → 1.1.0/code 2：真实 APK 导入校验通过；升级前写入快照；`adb install -r` 成功；升级后全部快照字段、当前结果引用和加密测试 Key 保留。3 个独立阶段实际断言均通过。 |
| 构建与签名 | `assembleDebug`、测试 APK 构建、`apksigner verify` 通过。 |
| Android lint | 0 错误、13 警告；主要为依赖/AGP/目标 API 更新建议、Kapt/KTX 建议及未使用的 tagline 资源。未通过升级全局工具规避。 |
| 视觉复核 | 真正运行的首页、结果页、深色大字体截图已查看，确认金色 A/B 标签、圆角裁切、滚动布局与颜色；不是设计稿。 |

## 重点覆盖

- 五题全部立场、混合回答、主动跳过、未答与未知选项；35%/50%/65% 边界，严格 `u < pA`；补充固定种子抽样检查。
- 合约版本、维度、题目/选项数量、全局 ID、枚举、长度、额外控制字段；常见高风险事项阻断。
- 12 个并发投币请求只抽样一次；恢复、数据库重开不重抽；明确再投保留旧记录；删除与清空的草稿引用一致。
- 人为让数据库事务失败，确认历史回滚、草稿保留；解除故障后重试复用原抽样，不通过保存失败偷偷重投。
- 完整离线输入 → 确认 → 五题 → 投币 → 结果 → 概率说明 → 重建 → 历史 → 删除；500 字长文本和否定句 A/B 方向。
- 被取消的旧请求即使迟到，也不能覆盖新事项；Key 加密、地址绑定、密文损坏处理和删除；音效开关持久保存且不改变其他配置。

## API 接口保留与复核

保留 `DecisionAiService.generate(DecisionRequest): AiResponse`、真实 `DeepSeekDecisionAiService` HTTP 适配、连接测试、个人 Base URL / Model / API Key 设置，以及 README 中未来代理的请求/响应契约。UI 换色没有删除网络能力，也没有把假响应当成联网成功。

TLS MockWebServer 验证：有效 POST 请求与 Bearer 鉴权、用户内容与系统提示分离、JSON mode、空 Key 不发请求、401/403 不重试、429/5xx 最多两次、超时、断连、取消、空/错误/截断 JSON、blocked/needs_clarification、非法地址和禁止重定向携带 Key。

升级测试使用明确标注的虚构 Key，验证旧版 Keystore 密文在覆盖升级后仍可读取，再删除该测试 Key。真实个人 Key 没有提供，真实 DeepSeek 调用未执行。

## 复现与证据

```powershell
.\build.ps1 -Tasks @('testDebugUnitTest','connectedDebugAndroidTest','lintDebug','assembleDebug')
```

设备测试会修改/清空受测 App 的测试草稿与历史，请只在专用模拟器或测试设备执行。普通安装、覆盖更新不运行这些测试，不清空数据。

升级专用测试需先装 1.0.0 与测试 APK，把 1.1.0 APK 复制到受测 App 的 `files/update-fixture.apk`，按顺序执行：

```text
am instrument -w -e updatePhase apk -e class cn.coindecision.app.UpdateApkTest cn.coindecision.app.test/androidx.test.runner.AndroidJUnitRunner
am instrument -w -e updatePhase seed -e class cn.coindecision.app.UpdateDataTest cn.coindecision.app.test/androidx.test.runner.AndroidJUnitRunner
# 在宿主机用 adb install -r 安装 1.1.0
am instrument -w -e updatePhase assert -e class cn.coindecision.app.UpdateDataTest cn.coindecision.app.test/androidx.test.runner.AndroidJUnitRunner
```

交付的 `verification/` 保存真实 JUnit XML、lint XML 与三个升级阶段/小屏测试日志。Gradle 控制台的总数会把重试/跳过算入展示，以上以 XML 和实际独立阶段断言为准。

最终 APK SHA-256：`c16792e040594764be8530d170584fd14aeeaccdd1c06d2ccbee0b879791bc05`。

固定测试证书 SHA-256：`d8601252ed767ef721913038866fa7a555afeb7afe022d684c74e7a578baaac7`。

## 未实测部分与限制

已验证范围未发现阻断问题，测试不能证明绝对零 bug。没有真实 API Key 或实体手机：未验证真实模型自然语言质量、真实计费与网络环境、扬声器响度、振动手感、实体设备完整 TalkBack 听读，以及各厂商系统安装器差异。实际覆盖升级由 adb 执行；App 内 APK 解析/校验已设备实测，完整系统安装器点击流程尚未逐台验证。

此 APK 使用本项目固定测试签名，不能作为正式公开发行签名。请私下保管签名备份；丢失签名无法制作可覆盖更新的同签名后续包。版本提升需继续保持包名与签名一致，数据库变更必须显式无损迁移。
