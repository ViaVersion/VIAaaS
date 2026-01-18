import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import com.google.javascript.jscomp.CommandLineRunner
import com.google.javascript.jscomp.Compiler
import com.google.javascript.jscomp.CompilerOptions
import com.google.javascript.jscomp.SourceFile
import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.github.hazendaz:htmlcompressor:2.3.1")
        classpath("com.google.javascript:closure-compiler:v20251216")
    }
}

plugins {
    `java-library`
    application
    kotlin("jvm") version "2.3.0"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.53.0"
    id("com.gradleup.shadow") version "8.3.9"
    id("com.palantir.git-version") version "4.2.0"
}

application {
    mainClass.set("com.viaversion.aas.VIAaaSKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

if (JavaVersion.current() < JavaVersion.VERSION_21) {
    throw GradleException("This build must be run with Java 21 or higher.")
}

val gitVersion: groovy.lang.Closure<String> by extra

group = "com.viaversion.aas"
version = "0.5.2+" + try {
    gitVersion()
} catch (e: Exception) {
    "unknown"
}

extra.set("archivesBaseName", "VIAaaS")

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    val vvVer = "5.7.2-SNAPSHOT"
    val vbVer = "5.7.1"
    val vrVer = "4.0.14"
    val vafVer = "4.0.8"
    val vlVer = "3.0.13"
    implementation("com.viaversion:viaversion-common:$vvVer") { isTransitive = false }
    implementation("com.viaversion:viabackwards-common:$vbVer") { isTransitive = false }
    implementation("com.viaversion:viarewind-common:$vrVer") { isTransitive = false }
    implementation("com.viaversion:viaaprilfools-common:$vafVer") { isTransitive = false }
    implementation("net.raphimc:ViaLegacy:$vlVer")

    val nettyVer = "4.2.9.Final"
    val nettyBoringSslVer = "2.0.74.Final"
    implementation("io.netty:netty-handler-proxy:$nettyVer")
    implementation("io.netty:netty-resolver-dns:$nettyVer")
    implementation("io.netty:netty-transport-native-epoll:$nettyVer:linux-aarch_64")
    implementation("io.netty:netty-transport-native-epoll:$nettyVer:linux-x86_64")
    implementation("io.netty:netty-transport-native-io_uring:$nettyVer:linux-aarch_64")
    implementation("io.netty:netty-transport-native-io_uring:$nettyVer:linux-x86_64")
    implementation("io.netty:netty-tcnative-boringssl-static:$nettyBoringSslVer:linux-aarch_64")
    implementation("io.netty:netty-tcnative-boringssl-static:$nettyBoringSslVer:linux-x86_64")

    implementation("com.google.guava:guava:33.5.0-jre")
    implementation("com.velocitypowered:velocity-native:3.4.0-SNAPSHOT")
    implementation("net.coobird:thumbnailator:0.4.21")
    implementation("org.powernukkit.fastutil:fastutil-lite:8.1.1")
    implementation("org.yaml:snakeyaml:2.5")

    val log4jVer = "2.24.3"
    val slf4jVer = "2.0.17"
    implementation("com.lmax:disruptor:4.0.0")
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-jul:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVer")
    implementation("org.jline:jline-terminal-jansi:3.30.3")
    implementation("org.slf4j:slf4j-api:$slf4jVer")

    val ktorVersion = "3.3.3"
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

    implementation("com.auth0:java-jwt:4.5.0")
}

val run: JavaExec by tasks
run.standardInput = System.`in`

tasks {
    shadowJar {
        transform(Log4j2PluginsCacheFileTransformer())
        mergeServiceFiles()
    }
    build {
        dependsOn(shadowJar)
    }
}

class JsMinifyFilter(reader: Reader) : FilterReader(minify(reader)) {
    companion object {
        private fun minify(reader: Reader): Reader {
            val sourceCode = reader.readText()
            val compiler = Compiler()
            val options = CompilerOptions().apply {
                setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2020)
                setRewritePolyfills(true)
                //setWarningLevel(com.google.javascript.jscomp.DiagnosticGroups.CHECK_TYPES, com.google.javascript.jscomp.CheckLevel.OFF)
            }
            val input = SourceFile.fromCode("script.js", sourceCode)
            val result = compiler.compile(
                CommandLineRunner.getBuiltinExterns(CompilerOptions.Environment.BROWSER),
                listOf(input),
                options
            )
            return if (result.success) {
                StringReader(compiler.toSource())
            } else {
                println("JS Minification failed for a file. Errors: ${result.errors.joinToString { it.description }}")
                StringReader(sourceCode)
            }
        }
    }
}

class HtmlMinifyFilter(reader: Reader) : FilterReader("".reader()) {
    init {
        `in` = HtmlCompressor().compress(reader.readText()).reader()
    }
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("viaaas_info.json") {
        filter<ReplaceTokens>(
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

abstract class CompileTs : DefaultTask() {
    init {
        group = "build"
        description = "Compiles TypeScript resources into JS"
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun execute() {
        val os = DefaultNativePlatform.getCurrentOperatingSystem()
        val checkCmd = if (os.isWindows) listOf("where", "npx") else listOf("which", "npx")

        val checkResult = execOperations.exec {
            isIgnoreExitValue = true
            commandLine(checkCmd)
        }

        if (checkResult.exitValue != 0) {
            logger.lifecycle("Command npx isn't available. Skipping compilation.")
            return
        }

        execOperations.exec {
            workingDir = project.rootDir
            commandLine("npx", "tsc")
        }
    }
}

tasks.register<CompileTs>("compileTs")

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
