plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.longlong"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    
    intellijPlatform {
        intellijIdeaCommunity("2023.2")
        bundledPlugin("com.intellij.platform.images") // 用于 jCEF 支持
        instrumentationTools()
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "232"
            untilBuild = "252.*"
        }
    }
    
    // 插件验证配置
    // 注意：verifyPlugin 需要下载 IDE，如遇网络问题可跳过
    // 直接使用 ./gradlew build buildPlugin 即可打包
    pluginVerification {
        ides {
            // 如需验证，取消注释以下任一行：
            // recommended()  // 自动下载推荐版本（需要稳定网络）
            // local("/Applications/IntelliJ IDEA.app")  // 使用本地安装的 IDE
        }
    }
    
    buildSearchableOptions = false
    instrumentCode = false
    
    // 发布配置
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
