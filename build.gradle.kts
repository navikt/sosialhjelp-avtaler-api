
object Versions {
    const val ktor_version = "2.1.1"
    const val kotlin_version = "1.7.10"
    const val ktlint = "0.45.2"
    const val logback_version = "1.2.11"
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

group = "no.nav"
version = "0.0.1"
application {
    mainClass.set("no.nav.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-netty-jvm:${Versions.ktor_version}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback_version}")
    testImplementation("io.ktor:ktor-server-tests-jvm:${Versions.ktor_version}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin_version}")
}
