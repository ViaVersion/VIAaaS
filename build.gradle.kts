import com.google.javascript.jscomp.CompilerOptions.LanguageMode
import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import org.gradlewebtools.minify.minifier.js.JsMinifier
import org.gradlewebtools.minify.minifier.js.JsMinifierOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files as JFiles

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.github.hazendaz:htmlcompressor:1.7.2") }
}

plugins {
    `java-library`
    application
    kotlin("jvm") version "1.5.31"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.39.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("com.palantir.git-version") version "0.12.3"
    id("org.gradlewebtools.minify") version "1.3.0" apply false
}

application {
    mainClass.set("com.viaversion.aas.VIAaaSKt")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
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
version = "0.4.15+" + try {
    gitVersion()
} catch (e: Exception) {
    "unknown"
}

extra.set("archivesBaseName", "VIAaaS")

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com/")
    maven("https://nexus.velocitypowered.com/repository/maven-public/")
    maven("https://jitpack.io/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    val vvVer = "4.1.0-1.18-pre4-SNAPSHOT"
    val vbVer = "4.1.0-1.18-pre2-SNAPSHOT"
    val vrVer = "d93606d"
    implementation("com.viaversion:viaversion:$vvVer") { isTransitive = false }
    implementation("com.viaversion:viabackwards:$vbVer") { isTransitive = false }
    implementation("com.github.ViaVersion.ViaRewind:viarewind-all:$vrVer") { isTransitive = false }

    implementation("io.netty:netty-all:4.1.70.Final")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.45.Final")
    implementation("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.10.Final:linux-x86_64")

    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.velocitypowered:velocity-native:3.0.1")
    implementation("net.coobird:thumbnailator:0.4.14")
    implementation("org.powernukkit.fastutil:fastutil-lite:8.1.1")
    implementation("org.yaml:snakeyaml:1.29")

    val log4jVer = "2.14.1"
    val slf4jVer = "1.7.32"
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-jul:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVer")
    implementation("org.jline:jline-terminal-jansi:3.21.0")
    implementation("org.slf4j:slf4j-api:$slf4jVer")

    val ktorVersion = "1.6.5"
    implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

    implementation("com.auth0:java-jwt:3.18.2")
}

val run: JavaExec by tasks
run.standardInput = System.`in`

project.configurations.implementation.get().isCanBeResolved = true
tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        configurations = listOf(project.configurations.implementation.get())
        transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
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
