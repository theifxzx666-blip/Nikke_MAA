@echo off
setlocal EnableExtensions

rem Quick launcher for MaaNikke Android debug screen mirroring.
rem Optional overrides:
rem   set ANDROID_SERIAL=2fb37497
rem   set ADB_EXE=C:\path\to\adb.exe
rem   set SCRCPY_EXE=C:\path\to\scrcpy.exe

set "DEVICE_SERIAL=%ANDROID_SERIAL%"
if not defined DEVICE_SERIAL set "DEVICE_SERIAL=2fb37497"

if not defined ADB_EXE set "ADB_EXE=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB_EXE%" set "ADB_EXE=adb.exe"

if defined SCRCPY_EXE if exist "%SCRCPY_EXE%" goto :have_scrcpy

for /f "delims=" %%I in ('where scrcpy.exe 2^>nul') do (
  set "SCRCPY_EXE=%%I"
  goto :have_scrcpy
)

for /f "usebackq delims=" %%I in (`powershell.exe -NoLogo -NoProfile -Command "Get-ChildItem -Path $env:USERPROFILE\Downloads -Filter scrcpy.exe -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName"`) do (
  set "SCRCPY_EXE=%%I"
)

:have_scrcpy
if not exist "%SCRCPY_EXE%" (
  echo [ERROR] scrcpy.exe not found.
  echo Set SCRCPY_EXE first, or put scrcpy.exe on PATH.
  pause
  exit /b 1
)

echo [INFO] Device: %DEVICE_SERIAL%
echo [INFO] ADB: %ADB_EXE%
echo [INFO] scrcpy: %SCRCPY_EXE%

"%ADB_EXE%" -s "%DEVICE_SERIAL%" get-state >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Device %DEVICE_SERIAL% is not connected or adb is unavailable.
  "%ADB_EXE%" devices
  pause
  exit /b 1
)

echo [INFO] Starting MaaNikke debug app...
"%ADB_EXE%" -s "%DEVICE_SERIAL%" shell am start -n com.codex.maanikke.debug/.MainActivity

echo [INFO] Opening phone screen mirror...
start "MaaNikkePhoneScreen" "%SCRCPY_EXE%" -s "%DEVICE_SERIAL%" --window-title MaaNikkePhoneScreen --display-id 0 --stay-awake --max-fps 30 --video-bit-rate 8M

echo [OK] MaaNikkePhoneScreen launched.
exit /b 0
