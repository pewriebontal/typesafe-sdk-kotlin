import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator)
}

group = "net.bontal"

abstract class GenerateVersionTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile.resolve("net/bontal/typesafesdk/internal")
        dir.mkdirs()
        dir.resolve("Version.kt").writeText(
            "package net.bontal.typesafesdk.internal\n\ninternal const val VERSION = \"${version.get()}\"\n",
        )
    }
}

val generateVersionKt = tasks.register<GenerateVersionTask>("generateVersionKt") {
    version.set(providers.gradleProperty("version"))
    outputDir.set(layout.buildDirectory.dir("generated/version"))
}

kotlin {
    android {
        namespace = "net.bontal.typesafesdk"
        compileSdk = 37
        minSdk = 21
        withHostTest {
            isReturnDefaultValues = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    explicitApi()

    coreLibrariesVersion = "2.2.21"

    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }

    jvmToolchain(21)

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVersionKt.map { it.outputDir.get() })
            dependencies {
                api(libs.ktor.client.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.java)
        }
    }
}

dokka {
    moduleName = "typesafe-sdk-kotlin"
}

apiValidation {
    ignoredProjects += "examples"
}

val signWithGpgCommand = providers.gradleProperty("signing.gnupg.keyName").isPresent

mavenPublishing {
    publishToMavenCentral()
    if (signWithGpgCommand || providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates("net.bontal", "typesafe-sdk-kotlin", version.toString())

    pom {
        name = "typesafe-sdk-kotlin"
        description = "Independent Kotlin and Java client for the TypeSafe AI System One API, for Android and the JVM."
        inceptionYear = "2026"
        url = "https://github.com/pewriebontal/typesafe-sdk-kotlin"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "bontal"
                name = "Bontal LLC"
                email = "0x@bontal.net"
                url = "https://bontal.net"
                organization = "Bontal LLC"
                organizationUrl = "https://bontal.net"
            }
        }

        scm {
            url = "https://github.com/pewriebontal/typesafe-sdk-kotlin"
            connection = "scm:git:git://github.com/pewriebontal/typesafe-sdk-kotlin.git"
            developerConnection = "scm:git:ssh://git@github.com/pewriebontal/typesafe-sdk-kotlin.git"
        }
    }
}

tasks.named<Test>("jvmTest") {
    val liveTests = providers.environmentVariable("TYPESAFE_LIVE_TESTS").orElse("")
    inputs.property("TYPESAFE_LIVE_TESTS", liveTests)
    outputs.upToDateWhen { liveTests.get().isEmpty() }
}

if (signWithGpgCommand) {
    plugins.withId("signing") {
        extensions.configure<SigningExtension> { useGpgCmd() }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
