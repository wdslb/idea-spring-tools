import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems

plugins {
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.intellij") version "1.0"
    id("net.researchgate.release") version "2.8.1"
}

group = "org.gap.ijplugins.spring.ideaspringtools"

repositories {
    mavenCentral()
    maven ("https://maven-central.storage-download.googleapis.com/maven2/")
    maven ("https://repo.huaweicloud.com/repository/maven/" )
    maven ("http://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
//    mavenLocal()
    maven ("https://jitpack.io")
    // maven ("https://dl.bintray.com/gayanper/maven")
    maven ("https://repo.spring.io/libs-snapshot/")
}

val languageServer by configurations.creating

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.ballerina-platform:lsp4intellij:master-SNAPSHOT")
    //implementation("com.github.ballerina-platform:lsp4intellij:0.94.2")
    //implementation("com.github.ballerina-platform:lsp4intellij:0.94.1-20201108.10.09.08.085")

    implementation("org.springframework.ide.vscode:commons-java:1.32.0-SNAPSHOT")
    languageServer("org.springframework.ide.vscode:spring-boot-language-server:1.32.0-SNAPSHOT:exec") {
        isTransitive = false
    }
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("IC-2021.3")
    pluginName.set("idea-spring-tools")
    downloadSources.set(true)
    updateSinceUntilBuild.set(true)
    plugins.set(listOf("IntelliLang","java"))
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

tasks.buildSearchableOptions {
    enabled = false
}

tasks.getByName<PatchPluginXmlTask>("patchPluginXml") {
    sinceBuild.set("191.*")
    untilBuild.set("213.*")
}

tasks.getByName<PrepareSandboxTask>("prepareSandbox").doLast {
    val pluginServerDir = intellij.sandboxDir.get() +
            FileSystems.getDefault().separator +
            "plugins" +
            FileSystems.getDefault().separator +
            intellij.pluginName.get() +
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
                    <plugin id="org.gap.ijplugins.spring.idea-spring-tools" version="$archiveVersion">
                        <idea-version since-build="191.8026.42" until-build="213.6777.52" />
                    </plugin>
                </plugins>                
                
            """.trimIndent()
            file("build/distributions/updatePlugins.xml").writeText(content)
        }
    }
}


release {
    failOnUnversionedFiles = false
    failOnSnapshotDependencies = false
    tagTemplate = "$version"
    buildTasks = arrayListOf("buildPlugin")
}

tasks {

    runIde {
        setJvmArgs(listOf("-Dboot.ls.custom.vmargs=-Xmx1g -Xms512m -Dsts4.jvmargs=-Xmx1g -Xms512m"))
    }
}
