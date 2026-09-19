plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":"))
    implementation(libs.ktor.client.okhttp)
    runtimeOnly(libs.slf4j.nop)
}

application {
    mainClass = "net.bontal.typesafesdk.examples.MainKt"
}
