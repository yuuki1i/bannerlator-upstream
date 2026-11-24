# D2 Touch integration

目标：Diablo II: Lord of Destruction Collector's Edition 1.13c，启动入口支持 `启动游戏.exe`、`启动游戏.bat` 和直接的 `game.exe -3dfx -direct`。

D2 功能源码集中在 `app/src/main/java/com/winlator/star/d2android/`，原生模块位于 `app/src/main/cpp/d2android/native/`。

## 已实现的代码路径

- `D2TouchOverlayView`：多指摇杆、独立攻击、F1-F8 技能拖动瞄准、目标切换、普通点击/拖动、菜单、附近物品提示和布局编辑。
- `D2ModUdpAdapter` / `D2CommandBridge`：本机 UDP 命令/状态通道，独立跟踪移动、攻击、技能和点击指针。
- `d2mod_113c.c`：1.13c 原生模块；通过游戏线程消息钩子接收命令，使用 1.13c `ClickMap` 和 `SetSelectedUnit` 执行原生寻路、单位交互和技能输入。
- 目标辅助：读取附近怪物/单位状态，支持锁定、切换目标、方向施法、NPC 对话、附近物品拾取和最近城镇储物箱定位。
- 背包整理：移植 MIT 上游布局算法；仅在离线单人城镇、无冲突 UI 面板且鼠标光标未持物时发起，默认固定护身符。
- `D2Launcher.exe`：启动 `game.exe` 时先挂起进程、注入模块，再恢复游戏。BAT 文件不会作为 Android shell 脚本执行。

## 兼容性与安全门禁

原始 D2Android 上游针对 1.13d，不能复用其 RVA。当前原生模块仅使用经 1.13c 地址表核验的入口，并在启用命令前验证：
- `Game.exe` SHA-256；
- `D2Common.dll` SHA-256；
- `D2Client.dll` 文件大小及 Git blob SHA-1。

任一指纹不匹配时模块拒绝启用。状态协议 v3 使用 16 位物件 class ID，以支持准确定位储物箱。

## 尚需验证

- 尚未在真实 Android 设备和用户的游戏副本上完成端到端烟测；构建成功不等于游戏内功能已验证。
- 自动整理仅面向离线单人城镇，且尚未进行实机验证。
- 不同分辨率、窗口缩放、游戏语言和自定义快捷键仍需实测。
