import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import org.gradlewebtools.minify.minifier.js.JSMinifierOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files as JFiles

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.github.hazendaz:htmlcompressor:1.7.1") }
}

plugins {
    `java-library`
    application
    kotlin("jvm") version "1.5.10"
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("com.palantir.git-version") version "0.12.3"
    id("org.gradlewebtools.minify") version "1.1.1" apply false
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
version = "0.4.0-SNAPSHOT+" + try {
    gitVersion()
} catch (e: Exception) {
    "unknown"
}

extra.set("archivesBaseName", "VIAaaS")

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.viaversion.com/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://jitpack.io")
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("com.viaversion:viaversion:4.0.0-1.17-pre5-SNAPSHOT") { isTransitive = false }
    implementation("com.viaversion:viabackwards:4.0.0-1.17-pre5-SNAPSHOT") { isTransitive = false }
    implementation("com.github.ViaVersion.ViaRewind:viarewind-all:dev-SNAPSHOT") { isTransitive = false }
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("io.netty:netty-all:4.1.65.Final")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.39.Final")
    implementation("org.powernukkit.fastutil:fastutil-lite:8.1.1")
    implementation("org.yaml:snakeyaml:1.28")

    val log4jVer = "2.14.1"
    implementation("net.minecrell:terminalconsoleappender:1.2.0")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-jul:$log4jVer")
    implementation("org.jline:jline-terminal-jansi:3.20.0")
    implementation("org.slf4j:slf4j-api:1.7.30")

    val ktorVersion = "1.6.0"
    implementation("com.auth0:java-jwt:3.16.0")
    implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("io.ipinfo:ipinfo-api:1.1")
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
        dependsOn(named("dependencyUpdates"))
    }
    jar {
        manifest.attributes("Multi-Release" to "true")
    }
}

class JsMinifyFilter(reader: java.io.Reader) : java.io.FilterReader("".reader()) {
    init {
        val minifier = org.gradlewebtools.minify.minifier.js.JsMinifier(
            minifierOptions = JSMinifierOptions(originalFileNames = true)
        )
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
            "tokens" to mapOf(
                "version" to project.property("version")
            )
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
