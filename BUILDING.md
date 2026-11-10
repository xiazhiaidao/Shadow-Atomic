# Shadow Atomic 0.33.2-alpha 源码

安装并配置Java17，解压后在项目目录执行 `gradlew.bat build`（Windows）或 `sh ./gradlew build`（Linux/macOS）。首次构建需要联网下载Gradle与Forge依赖。

玩家产物位于 `build/libs/shadow-atomic-0.33.2-alpha.jar`。默认jar任务排除 `local/shadowatomic/qa/**`，自动测试探针另行显式构建。

源码来自本版冻结的源文件.zip；发布时仅去除gradle.properties中开发机专用的Java绝对路径，补充本说明、许可说明和.gitignore。游戏源码与资源未修改。此发布目录未另行进行依赖下载和重新构建；已验证的玩家JAR随主分享包提供。

完整功能、环境与按键见主分享包README。许可证沿用模组元数据All Rights Reserved，第三方素材见ASSET_SOURCES.md与AUDIO_SOURCES.md。
