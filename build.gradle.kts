import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    application
    kotlin("jvm") version "1.4.32"
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.github.ben-manes.versions") version "0.38.0"
    id("com.palantir.git-version") version "0.12.3"
}

application {
    mainClassName = "com.github.creeper123123321.viaaas.VIAaaSKt"
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
version = "0.2.1-SNAPSHOT+" + try {
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
}

dependencies {
    implementation("us.myles:viaversion:3.3.0-21w13a") { isTransitive = false }
    implementation("nl.matsv:viabackwards:3.3.0-21w13a") { isTransitive = false }
    implementation("com.github.ViaVersion.ViaRewind:viarewind-all:dev-SNAPSHOT") { isTransitive = false }
    implementation("io.netty:netty-all:4.1.61.Final")
    implementation("org.yaml:snakeyaml:1.28")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("org.powernukkit.fastutil:fastutil-lite:8.1.1")

    val log4jVer = "2.14.1"
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-jul:$log4jVer")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("net.minecrell:terminalconsoleappender:1.2.0")
    implementation("org.jline:jline-terminal-jansi:3.19.0")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("org.tukaani:xz:1.9")

    val ktorVersion = "1.5.2"
    implementation(kotlin("stdlib-jdk8"))
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

tasks.named<ProcessResources>("processResources") {
    filesMatching("viaaas_info.json") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf(
                "version" to project.property("version")
        ))
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
