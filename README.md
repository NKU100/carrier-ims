# Carrier IMS

[English](README.en.md)

一个 LSPosed 模块，为 Google 未列入白名单的运营商打开 Pixel 上的 VoLTE、VoWiFi、VT 与 VoNR。

## 与现有方案的区别

[vvb2060/Ims](https://github.com/vvb2060/Ims) 和 [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch) 调用 `CarrierConfigManager.overrideConfig()`。这个 API 要求 `MODIFY_PHONE_STATE`，所以它们得经 Shizuku 借来 shell 的权限身份。Google 一直在加固这条路，在 Android 17 的 Pixel 上 `persistent=true` 已经写不进去——每次开机都得重写一遍。

本模块改拦**读取**一侧：在 `com.android.phone` 进程内拦截 `CarrierConfigLoader.getConfigForSubIdWithFeature`，把覆写项合并进它的返回值。读取方本就在权限检查之内，因此不需要借任何权限，不需要 Shizuku，也不存在会失效的 override 状态。模块不写任何磁盘状态：跑 `dumpsys carrier_config` 看到的仍是运营商原值，而每一个 `CarrierConfigManager` 的调用方拿到的都是覆写后的值。

设计与实测数据见 [`docs/superpowers/specs/2026-09-12-carrierims-xposed-design.md`](docs/superpowers/specs/2026-09-12-carrierims-xposed-design.md)。

## 要求

- Android 8.0 及以上（`minSdk 26`）
- LSPosed v2.2.0 及以上——模块使用 libxposed API 102，旧版本没有实现
- 同时不能有别的工具在覆写 carrier config

## 安装

1. 自行编译或下载 release APK 并安装。
2. 在 LSPosed 中启用 **Carrier IMS**。作用域固定为 `com.android.phone`。
3. 重启 `com.android.phone`，或重启手机。

## 改了哪些配置

VoLTE、VT、UT 补充业务；完整的 VoWiFi 组合，含模式与漫游模式选择器；跨 SIM 通话；让「增强 4G LTE」开关可见可编辑；VoNR 以及 5G NR SA/NSA。此外还会翻转 `VoIMS opt-in` 这个 provisioning 标志——部分运营商拿它作为 VoLTE 前面的第二道门。

完整清单在 [`CarrierOverrides.java`](app/src/main/java/io/github/nku100/carrierims/CarrierOverrides.java)。模块有意不做设置界面，改值请直接改这个文件再重新编译。

## 验证

`tools/probe/run-probe.sh` 会编译一个探针推到设备上，以 shell 身份经 binder 调用 carrier config 服务，再把每个注入的 key 与一份独立硬编码的期望值逐项比对。它为每个 key 打印一行 PASS/FAIL，任一不符即以非 0 退出。

```
ANDROID_HOME=<sdk> bash tools/probe/run-probe.sh
```

接了多台设备时用 `DEVICE=<serial>` 指定。

**跑之前先取基线。** 模块尚未启用时，这些 key 里有一部分本来就等于机器默认值，不取基线的话「全部 PASS」无法归因于模块。

要从读取侧这样测，而不是看 `dumpsys carrier_config`：模块不改任何存储状态，`dumpsys` 按设计就该一直显示运营商原值。同理也不要用 logcat 判断模块有没有加载——logd 的 main 缓冲区默认只有 256 KiB，开机日志很快就把模块那行冲掉了。

## 编译

需要 JDK 21，以及装了 platform 37.2 与 build-tools 37.0.0 的 Android SDK。

```
./gradlew :app:assembleRelease
```

请安装 **release** APK。debug 变体会额外带上 D8 为 API modeling 生成的约 760 个 `android.*` 桩类——因为这些名字由 boot classloader 优先解析，它们无害，但没必要塞进一个系统进程。

## 致谢

carrier config 的取值沿用 [vvb2060/Ims](https://github.com/vvb2060/Ims) v3.1。

## 许可证

Apache-2.0，见 [LICENSE](LICENSE)。
