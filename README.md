# D2 Touch — Diablo II 1.13c on Android

面向 Diablo II: Lord of Destruction 1.13c 的 Android 触控适配项目。

## 当前目标

- 独立模拟摇杆移动与独立索敌/攻击
- 可编辑的技能按键布局、大小、透明度、形状与贴图
- 按游戏版本校验后才启用原生控制桥接
- 中文界面与诊断

## 构建

使用 GitHub Actions 的 **CI Build (artifacts only)** 工作流构建测试 APK。测试构建不代表 1.13c 原生控制已通过实机验证。

## 兼容性

目标版本：Diablo II LoD 1.13c。未知或未验证的 EXE/DLL 指纹必须关闭原生注入；禁止套用 1.13d 的地址或内存布局。

## 法律与分发

游戏 EXE、DLL、MPQ、序列号和存档不应提交到公开仓库。保留上游及第三方许可证、版权声明和来源信息。
