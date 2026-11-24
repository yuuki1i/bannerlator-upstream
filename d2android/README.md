# D2Android 1.13c integration set

此目录是独立 D2 功能文件集。Android 侧实现位于 `app/src/main/java/com/winlator/star/d2android/`。

## 珍藏版启动方式

已按项目中提交的 `启动游戏.exe` 和 `启动游戏.bat` 检查启动链。BAT 的实际命令是：

```bat
start game.exe -3dfx -direct
```

Bannerlator/Wine 的游戏快捷方式应指向同目录的 `game.exe`，启动参数为 `-3dfx -direct`，工作目录必须设为游戏文件夹，以便正确加载旁边的 DLL 和 MPQ。不要把 BAT 当作 Android shell 脚本运行。配置见 `compatibility/collector-launcher.json`。

## D2 功能核心

- `D2LauncherProfile`：珍藏版启动器识别、游戏 EXE 解析及参数配置。
- `D2ControlProfile`：移动、攻击、技能键的独立布局。
- `D2JoystickMath`：摇杆归一化与死区。
- `D2TouchOverlayView`：多点触控层，分别跟踪移动和攻击触点。
- `D2CommandBridge`：语义命令适配接口；无有效 1.13c 游戏端适配器时拒绝发送。
- `D2CompatibilityGate`：SHA-256 白名单门禁；未配置完整指纹时保持关闭。

不携带游戏文件，不使用未经验证的 1.13d RVA。


## 触屏运行时实现进展

- Android 侧加入 D2AC/D2AM 二进制协议编码、UDP 状态接收和 1.13c fail-closed 适配器边界。
- 触屏层支持移动、攻击、拖动瞄准、多指并发，以及 F1-F8 两页技能按钮；技能 ID 从游戏状态热键表读取。
- 当前 native 1.13c 注入模块及其函数/结构偏移仍需独立验证。兼容门未开放前不会向游戏发送任何未经验证的命令。
