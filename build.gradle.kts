plugins {
    application
    kotlin("jvm") version "1.3.72"
}

application {
    mainClassName = "com.github.creeper123123321.viaaas.ViaaaSKt"
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
    implementation("us.myles:viaversion:3.1.1")
    implementation("nl.matsv:viabackwards-all:3.1.1")
    implementation("de.gerrygames:viarewind-all:1.5.1")
    implementation("net.md-5:bungeecord-chat:1.16-R0.3")
    implementation("io.netty:netty-all:4.1.51.Final")
    implementation(kotlin("stdlib-jdk8"))

    val ktorVersion = "1.4.0"

    implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion")
}

val run: JavaExec by tasks
run.standardInput = System.`in`