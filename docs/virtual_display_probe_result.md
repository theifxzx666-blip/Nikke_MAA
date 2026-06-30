# Android 16:9 VirtualDisplay Probe Result

Date: 2026-06-17

Device:

- Serial: `2fb37497`
- Model: Xiaomi Mi 10 Pro
- Android: 13 / SDK 33
- Root: available
- NIKKE package: `com.tencent.nikke`
- NIKKE activity: `com.tencent.nikke/.default_Activity`

Probe APK:

- Source: `tools/android_virtual_display_probe`
- APK: `outputs/android_probe/apk/MaaNikkeVirtualDisplayProbe.apk`
- Package: `com.codex.maanikke.probe`
- Virtual display request: `1280x720@160dpi`

Verified:

- The probe can create a system virtual display named `MaaNikkeProbe-1280x720`.
- Latest verified display id: `4`
- `dumpsys display` reports a `VIRTUAL` display with real/app size `1280x720`.
- Root launch command works:

```powershell
adb -s 2fb37497 shell su -c 'am start -S --display 4 -n com.tencent.nikke/.default_Activity'
```

- `dumpsys activity activities` reports NIKKE on `Display #4`, `state=RESUMED`, bounds `[0,0][1280,720]`.
- `dumpsys SurfaceFlinger --list` reports NIKKE activity and `SurfaceView` layers on the virtual display.

Current limitation:

- The simple public `TextureView`-backed `VirtualDisplay` preview remains black after NIKKE starts.
- APK internal `TextureView.getBitmap(1280,720)` also saved a black frame:
  `outputs/android_probe/maanikke_probe_preview_after_nikke.png`
- `screencap -d <display-id>` produces an empty file for this virtual display on this device.

Root/ImageReader follow-up:

- Source: `tools/android_root_imagereader_probe`
- Build output: `outputs/android_probe/root_ir_probe/maanikke-root-ir-probe.jar`
- Repeat command:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_root_imagereader_probe\run_root_probe.ps1 -Serial 2fb37497
```

- The root/app_process probe creates a `1280x720@160dpi` virtual display with MAA-Meow-style flags and uses an `ImageReader` surface as the virtual display sink.
- Latest verified display id after backend split: `9`
- Result: `frames=422`, `nonBlackFrames=388`, `lastNonZeroSamples=2859`.
- Last captured frame: `outputs/android_probe/root_ir_probe/maanikke_root_ir_last_latest.png`.

Root/Input follow-up:

- The root probe now injects a tap into the virtual display after a real NIKKE frame is available.
- Touch target: `(32, 86)` in the `1280x720` virtual display coordinate system.
- Latest verified display id after backend split: `9`
- Result: `touchProbeAttempted=true`, `touchDownResult=true`, `touchUpResult=true`, `touchBeforeNonZeroSamples=3907`, `touchAfterNonZeroSamples=3117`.
- Before touch: `outputs/android_probe/root_ir_probe/maanikke_root_ir_before_touch_latest.png`
- After touch: `outputs/android_probe/root_ir_probe/maanikke_root_ir_after_touch_latest.png`
- The after-touch frame shows the NIKKE announcement dialog, confirming that the injected MotionEvent was delivered to NIKKE on the virtual display.

Reusable backend split:

- `AndroidShellEnvironment`: prepares hidden API access, the shell-like `ActivityThread` context, and shell command execution.
- `VirtualDisplayController`: creates the fixed `1280x720@160dpi` virtual display with MAA-Meow-style flags.
- `FrameCaptureBackend`: owns `ImageReader RGBA_8888`, writes the latest frame PNG, and exposes frame/non-black counters.
- `InputInjector`: sends display-bound `MotionEvent` through hidden `InputManager.injectInputEvent`.
- `GameLauncher`: starts `com.tencent.nikke/.default_Activity` on the target display.
- `RootImageReaderProbe`: remains the smoke-test entrypoint that wires the backend together and writes evidence files.

Debug APK:

- Source: `tools/android_maanikke_debug_apk`
- APK: `outputs/android_probe/apk/MaaNikkeAndroidDebug.apk`
- Package: `com.codex.maanikke.debug`
- The APK embeds `assets/maanikke-root-ir-probe.jar`, copies it to `/data/local/tmp/maanikke-root-ir-probe.jar`, writes a pure-LF runner script at `/data/local/tmp/maanikke_run_probe_env.sh`, then runs the backend through `su 2000 -c /data/local/tmp/maanikke_run_probe_env.sh`.
- The `su 2000` launch shape is important on this Magisk device. Direct `su -c app_process ...` from the app UID crashed because the classpath runner path was not preserved correctly in the early test; the pure-LF runner script fixes the `ClassNotFoundException` failure.
- Latest APK-triggered smoke display id: `11`
- Latest APK-triggered smoke result: `frames=534`, `nonBlackFrames=486`, `touchDownResult=true`, `touchUpResult=true`, `touchBeforeNonZeroSamples=3982`, `touchAfterNonZeroSamples=2868`.
- The APK also exposes `start_game`, `back_to_home`, `handle_update`, and `claim_mail` backend tasks via UI buttons and `com.codex.maanikke.debug.RUN_START_GAME`, `com.codex.maanikke.debug.RUN_BACK_TO_HOME`, `com.codex.maanikke.debug.RUN_HANDLE_UPDATE`, `com.codex.maanikke.debug.RUN_CLAIM_MAIL`.
- The APK UI is now portrait-first: top status, a horizontal 16:9 live preview pane, metrics, task queue, fixed bottom action bar, and bottom navigation. The game itself still runs on the `1280x720@160dpi` virtual display; the foreground app preview is populated from the backend's latest PNG frame.
- Latest `start_game` regression: `displayId=29`, `frames=664`, `nonBlackFrames=601`, `actionCount=5`, `actionSuccess=true`, `finalState=home_clear`.
- Latest `back_to_home` regression: `displayId=30`, `frames=524`, `nonBlackFrames=477`, `actionCount=7`, `actionSuccess=true`, `finalState=home_clear`.
- Latest `handle_update` regression: `displayId=32`, `frames=469`, `nonBlackFrames=414`, `actionCount=0`, `actionSuccess=true`, `finalState=no_update_or_download_dialog`.
- Latest `claim_mail` claim-path regression: `displayId=34`, `frames=620`, `nonBlackFrames=568`, `actionCount=15`, `actionSuccess=true`, `finalState=mail_claimed_home_clear`.
- Latest `claim_mail` no-claim regression: `displayId=36`, `frames=549`, `nonBlackFrames=493`, `actionCount=9`, `actionSuccess=true`, `finalState=home_clear`.
- `start_game` currently handles announcement entry, announcement close, resource download confirm, update confirm, activity detail popup, and home notice list popup. `back_to_home` reuses the 16:9 virtual display without force-stopping NIKKE and requires multiple home anchors before returning `home_clear`. `handle_update` is a standalone update/download scene handler; if an update opens an external foreground package on the target virtual display, the task records `client_update_external:<package>` and stops. `claim_mail` opens the mailbox from home, detects the mailbox page and claim button, clicks all-claim, closes reward popups, and handles the no-claim path by closing the mailbox back to home.
- Phone evidence export directory: `/sdcard/Download/MaaNikkeDebug`
- Pulled local evidence: `outputs/android_probe/apk_debug_run/MaaNikkeDebug`
- Latest `start_game` local evidence: `outputs/android_probe/apk_regression_after_feature_start_game/`
- Latest `back_to_home` local evidence: `outputs/android_probe/apk_regression_after_feature_back_to_home/`
- Latest `handle_update` local evidence: `outputs/android_probe/apk_handle_update_run_iter2/`
- Latest `claim_mail` claim-path local evidence: `outputs/android_probe/apk_claim_mail_run_iter2/`
- Latest `claim_mail` no-claim local evidence: `outputs/android_probe/apk_claim_mail_run_iter4/`
- Latest UI screenshots: `outputs/android_probe/apk_ui_portrait_iter2/app-ui-portrait.png`, `outputs/android_probe/apk_live_portrait_iter1/app-live-12s.png`, `outputs/android_probe/apk_live_portrait_iter1/app-live-final.png`

Install and trigger:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 install -r .\outputs\android_probe\apk\MaaNikkeAndroidDebug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -n com.codex.maanikke.debug/.MainActivity
```

