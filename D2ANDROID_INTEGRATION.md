# D2 Touch integration

目标：Diablo II: Lord of Destruction 1.13c。

D2 功能源码集中在 `app/src/main/java/com/winlator/star/d2android/`，目录说明见 `d2android/README.md`。

核心组成：
- `D2JoystickMath`：独立模拟摇杆，输出归一化移动向量。
- `D2ControlProfile`：移动、攻击、技能控件独立布局，可配置大小、透明度、形状和贴图字段。
- `D2TouchOverlayView`：多点触控层，分别跟踪移动和攻击触点。
- `D2CommandBridge`：语义命令适配接口；无有效 1.13c 游戏端适配器时拒绝发送。
- `D2CompatibilityGate`：构建指纹白名单门禁，默认关闭。

## 安全兼容规则

D2Android 上游实现针对 1.13d。1.13d 的 RVA、结构偏移和补丁不可用于 1.13c。必须先核验 `Game.exe`、`D2Client.dll`、`D2Common.dll`、`D2Game.dll` 的实际版本和 SHA-256，再实现对应的原生模块。未知构建一律 fail-closed。

## 当前未完成项

- 尚无经过验证的 1.13c 原生游戏端移动/索敌/攻击适配器。
- 触控层已建立，但尚未接入 1.13c 原生命令执行路径。
- APK 尚未由 Actions 成功构建并验证。

不得把触控 UI 的显示误称为已实现游戏内移动或攻击。
