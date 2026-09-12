# Carrier IMS

[简体中文](README.md)

An LSPosed module that turns on VoLTE, VoWiFi, VT and VoNR for carriers that Google has not whitelisted on Pixel devices.

## How it differs from the existing tools

[vvb2060/Ims](https://github.com/vvb2060/Ims) and [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch) call `CarrierConfigManager.overrideConfig()`. That API needs `MODIFY_PHONE_STATE`, so they borrow the shell permission identity through Shizuku. Google keeps hardening that path, and on Android 17 Pixels `persistent=true` no longer sticks — the override has to be rewritten on every boot.

This module hooks the **read** side instead. Inside `com.android.phone` it intercepts `CarrierConfigLoader.getConfigForSubIdWithFeature` and merges the overrides into the returned bundle. Readers are already past the permission check, so there is no permission to borrow, no Shizuku, and no override state that could fail to persist. Nothing is written to disk: run `dumpsys carrier_config` and you will still see the carrier's original values, while every caller of `CarrierConfigManager` sees the patched ones.

The design and the measurements behind it are in [`docs/superpowers/specs/2026-09-12-carrierims-xposed-design.md`](docs/superpowers/specs/2026-09-12-carrierims-xposed-design.md).

## Requirements

- Android 8.0 or newer (`minSdk 26`)
- LSPosed v2.2.0 or newer — the module uses libxposed API 102, and older builds do not implement it
- No other tool overriding carrier config at the same time

## Install

1. Build or download the release APK and install it.
2. Enable **Carrier IMS** in LSPosed. Its scope is fixed to `com.android.phone`.
3. Restart `com.android.phone`, or reboot.

## What it changes

VoLTE, VT and UT supplementary services; the full VoWiFi set including the mode and roaming-mode pickers; cross-SIM calling; the Enhanced 4G LTE switch made visible and editable; VoNR plus 5G NR SA and NSA. It also flips the `VoIMS opt-in` provisioning flag, which some carriers use as a second gate in front of VoLTE.

The exact list lives in [`CarrierOverrides.java`](app/src/main/java/io/github/nku100/carrierims/CarrierOverrides.java). There is no settings UI by design — change a value there and rebuild.

## Verify

`tools/probe/run-probe.sh` builds a small probe, pushes it, and calls the carrier config service over binder as the shell uid, then compares every injected key against an independently hardcoded expectation. It prints one PASS/FAIL line per key and exits non-zero on any mismatch.

```
ANDROID_HOME=<sdk> bash tools/probe/run-probe.sh
```

Pass `DEVICE=<serial>` when more than one device is attached.

**Take a baseline first.** With the module disabled, some of these keys already hold the expected value by default, so "everything passed" means nothing until you have seen what fails without the module.

Measure from the read side like this rather than from `dumpsys carrier_config`: the module changes no stored state, so `dumpsys` is expected to keep showing the carrier's values. Logcat is no good either for deciding whether the module loaded — logd's main buffer holds 256 KiB by default, and a boot flushes the module's line out of it within a minute.

## Build

JDK 21 and an Android SDK with platform 37.2 and build-tools 37.0.0.

```
./gradlew :app:assembleRelease
```

Install the **release** APK. The debug variant additionally carries ~760 synthetic `android.*` stub classes that D8 emits for API modeling — harmless, since the boot classloader wins for those names, but pointless inside a system process.

## Credits

The set of carrier config values follows [vvb2060/Ims](https://github.com/vvb2060/Ims) v3.1.

## License

Apache-2.0. See [LICENSE](LICENSE).
