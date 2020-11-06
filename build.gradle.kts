plugins {
    id("com.github.johnrengelman.shadow") version "6.1.0"
    application
    kotlin("jvm") version "1.4.10"
}

application {
    mainClassName = "com.github.creeper123123321.viaaas.VIAaaSKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "com.github.creeper123123321.viaaas"
version = "0.0.1-SNAPSHOT"
extra.set("archivesBaseName", "VIAaaS")

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.viaversion.com/")
}

dependencies {
    implementation("us.myles:viaversion:3.2.0")
    implementation("nl.matsv:viabackwards-all:3.2.0")
    implementation("de.gerrygames:viarewind-all:1.5.2")
    implementation("io.netty:netty-all:4.1.53.Final")

    implementation("org.apache.logging.log4j:log4j-core:2.13.3")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.13.3")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("net.minecrell:terminalconsoleappender:1.2.0")
    implementation("org.jline:jline-terminal-jansi:3.12.1")

    val ktorVersion = "1.4.1"

    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
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
}
