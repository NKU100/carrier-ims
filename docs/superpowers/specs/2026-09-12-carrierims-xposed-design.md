# Carrier IMS —— LSPosed 模块设计

日期：2026-09-12
包名：`io.github.nku100.carrierims`

## 背景

Google Pixel 只对白名单内的运营商开放 VoLTE / VoNR / VoWiFi。名单外的用户需要覆写 carrier config 才能用上这些设备本就支持的能力。

现有的无 root 方案（[vvb2060/Ims](https://github.com/vvb2060/Ims)、[kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)）都调用 `CarrierConfigManager.overrideConfig()`。这个 API 要求 `MODIFY_PHONE_STATE`，只有系统应用持有，所以它们要绕一大圈：经 Shizuku 拿到 shell binder，用它启动一个 Instrumentation，再调 `startDelegateShellPermissionIdentity()` 把 shell 的权限身份借到自己进程，最后才能调 `overrideConfig()`。

Google 还在持续加固这条路。Pixel 私有的 `CarrierConfigLoader` 里陆续加了 `isSystemApp()`，随后又加了 `isSdkSandboxUidInternal()` 堵掉 SDK sandbox 这个旁路。在 Android 17 的 Pixel 上，三个加固方法全部存在，`persistent=true` 已经写不进去：

```
overrideConfig: subId=1, persistent=false, overrides=PersistableBundle[{...}]
```

`persistent=false` 意味着配置不落盘。每次开机都得靠 Shizuku 把应用拉起来重写一遍。

本模块换一条路：在 `com.android.phone` 进程里拦截 carrier config 的**读取**，直接在返回值上合并覆写项。读取方在权限检查之内，所以不需要 `MODIFY_PHONE_STATE`，不需要 Shizuku，不碰 override 机制，`persistent` 这个概念随之消失，Google 后续加固也影响不到。

## 目标

- 开启 VoLTE、VT、UT 补充业务、VoWiFi 全套、VoNR、5G NR SA/NSA，并让相关开关在设置里可见
- 去掉 Shizuku 依赖
- 覆写在每次读取时生效，重启和运营商配置重载后自动保持
- 兼容多个 Android 版本与机型：按签名探测 hook 目标，不写死参数列表

## 非目标

- 不做 UI、配置文件、Activity。覆写项写死在代码里，改值需重新编译。
- 不 hook `isSystemApp()`，不调 `overrideConfig()`。完全不使用 override 机制。
- 不做多语言、不做更新检查。
- 不兼容 LSPosed 以外的 Xposed 框架。模块使用 libxposed API 102，只有 LSPosed 实现它，且需要 LSPosed v2.2.0 或更新的版本——其 framework 才实现了 `XposedInterface.API_102` 与 `ExceptionMode`。

## 关键事实（已在 Pixel 6 Pro / Android 17 实测）

反射 `/system/priv-app/TeleService/TeleService.apk` 里的 `com.android.phone.CarrierConfigLoader` 得到的真实签名：

```java
public PersistableBundle getConfigForSubId(int, String)
public PersistableBundle getConfigForSubIdWithFeature(int, String, String)
public PersistableBundle getConfigSubsetForSubIdWithFeature(int, String, String, String[])
```

核对 AOSP 实现后确认三点，它们共同决定了本设计：

1. `getConfigForSubId` 直接委托给 `getConfigForSubIdWithFeature(subId, callingPackage, null)`。
2. `getConfigSubsetForSubIdWithFeature` 也先调 `getConfigForSubIdWithFeature` 取全量，再按 key 过滤。
3. `getConfigForSubIdWithFeature` 返回的是新建的 `retConfig`，合并顺序为：默认配置 → `mConfigFromDefaultApp` → `mConfigFromCarrierApp` → `mPersistentOverrideConfigs` → `mOverrideConfigs`。

所以只 hook `getConfigForSubIdWithFeature` 一处，就覆盖全部读取入口；原地修改返回值不污染任何缓存；subset 的 key 过滤在我们之后执行，"只返回请求的 key" 这个契约依然成立。

## 架构

模块只在 `com.android.phone` 进程装一个拦截器。三个类，各自单一职责：

| 类 | 职责 | 依赖 |
|---|---|---|
| `CarrierImsModule extends XposedModule` | 入口。解析 hook 目标，安装拦截器 | libxposed api |
| `CarrierOverrides` | 纯数据。构造覆写用的 `PersistableBundle` | 无 |
| `ImsProvisioning` | 把 `KEY_VOIMS_OPT_IN_STATUS` 置为 ENABLED，每个 subId 只做一次 | Android SDK |

`CarrierOverrides` 不含逻辑，改覆写项只动这一个文件。`ImsProvisioning` 与 hook 无关，可独立测试。

## Hook 目标解析

`onPackageReady` 里加载 `com.android.phone.CarrierConfigLoader`，遍历 `getDeclaredMethods()`，按**方法名加返回类型 `PersistableBundle`** 匹配，不限参数个数。候选按优先级排列：

1. `getConfigForSubIdWithFeature`（Android 11 及以上）
2. `getConfigForSubId`（更早版本）

只 hook 匹配到的第一个。新版里第二个委托给第一个，两个都 hook 只会重复注入。

一个都没匹配到时写 `Log.ERROR` 说明类名与已枚举到的方法，然后放弃。模块决不静默失败——静默失败正是本项目要修的那类 bug。

## 数据流

```
任意进程 CarrierConfigManager.getConfigForSubId(subId)
  → binder → com.android.phone
      CarrierConfigLoader.getConfigForSubIdWithFeature(subId, pkg, feature)
        默认配置 → mConfigFromDefaultApp → mConfigFromCarrierApp
                → mPersistentOverrideConfigs → mOverrideConfigs
  → 拦截器（PRIORITY_HIGHEST，位于链最外层）
        result.putAll(CarrierOverrides.get())
  → 返回调用方
```

拦截器取 `PRIORITY_HIGHEST`。libxposed 里高优先级的拦截器位于链首，包住其余环节，因此它在 `proceed()` 之后所做的修改最后生效，优先级最高。

## 覆写项

沿用 vvb2060/Ims v3.1 的取值：

- IMS 注册状态可见：`KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL`
- VoLTE / VT / UT：`KEY_CARRIER_VOLTE_AVAILABLE_BOOL`、`KEY_CARRIER_VT_AVAILABLE_BOOL`、`KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL`
- 跨 SIM 通话：`KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL`、`KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL`
- VoWiFi：`KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL`、`KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL`、`KEY_EDITABLE_WFC_MODE_BOOL`、`KEY_EDITABLE_WFC_ROAMING_MODE_BOOL`、`KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL`、`KEY_WFC_SPN_FORMAT_IDX_INT=6`

后两个 key 在 `CarrierConfigManager` 里标了 `@hide`，public SDK 的 `android.jar` 中没有对应常量。carrier config 的 key 本质就是字符串，因此直接内联字面量 `"show_wifi_calling_icon_in_status_bar_bool"` 与 `"wfc_spn_format_idx_int"`，其余 18 个 key 一律引用 `CarrierConfigManager` 常量，让编译器充当拼写检查。
- 增强 4G LTE 开关可见可编辑：`KEY_EDITABLE_ENHANCED_4G_LTE_BOOL=true`、`KEY_HIDE_ENHANCED_4G_LTE_BOOL=false`、`KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL=false`
- VoNR 与 5G：`KEY_VONR_ENABLED_BOOL`、`KEY_VONR_SETTING_VISIBILITY_BOOL`、`KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY={NSA, SA}`、`KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY={-128, -118, -108, -98}`

## IMS provisioning

部分运营商在第二层拦住 VoLTE：即使 carrier config 已覆写，provisioning 标志 `KEY_VOIMS_OPT_IN_STATUS` 仍为 disabled。carrier config 的读取 hook 覆盖不到这一层。

拦截器每次命中时，取本次调用的 `subId` 参数。若该 subId 尚未处理过，就提交后台任务：先读当前值，已是 `PROVISIONING_VALUE_ENABLED` 就跳过，否则写入。

`ProvisioningManager` 的 `createForSubscriptionId`、`setProvisioningIntValue`、`getProvisioningIntValue` 以及 `KEY_VOIMS_OPT_IN_STATUS`、`PROVISIONING_VALUE_ENABLED` 全是 `@SystemApi`，public SDK 里没有，所以只能反射调用。常量值也由反射读取字段获得，而不是把 `68`、`1` 写进源码——数值属于框架内部约定，硬编码等于埋一个静默失效的坑。`com.android.phone` 是平台签名的系统应用，不受 hidden API 限制，且其 uid 本身持有 `MODIFY_PHONE_STATE`，因此反射之外不需要任何绕过手段。

用一个并发 `Set<Integer>` 记录已处理的 subId。按 subId 而不是按全局标志去重，换卡后新的 subId 仍会被处理。写入放后台线程执行，避免阻塞 binder 调用。

## 错误处理

`getConfigForSubId` 是全系统都在调的路径。拦截器抛出的异常会传播给每一个调用方，足以打挂 telephony。因此：

- 拦截器内 `putAll` 全程 try/catch。失败就返回未经修改的原始结果，只记日志。
- hook 目标解析失败写 `Log.ERROR`，不安装拦截器，不抛异常。
- provisioning 写入失败只记日志，不影响 carrier config 覆写。

日志 TAG 统一用 `CarrierIms`。

## 构建与打包

- AGP 9.4.0，Gradle 9.7.1，JDK 21
- `compileSdk = 37`、`compileSdkMinor = 2`、`buildToolsVersion = "37.0.0"`
- `minSdk = 26`，`targetSdk = 37`
- `enableKotlin = false`，纯 Java
- `compileOnly("io.github.libxposed:api:102.0.0")`
- `packaging { resources { merges += "META-INF/xposed/*"; excludes += "**" } }`

版本取当前各项稳定版的最新：AGP 9.5.0 仍是 alpha，故取 9.4.0；它要求 Gradle 至少 9.6.0、JDK 至少 17，最高支持 API 37。Android 17 采用次版本号方案，已发布的平台为 37.0、37.1、37.2，本项目按最新的 37.2 编译。测试设备运行 37.0，低于 compileSdk 属正常。

`compileSdkMinor` 自 AGP 8.10.1 引入；等价的块形式为 `compileSdk { version = release(37) { minorApiLevel = 2 } }`。

libxposed 官方 example 当前用 AGP 9.2.1 且未设次版本，本项目在此之上更新。`minSdk = 26` 由 libxposed api 规定。AGP 9 默认启用内置 Kotlin，本项目不含 Kotlin 源码，故显式关闭。

`src/main/resources/META-INF/xposed/` 三个文件：

| 文件 | 内容 |
|---|---|
| `java_init.list` | `io.github.nku100.carrierims.CarrierImsModule` |
| `module.prop` | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=true` |
| `scope.list` | `com.android.phone` |

AndroidManifest 只声明 `<application android:label="Carrier IMS">`，无 Activity。

release 走 minify 时需要 libxposed 要求的这三条规则，用于保留入口类并在类名被混淆后重写 `java_init.list`：

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

入口类使用无参构造函数。API 102 的 `XposedModule` 不再要求 `(XposedInterface, ModuleLoadedParam)` 形式的构造函数。

## 验证方案

`dumpsys carrier_config` 打印的是 `CarrierConfigLoader` 的内部 bundle。本模块不改任何存储状态，所以注入**不会**出现在 dumpsys 里。必须从读取方验证。

1. 编译 APK，安装，在 LSPosed 中启用，重启 `com.android.phone`
2. `logcat -s CarrierIms` 确认 hook 命中，且未出现解析失败
3. 用 `app_process` 跑探针，以 shell 身份直接调 `ICarrierConfigLoader.getConfigForSubIdWithFeature(subId, "com.android.shell", null)`，打印本模块覆写的每个 key，逐项比对期望值
4. 停用其他会覆写 carrier config 的应用（如 vvb2060/Ims、pixel-volte-patch）及其依赖的 Shizuku，重复第 3 步，确认单靠本模块生效
5. 设置里确认 VoLTE 与 VoNR 开关出现
6. 重启手机后重复第 3 步，确认无需任何应用参与即自动生效

第 3 步是核心验收标准：它直接测量真实读取方看到的值。

## 已知风险

- `staticScope=true` 时用户不能自行修改 scope。目标进程唯一且固定，这是合理取舍；若要放开改为 `false`。
- hook 的是 AOSP 的 `CarrierConfigLoader`，多数机型都有。深度改造 telephony 的 OEM ROM 里方法可能不在此类中，模块会记录错误而不会崩溃。
- Google 可能重命名或重构读取路径。届时按签名探测的候选列表需要补充新名字，这与 vvb2060/Ims 反射探测私有方法的维护负担相当。
