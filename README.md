# Nono

[![English](https://img.shields.io/badge/README-English-blue)](README.en.md)

Nono 是一个 Android 通知规则工具，用来按应用、关键字、工作日和工作时间过滤通知。

## 捐助 / Sponsor

如果 Nono 对你有帮助，欢迎通过 GitHub Sponsors 里的 Open Collective 支持项目：
[https://opencollective.com/nonotification](https://opencollective.com/nonotification)

## 界面结构

- `通知`：查看延后、收集的通知
- `规则`：查看和编辑现有规则
- `配置`：搜索已安装应用，自动获取包名并创建规则
- `设置`：通知权限、导入导出、包名获取说明

## 搜索应用与自动获取包名

1. 打开 `配置` 标签页。
2. 在搜索框输入应用名或包名的一部分。 
3. 如果仍然缺少应用，通常与工作 Profile、双开空间或厂商私有容器有关

## 新规则能力

- `屏蔽通知`：命中规则后直接取消通知
- `允许通知`：命中规则后放行
- `延后通知`：命中规则后取消原通知，并在设定分钟后由 Nono 发出本地提醒
- `系统响铃`：命中规则时可尝试切换为保持不变、响铃、震动或静音

说明：

- `延后通知` 需要 Nono 自己发送通知，因此 Android 13 及以上需要授予通知权限
- `震动静音` 在部分系统上还需要授予勿扰访问，否则系统可能拒绝切换

## 导入导出配置

- 导出：在 `设置` 页点击 `导出配置`，生成 JSON 文件
- 导入：在 `设置` 页点击 `导入配置`，选择之前导出的 JSON 文件
- 当前导入策略：导入后覆盖本地规则

## 运行方式

1. 在 Android Studio 或 IntelliJ IDEA 中打开项目根目录。
2. 等待 Gradle 同步完成。
3. 连接真机并开启 USB 调试。
4. 运行 `app` 配置。
5. 安装后进入 `设置` 页，打开通知访问权限。
6. 回到 `规则` 或 `配置` 页调整规则。

## 捐助 / Sponsor

如果 Nono 对你有帮助，欢迎通过 GitHub Sponsors 里的 Open Collective 支持项目：
[https://opencollective.com/nonotification](https://opencollective.com/nonotification)

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。
