# 第三方组件与许可

本项目自身的代码采用根目录 [LICENSE](LICENSE) 中的 MIT 许可。第三方组件仍适用各自的许可；本项目的 MIT 许可不替代这些许可，也不授予相关商标权。

本说明针对首次公开发布 **v1.4.5** 的主要直接依赖及构建工具整理。版本以 [`app/build.gradle.kts`](app/build.gradle.kts)、[`build.gradle.kts`](build.gradle.kts) 和 [Gradle Wrapper 配置](gradle/wrapper/gradle-wrapper.properties) 为准。以下内容不是完整的传递依赖清单，也不表示对所有构建工具、Android SDK 或 APK 内每个文件完成了逐项许可审计。增加或升级依赖时，应同时检查对应版本的上游许可和附带声明。

## 应用使用的主要组件

| 组件 | 本项目的声明版本 | 许可 | 官方出处与许可依据 |
|---|---|---|---|
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 | [源码](https://github.com/androidx/androidx/tree/androidx-main/activity)、[版本 POM](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.10.1/activity-compose-1.10.1.pom) |
| Jetpack Compose UI、Foundation、Material 3、预览工具 | BOM 2025.04.01 管理版本 | Apache-2.0 | [AndroidX 许可](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt)、[BOM POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.04.01/compose-bom-2025.04.01.pom)、[UI 1.8.0 POM](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/1.8.0/ui-1.8.0.pom) |
| AndroidX Lifecycle Runtime / ViewModel Compose | 2.8.7 | Apache-2.0 | [源码](https://github.com/androidx/androidx/tree/androidx-main/lifecycle)、[版本 POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/2.8.7/lifecycle-runtime-ktx-2.8.7.pom) |
| AndroidX Room Runtime / KTX / Compiler | 2.7.1 | Apache-2.0 | [AndroidX 许可](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt)、[版本 POM](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.7.1/room-runtime-2.7.1.pom) |
| Kotlin 标准库与 Kotlin / Compose 编译插件 | Kotlin 2.1.20 | Apache-2.0 | [v2.1.20 许可](https://github.com/JetBrains/kotlin/blob/v2.1.20/license/LICENSE.txt)、[上游版权声明](https://github.com/JetBrains/kotlin/blob/v2.1.20/license/COPYRIGHT.txt)、[上游第三方说明目录](https://github.com/JetBrains/kotlin/tree/v2.1.20/license) |
| kotlinx.coroutines Android | 1.10.1 | Apache-2.0 | [1.10.1 许可](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.1/LICENSE.txt) |
| Material Icons，经 Compose `material-icons-extended` 使用 | BOM 当前指定 1.7.8 | Apache-2.0 | [Google Material Design Icons 许可](https://github.com/google/material-design-icons/blob/master/LICENSE)、[Compose 图标 1.7.8 POM](https://dl.google.com/dl/android/maven2/androidx/compose/material/material-icons-extended/1.7.8/material-icons-extended-1.7.8.pom) |

AndroidX 与 Material Icons 由各自上游的版权持有人及贡献者提供。Kotlin 的版权声明原文另存于 [LICENSES/Kotlin-2.1.20-COPYRIGHT.txt](LICENSES/Kotlin-2.1.20-COPYRIGHT.txt)。共同适用的 Apache License 2.0 全文见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)；上游项目内针对部分第三方代码的额外声明仍适用。

## 构建、调试与测试

这些组件用于构建或测试；不能据此认为其完整内容都被打包进正式 APK。`debugImplementation`、`testImplementation` 和 `androidTestImplementation` 与正式应用的运行时依赖有不同作用域。

| 组件 | 本项目的声明版本 | 许可 | 官方出处与许可依据 |
|---|---|---|---|
| Gradle / Gradle Wrapper | 8.11.1 | Gradle 自身及 Wrapper 为 Apache-2.0；完整 Gradle 分发另含其声明的第三方组件 | [v8.11.1 上游 LICENSE](https://github.com/gradle/gradle/blob/v8.11.1/LICENSE)、[本地保留原文](LICENSES/Gradle-8.11.1-LICENSE.txt) |
| Android Gradle Plugin | 8.9.2 | Apache-2.0 | [Google Maven 版本 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.9.2/gradle-8.9.2.pom) |
| Kotlin Symbol Processing (KSP) | 2.1.20-1.0.32 | Apache-2.0 | [对应 tag 许可](https://github.com/google/ksp/blob/2.1.20-1.0.32/LICENSE) |
| AndroidX Test Runner / Core KTX / Ext JUnit | 1.6.2 / 1.6.1 / 1.2.1 | Apache-2.0 | [AndroidX Test 许可](https://github.com/android/android-test/blob/main/LICENSE)、[Runner POM](https://dl.google.com/dl/android/maven2/androidx/test/runner/1.6.2/runner-1.6.2.pom)、[Core KTX POM](https://dl.google.com/dl/android/maven2/androidx/test/core-ktx/1.6.1/core-ktx-1.6.1.pom)、[Ext JUnit POM](https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/1.2.1/junit-1.2.1.pom) |
| Compose UI Test / UI Tooling / Test Manifest | 由 Compose BOM 管理 | Apache-2.0 | [AndroidX 许可](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| Room Testing | 2.7.1 | Apache-2.0 | [AndroidX 许可](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| kotlinx.coroutines Test | 1.10.1 | Apache-2.0 | [1.10.1 许可](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.1/LICENSE.txt) |
| JUnit 4 | 4.13.2 | EPL-1.0 | [r4.13.2 上游许可](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt)、[本地保留原文](LICENSES/EPL-1.0-JUnit.txt) |

`gradlew`、`gradlew.bat` 已保留上游的版权与 Apache-2.0 文件头。`gradle-wrapper.jar` 为 Gradle Wrapper 工具，Gradle 发行版通过 Wrapper 配置下载，未将完整 Gradle 发行版放入本仓库。JUnit 4 的 EPL-1.0 适用于对应的测试框架，不改变本项目自身代码的 MIT 许可。

## 图形、字体与初始数据

- 功能图标使用上表所列 Material Icons。`ui/Illustrations.kt` 的场景图和启动图标 XML 为本项目中的矢量资源，没有另行打包外部照片或字体文件。
- 字体由 Android 系统提供，不在本仓库分发系统字体。
- 初始清单中的商家名用于标识用户可编辑的餐馆或食品选项，相关品牌及商标归各自权利人所有，不表示本项目与其存在合作或背书关系。

## 许可原文来源

以下文件按上游公开原文保存，没有改写条款。核对日期：2026-10-17。

| 本地文件 | 下载来源 |
|---|---|
| [Apache-2.0.txt](LICENSES/Apache-2.0.txt) | [Apache Software Foundation 官方原文](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| [EPL-1.0-JUnit.txt](LICENSES/EPL-1.0-JUnit.txt) | [JUnit r4.13.2 官方仓库原文](https://raw.githubusercontent.com/junit-team/junit4/r4.13.2/LICENSE-junit.txt) |
| [Gradle-8.11.1-LICENSE.txt](LICENSES/Gradle-8.11.1-LICENSE.txt) | [Gradle v8.11.1 官方仓库原文，含其附带第三方声明](https://raw.githubusercontent.com/gradle/gradle/v8.11.1/LICENSE) |
| [Kotlin-2.1.20-COPYRIGHT.txt](LICENSES/Kotlin-2.1.20-COPYRIGHT.txt) | [Kotlin v2.1.20 官方仓库版权声明](https://raw.githubusercontent.com/JetBrains/kotlin/v2.1.20/license/COPYRIGHT.txt) |