You can also trigger the probe without tapping the UI:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -a com.codex.maanikke.debug.RUN_PROBE -n com.codex.maanikke.debug/.MainActivity
```

You can trigger the current backend tasks without tapping the UI:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -a com.codex.maanikke.debug.RUN_START_GAME -n com.codex.maanikke.debug/.MainActivity
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -a com.codex.maanikke.debug.RUN_BACK_TO_HOME -n com.codex.maanikke.debug/.MainActivity
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -a com.codex.maanikke.debug.RUN_HANDLE_UPDATE -n com.codex.maanikke.debug/.MainActivity
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -a com.codex.maanikke.debug.RUN_CLAIM_MAIL -n com.codex.maanikke.debug/.MainActivity
```

Conclusion:

The 16:9 container idea is valid at the Android window/task, capture, and input levels when following the MAA-Meow architecture. NIKKE can be launched into a `1280x720` virtual display with root, a root/app_process `ImageReader` sink can capture real NIKKE frames, and display-bound `MotionEvent` injection can interact with the game. The minimal public `TextureView` preview is not sufficient, but the MAA-Meow-style root remote service + ImageReader capture + InputManager path is viable for MaaCore integration.

Evidence files:

- `outputs/android_probe/activities_after_virtual_launch_v2.txt`
- `outputs/android_probe/window_displays_after_virtual_launch_v2.txt`
- `outputs/android_probe/display_after_virtual_launch_v2.txt`
- `outputs/android_probe/surfaceflinger_list_after_virtual_launch_v2.txt`
- `outputs/android_probe/maanikke_probe_default.png`
- `outputs/android_probe/maanikke_probe_preview_after_nikke.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_result_long.txt`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_probe_long.log`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_last_long.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_result_touch.txt`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_probe_touch.log`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_before_touch.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_after_touch.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_last_touch.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_result_latest.txt`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_probe_latest.log`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_before_touch_latest.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_after_touch_latest.png`
- `outputs/android_probe/root_ir_probe/maanikke_root_ir_last_latest.png`
- `outputs/android_probe/apk/MaaNikkeAndroidDebug.apk`
- `outputs/android_probe/apk_debug_run/MaaNikkeDebug/result-20260617-174051.txt`
- `outputs/android_probe/apk_debug_run/MaaNikkeDebug/probe-20260617-174051.log`
- `outputs/android_probe/apk_debug_run/MaaNikkeDebug/before-touch-20260617-174051.png`
- `outputs/android_probe/apk_debug_run/MaaNikkeDebug/after-touch-20260617-174051.png`
- `outputs/android_probe/apk_debug_run/MaaNikkeDebug/last-20260617-174051.png`
- `outputs/android_probe/apk_regression_after_feature_start_game/start_game-result.txt`
- `outputs/android_probe/apk_regression_after_feature_back_to_home/back_to_home-result.txt`
- `outputs/android_probe/apk_handle_update_run_iter2/handle_update-result.txt`
- `outputs/android_probe/apk_claim_mail_run_iter2/claim_mail-result.txt`
- `outputs/android_probe/apk_claim_mail_run_iter4/claim_mail-result.txt`
