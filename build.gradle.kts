import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeTask

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("org.jetbrains.intellij") version "1.12.0"
    kotlin("jvm") version "1.7.22"
    id("net.researchgate.release") version "2.8.1"
}

group = "org.gap.ijplugins.spring.ideaspringtools"

repositories {
    mavenCentral()
    maven ("https://repo.huaweicloud.com/repository/maven/" )
    maven ("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    maven ("https://jitpack.io")
    maven ("https://repo.spring.io/artifactory/libs-snapshot-local/")
}

val languageServer by configurations.creating

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    //implementation("com.github.ballerina-platform:lsp4intellij:0.94.2")
    implementation(project(":lsp4intellij"))

    implementation("org.springframework.ide.vscode:commons-java:1.40.0-SNAPSHOT")
    languageServer("org.springframework.ide.vscode:spring-boot-language-server:1.40.0-SNAPSHOT:exec") {
        isTransitive = false
    }
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("222-EAP-SNAPSHOT")
    type.set("IC")
    pluginName.set("idea-spring-tools")
    plugins.set(listOf("java"))
    updateSinceUntilBuild.set(true)
}

tasks {
    compileJava {
        sourceCompatibility = "11"
        targetCompatibility = "11"
        options.encoding = StandardCharsets.UTF_8.name()
        options.isDeprecation = true
    }
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }
}
tasks.getByName<PatchPluginXmlTask>("patchPluginXml") {
    untilBuild.set("223.*")
    sinceBuild.set("221.*")
}

tasks.getByName<PrepareSandboxTask>("prepareSandbox").doLast {
    val pluginServerDir = "${intellij.sandboxDir.get()} +" +
            FileSystems.getDefault().separator +
            "plugins" +
            FileSystems.getDefault().separator +
            "${intellij.pluginName.get()}" +
            FileSystems.getDefault().separator +
            "lib" +
            FileSystems.getDefault().separator +
            "server"

    mkdir(pluginServerDir)
    copy {
        from(languageServer)
        into(pluginServerDir)
        rename("spring-boot-language-server.*\\.jar", "language-server.jar")
    }
}


tasks {
    buildPlugin {
        doLast() {
            val content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <plugins>
                    <plugin id="org.gap.ijplugins.spring.idea-spring-tools" url="https://dl.bintray.com/gayanper/idea-spring-tools/${intellij.pluginName}-${version}.zip"
                        version="${version}">
                        <idea-version since-build="221.5080.210" until-build="223.*" />
                    </plugin>
                </plugins>                
                
            """.trimIndent()
            file("build/distributions/updatePlugins.xml").writeText(content)
        }
    }

    runIde {
        setJvmArgs(listOf("-Dsts4.jvmargs=-Xmx512m -Xms512m"))
        jbrVersion.set("17.0.3b469.37")
    }
}


release {
    failOnUnversionedFiles = false
    failOnSnapshotDependencies = false
    tagTemplate = "$version"
    buildTasks = arrayListOf("buildPlugin")
}
