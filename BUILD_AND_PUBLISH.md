# 📦 BookmarkPalace 打包测试发布指南

本文档详细介绍如何构建、测试和发布 BookmarkPalace 插件。

---

## 📋 目录

- [环境准备](#-环境准备)
- [本地构建](#-本地构建)
- [测试验证](#-测试验证)
- [版本发布](#-版本发布)
- [JetBrains Marketplace 发布](#-jetbrains-marketplace-发布)
- [常见问题](#-常见问题)

---

## 🔧 环境准备

### 系统要求

| 组件 | 最低版本 | 推荐版本 |
|------|----------|----------|
| JDK | 17 | 17 LTS |
| Gradle | 8.2 | 8.5+ |
| IntelliJ IDEA | 2023.2 | 最新版 |
| 内存 | 4GB | 8GB+ |

### 验证环境

```bash
# 检查 JDK 版本
java -version
# 输出应包含: openjdk version "17.x.x" 或更高

# 检查 JAVA_HOME
echo $JAVA_HOME
```

### 首次设置

```bash
# 1. 克隆项目
git clone <repository-url>
cd idea书签

# 2. 授予 gradlew 执行权限 (macOS/Linux)
chmod +x gradlew

# 3. 验证 Gradle Wrapper
./gradlew --version
```

---

## 🏗️ 本地构建

### 完整构建

```bash
# 清理并构建
./gradlew clean build
```

### 构建产物

构建完成后，产物位置：

| 产物 | 路径 |
|------|------|
| 编译类 | `build/classes/` |
| 插件 JAR | `build/libs/` |
| 可分发包 | `build/distributions/` |

### 仅编译（不打包）

```bash
./gradlew compileKotlin
```

### 打包插件

```bash
./gradlew buildPlugin
```

插件包：`build/distributions/BookmarkPalace-1.0.0.zip`

---

## 🧪 测试验证

### 1. 启动测试 IDE

```bash
# 启动一个带有插件的测试 IntelliJ IDEA 实例
./gradlew runIde
```

> 💡 首次运行会下载 IntelliJ IDEA Community Edition，需要几分钟。

### 2. 功能测试清单

在测试 IDE 中执行以下验证：

#### 基础功能
- [ ] **添加书签**: `Ctrl+Shift+B` 或右键菜单
- [ ] **快速添加**: `Ctrl+Alt+B` 无对话框添加
- [ ] **查看书签**: 左侧工具栏 → BookmarkPalace
- [ ] **跳转书签**: 双击书签条目
- [ ] **删除书签**: 右键 → 删除

#### 书签属性
- [ ] 别名编辑
- [ ] 颜色选择（9 种颜色）
- [ ] 标签添加/删除
- [ ] 注释编辑

#### 分组功能
- [ ] 按文件分组
- [ ] 按颜色分组
- [ ] 按标签分组
- [ ] 按状态分组

#### 搜索功能
- [ ] 按别名搜索
- [ ] 按代码内容搜索
- [ ] 按标签过滤

#### 导览图
- [ ] 打开导览图（工具菜单 → BookmarkPalace → 打开导览图）
- [ ] 添加书签节点到画布
- [ ] 创建连线
- [ ] 缩放画布
- [ ] 节点拖拽和编辑

#### 导入导出
- [ ] 导出 JSON
- [ ] 导出 Markdown
- [ ] 导出 Mermaid
- [ ] 导入 JSON

#### 国际化
- [ ] 切换为英文
- [ ] 切换为中文
- [ ] 验证所有 UI 文本正确显示

#### 边界测试
- [ ] 代码修改后书签位置自动更新
- [ ] 删除书签所在行后书签显示失效状态
- [ ] 重新绑定失效书签

### 3. 日志查看

测试 IDE 日志位置：
- **macOS**: `~/Library/Logs/JetBrains/IdeaIC<version>/idea.log`
- **Linux**: `~/.cache/JetBrains/IdeaIC<version>/log/idea.log`
- **Windows**: `%LOCALAPPDATA%\JetBrains\IdeaIC<version>\log\idea.log`

查看插件相关日志：
```bash
grep -i "bookmark" ~/Library/Logs/JetBrains/IdeaIC2023.2/idea.log
```

### 4. 插件验证

```bash
# 运行 JetBrains 官方插件验证器
./gradlew verifyPlugin
```

验证内容包括：
- 插件描述格式
- 兼容性声明
- 依赖检查
- API 使用检查

---

## 🚀 版本发布

### 1. 更新版本号

编辑以下文件：

**build.gradle.kts**
```kotlin
version = "1.1.0"  // 更新版本号
```

**src/main/resources/META-INF/plugin.xml**
```xml
<version>1.1.0</version>
```

### 2. 更新变更日志

在 `plugin.xml` 中更新：
```xml
<change-notes><![CDATA[
<h3>v1.1.0</h3>
<ul>
    <li>新功能: ...</li>
    <li>修复: ...</li>
</ul>
]]></change-notes>
```

### 3. 构建发布包

```bash
# 清理并构建
./gradlew clean build

# 运行所有检查
./gradlew check

# 验证插件
./gradlew verifyPlugin

# 打包
./gradlew buildPlugin
```

### 4. 本地安装测试

1. 打开 IntelliJ IDEA
2. `Settings/Preferences` → `Plugins`
3. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
4. 选择 `build/distributions/BookmarkPalace-x.x.x.zip`
5. 重启 IDE
6. 验证插件功能

---

## 🌐 JetBrains Marketplace 发布

### 前置条件

1. 注册 [JetBrains Hub](https://hub.jetbrains.com/) 账号
2. 在 [Plugin Repository](https://plugins.jetbrains.com/) 登录
3. 创建 API Token：`Hub` → `Settings` → `Personal Access Tokens`

### 配置 Token

**方式 1: 环境变量（推荐）**
```bash
export PUBLISH_TOKEN="your-token-here"
```

**方式 2: gradle.properties**
```properties
# ~/.gradle/gradle.properties
intellijPublishToken=your-token-here
```

### 发布命令

```bash
# 首次发布（新插件）
./gradlew publishPlugin

# 更新已有插件
./gradlew publishPlugin
```

### 发布流程

```
提交发布
   ↓
JetBrains 审核（1-2 工作日）
   ↓
审核通过
   ↓
上架 Marketplace
```

### 审核要点

确保以下内容符合规范：
- [ ] 插件描述清晰完整
- [ ] 提供插件截图
- [ ] 正确声明兼容版本
- [ ] 无恶意代码或广告
- [ ] 遵循开源协议

---

## 📁 构建产物清单

```
build/
├── classes/                    # 编译的类文件
│   └── kotlin/
│       └── main/
├── libs/                       # JAR 包
│   └── BookmarkPalace-1.0.0.jar
├── distributions/              # 可分发的插件包
│   └── BookmarkPalace-1.0.0.zip
├── reports/                    # 测试和验证报告
│   └── pluginVerifier/
└── tmp/                        # 临时文件
```

---

## 🔄 持续集成 (可选)

### GitHub Actions 示例

创建 `.github/workflows/build.yml`：

```yaml
name: Build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Grant execute permission
      run: chmod +x gradlew
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Verify Plugin
      run: ./gradlew verifyPlugin
    
    - name: Upload artifact
      uses: actions/upload-artifact@v4
      with:
        name: plugin-distribution
        path: build/distributions/*.zip
```

---

## ❓ 常见问题

### Q: Gradle 下载缓慢？

**A:** 配置国内镜像，编辑 `~/.gradle/init.gradle`：
```groovy
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin/' }
    }
}
```

### Q: runIde 启动失败？

**A:** 检查以下几点：
1. 确保 JDK 17+ 已安装
2. 清理缓存：`./gradlew clean`
3. 删除 `.intellijPlatform` 目录后重试

### Q: 插件在新版 IDE 不兼容？

**A:** 更新 `plugin.xml` 中的版本范围：
```xml
<idea-version since-build="232" until-build="251.*"/>
```

### Q: verifyPlugin 报错？

**A:** 常见原因：
- 使用了已废弃的 API
- 依赖的插件未声明
- 版本范围不正确

查看详细报告：`build/reports/pluginVerifier/`

### Q: 如何调试插件？

**A:** 
1. 在 IDEA 中配置 Run Configuration
2. 选择 `Gradle` → `runIde`
3. 在代码中设置断点
4. 以 Debug 模式运行

---

## 📚 参考资源

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Gradle IntelliJ Plugin](https://github.com/JetBrains/gradle-intellij-plugin)
- [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)
- [Publishing Plugins](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)

---

<p align="center">
  <strong>🏰 BookmarkPalace</strong> - 让代码导航更高效！<br>
  <sub>Made with ❤️ by 龙龙 longlongcoder</sub>
</p>
