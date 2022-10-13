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
    dependencies { classpath("com.github.hazendaz:htmlcompressor:1.7.3") }
}

plugins {
    `java-library`
    application
    kotlin("jvm") version "1.7.20"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.42.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.palantir.git-version") version "0.15.0"
    id("org.gradlewebtools.minify") version "1.3.1" apply false
}

application {
    mainClass.set("com.viaversion.aas.VIAaaSKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "11"

val gitVersion: groovy.lang.Closure<String> by extra

group = "com.github.creeper123123321.viaaas"
version = "0.4.18+" + try {
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

    val vvVer = "4.4.3-SNAPSHOT"
    val vbVer = "4.4.3-SNAPSHOT"
    val vrVer = "9e4ac93"
    implementation("com.viaversion:viaversion:$vvVer") { isTransitive = false }
    implementation("com.viaversion:viabackwards:$vbVer") { isTransitive = false }
    implementation("com.github.ViaVersion.ViaRewind:viarewind-all:$vrVer") { isTransitive = false }

    val nettyVer = "4.1.82.Final"
    implementation("io.netty:netty-handler-proxy:$nettyVer")
    implementation("io.netty:netty-resolver-dns:$nettyVer")
    implementation("io.netty:netty-transport-native-epoll:$nettyVer:linux-aarch_64")
    implementation("io.netty:netty-transport-native-epoll:$nettyVer:linux-x86_64")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.54.Final:linux-aarch_64")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.54.Final:linux-x86_64")
    implementation("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.15.Final:linux-aarch_64")
    implementation("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.15.Final:linux-x86_64")

    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.velocitypowered:velocity-native:3.1.2-SNAPSHOT")
    implementation("net.coobird:thumbnailator:0.4.17")
    implementation("org.powernukkit.fastutil:fastutil-lite:8.1.1")
    implementation("org.yaml:snakeyaml:1.33")

    val log4jVer = "2.19.0"
    val slf4jVer = "2.0.3"
    implementation("com.lmax:disruptor:3.4.4")
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-jul:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVer")
    implementation("org.jline:jline-terminal-jansi:3.21.0")
    implementation("org.slf4j:slf4j-api:$slf4jVer")

    val ktorVersion = "2.1.2"
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

    implementation("com.auth0:java-jwt:4.0.0")
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
