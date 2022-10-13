
object Versions {
    const val konfig_version = "1.6.10.0"
    const val ktor_version = "2.1.1"
    const val kotlin_version = "1.7.10"
    const val ktlint = "0.45.2"
    const val logback_version = "1.2.11"
    const val jackson_version = "2.13.3"
    const val microutils_version = "2.1.23"
    const val altinnKlientVersion = "3.1.0"
}

plugins {
    application
    kotlin("jvm") version "1.7.10"
    id("io.ktor.plugin") version "2.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
}

ktlint {
    this.version.set(Versions.ktlint)
}

group = "no.nav.sosialhjelp"
version = "0.0.1"
application {
    mainClass.set("no.nav.sosialhjelp.avtaler.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:${Versions.logback_version}")

    implementation("com.natpryce:konfig:${Versions.konfig_version}")

    // ktor server
    implementation("io.ktor:ktor-server-core-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-cio:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-netty-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktor_version}")

    // Ktor Client
    implementation("io.ktor:ktor-client-core:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-auth:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-mock:${Versions.ktor_version}")

    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor_version}")
    implementation("io.ktor:ktor-serialization-jackson-jvm:${Versions.ktor_version}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jackson_version}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson_version}")

    implementation("io.github.microutils:kotlin-logging:${Versions.microutils_version}")

    // altinn
    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:${Versions.altinnKlientVersion}")

    // test
    testImplementation("io.ktor:ktor-server-tests-jvm:${Versions.ktor_version}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin_version}")
}
