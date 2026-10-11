# 硬币音效来源

1.4.2 起使用真实硬币录音片段，替换原正弦合成音。

- 原作：**Coin sounds**，作者 **Breviceps**，2019-02-04。
- 原作及许可说明：https://freesound.org/people/Breviceps/sounds/458399/
- 许可：Creative Commons Zero（CC0），https://creativecommons.org/publicdomain/zero/1.0/ 。作者明确允许复制、修改、分发及商业用途。
- 使用来源：该页面公开的高质量预听文件 https://cdn.freesound.org/previews/458/458399_9159316-hq.mp3 。原录音包含硬币在木桌上旋转、掉落和计数的声音，Zoom H2n 录制。
- 处理：取 1.60–1.98 秒作抛起音、2.55–3.45 秒作落定音；转 22050 Hz、单声道、16-bit little-endian PCM；去直流、峰值归一化至 20%，首 5 ms 和尾 30 ms 淡入淡出。处理后文件位于 app/src/main/res/raw/coin_launch.pcm 与 coin_landing.pcm。
- 文件打包在 App 内，无须联网播放；遵从手机媒体音量、系统静音与 App 音效开关。此致谢保留在源码发行包内，虽 CC0 不要求署名。
