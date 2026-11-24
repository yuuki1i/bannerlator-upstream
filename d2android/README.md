# D2Android 1.13c integration set

此目录是独立 D2 功能文件集。Android 侧实现位于 `app/src/main/java/com/winlator/star/d2android/`。

- `D2ControlProfile`：移动、攻击、技能键的独立布局。
- `D2JoystickMath`：摇杆归一化与死区。
- `D2CommandBridge`：仅向已验证的游戏端适配器发出语义命令，不把鼠标点击伪装成原生移动。
- `D2CompatibilityGate`：SHA-256 白名单门禁；未配置 1.13c 指纹时始终关闭。

不携带游戏文件，不使用未经验证的 1.13d RVA。
