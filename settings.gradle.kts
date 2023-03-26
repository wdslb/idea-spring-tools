pluginManagement{
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
    }
}

rootProject.name = "idea-spring-tools"
include("lsp4intellij")
project(":lsp4intellij").projectDir = file("dependency-build/lsp4intellij")
