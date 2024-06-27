import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import com.google.javascript.jscomp.CompilerOptions.LanguageMode
import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import org.gradlewebtools.minify.minifier.js.JsMinifier
import org.gradlewebtools.minify.minifier.js.JsMinifierOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files as JFiles

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.github.hazendaz:htmlcompressor:2.0.0") }
}

plugins {
    `java-library`
    application
    kotlin("jvm") version "1.9.22"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.50.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.palantir.git-version") version "3.0.0"
    id("org.gradlewebtools.minify") version "2.1.0" apply false
}

application {
    mainClass.set("com.viaversion.aas.VIAaaSKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    withSourcesJar()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "17"

val gitVersion: groovy.lang.Closure<String> by extra

group = "com.viaversion.aas"
version = "0.4.19+" + try {
    gitVersion()
} catch (e: Exception) {
    "unknown"
}

extra.set("archivesBaseName", "VIAaaS")

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    val vvVer = "5.0.2-SNAPSHOT"
    val vbVer = "5.0.2-SNAPSHOT"
    val vrVer = "4.0.1-SNAPSHOT"
    implementation("com.viaversion:viaversion-common:$vvVer") { isTransitive = false }
    implementation("com.viaversion:viabackwards-common:$vbVer") { isTransitive = false }
    implementation("com.viaversion:viarewind-common:$vrVer") { isTransitive = false }
    implementation("net.raphimc:ViaAprilFools:3.0.1-SNAPSHOT")
    implementation("net.raphimc:ViaLegacy:3.0.2-SNAPSHOT")

    val nettyVer = "4.1.111.Final"
    implementation("io.netty:netty-handler-proxy:$nettyVer")
    implementation("io.netty:netty-resolver-dns:$nettyVer")
    implementation("io.netty:netty-transport-native-epoll:$nettyVer:linux-aarch_64")
    implementation("io.netty:netty-transport-native-epoll:$nettyVer:linux-x86_64")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.65.Final:linux-aarch_64")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.65.Final:linux-x86_64")
    implementation("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.25.Final:linux-aarch_64")
    implementation("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.25.Final:linux-x86_64")

    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("com.velocitypowered:velocity-native:3.3.0-SNAPSHOT")
    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("org.powernukkit.fastutil:fastutil-lite:8.1.1")
    implementation("org.yaml:snakeyaml:2.2")

    val log4jVer = "2.23.1"
    val slf4jVer = "2.0.12"
    implementation("com.lmax:disruptor:3.4.4")
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-jul:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVer")
    implementation("org.jline:jline-terminal-jansi:3.25.1")
    implementation("org.slf4j:slf4j-api:$slf4jVer")

    val ktorVersion = "2.3.11"
    implementation("io.ktor:ktor-network-tls-certificates-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-caching-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-compression:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")
    implementation("io.ktor:ktor-serialization-gson:$ktorVersion")
    implementation("io.ktor:ktor-client-java-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")

    implementation("com.auth0:java-jwt:4.4.0")
}

val run: JavaExec by tasks
run.standardInput = System.`in`

project.configurations.implementation.get().isCanBeResolved = true
tasks {
    named<ShadowJar>("shadowJar") {
        configurations = listOf(project.configurations.implementation.get())
        transform(Log4j2PluginsCacheFileTransformer::class.java)
    }
    build {
        dependsOn(shadowJar)
    }
    jar {
        manifest.attributes("Multi-Release" to "true")
    }
}

class JsMinifyFilter(reader: java.io.Reader) : java.io.FilterReader("".reader()) {
    companion object {
        val minifier = JsMinifier(
            minifierOptions = JsMinifierOptions(
                originalFileNames = true,
                languageOut = LanguageMode.ECMASCRIPT_2020,
                rewritePolyfills = true
            )
        )
    }

    init {
        val file = JFiles.createTempDirectory("via-").resolve("tmp-minify.js").toFile().also {
            it.writeText(reader.readText())
        }
        minifier.minify(file.parentFile, file.parentFile)
        `in` = file.readText(Charsets.UTF_8).reader()
        file.delete()
        file.parentFile.delete()
    }
}

class HtmlMinifyFilter(reader: java.io.Reader) : java.io.FilterReader("".reader()) {
    init {
        `in` = HtmlCompressor().compress(reader.readText()).reader()
    }
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("viaaas_info.json") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>(
            "tokens" to mapOf("version" to project.version)
        )
    }
    filesMatching("**/*.js") {
        filter<JsMinifyFilter>()
    }
    filesMatching("**/*.html") {
        filter<HtmlMinifyFilter>()
    }
}

tasks.register<Exec>("compileTs") {
    group = "build"
    description = "Compiles TypeScript resources into JS"
    workingDir = rootProject.rootDir

    onlyIf {
        val hasNpx = try {
            val result = exec {
                isIgnoreExitValue = true
                commandLine("sh", "-c", "command -v npx")
            }
            result.exitValue == 0
        } catch (e: Exception) {
            false
        }

        if (!hasNpx) println("Command npx isn't available")
        return@onlyIf hasNpx
    }

    commandLine("npx", "tsc")
}

tasks.getByName("processResources").dependsOn("compileTs")

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.getByName("shadowJar")) {
                builtBy(tasks.getByName("shadowJar"))
            }
        }
    }
    repositories {
        // mavenLocal()
    }
}
