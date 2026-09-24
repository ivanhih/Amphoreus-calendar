# 翁法罗斯月历

一款原生 Android 月历应用，以翁法罗斯主题插画呈现月份，同时按公历计算真实日期。应用默认离线运行，不含广告和统计 SDK。

## 功能

- 月历、日程列表、搜索，以及日程新增、编辑和删除。
- 全天日程、重复规则和本地提醒。
- 连接 Android 系统日历，查看并编辑有写入权限的日历。
- 添加 HTTPS ICS 订阅源，在应用内查看只读日程并定时同步。
- 三种桌面小组件：主题月历、今日安排和图文月历。
- 角色开屏动画，可在设置中选择参与轮播的插画。

## 安装与构建

应用支持 Android 8.0（API 26）及以上版本。用 Android Studio 打开项目，配置 JDK 17 和 Android SDK Platform 35，然后运行：

```powershell
.\gradlew.bat assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。也可以在本机已安装 Android SDK Build Tools 35.0.0 的情况下运行离线构建脚本：

```powershell
.\scripts\build.ps1
```

离线脚本生成 `dist/amphoreus-calendar-debug.apk`。这两个构建产物均为调试版本；发布到应用商店前需要配置正式签名。

## 使用说明

首次启动时，应用会请求系统日历权限。允许后可以选择要显示和保存日程的系统日历；拒绝权限时仍可使用应用内本地日历。

ICS 订阅源只在用户添加地址后联网。订阅日程在应用中为只读，地址和同步内容保存在设备本地。跨设备同步由 Android 上已配置的日历账号负责。

## 项目结构

- `app/src/main/java/.../core`：日期计算、重复规则和提醒逻辑。
- `app/src/main/java/.../data`：本地数据、系统日历与 ICS 同步。
- `app/src/main/java/.../ui`：月历、日程、设置和插画显示。
- `app/src/main/java/.../widget`：桌面小组件。
- `app/src/main/assets/art`：应用使用的 13 张插画素材。
- `app/src/main/res`：Android 界面资源和应用图标。
- `tools/calibrate_art.py`：更新插画素材后重新测量版式参数。

项目使用 Android SDK 自带 API，无额外运行时依赖。

重新测量插画参数需要 Python 3、NumPy 和 Pillow：

```powershell
python tools/calibrate_art.py --check
python tools/calibrate_art.py --write-java
```
