import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

plugins {
    kotlin("jvm") version "1.4.10"
    id("org.jetbrains.intellij") version "0.7.2"
    id("net.researchgate.release") version "2.8.1"
}

group = "org.gap.ijplugins.spring.ideaspringtools"

if(version.toString().endsWith("SNAPSHOT")) {
    version = version.toString().replace("SNAPSHOT", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HH.mm.ss.SSS")))
}

repositories {
    maven ("https://repo.huaweicloud.com/repository/maven")
    // mavenCentral()
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

    implementation("org.springframework.ide.vscode:commons-java:1.26.0-SNAPSHOT")
    languageServer("org.springframework.ide.vscode:spring-boot-language-server:1.26.0-SNAPSHOT:exec") {
        isTransitive = false
    }
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version = "IC-2020.3"
    pluginName = "idea-spring-tools"
    setPlugins("IntelliLang", "java")
    jreRepo = "https://jetbrains.bintray.com/intellij-jbr"
    downloadSources = false
}

tasks {
    compileJava {
        sourceCompatibility = "11"
        targetCompatibility = "11"
        options.encoding = StandardCharsets.UTF_8.name()
    }
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks.buildSearchableOptions {
    enabled = false
}

tasks.getByName<PatchPluginXmlTask>("patchPluginXml") {
    setUntilBuild("211.*")
    setSinceBuild("191.*")
}

tasks.getByName<PrepareSandboxTask>("prepareSandbox").doLast {
    val pluginServerDir = "${intellij.sandboxDirectory}/plugins/${intellij.pluginName}/lib/server"

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
                    <plugin id="org.gap.ijplugins.spring.idea-spring-tools" version="${archiveVersion}">
                        <idea-version since-build="191.8026.42" until-build="213.*" />
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
    afterReleaseBuild {
        dependsOn("publishPlugin")
    }

    publishPlugin {
        setToken(System.getenv("JB_API_KEY"))
    }

    runIde {
        setJvmArgs(listOf("-Dsts4.jvmargs=-Xmx512m -Xms512m"))
    }
}
