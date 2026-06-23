@echo off
setlocal EnableExtensions

rem Stable MaaNikke phone-screen mirroring.
rem It keeps exactly one low-load scrcpy session alive and never kills MaaNikke backend app_process.
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

set "LOG_DIR=%~dp0outputs\android_probe\scrcpy_guard"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo [INFO] Device: %DEVICE_SERIAL%
echo [INFO] ADB: %ADB_EXE%
echo [INFO] scrcpy: %SCRCPY_EXE%
echo [INFO] Logs: %LOG_DIR%

"%ADB_EXE%" -s "%DEVICE_SERIAL%" get-state >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Device %DEVICE_SERIAL% is not connected or adb is unavailable.
  "%ADB_EXE%" devices
  pause
  exit /b 1
)

"%ADB_EXE%" -s "%DEVICE_SERIAL%" shell svc power stayon true >nul 2>nul
"%ADB_EXE%" -s "%DEVICE_SERIAL%" shell input keyevent KEYCODE_WAKEUP >nul 2>nul

echo [INFO] Closing old desktop scrcpy processes only...
powershell.exe -NoLogo -NoProfile -Command "Get-Process scrcpy -ErrorAction SilentlyContinue | Stop-Process -Force"

:loop
for /f "delims=" %%P in ('powershell.exe -NoLogo -NoProfile -Command "(Get-Process scrcpy -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*MaaNikkePhoneScreen-Stable*' }).Id | Select-Object -First 1"') do set "RUNNING_PID=%%P"
if defined RUNNING_PID (
  "%ADB_EXE%" -s "%DEVICE_SERIAL%" get-state >nul 2>nul
  if not errorlevel 1 (
    timeout /t 5 /nobreak >nul
    set "RUNNING_PID="
    goto :loop
  )
)

echo [%date% %time%] starting stable scrcpy...
start "MaaNikkePhoneScreen-Stable" /b "%SCRCPY_EXE%" -s "%DEVICE_SERIAL%" --window-title MaaNikkePhoneScreen-Stable --display-id 0 --stay-awake --max-fps 20 --video-bit-rate 4M --video-codec h264 --no-audio --video-buffer 100 1>>"%LOG_DIR%\scrcpy.out.log" 2>>"%LOG_DIR%\scrcpy.err.log"
timeout /t 8 /nobreak >nul
set "RUNNING_PID="
goto :loop
